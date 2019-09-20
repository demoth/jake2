package jake2.game;

import jake2.game.monsters.M_Player;
import jake2.qcommon.*;
import jake2.qcommon.network.NetworkCommands;
import jake2.qcommon.util.Lib;
import jake2.qcommon.util.QuakeFile;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class GameExportsImpl implements GameExports {

    private static final String[] preloadclasslist =
            {
                    "jake2.game.PlayerWeapon",
                    "jake2.game.AIAdapter",
                    "jake2.qcommon.Cmd",
                    "jake2.game.EdictFindFilter",
                    "jake2.game.EdictIterator",
                    "jake2.qcommon.EndianHandler",
                    "jake2.game.EntBlockedAdapter",
                    "jake2.game.EntDieAdapter",
                    "jake2.game.EntDodgeAdapter",
                    "jake2.game.EntInteractAdapter",
                    "jake2.game.EntPainAdapter",
                    "jake2.game.EntThinkAdapter",
                    "jake2.game.EntTouchAdapter",
                    "jake2.game.EntUseAdapter",
                    "jake2.game.GameAI",
                    "jake2.game.GameBase",
                    "jake2.game.GameChase",
                    "jake2.game.GameCombat",
                    "jake2.game.GameFunc",
                    "jake2.game.GameMisc",
                    "jake2.game.GameSVCmds",
                    "jake2.game.GameSave",
                    "jake2.game.GameSpawn",
                    "jake2.game.GameTarget",
                    "jake2.game.GameTrigger",
                    "jake2.game.GameTurret",
                    "jake2.game.GameUtil",
                    "jake2.game.GameWeapon",
                    "jake2.qcommon.Info",
                    "jake2.game.ItemDropAdapter",
                    "jake2.game.ItemUseAdapter",
                    "jake2.game.Monster",
                    "jake2.game.PlayerClient",
                    "jake2.game.PlayerHud",
                    "jake2.game.PlayerTrail",
                    "jake2.game.PlayerView",
                    "jake2.game.SuperAdapter",
                    "jake2.game.monsters.M_Actor",
                    "jake2.game.monsters.M_Berserk",
                    "jake2.game.monsters.M_Boss2",
                    "jake2.game.monsters.M_Boss3",
                    "jake2.game.monsters.M_Boss31",
                    "jake2.game.monsters.M_Boss32",
                    "jake2.game.monsters.M_Brain",
                    "jake2.game.monsters.M_Chick",
                    "jake2.game.monsters.M_Flash",
                    "jake2.game.monsters.M_Flipper",
                    "jake2.game.monsters.M_Float",
                    "jake2.game.monsters.M_Flyer",
                    "jake2.game.monsters.M_Gladiator",
                    "jake2.game.monsters.M_Gunner",
                    "jake2.game.monsters.M_Hover",
                    "jake2.game.monsters.M_Infantry",
                    "jake2.game.monsters.M_Insane",
                    "jake2.game.monsters.M_Medic",
                    "jake2.game.monsters.M_Mutant",
                    "jake2.game.monsters.M_Parasite",
                    "jake2.game.monsters.M_Player",
                    "jake2.game.monsters.M_Soldier",
                    "jake2.game.monsters.M_Supertank",
                    "jake2.game.monsters.M_Tank",
                    "jake2.game.GameItems",
                    // DANGER! init as last, when all adatpers are != null
                    "jake2.game.GameItemList"
            };
    private final GameImports gameImports;

    // Previously was game_exports_t.Init()
    public GameExportsImpl(GameImports imports) {
        this.gameImports = imports;

        gameImports.dprintf("==== InitGame ====\n");

        // preload all classes to register the adapters
        // fixme
        for (String className : preloadclasslist) {
            try {
                Class.forName(className);
            } catch (Exception e) {
                Com.DPrintf("error loading class: " + e.getMessage());
            }
        }


        GameBase.gun_x = GameBase.gi.cvar("gun_x", "0", 0);
        GameBase.gun_y = GameBase.gi.cvar("gun_y", "0", 0);
        GameBase.gun_z = GameBase.gi.cvar("gun_z", "0", 0);

        //FIXME: sv_ prefix are wrong names for these variables
        GameBase.sv_rollspeed = GameBase.gi.cvar("sv_rollspeed", "200", 0);
        GameBase.sv_rollangle = GameBase.gi.cvar("sv_rollangle", "2", 0);
        GameBase.sv_maxvelocity = GameBase.gi.cvar("sv_maxvelocity", "2000", 0);
        GameBase.sv_gravity = GameBase.gi.cvar("sv_gravity", "800", 0);

        // noset vars
        Globals.dedicated = GameBase.gi.cvar("dedicated", "0",
                Defines.CVAR_NOSET);

        // latched vars
        GameBase.sv_cheats = GameBase.gi.cvar("cheats", "0",
                Defines.CVAR_SERVERINFO | Defines.CVAR_LATCH);
        GameBase.gi.cvar("gamename", Defines.GAMEVERSION,
                Defines.CVAR_SERVERINFO | Defines.CVAR_LATCH);
        GameBase.gi.cvar("gamedate", Globals.__DATE__, Defines.CVAR_SERVERINFO
                | Defines.CVAR_LATCH);

        GameBase.maxclients = GameBase.gi.cvar("maxclients", "4",
                Defines.CVAR_SERVERINFO | Defines.CVAR_LATCH);
        GameBase.maxspectators = GameBase.gi.cvar("maxspectators", "4",
                Defines.CVAR_SERVERINFO);
        GameBase.deathmatch = GameBase.gi.cvar("deathmatch", "0",
                Defines.CVAR_LATCH);
        GameBase.coop = GameBase.gi.cvar("coop", "0", Defines.CVAR_LATCH);
        GameBase.skill = GameBase.gi.cvar("skill", "0", Defines.CVAR_LATCH);
        GameBase.maxentities = GameBase.gi.cvar("maxentities", "1024",
                Defines.CVAR_LATCH);

        // change anytime vars
        GameBase.dmflags = GameBase.gi.cvar("dmflags", "0",
                Defines.CVAR_SERVERINFO);
        GameBase.fraglimit = GameBase.gi.cvar("fraglimit", "0",
                Defines.CVAR_SERVERINFO);
        GameBase.timelimit = GameBase.gi.cvar("timelimit", "0",
                Defines.CVAR_SERVERINFO);
        GameBase.password = GameBase.gi.cvar("password", "",
                Defines.CVAR_USERINFO);
        GameBase.spectator_password = GameBase.gi.cvar("spectator_password",
                "", Defines.CVAR_USERINFO);
        GameBase.needpass = GameBase.gi.cvar("needpass", "0",
                Defines.CVAR_SERVERINFO);
        GameBase.filterban = GameBase.gi.cvar("filterban", "1", 0);

        GameBase.g_select_empty = GameBase.gi.cvar("g_select_empty", "0",
                Defines.CVAR_ARCHIVE);

        GameBase.run_pitch = GameBase.gi.cvar("run_pitch", "0.002", 0);
        GameBase.run_roll = GameBase.gi.cvar("run_roll", "0.005", 0);
        GameBase.bob_up = GameBase.gi.cvar("bob_up", "0.005", 0);
        GameBase.bob_pitch = GameBase.gi.cvar("bob_pitch", "0.002", 0);
        GameBase.bob_roll = GameBase.gi.cvar("bob_roll", "0.002", 0);

        // flood control
        GameBase.flood_msgs = GameBase.gi.cvar("flood_msgs", "4", 0);
        GameBase.flood_persecond = GameBase.gi.cvar("flood_persecond", "4", 0);
        GameBase.flood_waitdelay = GameBase.gi.cvar("flood_waitdelay", "10", 0);

        // dm map list
        GameBase.sv_maplist = GameBase.gi.cvar("sv_maplist", "", 0);

        // items
        GameItems.InitItems();

        GameBase.game.helpmessage1 = "";
        GameBase.game.helpmessage2 = "";

        // initialize all entities for this game
        GameBase.game.maxentities = (int) GameBase.maxentities.value;
        CreateEdicts();

        // initialize all clients for this game
        GameBase.game.maxclients = (int) GameBase.maxclients.value;

        CreateClients();

        GameBase.num_edicts = GameBase.game.maxclients + 1;
    }

    private static void CreateEdicts() {
        GameBase.g_edicts = new edict_t[GameBase.game.maxentities];
        for (int i = 0; i < GameBase.game.maxentities; i++)
            GameBase.g_edicts[i] = new edict_t(i);
    }

    private static void CreateClients() {
        GameBase.game.clients = new gclient_t[GameBase.game.maxclients];
        for (int i = 0; i < GameBase.game.maxclients; i++)
            GameBase.game.clients[i] = new gclient_t(i);

    }

    /**
     * Cmd_Give_f
     *
     * Give items to a client.
     */
    private void Give_f(edict_t ent, List<String> args) {
        String name;
        gitem_t it;
        int index;
        int i;
        boolean give_all;
        edict_t it_ent;

        if (GameBase.deathmatch.value != 0 && GameBase.sv_cheats.value == 0) {
            gameImports.cprintf(ent, Defines.PRINT_HIGH, "You must run the server with '+set cheats 1' to enable this command.\n");
            return;
        }

        name = Cmd.getArguments(args);

        give_all = 0 == Lib.Q_stricmp(name, "all");

        if (give_all || 0 == Lib.Q_stricmp(args.get(1), "health")) {
            if (args.size() == 3)
                ent.health = Lib.atoi(args.get(2));
            else
                ent.health = ent.max_health;
            if (!give_all)
                return;
        }

        gclient_t client = (gclient_t) ent.client;
        if (give_all || 0 == Lib.Q_stricmp(name, "weapons")) {
            for (i = 1; i < GameBase.game.num_items; i++) {
                it = GameItemList.itemlist[i];
                if (null == it.pickup)
                    continue;
                if (0 == (it.flags & GameDefines.IT_WEAPON))
                    continue;
                client.pers.inventory[i] += 1;
            }
            if (!give_all)
                return;
        }

        if (give_all || 0 == Lib.Q_stricmp(name, "ammo")) {
            for (i = 1; i < GameBase.game.num_items; i++) {
                it = GameItemList.itemlist[i];
                if (null == it.pickup)
                    continue;
                if (0 == (it.flags & GameDefines.IT_AMMO))
                    continue;
                GameItems.Add_Ammo(ent, it, 1000);
            }
            if (!give_all)
                return;
        }

        if (give_all || Lib.Q_stricmp(name, "armor") == 0) {
            gitem_armor_t info;

            it = GameItems.FindItem("Jacket Armor");
            client.pers.inventory[GameItems.ITEM_INDEX(it)] = 0;

            it = GameItems.FindItem("Combat Armor");
            client.pers.inventory[GameItems.ITEM_INDEX(it)] = 0;

            it = GameItems.FindItem("Body Armor");
            info = (gitem_armor_t) it.info;
            client.pers.inventory[GameItems.ITEM_INDEX(it)] = info.max_count;

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
                if ((it.flags & (GameDefines.IT_ARMOR | GameDefines.IT_WEAPON | GameDefines.IT_AMMO)) != 0)
                    continue;
                client.pers.inventory[i] = 1;
            }
            return;
        }

        it = GameItems.FindItem(name);
        if (it == null) {
            name = args.get(1);
            it = GameItems.FindItem(name);
            if (it == null) {
                gameImports.cprintf(ent, Defines.PRINT_HIGH, "unknown item\n");
                return;
            }
        }

        if (it.pickup == null) {
            gameImports.cprintf(ent, Defines.PRINT_HIGH, "non-pickup item\n");
            return;
        }

        index = GameItems.ITEM_INDEX(it);

        if ((it.flags & GameDefines.IT_AMMO) != 0) {
            if (args.size() == 3)
                client.pers.inventory[index] = Lib.atoi(args.get(2));
            else
                client.pers.inventory[index] += it.quantity;
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
    private void God_f(edict_t ent) {
        String msg;

        if (GameBase.deathmatch.value != 0 && GameBase.sv_cheats.value == 0) {
            gameImports.cprintf(ent, Defines.PRINT_HIGH,
                    "You must run the server with '+set cheats 1' to enable this command.\n");
            return;
        }

        ent.flags ^= GameDefines.FL_GODMODE;
        if (0 == (ent.flags & GameDefines.FL_GODMODE))
            msg = "godmode OFF\n";
        else
            msg = "godmode ON\n";

        gameImports.cprintf(ent, Defines.PRINT_HIGH, msg);
    }

    /**
     * Cmd_Notarget_f
     *
     * Sets client to notarget
     *
     * argv(0) notarget.
     */
    private void Notarget_f(edict_t ent) {
        String msg;

        if (GameBase.deathmatch.value != 0 && GameBase.sv_cheats.value == 0) {
            gameImports.cprintf(ent, Defines.PRINT_HIGH,
                    "You must run the server with '+set cheats 1' to enable this command.\n");
            return;
        }

        ent.flags ^= GameDefines.FL_NOTARGET;
        if (0 == (ent.flags & GameDefines.FL_NOTARGET))
            msg = "notarget OFF\n";
        else
            msg = "notarget ON\n";

        gameImports.cprintf(ent, Defines.PRINT_HIGH, msg);
    }

    /**
     * Cmd_Noclip_f
     *
     * argv(0) noclip.
     */
    private void Noclip_f(edict_t ent) {
        String msg;

        if (GameBase.deathmatch.value != 0 && GameBase.sv_cheats.value == 0) {
            gameImports.cprintf(ent, Defines.PRINT_HIGH,
                    "You must run the server with '+set cheats 1' to enable this command.\n");
            return;
        }

        if (ent.movetype == GameDefines.MOVETYPE_NOCLIP) {
            ent.movetype = GameDefines.MOVETYPE_WALK;
            msg = "noclip OFF\n";
        } else {
            ent.movetype = GameDefines.MOVETYPE_NOCLIP;
            msg = "noclip ON\n";
        }

        gameImports.cprintf(ent,Defines.PRINT_HIGH, msg);
    }

    /**
     * Cmd_Use_f
     *
     * Use an inventory item.
     */
    private void Use_f(edict_t ent, List<String> args) {

        String itemName = Cmd.getArguments(args);

        gitem_t it = GameItems.FindItem(itemName);
        Com.dprintln("using:" + itemName);
        if (it == null) {
            gameImports.cprintf(ent, Defines.PRINT_HIGH, "unknown item: " + itemName + "\n");
            return;
        }
        if (it.use == null) {
            gameImports.cprintf(ent, Defines.PRINT_HIGH, "Item is not usable.\n");
            return;
        }
        int index = GameItems.ITEM_INDEX(it);
        gclient_t client = (gclient_t) ent.client;
        if (0 == client.pers.inventory[index]) {
            gameImports.cprintf(ent, Defines.PRINT_HIGH, "Out of item: " + itemName + "\n");
            return;
        }

        it.use.use(ent, it);
    }

    /**
     * Cmd_Drop_f
     *
     * Drop an inventory item.
     */
    private void Drop_f(edict_t ent, List<String> args) {

        String itemName = Cmd.getArguments(args);
        gitem_t it = GameItems.FindItem(itemName);
        if (it == null) {
            gameImports.cprintf(ent, Defines.PRINT_HIGH, "unknown item: " + itemName + "\n");
            return;
        }
        if (it.drop == null) {
            gameImports.cprintf(ent, Defines.PRINT_HIGH,
                    "Item is not dropable.\n");
            return;
        }
        int index = GameItems.ITEM_INDEX(it);
        gclient_t client = (gclient_t) ent.client;
        if (0 == client.pers.inventory[index]) {
            gameImports.cprintf(ent, Defines.PRINT_HIGH, "Out of item: " + itemName + "\n");
            return;
        }

        it.drop.drop(ent, it);
    }

    /**
     * Cmd_Inven_f.
     */
    private static void Inven_f(edict_t ent) {

        gclient_t cl = (gclient_t) ent.client;

        cl.showscores = false;
        cl.showhelp = false;

        if (cl.showinventory) {
            cl.showinventory = false;
            return;
        }

        cl.showinventory = true;

        GameBase.gi.WriteByte(NetworkCommands.svc_inventory);
        for (int i = 0; i < Defines.MAX_ITEMS; i++) {
            GameBase.gi.WriteShort(cl.pers.inventory[i]);
        }
        GameBase.gi.unicast(ent, true);
    }

    /**
     * Cmd_InvUse_f.
     */
    private void InvUse_f(edict_t ent) {
        gitem_t it;

        GameItems.ValidateSelectedItem(ent);

        gclient_t client = (gclient_t) ent.client;
        if (client.pers.selected_item == -1) {
            gameImports.cprintf(ent, Defines.PRINT_HIGH, "No item to use.\n");
            return;
        }

        it = GameItemList.itemlist[client.pers.selected_item];
        if (it.use == null) {
            gameImports.cprintf(ent, Defines.PRINT_HIGH, "Item is not usable.\n");
            return;
        }
        it.use.use(ent, it);
    }

    /**
     * Cmd_WeapPrev_f.
     */
    private static void WeapPrev_f(edict_t ent) {

        gclient_t cl = (gclient_t) ent.client;

        if (cl.pers.weapon == null)
            return;

        int selected_weapon = GameItems.ITEM_INDEX(cl.pers.weapon);

        // scan for the next valid one
        for (int i = 1; i <= Defines.MAX_ITEMS; i++) {
            int index = (selected_weapon + i) % Defines.MAX_ITEMS;
            if (0 == cl.pers.inventory[index])
                continue;

            gitem_t it = GameItemList.itemlist[index];
            if (it.use == null)
                continue;

            if (0 == (it.flags & GameDefines.IT_WEAPON))
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

        cl = (gclient_t) ent.client;

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
            if (0 == (it.flags & GameDefines.IT_WEAPON))
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
        int index;

        gclient_t cl = (gclient_t) ent.client;

        if (null == cl.pers.weapon || null == cl.pers.lastweapon)
            return;

        index = GameItems.ITEM_INDEX(cl.pers.lastweapon);
        if (0 == cl.pers.inventory[index])
            return;
        gitem_t it = GameItemList.itemlist[index];
        if (null == it.use)
            return;
        if (0 == (it.flags & GameDefines.IT_WEAPON))
            return;
        it.use.use(ent, it);
    }

    /**
     * Cmd_InvDrop_f
     */
    private void InvDrop_f(edict_t ent) {
        gitem_t it;

        GameItems.ValidateSelectedItem(ent);

        gclient_t client = (gclient_t) ent.client;
        if (client.pers.selected_item == -1) {
            gameImports.cprintf(ent, Defines.PRINT_HIGH, "No item to drop.\n");
            return;
        }

        it = GameItemList.itemlist[client.pers.selected_item];
        if (it.drop == null) {
            gameImports.cprintf(ent, Defines.PRINT_HIGH, "Item is not dropable.\n");
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
        gclient_t client = (gclient_t) ent.client;
        client.showinventory = false;
        client.showhelp = false;

        if (0 == GameBase.deathmatch.value && 0 == GameBase.coop.value)
            return;

        if (client.showscores) {
            client.showscores = false;
            return;
        }

        client.showscores = true;
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

        gclient_t client = (gclient_t) ent.client;
        client.showinventory = false;
        client.showscores = false;

        if (client.showhelp
                && (client.pers.game_helpchanged == GameBase.game.helpchanged)) {
            client.showhelp = false;
            return;
        }

        client.showhelp = true;
        client.pers.helpchanged = 0;
        PlayerHud.HelpComputer(ent);
    }

    /**
     * Cmd_Kill_f
     */
    private static void Kill_f(edict_t ent) {
        gclient_t client = (gclient_t) ent.client;
        if ((GameBase.level.time - client.respawn_time) < 5)
            return;
        ent.flags &= ~GameDefines.FL_GODMODE;
        ent.health = 0;
        GameBase.meansOfDeath = GameDefines.MOD_SUICIDE;
        PlayerClient.player_die.die(ent, ent, ent, 100000, Globals.vec3_origin);
    }

    /**
     * Cmd_PutAway_f
     */
    private static void PutAway_f(edict_t ent) {
        gclient_t client = (gclient_t) ent.client;
        client.showscores = false;
        client.showhelp = false;
        client.showinventory = false;
    }

    private static Comparator PlayerSort = (o1, o2) -> {
        int anum = ((Integer) o1).intValue();
        int bnum = ((Integer) o2).intValue();

        int anum1 = GameBase.game.clients[anum].getPlayerState().stats[Defines.STAT_FRAGS];
        int bnum1 = GameBase.game.clients[bnum].getPlayerState().stats[Defines.STAT_FRAGS];

        if (anum1 < bnum1)
            return -1;
        if (anum1 > bnum1)
            return 1;
        return 0;
    };


    /**
     * Cmd_Players_f
     */
    private void Players_f(edict_t ent) {
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
        Arrays.sort(index, 0, count - 1, PlayerSort);

        // print information
        large = "";

        for (i = 0; i < count; i++) {
            small = GameBase.game.clients[index[i].intValue()].getPlayerState().stats[Defines.STAT_FRAGS]
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

        gameImports.centerprintf(ent, large + "\n" + count + " players\n");
    }

    /**
     * Cmd_Wave_f
     */
    private void Wave_f(edict_t ent, List<String> args) {


        // can't wave when ducked
        gclient_t client = (gclient_t) ent.client;
        if ((client.getPlayerState().pmove.pm_flags & Defines.PMF_DUCKED) != 0)
            return;

        if (client.anim_priority > Defines.ANIM_WAVE)
            return;

        client.anim_priority = Defines.ANIM_WAVE;

        int type = args.size() != 2 ? 0 : Lib.atoi(args.get(1));

        switch (type) {
            case 0:
                gameImports.cprintf(ent, Defines.PRINT_HIGH, "flipoff\n");
                ent.s.frame = M_Player.FRAME_flip01 - 1;
                client.anim_end = M_Player.FRAME_flip12;
                break;
            case 1:
                gameImports.cprintf(ent, Defines.PRINT_HIGH, "salute\n");
                ent.s.frame = M_Player.FRAME_salute01 - 1;
                client.anim_end = M_Player.FRAME_salute11;
                break;
            case 2:
                gameImports.cprintf(ent, Defines.PRINT_HIGH, "taunt\n");
                ent.s.frame = M_Player.FRAME_taunt01 - 1;
                client.anim_end = M_Player.FRAME_taunt17;
                break;
            case 3:
                gameImports.cprintf(ent, Defines.PRINT_HIGH, "wave\n");
                ent.s.frame = M_Player.FRAME_wave01 - 1;
                client.anim_end = M_Player.FRAME_wave11;
                break;
            case 4:
            default:
                gameImports.cprintf(ent, Defines.PRINT_HIGH, "point\n");
                ent.s.frame = M_Player.FRAME_point01 - 1;
                client.anim_end = M_Player.FRAME_point12;
                break;
        }
    }

    /**
     * Command to print the players own position.
     */
    private void ShowPosition_f(edict_t ent) {
        gameImports.cprintf(ent, Defines.PRINT_HIGH, "pos=" + Lib.vtofsbeaty(ent.s.origin) + "\n");
    }

    /**
     * Cmd_Say_f
     */
    private void Say_f(edict_t ent, boolean team, boolean sayAll, List<String> args) {

        int i, j;
        edict_t other;
        String text;
        gclient_t cl;

        if (args.size() < 2 && !sayAll)
            return;

        if (0 == ((int) (GameBase.dmflags.value) & (Defines.DF_MODELTEAMS | Defines.DF_SKINTEAMS)))
            team = false;

        gclient_t client = (gclient_t) ent.client;
        if (team)
            text = "(" + client.pers.netname + "): ";
        else
            text = "" + client.pers.netname + ": ";

        String arguments = Cmd.getArguments(args);

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
            cl = client;

            if (GameBase.level.time < cl.flood_locktill) {
                gameImports.cprintf(ent, Defines.PRINT_HIGH, "You can't talk for "
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
                gameImports.cprintf(ent, Defines.PRINT_CHAT,
                        "Flood protection:  You can't talk for "
                                + (int) GameBase.flood_waitdelay.value
                                + " seconds.\n");
                return;
            }

            cl.flood_whenhead = (cl.flood_whenhead + 1) % 10;
            cl.flood_when[cl.flood_whenhead] = GameBase.level.time;
        }

        if (Globals.dedicated.value != 0)
            gameImports.cprintf(null, Defines.PRINT_CHAT, "" + text + "");

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
            gameImports.cprintf(other, Defines.PRINT_CHAT, "" + text + "");
        }

    }

    /**
     * Returns the playerlist. TODO: The list is badly formatted at the moment.
     */
    private void PlayerList_f(edict_t ent) {
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

            gclient_t client = (gclient_t) e2.client;
            st = ""
                    + (GameBase.level.framenum - client.resp.enterframe)
                    / 600
                    + ":"
                    + ((GameBase.level.framenum - client.resp.enterframe) % 600)
                    / 10 + " " + client.getPing() + " " + client.resp.score
                    + " " + client.pers.netname + " "
                    + (client.resp.spectator ? " (spectator)" : "") + "\n";

            if (text.length() + st.length() > 1024 - 50) {
                text += "And more...\n";
                gameImports.cprintf(ent, Defines.PRINT_HIGH, "" + text + "");
                return;
            }
            text += st;
        }
        gameImports.cprintf(ent, Defines.PRINT_HIGH, text);
    }


    @Override
    public void ClientCommand(edict_t ent, List<String> args) {
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
                GameItems.SelectNextItem(ent, GameDefines.IT_WEAPON);
                break;
            case "invprevw":
                GameItems.SelectPrevItem(ent, GameDefines.IT_WEAPON);
                break;
            case "invnextp":
                GameItems.SelectNextItem(ent, GameDefines.IT_POWERUP);
                break;
            case "invprevp":
                GameItems.SelectPrevItem(ent, GameDefines.IT_POWERUP);
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

    @Override
    public void G_RunFrame() {
        GameBase.G_RunFrame();
    }

    @Override
    public void WriteGame(String filename, boolean autosave) {
        try {
            QuakeFile f;

            if (!autosave)
                PlayerClient.SaveClientData();

            f = new QuakeFile(filename, "rw");

            if (f == null)
                GameBase.gi.error("Couldn't write to " + filename);

            GameBase.game.autosaved = autosave;
            GameBase.game.write(f);
            GameBase.game.autosaved = false;

            for (int i = 0; i < GameBase.game.maxclients; i++)
                GameBase.game.clients[i].write(f);

            Lib.fclose(f);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void ReadGame(String filename) {

        QuakeFile f = null;

        try {

            f = new QuakeFile(filename, "r");
            CreateEdicts();

            GameBase.game.load(f);

            for (int i = 0; i < GameBase.game.maxclients; i++) {
                GameBase.game.clients[i] = new gclient_t(i);
                GameBase.game.clients[i].read(f, GameBase.g_edicts);
            }

            f.close();
        }

        catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void WriteLevel(String filename) {
        try {
            int i;
            edict_t ent;
            QuakeFile f;

            f = new QuakeFile(filename, "rw");
            if (f == null)
                GameBase.gi.error("Couldn't open for writing: " + filename);

            // write out level_locals_t
            GameBase.level.write(f);

            // write out all the entities
            for (i = 0; i < GameBase.num_edicts; i++) {
                ent = GameBase.g_edicts[i];
                if (!ent.inuse)
                    continue;
                f.writeInt(i);
                ent.write(f);
            }

            i = -1;
            f.writeInt(-1);

            f.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void ReadLevel(String filename) {
        try {
            edict_t ent;

            QuakeFile f = new QuakeFile(filename, "r");

            if (f == null)
                GameBase.gi.error("Couldn't read level file " + filename);

            // wipe all the entities
            CreateEdicts();

            GameBase.num_edicts = (int) GameBase.maxclients.value + 1;

            // load the level locals
            GameBase.level.read(f, GameBase.g_edicts);

            // load all the entities
            while (true) {
                int entnum = f.readInt();
                if (entnum == -1)
                    break;

                if (entnum >= GameBase.num_edicts)
                    GameBase.num_edicts = entnum + 1;

                ent = GameBase.g_edicts[entnum];
                ent.read(f, GameBase.g_edicts);
                ent.cleararealinks();
                GameBase.gi.linkentity(ent);
            }

            Lib.fclose(f);

            // mark all clients as unconnected
            for (int i = 0; i < GameBase.maxclients.value; i++) {
                ent = GameBase.g_edicts[i + 1];
                ent.client = GameBase.game.clients[i];
                gclient_t client = (gclient_t) ent.client;
                client.pers.connected = false;
            }

            // do any load time things at this point
            for (int i = 0; i < GameBase.num_edicts; i++) {
                ent = GameBase.g_edicts[i];

                if (!ent.inuse)
                    continue;

                // fire any cross-level triggers
                if (ent.classname != null)
                    if ("target_crosslevel_target".equals(ent.classname))
                        ent.nextthink = GameBase.level.time + ent.delay;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void SpawnEntities(String mapname, String entities, String spawnpoint) {
        GameSpawn.SpawnEntities(mapname, entities, spawnpoint);
    }

    @Override
    public void ServerCommand(List<String> args) {
        GameSVCmds.ServerCommand(args);
    }

    @Override
    public void ClientBegin(edict_t ent) {
        PlayerClient.ClientBegin(ent);
    }

    @Override
    public String ClientUserinfoChanged(edict_t ent, String userinfo) {
        return PlayerClient.ClientUserinfoChanged(ent, userinfo);
    }

    @Override
    public boolean ClientConnect(edict_t ent, String userinfo) {
        return PlayerClient.ClientConnect(ent, userinfo);
    }

    @Override
    public void ClientDisconnect(edict_t ent) {
        PlayerClient.ClientDisconnect(ent);
    }

    @Override
    public void ClientThink(edict_t ent, usercmd_t ucmd) {
        PlayerClient.ClientThink(ent, ucmd);
    }

    @Override
    public edict_t getEdict(int index) {
        return GameBase.g_edicts[index];
    }

    @Override
    public int getNumEdicts() {
        return GameBase.num_edicts;
    }

}
