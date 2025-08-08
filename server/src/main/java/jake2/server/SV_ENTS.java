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
import jake2.qcommon.network.messages.server.*;
import jake2.qcommon.util.Math3D;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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

    private final int maxClientEntities; // maxclients->value*UPDATE_BACKUP*MAX_PACKET_ENTITIES
    private final entity_state_t[] client_entities; // [num_client_entities]
    private int next_client_entities; // next client_entity to use


    public SV_ENTS(GameImportsImpl gameImports, int clientEntitiesMax) {
        fatpvs = new byte[65536 / 8];
        this.gameImports = gameImports;

        maxClientEntities = clientEntitiesMax;

        // Clear all client entity states
        client_entities = new entity_state_t[maxClientEntities];
        for (int n = 0; n < client_entities.length; n++) {
            client_entities[n] = new entity_state_t(null);
        }

    }

    /**
     * SV_WritePlayerstateToClient
     * Writes the status of a player to a PlayerInfoMessage.
     */
    // todo: move this logic into players state message write Properties function.
    // todo: PlayerInfoMessage should hold only player state
    static PlayerInfoMessage buildPlayerInfoMessage(player_state_t lastFrameState, player_state_t currentPlayerState) {
        player_state_t ops = lastFrameState != null ? lastFrameState : new player_state_t();


        return new PlayerInfoMessage(ops, currentPlayerState);

//        return new PlayerInfoMessage(
//                messageFlags,
//                currentState.pmove.pm_type,
//                currentState.pmove.origin,
//                currentState.pmove.velocity,
//                currentState.pmove.pm_time,
//                currentState.pmove.pm_flags,
//                currentState.pmove.gravity,
//                currentState.pmove.delta_angles,
//                currentState.viewoffset,
//                currentState.viewangles,
//                currentState.kick_angles,
//                currentState.gunindex,
//                currentState.gunframe,
//                currentState.gunoffset,
//                currentState.gunangles,
//                currentState.blend,
//                currentState.fov,
//                currentState.rdflags,
//                statbits,
//                currentState.stats
//        );
    }

    /**
     * Writes a frame to a client system.
     */
    public Collection<ServerMessage> SV_WriteFrameToClient(client_t client) {
        List<ServerMessage> result = new ArrayList<>();
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

        result.add(new FrameHeaderMessage(
                gameImports.sv.framenum,
                lastReceivedFrameNum,
                client.surpressCount,
                currentFrame.areabytes,
                currentFrame.areabits
        ));

        client.surpressCount = 0;

        // delta encode the playerstate
        PlayerInfoMessage playerInfoMsg = buildPlayerInfoMessage(lastReceivedFrame != null ? lastReceivedFrame.ps : null, currentFrame.ps);
        result.add(playerInfoMsg);

        // delta encode the entities
        PacketEntitiesMessage packetEntities = buildPacketEntities(
                lastReceivedFrame,
                currentFrame,
                client_entities,
                gameImports.sv.baselines,
                maxClientEntities,
                gameImports.serverMain.getClients().size());
        result.add(packetEntities);
        return result;
    }

    // SV_EmitPacketEntities
    static PacketEntitiesMessage buildPacketEntities(client_frame_t lastReceivedFrame,
                                                      client_frame_t currentFrame,
                                                      entity_state_t[] client_entities,
                                                      entity_state_t[] baselines,
                                                      int maxEntities,
                                                      int maxClients) {
        // assert client_entities.length == maxEntities;
        // assert baselines.length == maxEntities;

        PacketEntitiesMessage result = new PacketEntitiesMessage();

        // Write delta entity
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
                // does not exist in the frame
                newnum = 9999;
            } else {
                newState = client_entities[(currentFrame.first_entity + newindex) % maxEntities];
                newnum = newState.number;
            }
            final int oldnum;
            if (oldindex >= from_num_entities)
                // does not exist in the frame
                oldnum = 9999;
            else {
                oldState = client_entities[(lastReceivedFrame.first_entity + oldindex) % maxEntities];
                oldnum = oldState.number;
            }

            if (newnum == oldnum) {
                // delta update from old position.
                // because the force parm is false, this will not result
                // in any bytes being emited if the entity has not changed at all.
                // Note: players are always 'newentities', this updates
                // their oldorigin always  and prevents warping
                final boolean isPlayer = newState.number <= maxClients;
                result.updates.add(new EntityUpdate(oldState, newState, false, isPlayer));
                oldindex++;
                newindex++;
            } else if (newnum < oldnum) {
                // this is a new entity, send it from the baseline
                result.updates.add(new EntityUpdate(baselines[newnum], newState, true, true));
                newindex++;
            } else {
                // if (newnum > oldnum) {
                // the old entity isn't present in the new message

                int flags = Defines.U_REMOVE;
                if (oldnum >= 256)
                    flags |= Defines.U_NUMBER16 | Defines.U_MOREBITS1;

                result.updates.add(new EntityUpdate(new DeltaEntityHeader(flags, oldnum)));
                oldindex++;
            }
        }
        return result;
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
            int ix = next_client_entities % maxClientEntities;
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
