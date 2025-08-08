package org.demoth.cake.stages

import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelInstance
import jake2.qcommon.Defines
import jake2.qcommon.Defines.CS_MODELS
import jake2.qcommon.Defines.MAX_EDICTS
import jake2.qcommon.Defines.MAX_PARSE_ENTITIES
import jake2.qcommon.entity_state_t
import jake2.qcommon.network.messages.server.EntityUpdate
import jake2.qcommon.network.messages.server.PacketEntitiesMessage
import jake2.qcommon.network.messages.server.SpawnBaselineMessage
import jake2.qcommon.util.Math3D
import org.demoth.cake.ClientEntity
import org.demoth.cake.ClientFrame
import org.demoth.cake.GameConfiguration
import org.demoth.cake.modelviewer.Md2CustomData
import org.demoth.cake.modelviewer.createGrid
import org.demoth.cake.modelviewer.createOriginArrows
import kotlin.math.abs

// responsible for managing entity states which are updated from the server
class ClientEntityManager {
    val frames: Array<ClientFrame> = Array(Defines.UPDATE_BACKUP) { ClientFrame() }

    var parse_entities: Int = 0 // index (not anded off) into cl_parse_entities[]
    // entity states - updated during processing of [PacketEntitiesMessage]
    private val cl_parse_entities = Array(Defines.MAX_PARSE_ENTITIES) { entity_state_t(null) }
    private val clientEntities = Array(MAX_EDICTS) { ClientEntity("") }

    var previousFrame: ClientFrame? = ClientFrame() // the frame that we will delta from (for PlayerInfo & PacketEntities)
    val currentFrame = ClientFrame() // latest frame information received from the server


    // model instances to be drawn - updated on every server frame
    var visibleEntities = mutableListOf<ClientEntity>()

    fun processBaselineMessage(msg: SpawnBaselineMessage) {
        clientEntities[msg.entityState.number].baseline.set(msg.entityState)
    }

    /**
     * CL_ParsePacketEntities
     * todo: fix nullability issues, remove !! unsafe dereferences, check duplicate fragments
     */
    fun processPacketEntitiesMessage(msg: PacketEntitiesMessage): Boolean {
        currentFrame.parse_entities = parse_entities // save ring buffer head
        currentFrame.num_entities = 0

        // delta from the entities present in oldframe
        val oldFrame = previousFrame
        var entityIdOldFrame = 99999
        var oldstate: entity_state_t? = null

        if (oldFrame != null) {
            oldstate = cl_parse_entities[oldFrame.parse_entities and (Defines.MAX_PARSE_ENTITIES - 1)]
            entityIdOldFrame = oldstate.number
        } else {
            // uncompressed frame: no delta required
        }
        var oldindex = 0
        msg.updates.forEach { update ->
            // while we haven't reached entities in the new frame
            val entityIdNewFrame = update.header.number
            while (entityIdOldFrame < entityIdNewFrame) {
                // one or more entities from the old packet are unchanged,
                // copy them to the new frame
                DeltaEntity(currentFrame, entityIdOldFrame, oldstate!!, null)
                // fixme: same piece of code asdf123
                oldindex++
                if (oldindex >= previousFrame!!.num_entities) {
                    entityIdOldFrame = 99999
                } else {
                    oldstate = cl_parse_entities[(oldFrame!!.parse_entities + oldindex) and (Defines.MAX_PARSE_ENTITIES - 1)]
                    entityIdOldFrame = oldstate!!.number
                }
            }

            // oldnum is either 99999 or has reached newnum value
            if ((update.header.flags and Defines.U_REMOVE) != 0) {
                // the entity present in oldframe is not in the current frame
                // fixme: assert entityIdOldFrame == u.header.number
                // otherwise we are removing (=not including it in the new frame) an entity that wasn't there O_o

                // fixme: same piece of code asdf123
                oldindex++
                if (oldindex >= previousFrame!!.num_entities) {
                    entityIdOldFrame = 99999
                } else {
                    oldstate = cl_parse_entities[(oldFrame!!.parse_entities + oldindex) and (Defines.MAX_PARSE_ENTITIES - 1)]
                    entityIdOldFrame = oldstate!!.number
                }
                return@forEach //
            }

            if (entityIdOldFrame == entityIdNewFrame) {
                // delta from previous state

                DeltaEntity(currentFrame, entityIdNewFrame, oldstate!!, update)
                // fixme: same piece of code asdf123
                oldindex++
                if (oldindex >= previousFrame!!.num_entities) {
                    entityIdOldFrame = 99999
                } else {
                    oldstate = cl_parse_entities[(oldFrame!!.parse_entities + oldindex) and (Defines.MAX_PARSE_ENTITIES - 1)]
                    entityIdOldFrame = oldstate!!.number
                }

            } else if (entityIdOldFrame > entityIdNewFrame) {
                // delta from baseline
                DeltaEntity(currentFrame, entityIdNewFrame, clientEntities[entityIdNewFrame].baseline, update)
            }
        }

        /*
         any remaining entities in the old frame are copied over,
         one or more entities from the old packet are unchanged
        */
        while (entityIdOldFrame != 99999) {
            DeltaEntity(currentFrame, entityIdOldFrame, oldstate!!, null);
            // fixme: same piece of code asdf123
            oldindex++
            if (oldindex >= previousFrame!!.num_entities) {
                entityIdOldFrame = 99999
            } else {
                oldstate = cl_parse_entities[(oldFrame!!.parse_entities + oldindex) and (Defines.MAX_PARSE_ENTITIES - 1)]
                entityIdOldFrame = oldstate!!.number
            }

        }

        // save the frame off in the backup array for later delta comparisons
        frames[currentFrame.serverframe and Defines.UPDATE_MASK].set(currentFrame)

        // if valid: todo: FireEntityEvents, CL_pred.CheckPredictionError
        // getting a valid frame message ends the connection process
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
        newState.number = newnum
        newState.event = 0
        Math3D.VectorCopy(old.origin, newState.old_origin)

        if (update != null) {
            newState.setByFlags(update.newState, update.header.flags)
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

        if (entity.serverframe == frame.serverframe - 1) { // shuffle the last state to previous
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
        entity.current.set(newState) // fixme: use assignment instead of copying fields?
    }

    /**
     * Computes the list of entities that should be visible during the current server frame.
     *
     * Key functionalities:
     * - Resets `lerpAcc` for server frame interpolation.
     * - Adds grid and origin visualization models to the visible entities.
     * - Iterates over the entities in the current frame, instantiating their models if they
     *   haven't been loaded yet and updating them according to the game state.
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
        renderState.lerpAcc = 0f // reset lerp between server frames
        visibleEntities.clear()
        // todo: put to a persistent client entities list?
        visibleEntities += ClientEntity("grid").apply { modelInstance = createGrid(16f, 8) }
        visibleEntities += ClientEntity("origin").apply { modelInstance = createOriginArrows(16f) }
        if (renderState.levelModel != null && renderState.drawLevel) {
            // todo: use area visibility to draw only part of the map (visible clusters)
            visibleEntities += renderState.levelModel!!
        }

        // entities in the current frame
        // draw client entities, check jake2.client.CL_ents#AddPacketEntities
        (0..<currentFrame.num_entities).forEach { // todo: clientEntities.forEach {...
            val newState = cl_parse_entities[currentFrame.parse_entities + it and (MAX_PARSE_ENTITIES - 1)]
            val entity = clientEntities[newState.number]

            // instantiate model if not yet done
            if (entity.modelInstance == null) {
                val modelIndex = newState.modelindex
                if (modelIndex == 255) { // this is a player
                    // fixme: how to get which model/skin does the player have?
                    entity.modelInstance = ModelInstance(renderState.playerModel).apply {
                        userData = Md2CustomData(0, 0, 0f ,1)
                    }
                    entity.name = "player"
                } else if (modelIndex != 0) {
                    val modelConfig = gameConfig[CS_MODELS + modelIndex]
                    val model = modelConfig?.resource as? Model
                    if (model != null) {
                        entity.name = modelConfig.value
                        entity.modelInstance = ModelInstance(model).apply {
                            if (!modelConfig.value.contains("*")) // skip brush models
                                userData = Md2CustomData(0, 0, 0f ,1)
                        }
                    }
                }
            }

            // update the model instance
            if (entity.modelInstance != null
                && newState.number != renderState.playerNumber + 1 // do not draw ourselves
                && renderState.drawEntities
            ) {
                // update animation frame
                (entity.modelInstance.userData as? Md2CustomData)?.let { userData ->
                    userData.frame1 = entity.prev.frame
                    userData.frame2 = newState.frame
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
                    modelInstance = ModelInstance(model).apply {
                        userData = Md2CustomData(0, 0, 0f ,1)
                    }
                }
            }
        }
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

}