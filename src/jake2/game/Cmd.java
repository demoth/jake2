/*
 * Cmd.java
 * Copyright (C) 2003
 * 
 * $Id: Cmd.java,v 1.18 2006-01-21 21:53:32 salomo Exp $
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

import jake2.game.monsters.M_Player;
import jake2.qcommon.*;
import jake2.qcommon.filesystem.FS;
import jake2.qcommon.util.Lib;
import jake2.server.SV_GAME;

import java.util.*;

/**
 * Cmd
 */
public final class Cmd {

    private static Command List_f = (List<String> args) -> {

        for (cmd_function_t cmd : Cmd.cmd_functions.values()) {
            Com.Printf(cmd.name + '\n');
        }

        Com.Printf(Cmd.cmd_functions.size() + " commands\n");
    };

    private static Command Exec_f = (List<String> args) -> {
        if (args.size() != 2) {
            Com.Printf("exec <filename> : execute a script file\n");
            return;
        }

        byte[] f = FS.LoadFile(args.get(1));
        if (f == null) {
            Com.Printf("couldn't exec " + args.get(1) + "\n");
            return;
        }
        Com.Printf("execing " + args.get(1) + "\n");

        Cbuf.InsertText(new String(f));

        FS.FreeFile(f);
    };

    private static Command Echo_f = (List<String> args) -> {
        if (args.size() < 2) {
            Com.Printf("usage: echo expression");
            return;
        }
        for (int i = 1; i < args.size(); i++) {
            Com.Printf(args.get(i) + " ");
        }
        Com.Printf("'\n");
    };

    private static Command Alias_f = (List<String> args) -> {
        if (args.size() == 1) {
            Com.Printf("Current alias commands:\n");
            for (cmdalias_t alias : Cmd.cmd_alias.values()) {
                Com.Printf(alias.getName() + " : " + alias.getValue() + "\n");
            }
            return;
        }

        // assume args.size >= 2
        String aliasName = args.get(1).trim();

        if (aliasName.length() > Defines.MAX_ALIAS_NAME) {
            Com.Printf("Alias name is too long\n");
            return;
        }

        // if the alias already exists, reuse it
        cmdalias_t alias = Cmd.cmd_alias.get(aliasName);

        if (alias == null) {
            alias = new cmdalias_t(aliasName);
            Cmd.cmd_alias.put(aliasName, alias);
        }

        if (args.size() >= 3) {
            alias.setValue(getArguments(args, 2));
        } else {
            alias.setValue("");
        }
    };

    private static Command Wait_f = (List<String> args) -> Globals.cmd_wait = true;

    private static Map<String, cmd_function_t> cmd_functions = new HashMap<>();

    private static Map<String, cmdalias_t> cmd_alias = new HashMap<>();

    private static final int ALIAS_LOOP_COUNT = 16;

    /**
     * Register our commands.
     */
    public static void Init() {

        Cmd.AddCommand("exec", Exec_f);
        Cmd.AddCommand("echo", Echo_f);
        Cmd.AddCommand("cmdlist", List_f);
        Cmd.AddCommand("alias", Alias_f);
        Cmd.AddCommand("wait", Wait_f);
    }

    private static Comparator PlayerSort = (o1, o2) -> {
        int anum = ((Integer) o1).intValue();
        int bnum = ((Integer) o2).intValue();

        int anum1 = GameBase.game.clients[anum].ps.stats[Defines.STAT_FRAGS];
        int bnum1 = GameBase.game.clients[bnum].ps.stats[Defines.STAT_FRAGS];

        if (anum1 < bnum1)
            return -1;
        if (anum1 > bnum1)
            return 1;
        return 0;
    };

    /** 
     * Cmd_MacroExpandString.
     */
    public static String MacroExpandString(String text) {

        boolean inquote = false;

        if (text.length() >= Defines.MAX_STRING_CHARS) {
            Com.Printf("Line exceeded " + Defines.MAX_STRING_CHARS
                    + " chars, discarded.\n");
            return null;
        }

        int expanded = 0;
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '"')
                inquote = !inquote;

            if (inquote) {
                result.append(text.charAt(i));
                continue; // don't expand inside quotes
            }

            if (text.charAt(i) != '$') {
                result.append(text.charAt(i));
                continue;
            }

            // scan out the complete macro, without $
            String token = Com.Parse(new Com.ParseHelp(text, i + 1));

            i += token.length();
            token = Cvar.VariableString(token);
            result.append(token);

            if (result.length() >= Defines.MAX_STRING_CHARS) {
                Com.Printf("Expanded line exceeded " + Defines.MAX_STRING_CHARS
                        + " chars, discarded.\n");
                return null;
            }

            if (++expanded == 100) {
                Com.Printf("Macro expansion loop, discarded.\n");
                return null;
            }
        }

        if (inquote) {
            Com.Printf("Line has unmatched quote, discarded.\n");
            return null;
        }

        return result.toString();
    }

    /**
     * Cmd_TokenizeString
     * 
     * Parses the given string into command line tokens. $Cvars will be expanded
     * unless they are in a quoted token.
     * TODO should return the tokens and not assign to static fields!
     */
    public static List<String> TokenizeString(String text, boolean macroExpand) {
        List<String> result = new ArrayList<>();

        // macro expand the text
        if (macroExpand)
            text = MacroExpandString(text);

        if (text == null || text.isEmpty())
            return result;

        Com.ParseHelp ph = new Com.ParseHelp(text);

        while (true) {

            // skip whitespace up to a \n
            char c = ph.skipwhitestoeol();

            if (c == '\n') { // a newline separates commands in the buffer
                c = ph.nextchar();
                break;
            }

            if (c == 0)
                return result;

            String word = Com.Parse(ph);

            result.add(word);

        }
        return result;
    }

    public static void AddCommand(String cmd_name, Command function) {
        // fail if the command is a variable name
        if ((Cvar.VariableString(cmd_name)).length() > 0) {
            Com.Printf("Cmd_AddCommand: " + cmd_name + " already defined as a var\n");
            return;
        }

        // fail if the command already exists
        if (cmd_functions.containsKey(cmd_name)) {
            Com.Printf("Cmd_AddCommand: " + cmd_name + " already defined\n");
            return;
        }

        cmd_functions.put(cmd_name, new cmd_function_t(cmd_name, function));
    }

    /**
     * Cmd_RemoveCommand 
     */
    public static void RemoveCommand(String cmd_name) {
        if (!cmd_functions.containsKey(cmd_name)) {
            Com.Printf("RemoveCommand: " + cmd_name + " does not exist\n");
        } else {
            Com.Printf("RemoveCommand: " + cmd_name + " removed\n");
            cmd_functions.remove(cmd_name);
        }
    }

    public static String getArguments(List<String> args, int startIndex) {
        if (args.isEmpty() || startIndex >= args.size())
            return "";

        StringBuilder value = new StringBuilder();
        for (int i = startIndex; i < args.size(); i++) {
            if (i != startIndex)
                value.append(" ");
            value.append(args.get(i));
        }
        return value.toString();
    }

    public static String getArguments(List<String> args) {
        return getArguments(args, 1);
    }

    /**
     * Cmd_ExecuteString
     * 
     * A complete command line has been parsed, so try to execute it 
     * FIXME: lookupnoadd the token to speed search? 
     */
    public static void ExecuteString(String text) {
        if (text.trim().startsWith("//"))
            return;

        List<String> args = TokenizeString(text, true);

        // execute the command line
        if (args.size() == 0)
            return; // no tokens

        // check functions
        cmd_function_t cmd = cmd_functions.get(args.get(0));
        if (cmd != null) {
            if (cmd.function != null) {
                // todo pass arguments to execute instead of using Cmd.Argc()
                cmd.function.execute(args);
            } else { // forward to server command
                Cmd.ExecuteString("cmd " + text);
            }
            return;
        }

        // check alias
        for (cmdalias_t alias : cmd_alias.values()) {
            if (args.get(0).equalsIgnoreCase(alias.getName())) {
                if (++Globals.alias_count == ALIAS_LOOP_COUNT) {
                    Com.Printf("ALIAS_LOOP_COUNT\n");
                    return;
                }
                Cbuf.InsertText(alias.getValue());
                return;
            }
        }

        // check cvars
        if (Cvar.printOrSet(args))
            return;

        // send it as a server command if we are connected
        Cmd.ForwardToServer(args);
    }

    /**
     * Cmd_Give_f
     * 
     * Give items to a client.
     */
    private static void Give_f(edict_t ent, List<String> args) {
        String name;
        gitem_t it;
        int index;
        int i;
        boolean give_all;
        edict_t it_ent;

        if (GameBase.deathmatch.value != 0 && GameBase.sv_cheats.value == 0) {
            SV_GAME.PF_cprintfhigh(ent,
            	"You must run the server with '+set cheats 1' to enable this command.\n");
            return;
        }

        name = getArguments(args);

        give_all = 0 == Lib.Q_stricmp(name, "all");

        if (give_all || 0 == Lib.Q_stricmp(args.get(1), "health")) {
            if (args.size() == 3)
                ent.health = Lib.atoi(args.get(2));
            else
                ent.health = ent.max_health;
            if (!give_all)
                return;
        }

        if (give_all || 0 == Lib.Q_stricmp(name, "weapons")) {
            for (i = 1; i < GameBase.game.num_items; i++) {
                it = GameItemList.itemlist[i];
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
                it = GameItemList.itemlist[i];
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
                it = GameItemList.itemlist[i];
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
            name = args.get(1);
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
            if (args.size() == 3)
                ent.client.pers.inventory[index] = Lib.atoi(args.get(2));
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

    /** 
     * Cmd_God_f
     * 
     * Sets client to godmode
     * 
     * argv(0) god
     */
    private static void God_f(edict_t ent) {
        String msg;

        if (GameBase.deathmatch.value != 0 && GameBase.sv_cheats.value == 0) {
            SV_GAME.PF_cprintfhigh(ent,
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

    /** 
     * Cmd_Notarget_f
     * 
     * Sets client to notarget
     * 
     * argv(0) notarget.
     */
    private static void Notarget_f(edict_t ent) {
        String msg;

        if (GameBase.deathmatch.value != 0 && GameBase.sv_cheats.value == 0) {
            SV_GAME.PF_cprintfhigh(ent, 
            	"You must run the server with '+set cheats 1' to enable this command.\n");
            return;
        }

        ent.flags ^= Defines.FL_NOTARGET;
        if (0 == (ent.flags & Defines.FL_NOTARGET))
            msg = "notarget OFF\n";
        else
            msg = "notarget ON\n";

        SV_GAME.PF_cprintfhigh(ent, msg);
    }

    /**
     * Cmd_Noclip_f
     * 
     * argv(0) noclip.
     */
    private static void Noclip_f(edict_t ent) {
        String msg;

        if (GameBase.deathmatch.value != 0 && GameBase.sv_cheats.value == 0) {
            SV_GAME.PF_cprintfhigh(ent, 
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

        SV_GAME.PF_cprintfhigh(ent, msg);
    }

    /**
     * Cmd_Use_f
     * 
     * Use an inventory item.
     */
    private static void Use_f(edict_t ent, List<String> args) {

        String itemName = getArguments(args);

        gitem_t it = GameItems.FindItem(itemName);
        Com.dprintln("using:" + itemName);
        if (it == null) {
            SV_GAME.PF_cprintfhigh(ent, "unknown item: " + itemName + "\n");
            return;
        }
        if (it.use == null) {
            SV_GAME.PF_cprintfhigh(ent, "Item is not usable.\n");
            return;
        }
        int index = GameItems.ITEM_INDEX(it);
        if (0 == ent.client.pers.inventory[index]) {
            SV_GAME.PF_cprintfhigh(ent, "Out of item: " + itemName + "\n");
            return;
        }

        it.use.use(ent, it);
    }

    /**
     * Cmd_Drop_f
     * 
     * Drop an inventory item.
     */
    private static void Drop_f(edict_t ent, List<String> args) {

        String itemName = getArguments(args);
        gitem_t it = GameItems.FindItem(itemName);
        if (it == null) {
            SV_GAME.PF_cprintfhigh(ent, "unknown item: " + itemName + "\n");
            return;
        }
        if (it.drop == null) {
            SV_GAME.PF_cprintf(ent, Defines.PRINT_HIGH,
                    "Item is not dropable.\n");
            return;
        }
        int index = GameItems.ITEM_INDEX(it);
        if (0 == ent.client.pers.inventory[index]) {
            SV_GAME.PF_cprintfhigh(ent, "Out of item: " + itemName + "\n");
            return;
        }

        it.drop.drop(ent, it);
    }

    /**
     * Cmd_Inven_f.
     */
    private static void Inven_f(edict_t ent) {
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

    /**
     * Cmd_InvUse_f.
     */
    private static void InvUse_f(edict_t ent) {
        gitem_t it;

        Cmd.ValidateSelectedItem(ent);

        if (ent.client.pers.selected_item == -1) {
            SV_GAME.PF_cprintfhigh(ent, "No item to use.\n");
            return;
        }

        it = GameItemList.itemlist[ent.client.pers.selected_item];
        if (it.use == null) {
            SV_GAME.PF_cprintfhigh(ent, "Item is not usable.\n");
            return;
        }
        it.use.use(ent, it);
    }

    /**
     * Cmd_WeapPrev_f.
     */
    private static void WeapPrev_f(edict_t ent) {
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

            it = GameItemList.itemlist[index];
            if (it.use == null)
                continue;

            if (0 == (it.flags & Defines.IT_WEAPON))
                continue;
            it.use.use(ent, it);
            if (cl.pers.weapon == it)
                return; // successful
        }
    }

    /**
     * Cmd_WeapNext_f.
     */
    private static void WeapNext_f(edict_t ent) {
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
            it = GameItemList.itemlist[index];
            if (null == it.use)
                continue;
            if (0 == (it.flags & Defines.IT_WEAPON))
                continue;
            it.use.use(ent, it);
            if (cl.pers.weapon == it)
                return; // successful
        }
    }

    /** 
     * Cmd_WeapLast_f.
     */
    private static void WeapLast_f(edict_t ent) {
        gclient_t cl;
        int index;
        gitem_t it;

        cl = ent.client;

        if (null == cl.pers.weapon || null == cl.pers.lastweapon)
            return;

        index = GameItems.ITEM_INDEX(cl.pers.lastweapon);
        if (0 == cl.pers.inventory[index])
            return;
        it = GameItemList.itemlist[index];
        if (null == it.use)
            return;
        if (0 == (it.flags & Defines.IT_WEAPON))
            return;
        it.use.use(ent, it);
    }

    /**
     * Cmd_InvDrop_f 
     */
    private static void InvDrop_f(edict_t ent) {
        gitem_t it;

        Cmd.ValidateSelectedItem(ent);

        if (ent.client.pers.selected_item == -1) {
            SV_GAME.PF_cprintfhigh(ent, "No item to drop.\n");
            return;
        }

        it = GameItemList.itemlist[ent.client.pers.selected_item];
        if (it.drop == null) {
            SV_GAME.PF_cprintfhigh(ent, "Item is not dropable.\n");
            return;
        }
        it.drop.drop(ent, it);
    }

    /** 
     * Cmd_Score_f
     * 
     * Display the scoreboard.
     * 
     */
    private static void Score_f(edict_t ent) {
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

    /**
     * Cmd_Help_f
     * 
     * Display the current help message. 
     *
     */
    static void Help_f(edict_t ent) {
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

    /**
     * Cmd_Kill_f
     */
    private static void Kill_f(edict_t ent) {
        if ((GameBase.level.time - ent.client.respawn_time) < 5)
            return;
        ent.flags &= ~Defines.FL_GODMODE;
        ent.health = 0;
        GameBase.meansOfDeath = Defines.MOD_SUICIDE;
        PlayerClient.player_die.die(ent, ent, ent, 100000, Globals.vec3_origin);
    }

    /**
     * Cmd_PutAway_f
     */
    private static void PutAway_f(edict_t ent) {
        ent.client.showscores = false;
        ent.client.showhelp = false;
        ent.client.showinventory = false;
    }

    /**
     * Cmd_Players_f
     */
    private static void Players_f(edict_t ent) {
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

        SV_GAME.PF_cprintfhigh(ent, large + "\n" + count + " players\n");
    }

    /**
     * Cmd_Wave_f
     */
    private static void Wave_f(edict_t ent, List<String> args) {


        // can't wave when ducked
        if ((ent.client.ps.pmove.pm_flags & pmove_t.PMF_DUCKED) != 0)
            return;

        if (ent.client.anim_priority > Defines.ANIM_WAVE)
            return;

        ent.client.anim_priority = Defines.ANIM_WAVE;

        int type = args.size() != 2 ? 0 : Lib.atoi(args.get(1));

        switch (type) {
        case 0:
            SV_GAME.PF_cprintfhigh(ent, "flipoff\n");
            ent.s.frame = M_Player.FRAME_flip01 - 1;
            ent.client.anim_end = M_Player.FRAME_flip12;
            break;
        case 1:
            SV_GAME.PF_cprintfhigh(ent, "salute\n");
            ent.s.frame = M_Player.FRAME_salute01 - 1;
            ent.client.anim_end = M_Player.FRAME_salute11;
            break;
        case 2:
            SV_GAME.PF_cprintfhigh(ent, "taunt\n");
            ent.s.frame = M_Player.FRAME_taunt01 - 1;
            ent.client.anim_end = M_Player.FRAME_taunt17;
            break;
        case 3:
            SV_GAME.PF_cprintfhigh(ent, "wave\n");
            ent.s.frame = M_Player.FRAME_wave01 - 1;
            ent.client.anim_end = M_Player.FRAME_wave11;
            break;
        case 4:
        default:
            SV_GAME.PF_cprintfhigh(ent, "point\n");
            ent.s.frame = M_Player.FRAME_point01 - 1;
            ent.client.anim_end = M_Player.FRAME_point12;
            break;
        }
    }

    /**
     * Command to print the players own position.
     */
    private static void ShowPosition_f(edict_t ent) {
        SV_GAME.PF_cprintfhigh(ent, "pos=" + Lib.vtofsbeaty(ent.s.origin) + "\n");
    }

    /**
     * Cmd_Say_f
     */
    private static void Say_f(edict_t ent, boolean team, boolean sayAll, List<String> args) {

        int i, j;
        edict_t other;
        String text;
        gclient_t cl;

        if (args.size() < 2 && !sayAll)
            return;

        if (0 == ((int) (GameBase.dmflags.value) & (Defines.DF_MODELTEAMS | Defines.DF_SKINTEAMS)))
            team = false;

        if (team)
            text = "(" + ent.client.pers.netname + "): ";
        else
            text = "" + ent.client.pers.netname + ": ";

        String arguments = getArguments(args);

        if (sayAll) {
            text += args.get(0);
            text += " ";
            text += arguments;
        } else {
            // FIXME: we assume that the matching quote is at the end
            if (arguments.startsWith("\""))
                text += arguments.substring(1, arguments.length() - 1);
            else
                text += arguments;
        }

        // don't let text be too long for malicious reasons
        if (text.length() > 150)
            //text[150] = 0;
            text = text.substring(0, 150);

        text += "\n";

        if (GameBase.flood_msgs.value != 0) {
            cl = ent.client;

            if (GameBase.level.time < cl.flood_locktill) {
                SV_GAME.PF_cprintfhigh(ent, "You can't talk for "
                                        + (int) (cl.flood_locktill - GameBase.level.time)
                                        + " more seconds\n");
                return;
            }
            i = (int) (cl.flood_whenhead - GameBase.flood_msgs.value + 1);
            if (i < 0)
                i = (10) + i;
            if (cl.flood_when[i] != 0
                    && GameBase.level.time - cl.flood_when[i] < GameBase.flood_persecond.value) {
                cl.flood_locktill = GameBase.level.time + GameBase.flood_waitdelay.value;
                SV_GAME.PF_cprintf(ent, Defines.PRINT_CHAT,
                        "Flood protection:  You can't talk for "
                                + (int) GameBase.flood_waitdelay.value
                                + " seconds.\n");
                return;
            }

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
    private static void PlayerList_f(edict_t ent) {
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
                SV_GAME.PF_cprintfhigh(ent, "" + text + "");
                return;
            }
            text += st;
        }
        SV_GAME.PF_cprintfhigh(ent, text);
    }

    /**
     * Adds the current command line as a clc_stringcmd to the client message.
     * things like godmode, noclip, etc, are commands directed to the server, so
     * when they are typed in at the console, they will need to be forwarded.
     */
    private static void ForwardToServer(List<String> args) {
        String cmd;

        cmd = args.get(0);
        if (Globals.cls.state <= Defines.ca_connected || cmd.charAt(0) == '-'
                || cmd.charAt(0) == '+') {
            Com.Printf("Unknown command \"" + cmd + "\"\n");
            return;
        }

        MSG.WriteByte(Globals.cls.netchan.message, Defines.clc_stringcmd);
        SZ.Print(Globals.cls.netchan.message, cmd);
        if (args.size() > 1) {
            SZ.Print(Globals.cls.netchan.message, " ");
            SZ.Print(Globals.cls.netchan.message, getArguments(args));
        }
    }

    /**
     * Find commands or aliases by prefix
     */
    public static List<String> CompleteCommand(String prefix) {
        List<String> cmds = new ArrayList<>();

        for (cmd_function_t cmd : cmd_functions.values())
            if (cmd.name.startsWith(prefix))
                cmds.add(cmd.name);

        for (cmdalias_t a : cmd_alias.values())
            if (a.getName().startsWith(prefix))
                cmds.add(a.getName());

        return cmds;
    }

    /**
     * Processes the commands the player enters in the quake console.
     */
    public static void ClientCommand(edict_t ent, List<String> args) {
        String cmd;
    
        if (ent.client == null)
            return; // not fully in game yet
    
        cmd = args.get(0).toLowerCase();
    
        if (cmd.equals("players")) {
            Players_f(ent);
            return;
        }
        if (cmd.equals("say")) {
            Say_f(ent, false, false, args);
            return;
        }
        if (cmd.equals("say_team")) {
            Say_f(ent, true, false, args);
            return;
        }
        if (cmd.equals("score")) {
            Score_f(ent);
            return;
        }
        if (cmd.equals("help")) {
            Help_f(ent);
            return;
        }
    
        if (GameBase.level.intermissiontime != 0)
            return;

        switch (cmd) {
            case "use":
                Use_f(ent, args);
                break;
            case "drop":
                Drop_f(ent, args);
                break;
            case "give":
                Give_f(ent, args);
                break;
            case "god":
                God_f(ent);
                break;
            case "notarget":
                Notarget_f(ent);
                break;
            case "noclip":
                Noclip_f(ent);
                break;
            case "inven":
                Inven_f(ent);
                break;
            case "invnext":
                GameItems.SelectNextItem(ent, -1);
                break;
            case "invprev":
                GameItems.SelectPrevItem(ent, -1);
                break;
            case "invnextw":
                GameItems.SelectNextItem(ent, Defines.IT_WEAPON);
                break;
            case "invprevw":
                GameItems.SelectPrevItem(ent, Defines.IT_WEAPON);
                break;
            case "invnextp":
                GameItems.SelectNextItem(ent, Defines.IT_POWERUP);
                break;
            case "invprevp":
                GameItems.SelectPrevItem(ent, Defines.IT_POWERUP);
                break;
            case "invuse":
                InvUse_f(ent);
                break;
            case "invdrop":
                InvDrop_f(ent);
                break;
            case "weapprev":
                WeapPrev_f(ent);
                break;
            case "weapnext":
                WeapNext_f(ent);
                break;
            case "weaplast":
                WeapLast_f(ent);
                break;
            case "kill":
                Kill_f(ent);
                break;
            case "putaway":
                PutAway_f(ent);
                break;
            case "wave":
                Wave_f(ent, args);
                break;
            case "playerlist":
                PlayerList_f(ent);
                break;
            case "showposition":
                ShowPosition_f(ent);
                break;
            default:
                // anything that doesn't match a command will be a chat
                Say_f(ent, false, true, args);
                break;
        }
    }

    static void ValidateSelectedItem(edict_t ent) {
        gclient_t cl = ent.client;
    
        if (cl.pers.inventory[cl.pers.selected_item] != 0)
            return; // valid
    
        GameItems.SelectNextItem(ent, -1);
    }
}