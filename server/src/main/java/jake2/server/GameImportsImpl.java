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
import static jake2.server.SV_INIT.gameImports;

/*
 Collection of functions provided by the main engine.
 Also serves as the holder of game state from the server side

 todo make singleton (same as game exports)
*/
public class GameImportsImpl implements GameImports {
    public GameExports gameExports;

    // persistent server state
    public server_static_t svs;

    // local (instance) server state
    public server_t sv;

    // hack for finishing game in coop mode
    public String firstmap = "";

    SV_WORLD world;

    private final Cvar localCvars;

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
        SV_GAME.PF_Configstring(num, string);
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
        return SV_GAME.PF_inPHS(p1, p2);
    }

    @Override
    public void SetAreaPortalState(int portalnum, boolean open) {
        CM.CM_SetAreaPortalState(portalnum, open);
    }

    @Override
    public boolean AreasConnected(int area1, int area2) {
        return CM.CM_AreasConnected(area1, area2);
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
        SV_SEND.SV_Multicast(origin, to);
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


    /*
==============
SV_Savegame_f

==============
*/
    private void SV_Savegame_f(List<String> args) {

        if (sv.state != ServerStates.SS_GAME) {
            Com.Printf("You must be in a game to save.\n");
            return;
        }

        if (args.size() != 2) {
            Com.Printf("USAGE: save <directory>\n");
            return;
        }

        if (gameImports.cvar("deathmatch", "", 0).value != 0) {
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
    //===============================================================
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
        SV_MAIN.SV_DropClient(SV_MAIN.sv_client);
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

        gameImports.svs.last_heartbeat = -9999999;
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

            SV_MAIN.sv_client = gameImports.svs.clients[id];
            SV_USER.sv_player = SV_MAIN.sv_client.edict;
            if (ClientStates.CS_FREE == SV_MAIN.sv_client.state) {
                Com.Printf("Client " + id + " is not active\n");
                return false;
            }
            return true;
        }

        // check for a name match
        for (int i = 0; i < SV_MAIN.maxclients.value; i++) {
            client_t cl = gameImports.svs.clients[i];
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

}
