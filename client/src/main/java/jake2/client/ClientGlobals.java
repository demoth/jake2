package jake2.client;

import jake2.client.render.DummyRenderer;
import jake2.client.render.model_t;
import jake2.qcommon.Defines;
import jake2.qcommon.exec.cvar_t;
import jake2.qcommon.entity_state_t;

public class ClientGlobals {
    static final console_t con = new console_t();

    public static client_state_t cl = new client_state_t();

    public static centity_t[] cl_entities;
    public static entity_state_t[] cl_parse_entities;
    public static client_static_t cls = new client_static_t();
    // Renderer interface used by VID, SCR, ...
    public static refexport_t re = new DummyRenderer();

    static cvar_t m_filter;
    static int vidref_val = Defines.VIDREF_GL;

    static vrect_t scr_vrect = new vrect_t();
    static int chat_bufferlen = 0;
    static int chat_backedit; // sfranzyshen
    static int gun_frame;
    static model_t gun_model;
    static String[] keybindings = new String[256];
    static boolean[] keydown = new boolean[256];
    static boolean chat_team = false;
    static String chat_buffer = "";
    static byte[][] key_lines;
    static int key_linepos;
    static int edit_line;
    static cvar_t crosshair;

    static cvar_t con_notifytime;

    public static viddef_t viddef = new viddef_t();

    static {
        cl_parse_entities = new entity_state_t[Defines.MAX_PARSE_ENTITIES];
        for (int i = 0; i < ClientGlobals.cl_parse_entities.length; i++) {
            cl_parse_entities[i] = new entity_state_t(null);
        }

        cl_entities = new centity_t[Defines.MAX_EDICTS];
        for (int i = 0; i < ClientGlobals.cl_entities.length; i++) {
            cl_entities[i] = new centity_t();
        }

        key_lines = new byte[32][];
        for (int i = 0; i < ClientGlobals.key_lines.length; i++) {
            key_lines[i] = new byte[Defines.MAXCMDLINE];
        }


    }


    static cvar_t rcon_client_password;
    static cvar_t rcon_address;
    static cvar_t cl_shownet;
    static cvar_t cl_showmiss;
    static cvar_t cl_showclamp;

    public static cvar_t cl_paused;
    //
    //	   userinfo
    //
    static cvar_t info_password;
    static cvar_t info_spectator;
    static cvar_t name;
    static cvar_t skin;
    static cvar_t rate;
    public static cvar_t fov;
    static cvar_t msg;
    static cvar_t hand;
    static cvar_t gender;
    static cvar_t gender_auto;
    static cvar_t cl_vwep;
    static cvar_t m_pitch;
    static cvar_t m_yaw;
    static cvar_t m_forward;
    static cvar_t m_side;

    static cvar_t cl_lightlevel;
    static cvar_t freelook;
    static cvar_t lookspring;
    static cvar_t lookstrafe;
    static cvar_t sensitivity;
    static cvar_t in_mouse;
    static cvar_t in_joystick;

    static cvar_t cl_3rd; //third person view
    static cvar_t cl_3rd_angle; //third person view
    static cvar_t cl_3rd_dist; //third person view

    static cvar_t cl_map; // CDawg hud map, sfranzyshen
    static cvar_t cl_map_zoom; // CDawg hud map, sfranzyshen

    static cvar_t cl_add_blend;
    static cvar_t cl_add_entities;
    static cvar_t cl_add_lights;
    static cvar_t cl_add_particles;
    static cvar_t cl_anglespeedkey;
    static cvar_t cl_autoskins;
    static cvar_t cl_footsteps;
    static cvar_t cl_forwardspeed;
    static cvar_t cl_gun;
    static cvar_t cl_maxfps;
    static cvar_t cl_noskins;
    static cvar_t cl_pitchspeed;
    static cvar_t cl_predict;
    static cvar_t cl_run;
    static cvar_t cl_sidespeed;
    static cvar_t cl_stereo;
    static cvar_t cl_stereo_separation;
    static cvar_t cl_timedemo = new cvar_t();
    static cvar_t cl_timeout;
    static cvar_t cl_upspeed;
    static cvar_t cl_yawspeed;
}
