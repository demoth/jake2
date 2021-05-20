package jake2.game;

import jake2.game.monsters.M_Player;
import jake2.qcommon.*;
import jake2.qcommon.exec.Cmd;
import jake2.qcommon.filesystem.QuakeFile;
import jake2.qcommon.network.MulticastTypes;
import jake2.qcommon.network.NetworkCommandType;
import jake2.qcommon.util.Lib;
import jake2.qcommon.util.Math3D;

import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;

import static jake2.game.GameBase.G_Find;
import static jake2.game.GameBase.findByClass;
import static jake2.game.PlayerClient.*;
import static java.util.Comparator.comparingInt;

/**
 * Collection of functions provided by the game module.
 * Also serves as the holder of game state from the game side
 * See jake2.server.SV_GAME#SV_InitGameProgs()
 *
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

    /////////////////////////////////////
    /////////  STATE    /////////////////
    /////////////////////////////////////
    public final GameImports gameImports;
    public PlayerView playerView;
    public PlayerTrail playerTrail;
    public SV sv;
    public game_locals_t game;
    public GameCvars gameCvars;
    public level_locals_t level;
    public int meansOfDeath;

    // this is used to store parsed entity fields during map loading
    // todo: pass directly instead of via global static field
    public spawn_temp_t st = new spawn_temp_t();

    //////////////
    // Collision
    //////////////
    // holds the entity that is blocking something' movement
    SubgameEntity obstacle;
    // todo: make consistent with maxentities cvar
    pushed_t[] pushed = new pushed_t[Defines.MAX_EDICTS];
    int pushed_p;
    // holds the result of gi.BoxEdicts
    // todo: make consistent with maxentities cvar
    // todo: remove and use result of gi.BoxEdicts
    SubgameEntity[] touch = new SubgameEntity[Defines.MAX_EDICTS];

    // Monster knowledge about the enemy
    // todo: move to separate class
    boolean enemy_vis;
    boolean enemy_infront;
    int enemy_range;
    float enemy_yaw;

    // Game Items related
    GameItemList items;
    // todo: move to appropriate places
    int quad_drop_timeout_hack;
    boolean is_quad;
    byte is_silenced;
    int jacket_armor_index;
    int combat_armor_index;
    int body_armor_index;
    int power_screen_index;
    int power_shield_index;

    int windsound_index;

    int player_die_i;

    // todo: move to PM related place
    SubgameEntity pm_passent;


    /**
     * entity with index = 0 is always the worldspawn.
     * entities with indices 1..maxclients are the players
     * then go other stuff
     */
    // todo: make consistent with maxentities cvar
    public SubgameEntity[] g_edicts = new SubgameEntity[Defines.MAX_EDICTS];
    int num_edicts;

    /////////////////////////////////////
    /////////  INITIALIZATION   /////////
    /////////////////////////////////////

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
                gameImports.dprintf("error loading class: " + e.getMessage());
            }
        }

        playerView = new PlayerView(gameImports);

        sv = new SV(gameImports);

        game = new game_locals_t();
        game.helpmessage1 = "";
        game.helpmessage2 = "";
        items = new GameItemList();
        game.num_items = items.itemlist.length - 1;

        level = new level_locals_t();

        // create necessary cvars
        gameCvars = new GameCvars(imports);

        CreateEdicts(gameImports.cvar("maxentities", "1024", Defines.CVAR_LATCH).value);
        CreateClients(gameImports.cvar("maxclients", "4", Defines.CVAR_SERVERINFO | Defines.CVAR_LATCH).value);

        for (int n = 0; n < Defines.MAX_EDICTS; n++) {
            pushed[n] = new pushed_t();
        }

        playerTrail = new PlayerTrail(this);
    }

    // create the entities array and fill it with empty entities
    private void CreateEdicts(float max) {
        // initialize all entities for this game
        game.maxentities = (int) max;
        g_edicts = new SubgameEntity[game.maxentities];
        for (int i = 0; i < game.maxentities; i++)
            g_edicts[i] = new SubgameEntity(i);
    }

    // create the clients array and fill it with empty clients
    private void CreateClients(float max) {
        // initialize all clients for this game
        game.maxclients = (int) max;

        game.clients = new gclient_t[game.maxclients];

        for (int i = 0; i < game.maxclients; i++)
            game.clients[i] = new gclient_t(i);

        // so far we have only clients, no other entities
        num_edicts = game.maxclients + 1;

    }

    /////////////////////////////////////
    /////////    COMMANDS       /////////
    /////////////////////////////////////

    /**
     * HelpComputer.
     * Prepare text with values and send them to client
     */
    private void prepareHelpComputerText(edict_t ent) {
        StringBuilder sb = new StringBuilder(256);
        String sk;

        if (gameCvars.skill.value == 0)
            sk = "easy";
        else if (gameCvars.skill.value == 1)
            sk = "medium";
        else if (gameCvars.skill.value == 2)
            sk = "hard";
        else
            sk = "hard+";

        // send the layout
        sb.append("xv 32 yv 8 picn help "); // background
        sb.append("xv 202 yv 12 string2 \"").append(sk).append("\" "); // skill
        sb.append("xv 0 yv 24 cstring2 \"").append(level.level_name)
                .append("\" "); // level name
        sb.append("xv 0 yv 54 cstring2 \"").append(game.helpmessage1)
                .append("\" "); // help 1
        sb.append("xv 0 yv 110 cstring2 \"").append(game.helpmessage2)
                .append("\" "); // help 2
        sb.append("xv 50 yv 164 string2 \" kills     goals    secrets\" ");
        sb.append("xv 50 yv 172 string2 \"");
        sb.append(String.format("%3d/%3d     %d/%d       %d/%d\" ", level.killed_monsters, level.total_monsters, level.found_goals, level.total_goals, level.found_secrets, level.total_secrets));

        gameImports.WriteByte(NetworkCommandType.svc_layout);
        gameImports.WriteString(sb.toString());
        gameImports.unicast(ent, true);
    }

    /**
     * Cmd_Give_f
     *
     * Give items to a client.
     */
    private void Give_f(SubgameEntity ent, List<String> args) {

        if (gameCvars.deathmatch.value != 0 && gameCvars.sv_cheats.value == 0) {
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
            for (i = 1; i < game.num_items; i++) {
                it = items.itemlist[i];
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
            for (i = 1; i < game.num_items; i++) {
                it = items.itemlist[i];
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

            it = GameItems.FindItem("Jacket Armor", this);
            client.pers.inventory[it.index] = 0;

            it = GameItems.FindItem("Combat Armor", this);
            client.pers.inventory[it.index] = 0;

            it = GameItems.FindItem("Body Armor", this);
            info = (gitem_armor_t) it.info;
            client.pers.inventory[it.index] = info.max_count;

            if (!give_all)
                return;
        }

        SubgameEntity it_ent;
        if (give_all || Lib.Q_stricmp(name, "Power Shield") == 0) {
            it = GameItems.FindItem("Power Shield", this);
            it_ent = GameUtil.G_Spawn(this);
            it_ent.classname = it.classname;
            GameItems.SpawnItem(it_ent, it, this);
            GameItems.Touch_Item(it_ent, ent, GameBase.dummyplane, null, this);
            if (it_ent.inuse)
                GameUtil.G_FreeEdict(it_ent, this);

            if (!give_all)
                return;
        }

        if (give_all || 0 == Lib.Q_stricmp(name, "items")) {

            for (i = 1; i < game.num_items; i++) {
                it = items.itemlist[i];
                if (0 == (it.flags & GameDefines.IT_POWERUP))
                    continue;

                it_ent = GameUtil.G_Spawn(this);
                it_ent.classname = it.classname;
                GameItems.SpawnItem(it_ent, it, this);
                GameItems.Touch_Item(it_ent, ent, GameBase.dummyplane, null, this);
                if (it_ent.inuse)
                    GameUtil.G_FreeEdict(it_ent, this);

            }

            if (!give_all)
                return;
        }


        if (give_all) {
            for (i = 1; i < game.num_items; i++) {
                it = items.itemlist[i];
                if (it.pickup != null)
                    continue;
                if ((it.flags & (GameDefines.IT_ARMOR | GameDefines.IT_WEAPON | GameDefines.IT_AMMO)) != 0)
                    continue;
                client.pers.inventory[i] = 1;
            }
            return;
        }

        it = GameItems.FindItem(name, this);
        if (it == null) {
            name = args.get(1);
            it = GameItems.FindItem(name, this);
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

        // set particular amount of ammo: give cells 53
        if ((it.flags & GameDefines.IT_AMMO) != 0) {
            if (args.size() == 3)
                client.pers.inventory[index] = Lib.atoi(args.get(2));
            else
                client.pers.inventory[index] += it.quantity;
        } else {
            it_ent = GameUtil.G_Spawn(this);
            it_ent.classname = it.classname;
            GameItems.SpawnItem(it_ent, it, this);
            GameItems.Touch_Item(it_ent, ent, GameBase.dummyplane, null, this);
            if (it_ent.inuse)
                GameUtil.G_FreeEdict(it_ent, this);
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

        if (gameCvars.deathmatch.value != 0 && gameCvars.sv_cheats.value == 0) {
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
        if (gameCvars.deathmatch.value != 0 && gameCvars.sv_cheats.value == 0) {
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

        if (gameCvars.deathmatch.value != 0 && gameCvars.sv_cheats.value == 0) {
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
        gitem_t it = GameItems.FindItem(itemName, this);
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
        gameImports.dprintf("using:" + itemName + "\n");
        it.use.use(ent, it, this);
    }

    /**
     * Cmd_Drop_f
     *
     * Drop an inventory item.
     */
    private void Drop_f(SubgameEntity ent, List<String> args) {

        String itemName = Cmd.getArguments(args);
        gitem_t it = GameItems.FindItem(itemName, this);
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

        it.drop.drop(ent, it, this);
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

        gameImports.WriteByte(NetworkCommandType.svc_inventory);
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

        GameItems.ValidateSelectedItem(ent, this);

        gclient_t client = ent.getClient();
        if (client.pers.selected_item == -1) {
            gameImports.cprintf(ent, Defines.PRINT_HIGH, "No item to use.\n");
            return;
        }

        it = items.itemlist[client.pers.selected_item];
        if (it.use == null) {
            gameImports.cprintf(ent, Defines.PRINT_HIGH, "Item is not usable.\n");
            return;
        }
        it.use.use(ent, it, this);
    }

    /**
     * Cmd_WeapPrev_f.
     */
    private void WeapPrev_f(SubgameEntity ent) {

        gclient_t cl = ent.getClient();

        if (cl.pers.weapon == null)
            return;

        int selected_weapon = cl.pers.weapon.index;

        // scan for the next valid one
        for (int i = 1; i <= Defines.MAX_ITEMS; i++) {
            int index = (selected_weapon + i) % Defines.MAX_ITEMS;
            if (0 == cl.pers.inventory[index])
                continue;

            gitem_t it = items.itemlist[index];
            if (it.use == null)
                continue;

            if (0 == (it.flags & GameDefines.IT_WEAPON))
                continue;
            it.use.use(ent, it, this);
            if (cl.pers.weapon == it)
                return; // successful
        }
    }

    /**
     * Cmd_WeapNext_f.
     */
    private void WeapNext_f(SubgameEntity ent) {
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
            it = items.itemlist[index];
            if (null == it.use)
                continue;
            if (0 == (it.flags & GameDefines.IT_WEAPON))
                continue;
            it.use.use(ent, it, this);
            if (cl.pers.weapon == it)
                return; // successful
        }
    }

    /**
     * Cmd_WeapLast_f.
     */
    private void WeapLast_f(SubgameEntity ent) {
        int index;

        gclient_t cl = ent.getClient();

        if (null == cl.pers.weapon || null == cl.pers.lastweapon)
            return;

        index = cl.pers.lastweapon.index;
        if (0 == cl.pers.inventory[index])
            return;
        gitem_t it = items.itemlist[index];
        if (null == it.use)
            return;
        if (0 == (it.flags & GameDefines.IT_WEAPON))
            return;
        it.use.use(ent, it, this);
    }

    /**
     * Cmd_InvDrop_f
     */
    private void InvDrop_f(SubgameEntity ent) {
        gitem_t it;

        GameItems.ValidateSelectedItem(ent, this);

        gclient_t client = ent.getClient();
        if (client.pers.selected_item == -1) {
            gameImports.cprintf(ent, Defines.PRINT_HIGH, "No item to drop.\n");
            return;
        }

        it = items.itemlist[client.pers.selected_item];
        if (it.drop == null) {
            gameImports.cprintf(ent, Defines.PRINT_HIGH, "Item is not dropable.\n");
            return;
        }
        it.drop.drop(ent, it, this);
    }

    /**
     * Cmd_Score_f
     *
     * Display the scoreboard.
     *
     */
    private void Score_f(SubgameEntity ent) {
        gclient_t client = ent.getClient();
        client.showinventory = false;
        client.showhelp = false;

        if (0 == gameCvars.deathmatch.value && 0 == gameCvars.coop.value)
            return;

        if (client.showscores) {
            client.showscores = false;
            return;
        }

        client.showscores = true;
        PlayerHud.DeathmatchScoreboard(ent, this);
    }

    /**
     * Cmd_Help_f
     *
     * Display the current help message.
     *
     */
    void Help_f(SubgameEntity ent) {
        // this is for backwards compatability
        if (gameCvars.deathmatch.value != 0) {
            Score_f(ent);
            return;
        }

        gclient_t client = ent.getClient();
        client.showinventory = false;
        client.showscores = false;

        if (client.showhelp
                && (client.pers.game_helpchanged == game.helpchanged)) {
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
    private void Kill_f(SubgameEntity ent, GameExportsImpl gameExports) {
        gclient_t client = ent.getClient();
        if ((level.time - client.respawn_time) < 5)
            return;
        ent.flags &= ~GameDefines.FL_GODMODE;
        ent.health = 0;
        gameExports.meansOfDeath = GameDefines.MOD_SUICIDE;
        PlayerClient.player_die.die(ent, ent, ent, 100000, Globals.vec3_origin, this);
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
        for (i = 0; i < game.maxclients; i++) {
            if (game.clients[i].pers.connected) {
                index[count] = new Integer(i);
                count++;
            }
        }

        // sort by frags
        Arrays.sort(index, 0, count - 1, comparingInt(p -> game.clients[p].getPlayerState().stats[Defines.STAT_FRAGS]));

        // print information
        large = "";

        for (i = 0; i < count; i++) {
            small = game.clients[index[i].intValue()].getPlayerState().stats[Defines.STAT_FRAGS]
                    + " "
                    + game.clients[index[i].intValue()].pers.netname
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

        if (0 == ((int) (gameCvars.dmflags.value) & (Defines.DF_MODELTEAMS | Defines.DF_SKINTEAMS)))
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

        if (gameCvars.flood_msgs.value != 0) {
            gclient_t cl = client;

            if (level.time < cl.flood_locktill) {
                gameImports.cprintf(ent, Defines.PRINT_HIGH, "You can't talk for "
                        + (int) (cl.flood_locktill - level.time)
                        + " more seconds\n");
                return;
            }
            int i = (int) (cl.flood_whenhead - gameCvars.flood_msgs.value + 1);
            if (i < 0)
                i = (10) + i;
            if (cl.flood_when[i] != 0
                    && level.time - cl.flood_when[i] < gameCvars.flood_persecond.value) {
                cl.flood_locktill = level.time + gameCvars.flood_waitdelay.value;
                gameImports.cprintf(ent, Defines.PRINT_CHAT,
                        "Flood protection:  You can't talk for "
                                + (int) gameCvars.flood_waitdelay.value
                                + " seconds.\n");
                return;
            }

            cl.flood_whenhead = (cl.flood_whenhead + 1) % 10;
            cl.flood_when[cl.flood_whenhead] = level.time;
        }

        if (gameImports.cvar("dedicated", "0", Defines.CVAR_NOSET).value != 0)
            gameImports.cprintf(null, Defines.PRINT_CHAT, "" + text + "");

        for (int j = 1; j <= game.maxclients; j++) {
            SubgameEntity other = g_edicts[j];
            if (!other.inuse)
                continue;
            if (other.getClient() == null)
                continue;
            if (team) {
                if (!GameUtil.OnSameTeam(ent, other, gameCvars.dmflags.value))
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

        for (int i = 0; i < game.maxclients; i++) {
            SubgameEntity e2 = g_edicts[1 + i];
            if (!e2.inuse)
                continue;

            gclient_t client = e2.getClient();
            String st = ""
                    + (level.framenum - client.resp.enterframe)
                    / 600
                    + ":"
                    + ((level.framenum - client.resp.enterframe) % 600)
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

    /////////////////////////////////////
    /////////    UPDATE         /////////
    /////////////////////////////////////
    /**
     * Exits a level.
     */
    private void exitLevel() {
        gameImports.AddCommandString("gamemap \"" + level.changemap + "\"\n");

        // todo: remove as not necessary, new game instance will be created anyway
        level.changemap = null;
        level.exitintermission = false;
        level.intermissiontime = 0;

        ClientEndServerFrames();

        // clear some things before going to next level
        for (int i = 0; i < game.maxclients; i++) {
            SubgameEntity ent = g_edicts[1 + i];
            if (!ent.inuse)
                continue;
            gclient_t client = ent.getClient();
            if (ent.health > client.pers.max_health)
                ent.health = client.pers.max_health;
        }
    }

    /**
     * ClientEndServerFrames.
     * Call playerView.ClientEndServerFrame for all clients
     */
    private void ClientEndServerFrames() {

        // calc the player views now that all pushing
        // and damage has been added
        for (int i = 0; i < game.maxclients; i++) {
            SubgameEntity ent = g_edicts[1 + i];
            if (!ent.inuse || null == ent.getClient()) {
                continue;
            }
            playerView.ClientEndServerFrame(ent, this);
        }

    }

    /**
     * This will be called once for each server frame, before running any other
     * entities in the world.
     */
    private void ClientBeginServerFrame(SubgameEntity ent) {

        if (level.intermissiontime != 0)
            return;

        gclient_t client = ent.getClient();

        if (gameCvars.deathmatch.value != 0
                && client.pers.spectator != client.resp.spectator
                && (level.time - client.respawn_time) >= 5) {
            spectatorRespawn(ent);
            return;
        }

        // run weapon animations if it hasn't been done by a ucmd_t
        if (!client.weapon_thunk && !client.resp.spectator)
            PlayerWeapon.Think_Weapon(ent, this);
        else
            client.weapon_thunk = false;

        if (ent.deadflag != 0) {
            // wait for any button just going down
            if (level.time > client.respawn_time) {
                // in deathmatch, only wait for attack button
                int buttonMask;
                if (gameCvars.deathmatch.value != 0)
                    buttonMask = Defines.BUTTON_ATTACK;
                else
                    buttonMask = -1;

                if ((client.latched_buttons & buttonMask) != 0
                        || (gameCvars.deathmatch.value != 0 && 0 != ((int) gameCvars.dmflags.value & Defines.DF_FORCE_RESPAWN))) {
                    respawn(ent, this);
                    client.latched_buttons = 0;
                }
            }
            return;
        }

        // add player trail so monsters can follow
        if (gameCvars.deathmatch.value != 0)
            if (!GameUtil.visible(ent, playerTrail.LastSpot(), this))
                playerTrail.Add(ent.s.old_origin, level.time);

        client.latched_buttons = 0;
    }

    /**
     * Only called when pers.spectator changes note that resp.spectator should
     * be the opposite of pers.spectator here
     */
    private void spectatorRespawn(SubgameEntity ent) {

        // if the user wants to become a spectator, make sure he doesn't
        // exceed max_spectators

        gclient_t client = ent.getClient();
        if (client.pers.spectator) {
            String spectator = Info.Info_ValueForKey(client.pers.userinfo, "spectator");

            if (!passwdOK(gameCvars.spectator_password.string, spectator)) {
                gameImports.cprintf(ent, Defines.PRINT_HIGH, "Spectator password incorrect.\n");
                client.pers.spectator = false;
                gameImports.WriteByte(NetworkCommandType.svc_stufftext);
                gameImports.WriteString("spectator 0\n");
                gameImports.unicast(ent, true);
                return;
            }

            // count spectators
            int numspec;
            int i;
            for (i = 1, numspec = 0; i <= game.maxclients; i++) {
                gclient_t other = g_edicts[i].getClient();
                if (g_edicts[i].inuse && other.pers.spectator)
                    numspec++;
            }

            if (numspec >= gameCvars.maxspectators.value) {
                gameImports.cprintf(ent, Defines.PRINT_HIGH,
                        "Server spectator limit is full.");
                client.pers.spectator = false;
                // reset his spectator var
                gameImports.WriteByte(NetworkCommandType.svc_stufftext);
                gameImports.WriteString("spectator 0\n");
                gameImports.unicast(ent, true);
                return;
            }
        } else {
            // he was a spectator and wants to join the game
            // he must have the right password
            String password = Info.Info_ValueForKey(client.pers.userinfo, "password");
            if (!passwdOK(gameCvars.password.string, password)) {
                gameImports.cprintf(ent, Defines.PRINT_HIGH, "Password incorrect.\n");
                client.pers.spectator = true;
                gameImports.WriteByte(NetworkCommandType.svc_stufftext);
                gameImports.WriteString("spectator 1\n");
                gameImports.unicast(ent, true);
                return;
            }
        }

        // clear client on respawn
        client.resp.score = client.pers.score = 0;

        ent.svflags &= ~Defines.SVF_NOCLIENT;
        PutClientInServer(ent, this);

        // add a teleportation effect
        if (!client.pers.spectator) {
            // send effect
            gameImports.WriteByte(NetworkCommandType.svc_muzzleflash);
            //gi.WriteShort(ent - g_edicts);
            gameImports.WriteShort(ent.index);

            gameImports.WriteByte(Defines.MZ_LOGIN);
            gameImports.multicast(ent.s.origin, MulticastTypes.MULTICAST_PVS);

            // hold in place briefly
            client.getPlayerState().pmove.pm_flags = Defines.PMF_TIME_TELEPORT;
            client.getPlayerState().pmove.pm_time = 14;
        }

        client.respawn_time = level.time;

        if (client.pers.spectator)
            gameImports.bprintf(Defines.PRINT_HIGH, client.pers.netname + " has moved to the sidelines\n");
        else
            gameImports.bprintf(Defines.PRINT_HIGH, client.pers.netname + " joined the game\n");
    }

    /**
     * G_RunEntity
     */
    private void runEntity(SubgameEntity ent) {

        // call prethink handler
        if (ent.prethink != null)
            ent.prethink.think(ent, this);

        switch (ent.movetype) {
            case GameDefines.MOVETYPE_PUSH:
            case GameDefines.MOVETYPE_STOP:
                SV.SV_Physics_Pusher(ent, this);
                break;
            case GameDefines.MOVETYPE_NONE:
                SV.SV_Physics_None(ent, this);
                break;
            case GameDefines.MOVETYPE_NOCLIP:
                SV.SV_Physics_Noclip(ent, this);
                break;
            case GameDefines.MOVETYPE_STEP:
                sv.SV_Physics_Step(ent, this);
                break;
            case GameDefines.MOVETYPE_TOSS:
            case GameDefines.MOVETYPE_BOUNCE:
            case GameDefines.MOVETYPE_FLY:
            case GameDefines.MOVETYPE_FLYMISSILE:
                sv.SV_Physics_Toss(ent, this);
                break;
            default:
                gameImports.error("SV_Physics: bad movetype " + (int) ent.movetype);
        }
    }

    /**
     * CheckDMRules.
     */
    private void CheckDMRules() {
        int i;
        gclient_t cl;

        if (level.intermissiontime != 0)
            return;

        if (0 == gameCvars.deathmatch.value)
            return;

        if (gameCvars.timelimit.value != 0) {
            if (level.time >= gameCvars.timelimit.value * 60) {
                gameImports.bprintf(Defines.PRINT_HIGH, "Timelimit hit.\n");
                EndDMLevel();
                return;
            }
        }

        if (gameCvars.fraglimit.value != 0) {
            for (i = 0; i < game.maxclients; i++) {
                cl = game.clients[i];
                if (!g_edicts[i + 1].inuse)
                    continue;

                if (cl.resp.score >= gameCvars.fraglimit.value) {
                    gameImports.bprintf(Defines.PRINT_HIGH, "Fraglimit hit.\n");
                    EndDMLevel();
                    return;
                }
            }
        }
    }

    /**
     * The timelimit or fraglimit has been exceeded.
     */
    private void EndDMLevel() {
        //char * s, * t, * f;
        //static const char * seps = " ,\n\r";
        String seps = " ,\n\r";

        // stay on same level flag
        if (((int) gameCvars.dmflags.value & Defines.DF_SAME_LEVEL) != 0) {
            PlayerHud.BeginIntermission(CreateTargetChangeLevel(level.mapname), this);
            return;
        }

        // see if it's in the map list
        if (gameCvars.sv_maplist.string.length() > 0) {
            String mapList = gameCvars.sv_maplist.string;
            // todo: cleanup parsing of maplist
            String f = null;
            StringTokenizer tk = new StringTokenizer(mapList, seps);
            while (tk.hasMoreTokens()){
                String t = tk.nextToken();

                // store first map
                if (f == null)
                    f = t;

                if (t.equalsIgnoreCase(level.mapname)) {
                    // it's in the list, go to the next one
                    if (!tk.hasMoreTokens()) {
                        // end of list, go to first one
                        if (f == null) // there isn't a first one, same gameExports.level
                            PlayerHud.BeginIntermission(CreateTargetChangeLevel(level.mapname), this);
                        else
                            PlayerHud.BeginIntermission(CreateTargetChangeLevel(f), this);
                    } else
                        PlayerHud.BeginIntermission(CreateTargetChangeLevel(tk.nextToken()), this);
                    return;
                }
            }
        }

        //not in the map list
        if (level.nextmap.length() > 0) // go to a specific map
            PlayerHud.BeginIntermission(CreateTargetChangeLevel(level.nextmap), this);
        else { // search for a changelevel
            EdictIterator edit = null;
            edit = G_Find(edit, findByClass, "target_changelevel", this);
            if (edit == null) { // the map designer didn't include a
                // change level,
                // so create a fake ent that goes back to the same level
                PlayerHud.BeginIntermission(CreateTargetChangeLevel(level.mapname), this);
                return;
            }
            PlayerHud.BeginIntermission(edit.o, this);
        }
    }

    /**
     * CheckNeedPass.
     */
    private void checkNeedPassCvar() {
        // if password or spectator_password has changed, update needpass
        // as needed
        if (gameCvars.password.modified || gameCvars.spectator_password.modified) {

            gameCvars.password.modified = false;
            gameCvars.spectator_password.modified = false;

            int need = 0;

            if (!gameCvars.password.string.isEmpty() && !"none".equalsIgnoreCase(gameCvars.password.string))
                need |= 1;

            if (!gameCvars.spectator_password.string.isEmpty() && !"none".equalsIgnoreCase(gameCvars.spectator_password.string))
                need |= 2;

            gameImports.cvar_set("needpass", "" + need);
        }
    }

    /**
     * Returns the created target changelevel.
     */
    private SubgameEntity CreateTargetChangeLevel(String map) {
        SubgameEntity ent = GameUtil.G_Spawn(this);
        ent.classname = "target_changelevel";
        level.nextmap = map;
        ent.map = map;
        return ent;
    }

    @Override
    public void ClientCommand(edict_t player, List<String> args) {
        SubgameEntity ent = g_edicts[player.index];

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

        if (level.intermissiontime != 0)
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
                GameItems.SelectNextItem(ent, -1, this);
                break;
            case "invprev":
                GameItems.SelectPrevItem(ent, -1, this);
                break;
            case "invnextw":
                GameItems.SelectNextItem(ent, GameDefines.IT_WEAPON, this);
                break;
            case "invprevw":
                GameItems.SelectPrevItem(ent, GameDefines.IT_WEAPON, this);
                break;
            case "invnextp":
                GameItems.SelectNextItem(ent, GameDefines.IT_POWERUP, this);
                break;
            case "invprevp":
                GameItems.SelectPrevItem(ent, GameDefines.IT_POWERUP, this);
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
                Kill_f(ent, this);
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
                GameSpawn.SpawnNewEntity(ent, args, this);
                break;
            case "spawnrandommonster":
                GameSpawn.SpawnRandomMonster(ent, this);
                break;
            default:
                // anything that doesn't match a command will be a chat
                Say_f(ent, false, true, args);
                break;
        }
    }

    @Override
    public void G_RunFrame() {
        level.framenum++;
        level.time = level.framenum * Defines.FRAMETIME;

        // choose a client for monsters to target this frame
        GameAI.AI_SetSightClient(this);

        // exit intermissions
        if (level.exitintermission) {
            // expected level.changemap != null
            exitLevel();
            return;
        }

        //
        // treat each object in turn
        // even the world gets a chance to think
        for (int i = 0; i < num_edicts; i++) {
            SubgameEntity ent = g_edicts[i];
            if (!ent.inuse)
                continue;

            level.current_entity = ent;

            Math3D.VectorCopy(ent.s.origin, ent.s.old_origin);

            // if the ground entity moved, make sure we are still on it
            if (ent.groundentity != null && ent.groundentity.linkcount != ent.groundentity_linkcount) {
                ent.groundentity = null;
                if (0 == (ent.flags & (GameDefines.FL_SWIM | GameDefines.FL_FLY)) && (ent.svflags & Defines.SVF_MONSTER) != 0) {
                    M.M_CheckGround(ent, this);
                }
            }

            if (i > 0 && i <= game.maxclients) {
                ClientBeginServerFrame(ent);
                continue;
            }

            runEntity(ent);
        }

        // see if it is time to end a deathmatch
        CheckDMRules();

        // see if needpass needs updated
        checkNeedPassCvar();

        // build the playerstate_t structures for all players
        ClientEndServerFrames();
    }

    @Override
    public void WriteGame(String filename, boolean autosave) {
        try {

            if (!autosave)
                PlayerClient.SaveClientData(this);

            QuakeFile f = new QuakeFile(filename, "rw");

            game.autosaved = autosave;
            game.write(f);
            game.autosaved = false;

            for (int i = 0; i < game.maxclients; i++)
                game.clients[i].write(f);

            Lib.fclose(f);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void readGameLocals(String filename) {

        try {

            QuakeFile f = new QuakeFile(filename, "r");

            game.load(f);

            for (int i = 0; i < game.maxclients; i++) {
                game.clients[i] = new gclient_t(i);
                game.clients[i].read(f, g_edicts, this);
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
            level.write(f);

            // write out all the entities
            for (int i = 0; i < num_edicts; i++) {
                SubgameEntity ent = g_edicts[i];
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

            num_edicts = game.maxclients + 1;

            // load the level locals
            level.read(f, g_edicts);

            // load all the entities
            while (true) {
                int entnum = f.readInt();
                if (entnum == -1)
                    break;

                if (entnum >= num_edicts)
                    num_edicts = entnum + 1;

                SubgameEntity ent = g_edicts[entnum];
                ent.read(f, g_edicts, game.clients, this);
                ent.cleararealinks();
                gameImports.linkentity(ent);
            }

            Lib.fclose(f);

            // mark all clients as unconnected
            for (int i = 0; i < game.maxclients; i++) {
                SubgameEntity ent = g_edicts[i + 1];
                ent.setClient(game.clients[i]);
                gclient_t client = ent.getClient();
                client.pers.connected = false;
            }

            // do any load time things at this point
            for (int i = 0; i < num_edicts; i++) {
                SubgameEntity ent = g_edicts[i];

                if (!ent.inuse)
                    continue;

                // fire any cross-level triggers
                if ("target_crosslevel_target".equals(ent.classname))
                    ent.nextthink = level.time + ent.delay;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void SpawnEntities(String mapname, String entities, String spawnpoint) {
        GameSpawn.SpawnEntities(mapname, entities, spawnpoint, this);
    }

    @Override
    public void ServerCommand(List<String> args) {
        GameSVCmds.ServerCommand(args, this);
    }

    @Override
    public void ClientBegin(edict_t e) {
        PlayerClient.ClientBegin(g_edicts[e.index], this);
    }

    @Override
    public String ClientUserinfoChanged(edict_t ent, String userinfo) {
        return PlayerClient.ClientUserinfoChanged((SubgameEntity) ent, userinfo, this);
    }

    @Override
    public boolean ClientConnect(edict_t ent, String userinfo) {
        return PlayerClient.ClientConnect((SubgameEntity) ent, userinfo, this);
    }

    @Override
    public void ClientDisconnect(edict_t ent) {
        PlayerClient.ClientDisconnect((SubgameEntity) ent, gameImports);
    }

    @Override
    public void ClientThink(edict_t ent, usercmd_t ucmd) {
        SubgameEntity e = g_edicts[ent.index];
        PlayerClient.ClientThink(e, ucmd, this);
    }

    @Override
    public edict_t getEdict(int index) {
        return g_edicts[index];
    }

    @Override
    public int getNumEdicts() {
        return num_edicts;
    }

}
