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
import jake2.qcommon.exec.Cbuf;
import jake2.qcommon.exec.Command;
import jake2.qcommon.exec.Cvar;
import jake2.qcommon.filesystem.FS;
import jake2.qcommon.network.NetworkCommands;
import jake2.qcommon.util.Lib;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class SV_USER {

    static edict_t sv_player;

    static final Map<String, Command> userCommands;

    static {
        userCommands = new HashMap<>();
        // auto issued
        userCommands.put("new", SV_USER::SV_New_f);
        userCommands.put("configstrings", SV_USER::SV_Configstrings_f);
        userCommands.put("baselines", SV_USER::SV_Baselines_f);
        userCommands.put("begin", SV_USER::SV_Begin_f);
        userCommands.put("nextserver", SV_USER::SV_Nextserver_f);
        userCommands.put("disconnect", SV_USER::SV_Disconnect_f);

        // issued by hand at client consoles
        userCommands.put("info", SV_USER::SV_ShowServerinfo_f);
        userCommands.put("download", SV_USER::SV_BeginDownload_f);
        userCommands.put("nextdl", SV_USER::SV_NextDownload_f);

    }


    /*
     * ============================================================
     * 
     * USER STRINGCMD EXECUTION
     * 
     * sv_client and sv_player will be valid.
     * ============================================================
     */

    /*
     * ================== SV_BeginDemoServer ==================
     */
    private static void SV_BeginDemoserver() {

        String name = "demos/" + SV_INIT.gameImports.sv.name;
        SV_INIT.gameImports.sv.demofile = FS.FOpenFile(name);
        if (SV_INIT.gameImports.sv.demofile == null)
            Com.Error(Defines.ERR_DROP, "Couldn't open " + name + "\n");
    }

    /*
     * ================ SV_New_f
     * 
     * Sends the first message from the server to a connected client. This will
     * be sent on the initial connection and upon each server load.
     * ================
     */
    private static void SV_New_f(List<String> args) {
        String gamedir;
        int playernum;

        Com.DPrintf("New() from " + SV_MAIN.sv_client.name + "\n");

        if (SV_MAIN.sv_client.state != ClientStates.CS_CONNECTED) {
            Com.Printf("New not valid -- already spawned\n");
            return;
        }

        // demo servers just dump the file message
        if (SV_INIT.gameImports.sv.state == ServerStates.SS_DEMO) {
            SV_BeginDemoserver();
            return;
        }

        //
        // serverdata needs to go over for all types of servers
        // to make sure the protocol is right, and to set the gamedir
        //
        gamedir = Cvar.getInstance().VariableString("gamedir");

        // send the serverdata
        MSG.WriteByte(SV_MAIN.sv_client.netchan.message,
                        NetworkCommands.svc_serverdata);
        MSG.WriteInt(SV_MAIN.sv_client.netchan.message,
                Defines.PROTOCOL_VERSION);
        
        MSG.WriteLong(SV_MAIN.sv_client.netchan.message,
                        SV_INIT.gameImports.svs.spawncount);
        MSG.WriteByte(SV_MAIN.sv_client.netchan.message,
                SV_INIT.gameImports.sv.isDemo ? 1 : 0);
        MSG.WriteString(SV_MAIN.sv_client.netchan.message, gamedir);

        if (SV_INIT.gameImports.sv.state == ServerStates.SS_CINEMATIC
                || SV_INIT.gameImports.sv.state == ServerStates.SS_PIC)
            playernum = -1;
        else
            //playernum = sv_client - svs.clients;
            playernum = SV_MAIN.sv_client.serverindex;

        MSG.WriteShort(SV_MAIN.sv_client.netchan.message, playernum);

        // send full levelname
        MSG.WriteString(SV_MAIN.sv_client.netchan.message,
                SV_INIT.gameImports.sv.configstrings[Defines.CS_NAME]);

        //
        // game server
        // 
        if (SV_INIT.gameImports.sv.state == ServerStates.SS_GAME) {
            // set up the entity for the client
            edict_t ent = SV_INIT.gameImports.gameExports.getEdict(playernum + 1);
            ent.s.number = playernum + 1;
            SV_MAIN.sv_client.edict = ent;
            SV_MAIN.sv_client.lastcmd = new usercmd_t();

            // begin fetching configstrings
            MSG.WriteByte(SV_MAIN.sv_client.netchan.message,
                    NetworkCommands.svc_stufftext);
            MSG.WriteString(SV_MAIN.sv_client.netchan.message,
                    "cmd configstrings " + SV_INIT.gameImports.svs.spawncount + " 0\n");
        }
        
    }

    /*
     * ================== SV_Configstrings_f ==================
     */
    private static void SV_Configstrings_f(List<String> args) {

        Com.DPrintf("Configstrings() from " + SV_MAIN.sv_client.name + "\n");

        if (SV_MAIN.sv_client.state != ClientStates.CS_CONNECTED) {
            Com.Printf("configstrings not valid -- already spawned\n");
            return;
        }

        // handle the case of a level changing while a client was connecting
        int spawnCount = args.size() >= 2 ? Lib.atoi(args.get(1)) : 0;
        if (spawnCount != SV_INIT.gameImports.svs.spawncount) {
            Com.Printf("SV_Configstrings_f from different level\n");
            SV_New_f(args);
            return;
        }

        int start = args.size() >= 3 ? Lib.atoi(args.get(2)) : 0;

        // write a packet full of data

        while (SV_MAIN.sv_client.netchan.message.cursize < Defines.MAX_MSGLEN / 2
                && start < Defines.MAX_CONFIGSTRINGS) {
            if (SV_INIT.gameImports.sv.configstrings[start] != null
                    && SV_INIT.gameImports.sv.configstrings[start].length() != 0) {
                MSG.WriteByte(SV_MAIN.sv_client.netchan.message,
                        NetworkCommands.svc_configstring);
                MSG.WriteShort(SV_MAIN.sv_client.netchan.message, start);
                MSG.WriteString(SV_MAIN.sv_client.netchan.message,
                        SV_INIT.gameImports.sv.configstrings[start]);
            }
            start++;
        }

        // send next command

        if (start == Defines.MAX_CONFIGSTRINGS) {
            MSG.WriteByte(SV_MAIN.sv_client.netchan.message,
                    NetworkCommands.svc_stufftext);
            MSG.WriteString(SV_MAIN.sv_client.netchan.message, "cmd baselines "
                    + SV_INIT.gameImports.svs.spawncount + " 0\n");
        } else {
            MSG.WriteByte(SV_MAIN.sv_client.netchan.message,
                    NetworkCommands.svc_stufftext);
            MSG.WriteString(SV_MAIN.sv_client.netchan.message,
                    "cmd configstrings " + SV_INIT.gameImports.svs.spawncount + " " + start
                            + "\n");
        }
    }

    /*
     * ================== SV_Baselines_f ==================
     */
    private static void SV_Baselines_f(List<String> args) {

        Com.DPrintf("Baselines() from " + SV_MAIN.sv_client.name + "\n");

        if (SV_MAIN.sv_client.state != ClientStates.CS_CONNECTED) {
            Com.Printf("baselines not valid -- already spawned\n");
            return;
        }

        // handle the case of a level changing while a client was connecting
        int spawnCount = args.size() >= 2 ? Lib.atoi(args.get(1)) : 0;
        if (spawnCount != SV_INIT.gameImports.svs.spawncount) {
            Com.Printf("SV_Baselines_f from different level\n");
            SV_New_f(args);
            return;
        }

        int start = args.size() >= 3 ? Lib.atoi(args.get(2)) : 0;

        //memset (&nullstate, 0, sizeof(nullstate));
        entity_state_t nullstate = new entity_state_t(null);

        // write a packet full of data

        while (SV_MAIN.sv_client.netchan.message.cursize < Defines.MAX_MSGLEN / 2
                && start < Defines.MAX_EDICTS) {
            entity_state_t base = SV_INIT.gameImports.sv.baselines[start];
            if (base.modelindex != 0 || base.sound != 0 || base.effects != 0) {
                MSG.WriteByte(SV_MAIN.sv_client.netchan.message,
                        NetworkCommands.svc_spawnbaseline);
                MSG.WriteDeltaEntity(nullstate, base,
                        SV_MAIN.sv_client.netchan.message, true, true);
            }
            start++;
        }

        // send next command

        if (start == Defines.MAX_EDICTS) {
            MSG.WriteByte(SV_MAIN.sv_client.netchan.message,
                    NetworkCommands.svc_stufftext);
            MSG.WriteString(SV_MAIN.sv_client.netchan.message, "precache "
                    + SV_INIT.gameImports.svs.spawncount + "\n");
        } else {
            MSG.WriteByte(SV_MAIN.sv_client.netchan.message,
                    NetworkCommands.svc_stufftext);
            MSG.WriteString(SV_MAIN.sv_client.netchan.message, "cmd baselines "
                    + SV_INIT.gameImports.svs.spawncount + " " + start + "\n");
        }
    }

    /*
     * ================== SV_Begin_f ==================
     */
    private static void SV_Begin_f(List<String> args) {
        Com.DPrintf("Begin() from " + SV_MAIN.sv_client.name + "\n");

        // handle the case of a level changing while a client was connecting
        int spawnCount = args.size() >= 2 ? Lib.atoi(args.get(1)) : 0;
        if (spawnCount != SV_INIT.gameImports.svs.spawncount) {
            Com.Printf("SV_Begin_f from different level\n");
            SV_New_f(args);
            return;
        }

        SV_MAIN.sv_client.state = ClientStates.CS_SPAWNED;

        // call the jake2.game begin function
        SV_INIT.gameImports.gameExports.ClientBegin(SV_USER.sv_player);

        Cbuf.InsertFromDefer();
    }

    //=============================================================================

    /*
     * ================== SV_NextDownload_f ==================
     */
    private static void SV_NextDownload_f(List<String> args) {
        int r;
        int percent;
        int size;

        if (SV_MAIN.sv_client.download == null)
            return;

        r = SV_MAIN.sv_client.downloadsize - SV_MAIN.sv_client.downloadcount;
        if (r > 1024)
            r = 1024;

        MSG.WriteByte(SV_MAIN.sv_client.netchan.message, NetworkCommands.svc_download);
        MSG.WriteShort(SV_MAIN.sv_client.netchan.message, r);

        SV_MAIN.sv_client.downloadcount += r;
        size = SV_MAIN.sv_client.downloadsize;
        if (size == 0)
            size = 1;
        percent = SV_MAIN.sv_client.downloadcount * 100 / size;
        MSG.WriteByte(SV_MAIN.sv_client.netchan.message, percent);
        SZ.Write(SV_MAIN.sv_client.netchan.message, SV_MAIN.sv_client.download,
                SV_MAIN.sv_client.downloadcount - r, r);

        if (SV_MAIN.sv_client.downloadcount != SV_MAIN.sv_client.downloadsize)
            return;

        SV_MAIN.sv_client.download = null;
    }

    /*
     * ================== SV_BeginDownload_f ==================
     */
    private static void SV_BeginDownload_f(List<String> args) {
        int offset = 0;

        if (args.size() < 2)
            return;

        String name = args.get(1);

        if (args.size() > 2)
            offset = Lib.atoi(args.get(2)); // downloaded offset

        // hacked by zoid to allow more conrol over download
        // first off, no .. or global allow check

        if (name.contains("..")
                || SV_MAIN.allow_download.value == 0 // leading dot is no good
                || name.charAt(0) == '.' // leading slash bad as well, must be
                                         // in subdir
                || name.charAt(0) == '/' // next up, skin check
                || (name.startsWith("players/") && 0 == SV_MAIN.allow_download_players.value) // now
                                                                                              // models
                || (name.startsWith("models/") && 0 == SV_MAIN.allow_download_models.value) // now
                                                                                            // sounds
                || (name.startsWith("sound/") && 0 == SV_MAIN.allow_download_sounds.value)
                // now maps (note special case for maps, must not be in pak)
                || (name.startsWith("maps/") && 0 == SV_MAIN.allow_download_maps.value) // MUST
                                                                                        // be
                                                                                        // in a
                                                                                        // subdirectory
                || name.indexOf('/') == -1) { // don't allow anything with ..
                                              // path
            MSG.WriteByte(SV_MAIN.sv_client.netchan.message,
                    NetworkCommands.svc_download);
            MSG.WriteShort(SV_MAIN.sv_client.netchan.message, -1);
            MSG.WriteByte(SV_MAIN.sv_client.netchan.message, 0);
            return;
        }

        SV_MAIN.sv_client.download = FS.LoadFile(name);
        
        // rst: this handles loading errors, no message yet visible 
        if (SV_MAIN.sv_client.download == null)
        {        	
        	return;
        }
        
        SV_MAIN.sv_client.downloadsize = SV_MAIN.sv_client.download.length;
        SV_MAIN.sv_client.downloadcount = offset;

        if (offset > SV_MAIN.sv_client.downloadsize)
            SV_MAIN.sv_client.downloadcount = SV_MAIN.sv_client.downloadsize;

        // special check for maps, if it
        // came from a pak file, don't
        // allow
        // download ZOID
        if (name.startsWith("maps/") && FS.FOpenFile(name).fromPack) {
            Com.DPrintf("Couldn't download " + name + " to "
                    + SV_MAIN.sv_client.name + "\n");
            if (SV_MAIN.sv_client.download != null) {
                SV_MAIN.sv_client.download = null;
            }

            MSG.WriteByte(SV_MAIN.sv_client.netchan.message,
                    NetworkCommands.svc_download);
            MSG.WriteShort(SV_MAIN.sv_client.netchan.message, -1);
            MSG.WriteByte(SV_MAIN.sv_client.netchan.message, 0);
            return;
        }

        SV_NextDownload_f(args);
        Com.DPrintf("Downloading " + name + " to " + SV_MAIN.sv_client.name
                + "\n");
    }

    //============================================================================

    /*
     * ================= SV_Disconnect_f
     * 
     * The client is going to disconnect, so remove the connection immediately
     * =================
     */
    private static void SV_Disconnect_f(List<String> args) {
        //	SV_EndRedirect ();
        SV_INIT.gameImports.SV_DropClient(SV_MAIN.sv_client);
    }

    /*
     * ================== SV_ShowServerinfo_f
     * 
     * Dumps the serverinfo info string ==================
     */
    private static void SV_ShowServerinfo_f(List<String> args) {
        Info.Print(Cvar.getInstance().Serverinfo());
    }

    static void SV_Nextserver() {
        String v;

        //ZOID, ss_pic can be nextserver'd in coop mode
        if (SV_INIT.gameImports.sv.state == ServerStates.SS_GAME
                || (SV_INIT.gameImports.sv.state == ServerStates.SS_PIC &&
                        0 == Cvar.getInstance().VariableValue("coop")))
            return; // can't nextserver while playing a normal game

        SV_INIT.gameImports.svs.spawncount++; // make sure another doesn't sneak in
        v = Cvar.getInstance().VariableString("nextserver");
        //if (!v[0])
        if (v.length() == 0)
            Cbuf.AddText("killserver\n");
        else {
            Cbuf.AddText(v);
            Cbuf.AddText("\n");
        }
        Cvar.getInstance().Set("nextserver", "");
    }

    /*
     * ================== SV_Nextserver_f
     * 
     * A cinematic has completed or been aborted by a client, so move to the
     * next server, ==================
     */
    private static void SV_Nextserver_f(List<String> args) {
        int spawnCount = args.size() >= 2 ? Lib.atoi(args.get(1)) : 0;
        if (spawnCount != SV_INIT.gameImports.svs.spawncount) {
            Com.DPrintf("Nextserver() from wrong level, from "
                    + SV_MAIN.sv_client.name + "\n");
            return; // leftover from last server
        }

        Com.DPrintf("Nextserver() from " + SV_MAIN.sv_client.name + "\n");

        SV_Nextserver();
    }

}
