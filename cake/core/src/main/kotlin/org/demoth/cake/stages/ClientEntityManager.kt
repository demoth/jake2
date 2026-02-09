package org.demoth.cake.stages

import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.math.Vector3
import jake2.qcommon.Com
import jake2.qcommon.Defines
import jake2.qcommon.Defines.CS_MODELS
import jake2.qcommon.Defines.MAX_EDICTS
import jake2.qcommon.Defines.MAX_PARSE_ENTITIES
import jake2.qcommon.entity_state_t
import jake2.qcommon.network.messages.server.EntityUpdate
import jake2.qcommon.network.messages.server.FrameHeaderMessage
import jake2.qcommon.network.messages.server.PacketEntitiesMessage
import jake2.qcommon.network.messages.server.PlayerInfoMessage
import jake2.qcommon.network.messages.server.SpawnBaselineMessage
import jake2.qcommon.util.Math3D
import org.demoth.cake.ClientEntity
import org.demoth.cake.ClientFrame
import org.demoth.cake.GameConfiguration
import org.demoth.cake.assets.Md2CustomData
import org.demoth.cake.createModelInstance
import org.demoth.cake.modelviewer.createGrid
import org.demoth.cake.modelviewer.createOriginArrows
import kotlin.math.abs

// responsible for managing entity states which are updated from the server
class ClientEntityManager {
    val frames: Array<ClientFrame> = Array(Defines.UPDATE_BACKUP) { ClientFrame() }

    var parse_entities: Int = 0 // index (not anded off) into cl_parse_entities[]
    // entity states - updated during processing of [PacketEntitiesMessage]
    private val cl_parse_entities = Array(MAX_PARSE_ENTITIES) { entity_state_t() }
    private val clientEntities = Array(MAX_EDICTS) { ClientEntity("") }

    var previousFrame: ClientFrame? = ClientFrame() // the frame that we will delta from (for PlayerInfo & PacketEntities)
    val currentFrame = ClientFrame() // latest frame information received from the server

    var time: Int = 0 // this is the time value that the client is rendering at.  always <= cls.realtime

    // model instances to be drawn - updated on every server frame
    var visibleEntities = mutableListOf<ClientEntity>()
    // RF_BEAM entities are collected separately because they are rendered as generated geometry.
    var visibleBeams = mutableListOf<ClientEntity>()

    var surpressCount = 0

    fun getEntitySoundOrigin(entityIndex: Int): Vector3? {
        if (entityIndex !in 0..<MAX_EDICTS) {
            return null // todo: warn
        }
        val entity = clientEntities[entityIndex]
        return Vector3(entity.current.origin[0], entity.current.origin[1], entity.current.origin[2])
    }

    fun processBaselineMessage(msg: SpawnBaselineMessage) {
        clientEntities[msg.entityState.index].baseline.set(msg.entityState)
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

        val mask = MAX_PARSE_ENTITIES - 1

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
        val newState: entity_state_t = cl_parse_entities[parse_entities and (MAX_PARSE_ENTITIES - 1)]
        parse_entities++ // we will need this for the next frame
        frame.num_entities++

        newState.set(old)
        newState.index = newnum
        newState.event = 0
        Math3D.VectorCopy(old.origin, newState.old_origin)

        if (update != null) {
            newState.setByFlags(update.newState, update.header.flags)
        }

        // Cake: Decide if we should clear the cached model instance in certain cases
        val reappeared = entity.serverframe != frame.serverframe - 1
        val modelIndexChanged =
            (newState.modelindex != entity.current.modelindex) ||
                    (newState.modelindex2 != entity.current.modelindex2) ||
                    (newState.modelindex3 != entity.current.modelindex3) ||
                    (newState.modelindex4 != entity.current.modelindex4)
        val becameBeam = (newState.renderfx and Defines.RF_BEAM) != 0
        val becameInvisible = newState.modelindex == 0

        if (reappeared || modelIndexChanged || becameInvisible || becameBeam) {
            // Any time visibility or model identity changes across frames, drop the cached instance.
            entity.modelInstance = null
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
     * Computes the list of entities that should be visible during the current server frame.
     *
     * Key functionalities:
     * - Resets `lerpAcc` for server frame interpolation.
     * - Adds grid and origin visualization models to the visible entities.
     * - Iterates over the entities in the current frame, instantiating their models if they
     *   haven't been loaded yet and updating them according to the game state.
     * - Collects RF_BEAM entities into [visibleBeams] for dedicated beam rendering.
     * - Attempts to load and manage the player's weapon model, updating its animation frames
     *   as necessary.
     *
     * Known issues and TODOs:
     * - Persistent storage for client entities is not implemented yet.
     * - Visibility is not optimized using spatial partitioning or visibility clusters.
     * - Handling of player models and skins is incomplete.
     * - Updates for weapon models upon change are currently missing.
     *
     *  Former `CL_AddPacketEntities`
     */
    fun computeVisibleEntities(renderState: RenderState, gameConfig: GameConfiguration) {
        renderState.lerpAcc = 0f
        visibleEntities.clear()
        visibleBeams.clear()
        visibleEntities += ClientEntity("grid").apply { modelInstance = createGrid(16f, 8) }
        visibleEntities += ClientEntity("origin").apply { modelInstance = createOriginArrows(16f) }
        if (renderState.levelModel != null && renderState.drawLevel) {
            visibleEntities += renderState.levelModel!!
        }

        val mask = MAX_PARSE_ENTITIES - 1

        for (i in 0 until currentFrame.num_entities) {
            // Make the mask application explicit to avoid any precedence confusion
            val idx = (currentFrame.parse_entities + i) and mask
            val newState = cl_parse_entities[idx]
            val entity = clientEntities[newState.index]

            // not visible to client
            if (newState.modelindex == 0) {
                continue
            }
            // Beam entities are rendered via a dedicated path; modelindex is non-zero only to keep
            // network visibility behavior compatible with the original protocol.
            if ((newState.renderfx and Defines.RF_BEAM) != 0) {
                entity.modelInstance = null
                if (renderState.drawEntities) {
                    visibleBeams += entity
                }
                continue
            }

            // assign a visible 3d model
            if (entity.modelInstance == null) {
                val modelIndex = newState.modelindex
                if (modelIndex == 255) {
                    // player
                    val model = renderState.playerModel
                    entity.name = "player"
                    if (model != null) {
                        entity.modelInstance = createModelInstance(model)
                    }
                } else {
                    val modelConfig = gameConfig[CS_MODELS + modelIndex]
                    val model = modelConfig?.resource as? Model
                    entity.name = modelConfig?.value
                    if (model != null) {
                        entity.modelInstance = createModelInstance(model)
                    }
                }
                // todo: warning if the model was not found!
            }

            // render it if the model was successfully loaded
            if (entity.modelInstance != null
                && newState.index != renderState.playerNumber + 1
                && renderState.drawEntities
            ) {
                (entity.modelInstance.userData as? Md2CustomData)?.let { userData ->
                    userData.frame1 = entity.prev.frame
                    userData.frame2 = newState.frame
                    userData.skinIndex = newState.skinnum
                }
                visibleEntities += entity
            }
        }

        // fixme: update the model if the weapon was changed
        // update player gun model
        if (renderState.gun == null) {
            // try to load the model
            val model = gameConfig[CS_MODELS + currentFrame.playerstate.gunindex]?.resource as? Model
            if (model != null) {
                renderState.gun = ClientEntity("gun").apply {
                    modelInstance = createModelInstance(model)
                }
            }
        }

        // update the gun animation
        renderState.gun?.let {
            visibleEntities += it
            (it.modelInstance.userData as? Md2CustomData)?.let { userData ->
                userData.frame1 = previousFrame?.playerstate?.gunframe ?: currentFrame.playerstate.gunframe
                userData.frame2 = currentFrame.playerstate.gunframe
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
                Com.Printf("Delta from invalid frame (not supposed to happen!).\n")
            }
            if (deltaFrame.serverframe != currentFrame.deltaframe) {
                // The frame that the server did the delta from is too old, so we can't reconstruct it properly.
                Com.Printf("Delta frame too old.\n")
            } else if (parse_entities - deltaFrame.parse_entities > MAX_PARSE_ENTITIES - 128) {
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
}
