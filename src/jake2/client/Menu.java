/*
 * Menu.java
 * Copyright (C) 2004
 * 
 * $Id: Menu.java,v 1.11 2004-10-04 12:50:37 hzi Exp $
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

import jake2.Globals;
import jake2.game.Cmd;
import jake2.game.cvar_t;
import jake2.qcommon.*;
import jake2.sound.S;
import jake2.sys.NET;
import jake2.sys.Sys;
import jake2.util.*;

import java.awt.Dimension;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Menu
 * 
 *  
 */

abstract class keyfunc_t {
    abstract String execute(int key);
}

public final class Menu extends Key {

    static int m_main_cursor;

    static final int NUM_CURSOR_FRAMES = 15;

    static final String menu_in_sound = "misc/menu1.wav";

    static final String menu_move_sound = "misc/menu2.wav";

    static final String menu_out_sound = "misc/menu3.wav";

    static boolean m_entersound; // play after drawing a frame, so caching

    // won't disrupt the sound

    static xcommand_t m_drawfunc;

    static keyfunc_t m_keyfunc;

    //	  =============================================================================
    /* Support Routines */

    public final static int MAX_MENU_DEPTH = 8;

    public static class menulayer_t {
        xcommand_t draw;

        keyfunc_t key;
    }

    static class menuframework_s {
        int x, y;

        int cursor;

        int nitems;

        int nslots;

        menucommon_s items[] = new menucommon_s[64];

        String statusbar;

        //void (*cursordraw)( struct _tag_menuframework *m );
        mcallback cursordraw;

    }

    abstract static class mcallback {
        abstract public void execute(Object self);
    }

    static class menucommon_s {
        int type;

        String name = "";

        int x, y;

        menuframework_s parent;

        int cursor_offset;

        int localdata[] = { 0, 0, 0, 0 };

        int flags;

        int n = -1; //position in an array.

        String statusbar;

        mcallback callback;

        mcallback statusbarfunc;

        mcallback ownerdraw;

        mcallback cursordraw;
    }

    static class menufield_s extends menucommon_s {
        //char buffer[80];
        StringBuffer buffer; //allow deletion.

        int cursor;

        int length;

        int visible_length;

        int visible_offset;
    }

    static class menuslider_s extends menucommon_s {

        float minvalue;

        float maxvalue;

        float curvalue;

        float range;
    }

    static class menulist_s extends menucommon_s {
        int curvalue;

        String itemnames[];
    }

    static class menuaction_s extends menucommon_s {

    }

    static class menuseparator_s extends menucommon_s {

    }

    public static menulayer_t m_layers[] = new menulayer_t[MAX_MENU_DEPTH];

    public static int m_menudepth;

    static void Banner(String name) {
        Dimension dim = new Dimension();
        Globals.re.DrawGetPicSize(dim, name);

        Globals.re.DrawPic(viddef.width / 2 - dim.width / 2,
                viddef.height / 2 - 110, name);
    }

    static void PushMenu(xcommand_t draw, keyfunc_t key) { //, String(*key)
                                                           // (int k) ) {
        int i;

        if (Cvar.VariableValue("maxclients") == 1 && Globals.server_state != 0)
            Cvar.Set("paused", "1");

        // if this menu is already present, drop back to that level
        // to avoid stacking menus by hotkeys
        for (i = 0; i < m_menudepth; i++)
            if (m_layers[i].draw == draw && m_layers[i].key == key) {
                m_menudepth = i;
            }

        if (i == m_menudepth) {
            if (m_menudepth >= MAX_MENU_DEPTH)
                Com.Error(ERR_FATAL, "PushMenu: MAX_MENU_DEPTH");

            m_layers[m_menudepth].draw = m_drawfunc;
            m_layers[m_menudepth].key = m_keyfunc;
            m_menudepth++;
        }

        m_drawfunc = draw;
        m_keyfunc = key;

        m_entersound = true;

        cls.key_dest = key_menu;
    }

    static void ForceMenuOff() {
        m_drawfunc = null;
        m_keyfunc = null;
        cls.key_dest = key_game;
        m_menudepth = 0;
        Key.ClearStates();
        Cvar.Set("paused", "0");
    }

    static void PopMenu() {
        S.StartLocalSound(menu_out_sound);
        if (m_menudepth < 1)
            Com.Error(ERR_FATAL, "PopMenu: depth < 1");
        m_menudepth--;

        m_drawfunc = m_layers[m_menudepth].draw;
        m_keyfunc = m_layers[m_menudepth].key;

        if (0 == m_menudepth)
            ForceMenuOff();
    }

    static String Default_MenuKey(menuframework_s m, int key) {
        String sound = null;
        menucommon_s item;

        if (m != null) {
            if ((item = ((menucommon_s) Menu_ItemAtCursor(m))) != null) {
                if (item.type == MTYPE_FIELD) {
                    if (Field_Key((menufield_s) item, key))
                        return null;
                }
            }
        }

        switch (key) {
        case K_ESCAPE:
            PopMenu();
            return menu_out_sound;
        case K_KP_UPARROW:
        case K_UPARROW:
            if (m != null) {
                m.cursor--;
                Menu_AdjustCursor(m, -1);
                sound = menu_move_sound;
            }
            break;
        case K_TAB:
            if (m != null) {
                m.cursor++;
                Menu_AdjustCursor(m, 1);
                sound = menu_move_sound;
            }
            break;
        case K_KP_DOWNARROW:
        case K_DOWNARROW:
            if (m != null) {
                m.cursor++;
                Menu_AdjustCursor(m, 1);
                sound = menu_move_sound;
            }
            break;
        case K_KP_LEFTARROW:
        case K_LEFTARROW:
            if (m != null) {
                Menu_SlideItem(m, -1);
                sound = menu_move_sound;
            }
            break;
        case K_KP_RIGHTARROW:
        case K_RIGHTARROW:
            if (m != null) {
                Menu_SlideItem(m, 1);
                sound = menu_move_sound;
            }
            break;

        case K_MOUSE1:
        case K_MOUSE2:
        case K_MOUSE3:
        case K_JOY1:
        case K_JOY2:
        case K_JOY3:
        case K_JOY4:
        /*
         * case K_AUX1 : case K_AUX2 : case K_AUX3 : case K_AUX4 : case K_AUX5 :
         * case K_AUX6 : case K_AUX7 : case K_AUX8 : case K_AUX9 : case K_AUX10 :
         * case K_AUX11 : case K_AUX12 : case K_AUX13 : case K_AUX14 : case
         * K_AUX15 : case K_AUX16 : case K_AUX17 : case K_AUX18 : case K_AUX19 :
         * case K_AUX20 : case K_AUX21 : case K_AUX22 : case K_AUX23 : case
         * K_AUX24 : case K_AUX25 : case K_AUX26 : case K_AUX27 : case K_AUX28 :
         * case K_AUX29 : case K_AUX30 : case K_AUX31 : case K_AUX32 :
         */
        case K_KP_ENTER:
        case K_ENTER:
            if (m != null)
                Menu_SelectItem(m);
            sound = menu_move_sound;
            break;
        }

        return sound;
    }

    /*
     * ================ DrawCharacter
     * 
     * Draws one solid graphics character cx and cy are in 320*240 coordinates,
     * and will be centered on higher res screens. ================
     */
    public static void DrawCharacter(int cx, int cy, int num) {
        re.DrawChar(cx + ((viddef.width - 320) >> 1), cy
                + ((viddef.height - 240) >> 1), num);
    }

    public static void Print(int cx, int cy, String str) {
        //while (*str)
        for (int n = 0; n < str.length(); n++) {
            DrawCharacter(cx, cy, str.charAt(n) + 128);
            //str++;
            cx += 8;
        }
    }

    public static void PrintWhite(int cx, int cy, String str) {
        for (int n = 0; n < str.length(); n++) {
            DrawCharacter(cx, cy, str.charAt(n));
            //str++;
            cx += 8;
        }
    }

    public static void DrawPic(int x, int y, String pic) {
        re.DrawPic(x + ((viddef.width - 320) >> 1), y
                + ((viddef.height - 240) >> 1), pic);
    }

    /*
     * ============= DrawCursor
     * 
     * Draws an animating cursor with the point at x,y. The pic will extend to
     * the left of x, and both above and below y. =============
     */
    static boolean cached;

    static void DrawCursor(int x, int y, int f) {
        //char cursorname[80];
        String cursorname;

        assert (f >= 0) : "negative time and cursor bug";

        f = Math.abs(f);

        if (!cached) {
            int i;

            for (i = 0; i < NUM_CURSOR_FRAMES; i++) {
                cursorname = "m_cursor" + i;

                re.RegisterPic(cursorname);
            }
            cached = true;
        }

        cursorname = "m_cursor" + f;
        re.DrawPic(x, y, cursorname);
    }

    public static void DrawTextBox(int x, int y, int width, int lines) {
        int cx, cy;
        int n;

        // draw left side
        cx = x;
        cy = y;
        DrawCharacter(cx, cy, 1);

        for (n = 0; n < lines; n++) {
            cy += 8;
            DrawCharacter(cx, cy, 4);
        }
        DrawCharacter(cx, cy + 8, 7);

        // draw middle
        cx += 8;
        while (width > 0) {
            cy = y;
            DrawCharacter(cx, cy, 2);

            for (n = 0; n < lines; n++) {
                cy += 8;
                DrawCharacter(cx, cy, 5);
            }
            DrawCharacter(cx, cy + 8, 8);

            width -= 1;
            cx += 8;
        }

        // draw right side
        cy = y;
        DrawCharacter(cx, cy, 3);
        for (n = 0; n < lines; n++) {
            cy += 8;
            DrawCharacter(cx, cy, 6);

        }
        DrawCharacter(cx, cy + 8, 9);

    }

    /*
     * =======================================================================
     * 
     * MAIN MENU
     * 
     * =======================================================================
     */
    static final int MAIN_ITEMS = 5;

    static xcommand_t Main_Draw = new xcommand_t() {
        public void execute() {
            Main_Draw();
        }
    };

    static void Main_Draw() {
        int i;
        int w, h;
        int ystart;
        int xoffset;
        int widest = -1;
        int totalheight = 0;
        String litname;
        String[] names = { "m_main_game", "m_main_multiplayer",
                "m_main_options", "m_main_video", "m_main_quit" };
        Dimension dim = new Dimension();

        for (i = 0; i < names.length; i++) {
            Globals.re.DrawGetPicSize(dim, names[i]);
            w = dim.width;
            h = dim.height;

            if (w > widest)
                widest = w;
            totalheight += (h + 12);
        }

        ystart = (Globals.viddef.height / 2 - 110);
        xoffset = (Globals.viddef.width - widest + 70) / 2;

        for (i = 0; i < names.length; i++) {
            if (i != m_main_cursor)
                Globals.re.DrawPic(xoffset, ystart + i * 40 + 13, names[i]);
        }

        //strcat(litname, "_sel");
        litname = names[m_main_cursor] + "_sel";
        Globals.re.DrawPic(xoffset, ystart + m_main_cursor * 40 + 13, litname);

        DrawCursor(xoffset - 25, ystart + m_main_cursor * 40 + 11,
                (int) ((Globals.cls.realtime / 100)) % NUM_CURSOR_FRAMES);

        Globals.re.DrawGetPicSize(dim, "m_main_plaque");
        w = dim.width;
        h = dim.height;
        Globals.re.DrawPic(xoffset - 30 - w, ystart, "m_main_plaque");

        Globals.re.DrawPic(xoffset - 30 - w, ystart + h + 5, "m_main_logo");
    }

    static keyfunc_t Main_Key = new keyfunc_t() {
        public String execute(int key) {
            return Main_Key(key);
        }
    };

    static String Main_Key(int key) {
        String sound = menu_move_sound;

        switch (key) {
        case Key.K_ESCAPE:
            PopMenu();
            break;

        case Key.K_KP_DOWNARROW:
        case Key.K_DOWNARROW:
            if (++m_main_cursor >= MAIN_ITEMS)
                m_main_cursor = 0;
            return sound;

        case Key.K_KP_UPARROW:
        case Key.K_UPARROW:
            if (--m_main_cursor < 0)
                m_main_cursor = MAIN_ITEMS - 1;
            return sound;

        case Key.K_KP_ENTER:
        case Key.K_ENTER:
            m_entersound = true;

            switch (m_main_cursor) {
            case 0:
                Menu_Game_f();
                break;

            case 1:
                Menu_Multiplayer_f();
                break;

            case 2:
                Menu_Options_f();
                break;

            case 3:
                Menu_Video_f();
                break;

            case 4:
                Menu_Quit_f();
                break;
            }
        }

        return null;
    }

    static xcommand_t Menu_Main = new xcommand_t() {
        public void execute() {
            Menu_Main_f();
        }
    };

    static void Menu_Main_f() {
        PushMenu(new xcommand_t() {
            public void execute() {
                Main_Draw();
            }
        }, new keyfunc_t() {
            public String execute(int key) {
                return Main_Key(key);
            }
        });
    }

    /*
     * =======================================================================
     * 
     * MULTIPLAYER MENU
     * 
     * =======================================================================
     */
    static menuframework_s s_multiplayer_menu = new menuframework_s();

    static menuaction_s s_join_network_server_action = new menuaction_s();

    static menuaction_s s_start_network_server_action = new menuaction_s();

    static menuaction_s s_player_setup_action = new menuaction_s();

    static void Multiplayer_MenuDraw() {
        Banner("m_banner_multiplayer");

        Menu_AdjustCursor(s_multiplayer_menu, 1);
        Menu_Draw(s_multiplayer_menu);
    }

    static void PlayerSetupFunc(Object unused) {
        Menu_PlayerConfig_f();
    }

    static void JoinNetworkServerFunc(Object unused) {
        Menu_JoinServer_f();
    }

    static void StartNetworkServerFunc(Object unused) {
        Menu_StartServer_f();
    }

    static void Multiplayer_MenuInit() {
        s_multiplayer_menu.x = (int) (viddef.width * 0.50f - 64);
        s_multiplayer_menu.nitems = 0;

        s_join_network_server_action.type = MTYPE_ACTION;
        s_join_network_server_action.flags = QMF_LEFT_JUSTIFY;
        s_join_network_server_action.x = 0;
        s_join_network_server_action.y = 0;
        s_join_network_server_action.name = " join network server";
        s_join_network_server_action.callback = new mcallback() {
            public void execute(Object o) {
                JoinNetworkServerFunc(o);
            };
        };

        s_start_network_server_action.type = MTYPE_ACTION;
        s_start_network_server_action.flags = QMF_LEFT_JUSTIFY;
        s_start_network_server_action.x = 0;
        s_start_network_server_action.y = 10;
        s_start_network_server_action.name = " start network server";
        s_start_network_server_action.callback = new mcallback() {
            public void execute(Object o) {
                StartNetworkServerFunc(o);
            }
        };

        s_player_setup_action.type = MTYPE_ACTION;
        s_player_setup_action.flags = QMF_LEFT_JUSTIFY;
        s_player_setup_action.x = 0;
        s_player_setup_action.y = 20;
        s_player_setup_action.name = " player setup";
        s_player_setup_action.callback = new mcallback() {
            public void execute(Object o) {
                PlayerSetupFunc(o);
            }
        };

        Menu_AddItem(s_multiplayer_menu, s_join_network_server_action);
        Menu_AddItem(s_multiplayer_menu, s_start_network_server_action);
        Menu_AddItem(s_multiplayer_menu, s_player_setup_action);

        Menu_SetStatusBar(s_multiplayer_menu, null);

        Menu_Center(s_multiplayer_menu);
    }

    static String Multiplayer_MenuKey(int key) {
        return Default_MenuKey(s_multiplayer_menu, key);
    }

    static xcommand_t Menu_Multiplayer = new xcommand_t() {
        public void execute() {
            Menu_Multiplayer_f();
        }
    };

    static void Menu_Multiplayer_f() {
        Multiplayer_MenuInit();
        PushMenu(new xcommand_t() {
            public void execute() {
                Multiplayer_MenuDraw();
            }
        }, new keyfunc_t() {
            public String execute(int key) {
                return Multiplayer_MenuKey(key);
            }
        });
    }

    /*
     * =======================================================================
     * 
     * KEYS MENU
     * 
     * =======================================================================
     */
    static String bindnames[][] = { { "+attack", "attack" },
            { "weapnext", "next weapon" }, { "+forward", "walk forward" },
            { "+back", "backpedal" }, { "+left", "turn left" },
            { "+right", "turn right" }, { "+speed", "run" },
            { "+moveleft", "step left" }, { "+moveright", "step right" },
            { "+strafe", "sidestep" }, { "+lookup", "look up" },
            { "+lookdown", "look down" }, { "centerview", "center view" },
            { "+mlook", "mouse look" }, { "+klook", "keyboard look" },
            { "+moveup", "up / jump" }, { "+movedown", "down / crouch" }, {

            "inven", "inventory" }, { "invuse", "use item" },
            { "invdrop", "drop item" }, { "invprev", "prev item" },
            { "invnext", "next item" }, {

            "cmd help", "help computer" }, { null, null } };

    int keys_cursor;

    static boolean bind_grab;

    static menuframework_s s_keys_menu = new menuframework_s();

    static menuaction_s s_keys_attack_action = new menuaction_s();

    static menuaction_s s_keys_change_weapon_action = new menuaction_s();

    static menuaction_s s_keys_walk_forward_action = new menuaction_s();

    static menuaction_s s_keys_backpedal_action = new menuaction_s();

    static menuaction_s s_keys_turn_left_action = new menuaction_s();

    static menuaction_s s_keys_turn_right_action = new menuaction_s();

    static menuaction_s s_keys_run_action = new menuaction_s();

    static menuaction_s s_keys_step_left_action = new menuaction_s();

    static menuaction_s s_keys_step_right_action = new menuaction_s();

    static menuaction_s s_keys_sidestep_action = new menuaction_s();

    static menuaction_s s_keys_look_up_action = new menuaction_s();

    static menuaction_s s_keys_look_down_action = new menuaction_s();

    static menuaction_s s_keys_center_view_action = new menuaction_s();

    static menuaction_s s_keys_mouse_look_action = new menuaction_s();

    static menuaction_s s_keys_keyboard_look_action = new menuaction_s();

    static menuaction_s s_keys_move_up_action = new menuaction_s();

    static menuaction_s s_keys_move_down_action = new menuaction_s();

    static menuaction_s s_keys_inventory_action = new menuaction_s();

    static menuaction_s s_keys_inv_use_action = new menuaction_s();

    static menuaction_s s_keys_inv_drop_action = new menuaction_s();

    static menuaction_s s_keys_inv_prev_action = new menuaction_s();

    static menuaction_s s_keys_inv_next_action = new menuaction_s();

    static menuaction_s s_keys_help_computer_action = new menuaction_s();

    static void UnbindCommand(String command) {
        int j;
        String b;

        for (j = 0; j < 256; j++) {
            b = keybindings[j];
            if (b == null)
                continue;
            if (b.equals(command))
                Key.SetBinding(j, "");
        }
    }

    static void FindKeysForCommand(String command, int twokeys[]) {
        int count;
        int j;
        String b;

        twokeys[0] = twokeys[1] = -1;
        count = 0;

        for (j = 0; j < 256; j++) {
            b = keybindings[j];
            if (b == null)
                continue;

            if (b.equals(command)) {
                twokeys[count] = j;
                count++;
                if (count == 2)
                    break;
            }
        }
    }

    static void KeyCursorDrawFunc(menuframework_s menu) {
        if (bind_grab)
            re.DrawChar(menu.x, menu.y + menu.cursor * 9, '=');
        else
            re.DrawChar(menu.x, menu.y + menu.cursor * 9, 12 + ((int) (Sys
                    .Milliseconds() / 250) & 1));
    }

    static void DrawKeyBindingFunc(Object self) {
        int keys[] = { 0, 0 };
        menuaction_s a = (menuaction_s) self;

        FindKeysForCommand(bindnames[a.localdata[0]][0], keys);

        if (keys[0] == -1) {
            Menu_DrawString(a.x + a.parent.x + 16, a.y + a.parent.y, "???");
        } else {
            int x;
            String name;

            name = Key.KeynumToString(keys[0]);

            Menu_DrawString(a.x + a.parent.x + 16, a.y + a.parent.y, name);

            x = name.length() * 8;

            if (keys[1] != -1) {
                Menu_DrawString(a.x + a.parent.x + 24 + x, a.y + a.parent.y,
                        "or");
                Menu_DrawString(a.x + a.parent.x + 48 + x, a.y + a.parent.y,
                        Key.KeynumToString(keys[1]));
            }
        }
    }

    static void KeyBindingFunc(Object self) {
        menuaction_s a = (menuaction_s) self;
        int keys[] = { 0, 0 };

        FindKeysForCommand(bindnames[a.localdata[0]][0], keys);

        if (keys[1] != -1)
            UnbindCommand(bindnames[a.localdata[0]][0]);

        bind_grab = true;

        Menu_SetStatusBar(s_keys_menu, "press a key or button for this action");
    }

    static void Keys_MenuInit() {
        int y = 0;
        int i = 0;

        s_keys_menu.x = (int) (viddef.width * 0.50);
        s_keys_menu.nitems = 0;
        s_keys_menu.cursordraw = new mcallback() {
            public void execute(Object o) {
                KeyCursorDrawFunc((menuframework_s) o);
            }
        };

        s_keys_attack_action.type = MTYPE_ACTION;
        s_keys_attack_action.flags = QMF_GRAYED;
        s_keys_attack_action.x = 0;
        s_keys_attack_action.y = y;
        s_keys_attack_action.ownerdraw = new mcallback() {
            public void execute(Object o) {
                DrawKeyBindingFunc(o);
            }
        };
        s_keys_attack_action.localdata[0] = i;
        s_keys_attack_action.name = bindnames[s_keys_attack_action.localdata[0]][1];

        s_keys_change_weapon_action.type = MTYPE_ACTION;
        s_keys_change_weapon_action.flags = QMF_GRAYED;
        s_keys_change_weapon_action.x = 0;
        s_keys_change_weapon_action.y = y += 9;
        s_keys_change_weapon_action.ownerdraw = new mcallback() {
            public void execute(Object o) {
                DrawKeyBindingFunc(o);
            }
        };

        s_keys_change_weapon_action.localdata[0] = ++i;
        s_keys_change_weapon_action.name = bindnames[s_keys_change_weapon_action.localdata[0]][1];

        s_keys_walk_forward_action.type = MTYPE_ACTION;
        s_keys_walk_forward_action.flags = QMF_GRAYED;
        s_keys_walk_forward_action.x = 0;
        s_keys_walk_forward_action.y = y += 9;
        s_keys_walk_forward_action.ownerdraw = new mcallback() {
            public void execute(Object o) {
                DrawKeyBindingFunc(o);
            }
        };
        s_keys_walk_forward_action.localdata[0] = ++i;
        s_keys_walk_forward_action.name = bindnames[s_keys_walk_forward_action.localdata[0]][1];

        s_keys_backpedal_action.type = MTYPE_ACTION;
        s_keys_backpedal_action.flags = QMF_GRAYED;
        s_keys_backpedal_action.x = 0;
        s_keys_backpedal_action.y = y += 9;
        s_keys_backpedal_action.ownerdraw = new mcallback() {
            public void execute(Object o) {
                DrawKeyBindingFunc(o);
            }
        };
        s_keys_backpedal_action.localdata[0] = ++i;
        s_keys_backpedal_action.name = bindnames[s_keys_backpedal_action.localdata[0]][1];

        s_keys_turn_left_action.type = MTYPE_ACTION;
        s_keys_turn_left_action.flags = QMF_GRAYED;
        s_keys_turn_left_action.x = 0;
        s_keys_turn_left_action.y = y += 9;
        s_keys_turn_left_action.ownerdraw = new mcallback() {
            public void execute(Object o) {
                DrawKeyBindingFunc(o);
            }
        };
        s_keys_turn_left_action.localdata[0] = ++i;
        s_keys_turn_left_action.name = bindnames[s_keys_turn_left_action.localdata[0]][1];

        s_keys_turn_right_action.type = MTYPE_ACTION;
        s_keys_turn_right_action.flags = QMF_GRAYED;
        s_keys_turn_right_action.x = 0;
        s_keys_turn_right_action.y = y += 9;
        s_keys_turn_right_action.ownerdraw = new mcallback() {
            public void execute(Object o) {
                DrawKeyBindingFunc(o);
            }
        };
        s_keys_turn_right_action.localdata[0] = ++i;
        s_keys_turn_right_action.name = bindnames[s_keys_turn_right_action.localdata[0]][1];

        s_keys_run_action.type = MTYPE_ACTION;
        s_keys_run_action.flags = QMF_GRAYED;
        s_keys_run_action.x = 0;
        s_keys_run_action.y = y += 9;
        s_keys_run_action.ownerdraw = new mcallback() {
            public void execute(Object o) {
                DrawKeyBindingFunc(o);
            }
        };
        s_keys_run_action.localdata[0] = ++i;
        s_keys_run_action.name = bindnames[s_keys_run_action.localdata[0]][1];

        s_keys_step_left_action.type = MTYPE_ACTION;
        s_keys_step_left_action.flags = QMF_GRAYED;
        s_keys_step_left_action.x = 0;
        s_keys_step_left_action.y = y += 9;
        s_keys_step_left_action.ownerdraw = new mcallback() {
            public void execute(Object o) {
                DrawKeyBindingFunc(o);
            }
        };
        s_keys_step_left_action.localdata[0] = ++i;
        s_keys_step_left_action.name = bindnames[s_keys_step_left_action.localdata[0]][1];

        s_keys_step_right_action.type = MTYPE_ACTION;
        s_keys_step_right_action.flags = QMF_GRAYED;
        s_keys_step_right_action.x = 0;
        s_keys_step_right_action.y = y += 9;
        s_keys_step_right_action.ownerdraw = new mcallback() {
            public void execute(Object o) {
                DrawKeyBindingFunc(o);
            }
        };

        s_keys_step_right_action.localdata[0] = ++i;
        s_keys_step_right_action.name = bindnames[s_keys_step_right_action.localdata[0]][1];

        s_keys_sidestep_action.type = MTYPE_ACTION;
        s_keys_sidestep_action.flags = QMF_GRAYED;
        s_keys_sidestep_action.x = 0;
        s_keys_sidestep_action.y = y += 9;
        s_keys_sidestep_action.ownerdraw = new mcallback() {
            public void execute(Object o) {
                DrawKeyBindingFunc(o);
            }
        };

        s_keys_sidestep_action.localdata[0] = ++i;
        s_keys_sidestep_action.name = bindnames[s_keys_sidestep_action.localdata[0]][1];

        s_keys_look_up_action.type = MTYPE_ACTION;
        s_keys_look_up_action.flags = QMF_GRAYED;
        s_keys_look_up_action.x = 0;
        s_keys_look_up_action.y = y += 9;
        s_keys_look_up_action.ownerdraw = new mcallback() {
            public void execute(Object o) {
                DrawKeyBindingFunc(o);
            }
        };

        s_keys_look_up_action.localdata[0] = ++i;
        s_keys_look_up_action.name = bindnames[s_keys_look_up_action.localdata[0]][1];

        s_keys_look_down_action.type = MTYPE_ACTION;
        s_keys_look_down_action.flags = QMF_GRAYED;
        s_keys_look_down_action.x = 0;
        s_keys_look_down_action.y = y += 9;
        s_keys_look_down_action.ownerdraw = new mcallback() {
            public void execute(Object o) {
                DrawKeyBindingFunc(o);
            }
        };

        s_keys_look_down_action.localdata[0] = ++i;
        s_keys_look_down_action.name = bindnames[s_keys_look_down_action.localdata[0]][1];

        s_keys_center_view_action.type = MTYPE_ACTION;
        s_keys_center_view_action.flags = QMF_GRAYED;
        s_keys_center_view_action.x = 0;
        s_keys_center_view_action.y = y += 9;
        s_keys_center_view_action.ownerdraw = new mcallback() {
            public void execute(Object o) {
                DrawKeyBindingFunc(o);
            }
        };

        s_keys_center_view_action.localdata[0] = ++i;
        s_keys_center_view_action.name = bindnames[s_keys_center_view_action.localdata[0]][1];

        s_keys_mouse_look_action.type = MTYPE_ACTION;
        s_keys_mouse_look_action.flags = QMF_GRAYED;
        s_keys_mouse_look_action.x = 0;
        s_keys_mouse_look_action.y = y += 9;
        s_keys_mouse_look_action.ownerdraw = new mcallback() {
            public void execute(Object o) {
                DrawKeyBindingFunc(o);
            }
        };

        s_keys_mouse_look_action.localdata[0] = ++i;
        s_keys_mouse_look_action.name = bindnames[s_keys_mouse_look_action.localdata[0]][1];

        s_keys_keyboard_look_action.type = MTYPE_ACTION;
        s_keys_keyboard_look_action.flags = QMF_GRAYED;
        s_keys_keyboard_look_action.x = 0;
        s_keys_keyboard_look_action.y = y += 9;
        s_keys_keyboard_look_action.ownerdraw = new mcallback() {
            public void execute(Object o) {
                DrawKeyBindingFunc(o);
            }
        };

        s_keys_keyboard_look_action.localdata[0] = ++i;
        s_keys_keyboard_look_action.name = bindnames[s_keys_keyboard_look_action.localdata[0]][1];

        s_keys_move_up_action.type = MTYPE_ACTION;
        s_keys_move_up_action.flags = QMF_GRAYED;
        s_keys_move_up_action.x = 0;
        s_keys_move_up_action.y = y += 9;
        s_keys_move_up_action.ownerdraw = new mcallback() {
            public void execute(Object o) {
                DrawKeyBindingFunc(o);
            }
        };

        s_keys_move_up_action.localdata[0] = ++i;
        s_keys_move_up_action.name = bindnames[s_keys_move_up_action.localdata[0]][1];

        s_keys_move_down_action.type = MTYPE_ACTION;
        s_keys_move_down_action.flags = QMF_GRAYED;
        s_keys_move_down_action.x = 0;
        s_keys_move_down_action.y = y += 9;
        s_keys_move_down_action.ownerdraw = new mcallback() {
            public void execute(Object o) {
                DrawKeyBindingFunc(o);
            }
        };

        s_keys_move_down_action.localdata[0] = ++i;
        s_keys_move_down_action.name = bindnames[s_keys_move_down_action.localdata[0]][1];

        s_keys_inventory_action.type = MTYPE_ACTION;
        s_keys_inventory_action.flags = QMF_GRAYED;
        s_keys_inventory_action.x = 0;
        s_keys_inventory_action.y = y += 9;
        s_keys_inventory_action.ownerdraw = new mcallback() {
            public void execute(Object o) {
                DrawKeyBindingFunc(o);
            }
        };

        s_keys_inventory_action.localdata[0] = ++i;
        s_keys_inventory_action.name = bindnames[s_keys_inventory_action.localdata[0]][1];

        s_keys_inv_use_action.type = MTYPE_ACTION;
        s_keys_inv_use_action.flags = QMF_GRAYED;
        s_keys_inv_use_action.x = 0;
        s_keys_inv_use_action.y = y += 9;
        s_keys_inv_use_action.ownerdraw = new mcallback() {
            public void execute(Object o) {
                DrawKeyBindingFunc(o);
            }
        };

        s_keys_inv_use_action.localdata[0] = ++i;
        s_keys_inv_use_action.name = bindnames[s_keys_inv_use_action.localdata[0]][1];

        s_keys_inv_drop_action.type = MTYPE_ACTION;
        s_keys_inv_drop_action.flags = QMF_GRAYED;
        s_keys_inv_drop_action.x = 0;
        s_keys_inv_drop_action.y = y += 9;
        s_keys_inv_drop_action.ownerdraw = new mcallback() {
            public void execute(Object o) {
                DrawKeyBindingFunc(o);
            }
        };

        s_keys_inv_drop_action.localdata[0] = ++i;
        s_keys_inv_drop_action.name = bindnames[s_keys_inv_drop_action.localdata[0]][1];

        s_keys_inv_prev_action.type = MTYPE_ACTION;
        s_keys_inv_prev_action.flags = QMF_GRAYED;
        s_keys_inv_prev_action.x = 0;
        s_keys_inv_prev_action.y = y += 9;
        s_keys_inv_prev_action.ownerdraw = new mcallback() {
            public void execute(Object o) {
                DrawKeyBindingFunc(o);
            }
        };

        s_keys_inv_prev_action.localdata[0] = ++i;
        s_keys_inv_prev_action.name = bindnames[s_keys_inv_prev_action.localdata[0]][1];

        s_keys_inv_next_action.type = MTYPE_ACTION;
        s_keys_inv_next_action.flags = QMF_GRAYED;
        s_keys_inv_next_action.x = 0;
        s_keys_inv_next_action.y = y += 9;
        s_keys_inv_next_action.ownerdraw = new mcallback() {
            public void execute(Object o) {
                DrawKeyBindingFunc(o);
            }
        };

        s_keys_inv_next_action.localdata[0] = ++i;
        s_keys_inv_next_action.name = bindnames[s_keys_inv_next_action.localdata[0]][1];

        s_keys_help_computer_action.type = MTYPE_ACTION;
        s_keys_help_computer_action.flags = QMF_GRAYED;
        s_keys_help_computer_action.x = 0;
        s_keys_help_computer_action.y = y += 9;
        s_keys_help_computer_action.ownerdraw = new mcallback() {
            public void execute(Object o) {
                DrawKeyBindingFunc(o);
            }
        };

        s_keys_help_computer_action.localdata[0] = ++i;
        s_keys_help_computer_action.name = bindnames[s_keys_help_computer_action.localdata[0]][1];

        Menu_AddItem(s_keys_menu, s_keys_attack_action);
        Menu_AddItem(s_keys_menu, s_keys_change_weapon_action);
        Menu_AddItem(s_keys_menu, s_keys_walk_forward_action);
        Menu_AddItem(s_keys_menu, s_keys_backpedal_action);
        Menu_AddItem(s_keys_menu, s_keys_turn_left_action);
        Menu_AddItem(s_keys_menu, s_keys_turn_right_action);
        Menu_AddItem(s_keys_menu, s_keys_run_action);
        Menu_AddItem(s_keys_menu, s_keys_step_left_action);
        Menu_AddItem(s_keys_menu, s_keys_step_right_action);
        Menu_AddItem(s_keys_menu, s_keys_sidestep_action);
        Menu_AddItem(s_keys_menu, s_keys_look_up_action);
        Menu_AddItem(s_keys_menu, s_keys_look_down_action);
        Menu_AddItem(s_keys_menu, s_keys_center_view_action);
        Menu_AddItem(s_keys_menu, s_keys_mouse_look_action);
        Menu_AddItem(s_keys_menu, s_keys_keyboard_look_action);
        Menu_AddItem(s_keys_menu, s_keys_move_up_action);
        Menu_AddItem(s_keys_menu, s_keys_move_down_action);

        Menu_AddItem(s_keys_menu, s_keys_inventory_action);
        Menu_AddItem(s_keys_menu, s_keys_inv_use_action);
        Menu_AddItem(s_keys_menu, s_keys_inv_drop_action);
        Menu_AddItem(s_keys_menu, s_keys_inv_prev_action);
        Menu_AddItem(s_keys_menu, s_keys_inv_next_action);

        Menu_AddItem(s_keys_menu, s_keys_help_computer_action);

        Menu_SetStatusBar(s_keys_menu, "enter to change, backspace to clear");
        Menu_Center(s_keys_menu);
    }

    static xcommand_t Keys_MenuDraw = new xcommand_t() {
        public void execute() {
            Keys_MenuDraw_f();
        }
    };

    static void Keys_MenuDraw_f() {
        Menu_AdjustCursor(s_keys_menu, 1);
        Menu_Draw(s_keys_menu);
    }

    static keyfunc_t Keys_MenuKey = new keyfunc_t() {
        public String execute(int key) {
            return Keys_MenuKey_f(key);
        }
    };

    static String Keys_MenuKey_f(int key) {
        menuaction_s item = (menuaction_s) Menu_ItemAtCursor(s_keys_menu);

        if (bind_grab) {
            if (key != K_ESCAPE && key != '`') {
                //char cmd[1024];
                String cmd;

                //Com_sprintf(cmd, sizeof(cmd), "bind \"%s\" \"%s\"\n",
                // Key_KeynumToString(key), bindnames[item.localdata[0]][0]);
                cmd = "bind \"" + Key.KeynumToString(key) + "\" \""
                        + bindnames[item.localdata[0]][0] + "\"";
                Cbuf.InsertText(cmd);
            }

            Menu_SetStatusBar(s_keys_menu,
                    "enter to change, backspace to clear");
            bind_grab = false;
            return menu_out_sound;
        }

        switch (key) {
        case K_KP_ENTER:
        case K_ENTER:
            KeyBindingFunc(item);
            return menu_in_sound;
        case K_BACKSPACE: // delete bindings
        case K_DEL: // delete bindings
        case K_KP_DEL:
            UnbindCommand(bindnames[item.localdata[0]][0]);
            return menu_out_sound;
        default:
            return Default_MenuKey(s_keys_menu, key);
        }
    }

    static xcommand_t Menu_Keys = new xcommand_t() {
        public void execute() {
            Menu_Keys_f();
        }
    };

    static void Menu_Keys_f() {
        Keys_MenuInit();
        PushMenu(new xcommand_t() {
            public void execute() {
                Keys_MenuDraw_f();
            }
        }, new keyfunc_t() {
            public String execute(int key) {
                return Keys_MenuKey_f(key);
            }
        });
    }

    /*
     * =======================================================================
     * 
     * CONTROLS MENU
     * 
     * =======================================================================
     */
    static cvar_t win_noalttab;

    static menuframework_s s_options_menu = new menuframework_s();

    static menuaction_s s_options_defaults_action = new menuaction_s();

    static menuaction_s s_options_customize_options_action = new menuaction_s();

    static menuslider_s s_options_sensitivity_slider = new menuslider_s();

    static menulist_s s_options_freelook_box = new menulist_s();

    static menulist_s s_options_noalttab_box = new menulist_s();

    static menulist_s s_options_alwaysrun_box = new menulist_s();

    static menulist_s s_options_invertmouse_box = new menulist_s();

    static menulist_s s_options_lookspring_box = new menulist_s();

    static menulist_s s_options_lookstrafe_box = new menulist_s();

    static menulist_s s_options_crosshair_box = new menulist_s();

    static menuslider_s s_options_sfxvolume_slider = new menuslider_s();

    static menulist_s s_options_joystick_box = new menulist_s();

    static menulist_s s_options_cdvolume_box = new menulist_s();

    static menulist_s s_options_quality_list = new menulist_s();

    //static menulist_s s_options_compatibility_list = new menulist_s();
    static menuaction_s s_options_console_action = new menuaction_s();

    static void CrosshairFunc(Object unused) {
        Cvar.SetValue("crosshair", s_options_crosshair_box.curvalue);
    }

    static void JoystickFunc(Object unused) {
        Cvar.SetValue("in_joystick", s_options_joystick_box.curvalue);
    }

    static void CustomizeControlsFunc(Object unused) {
        Menu_Keys_f();
    }

    static void AlwaysRunFunc(Object unused) {
        Cvar.SetValue("cl_run", s_options_alwaysrun_box.curvalue);
    }

    static void FreeLookFunc(Object unused) {
        Cvar.SetValue("freelook", s_options_freelook_box.curvalue);
    }

    static void MouseSpeedFunc(Object unused) {
        Cvar.SetValue("sensitivity",
                s_options_sensitivity_slider.curvalue / 2.0F);
    }

    static void NoAltTabFunc(Object unused) {
        Cvar.SetValue("win_noalttab", s_options_noalttab_box.curvalue);
    }

    static float ClampCvar(float min, float max, float value) {
        if (value < min)
            return min;
        if (value > max)
            return max;
        return value;
    }

    static void ControlsSetMenuItemValues() {
        s_options_sfxvolume_slider.curvalue = Cvar.VariableValue("s_volume") * 10;
        s_options_cdvolume_box.curvalue = 1 - ((int) Cvar
                .VariableValue("cd_nocd"));
        //s_options_quality_list.curvalue = 1 - ((int)
        // Cvar.VariableValue("s_loadas8bit"));
        if ("joal".equals(Cvar.VariableString("s_impl"))) {
            s_options_quality_list.curvalue = 0;
        } else {
            s_options_quality_list.curvalue = 1;
        }

        s_options_sensitivity_slider.curvalue = (sensitivity.value) * 2;

        Cvar.SetValue("cl_run", ClampCvar(0, 1, cl_run.value));
        s_options_alwaysrun_box.curvalue = (int) cl_run.value;

        s_options_invertmouse_box.curvalue = m_pitch.value < 0 ? 1 : 0;

        Cvar.SetValue("lookspring", ClampCvar(0, 1, lookspring.value));
        s_options_lookspring_box.curvalue = (int) lookspring.value;

        Cvar.SetValue("lookstrafe", ClampCvar(0, 1, lookstrafe.value));
        s_options_lookstrafe_box.curvalue = (int) lookstrafe.value;

        Cvar.SetValue("freelook", ClampCvar(0, 1, freelook.value));
        s_options_freelook_box.curvalue = (int) freelook.value;

        Cvar.SetValue("crosshair", ClampCvar(0, 3, Globals.crosshair.value));
        s_options_crosshair_box.curvalue = (int) Globals.crosshair.value;

        Cvar.SetValue("in_joystick", ClampCvar(0, 1, in_joystick.value));
        s_options_joystick_box.curvalue = (int) in_joystick.value;

        s_options_noalttab_box.curvalue = (int) win_noalttab.value;
    }

    static void ControlsResetDefaultsFunc(Object unused) {
        Cbuf.AddText("exec default.cfg\n");
        Cbuf.Execute();

        ControlsSetMenuItemValues();
    }

    static void InvertMouseFunc(Object unused) {
        Cvar.SetValue("m_pitch", -m_pitch.value);
    }

    static void LookspringFunc(Object unused) {
        Cvar.SetValue("lookspring", 1 - lookspring.value);
    }

    static void LookstrafeFunc(Object unused) {
        Cvar.SetValue("lookstrafe", 1 - lookstrafe.value);
    }

    static void UpdateVolumeFunc(Object unused) {
        Cvar.SetValue("s_volume", s_options_sfxvolume_slider.curvalue / 10);
    }

    static void UpdateCDVolumeFunc(Object unused) {
        Cvar.SetValue("cd_nocd", 1 - s_options_cdvolume_box.curvalue);
    }

    static void ConsoleFunc(Object unused) {
        /*
         * * the proper way to do this is probably to have ToggleConsole_f
         * accept a parameter
         */

        if (cl.attractloop) {
            Cbuf.AddText("killserver\n");
            return;
        }

        Key.ClearTyping();
        Console.ClearNotify();

        ForceMenuOff();
        cls.key_dest = key_console;
    }

    static void UpdateSoundQualityFunc(Object unused) {
        boolean driverNotChanged = false;
        if (s_options_quality_list.curvalue != 0) {
            //			Cvar.SetValue("s_khz", 22);
            //			Cvar.SetValue("s_loadas8bit", 0);
            driverNotChanged = S.getDriverName().equals("dummy");
            Cvar.Set("s_impl", "dummy");
        } else {
            //			Cvar.SetValue("s_khz", 11);
            //			Cvar.SetValue("s_loadas8bit", 1);
            driverNotChanged = S.getDriverName().equals("joal");
            Cvar.Set("s_impl", "joal");
        }

        //Cvar.SetValue("s_primary", s_options_compatibility_list.curvalue);

        if (driverNotChanged) {
            re.EndFrame();
            return;
        } else {

            DrawTextBox(8, 120 - 48, 36, 3);
            Print(16 + 16, 120 - 48 + 8, "Restarting the sound system. This");
            Print(16 + 16, 120 - 48 + 16, "could take up to a minute, so");
            Print(16 + 16, 120 - 48 + 24, "please be patient.");

            // the text box won't show up unless we do a buffer swap
            re.EndFrame();

            CL.Snd_Restart_f.execute();
        }
    }

    static String cd_music_items[] = { "disabled", "enabled", null };

    static String soundstate_items[] = { "on", "off", null };

    static String compatibility_items[] = { "max compatibility",
            "max performance", null };

    static String yesno_names[] = { "no", "yes", null };

    static String crosshair_names[] = { "none", "cross", "dot", "angle", null };

    static void Options_MenuInit() {

        win_noalttab = Cvar.Get("win_noalttab", "0", CVAR_ARCHIVE);

        /*
         * * configure controls menu and menu items
         */
        s_options_menu.x = viddef.width / 2;
        s_options_menu.y = viddef.height / 2 - 58;
        s_options_menu.nitems = 0;

        s_options_sfxvolume_slider.type = MTYPE_SLIDER;
        s_options_sfxvolume_slider.x = 0;
        s_options_sfxvolume_slider.y = 0;
        s_options_sfxvolume_slider.name = "effects volume";
        s_options_sfxvolume_slider.callback = new mcallback() {
            public void execute(Object o) {
                UpdateVolumeFunc(o);
            }
        };
        s_options_sfxvolume_slider.minvalue = 0;
        s_options_sfxvolume_slider.maxvalue = 10;
        s_options_sfxvolume_slider.curvalue = Cvar.VariableValue("s_volume") * 10;

        s_options_cdvolume_box.type = MTYPE_SPINCONTROL;
        s_options_cdvolume_box.x = 0;
        s_options_cdvolume_box.y = 10;
        s_options_cdvolume_box.name = "CD music";
        s_options_cdvolume_box.callback = new mcallback() {
            public void execute(Object o) {
                UpdateCDVolumeFunc(o);
            }
        };
        s_options_cdvolume_box.itemnames = cd_music_items;
        s_options_cdvolume_box.curvalue = 1 - (int) Cvar
                .VariableValue("cd_nocd");

        s_options_quality_list.type = MTYPE_SPINCONTROL;
        s_options_quality_list.x = 0;
        s_options_quality_list.y = 20;
        ;
        s_options_quality_list.name = "sound";
        s_options_quality_list.callback = new mcallback() {
            public void execute(Object o) {
                UpdateSoundQualityFunc(o);
            }
        };
        s_options_quality_list.itemnames = soundstate_items;
        //s_options_quality_list.curvalue = 1 - (int)
        // Cvar.VariableValue("s_loadas8bit");

        //		s_options_compatibility_list.type = MTYPE_SPINCONTROL;
        //		s_options_compatibility_list.x = 0;
        //		s_options_compatibility_list.y = 30;
        //		s_options_compatibility_list.name = "sound compatibility";
        //		s_options_compatibility_list.callback = new mcallback() {
        //			public void execute(Object o) {
        //				UpdateSoundQualityFunc(o);
        //			}
        //		};
        //		s_options_compatibility_list.itemnames = compatibility_items;
        //		s_options_compatibility_list.curvalue = (int)
        // Cvar.VariableValue("s_primary");

        s_options_sensitivity_slider.type = MTYPE_SLIDER;
        s_options_sensitivity_slider.x = 0;
        s_options_sensitivity_slider.y = 50;
        s_options_sensitivity_slider.name = "mouse speed";
        s_options_sensitivity_slider.callback = new mcallback() {
            public void execute(Object o) {
                MouseSpeedFunc(o);
            }
        };
        s_options_sensitivity_slider.minvalue = 2;
        s_options_sensitivity_slider.maxvalue = 22;

        s_options_alwaysrun_box.type = MTYPE_SPINCONTROL;
        s_options_alwaysrun_box.x = 0;
        s_options_alwaysrun_box.y = 60;
        s_options_alwaysrun_box.name = "always run";
        s_options_alwaysrun_box.callback = new mcallback() {
            public void execute(Object o) {
                AlwaysRunFunc(o);
            }
        };
        s_options_alwaysrun_box.itemnames = yesno_names;

        s_options_invertmouse_box.type = MTYPE_SPINCONTROL;
        s_options_invertmouse_box.x = 0;
        s_options_invertmouse_box.y = 70;
        s_options_invertmouse_box.name = "invert mouse";
        s_options_invertmouse_box.callback = new mcallback() {
            public void execute(Object o) {
                InvertMouseFunc(o);
            }
        };
        s_options_invertmouse_box.itemnames = yesno_names;

        s_options_lookspring_box.type = MTYPE_SPINCONTROL;
        s_options_lookspring_box.x = 0;
        s_options_lookspring_box.y = 80;
        s_options_lookspring_box.name = "lookspring";
        s_options_lookspring_box.callback = new mcallback() {
            public void execute(Object o) {
                LookspringFunc(o);
            }
        };
        s_options_lookspring_box.itemnames = yesno_names;

        s_options_lookstrafe_box.type = MTYPE_SPINCONTROL;
        s_options_lookstrafe_box.x = 0;
        s_options_lookstrafe_box.y = 90;
        s_options_lookstrafe_box.name = "lookstrafe";
        s_options_lookstrafe_box.callback = new mcallback() {
            public void execute(Object o) {
                LookstrafeFunc(o);
            }
        };
        s_options_lookstrafe_box.itemnames = yesno_names;

        s_options_freelook_box.type = MTYPE_SPINCONTROL;
        s_options_freelook_box.x = 0;
        s_options_freelook_box.y = 100;
        s_options_freelook_box.name = "free look";
        s_options_freelook_box.callback = new mcallback() {
            public void execute(Object o) {
                FreeLookFunc(o);
            }
        };
        s_options_freelook_box.itemnames = yesno_names;

        s_options_crosshair_box.type = MTYPE_SPINCONTROL;
        s_options_crosshair_box.x = 0;
        s_options_crosshair_box.y = 110;
        s_options_crosshair_box.name = "crosshair";
        s_options_crosshair_box.callback = new mcallback() {
            public void execute(Object o) {
                CrosshairFunc(o);
            }
        };
        s_options_crosshair_box.itemnames = crosshair_names;
        /*
         * s_options_noalttab_box.type = MTYPE_SPINCONTROL;
         * s_options_noalttab_box.x = 0; s_options_noalttab_box.y = 110;
         * s_options_noalttab_box.name = "disable alt-tab";
         * s_options_noalttab_box.callback = NoAltTabFunc;
         * s_options_noalttab_box.itemnames = yesno_names;
         */
        s_options_joystick_box.type = MTYPE_SPINCONTROL;
        s_options_joystick_box.x = 0;
        s_options_joystick_box.y = 120;
        s_options_joystick_box.name = "use joystick";
        s_options_joystick_box.callback = new mcallback() {
            public void execute(Object o) {
                JoystickFunc(o);
            }
        };
        s_options_joystick_box.itemnames = yesno_names;

        s_options_customize_options_action.type = MTYPE_ACTION;
        s_options_customize_options_action.x = 0;
        s_options_customize_options_action.y = 140;
        s_options_customize_options_action.name = "customize controls";
        s_options_customize_options_action.callback = new mcallback() {
            public void execute(Object o) {
                CustomizeControlsFunc(o);
            }
        };

        s_options_defaults_action.type = MTYPE_ACTION;
        s_options_defaults_action.x = 0;
        s_options_defaults_action.y = 150;
        s_options_defaults_action.name = "reset defaults";
        s_options_defaults_action.callback = new mcallback() {
            public void execute(Object o) {
                ControlsResetDefaultsFunc(o);
            }
        };

        s_options_console_action.type = MTYPE_ACTION;
        s_options_console_action.x = 0;
        s_options_console_action.y = 160;
        s_options_console_action.name = "go to console";
        s_options_console_action.callback = new mcallback() {
            public void execute(Object o) {
                ConsoleFunc(o);
            }
        };

        ControlsSetMenuItemValues();

        Menu_AddItem(s_options_menu, s_options_sfxvolume_slider);

        Menu_AddItem(s_options_menu, s_options_cdvolume_box);
        Menu_AddItem(s_options_menu, s_options_quality_list);
        //		Menu_AddItem(s_options_menu, s_options_compatibility_list);
        Menu_AddItem(s_options_menu, s_options_sensitivity_slider);
        Menu_AddItem(s_options_menu, s_options_alwaysrun_box);
        Menu_AddItem(s_options_menu, s_options_invertmouse_box);
        Menu_AddItem(s_options_menu, s_options_lookspring_box);
        Menu_AddItem(s_options_menu, s_options_lookstrafe_box);
        Menu_AddItem(s_options_menu, s_options_freelook_box);
        Menu_AddItem(s_options_menu, s_options_crosshair_box);
        //		Menu_AddItem(s_options_menu, s_options_joystick_box);
        Menu_AddItem(s_options_menu, s_options_customize_options_action);
        Menu_AddItem(s_options_menu, s_options_defaults_action);
        Menu_AddItem(s_options_menu, s_options_console_action);
    }

    static void Options_MenuDraw() {
        Banner("m_banner_options");
        Menu_AdjustCursor(s_options_menu, 1);
        Menu_Draw(s_options_menu);
    }

    static String Options_MenuKey(int key) {
        return Default_MenuKey(s_options_menu, key);
    }

    static xcommand_t Menu_Options = new xcommand_t() {
        public void execute() {
            Menu_Options_f();
        }
    };

    static void Menu_Options_f() {
        Options_MenuInit();
        PushMenu(new xcommand_t() {
            public void execute() {
                Options_MenuDraw();
            }
        }, new keyfunc_t() {
            public String execute(int key) {
                return Options_MenuKey(key);
            }
        });
    }

    /*
     * =======================================================================
     * 
     * VIDEO MENU
     * 
     * =======================================================================
     */

    static xcommand_t Menu_Video = new xcommand_t() {
        public void execute() {
            Menu_Video_f();
        }
    };

    static void Menu_Video_f() {
        VID.MenuInit();
        PushMenu(new xcommand_t() {
            public void execute() {
                VID.MenuDraw();
            }
        }, new keyfunc_t() {
            public String execute(int key) {
                return VID.MenuKey(key);
            }
        });
    }

    /*
     * =============================================================================
     * 
     * END GAME MENU
     * 
     * =============================================================================
     */
    static int credits_start_time;

    static String creditsIndex[] = new String[256];

    static String creditsBuffer;

    static String idcredits[] = { "+QUAKE II BY ID SOFTWARE", "",
            "+PROGRAMMING", "John Carmack", "John Cash", "Brian Hook", "",
            "+JAVA PORT BY BYTONIC", "CWEI", "HOZ", "RST", "", "+ART",
            "Adrian Carmack", "Kevin Cloud", "Paul Steed", "", "+LEVEL DESIGN",
            "Tim Willits", "American McGee", "Christian Antkow",
            "Paul Jaquays", "Brandon James", "", "+BIZ", "Todd Hollenshead",
            "Barrett (Bear) Alexander", "Donna Jackson", "", "",
            "+SPECIAL THANKS", "Ben Donges for beta testing", "", "", "", "",
            "", "", "+ADDITIONAL SUPPORT", "", "+LINUX PORT AND CTF",
            "Dave \"Zoid\" Kirsch", "", "+CINEMATIC SEQUENCES",
            "Ending Cinematic by Blur Studio - ", "Venice, CA", "",
            "Environment models for Introduction",
            "Cinematic by Karl Dolgener", "",
            "Assistance with environment design", "by Cliff Iwai", "",
            "+SOUND EFFECTS AND MUSIC",
            "Sound Design by Soundelux Media Labs.",
            "Music Composed and Produced by",
            "Soundelux Media Labs.  Special thanks",
            "to Bill Brown, Tom Ozanich, Brian",
            "Celano, Jeff Eisner, and The Soundelux", "Players.", "",
            "\"Level Music\" by Sonic Mayhem", "www.sonicmayhem.com", "",
            "\"Quake II Theme Song\"", "(C) 1997 Rob Zombie. All Rights",
            "Reserved.", "", "Track 10 (\"Climb\") by Jer Sypult", "",
            "Voice of computers by", "Carly Staehlin-Taylor", "",
            "+THANKS TO ACTIVISION", "+IN PARTICULAR:", "", "John Tam",
            "Steve Rosenthal", "Marty Stratton", "Henk Hartong", "",
            "Quake II(tm) (C)1997 Id Software, Inc.",
            "All Rights Reserved.  Distributed by",
            "Activision, Inc. under license.",
            "Quake II(tm), the Id Software name,",
            "the \"Q II\"(tm) logo and id(tm)",
            "logo are trademarks of Id Software,",
            "Inc. Activision(R) is a registered",
            "trademark of Activision, Inc. All",
            "other trademarks and trade names are",
            "properties of their respective owners.", null };

    static String credits[] = idcredits;

    static String xatcredits[] = { "+QUAKE II MISSION PACK: THE RECKONING",
            "+BY", "+XATRIX ENTERTAINMENT, INC.", "", "+DESIGN AND DIRECTION",
            "Drew Markham", "", "+PRODUCED BY", "Greg Goodrich", "",
            "+PROGRAMMING", "Rafael Paiz", "",
            "+LEVEL DESIGN / ADDITIONAL GAME DESIGN", "Alex Mayberry", "",
            "+LEVEL DESIGN", "Mal Blackwell", "Dan Koppel", "",
            "+ART DIRECTION", "Michael \"Maxx\" Kaufman", "",
            "+COMPUTER GRAPHICS SUPERVISOR AND",
            "+CHARACTER ANIMATION DIRECTION", "Barry Dempsey", "",
            "+SENIOR ANIMATOR AND MODELER", "Jason Hoover", "",
            "+CHARACTER ANIMATION AND", "+MOTION CAPTURE SPECIALIST",
            "Amit Doron", "", "+ART", "Claire Praderie-Markham",
            "Viktor Antonov", "Corky Lehmkuhl", "", "+INTRODUCTION ANIMATION",
            "Dominique Drozdz", "", "+ADDITIONAL LEVEL DESIGN", "Aaron Barber",
            "Rhett Baldwin", "", "+3D CHARACTER ANIMATION TOOLS",
            "Gerry Tyra, SA Technology", "",
            "+ADDITIONAL EDITOR TOOL PROGRAMMING", "Robert Duffy", "",
            "+ADDITIONAL PROGRAMMING", "Ryan Feltrin", "",
            "+PRODUCTION COORDINATOR", "Victoria Sylvester", "",
            "+SOUND DESIGN", "Gary Bradfield", "", "+MUSIC BY", "Sonic Mayhem",
            "", "", "", "+SPECIAL THANKS", "+TO",
            "+OUR FRIENDS AT ID SOFTWARE", "", "John Carmack", "John Cash",
            "Brian Hook", "Adrian Carmack", "Kevin Cloud", "Paul Steed",
            "Tim Willits", "Christian Antkow", "Paul Jaquays", "Brandon James",
            "Todd Hollenshead", "Barrett (Bear) Alexander",
            "Dave \"Zoid\" Kirsch", "Donna Jackson", "", "", "",
            "+THANKS TO ACTIVISION", "+IN PARTICULAR:", "", "Marty Stratton",
            "Henk \"The Original Ripper\" Hartong", "Kevin Kraff",
            "Jamey Gottlieb", "Chris Hepburn", "", "+AND THE GAME TESTERS", "",
            "Tim Vanlaw", "Doug Jacobs", "Steven Rosenthal", "David Baker",
            "Chris Campbell", "Aaron Casillas", "Steve Elwell",
            "Derek Johnstone", "Igor Krinitskiy", "Samantha Lee",
            "Michael Spann", "Chris Toft", "Juan Valdes", "",
            "+THANKS TO INTERGRAPH COMPUTER SYTEMS", "+IN PARTICULAR:", "",
            "Michael T. Nicolaou", "", "",
            "Quake II Mission Pack: The Reckoning",
            "(tm) (C)1998 Id Software, Inc. All",
            "Rights Reserved. Developed by Xatrix",
            "Entertainment, Inc. for Id Software,",
            "Inc. Distributed by Activision Inc.",
            "under license. Quake(R) is a",
            "registered trademark of Id Software,",
            "Inc. Quake II Mission Pack: The",
            "Reckoning(tm), Quake II(tm), the Id",
            "Software name, the \"Q II\"(tm) logo",
            "and id(tm) logo are trademarks of Id",
            "Software, Inc. Activision(R) is a",
            "registered trademark of Activision,",
            "Inc. Xatrix(R) is a registered",
            "trademark of Xatrix Entertainment,",
            "Inc. All other trademarks and trade",
            "names are properties of their", "respective owners.", null };

    static String roguecredits[] = { "+QUAKE II MISSION PACK 2: GROUND ZERO",
            "+BY", "+ROGUE ENTERTAINMENT, INC.", "", "+PRODUCED BY",
            "Jim Molinets", "", "+PROGRAMMING", "Peter Mack",
            "Patrick Magruder", "", "+LEVEL DESIGN", "Jim Molinets",
            "Cameron Lamprecht", "Berenger Fish", "Robert Selitto",
            "Steve Tietze", "Steve Thoms", "", "+ART DIRECTION",
            "Rich Fleider", "", "+ART", "Rich Fleider", "Steve Maines",
            "Won Choi", "", "+ANIMATION SEQUENCES", "Creat Studios",
            "Steve Maines", "", "+ADDITIONAL LEVEL DESIGN", "Rich Fleider",
            "Steve Maines", "Peter Mack", "", "+SOUND", "James Grunke", "",
            "+GROUND ZERO THEME", "+AND", "+MUSIC BY", "Sonic Mayhem", "",
            "+VWEP MODELS", "Brent \"Hentai\" Dill", "", "", "",
            "+SPECIAL THANKS", "+TO", "+OUR FRIENDS AT ID SOFTWARE", "",
            "John Carmack", "John Cash", "Brian Hook", "Adrian Carmack",
            "Kevin Cloud", "Paul Steed", "Tim Willits", "Christian Antkow",
            "Paul Jaquays", "Brandon James", "Todd Hollenshead",
            "Barrett (Bear) Alexander", "Katherine Anna Kang", "Donna Jackson",
            "Dave \"Zoid\" Kirsch", "", "", "", "+THANKS TO ACTIVISION",
            "+IN PARTICULAR:", "", "Marty Stratton", "Henk Hartong",
            "Mitch Lasky", "Steve Rosenthal", "Steve Elwell", "",
            "+AND THE GAME TESTERS", "", "The Ranger Clan",
            "Dave \"Zoid\" Kirsch", "Nihilistic Software", "Robert Duffy", "",
            "And Countless Others", "", "", "",
            "Quake II Mission Pack 2: Ground Zero",
            "(tm) (C)1998 Id Software, Inc. All",
            "Rights Reserved. Developed by Rogue",
            "Entertainment, Inc. for Id Software,",
            "Inc. Distributed by Activision Inc.",
            "under license. Quake(R) is a",
            "registered trademark of Id Software,",
            "Inc. Quake II Mission Pack 2: Ground",
            "Zero(tm), Quake II(tm), the Id",
            "Software name, the \"Q II\"(tm) logo",
            "and id(tm) logo are trademarks of Id",
            "Software, Inc. Activision(R) is a",
            "registered trademark of Activision,",
            "Inc. Rogue(R) is a registered",
            "trademark of Rogue Entertainment,",
            "Inc. All other trademarks and trade",
            "names are properties of their", "respective owners.", null };

    public static void Credits_MenuDraw() {
        int i, y;

        /*
         * * draw the credits
         */
        for (i = 0, y = (int) (viddef.height - ((cls.realtime - credits_start_time) / 40.0F)); credits[i] != null
                && y < viddef.height; y += 10, i++) {
            int j, stringoffset = 0;
            boolean bold = false;

            if (y <= -8)
                continue;

            if (credits[i].length() > 0 && credits[i].charAt(0) == '+') {
                bold = true;
                stringoffset = 1;
            } else {
                bold = false;
                stringoffset = 0;
            }

            for (j = 0; j + stringoffset < credits[i].length(); j++) {
                int x;

                x = (viddef.width - credits[i].length() * 8 - stringoffset * 8)
                        / 2 + (j + stringoffset) * 8;

                if (bold)
                    re
                            .DrawChar(x, y,
                                    credits[i].charAt(j + stringoffset) + 128);
                else
                    re.DrawChar(x, y, credits[i].charAt(j + stringoffset));
            }
        }

        if (y < 0)
            credits_start_time = cls.realtime;
    }

    public static String Credits_Key(int key) {
        switch (key) {
        case K_ESCAPE:
            if (creditsBuffer != null)
                //FS.FreeFile(creditsBuffer);
                ;
            PopMenu();
            break;
        }

        return menu_out_sound;

    }

    static xcommand_t Menu_Credits = new xcommand_t() {
        public void execute() {
            Menu_Credits_f();
        }
    };

    static void Menu_Credits_f() {
        int n;
        int isdeveloper = 0;

        byte b[] = FS.LoadFile("credits");

        if (b != null) {
            creditsBuffer = new String(b);
            String line[] = creditsBuffer.split("\r\n");

            for (n = 0; n < line.length; n++) {
                creditsIndex[n] = line[n];
            }

            creditsIndex[n] = null;
            credits = creditsIndex;
        } else {
            isdeveloper = FS.Developer_searchpath(1);

            if (isdeveloper == 1) // xatrix
                credits = xatcredits;
            else if (isdeveloper == 2) // ROGUE
                credits = roguecredits;
            else {
                credits = idcredits;
            }

        }

        credits_start_time = cls.realtime;
        PushMenu(new xcommand_t() {
            public void execute() {
                Credits_MenuDraw();
            }
        }, new keyfunc_t() {
            public String execute(int key) {
                return Credits_Key(key);
            }
        });
    }

    /*
     * =============================================================================
     * 
     * GAME MENU
     * 
     * =============================================================================
     */

    static int m_game_cursor;

    static menuframework_s s_game_menu = new menuframework_s();

    static menuaction_s s_easy_game_action = new menuaction_s();

    static menuaction_s s_medium_game_action = new menuaction_s();

    static menuaction_s s_hard_game_action = new menuaction_s();

    static menuaction_s s_load_game_action = new menuaction_s();

    static menuaction_s s_save_game_action = new menuaction_s();

    static menuaction_s s_credits_action = new menuaction_s();

    static menuseparator_s s_blankline = new menuseparator_s();

    static void StartGame() {
        // disable updates and start the cinematic going
        cl.servercount = -1;
        ForceMenuOff();
        Cvar.SetValue("deathmatch", 0);
        Cvar.SetValue("coop", 0);

        Cvar.SetValue("gamerules", 0); //PGM

        Cbuf.AddText("loading ; killserver ; wait ; newgame\n");
        cls.key_dest = key_game;
    }

    static void EasyGameFunc(Object data) {
        Cvar.ForceSet("skill", "0");
        StartGame();
    }

    static void MediumGameFunc(Object data) {
        Cvar.ForceSet("skill", "1");
        StartGame();
    }

    static void HardGameFunc(Object data) {
        Cvar.ForceSet("skill", "2");
        StartGame();
    }

    static void LoadGameFunc(Object unused) {
        Menu_LoadGame_f();
    }

    static void SaveGameFunc(Object unused) {
        Menu_SaveGame_f();
    }

    static void CreditsFunc(Object unused) {
        Menu_Credits_f();
    }

    static String difficulty_names[] = { "easy", "medium",
            "fuckin shitty hard", null };

    static void Game_MenuInit() {

        s_game_menu.x = (int) (viddef.width * 0.50);
        s_game_menu.nitems = 0;

        s_easy_game_action.type = MTYPE_ACTION;
        s_easy_game_action.flags = QMF_LEFT_JUSTIFY;
        s_easy_game_action.x = 0;
        s_easy_game_action.y = 0;
        s_easy_game_action.name = "easy";
        s_easy_game_action.callback = new mcallback() {
            public void execute(Object o) {
                EasyGameFunc(o);
            }
        };

        s_medium_game_action.type = MTYPE_ACTION;
        s_medium_game_action.flags = QMF_LEFT_JUSTIFY;
        s_medium_game_action.x = 0;
        s_medium_game_action.y = 10;
        s_medium_game_action.name = "medium";
        s_medium_game_action.callback = new mcallback() {
            public void execute(Object o) {
                MediumGameFunc(o);
            }
        };

        s_hard_game_action.type = MTYPE_ACTION;
        s_hard_game_action.flags = QMF_LEFT_JUSTIFY;
        s_hard_game_action.x = 0;
        s_hard_game_action.y = 20;
        s_hard_game_action.name = "hard";
        s_hard_game_action.callback = new mcallback() {
            public void execute(Object o) {
                HardGameFunc(o);
            }
        };

        s_blankline.type = MTYPE_SEPARATOR;

        s_load_game_action.type = MTYPE_ACTION;
        s_load_game_action.flags = QMF_LEFT_JUSTIFY;
        s_load_game_action.x = 0;
        s_load_game_action.y = 40;
        s_load_game_action.name = "load game";
        s_load_game_action.callback = new mcallback() {
            public void execute(Object o) {
                LoadGameFunc(o);
            }
        };

        s_save_game_action.type = MTYPE_ACTION;
        s_save_game_action.flags = QMF_LEFT_JUSTIFY;
        s_save_game_action.x = 0;
        s_save_game_action.y = 50;
        s_save_game_action.name = "save game";
        s_save_game_action.callback = new mcallback() {
            public void execute(Object o) {
                SaveGameFunc(o);
            }
        };

        s_credits_action.type = MTYPE_ACTION;
        s_credits_action.flags = QMF_LEFT_JUSTIFY;
        s_credits_action.x = 0;
        s_credits_action.y = 60;
        s_credits_action.name = "credits";
        s_credits_action.callback = new mcallback() {
            public void execute(Object o) {
                CreditsFunc(o);
            }
        };

        Menu_AddItem(s_game_menu, s_easy_game_action);
        Menu_AddItem(s_game_menu, s_medium_game_action);
        Menu_AddItem(s_game_menu, s_hard_game_action);
        Menu_AddItem(s_game_menu, s_blankline);
        Menu_AddItem(s_game_menu, s_load_game_action);
        Menu_AddItem(s_game_menu, s_save_game_action);
        Menu_AddItem(s_game_menu, s_blankline);
        Menu_AddItem(s_game_menu, s_credits_action);

        Menu_Center(s_game_menu);
    }

    static void Game_MenuDraw() {
        Banner("m_banner_game");
        Menu_AdjustCursor(s_game_menu, 1);
        Menu_Draw(s_game_menu);
    }

    static String Game_MenuKey(int key) {
        return Default_MenuKey(s_game_menu, key);
    }

    static xcommand_t Menu_Game = new xcommand_t() {
        public void execute() {
            Menu_Game_f();
        }
    };

    static void Menu_Game_f() {
        Game_MenuInit();
        PushMenu(new xcommand_t() {
            public void execute() {
                Game_MenuDraw();
            }
        }, new keyfunc_t() {
            public String execute(int key) {
                return Game_MenuKey(key);
            }
        });
        m_game_cursor = 1;
    }

    /*
     * =============================================================================
     * 
     * LOADGAME MENU
     * 
     * =============================================================================
     */

    public final static int MAX_SAVEGAMES = 15;

    static menuframework_s s_savegame_menu = new menuframework_s();

    static menuframework_s s_loadgame_menu = new menuframework_s();

    static menuaction_s s_loadgame_actions[] = new menuaction_s[MAX_SAVEGAMES];

    static {
        for (int n = 0; n < MAX_SAVEGAMES; n++)
            s_loadgame_actions[n] = new menuaction_s();
    }

    //String m_savestrings[] = new String [MAX_SAVEGAMES][32];
    static String m_savestrings[] = new String[MAX_SAVEGAMES];

    static {
        for (int n = 0; n < MAX_SAVEGAMES; n++)
            m_savestrings[n] = "";
    }

    static boolean m_savevalid[] = new boolean[MAX_SAVEGAMES];

    /** Search the save dir for saved games and their names. */
    static void Create_Savestrings() {
        int i;
        QuakeFile f;
        String name;

        for (i = 0; i < MAX_SAVEGAMES; i++) {

            m_savestrings[i] = "<EMPTY>";
            name = FS.Gamedir() + "/save/save" + i + "/server.ssv";

            try {
                f = new QuakeFile(name, "r");
                if (f == null) {
                    m_savestrings[i] = "<EMPTY>";
                    m_savevalid[i] = false;
                } else {
                    String str = f.readString();
                    if (str != null)
                        m_savestrings[i] = str;
                    f.close();
                    m_savevalid[i] = true;
                }
            } catch (Exception e) {
                m_savestrings[i] = "<EMPTY>";
                m_savevalid[i] = false;
            }
            ;
        }
    }

    static void LoadGameCallback(Object self) {
        menuaction_s a = (menuaction_s) self;

        if (m_savevalid[a.localdata[0]])
            Cbuf.AddText("load save" + a.localdata[0] + "\n");
        ForceMenuOff();
    }

    static void LoadGame_MenuInit() {
        int i;

        s_loadgame_menu.x = viddef.width / 2 - 120;
        s_loadgame_menu.y = viddef.height / 2 - 58;
        s_loadgame_menu.nitems = 0;

        Create_Savestrings();

        for (i = 0; i < MAX_SAVEGAMES; i++) {
            s_loadgame_actions[i].name = m_savestrings[i];
            s_loadgame_actions[i].flags = QMF_LEFT_JUSTIFY;
            s_loadgame_actions[i].localdata[0] = i;
            s_loadgame_actions[i].callback = new mcallback() {
                public void execute(Object o) {
                    LoadGameCallback(o);
                }
            };

            s_loadgame_actions[i].x = 0;
            s_loadgame_actions[i].y = (i) * 10;
            if (i > 0) // separate from autosave
                s_loadgame_actions[i].y += 10;

            s_loadgame_actions[i].type = MTYPE_ACTION;

            Menu_AddItem(s_loadgame_menu, s_loadgame_actions[i]);
        }
    }

    static void LoadGame_MenuDraw() {
        Banner("m_banner_load_game");
        //		Menu_AdjustCursor( &s_loadgame_menu, 1 );
        Menu_Draw(s_loadgame_menu);
    }

    static String LoadGame_MenuKey(int key) {
        if (key == K_ESCAPE || key == K_ENTER) {
            s_savegame_menu.cursor = s_loadgame_menu.cursor - 1;
            if (s_savegame_menu.cursor < 0)
                s_savegame_menu.cursor = 0;
        }
        return Default_MenuKey(s_loadgame_menu, key);
    }

    static xcommand_t Menu_LoadGame = new xcommand_t() {
        public void execute() {
            Menu_LoadGame_f();
        }
    };

    static void Menu_LoadGame_f() {
        LoadGame_MenuInit();
        PushMenu(new xcommand_t() {
            public void execute() {
                LoadGame_MenuDraw();
            }
        }, new keyfunc_t() {
            public String execute(int key) {
                return LoadGame_MenuKey(key);
            }
        });
    }

    /*
     * =============================================================================
     * 
     * SAVEGAME MENU
     * 
     * =============================================================================
     */
    //static menuframework_s s_savegame_menu;
    static menuaction_s s_savegame_actions[] = new menuaction_s[MAX_SAVEGAMES];

    static {
        for (int n = 0; n < MAX_SAVEGAMES; n++)
            s_savegame_actions[n] = new menuaction_s();

    }

    static void SaveGameCallback(Object self) {
        menuaction_s a = (menuaction_s) self;

        Cbuf.AddText("save save" + a.localdata[0] + "\n");
        ForceMenuOff();
    }

    static void SaveGame_MenuDraw() {
        Banner("m_banner_save_game");
        Menu_AdjustCursor(s_savegame_menu, 1);
        Menu_Draw(s_savegame_menu);
    }

    static void SaveGame_MenuInit() {
        int i;

        s_savegame_menu.x = viddef.width / 2 - 120;
        s_savegame_menu.y = viddef.height / 2 - 58;
        s_savegame_menu.nitems = 0;

        Create_Savestrings();

        // don't include the autosave slot
        for (i = 0; i < MAX_SAVEGAMES - 1; i++) {
            s_savegame_actions[i].name = m_savestrings[i + 1];
            s_savegame_actions[i].localdata[0] = i + 1;
            s_savegame_actions[i].flags = QMF_LEFT_JUSTIFY;
            s_savegame_actions[i].callback = new mcallback() {
                public void execute(Object o) {
                    SaveGameCallback(o);
                }
            };

            s_savegame_actions[i].x = 0;
            s_savegame_actions[i].y = (i) * 10;

            s_savegame_actions[i].type = MTYPE_ACTION;

            Menu_AddItem(s_savegame_menu, s_savegame_actions[i]);
        }
    }

    static String SaveGame_MenuKey(int key) {
        if (key == K_ENTER || key == K_ESCAPE) {
            s_loadgame_menu.cursor = s_savegame_menu.cursor - 1;
            if (s_loadgame_menu.cursor < 0)
                s_loadgame_menu.cursor = 0;
        }
        return Default_MenuKey(s_savegame_menu, key);
    }

    static xcommand_t Menu_SaveGame = new xcommand_t() {
        public void execute() {
            Menu_SaveGame_f();
        }
    };

    static void Menu_SaveGame_f() {
        if (0 == Globals.server_state)
            return; // not playing a game

        SaveGame_MenuInit();
        PushMenu(new xcommand_t() {
            public void execute() {
                SaveGame_MenuDraw();
            }
        }, new keyfunc_t() {
            public String execute(int key) {
                return SaveGame_MenuKey(key);
            }
        });
        Create_Savestrings();
    }

    /*
     * =============================================================================
     * 
     * JOIN SERVER MENU
     * 
     * =============================================================================
     */

    static menuframework_s s_joinserver_menu = new menuframework_s();

    static menuseparator_s s_joinserver_server_title = new menuseparator_s();

    static menuaction_s s_joinserver_search_action = new menuaction_s();

    static menuaction_s s_joinserver_address_book_action = new menuaction_s();

    static netadr_t local_server_netadr[] = new netadr_t[MAX_LOCAL_SERVERS];

    static String local_server_names[] = new String[MAX_LOCAL_SERVERS]; //[80];

    static menuaction_s s_joinserver_server_actions[] = new menuaction_s[MAX_LOCAL_SERVERS];

    //	   user readable information
    //	   network address
    static {
        for (int n = 0; n < MAX_LOCAL_SERVERS; n++) {
            local_server_netadr[n] = new netadr_t();
            local_server_names[n] = "";
            s_joinserver_server_actions[n] = new menuaction_s();
            s_joinserver_server_actions[n].n = n;
        }
    }

    static int m_num_servers;

    static void AddToServerList(netadr_t adr, String info) {
        int i;

        if (m_num_servers == MAX_LOCAL_SERVERS)
            return;

        String x = info.trim();

        // ignore if duplicated

        for (i = 0; i < m_num_servers; i++)
            if (x.equals(local_server_names[i]))
                return;

        local_server_netadr[m_num_servers].set(adr);
        local_server_names[m_num_servers] = x;
        s_joinserver_server_actions[m_num_servers].name = x;
        m_num_servers++;
    }

    static void JoinServerFunc(Object self) {
        String buffer;
        int index;

        index = ((menucommon_s) self).n;

        if (Lib.Q_stricmp(local_server_names[index], NO_SERVER_STRING) == 0)
            return;

        if (index >= m_num_servers)
            return;

        buffer = "connect " + NET.AdrToString(local_server_netadr[index])
                + "\n";
        Cbuf.AddText(buffer);
        ForceMenuOff();
    }

    static void AddressBookFunc(Object self) {
        Menu_AddressBook_f();
    }

    static void NullCursorDraw(Object self) {
    }

    static void SearchLocalGames() {
        int i;

        m_num_servers = 0;
        for (i = 0; i < MAX_LOCAL_SERVERS; i++)
            local_server_names[i] = NO_SERVER_STRING;

        DrawTextBox(8, 120 - 48, 36, 3);
        Print(16 + 16, 120 - 48 + 8, "Searching for local servers, this");
        Print(16 + 16, 120 - 48 + 16, "could take up to a minute, so");
        Print(16 + 16, 120 - 48 + 24, "please be patient.");

        // the text box won't show up unless we do a buffer swap
        re.EndFrame();

        // send out info packets
        CL.PingServers_f.execute();
    }

    static void SearchLocalGamesFunc(Object self) {
        SearchLocalGames();
    }

    static void JoinServer_MenuInit() {
        int i;

        s_joinserver_menu.x = (int) (viddef.width * 0.50 - 120);
        s_joinserver_menu.nitems = 0;

        s_joinserver_address_book_action.type = MTYPE_ACTION;
        s_joinserver_address_book_action.name = "address book";
        s_joinserver_address_book_action.flags = QMF_LEFT_JUSTIFY;
        s_joinserver_address_book_action.x = 0;
        s_joinserver_address_book_action.y = 0;
        s_joinserver_address_book_action.callback = new mcallback() {
            public void execute(Object o) {
                AddressBookFunc(o);
            }
        };

        s_joinserver_search_action.type = MTYPE_ACTION;
        s_joinserver_search_action.name = "refresh server list";
        s_joinserver_search_action.flags = QMF_LEFT_JUSTIFY;
        s_joinserver_search_action.x = 0;
        s_joinserver_search_action.y = 10;
        s_joinserver_search_action.callback = new mcallback() {
            public void execute(Object o) {
                SearchLocalGamesFunc(o);
            }
        };
        s_joinserver_search_action.statusbar = "search for servers";

        s_joinserver_server_title.type = MTYPE_SEPARATOR;
        s_joinserver_server_title.name = "connect to...";
        s_joinserver_server_title.x = 80;
        s_joinserver_server_title.y = 30;

        for (i = 0; i < MAX_LOCAL_SERVERS; i++) {
            s_joinserver_server_actions[i].type = MTYPE_ACTION;
            local_server_names[i] = NO_SERVER_STRING;
            s_joinserver_server_actions[i].name = local_server_names[i];
            s_joinserver_server_actions[i].flags = QMF_LEFT_JUSTIFY;
            s_joinserver_server_actions[i].x = 0;
            s_joinserver_server_actions[i].y = 40 + i * 10;
            s_joinserver_server_actions[i].callback = new mcallback() {
                public void execute(Object o) {
                    JoinServerFunc(o);
                }
            };
            s_joinserver_server_actions[i].statusbar = "press ENTER to connect";
        }

        Menu_AddItem(s_joinserver_menu, s_joinserver_address_book_action);
        Menu_AddItem(s_joinserver_menu, s_joinserver_server_title);
        Menu_AddItem(s_joinserver_menu, s_joinserver_search_action);

        for (i = 0; i < 8; i++)
            Menu_AddItem(s_joinserver_menu, s_joinserver_server_actions[i]);

        Menu_Center(s_joinserver_menu);

        SearchLocalGames();
    }

    static void JoinServer_MenuDraw() {
        Banner("m_banner_join_server");
        Menu_Draw(s_joinserver_menu);
    }

    static String JoinServer_MenuKey(int key) {
        return Default_MenuKey(s_joinserver_menu, key);
    }

    static xcommand_t Menu_JoinServer = new xcommand_t() {
        public void execute() {
            Menu_JoinServer_f();
        }
    };

    static void Menu_JoinServer_f() {
        JoinServer_MenuInit();
        PushMenu(new xcommand_t() {
            public void execute() {
                JoinServer_MenuDraw();
            }
        }, new keyfunc_t() {
            public String execute(int key) {
                return JoinServer_MenuKey(key);
            }
        });
    }

    /*
     * =============================================================================
     * 
     * START SERVER MENU
     * 
     * =============================================================================
     */
    static menuframework_s s_startserver_menu = new menuframework_s();

    static String mapnames[];

    static int nummaps;

    static menuaction_s s_startserver_start_action = new menuaction_s();

    static menuaction_s s_startserver_dmoptions_action = new menuaction_s();

    static menufield_s s_timelimit_field = new menufield_s();

    static menufield_s s_fraglimit_field = new menufield_s();

    static menufield_s s_maxclients_field = new menufield_s();

    static menufield_s s_hostname_field = new menufield_s();

    static menulist_s s_startmap_list = new menulist_s();

    static menulist_s s_rules_box = new menulist_s();

    static void DMOptionsFunc(Object self) {
        if (s_rules_box.curvalue == 1)
            return;
        Menu_DMOptions_f();
    }

    static void RulesChangeFunc(Object self) {
        // DM
        if (s_rules_box.curvalue == 0) {
            s_maxclients_field.statusbar = null;
            s_startserver_dmoptions_action.statusbar = null;
        } else if (s_rules_box.curvalue == 1)
        // coop // PGM
        {
            s_maxclients_field.statusbar = "4 maximum for cooperative";
            if (Lib.atoi(s_maxclients_field.buffer.toString()) > 4)
                s_maxclients_field.buffer = new StringBuffer("4");
            s_startserver_dmoptions_action.statusbar = "N/A for cooperative";
        }
        //	  =====
        //	  PGM
        // ROGUE GAMES
        else if (FS.Developer_searchpath(2) == 2) {
            if (s_rules_box.curvalue == 2) // tag
            {
                s_maxclients_field.statusbar = null;
                s_startserver_dmoptions_action.statusbar = null;
            }
            /*
             * else if(s_rules_box.curvalue == 3) // deathball {
             * s_maxclients_field.statusbar = null;
             * s_startserver_dmoptions_action.statusbar = null; }
             */
        }
        //	  PGM
        //	  =====
    }

    static void StartServerActionFunc(Object self) {
        //char startmap[1024];
        String startmap;
        int timelimit;
        int fraglimit;
        int maxclients;
        String spot;

        //strcpy(startmap, strchr(mapnames[s_startmap_list.curvalue], '\n') +
        // 1);
        String x = mapnames[s_startmap_list.curvalue];

        int pos = x.indexOf('\n');
        if (pos == -1)
            startmap = x;
        else
            startmap = x.substring(pos + 1, x.length());

        maxclients = Lib.atoi(s_maxclients_field.buffer.toString());
        timelimit = Lib.atoi(s_timelimit_field.buffer.toString());
        fraglimit = Lib.atoi(s_fraglimit_field.buffer.toString());

        Cvar.SetValue("maxclients", ClampCvar(0, maxclients, maxclients));
        Cvar.SetValue("timelimit", ClampCvar(0, timelimit, timelimit));
        Cvar.SetValue("fraglimit", ClampCvar(0, fraglimit, fraglimit));
        Cvar.Set("hostname", s_hostname_field.buffer.toString());
        //		Cvar.SetValue ("deathmatch", !s_rules_box.curvalue );
        //		Cvar.SetValue ("coop", s_rules_box.curvalue );

        //	  PGM
        if ((s_rules_box.curvalue < 2) || (FS.Developer_searchpath(2) != 2)) {
            Cvar.SetValue("deathmatch", 1 - (int) (s_rules_box.curvalue));
            Cvar.SetValue("coop", s_rules_box.curvalue);
            Cvar.SetValue("gamerules", 0);
        } else {
            Cvar.SetValue("deathmatch", 1);
            // deathmatch is always true for rogue games, right?
            Cvar.SetValue("coop", 0);
            // FIXME - this might need to depend on which game we're running
            Cvar.SetValue("gamerules", s_rules_box.curvalue);
        }
        //	  PGM

        spot = null;
        if (s_rules_box.curvalue == 1) // PGM
        {
            if (Lib.Q_stricmp(startmap, "bunk1") == 0)
                spot = "start";
            else if (Lib.Q_stricmp(startmap, "mintro") == 0)
                spot = "start";
            else if (Lib.Q_stricmp(startmap, "fact1") == 0)
                spot = "start";
            else if (Lib.Q_stricmp(startmap, "power1") == 0)
                spot = "pstart";
            else if (Lib.Q_stricmp(startmap, "biggun") == 0)
                spot = "bstart";
            else if (Lib.Q_stricmp(startmap, "hangar1") == 0)
                spot = "unitstart";
            else if (Lib.Q_stricmp(startmap, "city1") == 0)
                spot = "unitstart";
            else if (Lib.Q_stricmp(startmap, "boss1") == 0)
                spot = "bosstart";
        }

        if (spot != null) {
            if (Globals.server_state != 0)
                Cbuf.AddText("disconnect\n");
            Cbuf.AddText("gamemap \"*" + startmap + "$" + spot + "\"\n");
        } else {
            Cbuf.AddText("map " + startmap + "\n");
        }

        ForceMenuOff();
    }

    static String dm_coop_names[] = { "deathmatch", "cooperative", null };

    static String dm_coop_names_rogue[] = { "deathmatch", "cooperative", "tag",
    //			"deathball",
            null };

    static void StartServer_MenuInit() {

        //	  =======
        //	  PGM
        //	  =======

        byte[] buffer = null;
        String mapsname;
        String s;
        int i;
        RandomAccessFile fp;

        /*
         * * load the list of map names
         */
        mapsname = FS.Gamedir() + "/maps.lst";

        if ((fp = Lib.fopen(mapsname, "r")) == null) {
            buffer = FS.LoadFile("maps.lst");
            if (buffer == null)
                //if ((length = FS_LoadFile("maps.lst", (Object *) & buffer))
                // == -1)
                Com.Error(ERR_DROP, "couldn't find maps.lst\n");
        } else {
            try {
                int len = (int) fp.length();
                buffer = new byte[len];
                fp.readFully(buffer);
            } catch (Exception e) {
                Com.Error(ERR_DROP, "couldn't load maps.lst\n");
            }
        }

        s = new String(buffer);
        String lines[] = s.split("\r\n");

        nummaps = lines.length;

        if (nummaps == 0)
            Com.Error(ERR_DROP, "no maps in maps.lst\n");

        mapnames = new String[nummaps + 1];

        for (i = 0; i < nummaps; i++) {
            String shortname, longname, scratch;

            Com.ParseHelp ph = new Com.ParseHelp(lines[i]);

            shortname = Com.Parse(ph).toUpperCase();
            longname = Com.Parse(ph);
            scratch = longname + "\n" + shortname;
            mapnames[i] = scratch;
        }
        mapnames[nummaps] = null;

        if (fp != null) {
            Lib.fclose(fp);
            fp = null;

        } else {
            FS.FreeFile(buffer);
        }

        /*
         * * initialize the menu stuff
         */
        s_startserver_menu.x = (int) (viddef.width * 0.50);
        s_startserver_menu.nitems = 0;

        s_startmap_list.type = MTYPE_SPINCONTROL;
        s_startmap_list.x = 0;
        s_startmap_list.y = 0;
        s_startmap_list.name = "initial map";
        s_startmap_list.itemnames = mapnames;

        s_rules_box.type = MTYPE_SPINCONTROL;
        s_rules_box.x = 0;
        s_rules_box.y = 20;
        s_rules_box.name = "rules";

        //	  PGM - rogue games only available with rogue DLL.
        if (FS.Developer_searchpath(2) == 2)
            s_rules_box.itemnames = dm_coop_names_rogue;
        else
            s_rules_box.itemnames = dm_coop_names;
        //	  PGM

        if (Cvar.VariableValue("coop") != 0)
            s_rules_box.curvalue = 1;
        else
            s_rules_box.curvalue = 0;
        s_rules_box.callback = new mcallback() {
            public void execute(Object o) {
                RulesChangeFunc(o);
            }
        };

        s_timelimit_field.type = MTYPE_FIELD;
        s_timelimit_field.name = "time limit";
        s_timelimit_field.flags = QMF_NUMBERSONLY;
        s_timelimit_field.x = 0;
        s_timelimit_field.y = 36;
        s_timelimit_field.statusbar = "0 = no limit";
        s_timelimit_field.length = 3;
        s_timelimit_field.visible_length = 3;
        s_timelimit_field.buffer = new StringBuffer(Cvar
                .VariableString("timelimit"));

        s_fraglimit_field.type = MTYPE_FIELD;
        s_fraglimit_field.name = "frag limit";
        s_fraglimit_field.flags = QMF_NUMBERSONLY;
        s_fraglimit_field.x = 0;
        s_fraglimit_field.y = 54;
        s_fraglimit_field.statusbar = "0 = no limit";
        s_fraglimit_field.length = 3;
        s_fraglimit_field.visible_length = 3;
        s_fraglimit_field.buffer = new StringBuffer(Cvar
                .VariableString("fraglimit"));

        /*
         * * maxclients determines the maximum number of players that can join *
         * the game. If maxclients is only "1" then we should default the menu *
         * option to 8 players, otherwise use whatever its current value is. *
         * Clamping will be done when the server is actually started.
         */
        s_maxclients_field.type = MTYPE_FIELD;
        s_maxclients_field.name = "max players";
        s_maxclients_field.flags = QMF_NUMBERSONLY;
        s_maxclients_field.x = 0;
        s_maxclients_field.y = 72;
        s_maxclients_field.statusbar = null;
        s_maxclients_field.length = 3;
        s_maxclients_field.visible_length = 3;
        if (Cvar.VariableValue("maxclients") == 1)
            s_maxclients_field.buffer = new StringBuffer("8");
        else
            s_maxclients_field.buffer = new StringBuffer(Cvar
                    .VariableString("maxclients"));

        s_hostname_field.type = MTYPE_FIELD;
        s_hostname_field.name = "hostname";
        s_hostname_field.flags = 0;
        s_hostname_field.x = 0;
        s_hostname_field.y = 90;
        s_hostname_field.statusbar = null;
        s_hostname_field.length = 12;
        s_hostname_field.visible_length = 12;
        s_hostname_field.buffer = new StringBuffer(Cvar
                .VariableString("hostname"));
        s_hostname_field.cursor = s_hostname_field.buffer.length();

        s_startserver_dmoptions_action.type = MTYPE_ACTION;
        s_startserver_dmoptions_action.name = " deathmatch flags";
        s_startserver_dmoptions_action.flags = QMF_LEFT_JUSTIFY;
        s_startserver_dmoptions_action.x = 24;
        s_startserver_dmoptions_action.y = 108;
        s_startserver_dmoptions_action.statusbar = null;
        s_startserver_dmoptions_action.callback = new mcallback() {
            public void execute(Object o) {
                DMOptionsFunc(o);
            }
        };

        s_startserver_start_action.type = MTYPE_ACTION;
        s_startserver_start_action.name = " begin";
        s_startserver_start_action.flags = QMF_LEFT_JUSTIFY;
        s_startserver_start_action.x = 24;
        s_startserver_start_action.y = 128;
        s_startserver_start_action.callback = new mcallback() {
            public void execute(Object o) {
                StartServerActionFunc(o);
            }
        };

        Menu_AddItem(s_startserver_menu, s_startmap_list);
        Menu_AddItem(s_startserver_menu, s_rules_box);
        Menu_AddItem(s_startserver_menu, s_timelimit_field);
        Menu_AddItem(s_startserver_menu, s_fraglimit_field);
        Menu_AddItem(s_startserver_menu, s_maxclients_field);
        Menu_AddItem(s_startserver_menu, s_hostname_field);
        Menu_AddItem(s_startserver_menu, s_startserver_dmoptions_action);
        Menu_AddItem(s_startserver_menu, s_startserver_start_action);

        Menu_Center(s_startserver_menu);

        // call this now to set proper inital state
        RulesChangeFunc(null);
    }

    static void StartServer_MenuDraw() {
        Menu_Draw(s_startserver_menu);
    }

    static String StartServer_MenuKey(int key) {
        if (key == K_ESCAPE) {
            if (mapnames != null) {
                int i;

                for (i = 0; i < nummaps; i++)
                    mapnames[i] = null;

            }
            mapnames = null;
            nummaps = 0;
        }

        return Default_MenuKey(s_startserver_menu, key);
    }

    static xcommand_t Menu_StartServer = new xcommand_t() {
        public void execute() {
            Menu_StartServer_f();
        }
    };

    static void Menu_StartServer_f() {
        StartServer_MenuInit();
        PushMenu(new xcommand_t() {
            public void execute() {
                StartServer_MenuDraw();
            }
        }, new keyfunc_t() {
            public String execute(int key) {
                return StartServer_MenuKey(key);
            }
        });
    }

    /*
     * =============================================================================
     * 
     * DMOPTIONS BOOK MENU
     * 
     * =============================================================================
     */
    static String dmoptions_statusbar; //[128];

    static menuframework_s s_dmoptions_menu = new menuframework_s();

    static menulist_s s_friendlyfire_box = new menulist_s();

    static menulist_s s_falls_box = new menulist_s();

    static menulist_s s_weapons_stay_box = new menulist_s();

    static menulist_s s_instant_powerups_box = new menulist_s();

    static menulist_s s_powerups_box = new menulist_s();

    static menulist_s s_health_box = new menulist_s();

    static menulist_s s_spawn_farthest_box = new menulist_s();

    static menulist_s s_teamplay_box = new menulist_s();

    static menulist_s s_samelevel_box = new menulist_s();

    static menulist_s s_force_respawn_box = new menulist_s();

    static menulist_s s_armor_box = new menulist_s();

    static menulist_s s_allow_exit_box = new menulist_s();

    static menulist_s s_infinite_ammo_box = new menulist_s();

    static menulist_s s_fixed_fov_box = new menulist_s();

    static menulist_s s_quad_drop_box = new menulist_s();

    //	  ROGUE
    static menulist_s s_no_mines_box = new menulist_s();

    static menulist_s s_no_nukes_box = new menulist_s();

    static menulist_s s_stack_double_box = new menulist_s();

    static menulist_s s_no_spheres_box = new menulist_s();

    //	  ROGUE

    static void setvalue(int flags) {
        Cvar.SetValue("dmflags", flags);
        dmoptions_statusbar = "dmflags = " + flags;
    }

    static void DMFlagCallback(Object self) {
        menulist_s f = (menulist_s) self;
        int flags;
        int bit = 0;

        flags = (int) Cvar.VariableValue("dmflags");

        if (f == s_friendlyfire_box) {
            if (f.curvalue != 0)
                flags &= ~DF_NO_FRIENDLY_FIRE;
            else
                flags |= DF_NO_FRIENDLY_FIRE;
            setvalue(flags);
            return;
        } else if (f == s_falls_box) {
            if (f.curvalue != 0)
                flags &= ~DF_NO_FALLING;
            else
                flags |= DF_NO_FALLING;
            setvalue(flags);
            return;
        } else if (f == s_weapons_stay_box) {
            bit = DF_WEAPONS_STAY;
        } else if (f == s_instant_powerups_box) {
            bit = DF_INSTANT_ITEMS;
        } else if (f == s_allow_exit_box) {
            bit = DF_ALLOW_EXIT;
        } else if (f == s_powerups_box) {
            if (f.curvalue != 0)
                flags &= ~DF_NO_ITEMS;
            else
                flags |= DF_NO_ITEMS;
            setvalue(flags);
            return;
        } else if (f == s_health_box) {
            if (f.curvalue != 0)
                flags &= ~DF_NO_HEALTH;
            else
                flags |= DF_NO_HEALTH;
            setvalue(flags);
            return;
        } else if (f == s_spawn_farthest_box) {
            bit = DF_SPAWN_FARTHEST;
        } else if (f == s_teamplay_box) {
            if (f.curvalue == 1) {
                flags |= DF_SKINTEAMS;
                flags &= ~DF_MODELTEAMS;
            } else if (f.curvalue == 2) {
                flags |= DF_MODELTEAMS;
                flags &= ~DF_SKINTEAMS;
            } else {
                flags &= ~(DF_MODELTEAMS | DF_SKINTEAMS);
            }

            setvalue(flags);
            return;
        } else if (f == s_samelevel_box) {
            bit = DF_SAME_LEVEL;
        } else if (f == s_force_respawn_box) {
            bit = DF_FORCE_RESPAWN;
        } else if (f == s_armor_box) {
            if (f.curvalue != 0)
                flags &= ~DF_NO_ARMOR;
            else
                flags |= DF_NO_ARMOR;
            setvalue(flags);
            return;
        } else if (f == s_infinite_ammo_box) {
            bit = DF_INFINITE_AMMO;
        } else if (f == s_fixed_fov_box) {
            bit = DF_FIXED_FOV;
        } else if (f == s_quad_drop_box) {
            bit = DF_QUAD_DROP;
        }

        //	  =======
        //	  ROGUE
        else if (FS.Developer_searchpath(2) == 2) {
            if (f == s_no_mines_box) {
                bit = DF_NO_MINES;
            } else if (f == s_no_nukes_box) {
                bit = DF_NO_NUKES;
            } else if (f == s_stack_double_box) {
                bit = DF_NO_STACK_DOUBLE;
            } else if (f == s_no_spheres_box) {
                bit = DF_NO_SPHERES;
            }
        }
        //	  ROGUE
        //	  =======

        if (f != null) {
            if (f.curvalue == 0)
                flags &= ~bit;
            else
                flags |= bit;
        }

        Cvar.SetValue("dmflags", flags);

        dmoptions_statusbar = "dmflags = " + flags;

    }

    //static String yes_no_names[] = { "no", "yes", 0 };
    static String teamplay_names[] = { "disabled", "by skin", "by model", null };

    static void DMOptions_MenuInit() {

        int dmflags = (int) Cvar.VariableValue("dmflags");
        int y = 0;

        s_dmoptions_menu.x = (int) (viddef.width * 0.50);
        s_dmoptions_menu.nitems = 0;

        s_falls_box.type = MTYPE_SPINCONTROL;
        s_falls_box.x = 0;
        s_falls_box.y = y;
        s_falls_box.name = "falling damage";
        s_falls_box.callback = new mcallback() {
            public void execute(Object o) {
                DMFlagCallback(o);
            }
        };
        s_falls_box.itemnames = yes_no_names;
        s_falls_box.curvalue = (dmflags & DF_NO_FALLING) == 0 ? 1 : 0;

        s_weapons_stay_box.type = MTYPE_SPINCONTROL;
        s_weapons_stay_box.x = 0;
        s_weapons_stay_box.y = y += 10;
        s_weapons_stay_box.name = "weapons stay";
        s_weapons_stay_box.callback = new mcallback() {
            public void execute(Object o) {
                DMFlagCallback(o);
            }
        };
        s_weapons_stay_box.itemnames = yes_no_names;
        s_weapons_stay_box.curvalue = (dmflags & DF_WEAPONS_STAY) != 0 ? 1 : 0;

        s_instant_powerups_box.type = MTYPE_SPINCONTROL;
        s_instant_powerups_box.x = 0;
        s_instant_powerups_box.y = y += 10;
        s_instant_powerups_box.name = "instant powerups";
        s_instant_powerups_box.callback = new mcallback() {
            public void execute(Object o) {
                DMFlagCallback(o);
            }
        };
        s_instant_powerups_box.itemnames = yes_no_names;
        s_instant_powerups_box.curvalue = (dmflags & DF_INSTANT_ITEMS) != 0 ? 1
                : 0;

        s_powerups_box.type = MTYPE_SPINCONTROL;
        s_powerups_box.x = 0;
        s_powerups_box.y = y += 10;
        s_powerups_box.name = "allow powerups";
        s_powerups_box.callback = new mcallback() {
            public void execute(Object o) {
                DMFlagCallback(o);
            }
        };
        s_powerups_box.itemnames = yes_no_names;
        s_powerups_box.curvalue = (dmflags & DF_NO_ITEMS) == 0 ? 1 : 0;

        s_health_box.type = MTYPE_SPINCONTROL;
        s_health_box.x = 0;
        s_health_box.y = y += 10;
        s_health_box.callback = new mcallback() {
            public void execute(Object o) {
                DMFlagCallback(o);
            }
        };
        s_health_box.name = "allow health";
        s_health_box.itemnames = yes_no_names;
        s_health_box.curvalue = (dmflags & DF_NO_HEALTH) == 0 ? 1 : 0;

        s_armor_box.type = MTYPE_SPINCONTROL;
        s_armor_box.x = 0;
        s_armor_box.y = y += 10;
        s_armor_box.name = "allow armor";
        s_armor_box.callback = new mcallback() {
            public void execute(Object o) {
                DMFlagCallback(o);
            }
        };
        s_armor_box.itemnames = yes_no_names;
        s_armor_box.curvalue = (dmflags & DF_NO_ARMOR) == 0 ? 1 : 0;

        s_spawn_farthest_box.type = MTYPE_SPINCONTROL;
        s_spawn_farthest_box.x = 0;
        s_spawn_farthest_box.y = y += 10;
        s_spawn_farthest_box.name = "spawn farthest";
        s_spawn_farthest_box.callback = new mcallback() {
            public void execute(Object o) {
                DMFlagCallback(o);
            }
        };
        s_spawn_farthest_box.itemnames = yes_no_names;
        s_spawn_farthest_box.curvalue = (dmflags & DF_SPAWN_FARTHEST) != 0 ? 1
                : 0;

        s_samelevel_box.type = MTYPE_SPINCONTROL;
        s_samelevel_box.x = 0;
        s_samelevel_box.y = y += 10;
        s_samelevel_box.name = "same map";
        s_samelevel_box.callback = new mcallback() {
            public void execute(Object o) {
                DMFlagCallback(o);
            }
        };
        s_samelevel_box.itemnames = yes_no_names;
        s_samelevel_box.curvalue = (dmflags & DF_SAME_LEVEL) != 0 ? 1 : 0;

        s_force_respawn_box.type = MTYPE_SPINCONTROL;
        s_force_respawn_box.x = 0;
        s_force_respawn_box.y = y += 10;
        s_force_respawn_box.name = "force respawn";
        s_force_respawn_box.callback = new mcallback() {
            public void execute(Object o) {
                DMFlagCallback(o);
            }
        };
        s_force_respawn_box.itemnames = yes_no_names;
        s_force_respawn_box.curvalue = (dmflags & DF_FORCE_RESPAWN) != 0 ? 1
                : 0;

        s_teamplay_box.type = MTYPE_SPINCONTROL;
        s_teamplay_box.x = 0;
        s_teamplay_box.y = y += 10;
        s_teamplay_box.name = "teamplay";
        s_teamplay_box.callback = new mcallback() {
            public void execute(Object o) {
                DMFlagCallback(o);
            }
        };
        s_teamplay_box.itemnames = teamplay_names;

        s_allow_exit_box.type = MTYPE_SPINCONTROL;
        s_allow_exit_box.x = 0;
        s_allow_exit_box.y = y += 10;
        s_allow_exit_box.name = "allow exit";
        s_allow_exit_box.callback = new mcallback() {
            public void execute(Object o) {
                DMFlagCallback(o);
            }
        };
        s_allow_exit_box.itemnames = yes_no_names;
        s_allow_exit_box.curvalue = (dmflags & DF_ALLOW_EXIT) != 0 ? 1 : 0;

        s_infinite_ammo_box.type = MTYPE_SPINCONTROL;
        s_infinite_ammo_box.x = 0;
        s_infinite_ammo_box.y = y += 10;
        s_infinite_ammo_box.name = "infinite ammo";
        s_infinite_ammo_box.callback = new mcallback() {
            public void execute(Object o) {
                DMFlagCallback(o);
            }
        };
        s_infinite_ammo_box.itemnames = yes_no_names;
        s_infinite_ammo_box.curvalue = (dmflags & DF_INFINITE_AMMO) != 0 ? 1
                : 0;

        s_fixed_fov_box.type = MTYPE_SPINCONTROL;
        s_fixed_fov_box.x = 0;
        s_fixed_fov_box.y = y += 10;
        s_fixed_fov_box.name = "fixed FOV";
        s_fixed_fov_box.callback = new mcallback() {
            public void execute(Object o) {
                DMFlagCallback(o);
            }
        };
        s_fixed_fov_box.itemnames = yes_no_names;
        s_fixed_fov_box.curvalue = (dmflags & DF_FIXED_FOV) != 0 ? 1 : 0;

        s_quad_drop_box.type = MTYPE_SPINCONTROL;
        s_quad_drop_box.x = 0;
        s_quad_drop_box.y = y += 10;
        s_quad_drop_box.name = "quad drop";
        s_quad_drop_box.callback = new mcallback() {
            public void execute(Object o) {
                DMFlagCallback(o);
            }
        };
        s_quad_drop_box.itemnames = yes_no_names;
        s_quad_drop_box.curvalue = (dmflags & DF_QUAD_DROP) != 0 ? 1 : 0;

        s_friendlyfire_box.type = MTYPE_SPINCONTROL;
        s_friendlyfire_box.x = 0;
        s_friendlyfire_box.y = y += 10;
        s_friendlyfire_box.name = "friendly fire";
        s_friendlyfire_box.callback = new mcallback() {
            public void execute(Object o) {
                DMFlagCallback(o);
            }
        };
        s_friendlyfire_box.itemnames = yes_no_names;
        s_friendlyfire_box.curvalue = (dmflags & DF_NO_FRIENDLY_FIRE) == 0 ? 1
                : 0;

        //	  ============
        //	  ROGUE
        if (FS.Developer_searchpath(2) == 2) {
            s_no_mines_box.type = MTYPE_SPINCONTROL;
            s_no_mines_box.x = 0;
            s_no_mines_box.y = y += 10;
            s_no_mines_box.name = "remove mines";
            s_no_mines_box.callback = new mcallback() {
                public void execute(Object o) {
                    DMFlagCallback(o);
                }
            };
            s_no_mines_box.itemnames = yes_no_names;
            s_no_mines_box.curvalue = (dmflags & DF_NO_MINES) != 0 ? 1 : 0;

            s_no_nukes_box.type = MTYPE_SPINCONTROL;
            s_no_nukes_box.x = 0;
            s_no_nukes_box.y = y += 10;
            s_no_nukes_box.name = "remove nukes";
            s_no_nukes_box.callback = new mcallback() {
                public void execute(Object o) {
                    DMFlagCallback(o);
                }
            };
            s_no_nukes_box.itemnames = yes_no_names;
            s_no_nukes_box.curvalue = (dmflags & DF_NO_NUKES) != 0 ? 1 : 0;

            s_stack_double_box.type = MTYPE_SPINCONTROL;
            s_stack_double_box.x = 0;
            s_stack_double_box.y = y += 10;
            s_stack_double_box.name = "2x/4x stacking off";
            s_stack_double_box.callback = new mcallback() {
                public void execute(Object o) {
                    DMFlagCallback(o);
                }
            };
            s_stack_double_box.itemnames = yes_no_names;
            s_stack_double_box.curvalue = (dmflags & DF_NO_STACK_DOUBLE);

            s_no_spheres_box.type = MTYPE_SPINCONTROL;
            s_no_spheres_box.x = 0;
            s_no_spheres_box.y = y += 10;
            s_no_spheres_box.name = "remove spheres";
            s_no_spheres_box.callback = new mcallback() {
                public void execute(Object o) {
                    DMFlagCallback(o);
                }
            };
            s_no_spheres_box.itemnames = yes_no_names;
            s_no_spheres_box.curvalue = (dmflags & DF_NO_SPHERES) != 0 ? 1 : 0;

        }
        //	  ROGUE
        //	  ============

        Menu_AddItem(s_dmoptions_menu, s_falls_box);
        Menu_AddItem(s_dmoptions_menu, s_weapons_stay_box);
        Menu_AddItem(s_dmoptions_menu, s_instant_powerups_box);
        Menu_AddItem(s_dmoptions_menu, s_powerups_box);
        Menu_AddItem(s_dmoptions_menu, s_health_box);
        Menu_AddItem(s_dmoptions_menu, s_armor_box);
        Menu_AddItem(s_dmoptions_menu, s_spawn_farthest_box);
        Menu_AddItem(s_dmoptions_menu, s_samelevel_box);
        Menu_AddItem(s_dmoptions_menu, s_force_respawn_box);
        Menu_AddItem(s_dmoptions_menu, s_teamplay_box);
        Menu_AddItem(s_dmoptions_menu, s_allow_exit_box);
        Menu_AddItem(s_dmoptions_menu, s_infinite_ammo_box);
        Menu_AddItem(s_dmoptions_menu, s_fixed_fov_box);
        Menu_AddItem(s_dmoptions_menu, s_quad_drop_box);
        Menu_AddItem(s_dmoptions_menu, s_friendlyfire_box);

        //	  =======
        //	  ROGUE
        if (FS.Developer_searchpath(2) == 2) {
            Menu_AddItem(s_dmoptions_menu, s_no_mines_box);
            Menu_AddItem(s_dmoptions_menu, s_no_nukes_box);
            Menu_AddItem(s_dmoptions_menu, s_stack_double_box);
            Menu_AddItem(s_dmoptions_menu, s_no_spheres_box);
        }
        //	  ROGUE
        //	  =======

        Menu_Center(s_dmoptions_menu);

        // set the original dmflags statusbar
        DMFlagCallback(null);
        Menu_SetStatusBar(s_dmoptions_menu, dmoptions_statusbar);
    }

    static void DMOptions_MenuDraw() {
        Menu_Draw(s_dmoptions_menu);
    }

    static String DMOptions_MenuKey(int key) {
        return Default_MenuKey(s_dmoptions_menu, key);
    }

    static xcommand_t Menu_DMOptions = new xcommand_t() {
        public void execute() {
            Menu_DMOptions_f();
        }
    };

    static void Menu_DMOptions_f() {
        DMOptions_MenuInit();
        PushMenu(new xcommand_t() {
            public void execute() {
                DMOptions_MenuDraw();
            }
        }, new keyfunc_t() {
            public String execute(int key) {
                return DMOptions_MenuKey(key);
            }
        });
    }

    /*
     * =============================================================================
     * 
     * DOWNLOADOPTIONS BOOK MENU
     * 
     * =============================================================================
     */
    static menuframework_s s_downloadoptions_menu = new menuframework_s();

    static menuseparator_s s_download_title = new menuseparator_s();

    static menulist_s s_allow_download_box = new menulist_s();

    static menulist_s s_allow_download_maps_box = new menulist_s();

    static menulist_s s_allow_download_models_box = new menulist_s();

    static menulist_s s_allow_download_players_box = new menulist_s();

    static menulist_s s_allow_download_sounds_box = new menulist_s();

    static void DownloadCallback(Object self) {
        menulist_s f = (menulist_s) self;

        if (f == s_allow_download_box) {
            Cvar.SetValue("allow_download", f.curvalue);
        }

        else if (f == s_allow_download_maps_box) {
            Cvar.SetValue("allow_download_maps", f.curvalue);
        }

        else if (f == s_allow_download_models_box) {
            Cvar.SetValue("allow_download_models", f.curvalue);
        }

        else if (f == s_allow_download_players_box) {
            Cvar.SetValue("allow_download_players", f.curvalue);
        }

        else if (f == s_allow_download_sounds_box) {
            Cvar.SetValue("allow_download_sounds", f.curvalue);
        }
    }

    static String yes_no_names[] = { "no", "yes", null };

    static void DownloadOptions_MenuInit() {

        int y = 0;

        s_downloadoptions_menu.x = (int) (viddef.width * 0.50);
        s_downloadoptions_menu.nitems = 0;

        s_download_title.type = MTYPE_SEPARATOR;
        s_download_title.name = "Download Options";
        s_download_title.x = 48;
        s_download_title.y = y;

        s_allow_download_box.type = MTYPE_SPINCONTROL;
        s_allow_download_box.x = 0;
        s_allow_download_box.y = y += 20;
        s_allow_download_box.name = "allow downloading";
        s_allow_download_box.callback = new mcallback() {
            public void execute(Object o) {
                DownloadCallback(o);
            }
        };
        s_allow_download_box.itemnames = yes_no_names;
        s_allow_download_box.curvalue = (Cvar.VariableValue("allow_download") != 0) ? 1
                : 0;

        s_allow_download_maps_box.type = MTYPE_SPINCONTROL;
        s_allow_download_maps_box.x = 0;
        s_allow_download_maps_box.y = y += 20;
        s_allow_download_maps_box.name = "maps";
        s_allow_download_maps_box.callback = new mcallback() {
            public void execute(Object o) {
                DownloadCallback(o);
            }
        };
        s_allow_download_maps_box.itemnames = yes_no_names;
        s_allow_download_maps_box.curvalue = (Cvar
                .VariableValue("allow_download_maps") != 0) ? 1 : 0;

        s_allow_download_players_box.type = MTYPE_SPINCONTROL;
        s_allow_download_players_box.x = 0;
        s_allow_download_players_box.y = y += 10;
        s_allow_download_players_box.name = "player models/skins";
        s_allow_download_players_box.callback = new mcallback() {
            public void execute(Object o) {
                DownloadCallback(o);
            }
        };
        s_allow_download_players_box.itemnames = yes_no_names;
        s_allow_download_players_box.curvalue = (Cvar
                .VariableValue("allow_download_players") != 0) ? 1 : 0;

        s_allow_download_models_box.type = MTYPE_SPINCONTROL;
        s_allow_download_models_box.x = 0;
        s_allow_download_models_box.y = y += 10;
        s_allow_download_models_box.name = "models";
        s_allow_download_models_box.callback = new mcallback() {
            public void execute(Object o) {
                DownloadCallback(o);
            }
        };
        s_allow_download_models_box.itemnames = yes_no_names;
        s_allow_download_models_box.curvalue = (Cvar
                .VariableValue("allow_download_models") != 0) ? 1 : 0;

        s_allow_download_sounds_box.type = MTYPE_SPINCONTROL;
        s_allow_download_sounds_box.x = 0;
        s_allow_download_sounds_box.y = y += 10;
        s_allow_download_sounds_box.name = "sounds";
        s_allow_download_sounds_box.callback = new mcallback() {
            public void execute(Object o) {
                DownloadCallback(o);
            }
        };
        s_allow_download_sounds_box.itemnames = yes_no_names;
        s_allow_download_sounds_box.curvalue = (Cvar
                .VariableValue("allow_download_sounds") != 0) ? 1 : 0;

        Menu_AddItem(s_downloadoptions_menu, s_download_title);
        Menu_AddItem(s_downloadoptions_menu, s_allow_download_box);
        Menu_AddItem(s_downloadoptions_menu, s_allow_download_maps_box);
        Menu_AddItem(s_downloadoptions_menu, s_allow_download_players_box);
        Menu_AddItem(s_downloadoptions_menu, s_allow_download_models_box);
        Menu_AddItem(s_downloadoptions_menu, s_allow_download_sounds_box);

        Menu_Center(s_downloadoptions_menu);

        // skip over title
        if (s_downloadoptions_menu.cursor == 0)
            s_downloadoptions_menu.cursor = 1;
    }

    static void DownloadOptions_MenuDraw() {
        Menu_Draw(s_downloadoptions_menu);
    }

    static String DownloadOptions_MenuKey(int key) {
        return Default_MenuKey(s_downloadoptions_menu, key);
    }

    static xcommand_t Menu_DownloadOptions = new xcommand_t() {
        public void execute() {
            Menu_DownloadOptions_f();
        }
    };

    static void Menu_DownloadOptions_f() {
        DownloadOptions_MenuInit();
        PushMenu(new xcommand_t() {
            public void execute() {
                DownloadOptions_MenuDraw();
            }
        }, new keyfunc_t() {
            public String execute(int key) {
                return DownloadOptions_MenuKey(key);
            }
        });
    }

    /*
     * =============================================================================
     * 
     * ADDRESS BOOK MENU
     * 
     * =============================================================================
     */

    static menuframework_s s_addressbook_menu = new menuframework_s();

    static menufield_s s_addressbook_fields[] = new menufield_s[NUM_ADDRESSBOOK_ENTRIES];
    static {
        for (int n = 0; n < NUM_ADDRESSBOOK_ENTRIES; n++)
            s_addressbook_fields[n] = new menufield_s();
    }

    static void AddressBook_MenuInit() {
        int i;

        s_addressbook_menu.x = viddef.width / 2 - 142;
        s_addressbook_menu.y = viddef.height / 2 - 58;
        s_addressbook_menu.nitems = 0;

        for (i = 0; i < NUM_ADDRESSBOOK_ENTRIES; i++) {
            cvar_t adr;
            //char buffer[20];
            String buffer;

            //Com_sprintf(buffer, sizeof(buffer), "adr%d", i);
            buffer = "adr" + i;

            adr = Cvar.Get(buffer, "", CVAR_ARCHIVE);

            s_addressbook_fields[i].type = MTYPE_FIELD;
            s_addressbook_fields[i].name = null;
            s_addressbook_fields[i].callback = null;
            s_addressbook_fields[i].x = 0;
            s_addressbook_fields[i].y = i * 18 + 0;
            s_addressbook_fields[i].localdata[0] = i;
            s_addressbook_fields[i].cursor = 0;
            s_addressbook_fields[i].length = 60;
            s_addressbook_fields[i].visible_length = 30;

            s_addressbook_fields[i].buffer = new StringBuffer(adr.string);

            Menu_AddItem(s_addressbook_menu, s_addressbook_fields[i]);
        }
    }

    static keyfunc_t AddressBook_MenuKey = new keyfunc_t() {
        public String execute(int key) {
            return AddressBook_MenuKey_f(key);
        }
    };

    static String AddressBook_MenuKey_f(int key) {
        if (key == K_ESCAPE) {
            int index;
            //char buffer[20];
            String buffer;

            for (index = 0; index < NUM_ADDRESSBOOK_ENTRIES; index++) {
                buffer = "adr" + index;
                //Com_sprintf(buffer, sizeof(buffer), "adr%d", index);
                Cvar.Set(buffer, s_addressbook_fields[index].buffer.toString());
            }
        }
        return Default_MenuKey(s_addressbook_menu, key);
    }

    static xcommand_t AddressBook_MenuDraw = new xcommand_t() {
        public void execute() {
            AddressBook_MenuDraw_f();
        }
    };

    static void AddressBook_MenuDraw_f() {
        Banner("m_banner_addressbook");
        Menu_Draw(s_addressbook_menu);
    }

    static xcommand_t Menu_AddressBook = new xcommand_t() {
        public void execute() {
            Menu_AddressBook_f();
        }
    };

    static void Menu_AddressBook_f() {
        AddressBook_MenuInit();
        PushMenu(new xcommand_t() {
            public void execute() {
                AddressBook_MenuDraw_f();
            }
        }, new keyfunc_t() {
            public String execute(int key) {
                return AddressBook_MenuKey_f(key);
            }
        });
    }

    /*
     * =============================================================================
     * 
     * PLAYER CONFIG MENU
     * 
     * =============================================================================
     */
    static menuframework_s s_player_config_menu = new menuframework_s();

    static menufield_s s_player_name_field = new menufield_s();

    static menulist_s s_player_model_box = new menulist_s();

    static menulist_s s_player_skin_box = new menulist_s();

    static menulist_s s_player_handedness_box = new menulist_s();

    static menulist_s s_player_rate_box = new menulist_s();

    static menuseparator_s s_player_skin_title = new menuseparator_s();

    static menuseparator_s s_player_model_title = new menuseparator_s();

    static menuseparator_s s_player_hand_title = new menuseparator_s();

    static menuseparator_s s_player_rate_title = new menuseparator_s();

    static menuaction_s s_player_download_action = new menuaction_s();

    static class playermodelinfo_s {
        int nskins;

        String skindisplaynames[];

        //char displayname[MAX_DISPLAYNAME];
        String displayname;

        //char directory[MAX_QPATH];
        String directory;
    };

    static playermodelinfo_s s_pmi[] = new playermodelinfo_s[MAX_PLAYERMODELS];

    static String s_pmnames[] = new String[MAX_PLAYERMODELS];

    static int s_numplayermodels;

    static int rate_tbl[] = { 2500, 3200, 5000, 10000, 25000, 0 };

    static String rate_names[] = { "28.8 Modem", "33.6 Modem", "Single ISDN",
            "Dual ISDN/Cable", "T1/LAN", "User defined", null };

    static void DownloadOptionsFunc(Object self) {
        Menu_DownloadOptions_f();
    }

    static void HandednessCallback(Object unused) {
        Cvar.SetValue("hand", s_player_handedness_box.curvalue);
    }

    static void RateCallback(Object unused) {
        if (s_player_rate_box.curvalue != rate_tbl.length - 1) //sizeof(rate_tbl)
                                                               // / sizeof(*
                                                               // rate_tbl) - 1)
            Cvar.SetValue("rate", rate_tbl[s_player_rate_box.curvalue]);
    }

    static void ModelCallback(Object unused) {
        s_player_skin_box.itemnames = s_pmi[s_player_model_box.curvalue].skindisplaynames;
        s_player_skin_box.curvalue = 0;
    }

    static boolean IconOfSkinExists(String skin, String pcxfiles[],
            int npcxfiles) {
        int i;
        //char scratch[1024];
        String scratch;

        //strcpy(scratch, skin);
        scratch = skin;
        int pos = scratch.lastIndexOf('.');
        if (pos != -1)
            scratch = scratch.substring(0, pos) + "_i.pcx";

        else
            scratch += "_i.pcx";

        for (i = 0; i < npcxfiles; i++) {
            if (Lib.strcmp(pcxfiles[i], scratch) == 0)
                return true;
        }

        return false;
    }

    static boolean PlayerConfig_ScanDirectories() {
        //char findname[1024];
        String findname;
        //char scratch[1024];
        String scratch;

        int ndirs = 0, npms = 0;
        int a, b, c;
        String dirnames[];

        String path = null;

        int i;

        //extern String * FS_ListFiles(String , int *, unsigned, unsigned);

        s_numplayermodels = 0;

        /*
         * * get a list of directories
         */
        do {
            path = FS.NextPath(path);
            findname = path + "/players/*.*";

            if ((dirnames = FS.ListFiles(findname, 0, SFF_SUBDIR)) != null) {
                ndirs = dirnames.length;
                break;
            }
        } while (path != null);

        if (dirnames == null)
            return false;

        /*
         * * go through the subdirectories
         */
        npms = ndirs;
        if (npms > MAX_PLAYERMODELS)
            npms = MAX_PLAYERMODELS;

        for (i = 0; i < npms; i++) {
            int k, s;
            //String a, b, c;
            String pcxnames[];
            String skinnames[];
            int npcxfiles;
            int nskins = 0;

            if (dirnames[i] == null)
                continue;

            // verify the existence of tris.md2
            scratch = dirnames[i];
            scratch += "/tris.md2";
            if (Sys.FindFirst(scratch, 0, SFF_SUBDIR | SFF_HIDDEN | SFF_SYSTEM) == null) {
                //free(dirnames[i]);
                dirnames[i] = null;
                Sys.FindClose();
                continue;
            }
            Sys.FindClose();

            // verify the existence of at least one pcx skin
            scratch = dirnames[i] + "/*.pcx";
            pcxnames = FS.ListFiles(scratch, 0, 0);
            npcxfiles = pcxnames.length;

            if (pcxnames == null) {

                dirnames[i] = null;
                continue;
            }

            // count valid skins, which consist of a skin with a matching "_i"
            // icon
            for (k = 0; k < npcxfiles - 1; k++) {
                if (!pcxnames[k].endsWith("_i.pcx")) {
                    //if (!strstr(pcxnames[k], "_i.pcx")) {
                    if (IconOfSkinExists(pcxnames[k], pcxnames, npcxfiles - 1)) {
                        nskins++;
                    }
                }
            }
            if (nskins == 0)
                continue;

            skinnames = new String[nskins + 1]; //malloc(sizeof(String) *
                                                // (nskins + 1));
            //memset(skinnames, 0, sizeof(String) * (nskins + 1));

            // copy the valid skins
            for (s = 0, k = 0; k < npcxfiles - 1; k++) {

                if (pcxnames[k].indexOf("_i.pcx") < 0) {
                    if (IconOfSkinExists(pcxnames[k], pcxnames, npcxfiles - 1)) {
                        a = pcxnames[k].lastIndexOf('/');
                        b = pcxnames[k].lastIndexOf('\\');

                        if (a > b)
                            c = a;
                        else
                            c = b;

                        scratch = pcxnames[k].substring(c + 1, pcxnames[k]
                                .length());
                        int pos = scratch.lastIndexOf('.');
                        if (pos != -1)
                            scratch = scratch.substring(0, pos);

                        skinnames[s] = scratch;
                        s++;
                    }
                }
            }

            // at this point we have a valid player model
            if (s_pmi[s_numplayermodels] == null)
                s_pmi[s_numplayermodels] = new playermodelinfo_s();

            s_pmi[s_numplayermodels].nskins = nskins;
            s_pmi[s_numplayermodels].skindisplaynames = skinnames;

            // make short name for the model
            a = dirnames[i].lastIndexOf('/');
            b = dirnames[i].lastIndexOf('\\');

            if (a > b)
                c = a;
            else
                c = b;

            s_pmi[s_numplayermodels].displayname = dirnames[i].substring(c + 1);
            s_pmi[s_numplayermodels].directory = dirnames[i].substring(c + 1);

            s_numplayermodels++;
        }

        return true;

    }

    static int pmicmpfnc(Object _a, Object _b) {
        playermodelinfo_s a = (playermodelinfo_s) _a;
        playermodelinfo_s b = (playermodelinfo_s) _b;

        /*
         * * sort by male, female, then alphabetical
         */
        if (Lib.strcmp(a.directory, "male") == 0)
            return -1;
        else if (Lib.strcmp(b.directory, "male") == 0)
            return 1;

        if (Lib.strcmp(a.directory, "female") == 0)
            return -1;
        else if (Lib.strcmp(b.directory, "female") == 0)
            return 1;

        return Lib.strcmp(a.directory, b.directory);
    }

    static String handedness[] = { "right", "left", "center", null };

    static boolean PlayerConfig_MenuInit() {
        /*
         * extern cvar_t * name; extern cvar_t * team; extern cvar_t * skin;
         */
        //har currentdirectory[1024];
        String currentdirectory;
        //char currentskin[1024];
        String currentskin;

        int i = 0;

        int currentdirectoryindex = 0;
        int currentskinindex = 0;

        cvar_t hand = Cvar.Get("hand", "0", CVAR_USERINFO | CVAR_ARCHIVE);

        PlayerConfig_ScanDirectories();

        if (s_numplayermodels == 0)
            return false;

        if (hand.value < 0 || hand.value > 2)
            Cvar.SetValue("hand", 0);

        currentdirectory = skin.string;

        if (currentdirectory.lastIndexOf('/') != -1) {
            currentskin = Lib.rightFrom(currentdirectory, '/');
            currentdirectory = Lib.leftFrom(currentdirectory, '/');
        } else if (currentdirectory.lastIndexOf('\\') != -1) {
            currentskin = Lib.rightFrom(currentdirectory, '\\');
            currentdirectory = Lib.leftFrom(currentdirectory, '\\');
        } else {
            currentdirectory = "male";
            currentskin = "grunt";
        }

        //qsort(s_pmi, s_numplayermodels, sizeof(s_pmi[0]), pmicmpfnc);
        Arrays.sort(s_pmi, 0, s_numplayermodels, new Comparator() {
            public int compare(Object o1, Object o2) {
                return pmicmpfnc(o1, o2);
            }
        });

        //memset(s_pmnames, 0, sizeof(s_pmnames));
        s_pmnames = new String[MAX_PLAYERMODELS];

        for (i = 0; i < s_numplayermodels; i++) {
            s_pmnames[i] = s_pmi[i].displayname;
            if (Lib.Q_stricmp(s_pmi[i].directory, currentdirectory) == 0) {
                int j;

                currentdirectoryindex = i;

                for (j = 0; j < s_pmi[i].nskins; j++) {
                    if (Lib
                            .Q_stricmp(s_pmi[i].skindisplaynames[j],
                                    currentskin) == 0) {
                        currentskinindex = j;
                        break;
                    }
                }
            }
        }

        s_player_config_menu.x = viddef.width / 2 - 95;
        s_player_config_menu.y = viddef.height / 2 - 97;
        s_player_config_menu.nitems = 0;

        s_player_name_field.type = MTYPE_FIELD;
        s_player_name_field.name = "name";
        s_player_name_field.callback = null;
        s_player_name_field.x = 0;
        s_player_name_field.y = 0;
        s_player_name_field.length = 20;
        s_player_name_field.visible_length = 20;
        s_player_name_field.buffer = new StringBuffer(name.string);
        s_player_name_field.cursor = name.string.length();

        s_player_model_title.type = MTYPE_SEPARATOR;
        s_player_model_title.name = "model";
        s_player_model_title.x = -8;
        s_player_model_title.y = 60;

        s_player_model_box.type = MTYPE_SPINCONTROL;
        s_player_model_box.x = -56;
        s_player_model_box.y = 70;
        s_player_model_box.callback = new mcallback() {
            public void execute(Object o) {
                ModelCallback(o);
            }
        };
        s_player_model_box.cursor_offset = -48;
        s_player_model_box.curvalue = currentdirectoryindex;
        s_player_model_box.itemnames = s_pmnames;

        s_player_skin_title.type = MTYPE_SEPARATOR;
        s_player_skin_title.name = "skin";
        s_player_skin_title.x = -16;
        s_player_skin_title.y = 84;

        s_player_skin_box.type = MTYPE_SPINCONTROL;
        s_player_skin_box.x = -56;
        s_player_skin_box.y = 94;
        s_player_skin_box.name = null;
        s_player_skin_box.callback = null;
        s_player_skin_box.cursor_offset = -48;
        s_player_skin_box.curvalue = currentskinindex;
        s_player_skin_box.itemnames = s_pmi[currentdirectoryindex].skindisplaynames;

        s_player_hand_title.type = MTYPE_SEPARATOR;
        s_player_hand_title.name = "handedness";
        s_player_hand_title.x = 32;
        s_player_hand_title.y = 108;

        s_player_handedness_box.type = MTYPE_SPINCONTROL;
        s_player_handedness_box.x = -56;
        s_player_handedness_box.y = 118;
        s_player_handedness_box.name = null;
        s_player_handedness_box.cursor_offset = -48;
        s_player_handedness_box.callback = new mcallback() {
            public void execute(Object o) {
                HandednessCallback(o);
            }
        };
        s_player_handedness_box.curvalue = (int) Cvar.VariableValue("hand");
        s_player_handedness_box.itemnames = handedness;

        for (i = 0; i < rate_tbl.length - 1; i++)
            if (Cvar.VariableValue("rate") == rate_tbl[i])
                break;

        s_player_rate_title.type = MTYPE_SEPARATOR;
        s_player_rate_title.name = "connect speed";
        s_player_rate_title.x = 56;
        s_player_rate_title.y = 156;

        s_player_rate_box.type = MTYPE_SPINCONTROL;
        s_player_rate_box.x = -56;
        s_player_rate_box.y = 166;
        s_player_rate_box.name = null;
        s_player_rate_box.cursor_offset = -48;
        s_player_rate_box.callback = new mcallback() {
            public void execute(Object o) {
                RateCallback(o);
            }
        };
        s_player_rate_box.curvalue = i;
        s_player_rate_box.itemnames = rate_names;

        s_player_download_action.type = MTYPE_ACTION;
        s_player_download_action.name = "download options";
        s_player_download_action.flags = QMF_LEFT_JUSTIFY;
        s_player_download_action.x = -24;
        s_player_download_action.y = 186;
        s_player_download_action.statusbar = null;
        s_player_download_action.callback = new mcallback() {
            public void execute(Object o) {
                DownloadOptionsFunc(o);
            }
        };

        Menu_AddItem(s_player_config_menu, s_player_name_field);
        Menu_AddItem(s_player_config_menu, s_player_model_title);
        Menu_AddItem(s_player_config_menu, s_player_model_box);
        if (s_player_skin_box.itemnames != null) {
            Menu_AddItem(s_player_config_menu, s_player_skin_title);
            Menu_AddItem(s_player_config_menu, s_player_skin_box);
        }
        Menu_AddItem(s_player_config_menu, s_player_hand_title);
        Menu_AddItem(s_player_config_menu, s_player_handedness_box);
        Menu_AddItem(s_player_config_menu, s_player_rate_title);
        Menu_AddItem(s_player_config_menu, s_player_rate_box);
        Menu_AddItem(s_player_config_menu, s_player_download_action);

        return true;
    }

    static int yaw;

    static void PlayerConfig_MenuDraw() {

        refdef_t refdef = new refdef_t();
        //char scratch[MAX_QPATH];
        String scratch;

        //memset(refdef, 0, sizeof(refdef));

        refdef.x = viddef.width / 2;
        refdef.y = viddef.height / 2 - 72;
        refdef.width = 144;
        refdef.height = 168;
        refdef.fov_x = 40;
        refdef.fov_y = Math3D
                .CalcFov(refdef.fov_x, refdef.width, refdef.height);
        refdef.time = cls.realtime * 0.001f;

        if (s_pmi[s_player_model_box.curvalue].skindisplaynames != null) {

            int maxframe = 29;
            entity_t entity = new entity_t();

            //memset(entity, 0, sizeof(entity));

            scratch = "players/" + s_pmi[s_player_model_box.curvalue].directory
                    + "/tris.md2";

            entity.model = re.RegisterModel(scratch);

            scratch = "players/"
                    + s_pmi[s_player_model_box.curvalue].directory
                    + "/"
                    + s_pmi[s_player_model_box.curvalue].skindisplaynames[s_player_skin_box.curvalue]
                    + ".pcx";

            entity.skin = re.RegisterSkin(scratch);
            entity.flags = RF_FULLBRIGHT;
            entity.origin[0] = 80;
            entity.origin[1] = 0;
            entity.origin[2] = 0;
            Math3D.VectorCopy(entity.origin, entity.oldorigin);
            entity.frame = 0;
            entity.oldframe = 0;
            entity.backlerp = 0.0f;
            entity.angles[1] = yaw++;
            if (++yaw > 360)
                yaw -= 360;

            refdef.areabits = null;
            refdef.num_entities = 1;
            refdef.entities = new entity_t[] { entity };
            refdef.lightstyles = null;
            refdef.rdflags = RDF_NOWORLDMODEL;

            Menu_Draw(s_player_config_menu);

            DrawTextBox(
                    (int) ((refdef.x) * (320.0F / viddef.width) - 8),
                    (int) ((viddef.height / 2) * (240.0F / viddef.height) - 77),
                    refdef.width / 8, refdef.height / 8);
            refdef.height += 4;

            re.RenderFrame(refdef);

            scratch = "/players/"
                    + s_pmi[s_player_model_box.curvalue].directory
                    + "/"
                    + s_pmi[s_player_model_box.curvalue].skindisplaynames[s_player_skin_box.curvalue]
                    + "_i.pcx";

            re.DrawPic(s_player_config_menu.x - 40, refdef.y, scratch);
        }
    }

    static String PlayerConfig_MenuKey(int key) {
        int i;

        if (key == K_ESCAPE) {
            //char scratch[1024];
            String scratch;

            Cvar.Set("name", s_player_name_field.buffer.toString());

            scratch = s_pmi[s_player_model_box.curvalue].directory
                    + "/"
                    + s_pmi[s_player_model_box.curvalue].skindisplaynames[s_player_skin_box.curvalue];

            Cvar.Set("skin", scratch);

            for (i = 0; i < s_numplayermodels; i++) {
                int j;

                for (j = 0; j < s_pmi[i].nskins; j++) {
                    if (s_pmi[i].skindisplaynames[j] != null)
                        s_pmi[i].skindisplaynames[j] = null;
                }
                s_pmi[i].skindisplaynames = null;
                s_pmi[i].nskins = 0;
            }
        }
        return Default_MenuKey(s_player_config_menu, key);
    }

    static xcommand_t Menu_PlayerConfig = new xcommand_t() {
        public void execute() {
            Menu_PlayerConfig_f();
        }
    };

    static void Menu_PlayerConfig_f() {
        if (!PlayerConfig_MenuInit()) {
            Menu_SetStatusBar(s_multiplayer_menu,
                    "No valid player models found");
            return;
        }
        Menu_SetStatusBar(s_multiplayer_menu, null);
        PushMenu(new xcommand_t() {
            public void execute() {
                PlayerConfig_MenuDraw();
            }
        }, new keyfunc_t() {
            public String execute(int key) {
                return PlayerConfig_MenuKey(key);
            }
        });
    }

    /*
     * =======================================================================
     * 
     * QUIT MENU
     * 
     * =======================================================================
     */

    static String Quit_Key(int key) {
        switch (key) {
        case K_ESCAPE:
        case 'n':
        case 'N':
            PopMenu();
            break;

        case 'Y':
        case 'y':
            cls.key_dest = key_console;
            CL.Quit_f.execute();
            break;

        default:
            break;
        }

        return null;

    }

    static void Quit_Draw() {
        int w, h;
        Dimension d = new Dimension();
        re.DrawGetPicSize(d, "quit");
        w = d.width;
        h = d.height;
        re.DrawPic((viddef.width - w) / 2, (viddef.height - h) / 2, "quit");
    }

    static xcommand_t Menu_Quit = new xcommand_t() {
        public void execute() {
            Menu_Quit_f();
        }
    };

    static void Menu_Quit_f() {
        PushMenu(new xcommand_t() {
            public void execute() {
                Quit_Draw();
            }
        }, new keyfunc_t() {
            public String execute(int key) {
                return Quit_Key(key);
            }
        });
    }

    //	  =============================================================================
    /* Menu Subsystem */

    /**
     * Init
     */
    public static void Init() {
        Cmd.AddCommand("menu_main", Menu_Main);
        Cmd.AddCommand("menu_game", Menu_Game);
        Cmd.AddCommand("menu_loadgame", Menu_LoadGame);
        Cmd.AddCommand("menu_savegame", Menu_SaveGame);
        Cmd.AddCommand("menu_joinserver", Menu_JoinServer);
        Cmd.AddCommand("menu_addressbook", Menu_AddressBook);
        Cmd.AddCommand("menu_startserver", Menu_StartServer);
        Cmd.AddCommand("menu_dmoptions", Menu_DMOptions);
        Cmd.AddCommand("menu_playerconfig", Menu_PlayerConfig);
        Cmd.AddCommand("menu_downloadoptions", Menu_DownloadOptions);
        Cmd.AddCommand("menu_credits", Menu_Credits);
        Cmd.AddCommand("menu_multiplayer", Menu_Multiplayer);
        Cmd.AddCommand("menu_video", Menu_Video);
        Cmd.AddCommand("menu_options", Menu_Options);
        Cmd.AddCommand("menu_keys", Menu_Keys);
        Cmd.AddCommand("menu_quit", Menu_Quit);

        for (int i = 0; i < m_layers.length; i++) {
            m_layers[i] = new menulayer_t();
        }
    }

    /*
     * ================= Draw =================
     */
    static void Draw() {
        if (cls.key_dest != key_menu)
            return;

        // repaint everything next frame
        SCR.DirtyScreen();

        // dim everything behind it down
        if (cl.cinematictime > 0)
            re.DrawFill(0, 0, viddef.width, viddef.height, 0);
        else
            re.DrawFadeScreen();

        m_drawfunc.execute();

        // delay playing the enter sound until after the
        // menu has been drawn, to avoid delay while
        // caching images
        if (m_entersound) {
            S.StartLocalSound(menu_in_sound);
            m_entersound = false;
        }
    }

    /*
     * ================= Keydown =================
     */
    static void Keydown(int key) {
        String s;

        if (m_keyfunc != null)
            if ((s = m_keyfunc.execute(key)) != null)
                S.StartLocalSound(s);
    }

    public static void Action_DoEnter(menuaction_s a) {
        if (a.callback != null)
            a.callback.execute(a);
    }

    public static void Action_Draw(menuaction_s a) {
        if ((a.flags & QMF_LEFT_JUSTIFY) != 0) {
            if ((a.flags & QMF_GRAYED) != 0)
                Menu_DrawStringDark(a.x + a.parent.x + LCOLUMN_OFFSET, a.y
                        + a.parent.y, a.name);
            else
                Menu_DrawString(a.x + a.parent.x + LCOLUMN_OFFSET, a.y
                        + a.parent.y, a.name);
        } else {
            if ((a.flags & QMF_GRAYED) != 0)
                Menu_DrawStringR2LDark(a.x + a.parent.x + LCOLUMN_OFFSET, a.y
                        + a.parent.y, a.name);
            else
                Menu_DrawStringR2L(a.x + a.parent.x + LCOLUMN_OFFSET, a.y
                        + a.parent.y, a.name);
        }
        if (a.ownerdraw != null)
            a.ownerdraw.execute(a);
    }

    public static boolean Field_DoEnter(menufield_s f) {
        if (f.callback != null) {
            f.callback.execute(f);
            return true;
        }
        return false;
    }

    public static void Field_Draw(menufield_s f) {
        int i;
        String tempbuffer;
        //[128] = "";

        if (f.name != null)
            Menu_DrawStringR2LDark(f.x + f.parent.x + LCOLUMN_OFFSET, f.y
                    + f.parent.y, f.name);

        //strncpy(tempbuffer, f.buffer + f.visible_offset, f.visible_length);
        String s = f.buffer.toString();
        tempbuffer = s.substring(f.visible_offset, s.length());
        re.DrawChar(f.x + f.parent.x + 16, f.y + f.parent.y - 4, 18);
        re.DrawChar(f.x + f.parent.x + 16, f.y + f.parent.y + 4, 24);

        re.DrawChar(f.x + f.parent.x + 24 + f.visible_length * 8, f.y
                + f.parent.y - 4, 20);
        re.DrawChar(f.x + f.parent.x + 24 + f.visible_length * 8, f.y
                + f.parent.y + 4, 26);

        for (i = 0; i < f.visible_length; i++) {
            re
                    .DrawChar(f.x + f.parent.x + 24 + i * 8, f.y + f.parent.y
                            - 4, 19);
            re
                    .DrawChar(f.x + f.parent.x + 24 + i * 8, f.y + f.parent.y
                            + 4, 25);
        }

        Menu_DrawString(f.x + f.parent.x + 24, f.y + f.parent.y, tempbuffer);

        if (Menu_ItemAtCursor(f.parent) == f) {
            int offset;

            if (f.visible_offset != 0)
                offset = f.visible_length;
            else
                offset = f.cursor;

            if ((((int) (Sys.Milliseconds() / 250)) & 1) != 0) {
                re.DrawChar(f.x + f.parent.x + (offset + 2) * 8 + 8, f.y
                        + f.parent.y, 11);
            } else {
                re.DrawChar(f.x + f.parent.x + (offset + 2) * 8 + 8, f.y
                        + f.parent.y, ' ');
            }
        }
    }

    public static boolean Field_Key(menufield_s f, int k) {
        char key = (char) k;

        switch (key) {
        case K_KP_SLASH:
            key = '/';
            break;
        case K_KP_MINUS:
            key = '-';
            break;
        case K_KP_PLUS:
            key = '+';
            break;
        case K_KP_HOME:
            key = '7';
            break;
        case K_KP_UPARROW:
            key = '8';
            break;
        case K_KP_PGUP:
            key = '9';
            break;
        case K_KP_LEFTARROW:
            key = '4';
            break;
        case K_KP_5:
            key = '5';
            break;
        case K_KP_RIGHTARROW:
            key = '6';
            break;
        case K_KP_END:
            key = '1';
            break;
        case K_KP_DOWNARROW:
            key = '2';
            break;
        case K_KP_PGDN:
            key = '3';
            break;
        case K_KP_INS:
            key = '0';
            break;
        case K_KP_DEL:
            key = '.';
            break;
        }

        if (key > 127) {
            switch (key) {
            case K_DEL:
            default:
                return false;
            }
        }

        /*
         * * support pasting from the clipboard
         */
        if ((Character.toUpperCase(key) == 'V' && keydown[K_CTRL])
                || (((key == K_INS) || (key == K_KP_INS)) && keydown[K_SHIFT])) {
            String cbd;

            if ((cbd = Sys.GetClipboardData()) != null) {
                //strtok(cbd, "\n\r\b");
                String lines[] = cbd.split("\r\n");
                if (lines.length > 0 && lines[0].length() != 0) {
                    //strncpy(f.buffer, cbd, f.length - 1);
                    f.buffer = new StringBuffer(lines[0]);
                    f.cursor = f.buffer.length();

                    f.visible_offset = f.cursor - f.visible_length;

                    if (f.visible_offset < 0)
                        f.visible_offset = 0;
                }
            }
            return true;
        }

        switch (key) {
        case K_KP_LEFTARROW:
        case K_LEFTARROW:
        case K_BACKSPACE:
            if (f.cursor > 0) {
                f.buffer.deleteCharAt(f.cursor - 1);
                //memmove(f.buffer[f.cursor - 1], f.buffer[f.cursor], strlen(&
                // f.buffer[f.cursor]) + 1);
                f.cursor--;

                if (f.visible_offset != 0) {
                    f.visible_offset--;
                }
            }
            break;

        case K_KP_DEL:
        case K_DEL:
            //memmove(& f.buffer[f.cursor], & f.buffer[f.cursor + 1], strlen(&
            // f.buffer[f.cursor + 1]) + 1);
            f.buffer.deleteCharAt(f.cursor);
            break;

        case K_KP_ENTER:
        case K_ENTER:
        case K_ESCAPE:
        case K_TAB:
            return false;

        case K_SPACE:
        default:
            if (!Character.isDigit(key) && (f.flags & QMF_NUMBERSONLY) != 0)
                return false;

            if (f.cursor < f.length) {
                f.buffer.append(key);
                f.cursor++;

                if (f.cursor > f.visible_length) {
                    f.visible_offset++;
                }
            }
        }

        return true;
    }

    public static void Menu_AddItem(menuframework_s menu, menucommon_s item) {
        if (menu.nitems == 0)
            menu.nslots = 0;

        if (menu.nitems < MAXMENUITEMS) {
            menu.items[menu.nitems] = item;
            ((menucommon_s) menu.items[menu.nitems]).parent = menu;
            menu.nitems++;
        }

        menu.nslots = Menu_TallySlots(menu);
    }

    /*
     * * Menu_AdjustCursor * * This function takes the given menu, the
     * direction, and attempts * to adjust the menu's cursor so that it's at the
     * next available * slot.
     */
    public static void Menu_AdjustCursor(menuframework_s m, int dir) {
        menucommon_s citem;

        /*
         * * see if it's in a valid spot
         */
        if (m.cursor >= 0 && m.cursor < m.nitems) {
            if ((citem = Menu_ItemAtCursor(m)) != null) {
                if (citem.type != MTYPE_SEPARATOR)
                    return;
            }
        }

        /*
         * * it's not in a valid spot, so crawl in the direction indicated until
         * we * find a valid spot
         */
        if (dir == 1) {
            while (true) {
                citem = Menu_ItemAtCursor(m);
                if (citem != null)
                    if (citem.type != MTYPE_SEPARATOR)
                        break;
                m.cursor += dir;
                if (m.cursor >= m.nitems)
                    m.cursor = 0;
            }
        } else {
            while (true) {
                citem = Menu_ItemAtCursor(m);
                if (citem != null)
                    if (citem.type != MTYPE_SEPARATOR)
                        break;
                m.cursor += dir;
                if (m.cursor < 0)
                    m.cursor = m.nitems - 1;
            }
        }
    }

    public static void Menu_Center(menuframework_s menu) {
        int height;

        height = ((menucommon_s) menu.items[menu.nitems - 1]).y;
        height += 10;

        menu.y = (viddef.height - height) / 2;
    }

    public static void Menu_Draw(menuframework_s menu) {
        int i;
        menucommon_s item;

        /*
         * * draw contents
         */
        for (i = 0; i < menu.nitems; i++) {
            switch (((menucommon_s) menu.items[i]).type) {
            case MTYPE_FIELD:
                Field_Draw((menufield_s) menu.items[i]);
                break;
            case MTYPE_SLIDER:
                Slider_Draw((menuslider_s) menu.items[i]);
                break;
            case MTYPE_LIST:
                MenuList_Draw((menulist_s) menu.items[i]);
                break;
            case MTYPE_SPINCONTROL:
                SpinControl_Draw((menulist_s) menu.items[i]);
                break;
            case MTYPE_ACTION:
                Action_Draw((menuaction_s) menu.items[i]);
                break;
            case MTYPE_SEPARATOR:
                Separator_Draw((menuseparator_s) menu.items[i]);
                break;
            }
        }

        item = Menu_ItemAtCursor(menu);

        if (item != null && item.cursordraw != null) {
            item.cursordraw.execute(item);
        } else if (menu.cursordraw != null) {
            menu.cursordraw.execute(menu);
        } else if (item != null && item.type != MTYPE_FIELD) {
            if ((item.flags & QMF_LEFT_JUSTIFY) != 0) {
                re.DrawChar(menu.x + item.x - 24 + item.cursor_offset, menu.y
                        + item.y, 12 + ((int) (Sys.Milliseconds() / 250) & 1));
            } else {
                re.DrawChar(menu.x + item.cursor_offset, menu.y + item.y,
                        12 + ((int) (Sys.Milliseconds() / 250) & 1));
            }
        }

        if (item != null) {
            if (item.statusbarfunc != null)
                item.statusbarfunc.execute(item);
            else if (item.statusbar != null)
                Menu_DrawStatusBar(item.statusbar);
            else
                Menu_DrawStatusBar(menu.statusbar);

        } else {
            Menu_DrawStatusBar(menu.statusbar);
        }
    }

    public static void Menu_DrawStatusBar(String string) {
        if (string != null) {
            int l = string.length();
            int maxrow = viddef.height / 8;
            int maxcol = viddef.width / 8;
            int col = maxcol / 2 - l / 2;

            re.DrawFill(0, viddef.height - 8, viddef.width, 8, 4);
            Menu_DrawString(col * 8, viddef.height - 8, string);
        } else {
            re.DrawFill(0, viddef.height - 8, viddef.width, 8, 0);
        }
    }

    public static void Menu_DrawString(int x, int y, String string) {
        int i;

        for (i = 0; i < string.length(); i++) {
            re.DrawChar((x + i * 8), y, string.charAt(i));
        }
    }

    public static void Menu_DrawStringDark(int x, int y, String string) {
        int i;

        for (i = 0; i < string.length(); i++) {
            re.DrawChar((x + i * 8), y, string.charAt(i) + 128);
        }
    }

    public static void Menu_DrawStringR2L(int x, int y, String string) {
        int i;

        int l = string.length();
        for (i = 0; i < l; i++) {
            re.DrawChar((x - i * 8), y, string.charAt(l - i - 1));
        }
    }

    public static void Menu_DrawStringR2LDark(int x, int y, String string) {
        int i;

        int l = string.length();
        for (i = 0; i < l; i++) {
            re.DrawChar((x - i * 8), y, string.charAt(l - i - 1) + 128);
        }
    }

    public static menucommon_s Menu_ItemAtCursor(menuframework_s m) {
        if (m.cursor < 0 || m.cursor >= m.nitems)
            return null;

        return (menucommon_s) m.items[m.cursor];
    }

    static boolean Menu_SelectItem(menuframework_s s) {
        menucommon_s item = Menu_ItemAtCursor(s);

        if (item != null) {
            switch (item.type) {
            case MTYPE_FIELD:
                return Field_DoEnter((menufield_s) item);
            case MTYPE_ACTION:
                Action_DoEnter((menuaction_s) item);
                return true;
            case MTYPE_LIST:
                //			Menulist_DoEnter( ( menulist_s ) item );
                return false;
            case MTYPE_SPINCONTROL:
                //			SpinControl_DoEnter( ( menulist_s ) item );
                return false;
            }
        }
        return false;
    }

    public static void Menu_SetStatusBar(menuframework_s m, String string) {
        m.statusbar = string;
    }

    public static void Menu_SlideItem(menuframework_s s, int dir) {
        menucommon_s item = (menucommon_s) Menu_ItemAtCursor(s);

        if (item != null) {
            switch (item.type) {
            case MTYPE_SLIDER:
                Slider_DoSlide((menuslider_s) item, dir);
                break;
            case MTYPE_SPINCONTROL:
                SpinControl_DoSlide((menulist_s) item, dir);
                break;
            }
        }
    }

    public static int Menu_TallySlots(menuframework_s menu) {
        int i;
        int total = 0;

        for (i = 0; i < menu.nitems; i++) {
            if (((menucommon_s) menu.items[i]).type == MTYPE_LIST) {
                int nitems = 0;
                String n[] = ((menulist_s) menu.items[i]).itemnames;

                while (n[nitems] != null)
                    nitems++;

                total += nitems;
            } else {
                total++;
            }
        }

        return total;
    }

    public static void Menulist_DoEnter(menulist_s l) {
        int start;

        start = l.y / 10 + 1;

        l.curvalue = l.parent.cursor - start;

        if (l.callback != null)
            l.callback.execute(l);
    }

    public static void MenuList_Draw(menulist_s l) {
        String n[];
        int y = 0;

        Menu_DrawStringR2LDark(l.x + l.parent.x + LCOLUMN_OFFSET, l.y
                + l.parent.y, l.name);

        n = l.itemnames;

        re.DrawFill(l.x - 112 + l.parent.x, l.parent.y + l.y + l.curvalue * 10
                + 10, 128, 10, 16);
        int i = 0;

        while (n[i] != null) {
            Menu_DrawStringR2LDark(l.x + l.parent.x + LCOLUMN_OFFSET, l.y
                    + l.parent.y + y + 10, n[i]);

            i++;
            y += 10;
        }
    }

    public static void Separator_Draw(menuseparator_s s) {
        if (s.name != null)
            Menu_DrawStringR2LDark(s.x + s.parent.x, s.y + s.parent.y, s.name);
    }

    public static void Slider_DoSlide(menuslider_s s, int dir) {
        s.curvalue += dir;

        if (s.curvalue > s.maxvalue)
            s.curvalue = s.maxvalue;
        else if (s.curvalue < s.minvalue)
            s.curvalue = s.minvalue;

        if (s.callback != null)
            s.callback.execute(s);
    }

    public static final int SLIDER_RANGE = 10;

    public static void Slider_Draw(menuslider_s s) {
        int i;

        Menu_DrawStringR2LDark(s.x + s.parent.x + LCOLUMN_OFFSET, s.y
                + s.parent.y, s.name);

        s.range = (s.curvalue - s.minvalue) / (float) (s.maxvalue - s.minvalue);

        if (s.range < 0)
            s.range = 0;
        if (s.range > 1)
            s.range = 1;
        re.DrawChar(s.x + s.parent.x + RCOLUMN_OFFSET, s.y + s.parent.y, 128);
        for (i = 0; i < SLIDER_RANGE; i++)
            re.DrawChar(RCOLUMN_OFFSET + s.x + i * 8 + s.parent.x + 8, s.y
                    + s.parent.y, 129);
        re.DrawChar(RCOLUMN_OFFSET + s.x + i * 8 + s.parent.x + 8, s.y
                + s.parent.y, 130);
        re
                .DrawChar(
                        (int) (8 + RCOLUMN_OFFSET + s.parent.x + s.x + (SLIDER_RANGE - 1)
                                * 8 * s.range), s.y + s.parent.y, 131);
    }

    public static void SpinControl_DoEnter(menulist_s s) {
        s.curvalue++;
        if (s.itemnames[s.curvalue] == null)
            s.curvalue = 0;

        if (s.callback != null)
            s.callback.execute(s);
    }

    public static void SpinControl_DoSlide(menulist_s s, int dir) {
        s.curvalue += dir;

        if (s.curvalue < 0)
            s.curvalue = 0;
        else if (s.itemnames[s.curvalue] == null)
            s.curvalue--;

        if (s.callback != null)
            s.callback.execute(s);
    }

    public static void SpinControl_Draw(menulist_s s) {
        //char buffer[100];
        String buffer;

        if (s.name != null) {
            Menu_DrawStringR2LDark(s.x + s.parent.x + LCOLUMN_OFFSET, s.y
                    + s.parent.y, s.name);
        }

        if (s.itemnames[s.curvalue].indexOf('\n') == -1) {
            Menu_DrawString(RCOLUMN_OFFSET + s.x + s.parent.x,
                    s.y + s.parent.y, s.itemnames[s.curvalue]);
        } else {
            String line1, line2;
            line1 = Lib.leftFrom(s.itemnames[s.curvalue], '\n');
            Menu_DrawString(RCOLUMN_OFFSET + s.x + s.parent.x,
                    s.y + s.parent.y, line1);

            line2 = Lib.rightFrom(s.itemnames[s.curvalue], '\n');

            int pos = line2.indexOf('\n');
            if (pos != -1)
                line2 = line2.substring(0, pos);

            Menu_DrawString(RCOLUMN_OFFSET + s.x + s.parent.x, s.y + s.parent.y
                    + 10, line2);
        }
    }
}