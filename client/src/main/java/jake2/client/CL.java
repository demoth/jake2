/*
 * CL.java
 * Copyright (C) 2004
 * 
 * $Id: CL.java,v 1.34 2011-07-08 16:01:46 salomo Exp $
 */
/*
 Copyright (C) 1997-2001 Id Software, Inc.

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU General Public License
 as published by the Free Software Foundation; either version 2
 of the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

 See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software
 Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.

 */
package jake2.client;

import jake2.client.render.fast.Main;
import jake2.client.sound.S;
import jake2.qcommon.*;
import jake2.qcommon.exec.*;
import jake2.qcommon.filesystem.FS;
import jake2.qcommon.filesystem.qfiles;
import jake2.qcommon.network.NET;
import jake2.qcommon.network.Netchan;
import jake2.qcommon.network.messages.ConnectionlessCommand;
import jake2.qcommon.network.messages.NetworkPacket;
import jake2.qcommon.network.messages.client.StringCmdMessage;
import jake2.qcommon.network.messages.server.ConfigStringMessage;
import jake2.qcommon.network.messages.server.ServerDataMessage;
import jake2.qcommon.network.messages.server.ServerMessageType;
import jake2.qcommon.network.messages.server.StuffTextMessage;
import jake2.qcommon.network.netadr_t;
import jake2.qcommon.sys.Timer;
import jake2.qcommon.util.Lib;
import jake2.qcommon.util.Math3D;
import jake2.qcommon.util.Vargs;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.List;

import static jake2.qcommon.Defines.NS_CLIENT;
import static jake2.qcommon.exec.Cmd.getArguments;

/**
 * CL
 */
public final class CL {
    
    private static int precache_check; // for autodownload of precache items

    private static int precache_spawncount;

    private static int precache_tex;

    private static int precache_model_skin;

    private static byte precache_model[]; // used for skin checking in alias models

    private static final int PLAYER_MULT = 5;


    private static void Quit()
    {
        Cmd.ExecuteFunction("sv_shutdown", "Server quit ", "false");
        Cmd.ExecuteFunction("cl_shutdown");

        if (Globals.logfile != null)
        {
            try
            {
                Globals.logfile.close();
            }
            catch (IOException e)
            {
            }
            Globals.logfile= null;
        }

        System.exit(0);
    }

    static void SendKeyEvents() {
		ClientGlobals.re.getKeyboardHandler().Update();

        // grab frame time
        Globals.sys_frame_time = Timer.Milliseconds();
    }

    public static class cheatvar_t {
        String name;

        String value;

        cvar_t var;
    }

    private static String cheatvarsinfo[][] = { { "timescale", "1" },
            { "timedemo", "0" }, { "r_drawworld", "1" },
            { "cl_testlights", "0" }, { "r_fullbright", "0" },
            { "r_drawflat", "0" }, { "paused", "0" }, { "fixedtime", "0" },
            { "sw_draworder", "0" }, { "gl_lightmap", "0" },
            { "gl_saturatelighting", "0" }, { null, null } };

    private static cheatvar_t cheatvars[];

    static {
        cheatvars = new cheatvar_t[cheatvarsinfo.length];
        for (int n = 0; n < cheatvarsinfo.length; n++) {
            cheatvars[n] = new cheatvar_t();
            cheatvars[n].name = cheatvarsinfo[n][0];
            cheatvars[n].value = cheatvarsinfo[n][1];
        }
    }

    private static int numcheatvars;

    /**
     * Stop_f
     * 
     * Stop recording a demo.
     */
    private static Command Stop_f = (List<String> args) -> {
        try {

            int len;

            if (!ClientGlobals.cls.demorecording) {
                Com.Printf("Not recording a demo.\n");
                return;
            }

            //	   finish up
            len = -1;
            ClientGlobals.cls.demofile.writeInt(EndianHandler.swapInt(len));
            ClientGlobals.cls.demofile.close();
            ClientGlobals.cls.demofile = null;
            ClientGlobals.cls.demorecording = false;
            Com.Printf("Stopped demo.\n");
        } catch (IOException e) {
        }
    };

    private static entity_state_t nullstate = new entity_state_t(null);

    /**
     * Record_f
     * 
     * record &lt;demoname&gt;
     * Begins recording a demo from the current position.
     */
    private static Command Record_f = (List<String> args) -> {
        try {
            byte[] buf_data = new byte[Defines.MAX_MSGLEN];
            sizebuf_t buffer = new sizebuf_t();
            entity_state_t ent;

            if (args.size() != 2) {
                Com.Printf("record <demoname>\n");
                return;
            }

            if (ClientGlobals.cls.demorecording) {
                Com.Printf("Already recording.\n");
                return;
            }

            if (ClientGlobals.cls.state != Defines.ca_active) {
                Com.Printf("You must be in a level to record.\n");
                return;
            }

            //
            // open the demo file
            //
            String name = FS.getWriteDir() + "/demos/" + args.get(1) + ".dm2";

            Com.Printf("recording to " + name + ".\n");
            FS.CreatePath(name);
            ClientGlobals.cls.demofile = new RandomAccessFile(name, "rw");
            if (ClientGlobals.cls.demofile == null) {
                Com.Printf("ERROR: couldn't open.\n");
                return;
            }
            ClientGlobals.cls.demorecording = true;

            // don't start saving messages until a non-delta compressed
            // message is received
            ClientGlobals.cls.demowaiting = true;

            //
            // write out messages to hold the startup information
            //
            buffer.init(buf_data, Defines.MAX_MSGLEN);

            // send the serverdata
            new ServerDataMessage(Defines.PROTOCOL_VERSION,
                    0x10000 + ClientGlobals.cl.servercount,
                    true,
                    ClientGlobals.cl.gamedir,
                    ClientGlobals.cl.playernum,
                    ClientGlobals.cl.configstrings[Defines.CS_NAME]);

            // configstrings
            short i;
            for (i = 0; i < Defines.MAX_CONFIGSTRINGS; i++) {
                if (ClientGlobals.cl.configstrings[i].length() > 0) {
                    if (buffer.cursize + ClientGlobals.cl.configstrings[i].length()
                            + 32 > buffer.maxsize) {
                        // write it out
                        ClientGlobals.cls.demofile.writeInt(EndianHandler.swapInt(buffer.cursize));
                        ClientGlobals.cls.demofile
                                .write(buffer.data, 0, buffer.cursize);
                        buffer.cursize = 0;
                    }

                    new ConfigStringMessage(i, ClientGlobals.cl.configstrings[i]).writeTo(buffer);
                }

            }

            // baselines
            nullstate.clear();
            for (i = 0; i < Defines.MAX_EDICTS; i++) {
                ent = ClientGlobals.cl_entities[i].baseline;
                if (ent.modelindex == 0)
                    continue;

                if (buffer.cursize + 64 > buffer.maxsize) { // write it out
                    ClientGlobals.cls.demofile.writeInt(EndianHandler.swapInt(buffer.cursize));
                    ClientGlobals.cls.demofile.write(buffer.data, 0, buffer.cursize);
                    buffer.cursize = 0;
                }

                buffer.writeByte((byte) ServerMessageType.svc_spawnbaseline.type);
                DeltaUtils.WriteDeltaEntity(nullstate,
                        ClientGlobals.cl_entities[i].baseline, buffer, true, true);
            }
            new StuffTextMessage(StringCmdMessage.PRECACHE).writeTo(buffer);

            // write it to the demo file
            ClientGlobals.cls.demofile.writeInt(EndianHandler.swapInt(buffer.cursize));
            ClientGlobals.cls.demofile.write(buffer.data, 0, buffer.cursize);
            // the rest of the demo file will be individual frames

        } catch (IOException e) {
        }
    };

    /**
     * Adds the current command line as a clc_stringcmd to the client message.
     * things like godmode, noclip, etc, are commands directed to the server, so
     * when they are typed in at the console, they will need to be forwarded.
     *
     * see jake2.server.SV_MAIN#SV_ExecuteUserCommand(jake2.server.client_t, java.lang.String)
     */
    private static Command ForwardToServer_f = (List<String> args) -> {
        if (ClientGlobals.cls.state != Defines.ca_connected
                && ClientGlobals.cls.state != Defines.ca_active) {
            Com.Printf("Can't \"" + args.get(0) + "\", not connected\n");
            return;
        }

        // don't forward the first argument
        if (args.size() > 1) {
            ClientGlobals.cls.netchan.reliable.add(new StringCmdMessage(getArguments(args)));
        }
    };

    /**
     * Pause_f
     */
    private static Command Pause_f = (List<String> args) -> {
        // never pause in multiplayer

        if (Cvar.getInstance().VariableValue("maxclients") > 1
                || Globals.server_state == ServerStates.SS_DEAD) {
            Cvar.getInstance().SetValue("paused", 0);
            return;
        }

        Cvar.getInstance().SetValue("paused", ClientGlobals.cl_paused.value);
    };

    /**
     * Quit_f
     */
    static Command Quit_f = (List<String> args) -> {
        Disconnect();
        Quit();
    };

    /**
     * Connect_f
     */
    private static Command Connect_f = (List<String> args) -> {
        String server;

        if (args.size() != 2) {
            Com.Printf("usage: connect <server>\n");
            return;
        }

        if (Globals.server_state != ServerStates.SS_DEAD) {
            // if running a local server, kill it and reissue
            Cmd.ExecuteFunction("sv_shutdown", "Server quit", "false");
        } else {
            Disconnect();
        }

        server = args.get(1);

        NET.Config(true); // allow remote

        Disconnect();

        ClientGlobals.cls.state = Defines.ca_connecting;
        //strncpy (cls.servername, server, sizeof(cls.servername)-1);
        ClientGlobals.cls.servername = server;
        ClientGlobals.cls.connect_time = -99999;
        // CL_CheckForResend() will fire immediately
    };

    /**
     * Rcon_f
     * 
     * Send the rest of the command line over as an unconnected command.
     */
    private static Command Rcon_f = (List<String> args) -> {

        if (ClientGlobals.rcon_client_password.string.length() == 0) {
            Com.Printf("You must set 'rcon_password' before\nissuing an rcon command.\n");
            return;
        }

        // allow remote
        // fixme: why?
        NET.Config(true);

        // assemble password and arguments into a string and send
        StringBuilder message = new StringBuilder(1024);
        message.append(" ");
        message.append(ClientGlobals.rcon_client_password.string);
        message.append(" ");

        for (int i = 1; i < args.size(); i++) {
            message.append(args.get(i));
            message.append(" ");
        }

        final netadr_t to;

        if (ClientGlobals.cls.state == Defines.ca_connected || ClientGlobals.cls.state == Defines.ca_active) {
            to = ClientGlobals.cls.netchan.remote_address;
        } else {
            if (ClientGlobals.rcon_address.string.length() == 0) {
                Com.Printf("You must either be connected,\nor set the 'rcon_address' cvar\nto issue rcon commands\n");
                return;
            }
            to = netadr_t.fromString(ClientGlobals.rcon_address.string, Defines.PORT_SERVER);
        }
        Netchan.sendConnectionlessPacket(Defines.NS_CLIENT, to, ConnectionlessCommand.rcon, message.toString());
    };

    private static Command Disconnect_f = (List<String> args) -> Com.Error(Defines.ERR_DROP, "Disconnected from server");

    /**
     * Changing_f
     * 
     * Just sent as a hint to the client that they should drop to full console.
     */
    private static Command Changing_f = (List<String> args) -> {
        //ZOID
        //if we are downloading, we don't change!
        // This so we don't suddenly stop downloading a map

        if (ClientGlobals.cls.download != null)
            return;

        SCR.BeginLoadingPlaque();
        ClientGlobals.cls.state = Defines.ca_connected; // not active anymore, but
                                                  // not disconnected
        Com.Printf("\nChanging map...\n");
    };

    /**
     * Reconnect_f
     * 
     * The server is changing levels.
     */
    private static Command Reconnect_f = (List<String> args) -> {
        //ZOID
        //if we are downloading, we don't change! This so we don't suddenly
        // stop downloading a map
        if (ClientGlobals.cls.download != null)
            return;

        S.StopAllSounds();
        if (ClientGlobals.cls.state == Defines.ca_connected) {
            Com.Printf("reconnecting...\n");
            ClientGlobals.cls.state = Defines.ca_connected;
            ClientGlobals.cls.netchan.reliable.add(new StringCmdMessage(StringCmdMessage.NEW));
            return;
        }

        if (ClientGlobals.cls.servername != null) {
            if (ClientGlobals.cls.state >= Defines.ca_connected) {
                Disconnect();
                ClientGlobals.cls.connect_time = ClientGlobals.cls.realtime - 1500;
            } else
                ClientGlobals.cls.connect_time = -99999; // fire immediately

            ClientGlobals.cls.state = Defines.ca_connecting;
            Com.Printf("reconnecting...\n");
        }
    };

    /**
     * Skins_f
     * 
     * Load or download any custom player skins and models.
     */
    private static Command Skins_f = (List<String> args) -> {
        int i;

        for (i = 0; i < Defines.MAX_CLIENTS; i++) {
            if (ClientGlobals.cl.configstrings[Defines.CS_PLAYERSKINS + i] == null)
                continue;
            Com.Printf("client " + i + ": "
                    + ClientGlobals.cl.configstrings[Defines.CS_PLAYERSKINS + i]
                    + "\n");
            SCR.UpdateScreen();
            SendKeyEvents(); // pump message loop
            CL_parse.ParseClientinfo(i);
        }
    };

    /**
     * Userinfo_f
     */
    private static Command Userinfo_f = (List<String> args) -> {
        Com.Printf("User info settings:\n");
        Info.Print(Cvar.getInstance().Userinfo());
    };

    /**
     * Snd_Restart_f
     * 
     * Restart the sound subsystem so it can pick up new parameters and flush
     * all sounds.
     */
    static Command Snd_Restart_f = (List<String> args) -> {
        S.Shutdown();
        S.Init();
        CL_parse.RegisterSounds();
    };

    //	   ENV_CNT is map load, ENV_CNT+1 is first env map
    private static final int ENV_CNT = (Defines.CS_PLAYERSKINS + Defines.MAX_CLIENTS
            * CL.PLAYER_MULT);

    private static final int TEXTURE_CNT = (ENV_CNT + 13);

    private static String env_suf[] = { "rt", "bk", "lf", "ft", "up", "dn" };

    /**
     * The server will send this command right before allowing the client into
     * the server.
     */
    private static Command Precache_f = (List<String> args) -> {
        // Yet another hack to let old demos work the old precache sequence.
        if (args.size() < 2) {

            int iw[] = { 0 }; // for detecting cheater maps

            ClientGlobals.cm.CM_LoadMap(ClientGlobals.cl.configstrings[Defines.CS_MODELS + 1],
                    true, iw);

            CL_parse.RegisterSounds();
            CL_view.PrepRefresh();
            return;
        }

        CL.precache_check = Defines.CS_MODELS;
        CL.precache_spawncount = Lib.atoi(args.get(1));
        CL.precache_model = null;
        CL.precache_model_skin = 0;

        RequestNextDownload();
    };

    /*
     * =============== CL_Download_f
     *
     * Request a download from the server ===============
     */
    private static Command Download_f = (List<String> args) -> {
        String filename;

        if (args.size() != 2) {
            Com.Printf("Usage: download <filename>\n");
            return;
        }

        filename = args.get(1);

        if (filename.contains("..")) {
            Com.Printf("Refusing to download a path with ..\n");
            return;
        }

        if (FS.LoadFile(filename) != null) { // it exists, no need to
            // download
            Com.Printf("File already exists.\n");
            return;
        }

        ClientGlobals.cls.downloadname = filename;
        Com.Printf("Downloading " + ClientGlobals.cls.downloadname + "\n");

        // download to a temp name, and only rename
        // to the real name when done, so if interrupted
        // a runt file wont be left
        ClientGlobals.cls.downloadtempname = Com
                .StripExtension(ClientGlobals.cls.downloadname);
        ClientGlobals.cls.downloadtempname += ".tmp";
        ClientGlobals.cls.netchan.reliable.add(new StringCmdMessage(StringCmdMessage.DOWNLOAD + " " + ClientGlobals.cls.downloadname));
        ClientGlobals.cls.downloadnumber++;
    };


    private static int extratime;

    //	  ============================================================================

    /**
     * Shutdown
     * 
     * FIXME: this is a callback from Sys_Quit and Com_Error. It would be better
     * to run quit through here before the final handoff to the sys code.
     */
    private static boolean isdown = false;

    /**
     * WriteDemoMessage
     * 
     * Dumps the current net message, prefixed by the length
     */
    /*
    static void WriteDemoMessage() {
        int swlen;

        // the first eight bytes are just packet sequencing stuff
        swlen = Globals.net_message.cursize - 8;

        try {
            ClientGlobals.cls.demofile.writeInt(EndianHandler.swapInt(swlen));
            ClientGlobals.cls.demofile.write(Globals.net_message.data, 8, swlen);
        } catch (IOException e) {
        }

    }
    */
    /**
     * SendConnectPacket
     * 
     * We have gotten a challenge from the server, so try and connect.
     */
    private static void SendConnectPacket() {
        netadr_t adr = netadr_t.fromString(ClientGlobals.cls.servername, Defines.PORT_SERVER);
        if (adr == null) {
            Com.Printf("Bad server address\n");
            ClientGlobals.cls.connect_time = 0;
            return;
        }

        int port = (int) Cvar.getInstance().VariableValue("qport");
        Globals.userinfo_modified = false;

        Netchan.sendConnectionlessPacket(Defines.NS_CLIENT, adr, ConnectionlessCommand.connect,
                " " + Defines.PROTOCOL_VERSION + " " + port + " "
                + ClientGlobals.cls.challenge + " \"" + Cvar.getInstance().Userinfo() + "\"\n");
    }

    /**
     * CheckForResend
     * 
     * Resend a connect message if the last one has timed out.
     */
    private static void CheckForResend() {
        // if the local server is running and we aren't
        // then connect
        if (ClientGlobals.cls.state == Defines.ca_disconnected
                && Globals.server_state != ServerStates.SS_DEAD) {
            ClientGlobals.cls.state = Defines.ca_connecting;
            ClientGlobals.cls.servername = "localhost";
            // we don't need a challenge on the localhost
            SendConnectPacket();
            return;
        }

        // resend if we haven't gotten a reply yet
        if (ClientGlobals.cls.state != Defines.ca_connecting)
            return;

        if (ClientGlobals.cls.realtime - ClientGlobals.cls.connect_time < 3000)
            return;

        netadr_t adr = netadr_t.fromString(ClientGlobals.cls.servername, Defines.PORT_SERVER);
        if (adr == null) {
            Com.Printf("Bad server address\n");
            ClientGlobals.cls.state = Defines.ca_disconnected;
            return;
        }

        // for retransmit requests
        ClientGlobals.cls.connect_time = ClientGlobals.cls.realtime;

        Com.Printf("Connecting to " + ClientGlobals.cls.servername + "...\n");

        Netchan.sendConnectionlessPacket(Defines.NS_CLIENT, adr, ConnectionlessCommand.getchallenge, "\n");
    }

    /**
     * ClearState
     * 
     */
    static void ClearState() {
        S.StopAllSounds();
        CL_fx.ClearEffects();
        CL_tent.ClearTEnts();

        // wipe the entire cl structure

        ClientGlobals.cl = new client_state_t();
        for (int i = 0; i < ClientGlobals.cl_entities.length; i++) {
            ClientGlobals.cl_entities[i] = new centity_t();
        }

        ClientGlobals.cls.netchan.reliable.clear();
    }

    /**
     * Disconnect
     * 
     * Goes from a connected state to full screen console state Sends a
     * disconnect message to the server This is also called on Com_Error, so it
     * shouldn't cause any errors.
     */
    private static void Disconnect() {

        if (ClientGlobals.cls.state == Defines.ca_disconnected)
            return;

        if (ClientGlobals.cl_timedemo != null && ClientGlobals.cl_timedemo.value != 0.0f) {
            int time;

            time = (int) (Timer.Milliseconds() - ClientGlobals.cl.timedemo_start);
            if (time > 0)
                Com.Printf("%i frames, %3.1f seconds: %3.1f fps\n",
                        new Vargs(3).add(ClientGlobals.cl.timedemo_frames).add(
                                time / 1000.0).add(
                                ClientGlobals.cl.timedemo_frames * 1000.0 / time));
        }

        Math3D.VectorClear(ClientGlobals.cl.refdef.blend);
        
        ClientGlobals.re.CinematicSetPalette(null);

        Menu.ForceMenuOff();

        ClientGlobals.cls.connect_time = 0;

        SCR.StopCinematic();

        if (ClientGlobals.cls.demorecording)
            Stop_f.execute(Collections.emptyList());

        // send a disconnect message to the server

        byte[] data = new byte[128];
        sizebuf_t buf = new sizebuf_t();
        buf.init(data, data.length);
        new StringCmdMessage(StringCmdMessage.DISCONNECT).writeTo(buf);

        // fixme: was sending it 3 times
        ClientGlobals.cls.netchan.Transmit(List.of(new StringCmdMessage(StringCmdMessage.DISCONNECT)));


        ClearState();

        // stop download
        if (ClientGlobals.cls.download != null) {
            Lib.fclose(ClientGlobals.cls.download);
            ClientGlobals.cls.download = null;
        }

        ClientGlobals.cls.state = Defines.ca_disconnected;
    }

    /**
     * ParseStatusMessage
     * 
     * Handle a reply from a ping.
     */
    private static void ParseStatusMessage(netadr_t from, String s) {
        Com.Printf(s + "\n");
        Menu.AddToServerList(from, s);
    }

    /**
     * ConnectionlessPacket
     * 
     * Responses to broadcasts, etc
     */
    private static void CL_ConnectionlessPacket(NetworkPacket packet) {

        List<String> args = Cmd.TokenizeString(packet.connectionlessMessage, false);

        String c = args.get(0);
        
        Com.Println(packet.from + ": " + c);

        ConnectionlessCommand cmd = ConnectionlessCommand.fromString(c);

        // server connection
        switch (cmd) {
            case client_connect:
                if (ClientGlobals.cls.state == Defines.ca_connected) {
                    Com.Printf("Dup connect received.  Ignored.\n");
                    break;
                }
                Netchan.Setup(Defines.NS_CLIENT, ClientGlobals.cls.netchan, packet.from, ClientGlobals.cls.quakePort);
                ClientGlobals.cls.netchan.reliable.add(new StringCmdMessage(StringCmdMessage.NEW));
                ClientGlobals.cls.state = Defines.ca_connected;
                break;

            // server responding to a status broadcast
            case info:
                ParseStatusMessage(packet.from, packet.connectionlessParameters);
                break;

            // print command from somewhere
            case print:
                if (packet.connectionlessParameters.length() > 0)
                    Com.Printf(packet.connectionlessParameters);
                break;

            // ping from somewhere
            case ping:
                Netchan.sendConnectionlessPacket(Defines.NS_CLIENT, packet.from, ConnectionlessCommand.ack, "");
                break;

            // challenge from the server we are connecting to
            case challenge:
                ClientGlobals.cls.challenge = Lib.atoi(args.get(1));
                SendConnectPacket();
                break;

            default:
                Com.Printf("Unknown ServerConnectionlessCommand: " + c + '\n');
                break;
        }
    }


    /**
     * ReadPackets
     */
    private static void CL_ReadPackets() {
        while (true) {
            NetworkPacket networkPacket = NET.receiveNetworkPacket(
                    NET.ip_sockets[NS_CLIENT],
                    NET.ip_channels[NS_CLIENT],
                    NET.loopbacks[NS_CLIENT],
                    false);

            if (networkPacket == null)
                break;

            if (networkPacket.isConnectionless()) {
                CL_ConnectionlessPacket(networkPacket);
                continue;
            }

            if (ClientGlobals.cls.state == Defines.ca_disconnected
                    || ClientGlobals.cls.state == Defines.ca_connecting)
                continue; // dump it if not connected

            //
            // packet from server
            //
            if (!networkPacket.from.compareIp(ClientGlobals.cls.netchan.remote_address)) {
                Com.DPrintf(networkPacket.from + ":sequenced packet without connection\n");
                continue;
            }
            if (networkPacket.isValidForClient(ClientGlobals.cls.netchan)) {
                CL_parse.ParseServerMessage(networkPacket.parseBodyFromServer());
            } //else wasn't accepted for some reason
        }

        //
        // check timeout
        //
        if (ClientGlobals.cls.state >= Defines.ca_connected
                && ClientGlobals.cls.realtime - ClientGlobals.cls.netchan.last_received > ClientGlobals.cl_timeout.value * 1000) {
            if (++ClientGlobals.cl.timeoutcount > 5) // timeoutcount saves debugger
            {
                Com.Printf("\nServer connection timed out.\n");
                Disconnect();
            }
        } else
            ClientGlobals.cl.timeoutcount = 0;
    }

    //	  =============================================================================

    /**
     * FixUpGender_f
     */
    static void FixUpGender() {

        String sk;

        if (ClientGlobals.gender_auto.value != 0.0f) {

            if (ClientGlobals.gender.modified) {
                // was set directly, don't override the user
                ClientGlobals.gender.modified = false;
                return;
            }

            sk = ClientGlobals.skin.string;
            if (sk.startsWith("male") || sk.startsWith("cyborg"))
                Cvar.getInstance().Set("gender", "male");
            else if (sk.startsWith("female") || sk.startsWith("crackhor"))
                Cvar.getInstance().Set("gender", "female");
            else
                Cvar.getInstance().Set("gender", "none");
            ClientGlobals.gender.modified = false;
        }
    }

    static void RequestNextDownload() {
        int map_checksum = 0; // for detecting cheater maps

        qfiles.dmdl_t pheader;

        if (ClientGlobals.cls.state != Defines.ca_connected)
            return;

        cvar_t allowDownload = Cvar.getInstance().Get("allow_download", "1", Defines.CVAR_ARCHIVE);
        cvar_t allowDownloadMaps = Cvar.getInstance().Get("allow_download_maps", "1", Defines.CVAR_ARCHIVE);
        cvar_t allowDownloadModels = Cvar.getInstance().Get("allow_download_models", "1", Defines.CVAR_ARCHIVE);
        cvar_t allowDownloadSounds = Cvar.getInstance().Get("allow_download_sounds", "1", Defines.CVAR_ARCHIVE);
        cvar_t allowDownloadPlayers = Cvar.getInstance().Get( "allow_download_players","0", Defines.CVAR_ARCHIVE);

        if (allowDownload.value == 0 && CL.precache_check < ENV_CNT)
            CL.precache_check = ENV_CNT;

        //	  ZOID
        if (CL.precache_check == Defines.CS_MODELS) { // confirm map
            CL.precache_check = Defines.CS_MODELS + 2; // 0 isn't used
            if (allowDownloadMaps.value != 0)
                if (!CL_parse
                        .CheckOrDownloadFile(ClientGlobals.cl.configstrings[Defines.CS_MODELS + 1]))
                    return; // started a download
        }
        if (CL.precache_check >= Defines.CS_MODELS
                && CL.precache_check < Defines.CS_MODELS + Defines.MAX_MODELS) {
            if (allowDownloadModels.value != 0) {
                while (CL.precache_check < Defines.CS_MODELS
                        + Defines.MAX_MODELS
                        && ClientGlobals.cl.configstrings[CL.precache_check].length() > 0) {
                    if (ClientGlobals.cl.configstrings[CL.precache_check].charAt(0) == '*'
                            || ClientGlobals.cl.configstrings[CL.precache_check]
                                    .charAt(0) == '#') {
                        CL.precache_check++;
                        continue;
                    }
                    if (CL.precache_model_skin == 0) {
                        if (!CL_parse
                                .CheckOrDownloadFile(ClientGlobals.cl.configstrings[CL.precache_check])) {
                            CL.precache_model_skin = 1;
                            return; // started a download
                        }
                        CL.precache_model_skin = 1;
                    }

                    // checking for skins in the model
                    if (CL.precache_model == null) {

                        CL.precache_model = FS
                                .LoadFile(ClientGlobals.cl.configstrings[CL.precache_check]);
                        if (CL.precache_model == null) {
                            CL.precache_model_skin = 0;
                            CL.precache_check++;
                            continue; // couldn't load it
                        }
                        ByteBuffer bb = ByteBuffer.wrap(CL.precache_model);
                        bb.order(ByteOrder.LITTLE_ENDIAN);

                        int header = bb.getInt();

                        if (header != qfiles.IDALIASHEADER) {
                            // not an alias model
                            CL.precache_model = null;
                            CL.precache_model_skin = 0;
                            CL.precache_check++;
                            continue;
                        }
                        pheader = new qfiles.dmdl_t(ByteBuffer.wrap(
                                CL.precache_model).order(
                                ByteOrder.LITTLE_ENDIAN));
                        if (pheader.version != Defines.ALIAS_VERSION) {
                            CL.precache_check++;
                            CL.precache_model_skin = 0;
                            continue; // couldn't load it
                        }
                    }

                    pheader = new qfiles.dmdl_t(ByteBuffer.wrap(
                            CL.precache_model).order(ByteOrder.LITTLE_ENDIAN));

                    int num_skins = pheader.num_skins;

                    while (CL.precache_model_skin - 1 < num_skins) {
                        //Com.Printf("critical code section because of endian
                        // mess!\n");

                        String name = Lib.CtoJava(CL.precache_model,
                                pheader.ofs_skins
                                        + (CL.precache_model_skin - 1)
                                        * Defines.MAX_SKINNAME,
                                Defines.MAX_SKINNAME * num_skins);

                        if (!CL_parse.CheckOrDownloadFile(name)) {
                            CL.precache_model_skin++;
                            return; // started a download
                        }
                        CL.precache_model_skin++;
                    }
                    if (CL.precache_model != null) {
                        CL.precache_model = null;
                    }
                    CL.precache_model_skin = 0;
                    CL.precache_check++;
                }
            }
            CL.precache_check = Defines.CS_SOUNDS;
        }
        String fn;
        if (CL.precache_check >= Defines.CS_SOUNDS
                && CL.precache_check < Defines.CS_SOUNDS + Defines.MAX_SOUNDS) {
            if (allowDownloadSounds.value != 0) {
                if (CL.precache_check == Defines.CS_SOUNDS)
                    CL.precache_check++; // zero is blank
                while (CL.precache_check < Defines.CS_SOUNDS
                        + Defines.MAX_SOUNDS
                        && ClientGlobals.cl.configstrings[CL.precache_check].length() > 0) {
                    if (ClientGlobals.cl.configstrings[CL.precache_check].charAt(0) == '*') {
                        CL.precache_check++;
                        continue;
                    }
                    fn = "sound/"
                            + ClientGlobals.cl.configstrings[CL.precache_check++];
                    if (!CL_parse.CheckOrDownloadFile(fn))
                        return; // started a download
                }
            }
            CL.precache_check = Defines.CS_IMAGES;
        }
        if (CL.precache_check >= Defines.CS_IMAGES
                && CL.precache_check < Defines.CS_IMAGES + Defines.MAX_IMAGES) {
            if (CL.precache_check == Defines.CS_IMAGES)
                CL.precache_check++; // zero is blank

            while (CL.precache_check < Defines.CS_IMAGES + Defines.MAX_IMAGES
                    && ClientGlobals.cl.configstrings[CL.precache_check].length() > 0) {
                fn = "pics/" + ClientGlobals.cl.configstrings[CL.precache_check++]
                        + ".pcx";
                if (!CL_parse.CheckOrDownloadFile(fn))
                    return; // started a download
            }
            CL.precache_check = Defines.CS_PLAYERSKINS;
        }
        // skins are special, since a player has three things to download:
        // model, weapon model and skin
        // so precache_check is now *3
        if (CL.precache_check >= Defines.CS_PLAYERSKINS
                && CL.precache_check < Defines.CS_PLAYERSKINS
                        + Defines.MAX_CLIENTS * CL.PLAYER_MULT) {
            if (allowDownloadPlayers.value != 0) {
                while (CL.precache_check < Defines.CS_PLAYERSKINS
                        + Defines.MAX_CLIENTS * CL.PLAYER_MULT) {

                    int i, n;
                    //char model[MAX_QPATH], skin[MAX_QPATH], * p;
                    String model, skin;

                    i = (CL.precache_check - Defines.CS_PLAYERSKINS)
                            / CL.PLAYER_MULT;
                    n = (CL.precache_check - Defines.CS_PLAYERSKINS)
                            % CL.PLAYER_MULT;

                    if (ClientGlobals.cl.configstrings[Defines.CS_PLAYERSKINS + i]
                            .length() == 0) {
                        CL.precache_check = Defines.CS_PLAYERSKINS + (i + 1)
                                * CL.PLAYER_MULT;
                        continue;
                    }

                    int pos = ClientGlobals.cl.configstrings[Defines.CS_PLAYERSKINS + i].indexOf('\\');
                    
                    if (pos != -1)
                        pos++;
                    else
                        pos = 0;

                    int pos2 = ClientGlobals.cl.configstrings[Defines.CS_PLAYERSKINS + i].indexOf('\\', pos);
                    
                    if (pos2 == -1)
                        pos2 = ClientGlobals.cl.configstrings[Defines.CS_PLAYERSKINS + i].indexOf('/', pos);
                    
                    
                    model = ClientGlobals.cl.configstrings[Defines.CS_PLAYERSKINS + i]
                            .substring(pos, pos2);
                                        
                    skin = ClientGlobals.cl.configstrings[Defines.CS_PLAYERSKINS + i].substring(pos2 + 1);
                    
                    switch (n) {
                    case 0: // model
                        fn = "players/" + model + "/tris.md2";
                        if (!CL_parse.CheckOrDownloadFile(fn)) {
                            CL.precache_check = Defines.CS_PLAYERSKINS + i
                                    * CL.PLAYER_MULT + 1;
                            return; // started a download
                        }
                        n++;
                    /* FALL THROUGH */

                    case 1: // weapon model
                        fn = "players/" + model + "/weapon.md2";
                        if (!CL_parse.CheckOrDownloadFile(fn)) {
                            CL.precache_check = Defines.CS_PLAYERSKINS + i
                                    * CL.PLAYER_MULT + 2;
                            return; // started a download
                        }
                        n++;
                    /* FALL THROUGH */

                    case 2: // weapon skin
                        fn = "players/" + model + "/weapon.pcx";
                        if (!CL_parse.CheckOrDownloadFile(fn)) {
                            CL.precache_check = Defines.CS_PLAYERSKINS + i
                                    * CL.PLAYER_MULT + 3;
                            return; // started a download
                        }
                        n++;
                    /* FALL THROUGH */

                    case 3: // skin
                        fn = "players/" + model + "/" + skin + ".pcx";
                        if (!CL_parse.CheckOrDownloadFile(fn)) {
                            CL.precache_check = Defines.CS_PLAYERSKINS + i
                                    * CL.PLAYER_MULT + 4;
                            return; // started a download
                        }
                        n++;
                    /* FALL THROUGH */

                    case 4: // skin_i
                        fn = "players/" + model + "/" + skin + "_i.pcx";
                        if (!CL_parse.CheckOrDownloadFile(fn)) {
                            CL.precache_check = Defines.CS_PLAYERSKINS + i
                                    * CL.PLAYER_MULT + 5;
                            return; // started a download
                        }
                        // move on to next model
                        CL.precache_check = Defines.CS_PLAYERSKINS + (i + 1)
                                * CL.PLAYER_MULT;
                    }
                }
            }
            // precache phase completed
            CL.precache_check = ENV_CNT;
        }

        if (CL.precache_check == ENV_CNT) {
            CL.precache_check = ENV_CNT + 1;

            int iw[] = { map_checksum };

            ClientGlobals.cm.CM_LoadMap(ClientGlobals.cl.configstrings[Defines.CS_MODELS + 1],
                    true, iw);
            map_checksum = iw[0];

            if ((map_checksum ^ Lib
                    .atoi(ClientGlobals.cl.configstrings[Defines.CS_MAPCHECKSUM])) != 0) {
                Com
                        .Error(
                                Defines.ERR_DROP,
                                "Local map version differs from server: "
                                        + map_checksum
                                        + " != '"
                                        + ClientGlobals.cl.configstrings[Defines.CS_MAPCHECKSUM]
                                        + "'\n");
                return;
            }
        }

        if (CL.precache_check > ENV_CNT && CL.precache_check < TEXTURE_CNT) {
            if (allowDownload.value != 0
                    && allowDownloadMaps.value != 0) {
                while (CL.precache_check < TEXTURE_CNT) {
                    int n = CL.precache_check++ - ENV_CNT - 1;

                    if ((n & 1) != 0)
                        fn = "env/" + ClientGlobals.cl.configstrings[Defines.CS_SKY]
                                + env_suf[n / 2] + ".pcx";
                    else
                        fn = "env/" + ClientGlobals.cl.configstrings[Defines.CS_SKY]
                                + env_suf[n / 2] + ".tga";
                    if (!CL_parse.CheckOrDownloadFile(fn))
                        return; // started a download
                }
            }
            CL.precache_check = TEXTURE_CNT;
        }

        if (CL.precache_check == TEXTURE_CNT) {
            CL.precache_check = TEXTURE_CNT + 1;
            CL.precache_tex = 0;
        }

        // confirm existance of textures, download any that don't exist
        if (CL.precache_check == TEXTURE_CNT + 1) {
            // from qcommon/cmodel.c
            // extern int numtexinfo;
            // extern mapsurface_t map_surfaces[];

            if (allowDownload.value != 0
                    && allowDownloadMaps.value != 0) {
                while (CL.precache_tex < ClientGlobals.cm.numtexinfo) {

                    fn = "textures/" + ClientGlobals.cm.map_surfaces[CL.precache_tex++].rname
                            + ".wal";
                    if (!CL_parse.CheckOrDownloadFile(fn))
                        return; // started a download
                }
            }
            CL.precache_check = TEXTURE_CNT + 999;
        }

        //	  ZOID
        CL_parse.RegisterSounds();
        CL_view.PrepRefresh();

        ClientGlobals.cls.netchan.reliable.add(new StringCmdMessage(StringCmdMessage.BEGIN + " " + CL.precache_spawncount + "\n"));
    }

    /**
     * InitLocal
     */
    private static void InitLocal() {
        ClientGlobals.cls.state = Defines.ca_disconnected;
        ClientGlobals.cls.realtime = Timer.Milliseconds();

        CL_input.InitInput();

        Cvar.getInstance().Get("adr0", "", Defines.CVAR_ARCHIVE);
        Cvar.getInstance().Get("adr1", "", Defines.CVAR_ARCHIVE);
        Cvar.getInstance().Get("adr2", "", Defines.CVAR_ARCHIVE);
        Cvar.getInstance().Get("adr3", "", Defines.CVAR_ARCHIVE);
        Cvar.getInstance().Get("adr4", "", Defines.CVAR_ARCHIVE);
        Cvar.getInstance().Get("adr5", "", Defines.CVAR_ARCHIVE);
        Cvar.getInstance().Get("adr6", "", Defines.CVAR_ARCHIVE);
        Cvar.getInstance().Get("adr7", "", Defines.CVAR_ARCHIVE);
        Cvar.getInstance().Get("adr8", "", Defines.CVAR_ARCHIVE);

        //
        // register our variables
        //
        ClientGlobals.cl_stereo_separation = Cvar.getInstance().Get("cl_stereo_separation", "0.4",
                Defines.CVAR_ARCHIVE);
        ClientGlobals.cl_stereo = Cvar.getInstance().Get("cl_stereo", "0", 0);

        ClientGlobals.cl_add_blend = Cvar.getInstance().Get("cl_blend", "1", 0);
        ClientGlobals.cl_add_lights = Cvar.getInstance().Get("cl_lights", "1", 0);
        ClientGlobals.cl_add_particles = Cvar.getInstance().Get("cl_particles", "1", 0);
        ClientGlobals.cl_add_entities = Cvar.getInstance().Get("cl_entities", "1", 0);
        ClientGlobals.cl_gun = Cvar.getInstance().Get("cl_gun", "1", 0);
        ClientGlobals.cl_footsteps = Cvar.getInstance().Get("cl_footsteps", "1", 0);
        ClientGlobals.cl_noskins = Cvar.getInstance().Get("cl_noskins", "0", 0);
        ClientGlobals.cl_autoskins = Cvar.getInstance().Get("cl_autoskins", "0", 0);
        ClientGlobals.cl_predict = Cvar.getInstance().Get("cl_predict", "1", 0);

        ClientGlobals.cl_maxfps = Cvar.getInstance().Get("cl_maxfps", "90", 0);

        ClientGlobals.cl_upspeed = Cvar.getInstance().Get("cl_upspeed", "200", 0);
        ClientGlobals.cl_forwardspeed = Cvar.getInstance().Get("cl_forwardspeed", "200", 0);
        ClientGlobals.cl_sidespeed = Cvar.getInstance().Get("cl_sidespeed", "200", 0);
        ClientGlobals.cl_yawspeed = Cvar.getInstance().Get("cl_yawspeed", "140", 0);
        ClientGlobals.cl_pitchspeed = Cvar.getInstance().Get("cl_pitchspeed", "150", 0);
        ClientGlobals.cl_anglespeedkey = Cvar.getInstance().Get("cl_anglespeedkey", "1.5", 0);
        
        // third person view
        ClientGlobals.cl_3rd = Cvar.getInstance().Get("cl_3rd", "0", Defines.CVAR_ARCHIVE);
        ClientGlobals.cl_3rd_angle = Cvar.getInstance().Get("cl_3rd_angle", "30", Defines.CVAR_ARCHIVE);
        ClientGlobals.cl_3rd_dist = Cvar.getInstance().Get("cl_3rd_dist", "50", Defines.CVAR_ARCHIVE);

        // CDawg hud map, sfranzyshen 
        ClientGlobals.cl_map = Cvar.getInstance().Get("cl_map", "0", Defines.CVAR_ARCHIVE); // CDawg hud map, sfranzyshen
        ClientGlobals.cl_map_zoom = Cvar.getInstance().Get("cl_map_zoom", "300", Defines.CVAR_ARCHIVE); // CDawg hud map, sfranzyshen

        ClientGlobals.cl_run = Cvar.getInstance().Get("cl_run", "0", Defines.CVAR_ARCHIVE);
        ClientGlobals.lookspring = Cvar.getInstance().Get("lookspring", "0", Defines.CVAR_ARCHIVE);
        ClientGlobals.lookstrafe = Cvar.getInstance().Get("lookstrafe", "0", Defines.CVAR_ARCHIVE);
        ClientGlobals.sensitivity = Cvar.getInstance().Get("sensitivity", "3", Defines.CVAR_ARCHIVE);

        ClientGlobals.m_pitch = Cvar.getInstance().Get("m_pitch", "0.022", Defines.CVAR_ARCHIVE);
        ClientGlobals.m_yaw = Cvar.getInstance().Get("m_yaw", "0.022", 0);
        ClientGlobals.m_forward = Cvar.getInstance().Get("m_forward", "1", 0);
        ClientGlobals.m_side = Cvar.getInstance().Get("m_side", "1", 0);

        ClientGlobals.cl_shownet = Cvar.getInstance().Get("cl_shownet", "0", 0);
        ClientGlobals.cl_showmiss = Cvar.getInstance().Get("cl_showmiss", "0", 0);
        ClientGlobals.cl_showclamp = Cvar.getInstance().Get("showclamp", "0", 0);
        ClientGlobals.cl_timeout = Cvar.getInstance().Get("cl_timeout", "120", 0);
        ClientGlobals.cl_paused = Cvar.getInstance().Get("paused", "0", 0);
        ClientGlobals.cl_timedemo = Cvar.getInstance().Get("timedemo", "0", 0);

        ClientGlobals.rcon_client_password = Cvar.getInstance().Get("rcon_password", "", 0);
        ClientGlobals.rcon_address = Cvar.getInstance().Get("rcon_address", "", 0);

        ClientGlobals.cl_lightlevel = Cvar.getInstance().Get("r_lightlevel", "0", 0);

        //
        // userinfo
        //
        ClientGlobals.info_password = Cvar.getInstance().Get("password", "", Defines.CVAR_USERINFO);
        ClientGlobals.info_spectator = Cvar.getInstance().Get("spectator", "0",
                Defines.CVAR_USERINFO);
        ClientGlobals.name = Cvar.getInstance().Get("name", "unnamed", Defines.CVAR_USERINFO
                | Defines.CVAR_ARCHIVE);
        ClientGlobals.skin = Cvar.getInstance().Get("skin", "male/grunt", Defines.CVAR_USERINFO
                | Defines.CVAR_ARCHIVE);
        ClientGlobals.rate = Cvar.getInstance().Get("rate", "25000", Defines.CVAR_USERINFO
                | Defines.CVAR_ARCHIVE); // FIXME
        ClientGlobals.msg = Cvar.getInstance().Get("msg", "1", Defines.CVAR_USERINFO
                | Defines.CVAR_ARCHIVE);
        ClientGlobals.hand = Cvar.getInstance().Get("hand", "0", Defines.CVAR_USERINFO
                | Defines.CVAR_ARCHIVE);
        ClientGlobals.fov = Cvar.getInstance().Get("fov", "90", Defines.CVAR_USERINFO
                | Defines.CVAR_ARCHIVE);
        ClientGlobals.gender = Cvar.getInstance().Get("gender", "male", Defines.CVAR_USERINFO
                | Defines.CVAR_ARCHIVE);
        ClientGlobals.gender_auto = Cvar.getInstance().Get("gender_auto", "1", Defines.CVAR_ARCHIVE);
        ClientGlobals.gender.modified = false; // clear this so we know when user sets
                                         // it manually

        ClientGlobals.cl_vwep = Cvar.getInstance().Get("cl_vwep", "1", Defines.CVAR_ARCHIVE);

        //
        // register our commands
        //
        Cmd.AddCommand("cmd", ForwardToServer_f);
        Cmd.AddCommand("pause", Pause_f);
        Cmd.AddCommand("skins", Skins_f);

        Cmd.AddCommand("userinfo", Userinfo_f);
        Cmd.AddCommand("snd_restart", Snd_Restart_f);

        Cmd.AddCommand("changing", Changing_f);
        Cmd.AddCommand("disconnect", Disconnect_f);
        Cmd.AddCommand("record", Record_f);
        Cmd.AddCommand("stop", Stop_f);

        Cmd.AddCommand("quit", Quit_f);

        Cmd.AddCommand("connect", Connect_f);
        Cmd.AddCommand("reconnect", Reconnect_f);

        Cmd.AddCommand("rcon", Rcon_f);

        Cmd.AddCommand("precache", Precache_f);

        Cmd.AddCommand("download", Download_f);

        //
        // forward to server commands
        //
        // the only thing this does is allow command completion
        // to work -- all unknown commands are automatically
        // forwarded to the server
        // Cmd.AddCommand("wave", null);
        // Cmd.AddCommand("inven", null);
        // Cmd.AddCommand("kill", null);
        // Cmd.AddCommand("use", null);
        // Cmd.AddCommand("drop", null);
        // Cmd.AddCommand("say", null);
        // Cmd.AddCommand("say_team", null);
        // Cmd.AddCommand("info", null);
        // Cmd.AddCommand("prog", null);
        // Cmd.AddCommand("give", null);
        // Cmd.AddCommand("god", null);
        // Cmd.AddCommand("notarget", null);
        // Cmd.AddCommand("noclip", null);
        // Cmd.AddCommand("invuse", null);
        // Cmd.AddCommand("invprev", null);
        // Cmd.AddCommand("invnext", null);
        // Cmd.AddCommand("invdrop", null);
        // Cmd.AddCommand("weapnext", null);
        // Cmd.AddCommand("weapprev", null);

    }

    /**
     * WriteConfiguration
     * 
     * Writes key bindings and archived cvars to config.cfg.
     */
    public static void WriteConfiguration() {
        RandomAccessFile f;
        String path;

//        if (Globals.cls.state == Defines.ca_uninitialized)
//            return;

        path = FS.getWriteDir() + "/config.cfg";
        f = Lib.fopen(path, "rw");
        if (f == null) {
            Com.Printf("Couldn't write config.cfg.\n");
            return;
        }
        try {
            f.seek(0);
            f.setLength(0);
        } catch (IOException e1) {
        }
        try {
            f.writeBytes("// generated by quake, do not modify\n");
        } catch (IOException e) {
        }

        Key.WriteBindings(f);
        Lib.fclose(f);
        Cvar.getInstance().writeArchiveVariables(path);
    }

    /**
     * FixCvarCheats
     */
    private static void FixCvarCheats() {
        int i;
        CL.cheatvar_t var;

        if ("1".equals(ClientGlobals.cl.configstrings[Defines.CS_MAXCLIENTS])
                || 0 == ClientGlobals.cl.configstrings[Defines.CS_MAXCLIENTS]
                        .length())
            return; // single player can cheat

        // find all the cvars if we haven't done it yet
        if (0 == CL.numcheatvars) {
            while (CL.cheatvars[CL.numcheatvars].name != null) {
                CL.cheatvars[CL.numcheatvars].var = Cvar.getInstance().Get(
                        CL.cheatvars[CL.numcheatvars].name,
                        CL.cheatvars[CL.numcheatvars].value, 0);
                CL.numcheatvars++;
            }
        }

        // make sure they are all set to the proper values
        for (i = 0; i < CL.numcheatvars; i++) {
            var = CL.cheatvars[i];
            if (!var.var.string.equals(var.value)) {
                Cvar.getInstance().Set(var.name, var.value);
            }
        }
    }

    //	  =============================================================

    /**
     * SendCommand
     */
    private static void SendCommand() {
        // get new key events
        SendKeyEvents();

        // allow mice or other external controllers to add commands
        IN.Commands();

        // process console commands
        Cbuf.Execute();

        // fix any cheating cvars
        FixCvarCheats();

        // send intentions now
        CL_input.SendCmd();

        // resend a connection request if necessary
        CheckForResend();
    }

    //	private static int lasttimecalled;

    /**
     * Frame
     */
    public static void Frame(int msec) {
        extratime += msec;

        if (ClientGlobals.cl_timedemo.value == 0.0f) {
            if (ClientGlobals.cls.state == Defines.ca_connected && extratime < 100) {
                return; // don't flood packets out while connecting
            }
            if (extratime < 1000 / ClientGlobals.cl_maxfps.value) {
                return; // framerate is too high
            }
        }

        // let the mouse activate or deactivate
        IN.Frame();

        // decide the simulation time
        ClientGlobals.cls.frametime = extratime / 1000.0f;
        ClientGlobals.cl.time += extratime;
        ClientGlobals.cls.realtime = Globals.curtime;

        extratime = 0;

        if (ClientGlobals.cls.frametime > (1.0f / 5))
            ClientGlobals.cls.frametime = (1.0f / 5);

        // if in the debugger last frame, don't timeout
        if (msec > 5000)
            ClientGlobals.cls.netchan.last_received = Timer.Milliseconds();

        // fetch results from server
        CL_ReadPackets();

        // send a new command message to the server
        SendCommand();

        // predict all unacknowledged movements
        CL_pred.PredictMovement();

        // allow rendering DLL change
        VID.CheckChanges();
        if (!ClientGlobals.cl.refresh_prepped
                && ClientGlobals.cls.state == Defines.ca_active) {
            CL_view.PrepRefresh();
            // force GC after level loading
            // but not on playing a cinematic
            if (ClientGlobals.cl.cinematictime == 0) System.gc();
        }

        boolean changed = false;
        
        if (Main.r_worldmodel != null)
        {
            if (numleafs != Main.r_worldmodel.numleafs)
            {
        	numleafs = Main.r_worldmodel.numleafs;
        	changed = true;
            }
        }
        if (x != ClientGlobals.cl.frame.playerstate.pmove.origin[0])
        {
            x = ClientGlobals.cl.frame.playerstate.pmove.origin[0];
            changed = true;
        }
        
        if (y != ClientGlobals.cl.frame.playerstate.pmove.origin[1])
        {
            y = ClientGlobals.cl.frame.playerstate.pmove.origin[1];
            changed = true;
        }
        
        if (z != ClientGlobals.cl.frame.playerstate.pmove.origin[2])
        {
            z = ClientGlobals.cl.frame.playerstate.pmove.origin[2];
            changed = true;
        }
        
        if (viewcluster != Main.r_viewcluster)
        {
            viewcluster = Main.r_viewcluster;
            changed = true;
        }
        
        if (changed)
        {
            String tmp = " x=" + x + ", y=" + y + 
            	", z=" + z + ", leaf=" + viewcluster + "/" +
            	numleafs +"\n";
            
            Com.DPrintf(tmp);
        }
        
        SCR.UpdateScreen();

        // update audio
        S.Update(ClientGlobals.cl.refdef.vieworg, ClientGlobals.cl.v_forward,
                ClientGlobals.cl.v_right, ClientGlobals.cl.v_up);
        
        CDAudio.Update(); //sfranzyshen
        
        // advance local effects for next frame
        CL_fx.RunDLights();
        CL_fx.RunLightStyles();
        SCR.RunCinematic();
        SCR.RunConsole();

        ClientGlobals.cls.framecount++;
        if (ClientGlobals.cls.state != Defines.ca_active
                || ClientGlobals.cls.key_dest != Defines.key_game) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
            }
        }
    }

    private static int numleafs = 0;
    private static short x,y,z;
    private static int viewcluster;
    
    /**
     * Shutdown
     */
    public static void Shutdown() {

        if (isdown) {
            System.out.print("recursive shutdown\n");
            return;
        }
        isdown = true;

        WriteConfiguration();
        
        CDAudio.Shutdown();
        S.Shutdown();
        IN.Shutdown();
        VID.Shutdown();
    }

    /**
     * Initialize client subsystem.
     */
    public static void Init() {
        if (Globals.dedicated.value != 0.0f)
            return; // nothing running on the client

        // all archived variables will now be loaded

        Console.Init(); //ok

        S.Init(); //empty
        VID.Init();

        V.Init();
        Cmd.AddCommand("cl_drop", args -> CL.Drop());
        Cmd.AddCommand("cl_shutdown", args -> CL.Shutdown());

        Menu.Init();

        SCR.Init();
        //Globals.cls.disable_screen = 1.0f; // don't draw yet

        CDAudio.Init();
        InitLocal();
        IN.Init();

        FS.ExecAutoexec();
        Cbuf.Execute();
    }

    /**
     * Called after an ERR_DROP was thrown.
     */
    public static void Drop() {
        if (ClientGlobals.cls.state == Defines.ca_uninitialized)
            return;
        if (ClientGlobals.cls.state == Defines.ca_disconnected)
            return;

        Disconnect();

        // drop loading plaque unless this is the initial game start
        if (ClientGlobals.cls.disable_servercount != -1)
            SCR.EndLoadingPlaque(); // get rid of loading plaque
    }
}