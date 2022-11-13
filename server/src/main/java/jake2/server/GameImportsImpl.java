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
import jake2.qcommon.filesystem.QuakeFile;
import jake2.qcommon.network.MulticastTypes;
import jake2.qcommon.network.NET;
import jake2.qcommon.network.messages.NetworkMessage;
import jake2.qcommon.network.messages.server.PrintCenterMessage;
import jake2.qcommon.network.messages.server.ServerMessage;
import jake2.qcommon.network.messages.server.StuffTextMessage;
import jake2.qcommon.util.Lib;
import jake2.qcommon.util.Vargs;

import java.io.IOException;
import java.util.Date;
import java.util.List;

import static jake2.qcommon.Defines.CS_MAPCHECKSUM;
import static jake2.qcommon.Defines.CS_MODELS;
import static jake2.qcommon.exec.Cmd.getArguments;
import static jake2.server.SV_CCMDS.SV_CopySaveGame;

/*
 Collection of functions provided by the main engine.
 Also serves as the holder of game state from the server side

 todo make singleton (same as game exports)
*/
public class GameImportsImpl implements GameImports {

    String name;
    final JakeServer serverMain;
    public GameExports gameExports;

    // local (instance) server state
    public server_t sv;

    public int realtime; // always increasing, no clamping, etc
    // todo: remove
    public int spawncount; // incremented each server start

    // hack for finishing game in coop mode
    public String firstmap = "";

    String mapcmd = ""; // ie: *intro.cin+base

    SV_WORLD world;

    private final Cvar localCvars;

    CM cm;

    SV_ENTS sv_ents;

    SV_GAME sv_game;

    final float[] origin_v = { 0, 0, 0 };

    public GameImportsImpl(JakeServer serverMain, ChangeMapInfo changeMapInfo) {
        this.serverMain = serverMain;
        this.name = "jake2_" + SV_MAIN.gameCounter++;

        spawncount = Lib.rand();

        localCvars = new Cvar();

        world = new SV_WORLD();

        cm = new CM();
        sv_ents = new SV_ENTS(this, serverMain.getClients().size() * Defines.UPDATE_BACKUP * 64);

        sv_game = new SV_GAME(this);

        // heartbeats will always be sent to the id master

        // create local server state
        sv = new server_t(changeMapInfo);

        int[] iw = { 0 };
        if (changeMapInfo.state == ServerStates.SS_GAME) {
            sv.configstrings[CS_MODELS + 1] = "maps/" + changeMapInfo.mapName + ".bsp";
            sv.models[1] = cm.CM_LoadMap(sv.configstrings[CS_MODELS + 1], false, iw);
        } else {
            sv.models[1] = cm.CM_LoadMap("", false, iw); // no real map
        }
        sv.configstrings[CS_MAPCHECKSUM] = "" + iw[0];

        for (int i = 1; i < cm.CM_NumInlineModels(); i++) {
            sv.configstrings[CS_MODELS + 1 + i] = "*" + i;

            // copy references
            sv.models[i + 1] = cm.InlineModel(sv.configstrings[CS_MODELS + 1 + i]);
        }



        // archive server state to be used in savegame
        mapcmd = changeMapInfo.levelString;


        if (changeMapInfo.isLoadgame) {
            SV_Read_Latched_Vars();
        }

        // clear physics interaction links
        // Q: is it required? the instance was just created
        SV_WORLD.SV_ClearWorld(this);

        SV_InitOperatorCommands();

    }

    /**
     * 	SV_ReadServerFile
     *
     * 	todo - move read game locals to another function
     */
    void SV_Read_Latched_Vars() {
        try {

            Com.DPrintf("SV_ReadServerFile()\n");

            QuakeFile f = new QuakeFile(FS.getWriteDir() + "/save/current/server_latched_cvars.ssv", "r");

            // read all CVAR_LATCH cvars
            // these will be things like coop, skill, deathmatch, etc
            while (true) {
                String name = f.readString();
                if (name == null)
                    break;
                String value = f.readString();

                Com.DPrintf("Set " + name + " = " + value + "\n");
                Cvar.getInstance().ForceSet(name, value);
            }

            f.close();

            // read game state
            this.gameExports.readGameLocals(FS.getWriteDir() + "/save/current/game.ssv");
        } catch (Exception e) {
            Com.Printf("Couldn't read file " + e.getMessage() + "\n");
        }
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
        Cmd.AddCommand("say", this::SV_ConSay_f, true);
        Cmd.AddCommand("save", this::SV_Savegame_f, true);
        Cmd.AddCommand("killserver", this::SV_KillServer_f, true);

        // ip filtering and stuff
        Cmd.AddCommand("sv", args -> {
            if (gameExports != null)
                gameExports.ServerCommand(args);
        }, true);
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
    public void cprintf(edict_t ent, int printlevel, String s) {
        sv_game.PF_cprintf(ent, printlevel, s);
    }

    @Override
    public void centerprintf(edict_t ent, String s) {
        // todo: check if s is not empty
        sv_game.PF_Unicast(ent.index, true, new PrintCenterMessage(s));
    }

    @Override
    public void sound(edict_t ent, int channel, int soundindex, float volume,
                      float attenuation, float timeofs) {
        sv_game.PF_StartSound(ent, channel, soundindex, volume, attenuation,
                timeofs);
    }

    @Override
    public void positioned_sound(float[] origin, edict_t ent, int channel,
                                 int soundinedex, float volume, float attenuation, float timeofs) {

        SV_SEND.SV_StartSound(origin, ent, channel, soundinedex, volume,
                attenuation, timeofs, this);
    }

    /*
     config strings hold all the index strings, the lightstyles,
     and misc data like the sky definition and cdtrack.
     All of the current configstrings are sent to clients when
     they connect, and changes are sent to all connected clients.
    */
    @Override
    public void configstring(int num, String string) {
        sv_game.PF_Configstring(num, string);
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
        return sv_game.SV_FindIndex(name, Defines.CS_MODELS, Defines.MAX_MODELS, true);
    }

    @Override
    public int soundindex(String name) {
        return sv_game.SV_FindIndex(name, Defines.CS_SOUNDS, Defines.MAX_SOUNDS, true);
    }

    @Override
    public int imageindex(String name) {
        return sv_game.SV_FindIndex(name, Defines.CS_IMAGES, Defines.MAX_IMAGES, true);
    }

    @Override
    public void setmodel(edict_t ent, String name) {
        sv_game.PF_setmodel(ent, name);
    }

    /* collision detection */
    @Override
    public trace_t trace(float[] start, float[] mins, float[] maxs,
                         float[] end, edict_t passent, int contentmask) {
        return SV_WORLD.SV_Trace(start, mins, maxs, end, passent, contentmask, this);
    }

    @Override
    public boolean inPHS(float[] p1, float[] p2) {
        return sv_game.PF_inPHS(p1, p2);
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

        if (cvar("deathmatch", "", 0).value != 0) {
            Com.Printf("Can't savegame in a deathmatch\n");
            return;
        }

        String saveGame = args.get(1);
        if ("current".equals(saveGame)) {
            Com.Printf("Can't save to 'current'\n");
            return;
        }

        // fixme: quite hacky way to get a player
        if (serverMain.getClients().size() == 1 && serverMain.getClients().get(0).edict.getClient().getPlayerState().stats[Defines.STAT_HEALTH] <= 0) {
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
        SV_WriteLevelFile();

        // save server state
        try {
            SV_WriteServerFile(false);
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
        if (args.size() != 2) {
            Com.Printf("Usage: kick <userid>\n");
            return;
        }
        client_t client = SV_SetPlayer(args);
        if (client == null)
            return;

        serverMain.SV_BroadcastPrintf(Defines.PRINT_HIGH, client.name + " was kicked\n", client.gameName);
        // print directly, because the dropped client won't get the
        // SV_BroadcastPrintf message
        SV_SEND.SV_ClientPrintf(client, Defines.PRINT_HIGH, "You were kicked from the game\n");
        SV_MAIN.SV_DropClient(client);
        client.lastmessage = realtime; // min case there is a funny zombie
    }
    /*
    ================
    SV_Status_f
    ================
    */
    private void SV_Status_f(List<String> args) {
        // todo: check
        if (serverMain.getClients().isEmpty()) {
            Com.Printf("No server running.\n");
            return;
        }
        Com.Printf("map              : " + sv.name + "\n");

        Com.Printf("num score ping name            lastmsg address               qport \n");
        Com.Printf("--- ----- ---- --------------- ------- --------------------- ------\n");

        int i = 0;
        for (client_t cl : serverMain.getClients()) {
            if (ClientStates.CS_FREE == cl.state)
                continue;

            Com.Printf("%3i ", new Vargs().add(i));
            Com.Printf("%5i ", new Vargs().add(cl.edict.getClient().getPlayerState().stats[Defines.STAT_FRAGS]));

            if (cl.state == ClientStates.CS_CONNECTED)
                Com.Printf("CNCT ");
            else if (cl.state == ClientStates.CS_ZOMBIE)
                Com.Printf("ZMBI ");
            else {
                int ping = cl.ping < 9999 ? cl.ping : 9999;
                Com.Printf("%4i ", new Vargs().add(ping));
            }

            Com.Printf("%s", new Vargs().add(cl.name));
            int l = 16 - cl.name.length();
            int j;
            for (j = 0; j < l; j++)
                Com.Printf(" ");

            Com.Printf("%7i ", new Vargs().add(realtime - cl.lastmessage));

            String s = cl.netchan.remote_address.toString();
            Com.Printf(s);
            l = 22 - s.length();
            for (j = 0; j < l; j++)
                Com.Printf(" ");

            Com.Printf("%5i", new Vargs().add(cl.netchan.qport));

            Com.Printf("\n");
            i++;
        }
        Com.Printf("\n");
    }
    /*
    ==================
    SV_ConSay_f
    ==================
    */
    private void SV_ConSay_f(List<String> args) {

        if (args.size() < 2)
            return;

        // char[1024];
        String text = "console: ";
        String p = getArguments(args);

        if (p.charAt(0) == '"') {
            p = p.substring(1, p.length() - 1);
        }

        text += p;

        for (client_t client: serverMain.getClients()) {
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
        client_t client = SV_SetPlayer(args);
        if (client == null)
            return;

        Com.Printf("userinfo\n");
        Com.Printf("--------\n");
        Info.Print(client.userinfo);

    }

    /*
    ===============
    SV_KillServer_f

    Kick everyone off, possibly in preparation for a new jake2.game

    ===============
    */
    private void SV_KillServer_f(List<String> args) {
        serverMain.SV_Shutdown("Server was killed.\n", false);
        NET.Config(false); // close network sockets
    }
    //===========================================================

    /**
     * Sets sv_client and sv_player to the player with idnum Cmd.Argv(1)
     */
    private client_t SV_SetPlayer(List<String> args) {

        if (args.size() < 2)
            return null;

        String idOrName = args.get(1);

        // numeric values are just slot numbers
        // fixme: player name cannot start with a number?
        if (idOrName.charAt(0) >= '0' && idOrName.charAt(0) <= '9') {
            int id = Lib.atoi(idOrName);
            if (id < 0 || id >= serverMain.getClients().size()) {
                Com.Printf("Bad client slot: " + id + "\n");
                return null;
            }

            final client_t result = serverMain.getClients().get(id);
            if (ClientStates.CS_FREE == result.state) {
                Com.Printf("Client " + id + " is not active\n");
                return null;
            }
            return result;
        }

        // check for a name match
        for (client_t cl: serverMain.getClients()) {
            if (ClientStates.CS_FREE == cl.state)
                continue;
            if (idOrName.equals(cl.name)) {
                return cl;
            }
        }

        Com.Printf("Userid " + idOrName + " is not on the server\n");
        return null;
    }

    /**
     * SV_RunGameFrame.
     */
    void SV_RunGameFrame(long fixedtime) {

        // we always need to bump framenum, even if we
        // don't run the world, otherwise the delta
        // compression can get confused when a client
        // has the "current" frame
        sv.framenum++;

        if (SV_MAIN.sv_paused != null && 1 == SV_MAIN.sv_paused.value && serverMain.getClients().size() <= 1) {
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
        if (sv.state == ServerStates.SS_DEAD)
            return;

        for (client_t client : serverMain.getClients()) {
            if (client.state == ClientStates.CS_FREE || client.state == ClientStates.CS_ZOMBIE)
                continue;
            client.netchan.reliablePending.add(new StuffTextMessage(s));
        }
    }

    void SV_WriteLevelFile() {

        Com.DPrintf("SV_WriteLevelFile()\n");

        String name = FS.getWriteDir() + "/save/current/" + sv.name + ".sv2";

        try {
            QuakeFile f = new QuakeFile(name, "rw");

            for (int i = 0; i < Defines.MAX_CONFIGSTRINGS; i++)
                f.writeString(sv.configstrings[i]);

            cm.CM_WritePortalState(f);
            f.close();
        }
        catch (Exception e) {
            Com.Printf("Failed to open " + name + "\n");
            e.printStackTrace();
        }

        name = FS.getWriteDir() + "/save/current/" + sv.name + ".sav";
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
            QuakeFile f = new QuakeFile(FS.getWriteDir() + "/save/current/server_mapcmd.ssv", "rw");

            final String comment;
            if (autosave) {
                comment = "Autosave in " + sv.configstrings[Defines.CS_NAME];
            } else {
                comment = new Date() + " " + sv.configstrings[Defines.CS_NAME];
            }

            f.writeString(comment);
            f.writeString(mapcmd);
            f.close();


            QuakeFile cvarsFile = new QuakeFile(FS.getWriteDir() + "/save/current/server_latched_cvars.ssv", "rw");

            // write all CVAR_LATCH cvars
            // these will be things like coop, skill, deathmatch, etc
            Cvar.getInstance().eachCvarByFlags(Defines.CVAR_LATCH, var -> {
                try {
                    cvarsFile.writeString(var.name);
                    cvarsFile.writeString(var.string);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

            // rst: for termination.
            cvarsFile.writeString(null);
            cvarsFile.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // write game state
        gameExports.WriteGame(FS.getWriteDir() + "/save/current/game.ssv", autosave);
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
        for (client_t client : serverMain.getClientsForInstance(name)) {
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
        sv_game.PF_Unicast(index, reliable, msg);
    }
}
