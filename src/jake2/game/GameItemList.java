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

// Created on 20.11.2005 by RST.
// $Id: GameItemList.java,v 1.2 2006-01-21 21:53:32 salomo Exp $

package jake2.game;


import jake2.qcommon.Defines;


public class GameItemList {

	// RST: this was separated in the java conversion from the g_item.c 
	// because all adapters have to be created in the other 
	// classes before this class can be loaded.

	public static gitem_t itemlist[] = {
	//leave index 0 alone
	new gitem_t(null, null, null, null, null, null, null, 0, null,
	        null, null, 0, 0, null, 0, 0, null, 0, null),
	
	//
	// ARMOR
	//
	new gitem_t(
			
	/**
	 * QUAKED item_armor_body (.3 .3 1) (-16 -16 -16) (16 16 16)
	 */
	
	"item_armor_body", GameItems.Pickup_Armor, null, null, null,
	        "misc/ar1_pkup.wav", "models/items/armor/body/tris.md2",
	        Defines.EF_ROTATE, null,
	        /* icon */
	        "i_bodyarmor",
	        /* pickup */
	        "Body Armor",
	        /* width */
	        3, 0, null, Defines.IT_ARMOR, 0, GameItems.bodyarmor_info,
	        Defines.ARMOR_BODY,
	        /* precache */
	        ""),
	
	/*
	 * QUAKED item_armor_combat (.3 .3 1) (-16 -16 -16) (16 16 16)
	 */
	new gitem_t("item_armor_combat", GameItems.Pickup_Armor, null, null, null,
	        "misc/ar1_pkup.wav", "models/items/armor/combat/tris.md2",
	        Defines.EF_ROTATE, null,
	        /* icon */
	        "i_combatarmor",
	        /* pickup */
	        "Combat Armor",
	        /* width */
	        3, 0, null, Defines.IT_ARMOR, 0, GameItems.combatarmor_info,
	        Defines.ARMOR_COMBAT,
	        /* precache */
	        ""),
	
	/*
	 * QUAKED item_armor_jacket (.3 .3 1) (-16 -16 -16) (16 16 16)
	 */
	new gitem_t("item_armor_jacket", GameItems.Pickup_Armor, null, null, null,
	        "misc/ar1_pkup.wav", "models/items/armor/jacket/tris.md2",
	        Defines.EF_ROTATE, null,
	        /* icon */
	        "i_jacketarmor",
	        /* pickup */
	        "Jacket Armor",
	        /* width */
	        3, 0, null, Defines.IT_ARMOR, 0, GameItems.jacketarmor_info,
	        Defines.ARMOR_JACKET,
	        /* precache */
	        ""),
	
	/*
	 * QUAKED item_armor_shard (.3 .3 1) (-16 -16 -16) (16 16 16)
	 */
	new gitem_t("item_armor_shard", GameItems.Pickup_Armor, null, null, null,
	        "misc/ar2_pkup.wav", "models/items/armor/shard/tris.md2",
	        Defines.EF_ROTATE, null,
	        /* icon */
	        "i_jacketarmor",
	        /* pickup */
	        "Armor Shard",
	        /* width */
	        3, 0, null, Defines.IT_ARMOR, 0, null, Defines.ARMOR_SHARD,
	        /* precache */
	        ""),
	
	/*
	 * QUAKED item_power_screen (.3 .3 1) (-16 -16 -16) (16 16 16)
	 */
	new gitem_t("item_power_screen", GameItems.Pickup_PowerArmor, GameItems.Use_PowerArmor,
	        GameItems.Drop_PowerArmor, null, "misc/ar3_pkup.wav",
	        "models/items/armor/screen/tris.md2", Defines.EF_ROTATE,
	        null,
	        /* icon */
	        "i_powerscreen",
	        /* pickup */
	        "Power Screen",
	        /* width */
	        0, 60, null, Defines.IT_ARMOR, 0, null, 0,
	        /* precache */
	        ""),
	
	/*
	 * QUAKED item_power_shield (.3 .3 1) (-16 -16 -16) (16 16 16)
	 */
	new gitem_t("item_power_shield", GameItems.Pickup_PowerArmor, GameItems.Use_PowerArmor,
	        GameItems.Drop_PowerArmor, null, "misc/ar3_pkup.wav",
	        "models/items/armor/shield/tris.md2", Defines.EF_ROTATE,
	        null,
	        /* icon */
	        "i_powershield",
	        /* pickup */
	        "Power Shield",
	        /* width */
	        0, 60, null, Defines.IT_ARMOR, 0, null, 0,
	        /* precache */
	        "misc/power2.wav misc/power1.wav"),
	
	//
	// WEAPONS
	//
	
	/*
	 * weapon_blaster (.3 .3 1) (-16 -16 -16) (16 16 16) always owned,
	 * never in the world
	 */
	new gitem_t("weapon_blaster", null, PlayerWeapon.Use_Weapon, null,
	        PlayerWeapon.Weapon_Blaster, "misc/w_pkup.wav", null, 0,
	        "models/weapons/v_blast/tris.md2",
	        /* icon */
	        "w_blaster",
	        /* pickup */
	        "Blaster", 0, 0, null, Defines.IT_WEAPON
	                | Defines.IT_STAY_COOP, Defines.WEAP_BLASTER, null,
	        0,
	        /* precache */
	        "weapons/blastf1a.wav misc/lasfly.wav"),
	
	/*
	 * QUAKED weapon_shotgun (.3 .3 1) (-16 -16 -16) (16 16 16)
	 */
	new gitem_t("weapon_shotgun", PlayerWeapon.Pickup_Weapon,
	        PlayerWeapon.Use_Weapon, PlayerWeapon.Drop_Weapon,
	        PlayerWeapon.Weapon_Shotgun, "misc/w_pkup.wav",
	        "models/weapons/g_shotg/tris.md2", Defines.EF_ROTATE,
	        "models/weapons/v_shotg/tris.md2",
	        /* icon */
	        "w_shotgun",
	        /* pickup */
	        "Shotgun", 0, 1, "Shells", Defines.IT_WEAPON
	                | Defines.IT_STAY_COOP, Defines.WEAP_SHOTGUN, null,
	        0,
	        /* precache */
	        "weapons/shotgf1b.wav weapons/shotgr1b.wav"),
	
	/*
	 * QUAKED weapon_supershotgun (.3 .3 1) (-16 -16 -16) (16 16 16)
	 */
	new gitem_t("weapon_supershotgun", PlayerWeapon.Pickup_Weapon,
	        PlayerWeapon.Use_Weapon, PlayerWeapon.Drop_Weapon,
	        PlayerWeapon.Weapon_SuperShotgun, "misc/w_pkup.wav",
	        "models/weapons/g_shotg2/tris.md2", Defines.EF_ROTATE,
	        "models/weapons/v_shotg2/tris.md2",
	        /* icon */
	        "w_sshotgun",
	        /* pickup */
	        "Super Shotgun", 0, 2, "Shells", Defines.IT_WEAPON
	                | Defines.IT_STAY_COOP, Defines.WEAP_SUPERSHOTGUN,
	        null, 0,
	        /* precache */
	        "weapons/sshotf1b.wav"),
	
	/*
	 * QUAKED weapon_machinegun (.3 .3 1) (-16 -16 -16) (16 16 16)
	 */
	new gitem_t(
	        "weapon_machinegun",
	        PlayerWeapon.Pickup_Weapon,
	        PlayerWeapon.Use_Weapon,
	        PlayerWeapon.Drop_Weapon,
	        PlayerWeapon.Weapon_Machinegun,
	        "misc/w_pkup.wav",
	        "models/weapons/g_machn/tris.md2",
	        Defines.EF_ROTATE,
	        "models/weapons/v_machn/tris.md2",
	        /* icon */
	        "w_machinegun",
	        /* pickup */
	        "Machinegun",
	        0,
	        1,
	        "Bullets",
	        Defines.IT_WEAPON | Defines.IT_STAY_COOP,
	        Defines.WEAP_MACHINEGUN,
	        null,
	        0,
	        /* precache */
	        "weapons/machgf1b.wav weapons/machgf2b.wav weapons/machgf3b.wav weapons/machgf4b.wav weapons/machgf5b.wav"),
	
	/*
	 * QUAKED weapon_chaingun (.3 .3 1) (-16 -16 -16) (16 16 16)
	 */
	new gitem_t(
	        "weapon_chaingun",
	        PlayerWeapon.Pickup_Weapon,
	        PlayerWeapon.Use_Weapon,
	        PlayerWeapon.Drop_Weapon,
	        PlayerWeapon.Weapon_Chaingun,
	        "misc/w_pkup.wav",
	        "models/weapons/g_chain/tris.md2",
	        Defines.EF_ROTATE,
	        "models/weapons/v_chain/tris.md2",
	        /* icon */
	        "w_chaingun",
	        /* pickup */
	        "Chaingun",
	        0,
	        1,
	        "Bullets",
	        Defines.IT_WEAPON | Defines.IT_STAY_COOP,
	        Defines.WEAP_CHAINGUN,
	        null,
	        0,
	        /* precache */
	        "weapons/chngnu1a.wav weapons/chngnl1a.wav weapons/machgf3b.wav` weapons/chngnd1a.wav"),
	
	/*
	 * QUAKED ammo_grenades (.3 .3 1) (-16 -16 -16) (16 16 16)
	 */
	new gitem_t(
	        "ammo_grenades",
	        GameItems.Pickup_Ammo,
	        PlayerWeapon.Use_Weapon,
	        GameItems.Drop_Ammo,
	        PlayerWeapon.Weapon_Grenade,
	        "misc/am_pkup.wav",
	        "models/items/ammo/grenades/medium/tris.md2",
	        0,
	        "models/weapons/v_handgr/tris.md2",
	        /* icon */
	        "a_grenades",
	        /* pickup */
	        "Grenades",
	        /* width */
	        3,
	        5,
	        "grenades",
	        Defines.IT_AMMO | Defines.IT_WEAPON,
	        Defines.WEAP_GRENADES,
	        null,
	        Defines.AMMO_GRENADES,
	        /* precache */
	        "weapons/hgrent1a.wav weapons/hgrena1b.wav weapons/hgrenc1b.wav weapons/hgrenb1a.wav weapons/hgrenb2a.wav "),
	
	/*
	 * QUAKED weapon_grenadelauncher (.3 .3 1) (-16 -16 -16) (16 16 16)
	 */
	new gitem_t(
	        "weapon_grenadelauncher",
	        PlayerWeapon.Pickup_Weapon,
	        PlayerWeapon.Use_Weapon,
	        PlayerWeapon.Drop_Weapon,
	        PlayerWeapon.Weapon_GrenadeLauncher,
	        "misc/w_pkup.wav",
	        "models/weapons/g_launch/tris.md2",
	        Defines.EF_ROTATE,
	        "models/weapons/v_launch/tris.md2",
	        /* icon */
	        "w_glauncher",
	        /* pickup */
	        "Grenade Launcher",
	        0,
	        1,
	        "Grenades",
	        Defines.IT_WEAPON | Defines.IT_STAY_COOP,
	        Defines.WEAP_GRENADELAUNCHER,
	        null,
	        0,
	        /* precache */
	        "models/objects/grenade/tris.md2 weapons/grenlf1a.wav weapons/grenlr1b.wav weapons/grenlb1b.wav"),
	
	/*
	 * QUAKED weapon_rocketlauncher (.3 .3 1) (-16 -16 -16) (16 16 16)
	 */
	new gitem_t(
	        "weapon_rocketlauncher",
	        PlayerWeapon.Pickup_Weapon,
	        PlayerWeapon.Use_Weapon,
	        PlayerWeapon.Drop_Weapon,
	        PlayerWeapon.Weapon_RocketLauncher,
	        "misc/w_pkup.wav",
	        "models/weapons/g_rocket/tris.md2",
	        Defines.EF_ROTATE,
	        "models/weapons/v_rocket/tris.md2",
	        /* icon */
	        "w_rlauncher",
	        /* pickup */
	        "Rocket Launcher",
	        0,
	        1,
	        "Rockets",
	        Defines.IT_WEAPON | Defines.IT_STAY_COOP,
	        Defines.WEAP_ROCKETLAUNCHER,
	        null,
	        0,
	        /* precache */
	        "models/objects/rocket/tris.md2 weapons/rockfly.wav weapons/rocklf1a.wav weapons/rocklr1b.wav models/objects/debris2/tris.md2"),
	
	/*
	 * QUAKED weapon_hyperblaster (.3 .3 1) (-16 -16 -16) (16 16 16)
	 */
	new gitem_t(
	        "weapon_hyperblaster",
	        PlayerWeapon.Pickup_Weapon,
	        PlayerWeapon.Use_Weapon,
	        PlayerWeapon.Drop_Weapon,
	        PlayerWeapon.Weapon_HyperBlaster,
	        "misc/w_pkup.wav",
	        "models/weapons/g_hyperb/tris.md2",
	        Defines.EF_ROTATE,
	        "models/weapons/v_hyperb/tris.md2",
	        /* icon */
	        "w_hyperblaster",
	        /* pickup */
	        "HyperBlaster",
	        0,
	        1,
	        "Cells",
	        Defines.IT_WEAPON | Defines.IT_STAY_COOP,
	        Defines.WEAP_HYPERBLASTER,
	        null,
	        0,
	        /* precache */
	        "weapons/hyprbu1a.wav weapons/hyprbl1a.wav weapons/hyprbf1a.wav weapons/hyprbd1a.wav misc/lasfly.wav"),
	
	/*
	 * QUAKED weapon_railgun (.3 .3 1) (-16 -16 -16) (16 16 16)
	 */
	new gitem_t("weapon_railgun", PlayerWeapon.Pickup_Weapon,
	        PlayerWeapon.Use_Weapon, PlayerWeapon.Drop_Weapon,
	        PlayerWeapon.Weapon_Railgun, "misc/w_pkup.wav",
	        "models/weapons/g_rail/tris.md2", Defines.EF_ROTATE,
	        "models/weapons/v_rail/tris.md2",
	        /* icon */
	        "w_railgun",
	        /* pickup */
	        "Railgun", 0, 1, "Slugs", Defines.IT_WEAPON
	                | Defines.IT_STAY_COOP, Defines.WEAP_RAILGUN, null,
	        0,
	        /* precache */
	        "weapons/rg_hum.wav"),
	
	/*
	 * QUAKED weapon_bfg (.3 .3 1) (-16 -16 -16) (16 16 16)
	 */
	new gitem_t(
	        "weapon_bfg",
	        PlayerWeapon.Pickup_Weapon,
	        PlayerWeapon.Use_Weapon,
	        PlayerWeapon.Drop_Weapon,
	        PlayerWeapon.Weapon_BFG,
	        "misc/w_pkup.wav",
	        "models/weapons/g_bfg/tris.md2",
	        Defines.EF_ROTATE,
	        "models/weapons/v_bfg/tris.md2",
	        /* icon */
	        "w_bfg",
	        /* pickup */
	        "BFG10K",
	        0,
	        50,
	        "Cells",
	        Defines.IT_WEAPON | Defines.IT_STAY_COOP,
	        Defines.WEAP_BFG,
	        null,
	        0,
	        /* precache */
	        "sprites/s_bfg1.sp2 sprites/s_bfg2.sp2 sprites/s_bfg3.sp2 weapons/bfg__f1y.wav weapons/bfg__l1a.wav weapons/bfg__x1b.wav weapons/bfg_hum.wav"),
	
	//
	// AMMO ITEMS
	//
	
	/*
	 * QUAKED ammo_shells (.3 .3 1) (-16 -16 -16) (16 16 16)
	 */
	new gitem_t("ammo_shells", GameItems.Pickup_Ammo, null, GameItems.Drop_Ammo, null,
	        "misc/am_pkup.wav",
	        "models/items/ammo/shells/medium/tris.md2", 0, null,
	        /* icon */
	        "a_shells",
	        /* pickup */
	        "Shells",
	        /* width */
	        3, 10, null, Defines.IT_AMMO, 0, null, Defines.AMMO_SHELLS,
	        /* precache */
	        ""),
	
	/*
	 * QUAKED ammo_bullets (.3 .3 1) (-16 -16 -16) (16 16 16)
	 */
	new gitem_t("ammo_bullets", GameItems.Pickup_Ammo, null, GameItems.Drop_Ammo, null,
	        "misc/am_pkup.wav",
	        "models/items/ammo/bullets/medium/tris.md2", 0, null,
	        /* icon */
	        "a_bullets",
	        /* pickup */
	        "Bullets",
	        /* width */
	        3, 50, null, Defines.IT_AMMO, 0, null,
	        Defines.AMMO_BULLETS,
	        /* precache */
	        ""),
	
	/*
	 * QUAKED ammo_cells (.3 .3 1) (-16 -16 -16) (16 16 16)
	 */
	new gitem_t("ammo_cells", GameItems.Pickup_Ammo, null, GameItems.Drop_Ammo, null,
	        "misc/am_pkup.wav",
	        "models/items/ammo/cells/medium/tris.md2", 0, null,
	        /* icon */
	        "a_cells",
	        /* pickup */
	        "Cells",
	        /* width */
	        3, 50, null, Defines.IT_AMMO, 0, null, Defines.AMMO_CELLS,
	        /* precache */
	        ""),
	
	/*
	 * QUAKED ammo_rockets (.3 .3 1) (-16 -16 -16) (16 16 16)
	 */
	new gitem_t("ammo_rockets", GameItems.Pickup_Ammo, null, GameItems.Drop_Ammo, null,
	        "misc/am_pkup.wav",
	        "models/items/ammo/rockets/medium/tris.md2", 0, null,
	        /* icon */
	        "a_rockets",
	        /* pickup */
	        "Rockets",
	        /* width */
	        3, 5, null, Defines.IT_AMMO, 0, null, Defines.AMMO_ROCKETS,
	        /* precache */
	        ""),
	
	/*
	 * QUAKED ammo_slugs (.3 .3 1) (-16 -16 -16) (16 16 16)
	 */
	new gitem_t("ammo_slugs", GameItems.Pickup_Ammo, null, GameItems.Drop_Ammo, null,
	        "misc/am_pkup.wav",
	        "models/items/ammo/slugs/medium/tris.md2", 0, null,
	        /* icon */
	        "a_slugs",
	        /* pickup */
	        "Slugs",
	        /* width */
	        3, 10, null, Defines.IT_AMMO, 0, null, Defines.AMMO_SLUGS,
	        /* precache */
	        ""),
	
	//
	// POWERUP ITEMS
	//
	/*
	 * QUAKED item_quad (.3 .3 1) (-16 -16 -16) (16 16 16)
	 */
	new gitem_t("item_quad", GameItems.Pickup_Powerup, GameItems.Use_Quad,
	        GameItems.Drop_General, null, "items/pkup.wav",
	        "models/items/quaddama/tris.md2", Defines.EF_ROTATE, null,
	        /* icon */
	        "p_quad",
	        /* pickup */
	        "Quad Damage",
	        /* width */
	        2, 60, null, Defines.IT_POWERUP, 0, null, 0,
	        /* precache */
	        "items/damage.wav items/damage2.wav items/damage3.wav"),
	
	/*
	 * QUAKED item_invulnerability (.3 .3 1) (-16 -16 -16) (16 16 16)
	 */
	new gitem_t("item_invulnerability", GameItems.Pickup_Powerup,
	        GameItems.Use_Invulnerability, GameItems.Drop_General, null,
	        "items/pkup.wav", "models/items/invulner/tris.md2",
	        Defines.EF_ROTATE, null,
	        /* icon */
	        "p_invulnerability",
	        /* pickup */
	        "Invulnerability",
	        /* width */
	        2, 300, null, Defines.IT_POWERUP, 0, null, 0,
	        /* precache */
	        "items/protect.wav items/protect2.wav items/protect4.wav"),
	
	/*
	 * QUAKED item_silencer (.3 .3 1) (-16 -16 -16) (16 16 16)
	 */
	new gitem_t("item_silencer", GameItems.Pickup_Powerup, GameItems.Use_Silencer,
	        GameItems.Drop_General, null, "items/pkup.wav",
	        "models/items/silencer/tris.md2", Defines.EF_ROTATE, null,
	        /* icon */
	        "p_silencer",
	        /* pickup */
	        "Silencer",
	        /* width */
	        2, 60, null, Defines.IT_POWERUP, 0, null, 0,
	        /* precache */
	        ""),
	
	/*
	 * QUAKED item_breather (.3 .3 1) (-16 -16 -16) (16 16 16)
	 */
	new gitem_t("item_breather", GameItems.Pickup_Powerup, GameItems.Use_Breather,
	        GameItems.Drop_General, null, "items/pkup.wav",
	        "models/items/breather/tris.md2", Defines.EF_ROTATE, null,
	        /* icon */
	        "p_rebreather",
	        /* pickup */
	        "Rebreather",
	        /* width */
	        2, 60, null, Defines.IT_STAY_COOP | Defines.IT_POWERUP, 0,
	        null, 0,
	        /* precache */
	        "items/airout.wav"),
	
	/*
	 * QUAKED item_enviro (.3 .3 1) (-16 -16 -16) (16 16 16)
	 */
	new gitem_t("item_enviro", GameItems.Pickup_Powerup, GameItems.Use_Envirosuit,
	        GameItems.Drop_General, null, "items/pkup.wav",
	        "models/items/enviro/tris.md2", Defines.EF_ROTATE, null,
	        /* icon */
	        "p_envirosuit",
	        /* pickup */
	        "Environment Suit",
	        /* width */
	        2, 60, null, Defines.IT_STAY_COOP | Defines.IT_POWERUP, 0,
	        null, 0,
	        /* precache */
	        "items/airout.wav"),
	
	/*
	 * QUAKED item_ancient_head (.3 .3 1) (-16 -16 -16) (16 16 16)
	 * Special item that gives +2 to maximum health
	 */
	new gitem_t("item_ancient_head", GameItems.Pickup_AncientHead, null, null,
	        null, "items/pkup.wav", "models/items/c_head/tris.md2",
	        Defines.EF_ROTATE, null,
	        /* icon */
	        "i_fixme",
	        /* pickup */
	        "Ancient Head",
	        /* width */
	        2, 60, null, 0, 0, null, 0,
	        /* precache */
	        ""),
	
	/*
	 * QUAKED item_adrenaline (.3 .3 1) (-16 -16 -16) (16 16 16) gives
	 * +1 to maximum health
	 */
	new gitem_t("item_adrenaline", GameItems.Pickup_Adrenaline, null, null, null,
	        "items/pkup.wav", "models/items/adrenal/tris.md2",
	        Defines.EF_ROTATE, null,
	        /* icon */
	        "p_adrenaline",
	        /* pickup */
	        "Adrenaline",
	        /* width */
	        2, 60, null, 0, 0, null, 0,
	        /* precache */
	        ""),
	
	/*
	 * QUAKED item_bandolier (.3 .3 1) (-16 -16 -16) (16 16 16)
	 */
	new gitem_t("item_bandolier", GameItems.Pickup_Bandolier, null, null, null,
	        "items/pkup.wav", "models/items/band/tris.md2",
	        Defines.EF_ROTATE, null,
	        /* icon */
	        "p_bandolier",
	        /* pickup */
	        "Bandolier",
	        /* width */
	        2, 60, null, 0, 0, null, 0,
	        /* precache */
	        ""),
	
	/*
	 * QUAKED item_pack (.3 .3 1) (-16 -16 -16) (16 16 16)
	 */
	new gitem_t("item_pack", GameItems.Pickup_Pack, null, null, null,
	        "items/pkup.wav", "models/items/pack/tris.md2",
	        Defines.EF_ROTATE, null,
	        /* icon */
	        "i_pack",
	        /* pickup */
	        "Ammo Pack",
	        /* width */
	        2, 180, null, 0, 0, null, 0,
	        /* precache */
	        ""),
	
	//
	// KEYS
	//
	/*
	 * QUAKED key_data_cd (0 .5 .8) (-16 -16 -16) (16 16 16) key for
	 * computer centers
	 */
	new gitem_t("key_data_cd", GameItems.Pickup_Key, null, GameItems.Drop_General,
	        null, "items/pkup.wav",
	        "models/items/keys/data_cd/tris.md2", Defines.EF_ROTATE,
	        null, "k_datacd", "Data CD", 2, 0, null,
	        Defines.IT_STAY_COOP | Defines.IT_KEY, 0, null, 0,
	        /* precache */
	        ""),
	
	/*
	 * QUAKED key_power_cube (0 .5 .8) (-16 -16 -16) (16 16 16)
	 * TRIGGER_SPAWN NO_TOUCH warehouse circuits
	 */
	new gitem_t("key_power_cube", GameItems.Pickup_Key, null,
	        GameItems.Drop_General, null, "items/pkup.wav",
	        "models/items/keys/power/tris.md2", Defines.EF_ROTATE,
	        null, "k_powercube", "Power Cube", 2, 0, null,
	        Defines.IT_STAY_COOP | Defines.IT_KEY, 0, null, 0,
	        /* precache */
	        ""),
	
	/*
	 * QUAKED key_pyramid (0 .5 .8) (-16 -16 -16) (16 16 16) key for the
	 * entrance of jail3
	 */
	new gitem_t("key_pyramid", GameItems.Pickup_Key, null, GameItems.Drop_General,
	        null, "items/pkup.wav",
	        "models/items/keys/pyramid/tris.md2", Defines.EF_ROTATE,
	        null, "k_pyramid", "Pyramid Key", 2, 0, null,
	        Defines.IT_STAY_COOP | Defines.IT_KEY, 0, null, 0,
	        /* precache */
	        ""),
	
	/*
	 * QUAKED key_data_spinner (0 .5 .8) (-16 -16 -16) (16 16 16) key
	 * for the city computer
	 */
	new gitem_t("key_data_spinner", GameItems.Pickup_Key, null,
	        GameItems.Drop_General, null, "items/pkup.wav",
	        "models/items/keys/spinner/tris.md2", Defines.EF_ROTATE,
	        null, "k_dataspin", "Data Spinner", 2, 0, null,
	        Defines.IT_STAY_COOP | Defines.IT_KEY, 0, null, 0,
	        /* precache */
	        ""),
	
	/*
	 * QUAKED key_pass (0 .5 .8) (-16 -16 -16) (16 16 16) security pass
	 * for the security level
	 */
	new gitem_t("key_pass", GameItems.Pickup_Key, null, GameItems.Drop_General,
	        null, "items/pkup.wav", "models/items/keys/pass/tris.md2",
	        Defines.EF_ROTATE, null, "k_security", "Security Pass", 2,
	        0, null, Defines.IT_STAY_COOP | Defines.IT_KEY, 0, null, 0,
	        /* precache */
	        ""),
	
	/*
	 * QUAKED key_blue_key (0 .5 .8) (-16 -16 -16) (16 16 16) normal
	 * door key - blue
	 */
	new gitem_t("key_blue_key", GameItems.Pickup_Key, null,
	        GameItems.Drop_General, null, "items/pkup.wav",
	        "models/items/keys/key/tris.md2", Defines.EF_ROTATE, null,
	        "k_bluekey", "Blue Key", 2, 0, null, Defines.IT_STAY_COOP
	                | Defines.IT_KEY, 0, null, 0,
	        /* precache */
	        ""),
	
	/*
	 * QUAKED key_red_key (0 .5 .8) (-16 -16 -16) (16 16 16) normal door
	 * key - red
	 */
	new gitem_t("key_red_key", GameItems.Pickup_Key, null, GameItems.Drop_General,
	        null, "items/pkup.wav",
	        "models/items/keys/red_key/tris.md2", Defines.EF_ROTATE,
	        null, "k_redkey", "Red Key", 2, 0, null,
	        Defines.IT_STAY_COOP | Defines.IT_KEY, 0, null, 0,
	        /* precache */
	        ""),
	
	/*
	 * QUAKED key_commander_head (0 .5 .8) (-16 -16 -16) (16 16 16) tank
	 * commander's head
	 */
	new gitem_t("key_commander_head", GameItems.Pickup_Key, null,
	        GameItems.Drop_General, null, "items/pkup.wav",
	        "models/monsters/commandr/head/tris.md2", Defines.EF_GIB,
	        null,
	        /* icon */
	        "k_comhead",
	        /* pickup */
	        "Commander's Head",
	        /* width */
	        2, 0, null, Defines.IT_STAY_COOP | Defines.IT_KEY, 0, null,
	        0,
	        /* precache */
	        ""),
	
	/*
	 * QUAKED key_airstrike_target (0 .5 .8) (-16 -16 -16) (16 16 16)
	 * tank commander's head
	 */
	new gitem_t("key_airstrike_target", GameItems.Pickup_Key, null,
	        GameItems.Drop_General, null, "items/pkup.wav",
	        "models/items/keys/target/tris.md2", Defines.EF_ROTATE,
	        null,
	        /* icon */
	        "i_airstrike",
	        /* pickup */
	        "Airstrike Marker",
	        /* width */
	        2, 0, null, Defines.IT_STAY_COOP | Defines.IT_KEY, 0, null,
	        0,
	        /* precache */
	        ""),
	new gitem_t(null, GameItems.Pickup_Health, null, null, null,
	        "items/pkup.wav", null, 0, null,
	        /* icon */
	        "i_health",
	        /* pickup */
	        "Health",
	        /* width */
	        3, 0, null, 0, 0, null, 0,
	        /* precache */
	        "items/s_health.wav items/n_health.wav items/l_health.wav items/m_health.wav"),
	
	// end of list marker
	null };
}
