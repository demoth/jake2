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

// Created on 31.10.2003 by RST.
// $Id: Defines.java,v 1.6 2004-10-04 12:50:38 hzi Exp $

/** Contains the definitions for the game engine. */

package jake2;


public class Defines {

	public final static int WEAPON_READY = 0;
	public final static int WEAPON_ACTIVATING = 1;
	public final static int WEAPON_DROPPING = 2;
	public final static int WEAPON_FIRING = 3;

	public final static float GRENADE_TIMER = 3.0f;
	public final static int GRENADE_MINSPEED = 400;
	public final static int GRENADE_MAXSPEED = 800;

	// -----------------
	// client/q_shared.h

	// can accelerate and turn
	public final static int PM_NORMAL = 0;
	public final static int PM_SPECTATOR = 1;
	// no acceleration or turning
	public final static int PM_DEAD = 2;
	public final static int PM_GIB = 3; // different bounding box 
	public final static int PM_FREEZE = 4;

	public final static int EV_NONE = 0;
	public final static int EV_ITEM_RESPAWN = 1;
	public final static int EV_FOOTSTEP = 2;
	public final static int EV_FALLSHORT = 3;
	public final static int EV_FALL = 4;
	public final static int EV_FALLFAR = 5;
	public final static int EV_PLAYER_TELEPORT = 6;
	public final static int EV_OTHER_TELEPORT = 7;

	//	angle indexes
	public final static int PITCH = 0; // up / down 
	public final static int YAW = 1; // left / right 
	public final static int ROLL = 2; // fall over 

	public final static int MAX_STRING_CHARS = 1024; // max length of a string passed to Cmd_TokenizeString 
	public final static int MAX_STRING_TOKENS = 80; // max tokens resulting from Cmd_TokenizeString 
	public final static int MAX_TOKEN_CHARS = 128; // max length of an individual token 

	public final static int MAX_QPATH = 64; // max length of a quake game pathname 
	public final static int MAX_OSPATH = 128; // max length of a filesystem pathname 

	//	per-level limits
	public final static int MAX_CLIENTS = 256; // absolute limit 
	public final static int MAX_EDICTS = 1024; // must change protocol to increase more 
	public final static int MAX_LIGHTSTYLES = 256;
	public final static int MAX_MODELS = 256; // these are sent over the net as bytes 
	public final static int MAX_SOUNDS = 256; // so they cannot be blindly increased 
	public final static int MAX_IMAGES = 256;
	public final static int MAX_ITEMS = 256;
	public final static int MAX_GENERAL = (MAX_CLIENTS * 2); // general config strings 

	//	game print flags
	public final static int PRINT_LOW = 0; // pickup messages 
	public final static int PRINT_MEDIUM = 1; // death messages 
	public final static int PRINT_HIGH = 2; // critical messages 
	public final static int PRINT_CHAT = 3; // chat messages 

	public final static int ERR_FATAL = 0; // exit the entire game with a popup window 
	public final static int ERR_DROP = 1; // print to console and disconnect from game 
	public final static int ERR_DISCONNECT = 2; // don't kill server 

	public final static int PRINT_ALL = 0;
	public final static int PRINT_DEVELOPER = 1; // only print when "developer 1" 
	public final static int PRINT_ALERT = 2;

	//	key / value info strings
	public final static int MAX_INFO_KEY = 64;
	public final static int MAX_INFO_VALUE = 64;
	public final static int MAX_INFO_STRING = 512;

	// directory searching
	public final static int SFF_ARCH = 0x01;
	public final static int SFF_HIDDEN = 0x02;
	public final static int SFF_RDONLY = 0x04;
	public final static int SFF_SUBDIR = 0x08;
	public final static int SFF_SYSTEM = 0x10;

	public final static int CVAR_ARCHIVE = 1; // set to cause it to be saved to vars.rc 
	public final static int CVAR_USERINFO = 2; // added to userinfo when changed 
	public final static int CVAR_SERVERINFO = 4; // added to serverinfo when changed 
	public final static int CVAR_NOSET = 8; // don't allow change from console at all, 
	// but can be set from the command line
	public final static int CVAR_LATCH = 16; // save changes until server restart 

	// lower bits are stronger, and will eat weaker brushes completely
	public final static int CONTENTS_SOLID = 1; // an eye is never valid in a solid 
	public final static int CONTENTS_WINDOW = 2; // translucent, but not watery 
	public final static int CONTENTS_AUX = 4;
	public final static int CONTENTS_LAVA = 8;
	public final static int CONTENTS_SLIME = 16;
	public final static int CONTENTS_WATER = 32;
	public final static int CONTENTS_MIST = 64;
	public final static int LAST_VISIBLE_CONTENTS = 64;

	// remaining contents are non-visible, and don't eat brushes
	public final static int CONTENTS_AREAPORTAL = 0x8000;

	public final static int CONTENTS_PLAYERCLIP = 0x10000;
	public final static int CONTENTS_MONSTERCLIP = 0x20000;

	// currents can be added to any other contents, and may be mixed
	public final static int CONTENTS_CURRENT_0 = 0x40000;
	public final static int CONTENTS_CURRENT_90 = 0x80000;
	public final static int CONTENTS_CURRENT_180 = 0x100000;
	public final static int CONTENTS_CURRENT_270 = 0x200000;
	public final static int CONTENTS_CURRENT_UP = 0x400000;
	public final static int CONTENTS_CURRENT_DOWN = 0x800000;

	public final static int CONTENTS_ORIGIN = 0x1000000; // removed before bsping an entity 

	public final static int CONTENTS_MONSTER = 0x2000000; // should never be on a brush, only in game 
	public final static int CONTENTS_DEADMONSTER = 0x4000000;
	public final static int CONTENTS_DETAIL = 0x8000000; // brushes to be added after vis leafs 
	public final static int CONTENTS_TRANSLUCENT = 0x10000000; // auto set if any surface has trans 
	public final static int CONTENTS_LADDER = 0x20000000;

	public final static int SURF_LIGHT = 0x1; // value will hold the light strength 
	public final static int SURF_SLICK = 0x2; // effects game physics 

	public final static int SURF_SKY = 0x4; // don't draw, but add to skybox 
	public final static int SURF_WARP = 0x8; // turbulent water warp 
	public final static int SURF_TRANS33 = 0x10;
	public final static int SURF_TRANS66 = 0x20;
	public final static int SURF_FLOWING = 0x40; // scroll towards angle 
	public final static int SURF_NODRAW = 0x80; // don't bother referencing the texture 

	//
	// button bits
	//
	public final static int BUTTON_ATTACK = 1;
	public final static int BUTTON_USE = 2;
	public final static int BUTTON_ANY = 128; // any key whatsoever 

	public final static int MAXTOUCH = 32;

	// entity_state_t->effects
	// Effects are things handled on the client side (lights, particles, frame animations)
	// that happen constantly on the given entity.
	// An entity that has effects will be sent to the client
	// even if it has a zero index model.
	public final static int EF_ROTATE = 0x00000001; // rotate (bonus items) 
	public final static int EF_GIB = 0x00000002; // leave a trail 
	public final static int EF_BLASTER = 0x00000008; // redlight + trail 
	public final static int EF_ROCKET = 0x00000010; // redlight + trail 
	public final static int EF_GRENADE = 0x00000020;
	public final static int EF_HYPERBLASTER = 0x00000040;
	public final static int EF_BFG = 0x00000080;
	public final static int EF_COLOR_SHELL = 0x00000100;
	public final static int EF_POWERSCREEN = 0x00000200;
	public final static int EF_ANIM01 = 0x00000400; // automatically cycle between frames 0 and 1 at 2 hz 
	public final static int EF_ANIM23 = 0x00000800; // automatically cycle between frames 2 and 3 at 2 hz 
	public final static int EF_ANIM_ALL = 0x00001000; // automatically cycle through all frames at 2hz 
	public final static int EF_ANIM_ALLFAST = 0x00002000; // automatically cycle through all frames at 10hz 
	public final static int EF_FLIES = 0x00004000;
	public final static int EF_QUAD = 0x00008000;
	public final static int EF_PENT = 0x00010000;
	public final static int EF_TELEPORTER = 0x00020000; // particle fountain 
	public final static int EF_FLAG1 = 0x00040000;
	public final static int EF_FLAG2 = 0x00080000;
	// RAFAEL
	public final static int EF_IONRIPPER = 0x00100000;
	public final static int EF_GREENGIB = 0x00200000;
	public final static int EF_BLUEHYPERBLASTER = 0x00400000;
	public final static int EF_SPINNINGLIGHTS = 0x00800000;
	public final static int EF_PLASMA = 0x01000000;
	public final static int EF_TRAP = 0x02000000;

	//ROGUE
	public final static int EF_TRACKER = 0x04000000;
	public final static int EF_DOUBLE = 0x08000000;
	public final static int EF_SPHERETRANS = 0x10000000;
	public final static int EF_TAGTRAIL = 0x20000000;
	public final static int EF_HALF_DAMAGE = 0x40000000;
	public final static int EF_TRACKERTRAIL = 0x80000000;
	//ROGUE

	// entity_state_t->renderfx flags
	public final static int RF_MINLIGHT = 1; // allways have some light (viewmodel) 
	public final static int RF_VIEWERMODEL = 2; // don't draw through eyes, only mirrors 
	public final static int RF_WEAPONMODEL = 4; // only draw through eyes 
	public final static int RF_FULLBRIGHT = 8; // allways draw full intensity 
	public final static int RF_DEPTHHACK = 16; // for view weapon Z crunching 
	public final static int RF_TRANSLUCENT = 32;
	public final static int RF_FRAMELERP = 64;
	public final static int RF_BEAM = 128;
	public final static int RF_CUSTOMSKIN = 256; // skin is an index in image_precache 
	public final static int RF_GLOW = 512; // pulse lighting for bonus items 
	public final static int RF_SHELL_RED = 1024;
	public final static int RF_SHELL_GREEN = 2048;
	public final static int RF_SHELL_BLUE = 4096;

	//ROGUE
	public final static int RF_IR_VISIBLE = 0x00008000; // 32768 
	public final static int RF_SHELL_DOUBLE = 0x00010000; // 65536 
	public final static int RF_SHELL_HALF_DAM = 0x00020000;
	public final static int RF_USE_DISGUISE = 0x00040000;
	//ROGUE

	// player_state_t->refdef flags
	public final static int RDF_UNDERWATER = 1; // warp the screen as apropriate 
	public final static int RDF_NOWORLDMODEL = 2; // used for player configuration screen 

	//ROGUE
	public final static int RDF_IRGOGGLES = 4;
	public final static int RDF_UVGOGGLES = 8;
	//ROGUE

	// muzzle flashes / player effects
	public final static int MZ_BLASTER = 0;
	public final static int MZ_MACHINEGUN = 1;
	public final static int MZ_SHOTGUN = 2;
	public final static int MZ_CHAINGUN1 = 3;
	public final static int MZ_CHAINGUN2 = 4;
	public final static int MZ_CHAINGUN3 = 5;
	public final static int MZ_RAILGUN = 6;
	public final static int MZ_ROCKET = 7;
	public final static int MZ_GRENADE = 8;
	public final static int MZ_LOGIN = 9;
	public final static int MZ_LOGOUT = 10;
	public final static int MZ_RESPAWN = 11;
	public final static int MZ_BFG = 12;
	public final static int MZ_SSHOTGUN = 13;
	public final static int MZ_HYPERBLASTER = 14;
	public final static int MZ_ITEMRESPAWN = 15;
	// RAFAEL
	public final static int MZ_IONRIPPER = 16;
	public final static int MZ_BLUEHYPERBLASTER = 17;
	public final static int MZ_PHALANX = 18;
	public final static int MZ_SILENCED = 128; // bit flag ORed with one of the above numbers 

	//ROGUE
	public final static int MZ_ETF_RIFLE = 30;
	public final static int MZ_UNUSED = 31;
	public final static int MZ_SHOTGUN2 = 32;
	public final static int MZ_HEATBEAM = 33;
	public final static int MZ_BLASTER2 = 34;
	public final static int MZ_TRACKER = 35;
	public final static int MZ_NUKE1 = 36;
	public final static int MZ_NUKE2 = 37;
	public final static int MZ_NUKE4 = 38;
	public final static int MZ_NUKE8 = 39;
	//ROGUE

	//
	// monster muzzle flashes
	//
	public final static int MZ2_TANK_BLASTER_1 = 1;
	public final static int MZ2_TANK_BLASTER_2 = 2;
	public final static int MZ2_TANK_BLASTER_3 = 3;
	public final static int MZ2_TANK_MACHINEGUN_1 = 4;
	public final static int MZ2_TANK_MACHINEGUN_2 = 5;
	public final static int MZ2_TANK_MACHINEGUN_3 = 6;
	public final static int MZ2_TANK_MACHINEGUN_4 = 7;
	public final static int MZ2_TANK_MACHINEGUN_5 = 8;
	public final static int MZ2_TANK_MACHINEGUN_6 = 9;
	public final static int MZ2_TANK_MACHINEGUN_7 = 10;
	public final static int MZ2_TANK_MACHINEGUN_8 = 11;
	public final static int MZ2_TANK_MACHINEGUN_9 = 12;
	public final static int MZ2_TANK_MACHINEGUN_10 = 13;
	public final static int MZ2_TANK_MACHINEGUN_11 = 14;
	public final static int MZ2_TANK_MACHINEGUN_12 = 15;
	public final static int MZ2_TANK_MACHINEGUN_13 = 16;
	public final static int MZ2_TANK_MACHINEGUN_14 = 17;
	public final static int MZ2_TANK_MACHINEGUN_15 = 18;
	public final static int MZ2_TANK_MACHINEGUN_16 = 19;
	public final static int MZ2_TANK_MACHINEGUN_17 = 20;
	public final static int MZ2_TANK_MACHINEGUN_18 = 21;
	public final static int MZ2_TANK_MACHINEGUN_19 = 22;
	public final static int MZ2_TANK_ROCKET_1 = 23;
	public final static int MZ2_TANK_ROCKET_2 = 24;
	public final static int MZ2_TANK_ROCKET_3 = 25;

	public final static int MZ2_INFANTRY_MACHINEGUN_1 = 26;
	public final static int MZ2_INFANTRY_MACHINEGUN_2 = 27;
	public final static int MZ2_INFANTRY_MACHINEGUN_3 = 28;
	public final static int MZ2_INFANTRY_MACHINEGUN_4 = 29;
	public final static int MZ2_INFANTRY_MACHINEGUN_5 = 30;
	public final static int MZ2_INFANTRY_MACHINEGUN_6 = 31;
	public final static int MZ2_INFANTRY_MACHINEGUN_7 = 32;
	public final static int MZ2_INFANTRY_MACHINEGUN_8 = 33;
	public final static int MZ2_INFANTRY_MACHINEGUN_9 = 34;
	public final static int MZ2_INFANTRY_MACHINEGUN_10 = 35;
	public final static int MZ2_INFANTRY_MACHINEGUN_11 = 36;
	public final static int MZ2_INFANTRY_MACHINEGUN_12 = 37;
	public final static int MZ2_INFANTRY_MACHINEGUN_13 = 38;

	public final static int MZ2_SOLDIER_BLASTER_1 = 39;
	public final static int MZ2_SOLDIER_BLASTER_2 = 40;
	public final static int MZ2_SOLDIER_SHOTGUN_1 = 41;
	public final static int MZ2_SOLDIER_SHOTGUN_2 = 42;
	public final static int MZ2_SOLDIER_MACHINEGUN_1 = 43;
	public final static int MZ2_SOLDIER_MACHINEGUN_2 = 44;

	public final static int MZ2_GUNNER_MACHINEGUN_1 = 45;
	public final static int MZ2_GUNNER_MACHINEGUN_2 = 46;
	public final static int MZ2_GUNNER_MACHINEGUN_3 = 47;
	public final static int MZ2_GUNNER_MACHINEGUN_4 = 48;
	public final static int MZ2_GUNNER_MACHINEGUN_5 = 49;
	public final static int MZ2_GUNNER_MACHINEGUN_6 = 50;
	public final static int MZ2_GUNNER_MACHINEGUN_7 = 51;
	public final static int MZ2_GUNNER_MACHINEGUN_8 = 52;
	public final static int MZ2_GUNNER_GRENADE_1 = 53;
	public final static int MZ2_GUNNER_GRENADE_2 = 54;
	public final static int MZ2_GUNNER_GRENADE_3 = 55;
	public final static int MZ2_GUNNER_GRENADE_4 = 56;

	public final static int MZ2_CHICK_ROCKET_1 = 57;

	public final static int MZ2_FLYER_BLASTER_1 = 58;
	public final static int MZ2_FLYER_BLASTER_2 = 59;

	public final static int MZ2_MEDIC_BLASTER_1 = 60;

	public final static int MZ2_GLADIATOR_RAILGUN_1 = 61;

	public final static int MZ2_HOVER_BLASTER_1 = 62;

	public final static int MZ2_ACTOR_MACHINEGUN_1 = 63;

	public final static int MZ2_SUPERTANK_MACHINEGUN_1 = 64;
	public final static int MZ2_SUPERTANK_MACHINEGUN_2 = 65;
	public final static int MZ2_SUPERTANK_MACHINEGUN_3 = 66;
	public final static int MZ2_SUPERTANK_MACHINEGUN_4 = 67;
	public final static int MZ2_SUPERTANK_MACHINEGUN_5 = 68;
	public final static int MZ2_SUPERTANK_MACHINEGUN_6 = 69;
	public final static int MZ2_SUPERTANK_ROCKET_1 = 70;
	public final static int MZ2_SUPERTANK_ROCKET_2 = 71;
	public final static int MZ2_SUPERTANK_ROCKET_3 = 72;

	public final static int MZ2_BOSS2_MACHINEGUN_L1 = 73;
	public final static int MZ2_BOSS2_MACHINEGUN_L2 = 74;
	public final static int MZ2_BOSS2_MACHINEGUN_L3 = 75;
	public final static int MZ2_BOSS2_MACHINEGUN_L4 = 76;
	public final static int MZ2_BOSS2_MACHINEGUN_L5 = 77;
	public final static int MZ2_BOSS2_ROCKET_1 = 78;
	public final static int MZ2_BOSS2_ROCKET_2 = 79;
	public final static int MZ2_BOSS2_ROCKET_3 = 80;
	public final static int MZ2_BOSS2_ROCKET_4 = 81;

	public final static int MZ2_FLOAT_BLASTER_1 = 82;

	public final static int MZ2_SOLDIER_BLASTER_3 = 83;
	public final static int MZ2_SOLDIER_SHOTGUN_3 = 84;
	public final static int MZ2_SOLDIER_MACHINEGUN_3 = 85;
	public final static int MZ2_SOLDIER_BLASTER_4 = 86;
	public final static int MZ2_SOLDIER_SHOTGUN_4 = 87;
	public final static int MZ2_SOLDIER_MACHINEGUN_4 = 88;
	public final static int MZ2_SOLDIER_BLASTER_5 = 89;
	public final static int MZ2_SOLDIER_SHOTGUN_5 = 90;
	public final static int MZ2_SOLDIER_MACHINEGUN_5 = 91;
	public final static int MZ2_SOLDIER_BLASTER_6 = 92;
	public final static int MZ2_SOLDIER_SHOTGUN_6 = 93;
	public final static int MZ2_SOLDIER_MACHINEGUN_6 = 94;
	public final static int MZ2_SOLDIER_BLASTER_7 = 95;
	public final static int MZ2_SOLDIER_SHOTGUN_7 = 96;
	public final static int MZ2_SOLDIER_MACHINEGUN_7 = 97;
	public final static int MZ2_SOLDIER_BLASTER_8 = 98;
	public final static int MZ2_SOLDIER_SHOTGUN_8 = 99;
	public final static int MZ2_SOLDIER_MACHINEGUN_8 = 100;

	// --- Xian shit below ---
	public final static int MZ2_MAKRON_BFG = 101;
	public final static int MZ2_MAKRON_BLASTER_1 = 102;
	public final static int MZ2_MAKRON_BLASTER_2 = 103;
	public final static int MZ2_MAKRON_BLASTER_3 = 104;
	public final static int MZ2_MAKRON_BLASTER_4 = 105;
	public final static int MZ2_MAKRON_BLASTER_5 = 106;
	public final static int MZ2_MAKRON_BLASTER_6 = 107;
	public final static int MZ2_MAKRON_BLASTER_7 = 108;
	public final static int MZ2_MAKRON_BLASTER_8 = 109;
	public final static int MZ2_MAKRON_BLASTER_9 = 110;
	public final static int MZ2_MAKRON_BLASTER_10 = 111;
	public final static int MZ2_MAKRON_BLASTER_11 = 112;
	public final static int MZ2_MAKRON_BLASTER_12 = 113;
	public final static int MZ2_MAKRON_BLASTER_13 = 114;
	public final static int MZ2_MAKRON_BLASTER_14 = 115;
	public final static int MZ2_MAKRON_BLASTER_15 = 116;
	public final static int MZ2_MAKRON_BLASTER_16 = 117;
	public final static int MZ2_MAKRON_BLASTER_17 = 118;
	public final static int MZ2_MAKRON_RAILGUN_1 = 119;
	public final static int MZ2_JORG_MACHINEGUN_L1 = 120;
	public final static int MZ2_JORG_MACHINEGUN_L2 = 121;
	public final static int MZ2_JORG_MACHINEGUN_L3 = 122;
	public final static int MZ2_JORG_MACHINEGUN_L4 = 123;
	public final static int MZ2_JORG_MACHINEGUN_L5 = 124;
	public final static int MZ2_JORG_MACHINEGUN_L6 = 125;
	public final static int MZ2_JORG_MACHINEGUN_R1 = 126;
	public final static int MZ2_JORG_MACHINEGUN_R2 = 127;
	public final static int MZ2_JORG_MACHINEGUN_R3 = 128;
	public final static int MZ2_JORG_MACHINEGUN_R4 = 129;
	public final static int MZ2_JORG_MACHINEGUN_R5 = 130;
	public final static int MZ2_JORG_MACHINEGUN_R6 = 131;
	public final static int MZ2_JORG_BFG_1 = 132;
	public final static int MZ2_BOSS2_MACHINEGUN_R1 = 133;
	public final static int MZ2_BOSS2_MACHINEGUN_R2 = 134;
	public final static int MZ2_BOSS2_MACHINEGUN_R3 = 135;
	public final static int MZ2_BOSS2_MACHINEGUN_R4 = 136;
	public final static int MZ2_BOSS2_MACHINEGUN_R5 = 137;

	//ROGUE
	public final static int MZ2_CARRIER_MACHINEGUN_L1 = 138;
	public final static int MZ2_CARRIER_MACHINEGUN_R1 = 139;
	public final static int MZ2_CARRIER_GRENADE = 140;
	public final static int MZ2_TURRET_MACHINEGUN = 141;
	public final static int MZ2_TURRET_ROCKET = 142;
	public final static int MZ2_TURRET_BLASTER = 143;
	public final static int MZ2_STALKER_BLASTER = 144;
	public final static int MZ2_DAEDALUS_BLASTER = 145;
	public final static int MZ2_MEDIC_BLASTER_2 = 146;
	public final static int MZ2_CARRIER_RAILGUN = 147;
	public final static int MZ2_WIDOW_DISRUPTOR = 148;
	public final static int MZ2_WIDOW_BLASTER = 149;
	public final static int MZ2_WIDOW_RAIL = 150;
	public final static int MZ2_WIDOW_PLASMABEAM = 151; // PMM - not used 
	public final static int MZ2_CARRIER_MACHINEGUN_L2 = 152;
	public final static int MZ2_CARRIER_MACHINEGUN_R2 = 153;
	public final static int MZ2_WIDOW_RAIL_LEFT = 154;
	public final static int MZ2_WIDOW_RAIL_RIGHT = 155;
	public final static int MZ2_WIDOW_BLASTER_SWEEP1 = 156;
	public final static int MZ2_WIDOW_BLASTER_SWEEP2 = 157;
	public final static int MZ2_WIDOW_BLASTER_SWEEP3 = 158;
	public final static int MZ2_WIDOW_BLASTER_SWEEP4 = 159;
	public final static int MZ2_WIDOW_BLASTER_SWEEP5 = 160;
	public final static int MZ2_WIDOW_BLASTER_SWEEP6 = 161;
	public final static int MZ2_WIDOW_BLASTER_SWEEP7 = 162;
	public final static int MZ2_WIDOW_BLASTER_SWEEP8 = 163;
	public final static int MZ2_WIDOW_BLASTER_SWEEP9 = 164;
	public final static int MZ2_WIDOW_BLASTER_100 = 165;
	public final static int MZ2_WIDOW_BLASTER_90 = 166;
	public final static int MZ2_WIDOW_BLASTER_80 = 167;
	public final static int MZ2_WIDOW_BLASTER_70 = 168;
	public final static int MZ2_WIDOW_BLASTER_60 = 169;
	public final static int MZ2_WIDOW_BLASTER_50 = 170;
	public final static int MZ2_WIDOW_BLASTER_40 = 171;
	public final static int MZ2_WIDOW_BLASTER_30 = 172;
	public final static int MZ2_WIDOW_BLASTER_20 = 173;
	public final static int MZ2_WIDOW_BLASTER_10 = 174;
	public final static int MZ2_WIDOW_BLASTER_0 = 175;
	public final static int MZ2_WIDOW_BLASTER_10L = 176;
	public final static int MZ2_WIDOW_BLASTER_20L = 177;
	public final static int MZ2_WIDOW_BLASTER_30L = 178;
	public final static int MZ2_WIDOW_BLASTER_40L = 179;
	public final static int MZ2_WIDOW_BLASTER_50L = 180;
	public final static int MZ2_WIDOW_BLASTER_60L = 181;
	public final static int MZ2_WIDOW_BLASTER_70L = 182;
	public final static int MZ2_WIDOW_RUN_1 = 183;
	public final static int MZ2_WIDOW_RUN_2 = 184;
	public final static int MZ2_WIDOW_RUN_3 = 185;
	public final static int MZ2_WIDOW_RUN_4 = 186;
	public final static int MZ2_WIDOW_RUN_5 = 187;
	public final static int MZ2_WIDOW_RUN_6 = 188;
	public final static int MZ2_WIDOW_RUN_7 = 189;
	public final static int MZ2_WIDOW_RUN_8 = 190;
	public final static int MZ2_CARRIER_ROCKET_1 = 191;
	public final static int MZ2_CARRIER_ROCKET_2 = 192;
	public final static int MZ2_CARRIER_ROCKET_3 = 193;
	public final static int MZ2_CARRIER_ROCKET_4 = 194;
	public final static int MZ2_WIDOW2_BEAMER_1 = 195;
	public final static int MZ2_WIDOW2_BEAMER_2 = 196;
	public final static int MZ2_WIDOW2_BEAMER_3 = 197;
	public final static int MZ2_WIDOW2_BEAMER_4 = 198;
	public final static int MZ2_WIDOW2_BEAMER_5 = 199;
	public final static int MZ2_WIDOW2_BEAM_SWEEP_1 = 200;
	public final static int MZ2_WIDOW2_BEAM_SWEEP_2 = 201;
	public final static int MZ2_WIDOW2_BEAM_SWEEP_3 = 202;
	public final static int MZ2_WIDOW2_BEAM_SWEEP_4 = 203;
	public final static int MZ2_WIDOW2_BEAM_SWEEP_5 = 204;
	public final static int MZ2_WIDOW2_BEAM_SWEEP_6 = 205;
	public final static int MZ2_WIDOW2_BEAM_SWEEP_7 = 206;
	public final static int MZ2_WIDOW2_BEAM_SWEEP_8 = 207;
	public final static int MZ2_WIDOW2_BEAM_SWEEP_9 = 208;
	public final static int MZ2_WIDOW2_BEAM_SWEEP_10 = 209;
	public final static int MZ2_WIDOW2_BEAM_SWEEP_11 = 210;

	public final static int SPLASH_UNKNOWN = 0;
	public final static int SPLASH_SPARKS = 1;
	public final static int SPLASH_BLUE_WATER = 2;
	public final static int SPLASH_BROWN_WATER = 3;
	public final static int SPLASH_SLIME = 4;
	public final static int SPLASH_LAVA = 5;
	public final static int SPLASH_BLOOD = 6;

	//	   sound channels
	//	   channel 0 never willingly overrides
	//	   other channels (1-7) allways override a playing sound on that channel
	public final static int CHAN_AUTO = 0;
	public final static int CHAN_WEAPON = 1;
	public final static int CHAN_VOICE = 2;
	public final static int CHAN_ITEM = 3;
	public final static int CHAN_BODY = 4;
	//	   modifier flags
	public final static int CHAN_NO_PHS_ADD = 8;
	// send to all clients, not just ones in PHS (ATTN 0 will also do this)
	public final static int CHAN_RELIABLE = 16; // send by reliable message, not datagram 

	//	   sound attenuation values
	public final static int ATTN_NONE = 0; // full volume the entire level 
	public final static int ATTN_NORM = 1;
	public final static int ATTN_IDLE = 2;
	public final static int ATTN_STATIC = 3; // diminish very rapidly with distance 

	//	   player_state->stats[] indexes
	public final static int STAT_HEALTH_ICON = 0;
	public final static int STAT_HEALTH = 1;
	public final static int STAT_AMMO_ICON = 2;
	public final static int STAT_AMMO = 3;
	public final static int STAT_ARMOR_ICON = 4;
	public final static int STAT_ARMOR = 5;
	public final static int STAT_SELECTED_ICON = 6;
	public final static int STAT_PICKUP_ICON = 7;
	public final static int STAT_PICKUP_STRING = 8;
	public final static int STAT_TIMER_ICON = 9;
	public final static int STAT_TIMER = 10;
	public final static int STAT_HELPICON = 11;
	public final static int STAT_SELECTED_ITEM = 12;
	public final static int STAT_LAYOUTS = 13;
	public final static int STAT_FRAGS = 14;
	public final static int STAT_FLASHES = 15; // cleared each frame, 1 = health, 2 = armor 
	public final static int STAT_CHASE = 16;
	public final static int STAT_SPECTATOR = 17;

	public final static int MAX_STATS = 32;

	//	   dmflags->value flags
	public final static int DF_NO_HEALTH = 0x00000001; // 1 
	public final static int DF_NO_ITEMS = 0x00000002; // 2 
	public final static int DF_WEAPONS_STAY = 0x00000004; // 4 
	public final static int DF_NO_FALLING = 0x00000008; // 8 
	public final static int DF_INSTANT_ITEMS = 0x00000010; // 16 
	public final static int DF_SAME_LEVEL = 0x00000020; // 32 
	public final static int DF_SKINTEAMS = 0x00000040; // 64 
	public final static int DF_MODELTEAMS = 0x00000080; // 128 
	public final static int DF_NO_FRIENDLY_FIRE = 0x00000100; // 256 
	public final static int DF_SPAWN_FARTHEST = 0x00000200; // 512 
	public final static int DF_FORCE_RESPAWN = 0x00000400; // 1024 
	public final static int DF_NO_ARMOR = 0x00000800; // 2048 
	public final static int DF_ALLOW_EXIT = 0x00001000; // 4096 
	public final static int DF_INFINITE_AMMO = 0x00002000; // 8192 
	public final static int DF_QUAD_DROP = 0x00004000; // 16384 
	public final static int DF_FIXED_FOV = 0x00008000; // 32768 

	//	   RAFAEL
	public final static int DF_QUADFIRE_DROP = 0x00010000; // 65536 

	//	  ROGUE
	public final static int DF_NO_MINES = 0x00020000;
	public final static int DF_NO_STACK_DOUBLE = 0x00040000;
	public final static int DF_NO_NUKES = 0x00080000;
	public final static int DF_NO_SPHERES = 0x00100000;
	//	  ROGUE

	//
	//	config strings are a general means of communication from
	//	the server to all connected clients.
	//	Each config string can be at most MAX_QPATH characters.
	//
	public final static int CS_NAME = 0;
	public final static int CS_CDTRACK = 1;
	public final static int CS_SKY = 2;
	public final static int CS_SKYAXIS = 3; // %f %f %f format 
	public final static int CS_SKYROTATE = 4;
	public final static int CS_STATUSBAR = 5; // display program string 

	public final static int CS_AIRACCEL = 29; // air acceleration control 
	public final static int CS_MAXCLIENTS = 30;
	public final static int CS_MAPCHECKSUM = 31; // for catching cheater maps 

	public final static int CS_MODELS = 32;
	public final static int CS_SOUNDS = (CS_MODELS + MAX_MODELS);
	public final static int CS_IMAGES = (CS_SOUNDS + MAX_SOUNDS);
	public final static int CS_LIGHTS = (CS_IMAGES + MAX_IMAGES);
	public final static int CS_ITEMS = (CS_LIGHTS + MAX_LIGHTSTYLES);
	public final static int CS_PLAYERSKINS = (CS_ITEMS + MAX_ITEMS);
	public final static int CS_GENERAL = (CS_PLAYERSKINS + MAX_CLIENTS);
	public final static int MAX_CONFIGSTRINGS = (CS_GENERAL + MAX_GENERAL);

	public final static int HEALTH_IGNORE_MAX = 1;
	public final static int HEALTH_TIMED = 2;

	// gi.BoxEdicts() can return a list of either solid or trigger entities
	// FIXME: eliminate AREA_ distinction?
	public final static int AREA_SOLID = 1;
	public final static int AREA_TRIGGERS = 2;

	public final static int TE_GUNSHOT = 0;
	public final static int TE_BLOOD = 1;
	public final static int TE_BLASTER = 2;
	public final static int TE_RAILTRAIL = 3;
	public final static int TE_SHOTGUN = 4;
	public final static int TE_EXPLOSION1 = 5;
	public final static int TE_EXPLOSION2 = 6;
	public final static int TE_ROCKET_EXPLOSION = 7;
	public final static int TE_GRENADE_EXPLOSION = 8;
	public final static int TE_SPARKS = 9;
	public final static int TE_SPLASH = 10;
	public final static int TE_BUBBLETRAIL = 11;
	public final static int TE_SCREEN_SPARKS = 12;
	public final static int TE_SHIELD_SPARKS = 13;
	public final static int TE_BULLET_SPARKS = 14;
	public final static int TE_LASER_SPARKS = 15;
	public final static int TE_PARASITE_ATTACK = 16;
	public final static int TE_ROCKET_EXPLOSION_WATER = 17;
	public final static int TE_GRENADE_EXPLOSION_WATER = 18;
	public final static int TE_MEDIC_CABLE_ATTACK = 19;
	public final static int TE_BFG_EXPLOSION = 20;
	public final static int TE_BFG_BIGEXPLOSION = 21;
	public final static int TE_BOSSTPORT = 22; // used as '22' in a map, so DON'T RENUMBER!!! 
	public final static int TE_BFG_LASER = 23;
	public final static int TE_GRAPPLE_CABLE = 24;
	public final static int TE_WELDING_SPARKS = 25;
	public final static int TE_GREENBLOOD = 26;
	public final static int TE_BLUEHYPERBLASTER = 27;
	public final static int TE_PLASMA_EXPLOSION = 28;
	public final static int TE_TUNNEL_SPARKS = 29;
	//ROGUE 
	public final static int TE_BLASTER2 = 30;
	public final static int TE_RAILTRAIL2 = 31;
	public final static int TE_FLAME = 32;
	public final static int TE_LIGHTNING = 33;
	public final static int TE_DEBUGTRAIL = 34;
	public final static int TE_PLAIN_EXPLOSION = 35;
	public final static int TE_FLASHLIGHT = 36;
	public final static int TE_FORCEWALL = 37;
	public final static int TE_HEATBEAM = 38;
	public final static int TE_MONSTER_HEATBEAM = 39;
	public final static int TE_STEAM = 40;
	public final static int TE_BUBBLETRAIL2 = 41;
	public final static int TE_MOREBLOOD = 42;
	public final static int TE_HEATBEAM_SPARKS = 43;
	public final static int TE_HEATBEAM_STEAM = 44;
	public final static int TE_CHAINFIST_SMOKE = 45;
	public final static int TE_ELECTRIC_SPARKS = 46;
	public final static int TE_TRACKER_EXPLOSION = 47;
	public final static int TE_TELEPORT_EFFECT = 48;
	public final static int TE_DBALL_GOAL = 49;
	public final static int TE_WIDOWBEAMOUT = 50;
	public final static int TE_NUKEBLAST = 51;
	public final static int TE_WIDOWSPLASH = 52;
	public final static int TE_EXPLOSION1_BIG = 53;
	public final static int TE_EXPLOSION1_NP = 54;
	public final static int TE_FLECHETTE = 55;

	//	content masks
	public final static int MASK_ALL = (-1);
	public final static int MASK_SOLID = (CONTENTS_SOLID | CONTENTS_WINDOW);
	public final static int MASK_PLAYERSOLID = (CONTENTS_SOLID | CONTENTS_PLAYERCLIP | CONTENTS_WINDOW | CONTENTS_MONSTER);
	public final static int MASK_DEADSOLID = (CONTENTS_SOLID | CONTENTS_PLAYERCLIP | CONTENTS_WINDOW);
	public final static int MASK_MONSTERSOLID = (CONTENTS_SOLID | CONTENTS_MONSTERCLIP | CONTENTS_WINDOW | CONTENTS_MONSTER);
	public final static int MASK_WATER = (CONTENTS_WATER | CONTENTS_LAVA | CONTENTS_SLIME);
	public final static int MASK_OPAQUE = (CONTENTS_SOLID | CONTENTS_SLIME | CONTENTS_LAVA);
	public final static int MASK_SHOT = (CONTENTS_SOLID | CONTENTS_MONSTER | CONTENTS_WINDOW | CONTENTS_DEADMONSTER);
	public final static int MASK_CURRENT =
		(CONTENTS_CURRENT_0
			| CONTENTS_CURRENT_90
			| CONTENTS_CURRENT_180
			| CONTENTS_CURRENT_270
			| CONTENTS_CURRENT_UP
			| CONTENTS_CURRENT_DOWN);

	// item spawnflags
	public final static int ITEM_TRIGGER_SPAWN = 0x00000001;
	public final static int ITEM_NO_TOUCH = 0x00000002;
	// 6 bits reserved for editor flags
	// 8 bits used as power cube id bits for coop games
	public final static int DROPPED_ITEM = 0x00010000;
	public final static int DROPPED_PLAYER_ITEM = 0x00020000;
	public final static int ITEM_TARGETS_USED = 0x00040000;

	// (machen nur GL)
	public final static int VIDREF_GL = 1;
	public final static int VIDREF_SOFT = 2;
	public final static int VIDREF_OTHER = 3;

	// --------------
	// game/g_local.h

	public final static int FFL_SPAWNTEMP = 1;
	public final static int FFL_NOSPAWN = 2;

	// enum fieldtype_t
	public final static int F_INT = 0;
	public final static int F_FLOAT = 1;
	public final static int F_LSTRING = 2; // string on disk, pointer in memory, TAG_LEVEL
	public final static int F_GSTRING = 3; // string on disk, pointer in memory, TAG_GAME
	public final static int F_VECTOR = 4;
	public final static int F_ANGLEHACK = 5;
	public final static int F_EDICT = 6; // index on disk, pointer in memory
	public final static int F_ITEM = 7; // index on disk, pointer in memory
	public final static int F_CLIENT = 8; // index on disk, pointer in memory
	public final static int F_FUNCTION = 9;
	public final static int F_MMOVE = 10;
	public final static int F_IGNORE = 11;

	public final static int DEFAULT_BULLET_HSPREAD = 300;
	public final static int DEFAULT_BULLET_VSPREAD = 500;
	public final static int DEFAULT_SHOTGUN_HSPREAD = 1000;
	public final static int DEFAULT_SHOTGUN_VSPREAD = 500;
	public final static int DEFAULT_DEATHMATCH_SHOTGUN_COUNT = 12;
	public final static int DEFAULT_SHOTGUN_COUNT = 12;
	public final static int DEFAULT_SSHOTGUN_COUNT = 20;

	public final static int ANIM_BASIC = 0; // stand / run 
	public final static int ANIM_WAVE = 1;
	public final static int ANIM_JUMP = 2;
	public final static int ANIM_PAIN = 3;
	public final static int ANIM_ATTACK = 4;
	public final static int ANIM_DEATH = 5;
	public final static int ANIM_REVERSE = 6;

	public final static int AMMO_BULLETS = 0;
	public final static int AMMO_SHELLS = 1;
	public final static int AMMO_ROCKETS = 2;
	public final static int AMMO_GRENADES = 3;
	public final static int AMMO_CELLS = 4;
	public final static int AMMO_SLUGS = 5;

	//	view pitching times
	public final static float DAMAGE_TIME = 0.5f;
	public final static float FALL_TIME = 0.3f;

	//	damage flags
	public final static int DAMAGE_RADIUS = 0x00000001; // damage was indirect 
	public final static int DAMAGE_NO_ARMOR = 0x00000002; // armour does not protect from this damage 
	public final static int DAMAGE_ENERGY = 0x00000004; // damage is from an energy based weapon 
	public final static int DAMAGE_NO_KNOCKBACK = 0x00000008; // do not affect velocity, just view angles 
	public final static int DAMAGE_BULLET = 0x00000010; // damage is from a bullet (used for ricochets) 
	public final static int DAMAGE_NO_PROTECTION = 0x00000020;
	// armor, shields, invulnerability, and godmode have no effect

	public final static int DAMAGE_NO = 0;
	public final static int DAMAGE_YES = 1; // will take damage if hit 
	public final static int DAMAGE_AIM = 2; // auto targeting recognizes this 

	//	means of death
	public final static int MOD_UNKNOWN = 0;
	public final static int MOD_BLASTER = 1;
	public final static int MOD_SHOTGUN = 2;
	public final static int MOD_SSHOTGUN = 3;
	public final static int MOD_MACHINEGUN = 4;
	public final static int MOD_CHAINGUN = 5;
	public final static int MOD_GRENADE = 6;
	public final static int MOD_G_SPLASH = 7;
	public final static int MOD_ROCKET = 8;
	public final static int MOD_R_SPLASH = 9;
	public final static int MOD_HYPERBLASTER = 10;
	public final static int MOD_RAILGUN = 11;
	public final static int MOD_BFG_LASER = 12;
	public final static int MOD_BFG_BLAST = 13;
	public final static int MOD_BFG_EFFECT = 14;
	public final static int MOD_HANDGRENADE = 15;
	public final static int MOD_HG_SPLASH = 16;
	public final static int MOD_WATER = 17;
	public final static int MOD_SLIME = 18;
	public final static int MOD_LAVA = 19;
	public final static int MOD_CRUSH = 20;
	public final static int MOD_TELEFRAG = 21;
	public final static int MOD_FALLING = 22;
	public final static int MOD_SUICIDE = 23;
	public final static int MOD_HELD_GRENADE = 24;
	public final static int MOD_EXPLOSIVE = 25;
	public final static int MOD_BARREL = 26;
	public final static int MOD_BOMB = 27;
	public final static int MOD_EXIT = 28;
	public final static int MOD_SPLASH = 29;
	public final static int MOD_TARGET_LASER = 30;
	public final static int MOD_TRIGGER_HURT = 31;
	public final static int MOD_HIT = 32;
	public final static int MOD_TARGET_BLASTER = 33;
	public final static int MOD_FRIENDLY_FIRE = 0x8000000;

	//	edict->spawnflags
	//	these are set with checkboxes on each entity in the map editor
	public final static int SPAWNFLAG_NOT_EASY = 0x00000100;
	public final static int SPAWNFLAG_NOT_MEDIUM = 0x00000200;
	public final static int SPAWNFLAG_NOT_HARD = 0x00000400;
	public final static int SPAWNFLAG_NOT_DEATHMATCH = 0x00000800;
	public final static int SPAWNFLAG_NOT_COOP = 0x00001000;

	//	edict->flags
	public final static int FL_FLY = 0x00000001;
	public final static int FL_SWIM = 0x00000002; // implied immunity to drowining 
	public final static int FL_IMMUNE_LASER = 0x00000004;
	public final static int FL_INWATER = 0x00000008;
	public final static int FL_GODMODE = 0x00000010;
	public final static int FL_NOTARGET = 0x00000020;
	public final static int FL_IMMUNE_SLIME = 0x00000040;
	public final static int FL_IMMUNE_LAVA = 0x00000080;
	public final static int FL_PARTIALGROUND = 0x00000100; // not all corners are valid 
	public final static int FL_WATERJUMP = 0x00000200; // player jumping out of water 
	public final static int FL_TEAMSLAVE = 0x00000400; // not the first on the team 
	public final static int FL_NO_KNOCKBACK = 0x00000800;
	public final static int FL_POWER_ARMOR = 0x00001000; // power armor (if any) is active 
	public final static int FL_RESPAWN = 0x80000000; // used for item respawning 

	public final static float FRAMETIME = 0.1f;

	//	memory tags to allow dynamic memory to be cleaned up
	public final static int TAG_GAME = 765; // clear when unloading the dll 
	public final static int TAG_LEVEL = 766; // clear when loading a new level 

	public final static int MELEE_DISTANCE = 80;

	public final static int BODY_QUEUE_SIZE = 8;

	//	deadflag
	public final static int DEAD_NO = 0;
	public final static int DEAD_DYING = 1;
	public final static int DEAD_DEAD = 2;
	public final static int DEAD_RESPAWNABLE = 3;

	//	range
	public final static int RANGE_MELEE = 0;
	public final static int RANGE_NEAR = 1;
	public final static int RANGE_MID = 2;
	public final static int RANGE_FAR = 3;

	//	gib types
	public final static int GIB_ORGANIC = 0;
	public final static int GIB_METALLIC = 1;

	//	monster ai flags
	public final static int AI_STAND_GROUND = 0x00000001;
	public final static int AI_TEMP_STAND_GROUND = 0x00000002;
	public final static int AI_SOUND_TARGET = 0x00000004;
	public final static int AI_LOST_SIGHT = 0x00000008;
	public final static int AI_PURSUIT_LAST_SEEN = 0x00000010;
	public final static int AI_PURSUE_NEXT = 0x00000020;
	public final static int AI_PURSUE_TEMP = 0x00000040;
	public final static int AI_HOLD_FRAME = 0x00000080;
	public final static int AI_GOOD_GUY = 0x00000100;
	public final static int AI_BRUTAL = 0x00000200;
	public final static int AI_NOSTEP = 0x00000400;
	public final static int AI_DUCKED = 0x00000800;
	public final static int AI_COMBAT_POINT = 0x00001000;
	public final static int AI_MEDIC = 0x00002000;
	public final static int AI_RESURRECTING = 0x00004000;

	//	monster attack state
	public final static int AS_STRAIGHT = 1;
	public final static int AS_SLIDING = 2;
	public final static int AS_MELEE = 3;
	public final static int AS_MISSILE = 4;

	//	 armor types
	public final static int ARMOR_NONE = 0;
	public final static int ARMOR_JACKET = 1;
	public final static int ARMOR_COMBAT = 2;
	public final static int ARMOR_BODY = 3;
	public final static int ARMOR_SHARD = 4;

	//	 power armor types
	public final static int POWER_ARMOR_NONE = 0;
	public final static int POWER_ARMOR_SCREEN = 1;
	public final static int POWER_ARMOR_SHIELD = 2;

	//	 handedness values
	public final static int RIGHT_HANDED = 0;
	public final static int LEFT_HANDED = 1;
	public final static int CENTER_HANDED = 2;

	//	 game.serverflags values
	public final static int SFL_CROSS_TRIGGER_1 = 0x00000001;
	public final static int SFL_CROSS_TRIGGER_2 = 0x00000002;
	public final static int SFL_CROSS_TRIGGER_3 = 0x00000004;
	public final static int SFL_CROSS_TRIGGER_4 = 0x00000008;
	public final static int SFL_CROSS_TRIGGER_5 = 0x00000010;
	public final static int SFL_CROSS_TRIGGER_6 = 0x00000020;
	public final static int SFL_CROSS_TRIGGER_7 = 0x00000040;
	public final static int SFL_CROSS_TRIGGER_8 = 0x00000080;
	public final static int SFL_CROSS_TRIGGER_MASK = 0x000000ff;

	//	 noise types for PlayerNoise
	public final static int PNOISE_SELF = 0;
	public final static int PNOISE_WEAPON = 1;
	public final static int PNOISE_IMPACT = 2;

	//	gitem_t->flags
	public final static int IT_WEAPON = 1; // use makes active weapon 
	public final static int IT_AMMO = 2;
	public final static int IT_ARMOR = 4;
	public final static int IT_STAY_COOP = 8;
	public final static int IT_KEY = 16;
	public final static int IT_POWERUP = 32;

	//	gitem_t->weapmodel for weapons indicates model index
	public final static int WEAP_BLASTER = 1;
	public final static int WEAP_SHOTGUN = 2;
	public final static int WEAP_SUPERSHOTGUN = 3;
	public final static int WEAP_MACHINEGUN = 4;
	public final static int WEAP_CHAINGUN = 5;
	public final static int WEAP_GRENADES = 6;
	public final static int WEAP_GRENADELAUNCHER = 7;
	public final static int WEAP_ROCKETLAUNCHER = 8;
	public final static int WEAP_HYPERBLASTER = 9;
	public final static int WEAP_RAILGUN = 10;
	public final static int WEAP_BFG = 11;

	//	edict->movetype values
	public final static int MOVETYPE_NONE = 0; // never moves 
	public final static int MOVETYPE_NOCLIP = 1; // origin and angles change with no interaction 
	public final static int MOVETYPE_PUSH = 2; // no clip to world, push on box contact 
	public final static int MOVETYPE_STOP = 3; // no clip to world, stops on box contact 

	public final static int MOVETYPE_WALK = 4; // gravity 
	public final static int MOVETYPE_STEP = 5; // gravity, special edge handling 
	public final static int MOVETYPE_FLY = 6;
	public final static int MOVETYPE_TOSS = 7; // gravity 
	public final static int MOVETYPE_FLYMISSILE = 8; // extra size to monsters 
	public final static int MOVETYPE_BOUNCE = 9;

	public final static int MULTICAST_ALL = 0;
	public final static int MULTICAST_PHS = 1;
	public final static int MULTICAST_PVS = 2;
	public final static int MULTICAST_ALL_R = 3;
	public final static int MULTICAST_PHS_R = 4;
	public final static int MULTICAST_PVS_R = 5;

	// -------------
	// client/game.h

	public final static int SOLID_NOT = 0; // no interaction with other objects
	public final static int SOLID_TRIGGER = 1; // only touch when inside, after moving
	public final static int SOLID_BBOX = 2; // touch on edge
	public final static int SOLID_BSP = 3; // bsp clip, touch on edge

	public final static int GAME_API_VERSION = 3;

	//	   edict->svflags
	public final static int SVF_NOCLIENT = 0x00000001; // don't send entity to clients, even if it has effects 
	public final static int SVF_DEADMONSTER = 0x00000002; // treat as CONTENTS_DEADMONSTER for collision 
	public final static int SVF_MONSTER = 0x00000004; // treat as CONTENTS_MONSTER for collision 

	public final static int MAX_ENT_CLUSTERS = 16;

	public final static int sv_stopspeed = 100;
	public final static int sv_friction = 6;
	public final static int sv_waterfriction = 1;

	public final static int PLAT_LOW_TRIGGER = 1;

	public final static int STATE_TOP = 0;
	public final static int STATE_BOTTOM = 1;
	public final static int STATE_UP = 2;
	public final static int STATE_DOWN = 3;

	public final static int DOOR_START_OPEN = 1;
	public final static int DOOR_REVERSE = 2;
	public final static int DOOR_CRUSHER = 4;
	public final static int DOOR_NOMONSTER = 8;
	public final static int DOOR_TOGGLE = 32;
	public final static int DOOR_X_AXIS = 64;
	public final static int DOOR_Y_AXIS = 128;

	// R E N D E R E R 
	////////////////////
	public static final int MAX_DLIGHTS = 32;
	public static final int MAX_ENTITIES = 128;
	public static final int MAX_PARTICLES = 4096;

	// gl_model.h
	public static final int SURF_PLANEBACK = 2;
	public static final int SURF_DRAWSKY = 4;
	public static final int SURF_DRAWTURB = 0x10;
	public static final int SURF_DRAWBACKGROUND = 0x40;
	public static final int SURF_UNDERWATER = 0x80;

	public static final float POWERSUIT_SCALE = 4.0f;

	public static final int SHELL_RED_COLOR = 0xF2;
	public static final int SHELL_GREEN_COLOR = 0xD0;
	public static final int SHELL_BLUE_COLOR = 0xF3;

	public static final int SHELL_RG_COLOR = 0xDC;

	public static final int SHELL_RB_COLOR = 0x68; //0x86
	public static final int SHELL_BG_COLOR = 0x78;

	// ROGUE
	public static final int SHELL_DOUBLE_COLOR = 0xDF; // 223
	public static final int SHELL_HALF_DAM_COLOR = 0x90;
	public static final int SHELL_CYAN_COLOR = 0x72;

	// ---------
	// qcommon.h

	public final static int svc_bad = 0;

	// these ops are known to the game dll
	// protocol bytes that can be directly added to messages

	public final static int svc_muzzleflash = 1;
	public final static int svc_muzzleflash2 = 2;
	public final static int svc_temp_entity = 3;
	public final static int svc_layout = 4;
	public final static int svc_inventory = 5;

	// the rest are private to the client and server
	public final static int svc_nop = 6;
	public final static int svc_disconnect = 7;
	public final static int svc_reconnect = 8;
	public final static int svc_sound = 9; // <see code> 
	public final static int svc_print = 10; // [byte] id [string] null terminated string 
	public final static int svc_stufftext = 11;
	// [string] stuffed into client's console buffer, should be \n terminated
	public final static int svc_serverdata = 12; // [long] protocol ... 
	public final static int svc_configstring = 13; // [short] [string] 
	public final static int svc_spawnbaseline = 14;
	public final static int svc_centerprint = 15; // [string] to put in center of the screen 
	public final static int svc_download = 16; // [short] size [size bytes] 
	public final static int svc_playerinfo = 17; // variable 
	public final static int svc_packetentities = 18; // [...] 
	public final static int svc_deltapacketentities = 19; // [...] 
	public final static int svc_frame = 20;

	public static final int NUMVERTEXNORMALS = 162;
	public static final int PROTOCOL_VERSION = 34;
	public final static int PORT_MASTER = 27900;
	public final static int PORT_CLIENT = 27901;
	public final static int PORT_SERVER = 27910;
	public final static int PORT_ANY = -1;

	public final static int PS_M_TYPE = (1 << 0);
	public final static int PS_M_ORIGIN = (1 << 1);
	public final static int PS_M_VELOCITY = (1 << 2);
	public final static int PS_M_TIME = (1 << 3);
	public final static int PS_M_FLAGS = (1 << 4);
	public final static int PS_M_GRAVITY = (1 << 5);
	public final static int PS_M_DELTA_ANGLES = (1 << 6);

	public final static int UPDATE_BACKUP = 16; // copies of entity_state_t to keep buffered 
	// must be power of two
	public final static int UPDATE_MASK = (UPDATE_BACKUP - 1);

	public final static int PS_VIEWOFFSET = (1 << 7);
	public final static int PS_VIEWANGLES = (1 << 8);
	public final static int PS_KICKANGLES = (1 << 9);
	public final static int PS_BLEND = (1 << 10);
	public final static int PS_FOV = (1 << 11);
	public final static int PS_WEAPONINDEX = (1 << 12);
	public final static int PS_WEAPONFRAME = (1 << 13);
	public final static int PS_RDFLAGS = (1 << 14);

	public static final int CM_ANGLE1 = (1 << 0);
	public static final int CM_ANGLE2 = (1 << 1);
	public static final int CM_ANGLE3 = (1 << 2);
	public static final int CM_FORWARD = (1 << 3);
	public static final int CM_SIDE = (1 << 4);
	public static final int CM_UP = (1 << 5);
	public static final int CM_BUTTONS = (1 << 6);
	public static final int CM_IMPULSE = (1 << 7);

	// try to pack the common update flags into the first byte
	public final static int U_ORIGIN1 = (1 << 0);
	public final static int U_ORIGIN2 = (1 << 1);
	public final static int U_ANGLE2 = (1 << 2);
	public final static int U_ANGLE3 = (1 << 3);
	public final static int U_FRAME8 = (1 << 4); // frame is a byte 
	public final static int U_EVENT = (1 << 5);
	public final static int U_REMOVE = (1 << 6); // REMOVE this entity, don't add it 
	public final static int U_MOREBITS1 = (1 << 7); // read one additional byte 

	// second byte
	public final static int U_NUMBER16 = (1 << 8); // NUMBER8 is implicit if not set 
	public final static int U_ORIGIN3 = (1 << 9);
	public final static int U_ANGLE1 = (1 << 10);
	public final static int U_MODEL = (1 << 11);
	public final static int U_RENDERFX8 = (1 << 12); // fullbright, etc 
	public final static int U_EFFECTS8 = (1 << 14); // autorotate, trails, etc 
	public final static int U_MOREBITS2 = (1 << 15); // read one additional byte 

	// third byte
	public final static int U_SKIN8 = (1 << 16);
	public final static int U_FRAME16 = (1 << 17); // frame is a short 
	public final static int U_RENDERFX16 = (1 << 18); // 8 + 16 = 32 
	public final static int U_EFFECTS16 = (1 << 19); // 8 + 16 = 32 
	public final static int U_MODEL2 = (1 << 20); // weapons, flags, etc 
	public final static int U_MODEL3 = (1 << 21);
	public final static int U_MODEL4 = (1 << 22);
	public final static int U_MOREBITS3 = (1 << 23); // read one additional byte 

	// fourth byte
	public final static int U_OLDORIGIN = (1 << 24); // FIXME: get rid of this 
	public final static int U_SKIN16 = (1 << 25);
	public final static int U_SOUND = (1 << 26);
	public final static int U_SOLID = (1 << 27);

	public static final int SHELL_WHITE_COLOR = 0xD7;

	public static final int MAX_TRIANGLES = 4096;
	public static final int MAX_VERTS = 2048;
	public static final int MAX_FRAMES = 512;
	public static final int MAX_MD2SKINS = 32;
	public static final int MAX_SKINNAME = 64;

	public static final int MAXLIGHTMAPS = 4;
	public static final int MIPLEVELS = 4;

	public static final int clc_bad = 0;
	public static final int clc_nop = 1;
	public static final int clc_move = 2; // [[usercmd_t]
	public static final int clc_userinfo = 3; // [[userinfo string]
	public static final int clc_stringcmd = 4; // [string] message

	public static final int NS_CLIENT = 0;
	public static final int NS_SERVER = 1;

	public static final int NA_LOOPBACK = 0;
	public static final int NA_BROADCAST = 1;
	public static final int NA_IP = 2;
	public static final int NA_IPX = 3;
	public static final int NA_BROADCAST_IPX = 4;

	public final static int SND_VOLUME = (1 << 0); // a byte 
	public final static int SND_ATTENUATION = (1 << 1); // a byte 
	public final static int SND_POS = (1 << 2); // three coordinates 
	public final static int SND_ENT = (1 << 3); // a short 0-2: channel, 3-12: entity 
	public final static int SND_OFFSET = (1 << 4); // a byte, msec offset from frame start 

	public final static float DEFAULT_SOUND_PACKET_VOLUME = 1.0f;
	public final static float DEFAULT_SOUND_PACKET_ATTENUATION = 1.0f;

	// --------
	// client.h
	public static final int MAX_PARSE_ENTITIES = 1024;
	public static final int MAX_CLIENTWEAPONMODELS = 20;

	public static int CMD_BACKUP = 64; // allow a lot of command backups for very fast systems	

	public static final int ca_uninitialized = 0;
	public static final int ca_disconnected = 1;
	public static final int ca_connecting = 2;
	public static final int ca_connected = 3;
	public static final int ca_active = 4;

	public static final int MAX_ALIAS_NAME = 32;
	public static final int MAX_NUM_ARGVS = 50;

	public static final int MAX_MSGLEN = 1400;

	// ---------
	// console.h
	public static final int NUM_CON_TIMES = 4;
	public static final int CON_TEXTSIZE = 32768;

	public final static int BSPVERSION = 38;

	// --------
	// qfiles.h 

	// upper design bounds
	// leaffaces, leafbrushes, planes, and verts are still bounded by
	// 16 bit short limits
	public final static int MAX_MAP_MODELS = 1024;
	public final static int MAX_MAP_BRUSHES = 8192;
	public final static int MAX_MAP_ENTITIES = 2048;
	public final static int MAX_MAP_ENTSTRING = 0x40000;
	public final static int MAX_MAP_TEXINFO = 8192;

	public final static int MAX_MAP_AREAS = 256;
	public final static int MAX_MAP_AREAPORTALS = 1024;
	public final static int MAX_MAP_PLANES = 65536;
	public final static int MAX_MAP_NODES = 65536;
	public final static int MAX_MAP_BRUSHSIDES = 65536;
	public final static int MAX_MAP_LEAFS = 65536;
	public final static int MAX_MAP_VERTS = 65536;
	public final static int MAX_MAP_FACES = 65536;
	public final static int MAX_MAP_LEAFFACES = 65536;
	public final static int MAX_MAP_LEAFBRUSHES = 65536;
	public final static int MAX_MAP_PORTALS = 65536;
	public final static int MAX_MAP_EDGES = 128000;
	public final static int MAX_MAP_SURFEDGES = 256000;
	public final static int MAX_MAP_LIGHTING = 0x200000;
	public final static int MAX_MAP_VISIBILITY = 0x100000;

	// key / value pair sizes
	public final static int MAX_KEY = 32;
	public final static int MAX_VALUE = 1024;

	// 0-2 are axial planes
	public final static int PLANE_X = 0;
	public final static int PLANE_Y = 1;
	public final static int PLANE_Z = 2;

	// 3-5 are non-axial planes snapped to the nearest
	public final static int PLANE_ANYX = 3;
	public final static int PLANE_ANYY = 4;
	public final static int PLANE_ANYZ = 5;

	public final static int LUMP_ENTITIES = 0;
	public final static int LUMP_PLANES = 1;
	public final static int LUMP_VERTEXES = 2;
	public final static int LUMP_VISIBILITY = 3;
	public final static int LUMP_NODES = 4;
	public final static int LUMP_TEXINFO = 5;
	public final static int LUMP_FACES = 6;
	public final static int LUMP_LIGHTING = 7;
	public final static int LUMP_LEAFS = 8;
	public final static int LUMP_LEAFFACES = 9;
	public final static int LUMP_LEAFBRUSHES = 10;
	public final static int LUMP_EDGES = 11;
	public final static int LUMP_SURFEDGES = 12;
	public final static int LUMP_MODELS = 13;
	public final static int LUMP_BRUSHES = 14;
	public final static int LUMP_BRUSHSIDES = 15;
	public final static int LUMP_POP = 16;
	public final static int LUMP_AREAS = 17;
	public final static int LUMP_AREAPORTALS = 18;
	public final static int HEADER_LUMPS = 19;

	public final static int DTRIVERTX_V0 = 0;
	public final static int DTRIVERTX_V1 = 1;
	public final static int DTRIVERTX_V2 = 2;
	public final static int DTRIVERTX_LNI = 3;
	public final static int DTRIVERTX_SIZE = 4;

	public final static int ALIAS_VERSION = 8;
	public static final String GAMEVERSION = "baseq2";
	public static final int API_VERSION = 3; // ref_library (refexport_t)

	public final static int DVIS_PVS = 0;
	public final static int DVIS_PHS = 1;

	// ----------------
	// client/keydest_t
	public static final int key_game = 0;
	public static final int key_console = 1;
	public static final int key_message = 2;
	public static final int key_menu = 3;

	// ---------------
	// server/server.h
	public static final int cs_free = 0; // can be reused for a new connection
	public static final int cs_zombie = 1; // client has been disconnected, but don't reuse
	// connection for a couple seconds
	public static final int cs_connected = 2; // has been assigned to a client_t, but not in game yet
	public static final int cs_spawned = 3;

	public static final int MAX_CHALLENGES = 1024;

	public static final int ss_dead = 0; // no map loaded
	public static final int ss_loading = 1; // spawning level edicts
	public static final int ss_game = 2; // actively running
	public static final int ss_cinematic = 3;
	public static final int ss_demo = 4;
	public static final int ss_pic = 5;

	public final static int SV_OUTPUTBUF_LENGTH = (MAX_MSGLEN - 16);
	public final static int RD_NONE = 0;
	public final static int RD_CLIENT = 1;
	public final static int RD_PACKET = 2;

	public final static int RATE_MESSAGES = 10;

	public final static int LATENCY_COUNTS = 16;

	public static final int MAXCMDLINE = 256;

	public static final int MAX_MASTERS = 8;

	//server/sv_world.h
	public static final int AREA_DEPTH = 4;
	public static final int AREA_NODES = 32;

	public static final int EXEC_NOW = 0;
	public static final int EXEC_INSERT = 1;
	public static final int EXEC_APPEND = 2;

	//client/qmenu.h
	public final static int MAXMENUITEMS = 64;

	public final static int MTYPE_SLIDER = 0;
	public final static int MTYPE_LIST = 1;
	public final static int MTYPE_ACTION = 2;
	public final static int MTYPE_SPINCONTROL = 3;
	public final static int MTYPE_SEPARATOR = 4;
	public final static int MTYPE_FIELD = 5;

	public final static int K_TAB = 9;
	public final static int K_ENTER = 13;
	public final static int K_ESCAPE = 27;
	public final static int K_SPACE = 32;

	// normal keys should be passed as lowercased ascii

	public final static int K_BACKSPACE = 127;
	public final static int K_UPARROW = 128;
	public final static int K_DOWNARROW = 129;
	public final static int K_LEFTARROW = 130;
	public final static int K_RIGHTARROW = 131;

	public final static int QMF_LEFT_JUSTIFY = 0x00000001;
	public final static int QMF_GRAYED = 0x00000002;
	public final static int QMF_NUMBERSONLY = 0x00000004;

	public final static int RCOLUMN_OFFSET = 16;
	public final static int LCOLUMN_OFFSET = -16;

	public final static int MAX_DISPLAYNAME = 16;
	public final static int MAX_PLAYERMODELS = 1024;

	public final static int MAX_LOCAL_SERVERS = 8;
	public final static String NO_SERVER_STRING = "<no server>";
	public final static int NUM_ADDRESSBOOK_ENTRIES = 9;

	public final static int STEPSIZE = 18;


	public static final float MOVE_STOP_EPSILON = 0.1f;

	/*
	==================
	PM_StepSlideMove
	
	Each intersection will try to step over the obstruction instead of
	sliding along it.
	
	Returns a new origin, velocity, and contact entity
	Does not modify any world state?
	==================
	*/
	public final static float MIN_STEP_NORMAL = 0.7f; // can't step up onto very steep slopes


	// used by filefinders in Sys
	public final static int FILEISREADABLE = 1;

	public final static int FILEISWRITABLE = 2;

	public final static int FILEISFILE = 4;

	public final static int FILEISDIRECTORY = 8;

	// datentyp konstanten
	// groesse in bytes
	public final static int SIZE_OF_SHORT = 2;

	public final static int SIZE_OF_INT = 4;

	public final static int SIZE_OF_LONG = 8;

	public final static int SIZE_OF_FLOAT = 4;

	public final static int SIZE_OF_DOUBLE = 8;}
