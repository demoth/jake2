/*
 * CL_parse.java
 * Copyright (C) 2004
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

import jake2.client.render.model_t;
import jake2.client.sound.S;
import jake2.qcommon.*;
import jake2.qcommon.exec.Cbuf;
import jake2.qcommon.exec.Cvar;
import jake2.qcommon.filesystem.FS;
import jake2.qcommon.network.messages.client.StringCmdMessage;
import jake2.qcommon.network.messages.server.*;
import jake2.qcommon.util.Lib;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * CL_parse
 */
public class CL_parse {

    //// cl_parse.c -- parse a message received from the server

    public static String svc_strings[] = {
            "svc_bad",
            "svc_muzzleflash",
            "svc_muzzlflash2",
            "svc_temp_entity",
            "svc_layout",
            "svc_inventory",
            "svc_nop",
            "svc_disconnect",
            "svc_reconnect",
            "svc_sound",
            "svc_print",
            "svc_stufftext",
            "svc_serverdata",
            "svc_configstring",
            "svc_spawnbaseline",
            "svc_centerprint",
            "svc_download",
            "svc_playerinfo",
            "svc_packetentities",
            "svc_deltapacketentities",
            "svc_frame"
    };

    //	  =============================================================================

    public static String DownloadFileName(String fn) {
        return FS.getWriteDir() + "/" + fn;
    }

    /**
     * CL_CheckOrDownloadFile returns true if the file exists, 
     * otherwise it attempts to start a
     * download from the server.
     */
    public static boolean CheckOrDownloadFile(String filename) {
        if (filename.indexOf("..") != -1) {
            Com.Printf("Refusing to download a path with ..\n");
            return true;
        }

        if (FS.FileExists(filename)) {
            // it exists, no need to download
            return true;
        } else if (FS.FileExists(filename.toLowerCase())) {
            // some mods mess up the case
            Com.Printf("Found file by lowercase: " + filename + "\n");
            return true;
        }

        ClientGlobals.cls.downloadname = filename;

        // download to a temp name, and only rename
        // to the real name when done, so if interrupted
        // a runt file wont be left
        ClientGlobals.cls.downloadtempname = Com
                .StripExtension(ClientGlobals.cls.downloadname);
        ClientGlobals.cls.downloadtempname += ".tmp";

        //	  ZOID
        // check to see if we already have a tmp for this file, if so, try to
        // resume
        // open the file if not opened yet
        String name = DownloadFileName(ClientGlobals.cls.downloadtempname);

        RandomAccessFile fp = Lib.fopen(name, "r+b");
        
        if (fp != null) { 
            
            // it exists
            long len = 0;

            try {
                len = fp.length();
            } 
            catch (IOException e) {
            }
            

            ClientGlobals.cls.download = fp;

            // give the server an offset to start the download
            Com.Printf("Resuming " + ClientGlobals.cls.downloadname + "\n");
            new StringCmdMessage(StringCmdMessage.DOWNLOAD + " " + ClientGlobals.cls.downloadname + " " + len).writeTo(ClientGlobals.cls.netchan.message);
        } else {
            Com.Printf("Downloading " + ClientGlobals.cls.downloadname + "\n");
            new StringCmdMessage(StringCmdMessage.DOWNLOAD + " " + ClientGlobals.cls.downloadname).writeTo(ClientGlobals.cls.netchan.message);
        }

        ClientGlobals.cls.downloadnumber++;

        return false;
    }

    /*
     * ====================== CL_RegisterSounds ======================
     */
    public static void RegisterSounds() {
        S.BeginRegistration();
        CL_tent.RegisterTEntSounds();
        for (int i = 1; i < Defines.MAX_SOUNDS; i++) {
            if (ClientGlobals.cl.configstrings[Defines.CS_SOUNDS + i] == null
                    || ClientGlobals.cl.configstrings[Defines.CS_SOUNDS + i]
                            .equals("")
                    || ClientGlobals.cl.configstrings[Defines.CS_SOUNDS + i]
                            .equals("\0"))
                break;
            ClientGlobals.cl.sound_precache[i] = S
                    .RegisterSound(ClientGlobals.cl.configstrings[Defines.CS_SOUNDS
                            + i]);
            CL.SendKeyEvents(); // pump message loop
        }
        S.EndRegistration();
    }

    /*
     * ===================== CL_ParseDownload
     * 
     * A download message has been received from the server
     * =====================
     */
    public static void ParseDownload(DownloadMessage msg) {

        // read the data
        int percent = msg.percentage;
        if (msg.data == null) {
            Com.Printf("Server does not have this file.\n");
            if (ClientGlobals.cls.download != null) {
                // if here, we tried to resume a file but the server said no
                try {
                    ClientGlobals.cls.download.close();
                } catch (IOException e) {
                }
                ClientGlobals.cls.download = null;
            }
            CL.RequestNextDownload();
            return;
        }

        // open the file if not opened yet
        if (ClientGlobals.cls.download == null) {
            String name = DownloadFileName(ClientGlobals.cls.downloadtempname).toLowerCase();

            FS.CreatePath(name);

            ClientGlobals.cls.download = Lib.fopen(name, "rw");
            if (ClientGlobals.cls.download == null) {
                Com.Printf("Failed to open " + ClientGlobals.cls.downloadtempname + "\n");
                CL.RequestNextDownload();
                return;
            }
        }


        try {
            ClientGlobals.cls.download.write(msg.data);
        } catch (Exception e) {
            Com.dprintln("Could not write downloaded data to file: " + e.getMessage());
        }

        if (percent != 100) {
            // request next block
            //	   change display routines by zoid
            ClientGlobals.cls.downloadpercent = percent;
            new StringCmdMessage(StringCmdMessage.NEXT_DOWNLOAD).writeTo(ClientGlobals.cls.netchan.message);
        } else {
            try {
                ClientGlobals.cls.download.close();
            } 
            catch (IOException e) {
            }

            // rename the temp file to it's final name
            String oldn = DownloadFileName(ClientGlobals.cls.downloadtempname);
            String newn = DownloadFileName(ClientGlobals.cls.downloadname);
            int r = Lib.rename(oldn, newn);
            if (r != 0)
                Com.Printf("failed to rename.\n");

            ClientGlobals.cls.download = null;
            ClientGlobals.cls.downloadpercent = 0;

            // get another file if needed

            CL.RequestNextDownload();
        }
    }

    /*
     * =====================================================================
     * 
     * SERVER CONNECTING MESSAGES
     * 
     * =====================================================================
     */

    /*
     * ================== CL_ParseServerData ==================
     */
    //checked once, was ok.
    public static void ParseServerData(ServerDataMessage serverData) {
        Com.DPrintf("ParseServerData():Serverdata packet received.\n");
        //
        //	   wipe the client_state_t struct
        //
        CL.ClearState();
        ClientGlobals.cls.state = Defines.ca_connected;

        //	   parse protocol version number
        ClientGlobals.cls.serverProtocol = serverData.protocol;

        // BIG HACK to let demos from release work with the 3.0x patch!!!
        // fixme: get rid of this
        if (Globals.server_state == ServerStates.SS_DEAD || Defines.PROTOCOL_VERSION != 34) {
            if (serverData.protocol != Defines.PROTOCOL_VERSION)
                Com.Error(Defines.ERR_DROP, "Server returned version " + serverData.protocol
                        + ", not " + Defines.PROTOCOL_VERSION);
        }

        ClientGlobals.cl.servercount = serverData.spawnCount;
        ClientGlobals.cl.attractloop = serverData.demo;

        // game directory
        ClientGlobals.cl.gamedir = serverData.gameName;
        Com.dprintln("gamedir=" + serverData.gameName);

        // set gamedir
        // wtf?!
        if (serverData.gameName.length() > 0
                && (FS.fs_gamedirvar.string == null
                        || FS.fs_gamedirvar.string.length() == 0 || FS.fs_gamedirvar.string
                        .equals(serverData.gameName))
                || (serverData.gameName.length() == 0 && (FS.fs_gamedirvar.string != null || FS.fs_gamedirvar.string
                        .length() == 0)))
            Cvar.getInstance().Set("game", serverData.gameName);

        // parse player entity number
        ClientGlobals.cl.playernum = serverData.playerNumber;
        Com.dprintln("numplayers=" + ClientGlobals.cl.playernum);
        // get the full level name
        Com.dprintln("levelname=" + serverData.levelString);

        if (ClientGlobals.cl.playernum == -1) { // playing a cinematic or showing a
            // pic, not a level
            SCR.PlayCinematic(serverData.levelString);
        } else {
            // seperate the printfs so the server message can have a color
            //			Com.Printf(
            //				"\n\n\35\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\36\37\n\n");
            //			Com.Printf('\02' + str + "\n");
            Com.Printf("Levelname:" + serverData.levelString + "\n");
            // need to prep refresh at next oportunity
            ClientGlobals.cl.refresh_prepped = false;
        }
    }

    /*
     * ================ CL_LoadClientinfo
     * 
     * ================
     */
    public static void LoadClientinfo(clientinfo_t ci, String s) {
        //char model_name[MAX_QPATH];
        //char skin_name[MAX_QPATH];
        //char model_filename[MAX_QPATH];
        //char skin_filename[MAX_QPATH];
        //char weapon_filename[MAX_QPATH];

        String model_name, skin_name, model_filename, skin_filename, weapon_filename;

        ci.cinfo = s;
        //ci.cinfo[sizeof(ci.cinfo) - 1] = 0;

        // isolate the player's name
        ci.name = s;
        //ci.name[sizeof(ci.name) - 1] = 0;

        int t = s.indexOf('\\');
        //t = strstr(s, "\\");

        if (t != -1) {
            ci.name = s.substring(0, t);
            s = s.substring(t + 1, s.length());
            //s = t + 1;
        }

        if (ClientGlobals.cl_noskins.value != 0 || s.length() == 0) {

            model_filename = ("players/male/tris.md2");
            weapon_filename = ("players/male/weapon.md2");
            skin_filename = ("players/male/grunt.pcx");
            ci.iconname = ("/players/male/grunt_i.pcx");

            ci.model = ClientGlobals.re.RegisterModel(model_filename);

            ci.weaponmodel = new model_t[Defines.MAX_CLIENTWEAPONMODELS];
            ci.weaponmodel[0] = ClientGlobals.re.RegisterModel(weapon_filename);
            ci.skin = ClientGlobals.re.RegisterSkin(skin_filename);
            ci.icon = ClientGlobals.re.RegisterPic(ci.iconname);
            
        } else {
            // isolate the model name

            int pos = s.indexOf('/');

            if (pos == -1)
                pos = s.indexOf('/');
            if (pos == -1) {
                pos = 0;
                Com.Error(Defines.ERR_FATAL, "Invalid model name:" + s);
            }

            model_name = s.substring(0, pos);

            // isolate the skin name
            skin_name = s.substring(pos + 1, s.length());

            // model file
            model_filename = "players/" + model_name + "/tris.md2";
            ci.model = ClientGlobals.re.RegisterModel(model_filename);

            if (ci.model == null) {
                model_name = "male";
                model_filename = "players/male/tris.md2";
                ci.model = ClientGlobals.re.RegisterModel(model_filename);
            }

            // skin file
            skin_filename = "players/" + model_name + "/" + skin_name + ".pcx";
            ci.skin = ClientGlobals.re.RegisterSkin(skin_filename);

            // if we don't have the skin and the model wasn't male,
            // see if the male has it (this is for CTF's skins)
            if (ci.skin == null && !model_name.equalsIgnoreCase("male")) {
                // change model to male
                model_name = "male";
                model_filename = "players/male/tris.md2";
                ci.model = ClientGlobals.re.RegisterModel(model_filename);

                // see if the skin exists for the male model
                skin_filename = "players/" + model_name + "/" + skin_name
                        + ".pcx";
                ci.skin = ClientGlobals.re.RegisterSkin(skin_filename);
            }

            // if we still don't have a skin, it means that the male model
            // didn't have
            // it, so default to grunt
            if (ci.skin == null) {
                // see if the skin exists for the male model
                skin_filename = "players/" + model_name + "/grunt.pcx";
                ci.skin = ClientGlobals.re.RegisterSkin(skin_filename);
            }

            // weapon file
            for (int i = 0; i < CL_view.num_cl_weaponmodels; i++) {
                weapon_filename = "players/" + model_name + "/"
                        + CL_view.cl_weaponmodels[i];
                ci.weaponmodel[i] = ClientGlobals.re.RegisterModel(weapon_filename);
                if (null == ci.weaponmodel[i] && model_name.equals("cyborg")) {
                    // try male
                    weapon_filename = "players/male/"
                            + CL_view.cl_weaponmodels[i];
                    ci.weaponmodel[i] = ClientGlobals.re
                            .RegisterModel(weapon_filename);
                }
                if (0 == ClientGlobals.cl_vwep.value)
                    break; // only one when vwep is off
            }

            // icon file
            ci.iconname = "/players/" + model_name + "/" + skin_name + "_i.pcx";
            ci.icon = ClientGlobals.re.RegisterPic(ci.iconname);
        }

        // must have loaded all data types to be valud
        if (ci.skin == null || ci.icon == null || ci.model == null
                || ci.weaponmodel[0] == null) {
            ci.skin = null;
            ci.icon = null;
            ci.model = null;
            ci.weaponmodel[0] = null;
            return;
        }
    }

    /*
     * ================ CL_ParseClientinfo
     * 
     * Load the skin, icon, and model for a client ================
     */
    public static void ParseClientinfo(int player) {
        String s = ClientGlobals.cl.configstrings[player + Defines.CS_PLAYERSKINS];

        clientinfo_t ci = ClientGlobals.cl.clientinfo[player];

        LoadClientinfo(ci, s);
    }

    /*
     * ================ CL_ParseConfigString ================
     */
    public static void ParseConfigString(ConfigStringMessage configMsg) {
        Com.Printf("Received " + configMsg + "\n");
        if (configMsg.index < 0 || configMsg.index >= Defines.MAX_CONFIGSTRINGS)
            Com.Error(Defines.ERR_DROP, "configstring > MAX_CONFIGSTRINGS");

        client_state_t clientState = ClientGlobals.cl;
        String olds = clientState.configstrings[configMsg.index];
        clientState.configstrings[configMsg.index] = configMsg.config;
        
        // do something apropriate

        if (configMsg.index >= Defines.CS_LIGHTS
                && configMsg.index < Defines.CS_LIGHTS + Defines.MAX_LIGHTSTYLES) {
            
            CL_fx.SetLightstyle(configMsg.index - Defines.CS_LIGHTS);
            
        } else if (configMsg.index == Defines.CS_CDTRACK) {
        	if (clientState.refresh_prepped)
        		CDAudio.Play(Lib.atoi(clientState.configstrings[Defines.CS_CDTRACK]), true);
        	
        } else if (configMsg.index >= Defines.CS_MODELS && configMsg.index < Defines.CS_MODELS + Defines.MAX_MODELS) {
            if (clientState.refresh_prepped) {
                clientState.model_draw[configMsg.index - Defines.CS_MODELS] = ClientGlobals.re
                        .RegisterModel(clientState.configstrings[configMsg.index]);
                if (clientState.configstrings[configMsg.index].startsWith("*"))
                    clientState.model_clip[configMsg.index - Defines.CS_MODELS] = ClientGlobals.cm.InlineModel(clientState.configstrings[configMsg.index]);
                else
                    clientState.model_clip[configMsg.index - Defines.CS_MODELS] = null;
            }
        } else if (configMsg.index >= Defines.CS_SOUNDS
                && configMsg.index < Defines.CS_SOUNDS + Defines.MAX_MODELS) {
            if (clientState.refresh_prepped)
                clientState.sound_precache[configMsg.index - Defines.CS_SOUNDS] = S
                        .RegisterSound(clientState.configstrings[configMsg.index]);
        } else if (configMsg.index >= Defines.CS_IMAGES
                && configMsg.index < Defines.CS_IMAGES + Defines.MAX_MODELS) {
            if (clientState.refresh_prepped)
                clientState.image_precache[configMsg.index - Defines.CS_IMAGES] = ClientGlobals.re
                        .RegisterPic(clientState.configstrings[configMsg.index]);
        } else if (configMsg.index >= Defines.CS_PLAYERSKINS
                && configMsg.index < Defines.CS_PLAYERSKINS + Defines.MAX_CLIENTS) {
            if (clientState.refresh_prepped && !olds.equals(configMsg.config))
                ParseClientinfo(configMsg.index - Defines.CS_PLAYERSKINS);
        }
    }

    /*
     * =====================================================================
     * 
     * ACTION MESSAGES
     * 
     * =====================================================================
     */

    private static final float[] pos_v = { 0, 0, 0 };
    /*
     * ================== CL_ParseStartSoundPacket ==================
     */
    public static void ParseStartSoundPacket(SoundMessage soundMsg) {

        if (null == ClientGlobals.cl.sound_precache[soundMsg.soundIndex]) {
            // todo: warning
            return;
        }

        S.StartSound(
                soundMsg.origin,
                soundMsg.entityIndex,
                soundMsg.sendchan,
                ClientGlobals.cl.sound_precache[soundMsg.soundIndex],
                soundMsg.volume,
                soundMsg.attenuation,
                soundMsg.timeOffset);
    }

    public static void SHOWNET(String s) {
        if (ClientGlobals.cl_shownet.value >= 2)
            Com.Printf(Globals.net_message.readcount - 1 + ":" + s + "\n");
    }


    static frame_t old;

    /*
     * ===================== CL_ParseServerMessage =====================
     */
    public static void ParseServerMessage() {
        while (true) {
            if (Globals.net_message.readcount > Globals.net_message.cursize) {
                Com.Error(Defines.ERR_FATAL,
                        "CL_ParseServerMessage: Bad server message:");
                break;
            }

            int cmd = MSG.ReadByte(Globals.net_message);

            if (cmd == -1) {
                break;
            }

            ServerMessageType msgType = ServerMessageType.fromInt(cmd);
            ServerMessage msg = ServerMessage.parseFromBuffer(msgType, Globals.net_message);
            if (msg != null) {
                // process
                if (msg instanceof DisconnectMessage) {
                    Com.Error(Defines.ERR_DISCONNECT, "Server disconnected\n");
                } else if (msg instanceof ReconnectMessage) {
                    Com.Printf("Server disconnected, reconnecting\n");
                    if (ClientGlobals.cls.download != null) {
                        //ZOID, close download
                        try {
                            ClientGlobals.cls.download.close();
                        } catch (IOException e) {
                        }
                        ClientGlobals.cls.download = null;
                    }
                    ClientGlobals.cls.state = Defines.ca_connecting;
                    ClientGlobals.cls.connect_time = -99999; // CL_CheckForResend() will
                    // fire immediately

                } else if (msg instanceof PrintMessage) {
                    if (((PrintMessage) msg).level == Defines.PRINT_CHAT) {
                        S.StartLocalSound("misc/talk.wav");
                        ClientGlobals.con.ormask = 128;
                    }
                    Com.Printf(((PrintMessage) msg).text);
                    ClientGlobals.con.ormask = 0;
                } else if (msg instanceof PrintCenterMessage) {
                    SCR.CenterPrint(((PrintCenterMessage) msg).text);
                } else if (msg instanceof StuffTextMessage) {
                    Com.DPrintf("stufftext: " + ((StuffTextMessage) msg).text + "\n");
                    Cbuf.AddText(((StuffTextMessage) msg).text);
                } else if (msg instanceof ServerDataMessage) {
                    Cbuf.Execute(); // make sure any stuffed commands are done
                    ParseServerData((ServerDataMessage) msg);
                } else if (msg instanceof ConfigStringMessage) {
                    ParseConfigString((ConfigStringMessage) msg);
                } else if (msg instanceof SoundMessage) {
                    ParseStartSoundPacket((SoundMessage) msg);
                } else if (msg instanceof WeaponSoundMessage) {
                    CL_fx.ParseMuzzleFlash((WeaponSoundMessage) msg);
                } else if (msg instanceof MuzzleFlash2Message) {
                    CL_fx.ParseMuzzleFlash2((MuzzleFlash2Message) msg);
                } else if (msg instanceof FrameHeaderMessage) {
                    old = CL_ents.processFrameMessage((FrameHeaderMessage) msg);
                } else if (msg instanceof PlayerInfoMessage) {
                    CL_ents.ParsePlayerstate(old, ClientGlobals.cl.frame, (PlayerInfoMessage) msg);
                } else if (msg instanceof LayoutMessage) {
                    ClientGlobals.cl.layout = ((LayoutMessage) msg).layout;
                } else if (msg instanceof InventoryMessage) {
                    CL_inv.ParseInventory((InventoryMessage) msg);
                } else if (msg instanceof TEMessage) {
                    CL_tent.ParseTEnt((TEMessage) msg);
                } else if (msg instanceof SpawnBaselineMessage) {
                    SpawnBaselineMessage m = (SpawnBaselineMessage) msg;
                    ClientGlobals.cl_entities[m.entityState.number].baseline.set(m.entityState);
                } else if (msg instanceof PacketEntitiesMessage) {
//                     should be called after CL_ents.processFrameMessage
                    CL_ents.parsePacketEntities(old, (PacketEntitiesMessage) msg);
                } else if (msg instanceof DownloadMessage) {
                    ParseDownload((DownloadMessage) msg);
                }
                continue;
            }
            CL_view.AddNetgraph();

            //
            // we don't know if it is ok to save a demo message until
            // after we have parsed the frame
            //
            if (ClientGlobals.cls.demorecording && !ClientGlobals.cls.demowaiting)
                CL.WriteDemoMessage();
        }
    }
}