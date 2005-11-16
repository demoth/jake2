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

// Created on 16.11.2005 by RST.
// $Id: GameItems.java,v 1.1 2005-11-16 22:24:53 salomo Exp $

package jake2.game;


import java.util.StringTokenizer;

import jake2.*;
import jake2.client.*;
import jake2.game.*;
import jake2.qcommon.*;
import jake2.render.*;
import jake2.server.*;
import jake2.util.Lib;
import jake2.util.Math3D;


public class GameItems {

    public static gitem_t itemlist[] = {
    //leave index 0 alone
    new gitem_t(null, null, null, null, null, null, null, 0, null,
            null, null, 0, 0, null, 0, 0, null, 0, null),
    
    //
    // ARMOR
    //
    new gitem_t(
    /*
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
    public static gitem_armor_t jacketarmor_info = new gitem_armor_t(25, 50,
    .30f, .00f, Defines.ARMOR_JACKET);
    public static gitem_armor_t combatarmor_info = new gitem_armor_t(50, 100,
    .60f, .30f, Defines.ARMOR_COMBAT);
    public static gitem_armor_t bodyarmor_info = new gitem_armor_t(100, 200,
    .80f, .60f, Defines.ARMOR_BODY);
    static int quad_drop_timeout_hack = 0;
    static int jacket_armor_index;
    static int combat_armor_index;
    static int body_armor_index;
    static int power_screen_index;
    static int power_shield_index;
    static EntThinkAdapter DoRespawn = new EntThinkAdapter() {
        public boolean think(edict_t ent) {
            if (ent.team != null) {
                edict_t master;
                int count;
                int choice = 0;
    
                master = ent.teammaster;
    
                // count the depth
                for (count = 0, ent = master; ent != null; ent = ent.chain, count++)
                    ;
    
                choice = Lib.rand() % count;
    
                for (count = 0, ent = master; count < choice; ent = ent.chain, count++)
                    ;
            }
    
            ent.svflags &= ~Defines.SVF_NOCLIENT;
            ent.solid = Defines.SOLID_TRIGGER;
            GameBase.gi.linkentity(ent);
    
            // send an effect
            ent.s.event = Defines.EV_ITEM_RESPAWN;
    
            return false;
        }
    };
    static EntInteractAdapter Pickup_Pack = new EntInteractAdapter() {
        public boolean interact(edict_t ent, edict_t other) {
    
            gitem_t item;
            int index;
    
            if (other.client.pers.max_bullets < 300)
                other.client.pers.max_bullets = 300;
            if (other.client.pers.max_shells < 200)
                other.client.pers.max_shells = 200;
            if (other.client.pers.max_rockets < 100)
                other.client.pers.max_rockets = 100;
            if (other.client.pers.max_grenades < 100)
                other.client.pers.max_grenades = 100;
            if (other.client.pers.max_cells < 300)
                other.client.pers.max_cells = 300;
            if (other.client.pers.max_slugs < 100)
                other.client.pers.max_slugs = 100;
    
            item = FindItem("Bullets");
            if (item != null) {
                index = ITEM_INDEX(item);
                other.client.pers.inventory[index] += item.quantity;
                if (other.client.pers.inventory[index] > other.client.pers.max_bullets)
                    other.client.pers.inventory[index] = other.client.pers.max_bullets;
            }
    
            item = FindItem("Shells");
            if (item != null) {
                index = ITEM_INDEX(item);
                other.client.pers.inventory[index] += item.quantity;
                if (other.client.pers.inventory[index] > other.client.pers.max_shells)
                    other.client.pers.inventory[index] = other.client.pers.max_shells;
            }
    
            item = FindItem("Cells");
            if (item != null) {
                index = ITEM_INDEX(item);
                other.client.pers.inventory[index] += item.quantity;
                if (other.client.pers.inventory[index] > other.client.pers.max_cells)
                    other.client.pers.inventory[index] = other.client.pers.max_cells;
            }
    
            item = FindItem("Grenades");
            if (item != null) {
                index = ITEM_INDEX(item);
                other.client.pers.inventory[index] += item.quantity;
                if (other.client.pers.inventory[index] > other.client.pers.max_grenades)
                    other.client.pers.inventory[index] = other.client.pers.max_grenades;
            }
    
            item = FindItem("Rockets");
            if (item != null) {
                index = ITEM_INDEX(item);
                other.client.pers.inventory[index] += item.quantity;
                if (other.client.pers.inventory[index] > other.client.pers.max_rockets)
                    other.client.pers.inventory[index] = other.client.pers.max_rockets;
            }
    
            item = FindItem("Slugs");
            if (item != null) {
                index = ITEM_INDEX(item);
                other.client.pers.inventory[index] += item.quantity;
                if (other.client.pers.inventory[index] > other.client.pers.max_slugs)
                    other.client.pers.inventory[index] = other.client.pers.max_slugs;
            }
    
            if (0 == (ent.spawnflags & Defines.DROPPED_ITEM)
                    && (GameBase.deathmatch.value != 0))
                SetRespawn(ent, ent.item.quantity);
    
            return true;
        }
    };
    final static EntInteractAdapter Pickup_Health = new EntInteractAdapter() {
        public boolean interact(edict_t ent, edict_t other) {
    
            if (0 == (ent.style & Defines.HEALTH_IGNORE_MAX))
                if (other.health >= other.max_health)
                    return false;
    
            other.health += ent.count;
    
            if (0 == (ent.style & Defines.HEALTH_IGNORE_MAX)) {
                if (other.health > other.max_health)
                    other.health = other.max_health;
            }
    
            if (0 != (ent.style & Defines.HEALTH_TIMED)) {
                ent.think = GameUtil.MegaHealth_think;
                ent.nextthink = GameBase.level.time + 5f;
                ent.owner = other;
                ent.flags |= Defines.FL_RESPAWN;
                ent.svflags |= Defines.SVF_NOCLIENT;
                ent.solid = Defines.SOLID_NOT;
            } else {
                if (!((ent.spawnflags & Defines.DROPPED_ITEM) != 0)
                        && (GameBase.deathmatch.value != 0))
                    SetRespawn(ent, 30);
            }
    
            return true;
        }
    
    };
    static EntTouchAdapter Touch_Item = new EntTouchAdapter() {
        public void touch(edict_t ent, edict_t other, cplane_t plane,
                csurface_t surf) {
            boolean taken;
    
            if (ent.classname.equals("item_breather"))
                taken = false;
    
            if (other.client == null)
                return;
            if (other.health < 1)
                return; // dead people can't pickup
            if (ent.item.pickup == null)
                return; // not a grabbable item?
    
            taken = ent.item.pickup.interact(ent, other);
    
            if (taken) {
                // flash the screen
                other.client.bonus_alpha = 0.25f;
    
                // show icon and name on status bar
                other.client.ps.stats[Defines.STAT_PICKUP_ICON] = (short) GameBase.gi
                        .imageindex(ent.item.icon);
                other.client.ps.stats[Defines.STAT_PICKUP_STRING] = (short) (Defines.CS_ITEMS + ITEM_INDEX(ent.item));
                other.client.pickup_msg_time = GameBase.level.time + 3.0f;
    
                // change selected item
                if (ent.item.use != null)
                    other.client.pers.selected_item = other.client.ps.stats[Defines.STAT_SELECTED_ITEM] = (short) ITEM_INDEX(ent.item);
    
                if (ent.item.pickup == Pickup_Health) {
                    if (ent.count == 2)
                        GameBase.gi.sound(other, Defines.CHAN_ITEM, GameBase.gi
                                .soundindex("items/s_health.wav"), 1,
                                Defines.ATTN_NORM, 0);
                    else if (ent.count == 10)
                        GameBase.gi.sound(other, Defines.CHAN_ITEM, GameBase.gi
                                .soundindex("items/n_health.wav"), 1,
                                Defines.ATTN_NORM, 0);
                    else if (ent.count == 25)
                        GameBase.gi.sound(other, Defines.CHAN_ITEM, GameBase.gi
                                .soundindex("items/l_health.wav"), 1,
                                Defines.ATTN_NORM, 0);
                    else
                        // (ent.count == 100)
                        GameBase.gi.sound(other, Defines.CHAN_ITEM, GameBase.gi
                                .soundindex("items/m_health.wav"), 1,
                                Defines.ATTN_NORM, 0);
                } else if (ent.item.pickup_sound != null) {
                    GameBase.gi.sound(other, Defines.CHAN_ITEM, GameBase.gi
                            .soundindex(ent.item.pickup_sound), 1,
                            Defines.ATTN_NORM, 0);
                }
            }
    
            if (0 == (ent.spawnflags & Defines.ITEM_TARGETS_USED)) {
                GameUtil.G_UseTargets(ent, other);
                ent.spawnflags |= Defines.ITEM_TARGETS_USED;
            }
    
            if (!taken)
                return;
            
            Com.dprintln("Picked up:" + ent.classname);
    
            if (!((GameBase.coop.value != 0) && (ent.item.flags & Defines.IT_STAY_COOP) != 0)
                    || 0 != (ent.spawnflags & (Defines.DROPPED_ITEM | Defines.DROPPED_PLAYER_ITEM))) {
                if ((ent.flags & Defines.FL_RESPAWN) != 0)
                    ent.flags &= ~Defines.FL_RESPAWN;
                else
                    GameUtil.G_FreeEdict(ent);
            }
        }
    };
    static EntTouchAdapter drop_temp_touch = new EntTouchAdapter() {
        public void touch(edict_t ent, edict_t other, cplane_t plane,
                csurface_t surf) {
            if (other == ent.owner)
                return;
    
            Touch_Item.touch(ent, other, plane, surf);
        }
    };
    static EntThinkAdapter drop_make_touchable = new EntThinkAdapter() {
        public boolean think(edict_t ent) {
            ent.touch = Touch_Item;
            if (GameBase.deathmatch.value != 0) {
                ent.nextthink = GameBase.level.time + 29;
                ent.think = GameUtil.G_FreeEdictA;
            }
            return false;
        }
    };
    static ItemUseAdapter Use_Quad = new ItemUseAdapter() {
    
        public void use(edict_t ent, gitem_t item) {
            int timeout;
    
            ent.client.pers.inventory[ITEM_INDEX(item)]--;
            GameUtil.ValidateSelectedItem(ent);
    
            if (quad_drop_timeout_hack != 0) {
                timeout = quad_drop_timeout_hack;
                quad_drop_timeout_hack = 0;
            } else {
                timeout = 300;
            }
    
            if (ent.client.quad_framenum > GameBase.level.framenum)
                ent.client.quad_framenum += timeout;
            else
                ent.client.quad_framenum = GameBase.level.framenum + timeout;
    
            GameBase.gi.sound(ent, Defines.CHAN_ITEM, GameBase.gi
                    .soundindex("items/damage.wav"), 1, Defines.ATTN_NORM, 0);
        }
    };
    static ItemUseAdapter Use_Invulnerability = new ItemUseAdapter() {
        public void use(edict_t ent, gitem_t item) {
            ent.client.pers.inventory[ITEM_INDEX(item)]--;
            GameUtil.ValidateSelectedItem(ent);
    
            if (ent.client.invincible_framenum > GameBase.level.framenum)
                ent.client.invincible_framenum += 300;
            else
                ent.client.invincible_framenum = GameBase.level.framenum + 300;
    
            GameBase.gi.sound(ent, Defines.CHAN_ITEM, GameBase.gi
                    .soundindex("items/protect.wav"), 1, Defines.ATTN_NORM, 0);
        }
    };
    static ItemUseAdapter Use_Breather = new ItemUseAdapter() {
        public void use(edict_t ent, gitem_t item) {
            ent.client.pers.inventory[ITEM_INDEX(item)]--;
    
            GameUtil.ValidateSelectedItem(ent);
    
            if (ent.client.breather_framenum > GameBase.level.framenum)
                ent.client.breather_framenum += 300;
            else
                ent.client.breather_framenum = GameBase.level.framenum + 300;
    
            GameBase.gi.sound(ent, Defines.CHAN_ITEM, GameBase.gi
                    .soundindex("items/damage.wav"), 1, Defines.ATTN_NORM, 0);
        }
    };
    static ItemUseAdapter Use_Envirosuit = new ItemUseAdapter() {
        public void use(edict_t ent, gitem_t item) {
            ent.client.pers.inventory[ITEM_INDEX(item)]--;
            GameUtil.ValidateSelectedItem(ent);
    
            if (ent.client.enviro_framenum > GameBase.level.framenum)
                ent.client.enviro_framenum += 300;
            else
                ent.client.enviro_framenum = GameBase.level.framenum + 300;
    
            GameBase.gi.sound(ent, Defines.CHAN_ITEM, GameBase.gi
                    .soundindex("items/damage.wav"), 1, Defines.ATTN_NORM, 0);
        }
    };
    static ItemUseAdapter Use_Silencer = new ItemUseAdapter() {
        public void use(edict_t ent, gitem_t item) {
    
            ent.client.pers.inventory[ITEM_INDEX(item)]--;
            GameUtil.ValidateSelectedItem(ent);
            ent.client.silencer_shots += 30;
    
            GameBase.gi.sound(ent, Defines.CHAN_ITEM, GameBase.gi
                    .soundindex("items/damage.wav"), 1, Defines.ATTN_NORM, 0);
        }
    };
    static EntInteractAdapter Pickup_Key = new EntInteractAdapter() {
        public boolean interact(edict_t ent, edict_t other) {
            if (GameBase.coop.value != 0) {
                if (Lib.strcmp(ent.classname, "key_power_cube") == 0) {
                    if ((other.client.pers.power_cubes & ((ent.spawnflags & 0x0000ff00) >> 8)) != 0)
                        return false;
                    other.client.pers.inventory[ITEM_INDEX(ent.item)]++;
                    other.client.pers.power_cubes |= ((ent.spawnflags & 0x0000ff00) >> 8);
                } else {
                    if (other.client.pers.inventory[ITEM_INDEX(ent.item)] != 0)
                        return false;
                    other.client.pers.inventory[ITEM_INDEX(ent.item)] = 1;
                }
                return true;
            }
            other.client.pers.inventory[ITEM_INDEX(ent.item)]++;
            return true;
        }
    };
    public static EntInteractAdapter Pickup_Ammo = new EntInteractAdapter() {
        public boolean interact(edict_t ent, edict_t other) {
            int oldcount;
            int count;
            boolean weapon;
    
            weapon = (ent.item.flags & Defines.IT_WEAPON) != 0;
            if ((weapon)
                    && ((int) GameBase.dmflags.value & Defines.DF_INFINITE_AMMO) != 0)
                count = 1000;
            else if (ent.count != 0)
                count = ent.count;
            else
                count = ent.item.quantity;
    
            oldcount = other.client.pers.inventory[ITEM_INDEX(ent.item)];
    
            if (!Add_Ammo(other, ent.item, count))
                return false;
    
            if (weapon && 0 == oldcount) {
                if (other.client.pers.weapon != ent.item
                        && (0 == GameBase.deathmatch.value || other.client.pers.weapon == FindItem("blaster")))
                    other.client.newweapon = ent.item;
            }
    
            if (0 == (ent.spawnflags & (Defines.DROPPED_ITEM | Defines.DROPPED_PLAYER_ITEM))
                    && (GameBase.deathmatch.value != 0))
                SetRespawn(ent, 30);
            return true;
        }
    };
    public static EntInteractAdapter Pickup_Armor = new EntInteractAdapter() {
        public boolean interact(edict_t ent, edict_t other) {
            int old_armor_index;
            gitem_armor_t oldinfo;
            gitem_armor_t newinfo;
            int newcount;
            float salvage;
            int salvagecount;
    
            // get info on new armor
            newinfo = (gitem_armor_t) ent.item.info;
    
            old_armor_index = ArmorIndex(other);
    
            // handle armor shards specially
            if (ent.item.tag == Defines.ARMOR_SHARD) {
                if (0 == old_armor_index)
                    other.client.pers.inventory[jacket_armor_index] = 2;
                else
                    other.client.pers.inventory[old_armor_index] += 2;
            }
    
            // if player has no armor, just use it
            else if (0 == old_armor_index) {
                other.client.pers.inventory[ITEM_INDEX(ent.item)] = newinfo.base_count;
            }
    
            // use the better armor
            else {
                // get info on old armor
                if (old_armor_index == jacket_armor_index)
                    oldinfo = jacketarmor_info;
    
                else if (old_armor_index == combat_armor_index)
                    oldinfo = combatarmor_info;
    
                else
                    // (old_armor_index == body_armor_index)
                    oldinfo = bodyarmor_info;
    
                if (newinfo.normal_protection > oldinfo.normal_protection) {
                    // calc new armor values
                    salvage = oldinfo.normal_protection
                            / newinfo.normal_protection;
                    salvagecount = (int) salvage
                            * other.client.pers.inventory[old_armor_index];
                    newcount = newinfo.base_count + salvagecount;
                    if (newcount > newinfo.max_count)
                        newcount = newinfo.max_count;
    
                    // zero count of old armor so it goes away
                    other.client.pers.inventory[old_armor_index] = 0;
    
                    // change armor to new item with computed value
                    other.client.pers.inventory[ITEM_INDEX(ent.item)] = newcount;
                } else {
                    // calc new armor values
                    salvage = newinfo.normal_protection
                            / oldinfo.normal_protection;
                    salvagecount = (int) salvage * newinfo.base_count;
                    newcount = other.client.pers.inventory[old_armor_index]
                            + salvagecount;
                    if (newcount > oldinfo.max_count)
                        newcount = oldinfo.max_count;
    
                    // if we're already maxed out then we don't need the new
                    // armor
                    if (other.client.pers.inventory[old_armor_index] >= newcount)
                        return false;
    
                    // update current armor value
                    other.client.pers.inventory[old_armor_index] = newcount;
                }
            }
    
            if (0 == (ent.spawnflags & Defines.DROPPED_ITEM)
                    && (GameBase.deathmatch.value != 0))
                SetRespawn(ent, 20);
    
            return true;
        }
    };
    public static EntInteractAdapter Pickup_PowerArmor = new EntInteractAdapter() {
        public boolean interact(edict_t ent, edict_t other) {
    
            int quantity;
    
            quantity = other.client.pers.inventory[ITEM_INDEX(ent.item)];
    
            other.client.pers.inventory[ITEM_INDEX(ent.item)]++;
    
            if (GameBase.deathmatch.value != 0) {
                if (0 == (ent.spawnflags & Defines.DROPPED_ITEM))
                    SetRespawn(ent, ent.item.quantity);
                // auto-use for DM only if we didn't already have one
                if (0 == quantity)
                    ent.item.use.use(other, ent.item);
            }
            return true;
        }
    };
    public static EntInteractAdapter Pickup_Powerup = new EntInteractAdapter() {
    
        public boolean interact(edict_t ent, edict_t other) {
            int quantity;
    
            quantity = other.client.pers.inventory[ITEM_INDEX(ent.item)];
            if ((GameBase.skill.value == 1 && quantity >= 2)
                    || (GameBase.skill.value >= 2 && quantity >= 1))
                return false;
    
            if ((GameBase.coop.value != 0)
                    && (ent.item.flags & Defines.IT_STAY_COOP) != 0
                    && (quantity > 0))
                return false;
    
            other.client.pers.inventory[ITEM_INDEX(ent.item)]++;
    
            if (GameBase.deathmatch.value != 0) {
                if (0 == (ent.spawnflags & Defines.DROPPED_ITEM))
                    SetRespawn(ent, ent.item.quantity);
                if (((int) GameBase.dmflags.value & Defines.DF_INSTANT_ITEMS) != 0
                        || ((ent.item.use == Use_Quad) && 0 != (ent.spawnflags & Defines.DROPPED_PLAYER_ITEM))) {
                    if ((ent.item.use == Use_Quad)
                            && 0 != (ent.spawnflags & Defines.DROPPED_PLAYER_ITEM))
                        quad_drop_timeout_hack = (int) ((ent.nextthink - GameBase.level.time) / Defines.FRAMETIME);
    
                    ent.item.use.use(other, ent.item);
                }
            }
    
            return true;
        }
    };
    public static EntInteractAdapter Pickup_Adrenaline = new EntInteractAdapter() {
        public boolean interact(edict_t ent, edict_t other) {
            if (GameBase.deathmatch.value == 0)
                other.max_health += 1;
    
            if (other.health < other.max_health)
                other.health = other.max_health;
    
            if (0 == (ent.spawnflags & Defines.DROPPED_ITEM)
                    && (GameBase.deathmatch.value != 0))
                SetRespawn(ent, ent.item.quantity);
    
            return true;
    
        }
    };
    public static EntInteractAdapter Pickup_AncientHead = new EntInteractAdapter() {
        public boolean interact(edict_t ent, edict_t other) {
            other.max_health += 2;
    
            if (0 == (ent.spawnflags & Defines.DROPPED_ITEM)
                    && (GameBase.deathmatch.value != 0))
                SetRespawn(ent, ent.item.quantity);
    
            return true;
        }
    };
    public static EntInteractAdapter Pickup_Bandolier = new EntInteractAdapter() {
        public boolean interact(edict_t ent, edict_t other) {
            gitem_t item;
            int index;
    
            if (other.client.pers.max_bullets < 250)
                other.client.pers.max_bullets = 250;
            if (other.client.pers.max_shells < 150)
                other.client.pers.max_shells = 150;
            if (other.client.pers.max_cells < 250)
                other.client.pers.max_cells = 250;
            if (other.client.pers.max_slugs < 75)
                other.client.pers.max_slugs = 75;
    
            item = FindItem("Bullets");
            if (item != null) {
                index = ITEM_INDEX(item);
                other.client.pers.inventory[index] += item.quantity;
                if (other.client.pers.inventory[index] > other.client.pers.max_bullets)
                    other.client.pers.inventory[index] = other.client.pers.max_bullets;
            }
    
            item = FindItem("Shells");
            if (item != null) {
                index = ITEM_INDEX(item);
                other.client.pers.inventory[index] += item.quantity;
                if (other.client.pers.inventory[index] > other.client.pers.max_shells)
                    other.client.pers.inventory[index] = other.client.pers.max_shells;
            }
    
            if (0 == (ent.spawnflags & Defines.DROPPED_ITEM)
                    && (GameBase.deathmatch.value != 0))
                SetRespawn(ent, ent.item.quantity);
    
            return true;
    
        }
    };
    public static ItemDropAdapter Drop_Ammo = new ItemDropAdapter() {
        public void drop(edict_t ent, gitem_t item) {
            edict_t dropped;
            int index;
    
            index = ITEM_INDEX(item);
            dropped = Drop_Item(ent, item);
            if (ent.client.pers.inventory[index] >= item.quantity)
                dropped.count = item.quantity;
            else
                dropped.count = ent.client.pers.inventory[index];
    
            if (ent.client.pers.weapon != null
                    && ent.client.pers.weapon.tag == Defines.AMMO_GRENADES
                    && item.tag == Defines.AMMO_GRENADES
                    && ent.client.pers.inventory[index] - dropped.count <= 0) {
                GameBase.gi.cprintf(ent, Defines.PRINT_HIGH,
                        "Can't drop current weapon\n");
                GameUtil.G_FreeEdict(dropped);
                return;
            }
    
            ent.client.pers.inventory[index] -= dropped.count;
            Cmd.ValidateSelectedItem(ent);
        }
    };
    public static ItemDropAdapter Drop_General = new ItemDropAdapter() {
        public void drop(edict_t ent, gitem_t item) {
            Drop_Item(ent, item);
            ent.client.pers.inventory[ITEM_INDEX(item)]--;
            Cmd.ValidateSelectedItem(ent);
        }
    };
    public static ItemDropAdapter Drop_PowerArmor = new ItemDropAdapter() {
        public void drop(edict_t ent, gitem_t item) {
            if (0 != (ent.flags & Defines.FL_POWER_ARMOR)
                    && (ent.client.pers.inventory[ITEM_INDEX(item)] == 1))
                Use_PowerArmor.use(ent, item);
            Drop_General.drop(ent, item);
        }
    };
    public static EntThinkAdapter droptofloor = new EntThinkAdapter() {
        public boolean think(edict_t ent) {
            trace_t tr;
            float[] dest = { 0, 0, 0 };
    
            //float v[];
    
            //v = Lib.tv(-15, -15, -15);
            //Math3D.VectorCopy(v, ent.mins);
            ent.mins[0] = ent.mins[1] = ent.mins[2] = -15;
            //v = Lib.tv(15, 15, 15);
            //Math3D.VectorCopy(v, ent.maxs);
            ent.maxs[0] = ent.maxs[1] = ent.maxs[2] = 15;
    
            if (ent.model != null)
                GameBase.gi.setmodel(ent, ent.model);
            else
                GameBase.gi.setmodel(ent, ent.item.world_model);
            ent.solid = Defines.SOLID_TRIGGER;
            ent.movetype = Defines.MOVETYPE_TOSS;
            ent.touch = Touch_Item;
    
            float v[] = { 0, 0, -128 };
            Math3D.VectorAdd(ent.s.origin, v, dest);
    
            tr = GameBase.gi.trace(ent.s.origin, ent.mins, ent.maxs, dest, ent,
                    Defines.MASK_SOLID);
            if (tr.startsolid) {
                GameBase.gi.dprintf("droptofloor: " + ent.classname
                        + " startsolid at " + Lib.vtos(ent.s.origin) + "\n");
                GameUtil.G_FreeEdict(ent);
                return true;
            }
    
            Math3D.VectorCopy(tr.endpos, ent.s.origin);
    
            if (ent.team != null) {
                ent.flags &= ~Defines.FL_TEAMSLAVE;
                ent.chain = ent.teamchain;
                ent.teamchain = null;
    
                ent.svflags |= Defines.SVF_NOCLIENT;
                ent.solid = Defines.SOLID_NOT;
                if (ent == ent.teammaster) {
                    ent.nextthink = GameBase.level.time + Defines.FRAMETIME;
                    ent.think = DoRespawn;
                }
            }
    
            if ((ent.spawnflags & Defines.ITEM_NO_TOUCH) != 0) {
                ent.solid = Defines.SOLID_BBOX;
                ent.touch = null;
                ent.s.effects &= ~Defines.EF_ROTATE;
                ent.s.renderfx &= ~Defines.RF_GLOW;
            }
    
            if ((ent.spawnflags & Defines.ITEM_TRIGGER_SPAWN) != 0) {
                ent.svflags |= Defines.SVF_NOCLIENT;
                ent.solid = Defines.SOLID_NOT;
                ent.use = Use_Item;
            }
    
            GameBase.gi.linkentity(ent);
            return true;
        }
    };
    public static ItemUseAdapter Use_PowerArmor = new ItemUseAdapter() {
        public void use(edict_t ent, gitem_t item) {
            int index;
    
            if ((ent.flags & Defines.FL_POWER_ARMOR) != 0) {
                ent.flags &= ~Defines.FL_POWER_ARMOR;
                GameBase.gi
                        .sound(ent, Defines.CHAN_AUTO, GameBase.gi
                                .soundindex("misc/power2.wav"), 1,
                                Defines.ATTN_NORM, 0);
            } else {
                index = ITEM_INDEX(FindItem("cells"));
                if (0 == ent.client.pers.inventory[index]) {
                    GameBase.gi.cprintf(ent, Defines.PRINT_HIGH,
                            "No cells for power armor.\n");
                    return;
                }
                ent.flags |= Defines.FL_POWER_ARMOR;
                GameBase.gi
                        .sound(ent, Defines.CHAN_AUTO, GameBase.gi
                                .soundindex("misc/power1.wav"), 1,
                                Defines.ATTN_NORM, 0);
            }
        }
    };
    public static EntUseAdapter Use_Item = new EntUseAdapter() {
        public void use(edict_t ent, edict_t other, edict_t activator) {
            ent.svflags &= ~Defines.SVF_NOCLIENT;
            ent.use = null;
    
            if ((ent.spawnflags & Defines.ITEM_NO_TOUCH) != 0) {
                ent.solid = Defines.SOLID_BBOX;
                ent.touch = null;
            } else {
                ent.solid = Defines.SOLID_TRIGGER;
                ent.touch = Touch_Item;
            }
    
            GameBase.gi.linkentity(ent);
        }
    };

    /*
     * =============== GetItemByIndex ===============
     */
    public static gitem_t GetItemByIndex(int index) {
        if (index == 0 || index >= GameBase.game.num_items)
            return null;
    
        return itemlist[index];
    }

    /*
     * =============== FindItemByClassname
     * 
     * ===============
     */
    static gitem_t FindItemByClassname(String classname) {
    
        for (int i = 1; i < GameBase.game.num_items; i++) {
            gitem_t it = itemlist[i];
    
            if (it.classname == null)
                continue;
            if (it.classname.equalsIgnoreCase(classname))
                return it;
        }
    
        return null;
    }

    /*
     * =============== FindItem ===============
     */
    //geht.
    static gitem_t FindItem(String pickup_name) {
        for (int i = 1; i < GameBase.game.num_items; i++) {
            gitem_t it = itemlist[i];
    
            if (it.pickup_name == null)
                continue;
            if (it.pickup_name.equalsIgnoreCase(pickup_name))
                return it;
        }
        Com.Println("Item not found:" + pickup_name);
        return null;
    }

    static void SetRespawn(edict_t ent, float delay) {
        ent.flags |= Defines.FL_RESPAWN;
        ent.svflags |= Defines.SVF_NOCLIENT;
        ent.solid = Defines.SOLID_NOT;
        ent.nextthink = GameBase.level.time + delay;
        ent.think = DoRespawn;
        GameBase.gi.linkentity(ent);
    }

    static int ITEM_INDEX(gitem_t item) {
        return item.index;
    }

    static edict_t Drop_Item(edict_t ent, gitem_t item) {
        edict_t dropped;
        float[] forward = { 0, 0, 0 };
        float[] right = { 0, 0, 0 };
        float[] offset = { 0, 0, 0 };
    
        dropped = GameUtil.G_Spawn();
    
        dropped.classname = item.classname;
        dropped.item = item;
        dropped.spawnflags = Defines.DROPPED_ITEM;
        dropped.s.effects = item.world_model_flags;
        dropped.s.renderfx = Defines.RF_GLOW;
        Math3D.VectorSet(dropped.mins, -15, -15, -15);
        Math3D.VectorSet(dropped.maxs, 15, 15, 15);
        GameBase.gi.setmodel(dropped, dropped.item.world_model);
        dropped.solid = Defines.SOLID_TRIGGER;
        dropped.movetype = Defines.MOVETYPE_TOSS;
    
        dropped.touch = drop_temp_touch;
    
        dropped.owner = ent;
    
        if (ent.client != null) {
            trace_t trace;
    
            Math3D.AngleVectors(ent.client.v_angle, forward, right, null);
            Math3D.VectorSet(offset, 24, 0, -16);
            Math3D.G_ProjectSource(ent.s.origin, offset, forward, right,
                    dropped.s.origin);
            trace = GameBase.gi.trace(ent.s.origin, dropped.mins, dropped.maxs,
                    dropped.s.origin, ent, Defines.CONTENTS_SOLID);
            Math3D.VectorCopy(trace.endpos, dropped.s.origin);
        } else {
            Math3D.AngleVectors(ent.s.angles, forward, right, null);
            Math3D.VectorCopy(ent.s.origin, dropped.s.origin);
        }
    
        Math3D.VectorScale(forward, 100, dropped.velocity);
        dropped.velocity[2] = 300;
    
        dropped.think = drop_make_touchable;
        dropped.nextthink = GameBase.level.time + 1;
    
        GameBase.gi.linkentity(dropped);
    
        return dropped;
    }

    static void Use_Item(edict_t ent, edict_t other, edict_t activator) {
        ent.svflags &= ~Defines.SVF_NOCLIENT;
        ent.use = null;
    
        if ((ent.spawnflags & Defines.ITEM_NO_TOUCH) != 0) {
            ent.solid = Defines.SOLID_BBOX;
            ent.touch = null;
        } else {
            ent.solid = Defines.SOLID_TRIGGER;
            ent.touch = Touch_Item;
        }
    
        GameBase.gi.linkentity(ent);
    }

    static int PowerArmorType(edict_t ent) {
        if (ent.client == null)
            return Defines.POWER_ARMOR_NONE;
    
        if (0 == (ent.flags & Defines.FL_POWER_ARMOR))
            return Defines.POWER_ARMOR_NONE;
    
        if (ent.client.pers.inventory[power_shield_index] > 0)
            return Defines.POWER_ARMOR_SHIELD;
    
        if (ent.client.pers.inventory[power_screen_index] > 0)
            return Defines.POWER_ARMOR_SCREEN;
    
        return Defines.POWER_ARMOR_NONE;
    }

    static int ArmorIndex(edict_t ent) {
        if (ent.client == null)
            return 0;
    
        if (ent.client.pers.inventory[jacket_armor_index] > 0)
            return jacket_armor_index;
    
        if (ent.client.pers.inventory[combat_armor_index] > 0)
            return combat_armor_index;
    
        if (ent.client.pers.inventory[body_armor_index] > 0)
            return body_armor_index;
    
        return 0;
    }

    public static boolean Pickup_PowerArmor(edict_t ent, edict_t other) {
        int quantity;
    
        quantity = other.client.pers.inventory[ITEM_INDEX(ent.item)];
    
        other.client.pers.inventory[ITEM_INDEX(ent.item)]++;
    
        if (GameBase.deathmatch.value != 0) {
            if (0 == (ent.spawnflags & Defines.DROPPED_ITEM))
                SetRespawn(ent, ent.item.quantity);
            // auto-use for DM only if we didn't already have one
            if (0 == quantity)
                ent.item.use.use(other, ent.item);
        }
    
        return true;
    }

    public static boolean Add_Ammo(edict_t ent, gitem_t item, int count) {
        int index;
        int max;
    
        if (null == ent.client)
            return false;
    
        if (item.tag == Defines.AMMO_BULLETS)
            max = ent.client.pers.max_bullets;
        else if (item.tag == Defines.AMMO_SHELLS)
            max = ent.client.pers.max_shells;
        else if (item.tag == Defines.AMMO_ROCKETS)
            max = ent.client.pers.max_rockets;
        else if (item.tag == Defines.AMMO_GRENADES)
            max = ent.client.pers.max_grenades;
        else if (item.tag == Defines.AMMO_CELLS)
            max = ent.client.pers.max_cells;
        else if (item.tag == Defines.AMMO_SLUGS)
            max = ent.client.pers.max_slugs;
        else
            return false;
    
        index = ITEM_INDEX(item);
    
        if (ent.client.pers.inventory[index] == max)
            return false;
    
        ent.client.pers.inventory[index] += count;
    
        if (ent.client.pers.inventory[index] > max)
            ent.client.pers.inventory[index] = max;
    
        return true;
    }

    public static void InitItems() {
        GameBase.game.num_items = itemlist.length - 1;
    }

    /*
     * =============== SetItemNames
     * 
     * Called by worldspawn ===============
     */
    public static void SetItemNames() {
        int i;
        gitem_t it;
    
        for (i = 1; i < GameBase.game.num_items; i++) {
            it = itemlist[i];
            GameBase.gi.configstring(Defines.CS_ITEMS + i, it.pickup_name);
        }
    
        jacket_armor_index = ITEM_INDEX(FindItem("Jacket Armor"));
        combat_armor_index = ITEM_INDEX(FindItem("Combat Armor"));
        body_armor_index = ITEM_INDEX(FindItem("Body Armor"));
        power_screen_index = ITEM_INDEX(FindItem("Power Screen"));
        power_shield_index = ITEM_INDEX(FindItem("Power Shield"));
    }

    public static void SelectNextItem(edict_t ent, int itflags) {
        gclient_t cl;
        int i, index;
        gitem_t it;
    
        cl = ent.client;
    
        if (cl.chase_target != null) {
            GameChase.ChaseNext(ent);
            return;
        }
    
        // scan for the next valid one
        for (i = 1; i <= Defines.MAX_ITEMS; i++) {
            index = (cl.pers.selected_item + i) % Defines.MAX_ITEMS;
            if (0 == cl.pers.inventory[index])
                continue;
            it = itemlist[index];
            if (it.use == null)
                continue;
            if (0 == (it.flags & itflags))
                continue;
    
            cl.pers.selected_item = index;
            return;
        }
    
        cl.pers.selected_item = -1;
    }

    public static void SelectPrevItem(edict_t ent, int itflags) {
        gclient_t cl;
        int i, index;
        gitem_t it;
    
        cl = ent.client;
    
        if (cl.chase_target != null) {
            GameChase.ChasePrev(ent);
            return;
        }
    
        // scan for the next valid one
        for (i = 1; i <= Defines.MAX_ITEMS; i++) {
            index = (cl.pers.selected_item + Defines.MAX_ITEMS - i)
                    % Defines.MAX_ITEMS;
            if (0 == cl.pers.inventory[index])
                continue;
            it = itemlist[index];
            if (null == it.use)
                continue;
            if (0 == (it.flags & itflags))
                continue;
    
            cl.pers.selected_item = index;
            return;
        }
    
        cl.pers.selected_item = -1;
    }

    /*
     * =============== PrecacheItem
     * 
     * Precaches all data needed for a given item. This will be called for each
     * item spawned in a level, and for each item in each client's inventory.
     * ===============
     */
    public static void PrecacheItem(gitem_t it) {
        String s;
        String data;
        int len;
        gitem_t ammo;
    
        if (it == null)
            return;
    
        if (it.pickup_sound != null)
            GameBase.gi.soundindex(it.pickup_sound);
    
        if (it.world_model != null)
            GameBase.gi.modelindex(it.world_model);
    
        if (it.view_model != null)
            GameBase.gi.modelindex(it.view_model);
    
        if (it.icon != null)
            GameBase.gi.imageindex(it.icon);
    
        // parse everything for its ammo
        if (it.ammo != null && it.ammo.length() != 0) {
            ammo = FindItem(it.ammo);
            if (ammo != it)
                PrecacheItem(ammo);
        }
    
        // parse the space seperated precache string for other items
        s = it.precaches;
        if (s == null || s.length() != 0)
            return;
    
        StringTokenizer tk = new StringTokenizer(s);
    
        while (tk.hasMoreTokens()) {
            data = tk.nextToken();
    
            len = data.length();
    
            if (len >= Defines.MAX_QPATH || len < 5)
                GameBase.gi
                        .error("PrecacheItem: it.classname has bad precache string: "
                                + s);
    
            // determine type based on extension
            if (data.endsWith("md2"))
                GameBase.gi.modelindex(data);
            else if (data.endsWith("sp2"))
                GameBase.gi.modelindex(data);
            else if (data.endsWith("wav"))
                GameBase.gi.soundindex(data);
            else if (data.endsWith("pcx"))
                GameBase.gi.imageindex(data);
            else
                GameBase.gi.error("PrecacheItem: bad precache string: " + data);
        }
    }

    /*
     * ============ SpawnItem
     * 
     * Sets the clipping size and plants the object on the floor.
     * 
     * Items can't be immediately dropped to floor, because they might be on an
     * entity that hasn't spawned yet. ============
     */
    public static void SpawnItem(edict_t ent, gitem_t item) {
        PrecacheItem(item);
    
        if (ent.spawnflags != 0) {
            if (Lib.strcmp(ent.classname, "key_power_cube") != 0) {
                ent.spawnflags = 0;
                GameBase.gi.dprintf("" + ent.classname + " at "
                        + Lib.vtos(ent.s.origin)
                        + " has invalid spawnflags set\n");
            }
        }
    
        // some items will be prevented in deathmatch
        if (GameBase.deathmatch.value != 0) {
            if (((int) GameBase.dmflags.value & Defines.DF_NO_ARMOR) != 0) {
                if (item.pickup == Pickup_Armor
                        || item.pickup == Pickup_PowerArmor) {
                    GameUtil.G_FreeEdict(ent);
                    return;
                }
            }
            if (((int) GameBase.dmflags.value & Defines.DF_NO_ITEMS) != 0) {
                if (item.pickup == Pickup_Powerup) {
                    GameUtil.G_FreeEdict(ent);
                    return;
                }
            }
            if (((int) GameBase.dmflags.value & Defines.DF_NO_HEALTH) != 0) {
                if (item.pickup == Pickup_Health
                        || item.pickup == Pickup_Adrenaline
                        || item.pickup == Pickup_AncientHead) {
                    GameUtil.G_FreeEdict(ent);
                    return;
                }
            }
            if (((int) GameBase.dmflags.value & Defines.DF_INFINITE_AMMO) != 0) {
                if ((item.flags == Defines.IT_AMMO)
                        || (Lib.strcmp(ent.classname, "weapon_bfg") == 0)) {
                    GameUtil.G_FreeEdict(ent);
                    return;
                }
            }
        }
    
        if (GameBase.coop.value != 0
                && (Lib.strcmp(ent.classname, "key_power_cube") == 0)) {
            ent.spawnflags |= (1 << (8 + GameBase.level.power_cubes));
            GameBase.level.power_cubes++;
        }
    
        // don't let them drop items that stay in a coop game
        if ((GameBase.coop.value != 0)
                && (item.flags & Defines.IT_STAY_COOP) != 0) {
            item.drop = null;
        }
    
        ent.item = item;
        ent.nextthink = GameBase.level.time + 2 * Defines.FRAMETIME;
        // items start after other solids
        ent.think = droptofloor;
        ent.s.effects = item.world_model_flags;
        ent.s.renderfx = Defines.RF_GLOW;
    
        if (ent.model != null)
            GameBase.gi.modelindex(ent.model);
    }

    /*
     * QUAKED item_health (.3 .3 1) (-16 -16 -16) (16 16 16)
     */
    public static void SP_item_health(edict_t self) {
        if (GameBase.deathmatch.value != 0
                && ((int) GameBase.dmflags.value & Defines.DF_NO_HEALTH) != 0) {
            GameUtil.G_FreeEdict(self);
        }
    
        self.model = "models/items/healing/medium/tris.md2";
        self.count = 10;
        SpawnItem(self, FindItem("Health"));
        GameBase.gi.soundindex("items/n_health.wav");
    }

    /*
     * QUAKED item_health_small (.3 .3 1) (-16 -16 -16) (16 16 16)
     */
    static void SP_item_health_small(edict_t self) {
        if (GameBase.deathmatch.value != 0
                && ((int) GameBase.dmflags.value & Defines.DF_NO_HEALTH) != 0) {
            GameUtil.G_FreeEdict(self);
            return;
        }
    
        self.model = "models/items/healing/stimpack/tris.md2";
        self.count = 2;
        SpawnItem(self, FindItem("Health"));
        self.style = Defines.HEALTH_IGNORE_MAX;
        GameBase.gi.soundindex("items/s_health.wav");
    }

    /*
     * QUAKED item_health_large (.3 .3 1) (-16 -16 -16) (16 16 16)
     */
    static void SP_item_health_large(edict_t self) {
        if (GameBase.deathmatch.value != 0
                && ((int) GameBase.dmflags.value & Defines.DF_NO_HEALTH) != 0) {
            GameUtil.G_FreeEdict(self);
            return;
        }
    
        self.model = "models/items/healing/large/tris.md2";
        self.count = 25;
        SpawnItem(self, FindItem("Health"));
        GameBase.gi.soundindex("items/l_health.wav");
    }

    /*
     * QUAKED item_health_mega (.3 .3 1) (-16 -16 -16) (16 16 16)
     */
    static void SP_item_health_mega(edict_t self) {
        if (GameBase.deathmatch.value != 0
                && ((int) GameBase.dmflags.value & Defines.DF_NO_HEALTH) != 0) {
            GameUtil.G_FreeEdict(self);
            return;
        }
    
        self.model = "models/items/mega_h/tris.md2";
        self.count = 100;
        SpawnItem(self, FindItem("Health"));
        GameBase.gi.soundindex("items/m_health.wav");
        self.style = Defines.HEALTH_IGNORE_MAX | Defines.HEALTH_TIMED;
    }

    /*
     * =============== 
     * Touch_Item 
     * ===============
     */
    public static void Touch_Item(edict_t ent, edict_t other, cplane_t plane,
            csurface_t surf) {
        boolean taken;
    
        if (other.client == null)
            return;
        if (other.health < 1)
            return; // dead people can't pickup
        if (ent.item.pickup == null)
            return; // not a grabbable item?
    
        taken = ent.item.pickup.interact(ent, other);
    
        if (taken) {
            // flash the screen
            other.client.bonus_alpha = 0.25f;
    
            // show icon and name on status bar
            other.client.ps.stats[Defines.STAT_PICKUP_ICON] = (short) GameBase.gi
                    .imageindex(ent.item.icon);
            other.client.ps.stats[Defines.STAT_PICKUP_STRING] = (short) (Defines.CS_ITEMS + ITEM_INDEX(ent.item));
            other.client.pickup_msg_time = GameBase.level.time + 3.0f;
    
            // change selected item
            if (ent.item.use != null)
                other.client.pers.selected_item = other.client.ps.stats[Defines.STAT_SELECTED_ITEM] = (short) ITEM_INDEX(ent.item);
    
            if (ent.item.pickup == Pickup_Health) {
                if (ent.count == 2)
                    GameBase.gi.sound(other, Defines.CHAN_ITEM, GameBase.gi
                            .soundindex("items/s_health.wav"), 1,
                            Defines.ATTN_NORM, 0);
                else if (ent.count == 10)
                    GameBase.gi.sound(other, Defines.CHAN_ITEM, GameBase.gi
                            .soundindex("items/n_health.wav"), 1,
                            Defines.ATTN_NORM, 0);
                else if (ent.count == 25)
                    GameBase.gi.sound(other, Defines.CHAN_ITEM, GameBase.gi
                            .soundindex("items/l_health.wav"), 1,
                            Defines.ATTN_NORM, 0);
                else
                    // (ent.count == 100)
                    GameBase.gi.sound(other, Defines.CHAN_ITEM, GameBase.gi
                            .soundindex("items/m_health.wav"), 1,
                            Defines.ATTN_NORM, 0);
            } else if (ent.item.pickup_sound != null) {
                GameBase.gi.sound(other, Defines.CHAN_ITEM, GameBase.gi
                        .soundindex(ent.item.pickup_sound), 1,
                        Defines.ATTN_NORM, 0);
            }
        }
    
        if (0 == (ent.spawnflags & Defines.ITEM_TARGETS_USED)) {
            GameUtil.G_UseTargets(ent, other);
            ent.spawnflags |= Defines.ITEM_TARGETS_USED;
        }
    
        if (!taken)
            return;
    
        if (!((GameBase.coop.value != 0) && (ent.item.flags & Defines.IT_STAY_COOP) != 0)
                || 0 != (ent.spawnflags & (Defines.DROPPED_ITEM | Defines.DROPPED_PLAYER_ITEM))) {
            if ((ent.flags & Defines.FL_RESPAWN) != 0)
                ent.flags &= ~Defines.FL_RESPAWN;
            else
                GameUtil.G_FreeEdict(ent);
        }
    }

}
