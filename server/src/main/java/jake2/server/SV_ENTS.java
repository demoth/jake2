/*
 * Copyright (C) 1997-2001 Id Software, Inc.
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.
 * 
 * See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place - Suite 330, Boston, MA 02111-1307, USA.
 *  
 */

// Created on 17.01.2004 by RST.

package jake2.server;

import jake2.qcommon.*;
import jake2.qcommon.network.NetworkCommandType;
import jake2.qcommon.network.commands.FrameMessage;
import jake2.qcommon.network.commands.PlayerInfoMessage;
import jake2.qcommon.util.Math3D;

public class SV_ENTS {

    final GameImportsImpl gameImports;
    /**
     * =============================================================================
     * 
     * Build a client frame structure
     * 
     * =============================================================================
     */

    public final byte[] fatpvs; // 32767 is MAX_MAP_LEAFS
    // stack variable
    private final byte[] buf_data;

    private final int num_client_entities; // maxclients->value*UPDATE_BACKUP*MAX_PACKET_ENTITIES
    private final entity_state_t[] client_entities; // [num_client_entities]
    private int next_client_entities; // next client_entity to use


    public SV_ENTS(GameImportsImpl gameImports, int clientEntitiesMax) {
        fatpvs = new byte[65536 / 8];
        buf_data = new byte[32768];
        this.gameImports = gameImports;

        num_client_entities = clientEntitiesMax;

        // Clear all client entity states
        client_entities = new entity_state_t[num_client_entities];
        for (int n = 0; n < client_entities.length; n++) {
            client_entities[n] = new entity_state_t(null);
        }

    }

    /*
     * =============================================================================
     * 
     * Encode a client frame onto the network channel
     * 
     * =============================================================================
     */

    /**
     * Writes a delta update of an entity_state_t list to the message.
     */
    void SV_EmitPacketEntities(client_frame_t lastReceivedFrame, client_frame_t currentFrame) {

        final sizebuf_t msg = gameImports.msg;
        MSG.WriteByte(msg, NetworkCommandType.svc_packetentities.type);

        int from_num_entities;
        if (lastReceivedFrame == null)
            from_num_entities = 0;
        else
            from_num_entities = lastReceivedFrame.num_entities;

        int newindex = 0;
        int oldindex = 0;
        entity_state_t oldState = null;
        entity_state_t newState = null;
        while (newindex < currentFrame.num_entities || oldindex < from_num_entities) {
            final int newnum;
            if (newindex >= currentFrame.num_entities) {
                newnum = 9999;
            } else {
                newState = client_entities[(currentFrame.first_entity + newindex) % num_client_entities];
                newnum = newState.number;
            }
            final int oldnum;
            if (oldindex >= from_num_entities)
                oldnum = 9999;
            else {
                oldState = client_entities[(lastReceivedFrame.first_entity + oldindex) % num_client_entities];
                oldnum = oldState.number;
            }

            if (newnum == oldnum) { 
            	// delta update from old position
                // because the force parm is false, this will not result
                // in any bytes being emited if the entity has not changed at
                // all note that players are always 'newentities', this updates
                // their oldorigin always
                // and prevents warping
                MSG.WriteDeltaEntity(oldState, newState, msg, false, newState.number <= gameImports.serverMain.getClients().size());
                oldindex++;
                newindex++;
            } else if (newnum < oldnum) {
            	// this is a new entity, send it from the baseline
                MSG.WriteDeltaEntity(gameImports.sv.baselines[newnum], newState, msg, true, true);
                newindex++;
            } else {
                // if (newnum > oldnum) {

            	// the old entity isn't present in the new message
                int bits = Defines.U_REMOVE;
                if (oldnum >= 256)
                    bits |= Defines.U_NUMBER16 | Defines.U_MOREBITS1;

                MSG.WriteByte(msg, bits & 255);
                if ((bits & 0x0000ff00) != 0)
                    MSG.WriteByte(msg, (bits >> 8) & 255);

                if ((bits & Defines.U_NUMBER16) != 0)
                    MSG.WriteShort(msg, oldnum);
                else
                    MSG.WriteByte(msg, oldnum);

                oldindex++;
            }
        }

        MSG.WriteShort(msg, 0); // end of packetentities

    }

    /**
     * SV_WritePlayerstateToClient
     * Writes the status of a player to a PlayerInfoMessage.
     */
    static PlayerInfoMessage buildPlayerInfoMessage(player_state_t lastFrameState, player_state_t currentPlayerState, GameImportsImpl gameImports) {
        player_state_t ops = lastFrameState != null ? lastFrameState : new player_state_t();

        // determine what needs to be sent
        int messageFlags = 0;

        if (currentPlayerState.pmove.pm_type != ops.pmove.pm_type)
            messageFlags |= Defines.PS_M_TYPE;

        if (currentPlayerState.pmove.origin[0] != ops.pmove.origin[0]
                || currentPlayerState.pmove.origin[1] != ops.pmove.origin[1]
                || currentPlayerState.pmove.origin[2] != ops.pmove.origin[2])
            messageFlags |= Defines.PS_M_ORIGIN;

        if (currentPlayerState.pmove.velocity[0] != ops.pmove.velocity[0]
                || currentPlayerState.pmove.velocity[1] != ops.pmove.velocity[1]
                || currentPlayerState.pmove.velocity[2] != ops.pmove.velocity[2])
            messageFlags |= Defines.PS_M_VELOCITY;

        if (currentPlayerState.pmove.pm_time != ops.pmove.pm_time)
            messageFlags |= Defines.PS_M_TIME;

        if (currentPlayerState.pmove.pm_flags != ops.pmove.pm_flags)
            messageFlags |= Defines.PS_M_FLAGS;

        if (currentPlayerState.pmove.gravity != ops.pmove.gravity)
            messageFlags |= Defines.PS_M_GRAVITY;

        if (currentPlayerState.pmove.delta_angles[0] != ops.pmove.delta_angles[0]
                || currentPlayerState.pmove.delta_angles[1] != ops.pmove.delta_angles[1]
                || currentPlayerState.pmove.delta_angles[2] != ops.pmove.delta_angles[2])
            messageFlags |= Defines.PS_M_DELTA_ANGLES;

        if (currentPlayerState.viewoffset[0] != ops.viewoffset[0]
                || currentPlayerState.viewoffset[1] != ops.viewoffset[1]
                || currentPlayerState.viewoffset[2] != ops.viewoffset[2])
            messageFlags |= Defines.PS_VIEWOFFSET;

        if (currentPlayerState.viewangles[0] != ops.viewangles[0]
                || currentPlayerState.viewangles[1] != ops.viewangles[1]
                || currentPlayerState.viewangles[2] != ops.viewangles[2])
            messageFlags |= Defines.PS_VIEWANGLES;

        if (currentPlayerState.kick_angles[0] != ops.kick_angles[0]
                || currentPlayerState.kick_angles[1] != ops.kick_angles[1]
                || currentPlayerState.kick_angles[2] != ops.kick_angles[2])
            messageFlags |= Defines.PS_KICKANGLES;

        if (currentPlayerState.blend[0] != ops.blend[0] || currentPlayerState.blend[1] != ops.blend[1]
                || currentPlayerState.blend[2] != ops.blend[2] || currentPlayerState.blend[3] != ops.blend[3])
            messageFlags |= Defines.PS_BLEND;

        if (currentPlayerState.fov != ops.fov)
            messageFlags |= Defines.PS_FOV;

        messageFlags |= Defines.PS_WEAPONINDEX;

        if (currentPlayerState.gunframe != ops.gunframe)
            messageFlags |= Defines.PS_WEAPONFRAME;

        if (currentPlayerState.rdflags != ops.rdflags)
            messageFlags |= Defines.PS_RDFLAGS;

        final sizebuf_t msg = gameImports.msg;
        // write it

        // send stats
        int statbits = 0;
        for (int i = 0; i < Defines.MAX_STATS; i++)
            if (currentPlayerState.stats[i] != ops.stats[i])
                statbits |= 1 << i;

        return new PlayerInfoMessage(
                messageFlags,
                currentPlayerState.pmove.pm_type,
                currentPlayerState.pmove.origin,
                currentPlayerState.pmove.velocity,
                currentPlayerState.pmove.pm_time,
                currentPlayerState.pmove.pm_flags,
                currentPlayerState.pmove.gravity,
                currentPlayerState.pmove.delta_angles,
                currentPlayerState.viewoffset,
                currentPlayerState.viewangles,
                currentPlayerState.kick_angles,
                currentPlayerState.gunindex,
                currentPlayerState.gunframe,
                currentPlayerState.gunoffset,
                currentPlayerState.gunangles,
                currentPlayerState.blend,
                currentPlayerState.fov,
                currentPlayerState.rdflags,
                statbits,
                currentPlayerState.stats
        );
    }

    /**
     * Writes a frame to a client system.
     */
    public void SV_WriteFrameToClient(client_t client) {
        // this is the frame we are creating
        client_frame_t currentFrame = client.frames[gameImports.sv.framenum & Defines.UPDATE_MASK];
        client_frame_t lastReceivedFrame;
        int lastReceivedFrameNum;
        // client is asking for a retransmit
        if (client.lastReceivedFrame <= 0) {
            lastReceivedFrame = null;
            lastReceivedFrameNum = -1;
        } else if (gameImports.sv.framenum - client.lastReceivedFrame >= (Defines.UPDATE_BACKUP - 3)) {
            // client hasn't gotten a good message through in a long time
            // Com_Printf ("%s: Delta request from out-of-date packet.\n",
            // client.name);
            lastReceivedFrame = null;
            lastReceivedFrameNum = -1;
        } else {
            // we have a valid message to delta from
            lastReceivedFrame = client.frames[client.lastReceivedFrame & Defines.UPDATE_MASK];
            lastReceivedFrameNum = client.lastReceivedFrame;
        }

        new FrameMessage(
                gameImports.sv.framenum,
                lastReceivedFrameNum,
                client.surpressCount,
                currentFrame.areabytes,
                currentFrame.areabits
        ).writeTo(gameImports.msg);

        client.surpressCount = 0;

        // delta encode the playerstate
        PlayerInfoMessage playerInfoMsg = buildPlayerInfoMessage(lastReceivedFrame != null ? lastReceivedFrame.ps : null, currentFrame.ps, gameImports);
        playerInfoMsg.writeTo(gameImports.msg);

        // delta encode the entities
        SV_EmitPacketEntities(lastReceivedFrame, currentFrame);
    }

    /** 
     * The client will interpolate the view position, so we can't use a single
     * PVS point. 
     */
    public void SV_FatPVS(float[] org) {
        int leafs[] = new int[64];
        int i, j, count;
        int longs;
        byte src[];
        float[] mins = { 0, 0, 0 }, maxs = { 0, 0, 0 };

        for (i = 0; i < 3; i++) {
            mins[i] = org[i] - 8;
            maxs[i] = org[i] + 8;
        }
        final CM cm = gameImports.cm;
        count = cm.CM_BoxLeafnums(mins, maxs, leafs, 64, null);

        if (count < 1)
            Com.Error(Defines.ERR_FATAL, "SV_FatPVS: count < 1");

        longs = (cm.CM_NumClusters() + 31) >> 5;

        // convert leafs to clusters
        for (i = 0; i < count; i++)
            leafs[i] = cm.CM_LeafCluster(leafs[i]);

        System.arraycopy(cm.CM_ClusterPVS(leafs[0]), 0, fatpvs, 0,
                longs << 2);
        // or in all the other leaf bits
        for (i = 1; i < count; i++) {
            for (j = 0; j < i; j++)
                if (leafs[i] == leafs[j])
                    break;
            if (j != i)
                continue; // already have the cluster we want

            src = cm.CM_ClusterPVS(leafs[i]);

            //for (j=0 ; j<longs ; j++)
            //	((long *)fatpvs)[j] |= ((long *)src)[j];
            int k = 0;
            for (j = 0; j < longs; j++) {
                fatpvs[k] |= src[k++];
                fatpvs[k] |= src[k++];
                fatpvs[k] |= src[k++];
                fatpvs[k] |= src[k++];
            }
        }
    }

    /**
     * Decides which entities are going to be visible to the client, and copies
     * off the playerstat and areabits.
     */
    public void SV_BuildClientFrame(client_t client) {

        edict_t clent = client.edict;
        if (clent.getClient() == null)
            return; // not in game yet

        // this is the frame we are creating
        client_frame_t frame = client.frames[gameImports.sv.framenum & Defines.UPDATE_MASK];

        frame.senttime = gameImports.realtime; // save it for ping calc later

        // find the client's PVS
        int i;
        float[] org = {0, 0, 0};
        for (i = 0; i < 3; i++)
            org[i] = clent.getClient().getPlayerState().pmove.origin[i] * 0.125f
                    + clent.getClient().getPlayerState().viewoffset[i];

        int leafnum = gameImports.cm.CM_PointLeafnum(org);
        int clientarea = gameImports.cm.CM_LeafArea(leafnum);
        int clientcluster = gameImports.cm.CM_LeafCluster(leafnum);

        // calculate the visible areas
        frame.areabytes = gameImports.cm.CM_WriteAreaBits(frame.areabits, clientarea);

        // grab the current player_state_t
        frame.ps.set(clent.getClient().getPlayerState());

        SV_FatPVS(org);
        byte[] clientphs = gameImports.cm.CM_ClusterPHS(clientcluster);

        // build up the list of visible entities
        frame.num_entities = 0;
        frame.first_entity = next_client_entities;

        for (int e = 1; e < gameImports.gameExports.getNumEdicts(); e++) {
            edict_t ent = gameImports.gameExports.getEdict(e);

            // ignore ents without visible models
            if ((ent.svflags & Defines.SVF_NOCLIENT) != 0)
                continue;

            // ignore ents without visible models unless they have an effect
            if (0 == ent.s.modelindex && 0 == ent.s.effects && 0 == ent.s.sound
                    && 0 == ent.s.event)
                continue;

            // ignore if not touching a PV leaf
            // check area
            if (ent != clent) {
                if (!gameImports.cm.CM_AreasConnected(clientarea, ent.areanum)) {
                	// doors can legally straddle two areas, so we may need to check another one
                    if (0 == ent.areanum2 || !gameImports.cm.CM_AreasConnected(clientarea, ent.areanum2))
                        continue; // blocked by a door
                }

                // beams just check one point for PHS
                int l;
                if ((ent.s.renderfx & Defines.RF_BEAM) != 0) {
                    l = ent.clusternums[0];
                    if (0 == (clientphs[l >> 3] & (1 << (l & 7))))
                        continue;
                } else {
                    // FIXME: if an ent has a model and a sound, but isn't
                    // in the PVS, only the PHS, clear the model
                    byte[] bitvector;
                    if (ent.s.sound == 0) {
                        bitvector = fatpvs; //clientphs;
                    } else
                        bitvector = fatpvs;

                    if (ent.num_clusters == -1) { // too many leafs for
                                                  // individual check, go by
                                                  // headnode
                        if (!gameImports.cm.CM_HeadnodeVisible(ent.headnode, bitvector))
                            continue;
                    } else { // check individual leafs
                        for (i = 0; i < ent.num_clusters; i++) {
                            l = ent.clusternums[i];
                            if ((bitvector[l >> 3] & (1 << (l & 7))) != 0)
                                break;
                        }
                        if (i == ent.num_clusters)
                            continue; // not visible
                    }

                    if (ent.s.modelindex == 0) { // don't send sounds if they
                                                 // will be attenuated away
                        float[] delta = { 0, 0, 0 };
                        float len;

                        Math3D.VectorSubtract(org, ent.s.origin, delta);
                        len = Math3D.VectorLength(delta);
                        if (len > 400)
                            continue;
                    }
                }
            }

            // add it to the circular client_entities array
            int ix = next_client_entities % num_client_entities;
            entity_state_t state = client_entities[ix];
            if (ent.s.number != e) {
                Com.DPrintf("FIXING ENT.S.NUMBER!!!\n");
                ent.s.number = e;
            }

            //*state = ent.s;
            client_entities[ix].set(ent.s);

            // don't mark players missiles as solid
            if (ent.getOwner() == client.edict)
                state.solid = 0;

            next_client_entities++;
            frame.num_entities++;
        }
    }
}
