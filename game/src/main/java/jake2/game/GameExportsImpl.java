package jake2.game;

import jake2.game.monsters.M_Player;
import jake2.qcommon.*;
import jake2.qcommon.exec.Cmd;
import jake2.qcommon.filesystem.QuakeFile;
import jake2.qcommon.network.NetworkCommands;
import jake2.qcommon.util.Lib;
import jake2.qcommon.util.Vargs;

import java.util.Arrays;
import java.util.List;

import static java.util.Comparator.comparingInt;

/**
 * See jake2.server.SV_GAME#SV_InitGameProgs()
 */
public class GameExportsImpl implements GameExports {

    private static final String[] preloadclasslist =
            {
                    "jake2.game.PlayerWeapon",
                    "jake2.game.AIAdapter",
                    "jake2.qcommon.exec.Cmd",
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
                    "jake2.qcommon.M_Flash",
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

        // todo replace with constructor
        GameBase.Init(gameImports);
    }

    /**
     * HelpComputer.
     * Prepare text with values and send them to client
     */
    private void prepareHelpComputerText(edict_t ent) {
        StringBuilder sb = new StringBuilder(256);
        String sk;

        if (GameBase.skill.value == 0)
            sk = "easy";
        else if (GameBase.skill.value == 1)
            sk = "medium";
        else if (GameBase.skill.value == 2)
            sk = "hard";
        else
            sk = "hard+";

        // send the layout
        sb.append("xv 32 yv 8 picn help "); // background
        sb.append("xv 202 yv 12 string2 \"").append(sk).append("\" "); // skill
        sb.append("xv 0 yv 24 cstring2 \"").append(GameBase.level.level_name)
                .append("\" "); // level name
        sb.append("xv 0 yv 54 cstring2 \"").append(GameBase.game.helpmessage1)
                .append("\" "); // help 1
        sb.append("xv 0 yv 110 cstring2 \"").append(GameBase.game.helpmessage2)
                .append("\" "); // help 2
        sb.append("xv 50 yv 164 string2 \" kills     goals    secrets\" ");
        sb.append("xv 50 yv 172 string2 \"");
        sb.append(Com.sprintf("%3i/%3i     %i/%i       %i/%i\" ", new Vargs(6)
                .add(GameBase.level.killed_monsters).add(
                        GameBase.level.total_monsters).add(
                        GameBase.level.found_goals).add(
                        GameBase.level.total_goals).add(
                        GameBase.level.found_secrets).add(
                        GameBase.level.total_secrets)));

        gameImports.WriteByte(NetworkCommands.svc_layout);
        gameImports.WriteString(sb.toString());
        gameImports.unicast(ent, true);
    }

    /**
     * Cmd_Give_f
     *
     * Give items to a client.
     */
    private void Give_f(SubgameEntity ent, List<String> args) {

        if (GameBase.deathmatch.value != 0 && GameBase.sv_cheats.value == 0) {
            gameImports.cprintf(ent, Defines.PRINT_HIGH, "You must run the server with '+set cheats 1' to enable this command.\n");
            return;
        }

        String name = Cmd.getArguments(args);

        boolean give_all = 0 == Lib.Q_stricmp(name, "all");

        if (give_all || 0 == Lib.Q_stricmp(args.get(1), "health")) {
            if (args.size() == 3)
                ent.health = Lib.atoi(args.get(2));
            else
                ent.health = ent.max_health;
            if (!give_all)
                return;
        }

        gclient_t client = ent.getClient();
        int i;
        gitem_t it;
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
            client.pers.inventory[it.index] = 0;

            it = GameItems.FindItem("Combat Armor");
            client.pers.inventory[it.index] = 0;

            it = GameItems.FindItem("Body Armor");
            info = (gitem_armor_t) it.info;
            client.pers.inventory[it.index] = info.max_count;

            if (!give_all)
                return;
        }

        SubgameEntity it_ent;
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

        int index = it.index;

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
    private void God_f(SubgameEntity ent) {
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
     * Sets client to notarget
     */
    private void Notarget_f(SubgameEntity ent) {

        // why do you need notarget in deathmatch??
        if (GameBase.deathmatch.value != 0 && GameBase.sv_cheats.value == 0) {
            gameImports.cprintf(ent, Defines.PRINT_HIGH,
                    "You must run the server with '+set cheats 1' to enable this command.\n");
            return;
        }

        ent.flags ^= GameDefines.FL_NOTARGET;
        String msg;
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
    private void Noclip_f(SubgameEntity ent) {
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
    private void Use_f(SubgameEntity ent, List<String> args) {

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
        int index = it.index;
        gclient_t client = ent.getClient();
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
    private void Drop_f(SubgameEntity ent, List<String> args) {

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
        int index = it.index;
        gclient_t client = ent.getClient();
        if (0 == client.pers.inventory[index]) {
            gameImports.cprintf(ent, Defines.PRINT_HIGH, "Out of item: " + itemName + "\n");
            return;
        }

        it.drop.drop(ent, it);
    }

    /**
     * Cmd_Inven_f.
     */
    private void Inven_f(SubgameEntity ent) {

        gclient_t cl = ent.getClient();

        cl.showscores = false;
        cl.showhelp = false;

        if (cl.showinventory) {
            cl.showinventory = false;
            return;
        }

        cl.showinventory = true;

        gameImports.WriteByte(NetworkCommands.svc_inventory);
        for (int i = 0; i < Defines.MAX_ITEMS; i++) {
            gameImports.WriteShort(cl.pers.inventory[i]);
        }
        gameImports.unicast(ent, true);
    }

    /**
     * Cmd_InvUse_f.
     */
    private void InvUse_f(SubgameEntity ent) {
        gitem_t it;

        GameItems.ValidateSelectedItem(ent);

        gclient_t client = ent.getClient();
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
    private static void WeapPrev_f(SubgameEntity ent) {

        gclient_t cl = ent.getClient();

        if (cl.pers.weapon == null)
            return;

        int selected_weapon = cl.pers.weapon.index;

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
    private static void WeapNext_f(SubgameEntity ent) {
        gclient_t cl;
        int i, index;
        gitem_t it;
        int selected_weapon;

        cl = ent.getClient();

        if (null == cl.pers.weapon)
            return;

        selected_weapon = cl.pers.weapon.index;

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
    private static void WeapLast_f(SubgameEntity ent) {
        int index;

        gclient_t cl = ent.getClient();

        if (null == cl.pers.weapon || null == cl.pers.lastweapon)
            return;

        index = cl.pers.lastweapon.index;
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
    private void InvDrop_f(SubgameEntity ent) {
        gitem_t it;

        GameItems.ValidateSelectedItem(ent);

        gclient_t client = ent.getClient();
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
    private static void Score_f(SubgameEntity ent) {
        gclient_t client = ent.getClient();
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
    private void Help_f(SubgameEntity ent) {
        // this is for backwards compatability
        if (GameBase.deathmatch.value != 0) {
            Score_f(ent);
            return;
        }

        gclient_t client = ent.getClient();
        client.showinventory = false;
        client.showscores = false;

        if (client.showhelp
                && (client.pers.game_helpchanged == GameBase.game.helpchanged)) {
            client.showhelp = false;
            return;
        }

        client.showhelp = true;
        client.pers.helpchanged = 0;
        prepareHelpComputerText(ent);
    }

    /**
     * Cmd_Kill_f
     */
    private static void Kill_f(SubgameEntity ent) {
        gclient_t client = ent.getClient();
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
    private static void PutAway_f(SubgameEntity ent) {
        gclient_t client = ent.getClient();
        client.showscores = false;
        client.showhelp = false;
        client.showinventory = false;
    }

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
        for (i = 0; i < GameBase.game.maxclients; i++) {
            if (GameBase.game.clients[i].pers.connected) {
                index[count] = new Integer(i);
                count++;
            }
        }

        // sort by frags
        Arrays.sort(index, 0, count - 1, comparingInt(p -> GameBase.game.clients[p].getPlayerState().stats[Defines.STAT_FRAGS]));

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
    private void Wave_f(SubgameEntity ent, List<String> args) {


        // can't wave when ducked
        gclient_t client = ent.getClient();
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
    private void Say_f(SubgameEntity ent, boolean team, boolean sayAll, List<String> args) {

        if (args.size() < 2 && !sayAll)
            return;

        if (0 == ((int) (GameBase.dmflags.value) & (Defines.DF_MODELTEAMS | Defines.DF_SKINTEAMS)))
            team = false;

        gclient_t client = ent.getClient();
        String text;
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
            gclient_t cl = client;

            if (GameBase.level.time < cl.flood_locktill) {
                gameImports.cprintf(ent, Defines.PRINT_HIGH, "You can't talk for "
                        + (int) (cl.flood_locktill - GameBase.level.time)
                        + " more seconds\n");
                return;
            }
            int i = (int) (cl.flood_whenhead - GameBase.flood_msgs.value + 1);
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

        if (gameImports.cvar("dedicated", "0", Defines.CVAR_NOSET).value != 0)
            gameImports.cprintf(null, Defines.PRINT_CHAT, "" + text + "");

        for (int j = 1; j <= GameBase.game.maxclients; j++) {
            SubgameEntity other = GameBase.g_edicts[j];
            if (!other.inuse)
                continue;
            if (other.getClient() == null)
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

        // connect time, ping, score, name
        String text = "";

        for (int i = 0; i < GameBase.game.maxclients; i++) {
            SubgameEntity e2 = GameBase.g_edicts[1 + i];
            if (!e2.inuse)
                continue;

            gclient_t client = e2.getClient();
            String st = ""
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
    public void ClientCommand(edict_t player, List<String> args) {
        SubgameEntity ent = GameBase.g_edicts[player.index];

        if (ent.getClient() == null)
            return; // not fully in game yet

        String cmd = args.get(0).toLowerCase();

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
            case "spawn":
                GameSpawn.SpawnNewEntity(ent, args);
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

            if (!autosave)
                PlayerClient.SaveClientData();

            QuakeFile f = new QuakeFile(filename, "rw");

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
            GameBase.CreateEdicts(gameImports.cvar("maxentities", "1024", Defines.CVAR_LATCH).value);

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

            QuakeFile f = new QuakeFile(filename, "rw");

            // write out level_locals_t
            GameBase.level.write(f);

            // write out all the entities
            for (int i = 0; i < GameBase.num_edicts; i++) {
                SubgameEntity ent = GameBase.g_edicts[i];
                if (!ent.inuse)
                    continue;
                f.writeInt(i);
                ent.write(f);
            }
            f.writeInt(-1);

            f.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void ReadLevel(String filename) {
        try {

            QuakeFile f = new QuakeFile(filename, "r");

            if (f == null)
                gameImports.error("Couldn't read level file " + filename);

            // wipe all the entities
            GameBase.CreateEdicts(gameImports.cvar("maxentities", "1024", Defines.CVAR_LATCH).value);

            GameBase.num_edicts = (int) GameBase.game.maxclients + 1;

            // load the level locals
            GameBase.level.read(f, GameBase.g_edicts);

            // load all the entities
            SubgameEntity ent;
            while (true) {
                int entnum = f.readInt();
                if (entnum == -1)
                    break;

                if (entnum >= GameBase.num_edicts)
                    GameBase.num_edicts = entnum + 1;

                ent = GameBase.g_edicts[entnum];
                ent.read(f, GameBase.g_edicts);
                ent.cleararealinks();
                gameImports.linkentity(ent);
            }

            Lib.fclose(f);

            // mark all clients as unconnected
            for (int i = 0; i < GameBase.game.maxclients; i++) {
                ent = GameBase.g_edicts[i + 1];
                ent.setClient(GameBase.game.clients[i]);
                gclient_t client = ent.getClient();
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
    public void ClientBegin(edict_t e) {
        SubgameEntity ent = GameBase.g_edicts[e.index];
        PlayerClient.ClientBegin(ent);
    }

    @Override
    public String ClientUserinfoChanged(edict_t ent, String userinfo) {
        return PlayerClient.ClientUserinfoChanged((SubgameEntity) ent, userinfo);
    }

    @Override
    public boolean ClientConnect(edict_t ent, String userinfo) {
        return PlayerClient.ClientConnect((SubgameEntity) ent, userinfo);
    }

    @Override
    public void ClientDisconnect(edict_t ent) {
        PlayerClient.ClientDisconnect((SubgameEntity) ent);
    }

    @Override
    public void ClientThink(edict_t ent, usercmd_t ucmd) {
        SubgameEntity e = GameBase.g_edicts[ent.index];
        PlayerClient.ClientThink(e, ucmd);
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
