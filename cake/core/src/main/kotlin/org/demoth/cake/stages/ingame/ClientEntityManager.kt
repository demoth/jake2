package org.demoth.cake.stages.ingame

import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Disposable
import jake2.qcommon.Com
import jake2.qcommon.Defines
import jake2.qcommon.entity_state_t
import jake2.qcommon.exec.Cvar
import jake2.qcommon.network.messages.server.EntityUpdate
import jake2.qcommon.network.messages.server.FrameHeaderMessage
import jake2.qcommon.network.messages.server.PacketEntitiesMessage
import jake2.qcommon.network.messages.server.PlayerInfoMessage
import jake2.qcommon.network.messages.server.SpawnBaselineMessage
import jake2.qcommon.util.Math3D
import org.demoth.cake.ClientEntity
import org.demoth.cake.ClientFrame
import org.demoth.cake.GameConfiguration
import org.demoth.cake.assets.Md2Asset
import org.demoth.cake.assets.Md2CustomData
import org.demoth.cake.createModelInstance
import org.demoth.cake.modelviewer.createOriginArrows
import kotlin.math.abs

// responsible for managing entity states which are updated from the server
// also manages client side entities (gun model, level model)
class ClientEntityManager : Disposable {
    companion object {
        private const val POWERSCREEN_MODEL_PATH = "models/items/armor/effect/tris.md2"
        private const val LEGACY_POWERSCREEN_ALPHA = 0.30f
        private const val SHELL_RENDERFX_MASK = Defines.RF_SHELL_RED or Defines.RF_SHELL_GREEN or
            Defines.RF_SHELL_BLUE or Defines.RF_SHELL_DOUBLE or Defines.RF_SHELL_HALF_DAM
    }

    val frames: Array<ClientFrame> = Array(Defines.UPDATE_BACKUP) { ClientFrame() }

    var parse_entities: Int = 0 // index (not anded off) into cl_parse_entities[]
    // entity states - updated during processing of [PacketEntitiesMessage]
    private val cl_parse_entities = Array(Defines.MAX_PARSE_ENTITIES) { entity_state_t() }
    private val clientEntities = Array(Defines.MAX_EDICTS) { ClientEntity("") }

    var previousFrame: ClientFrame? =
        ClientFrame() // the frame that we will delta from (for PlayerInfo & PacketEntities)
    val currentFrame = ClientFrame() // latest frame information received from the server

    var time: Int = 0 // this is the time value that the client is rendering at.  always <= cls.realtime
    var lerpAcc: Float = 0f // interpolation accumulator // (0, serverFrame)
    val rDrawSky = Cvar.getInstance().Get("r_drawsky", "1", 0)
    private val rDrawBeams = Cvar.getInstance().Get("r_drawbeams", "1", 0)
    private val rDrawSprites = Cvar.getInstance().Get("r_drawsprites", "1", 0)
    private val rDrawEntities = Cvar.getInstance().Get("r_drawentities", "1", 0)
    private val clGun = Cvar.getInstance().Get("cl_gun", "1", 0)
    private val clVwep = Cvar.getInstance().Get("cl_vwep", "1", Defines.CVAR_ARCHIVE)

    // model instances to be drawn - updated on every server frame
    var visibleEntities = mutableListOf<ClientEntity>()
    // `.sp2` entities are rendered as camera-facing quads via a dedicated sprite renderer.
    var visibleSprites = mutableListOf<ClientEntity>()
    // RF_BEAM entities are collected separately because they are rendered as generated geometry.
    var visibleBeams = mutableListOf<ClientEntity>()
    // Per-frame pool for linked model passes (`modelindex2/3/4`).
    // Reused to avoid per-frame allocations while preserving independent render instances.
    private val linkedEntityPool = mutableListOf<ClientEntity>()
    private var linkedEntityPoolSize = 0

    var viewGun: ClientEntity? = null

    var skyEntity: ClientEntity? = null

    // debug related
    private val rDebug = Cvar.getInstance().Get("r_debug", "0", 0)
    private var debugWorldOrigin: ClientEntity? = null

    var surpressCount = 0

    fun getEntityOrigin(entityIndex: Int): Vector3? {
        if (entityIndex !in 0..<Defines.MAX_EDICTS) {
            return null // todo: warn
        }
        val entity = clientEntities[entityIndex]
        return Vector3(entity.current.origin[0], entity.current.origin[1], entity.current.origin[2])
    }

    /**
     * Read-only accessor for replicated entity angles.
     *
     * Effects code may use this for muzzle/beam orientation, but state mutation remains owned by this
     * manager via server messages only.
     */
    fun getEntityAngles(entityIndex: Int): FloatArray? {
        if (entityIndex !in 0..<Defines.MAX_EDICTS) {
            return null // todo: warn
        }
        return clientEntities[entityIndex].current.angles.copyOf()
    }

    fun processBaselineMessage(msg: SpawnBaselineMessage) {
        clientEntities[msg.entityState.index].baseline.set(msg.entityState)
    }

    // Cross-reference: old client iterates dynamic collision entities from
    // `CL_pred.ClipMoveToEntities` and `CL_pred.PMpointcontents` over
    // `cl.frame.num_entities` + `cl_parse_entities`.
    // Also used by `Game3dScreen` for client-side entity event sound dispatch.
    fun forEachCurrentEntityState(action: (entity_state_t) -> Unit) {
        val mask = Defines.MAX_PARSE_ENTITIES - 1
        for (i in 0 until currentFrame.num_entities) {
            val idx = (currentFrame.parse_entities + i) and mask
            action(cl_parse_entities[idx])
        }
    }

    // Iterates current-frame replicated states with their owning client entity state.
    fun forEachCurrentEntity(action: (ClientEntity, entity_state_t) -> Unit) {
        val mask = Defines.MAX_PARSE_ENTITIES - 1
        for (i in 0 until currentFrame.num_entities) {
            val idx = (currentFrame.parse_entities + i) and mask
            val state = cl_parse_entities[idx]
            action(clientEntities[state.index], state)
        }
    }

    /**
     * CL_ParsePacketEntities
     * todo: fix nullability issues, remove !! unsafe dereferences, check duplicate fragments
     */
    fun processPacketEntitiesMessage(msg: PacketEntitiesMessage): Boolean {
        // Save ring buffer head: first entity slot for this frame
        currentFrame.parse_entities = parse_entities
        currentFrame.num_entities = 0

        // Determine if we have a valid previous frame to delta from
        val oldFrame = previousFrame?.takeIf { it.valid && it.num_entities > 0 }

        val mask = Defines.MAX_PARSE_ENTITIES - 1

        var oldindex = 0
        var oldstate: entity_state_t? = null
        var oldnum = if (oldFrame != null && oldFrame.num_entities > 0) {
            oldstate = cl_parse_entities[(oldFrame.parse_entities + oldindex) and mask]
            oldstate.index
        } else {
            9999
        }

        // Helper to advance old pointer safely
        fun advanceOld() {
            oldindex++
            if (oldFrame == null || oldindex >= oldFrame.num_entities) {
                oldstate = null
                oldnum = 9999
            } else {
                oldstate = cl_parse_entities[(oldFrame.parse_entities + oldindex) and mask]
                oldnum = oldstate!!.index
            }
        }

        // Copy unchanged old entities while oldnum < targetNum
        fun copyUnchangedUntil(targetNum: Int) {
            while (oldnum < targetNum) {
                // unchanged entity copied over to new frame
                DeltaEntity(currentFrame, oldnum, oldstate!!, null)
                advanceOld()
            }
        }

        // Process updates in order
        for (update in msg.updates) {
            val newnum = update.header.number

            // Bring old pointer up to the current newnum, copying unchanged
            copyUnchangedUntil(newnum)

            if ((update.header.flags and Defines.U_REMOVE) != 0) {
                // Removal: ensure we are at the entity being removed
                // If due to some earlier mismatch we are still behind, pull forward copying unchanged
                copyUnchangedUntil(newnum)
                // Now either oldnum == newnum (expected) or oldnum > newnum (already gone)
                if (oldnum == newnum) {
                    // Skip it: don't emit to new frame
                    advanceOld()
                }
                // continue to next update
                continue
            }

            if (oldnum == newnum) {
                // Delta from previous state
                DeltaEntity(currentFrame, newnum, oldstate!!, update)
                advanceOld()
            } else {
                // oldnum > newnum => new entity: delta from baseline
                DeltaEntity(currentFrame, newnum, clientEntities[newnum].baseline, update)
            }
        }

        // Any remaining old entities are unchanged: copy them over
        while (oldnum != 9999) {
            DeltaEntity(currentFrame, oldnum, oldstate!!, null)
            advanceOld()
        }

        // Save the frame for future deltas
        frames[currentFrame.serverframe and Defines.UPDATE_MASK].set(currentFrame)

        return currentFrame.valid
    }

    /**
     * Update entity based on the delta [update] received from server and it's previous state.
     */
    private fun DeltaEntity(frame: ClientFrame, newnum: Int, old: entity_state_t, update: EntityUpdate?) {
        val entity: ClientEntity = clientEntities[newnum]
        // parse_entities now points to the last state from last frame
        val newState: entity_state_t = cl_parse_entities[parse_entities and (Defines.MAX_PARSE_ENTITIES - 1)]
        parse_entities++ // we will need this for the next frame
        frame.num_entities++

        newState.set(old)
        newState.index = newnum
        newState.event = 0
        Math3D.VectorCopy(old.origin, newState.old_origin)

        if (update != null) {
            newState.setByFlags(update.newState, update.header.flags)
        }

        // Cake: Decide if we should clear the cached model instance in certain cases.
        // Keep this continuity check separate from no-lerp discontinuity handling below.
        val wasInPreviousFrame = entity.serverframe == frame.serverframe - 1
        val modelIndexChanged =
            (newState.modelindex != entity.current.modelindex) ||
                    (newState.modelindex2 != entity.current.modelindex2) ||
                    (newState.modelindex3 != entity.current.modelindex3) ||
                    (newState.modelindex4 != entity.current.modelindex4)
        val becameBeam = (newState.renderfx and Defines.RF_BEAM) != 0
        val becameInvisible = newState.modelindex == 0

        if (!wasInPreviousFrame || modelIndexChanged || becameInvisible || becameBeam) {
            // Any time visibility or model identity changes across frames, drop the cached instance.
            entity.modelInstance = null
            entity.spriteAsset = null
        }

        // some data changes will force no lerping
        if (newState.modelindex != entity.current.modelindex
            || newState.modelindex2 != entity.current.modelindex2
            || newState.modelindex3 != entity.current.modelindex3
            || newState.modelindex4 != entity.current.modelindex4
            || abs(newState.origin[0] - entity.current.origin[0]) > 512
            || abs(newState.origin[1] - entity.current.origin[1]) > 512
            || abs(newState.origin[2] - entity.current.origin[2]) > 512
            || newState.event == Defines.EV_PLAYER_TELEPORT
            || newState.event == Defines.EV_OTHER_TELEPORT) {
            entity.serverframe = -99
        }

        // Cross-reference legacy CL_ents.DeltaEntity ordering:
        // this continuity check must happen after potential serverframe=-99 overrides.
        val reappeared = entity.serverframe != frame.serverframe - 1
        if (!reappeared) { // shuffle the last state to previous
            // Copy !
            entity.prev.set(entity.current)
        } else {
            // wasn't in last update, so initialize some things
            entity.trailcount = 1024 // for diminishing rocket / grenade trails
            // duplicate the current state so lerping doesn't hurt anything
            entity.prev.set(newState)
            if (newState.event == Defines.EV_OTHER_TELEPORT) {
                Math3D.VectorCopy(newState.origin, entity.prev.origin)
                Math3D.VectorCopy(newState.origin, entity.lerp_origin)
            } else {
                Math3D.VectorCopy(newState.old_origin, entity.prev.origin)
                Math3D.VectorCopy(newState.old_origin, entity.lerp_origin)
            }
        }
        entity.serverframe = frame.serverframe
        // Copy !
        entity.current.set(newState)
    }

    /**
     * Computes frame-visible entity buckets for rendering.
     *
     * Buckets:
     * - [visibleEntities]: model-backed entities (MD2/brush/debug).
     * - [visibleSprites]: `.sp2` billboard entities.
     * - [visibleBeams]: RF_BEAM entities rendered by beam pipeline.
     *
     * Compatibility behavior:
     * - Resolves legacy auto-frame flags (`EF_ANIM01/23/ALL/ALLFAST`).
     * - Resolves legacy translucency upgrades from effects to renderfx.
     * - Resolves legacy alpha overrides (`EF_BFG`, `EF_PLASMA`, `EF_SPHERETRANS`).
     *
     * Invariants:
     * - `visibleSprites` entities must have `spriteAsset != null` and `modelInstance == null`.
     * - `visibleEntities` entities must have `modelInstance != null`.
     * - Local player model remains culled from world entity bucket.
     * - `modelindex == 255` resolves player model via `skinnum & 0xFF` client slot.
     * - Linked model passes (`modelindex2/3/4`) are emitted as companion entities.
     *
     * Legacy counterpart:
     * - `client/CL_ents.AddPacketEntities`.
     */
    fun computeVisibleEntities(gameConfig: GameConfiguration) {
        lerpAcc = 0f
        visibleEntities.clear()
        visibleSprites.clear()
        visibleBeams.clear()
        linkedEntityPoolSize = 0
        if (rDebug.value != 0f) {
            if (debugWorldOrigin == null) {
                debugWorldOrigin = ClientEntity("origin").apply {
                    modelInstance = createOriginArrows(16f)
                    depthHack = true
                }
            }
            visibleEntities += debugWorldOrigin!!
        }
        // World model-0 rendering is owned by the dedicated batch renderer path.

        val mask = Defines.MAX_PARSE_ENTITIES - 1

        for (i in 0 until currentFrame.num_entities) {
            // Make the mask application explicit to avoid any precedence confusion
            val idx = (currentFrame.parse_entities + i) and mask
            val newState = cl_parse_entities[idx]
            val entity = clientEntities[newState.index]
            val resolvedFrame = resolveEntityFrame(newState)
            val resolvedRenderFx = resolveEntityRenderFx(newState.effects, newState.renderfx)
            val resolvedAlpha = resolveEntityAlpha(newState.effects, newState.renderfx)
            entity.resolvedFrame = resolvedFrame
            entity.resolvedRenderFx = resolvedRenderFx
            entity.alpha = resolvedAlpha

            // not visible to client
            if (newState.modelindex == 0) {
                continue
            }
            // Beam entities are rendered via a dedicated path; modelindex is non-zero only to keep
            // network visibility behavior compatible with the original protocol.
            if ((newState.renderfx and Defines.RF_BEAM) != 0) {
                entity.modelInstance = null
                if (rDrawBeams?.value != 0f) {
                    visibleBeams += entity
                }
                continue
            }

            // assign a visible 3d model
            val modelIndex = newState.modelindex
            if (modelIndex == 255) {
                // custom player model/skin: index comes from low byte of skinnum
                val playerModel = gameConfig.playerConfiguration.getPlayerModel(newState.skinnum, newState.renderfx)
                entity.name = "player"
                if (playerModel != null && (entity.modelInstance == null || entity.modelInstance.model !== playerModel)) {
                    entity.modelInstance = createModelInstance(playerModel)
                    entity.spriteAsset = null
                }
            } else {
                val model = gameConfig.getModel(modelIndex)
                val sprite = gameConfig.getSpriteModel(modelIndex)
                entity.name = gameConfig.getModelName(modelIndex)
                when {
                    model != null -> {
                        if (entity.modelInstance == null || entity.modelInstance.model !== model) {
                            entity.modelInstance = createModelInstance(model)
                        }
                        entity.spriteAsset = null
                    }
                    sprite != null -> {
                        entity.modelInstance = null
                        entity.spriteAsset = sprite
                    }
                    else -> {
                        // prevent stale model reuse when a config/model lookup fails
                        entity.modelInstance = null
                        entity.spriteAsset = null
                    }
                }
                // todo: warning if the model was not found!
            }

            // render it if the model was successfully loaded
            if (newState.index != gameConfig.playerConfiguration.playerIndex + 1) { // do not render our own model
                if (entity.modelInstance != null) {
                    if (rDrawEntities?.value != 0f) {
                        (entity.modelInstance.userData as? Md2CustomData)?.let { userData ->
                            userData.frame1 = entity.prev.frame
                            userData.frame2 = resolvedFrame
                            // player models are loaded with an external skin texture, so slot is always 0
                            userData.skinIndex = if (newState.modelindex == 255) 0 else newState.skinnum
                        }
                        visibleEntities += entity
                    }
                } else if (entity.spriteAsset != null && rDrawSprites?.value != 0f) {
                    visibleSprites += entity
                }
                appendLinkedModelPasses(
                    owner = entity,
                    ownerState = newState,
                    gameConfig = gameConfig,
                    resolvedFrame = resolvedFrame,
                )
                appendPowerScreenPass(
                    owner = entity,
                    ownerState = newState,
                    gameConfig = gameConfig,
                )
            }
        }

        // update player gun model and switch it when weapon changes
        var gunModelChanged = false
        val gunIndex = currentFrame.playerstate.gunindex
        if (gunIndex <= 0) {
            viewGun = null
        } else {
            val gunModel = gameConfig.getModel(gunIndex)
            if (gunModel == null) {
                viewGun = null
            } else {
                val currentGun = viewGun
                if (currentGun == null || currentGun.modelInstance.model !== gunModel) {
                    viewGun = ClientEntity(gameConfig.getModelName(gunIndex) ?: "gun_$gunIndex").apply {
                        modelInstance = createModelInstance(gunModel)
                        depthHack = true
                    }
                    gunModelChanged = true
                }
            }
        }

        // update the gun animation
        viewGun?.let { gun ->
            // viewGun should inherit player's own EF_SHELL effect
            val localPlayerShellRenderFx = clientEntities.getOrNull(gameConfig.playerConfiguration.playerIndex + 1)
                ?.takeIf { it.serverframe == currentFrame.serverframe }
                ?.resolvedRenderFx
                ?.and(SHELL_RENDERFX_MASK)
                ?: 0
            gun.resolvedRenderFx = Defines.RF_MINLIGHT or Defines.RF_DEPTHHACK or Defines.RF_WEAPONMODEL or localPlayerShellRenderFx
            gun.alpha = 1f
            if (rDrawEntities?.value != 0f && clGun?.value != 0f) {
                visibleEntities += gun
            }
            (gun.modelInstance.userData as? Md2CustomData)?.let { userData ->
                userData.frame1 = when {
                    gunModelChanged -> currentFrame.playerstate.gunframe
                    currentFrame.playerstate.gunframe == 0 -> 0
                    else -> previousFrame?.playerstate?.gunframe ?: currentFrame.playerstate.gunframe
                }
                userData.frame2 = currentFrame.playerstate.gunframe
            }
        }
    }

    /**
     * Emits legacy-style linked model passes (`modelindex2/3/4`) as companion entities.
     *
     * Legacy counterpart:
     * `client/CL_ents.AddPacketEntities` linked model branches.
     */
    private fun appendLinkedModelPasses(
        owner: ClientEntity,
        ownerState: entity_state_t,
        gameConfig: GameConfiguration,
        resolvedFrame: Int,
    ) {
        appendLinkedModelPass(owner, ownerState, ownerState.modelindex2, 2, gameConfig, resolvedFrame)
        appendLinkedModelPass(owner, ownerState, ownerState.modelindex3, 3, gameConfig, resolvedFrame)
        appendLinkedModelPass(owner, ownerState, ownerState.modelindex4, 4, gameConfig, resolvedFrame)
    }

    /**
     * Emits legacy powerscreen companion pass when `EF_POWERSCREEN` is active.
     *
     * Legacy counterpart:
     * `client/CL_ents.AddPacketEntities` powerscreen branch (`models/items/armor/effect/tris.md2`).
     */
    private fun appendPowerScreenPass(
        owner: ClientEntity,
        ownerState: entity_state_t,
        gameConfig: GameConfiguration,
    ) {
        if ((ownerState.effects and Defines.EF_POWERSCREEN) == 0) {
            return
        }
        if (rDrawEntities?.value == 0f) {
            return
        }

        val powerScreenModel = gameConfig.tryAcquireAsset<Md2Asset>(POWERSCREEN_MODEL_PATH)?.model ?: return
        val powerScreenEntity = acquireLinkedEntity("powerscreen")

        powerScreenEntity.prev.set(owner.prev)
        powerScreenEntity.current.set(owner.current)
        powerScreenEntity.resolvedFrame = 0
        powerScreenEntity.resolvedRenderFx = Defines.RF_TRANSLUCENT or Defines.RF_SHELL_GREEN
        powerScreenEntity.alpha = owner.alpha * LEGACY_POWERSCREEN_ALPHA
        powerScreenEntity.depthHack = false
        powerScreenEntity.spriteAsset = null

        if (powerScreenEntity.modelInstance == null || powerScreenEntity.modelInstance.model !== powerScreenModel) {
            powerScreenEntity.modelInstance = createModelInstance(powerScreenModel)
        }

        (powerScreenEntity.modelInstance.userData as? Md2CustomData)?.let { userData ->
            // Legacy powerscreen pass is rendered in frame 0 with default skin.
            userData.frame1 = 0
            userData.frame2 = 0
            userData.skinIndex = 0
        }
        visibleEntities += powerScreenEntity
    }

    /**
     * Build one linked pass entity for `modelindex2/3/4`.
     *
     * Important simplifications:
     * - Uses legacy Jake2-style defaults (`flags=0`, `alpha=1`) for linked models.
     * - Preserves defender-sphere transparency special case for `modelindex2`.
     */
    private fun appendLinkedModelPass(
        owner: ClientEntity,
        ownerState: entity_state_t,
        linkedModelIndex: Int,
        linkedSlot: Int,
        gameConfig: GameConfiguration,
        resolvedFrame: Int,
    ) {
        if (linkedModelIndex == 0) {
            return
        }

        val linkedName = if (linkedModelIndex == 255) {
            "linked_weapon"
        } else {
            gameConfig.getModelName(linkedModelIndex) ?: "linked${linkedSlot}_$linkedModelIndex"
        }
        val linkedEntity = acquireLinkedEntity(linkedName)

        // Linked model passes follow the owning entity transform/animation state.
        linkedEntity.prev.set(owner.prev)
        linkedEntity.current.set(owner.current)
        linkedEntity.resolvedFrame = resolvedFrame
        linkedEntity.resolvedRenderFx = 0
        linkedEntity.alpha = 1f
        linkedEntity.depthHack = false

        val model = if (linkedModelIndex == 255) {
            // `modelindex2 == 255` means per-player linked weapon model in legacy protocol.
            gameConfig.playerConfiguration.getPlayerWeaponModel(
                skinnum = ownerState.skinnum,
                vwepEnabled = clVwep.value != 0f,
            )
        } else {
            gameConfig.getModel(linkedModelIndex)
        }
        val sprite = if (linkedModelIndex == 255) {
            null
        } else {
            gameConfig.getSpriteModel(linkedModelIndex)
        }

        when {
            model != null -> {
                if (linkedEntity.modelInstance == null || linkedEntity.modelInstance.model !== model) {
                    linkedEntity.modelInstance = createModelInstance(model)
                }
                linkedEntity.spriteAsset = null
            }
            sprite != null -> {
                linkedEntity.modelInstance = null
                linkedEntity.spriteAsset = sprite
            }
            else -> {
                linkedEntity.modelInstance = null
                linkedEntity.spriteAsset = null
                return
            }
        }

        if (linkedSlot == 2 && linkedModelIndex != 255) {
            val linkedModelName = gameConfig.getModelName(linkedModelIndex)
            if (linkedModelName.equals("models/items/shell/tris.md2", ignoreCase = true)) {
                linkedEntity.resolvedRenderFx = Defines.RF_TRANSLUCENT
                linkedEntity.alpha = 0.32f
            }
        }

        if (linkedEntity.modelInstance != null) {
            if (rDrawEntities?.value == 0f) {
                return
            }
            (linkedEntity.modelInstance.userData as? Md2CustomData)?.let { userData ->
                userData.frame1 = owner.prev.frame
                userData.frame2 = resolvedFrame
                // Legacy linked model path resets skinnum for linked passes.
                userData.skinIndex = 0
            }
            visibleEntities += linkedEntity
        } else if (linkedEntity.spriteAsset != null && rDrawSprites?.value != 0f) {
            visibleSprites += linkedEntity
        }
    }

    private fun acquireLinkedEntity(name: String): ClientEntity {
        if (linkedEntityPoolSize >= linkedEntityPool.size) {
            linkedEntityPool += ClientEntity(name)
        }
        val linkedEntity = linkedEntityPool[linkedEntityPoolSize]
        linkedEntityPoolSize++
        linkedEntity.name = name
        return linkedEntity
    }

    /**
     * Mirrors legacy client frame selection in `CL_ents.AddPacketEntities`.
     */
    private fun resolveEntityFrame(state: entity_state_t): Int {
        val effects = state.effects
        val autoAnim = 2 * time / 1000
        return when {
            (effects and Defines.EF_ANIM01) != 0 -> autoAnim and 1
            (effects and Defines.EF_ANIM23) != 0 -> 2 + (autoAnim and 1)
            (effects and Defines.EF_ANIM_ALL) != 0 -> autoAnim
            (effects and Defines.EF_ANIM_ALLFAST) != 0 -> time / 100
            else -> state.frame
        }
    }

    /**
     * Mirrors legacy renderfx upgrades in `CL_ents.AddPacketEntities`.
     * Shell-related upgrades are consumed by the MD2 Fresnel-rim highlight path.
     */
    private fun resolveEntityRenderFx(effects: Int, renderFx: Int): Int {
        var resolved = renderFx
        if ((effects and Defines.EF_PENT) != 0) {
            resolved = resolved or Defines.RF_SHELL_RED
        }
        if ((effects and Defines.EF_QUAD) != 0) {
            resolved = resolved or Defines.RF_SHELL_BLUE
        }
        if ((effects and Defines.EF_DOUBLE) != 0) {
            resolved = resolved or Defines.RF_SHELL_DOUBLE
        }
        if ((effects and Defines.EF_HALF_DAMAGE) != 0) {
            resolved = resolved or Defines.RF_SHELL_HALF_DAM
        }
        if ((effects and Defines.EF_BFG) != 0 ||
            (effects and Defines.EF_PLASMA) != 0 ||
            (effects and Defines.EF_SPHERETRANS) != 0
        ) {
            resolved = resolved or Defines.RF_TRANSLUCENT
        }
        return resolved
    }

    /**
     * Mirrors legacy alpha tweaks for translucent entity effects in `CL_ents.AddPacketEntities`.
     */
    private fun resolveEntityAlpha(effects: Int, renderFx: Int): Float {
        var alpha = 1f
        if ((renderFx and Defines.RF_TRANSLUCENT) != 0) {
            alpha = 0.70f
        }
        if ((effects and Defines.EF_BFG) != 0) {
            alpha = 0.30f
        }
        if ((effects and Defines.EF_PLASMA) != 0) {
            alpha = 0.60f
        }
        if ((effects and Defines.EF_SPHERETRANS) != 0) {
            alpha = if ((effects and Defines.EF_TRACKERTRAIL) != 0) 0.60f else 0.30f
        }
        return alpha
    }

    fun setSkyModel(model: Model?) {
        skyEntity = if (model == null) {
            null
        } else {
            ClientEntity("sky").apply {
                modelInstance = ModelInstance(model)
            }
        }
    }

    fun processServerFrameHeader(msg: FrameHeaderMessage) {
        // update current frame
        currentFrame.reset()
        currentFrame.serverframe = msg.frameNumber
        currentFrame.deltaframe = msg.lastFrame
        currentFrame.servertime = currentFrame.serverframe * 100

        surpressCount = msg.suppressCount

        // If the frame is delta compressed from data that we
        // no longer have available, we must suck up the rest of
        // the frame, but not use it, then ask for a non-compressed
        // message
        val deltaFrame: ClientFrame?
        if (currentFrame.deltaframe <= 0) {
            // uncompressed frame, don't need a delta frame
            currentFrame.valid = true // uncompressed frame
            deltaFrame = null
        } else {
            deltaFrame = frames[currentFrame.deltaframe and Defines.UPDATE_MASK]
            if (!deltaFrame.valid) { // should never happen
                Com.Printf("asdfasdfasdfDelta from invalid frame (not supposed to happen!).\n")
            }
            if (deltaFrame.serverframe != currentFrame.deltaframe) {
                // The frame that the server did the delta from is too old, so we can't reconstruct it properly.
                Com.Printf("Delta frame too old.\n")
            } else if (parse_entities - deltaFrame.parse_entities > Defines.MAX_PARSE_ENTITIES - 128) {
                Com.Printf("Delta parse_entities too old.\n")
            } else {
                currentFrame.valid = true  // valid delta parse
            }
        }
        previousFrame = deltaFrame

        // determine delta frame:
        // clamp time
        time = time.coerceIn(currentFrame.servertime - 100, currentFrame.servertime)

        // read areabits
        System.arraycopy(msg.areaBits, 0, currentFrame.areabits, 0, msg.areaBits.size);
    }

    fun processPlayerInfoMessage(msg: PlayerInfoMessage) {
        val currentPlayerState = currentFrame.playerstate

        // clear to old value before delta parsing
        val deltaFrame = previousFrame
        if (deltaFrame == null) {
            currentPlayerState.clear()
        } else {
            currentPlayerState.set(deltaFrame.playerstate)
        }

        //
        // parse the pmove_state_t
        //
        if ((msg.deltaFlags and Defines.PS_M_TYPE) != 0)
            currentPlayerState.pmove.pm_type = msg.currentState.pmove.pm_type;

//        if (ClientGlobals.cl.attractloop)
//            state.pmove.pm_type = Defines.PM_FREEZE; // demo playback

        if ((msg.deltaFlags and Defines.PS_M_ORIGIN) != 0)
            currentPlayerState.pmove.origin = msg.currentState.pmove.origin;
        if ((msg.deltaFlags and Defines.PS_M_VELOCITY) != 0)
            currentPlayerState.pmove.velocity = msg.currentState.pmove.velocity;
        if ((msg.deltaFlags and Defines.PS_M_TIME) != 0)
            currentPlayerState.pmove.pm_time = msg.currentState.pmove.pm_time;
        if ((msg.deltaFlags and Defines.PS_M_FLAGS) != 0)
            currentPlayerState.pmove.pm_flags = msg.currentState.pmove.pm_flags;
        if ((msg.deltaFlags and Defines.PS_M_GRAVITY) != 0)
            currentPlayerState.pmove.gravity = msg.currentState.pmove.gravity;
        if ((msg.deltaFlags and Defines.PS_M_DELTA_ANGLES) != 0)
            currentPlayerState.pmove.delta_angles = msg.currentState.pmove.delta_angles;
        //
        // parse the rest of the player_state_t
        //
        if ((msg.deltaFlags and Defines.PS_VIEWOFFSET) != 0)
            currentPlayerState.viewoffset = msg.currentState.viewoffset;
        if ((msg.deltaFlags and Defines.PS_VIEWANGLES) != 0)
            currentPlayerState.viewangles = msg.currentState.viewangles;
        if ((msg.deltaFlags and Defines.PS_KICKANGLES) != 0)
            currentPlayerState.kick_angles = msg.currentState.kick_angles;
        if ((msg.deltaFlags and Defines.PS_WEAPONINDEX) != 0)
            currentPlayerState.gunindex = msg.currentState.gunindex;
        if ((msg.deltaFlags and Defines.PS_WEAPONFRAME) != 0) {
            currentPlayerState.gunframe = msg.currentState.gunframe;
            currentPlayerState.gunoffset = msg.currentState.gunoffset;
            currentPlayerState.gunangles = msg.currentState.gunangles;
        }
        if ((msg.deltaFlags and Defines.PS_BLEND) != 0)
            currentPlayerState.blend = msg.currentState.blend;
        if ((msg.deltaFlags and Defines.PS_FOV) != 0)
            currentPlayerState.fov = msg.currentState.fov;
        if ((msg.deltaFlags and Defines.PS_RDFLAGS) != 0)
            currentPlayerState.rdflags = msg.currentState.rdflags;

        // copy only changed stats
        for (i in (0..<Defines.MAX_STATS)) {
            if ((msg.statbits and (1 shl i)) != 0) {
                currentPlayerState.stats[i] = msg.currentState.stats[i];
            }
        }
    }

    override fun dispose() {
        if (debugWorldOrigin != null)
            debugWorldOrigin!!.modelInstance.model.dispose()
    }
}
