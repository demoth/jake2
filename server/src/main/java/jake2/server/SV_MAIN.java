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
import jake2.qcommon.filesystem.FS;
import jake2.qcommon.network.*;
import jake2.qcommon.sys.Sys;
import jake2.qcommon.util.Lib;

import java.io.IOException;
import java.util.List;

import static jake2.qcommon.Defines.ERR_DROP;
import static jake2.qcommon.Defines.PRINT_ALL;
import static jake2.server.SV_SEND.SV_DemoCompleted;
import static jake2.server.SV_SEND.SV_SendClientDatagram;
import static jake2.server.SV_USER.userCommands;

public class SV_MAIN {
    private static final int MAX_STRINGCMDS = 8;
    private static final byte[] NULLBYTE = {0};

    public static GameImportsImpl gameImports;

    /** Addess of group servers.*/
    static netadr_t master_adr[] = new netadr_t[Defines.MAX_MASTERS];

    static client_t clients[]; // [maxclients->value];

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

    static cvar_t maxclients;

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

        String status = Cvar.getInstance().Serverinfo() + "\n";

        for (int i = 0; i < maxclients.value; i++) {
            client_t cl = clients[i];
            if (cl.state == ClientStates.CS_CONNECTED || cl.state == ClientStates.CS_SPAWNED) {
                String player = "" + cl.edict.getClient().getPlayerState().stats[Defines.STAT_FRAGS] + " " + cl.ping + "\"" + cl.name + "\"\n";

                int playerLength = player.length();
                int statusLength = status.length();

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
        Netchan.OutOfBandPrint(Defines.NS_SERVER, Globals.net_from, "print\n" + SV_StatusString());
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

        if (maxclients.value == 1)
            return; // ignore in single player

        int version = args.size() < 2 ? 0 : Lib.atoi(args.get(1));

        String string;
        if (version != Defines.PROTOCOL_VERSION)
            string = SV_MAIN.hostname.string + ": wrong version\n";
        else {
            int players = 0;
            for (int i = 0; i < maxclients.value; i++)
                if (clients[i].state == ClientStates.CS_CONNECTED || clients[i].state == ClientStates.CS_SPAWNED)
                    players++;

            string = SV_MAIN.hostname.string + " " + gameImports.sv.name + " " + players + "/" + (int) maxclients.value + "\n";
        }

        Netchan.OutOfBandPrint(Defines.NS_SERVER, Globals.net_from, "info\n" + string);
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

        for (i = 0; i < Defines.MAX_CHALLENGES; i++) {
            if (NET.CompareBaseAdr(Globals.net_from, gameImports.svs.challenges[i].adr))
                break;
            if (gameImports.svs.challenges[i].time < oldestTime) {
                oldestTime = gameImports.svs.challenges[i].time;
                oldest = i;
            }
        }

        if (i == Defines.MAX_CHALLENGES) {
            // overwrite the oldest
            gameImports.svs.challenges[oldest].challenge = Lib.rand() & 0x7fff;
            gameImports.svs.challenges[oldest].adr = Globals.net_from;
            gameImports.svs.challenges[oldest].time = (int) Globals.curtime;
            i = oldest;
        }

        // send it back
        Netchan.OutOfBandPrint(Defines.NS_SERVER, Globals.net_from, "challenge " + gameImports.svs.challenges[i].challenge);
    }

    /**
     * A connection request that did not come from the master.
     */
    private static void SVC_DirectConnect(List<String> args) {

        netadr_t adr = Globals.net_from;

        Com.DPrintf("SVC_DirectConnect ()\n");

        int version = args.size() >= 2 ? Lib.atoi(args.get(1)) : 0;

        if (version != Defines.PROTOCOL_VERSION) {
            Netchan.OutOfBandPrint(Defines.NS_SERVER, adr, "print\nServer is version " + Globals.VERSION + "\n");
            Com.DPrintf("    rejected connect from version " + version + "\n");
            return;
        }

        int qport = args.size() >= 3 ? Lib.atoi(args.get(2)) : 0;
        int challenge = args.size() >= 4 ? Lib.atoi(args.get(3)) : 0;
        String userinfo = args.size() >= 5 ? args.get(4) : "";

        // force the IP key/value pair so the game can filter based on ip
        userinfo = Info.Info_SetValueForKey(userinfo, "ip", NET.AdrToString(Globals.net_from));

        if (gameImports.sv.isDemo) {
            if (!NET.IsLocalAddress(adr)) {
                Com.Printf("Remote connect in attract loop.  Ignored.\n");
                Netchan.OutOfBandPrint(Defines.NS_SERVER, adr, "print\nConnection refused.\n");
                return;
            }
        }

        // see if the challenge is valid
        int j;
        if (!NET.IsLocalAddress(adr)) {
            for (j = 0; j < Defines.MAX_CHALLENGES; j++) {
                if (NET.CompareBaseAdr(Globals.net_from, gameImports.svs.challenges[j].adr)) {
                    if (challenge == gameImports.svs.challenges[j].challenge)
                        break; // good
                    Netchan.OutOfBandPrint(Defines.NS_SERVER, adr, "print\nBad challenge.\n");
                    return;
                }
            }
            if (j == Defines.MAX_CHALLENGES) {
                Netchan.OutOfBandPrint(Defines.NS_SERVER, adr, "print\nNo challenge for address.\n");
                return;
            }
        }

        // if there is already a slot for this ip, reuse it
        client_t cl;
        for (int i = 0; i < maxclients.value; i++) {
            cl = clients[i];

            if (cl.state == ClientStates.CS_FREE)
                continue;
            if (NET.CompareBaseAdr(adr, cl.netchan.remote_address)
                    && (cl.netchan.qport == qport || adr.port == cl.netchan.remote_address.port)) {
                if (!NET.IsLocalAddress(adr) && (gameImports.svs.realtime - cl.lastconnect) < ((int) SV_MAIN.sv_reconnect_limit.value * 1000)) {
                    Com.DPrintf(NET.AdrToString(adr) + ":reconnect rejected : too soon\n");
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
        for (int i = 0; i < maxclients.value; i++) {
            cl = clients[i];
            if (cl.state == ClientStates.CS_FREE) {
                index = i;
                break;
            }
        }
        if (index == -1) {
            Netchan.OutOfBandPrint(Defines.NS_SERVER, adr, "print\nServer is full.\n");
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

        gameImports.sv_client = clients[i];
        
        int edictnum = i + 1;
        
        edict_t ent = gameImports.gameExports.getEdict(edictnum);
        clients[i].edict = ent;
        
        // save challenge for checksumming
        clients[i].challenge = challenge;
        
        

        // get the game a chance to reject this connection or modify the
        // userinfo
        if (!(gameImports.gameExports.ClientConnect(ent, userinfo))) {
            if (Info.Info_ValueForKey(userinfo, "rejmsg") != null) {
                Netchan.OutOfBandPrint(Defines.NS_SERVER, adr, "print\n" + Info.Info_ValueForKey(userinfo, "rejmsg") + "\nConnection refused.\n");
            } else {
                Netchan.OutOfBandPrint(Defines.NS_SERVER, adr, "print\nConnection refused.\n");
            }
            Com.DPrintf("Game rejected a connection.\n");
            return;
        }

        // parse some info from the info strings
        clients[i].userinfo = userinfo;
        SV_UserinfoChanged(clients[i]);

        // send the connect packet to the client
        Netchan.OutOfBandPrint(Defines.NS_SERVER, adr, "client_connect");

        Netchan.Setup(Defines.NS_SERVER, clients[i].netchan, adr, qport);

        clients[i].state = ClientStates.CS_CONNECTED;

        SZ.Init(clients[i].datagram, clients[i].datagram_buf, clients[i].datagram_buf.length);
        
        clients[i].datagram.allowoverflow = true;
        clients[i].lastmessage = gameImports.svs.realtime; // don't timeout
        clients[i].lastconnect = gameImports.svs.realtime;
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
    private static void SVC_RemoteCommand(List<String> args, GameImportsImpl gameImports) {

        boolean rconIsValid = Rcon_Validate(args);

        String msg = Lib.CtoJava(Globals.net_message.data, 4, 1024);

        if (rconIsValid) {
            Com.Printf("Rcon from " + NET.AdrToString(Globals.net_from) + ":\n" + msg + "\n");
        } else {
            Com.Printf("Bad rcon from " + NET.AdrToString(Globals.net_from) + ":\n" + msg + "\n");
        }

        // todo identify gameImports instance by client

        Com.BeginRedirect(Defines.RD_PACKET, Defines.SV_OUTPUTBUF_LENGTH,
                (target, buffer) -> SV_SEND.SV_FlushRedirect(target, Lib.stringToBytes(buffer.toString()), gameImports));

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
                SVC_RemoteCommand(args, gameImports);
                break;
            default:
                Com.Printf("bad connectionless packet from " + NET.AdrToString(Globals.net_from) + "\n");
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
        final GameImportsImpl serverInstance = gameImports;
        if (serverInstance == null)
            return;
        if (!serverInstance.svs.initialized) {
            Sys.Error("!serverInstance.svs.initialized");
        }

        serverInstance.svs.realtime += msec;

        // keep the random time dependent
        Lib.rand();

        // check timeouts
        SV_CheckTimeouts();

        // get packets from clients
        SV_ReadPackets();

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
        SV_CalcPings();

        // give the clients some timeslices
        SV_GiveMsec();

        // let everything in the world think and move
        serverInstance.SV_RunGameFrame();

        // send messages back to the clients that had packets read this frame
        SV_SendClientMessages();

        // save the entire world state if recording a serverdemo
        serverInstance.sv_ents.SV_RecordDemoMessage();

        // send a heartbeat to the master if needed
        //Master_Heartbeat();

        // clear teleport flags, etc for next frame
        clearEntityStateEvents(serverInstance.gameExports);

    }

    static void SV_SendClientMessages() {

        int msglen = 0;
        server_t sv = gameImports.sv;
        // read the next demo message if needed
        if (sv.state == ServerStates.SS_DEMO && sv.demofile != null) {
            if (SV_MAIN.sv_paused.value != 0)
                msglen = 0;
            else {
                // get the next message
                //r = fread (&msglen, 4, 1, sv.demofile);
                try {
                    msglen = EndianHandler.swapInt(sv.demofile.readInt());
                }
                catch (Exception e) {
                    SV_DemoCompleted(gameImports);
                    return;
                }

                //msglen = LittleLong (msglen);
                if (msglen == -1) {
                    SV_DemoCompleted(gameImports);
                    return;
                }
                if (msglen > Defines.MAX_MSGLEN)
                    Com.Error(Defines.ERR_DROP, "SV_SendClientMessages: msglen > MAX_MSGLEN");

                //r = fread (msgbuf, msglen, 1, sv.demofile);
                int r = 0;
                try {
                    r = sv.demofile.read(gameImports.msgbuf, 0, msglen);
                }
                catch (IOException e1) {
                    Com.Printf("IOError: reading demo file, " + e1);
                }
                if (r != msglen) {
                    SV_DemoCompleted(gameImports);
                    return;
                }
            }
        }

        // send a message to each connected client
        // todo send only to related clients
        for (int i = 0; i < SV_MAIN.maxclients.value; i++) {
            client_t c = SV_MAIN.clients[i];

            if (c.state == ClientStates.CS_FREE)
                continue;
            // if the reliable message overflowed,
            // drop the client
            if (c.netchan.message.overflowed) {
                c.netchan.message.clear();
                c.datagram.clear();
                SV_BroadcastPrintf(Defines.PRINT_HIGH, c.name + " overflowed\n");
                SV_DropClient(c);
            }

            if (sv.state == ServerStates.SS_CINEMATIC || sv.state == ServerStates.SS_DEMO || sv.state == ServerStates.SS_PIC)
                Netchan.Transmit(c.netchan, msglen, gameImports.msgbuf);
            else if (c.state == ClientStates.CS_SPAWNED) {
                // don't overrun bandwidth
                if (SV_RateDrop(c))
                    continue;

                SV_SendClientDatagram(c, gameImports);
            }
            else {
                // just update reliable	if needed
                if (c.netchan.message.cursize != 0 || Globals.curtime - c.netchan.last_sent > 1000)
                    Netchan.Transmit(c.netchan, 0, NULLBYTE);
            }
        }
    }

    /**
     * Called when the player is totally leaving the server, either willingly or
     * unwillingly. This is NOT called if the entire server is quiting or
     * crashing.
     */
    static void SV_DropClient(client_t client) {
        // add the disconnect
        MSG.WriteByte(client.netchan.message, NetworkCommands.svc_disconnect);

        if (client.state == ClientStates.CS_SPAWNED) {
            // call the prog function for removing a client
            // this will remove the body, among other things
            gameImports.gameExports.ClientDisconnect(client.edict);
        }

        if (client.download != null) {
            client.download = null;
        }

        client.state = ClientStates.CS_ZOMBIE; // become free in a few seconds
        client.name = "";
    }

    /**
     * Sends text to all active clients
     */
    static void SV_BroadcastPrintf(int level, String s) {

        // echo to console
        if (Globals.dedicated.value != 0) {
            Com.Printf(s);
        }

        // todo: send only to related clients
        for (int i = 0; i < SV_MAIN.maxclients.value; i++) {
            client_t cl = SV_MAIN.clients[i];
            if (level < cl.messagelevel)
                continue;
            if (cl.state != ClientStates.CS_SPAWNED)
                continue;
            MSG.WriteByte(cl.netchan.message, NetworkCommands.svc_print);
            MSG.WriteByte(cl.netchan.message, level);
            MSG.WriteString(cl.netchan.message, s);
        }
    }

    /**
     * SV_RateDrop
     * <p>
     * Returns true if the client is over its current
     * bandwidth estimation and should not be sent another packet
     */
    static boolean SV_RateDrop(client_t c) {
        int total;
        int i;

        // never drop over the loopback
        if (c.netchan.remote_address.type == NetAddrType.NA_LOOPBACK)
            return false;

        total = 0;

        for (i = 0; i < Defines.RATE_MESSAGES; i++) {
            total += c.message_size[i];
        }

        if (total > c.rate) {
            c.surpressCount++;
            c.message_size[gameImports.sv.framenum % Defines.RATE_MESSAGES] = 0;
            return true;
        }

        return false;
    }

    /**
     * Reads packets from the network or loopback.
     */
    static void SV_ReadPackets() {

        while (NET.GetPacket(Defines.NS_SERVER, Globals.net_from, Globals.net_message)) {

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
            for (int i = 0; i < maxclients.value; i++) {
                client_t cl = clients[i];
                if (cl.state == ClientStates.CS_FREE)
                    continue;
                if (!NET.CompareBaseAdr(Globals.net_from, cl.netchan.remote_address))
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
                        // todo: identify gameImports instance by client
                        // todo: use sv_main realtime
                        cl.lastmessage = gameImports.svs.realtime; // don't timeout
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
    static void SV_ExecuteClientMessage(client_t cl) {

        usercmd_t oldest = new usercmd_t();
        usercmd_t oldcmd = new usercmd_t();
        usercmd_t newcmd = new usercmd_t();

        gameImports.sv_client = cl;

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
                    SV_UserinfoChanged(cl);
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
                            cl.frame_latency[cl.lastframe & (Defines.LATENCY_COUNTS - 1)] = gameImports.svs.realtime - cl.frames[cl.lastframe & Defines.UPDATE_MASK].senttime;
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
                        SV_ExecuteUserCommand(cl, s);

                    if (cl.state == ClientStates.CS_ZOMBIE)
                        return; // disconnect command
                    break;
            }
        }
    }

    static void SV_ClientThink(client_t cl, usercmd_t cmd) {
        cl.commandMsec -= cmd.msec & 0xFF;

        if (cl.commandMsec < 0 && SV_MAIN.sv_enforcetime.value != 0) {
            Com.DPrintf("commandMsec underflow from " + cl.name + "\n");
            return;
        }

        gameImports.gameExports.ClientThink(cl.edict, cmd);
    }

    /**
     * Pull specific info from a newly changed userinfo string into a more C
     * freindly form.
     */
    static void SV_UserinfoChanged(client_t cl) {

        // call prog code to allow overrides
        gameImports.gameExports.ClientUserinfoChanged(cl.edict, cl.userinfo);

        // name for C code
        cl.name = Info.Info_ValueForKey(cl.userinfo, "name");

        // mask off high bit
        //TODO: masking for german umlaute
        //for (i=0 ; i<sizeof(cl.name) ; i++)
        //	cl.name[i] &= 127;

        // rate command
        String val = Info.Info_ValueForKey(cl.userinfo, "rate");
        if (val.length() > 0) {
            cl.rate = Lib.atoi(val);
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

    private static void SV_ExecuteUserCommand(client_t cl, String s) {

        Com.dprintln("SV_ExecuteUserCommand:" + s );

        List<String> args = Cmd.TokenizeString(s, true);
        if (args.isEmpty())
            return;

        if (userCommands.containsKey(args.get(0))) {
            userCommands.get(args.get(0)).execute(args, gameImports);
            return;
        }

        if (gameImports.sv.state == ServerStates.SS_GAME)
            gameImports.gameExports.ClientCommand(cl.edict, args);
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
    static void SV_CheckTimeouts() {
        // todo move use SV_MAIN realtime
        int droppoint = (int) (gameImports.svs.realtime - 1000 * SV_MAIN.timeout.value);
        int zombiepoint = (int) (gameImports.svs.realtime - 1000 * SV_MAIN.zombietime.value);

        for (int i = 0; i < maxclients.value; i++) {
            client_t cl = clients[i];
            // message times may be wrong across a changelevel
            if (cl.lastmessage > gameImports.svs.realtime)
                cl.lastmessage = gameImports.svs.realtime;

            if (cl.state == ClientStates.CS_ZOMBIE && cl.lastmessage < zombiepoint) {
                cl.state = ClientStates.CS_FREE; // can now be reused
                continue;
            }
            if ((cl.state == ClientStates.CS_CONNECTED || cl.state == ClientStates.CS_SPAWNED) && cl.lastmessage < droppoint) {
                // todo identify gameImports instance by client
                SV_BroadcastPrintf(Defines.PRINT_HIGH, cl.name + " timed out\n");
                SV_DropClient(cl);
                cl.state = ClientStates.CS_FREE; // don't bother with zombie state
            }
        }
    }

    /**
     * Updates the cl.ping variables.
     */
    static void SV_CalcPings() {

        for (int i = 0; i < maxclients.value; i++) {
            client_t cl = clients[i];
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
    static void SV_GiveMsec() {
        // todo: base on sv_main state, not on gameImports.sv.framenum
        if ((gameImports.sv.framenum & 15) != 0)
            return;

        for (int i = 0; i < maxclients.value; i++) {
            client_t cl = clients[i];
            if (cl.state == ClientStates.CS_FREE)
                continue;

            cl.commandMsec = 1800; // 1600 + some slop
        }
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
        if (gameImports.svs.last_heartbeat > gameImports.svs.realtime)
            gameImports.svs.last_heartbeat = gameImports.svs.realtime;

        if (gameImports.svs.realtime - gameImports.svs.last_heartbeat < SV_MAIN.HEARTBEAT_SECONDS * 1000)
            return; // not time to send yet

        gameImports.svs.last_heartbeat = gameImports.svs.realtime;

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
     * Used by SV_Shutdown to send a final message to all connected clients
     * before the server goes down. The messages are sent immediately, not just
     * stuck on the outgoing message list, because the server is going to
     * totally exit after returning from this function.
     */
    private static void SV_FinalMessage(String message, boolean reconnect) {

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
        for (client_t cl : clients) {
            if (cl.state == ClientStates.CS_CONNECTED || cl.state == ClientStates.CS_SPAWNED)
                Netchan.Transmit(cl.netchan, Globals.net_message.cursize, Globals.net_message.data);
        }
        for (client_t cl : clients) {
            if (cl.state == ClientStates.CS_CONNECTED || cl.state == ClientStates.CS_SPAWNED)
                Netchan.Transmit(cl.netchan, Globals.net_message.cursize, Globals.net_message.data);
        }
    }

    /**
     * Called when each game quits, before Sys_Quit or Sys_Error.
     * todo: shutdown a particular instance, not the whole server
     */
    public static void SV_Shutdown(String finalmsg, boolean reconnect) {
        if (gameImports == null)
            return;

        // todo: identify if we need to send the final message (clients is always != null)
        if (clients != null)
            SV_FinalMessage(finalmsg, reconnect);

        Master_Shutdown();

        Com.Printf("==== ShutdownGame ====\n");

        gameImports = null;
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
    // fixme: update only related clients

    /**
     * Resets the clients, use when maxclients.value is changed
     */
    static void resetClients(GameExports gameExports) {
        // Clear all clients
        clients = new client_t[(int) maxclients.value];

        for (int i = 0; i < maxclients.value; i++) {
            clients[i] = new client_t();
            clients[i].lastcmd = new usercmd_t();
            clients[i].edict = gameExports.getEdict(i + 1);
        }
    }

    /**
     * Only called at quake2.exe startup, not for each game
     */
    public static void SV_Init() {
        maxclients = Cvar.getInstance().GetForceFlags("maxclients", "1", Defines.CVAR_SERVERINFO | Defines.CVAR_LATCH);

        // Clear all clients
        clients = new client_t[(int) maxclients.value];
        for (int n = 0; n < clients.length; n++) {
            clients[n] = new client_t();
            clients[n].lastcmd = new usercmd_t();
        }

        // add commands to start the server instance. Other sv_ccmds are registered after the server is up (when these 4 are run)
        Cmd.AddCommand("map", SV_CCMDS::SV_Map_f);
        Cmd.AddCommand("demomap", SV_CCMDS::SV_DemoMap_f);
        Cmd.AddCommand("gamemap", SV_CCMDS::SV_GameMap_f);
        Cmd.AddCommand("load", SV_CCMDS::SV_Loadgame_f);
        Cmd.AddCommand("sv_shutdown", args -> {
            String reason;
            if (args.size() > 1) {
                reason = args.get(1);
            } else {
                reason = "Server is shut down";
            }

            SV_MAIN.SV_Shutdown(reason + "\n", args.size() > 2 && Boolean.parseBoolean(args.get(2)));
        });

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

        hostname = Cvar.getInstance().Get("hostname", "noname", Defines.CVAR_SERVERINFO | Defines.CVAR_ARCHIVE);
        timeout = Cvar.getInstance().Get("timeout", "125", 0);
        zombietime = Cvar.getInstance().Get("zombietime", "2", 0);
        sv_showclamp = Cvar.getInstance().Get("showclamp", "0", 0);
        sv_paused = Cvar.getInstance().Get("paused", "0", 0);
        sv_timedemo = Cvar.getInstance().Get("timedemo", "0", 0);
        sv_enforcetime = Cvar.getInstance().Get("sv_enforcetime", "0", 0);

        allow_download = Cvar.getInstance().Get("allow_download", "1", Defines.CVAR_ARCHIVE);
        allow_download_players = Cvar.getInstance().Get("allow_download_players", "0", Defines.CVAR_ARCHIVE);
        allow_download_models = Cvar.getInstance().Get("allow_download_models", "1", Defines.CVAR_ARCHIVE);
        allow_download_sounds = Cvar.getInstance().Get("allow_download_sounds", "1", Defines.CVAR_ARCHIVE);
        allow_download_maps = Cvar.getInstance().Get("allow_download_maps", "1", Defines.CVAR_ARCHIVE);

        Cvar.getInstance().Get("sv_noreload", "0", 0);
        sv_airaccelerate = Cvar.getInstance().Get("sv_airaccelerate", "0", Defines.CVAR_LATCH);
        public_server = Cvar.getInstance().Get("public", "0", 0);
        sv_reconnect_limit = Cvar.getInstance().Get("sv_reconnect_limit", "3", Defines.CVAR_ARCHIVE);
    }
}
