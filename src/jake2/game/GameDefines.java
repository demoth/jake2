package jake2.game;

public interface GameDefines {
    // item spawnflags
    int ITEM_TRIGGER_SPAWN = 0x00000001;
    int ITEM_NO_TOUCH = 0x00000002;

    // 6 bits reserved for editor flags
    // 8 bits used as power cube id bits for coop games
    int DROPPED_ITEM = 0x00010000;
    int DROPPED_PLAYER_ITEM = 0x00020000;
    int ITEM_TARGETS_USED = 0x00040000;
    int HEALTH_IGNORE_MAX = 1;
    int HEALTH_TIMED = 2;

    float GRENADE_TIMER = 3.0f;
    int GRENADE_MINSPEED = 400;
    int GRENADE_MAXSPEED = 800;

    int DEFAULT_BULLET_HSPREAD = 300;
    int DEFAULT_BULLET_VSPREAD = 500;
    int DEFAULT_SHOTGUN_HSPREAD = 1000;
    int DEFAULT_SHOTGUN_VSPREAD = 500;
    int DEFAULT_DEATHMATCH_SHOTGUN_COUNT = 12;
    int DEFAULT_SHOTGUN_COUNT = 12;
    int DEFAULT_SSHOTGUN_COUNT = 20;

    // ammo types
    // todo enum
    int AMMO_BULLETS = 0;
    int AMMO_SHELLS = 1;
    int AMMO_ROCKETS = 2;
    int AMMO_GRENADES = 3;
    int AMMO_CELLS = 4;
    int AMMO_SLUGS = 5;

    //	gitem_t->weapmodel for weapons indicates model index
    int WEAP_BLASTER = 1;
    int WEAP_SHOTGUN = 2;
    int WEAP_SUPERSHOTGUN = 3;
    int WEAP_MACHINEGUN = 4;
    int WEAP_CHAINGUN = 5;
    int WEAP_GRENADES = 6;
    int WEAP_GRENADELAUNCHER = 7;
    int WEAP_ROCKETLAUNCHER = 8;
    int WEAP_HYPERBLASTER = 9;
    int WEAP_RAILGUN = 10;
    int WEAP_BFG = 11;

    //	 armor types
    // todo enum
    int ARMOR_NONE = 0;
    int ARMOR_JACKET = 1;
    int ARMOR_COMBAT = 2;
    int ARMOR_BODY = 3;
    int ARMOR_SHARD = 4;

    //	 power armor types
    // todo enum
    int POWER_ARMOR_NONE = 0;
    int POWER_ARMOR_SCREEN = 1;
    int POWER_ARMOR_SHIELD = 2;

    //	means of death
    // todo enum
    int MOD_UNKNOWN = 0;
    int MOD_BLASTER = 1;
    int MOD_SHOTGUN = 2;
    int MOD_SSHOTGUN = 3;
    int MOD_MACHINEGUN = 4;
    int MOD_CHAINGUN = 5;
    int MOD_GRENADE = 6;
    int MOD_G_SPLASH = 7;
    int MOD_ROCKET = 8;
    int MOD_R_SPLASH = 9;
    int MOD_HYPERBLASTER = 10;
    int MOD_RAILGUN = 11;
    int MOD_BFG_LASER = 12;
    int MOD_BFG_BLAST = 13;
    int MOD_BFG_EFFECT = 14;
    int MOD_HANDGRENADE = 15;
    int MOD_HG_SPLASH = 16;
    int MOD_WATER = 17;
    int MOD_SLIME = 18;
    int MOD_LAVA = 19;
    int MOD_CRUSH = 20;
    int MOD_TELEFRAG = 21;
    int MOD_FALLING = 22;
    int MOD_SUICIDE = 23;
    int MOD_HELD_GRENADE = 24;
    int MOD_EXPLOSIVE = 25;
    int MOD_BARREL = 26;
    int MOD_BOMB = 27;
    int MOD_EXIT = 28;
    int MOD_SPLASH = 29;
    int MOD_TARGET_LASER = 30;
    int MOD_TRIGGER_HURT = 31;
    int MOD_HIT = 32;
    int MOD_TARGET_BLASTER = 33;
    int MOD_FRIENDLY_FIRE = 0x8000000;

    // edict->spawnflags
    // these are set with checkboxes on each entity in the map editor
    int SPAWNFLAG_NOT_EASY = 0x00000100;
    int SPAWNFLAG_NOT_MEDIUM = 0x00000200;
    int SPAWNFLAG_NOT_HARD = 0x00000400;
    int SPAWNFLAG_NOT_DEATHMATCH = 0x00000800;
    int SPAWNFLAG_NOT_COOP = 0x00001000;

    int MELEE_DISTANCE = 80;
    int BODY_QUEUE_SIZE = 8;

    //	deadflag
    // todo enum
    int DEAD_NO = 0;
    int DEAD_DYING = 1;
    int DEAD_DEAD = 2;
    int DEAD_RESPAWNABLE = 3;

    //	range
    // todo enum
    int RANGE_MELEE = 0;
    int RANGE_NEAR = 1;
    int RANGE_MID = 2;
    int RANGE_FAR = 3;

    //	gib types
    // todo enum
    int GIB_ORGANIC = 0;
    int GIB_METALLIC = 1;

    //	monster ai flags
    int AI_STAND_GROUND = 0x00000001;
    int AI_TEMP_STAND_GROUND = 0x00000002;
    int AI_SOUND_TARGET = 0x00000004;
    int AI_LOST_SIGHT = 0x00000008;
    int AI_PURSUIT_LAST_SEEN = 0x00000010;
    int AI_PURSUE_NEXT = 0x00000020;
    int AI_PURSUE_TEMP = 0x00000040;
    int AI_HOLD_FRAME = 0x00000080;
    int AI_GOOD_GUY = 0x00000100;
    int AI_BRUTAL = 0x00000200;
    int AI_NOSTEP = 0x00000400;
    int AI_DUCKED = 0x00000800;
    int AI_COMBAT_POINT = 0x00001000;
    int AI_MEDIC = 0x00002000;
    int AI_RESURRECTING = 0x00004000;

    //	 noise types for PlayerNoise
    int PNOISE_SELF = 0;
    int PNOISE_WEAPON = 1;
    int PNOISE_IMPACT = 2;

    //	gitem_t->flags
    int IT_WEAPON = 1; // use makes active weapon
    int IT_AMMO = 2;
    int IT_ARMOR = 4;
    int IT_STAY_COOP = 8;
    int IT_KEY = 16;
    int IT_POWERUP = 32;

    //	edict->movetype values
    // todo enum
    int MOVETYPE_NONE = 0; // never moves
    int MOVETYPE_NOCLIP = 1; // origin and angles change with no interaction
    int MOVETYPE_PUSH = 2; // no clip to world, push on box contact
    int MOVETYPE_STOP = 3; // no clip to world, stops on box contact
    int MOVETYPE_WALK = 4; // gravity
    int MOVETYPE_STEP = 5; // gravity, special edge handling
    int MOVETYPE_FLY = 6;
    int MOVETYPE_TOSS = 7; // gravity
    int MOVETYPE_FLYMISSILE = 8; // extra size to monsters
    int MOVETYPE_BOUNCE = 9;

    //	monster attack state
    int AS_STRAIGHT = 1;
    int AS_SLIDING = 2;
    int AS_MELEE = 3;
    int AS_MISSILE = 4;
}
