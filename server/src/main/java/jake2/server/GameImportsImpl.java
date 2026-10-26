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

// Created on 31.10.2003 by RST.
// $Id: game_import_t.java,v 1.7 2006-01-21 21:53:31 salomo Exp $
package jake2.server;

import jake2.qcommon.*;
import jake2.qcommon.exec.Cbuf;
import jake2.qcommon.exec.Cvar;
import jake2.qcommon.exec.CvarType;
import jake2.qcommon.exec.cvar_t;
import jake2.qcommon.vfs.EngineWriteRoot;
import jake2.qcommon.network.MulticastTypes;
import jake2.qcommon.network.NET;
import jake2.qcommon.network.messages.NetworkMessage;
import jake2.qcommon.network.messages.server.ConfigStringMessage;
import jake2.qcommon.network.messages.server.PrintCenterMessage;
import jake2.qcommon.network.messages.server.FrameHeaderMessage;
import jake2.qcommon.network.messages.server.PlayerInfoMessage;
import jake2.qcommon.network.messages.server.PacketEntitiesMessage;
import jake2.qcommon.network.messages.server.EntityUpdate;
import jake2.qcommon.network.messages.server.DeltaEntityHeader;
import jake2.qcommon.network.messages.server.ServerMessage;
import jake2.qcommon.network.messages.server.StuffTextMessage;
import jake2.qcommon.network.messages.server.SoundMessage;
import jake2.qcommon.util.Lib;
import jake2.qcommon.util.Math3D;
import jake2.server.save.ServerLevelJsonStore;
import jake2.server.save.ServerSaveJsonStore;
import jake2.server.save.ServerSaveFiles;

import java.io.File;
import java.lang.reflect.Constructor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Collection;
import java.util.List;

import static jake2.qcommon.Defines.CS_MAPCHECKSUM;
import static jake2.qcommon.Defines.CS_MODELS;

/** Owns one server-side game instance and exposes engine services to its gameplay module. */
@SuppressWarnings("deprecation")
public class GameImportsImpl implements GameImports {

    String name;
    final SV_MAIN serverMain;
    public GameExports gameExports;

    // Per-game map state. The routing name above is independent of the loaded map name.
    final String mapName;
    ServerStates state;
    final boolean isDemo;
    final boolean loadgame;
    int frameNum;
    final cmodel_t[] models = new cmodel_t[Defines.MAX_MODELS];
    final String[] configstrings = new String[Defines.MAX_CONFIGSTRINGS];
    final entity_state_t[] baselines = new entity_state_t[Defines.MAX_EDICTS];

    public int realtime; // always increasing, no clamping, etc
    // todo: remove
    public int spawncount; // incremented each server start

    // hack for finishing game in coop mode
    public String firstmap = "";

    String mapcmd = ""; // ie: *intro.cin+base

    // Spatial indexing and query scratch state belong to this Game.
    private static final int MAX_TOTAL_ENT_LEAFS = 128;
    private areanode_t[] areaNodes;
    private int areaNodeCount;
    private float[] areaMins;
    private float[] areaMaxs;
    private ServerEntity[] areaEntities;
    private int areaCount;
    private int areaMaxCount;
    private int areaType;
    private final ServerEntity[] pointContentsTouches = new ServerEntity[Defines.MAX_EDICTS];
    private final ServerEntity[] traceTouches = new ServerEntity[Defines.MAX_EDICTS];
    private final int[] entityLeafs = new int[MAX_TOTAL_ENT_LEAFS];
    private final int[] entityClusters = new int[MAX_TOTAL_ENT_LEAFS];

    private final Cvar localCvars = new Cvar();

    CM cm;

    // Entity snapshots are shared by this Game's clients; frame acknowledgements stay on each client.
    private final byte[] fatPvs = new byte[65536 / 8];
    private final entity_state_t[] clientEntityStates;
    private int nextClientEntity;

    private final float configuredAirAcceleration;
    final float airAcceleration;
    private final PMove.PmoveProcessor pmoveProcessor = PMove.newLegacyProcessor();

    public GameImportsImpl(SV_MAIN serverMain, ChangeMapInfo changeMapInfo) {
        this(serverMain, changeMapInfo, null);
    }

    GameImportsImpl(SV_MAIN serverMain, ChangeMapInfo changeMapInfo, GameImportsImpl previous) {
        this.serverMain = serverMain;
        this.name = serverMain.allocateGameName();
        if (previous != null && !changeMapInfo.isLoadgame)
            previous.localCvars.copyValuesTo(localCvars);

        spawncount = Lib.rand();

        cm = new CM();
        clientEntityStates = new entity_state_t[serverMain.getClients().size() * Defines.UPDATE_BACKUP * 64];
        for (int i = 0; i < clientEntityStates.length; i++) clientEntityStates[i] = new entity_state_t();

        // heartbeats will always be sent to the id master

        mapName = changeMapInfo.mapName;
        state = ServerStates.SS_LOADING;
        isDemo = changeMapInfo.isDemo;
        loadgame = changeMapInfo.isLoadgame;
        for (int i = 0; i < models.length; i++) models[i] = new cmodel_t();
        for (int i = 0; i < baselines.length; i++) baselines[i] = new entity_state_t();
        // Fallback for maps that do not supply a display name.
        configstrings[Defines.CS_NAME] = mapName;

        int[] iw = { 0 };
        if (changeMapInfo.state == ServerStates.SS_GAME) {
            configstrings[CS_MODELS + 1] = "maps/" + changeMapInfo.mapName + ".bsp";
            models[1] = cm.CM_LoadMap(configstrings[CS_MODELS + 1], false, iw);
        } else {
            models[1] = cm.CM_LoadMap("", false, iw); // no real map
        }
        configstrings[CS_MAPCHECKSUM] = "" + iw[0];

        for (int i = 1; i < cm.CM_NumInlineModels(); i++) {
            configstrings[CS_MODELS + 1 + i] = "*" + i;

            // copy references
            models[i + 1] = cm.InlineModel(configstrings[CS_MODELS + 1 + i]);
        }



        // archive server state to be used in savegame
        mapcmd = changeMapInfo.levelString;


        if (changeMapInfo.isLoadgame) {
            SV_Read_Latched_Vars();
        }

        // Snapshot the applied setting for this game; client prediction receives the same value.
        configuredAirAcceleration = Cvar.getInstance().find("sv_airaccelerate", Float.class).getTypedValue();
        airAcceleration = Cvar.getInstance().find("deathmatch", Boolean.class).getTypedValue()
                ? configuredAirAcceleration : 0f;
        configstrings[Defines.CS_AIRACCEL] = Float.toString(airAcceleration);

        // clear physics interaction links
        // Q: is it required? the instance was just created
        clearWorld();


    }

    /** Allocate the gameplay module after engine services and latched settings are ready. */
    static GameImportsImpl create(SV_MAIN host, ChangeMapInfo changeMapInfo, GameImportsImpl previous) {
        GameImportsImpl game = new GameImportsImpl(host, changeMapInfo, previous);
        game.gameExports = game.createGameModInstance();
        if (changeMapInfo.isLoadgame)
            game.gameExports.readGameLocals(EngineWriteRoot.resolve("save/current/game.ssv.json").toString());
        return game;
    }

    /**
     * Find and create an instance of the game subsystem
     */
    private GameExports createGameModInstance() {
        // todo: introduce proper Dependency Injection
        try {
            Class<?> game = Class.forName("jake2.game.GameExportsImpl");
            Constructor<?> constructor = game.getConstructor(GameImports.class);
            return (GameExports) constructor.newInstance(this);
        } catch (Exception e) {
            e.printStackTrace();
            Com.Error(Defines.ERR_FATAL, "Could not initialise game subsystem due to : " + e.getMessage() + "\n");
            return null;
        }
    }

    /** Capture persistent player data before allocating the replacement Game. */
    void prepareLevelTransition() {
        gameExports.SaveClientData();
    }

    /** Populate and settle this level while the Host keeps clients in the loading handshake. */
    void initializeLevel(ChangeMapInfo changeMapInfo, GameImportsImpl previous) {
        if (previous != null && !changeMapInfo.isLoadgame) {
            // copy persistent data between instances.
            // after a 'map' command the persistent state will be empty
            gameExports.fromPrevious(previous.gameExports, changeMapInfo.spawnPoint);
        }


        // spawn fresh entities
        gameExports.SpawnEntities(mapName, cm.CM_EntityString(), changeMapInfo.spawnPoint);

        if (isPreviousLevel()) {
            // fixme: need to spawn all entities and then remove them,
            //  because otherwise precaching will not work properly - configstrings will not match.
            clearWorld();

            // loads a stored game, runs 100 frames afterwards
            restorePreviousGame();
        } else {
            // run two frames to allow everything to settle
            gameExports.G_RunFrame();
            gameExports.G_RunFrame();

        }
        // create a baseline for more efficient communications
        createBaselines();


        // Level restoration may replace configstrings, including saves from before air movement was published.
        configstrings[Defines.CS_AIRACCEL] = Float.toString(airAcceleration);

        // all precaches are complete
        state = changeMapInfo.state;
    }

    /** Entity events last for one transmission; the Host calls this after sending snapshots. */
    void clearEntityStateEvents() {
        for (int i = 0; i < gameExports.getNumEdicts(); i++)
            gameExports.getEdict(i).s.event = 0;
    }

    /**
     * 	SV_ReadServerFile
     *
     * 	todo - move read game locals to another function
     */
    void SV_Read_Latched_Vars() {
        try {

            Com.DPrintf("SV_ReadServerFile()\n");
            ServerSaveJsonStore store = ServerSaveJsonStore.forWriteDir(EngineWriteRoot.pathString());
            ServerSaveJsonStore.ServerLatchedCvarsSnapshot snapshot = store.readLatchedCvars("current");

            // read all CVAR_LATCH cvars
            // these will be things like coop, skill, deathmatch, etc
            for (ServerSaveJsonStore.LatchedCvarSnapshot cvar : snapshot.cvars()) {
                String name = cvar.name();
                String value = cvar.value();
                Com.DPrintf("Set " + name + " = " + value + "\n");
                var result = writeRegistry(name).forceSetResult(name, value);
                if (result.getDiagnostic() != null) Com.Printf(result.getDiagnostic() + "\n");
            }
        } catch (Exception e) {
            Com.Printf("Couldn't read file " + e.getMessage() + "\n");
        }
    }

    // special messages
    @Override
    public void bprintf(int printlevel, String s) {
        serverMain.SV_BroadcastPrintf(printlevel, s, name);
    }

    @Override
    public void dprintf(String s) {
        Com.Printf(s);
    }

    @Override
    public void cprintf(ServerEntity ent, int printlevel, String s) {

        int n = 0;

        if (ent != null) {
            n = ent.index;
            if (n < 1 || n > serverMain.getClients().size())
                Com.Error(Defines.ERR_DROP, "cprintf to a non-client");
        }

        if (ent != null)
            serverMain.getClients().get(n - 1).queuePrint(printlevel, s);
        else
            Com.Printf(s);
    }

    @Override
    public void centerprintf(ServerEntity ent, String s) {
        // todo: check if s is not empty
        unicastMessage(ent.index, new PrintCenterMessage(s), true);
    }

    @Override
    public void sound(ServerEntity ent, int channel, int soundindex, float volume,
                      float attenuation, float timeofs) {
        if (ent == null) return;
        positioned_sound(null, ent, channel, soundindex, volume, attenuation, timeofs);
    }

    @Override
    public void positioned_sound(float[] origin, ServerEntity ent, int channel,
                                 int soundindex, float volume, float attenuation, float timeofs) {

        if (volume < 0 || volume > 1.0)
            Com.Error(Defines.ERR_FATAL, "SV_StartSound: volume = " + volume);

        if (attenuation < 0 || attenuation > 4)
            Com.Error(Defines.ERR_FATAL, "SV_StartSound: attenuation = " + attenuation);

        //    if (channel < 0 || channel > 15)
        //        Com_Error (ERR_FATAL, "SV_StartSound: channel = %i", channel);

        if (timeofs < 0 || timeofs > 0.255)
            Com.Error(Defines.ERR_FATAL, "SV_StartSound: timeofs = " + timeofs);

        int entityIndex = ent.index;

        // no PHS flag
        boolean use_phs;
        if ((channel & 8) != 0) {
            use_phs = false;
            channel &= 7;
        }
        else
            use_phs = true;

        int sendchan = (entityIndex << 3) | (channel & 7);

        int flags = 0;
        if (volume != Defines.DEFAULT_SOUND_PACKET_VOLUME)
            flags |= Defines.SND_VOLUME;
        if (attenuation != Defines.DEFAULT_SOUND_PACKET_ATTENUATION)
            flags |= Defines.SND_ATTENUATION;

        // the client doesn't know that bmodels have weird origins
        // the origin can also be explicitly set
        if ((ent.svflags & Defines.SVF_NOCLIENT) != 0 || (ent.solid == Defines.SOLID_BSP) || origin != null)
            flags |= Defines.SND_POS;

        // always send the ent number for channel overrides
        flags |= Defines.SND_ENT;

        if (timeofs != 0)
            flags |= Defines.SND_OFFSET;

        // use the ent origin unless it is a bmodel or explicitly specified
        if (origin == null) {
            origin = new float[3];
            if (ent.solid == Defines.SOLID_BSP) {
                for (int i = 0; i < 3; i++)
                    origin[i] = ent.s.origin[i] + 0.5f * (ent.mins[i] + ent.maxs[i]);
            }
            else {
                Math3D.VectorCopy(ent.s.origin, origin);
            }
        }

        // Queued messages own their position until the transport serializes them.
        else {
            origin = origin.clone();
        }

        // if the sound doesn't attenuate,send it to everyone
        // (global radio chatter, voiceovers, etc)
        if (attenuation == Defines.ATTN_NONE)
            use_phs = false;

        final MulticastTypes multicastType;

        if ((channel & Defines.CHAN_RELIABLE) != 0) {
            if (use_phs)
                multicastType = MulticastTypes.MULTICAST_PHS_R;
            else
                multicastType = MulticastTypes.MULTICAST_ALL_R;
        } else {
            if (use_phs)
                multicastType = MulticastTypes.MULTICAST_PHS;
            else
                multicastType = MulticastTypes.MULTICAST_ALL;
        }
        multicastMessage(origin, new SoundMessage(flags, soundindex, volume, attenuation, timeofs, sendchan, origin), multicastType);
    }

    /*
     config strings hold all the index strings, the lightstyles,
     and misc data like the sky definition and cdtrack.
     All of the current configstrings are sent to clients when
     they connect, and changes are sent to all connected clients.
    */
    @Override
    public void configstring(int num, String string) {
        if (num < 0 || num >= Defines.MAX_CONFIGSTRINGS)
            Com.Error(Defines.ERR_DROP, "configstring: bad index " + num
                    + "\n");

        if (string == null)
            string = "";

        // Update this Game's resource table.
        configstrings[num] = string;

        if (state != ServerStates.SS_LOADING) {
            // send the update to everyone
            multicastMessage(null, new ConfigStringMessage(num, string), MulticastTypes.MULTICAST_ALL_R);
        }
    }

    @Override
    public void error(String err) {
        Com.Error(Defines.ERR_FATAL, err);
    }

    @Override
    public void error(int level, String err) {
        Com.Error(level, err);
    }

    /* the *index functions create configstrings and some internal server state */
    @Override
    public int modelindex(String name) {
        return findConfigstringIndex(name, Defines.CS_MODELS, Defines.MAX_MODELS);
    }

    @Override
    public int soundindex(String name) {
        return findConfigstringIndex(name, Defines.CS_SOUNDS, Defines.MAX_SOUNDS);
    }

    @Override
    public int imageindex(String name) {
        return findConfigstringIndex(name, Defines.CS_IMAGES, Defines.MAX_IMAGES);
    }

    @Override
    public void setmodel(ServerEntity ent, String name) {

        if (name == null) {
            Com.DPrintf( "Error: GameImportsImpl.setmodel: name is null");
            return;
        }

        ent.s.modelindex = findConfigstringIndex(name, Defines.CS_MODELS, Defines.MAX_MODELS);

        // if it is an inline model, get the size information for it
        if (name.startsWith("*")) {
            cmodel_t mod = cm.InlineModel(name);
            Math3D.VectorCopy(mod.mins, ent.mins);
            Math3D.VectorCopy(mod.maxs, ent.maxs);
            linkentity(ent);
        }
    }

    /* collision detection */
    @Override
    public trace_t trace(float[] start, float[] mins, float[] maxs,
                         float[] end, ServerEntity passent, int contentmask) {
        moveclip_t clip = new moveclip_t();
        if (mins == null)
            mins = Globals.vec3_origin;
        if (maxs == null)
            maxs = Globals.vec3_origin;

        // clip to world
        clip.trace = cm.BoxTrace(start, end, mins, maxs, 0, contentmask);
        clip.trace.ent = gameExports.getEdict(0);
        if (clip.trace.fraction == 0)
            return clip.trace; // blocked by the world
        clip.contentmask = contentmask;
        clip.start = start;
        clip.end = end;
        clip.mins = mins;
        clip.maxs = maxs;
        clip.passedict = passent;
        Math3D.VectorCopy(mins, clip.mins2);
        Math3D.VectorCopy(maxs, clip.maxs2);
        // create the bounding box of the entire move
        traceBounds(start, clip.mins2, clip.maxs2, end, clip.boxmins,
                clip.boxmaxs);
        // clip to other solid entities
        clipMoveToEntities(clip);
        return clip.trace;
    }

    @Override
    public boolean inPHS(float[] p1, float[] p2) {
        int leafnum;
        int cluster;
        int area1, area2;
        byte mask[];

        leafnum = cm.CM_PointLeafnum(p1);
        cluster = cm.CM_LeafCluster(leafnum);
        area1 = cm.CM_LeafArea(leafnum);
        mask = cm.CM_ClusterPHS(cluster);

        leafnum = cm.CM_PointLeafnum(p2);
        cluster = cm.CM_LeafCluster(leafnum);
        area2 = cm.CM_LeafArea(leafnum);

        // quake2 bugfix
        if (cluster == -1)
            return false;
        if (mask != null && (0 == (mask[cluster >> 3] & (1 << (cluster & 7)))))
            return false; // more than one bounce away
        if (!cm.CM_AreasConnected(area1, area2))
            return false; // a door blocks hearing

        return true;
    }

    @Override
    public void SetAreaPortalState(int portalnum, boolean open) {
        cm.CM_SetAreaPortalState(portalnum, open);
    }

    @Override
    public boolean AreasConnected(int area1, int area2) {
        return cm.CM_AreasConnected(area1, area2);
    }

    /*
     an entity will never be sent to a client or used for collision
     if it is not passed to linkentity. If the size, position, or
     solidity changes, it must be relinked.
    */
    @Override
    public void linkentity(ServerEntity ent) {
        areanode_t node;
        int num_leafs;
        int j, k;
        int area;
        int topnode = 0;
        if (ent.area.prev != null)
            unlinkentity(ent); // unlink from old position
        if (ent == gameExports.getEdict(0))
            return; // don't add the world
        if (!ent.inuse)
            return;
        // set the size
        Math3D.VectorSubtract(ent.maxs, ent.mins, ent.size);
        // encode the size into the entity_state for client prediction
        if (ent.solid == Defines.SOLID_BBOX
                && 0 == (ent.svflags & Defines.SVF_DEADMONSTER)) {
            // assume that x/y are equal and symetric
            int i = (int) (ent.maxs[0] / 8);
            if (i < 1)
                i = 1;
            if (i > 31)
                i = 31;
            // z is not symetric
            j = (int) ((-ent.mins[2]) / 8);
            if (j < 1)
                j = 1;
            if (j > 31)
                j = 31;
            // and z maxs can be negative...
            k = (int) ((ent.maxs[2] + 32) / 8);
            if (k < 1)
                k = 1;
            if (k > 63)
                k = 63;
            ent.s.solid = (k << 10) | (j << 5) | i;
        } else if (ent.solid == Defines.SOLID_BSP) {
            ent.s.solid = 31; // a solid_bbox will never create this value
        } else
            ent.s.solid = 0;
        // set the abs box
        if (ent.solid == Defines.SOLID_BSP
                && (ent.s.angles[0] != 0 || ent.s.angles[1] != 0 || ent.s.angles[2] != 0)) {
            // expand for rotation
            float max, v;
            max = 0;
            for (int i = 0; i < 3; i++) {
                v = Math.abs(ent.mins[i]);
                if (v > max)
                    max = v;
                v = Math.abs(ent.maxs[i]);
                if (v > max)
                    max = v;
            }
            for (int i = 0; i < 3; i++) {
                ent.absmin[i] = ent.s.origin[i] - max;
                ent.absmax[i] = ent.s.origin[i] + max;
            }
        } else {
            // normal
            Math3D.VectorAdd(ent.s.origin, ent.mins, ent.absmin);
            Math3D.VectorAdd(ent.s.origin, ent.maxs, ent.absmax);
        }
        // because movement is clipped an epsilon away from an actual edge,
        // we must fully check even when bounding boxes don't quite touch
        ent.absmin[0]--;
        ent.absmin[1]--;
        ent.absmin[2]--;
        ent.absmax[0]++;
        ent.absmax[1]++;
        ent.absmax[2]++;
        // link to PVS leafs
        ent.num_clusters = 0;
        ent.areanum = 0;
        ent.areanum2 = 0;
        // get all leafs, including solids
        int iw[] = { topnode };
        num_leafs = cm.CM_BoxLeafnums(ent.absmin, ent.absmax, this.entityLeafs, MAX_TOTAL_ENT_LEAFS, iw);
        topnode = iw[0];
        // set areas
        for (int i = 0; i < num_leafs; i++) {
            this.entityClusters[i] = cm.CM_LeafCluster(this.entityLeafs[i]);
            area = cm.CM_LeafArea(this.entityLeafs[i]);
            if (area != 0) {
                // doors may legally straggle two areas,
                // but nothing should ever need more than that
                if (ent.areanum != 0 && ent.areanum != area) {
                    if (ent.areanum2 != 0 && ent.areanum2 != area
                            && state == ServerStates.SS_LOADING)
                        Com.DPrintf("Object touching 3 areas at "
                                + ent.absmin[0] + " " + ent.absmin[1] + " "
                                + ent.absmin[2] + "\n");
                    ent.areanum2 = area;
                } else
                    ent.areanum = area;
            }
        }
        if (num_leafs >= MAX_TOTAL_ENT_LEAFS) {
            // assume we missed some leafs, and mark by headnode
            ent.num_clusters = -1;
            ent.headnode = topnode;
        } else {
            ent.num_clusters = 0;
            for (int i = 0; i < num_leafs; i++) {
                if (this.entityClusters[i] == -1)
                    continue; // not a visible leaf
                for (j = 0; j < i; j++)
                    if (this.entityClusters[j] == this.entityClusters[i])
                        break;
                if (j == i) {
                    if (ent.num_clusters == Defines.MAX_ENT_CLUSTERS) {
                        // assume we missed some leafs, and mark by headnode
                        ent.num_clusters = -1;
                        ent.headnode = topnode;
                        break;
                    }
                    ent.clusternums[ent.num_clusters++] = this.entityClusters[i];
                }
            }
        }
        // if first time, make sure old_origin is valid
        if (0 == ent.linkcount) {
            Math3D.VectorCopy(ent.s.origin, ent.s.old_origin);
        }
        ent.linkcount++;
        if (ent.solid == Defines.SOLID_NOT)
            return;
        // find the first node that the ent's box crosses
        node = this.areaNodes[0];
        while (true) {
            if (node.axis == -1)
                break;
            if (ent.absmin[node.axis] > node.dist)
                node = node.children[0];
            else if (ent.absmax[node.axis] < node.dist)
                node = node.children[1];
            else
                break; // crosses the node
        }
        // link it in
        if (ent.solid == Defines.SOLID_TRIGGER)
            insertLinkBefore(ent.area, node.trigger_edicts);
        else
            insertLinkBefore(ent.area, node.solid_edicts);
    }

    @Override
    public void unlinkentity(ServerEntity ent) {
        if (null == ent.area.prev)
            return; // not linked in anywhere
        removeLink(ent.area);
        ent.area.prev = ent.area.next = null;
    }

    /** Collect linked entities overlapping the supplied bounds in this Game. */
    @Override
    public int BoxEdicts(float[] mins, float[] maxs, ServerEntity list[],
                         int maxcount, int areatype) {
        this.areaMins = mins;
        this.areaMaxs = maxs;
        this.areaEntities = list;
        this.areaCount = 0;
        this.areaMaxCount = maxcount;
        this.areaType = areatype;
        collectAreaEntities(this.areaNodes[0]);
        return this.areaCount;
    }

    @Override
    public void Pmove(pmove_t pmove) {
        pmove.airAccelerate = airAcceleration;
        pmoveProcessor.move(pmove);
    }

    @Override
    public <T> cvar_t<T> registerCvar(String name, CvarType<T> type, T defaultValue, int flags, String description) {
        if (!localCvars.isRegistered(name)) Cvar.getInstance().transferEarlyValue(name, localCvars);
        return localCvars.register(name, type, defaultValue, flags, description);
    }

    @Override
    public <T> cvar_t<T> findCvar(String name, Class<T> type) {
        return (localCvars.isRegistered(name) ? localCvars : Cvar.getInstance()).find(name, type);
    }

    /** Writes follow the same ownership as reads; undeclared game inputs stay local. */
    private Cvar writeRegistry(String name) {
        if (localCvars.isRegistered(name)) return localCvars;
        return Cvar.getInstance().isRegistered(name) ? Cvar.getInstance() : localCvars;
    }

    /*
     add commands to the server console as if they were typed in
     for map changing, etc
    */
    @Override
    public void AddCommandString(String text) {
        Cbuf.AddText(text);
    }

    @Override
    public int getPointContents(float[] p) {
        ServerEntity hit;
        int i, num;
        int contents, c2;
        int headnode;
        // get base contents from world
        contents = cm.PointContents(p, models[1].headnode);
        // or in contents from all the other entities
        num = BoxEdicts(p, p, this.pointContentsTouches, Defines.MAX_EDICTS, Defines.AREA_SOLID);
        for (i = 0; i < num; i++) {
            hit = this.pointContentsTouches[i];
            // might intersect, so do an exact clip
            headnode = hullForEntity(hit);
            if (hit.solid != Defines.SOLID_BSP) {
	    }
            c2 = cm.TransformedPointContents(p, headnode, hit.s.origin,
                    hit.s.angles);
            contents |= c2;
        }
        return contents;
    }

    void saveGame(List<String> args) {

        if (state != ServerStates.SS_GAME) {
            Com.Printf("You must be in a game to save.\n");
            return;
        }

        if (args.size() != 2) {
            Com.Printf("USAGE: save <directory>\n");
            return;
        }

        if (Cvar.getInstance().find("deathmatch", Boolean.class).getTypedValue()) {
            Com.Printf("Can't savegame in a deathmatch\n");
            return;
        }

        String saveGame;
        try {
            saveGame = ServerSaveJsonStore.normalizeSlot(args.get(1));
        } catch (IllegalArgumentException e) {
            Com.Printf("Bad save name.\n");
            return;
        }
        if ("current".equals(saveGame)) {
            Com.Printf("Can't save to 'current'\n");
            return;
        }

        if (serverMain.getClients().size() == 1) {
            ClientNetworkInfo client = serverMain.getClients().get(0);
            if (client.state != ClientStates.CS_SPAWNED || client.edict == null) {
                Com.Printf("Can't savegame before the player has spawned.\n");
                return;
            }
            if (client.edict.getClient().getPlayerState().stats[Defines.STAT_HEALTH] <= 0) {
                Com.Printf("\nCan't savegame while dead!\n");
                return;
            }
        }

        Com.Printf("Saving game...\n");

        // archive current level, including all client edicts.
        // when the level is reloaded, they will be shells awaiting
        // a connecting client
        SV_WriteLevelFile();

        // save server state
        try {
            SV_WriteServerFile(false);
        }
        catch (Exception e) {
            Com.Printf("IOError in SV_WriteServerFile: " + e);
        }

        // copy it off
        new ServerSaveFiles(EngineWriteRoot.path()).copy("current", saveGame);
        Com.Printf("Done.\n");
    }

    /**
     * SV_RunGameFrame.
     */
    void SV_RunGameFrame(long fixedtime) {

        // we always need to bump framenum, even if we
        // don't run the world, otherwise the delta
        // compression can get confused when a client
        // has the "current" frame
        frameNum++;

        if (serverMain.isPaused() && serverMain.getClients().size() <= 1) {
            return;
        }

        gameExports.G_RunFrame();

        // never get more than one tic behind
        if (fixedtime < realtime) {
            realtime = (int) fixedtime;
        }

    }

    /**
     * Sends text to all active clients
     */
    void SV_BroadcastCommand(String s) {

        //fixme: check if server is running
        if (state == ServerStates.SS_DEAD)
            return;

        for (ClientNetworkInfo client : serverMain.getClients()) {
            if (client.state == ClientStates.CS_FREE || client.state == ClientStates.CS_ZOMBIE)
                continue;
            client.netchan.reliablePending.add(new StuffTextMessage(s));
        }
    }

    /** Reset this Game's spatial index before spawning or restoring entities. */
    void clearWorld() {
        areaNodeCount = 0;
        areaNodes = new areanode_t[Defines.AREA_NODES];
        for (int i = 0; i < areaNodes.length; i++) areaNodes[i] = new areanode_t();
        createAreaNode(0, models[1].mins, models[1].maxs);
    }

    private static void clearLink(link_t l) {
        l.prev = l.next = l;
    }

    private static void removeLink(link_t l) {
        l.next.prev = l.prev;
        l.prev.next = l.next;
    }

    private static void insertLinkBefore(link_t l, link_t before) {
        l.next = before;
        l.prev = before.prev;
        l.prev.next = l;
        l.next.prev = l;
    }

    private areanode_t createAreaNode(int depth, float[] mins, float[] maxs) {
        areanode_t anode;
        float[] size = { 0, 0, 0 };
        float[] mins1 = { 0, 0, 0 }, maxs1 = { 0, 0, 0 }, mins2 = { 0, 0, 0 }, maxs2 = {
                0, 0, 0 };
        anode = this.areaNodes[this.areaNodeCount];
        // just for debugging (rst)
//        Math3D.VectorCopy(mins, anode.mins_rst);
//        Math3D.VectorCopy(maxs, anode.maxs_rst);
        this.areaNodeCount++;
        clearLink(anode.trigger_edicts);
        clearLink(anode.solid_edicts);
        if (depth == Defines.AREA_DEPTH) {
            anode.axis = -1;
            anode.children[0] = anode.children[1] = null;
            return anode;
        }
        Math3D.VectorSubtract(maxs, mins, size);
        if (size[0] > size[1])
            anode.axis = 0;
        else
            anode.axis = 1;
        anode.dist = 0.5f * (maxs[anode.axis] + mins[anode.axis]);
        Math3D.VectorCopy(mins, mins1);
        Math3D.VectorCopy(mins, mins2);
        Math3D.VectorCopy(maxs, maxs1);
        Math3D.VectorCopy(maxs, maxs2);
        maxs1[anode.axis] = mins2[anode.axis] = anode.dist;
        anode.children[0] = createAreaNode(depth + 1, mins2, maxs2);
        anode.children[1] = createAreaNode(depth + 1, mins1, maxs1);
        return anode;
    }

    private void collectAreaEntities(areanode_t node) {
        link_t l, next, start;
        ServerEntity check;
        // touch linked edicts
        if (this.areaType == Defines.AREA_SOLID)
            start = node.solid_edicts;
        else
            start = node.trigger_edicts;
        for (l = start.next; l != start; l = next) {
            next = l.next;
            check = (ServerEntity) l.o;
            if (check.solid == Defines.SOLID_NOT)
                continue; // deactivated
            if (check.absmin[0] > this.areaMaxs[0]
                    || check.absmin[1] > this.areaMaxs[1]
                    || check.absmin[2] > this.areaMaxs[2]
                    || check.absmax[0] < this.areaMins[0]
                    || check.absmax[1] < this.areaMins[1]
                    || check.absmax[2] < this.areaMins[2])
                continue; // not touching
            if (this.areaCount == this.areaMaxCount) {
                Com.Printf("BoxEdicts: MAXCOUNT\n");
                return;
            }
            this.areaEntities[this.areaCount] = check;
            this.areaCount++;
        }
        if (node.axis == -1)
            return; // terminal node
        // recurse down both sides
        if (this.areaMaxs[node.axis] > node.dist)
            collectAreaEntities(node.children[0]);
        if (this.areaMins[node.axis] < node.dist)
            collectAreaEntities(node.children[1]);
    }

    private int hullForEntity(ServerEntity ent) {
        cmodel_t model;
        // decide which clipping hull to use, based on the size
        if (ent.solid == Defines.SOLID_BSP) {
            // explicit hulls in the BSP model
            model = models[ent.s.modelindex];
            if (null == model)
                Com.Error(Defines.ERR_FATAL,
                        "MOVETYPE_PUSH with a non bsp model");
            return model.headnode;
        }
        // create a temp hull from bounding box sizes
        return cm.HeadnodeForBox(ent.mins, ent.maxs);
    }

    private void clipMoveToEntities(moveclip_t clip) {
        int i, num;
        ServerEntity touch;
        trace_t trace;
        int headnode;
        float angles[];
        num = BoxEdicts(clip.boxmins, clip.boxmaxs, this.traceTouches, Defines.MAX_EDICTS, Defines.AREA_SOLID);
        // be careful, it is possible to have an entity in this
        // list removed before we get to it (killtriggered)
        for (i = 0; i < num; i++) {
            touch = this.traceTouches[i];
            if (touch.solid == Defines.SOLID_NOT)
                continue;
            if (touch == clip.passedict)
                continue;
            if (clip.trace.allsolid)
                return;
            if (clip.passedict != null) {
                if (touch.getOwner() == clip.passedict)
                    continue; // don't clip against own missiles
                if (clip.passedict.getOwner() == touch)
                    continue; // don't clip against owner
            }
            if (0 == (clip.contentmask & Defines.CONTENTS_DEADMONSTER)
                    && 0 != (touch.svflags & Defines.SVF_DEADMONSTER))
                continue;
            // might intersect, so do an exact clip
            headnode = hullForEntity(touch);
            angles = touch.s.angles;
            if (touch.solid != Defines.SOLID_BSP)
                angles = Globals.vec3_origin; // boxes don't rotate
            if ((touch.svflags & Defines.SVF_MONSTER) != 0)
                trace = cm.TransformedBoxTrace(clip.start, clip.end,
                        clip.mins2, clip.maxs2, headnode, clip.contentmask,
                        touch.s.origin, angles);
            else
                trace = cm.TransformedBoxTrace(clip.start, clip.end, clip.mins,
                        clip.maxs, headnode, clip.contentmask, touch.s.origin,
                        angles);
            if (trace.allsolid || trace.startsolid
                    || trace.fraction < clip.trace.fraction) {
                trace.ent = touch;
                if (clip.trace.startsolid) {
                    clip.trace = trace;
                    clip.trace.startsolid = true;
                } else
                    clip.trace.set(trace);
            } else if (trace.startsolid)
                clip.trace.startsolid = true;
        }
    }

    private static void traceBounds(float[] start, float[] mins, float[] maxs, float[] end, float[] boxmins, float[] boxmaxs) {
        int i;
        for (i = 0; i < 3; i++) {
            if (end[i] > start[i]) {
                boxmins[i] = start[i] + mins[i] - 1;
                boxmaxs[i] = end[i] + maxs[i] + 1;
            } else {
                boxmins[i] = end[i] + mins[i] - 1;
                boxmaxs[i] = start[i] + maxs[i] + 1;
            }
        }
    }

    /** Find or allocate a configstring index within this Game's resource table. */
    private int findConfigstringIndex(String val, int start, int max) {
        int i;

        if (val == null || val.length() == 0)
            return 0;

        for (i = 1; i < max && configstrings[start + i] != null; i++)
            if (val.equals(configstrings[start + i]))
                return i;

        if (i == max)
            Com.Error(Defines.ERR_DROP, "*Index: overflow");

        configstring(start + i, val);

        return i;
    }

    /** Build the entity baselines used to compress client updates. */
    void createBaselines() {
        for (int entnum = 1; entnum < gameExports.getNumEdicts(); entnum++) {
            ServerEntity svent = gameExports.getEdict(entnum);

            if (!svent.inuse)
                continue;
            if (0 == svent.s.modelindex && 0 == svent.s.sound
                    && 0 == svent.s.effects)
                continue;

            svent.s.index = entnum;

            // take current state as baseline
            Math3D.VectorCopy(svent.s.origin, svent.s.old_origin);
            baselines[entnum].set(svent.s);
        }
    }

    /** Restore archived level state, settling revisited levels before clients reconnect. */
    void restorePreviousGame() {

        // get configstrings and areaportals
        // then read game entities
        readLevelFile();

        if (!loadgame) {
            // coming back to a level after being in a different
            // level, so run it for 100 frames

            // assert state == LOADING, otherwise, updates from 100 frames may overflow the reliable channel
            for (int i = 0; i < 100; i++) {
                gameExports.G_RunFrame();
            }
        }
    }

    boolean isPreviousLevel() {
        if (Cvar.getInstance().find("sv_noreload", Boolean.class).getTypedValue())
            return false;

        if (Cvar.getInstance().find("deathmatch", Boolean.class).getTypedValue())
            return false;

        String name = EngineWriteRoot.resolve("save/current/" + mapName + ".sav.json").toString();

        return new File(name).exists();

    }

    /** Restore this game's configstrings, portals and gameplay state from the current save. */
    void readLevelFile() {
        Com.DPrintf("SV_ReadLevelFile()\n");

        try {
            ServerLevelJsonStore store = ServerLevelJsonStore.forWriteDir(EngineWriteRoot.pathString());
            store.applyLevelState(store.readLevelState("current", mapName), configstrings, cm);
        } catch (Exception e) {
            Com.Printf("Failed to open current level state for " + mapName + "\n");
            e.printStackTrace();
        }

        String name = EngineWriteRoot.resolve("save/current/" + mapName + ".sav.json").toString();
        gameExports.ReadLevel(name);
    }

    void SV_WriteLevelFile() {

        Com.DPrintf("SV_WriteLevelFile()\n");

        try {
            ServerLevelJsonStore store = ServerLevelJsonStore.forWriteDir(EngineWriteRoot.pathString());
            store.writeLevelState("current", mapName, configstrings, cm.portalopen);
        }
        catch (Exception e) {
            Com.Printf("Failed to write current level state for " + mapName + "\n");
            e.printStackTrace();
        }

        String name = EngineWriteRoot.resolve("save/current/" + mapName + ".sav.json").toString();
        gameExports.WriteLevel(name);
    }

    /**
     * SV_WriteServerFile.
     * Save contains 2 steps: server state information (server.ssv) and game state information (game.ssv).
     * Server state contains:
     * 		comment (date)
     * 		mapcommand
     * 		latched cvars
     *
     * Game state saving is delegated to the game module
     */
    void SV_WriteServerFile(boolean autosave) {

        Com.DPrintf("SV_WriteServerFile(autosave:" + autosave + ")\n");

        try {
            ServerSaveJsonStore store = ServerSaveJsonStore.forWriteDir(EngineWriteRoot.pathString());

            final String comment;
            if (autosave) {
                comment = "Autosave in " + configstrings[Defines.CS_NAME];
            } else {
                comment = new Date() + " " + configstrings[Defines.CS_NAME];
            }

            store.writeMapCommand("current", comment, mapcmd);

            writeLatchedCvars(store);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // write game state
        gameExports.WriteGame(EngineWriteRoot.resolve("save/current/game.ssv.json").toString(), autosave);
    }

    /** Save shared and local latched cvars, with local entries taking precedence. */
    void writeLatchedCvars(ServerSaveJsonStore store) throws IOException {
        List<ServerSaveJsonStore.LatchedCvarSnapshot> latchedCvars = new ArrayList<>();
        Cvar.getInstance().eachCvarByFlags(Defines.CVAR_LATCH, var -> {
            if (!localCvars.isRegistered(var.name)) {
                // Air acceleration is captured per instance; the global value may now belong to another game.
                String value = var.name.equals("sv_airaccelerate")
                        ? Float.toString(configuredAirAcceleration) : var.getRawString();
                latchedCvars.add(new ServerSaveJsonStore.LatchedCvarSnapshot(var.name, value));
            }
        });
        localCvars.eachCvarByFlags(Defines.CVAR_LATCH, var ->
                latchedCvars.add(new ServerSaveJsonStore.LatchedCvarSnapshot(var.name, var.getRawString())));
        store.writeLatchedCvars("current", latchedCvars);
    }

    /**
     *SV_Multicast
     *
     * Sends the contents of sv.multicast to a subset of the clients,
     * then clears sv.multicast.
     *
     * MULTICAST_ALL	same as broadcast (origin can be null)
     * MULTICAST_PVS	send to clients potentially visible from org
     * MULTICAST_PHS	send to clients potentially hearable from org
     */
    private void SV_Multicast(float[] origin, MulticastTypes to, NetworkMessage msg) {
        byte mask[];
        int leafnum, cluster;
        int j;
        boolean reliable;
        int area1, area2;

        reliable = false;

        if (to != MulticastTypes.MULTICAST_ALL_R && to != MulticastTypes.MULTICAST_ALL) {
            leafnum = cm.CM_PointLeafnum(origin);
            area1 = cm.CM_LeafArea(leafnum);
        }
        else {
            leafnum = 0; // just to avoid compiler warnings
            area1 = 0;
        }

        switch (to) {
            case MULTICAST_ALL_R :
                reliable = true; // intentional fallthrough, no break here
            case MULTICAST_ALL :
                leafnum = 0;
                mask = null;
                break;

            case MULTICAST_PHS_R :
                reliable = true; // intentional fallthrough
            case MULTICAST_PHS :
                leafnum = cm.CM_PointLeafnum(origin);
                cluster = cm.CM_LeafCluster(leafnum);
                mask = cm.CM_ClusterPHS(cluster);
                break;

            case MULTICAST_PVS_R :
                reliable = true; // intentional fallthrough
            case MULTICAST_PVS :
                leafnum = cm.CM_PointLeafnum(origin);
                cluster = cm.CM_LeafCluster(leafnum);
                mask = cm.CM_ClusterPVS(cluster);
                break;

            default:
                mask = null;
                Com.Error(Defines.ERR_FATAL, "SV_Multicast: bad to:" + to + "\n");
        }

        // send the data to all relevent clients
        for (ClientNetworkInfo client : serverMain.getClientsForInstance(name)) {
            if (client == null)
                continue;
            if (client.state == ClientStates.CS_FREE || client.state == ClientStates.CS_ZOMBIE)
                continue;
            if (client.state != ClientStates.CS_SPAWNED && !reliable)
                continue;

            if (mask != null) {
                leafnum = cm.CM_PointLeafnum(client.edict.s.origin);
                cluster = cm.CM_LeafCluster(leafnum);
                area2 = cm.CM_LeafArea(leafnum);
                if (!cm.CM_AreasConnected(area1, area2))
                    continue;

                // quake2 bugfix
                if (cluster == -1)
                    continue;
                if (mask != null && (0 == (mask[cluster >> 3] & (1 << (cluster & 7)))))
                    continue;
            }

            if (reliable) {
                client.netchan.reliablePending.add(msg);
            } else
                client.unreliable.add(msg);
        }

    }

    @Override
    public void multicastMessage(float[] origin, ServerMessage msg, MulticastTypes to) {
        SV_Multicast(origin, to, msg);
    }

    @Override
    public void unicastMessage(int index, ServerMessage msg, boolean reliable) {

        if (index < 1 || index > serverMain.getClients().size())
            return;

        ClientNetworkInfo client = serverMain.getClients().get(index - 1);

        if (reliable) {
            client.netchan.reliablePending.add(msg);
        } else
            client.unreliable.add(msg);

    }

    /**
     * Build the frame and prepare a list of messages to a client system.
     * @return - listOf(FrameHeaderMessage, PlayerInfoMessage, PacketEntitiesMessage)
     *
     * SV_WriteFrameToClient
     */
    Collection<ServerMessage> buildClientFrameMessages(ClientNetworkInfo client) {
        // first, build the "frame" (previously it was invoked outside of this method)
        buildClientFrame(client);

        // now, build the messages
        List<ServerMessage> result = new ArrayList<>();
        // this is the frame we are creating
        client_frame_t currentFrame = client.frames[frameNum & Defines.UPDATE_MASK];
        client_frame_t lastReceivedFrame;
        int lastReceivedFrameNum;
        // client is asking for a retransmit
        if (client.lastReceivedFrame <= 0) {
            lastReceivedFrame = null;
            lastReceivedFrameNum = -1;
        } else if (frameNum - client.lastReceivedFrame >= (Defines.UPDATE_BACKUP - 3)) {
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
                frameNum,
                lastReceivedFrameNum,
                client.surpressCount,
                currentFrame.areabytes,
                currentFrame.areabits
        ));

        client.surpressCount = 0;

        // delta encode the playerstate
        PlayerInfoMessage playerInfoMsg = new PlayerInfoMessage(lastReceivedFrame != null ? lastReceivedFrame.ps : new player_state_t(), currentFrame.ps);
        result.add(playerInfoMsg);

        // delta encode the entities
        PacketEntitiesMessage packetEntities = buildPacketEntities(
                lastReceivedFrame,
                currentFrame);
        result.add(packetEntities);
        return result;
    }

    // SV_EmitPacketEntities
    /**
     * Encode recent entity updates into a message using the previously sent updates and the baseline (shared with the client in the beginning of the match).
     */
    private PacketEntitiesMessage buildPacketEntities(client_frame_t lastReceivedFrame,
                                                      client_frame_t currentFrame) {
        int maxClients = serverMain.getClients().size();
        assert baselines.length == Defines.MAX_EDICTS;
        int maxEntityStates = clientEntityStates.length;
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
                newState = clientEntityStates[(currentFrame.first_entity + newindex) % maxEntityStates];
                newnum = newState.index;
            }
            final int oldnum;
            if (oldindex >= from_num_entities)
                // does not exist in the frame
                oldnum = 9999;
            else {
                oldState = clientEntityStates[(lastReceivedFrame.first_entity + oldindex) % maxEntityStates];
                oldnum = oldState.index;
            }

            if (newnum == oldnum) {
                // delta update from old position.
                // because the force parm is false, this will not result
                // in any bytes being emited if the entity has not changed at all.
                // Note: players are always 'newentities', this updates
                // their oldorigin always  and prevents warping
                final boolean isPlayer = newState.index <= maxClients;
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
    private void buildFatPvs(float[] org) {
        int leafs[] = new int[64];
        int i, j, count;
        int longs;
        byte src[];
        float[] mins = { 0, 0, 0 }, maxs = { 0, 0, 0 };

        for (i = 0; i < 3; i++) {
            mins[i] = org[i] - 8;
            maxs[i] = org[i] + 8;
        }
        count = cm.CM_BoxLeafnums(mins, maxs, leafs, 64, null);

        if (count < 1)
            Com.Error(Defines.ERR_FATAL, "SV_FatPVS: count < 1");

        longs = (cm.CM_NumClusters() + 31) >> 5;

        // convert leafs to clusters
        for (i = 0; i < count; i++)
            leafs[i] = cm.CM_LeafCluster(leafs[i]);

        System.arraycopy(cm.CM_ClusterPVS(leafs[0]), 0, fatPvs, 0,
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
            //	((long *)fatPvs)[j] |= ((long *)src)[j];
            int k = 0;
            for (j = 0; j < longs; j++) {
                fatPvs[k] |= src[k++];
                fatPvs[k] |= src[k++];
                fatPvs[k] |= src[k++];
                fatPvs[k] |= src[k++];
            }
        }
    }

    /**
     * Decides which entities are going to be visible to the client (update the clientEntityStates from game entities' state),
     * and copies off the playerstat and areabits.
     */
    private void buildClientFrame(ClientNetworkInfo client) {

        ServerEntity clent = client.edict;
        if (clent.getClient() == null)
            return; // not in game yet

        // this is the frame we are creating
        client_frame_t frame = client.frames[frameNum & Defines.UPDATE_MASK];

        frame.senttime = realtime; // save it for ping calc later

        // find the client's PVS
        int i;
        float[] org = {0, 0, 0};
        for (i = 0; i < 3; i++)
            org[i] = clent.getClient().getPlayerState().pmove.origin[i] * 0.125f
                    + clent.getClient().getPlayerState().viewoffset[i];

        int leafnum = cm.CM_PointLeafnum(org);
        int clientarea = cm.CM_LeafArea(leafnum);
        int clientcluster = cm.CM_LeafCluster(leafnum);

        // calculate the visible areas
        frame.areabytes = cm.CM_WriteAreaBits(frame.areabits, clientarea);

        // grab the current player_state_t
        frame.ps.set(clent.getClient().getPlayerState());

        buildFatPvs(org);
        byte[] clientphs = cm.CM_ClusterPHS(clientcluster);

        // build up the list of visible entities
        frame.num_entities = 0;
        frame.first_entity = nextClientEntity;

        for (int e = 1; e < gameExports.getNumEdicts(); e++) {
            ServerEntity ent = gameExports.getEdict(e);

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
                if (!cm.CM_AreasConnected(clientarea, ent.areanum)) {
                    // doors can legally straddle two areas, so we may need to check another one
                    if (0 == ent.areanum2 || !cm.CM_AreasConnected(clientarea, ent.areanum2))
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
                        bitvector = fatPvs; //clientphs;
                    } else
                        bitvector = fatPvs;

                    if (ent.num_clusters == -1) { // too many leafs for
                                                  // individual check, go by
                                                  // headnode
                        if (!cm.CM_HeadnodeVisible(ent.headnode, bitvector))
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
            int ix = nextClientEntity % clientEntityStates.length;
            entity_state_t state = clientEntityStates[ix];
            if (ent.s.index != e) {
                Com.DPrintf("FIXING ENT.S.NUMBER!!!\n");
                ent.s.index = e;
            }

            //*state = ent.s;
            clientEntityStates[ix].set(ent.s);

            // don't mark players missiles as solid
            if (ent.getOwner() == client.edict)
                state.solid = 0;

            nextClientEntity++;
            frame.num_entities++;
        }
    }
}
