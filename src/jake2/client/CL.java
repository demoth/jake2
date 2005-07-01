/*
 * CL.java
 * Copyright (C) 2004
 * 
 * $Id: CL.java,v 1.23 2005-07-01 14:20:48 hzi Exp $
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

import jake2.Defines;
import jake2.Globals;
import jake2.game.*;
import jake2.qcommon.*;
import jake2.server.SV_MAIN;
import jake2.sound.S;
import jake2.sys.*;
import jake2.util.*;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * CL
 */
public final class CL {
    
    static int precache_check; // for autodownload of precache items

    static int precache_spawncount;

    static int precache_tex;

    static int precache_model_skin;

    static byte precache_model[]; // used for skin checking in alias models

    public static final int PLAYER_MULT = 5;

    /*
     * ================== CL_FixCvarCheats
     * 
     * ==================
     */
    public static class cheatvar_t {
        String name;

        String value;

        cvar_t var;
    }

    public static String cheatvarsinfo[][] = { { "timescale", "1" },
            { "timedemo", "0" }, { "r_drawworld", "1" },
            { "cl_testlights", "0" }, { "r_fullbright", "0" },
            { "r_drawflat", "0" }, { "paused", "0" }, { "fixedtime", "0" },
            { "sw_draworder", "0" }, { "gl_lightmap", "0" },
            { "gl_saturatelighting", "0" }, { null, null } };

    public static cheatvar_t cheatvars[];

    static {
        cheatvars = new cheatvar_t[cheatvarsinfo.length];
        for (int n = 0; n < cheatvarsinfo.length; n++) {
            cheatvars[n] = new cheatvar_t();
            cheatvars[n].name = cheatvarsinfo[n][0];
            cheatvars[n].value = cheatvarsinfo[n][1];
        }
    }

    static int numcheatvars;

    /*
     * ==================== CL_Stop_f
     * 
     * stop recording a demo ====================
     */
    static xcommand_t Stop_f = new xcommand_t() {
        public void execute() {
            try {

                int len;

                if (!Globals.cls.demorecording) {
                    Com.Printf("Not recording a demo.\n");
                    return;
                }

                //	   finish up
                len = -1;
                Globals.cls.demofile.writeInt(EndianHandler.swapInt(len));
                Globals.cls.demofile.close();
                Globals.cls.demofile = null;
                Globals.cls.demorecording = false;
                Com.Printf("Stopped demo.\n");
            } catch (IOException e) {
            }
        }
    };

    /*
     * ==================== CL_Record_f
     * 
     * record <demoname>
     * 
     * Begins recording a demo from the current position ====================
     */
    static entity_state_t nullstate = new entity_state_t(null);

    static xcommand_t Record_f = new xcommand_t() {
        public void execute() {
            try {
                String name;
                byte buf_data[] = new byte[Defines.MAX_MSGLEN];
                sizebuf_t buf = new sizebuf_t();
                int i;
                entity_state_t ent;

                if (Cmd.Argc() != 2) {
                    Com.Printf("record <demoname>\n");
                    return;
                }

                if (Globals.cls.demorecording) {
                    Com.Printf("Already recording.\n");
                    return;
                }

                if (Globals.cls.state != Defines.ca_active) {
                    Com.Printf("You must be in a level to record.\n");
                    return;
                }

                //
                // open the demo file
                //
                name = FS.Gamedir() + "/demos/" + Cmd.Argv(1) + ".dm2";

                Com.Printf("recording to " + name + ".\n");
                FS.CreatePath(name);
                Globals.cls.demofile = new RandomAccessFile(name, "rw");
                if (Globals.cls.demofile == null) {
                    Com.Printf("ERROR: couldn't open.\n");
                    return;
                }
                Globals.cls.demorecording = true;

                // don't start saving messages until a non-delta compressed
                // message is received
                Globals.cls.demowaiting = true;

                //
                // write out messages to hold the startup information
                //
                SZ.Init(buf, buf_data, Defines.MAX_MSGLEN);

                // send the serverdata
                MSG.WriteByte(buf, Defines.svc_serverdata);
                MSG.WriteInt(buf, Defines.PROTOCOL_VERSION);
                MSG.WriteInt(buf, 0x10000 + Globals.cl.servercount);
                MSG.WriteByte(buf, 1); // demos are always attract loops
                MSG.WriteString(buf, Globals.cl.gamedir);
                MSG.WriteShort(buf, Globals.cl.playernum);

                MSG.WriteString(buf, Globals.cl.configstrings[Defines.CS_NAME]);

                // configstrings
                for (i = 0; i < Defines.MAX_CONFIGSTRINGS; i++) {
                    if (Globals.cl.configstrings[i].length() > 0) {
                        if (buf.cursize + Globals.cl.configstrings[i].length()
                                + 32 > buf.maxsize) { 
                            // write it out
                            Globals.cls.demofile.writeInt(EndianHandler.swapInt(buf.cursize));
                            Globals.cls.demofile
                                    .write(buf.data, 0, buf.cursize);
                            buf.cursize = 0;
                        }

                        MSG.WriteByte(buf, Defines.svc_configstring);
                        MSG.WriteShort(buf, i);
                        MSG.WriteString(buf, Globals.cl.configstrings[i]);
                    }

                }

                // baselines
                nullstate.clear();
                for (i = 0; i < Defines.MAX_EDICTS; i++) {
                    ent = Globals.cl_entities[i].baseline;
                    if (ent.modelindex == 0)
                        continue;

                    if (buf.cursize + 64 > buf.maxsize) { // write it out
                        Globals.cls.demofile.writeInt(EndianHandler.swapInt(buf.cursize));
                        Globals.cls.demofile.write(buf.data, 0, buf.cursize);
                        buf.cursize = 0;
                    }

                    MSG.WriteByte(buf, Defines.svc_spawnbaseline);
                    MSG.WriteDeltaEntity(nullstate,
                            Globals.cl_entities[i].baseline, buf, true, true);
                }

                MSG.WriteByte(buf, Defines.svc_stufftext);
                MSG.WriteString(buf, "precache\n");

                // write it to the demo file
                Globals.cls.demofile.writeInt(EndianHandler.swapInt(buf.cursize));
                Globals.cls.demofile.write(buf.data, 0, buf.cursize);
                // the rest of the demo file will be individual frames

            } catch (IOException e) {
            }
        }
    };

    /*
     * ================== CL_ForwardToServer_f ==================
     */
    static xcommand_t ForwardToServer_f = new xcommand_t() {
        public void execute() {
            if (Globals.cls.state != Defines.ca_connected
                    && Globals.cls.state != Defines.ca_active) {
                Com.Printf("Can't \"" + Cmd.Argv(0) + "\", not connected\n");
                return;
            }

            // don't forward the first argument
            if (Cmd.Argc() > 1) {
                MSG.WriteByte(Globals.cls.netchan.message,
                        Defines.clc_stringcmd);
                SZ.Print(Globals.cls.netchan.message, Cmd.Args());
            }
        }
    };

    /*
     * ================== CL_Pause_f ==================
     */
    static xcommand_t Pause_f = new xcommand_t() {
        public void execute() {
            // never pause in multiplayer

            if (Cvar.VariableValue("maxclients") > 1
                    || Globals.server_state == 0) {
                Cvar.SetValue("paused", 0);
                return;
            }

            Cvar.SetValue("paused", Globals.cl_paused.value);
        }
    };

    /*
     * ================== CL_Quit_f ==================
     */
    static xcommand_t Quit_f = new xcommand_t() {
        public void execute() {
            Disconnect();
            Com.Quit();
        }
    };

    /*
     * ================ CL_Connect_f
     * 
     * ================
     */
    static xcommand_t Connect_f = new xcommand_t() {
        public void execute() {
            String server;

            if (Cmd.Argc() != 2) {
                Com.Printf("usage: connect <server>\n");
                return;
            }

            if (Globals.server_state != 0) {
                // if running a local server, kill it and reissue
                SV_MAIN.SV_Shutdown("Server quit\n", false);
            } else {
                Disconnect();
            }

            server = Cmd.Argv(1);

            NET.Config(true); // allow remote

            Disconnect();

            Globals.cls.state = Defines.ca_connecting;
            //strncpy (cls.servername, server, sizeof(cls.servername)-1);
            Globals.cls.servername = server;
            Globals.cls.connect_time = -99999;
            // CL_CheckForResend() will fire immediately
        }
    };

    /*
     * ===================== CL_Rcon_f
     * 
     * Send the rest of the command line over as an unconnected command.
     * =====================
     */
    static xcommand_t Rcon_f = new xcommand_t() {
        public void execute() {
            StringBuffer message = new StringBuffer(1024);
            int i;
            netadr_t to = new netadr_t();

            if (Globals.rcon_client_password.string == null) {
                Com
                        .Printf("You must set 'rcon_password' before\nissuing an rcon command.\n");
                return;
            }

            message.append((char) 255);
            message.append((char) 255);
            message.append((char) 255);
            message.append((char) 255);

            NET.Config(true); // allow remote

            //strcat (message, "rcon ");
            message.append("rcon ");

            //strcat (message, rcon_client_password.string);
            message.append(Globals.rcon_client_password.string);
            //strcat (message, " ");
            message.append(" ");

            for (i = 1; i < Cmd.Argc(); i++) {
                //strcat (message, Cmd.Argv(i));
                message.append(Cmd.Argv(i));
                //strcat (message, " ");
                message.append(" ");
            }

            if (Globals.cls.state >= Defines.ca_connected)
                to = Globals.cls.netchan.remote_address;
            else {
                if (Globals.rcon_address.string.length() == 0) {
                    Com
                            .Printf("You must either be connected,\nor set the 'rcon_address' cvar\nto issue rcon commands\n");

                    return;
                }
                NET.StringToAdr(Globals.rcon_address.string, to);
                if (to.port == 0)
                    //to.port = BigShort (PORT_SERVER);
                    to.port = Defines.PORT_SERVER;
            }
            message.append('\0');
            String b = message.toString();
            NET.SendPacket(Defines.NS_CLIENT, b.length(), b.getBytes(), to);
        }
    };

    static xcommand_t Disconnect_f = new xcommand_t() {
        public void execute() {
            Com.Error(Defines.ERR_DROP, "Disconnected from server");
        }
    };

    /*
     * ================= CL_Changing_f
     * 
     * Just sent as a hint to the client that they should drop to full console
     * =================
     */
    static xcommand_t Changing_f = new xcommand_t() {
        public void execute() {
            //ZOID
            //if we are downloading, we don't change!
            // This so we don't suddenly stop downloading a map

            if (Globals.cls.download != null)
                return;

            SCR.BeginLoadingPlaque();
            Globals.cls.state = Defines.ca_connected; // not active anymore, but
                                                      // not disconnected
            Com.Printf("\nChanging map...\n");
        }
    };

    /*
     * ================= CL_Reconnect_f
     * 
     * The server is changing levels =================
     */
    static xcommand_t Reconnect_f = new xcommand_t() {
        public void execute() {
            //ZOID
            //if we are downloading, we don't change! This so we don't suddenly
            // stop downloading a map
            if (Globals.cls.download != null)
                return;

            S.StopAllSounds();
            if (Globals.cls.state == Defines.ca_connected) {
                Com.Printf("reconnecting...\n");
                Globals.cls.state = Defines.ca_connected;
                MSG.WriteChar(Globals.cls.netchan.message,
                        Defines.clc_stringcmd);
                MSG.WriteString(Globals.cls.netchan.message, "new");
                return;
            }

            if (Globals.cls.servername != null) {
                if (Globals.cls.state >= Defines.ca_connected) {
                    Disconnect();
                    Globals.cls.connect_time = Globals.cls.realtime - 1500;
                } else
                    Globals.cls.connect_time = -99999; // fire immediately

                Globals.cls.state = Defines.ca_connecting;
                Com.Printf("reconnecting...\n");
            }
        }
    };

    /*
     * ================= CL_PingServers_f =================
     */
    static xcommand_t PingServers_f = new xcommand_t() {
        public void execute() {
            int i;
            netadr_t adr = new netadr_t();
            //char name[32];
            String name;
            String adrstring;
            cvar_t noudp;
            cvar_t noipx;

            NET.Config(true); // allow remote

            // send a broadcast packet
            Com.Printf("pinging broadcast...\n");

            noudp = Cvar.Get("noudp", "0", Defines.CVAR_NOSET);
            if (noudp.value == 0.0f) {
                adr.type = Defines.NA_BROADCAST;
                adr.port = Defines.PORT_SERVER;
                //adr.port = BigShort(PORT_SERVER);
                Netchan.OutOfBandPrint(Defines.NS_CLIENT, adr, "info "
                        + Defines.PROTOCOL_VERSION);
            }

            // we use no IPX
            noipx = Cvar.Get("noipx", "1", Defines.CVAR_NOSET);
            if (noipx.value == 0.0f) {
                adr.type = Defines.NA_BROADCAST_IPX;
                //adr.port = BigShort(PORT_SERVER);
                adr.port = Defines.PORT_SERVER;
                Netchan.OutOfBandPrint(Defines.NS_CLIENT, adr, "info "
                        + Defines.PROTOCOL_VERSION);
            }

            // send a packet to each address book entry
            for (i = 0; i < 16; i++) {
                //Com_sprintf (name, sizeof(name), "adr%i", i);
                name = "adr" + i;
                adrstring = Cvar.VariableString(name);
                if (adrstring == null || adrstring.length() == 0)
                    continue;

                Com.Printf("pinging " + adrstring + "...\n");
                if (!NET.StringToAdr(adrstring, adr)) {
                    Com.Printf("Bad address: " + adrstring + "\n");
                    continue;
                }
                if (adr.port == 0)
                    //adr.port = BigShort(PORT_SERVER);
                    adr.port = Defines.PORT_SERVER;
                Netchan.OutOfBandPrint(Defines.NS_CLIENT, adr, "info "
                        + Defines.PROTOCOL_VERSION);
            }
        }
    };

    /*
     * ================= CL_Skins_f
     * 
     * Load or download any custom player skins and models =================
     */
    static xcommand_t Skins_f = new xcommand_t() {
        public void execute() {
            int i;

            for (i = 0; i < Defines.MAX_CLIENTS; i++) {
                if (Globals.cl.configstrings[Defines.CS_PLAYERSKINS + i] == null)
                    continue;
                Com.Printf("client " + i + ": "
                        + Globals.cl.configstrings[Defines.CS_PLAYERSKINS + i]
                        + "\n");
                SCR.UpdateScreen();
                Sys.SendKeyEvents(); // pump message loop
                CL_parse.ParseClientinfo(i);
            }
        }
    };

    /*
     * ============== CL_Userinfo_f ==============
     */
    static xcommand_t Userinfo_f = new xcommand_t() {
        public void execute() {
            Com.Printf("User info settings:\n");
            Info.Print(Cvar.Userinfo());
        }
    };

    /*
     * ================= CL_Snd_Restart_f
     * 
     * Restart the sound subsystem so it can pick up new parameters and flush
     * all sounds =================
     */
    static xcommand_t Snd_Restart_f = new xcommand_t() {
        public void execute() {
            S.Shutdown();
            S.Init();
            CL_parse.RegisterSounds();
        }
    };

    //	   ENV_CNT is map load, ENV_CNT+1 is first env map
    public static final int ENV_CNT = (Defines.CS_PLAYERSKINS + Defines.MAX_CLIENTS
            * CL.PLAYER_MULT);

    public static final int TEXTURE_CNT = (ENV_CNT + 13);

    static String env_suf[] = { "rt", "bk", "lf", "ft", "up", "dn" };

    /*
     * ================= CL_Precache_f
     * 
     * The server will send this command right before allowing the client into
     * the server =================
     */
    static xcommand_t Precache_f = new xcommand_t() {
        public void execute() {
            /*
             * Yet another hack to let old demos work the old precache sequence
             */

            if (Cmd.Argc() < 2) {

                int iw[] = { 0 }; // for detecting cheater maps

                CM.CM_LoadMap(Globals.cl.configstrings[Defines.CS_MODELS + 1],
                        true, iw);
                int mapchecksum = iw[0];
                CL_parse.RegisterSounds();
                CL_view.PrepRefresh();
                return;
            }

            CL.precache_check = Defines.CS_MODELS;
            CL.precache_spawncount = Lib.atoi(Cmd.Argv(1));
            CL.precache_model = null;
            CL.precache_model_skin = 0;

            RequestNextDownload();
        }
    };

    /*
     * ================== CL_Frame
     * 
     * ==================
     */
    private static int extratime;

    //	  ============================================================================

    /*
     * =============== CL_Shutdown
     * 
     * FIXME: this is a callback from Sys_Quit and Com_Error. It would be better
     * to run quit through here before the final handoff to the sys code.
     * ===============
     */
    static boolean isdown = false;

    /*
     * ==================== CL_WriteDemoMessage
     * 
     * Dumps the current net message, prefixed by the length
     * ====================
     */
    static void WriteDemoMessage() {
        int swlen;

        // the first eight bytes are just packet sequencing stuff
        swlen = Globals.net_message.cursize - 8;

        try {
            Globals.cls.demofile.writeInt(EndianHandler.swapInt(swlen));
            Globals.cls.demofile.write(Globals.net_message.data, 8, swlen);
        } catch (IOException e) {
        }

    }

    /*
     * ======================= CL_SendConnectPacket
     * 
     * We have gotten a challenge from the server, so try and connect.
     * ======================
     */
    static void SendConnectPacket() {
        netadr_t adr = new netadr_t();
        int port;

        if (!NET.StringToAdr(Globals.cls.servername, adr)) {
            Com.Printf("Bad server address\n");
            Globals.cls.connect_time = 0;
            return;
        }
        if (adr.port == 0)
            adr.port = Defines.PORT_SERVER;
        //			adr.port = BigShort(PORT_SERVER);

        port = (int) Cvar.VariableValue("qport");
        Globals.userinfo_modified = false;

        Netchan.OutOfBandPrint(Defines.NS_CLIENT, adr, "connect "
                + Defines.PROTOCOL_VERSION + " " + port + " "
                + Globals.cls.challenge + " \"" + Cvar.Userinfo() + "\"\n");
    }

    /*
     * ================= CL_CheckForResend
     * 
     * Resend a connect message if the last one has timed out =================
     */
    static void CheckForResend() {
        netadr_t adr = new netadr_t();

        // if the local server is running and we aren't
        // then connect
        if (Globals.cls.state == Defines.ca_disconnected
                && Globals.server_state != 0) {
            Globals.cls.state = Defines.ca_connecting;
            Globals.cls.servername = "localhost";
            // we don't need a challenge on the localhost
            SendConnectPacket();
            return;
        }

        // resend if we haven't gotten a reply yet
        if (Globals.cls.state != Defines.ca_connecting)
            return;

        if (Globals.cls.realtime - Globals.cls.connect_time < 3000)
            return;

        if (!NET.StringToAdr(Globals.cls.servername, adr)) {
            Com.Printf("Bad server address\n");
            Globals.cls.state = Defines.ca_disconnected;
            return;
        }
        if (adr.port == 0)
            //			adr.port = BigShort(PORT_SERVER);
            adr.port = Defines.PORT_SERVER;

        Globals.cls.connect_time = Globals.cls.realtime; // for retransmit
                                                         // requests

        Com.Printf("Connecting to " + Globals.cls.servername + "...\n");

        Netchan.OutOfBandPrint(Defines.NS_CLIENT, adr, "getchallenge\n");
    }

    /*
     * ===================== CL_ClearState
     * 
     * =====================
     */

    static void ClearState() {
        S.StopAllSounds();
        CL_fx.ClearEffects();
        CL_tent.ClearTEnts();

        // wipe the entire cl structure

        Globals.cl = new client_state_t();
        for (int i = 0; i < Globals.cl_entities.length; i++) {
            Globals.cl_entities[i] = new centity_t();
        }

        SZ.Clear(Globals.cls.netchan.message);
    }

    /*
     * ===================== CL_Disconnect
     * 
     * Goes from a connected state to full screen console state Sends a
     * disconnect message to the server This is also called on Com_Error, so it
     * shouldn't cause any errors =====================
     */

    static void Disconnect() {

        String fin;

        if (Globals.cls.state == Defines.ca_disconnected)
            return;

        if (Globals.cl_timedemo != null && Globals.cl_timedemo.value != 0.0f) {
            int time;

            time = (int) (Timer.Milliseconds() - Globals.cl.timedemo_start);
            if (time > 0)
                Com.Printf("%i frames, %3.1f seconds: %3.1f fps\n",
                        new Vargs(3).add(Globals.cl.timedemo_frames).add(
                                time / 1000.0).add(
                                Globals.cl.timedemo_frames * 1000.0 / time));
        }

        Math3D.VectorClear(Globals.cl.refdef.blend);
        //re.CinematicSetPalette(null);

        Menu.ForceMenuOff();

        Globals.cls.connect_time = 0;

        //		SCR.StopCinematic();

        if (Globals.cls.demorecording)
            Stop_f.execute();

        // send a disconnect message to the server
        fin = (char) Defines.clc_stringcmd + "disconnect";
        Netchan.Transmit(Globals.cls.netchan, fin.length(), fin.getBytes());
        Netchan.Transmit(Globals.cls.netchan, fin.length(), fin.getBytes());
        Netchan.Transmit(Globals.cls.netchan, fin.length(), fin.getBytes());

        ClearState();

        // stop download
        if (Globals.cls.download != null) {
            Lib.fclose(Globals.cls.download);
            Globals.cls.download = null;
            //			fclose(cls.download);
            //			cls.download = NULL;
        }

        Globals.cls.state = Defines.ca_disconnected;
    }

    /*
     * ================= CL_ParseStatusMessage
     * 
     * Handle a reply from a ping =================
     */
    static void ParseStatusMessage() {
        String s;

        s = MSG.ReadString(Globals.net_message);

        Com.Printf(s + "\n");
        Menu.AddToServerList(Globals.net_from, s);
    }

    /*
     * ================= CL_ConnectionlessPacket
     * 
     * Responses to broadcasts, etc =================
     */
    static void ConnectionlessPacket() {
        String s;
        String c;

        MSG.BeginReading(Globals.net_message);
        MSG.ReadLong(Globals.net_message); // skip the -1

        s = MSG.ReadStringLine(Globals.net_message);

        Cmd.TokenizeString(s.toCharArray(), false);

        c = Cmd.Argv(0);
        
        Com.Println(Globals.net_from.toString() + ": " + c);

        // server connection
        if (c.equals("client_connect")) {
            if (Globals.cls.state == Defines.ca_connected) {
                Com.Printf("Dup connect received.  Ignored.\n");
                return;
            }
            Netchan.Setup(Defines.NS_CLIENT, Globals.cls.netchan,
                    Globals.net_from, Globals.cls.quakePort);
            MSG.WriteChar(Globals.cls.netchan.message, Defines.clc_stringcmd);
            MSG.WriteString(Globals.cls.netchan.message, "new");
            Globals.cls.state = Defines.ca_connected;
            return;
        }

        // server responding to a status broadcast
        if (c.equals("info")) {
            ParseStatusMessage();
            return;
        }

        // remote command from gui front end
        if (c.equals("cmd")) {
            if (!NET.IsLocalAddress(Globals.net_from)) {
                Com.Printf("Command packet from remote host.  Ignored.\n");
                return;
            }
            s = MSG.ReadString(Globals.net_message);
            Cbuf.AddText(s);
            Cbuf.AddText("\n");
            return;
        }
        // print command from somewhere
        if (c.equals("print")) {
            s = MSG.ReadString(Globals.net_message);
            Com.Printf(s);
            return;
        }

        // ping from somewhere
        if (c.equals("ping")) {
            Netchan.OutOfBandPrint(Defines.NS_CLIENT, Globals.net_from, "ack");
            return;
        }

        // challenge from the server we are connecting to
        if (c.equals("challenge")) {
            Globals.cls.challenge = Lib.atoi(Cmd.Argv(1));
            SendConnectPacket();
            return;
        }

        // echo request from server
        if (c.equals("echo")) {
            Netchan.OutOfBandPrint(Defines.NS_CLIENT, Globals.net_from, Cmd
                    .Argv(1));
            return;
        }

        Com.Printf("Unknown command.\n");
    }


    /*
     * ================= CL_ReadPackets =================
     */
    static void ReadPackets() {
        while (NET.GetPacket(Defines.NS_CLIENT, Globals.net_from,
                Globals.net_message)) {

            //
            // remote command packet
            //		
            if (Globals.net_message.data[0] == -1
                    && Globals.net_message.data[1] == -1
                    && Globals.net_message.data[2] == -1
                    && Globals.net_message.data[3] == -1) {
                //			if (*(int *)net_message.data == -1)
                ConnectionlessPacket();
                continue;
            }

            if (Globals.cls.state == Defines.ca_disconnected
                    || Globals.cls.state == Defines.ca_connecting)
                continue; // dump it if not connected

            if (Globals.net_message.cursize < 8) {
                Com.Printf(NET.AdrToString(Globals.net_from)
                        + ": Runt packet\n");
                continue;
            }

            //
            // packet from server
            //
            if (!NET.CompareAdr(Globals.net_from,
                    Globals.cls.netchan.remote_address)) {
                Com.DPrintf(NET.AdrToString(Globals.net_from)
                        + ":sequenced packet without connection\n");
                continue;
            }
            if (!Netchan.Process(Globals.cls.netchan, Globals.net_message))
                continue; // wasn't accepted for some reason
            CL_parse.ParseServerMessage();
        }

        //
        // check timeout
        //
        if (Globals.cls.state >= Defines.ca_connected
                && Globals.cls.realtime - Globals.cls.netchan.last_received > Globals.cl_timeout.value * 1000) {
            if (++Globals.cl.timeoutcount > 5) // timeoutcount saves debugger
            {
                Com.Printf("\nServer connection timed out.\n");
                Disconnect();
                return;
            }
        } else
            Globals.cl.timeoutcount = 0;
    }

    //	  =============================================================================

    /*
     * ============== CL_FixUpGender_f ==============
     */
    static void FixUpGender() {

        String sk;

        if (Globals.gender_auto.value != 0.0f) {

            if (Globals.gender.modified) {
                // was set directly, don't override the user
                Globals.gender.modified = false;
                return;
            }

            sk = Globals.skin.string;
            if (sk.startsWith("male") || sk.startsWith("cyborg"))
                Cvar.Set("gender", "male");
            else if (sk.startsWith("female") || sk.startsWith("crackhor"))
                Cvar.Set("gender", "female");
            else
                Cvar.Set("gender", "none");
            Globals.gender.modified = false;
        }
    }

    public static void RequestNextDownload() {
        int map_checksum = 0; // for detecting cheater maps
        //char fn[MAX_OSPATH];
        String fn;

        qfiles.dmdl_t pheader;

        if (Globals.cls.state != Defines.ca_connected)
            return;

        if (SV_MAIN.allow_download.value == 0 && CL.precache_check < ENV_CNT)
            CL.precache_check = ENV_CNT;

        //	  ZOID
        if (CL.precache_check == Defines.CS_MODELS) { // confirm map
            CL.precache_check = Defines.CS_MODELS + 2; // 0 isn't used
            if (SV_MAIN.allow_download_maps.value != 0)
                if (!CL_parse
                        .CheckOrDownloadFile(Globals.cl.configstrings[Defines.CS_MODELS + 1]))
                    return; // started a download
        }
        if (CL.precache_check >= Defines.CS_MODELS
                && CL.precache_check < Defines.CS_MODELS + Defines.MAX_MODELS) {
            if (SV_MAIN.allow_download_models.value != 0) {
                while (CL.precache_check < Defines.CS_MODELS
                        + Defines.MAX_MODELS
                        && Globals.cl.configstrings[CL.precache_check].length() > 0) {
                    if (Globals.cl.configstrings[CL.precache_check].charAt(0) == '*'
                            || Globals.cl.configstrings[CL.precache_check]
                                    .charAt(0) == '#') {
                        CL.precache_check++;
                        continue;
                    }
                    if (CL.precache_model_skin == 0) {
                        if (!CL_parse
                                .CheckOrDownloadFile(Globals.cl.configstrings[CL.precache_check])) {
                            CL.precache_model_skin = 1;
                            return; // started a download
                        }
                        CL.precache_model_skin = 1;
                    }

                    // checking for skins in the model
                    if (CL.precache_model == null) {

                        CL.precache_model = FS
                                .LoadFile(Globals.cl.configstrings[CL.precache_check]);
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
                            FS.FreeFile(CL.precache_model);
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
                        FS.FreeFile(CL.precache_model);
                        CL.precache_model = null;
                    }
                    CL.precache_model_skin = 0;
                    CL.precache_check++;
                }
            }
            CL.precache_check = Defines.CS_SOUNDS;
        }
        if (CL.precache_check >= Defines.CS_SOUNDS
                && CL.precache_check < Defines.CS_SOUNDS + Defines.MAX_SOUNDS) {
            if (SV_MAIN.allow_download_sounds.value != 0) {
                if (CL.precache_check == Defines.CS_SOUNDS)
                    CL.precache_check++; // zero is blank
                while (CL.precache_check < Defines.CS_SOUNDS
                        + Defines.MAX_SOUNDS
                        && Globals.cl.configstrings[CL.precache_check].length() > 0) {
                    if (Globals.cl.configstrings[CL.precache_check].charAt(0) == '*') {
                        CL.precache_check++;
                        continue;
                    }
                    fn = "sound/"
                            + Globals.cl.configstrings[CL.precache_check++];
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
                    && Globals.cl.configstrings[CL.precache_check].length() > 0) {
                fn = "pics/" + Globals.cl.configstrings[CL.precache_check++]
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
            if (SV_MAIN.allow_download_players.value != 0) {
                while (CL.precache_check < Defines.CS_PLAYERSKINS
                        + Defines.MAX_CLIENTS * CL.PLAYER_MULT) {

                    int i, n;
                    //char model[MAX_QPATH], skin[MAX_QPATH], * p;
                    String model, skin;

                    i = (CL.precache_check - Defines.CS_PLAYERSKINS)
                            / CL.PLAYER_MULT;
                    n = (CL.precache_check - Defines.CS_PLAYERSKINS)
                            % CL.PLAYER_MULT;

                    if (Globals.cl.configstrings[Defines.CS_PLAYERSKINS + i]
                            .length() == 0) {
                        CL.precache_check = Defines.CS_PLAYERSKINS + (i + 1)
                                * CL.PLAYER_MULT;
                        continue;
                    }

                    int pos = Globals.cl.configstrings[Defines.CS_PLAYERSKINS + i].indexOf('\\');
                    
                    if (pos != -1)
                        pos++;
                    else
                        pos = 0;

                    int pos2 = Globals.cl.configstrings[Defines.CS_PLAYERSKINS + i].indexOf('\\', pos);
                    
                    if (pos2 == -1)
                        pos2 = Globals.cl.configstrings[Defines.CS_PLAYERSKINS + i].indexOf('/', pos);
                    
                    
                    model = Globals.cl.configstrings[Defines.CS_PLAYERSKINS + i]
                            .substring(pos, pos2);
                                        
                    skin = Globals.cl.configstrings[Defines.CS_PLAYERSKINS + i].substring(pos2 + 1);
                    
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

            CM.CM_LoadMap(Globals.cl.configstrings[Defines.CS_MODELS + 1],
                    true, iw);
            map_checksum = iw[0];

            if ((map_checksum ^ Lib
                    .atoi(Globals.cl.configstrings[Defines.CS_MAPCHECKSUM])) != 0) {
                Com
                        .Error(
                                Defines.ERR_DROP,
                                "Local map version differs from server: "
                                        + map_checksum
                                        + " != '"
                                        + Globals.cl.configstrings[Defines.CS_MAPCHECKSUM]
                                        + "'\n");
                return;
            }
        }

        if (CL.precache_check > ENV_CNT && CL.precache_check < TEXTURE_CNT) {
            if (SV_MAIN.allow_download.value != 0
                    && SV_MAIN.allow_download_maps.value != 0) {
                while (CL.precache_check < TEXTURE_CNT) {
                    int n = CL.precache_check++ - ENV_CNT - 1;

                    if ((n & 1) != 0)
                        fn = "env/" + Globals.cl.configstrings[Defines.CS_SKY]
                                + env_suf[n / 2] + ".pcx";
                    else
                        fn = "env/" + Globals.cl.configstrings[Defines.CS_SKY]
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

            if (SV_MAIN.allow_download.value != 0
                    && SV_MAIN.allow_download_maps.value != 0) {
                while (CL.precache_tex < CM.numtexinfo) {
                    //char fn[MAX_OSPATH];

                    fn = "textures/" + CM.map_surfaces[CL.precache_tex++].rname
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

        MSG.WriteByte(Globals.cls.netchan.message, Defines.clc_stringcmd);
        MSG.WriteString(Globals.cls.netchan.message, "begin "
                + CL.precache_spawncount + "\n");
    }

    /*
     * ================= CL_InitLocal =================
     */
    public static void InitLocal() {
        Globals.cls.state = Defines.ca_disconnected;
        Globals.cls.realtime = Timer.Milliseconds();

        CL_input.InitInput();

        Cvar.Get("adr0", "", Defines.CVAR_ARCHIVE);
        Cvar.Get("adr1", "", Defines.CVAR_ARCHIVE);
        Cvar.Get("adr2", "", Defines.CVAR_ARCHIVE);
        Cvar.Get("adr3", "", Defines.CVAR_ARCHIVE);
        Cvar.Get("adr4", "", Defines.CVAR_ARCHIVE);
        Cvar.Get("adr5", "", Defines.CVAR_ARCHIVE);
        Cvar.Get("adr6", "", Defines.CVAR_ARCHIVE);
        Cvar.Get("adr7", "", Defines.CVAR_ARCHIVE);
        Cvar.Get("adr8", "", Defines.CVAR_ARCHIVE);

        //
        // register our variables
        //
        Globals.cl_stereo_separation = Cvar.Get("cl_stereo_separation", "0.4",
                Defines.CVAR_ARCHIVE);
        Globals.cl_stereo = Cvar.Get("cl_stereo", "0", 0);

        Globals.cl_add_blend = Cvar.Get("cl_blend", "1", 0);
        Globals.cl_add_lights = Cvar.Get("cl_lights", "1", 0);
        Globals.cl_add_particles = Cvar.Get("cl_particles", "1", 0);
        Globals.cl_add_entities = Cvar.Get("cl_entities", "1", 0);
        Globals.cl_gun = Cvar.Get("cl_gun", "1", 0);
        Globals.cl_footsteps = Cvar.Get("cl_footsteps", "1", 0);
        Globals.cl_noskins = Cvar.Get("cl_noskins", "0", 0);
        Globals.cl_autoskins = Cvar.Get("cl_autoskins", "0", 0);
        Globals.cl_predict = Cvar.Get("cl_predict", "1", 0);

        Globals.cl_maxfps = Cvar.Get("cl_maxfps", "90", 0);

        Globals.cl_upspeed = Cvar.Get("cl_upspeed", "200", 0);
        Globals.cl_forwardspeed = Cvar.Get("cl_forwardspeed", "200", 0);
        Globals.cl_sidespeed = Cvar.Get("cl_sidespeed", "200", 0);
        Globals.cl_yawspeed = Cvar.Get("cl_yawspeed", "140", 0);
        Globals.cl_pitchspeed = Cvar.Get("cl_pitchspeed", "150", 0);
        Globals.cl_anglespeedkey = Cvar.Get("cl_anglespeedkey", "1.5", 0);

        Globals.cl_run = Cvar.Get("cl_run", "0", Defines.CVAR_ARCHIVE);
        Globals.lookspring = Cvar.Get("lookspring", "0", Defines.CVAR_ARCHIVE);
        Globals.lookstrafe = Cvar.Get("lookstrafe", "0", Defines.CVAR_ARCHIVE);
        Globals.sensitivity = Cvar
                .Get("sensitivity", "3", Defines.CVAR_ARCHIVE);

        Globals.m_pitch = Cvar.Get("m_pitch", "0.022", Defines.CVAR_ARCHIVE);
        Globals.m_yaw = Cvar.Get("m_yaw", "0.022", 0);
        Globals.m_forward = Cvar.Get("m_forward", "1", 0);
        Globals.m_side = Cvar.Get("m_side", "1", 0);

        Globals.cl_shownet = Cvar.Get("cl_shownet", "0", 0);
        Globals.cl_showmiss = Cvar.Get("cl_showmiss", "0", 0);
        Globals.cl_showclamp = Cvar.Get("showclamp", "0", 0);
        Globals.cl_timeout = Cvar.Get("cl_timeout", "120", 0);
        Globals.cl_paused = Cvar.Get("paused", "0", 0);
        Globals.cl_timedemo = Cvar.Get("timedemo", "0", 0);

        Globals.rcon_client_password = Cvar.Get("rcon_password", "", 0);
        Globals.rcon_address = Cvar.Get("rcon_address", "", 0);

        Globals.cl_lightlevel = Cvar.Get("r_lightlevel", "0", 0);

        //
        // userinfo
        //
        Globals.info_password = Cvar.Get("password", "", Defines.CVAR_USERINFO);
        Globals.info_spectator = Cvar.Get("spectator", "0",
                Defines.CVAR_USERINFO);
        Globals.name = Cvar.Get("name", "unnamed", Defines.CVAR_USERINFO
                | Defines.CVAR_ARCHIVE);
        Globals.skin = Cvar.Get("skin", "male/grunt", Defines.CVAR_USERINFO
                | Defines.CVAR_ARCHIVE);
        Globals.rate = Cvar.Get("rate", "25000", Defines.CVAR_USERINFO
                | Defines.CVAR_ARCHIVE); // FIXME
        Globals.msg = Cvar.Get("msg", "1", Defines.CVAR_USERINFO
                | Defines.CVAR_ARCHIVE);
        Globals.hand = Cvar.Get("hand", "0", Defines.CVAR_USERINFO
                | Defines.CVAR_ARCHIVE);
        Globals.fov = Cvar.Get("fov", "90", Defines.CVAR_USERINFO
                | Defines.CVAR_ARCHIVE);
        Globals.gender = Cvar.Get("gender", "male", Defines.CVAR_USERINFO
                | Defines.CVAR_ARCHIVE);
        Globals.gender_auto = Cvar
                .Get("gender_auto", "1", Defines.CVAR_ARCHIVE);
        Globals.gender.modified = false; // clear this so we know when user sets
                                         // it manually

        Globals.cl_vwep = Cvar.Get("cl_vwep", "1", Defines.CVAR_ARCHIVE);

        //
        // register our commands
        //
        Cmd.AddCommand("cmd", ForwardToServer_f);
        Cmd.AddCommand("pause", Pause_f);
        Cmd.AddCommand("pingservers", PingServers_f);
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

        Cmd.AddCommand("download", CL_parse.Download_f);

        //
        // forward to server commands
        //
        // the only thing this does is allow command completion
        // to work -- all unknown commands are automatically
        // forwarded to the server
        Cmd.AddCommand("wave", null);
        Cmd.AddCommand("inven", null);
        Cmd.AddCommand("kill", null);
        Cmd.AddCommand("use", null);
        Cmd.AddCommand("drop", null);
        Cmd.AddCommand("say", null);
        Cmd.AddCommand("say_team", null);
        Cmd.AddCommand("info", null);
        Cmd.AddCommand("prog", null);
        Cmd.AddCommand("give", null);
        Cmd.AddCommand("god", null);
        Cmd.AddCommand("notarget", null);
        Cmd.AddCommand("noclip", null);
        Cmd.AddCommand("invuse", null);
        Cmd.AddCommand("invprev", null);
        Cmd.AddCommand("invnext", null);
        Cmd.AddCommand("invdrop", null);
        Cmd.AddCommand("weapnext", null);
        Cmd.AddCommand("weapprev", null);

    }

    /*
     * =============== CL_WriteConfiguration
     * 
     * Writes key bindings and archived cvars to config.cfg ===============
     */
    public static void WriteConfiguration() {
        RandomAccessFile f;
        String path;

//        if (Globals.cls.state == Defines.ca_uninitialized)
//            return;

        path = FS.Gamedir() + "/config.cfg";
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
        Cvar.WriteVariables(path);
    }

    public static void FixCvarCheats() {
        int i;
        CL.cheatvar_t var;

        if ("1".equals(Globals.cl.configstrings[Defines.CS_MAXCLIENTS])
                || 0 == Globals.cl.configstrings[Defines.CS_MAXCLIENTS]
                        .length())
            return; // single player can cheat

        // find all the cvars if we haven't done it yet
        if (0 == CL.numcheatvars) {
            while (CL.cheatvars[CL.numcheatvars].name != null) {
                CL.cheatvars[CL.numcheatvars].var = Cvar.Get(
                        CL.cheatvars[CL.numcheatvars].name,
                        CL.cheatvars[CL.numcheatvars].value, 0);
                CL.numcheatvars++;
            }
        }

        // make sure they are all set to the proper values
        for (i = 0; i < CL.numcheatvars; i++) {
            var = CL.cheatvars[i];
            if (!var.var.string.equals(var.value)) {
                Cvar.Set(var.name, var.value);
            }
        }
    }

    //	  ============================================================================

    /*
     * ================== CL_SendCommand
     * 
     * ==================
     */
    public static void SendCommand() {
        // get new key events
        Sys.SendKeyEvents();

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

    public static void Frame(int msec) {
        
        if (Globals.dedicated.value != 0)
            return;

        extratime += msec;

        if (Globals.cl_timedemo.value == 0.0f) {
            if (Globals.cls.state == Defines.ca_connected && extratime < 100) {
                return; // don't flood packets out while connecting
            }
            if (extratime < 1000 / Globals.cl_maxfps.value) {
                return; // framerate is too high
            }
        }

        // let the mouse activate or deactivate
        IN.Frame();

        // decide the simulation time
        Globals.cls.frametime = extratime / 1000.0f;
        Globals.cl.time += extratime;
        Globals.cls.realtime = Globals.curtime;

        extratime = 0;

        if (Globals.cls.frametime > (1.0f / 5))
            Globals.cls.frametime = (1.0f / 5);

        // if in the debugger last frame, don't timeout
        if (msec > 5000)
            Globals.cls.netchan.last_received = Timer.Milliseconds();

        // fetch results from server
        ReadPackets();

        // send a new command message to the server
        SendCommand();

        // predict all unacknowledged movements
        CL_pred.PredictMovement();

        // allow rendering DLL change
        VID.CheckChanges();
        if (!Globals.cl.refresh_prepped
                && Globals.cls.state == Defines.ca_active) {
            CL_view.PrepRefresh();
            // force GC after level loading
            // but not on playing a cinematic
            if (Globals.cl.cinematictime == 0) System.gc();
        }

        SCR.UpdateScreen();

        // update audio
        S.Update(Globals.cl.refdef.vieworg, Globals.cl.v_forward,
                Globals.cl.v_right, Globals.cl.v_up);

        // advance local effects for next frame
        CL_fx.RunDLights();
        CL_fx.RunLightStyles();

        SCR.RunConsole();

        Globals.cls.framecount++;
        if (Globals.cls.state != Defines.ca_active
                || Globals.cls.key_dest != Defines.key_game) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
            }
        }
    }

    public static void Shutdown() {

        if (isdown) {
            System.out.print("recursive shutdown\n");
            return;
        }
        isdown = true;

        WriteConfiguration();

        S.Shutdown();
        IN.Shutdown();
        VID.Shutdown();
    }

    /**
     * initialize client subsystem
     */
    public static void Init() {
        if (Globals.dedicated.value != 0.0f)
            return; // nothing running on the client

        // all archived variables will now be loaded

        Console.Init(); //ok

        S.Init(); //empty
        VID.Init();

        V.Init();

        Globals.net_message.data = Globals.net_message_buffer;
        Globals.net_message.maxsize = Globals.net_message_buffer.length;

        Menu.Init();

        SCR.Init();
        //Globals.cls.disable_screen = 1.0f; // don't draw yet

        InitLocal();
        IN.Init();

        FS.ExecAutoexec();
        Cbuf.Execute();
    }

    /**
     * Called after an ERR_DROP was thrown.
     */
    public static void Drop() {
        if (Globals.cls.state == Defines.ca_uninitialized)
            return;
        if (Globals.cls.state == Defines.ca_disconnected)
            return;

        Disconnect();

        // drop loading plaque unless this is the initial game start
        if (Globals.cls.disable_servercount != -1)
            SCR.EndLoadingPlaque(); // get rid of loading plaque
    }
}