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
import jake2.qcommon.exec.Cvar;
import jake2.qcommon.filesystem.FS;
import jake2.qcommon.network.messages.NetworkMessage;
import jake2.qcommon.network.messages.client.StringCmdMessage;
import jake2.qcommon.network.messages.server.*;
import jake2.qcommon.util.Lib;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class SV_USER {

    static final Map<String, ServerUserCommand> userCommands;

    static {
        userCommands = new HashMap<>();
        // auto issued
        userCommands.put(StringCmdMessage.NEW, SV_USER::SV_New_f);
        userCommands.put(StringCmdMessage.CONFIG_STRINGS, SV_USER::SV_Configstrings_f);
        userCommands.put(StringCmdMessage.BASELINES, SV_USER::SV_Baselines_f);
        userCommands.put(StringCmdMessage.BEGIN, SV_USER::SV_Begin_f);
        userCommands.put(StringCmdMessage.NEXT_SERVER, SV_USER::SV_Nextserver_f);
        userCommands.put(StringCmdMessage.DISCONNECT, SV_USER::SV_Disconnect_f);

        // issued by hand at client consoles
        userCommands.put(StringCmdMessage.INFO, SV_USER::SV_ShowServerinfo_f);
        userCommands.put(StringCmdMessage.DOWNLOAD, SV_USER::SV_BeginDownload_f);
        userCommands.put(StringCmdMessage.NEXT_DOWNLOAD, SV_USER::SV_NextDownload_f);

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
     * ================ SV_New_f
     * 
     * Sends the first message from the server to a connected client. This will
     * be sent on the initial connection and upon each server load.
     * ================
     */
    private static void SV_New_f(List<String> args, GameImportsImpl gameImports, ClientNetworkInfo client) {

        Com.DPrintf("New() from " + client.name + "\n");

        if (client.state != ClientStates.CS_CONNECTED) {
            Com.Printf("New not valid -- already spawned\n");
            return;
        }

        //
        // serverdata needs to go over for all types of servers
        // to make sure the protocol is right, and to set the gamedir
        //
        String gamedir = Cvar.getInstance().VariableString("gamedir");

        // send the serverdata
        // send full levelname

        final int clientIndex;
        if (gameImports.sv.state == ServerStates.SS_CINEMATIC || gameImports.sv.state == ServerStates.SS_PIC)
            clientIndex = -1;
        else
            clientIndex = client.edict.index - 1;


        client.netchan.reliablePending.add(
                new ServerDataMessage(
                        Defines.PROTOCOL_VERSION,
                        gameImports.spawncount,
                        gameImports.sv.isDemo,
                        gamedir,
                        clientIndex,
                        gameImports.sv.configstrings[Defines.CS_NAME]
                ));
        //
        // game server
        // 
        if (gameImports.sv.state == ServerStates.SS_GAME) {
            // set up the entity for the client
            ServerEntity ent = gameImports.gameExports.getEdict(clientIndex + 1);
            ent.s.number = clientIndex + 1;
            client.edict = ent;
            client.lastcmd = new usercmd_t();

            // begin fetching configstrings
            client.netchan.reliablePending.add(new StuffTextMessage(String.format("cmd %s %d 0", StringCmdMessage.CONFIG_STRINGS, gameImports.spawncount)));
        }
    }

    /*
     * ================== SV_Configstrings_f ==================
     */
    private static void SV_Configstrings_f(List<String> args, GameImportsImpl gameImports, ClientNetworkInfo client) {

        Com.DPrintf("Configstrings() from " + client.name + "\n");

        if (client.state != ClientStates.CS_CONNECTED) {
            Com.Printf("configstrings not valid -- already spawned\n");
            return;
        }

        // handle the case of a level changing while a client was connecting
        int spawnCount = args.size() >= 2 ? Lib.atoi(args.get(1)) : 0;
        if (spawnCount != gameImports.spawncount) {
            Com.Printf("SV_Configstrings_f from different level\n");
            SV_New_f(args, gameImports, client);
            return;
        }

        int start = args.size() >= 3 ? Lib.atoi(args.get(2)) : 0;

        // write a packet full of data
        int currentReliableSize = client.netchan.reliablePending.stream().mapToInt(NetworkMessage::getSize).sum();
        while (currentReliableSize < Defines.MAX_MSGLEN / 2 && start < Defines.MAX_CONFIGSTRINGS) {
            if (gameImports.sv.configstrings[start] != null && gameImports.sv.configstrings[start].length() != 0) {
                final ConfigStringMessage config = new ConfigStringMessage(start, gameImports.sv.configstrings[start]);
                currentReliableSize += config.getSize();
                client.netchan.reliablePending.add(config);
            }
            start++;
        }

        // send next command
        final String nextCmd;

        if (start == Defines.MAX_CONFIGSTRINGS) {
            nextCmd = String.format("cmd %s %d 0", StringCmdMessage.BASELINES, gameImports.spawncount);
        } else {
            nextCmd = String.format("cmd %s %d %d", StringCmdMessage.CONFIG_STRINGS, gameImports.spawncount, start);
        }
        client.netchan.reliablePending.add(new StuffTextMessage(nextCmd));
    }

    /*
     * ================== SV_Baselines_f ==================
     */
    private static void SV_Baselines_f(List<String> args, GameImportsImpl gameImports, ClientNetworkInfo client) {

        Com.DPrintf("Baselines() from " + client.name + "\n");

        if (client.state != ClientStates.CS_CONNECTED) {
            Com.Printf("baselines not valid -- already spawned\n");
            return;
        }

        // handle the case of a level changing while a client was connecting
        int spawnCount = args.size() >= 2 ? Lib.atoi(args.get(1)) : 0;
        if (spawnCount != gameImports.spawncount) {
            Com.Printf("SV_Baselines_f from different level\n");
            SV_New_f(args, gameImports, client);
            return;
        }

        // todo: validate argument
        int start = args.size() >= 3 ? Lib.atoi(args.get(2)) : 0;

        // write a packet full of data
        int currentReliableSize = client.netchan.reliablePending.stream().mapToInt(NetworkMessage::getSize).sum();
        while (currentReliableSize < Defines.MAX_MSGLEN / 2 && start < Defines.MAX_EDICTS) {
            entity_state_t base = gameImports.sv.baselines[start];
            if (base.modelindex != 0 || base.sound != 0 || base.effects != 0) {
                final SpawnBaselineMessage spawn = new SpawnBaselineMessage(base);
                currentReliableSize += spawn.getSize();
                client.netchan.reliablePending.add(spawn);
            }
            start++;
        }

        // send next command
        final String nextCmd;
        if (start == Defines.MAX_EDICTS) {
            // finished sending baselines
            nextCmd = String.format("%s %d", StringCmdMessage.PRECACHE,gameImports.spawncount);
        } else {
            // continue from where we finished
            nextCmd = String.format("cmd %s %d %d", StringCmdMessage.BASELINES, gameImports.spawncount, start);
        }
        client.netchan.reliablePending.add(new StuffTextMessage(nextCmd));
    }

    /*
     * ================== SV_Begin_f ==================
     */
    private static void SV_Begin_f(List<String> args, GameImportsImpl gameImports, ClientNetworkInfo client) {
        Com.DPrintf("Begin() from " + client.name + "\n");

        // handle the case of a level changing while a client was connecting
        int spawnCount = args.size() >= 2 ? Lib.atoi(args.get(1)) : 0;
        if (spawnCount != gameImports.spawncount) {
            Com.Printf("SV_Begin_f from different level\n");
            SV_New_f(args, gameImports, client);
            return;
        }

        client.state = ClientStates.CS_SPAWNED;

        // call the jake2.game begin function
        gameImports.gameExports.ClientBegin(client.edict);

        Cbuf.InsertFromDefer();
    }

    //=============================================================================

    /*
     * ================== SV_NextDownload_f ==================
     */
    private static void SV_NextDownload_f(List<String> args, GameImportsImpl gameImports, ClientNetworkInfo client) {

        if (client.download == null)
            return;

        int packet = client.downloadsize - client.downloadcount;
        if (packet > 1024)
            packet = 1024;

        client.downloadcount += packet;
        int size = client.downloadsize;
        if (size == 0)
            size = 1;
        byte percent = (byte) (client.downloadcount * 100 / size);

        byte[] data = new byte[packet];
        System.arraycopy(client.download, client.downloadcount - packet, data, 0, packet);
        client.netchan.reliablePending.add(new DownloadMessage(data, percent));

        if (client.downloadcount == client.downloadsize) {
            client.download = null;
        }
    }

    /*
     * ================== SV_BeginDownload_f ==================
     */
    private static void SV_BeginDownload_f(List<String> args, GameImportsImpl gameImports, ClientNetworkInfo client) {
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

            // refuse
            client.netchan.reliablePending.add(new DownloadMessage());
            return;
        }

        client.download = FS.LoadFile(name);
        
        // rst: this handles loading errors, no message yet visible 
        if (client.download == null) {
        	return;
        }
        
        client.downloadsize = client.download.length;
        client.downloadcount = offset;

        if (offset > client.downloadsize)
            client.downloadcount = client.downloadsize;

        // special check for maps, if it came from a pak file, don't allow download ZOID
        if (name.startsWith("maps/") && FS.FOpenFile(name).fromPack) {
            Com.DPrintf("Couldn't download " + name + " to " + client.name + "\n");
            if (client.download != null) {
                client.download = null;
            }

            // refuse
            client.netchan.reliablePending.add(new DownloadMessage());
            return;
        }

        SV_NextDownload_f(args, gameImports, client);
        Com.DPrintf("Downloading " + name + " to " + client.name + "\n");
    }

    //============================================================================

    /*
     * ================= SV_Disconnect_f
     * 
     * The client is going to disconnect, so remove the connection immediately
     * =================
     */
    private static void SV_Disconnect_f(List<String> args, GameImportsImpl gameImports, ClientNetworkInfo client) {
        //	SV_EndRedirect ();
        SV_MAIN.SV_DropClient(client);
    }

    /*
     * ================== SV_ShowServerinfo_f
     * 
     * Dumps the serverinfo info string ==================
     */
    private static void SV_ShowServerinfo_f(List<String> args, GameImportsImpl gameImports, ClientNetworkInfo client) {
        Info.Print(Cvar.getInstance().Serverinfo());
    }

    static void SV_Nextserver(GameImportsImpl gameImports) {

        //ZOID, ss_pic can be nextserver'd in coop mode
        if (gameImports.sv.state == ServerStates.SS_GAME
                || (gameImports.sv.state == ServerStates.SS_PIC && 0 == Cvar.getInstance().VariableValue("coop")))
            return; // can't nextserver while playing a normal game

        gameImports.spawncount++; // make sure another doesn't sneak in
        String v = Cvar.getInstance().VariableString("nextserver");
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
    private static void SV_Nextserver_f(List<String> args, GameImportsImpl gameImports, ClientNetworkInfo client) {
        int spawnCount = args.size() >= 2 ? Lib.atoi(args.get(1)) : 0;
        if (spawnCount != gameImports.spawncount) {
            Com.DPrintf("Nextserver() from wrong level, from " + client.name + "\n");
            return; // leftover from last server
        }

        Com.DPrintf("Nextserver() from " + client.name + "\n");
        SV_Nextserver(gameImports);
    }

}
