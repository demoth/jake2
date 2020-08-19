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
import jake2.qcommon.network.NET;

import java.lang.reflect.Constructor;
import java.util.List;

import static jake2.qcommon.Defines.*;

public class SV_INIT {

    // todo implement singleton
    @Deprecated
    public static GameImportsImpl gameImports;


    /**
     * SV_InitGame.
     * 
     * A brand new game has been started.
     * todo: move to main?
     */
    static GameImportsImpl SV_InitGame() {

        if (gameImports != null) {
            // cause any connected clients to reconnect
            SV_MAIN.SV_Shutdown("Server restarted\n", true);
        } else {
            // make sure the client is down
            Cmd.ExecuteFunction("loading");
            Cmd.ExecuteFunction("cl_drop");
        }

        // get any latched variable changes (maxclients, etc)
        Cvar.getInstance().updateLatchedVars();

        if (Cvar.getInstance().VariableValue("coop") != 0 && Cvar.getInstance().VariableValue("deathmatch") != 0) {
            Com.Printf("Deathmatch and Coop both set, disabling Coop\n");
            Cvar.getInstance().FullSet("coop", "0", Defines.CVAR_SERVERINFO | Defines.CVAR_LATCH);
        }

        // dedicated servers are can't be single player and are usually DM
        // so unless they explicity set coop, force it to deathmatch
        if (Globals.dedicated.value != 0) {
            if (0 == Cvar.getInstance().VariableValue("coop"))
                Cvar.getInstance().FullSet("deathmatch", "1", Defines.CVAR_SERVERINFO | Defines.CVAR_LATCH);
        }

        // todo: persist server static (svs)
        gameImports = new GameImportsImpl();

        // set max clients based on game mode
        if (Cvar.getInstance().VariableValue("deathmatch") != 0) {
            if (SV_INIT.gameImports.maxclients.value <= 1)
                Cvar.getInstance().FullSet("maxclients", "8", Defines.CVAR_SERVERINFO | Defines.CVAR_LATCH);
            else if (SV_INIT.gameImports.maxclients.value > Defines.MAX_CLIENTS)
                Cvar.getInstance().FullSet("maxclients", "" + Defines.MAX_CLIENTS, Defines.CVAR_SERVERINFO | Defines.CVAR_LATCH);
        } else if (Cvar.getInstance().VariableValue("coop") != 0) {
            if (SV_INIT.gameImports.maxclients.value <= 1 || SV_INIT.gameImports.maxclients.value > 4)
                Cvar.getInstance().FullSet("maxclients", "4", Defines.CVAR_SERVERINFO | Defines.CVAR_LATCH);

        } else {
            // non-deathmatch, non-coop is one player
            Cvar.getInstance().FullSet("maxclients", "1", Defines.CVAR_SERVERINFO | Defines.CVAR_LATCH);
        }

        // init network stuff
        NET.Config((SV_INIT.gameImports.maxclients.value > 1));
        NET.StringToAdr("192.246.40.37:" + Defines.PORT_MASTER, SV_MAIN.master_adr[0]);

        gameImports.gameExports = createGameModInstance(gameImports);
        gameImports.resetClients(); // why? should have default values already
        return gameImports;
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
     * @param levelstring
     * the full syntax is:
     * 
     * map [*]mapname$spawnpoint+nextserver
     * 
     * command from the console or progs. Map can also be a.cin, .pcx, or .dm2 file.
     * 
     * Nextserver is used to allow a cinematic to play, then proceed to
     * another level:
     * 
     * map tram.cin+jail_e3
     */
    static void SV_Map(boolean isDemo, String levelstring, boolean loadgame) {

        if (gameImports != null && gameImports.sv != null) {
            gameImports.sv.loadgame = loadgame;
            gameImports.sv.isDemo = isDemo;
        }

        if (gameImports == null || gameImports.sv == null || gameImports.sv.state == ServerStates.SS_DEAD && !gameImports.sv.loadgame)
            SV_InitGame(); // the game is just starting

        String level = levelstring; // bis hier her ok.

        // if there is a + in the map, set nextserver to the remainder

        int c = level.indexOf('+');
        if (c != -1) {
            Cvar.getInstance().Set("nextserver", "gamemap \"" + level.substring(c + 1) + "\"");
            level = level.substring(0, c);
        } else {
            Cvar.getInstance().Set("nextserver", "");
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
        if (Cvar.getInstance().VariableValue("coop") != 0 && level.equals("victory.pcx"))
            Cvar.getInstance().Set("nextserver", "gamemap \"*" + gameImports.firstmap + "\"");

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


        Cmd.ExecuteFunction("loading"); // for local system
        gameImports.SV_BroadcastCommand("changing\n");

        if (l > 4 && level.endsWith(".cin")) {
            gameImports.SV_SpawnServer(level, spawnpoint, ServerStates.SS_CINEMATIC, isDemo, loadgame);
        } else if (l > 4 && level.endsWith(".dm2")) {
            gameImports.SV_SpawnServer(level, spawnpoint, ServerStates.SS_DEMO, isDemo, loadgame);
        } else if (l > 4 && level.endsWith(".pcx")) {
            gameImports.SV_SpawnServer(level, spawnpoint, ServerStates.SS_PIC, isDemo, loadgame);
        } else {
            gameImports.SV_SendClientMessages();
            gameImports.SV_SpawnServer(level, spawnpoint, ServerStates.SS_GAME, isDemo, loadgame);
            Cbuf.CopyToDefer();
        }

        gameImports.SV_BroadcastCommand("reconnect\n");
    }

    /**
     * Only called at quake2.exe startup, not for each game
     */
    public static void SV_Init() {

        // add commands to start the server instance. Other sv_ccmds are registered after the server is up (when these 4 are run)
        Cmd.AddCommand("map", SV_CCMDS::SV_Map_f);
        Cmd.AddCommand("demomap", SV_CCMDS::SV_DemoMap_f);
        Cmd.AddCommand("gamemap", SV_CCMDS::SV_GameMap_f);
        Cmd.AddCommand("load", SV_CCMDS::SV_Loadgame_f);

        Cmd.AddCommand("maplist", (List<String> args) -> {
            byte[] bytes = FS.LoadFile("maps.lst");
            if (bytes == null) {
                Com.Error(ERR_DROP, "Could not read maps.lst");
                return;
            }
            for (String line : new String(bytes).split("\n")){
                Com.Printf(PRINT_ALL, line.trim() + "\n");
            }
        });
        Cmd.AddCommand("jvm_memory", SV_CCMDS::VM_Mem_f);



        Cvar.getInstance().Get("rcon_password", "", 0);
        Cvar.getInstance().Get("deathmatch", "0", Defines.CVAR_LATCH);
        Cvar.getInstance().Get("coop", "0", Defines.CVAR_LATCH);
        Cvar.getInstance().Get("dmflags", "" + Defines.DF_INSTANT_ITEMS, Defines.CVAR_SERVERINFO);
        Cvar.getInstance().Get("fraglimit", "0", Defines.CVAR_SERVERINFO);
        Cvar.getInstance().Get("timelimit", "0", Defines.CVAR_SERVERINFO);
        Cvar.getInstance().Get("cheats", "0", Defines.CVAR_SERVERINFO | Defines.CVAR_LATCH);
        Cvar.getInstance().Get("protocol", "" + Defines.PROTOCOL_VERSION, Defines.CVAR_SERVERINFO | Defines.CVAR_NOSET);

        SV_MAIN.hostname = Cvar.getInstance().Get("hostname", "noname", Defines.CVAR_SERVERINFO | Defines.CVAR_ARCHIVE);
        SV_MAIN.timeout = Cvar.getInstance().Get("timeout", "125", 0);
        SV_MAIN.zombietime = Cvar.getInstance().Get("zombietime", "2", 0);
        SV_MAIN.sv_showclamp = Cvar.getInstance().Get("showclamp", "0", 0);
        SV_MAIN.sv_paused = Cvar.getInstance().Get("paused", "0", 0);
        SV_MAIN.sv_timedemo = Cvar.getInstance().Get("timedemo", "0", 0);
        SV_MAIN.sv_enforcetime = Cvar.getInstance().Get("sv_enforcetime", "0", 0);

        SV_MAIN.allow_download = Cvar.getInstance().Get("allow_download", "1", Defines.CVAR_ARCHIVE);
        SV_MAIN.allow_download_players = Cvar.getInstance().Get("allow_download_players", "0", Defines.CVAR_ARCHIVE);
        SV_MAIN.allow_download_models = Cvar.getInstance().Get("allow_download_models", "1", Defines.CVAR_ARCHIVE);
        SV_MAIN.allow_download_sounds = Cvar.getInstance().Get("allow_download_sounds", "1", Defines.CVAR_ARCHIVE);
        SV_MAIN.allow_download_maps = Cvar.getInstance().Get("allow_download_maps", "1", Defines.CVAR_ARCHIVE);

        Cvar.getInstance().Get("sv_noreload", "0", 0);
        SV_MAIN.sv_airaccelerate = Cvar.getInstance().Get("sv_airaccelerate", "0", Defines.CVAR_LATCH);
        SV_MAIN.public_server = Cvar.getInstance().Get("public", "0", 0);
        SV_MAIN.sv_reconnect_limit = Cvar.getInstance().Get("sv_reconnect_limit", "3", Defines.CVAR_ARCHIVE);

    }
}