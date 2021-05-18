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
import jake2.qcommon.exec.Cbuf;
import jake2.qcommon.exec.Cmd;
import jake2.qcommon.exec.Cvar;
import jake2.qcommon.exec.cvar_t;
import jake2.qcommon.filesystem.FS;
import jake2.qcommon.filesystem.QuakeFile;
import jake2.qcommon.network.*;
import jake2.qcommon.util.Lib;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import static jake2.qcommon.Defines.*;
import static jake2.server.SV_CCMDS.*;
import static jake2.server.SV_SEND.SV_SendClientDatagram;
import static jake2.server.SV_USER.userCommands;

public class SV_MAIN implements JakeServer {

    @Override
    public List<client_t> getClients() {
        return clients;
    }

    private static final int MAX_STRINGCMDS = 8;
    private static final byte[] NULLBYTE = {0};

    public static GameImportsImpl gameImports;

    /** Addess of group servers.*/
    static netadr_t master_adr[] = new netadr_t[Defines.MAX_MASTERS];

    // prevent invalid IPs from connecting
    private final challenge_t[] challenges = new challenge_t[Defines.MAX_CHALLENGES]; // to

    private List<client_t> clients; // [maxclients->value];

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

    /* ==============================================================================
     * 
     * CONNECTIONLESS COMMANDS
     * 
     * ==============================================================================*/

    /**
     * Builds the string that is sent as heartbeats and status replies.
     */
    private String SV_StatusString() {

        String status = Cvar.getInstance().Serverinfo() + "\n";

        for (int i = 0; i < maxclients.value; i++) {
            client_t cl = clients.get(i);
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
    private void SVC_Status() {
        Netchan.OutOfBandPrint(Defines.NS_SERVER, Globals.net_from, "print\n" + SV_StatusString());
    }

    /**
     *  SVC_Ack
     */
    private static void SVC_Ack() {
        Com.Printf("Ping acknowledge from " + Globals.net_from.toString()
                + "\n");
    }

    /**
     * SVC_Info, responds with short info for broadcast scans The second parameter should
     * be the current protocol version number.
     */
    private void SVC_Info(List<String> args) {

        if (maxclients.value == 1)
            return; // ignore in single player

        int version = args.size() < 2 ? 0 : Lib.atoi(args.get(1));

        String string;
        if (version != Defines.PROTOCOL_VERSION)
            string = SV_MAIN.hostname.string + ": wrong version\n";
        else {
            int players = 0;
            for (int i = 0; i < maxclients.value; i++)
                if (clients.get(i).state == ClientStates.CS_CONNECTED || clients.get(i).state == ClientStates.CS_SPAWNED)
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
    private void SVC_GetChallenge() {
        int i;
        int oldest;
        int oldestTime;

        oldest = 0;
        oldestTime = 0x7fffffff;

        for (i = 0; i < Defines.MAX_CHALLENGES; i++) {
            if (Globals.net_from.CompareBaseAdr(challenges[i].adr))
                break;
            if (challenges[i].time < oldestTime) {
                oldestTime = challenges[i].time;
                oldest = i;
            }
        }

        if (i == Defines.MAX_CHALLENGES) {
            // overwrite the oldest
            challenges[oldest].challenge = Lib.rand() & 0x7fff;
            challenges[oldest].adr = Globals.net_from;
            challenges[oldest].time = (int) Globals.curtime;
            i = oldest;
        }

        // send it back
        Netchan.OutOfBandPrint(Defines.NS_SERVER, Globals.net_from, "challenge " + challenges[i].challenge);
    }

    /**
     * A connection request that did not come from the master.
     */
    private void SVC_DirectConnect(List<String> args) {

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
        userinfo = Info.Info_SetValueForKey(userinfo, "ip", Globals.net_from.toString());

        if (gameImports.sv.isDemo) {
            if (!adr.IsLocalAddress()) {
                Com.Printf("Remote connect in attract loop.  Ignored.\n");
                Netchan.OutOfBandPrint(Defines.NS_SERVER, adr, "print\nConnection refused.\n");
                return;
            }
        }

        // see if the challenge is valid
        int j;
        if (!adr.IsLocalAddress()) {
            for (j = 0; j < Defines.MAX_CHALLENGES; j++) {
                if (Globals.net_from.CompareBaseAdr(challenges[j].adr)) {
                    if (challenge == challenges[j].challenge)
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
        for (int i = 0; i < maxclients.value; i++) {
            client_t cl = clients.get(i);

            if (cl.state == ClientStates.CS_FREE)
                continue;
            if (adr.CompareBaseAdr(cl.netchan.remote_address)
                    && (cl.netchan.qport == qport || adr.port == cl.netchan.remote_address.port)) {
                if (!adr.IsLocalAddress() && (gameImports.realtime - cl.lastconnect) < ((int) SV_MAIN.sv_reconnect_limit.value * 1000)) {
                    Com.DPrintf(adr.toString() + ":reconnect rejected : too soon\n");
                    return;
                }
                Com.Printf(adr + ":reconnect\n");

                gotnewcl(i, challenge, userinfo, adr, qport);
                return;
            }
        }

        // find a client slot
        //newcl = null;
        int index = -1;
        for (int i = 0; i < maxclients.value; i++) {
            client_t cl = clients.get(i);
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
    private void gotnewcl(int i, int challenge, String userinfo,
                                 netadr_t adr, int qport) {
        // build a new connection
        // accept the new client
        // this is the only place a client_t is ever initialized

        gameImports.sv_client = clients.get(i);
        
        int edictnum = i + 1;
        
        edict_t ent = gameImports.gameExports.getEdict(edictnum);
        clients.get(i).edict = ent;
        
        // save challenge for checksumming
        clients.get(i).challenge = challenge;
        
        

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
        clients.get(i).userinfo = userinfo;
        SV_UserinfoChanged(clients.get(i));

        // send the connect packet to the client
        Netchan.OutOfBandPrint(Defines.NS_SERVER, adr, "client_connect");

        Netchan.Setup(Defines.NS_SERVER, clients.get(i).netchan, adr, qport);

        clients.get(i).state = ClientStates.CS_CONNECTED;

        SZ.Init(clients.get(i).datagram, clients.get(i).datagram_buf, clients.get(i).datagram_buf.length);
        
        clients.get(i).datagram.allowoverflow = true;
        clients.get(i).lastmessage = gameImports.realtime; // don't timeout
        clients.get(i).lastconnect = gameImports.realtime;
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
            Com.Printf("Rcon from " + Globals.net_from.toString() + ":\n" + msg + "\n");
        } else {
            Com.Printf("Bad rcon from " + Globals.net_from.toString() + ":\n" + msg + "\n");
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
    void SV_ConnectionlessPacket() {

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
                Com.Printf("bad connectionless packet from " + Globals.net_from.toString() + "\n");
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
    public void update(long msec) {
        Globals.time_before_game = Globals.time_after_game = 0;

        // if server is not active, do nothing
        // like when connected to another server
        final GameImportsImpl serverInstance = gameImports;
        if (serverInstance == null)
            return;

        serverInstance.realtime += msec;

        // keep the random time dependent
        Lib.rand();

        // check timeouts
        SV_CheckTimeouts();

        // get packets from clients
        SV_ReadPackets();

        //if (Game.g_edicts[1] !=null)
        //	Com.p("player at:" + Lib.vtofsbeaty(Game.g_edicts[1].s.origin ));

        // move autonomous things around if enough time has passed
        if (0 == SV_MAIN.sv_timedemo.value && serverInstance.realtime < serverInstance.sv.time) {
            // never let the time get too far off
            if (serverInstance.sv.time - serverInstance.realtime > 100) {
                if (SV_MAIN.sv_showclamp.value != 0)
                    Com.Printf("sv lowclamp\n");
                serverInstance.realtime = serverInstance.sv.time - 100;
            }
            NET.Sleep(serverInstance.sv.time - serverInstance.realtime);
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


        // send a heartbeat to the master if needed
        //Master_Heartbeat();

        // clear teleport flags, etc for next frame
        clearEntityStateEvents(serverInstance.gameExports);

    }

    void SV_SendClientMessages() {

        int msglen = 0;
        server_t sv = gameImports.sv;
        // send a message to each connected client
        // todo send only to related clients
        for (int i = 0; i < maxclients.value; i++) {
            client_t c = clients.get(i);

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
    public void SV_BroadcastPrintf(int level, String s) {

        // echo to console
        if (Globals.dedicated.value != 0) {
            Com.Printf(s);
        }

        // todo: send only to related clients
        for (int i = 0; i < maxclients.value; i++) {
            client_t cl = clients.get(i);
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
    void SV_ReadPackets() {

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
                client_t cl = clients.get(i);
                if (cl.state == ClientStates.CS_FREE)
                    continue;
                if (!Globals.net_from.CompareBaseAdr(cl.netchan.remote_address))
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
                        cl.lastmessage = gameImports.realtime; // don't timeout
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
                            cl.frame_latency[cl.lastframe & (Defines.LATENCY_COUNTS - 1)] = gameImports.realtime - cl.frames[cl.lastframe & Defines.UPDATE_MASK].senttime;
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
    void SV_CheckTimeouts() {
        // todo move use SV_MAIN realtime
        int droppoint = (int) (gameImports.realtime - 1000 * SV_MAIN.timeout.value);
        int zombiepoint = (int) (gameImports.realtime - 1000 * SV_MAIN.zombietime.value);

        for (int i = 0; i < maxclients.value; i++) {
            client_t cl = clients.get(i);
            // message times may be wrong across a changelevel
            if (cl.lastmessage > gameImports.realtime)
                cl.lastmessage = gameImports.realtime;

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
    void SV_CalcPings() {

        for (int i = 0; i < maxclients.value; i++) {
            client_t cl = clients.get(i);
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
        // todo: base on sv_main state, not on gameImports.sv.framenum
        if ((gameImports.sv.framenum & 15) != 0)
            return;

        for (int i = 0; i < maxclients.value; i++) {
            client_t cl = clients.get(i);
            if (cl.state == ClientStates.CS_FREE)
                continue;

            cl.commandMsec = 1800; // 1600 + some slop
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
                            + SV_MAIN.master_adr[i].toString() + "\n");
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
    private void SV_FinalMessage(String message, boolean reconnect) {

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
    @Override
    public void SV_Shutdown(String finalmsg, boolean reconnect) {
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
    void resetClients(GameExports gameExports) {
        // Clear all clients
        final int max = (int) maxclients.value;
        clients = new ArrayList<>(max);

        for (int i = 0; i < max; i++) {
            client_t cl = new client_t();
            cl.lastcmd = new usercmd_t();
            cl.edict = gameExports.getEdict(i + 1);
            clients.add(cl);
        }
    }

    /**
     * Only called at quake2.exe startup, not for each game
     */
    public SV_MAIN() {
        for (int n = 0; n < Defines.MAX_CHALLENGES; n++) {
            challenges[n] = new challenge_t();
        }

        maxclients = Cvar.getInstance().GetForceFlags("maxclients", "1", Defines.CVAR_SERVERINFO | Defines.CVAR_LATCH);

        // Clear all clients
        final int max = (int) maxclients.value;
        clients = new ArrayList<>(max);

        for (int n = 0; n < max; n++) {
            client_t cl = new client_t();
            cl.lastcmd = new usercmd_t();
            clients.add(cl);
        }

        // add commands to start the server instance. Other sv_ccmds are registered after the server is up (when these 4 are run)
        Cmd.AddCommand("map", this::SV_Map_f);
        Cmd.AddCommand("gamemap", this::SV_GameMap_f);
        Cmd.AddCommand("load", this::SV_Loadgame_f);
        Cmd.AddCommand("sv_shutdown", args -> {
            String reason;
            if (args.size() > 1) {
                reason = args.get(1);
            } else {
                reason = "Server is shut down";
            }

            SV_Shutdown(reason + "\n", args.size() > 2 && Boolean.parseBoolean(args.get(2)));
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

    /**
     * SV_InitGame.
     *
     * A brand new game has been started.
     */
    GameImportsImpl createGameInstance() {

        GameImportsImpl gameImports = new GameImportsImpl(this);
        gameImports.gameExports = createGameModInstance(gameImports);
        // why? should have default values already
        // fixme should not recreate all the clients
        resetClients(gameImports.gameExports);
        return gameImports;
    }

    private boolean initializeServerCvars() {
        // get any latched variable changes (maxclients, etc)
        Cvar.getInstance().updateLatchedVars();

        if (Cvar.getInstance().VariableValue("coop") != 0 && Cvar.getInstance().VariableValue("deathmatch") != 0) {
            Com.Printf("Deathmatch and Coop both set, disabling Coop\n");
            Cvar.getInstance().FullSet("coop", "0", Defines.CVAR_SERVERINFO | Defines.CVAR_LATCH);
        }

        // dedicated servers are can't be single player and are usually DM
        // so unless they explicity set coop, force it to deathmatch
        if (Cvar.getInstance().Get("dedicated", "0", 0).value != 0) {
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
        return maxclients.value > 1;
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

    void SV_Loadgame_f(List<String> args) {

        if (args.size() != 2) {
            Com.Printf("USAGE: load <directory>\n");
            return;
        }

        Com.Printf("Loading game...\n");

        String saveGame = args.get(1);
        if (saveGame.contains("..") || saveGame.contains("/") || saveGame.contains("\\")) {
            Com.Printf("Bad save name.\n");
            return;
        }

        // make sure the server.ssv file exists
        String name = FS.getWriteDir() + "/save/" + saveGame + "/server_mapcmd.ssv";
        RandomAccessFile f;
        try {
            f = new RandomAccessFile(name, "r");
        }
        catch (FileNotFoundException e) {
            Com.Printf("No such savegame: " + name + "\n");
            return;
        }

        try {
            f.close();
        }
        catch (IOException e1) {
            e1.printStackTrace();
        }

        SV_CopySaveGame(saveGame, "current");

        final String mapCommand = SV_ReadMapCommand();

        // go to the map
        spawnServerInstance(new ChangeMapInfo(mapCommand, false, true));
    }

    private String SV_ReadMapCommand() {
        Com.DPrintf("SV_ReadMapCommand()\n");

        try {
            QuakeFile f = new QuakeFile(FS.getWriteDir() + "/save/current/server_mapcmd.ssv", "r");
            // read the comment field
            Com.DPrintf("SV_ReadMapCommand: Loading save: " + f.readString() + "\n");

            // read the mapcmd
            return f.readString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /*
==================
SV_GameMap_f

Saves the state of the map just being exited and goes to a new map.

If the initial character of the map string is '*', the next map is
in a new unit, so the current savegame directory is cleared of
map files.

Example:

*inter.cin+jail

Clears the archived maps, plays the inter.cin cinematic, then
goes to map jail.bsp.
==================
*/
    void SV_GameMap_f(List<String> args) {

        if (args.size() != 2) {
            Com.Printf("USAGE: gamemap <map>\n");
            return;
        }

        String mapName = args.get(1);
        Com.DPrintf("SV_GameMap(" + mapName + ")\n");

        FS.CreatePath(FS.getWriteDir() + "/save/current/");

        // check for clearing the current savegame
        if (mapName.charAt(0) == '*') {
            // wipe all the *.sav files
            SV_WipeSavegame("current");
        }
        else { // save the map just exited
            // todo: init gameImports in a proper place
            if (gameImports != null && gameImports.sv.state == ServerStates.SS_GAME) {
                // clear all the client inuse flags before saving so that
                // when the level is re-entered, the clients will spawn
                // at spawn points instead of occupying body shells
                boolean[] savedInuse = new boolean[(int) maxclients.value];
                for (int i = 0; i < maxclients.value; i++) {
                    client_t cl = clients.get(i);
                    savedInuse[i] = cl.edict.inuse;
                    cl.edict.inuse = false;
                }

                gameImports.SV_WriteLevelFile();

                // we must restore these for clients to transfer over correctly
                for (int i = 0; i < maxclients.value; i++) {
                    client_t cl = clients.get(i);
                    cl.edict.inuse = savedInuse[i];

                }
            }
        }

        // start up the next map
        spawnServerInstance(new ChangeMapInfo(mapName, false, false));

        // copy off the level to the autosave slot
        if (0 == Globals.dedicated.value) {
            SV_MAIN.gameImports.SV_WriteServerFile(true);
            SV_CopySaveGame("current", "save0");
        }
    }

    /**
     * SV_Map
     */
    void spawnServerInstance(ChangeMapInfo changeMapInfo) {
        Globals.server_state = ServerStates.SS_DEAD; //todo check if this is needed

        if (gameImports == null || gameImports.sv == null || gameImports.sv.state == ServerStates.SS_DEAD && !gameImports.sv.loadgame) {

            if (gameImports != null) {
                // cause any connected clients to reconnect
                SV_Shutdown("Server restarted\n", true);
            } else {
                // make sure the client is down
                Cmd.ExecuteFunction("loading");
                Cmd.ExecuteFunction("cl_drop");
            }


            boolean multiplayer = initializeServerCvars();
            // init network stuff
            NET.Config(multiplayer);
            // long gone
            SV_MAIN.master_adr[0] = netadr_t.fromString("192.246.40.37", Defines.PORT_MASTER);

            gameImports = createGameInstance(); // the game is just starting
        }

        if (changeMapInfo.isLoadgame) {
            SV_Read_Latched_Vars(gameImports);
        }

        Cvar.getInstance().Set("nextserver", "gamemap \"" + changeMapInfo.nextServer + "\"");

        Cmd.ExecuteFunction("loading"); // for local system
        gameImports.SV_BroadcastCommand("changing\n");
        SV_SendClientMessages();

        Com.Printf("------- Server Initialization -------\n");

        // any partially connected client will be restarted
        gameImports.spawncount++;
        gameImports.realtime = 0;
        // archive server state to be used in savegame
        gameImports.mapcmd = changeMapInfo.levelString;


        // wipe the entire per-level structure
        gameImports.sv = new server_t();

        gameImports.sv.loadgame = changeMapInfo.isLoadgame;
        gameImports.sv.isDemo = changeMapInfo.isDemo;
        gameImports.sv.name = changeMapInfo.mapName;
        gameImports.sv.time = 1000;

        // save name for levels that don't set message
        gameImports.sv.configstrings[CS_NAME] = changeMapInfo.mapName;

        if (Cvar.getInstance().VariableValue("deathmatch") != 0) {
            gameImports.sv.configstrings[CS_AIRACCEL] = "" + sv_airaccelerate.value;
            PMove.pm_airaccelerate = sv_airaccelerate.value;
        } else {
            gameImports.sv.configstrings[CS_AIRACCEL] = "0";
            PMove.pm_airaccelerate = 0;
        }

        SZ.Init(gameImports.sv.multicast, gameImports.sv.multicast_buf, gameImports.sv.multicast_buf.length);


        // question: if we spawn a new server - all existing clients will receive the 'reconnect'
        // then why update state and lastframe?

        // leave slots at start for clients only
        for (client_t cl: gameImports.serverMain.getClients()) {
            // needs to reconnect
            if (cl.state == ClientStates.CS_SPAWNED)
                cl.state = ClientStates.CS_CONNECTED;
            cl.lastframe = -1;
        }

        int[] iw = { 0 };

        if (changeMapInfo.state == ServerStates.SS_GAME) {
            gameImports.sv.configstrings[CS_MODELS + 1] = "maps/" + changeMapInfo.mapName + ".bsp";
            gameImports.sv.models[1] = gameImports.cm.CM_LoadMap(gameImports.sv.configstrings[CS_MODELS + 1], false, iw);
        } else {
            gameImports.sv.models[1] = gameImports.cm.CM_LoadMap("", false, iw); // no real map
        }
        gameImports.sv.configstrings[CS_MAPCHECKSUM] = "" + iw[0];

        // clear physics interaction links

        SV_WORLD.SV_ClearWorld(gameImports);

        for (int i = 1; i < gameImports.cm.CM_NumInlineModels(); i++) {
            gameImports.sv.configstrings[CS_MODELS + 1 + i] = "*" + i;

            // copy references
            gameImports.sv.models[i + 1] = gameImports.cm.InlineModel(gameImports.sv.configstrings[CS_MODELS + 1 + i]);
        }


        // spawn the rest of the entities on the map

        // precache and static commands can be issued during
        // map initialization
        gameImports.sv.state = ServerStates.SS_LOADING;
        Globals.server_state = gameImports.sv.state;

        // load and spawn all other entities
        gameImports.gameExports.SpawnEntities(gameImports.sv.name, gameImports.cm.CM_EntityString(), changeMapInfo.spawnPoint);

        // run two frames to allow everything to settle
        gameImports.gameExports.G_RunFrame();
        gameImports.gameExports.G_RunFrame();

        // all precaches are complete
        gameImports.sv.state = changeMapInfo.state;
        Globals.server_state = gameImports.sv.state;

        // create a baseline for more efficient communications
        gameImports.sv_game.SV_CreateBaseline();

        // check for a savegame
        gameImports.sv_game.SV_CheckForSavegame(gameImports.sv);

        // set serverinfo variable
        Cvar.getInstance().FullSet("mapname", gameImports.sv.name, CVAR_SERVERINFO | CVAR_NOSET);

        if (changeMapInfo.state == ServerStates.SS_GAME)
            Cbuf.CopyToDefer();

        gameImports.SV_BroadcastCommand("reconnect\n");
    }

    /*
==================
SV_Map_f

Goes directly to a given map without any savegame archiving.
For development work
==================
*/
    void SV_Map_f(List<String> args) {
        String mapName;
        //char expanded[MAX_QPATH];
        if (args.size() < 2) {
            Com.Printf("usage: map <map_name>\n");
            return;
        }

        // if not a pcx, demo, or cinematic, check to make sure the level exists
        mapName = args.get(1);
        if (!mapName.contains(".")) {
            String mapPath = "maps/" + mapName + ".bsp";
            if (FS.LoadFile(mapPath) == null) {
                Com.Printf("Can't find " + mapPath + "\n");
                return;
            }
        }

        if (SV_MAIN.gameImports != null)
            SV_MAIN.gameImports.sv.state = ServerStates.SS_DEAD; // don't save current level when changing

        SV_WipeSavegame("current");
        SV_GameMap_f(args);
    }

}
