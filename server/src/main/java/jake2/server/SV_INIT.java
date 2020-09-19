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
import jake2.qcommon.exec.cvar_t;
import jake2.qcommon.network.NET;

import java.lang.reflect.Constructor;

import static jake2.qcommon.Defines.ERR_FATAL;

public class SV_INIT {


    /**
     * SV_InitGame.
     * 
     * A brand new game has been started.
     * todo: move to main?
     */
    static GameImportsImpl SV_InitGame() {

        if (SV_MAIN.gameImports != null) {
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


        // set max clients based on game mode
        final cvar_t maxclients = Cvar.getInstance().Get("maxclients", "1", Defines.CVAR_SERVERINFO | Defines.CVAR_LATCH);

        if (Cvar.getInstance().VariableValue("deathmatch") != 0) {
            if (maxclients.value <= 1)
                Cvar.getInstance().FullSet("maxclients", "8", Defines.CVAR_SERVERINFO | Defines.CVAR_LATCH);
            else if (maxclients.value > Defines.MAX_CLIENTS)
                Cvar.getInstance().FullSet("maxclients", "" + Defines.MAX_CLIENTS, Defines.CVAR_SERVERINFO | Defines.CVAR_LATCH);
        } else if (Cvar.getInstance().VariableValue("coop") != 0) {
            if (maxclients.value <= 1 || maxclients.value > 4)
                Cvar.getInstance().FullSet("maxclients", "4", Defines.CVAR_SERVERINFO | Defines.CVAR_LATCH);

        } else {
            // non-deathmatch, non-coop is one player
            Cvar.getInstance().FullSet("maxclients", "1", Defines.CVAR_SERVERINFO | Defines.CVAR_LATCH);
        }

        // todo: persist server static (svs)
        SV_MAIN.gameImports = new GameImportsImpl();

        // init network stuff
        NET.Config((maxclients.value > 1));
        NET.StringToAdr("192.246.40.37:" + Defines.PORT_MASTER, SV_MAIN.master_adr[0]);

        SV_MAIN.gameImports.gameExports = createGameModInstance(SV_MAIN.gameImports);
        // why? should have default values already
        // fixme should not recreate all the clients
        SV_MAIN.resetClients(SV_MAIN.gameImports.gameExports);
        return SV_MAIN.gameImports;
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

        if (SV_MAIN.gameImports != null && SV_MAIN.gameImports.sv != null) {
            SV_MAIN.gameImports.sv.loadgame = loadgame;
            SV_MAIN.gameImports.sv.isDemo = isDemo;
        }

        if (SV_MAIN.gameImports == null || SV_MAIN.gameImports.sv == null || SV_MAIN.gameImports.sv.state == ServerStates.SS_DEAD && !SV_MAIN.gameImports.sv.loadgame)
            SV_InitGame(); // the game is just starting

        // archive server state to be used in savegame
        SV_MAIN.gameImports.svs.mapcmd = levelstring;

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
        if (SV_MAIN.gameImports.firstmap.length() == 0)
        {        
        	if (!levelstring.endsWith(".cin") && !levelstring.endsWith(".pcx") && !levelstring.endsWith(".dm2"))
        	{
        		int pos = levelstring.indexOf('+');
                SV_MAIN.gameImports.firstmap = levelstring.substring(pos + 1);
        	}
        }

        // ZOID: special hack for end game screen in coop mode
        if (Cvar.getInstance().VariableValue("coop") != 0 && level.equals("victory.pcx"))
            Cvar.getInstance().Set("nextserver", "gamemap \"*" + SV_MAIN.gameImports.firstmap + "\"");

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
        SV_MAIN.gameImports.SV_BroadcastCommand("changing\n");

        if (l > 4 && level.endsWith(".cin")) {
            SV_MAIN.gameImports.SV_SpawnServer(level, spawnpoint, ServerStates.SS_CINEMATIC, isDemo, loadgame);
        } else if (l > 4 && level.endsWith(".dm2")) {
            SV_MAIN.gameImports.SV_SpawnServer(level, spawnpoint, ServerStates.SS_DEMO, isDemo, loadgame);
        } else if (l > 4 && level.endsWith(".pcx")) {
            SV_MAIN.gameImports.SV_SpawnServer(level, spawnpoint, ServerStates.SS_PIC, isDemo, loadgame);
        } else {
            SV_MAIN.SV_SendClientMessages();
            SV_MAIN.gameImports.SV_SpawnServer(level, spawnpoint, ServerStates.SS_GAME, isDemo, loadgame);
            Cbuf.CopyToDefer();
        }

        SV_MAIN.gameImports.SV_BroadcastCommand("reconnect\n");
    }

}