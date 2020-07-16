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

// Created on 14.01.2004 by RST.
// $Id: SV_INIT.java,v 1.17 2006-01-20 22:44:07 salomo Exp $
package jake2.server;

import jake2.qcommon.*;
import jake2.qcommon.exec.Cbuf;
import jake2.qcommon.exec.Cmd;
import jake2.qcommon.exec.Cvar;
import jake2.qcommon.filesystem.FS;
import jake2.qcommon.network.MulticastTypes;
import jake2.qcommon.network.NET;
import jake2.qcommon.network.NetworkCommands;
import jake2.qcommon.util.Math3D;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Constructor;

import static jake2.qcommon.Defines.ERR_FATAL;

public class SV_INIT {

    // todo implement singleton
    public static GameExports gameExports;
    public static GameImportsImpl gameImports;

    /**
     * SV_FindIndex.
     */
    private static int SV_FindIndex(String name, int start, int max,
                                    boolean create) {
        int i;

        if (name == null || name.length() == 0)
            return 0;

        for (i = 1; i < max && gameImports.sv.configstrings[start + i] != null; i++)
            if (name.equals(gameImports.sv.configstrings[start + i]))
                return i;

        if (!create)
            return 0;

        if (i == max)
            Com.Error(Defines.ERR_DROP, "*Index: overflow");

        gameImports.sv.configstrings[start + i] = name;

        if (gameImports.sv.state != ServerStates.SS_LOADING) {
            // send the update to everyone
            gameImports.sv.multicast.clear();
            MSG.WriteChar(gameImports.sv.multicast, NetworkCommands.svc_configstring);
            MSG.WriteShort(gameImports.sv.multicast, start + i);
            MSG.WriteString(gameImports.sv.multicast, name);
            SV_SEND.SV_Multicast(Globals.vec3_origin, MulticastTypes.MULTICAST_ALL_R);
        }

        return i;
    }

    static int SV_ModelIndex(String name) {
        return SV_FindIndex(name, Defines.CS_MODELS, Defines.MAX_MODELS, true);
    }

    static int SV_SoundIndex(String name) {
        return SV_FindIndex(name, Defines.CS_SOUNDS, Defines.MAX_SOUNDS, true);
    }

    static int SV_ImageIndex(String name) {
        return SV_FindIndex(name, Defines.CS_IMAGES, Defines.MAX_IMAGES, true);
    }

    /**
     * SV_CreateBaseline
     * 
     * Entity baselines are used to compress the update messages to the clients --
     * only the fields that differ from the baseline will be transmitted.
     */
    private static void SV_CreateBaseline() {
        for (int entnum = 1; entnum < gameExports.getNumEdicts(); entnum++) {
            edict_t svent = gameExports.getEdict(entnum);

            if (!svent.inuse)
                continue;
            if (0 == svent.s.modelindex && 0 == svent.s.sound
                    && 0 == svent.s.effects)
                continue;
            
            svent.s.number = entnum;

            // take current state as baseline
            Math3D.VectorCopy(svent.s.origin, svent.s.old_origin);
            gameImports.sv.baselines[entnum].set(svent.s);
        }
    }

    /** 
     * SV_CheckForSavegame.
     */
    private static void SV_CheckForSavegame() {

        String name;
        RandomAccessFile f;

        int i;

        if (SV_MAIN.sv_noreload.value != 0)
            return;

        if (Cvar.VariableValue("deathmatch") != 0)
            return;

        name = FS.getWriteDir() + "/save/current/" + gameImports.sv.name + ".sav";
        try {
            f = new RandomAccessFile(name, "r");
        }

        catch (Exception e) {
            return;
        }

        try {
            f.close();
        } catch (IOException e1) {
            e1.printStackTrace();
        }

        SV_WORLD.SV_ClearWorld();

        // get configstrings and areaportals
        SV_CCMDS.SV_ReadLevelFile();

        if (!gameImports.sv.loadgame) { 
            // coming back to a level after being in a different
            // level, so run it for ten seconds

            // rlava2 was sending too many lightstyles, and overflowing the
            // reliable data. temporarily changing the server state to loading
            // prevents these from being passed down.
            ServerStates previousState; // PGM

            previousState = gameImports.sv.state; // PGM
            gameImports.sv.state = ServerStates.SS_LOADING; // PGM
            for (i = 0; i < 100; i++)
                gameExports.G_RunFrame();

            gameImports.sv.state = previousState; // PGM
        }
    }

    /**
     * SV_SpawnServer.
     * 
     * Change the server to a new map, taking all connected clients along with
     * it.
     */
    private static void SV_SpawnServer(String server, String spawnpoint,
                                       ServerStates serverstate, boolean attractloop, boolean loadgame) {
        int i;
        int checksum = 0;

        if (attractloop)
            Cvar.Set("paused", "0");

        Com.Printf("------- Server Initialization -------\n");

        Com.DPrintf("SpawnServer: " + server + "\n");
        if (gameImports != null && gameImports.sv != null && gameImports.sv.demofile != null)
            try {
                gameImports.sv.demofile.close();
            } 
        	catch (Exception e) {
                Com.DPrintf("Could not close demofile: " + e.getMessage() +  "\n");
            }

        // any partially connected client will be restarted
        gameImports.svs.spawncount++;

        Globals.server_state = ServerStates.SS_DEAD; //todo check if this is needed

        // wipe the entire per-level structure
        gameImports.sv = new server_t();

        gameImports.svs.realtime = 0;
        gameImports.sv.loadgame = loadgame;
        gameImports.sv.attractloop = attractloop;

        // save name for levels that don't set message
        gameImports.sv.configstrings[Defines.CS_NAME] = server;

        if (Cvar.VariableValue("deathmatch") != 0) {
            gameImports.sv.configstrings[Defines.CS_AIRACCEL] = ""
                    + SV_MAIN.sv_airaccelerate.value;
            PMove.pm_airaccelerate = SV_MAIN.sv_airaccelerate.value;
        } else {
            gameImports.sv.configstrings[Defines.CS_AIRACCEL] = "0";
            PMove.pm_airaccelerate = 0;
        }

        SZ.Init(gameImports.sv.multicast, gameImports.sv.multicast_buf, gameImports.sv.multicast_buf.length);

        gameImports.sv.name = server;

        // leave slots at start for clients only
        for (i = 0; i < SV_MAIN.maxclients.value; i++) {
            // needs to reconnect
            if (gameImports.svs.clients[i].state == ClientStates.CS_SPAWNED)
                gameImports.svs.clients[i].state = ClientStates.CS_CONNECTED;
            gameImports.svs.clients[i].lastframe = -1;
        }

        gameImports.sv.time = 1000;

        gameImports.sv.name = server;
        gameImports.sv.configstrings[Defines.CS_NAME] = server;

        int iw[] = { checksum };

        if (serverstate != ServerStates.SS_GAME) {
            gameImports.sv.models[1] = CM.CM_LoadMap("", false, iw); // no real map
        } else {
            gameImports.sv.configstrings[Defines.CS_MODELS + 1] = "maps/" + server + ".bsp";
            gameImports.sv.models[1] = CM.CM_LoadMap(
                    gameImports.sv.configstrings[Defines.CS_MODELS + 1], false, iw);
        }
        checksum = iw[0];
        gameImports.sv.configstrings[Defines.CS_MAPCHECKSUM] = "" + checksum;


        // clear physics interaction links
        
        SV_WORLD.SV_ClearWorld();

        for (i = 1; i < CM.CM_NumInlineModels(); i++) {
            gameImports.sv.configstrings[Defines.CS_MODELS + 1 + i] = "*" + i;
            
            // copy references
            gameImports.sv.models[i + 1] = CM.InlineModel(gameImports.sv.configstrings[Defines.CS_MODELS + 1 + i]);
        }

     
        // spawn the rest of the entities on the map

        // precache and static commands can be issued during
        // map initialization

        gameImports.sv.state = ServerStates.SS_LOADING;
        Globals.server_state = gameImports.sv.state;

        // load and spawn all other entities
        gameExports.SpawnEntities(gameImports.sv.name, CM.CM_EntityString(), spawnpoint);

        // run two frames to allow everything to settle
        gameExports.G_RunFrame();
        gameExports.G_RunFrame();

        // all precaches are complete
        gameImports.sv.state = serverstate;
        Globals.server_state = gameImports.sv.state;

        // create a baseline for more efficient communications
        SV_CreateBaseline();

        // check for a savegame
        SV_CheckForSavegame();

        // set serverinfo variable
        Cvar.FullSet("mapname", gameImports.sv.name, Defines.CVAR_SERVERINFO
                | Defines.CVAR_NOSET);
    }

    /**
     * SV_InitGame.
     * 
     * A brand new game has been started.
     */
    static void SV_InitGame() {

        if (gameImports != null) {
            // cause any connected clients to reconnect
            SV_MAIN.SV_Shutdown("Server restarted\n", true);
        } else {
            // make sure the client is down
            Cmd.ExecuteFunction("loading");
            Cmd.ExecuteFunction("cl_drop");
        }

        // get any latched variable changes (maxclients, etc)
        Cvar.updateLatchedVars();
        gameImports = new GameImportsImpl();

        if (Cvar.VariableValue("coop") != 0 && Cvar.VariableValue("deathmatch") != 0) {
            Com.Printf("Deathmatch and Coop both set, disabling Coop\n");
            Cvar.FullSet("coop", "0", Defines.CVAR_SERVERINFO | Defines.CVAR_LATCH);
        }

        // dedicated servers are can't be single player and are usually DM
        // so unless they explicity set coop, force it to deathmatch
        if (Globals.dedicated.value != 0) {
            if (0 == Cvar.VariableValue("coop"))
                Cvar.FullSet("deathmatch", "1", Defines.CVAR_SERVERINFO | Defines.CVAR_LATCH);
        }

        // set max clients based on game mode
        if (Cvar.VariableValue("deathmatch") != 0) {
            if (SV_MAIN.maxclients.value <= 1)
                Cvar.FullSet("maxclients", "8", Defines.CVAR_SERVERINFO | Defines.CVAR_LATCH);
            else if (SV_MAIN.maxclients.value > Defines.MAX_CLIENTS)
                Cvar.FullSet("maxclients", "" + Defines.MAX_CLIENTS, Defines.CVAR_SERVERINFO | Defines.CVAR_LATCH);
        } else if (Cvar.VariableValue("coop") != 0) {
            if (SV_MAIN.maxclients.value <= 1 || SV_MAIN.maxclients.value > 4)
                Cvar.FullSet("maxclients", "4", Defines.CVAR_SERVERINFO | Defines.CVAR_LATCH);

        } else {
            // non-deathmatch, non-coop is one player
            Cvar.FullSet("maxclients", "1", Defines.CVAR_SERVERINFO | Defines.CVAR_LATCH);
        }

        // init network stuff
        NET.Config((SV_MAIN.maxclients.value > 1));
        NET.StringToAdr("192.246.40.37:" + Defines.PORT_MASTER, SV_MAIN.master_adr[0]);

        // this is one of the most important points of game initialization:
        // new instance of game mode is created and is ready to update.
        // The journey to "un-static-ification" can start from here and grow in both directions to server and game modules
        // todo: put server initialization code above into constructor of GameImportsImpl
        // init game
        gameExports = createGameModInstance(gameImports);

        gameImports.resetClients(gameExports);
    }

    /**
     * Find and create an instance of the game subsystem
     */
    private static GameExports createGameModInstance(GameImportsImpl gameImports) {

        // todo: introduce proper Dependency Injection
        try {
            Class<?> game = Class.forName("jake2.game.GameExportsImpl");
            Constructor<?> constructor = game.getConstructor(GameImports.class);
            return (GameExports) constructor.newInstance(gameImports);
        } catch (Exception e) {
            e.printStackTrace();
            Com.Error(ERR_FATAL, "Could not initialise game subsystem due to : " + e.getMessage() + "\n");
            return null;
        }
    }


    /**
     * SV_Map
     * 
     * the full syntax is:
     * 
     * map [*] <map>$ <startspot>+ <nextserver>
     * 
     * command from the console or progs. Map can also be a.cin, .pcx, or .dm2 file.
     * 
     * Nextserver is used to allow a cinematic to play, then proceed to
     * another level:
     * 
     * map tram.cin+jail_e3
     */
    static void SV_Map(boolean attractloop, String levelstring, boolean loadgame) {

        if (gameImports != null && gameImports.sv != null) {
            gameImports.sv.loadgame = loadgame;
            gameImports.sv.attractloop = attractloop;
        }

        if (gameImports == null || gameImports.sv == null || gameImports.sv.state == ServerStates.SS_DEAD && !gameImports.sv.loadgame)
            SV_InitGame(); // the game is just starting

        String level = levelstring; // bis hier her ok.

        // if there is a + in the map, set nextserver to the remainder

        int c = level.indexOf('+');
        if (c != -1) {
            Cvar.Set("nextserver", "gamemap \"" + level.substring(c + 1) + "\"");
            level = level.substring(0, c);
        } else {
            Cvar.Set("nextserver", "");
        }
        
        // rst: base1 works for full, damo1 works for demo, so we need to store first map.
        if (gameImports.firstmap.length() == 0)
        {        
        	if (!levelstring.endsWith(".cin") && !levelstring.endsWith(".pcx") && !levelstring.endsWith(".dm2"))
        	{
        		int pos = levelstring.indexOf('+');
                gameImports.firstmap = levelstring.substring(pos + 1);
        	}
        }

        // ZOID: special hack for end game screen in coop mode
        if (Cvar.VariableValue("coop") != 0 && level.equals("victory.pcx"))
            Cvar.Set("nextserver", "gamemap \"*" + gameImports.firstmap + "\"");

        // if there is a $, use the remainder as a spawnpoint
        int pos = level.indexOf('$');
        String spawnpoint;
        if (pos != -1) {
            spawnpoint = level.substring(pos + 1);
            level = level.substring(0, pos);

        } else
            spawnpoint = "";

        // skip the end-of-unit flag * if necessary
        if (level.charAt(0) == '*')
            level = level.substring(1);

        int l = level.length();
        if (l > 4 && level.endsWith(".cin")) {
            Cmd.ExecuteFunction("loading"); // for local system
            SV_SEND.SV_BroadcastCommand("changing\n");
            SV_SpawnServer(level, spawnpoint, ServerStates.SS_CINEMATIC,
                    attractloop, loadgame);
        } else if (l > 4 && level.endsWith(".dm2")) {
            Cmd.ExecuteFunction("loading"); // for local system
            SV_SEND.SV_BroadcastCommand("changing\n");
            SV_SpawnServer(level, spawnpoint, ServerStates.SS_DEMO, attractloop,
                    loadgame);
        } else if (l > 4 && level.endsWith(".pcx")) {
            Cmd.ExecuteFunction("loading"); // for local system
            SV_SEND.SV_BroadcastCommand("changing\n");
            SV_SpawnServer(level, spawnpoint, ServerStates.SS_PIC, attractloop,
                    loadgame);
        } else {
            Cmd.ExecuteFunction("loading"); // for local system
            SV_SEND.SV_BroadcastCommand("changing\n");
            SV_SEND.SV_SendClientMessages();
            SV_SpawnServer(level, spawnpoint, ServerStates.SS_GAME, attractloop,
                    loadgame);
            Cbuf.CopyToDefer();
        }

        SV_SEND.SV_BroadcastCommand("reconnect\n");
    }
}