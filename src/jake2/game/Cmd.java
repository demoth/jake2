/*
 * Cmd.java
 * Copyright (C) 2003
 * 
 * $Id: Cmd.java,v 1.15 2005-11-16 22:24:52 salomo Exp $
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
package jake2.game;

import jake2.Defines;
import jake2.Globals;
import jake2.game.monsters.M_Player;
import jake2.qcommon.*;
import jake2.server.SV_GAME;
import jake2.util.Lib;

import java.util.*;
import java.util.Arrays;
import java.util.Vector;

/**
 * Cmd
 */
public final class Cmd {
    static xcommand_t List_f = new xcommand_t() {
        public void execute() {
            cmd_function_t cmd = Cmd.cmd_functions;
            int i = 0;

            while (cmd != null) {
                Com.Printf(cmd.name + '\n');
                i++;
                cmd = cmd.next;
            }
            Com.Printf(i + " commands\n");
        }
    };

    static xcommand_t Exec_f = new xcommand_t() {
        public void execute() {
            if (Cmd.Argc() != 2) {
                Com.Printf("exec <filename> : execute a script file\n");
                return;
            }

            byte[] f = null;
            f = FS.LoadFile(Cmd.Argv(1));
            if (f == null) {
                Com.Printf("couldn't exec " + Cmd.Argv(1) + "\n");
                return;
            }
            Com.Printf("execing " + Cmd.Argv(1) + "\n");

            Cbuf.InsertText(new String(f));

            FS.FreeFile(f);
        }
    };

    static xcommand_t Echo_f = new xcommand_t() {
        public void execute() {
            for (int i = 1; i < Cmd.Argc(); i++) {
                Com.Printf(Cmd.Argv(i) + " ");
            }
            Com.Printf("'\n");
        }
    };

    static xcommand_t Alias_f = new xcommand_t() {
        public void execute() {
            cmdalias_t a = null;
            if (Cmd.Argc() == 1) {
                Com.Printf("Current alias commands:\n");
                for (a = Globals.cmd_alias; a != null; a = a.next) {
                    Com.Printf(a.name + " : " + a.value);
                }
                return;
            }

            String s = Cmd.Argv(1);
            if (s.length() > Defines.MAX_ALIAS_NAME) {
                Com.Printf("Alias name is too long\n");
                return;
            }

            // if the alias already exists, reuse it
            for (a = Globals.cmd_alias; a != null; a = a.next) {
                if (s.equalsIgnoreCase(a.name)) {
                    a.value = null;
                    break;
                }
            }

            if (a == null) {
                a = new cmdalias_t();
                a.next = Globals.cmd_alias;
                Globals.cmd_alias = a;
            }
            a.name = s;

            // copy the rest of the command line
            String cmd = "";
            int c = Cmd.Argc();
            for (int i = 2; i < c; i++) {
                cmd = cmd + Cmd.Argv(i);
                if (i != (c - 1))
                    cmd = cmd + " ";
            }
            cmd = cmd + "\n";

            a.value = cmd;
        }
    };

    public static xcommand_t Wait_f = new xcommand_t() {
        public void execute() {
            Globals.cmd_wait = true;
        }
    };

    public static cmd_function_t cmd_functions = null;

    public static int cmd_argc;

    public static String[] cmd_argv = new String[Defines.MAX_STRING_TOKENS];

    public static String cmd_args;

    public static final int ALIAS_LOOP_COUNT = 16;

    /**
     * register our commands
     */
    public static void Init() {

        Cmd.AddCommand("exec", Exec_f);
        Cmd.AddCommand("echo", Echo_f);
        Cmd.AddCommand("cmdlist", List_f);
        Cmd.AddCommand("alias", Alias_f);
        Cmd.AddCommand("wait", Wait_f);
    }

    private static char expanded[] = new char[Defines.MAX_STRING_CHARS];

    private static char temporary[] = new char[Defines.MAX_STRING_CHARS];

    public static Comparator PlayerSort = new Comparator() {
        public int compare(Object o1, Object o2) {
            int anum = ((Integer) o1).intValue();
            int bnum = ((Integer) o2).intValue();
    
            int anum1 = GameBase.game.clients[anum].ps.stats[Defines.STAT_FRAGS];
            int bnum1 = GameBase.game.clients[bnum].ps.stats[Defines.STAT_FRAGS];
    
            if (anum1 < bnum1)
                return -1;
            if (anum1 > bnum1)
                return 1;
            return 0;
        }
    };

    /*
     * ====================== 
     * Cmd_MacroExpandString 
     * ======================
     */
    public static char[] MacroExpandString(char text[], int len) {
        int i, j, count;
        boolean inquote;

        char scan[];

        String token;
        inquote = false;

        scan = text;

        if (len >= Defines.MAX_STRING_CHARS) {
            Com.Printf("Line exceeded " + Defines.MAX_STRING_CHARS
                    + " chars, discarded.\n");
            return null;
        }

        count = 0;

        for (i = 0; i < len; i++) {
            if (scan[i] == '"')
                inquote = !inquote;

            if (inquote)
                continue; // don't expand inside quotes

            if (scan[i] != '$')
                continue;

            // scan out the complete macro, without $
            Com.ParseHelp ph = new Com.ParseHelp(text, i + 1);
            token = Com.Parse(ph);

            if (ph.data == null)
                continue;

            token = Cvar.VariableString(token);

            j = token.length();

            len += j;

            if (len >= Defines.MAX_STRING_CHARS) {
                Com.Printf("Expanded line exceeded " + Defines.MAX_STRING_CHARS
                        + " chars, discarded.\n");
                return null;
            }

            //strncpy(temporary, scan, i);
            System.arraycopy(scan, 0, temporary, 0, i);

            //strcpy(temporary + i, token);
            System.arraycopy(token.toCharArray(), 0, temporary, i, token.length());

            //strcpy(temporary + i + j, start);
            System.arraycopy(ph.data, ph.index, temporary, i + j, len - ph.index - j);

            //strcpy(expanded, temporary);
            System.arraycopy(temporary, 0, expanded, 0, 0);
            scan = expanded;
            i--;

            if (++count == 100) {
                Com.Printf("Macro expansion loop, discarded.\n");
                return null;
            }
        }

        if (inquote) {
            Com.Printf("Line has unmatched quote, discarded.\n");
            return null;
        }

        return scan;
    }

    /*
     * ============ 
     * Cmd_TokenizeString
     * 
     * Parses the given string into command line tokens. $Cvars will be expanded
     * unless they are in a quoted token 
     * ============
     */
    public static void TokenizeString(char text[], boolean macroExpand) {
        String com_token;

        cmd_argc = 0;
        cmd_args = "";

        int len = Lib.strlen(text);

        // macro expand the text
        if (macroExpand)
            text = MacroExpandString(text, len);

        if (text == null)
            return;

        len = Lib.strlen(text);

        Com.ParseHelp ph = new Com.ParseHelp(text);

        while (true) {

            // skip whitespace up to a /n
            char c = ph.skipwhitestoeol();

            if (c == '\n') { // a newline seperates commands in the buffer
                c = ph.nextchar();
                break;
            }

            if (c == 0)
                return;

            // set cmd_args to everything after the first arg
            if (cmd_argc == 1) {
                cmd_args = new String(text, ph.index, len - ph.index);
                cmd_args.trim();
            }

            com_token = Com.Parse(ph);

            if (ph.data == null)
                return;

            if (cmd_argc < Defines.MAX_STRING_TOKENS) {
                cmd_argv[cmd_argc] = com_token;
                cmd_argc++;
            }
        }
    }

    public static void AddCommand(String cmd_name, xcommand_t function) {
        cmd_function_t cmd;
        //Com.DPrintf("Cmd_AddCommand: " + cmd_name + "\n");
        // fail if the command is a variable name
        if ((Cvar.VariableString(cmd_name)).length() > 0) {
            Com.Printf("Cmd_AddCommand: " + cmd_name
                    + " already defined as a var\n");
            return;
        }

        // fail if the command already exists
        for (cmd = cmd_functions; cmd != null; cmd = cmd.next) {
            if (cmd_name.equals(cmd.name)) {
                Com
                        .Printf("Cmd_AddCommand: " + cmd_name
                                + " already defined\n");
                return;
            }
        }

        cmd = new cmd_function_t();
        cmd.name = cmd_name;

        cmd.function = function;
        cmd.next = cmd_functions;
        cmd_functions = cmd;
    }

    /*
     * ============ 
     * Cmd_RemoveCommand 
     * ============
     */
    public static void RemoveCommand(String cmd_name) {
        cmd_function_t cmd, back = null;

        back = cmd = cmd_functions;

        while (true) {

            if (cmd == null) {
                Com.Printf("Cmd_RemoveCommand: " + cmd_name + " not added\n");
                return;
            }
            if (0 == Lib.strcmp(cmd_name, cmd.name)) {
                if (cmd == cmd_functions)
                    cmd_functions = cmd.next;
                else
                    back.next = cmd.next;
                return;
            }
            back = cmd;
            cmd = cmd.next;
        }
    }

    /*
     * ============ 
     * Cmd_Exists 
     * ============
     */
    public static boolean Exists(String cmd_name) {
        cmd_function_t cmd;

        for (cmd = cmd_functions; cmd != null; cmd = cmd.next) {
            if (cmd.name.equals(cmd_name))
                return true;
        }

        return false;
    }

    public static int Argc() {
        return cmd_argc;
    }

    public static String Argv(int i) {
        if (i < 0 || i >= cmd_argc)
            return "";
        return cmd_argv[i];
    }

    public static String Args() {
        return new String(cmd_args);
    }

    /*
     * ============ 
     * Cmd_ExecuteString
     * 
     * A complete command line has been parsed, so try to execute it 
     * FIXME: lookupnoadd the token to speed search? 
     * ============
     */
    public static void ExecuteString(String text) {

        cmd_function_t cmd;
        cmdalias_t a;

        TokenizeString(text.toCharArray(), true);

        //		if (Argc() > 0) {
        //			Com.DPrintf("tokenized:");
        //			for (int xxx = 0; xxx < Argc(); xxx++)
        //				Com.DPrintf("[" + Argv(xxx) + "]");
        //
        //			Com.DPrintf("\n");
        //		}
        // execute the command line
        if (Argc() == 0)
            return; // no tokens

        // check functions
        for (cmd = cmd_functions; cmd != null; cmd = cmd.next) {
            if (cmd_argv[0].equalsIgnoreCase(cmd.name)) {
                if (null == cmd.function) { // forward to server command
                    Cmd.ExecuteString("cmd " + text);
                } else {
                    cmd.function.execute();
                }
                return;
            }
        }

        // check alias
        for (a = Globals.cmd_alias; a != null; a = a.next) {

            if (cmd_argv[0].equalsIgnoreCase(a.name)) {

                if (++Globals.alias_count == ALIAS_LOOP_COUNT) {
                    Com.Printf("ALIAS_LOOP_COUNT\n");
                    return;
                }
                Cbuf.InsertText(a.value);
                return;
            }
        }

        // check cvars
        if (Cvar.Command())
            return;

        // send it as a server command if we are connected
        Cmd.ForwardToServer();
    }

    /*
     * ================== 
     * Cmd_Give_f
     * 
     * Give items to a client 
     * ==================
     */
    public static void Give_f(edict_t ent) {
        String name;
        gitem_t it;
        int index;
        int i;
        boolean give_all;
        edict_t it_ent;

        if (GameBase.deathmatch.value == 0 && GameBase.sv_cheats.value == 0) {
            SV_GAME
                    .PF_cprintf(ent, Defines.PRINT_HIGH,
                            "You must run the server with '+set cheats 1' to enable this command.\n");
            return;
        }

        name = Cmd.Args();

        if (0 == Lib.Q_stricmp(name, "all"))
            give_all = true;
        else
            give_all = false;

        if (give_all || 0 == Lib.Q_stricmp(Cmd.Argv(1), "health")) {
            if (Cmd.Argc() == 3)
                ent.health = Lib.atoi(Cmd.Argv(2));
            else
                ent.health = ent.max_health;
            if (!give_all)
                return;
        }

        if (give_all || 0 == Lib.Q_stricmp(name, "weapons")) {
            for (i = 1; i < GameBase.game.num_items; i++) {
                it = GameItems.itemlist[i];
                if (null == it.pickup)
                    continue;
                if (0 == (it.flags & Defines.IT_WEAPON))
                    continue;
                ent.client.pers.inventory[i] += 1;
            }
            if (!give_all)
                return;
        }

        if (give_all || 0 == Lib.Q_stricmp(name, "ammo")) {
            for (i = 1; i < GameBase.game.num_items; i++) {
                it = GameItems.itemlist[i];
                if (null == it.pickup)
                    continue;
                if (0 == (it.flags & Defines.IT_AMMO))
                    continue;
                GameItems.Add_Ammo(ent, it, 1000);
            }
            if (!give_all)
                return;
        }

        if (give_all || Lib.Q_stricmp(name, "armor") == 0) {
            gitem_armor_t info;

            it = GameItems.FindItem("Jacket Armor");
            ent.client.pers.inventory[GameItems.ITEM_INDEX(it)] = 0;

            it = GameItems.FindItem("Combat Armor");
            ent.client.pers.inventory[GameItems.ITEM_INDEX(it)] = 0;

            it = GameItems.FindItem("Body Armor");
            info = (gitem_armor_t) it.info;
            ent.client.pers.inventory[GameItems.ITEM_INDEX(it)] = info.max_count;

            if (!give_all)
                return;
        }

        if (give_all || Lib.Q_stricmp(name, "Power Shield") == 0) {
            it = GameItems.FindItem("Power Shield");
            it_ent = GameUtil.G_Spawn();
            it_ent.classname = it.classname;
            GameItems.SpawnItem(it_ent, it);
            GameItems.Touch_Item(it_ent, ent, GameBase.dummyplane, null);
            if (it_ent.inuse)
                GameUtil.G_FreeEdict(it_ent);

            if (!give_all)
                return;
        }

        if (give_all) {
            for (i = 1; i < GameBase.game.num_items; i++) {
                it = GameItems.itemlist[i];
                if (it.pickup != null)
                    continue;
                if ((it.flags & (Defines.IT_ARMOR | Defines.IT_WEAPON | Defines.IT_AMMO)) != 0)
                    continue;
                ent.client.pers.inventory[i] = 1;
            }
            return;
        }

        it = GameItems.FindItem(name);
        if (it == null) {
            name = Cmd.Argv(1);
            it = GameItems.FindItem(name);
            if (it == null) {
                SV_GAME.PF_cprintf(ent, Defines.PRINT_HIGH, "unknown item\n");
                return;
            }
        }

        if (it.pickup == null) {
            SV_GAME.PF_cprintf(ent, Defines.PRINT_HIGH, "non-pickup item\n");
            return;
        }

        index = GameItems.ITEM_INDEX(it);

        if ((it.flags & Defines.IT_AMMO) != 0) {
            if (Cmd.Argc() == 3)
                ent.client.pers.inventory[index] = Lib.atoi(Cmd.Argv(2));
            else
                ent.client.pers.inventory[index] += it.quantity;
        } else {
            it_ent = GameUtil.G_Spawn();
            it_ent.classname = it.classname;
            GameItems.SpawnItem(it_ent, it);
            GameItems.Touch_Item(it_ent, ent, GameBase.dummyplane, null);
            if (it_ent.inuse)
                GameUtil.G_FreeEdict(it_ent);
        }
    }

    /*
     * ================== 
     * Cmd_God_f
     * 
     * Sets client to godmode
     * 
     * argv(0) god 
     * ==================
     */
    public static void God_f(edict_t ent) {
        String msg;

        if (GameBase.deathmatch.value == 0 && GameBase.sv_cheats.value == 0) {
            SV_GAME
                    .PF_cprintf(ent, Defines.PRINT_HIGH,
                            "You must run the server with '+set cheats 1' to enable this command.\n");
            return;
        }

        ent.flags ^= Defines.FL_GODMODE;
        if (0 == (ent.flags & Defines.FL_GODMODE))
            msg = "godmode OFF\n";
        else
            msg = "godmode ON\n";

        SV_GAME.PF_cprintf(ent, Defines.PRINT_HIGH, msg);
    }

    /*
     * ================== 
     * Cmd_Notarget_f
     * 
     * Sets client to notarget
     * 
     * argv(0) notarget 
     * ==================
     */
    public static void Notarget_f(edict_t ent) {
        String msg;

        if (GameBase.deathmatch.value != 0 && GameBase.sv_cheats.value == 0) {
            SV_GAME
                    .PF_cprintf(ent, Defines.PRINT_HIGH,
                            "You must run the server with '+set cheats 1' to enable this command.\n");
            return;
        }

        ent.flags ^= Defines.FL_NOTARGET;
        if (0 == (ent.flags & Defines.FL_NOTARGET))
            msg = "notarget OFF\n";
        else
            msg = "notarget ON\n";

        SV_GAME.PF_cprintf(ent, Defines.PRINT_HIGH, msg);
    }

    /*
     * ================== 
     * Cmd_Noclip_f
     * 
     * argv(0) noclip 
     * ==================
     */
    public static void Noclip_f(edict_t ent) {
        String msg;

        if (GameBase.deathmatch.value != 0 && GameBase.sv_cheats.value == 0) {
            SV_GAME
                    .PF_cprintf(ent, Defines.PRINT_HIGH,
                            "You must run the server with '+set cheats 1' to enable this command.\n");
            return;
        }

        if (ent.movetype == Defines.MOVETYPE_NOCLIP) {
            ent.movetype = Defines.MOVETYPE_WALK;
            msg = "noclip OFF\n";
        } else {
            ent.movetype = Defines.MOVETYPE_NOCLIP;
            msg = "noclip ON\n";
        }

        SV_GAME.PF_cprintf(ent, Defines.PRINT_HIGH, msg);
    }

    /*
     * ================== 
     * Cmd_Use_f
     * 
     * Use an inventory item 
     * ==================
     */
    public static void Use_f(edict_t ent) {
        int index;
        gitem_t it;
        String s;

        s = Cmd.Args();

        it = GameItems.FindItem(s);
        Com.dprintln("using:" + s);
        if (it == null) {
            SV_GAME.PF_cprintf(ent, Defines.PRINT_HIGH, "unknown item: " + s
                    + "\n");
            return;
        }
        if (it.use == null) {
            SV_GAME.PF_cprintf(ent, Defines.PRINT_HIGH,
                            "Item is not usable.\n");
            return;
        }
        index = GameItems.ITEM_INDEX(it);
        if (0 == ent.client.pers.inventory[index]) {
            SV_GAME.PF_cprintf(ent, Defines.PRINT_HIGH, "Out of item: " + s
                    + "\n");
            return;
        }

        it.use.use(ent, it);
    }

    /*
     * ================== 
     * Cmd_Drop_f
     * 
     * Drop an inventory item 
     * ==================
     */
    public static void Drop_f(edict_t ent) {
        int index;
        gitem_t it;
        String s;

        s = Cmd.Args();
        it = GameItems.FindItem(s);
        if (it == null) {
            SV_GAME.PF_cprintf(ent, Defines.PRINT_HIGH, "unknown item: " + s
                    + "\n");
            return;
        }
        if (it.drop == null) {
            SV_GAME.PF_cprintf(ent, Defines.PRINT_HIGH,
                    "Item is not dropable.\n");
            return;
        }
        index = GameItems.ITEM_INDEX(it);
        if (0 == ent.client.pers.inventory[index]) {
            SV_GAME.PF_cprintf(ent, Defines.PRINT_HIGH, "Out of item: " + s
                    + "\n");
            return;
        }

        it.drop.drop(ent, it);
    }

    /*
     * ================= Cmd_Inven_f =================
     */
    public static void Inven_f(edict_t ent) {
        int i;
        gclient_t cl;

        cl = ent.client;

        cl.showscores = false;
        cl.showhelp = false;

        if (cl.showinventory) {
            cl.showinventory = false;
            return;
        }

        cl.showinventory = true;

        GameBase.gi.WriteByte(Defines.svc_inventory);
        for (i = 0; i < Defines.MAX_ITEMS; i++) {
            GameBase.gi.WriteShort(cl.pers.inventory[i]);
        }
        GameBase.gi.unicast(ent, true);
    }

    /*
     * ================= 
     * 
     * Cmd_InvUse_f 
     * 
     * =================
     */
    public static void InvUse_f(edict_t ent) {
        gitem_t it;

        Cmd.ValidateSelectedItem(ent);

        if (ent.client.pers.selected_item == -1) {
            SV_GAME.PF_cprintf(ent, Defines.PRINT_HIGH, "No item to use.\n");
            return;
        }

        it = GameItems.itemlist[ent.client.pers.selected_item];
        if (it.use == null) {
            SV_GAME
                    .PF_cprintf(ent, Defines.PRINT_HIGH,
                            "Item is not usable.\n");
            return;
        }
        it.use.use(ent, it);
    }

    /*
     * ================= 
     * 
     * Cmd_WeapPrev_f 
     * 
     * =================
     */
    public static void WeapPrev_f(edict_t ent) {
        gclient_t cl;
        int i, index;
        gitem_t it;
        int selected_weapon;

        cl = ent.client;

        if (cl.pers.weapon == null)
            return;

        selected_weapon = GameItems.ITEM_INDEX(cl.pers.weapon);

        // scan for the next valid one
        for (i = 1; i <= Defines.MAX_ITEMS; i++) {
            index = (selected_weapon + i) % Defines.MAX_ITEMS;
            if (0 == cl.pers.inventory[index])
                continue;

            it = GameItems.itemlist[index];
            if (it.use == null)
                continue;

            if (0 == (it.flags & Defines.IT_WEAPON))
                continue;
            it.use.use(ent, it);
            if (cl.pers.weapon == it)
                return; // successful
        }
    }

    /*
     * ================= 
     * 
     * Cmd_WeapNext_f 
     * 
     * =================
     */
    public static void WeapNext_f(edict_t ent) {
        gclient_t cl;
        int i, index;
        gitem_t it;
        int selected_weapon;

        cl = ent.client;

        if (null == cl.pers.weapon)
            return;

        selected_weapon = GameItems.ITEM_INDEX(cl.pers.weapon);

        // scan for the next valid one
        for (i = 1; i <= Defines.MAX_ITEMS; i++) {
            index = (selected_weapon + Defines.MAX_ITEMS - i)
                    % Defines.MAX_ITEMS;
            //bugfix rst
            if (index == 0)
                index++;
            if (0 == cl.pers.inventory[index])
                continue;
            it = GameItems.itemlist[index];
            if (null == it.use)
                continue;
            if (0 == (it.flags & Defines.IT_WEAPON))
                continue;
            it.use.use(ent, it);
            if (cl.pers.weapon == it)
                return; // successful
        }
    }

    /*
     * ================= 
     * 
     * Cmd_WeapLast_f 
     * 
     * =================
     */
    public static void WeapLast_f(edict_t ent) {
        gclient_t cl;
        int index;
        gitem_t it;

        cl = ent.client;

        if (null == cl.pers.weapon || null == cl.pers.lastweapon)
            return;

        index = GameItems.ITEM_INDEX(cl.pers.lastweapon);
        if (0 == cl.pers.inventory[index])
            return;
        it = GameItems.itemlist[index];
        if (null == it.use)
            return;
        if (0 == (it.flags & Defines.IT_WEAPON))
            return;
        it.use.use(ent, it);
    }

    /*
     * ================= 
     * 
     * Cmd_InvDrop_f 
     * 
     * =================
     */
    public static void InvDrop_f(edict_t ent) {
        gitem_t it;

        Cmd.ValidateSelectedItem(ent);

        if (ent.client.pers.selected_item == -1) {
            SV_GAME.PF_cprintf(ent, Defines.PRINT_HIGH, "No item to drop.\n");
            return;
        }

        it = GameItems.itemlist[ent.client.pers.selected_item];
        if (it.drop == null) {
            SV_GAME.PF_cprintf(ent, Defines.PRINT_HIGH,
                    "Item is not dropable.\n");
            return;
        }
        it.drop.drop(ent, it);
    }

    /*
     * ================== 
     * Cmd_Score_f
     * 
     * Display the scoreboard 
     * 
     * ==================
     */
    public static void Score_f(edict_t ent) {
        ent.client.showinventory = false;
        ent.client.showhelp = false;

        if (0 == GameBase.deathmatch.value && 0 == GameBase.coop.value)
            return;

        if (ent.client.showscores) {
            ent.client.showscores = false;
            return;
        }

        ent.client.showscores = true;
        PlayerHud.DeathmatchScoreboard(ent);
    }

    /*
     * ================== 
     * Cmd_Help_f
     * 
     * Display the current help message 
     * ==================
     */
    public static void Help_f(edict_t ent) {
        // this is for backwards compatability
        if (GameBase.deathmatch.value != 0) {
            Score_f(ent);
            return;
        }

        ent.client.showinventory = false;
        ent.client.showscores = false;

        if (ent.client.showhelp
                && (ent.client.pers.game_helpchanged == GameBase.game.helpchanged)) {
            ent.client.showhelp = false;
            return;
        }

        ent.client.showhelp = true;
        ent.client.pers.helpchanged = 0;
        PlayerHud.HelpComputer(ent);
    }

    //=======================================================================

    /*
     * ================= Cmd_Kill_f =================
     */
    public static void Kill_f(edict_t ent) {
        if ((GameBase.level.time - ent.client.respawn_time) < 5)
            return;
        ent.flags &= ~Defines.FL_GODMODE;
        ent.health = 0;
        GameBase.meansOfDeath = Defines.MOD_SUICIDE;
        PlayerClient.player_die.die(ent, ent, ent, 100000, Globals.vec3_origin);
    }

    /*
     * ================= Cmd_PutAway_f =================
     */
    public static void PutAway_f(edict_t ent) {
        ent.client.showscores = false;
        ent.client.showhelp = false;
        ent.client.showinventory = false;
    }

    /*
     * ================= Cmd_Players_f =================
     */
    public static void Players_f(edict_t ent) {
        int i;
        int count;
        String small;
        String large;

        Integer index[] = new Integer[256];

        count = 0;
        for (i = 0; i < GameBase.maxclients.value; i++) {
            if (GameBase.game.clients[i].pers.connected) {
                index[count] = new Integer(i);
                count++;
            }
        }

        // sort by frags
        //qsort(index, count, sizeof(index[0]), PlayerSort);
        //replaced by:
        Arrays.sort(index, 0, count - 1, Cmd.PlayerSort);

        // print information
        large = "";

        for (i = 0; i < count; i++) {
            small = GameBase.game.clients[index[i].intValue()].ps.stats[Defines.STAT_FRAGS]
                    + " "
                    + GameBase.game.clients[index[i].intValue()].pers.netname
                    + "\n";

            if (small.length() + large.length() > 1024 - 100) {
                // can't print all of them in one packet
                large += "...\n";
                break;
            }
            large += small;
        }

        SV_GAME.PF_cprintf(ent, Defines.PRINT_HIGH, "" + large + "\n" + count
                + " players\n");
    }

    /*
     * ================= Cmd_Wave_f =================
     */
    public static void Wave_f(edict_t ent) {
        int i;

        i = Lib.atoi(Cmd.Argv(1));

        // can't wave when ducked
        if ((ent.client.ps.pmove.pm_flags & pmove_t.PMF_DUCKED) != 0)
            return;

        if (ent.client.anim_priority > Defines.ANIM_WAVE)
            return;

        ent.client.anim_priority = Defines.ANIM_WAVE;

        switch (i) {
        case 0:
            SV_GAME.PF_cprintf(ent, Defines.PRINT_HIGH, "flipoff\n");
            ent.s.frame = M_Player.FRAME_flip01 - 1;
            ent.client.anim_end = M_Player.FRAME_flip12;
            break;
        case 1:
            SV_GAME.PF_cprintf(ent, Defines.PRINT_HIGH, "salute\n");
            ent.s.frame = M_Player.FRAME_salute01 - 1;
            ent.client.anim_end = M_Player.FRAME_salute11;
            break;
        case 2:
            SV_GAME.PF_cprintf(ent, Defines.PRINT_HIGH, "taunt\n");
            ent.s.frame = M_Player.FRAME_taunt01 - 1;
            ent.client.anim_end = M_Player.FRAME_taunt17;
            break;
        case 3:
            SV_GAME.PF_cprintf(ent, Defines.PRINT_HIGH, "wave\n");
            ent.s.frame = M_Player.FRAME_wave01 - 1;
            ent.client.anim_end = M_Player.FRAME_wave11;
            break;
        case 4:
        default:
            SV_GAME.PF_cprintf(ent, Defines.PRINT_HIGH, "point\n");
            ent.s.frame = M_Player.FRAME_point01 - 1;
            ent.client.anim_end = M_Player.FRAME_point12;
            break;
        }
    }

    /*
     * ================== Cmd_Say_f ==================
     */
    public static void Say_f(edict_t ent, boolean team, boolean arg0) {

        int i, j;
        edict_t other;
        String text;
        gclient_t cl;

        if (Cmd.Argc() < 2 && !arg0)
            return;

        if (0 == ((int) (GameBase.dmflags.value) & (Defines.DF_MODELTEAMS | Defines.DF_SKINTEAMS)))
            team = false;

        if (team)
            text = "(" + ent.client.pers.netname + "): ";
        else
            text = "" + ent.client.pers.netname + ": ";

        if (arg0) {
            text += Cmd.Argv(0);
            text += " ";
            text += Cmd.Args();
        } else {
            if (Cmd.Args().startsWith("\""))
                text += Cmd.Args().substring(1, Cmd.Args().length() - 1);
            else
                text += Cmd.Args();
            /*
             * p = gi.args(); // *p == if (p == '"') { p++; p[strlen(p) - 1] =
             * 0; } strcat(text, p);
             */
        }

        // don't let text be too long for malicious reasons
        if (text.length() > 150)
            //text[150] = 0;
            text = text.substring(0, 150);

        text += "\n";

        if (GameBase.flood_msgs.value != 0) {
            cl = ent.client;

            if (GameBase.level.time < cl.flood_locktill) {
                SV_GAME
                        .PF_cprintf(
                                ent,
                                Defines.PRINT_HIGH,
                                "You can't talk for "
                                        + (int) (cl.flood_locktill - GameBase.level.time)
                                        + " more seconds\n");
                return;
            }
            i = (int) (cl.flood_whenhead - GameBase.flood_msgs.value + 1);
            if (i < 0)
                //i = (sizeof(cl.flood_when) / sizeof(cl.flood_when[0])) + i;
                i = (10) + i;
            if (cl.flood_when[i] != 0
                    && GameBase.level.time - cl.flood_when[i] < GameBase.flood_persecond.value) {
                cl.flood_locktill = GameBase.level.time
                        + GameBase.flood_waitdelay.value;
                SV_GAME.PF_cprintf(ent, Defines.PRINT_CHAT,
                        "Flood protection:  You can't talk for "
                                + (int) GameBase.flood_waitdelay.value
                                + " seconds.\n");
                return;
            }
            //cl.flood_whenhead = (cl.flood_whenhead + 1) %
            // (sizeof(cl.flood_when) / sizeof(cl.flood_when[0]));
            cl.flood_whenhead = (cl.flood_whenhead + 1) % 10;
            cl.flood_when[cl.flood_whenhead] = GameBase.level.time;
        }

        if (Globals.dedicated.value != 0)
            SV_GAME.PF_cprintf(null, Defines.PRINT_CHAT, "" + text + "");

        for (j = 1; j <= GameBase.game.maxclients; j++) {
            other = GameBase.g_edicts[j];
            if (!other.inuse)
                continue;
            if (other.client == null)
                continue;
            if (team) {
                if (!GameUtil.OnSameTeam(ent, other))
                    continue;
            }
            SV_GAME.PF_cprintf(other, Defines.PRINT_CHAT, "" + text + "");
        }

    }

    /**
     * Returns the playerlist. TODO: The list is badly formatted at the moment.
     */
    public static void PlayerList_f(edict_t ent) {
        int i;
        String st;
        String text;
        edict_t e2;

        // connect time, ping, score, name
        text = "";

        for (i = 0; i < GameBase.maxclients.value; i++) {
            e2 = GameBase.g_edicts[1 + i];
            if (!e2.inuse)
                continue;

            st = ""
                    + (GameBase.level.framenum - e2.client.resp.enterframe)
                    / 600
                    + ":"
                    + ((GameBase.level.framenum - e2.client.resp.enterframe) % 600)
                    / 10 + " " + e2.client.ping + " " + e2.client.resp.score
                    + " " + e2.client.pers.netname + " "
                    + (e2.client.resp.spectator ? " (spectator)" : "") + "\n";

            if (text.length() + st.length() > 1024 - 50) {
                text += "And more...\n";
                SV_GAME.PF_cprintf(ent, Defines.PRINT_HIGH, "" + text + "");
                return;
            }
            text += st;
        }
        SV_GAME.PF_cprintf(ent, Defines.PRINT_HIGH, text);
    }

    //	  ======================================================================

    /*
     * =================== Cmd_ForwardToServer
     * 
     * adds the current command line as a clc_stringcmd to the client message.
     * things like godmode, noclip, etc, are commands directed to the server, so
     * when they are typed in at the console, they will need to be forwarded.
     * ===================
     */
    public static void ForwardToServer() {
        String cmd;

        cmd = Cmd.Argv(0);
        if (Globals.cls.state <= Defines.ca_connected || cmd.charAt(0) == '-'
                || cmd.charAt(0) == '+') {
            Com.Printf("Unknown command \"" + cmd + "\"\n");
            return;
        }

        MSG.WriteByte(Globals.cls.netchan.message, Defines.clc_stringcmd);
        SZ.Print(Globals.cls.netchan.message, cmd);
        if (Cmd.Argc() > 1) {
            SZ.Print(Globals.cls.netchan.message, " ");
            SZ.Print(Globals.cls.netchan.message, Cmd.Args());
        }
    }

    /*
     * ============ Cmd_CompleteCommand ============
     */
    public static Vector CompleteCommand(String partial) {
        Vector cmds = new Vector();

        // check for match
        for (cmd_function_t cmd = cmd_functions; cmd != null; cmd = cmd.next)
            if (cmd.name.startsWith(partial))
                cmds.add(cmd.name);
        for (cmdalias_t a = Globals.cmd_alias; a != null; a = a.next)
            if (a.name.startsWith(partial))
                cmds.add(a.name);

        return cmds;
    }

    /**
     * Processes the commands the player enters in the quake console.
     *  
     */
    public static void ClientCommand(edict_t ent) {
        String cmd;
    
        if (ent.client == null)
            return; // not fully in game yet
    
        cmd = GameBase.gi.argv(0);
    
        if (Lib.Q_stricmp(cmd, "players") == 0) {
            Players_f(ent);
            return;
        }
        if (Lib.Q_stricmp(cmd, "say") == 0) {
            Say_f(ent, false, false);
            return;
        }
        if (Lib.Q_stricmp(cmd, "say_team") == 0) {
            Say_f(ent, true, false);
            return;
        }
        if (Lib.Q_stricmp(cmd, "score") == 0) {
            Score_f(ent);
            return;
        }
        if (Lib.Q_stricmp(cmd, "help") == 0) {
            Help_f(ent);
            return;
        }
    
        if (GameBase.level.intermissiontime != 0)
            return;
    
        if (Lib.Q_stricmp(cmd, "use") == 0)
            Use_f(ent);
    
        else if (Lib.Q_stricmp(cmd, "drop") == 0)
            Drop_f(ent);
        else if (Lib.Q_stricmp(cmd, "give") == 0)
            Give_f(ent);
        else if (Lib.Q_stricmp(cmd, "god") == 0)
            God_f(ent);
        else if (Lib.Q_stricmp(cmd, "notarget") == 0)
            Notarget_f(ent);
        else if (Lib.Q_stricmp(cmd, "noclip") == 0)
            Noclip_f(ent);
        else if (Lib.Q_stricmp(cmd, "inven") == 0)
            Inven_f(ent);
        else if (Lib.Q_stricmp(cmd, "invnext") == 0)
            GameItems.SelectNextItem(ent, -1);
        else if (Lib.Q_stricmp(cmd, "invprev") == 0)
            GameItems.SelectPrevItem(ent, -1);
        else if (Lib.Q_stricmp(cmd, "invnextw") == 0)
            GameItems.SelectNextItem(ent, Defines.IT_WEAPON);
        else if (Lib.Q_stricmp(cmd, "invprevw") == 0)
            GameItems.SelectPrevItem(ent, Defines.IT_WEAPON);
        else if (Lib.Q_stricmp(cmd, "invnextp") == 0)
            GameItems.SelectNextItem(ent, Defines.IT_POWERUP);
        else if (Lib.Q_stricmp(cmd, "invprevp") == 0)
            GameItems.SelectPrevItem(ent, Defines.IT_POWERUP);
        else if (Lib.Q_stricmp(cmd, "invuse") == 0)
            InvUse_f(ent);
        else if (Lib.Q_stricmp(cmd, "invdrop") == 0)
            InvDrop_f(ent);
        else if (Lib.Q_stricmp(cmd, "weapprev") == 0)
            WeapPrev_f(ent);
        else if (Lib.Q_stricmp(cmd, "weapnext") == 0)
            WeapNext_f(ent);
        else if (Lib.Q_stricmp(cmd, "weaplast") == 0)
            WeapLast_f(ent);
        else if (Lib.Q_stricmp(cmd, "kill") == 0)
            Kill_f(ent);
        else if (Lib.Q_stricmp(cmd, "putaway") == 0)
            PutAway_f(ent);
        else if (Lib.Q_stricmp(cmd, "wave") == 0)
            Wave_f(ent);
        else if (Lib.Q_stricmp(cmd, "playerlist") == 0)
            PlayerList_f(ent);
        else
            // anything that doesn't match a command will be a chat
            Say_f(ent, false, true);
    }

    public static void ValidateSelectedItem(edict_t ent) {
        gclient_t cl;
    
        cl = ent.client;
    
        if (cl.pers.inventory[cl.pers.selected_item] != 0)
            return; // valid
    
        GameItems.SelectNextItem(ent, -1);
    }
}