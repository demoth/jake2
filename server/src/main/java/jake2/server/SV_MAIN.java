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

// Created on 13.01.2004 by RST.
// $Id: SV_MAIN.java,v 1.16 2006-01-20 22:44:07 salomo Exp $
package jake2.server;

import jake2.qcommon.*;
import jake2.qcommon.exec.Cmd;
import jake2.qcommon.exec.Cvar;
import jake2.qcommon.exec.cvar_t;
import jake2.qcommon.network.NET;
import jake2.qcommon.network.Netchan;
import jake2.qcommon.network.NetworkCommands;
import jake2.qcommon.network.netadr_t;
import jake2.qcommon.sys.Sys;
import jake2.qcommon.util.Lib;

import java.util.List;

public class SV_MAIN {

	/** Addess of group servers.*/ 
    static netadr_t master_adr[] = new netadr_t[Defines.MAX_MASTERS];

    static {
        for (int i = 0; i < Defines.MAX_MASTERS; i++) {
            master_adr[i] = new netadr_t();
        }
    }

    static cvar_t sv_paused;

    static cvar_t sv_timedemo;

    static cvar_t sv_enforcetime;

    static cvar_t timeout; // seconds without any message

    static cvar_t zombietime; // seconds to sink messages after
                                     // disconnect

    static cvar_t allow_download;

    static cvar_t allow_download_players;

    static cvar_t allow_download_models;

    static cvar_t allow_download_sounds;

    static cvar_t allow_download_maps;

    static cvar_t sv_airaccelerate;

    static cvar_t sv_showclamp;

    static cvar_t hostname;

    static cvar_t public_server; // should heartbeats be sent

    static cvar_t sv_reconnect_limit; // minimum seconds between connect
                                             // messages

    /**
     * Send a message to the master every few minutes to let it know we are
     * alive, and log information.
     */
    private static final int HEARTBEAT_SECONDS = 300;

    /* ==============================================================================
     * 
     * CONNECTIONLESS COMMANDS
     * 
     * ==============================================================================*/
     
    /**
     * Builds the string that is sent as heartbeats and status replies.
     */
    private static String SV_StatusString() {
        String player;
        String status = "";
        int i;
        client_t cl;
        int statusLength;
        int playerLength;

        status = Cvar.getInstance().Serverinfo() + "\n";

        for (i = 0; i < SV_INIT.gameImports.maxclients.value; i++) {
            cl = SV_INIT.gameImports.svs.clients[i];
            if (cl.state == ClientStates.CS_CONNECTED
                    || cl.state == ClientStates.CS_SPAWNED) {
                player = "" + cl.edict.getClient().getPlayerState().stats[Defines.STAT_FRAGS]
                        + " " + cl.ping + "\"" + cl.name + "\"\n";

                playerLength = player.length();
                statusLength = status.length();

                if (statusLength + playerLength >= 1024)
                    break; // can't hold any more

                status += player;
            }
        }

        return status;
    }

    /**
     * Responds with all the info that qplug or qspy can see
     */
    private static void SVC_Status() {
        Netchan.OutOfBandPrint(Defines.NS_SERVER, Globals.net_from, "print\n"
                + SV_StatusString());
    }

    /**
     *  SVC_Ack
     */
    private static void SVC_Ack() {
        Com.Printf("Ping acknowledge from " + NET.AdrToString(Globals.net_from)
                + "\n");
    }

    /**
     * SVC_Info, responds with short info for broadcast scans The second parameter should
     * be the current protocol version number.
     */
    private static void SVC_Info(List<String> args) {

        if (SV_INIT.gameImports.maxclients.value == 1)
            return; // ignore in single player

        int version = args.size() < 2 ? 0 : Lib.atoi(args.get(1));

        String string;
        if (version != Defines.PROTOCOL_VERSION)
            string = SV_MAIN.hostname.string + ": wrong version\n";
        else {
            int count = 0;
            for (int i = 0; i < SV_INIT.gameImports.maxclients.value; i++)
                if (SV_INIT.gameImports.svs.clients[i].state == ClientStates.CS_CONNECTED ||
                        SV_INIT.gameImports.svs.clients[i].state == ClientStates.CS_SPAWNED)
                    count++;

            string = SV_MAIN.hostname.string + " " + SV_INIT.gameImports.sv.name + " "
                    + count + "/" + (int) SV_INIT.gameImports.maxclients.value + "\n";
        }

        Netchan.OutOfBandPrint(Defines.NS_SERVER, Globals.net_from, "info\n"
                + string);
    }

    /**
     * SVC_Ping, Just responds with an acknowledgement.
     */
    private static void SVC_Ping() {
        Netchan.OutOfBandPrint(Defines.NS_SERVER, Globals.net_from, "ack");
    }

    /** 
     * Returns a challenge number that can be used in a subsequent
     * client_connect command. We do this to prevent denial of service attacks
     * that flood the server with invalid connection IPs. With a challenge, they
     * must give a valid IP address.
     */
    private static void SVC_GetChallenge() {
        int i;
        int oldest;
        int oldestTime;

        oldest = 0;
        oldestTime = 0x7fffffff;

        // see if we already have a challenge for this ip
        for (i = 0; i < Defines.MAX_CHALLENGES; i++) {
            if (NET.CompareBaseAdr(Globals.net_from,
                    SV_INIT.gameImports.svs.challenges[i].adr))
                break;
            if (SV_INIT.gameImports.svs.challenges[i].time < oldestTime) {
                oldestTime = SV_INIT.gameImports.svs.challenges[i].time;
                oldest = i;
            }
        }

        if (i == Defines.MAX_CHALLENGES) {
            // overwrite the oldest
            SV_INIT.gameImports.svs.challenges[oldest].challenge = Lib.rand() & 0x7fff;
            SV_INIT.gameImports.svs.challenges[oldest].adr = Globals.net_from;
            SV_INIT.gameImports.svs.challenges[oldest].time = (int) Globals.curtime;
            i = oldest;
        }

        // send it back
        Netchan.OutOfBandPrint(Defines.NS_SERVER, Globals.net_from,
                "challenge " + SV_INIT.gameImports.svs.challenges[i].challenge);
    }

    /**
     * A connection request that did not come from the master.
     */
    private static void SVC_DirectConnect(List<String> args) {

        netadr_t adr = Globals.net_from;

        Com.DPrintf("SVC_DirectConnect ()\n");

        int version = args.size() >= 2 ? Lib.atoi(args.get(1)) : 0;

        if (version != Defines.PROTOCOL_VERSION) {
            Netchan.OutOfBandPrint(Defines.NS_SERVER, adr,
                    "print\nServer is version " + Globals.VERSION + "\n");
            Com.DPrintf("    rejected connect from version " + version + "\n");
            return;
        }

        int qport = args.size() >= 3 ? Lib.atoi(args.get(2)) : 0;
        int challenge = args.size() >= 4 ? Lib.atoi(args.get(3)) : 0;
        String userinfo = args.size() >= 5 ? args.get(4) : "";

        // force the IP key/value pair so the game can filter based on ip
        userinfo = Info.Info_SetValueForKey(userinfo, "ip", NET.AdrToString(Globals.net_from));

        // attractloop servers are ONLY for local clients
        if (SV_INIT.gameImports.sv.isDemo) {
            if (!NET.IsLocalAddress(adr)) {
                Com.Printf("Remote connect in attract loop.  Ignored.\n");
                Netchan.OutOfBandPrint(Defines.NS_SERVER, adr,
                        "print\nConnection refused.\n");
                return;
            }
        }

        // see if the challenge is valid
        int i;
        if (!NET.IsLocalAddress(adr)) {
            for (i = 0; i < Defines.MAX_CHALLENGES; i++) {
                if (NET.CompareBaseAdr(Globals.net_from,
                        SV_INIT.gameImports.svs.challenges[i].adr)) {
                    if (challenge == SV_INIT.gameImports.svs.challenges[i].challenge)
                        break; // good
                    Netchan.OutOfBandPrint(Defines.NS_SERVER, adr,
                            "print\nBad challenge.\n");
                    return;
                }
            }
            if (i == Defines.MAX_CHALLENGES) {
                Netchan.OutOfBandPrint(Defines.NS_SERVER, adr,
                        "print\nNo challenge for address.\n");
                return;
            }
        }

        // if there is already a slot for this ip, reuse it
        client_t cl;
        for (i = 0; i < SV_INIT.gameImports.maxclients.value; i++) {
            cl = SV_INIT.gameImports.svs.clients[i];

            if (cl.state == ClientStates.CS_FREE)
                continue;
            if (NET.CompareBaseAdr(adr, cl.netchan.remote_address)
                    && (cl.netchan.qport == qport || adr.port == cl.netchan.remote_address.port)) {
                if (!NET.IsLocalAddress(adr)
                        && (SV_INIT.gameImports.svs.realtime - cl.lastconnect) < ((int) SV_MAIN.sv_reconnect_limit.value * 1000)) {
                    Com.DPrintf(NET.AdrToString(adr)
                            + ":reconnect rejected : too soon\n");
                    return;
                }
                Com.Printf(NET.AdrToString(adr) + ":reconnect\n");

                gotnewcl(i, challenge, userinfo, adr, qport);
                return;
            }
        }

        // find a client slot
        //newcl = null;
        int index = -1;
        for (i = 0; i < SV_INIT.gameImports.maxclients.value; i++) {
            cl = SV_INIT.gameImports.svs.clients[i];
            if (cl.state == ClientStates.CS_FREE) {
                index = i;
                break;
            }
        }
        if (index == -1) {
            Netchan.OutOfBandPrint(Defines.NS_SERVER, adr,
                    "print\nServer is full.\n");
            Com.DPrintf("Rejected a connection.\n");
            return;
        }
        gotnewcl(index, challenge, userinfo, adr, qport);
    }

    /**
     * Initializes player structures after successfull connection.
     */
    private static void gotnewcl(int i, int challenge, String userinfo,
                                 netadr_t adr, int qport) {
        // build a new connection
        // accept the new client
        // this is the only place a client_t is ever initialized

        SV_INIT.gameImports.sv_client = SV_INIT.gameImports.svs.clients[i];
        
        int edictnum = i + 1;
        
        edict_t ent = SV_INIT.gameImports.gameExports.getEdict(edictnum);
        SV_INIT.gameImports.svs.clients[i].edict = ent;
        
        // save challenge for checksumming
        SV_INIT.gameImports.svs.clients[i].challenge = challenge;
        
        

        // get the game a chance to reject this connection or modify the
        // userinfo
        if (!(SV_INIT.gameImports.gameExports.ClientConnect(ent, userinfo))) {
            if (Info.Info_ValueForKey(userinfo, "rejmsg") != null)
                Netchan.OutOfBandPrint(Defines.NS_SERVER, adr, "print\n"
                        + Info.Info_ValueForKey(userinfo, "rejmsg")
                        + "\nConnection refused.\n");
            else
                Netchan.OutOfBandPrint(Defines.NS_SERVER, adr,
                        "print\nConnection refused.\n");
            Com.DPrintf("Game rejected a connection.\n");
            return;
        }

        // parse some info from the info strings
        SV_INIT.gameImports.svs.clients[i].userinfo = userinfo;
        SV_UserinfoChanged(SV_INIT.gameImports.svs.clients[i]);

        // send the connect packet to the client
        Netchan.OutOfBandPrint(Defines.NS_SERVER, adr, "client_connect");

        Netchan.Setup(Defines.NS_SERVER, SV_INIT.gameImports.svs.clients[i].netchan, adr, qport);

        SV_INIT.gameImports.svs.clients[i].state = ClientStates.CS_CONNECTED;

        SZ.Init(SV_INIT.gameImports.svs.clients[i].datagram,
                SV_INIT.gameImports.svs.clients[i].datagram_buf,
                SV_INIT.gameImports.svs.clients[i].datagram_buf.length);
        
        SV_INIT.gameImports.svs.clients[i].datagram.allowoverflow = true;
        SV_INIT.gameImports.svs.clients[i].lastmessage = SV_INIT.gameImports.svs.realtime; // don't timeout
        SV_INIT.gameImports.svs.clients[i].lastconnect = SV_INIT.gameImports.svs.realtime;
        Com.DPrintf("new client added.\n");
    }

    /** 
     * Checks if the rcon password is corect.
     */
    private static boolean Rcon_Validate(List<String> args) {
        String rconPassword = Cvar.getInstance().Get("rcon_password", "", 0).string;
        if (rconPassword.isEmpty())
            return false;

        return args.size() >= 2 && args.get(1).equals(rconPassword);
    }

    /**
     * A client issued an rcon command. Shift down the remaining args Redirect
     * all printfs from the server to the client.
     */
    private static void SVC_RemoteCommand(List<String> args) {

        boolean rconIsValid = Rcon_Validate(args);

        String msg = Lib.CtoJava(Globals.net_message.data, 4, 1024);

        if (rconIsValid) {
            Com.Printf("Rcon from " + NET.AdrToString(Globals.net_from) + ":\n"
                    + msg + "\n");
        } else {
            Com.Printf("Bad rcon from " + NET.AdrToString(Globals.net_from)
                    + ":\n" + msg + "\n");
        }

        Com.BeginRedirect(Defines.RD_PACKET, Defines.SV_OUTPUTBUF_LENGTH,
                (target, buffer) -> SV_SEND.SV_FlushRedirect(target, Lib.stringToBytes(buffer.toString())));

        if (rconIsValid) {
            Cmd.ExecuteString(Cmd.getArguments(args, 2));
        } else {
            // redirected to client
            Com.Printf("Bad rcon_password.\n");
        }

        Com.EndRedirect();
    }

    /**
     * A connectionless packet has four leading 0xff characters to distinguish
     * it from a game channel. Clients that are in the game can still send
     * connectionless packets. It is used also by rcon commands.
     */
    static void SV_ConnectionlessPacket() {

        MSG.BeginReading(Globals.net_message);
        MSG.ReadLong(Globals.net_message); // skip the -1 marker

        String messageLine = MSG.ReadStringLine(Globals.net_message);

        List<String> args = Cmd.TokenizeString(messageLine, false);

        if (args.isEmpty()) {
            Com.Printf("Received empty packet!: " + messageLine);
            return;
        }

        String cmd = args.get(0);
        
        //for debugging purposes 
        //Com.Printf("Packet " + NET.AdrToString(Netchan.net_from) + " : " + c + "\n");
        //Com.Printf(Lib.hexDump(net_message.data, 64, false) + "\n");

        switch (cmd) {
            case "ping":
                SVC_Ping();
                break;
            case "ack":
                SVC_Ack();
                break;
            case "status":
                SVC_Status();
                break;
            case "info":
                SVC_Info(args);
                break;
            case "getchallenge":
                SVC_GetChallenge();
                break;
            case "connect":
                SVC_DirectConnect(args);
                break;
            case "rcon":
                SVC_RemoteCommand(args);
                break;
            default:
                Com.Printf("bad connectionless packet from "
                        + NET.AdrToString(Globals.net_from) + "\n");
                Com.Printf("[" + messageLine + "]\n");
                Com.Printf("" + Lib.hexDump(Globals.net_message.data, 128, false));
                break;
        }
    }

    /**
     * SV_PrepWorldFrame
     * 
     * This has to be done before the world logic, because player processing
     * happens outside RunWorldFrame.
     */
    private static void clearEntityStateEvents(GameExports gameExports) {
        for (int i = 0; i < gameExports.getNumEdicts(); i++) {
            // events only last for a single message
            gameExports.getEdict(i).s.event = 0;
        }

    }

    /**
     * SV_Frame.
     */
    public static void SV_Frame(long msec) {
        Globals.time_before_game = Globals.time_after_game = 0;

        // if server is not active, do nothing
        // like when connected to another server
        final GameImportsImpl serverInstance = SV_INIT.gameImports;
        if (serverInstance == null)
            return;
        if (!serverInstance.svs.initialized) {
            Sys.Error("!serverInstance.svs.initialized");
        }

        serverInstance.svs.realtime += msec;

        // keep the random time dependent
        Lib.rand();

        // check timeouts
        serverInstance.SV_CheckTimeouts();

        // get packets from clients
        serverInstance.SV_ReadPackets();

        //if (Game.g_edicts[1] !=null)
        //	Com.p("player at:" + Lib.vtofsbeaty(Game.g_edicts[1].s.origin ));

        // move autonomous things around if enough time has passed
        if (0 == SV_MAIN.sv_timedemo.value && serverInstance.svs.realtime < serverInstance.sv.time) {
            // never let the time get too far off
            if (serverInstance.sv.time - serverInstance.svs.realtime > 100) {
                if (SV_MAIN.sv_showclamp.value != 0)
                    Com.Printf("sv lowclamp\n");
                serverInstance.svs.realtime = serverInstance.sv.time - 100;
            }
            NET.Sleep(serverInstance.sv.time - serverInstance.svs.realtime);
            return;
        }

        // update ping based on the last known frame from all clients
        serverInstance.SV_CalcPings();

        // give the clients some timeslices
        serverInstance.SV_GiveMsec();

        // let everything in the world think and move
        serverInstance.SV_RunGameFrame();

        // send messages back to the clients that had packets read this frame
        SV_SEND.SV_SendClientMessages();

        // save the entire world state if recording a serverdemo
        SV_ENTS.SV_RecordDemoMessage();

        // send a heartbeat to the master if needed
        Master_Heartbeat();

        // clear teleport flags, etc for next frame
        clearEntityStateEvents(serverInstance.gameExports);

    }

    private static void Master_Heartbeat() {
        String string;
        int i;

        // pgm post3.19 change, cvar pointer not validated before dereferencing
        if (Globals.dedicated == null || 0 == Globals.dedicated.value)
            return; // only dedicated servers send heartbeats

        // pgm post3.19 change, cvar pointer not validated before dereferencing
        if (null == SV_MAIN.public_server || 0 == SV_MAIN.public_server.value)
            return; // a private dedicated game

        // check for time wraparound
        if (SV_INIT.gameImports.svs.last_heartbeat > SV_INIT.gameImports.svs.realtime)
            SV_INIT.gameImports.svs.last_heartbeat = SV_INIT.gameImports.svs.realtime;

        if (SV_INIT.gameImports.svs.realtime - SV_INIT.gameImports.svs.last_heartbeat < SV_MAIN.HEARTBEAT_SECONDS * 1000)
            return; // not time to send yet

        SV_INIT.gameImports.svs.last_heartbeat = SV_INIT.gameImports.svs.realtime;

        // send the same string that we would give for a status OOB command
        string = SV_StatusString();

        // send to group master
        for (i = 0; i < Defines.MAX_MASTERS; i++)
            if (SV_MAIN.master_adr[i].port != 0) {
                Com.Printf("Sending heartbeat to "
                        + NET.AdrToString(SV_MAIN.master_adr[i]) + "\n");
                Netchan.OutOfBandPrint(Defines.NS_SERVER,
                        SV_MAIN.master_adr[i], "heartbeat\n" + string);
            }
    }
    
    /**
     * Master_Shutdown, Informs all masters that this server is going down.
     */
    private static void Master_Shutdown() {
        int i;

        // pgm post3.19 change, cvar pointer not validated before dereferencing
        if (null == Globals.dedicated || 0 == Globals.dedicated.value)
            return; // only dedicated servers send heartbeats

        // pgm post3.19 change, cvar pointer not validated before dereferencing
        if (null == SV_MAIN.public_server || 0 == SV_MAIN.public_server.value)
            return; // a private dedicated game

        // send to group master
        for (i = 0; i < Defines.MAX_MASTERS; i++)
            if (SV_MAIN.master_adr[i].port != 0) {
                if (i > 0)
                    Com.Printf("Sending heartbeat to "
                            + NET.AdrToString(SV_MAIN.master_adr[i]) + "\n");
                Netchan.OutOfBandPrint(Defines.NS_SERVER,
                        SV_MAIN.master_adr[i], "shutdown");
            }
    }

    /**
     * Pull specific info from a newly changed userinfo string into a more C
     * freindly form.
     */
    static void SV_UserinfoChanged(client_t cl) {
        String val;
        int i;

        // call prog code to allow overrides
        SV_INIT.gameImports.gameExports.ClientUserinfoChanged(cl.edict, cl.userinfo);

        // name for C code
        cl.name = Info.Info_ValueForKey(cl.userinfo, "name");

        // mask off high bit
        //TODO: masking for german umlaute
        //for (i=0 ; i<sizeof(cl.name) ; i++)
        //	cl.name[i] &= 127;

        // rate command
        val = Info.Info_ValueForKey(cl.userinfo, "rate");
        if (val.length() > 0) {
            i = Lib.atoi(val);
            cl.rate = i;
            if (cl.rate < 100)
                cl.rate = 100;
            if (cl.rate > 15000)
                cl.rate = 15000;
        } else
            cl.rate = 5000;

        // msg command
        val = Info.Info_ValueForKey(cl.userinfo, "msg");
        if (val.length() > 0) {
            cl.messagelevel = Lib.atoi(val);
        }

    }

    /**
     * Used by SV_Shutdown to send a final message to all connected clients
     * before the server goes down. The messages are sent immediately, not just
     * stuck on the outgoing message list, because the server is going to
     * totally exit after returning from this function.
     */
    private static void SV_FinalMessage(String message, boolean reconnect) {
        int i;
        client_t cl;

        Globals.net_message.clear();
        MSG.WriteByte(Globals.net_message, NetworkCommands.svc_print);
        MSG.WriteByte(Globals.net_message, Defines.PRINT_HIGH);
        MSG.WriteString(Globals.net_message, message);

        if (reconnect)
            MSG.WriteByte(Globals.net_message, NetworkCommands.svc_reconnect);
        else
            MSG.WriteByte(Globals.net_message, NetworkCommands.svc_disconnect);

        // send it twice
        // stagger the packets to crutch operating system limited buffers

        for (i = 0; i < SV_INIT.gameImports.svs.clients.length; i++) {
            cl = SV_INIT.gameImports.svs.clients[i];
            if (cl.state == ClientStates.CS_CONNECTED || cl.state == ClientStates.CS_SPAWNED)
                Netchan.Transmit(cl.netchan, Globals.net_message.cursize,
                        Globals.net_message.data);
        }
        for (i = 0; i < SV_INIT.gameImports.svs.clients.length; i++) {
            cl = SV_INIT.gameImports.svs.clients[i];
            if (cl.state == ClientStates.CS_CONNECTED || cl.state == ClientStates.CS_SPAWNED)
                Netchan.Transmit(cl.netchan, Globals.net_message.cursize,
                        Globals.net_message.data);
        }
    }

    /**
     * Called when each game quits, before Sys_Quit or Sys_Error.
     */
    public static void SV_Shutdown(String finalmsg, boolean reconnect) {
        if (SV_INIT.gameImports == null)
            return;

        if (SV_INIT.gameImports.svs.clients != null)
            SV_FinalMessage(finalmsg, reconnect);

        Master_Shutdown();

        Com.Printf("==== ShutdownGame ====\n");

        SV_INIT.gameImports = null;
        Globals.server_state = ServerStates.SS_DEAD;

/*
        // free current level
        if (SV_INIT.gameImports.sv != null && SV_INIT.gameImports.sv.demofile != null)
            try {
                SV_INIT.gameImports.sv.demofile.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

        SV_INIT.gameImports.sv = new server_t();

        Globals.server_state = SV_INIT.gameImports.sv.state;

        if (SV_INIT.gameImports.svs.demofile != null)
            try {
                SV_INIT.gameImports.svs.demofile.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }

        SV_INIT.gameImports.svs = new server_static_t();
*/
    }
}