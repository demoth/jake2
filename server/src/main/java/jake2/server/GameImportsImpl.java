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
import jake2.qcommon.exec.Cmd;
import jake2.qcommon.exec.Cvar;
import jake2.qcommon.exec.cvar_t;
import jake2.qcommon.filesystem.FS;
import jake2.qcommon.network.*;
import jake2.qcommon.util.Lib;
import jake2.qcommon.util.Vargs;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.List;

import static jake2.qcommon.exec.Cmd.getArguments;
import static jake2.server.SV_CCMDS.*;
import static jake2.server.SV_INIT.SV_CheckForSavegame;
import static jake2.server.SV_INIT.SV_CreateBaseline;
import static jake2.server.SV_MAIN.SV_ConnectionlessPacket;
import static jake2.server.SV_USER.userCommands;

/*
 Collection of functions provided by the main engine.
 Also serves as the holder of game state from the server side

 todo make singleton (same as game exports)
*/
public class GameImportsImpl implements GameImports {
    private static final int MAX_STRINGCMDS = 8;

    public GameExports gameExports;

    // persistent server state
    public server_static_t svs;

    // local (instance) server state
    public server_t sv;

    // hack for finishing game in coop mode
    public String firstmap = "";

    SV_WORLD world;

    private final Cvar localCvars;

    public CM cm;

    public GameImportsImpl() {

        // Initialize server static state
        svs = new server_static_t();
        svs.initialized = true;
        svs.spawncount = Lib.rand();

        // Clear all clients
        svs.clients = new client_t[(int) SV_MAIN.maxclients.value]; //todo use cvar
        for (int n = 0; n < svs.clients.length; n++) {
            svs.clients[n] = new client_t();
            svs.clients[n].serverindex = n;
            svs.clients[n].lastcmd = new usercmd_t();
        }

        svs.num_client_entities = ((int) SV_MAIN.maxclients.value)
                * Defines.UPDATE_BACKUP * 64; //ok.

        // Clear all client entity states
        svs.client_entities = new entity_state_t[svs.num_client_entities];
        for (int n = 0; n < svs.client_entities.length; n++) {
            svs.client_entities[n] = new entity_state_t(null);
        }

        // heartbeats will always be sent to the id master
        svs.last_heartbeat = -99999; // send immediately

        // create local server state
        sv = new server_t();

        world = new SV_WORLD();
        cm = new CM();
        SV_InitOperatorCommands();

        localCvars = new Cvar();
    }

    /**
     * bind operator commands to this server instance
     */
    private void SV_InitOperatorCommands() {
        Cmd.AddCommand("heartbeat", this::SV_Heartbeat_f, true);
        Cmd.AddCommand("kick", this::SV_Kick_f, true);
        Cmd.AddCommand("status", this::SV_Status_f, true);
        Cmd.AddCommand("serverinfo", this::SV_Serverinfo_f, true);
        Cmd.AddCommand("dumpuser", this::SV_DumpUser_f, true);

        Cmd.AddCommand("setmaster", this::SV_SetMaster_f, true);

        if (Globals.dedicated.value != 0)
            Cmd.AddCommand("say", this::SV_ConSay_f, true);

        Cmd.AddCommand("serverrecord", this::SV_ServerRecord_f, true);
        Cmd.AddCommand("serverstop", this::SV_ServerStop_f, true);
        Cmd.AddCommand("save", this::SV_Savegame_f, true);
        Cmd.AddCommand("killserver", this::SV_KillServer_f, true);

        // ip filtering and stuff
        Cmd.AddCommand("sv", args -> {
            if (gameExports != null)
                gameExports.ServerCommand(args);
        }, true);

        // this command doesn't look like linked to this particular instance
        Cmd.AddCommand("sv_shutdown", args -> {
            String reason;
            if (args.size() > 1) {
                reason = args.get(1);
            } else {
                reason = "Server is shut down";
            }

            SV_MAIN.SV_Shutdown(reason + "\n", args.size() > 2 && Boolean.parseBoolean(args.get(2)));
        });
    }

    void resetClients() {
        for (int i = 0; i < SV_MAIN.maxclients.value; i++) {
            svs.clients[i].edict = gameExports.getEdict(i + 1);
        }
    }

    // special messages
    @Override
    public void bprintf(int printlevel, String s) {
        SV_SEND.SV_BroadcastPrintf(printlevel, s);
    }

    @Override
    public void dprintf(String s) {
        Com.Printf(s);
    }

    @Override
    public void cprintf(edict_t ent, int printlevel, String s) {
        SV_GAME.PF_cprintf(ent, printlevel, s);
    }

    @Override
    public void centerprintf(edict_t ent, String s) {
        SV_GAME.PF_centerprintf(ent, s);
    }

    @Override
    public void sound(edict_t ent, int channel, int soundindex, float volume,
                      float attenuation, float timeofs) {
        SV_GAME.PF_StartSound(ent, channel, soundindex, volume, attenuation,
                timeofs);
    }

    @Override
    public void positioned_sound(float[] origin, edict_t ent, int channel,
                                 int soundinedex, float volume, float attenuation, float timeofs) {

        SV_SEND.SV_StartSound(origin, ent, channel, soundinedex, volume,
                attenuation, timeofs);
    }

    /*
     config strings hold all the index strings, the lightstyles,
     and misc data like the sky definition and cdtrack.
     All of the current configstrings are sent to clients when
     they connect, and changes are sent to all connected clients.
    */
    @Override
    public void configstring(int num, String string) {
        SV_GAME.PF_Configstring(num, string, this);
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
        return SV_INIT.SV_ModelIndex(name);
    }

    @Override
    public int soundindex(String name) {
        return SV_INIT.SV_SoundIndex(name);
    }

    @Override
    public int imageindex(String name) {
        return SV_INIT.SV_ImageIndex(name);
    }

    @Override
    public void setmodel(edict_t ent, String name) {
        SV_GAME.PF_setmodel(ent, name, this);
    }

    /* collision detection */
    @Override
    public trace_t trace(float[] start, float[] mins, float[] maxs,
                         float[] end, edict_t passent, int contentmask) {
        return SV_WORLD.SV_Trace(start, mins, maxs, end, passent, contentmask, this);
    }

    @Override
    public boolean inPHS(float[] p1, float[] p2) {
        return SV_GAME.PF_inPHS(p1, p2, this);
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
    public void linkentity(edict_t ent) {
        SV_WORLD.SV_LinkEdict(ent, this);
    }

    @Override
    public void unlinkentity(edict_t ent) {
        SV_WORLD.SV_UnlinkEdict(ent);
    }

    /* call before removing an interactive edict */
    @Override
    public int BoxEdicts(float[] mins, float[] maxs, edict_t list[],
                         int maxcount, int areatype) {
        return SV_WORLD.SV_AreaEdicts(mins, maxs, list, maxcount, areatype, this);
    }

    @Override
    public void Pmove(pmove_t pmove) {
        PMove.Pmove(pmove);
    }

    /*
     player movement code common with client prediction
     network messaging
    */
    @Override
    public void multicast(float[] origin, MulticastTypes to) {
        SV_SEND.SV_Multicast(origin, to, cm);
    }

    @Override
    public void unicast(edict_t ent, boolean reliable) {
        SV_GAME.PF_Unicast(ent, reliable);
    }

    @Override
    public void WriteByte(int c) {
        SV_GAME.PF_WriteByte(c);
    }

    @Override
    public void WriteShort(int c) {
        SV_GAME.PF_WriteShort(c);
    }

    @Override
    public void WriteString(String s) {
        SV_GAME.PF_WriteString(s);
    }

    @Override
    public void WritePosition(float[] pos) {
        SV_GAME.PF_WritePos(pos);
    }

    /* some fractional bits */
    @Override
    public void WriteDir(float[] pos) {
        SV_GAME.PF_WriteDir(pos);
    }

    /**
     * Ready only expected
     * Todo: return value instead
     */
    @Override
    public cvar_t cvar(String var_name, String value, int flags) {
        final cvar_t parentValue = Cvar.getInstance().FindVar(var_name);
        if (parentValue != null) {
            return parentValue;
        } else {
            return localCvars.Get(var_name, value, flags);
        }
    }

    @Override
    public cvar_t cvar_set(String var_name, String value) {
        return localCvars.Set(var_name, value);
    }

    @Override
    public cvar_t cvar_forceset(String var_name, String value) {
        return localCvars.ForceSet(var_name, value);
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
        return SV_WORLD.SV_PointContents(p, this);
    }

    private void SV_Savegame_f(List<String> args) {

        if (sv.state != ServerStates.SS_GAME) {
            Com.Printf("You must be in a game to save.\n");
            return;
        }

        if (args.size() != 2) {
            Com.Printf("USAGE: save <directory>\n");
            return;
        }

        if (SV_INIT.gameImports.cvar("deathmatch", "", 0).value != 0) {
            Com.Printf("Can't savegame in a deathmatch\n");
            return;
        }

        String saveGame = args.get(1);
        if ("current".equals(saveGame)) {
            Com.Printf("Can't save to 'current'\n");
            return;
        }

        if (SV_MAIN.maxclients.value == 1 && svs.clients[0].edict.getClient().getPlayerState().stats[Defines.STAT_HEALTH] <= 0) {
            Com.Printf("\nCan't savegame while dead!\n");
            return;
        }

        if (saveGame.contains("..") || saveGame.contains("/") || saveGame.contains("\\")) {
            Com.Printf("Bad save name.\n");
        }

        Com.Printf("Saving game...\n");

        // archive current level, including all client edicts.
        // when the level is reloaded, they will be shells awaiting
        // a connecting client
        SV_WriteLevelFile(this);

        // save server state
        try {
            SV_WriteServerFile(false, this);
        }
        catch (Exception e) {
            Com.Printf("IOError in SV_WriteServerFile: " + e);
        }

        // copy it off
        SV_CopySaveGame("current", saveGame);
        Com.Printf("Done.\n");
    }

	/*
	==================
	SV_Kick_f

	Kick a user off of the server
	==================
	*/
    private void SV_Kick_f(List<String> args) {
        if (!svs.initialized) {
            Com.Printf("No server running.\n");
            return;
        }

        if (args.size() != 2) {
            Com.Printf("Usage: kick <userid>\n");
            return;
        }

        if (!SV_SetPlayer(args))
            return;

        SV_SEND.SV_BroadcastPrintf(Defines.PRINT_HIGH, SV_MAIN.sv_client.name + " was kicked\n");
        // print directly, because the dropped client won't get the
        // SV_BroadcastPrintf message
        SV_SEND.SV_ClientPrintf(SV_MAIN.sv_client, Defines.PRINT_HIGH, "You were kicked from the game\n");
        SV_DropClient(SV_MAIN.sv_client);
        SV_MAIN.sv_client.lastmessage = svs.realtime; // min case there is a funny zombie
    }
    /*
    ================
    SV_Status_f
    ================
    */
    private void SV_Status_f(List<String> args) {
        int i, j, l;
        client_t cl;
        String s;
        int ping;
        if (svs.clients == null) {
            Com.Printf("No server running.\n");
            return;
        }
        Com.Printf("map              : " + sv.name + "\n");

        Com.Printf("num score ping name            lastmsg address               qport \n");
        Com.Printf("--- ----- ---- --------------- ------- --------------------- ------\n");
        for (i = 0; i < SV_MAIN.maxclients.value; i++) {
            cl = svs.clients[i];
            if (ClientStates.CS_FREE == cl.state)
                continue;

            Com.Printf("%3i ", new Vargs().add(i));
            Com.Printf("%5i ", new Vargs().add(cl.edict.getClient().getPlayerState().stats[Defines.STAT_FRAGS]));

            if (cl.state == ClientStates.CS_CONNECTED)
                Com.Printf("CNCT ");
            else if (cl.state == ClientStates.CS_ZOMBIE)
                Com.Printf("ZMBI ");
            else {
                ping = cl.ping < 9999 ? cl.ping : 9999;
                Com.Printf("%4i ", new Vargs().add(ping));
            }

            Com.Printf("%s", new Vargs().add(cl.name));
            l = 16 - cl.name.length();
            for (j = 0; j < l; j++)
                Com.Printf(" ");

            Com.Printf("%7i ", new Vargs().add(svs.realtime - cl.lastmessage));

            s = NET.AdrToString(cl.netchan.remote_address);
            Com.Printf(s);
            l = 22 - s.length();
            for (j = 0; j < l; j++)
                Com.Printf(" ");

            Com.Printf("%5i", new Vargs().add(cl.netchan.qport));

            Com.Printf("\n");
        }
        Com.Printf("\n");
    }
    /*
    ==================
    SV_ConSay_f
    ==================
    */
    private void SV_ConSay_f(List<String> args) {
        client_t client;
        int j;
        String p;
        String text; // char[1024];

        if (args.size() < 2)
            return;

        text = "console: ";
        p = getArguments(args);

        if (p.charAt(0) == '"') {
            p = p.substring(1, p.length() - 1);
        }

        text += p;

        for (j = 0; j < SV_MAIN.maxclients.value; j++) {
            client = svs.clients[j];
            if (client.state != ClientStates.CS_SPAWNED)
                continue;
            SV_SEND.SV_ClientPrintf(client, Defines.PRINT_CHAT, text + "\n");
        }
    }
    /*
    ==================
    SV_Heartbeat_f
    ==================
    */
    private void SV_Heartbeat_f(List<String> agrs) {
        svs.last_heartbeat = -9999999;
    }
    /*
    ===========
    SV_Serverinfo_f

      Examine or change the serverinfo string
    ===========
    */
    private void SV_Serverinfo_f(List<String> args) {
        Com.Printf("Server info settings:\n");
        Info.Print(Cvar.getInstance().Serverinfo());
    }
    /*
    ===========
    SV_DumpUser_f

    Examine all a users info strings
    ===========
    */
    private void SV_DumpUser_f(List<String> args) {
        if (args.size() != 2) {
            Com.Printf("Usage: info <userid>\n");
            return;
        }

        if (!SV_SetPlayer(args))
            return;

        Com.Printf("userinfo\n");
        Com.Printf("--------\n");
        Info.Print(SV_MAIN.sv_client.userinfo);

    }
    /*
    ==============
    SV_ServerRecord_f

    Begins server demo recording.  Every entity and every message will be
    recorded, but no playerinfo will be stored.  Primarily for demo merging.
    ==============
    */
    private void SV_ServerRecord_f(List<String> args) {
        byte[] buf_data = new byte[32768];
        sizebuf_t buf = new sizebuf_t();
        int len;
        int i;

        if (args.size() != 2) {
            Com.Printf("serverrecord <demoname>\n");
            return;
        }

        if (svs.demofile != null) {
            Com.Printf("Already recording.\n");
            return;
        }

        if (sv.state != ServerStates.SS_GAME) {
            Com.Printf("You must be in a level to record.\n");
            return;
        }

        //
        // open the demo file
        //
        String name = FS.getWriteDir() + "/demos/" + args.get(1) + ".dm2";

        Com.Printf("recording to " + name + ".\n");
        FS.CreatePath(name);
        try {
            svs.demofile = new RandomAccessFile(name, "rw");
        }
        catch (Exception e) {
            Com.Printf("ERROR: couldn't open.\n");
            return;
        }

        // setup a buffer to catch all multicasts
        SZ.Init(svs.demo_multicast, svs.demo_multicast_buf, svs.demo_multicast_buf.length);

        //
        // write a single giant fake message with all the startup info
        //
        SZ.Init(buf, buf_data, buf_data.length);

        //
        // serverdata needs to go over for all types of servers
        // to make sure the protocol is right, and to set the gamedir
        //
        // send the serverdata
        MSG.WriteByte(buf, NetworkCommands.svc_serverdata);
        MSG.WriteLong(buf, Defines.PROTOCOL_VERSION);
        MSG.WriteLong(buf, svs.spawncount);
        // 2 means server demo
        MSG.WriteByte(buf, 2); // demos are always attract loops
        MSG.WriteString(buf, cvar("gamedir", "", 0).string);
        MSG.WriteShort(buf, -1);
        // send full levelname
        MSG.WriteString(buf, sv.configstrings[Defines.CS_NAME]);

        for (i = 0; i < Defines.MAX_CONFIGSTRINGS; i++)
            if (sv.configstrings[i] != null && sv.configstrings[i].length() > 0) {
                MSG.WriteByte(buf, NetworkCommands.svc_configstring);
                MSG.WriteShort(buf, i);
                MSG.WriteString(buf, sv.configstrings[i]);
            }

        // write it to the demo file
        Com.DPrintf("signon message length: " + buf.cursize + "\n");
        len = EndianHandler.swapInt(buf.cursize);
        //fwrite(len, 4, 1, svs.demofile);
        //fwrite(buf.data, buf.cursize, 1, svs.demofile);
        try {
            svs.demofile.writeInt(len);
            svs.demofile.write(buf.data, 0, buf.cursize);
        }
        catch (IOException e1) {
            // TODO: do quake2 error handling!
            e1.printStackTrace();
        }

        // the rest of the demo file will be individual frames
    }
    /*
    ==============
    SV_ServerStop_f

    Ends server demo recording
    ==============
    */
    private void SV_ServerStop_f(List<String> args) {
        if (svs.demofile == null) {
            Com.Printf("Not doing a serverrecord.\n");
            return;
        }
        try {
            svs.demofile.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        svs.demofile = null;
        Com.Printf("Recording completed.\n");
    }
    /*
    ===============
    SV_KillServer_f

    Kick everyone off, possibly in preparation for a new jake2.game

    ===============
    */
    private void SV_KillServer_f(List<String> args) {
        if (!svs.initialized)
            return;
        SV_MAIN.SV_Shutdown("Server was killed.\n", false);
        NET.Config(false); // close network sockets
    }
    //===========================================================

    /*
    ====================
    SV_SetMaster_f

    Specify a list of master servers
    ====================
    */
    @Deprecated
    private void SV_SetMaster_f(List<String> args) {
        int i, slot;

        // only dedicated servers send heartbeats
        if (Globals.dedicated.value == 0) {
            Com.Printf("Only dedicated servers use masters.\n");
            return;
        }

        // make sure the server is listed public
        Cvar.getInstance().Set("public", "1");

        for (i = 1; i < Defines.MAX_MASTERS; i++)
            SV_MAIN.master_adr[i] = new netadr_t();

        slot = 1; // slot 0 will always contain the id master
        for (i = 1; i < args.size(); i++) {
            if (slot == Defines.MAX_MASTERS)
                break;

            if (!NET.StringToAdr(args.get(i), SV_MAIN.master_adr[i])) {
                Com.Printf("Bad address: " + args.get(i) + "\n");
                continue;
            }
            if (SV_MAIN.master_adr[slot].port == 0)
                SV_MAIN.master_adr[slot].port = Defines.PORT_MASTER;

            Com.Printf("Master server at " + NET.AdrToString(SV_MAIN.master_adr[slot]) + "\n");
            Com.Printf("Sending a ping.\n");

            Netchan.OutOfBandPrint(Defines.NS_SERVER, SV_MAIN.master_adr[slot], "ping");

            slot++;
        }

        SV_INIT.gameImports.svs.last_heartbeat = -9999999;
    }

    /*
    ==================
    SV_SetPlayer

    Sets sv_client and sv_player to the player with idnum Cmd.Argv(1)
    ==================
    */
    private boolean SV_SetPlayer(List<String> args) {

        if (args.size() < 2)
            return false;

        String idOrName = args.get(1);

        // numeric values are just slot numbers
        if (idOrName.charAt(0) >= '0' && idOrName.charAt(0) <= '9') {
            int id = Lib.atoi(idOrName);
            if (id < 0 || id >= SV_MAIN.maxclients.value) {
                Com.Printf("Bad client slot: " + id + "\n");
                return false;
            }

            SV_MAIN.sv_client = svs.clients[id];
            SV_USER.sv_player = SV_MAIN.sv_client.edict;
            if (ClientStates.CS_FREE == SV_MAIN.sv_client.state) {
                Com.Printf("Client " + id + " is not active\n");
                return false;
            }
            return true;
        }

        // check for a name match
        for (int i = 0; i < SV_MAIN.maxclients.value; i++) {
            client_t cl = svs.clients[i];
            if (ClientStates.CS_FREE == cl.state)
                continue;
            if (idOrName.equals(cl.name)) {
                SV_MAIN.sv_client = cl;
                SV_USER.sv_player = SV_MAIN.sv_client.edict;
                return true;
            }
        }

        Com.Printf("Userid " + idOrName + " is not on the server\n");
        return false;
    }

    /**
     * SV_SpawnServer.
     *
     * Change the server to a new map, taking all connected clients along with
     * it.
     */
    void SV_SpawnServer(String mapName, String spawnpoint, ServerStates serverstate, boolean isDemo, boolean loadgame) {

        if (isDemo)
            Cvar.getInstance().Set("paused", "0");

        Com.Printf("------- Server Initialization -------\n");

        Com.DPrintf("SpawnServer: " + mapName + "\n");
        if (sv != null && sv.demofile != null)
            try {
                sv.demofile.close();
            }
            catch (Exception e) {
                Com.DPrintf("Could not close demofile: " + e.getMessage() +  "\n");
            }

        // any partially connected client will be restarted
        svs.spawncount++;

        Globals.server_state = ServerStates.SS_DEAD; //todo check if this is needed

        // wipe the entire per-level structure
        sv = new server_t();

        svs.realtime = 0;
        sv.loadgame = loadgame;
        sv.isDemo = isDemo;

        // save name for levels that don't set message
        sv.configstrings[Defines.CS_NAME] = mapName;

        if (Cvar.getInstance().VariableValue("deathmatch") != 0) {
            sv.configstrings[Defines.CS_AIRACCEL] = "" + SV_MAIN.sv_airaccelerate.value;
            PMove.pm_airaccelerate = SV_MAIN.sv_airaccelerate.value;
        } else {
            sv.configstrings[Defines.CS_AIRACCEL] = "0";
            PMove.pm_airaccelerate = 0;
        }

        SZ.Init(sv.multicast, sv.multicast_buf, sv.multicast_buf.length);

        sv.name = mapName;

        // leave slots at start for clients only
        for (int i = 0; i < SV_MAIN.maxclients.value; i++) {
            // needs to reconnect
            if (svs.clients[i].state == ClientStates.CS_SPAWNED)
                svs.clients[i].state = ClientStates.CS_CONNECTED;
            svs.clients[i].lastframe = -1;
        }

        sv.time = 1000;

        sv.name = mapName;
        sv.configstrings[Defines.CS_NAME] = mapName;

        int checksum = 0;
        int iw[] = { checksum };

        if (serverstate != ServerStates.SS_GAME) {
            sv.models[1] = cm.CM_LoadMap("", false, iw); // no real map
        } else {
            sv.configstrings[Defines.CS_MODELS + 1] = "maps/" + mapName + ".bsp";
            sv.models[1] = cm.CM_LoadMap(sv.configstrings[Defines.CS_MODELS + 1], false, iw);
        }
        checksum = iw[0];
        sv.configstrings[Defines.CS_MAPCHECKSUM] = "" + checksum;


        // clear physics interaction links

        SV_WORLD.SV_ClearWorld(this);

        for (int i = 1; i < cm.CM_NumInlineModels(); i++) {
            sv.configstrings[Defines.CS_MODELS + 1 + i] = "*" + i;

            // copy references
            sv.models[i + 1] = cm.InlineModel(sv.configstrings[Defines.CS_MODELS + 1 + i]);
        }


        // spawn the rest of the entities on the map

        // precache and static commands can be issued during
        // map initialization

        sv.state = ServerStates.SS_LOADING;
        Globals.server_state = sv.state;

        // load and spawn all other entities
        gameExports.SpawnEntities(sv.name, cm.CM_EntityString(), spawnpoint);

        // run two frames to allow everything to settle
        gameExports.G_RunFrame();
        gameExports.G_RunFrame();

        // all precaches are complete
        sv.state = serverstate;
        Globals.server_state = sv.state;

        // create a baseline for more efficient communications
        SV_CreateBaseline();

        // check for a savegame
        SV_CheckForSavegame(sv);

        // set serverinfo variable
        Cvar.getInstance().FullSet("mapname", sv.name, Defines.CVAR_SERVERINFO | Defines.CVAR_NOSET);
    }

    /**
     * If a packet has not been received from a client for timeout.value
     * seconds, drop the conneciton. Server frames are used instead of realtime
     * to avoid dropping the local client while debugging.
     *
     * When a client is normally dropped, the client_t goes into a zombie state
     * for a few seconds to make sure any final reliable message gets resent if
     * necessary.
     */
    void SV_CheckTimeouts() {
        int droppoint = (int) (svs.realtime - 1000 * SV_MAIN.timeout.value);
        int zombiepoint = (int) (svs.realtime - 1000 * SV_MAIN.zombietime.value);

        for (int i = 0; i < SV_MAIN.maxclients.value; i++) {
            client_t cl = svs.clients[i];
            // message times may be wrong across a changelevel
            if (cl.lastmessage > svs.realtime)
                cl.lastmessage = svs.realtime;

            if (cl.state == ClientStates.CS_ZOMBIE && cl.lastmessage < zombiepoint) {
                cl.state = ClientStates.CS_FREE; // can now be reused
                continue;
            }
            if ((cl.state == ClientStates.CS_CONNECTED || cl.state == ClientStates.CS_SPAWNED)
                    && cl.lastmessage < droppoint) {
                SV_SEND.SV_BroadcastPrintf(Defines.PRINT_HIGH, cl.name
                        + " timed out\n");
                SV_DropClient(cl);
                cl.state = ClientStates.CS_FREE; // don't bother with zombie state
            }
        }
    }

    /**
     * Called when the player is totally leaving the server, either willingly or
     * unwillingly. This is NOT called if the entire server is quiting or
     * crashing.
     */
    void SV_DropClient(client_t client) {
        // add the disconnect
        MSG.WriteByte(client.netchan.message, NetworkCommands.svc_disconnect);

        if (client.state == ClientStates.CS_SPAWNED) {
            // call the prog function for removing a client
            // this will remove the body, among other things
            gameExports.ClientDisconnect(client.edict);
        }

        if (client.download != null) {
            client.download = null;
        }

        client.state = ClientStates.CS_ZOMBIE; // become free in a few seconds
        client.name = "";
    }

    /**
     * Updates the cl.ping variables.
     */
    void SV_CalcPings() {

        for (int i = 0; i < SV_MAIN.maxclients.value; i++) {
            client_t cl = svs.clients[i];
            if (cl.state != ClientStates.CS_SPAWNED)
                continue;

            int total = 0;
            int count = 0;
            for (int j = 0; j < Defines.LATENCY_COUNTS; j++) {
                if (cl.frame_latency[j] > 0) {
                    count++;
                    total += cl.frame_latency[j];
                }
            }
            if (0 == count)
                cl.ping = 0;
            else
                cl.ping = total / count;

            // let the jake2.game dll know about the ping
            cl.edict.getClient().setPing(cl.ping);
        }
    }

    /**
     * Every few frames, gives all clients an allotment of milliseconds for
     * their command moves. If they exceed it, assume cheating.
     */
    void SV_GiveMsec() {

        if ((sv.framenum & 15) != 0)
            return;

        for (int i = 0; i < SV_MAIN.maxclients.value; i++) {
            client_t cl = svs.clients[i];
            if (cl.state == ClientStates.CS_FREE)
                continue;

            cl.commandMsec = 1800; // 1600 + some slop
        }
    }

    /**
     * SV_RunGameFrame.
     */
    void SV_RunGameFrame() {

        // we always need to bump framenum, even if we
        // don't run the world, otherwise the delta
        // compression can get confused when a client
        // has the "current" frame
        sv.framenum++;
        sv.time = sv.framenum * 100;

        // don't run if paused
        if (0 == SV_MAIN.sv_paused.value || SV_MAIN.maxclients.value > 1) {
            gameExports.G_RunFrame();

            // never get more than one tic behind
            if (sv.time < svs.realtime) {
                if (SV_MAIN.sv_showclamp.value != 0)
                    Com.Printf("sv highclamp\n");
                svs.realtime = sv.time;
            }
        }

    }

    /**
     * Reads packets from the network or loopback.
     */
    void SV_ReadPackets() {

        while (NET.GetPacket(Defines.NS_SERVER, Globals.net_from,
                Globals.net_message)) {

            // check for connectionless packet (0xffffffff) first
            if ((Globals.net_message.data[0] == -1)
                    && (Globals.net_message.data[1] == -1)
                    && (Globals.net_message.data[2] == -1)
                    && (Globals.net_message.data[3] == -1)) {
                SV_ConnectionlessPacket();
                continue;
            }

            // read the qport out of the message so we can fix up
            // stupid address translating routers
            MSG.BeginReading(Globals.net_message);
            MSG.ReadLong(Globals.net_message); // sequence number
            MSG.ReadLong(Globals.net_message); // sequence number
            int qport = MSG.ReadShort(Globals.net_message) & 0xffff;

            // check for packets from connected clients
            for (int i = 0; i < SV_MAIN.maxclients.value; i++) {
                client_t cl = svs.clients[i];
                if (cl.state == ClientStates.CS_FREE)
                    continue;
                if (!NET.CompareBaseAdr(Globals.net_from,
                        cl.netchan.remote_address))
                    continue;
                if (cl.netchan.qport != qport)
                    continue;
                if (cl.netchan.remote_address.port != Globals.net_from.port) {
                    Com.Printf("SV_ReadPackets: fixing up a translated port\n");
                    cl.netchan.remote_address.port = Globals.net_from.port;
                }

                if (Netchan.Process(cl.netchan, Globals.net_message)) {
                    // this is a valid, sequenced packet, so process it
                    if (cl.state != ClientStates.CS_ZOMBIE) {
                        cl.lastmessage = svs.realtime; // don't timeout
                        SV_ExecuteClientMessage(cl);
                    }
                }
                break;
            }

        }
    }

    /*
     * =================== SV_ExecuteClientMessage
     *
     * The current net_message is parsed for the given client
     * ===================
     */
    void SV_ExecuteClientMessage(client_t cl) {

        usercmd_t oldest = new usercmd_t();
        usercmd_t oldcmd = new usercmd_t();
        usercmd_t newcmd = new usercmd_t();

        SV_MAIN.sv_client = cl;
        SV_USER.sv_player = SV_MAIN.sv_client.edict;

        // only allow one move command
        boolean move_issued = false;
        int stringCmdCount = 0;

        while (true) {
            if (Globals.net_message.readcount > Globals.net_message.cursize) {
                Com.Printf("SV_ReadClientMessage: bad read:\n");
                Com.Printf(Lib.hexDump(Globals.net_message.data, 32, false));
                SV_DropClient(cl);
                return;
            }

            ClientCommands c = ClientCommands.fromInt(MSG.ReadByte(Globals.net_message));
            if (c == ClientCommands.CLC_BAD)
                break;

            String s;
            usercmd_t nullcmd;
            int checksum;
            int calculatedChecksum;
            int checksumIndex;
            int lastframe;
            switch (c) {
                default:
                    Com.Printf("SV_ReadClientMessage: unknown command char: " + c + "\n");
                    SV_DropClient(cl);
                    return;

                case CLC_NOP:
                    break;

                case CLC_USERINFO:
                    cl.userinfo = MSG.ReadString(Globals.net_message);
                    SV_MAIN.SV_UserinfoChanged(cl);
                    break;

                case CLC_MOVE:
                    if (move_issued)
                        return; // someone is trying to cheat...

                    move_issued = true;
                    checksumIndex = Globals.net_message.readcount;
                    checksum = MSG.ReadByte(Globals.net_message);
                    lastframe = MSG.ReadLong(Globals.net_message);

                    if (lastframe != cl.lastframe) {
                        cl.lastframe = lastframe;
                        if (cl.lastframe > 0) {
                            cl.frame_latency[cl.lastframe
                                    & (Defines.LATENCY_COUNTS - 1)] = SV_INIT.gameImports.svs.realtime
                                    - cl.frames[cl.lastframe & Defines.UPDATE_MASK].senttime;
                        }
                    }

                    //memset (nullcmd, 0, sizeof(nullcmd));
                    nullcmd = new usercmd_t();
                    MSG.ReadDeltaUsercmd(Globals.net_message, nullcmd, oldest);
                    MSG.ReadDeltaUsercmd(Globals.net_message, oldest, oldcmd);
                    MSG.ReadDeltaUsercmd(Globals.net_message, oldcmd, newcmd);

                    if (cl.state != ClientStates.CS_SPAWNED) {
                        cl.lastframe = -1;
                        break;
                    }

                    // if the checksum fails, ignore the rest of the packet

                    calculatedChecksum = CRC.BlockSequenceCRCByte(
                            Globals.net_message.data, checksumIndex + 1,
                            Globals.net_message.readcount - checksumIndex - 1,
                            cl.netchan.incoming_sequence);

                    if ((calculatedChecksum & 0xff) != checksum) {
                        Com.DPrintf("Failed command checksum for " + cl.name + " ("
                                + calculatedChecksum + " != " + checksum + ")/"
                                + cl.netchan.incoming_sequence + "\n");
                        return;
                    }

                    if (0 == SV_MAIN.sv_paused.value) {
                        int net_drop = cl.netchan.dropped;
                        if (net_drop < 20) {

                            //if (net_drop > 2)

                            //	Com.Printf ("drop %i\n", net_drop);
                            while (net_drop > 2) {
                                SV_ClientThink(cl, cl.lastcmd);

                                net_drop--;
                            }
                            if (net_drop > 1)
                                SV_ClientThink(cl, oldest);

                            if (net_drop > 0)
                                SV_ClientThink(cl, oldcmd);

                        }
                        SV_ClientThink(cl, newcmd);
                    }

                    // copy.
                    cl.lastcmd.set(newcmd);
                    break;

                case CLC_STRINGCMD:
                    s = MSG.ReadString(Globals.net_message);

                    // malicious users may try using too many string commands
                    if (++stringCmdCount < MAX_STRINGCMDS)
                        SV_ExecuteUserCommand(s);

                    if (cl.state == ClientStates.CS_ZOMBIE)
                        return; // disconnect command
                    break;
            }
        }
    }

    private void SV_ClientThink(client_t cl, usercmd_t cmd) {
        cl.commandMsec -= cmd.msec & 0xFF;

        if (cl.commandMsec < 0 && SV_MAIN.sv_enforcetime.value != 0) {
            Com.DPrintf("commandMsec underflow from " + cl.name + "\n");
            return;
        }

        gameExports.ClientThink(cl.edict, cmd);
    }

    private void SV_ExecuteUserCommand(String s) {

        Com.dprintln("SV_ExecuteUserCommand:" + s );

        List<String> args = Cmd.TokenizeString(s, true);
        if (args.isEmpty())
            return;

        SV_USER.sv_player = SV_MAIN.sv_client.edict;

        if (userCommands.containsKey(args.get(0))) {
            userCommands.get(args.get(0)).execute(args);
            return;
        }

        if (sv.state == ServerStates.SS_GAME)
            gameExports.ClientCommand(SV_USER.sv_player, args);
    }

}
