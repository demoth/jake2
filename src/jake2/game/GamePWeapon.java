/*
 * Copyright (C) 1997-2001 Id Software, Inc.
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.
 * 
 * See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place - Suite 330, Boston, MA 02111-1307, USA.
 *  
 */

// Created on 16.11.2003 by RST.
// $Id: GamePWeapon.java,v 1.7 2005-02-06 19:01:45 salomo Exp $
package jake2.game;

import jake2.Defines;
import jake2.Globals;
import jake2.game.monsters.M_Player;
import jake2.qcommon.Com;
import jake2.util.Lib;
import jake2.util.Math3D;

public class GamePWeapon {

    public static EntThinkAdapter Weapon_Grenade = new EntThinkAdapter() {

        public boolean think(edict_t ent) {
            if ((ent.client.newweapon != null)
                    && (ent.client.weaponstate == Defines.WEAPON_READY)) {
                ChangeWeapon(ent);
                return true;
            }

            if (ent.client.weaponstate == Defines.WEAPON_ACTIVATING) {
                ent.client.weaponstate = Defines.WEAPON_READY;
                ent.client.ps.gunframe = 16;
                return true;
            }

            if (ent.client.weaponstate == Defines.WEAPON_READY) {
                if (((ent.client.latched_buttons | ent.client.buttons) & Defines.BUTTON_ATTACK) != 0) {
                    ent.client.latched_buttons &= ~Defines.BUTTON_ATTACK;
                    if (0 != ent.client.pers.inventory[ent.client.ammo_index]) {
                        ent.client.ps.gunframe = 1;
                        ent.client.weaponstate = Defines.WEAPON_FIRING;
                        ent.client.grenade_time = 0;
                    } else {
                        if (GameBase.level.time >= ent.pain_debounce_time) {
                            GameBase.gi.sound(ent, Defines.CHAN_VOICE,
                                    GameBase.gi
                                            .soundindex("weapons/noammo.wav"),
                                    1, Defines.ATTN_NORM, 0);
                            ent.pain_debounce_time = GameBase.level.time + 1;
                        }
                        NoAmmoWeaponChange(ent);
                    }
                    return true;
                }

                if ((ent.client.ps.gunframe == 29)
                        || (ent.client.ps.gunframe == 34)
                        || (ent.client.ps.gunframe == 39)
                        || (ent.client.ps.gunframe == 48)) {
                    if ((Lib.rand() & 15) != 0)
                        return true;
                }

                if (++ent.client.ps.gunframe > 48)
                    ent.client.ps.gunframe = 16;
                return true;
            }

            if (ent.client.weaponstate == Defines.WEAPON_FIRING) {
                if (ent.client.ps.gunframe == 5)
                    GameBase.gi.sound(ent, Defines.CHAN_WEAPON, GameBase.gi
                            .soundindex("weapons/hgrena1b.wav"), 1,
                            Defines.ATTN_NORM, 0);

                if (ent.client.ps.gunframe == 11) {
                    if (0 == ent.client.grenade_time) {
                        ent.client.grenade_time = GameBase.level.time
                                + Defines.GRENADE_TIMER + 0.2f;
                        ent.client.weapon_sound = GameBase.gi
                                .soundindex("weapons/hgrenc1b.wav");
                    }

                    // they waited too long, detonate it in their hand
                    if (!ent.client.grenade_blew_up
                            && GameBase.level.time >= ent.client.grenade_time) {
                        ent.client.weapon_sound = 0;
                        weapon_grenade_fire(ent, true);
                        ent.client.grenade_blew_up = true;
                    }

                    if ((ent.client.buttons & Defines.BUTTON_ATTACK) != 0)
                        return true;

                    if (ent.client.grenade_blew_up) {
                        if (GameBase.level.time >= ent.client.grenade_time) {
                            ent.client.ps.gunframe = 15;
                            ent.client.grenade_blew_up = false;
                        } else {
                            return true;
                        }
                    }
                }

                if (ent.client.ps.gunframe == 12) {
                    ent.client.weapon_sound = 0;
                    weapon_grenade_fire(ent, false);
                }

                if ((ent.client.ps.gunframe == 15)
                        && (GameBase.level.time < ent.client.grenade_time))
                    return true;

                ent.client.ps.gunframe++;

                if (ent.client.ps.gunframe == 16) {
                    ent.client.grenade_time = 0;
                    ent.client.weaponstate = Defines.WEAPON_READY;
                }
            }
            return true;
        }
    };

    /*
     * ======================================================================
     * 
     * GRENADE LAUNCHER
     * 
     * ======================================================================
     */

    public static EntThinkAdapter weapon_grenadelauncher_fire = new EntThinkAdapter() {

        public boolean think(edict_t ent) {
            float[] offset = { 0, 0, 0 };
            float[] forward = { 0, 0, 0 }, right = { 0, 0, 0 };
            float[] start = { 0, 0, 0 };
            int damage = 120;
            float radius;

            radius = damage + 40;
            if (is_quad)
                damage *= 4;

            Math3D.VectorSet(offset, 8, 8, ent.viewheight - 8);
            Math3D.AngleVectors(ent.client.v_angle, forward, right, null);
            P_ProjectSource(ent.client, ent.s.origin, offset, forward, right,
                    start);

            Math3D.VectorScale(forward, -2, ent.client.kick_origin);
            ent.client.kick_angles[0] = -1;

            Fire.fire_grenade(ent, start, forward, damage, 600, 2.5f, radius);

            GameBase.gi.WriteByte(Defines.svc_muzzleflash);
            GameBase.gi.WriteShort(ent.index);
            GameBase.gi.WriteByte(Defines.MZ_GRENADE | is_silenced);
            GameBase.gi.multicast(ent.s.origin, Defines.MULTICAST_PVS);

            ent.client.ps.gunframe++;

            GameWeapon.PlayerNoise(ent, start, Defines.PNOISE_WEAPON);

            if (0 == ((int) GameBase.dmflags.value & Defines.DF_INFINITE_AMMO))
                ent.client.pers.inventory[ent.client.ammo_index]--;

            return true;
        }
    };

    public static EntThinkAdapter Weapon_GrenadeLauncher = new EntThinkAdapter() {

        public boolean think(edict_t ent) {

            int pause_frames[] = { 34, 51, 59, 0 };
            int fire_frames[] = { 6, 0 };

            Weapon_Generic(ent, 5, 16, 59, 64, pause_frames, fire_frames,
                    weapon_grenadelauncher_fire);
            return true;
        }
    };

    /*
     * ======================================================================
     * 
     * ROCKET
     * 
     * ======================================================================
     */

    public static EntThinkAdapter Weapon_RocketLauncher_Fire = new EntThinkAdapter() {

        public boolean think(edict_t ent) {

            float[] offset = { 0, 0, 0 }, start = { 0, 0, 0 };
            float[] forward = { 0, 0, 0 }, right = { 0, 0, 0 };
            int damage;
            float damage_radius;
            int radius_damage;

            damage = 100 + (int) (Lib.random() * 20.0);
            radius_damage = 120;
            damage_radius = 120;
            if (is_quad) {
                damage *= 4;
                radius_damage *= 4;
            }

            Math3D.AngleVectors(ent.client.v_angle, forward, right, null);

            Math3D.VectorScale(forward, -2, ent.client.kick_origin);
            ent.client.kick_angles[0] = -1;

            Math3D.VectorSet(offset, 8, 8, ent.viewheight - 8);
            P_ProjectSource(ent.client, ent.s.origin, offset, forward, right,
                    start);
            Fire.fire_rocket(ent, start, forward, damage, 650, damage_radius,
                    radius_damage);

            // send muzzle flash
            GameBase.gi.WriteByte(Defines.svc_muzzleflash);

            GameBase.gi.WriteShort(ent.index);
            GameBase.gi.WriteByte(Defines.MZ_ROCKET | is_silenced);
            GameBase.gi.multicast(ent.s.origin, Defines.MULTICAST_PVS);

            ent.client.ps.gunframe++;

            GameWeapon.PlayerNoise(ent, start, Defines.PNOISE_WEAPON);

            if (0 == ((int) GameBase.dmflags.value & Defines.DF_INFINITE_AMMO))
                ent.client.pers.inventory[ent.client.ammo_index]--;

            return true;
        }
    };

    public static EntThinkAdapter Weapon_RocketLauncher = new EntThinkAdapter() {

        public boolean think(edict_t ent) {

            int pause_frames[] = { 25, 33, 42, 50, 0 };
            int fire_frames[] = { 5, 0 };

            Weapon_Generic(ent, 4, 12, 50, 54, pause_frames, fire_frames,
                    Weapon_RocketLauncher_Fire);
            return true;
        }
    };

    public static EntThinkAdapter Weapon_Blaster_Fire = new EntThinkAdapter() {

        public boolean think(edict_t ent) {

            int damage;

            if (GameBase.deathmatch.value != 0)
                damage = 15;
            else
                damage = 10;
            Blaster_Fire(ent, Globals.vec3_origin, damage, false,
                    Defines.EF_BLASTER);
            ent.client.ps.gunframe++;
            return true;
        }
    };

    public static EntThinkAdapter Weapon_Blaster = new EntThinkAdapter() {

        public boolean think(edict_t ent) {

            int pause_frames[] = { 19, 32, 0 };
            int fire_frames[] = { 5, 0 };

            Weapon_Generic(ent, 4, 8, 52, 55, pause_frames, fire_frames,
                    Weapon_Blaster_Fire);
            return true;
        }
    };

    public static EntThinkAdapter Weapon_HyperBlaster_Fire = new EntThinkAdapter() {

        public boolean think(edict_t ent) {
            float rotation;
            float[] offset = { 0, 0, 0 };
            int effect;
            int damage;

            ent.client.weapon_sound = GameBase.gi
                    .soundindex("weapons/hyprbl1a.wav");

            if (0 == (ent.client.buttons & Defines.BUTTON_ATTACK)) {
                ent.client.ps.gunframe++;
            } else {
                if (0 == ent.client.pers.inventory[ent.client.ammo_index]) {
                    if (GameBase.level.time >= ent.pain_debounce_time) {
                        GameBase.gi.sound(ent, Defines.CHAN_VOICE, GameBase.gi
                                .soundindex("weapons/noammo.wav"), 1,
                                Defines.ATTN_NORM, 0);
                        ent.pain_debounce_time = GameBase.level.time + 1;
                    }
                    NoAmmoWeaponChange(ent);
                } else {
                    rotation = (float) ((ent.client.ps.gunframe - 5) * 2
                            * Math.PI / 6);
                    offset[0] = (float) (-4 * Math.sin(rotation));
                    offset[1] = 0f;
                    offset[2] = (float) (4 * Math.cos(rotation));

                    if ((ent.client.ps.gunframe == 6)
                            || (ent.client.ps.gunframe == 9))
                        effect = Defines.EF_HYPERBLASTER;
                    else
                        effect = 0;
                    if (GameBase.deathmatch.value != 0)
                        damage = 15;
                    else
                        damage = 20;
                    Blaster_Fire(ent, offset, damage, true, effect);
                    if (0 == ((int) GameBase.dmflags.value & Defines.DF_INFINITE_AMMO))
                        ent.client.pers.inventory[ent.client.ammo_index]--;

                    ent.client.anim_priority = Defines.ANIM_ATTACK;
                    if ((ent.client.ps.pmove.pm_flags & pmove_t.PMF_DUCKED) != 0) {
                        ent.s.frame = M_Player.FRAME_crattak1 - 1;
                        ent.client.anim_end = M_Player.FRAME_crattak9;
                    } else {
                        ent.s.frame = M_Player.FRAME_attack1 - 1;
                        ent.client.anim_end = M_Player.FRAME_attack8;
                    }
                }

                ent.client.ps.gunframe++;
                if (ent.client.ps.gunframe == 12
                        && 0 != ent.client.pers.inventory[ent.client.ammo_index])
                    ent.client.ps.gunframe = 6;
            }

            if (ent.client.ps.gunframe == 12) {
                GameBase.gi.sound(ent, Defines.CHAN_AUTO, GameBase.gi
                        .soundindex("weapons/hyprbd1a.wav"), 1,
                        Defines.ATTN_NORM, 0);
                ent.client.weapon_sound = 0;
            }

            return true;

        }
    };

    public static EntThinkAdapter Weapon_HyperBlaster = new EntThinkAdapter() {
        public boolean think(edict_t ent) {

            int pause_frames[] = { 0 };
            int fire_frames[] = { 6, 7, 8, 9, 10, 11, 0 };

            Weapon_Generic(ent, 5, 20, 49, 53, pause_frames, fire_frames,
                    Weapon_HyperBlaster_Fire);
            return true;
        }
    };

    public static EntThinkAdapter Weapon_Machinegun = new EntThinkAdapter() {
        public boolean think(edict_t ent) {

            int pause_frames[] = { 23, 45, 0 };
            int fire_frames[] = { 4, 5, 0 };

            Weapon_Generic(ent, 3, 5, 45, 49, pause_frames, fire_frames,
                    Machinegun_Fire);
            return true;
        }
    };

    public static EntThinkAdapter Weapon_Chaingun = new EntThinkAdapter() {
        public boolean think(edict_t ent) {

            int pause_frames[] = { 38, 43, 51, 61, 0 };
            int fire_frames[] = { 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
                    17, 18, 19, 20, 21, 0 };

            Weapon_Generic(ent, 4, 31, 61, 64, pause_frames, fire_frames,
                    Chaingun_Fire);
            return true;
        }
    };

    /*
     * ======================================================================
     * 
     * SHOTGUN / SUPERSHOTGUN
     * 
     * ======================================================================
     */

    public static EntThinkAdapter weapon_shotgun_fire = new EntThinkAdapter() {

        public boolean think(edict_t ent) {

            float[] start = { 0, 0, 0 };
            float[] forward = { 0, 0, 0 }, right = { 0, 0, 0 };
            float[] offset = { 0, 0, 0 };
            int damage = 4;
            int kick = 8;

            if (ent.client.ps.gunframe == 9) {
                ent.client.ps.gunframe++;
                return true;
            }

            Math3D.AngleVectors(ent.client.v_angle, forward, right, null);

            Math3D.VectorScale(forward, -2, ent.client.kick_origin);
            ent.client.kick_angles[0] = -2;

            Math3D.VectorSet(offset, 0, 8, ent.viewheight - 8);
            P_ProjectSource(ent.client, ent.s.origin, offset, forward, right,
                    start);

            if (is_quad) {
                damage *= 4;
                kick *= 4;
            }

            if (GameBase.deathmatch.value != 0)
                Fire.fire_shotgun(ent, start, forward, damage, kick, 500, 500,
                        Defines.DEFAULT_DEATHMATCH_SHOTGUN_COUNT,
                        Defines.MOD_SHOTGUN);
            else
                Fire.fire_shotgun(ent, start, forward, damage, kick, 500, 500,
                        Defines.DEFAULT_SHOTGUN_COUNT, Defines.MOD_SHOTGUN);

            // send muzzle flash
            GameBase.gi.WriteByte(Defines.svc_muzzleflash);

            GameBase.gi.WriteShort(ent.index);
            GameBase.gi.WriteByte(Defines.MZ_SHOTGUN | is_silenced);
            GameBase.gi.multicast(ent.s.origin, Defines.MULTICAST_PVS);

            ent.client.ps.gunframe++;
            GameWeapon.PlayerNoise(ent, start, Defines.PNOISE_WEAPON);

            if (0 == ((int) GameBase.dmflags.value & Defines.DF_INFINITE_AMMO))
                ent.client.pers.inventory[ent.client.ammo_index]--;

            return true;
        }
    };

    public static EntThinkAdapter Weapon_Shotgun = new EntThinkAdapter() {
        public boolean think(edict_t ent) {
            int pause_frames[] = { 22, 28, 34, 0 };
            int fire_frames[] = { 8, 9, 0 };

            Weapon_Generic(ent, 7, 18, 36, 39, pause_frames, fire_frames,
                    weapon_shotgun_fire);
            return true;
        }
    };

    public static EntThinkAdapter weapon_supershotgun_fire = new EntThinkAdapter() {

        public boolean think(edict_t ent) {

            float[] start = { 0, 0, 0 };
            float[] forward = { 0, 0, 0 }, right = { 0, 0, 0 };
            float[] offset = { 0, 0, 0 };
            float[] v = { 0, 0, 0 };
            int damage = 6;
            int kick = 12;

            Math3D.AngleVectors(ent.client.v_angle, forward, right, null);

            Math3D.VectorScale(forward, -2, ent.client.kick_origin);
            ent.client.kick_angles[0] = -2;

            Math3D.VectorSet(offset, 0, 8, ent.viewheight - 8);
            P_ProjectSource(ent.client, ent.s.origin, offset, forward, right,
                    start);

            if (is_quad) {
                damage *= 4;
                kick *= 4;
            }

            v[Defines.PITCH] = ent.client.v_angle[Defines.PITCH];
            v[Defines.YAW] = ent.client.v_angle[Defines.YAW] - 5;
            v[Defines.ROLL] = ent.client.v_angle[Defines.ROLL];
            Math3D.AngleVectors(v, forward, null, null);
            Fire.fire_shotgun(ent, start, forward, damage, kick,
                    Defines.DEFAULT_SHOTGUN_HSPREAD,
                    Defines.DEFAULT_SHOTGUN_VSPREAD,
                    Defines.DEFAULT_SSHOTGUN_COUNT / 2, Defines.MOD_SSHOTGUN);
            v[Defines.YAW] = ent.client.v_angle[Defines.YAW] + 5;
            Math3D.AngleVectors(v, forward, null, null);
            Fire.fire_shotgun(ent, start, forward, damage, kick,
                    Defines.DEFAULT_SHOTGUN_HSPREAD,
                    Defines.DEFAULT_SHOTGUN_VSPREAD,
                    Defines.DEFAULT_SSHOTGUN_COUNT / 2, Defines.MOD_SSHOTGUN);

            // send muzzle flash
            GameBase.gi.WriteByte(Defines.svc_muzzleflash);

            GameBase.gi.WriteShort(ent.index);
            GameBase.gi.WriteByte(Defines.MZ_SSHOTGUN | is_silenced);
            GameBase.gi.multicast(ent.s.origin, Defines.MULTICAST_PVS);

            ent.client.ps.gunframe++;
            GameWeapon.PlayerNoise(ent, start, Defines.PNOISE_WEAPON);

            if (0 == ((int) GameBase.dmflags.value & Defines.DF_INFINITE_AMMO))
                ent.client.pers.inventory[ent.client.ammo_index] -= 2;

            return true;
        }
    };

    public static EntThinkAdapter Weapon_SuperShotgun = new EntThinkAdapter() {
        public boolean think(edict_t ent) {

            int pause_frames[] = { 29, 42, 57, 0 };
            int fire_frames[] = { 7, 0 };

            Weapon_Generic(ent, 6, 17, 57, 61, pause_frames, fire_frames,
                    weapon_supershotgun_fire);
            return true;
        }
    };

    /*
     * ======================================================================
     * 
     * RAILGUN
     * 
     * ======================================================================
     */
    public static EntThinkAdapter weapon_railgun_fire = new EntThinkAdapter() {

        public boolean think(edict_t ent) {

            float[] start = { 0, 0, 0 };
            float[] forward = { 0, 0, 0 }, right = { 0, 0, 0 };
            float[] offset = { 0, 0, 0 };
            int damage;
            int kick;

            if (GameBase.deathmatch.value != 0) { // normal damage is too
                // extreme in dm
                damage = 100;
                kick = 200;
            } else {
                damage = 150;
                kick = 250;
            }

            if (is_quad) {
                damage *= 4;
                kick *= 4;
            }

            Math3D.AngleVectors(ent.client.v_angle, forward, right, null);

            Math3D.VectorScale(forward, -3, ent.client.kick_origin);
            ent.client.kick_angles[0] = -3;

            Math3D.VectorSet(offset, 0, 7, ent.viewheight - 8);
            P_ProjectSource(ent.client, ent.s.origin, offset, forward, right,
                    start);
            Fire.fire_rail(ent, start, forward, damage, kick);

            // send muzzle flash
            GameBase.gi.WriteByte(Defines.svc_muzzleflash);

            GameBase.gi.WriteShort(ent.index);
            GameBase.gi.WriteByte(Defines.MZ_RAILGUN | is_silenced);
            GameBase.gi.multicast(ent.s.origin, Defines.MULTICAST_PVS);

            ent.client.ps.gunframe++;
            GameWeapon.PlayerNoise(ent, start, Defines.PNOISE_WEAPON);

            if (0 == ((int) GameBase.dmflags.value & Defines.DF_INFINITE_AMMO))
                ent.client.pers.inventory[ent.client.ammo_index]--;

            return true;
        }
    };

    public static EntThinkAdapter Weapon_Railgun = new EntThinkAdapter() {

        public boolean think(edict_t ent) {

            int pause_frames[] = { 56, 0 };
            int fire_frames[] = { 4, 0 };
            Weapon_Generic(ent, 3, 18, 56, 61, pause_frames, fire_frames,
                    weapon_railgun_fire);
            return true;
        }
    };

    /*
     * ======================================================================
     * 
     * BFG10K
     * 
     * ======================================================================
     */

    public static EntThinkAdapter weapon_bfg_fire = new EntThinkAdapter() {

        public boolean think(edict_t ent) {

            float[] offset = { 0, 0, 0 }, start = { 0, 0, 0 };
            float[] forward = { 0, 0, 0 }, right = { 0, 0, 0 };
            int damage;
            float damage_radius = 1000;

            if (GameBase.deathmatch.value != 0)
                damage = 200;
            else
                damage = 500;

            if (ent.client.ps.gunframe == 9) {
                // send muzzle flash
                GameBase.gi.WriteByte(Defines.svc_muzzleflash);

                GameBase.gi.WriteShort(ent.index);
                GameBase.gi.WriteByte(Defines.MZ_BFG | is_silenced);
                GameBase.gi.multicast(ent.s.origin, Defines.MULTICAST_PVS);

                ent.client.ps.gunframe++;

                GameWeapon.PlayerNoise(ent, start, Defines.PNOISE_WEAPON);
                return true;
            }

            // cells can go down during windup (from power armor hits), so
            // check again and abort firing if we don't have enough now
            if (ent.client.pers.inventory[ent.client.ammo_index] < 50) {
                ent.client.ps.gunframe++;
                return true;
            }

            if (is_quad)
                damage *= 4;

            Math3D.AngleVectors(ent.client.v_angle, forward, right, null);

            Math3D.VectorScale(forward, -2, ent.client.kick_origin);

            // make a big pitch kick with an inverse fall
            ent.client.v_dmg_pitch = -40;
            ent.client.v_dmg_roll = Lib.crandom() * 8;
            ent.client.v_dmg_time = GameBase.level.time + Defines.DAMAGE_TIME;

            Math3D.VectorSet(offset, 8, 8, ent.viewheight - 8);
            P_ProjectSource(ent.client, ent.s.origin, offset, forward, right,
                    start);
            Fire.fire_bfg(ent, start, forward, damage, 400, damage_radius);

            ent.client.ps.gunframe++;

            GameWeapon.PlayerNoise(ent, start, Defines.PNOISE_WEAPON);

            if (0 == ((int) GameBase.dmflags.value & Defines.DF_INFINITE_AMMO))
                ent.client.pers.inventory[ent.client.ammo_index] -= 50;

            return true;
        }
    };

    public static EntThinkAdapter Weapon_BFG = new EntThinkAdapter() {
        public boolean think(edict_t ent) {

            Weapon_Generic(ent, 8, 32, 55, 58, pause_frames, fire_frames,
                    weapon_bfg_fire);
            return true;
        }
    };

    public static boolean is_quad;

    public static byte is_silenced;

    public static EntInteractAdapter Pickup_Weapon = new EntInteractAdapter() {
        public boolean interact(edict_t ent, edict_t other) {
            int index;
            gitem_t ammo;

            index = GameUtil.ITEM_INDEX(ent.item);

            if ((((int) (GameBase.dmflags.value) & Defines.DF_WEAPONS_STAY) != 0 || GameBase.coop.value != 0)
                    && 0 != other.client.pers.inventory[index]) {
                if (0 == (ent.spawnflags & (Defines.DROPPED_ITEM | Defines.DROPPED_PLAYER_ITEM)))
                    return false; // leave the weapon for others to pickup
            }

            other.client.pers.inventory[index]++;

            if (0 == (ent.spawnflags & Defines.DROPPED_ITEM)) {
                // give them some ammo with it
                ammo = GameUtil.FindItem(ent.item.ammo);
                if (((int) GameBase.dmflags.value & Defines.DF_INFINITE_AMMO) != 0)
                    GameAI.Add_Ammo(other, ammo, 1000);
                else
                    GameAI.Add_Ammo(other, ammo, ammo.quantity);

                if (0 == (ent.spawnflags & Defines.DROPPED_PLAYER_ITEM)) {
                    if (GameBase.deathmatch.value != 0) {
                        if (((int) (GameBase.dmflags.value) & Defines.DF_WEAPONS_STAY) != 0)
                            ent.flags |= Defines.FL_RESPAWN;
                        else
                            GameUtil.SetRespawn(ent, 30);
                    }
                    if (GameBase.coop.value != 0)
                        ent.flags |= Defines.FL_RESPAWN;
                }
            }

            if (other.client.pers.weapon != ent.item
                    && (other.client.pers.inventory[index] == 1)
                    && (0 == GameBase.deathmatch.value || other.client.pers.weapon == GameUtil
                            .FindItem("blaster")))
                other.client.newweapon = ent.item;

            return true;
        }
    };

    /*
     * ================ Use_Weapon
     * 
     * Make the weapon ready if there is ammo ================
     */
    public static ItemUseAdapter Use_Weapon = new ItemUseAdapter() {

        public void use(edict_t ent, gitem_t item) {
            int ammo_index;
            gitem_t ammo_item;

            // see if we're already using it
            if (item == ent.client.pers.weapon)
                return;

            if (item.ammo != null && 0 == GameBase.g_select_empty.value
                    && 0 == (item.flags & Defines.IT_AMMO)) {
                
                ammo_item = GameUtil.FindItem(item.ammo);
                ammo_index = GameUtil.ITEM_INDEX(ammo_item);

                if (0 == ent.client.pers.inventory[ammo_index]) {
                    GameBase.gi.cprintf(ent, Defines.PRINT_HIGH, "No "
                            + ammo_item.pickup_name + " for "
                            + item.pickup_name + ".\n");
                    return;
                }

                if (ent.client.pers.inventory[ammo_index] < item.quantity) {
                    GameBase.gi.cprintf(ent, Defines.PRINT_HIGH, "Not enough "
                            + ammo_item.pickup_name + " for "
                            + item.pickup_name + ".\n");
                    return;
                }
            }

            // change to this weapon when down
            ent.client.newweapon = item;
        }
    };

    /*
     * ================ Drop_Weapon ================
     */

    public static ItemDropAdapter Drop_Weapon = new ItemDropAdapter() {
        public void drop(edict_t ent, gitem_t item) {
            int index;

            if (0 != ((int) (GameBase.dmflags.value) & Defines.DF_WEAPONS_STAY))
                return;

            index = GameUtil.ITEM_INDEX(item);
            // see if we're already using it
            if (((item == ent.client.pers.weapon) || (item == ent.client.newweapon))
                    && (ent.client.pers.inventory[index] == 1)) {
                GameBase.gi.cprintf(ent, Defines.PRINT_HIGH,
                        "Can't drop current weapon\n");
                return;
            }

            GameUtil.Drop_Item(ent, item);
            ent.client.pers.inventory[index]--;
        }
    };

    /*
     * ======================================================================
     * 
     * MACHINEGUN / CHAINGUN
     * 
     * ======================================================================
     */

    public static EntThinkAdapter Machinegun_Fire = new EntThinkAdapter() {

        public boolean think(edict_t ent) {

            int i;
            float[] start = { 0, 0, 0 };
            float[] forward = { 0, 0, 0 }, right = { 0, 0, 0 };
            float[] angles = { 0, 0, 0 };
            int damage = 8;
            int kick = 2;
            float[] offset = { 0, 0, 0 };

            if (0 == (ent.client.buttons & Defines.BUTTON_ATTACK)) {
                ent.client.machinegun_shots = 0;
                ent.client.ps.gunframe++;
                return true;
            }

            if (ent.client.ps.gunframe == 5)
                ent.client.ps.gunframe = 4;
            else
                ent.client.ps.gunframe = 5;

            if (ent.client.pers.inventory[ent.client.ammo_index] < 1) {
                ent.client.ps.gunframe = 6;
                if (GameBase.level.time >= ent.pain_debounce_time) {
                    GameBase.gi.sound(ent, Defines.CHAN_VOICE, GameBase.gi
                            .soundindex("weapons/noammo.wav"), 1,
                            Defines.ATTN_NORM, 0);
                    ent.pain_debounce_time = GameBase.level.time + 1;
                }
                NoAmmoWeaponChange(ent);
                return true;
            }

            if (is_quad) {
                damage *= 4;
                kick *= 4;
            }

            for (i = 1; i < 3; i++) {
                ent.client.kick_origin[i] = Lib.crandom() * 0.35f;
                ent.client.kick_angles[i] = Lib.crandom() * 0.7f;
            }
            ent.client.kick_origin[0] = Lib.crandom() * 0.35f;
            ent.client.kick_angles[0] = ent.client.machinegun_shots * -1.5f;

            // raise the gun as it is firing
            if (0 == GameBase.deathmatch.value) {
                ent.client.machinegun_shots++;
                if (ent.client.machinegun_shots > 9)
                    ent.client.machinegun_shots = 9;
            }

            // get start / end positions
            Math3D
                    .VectorAdd(ent.client.v_angle, ent.client.kick_angles,
                            angles);
            Math3D.AngleVectors(angles, forward, right, null);
            Math3D.VectorSet(offset, 0, 8, ent.viewheight - 8);
            P_ProjectSource(ent.client, ent.s.origin, offset, forward, right,
                    start);
            Fire.fire_bullet(ent, start, forward, damage, kick,
                    Defines.DEFAULT_BULLET_HSPREAD,
                    Defines.DEFAULT_BULLET_VSPREAD, Defines.MOD_MACHINEGUN);

            GameBase.gi.WriteByte(Defines.svc_muzzleflash);

            GameBase.gi.WriteShort(ent.index);
            GameBase.gi.WriteByte(Defines.MZ_MACHINEGUN | is_silenced);
            GameBase.gi.multicast(ent.s.origin, Defines.MULTICAST_PVS);

            GameWeapon.PlayerNoise(ent, start, Defines.PNOISE_WEAPON);

            if (0 == ((int) GameBase.dmflags.value & Defines.DF_INFINITE_AMMO))
                ent.client.pers.inventory[ent.client.ammo_index]--;

            ent.client.anim_priority = Defines.ANIM_ATTACK;
            if ((ent.client.ps.pmove.pm_flags & pmove_t.PMF_DUCKED) != 0) {
                ent.s.frame = M_Player.FRAME_crattak1
                        - (int) (Lib.random() + 0.25);
                ent.client.anim_end = M_Player.FRAME_crattak9;
            } else {
                ent.s.frame = M_Player.FRAME_attack1
                        - (int) (Lib.random() + 0.25);
                ent.client.anim_end = M_Player.FRAME_attack8;
            }
            return true;
        }
    };

    public static EntThinkAdapter Chaingun_Fire = new EntThinkAdapter() {

        public boolean think(edict_t ent) {

            int i;
            int shots;
            float[] start = { 0, 0, 0 };
            float[] forward = { 0, 0, 0 }, right = { 0, 0, 0 }, up = { 0, 0, 0 };
            float r, u;
            float[] offset = { 0, 0, 0 };
            int damage;
            int kick = 2;

            if (GameBase.deathmatch.value != 0)
                damage = 6;
            else
                damage = 8;

            if (ent.client.ps.gunframe == 5)
                GameBase.gi.sound(ent, Defines.CHAN_AUTO, GameBase.gi
                        .soundindex("weapons/chngnu1a.wav"), 1,
                        Defines.ATTN_IDLE, 0);

            if ((ent.client.ps.gunframe == 14)
                    && 0 == (ent.client.buttons & Defines.BUTTON_ATTACK)) {
                ent.client.ps.gunframe = 32;
                ent.client.weapon_sound = 0;
                return true;
            } else if ((ent.client.ps.gunframe == 21)
                    && (ent.client.buttons & Defines.BUTTON_ATTACK) != 0
                    && 0 != ent.client.pers.inventory[ent.client.ammo_index]) {
                ent.client.ps.gunframe = 15;
            } else {
                ent.client.ps.gunframe++;
            }

            if (ent.client.ps.gunframe == 22) {
                ent.client.weapon_sound = 0;
                GameBase.gi.sound(ent, Defines.CHAN_AUTO, GameBase.gi
                        .soundindex("weapons/chngnd1a.wav"), 1,
                        Defines.ATTN_IDLE, 0);
            } else {
                ent.client.weapon_sound = GameBase.gi
                        .soundindex("weapons/chngnl1a.wav");
            }

            ent.client.anim_priority = Defines.ANIM_ATTACK;
            if ((ent.client.ps.pmove.pm_flags & pmove_t.PMF_DUCKED) != 0) {
                ent.s.frame = M_Player.FRAME_crattak1
                        - (ent.client.ps.gunframe & 1);
                ent.client.anim_end = M_Player.FRAME_crattak9;
            } else {
                ent.s.frame = M_Player.FRAME_attack1
                        - (ent.client.ps.gunframe & 1);
                ent.client.anim_end = M_Player.FRAME_attack8;
            }

            if (ent.client.ps.gunframe <= 9)
                shots = 1;
            else if (ent.client.ps.gunframe <= 14) {
                if ((ent.client.buttons & Defines.BUTTON_ATTACK) != 0)
                    shots = 2;
                else
                    shots = 1;
            } else
                shots = 3;

            if (ent.client.pers.inventory[ent.client.ammo_index] < shots)
                shots = ent.client.pers.inventory[ent.client.ammo_index];

            if (0 == shots) {
                if (GameBase.level.time >= ent.pain_debounce_time) {
                    GameBase.gi.sound(ent, Defines.CHAN_VOICE, GameBase.gi
                            .soundindex("weapons/noammo.wav"), 1,
                            Defines.ATTN_NORM, 0);
                    ent.pain_debounce_time = GameBase.level.time + 1;
                }
                NoAmmoWeaponChange(ent);
                return true;
            }

            if (is_quad) {
                damage *= 4;
                kick *= 4;
            }

            for (i = 0; i < 3; i++) {
                ent.client.kick_origin[i] = Lib.crandom() * 0.35f;
                ent.client.kick_angles[i] = Lib.crandom() * 0.7f;
            }

            for (i = 0; i < shots; i++) {
                // get start / end positions
                Math3D.AngleVectors(ent.client.v_angle, forward, right, up);
                r = 7 + Lib.crandom() * 4;
                u = Lib.crandom() * 4;
                Math3D.VectorSet(offset, 0, r, u + ent.viewheight - 8);
                P_ProjectSource(ent.client, ent.s.origin, offset, forward,
                        right, start);

                Fire.fire_bullet(ent, start, forward, damage, kick,
                        Defines.DEFAULT_BULLET_HSPREAD,
                        Defines.DEFAULT_BULLET_VSPREAD, Defines.MOD_CHAINGUN);
            }

            // send muzzle flash
            GameBase.gi.WriteByte(Defines.svc_muzzleflash);

            GameBase.gi.WriteShort(ent.index);
            GameBase.gi.WriteByte((Defines.MZ_CHAINGUN1 + shots - 1)
                    | is_silenced);
            GameBase.gi.multicast(ent.s.origin, Defines.MULTICAST_PVS);

            GameWeapon.PlayerNoise(ent, start, Defines.PNOISE_WEAPON);

            if (0 == ((int) GameBase.dmflags.value & Defines.DF_INFINITE_AMMO))
                ent.client.pers.inventory[ent.client.ammo_index] -= shots;

            return true;
        }
    };

    public static int pause_frames[] = { 39, 45, 50, 55, 0 };

    public static int fire_frames[] = { 9, 17, 0 };

    public static void P_ProjectSource(gclient_t client, float[] point,
            float[] distance, float[] forward, float[] right, float[] result) {
        float[] _distance = { 0, 0, 0 };

        Math3D.VectorCopy(distance, _distance);
        if (client.pers.hand == Defines.LEFT_HANDED)
            _distance[1] *= -1;
        else if (client.pers.hand == Defines.CENTER_HANDED)
            _distance[1] = 0;
        Math3D.G_ProjectSource(point, _distance, forward, right, result);
    }

    /*
     * =============== ChangeWeapon
     * 
     * The old weapon has been dropped all the way, so make the new one current
     * ===============
     */
    public static void ChangeWeapon(edict_t ent) {
        int i;

        if (ent.client.grenade_time != 0) {
            ent.client.grenade_time = GameBase.level.time;
            ent.client.weapon_sound = 0;
            weapon_grenade_fire(ent, false);
            ent.client.grenade_time = 0;
        }

        ent.client.pers.lastweapon = ent.client.pers.weapon;
        ent.client.pers.weapon = ent.client.newweapon;
        ent.client.newweapon = null;
        ent.client.machinegun_shots = 0;

        // set visible model
        if (ent.s.modelindex == 255) {
            if (ent.client.pers.weapon != null)
                i = ((ent.client.pers.weapon.weapmodel & 0xff) << 8);
            else
                i = 0;
            ent.s.skinnum = (ent.index - 1) | i;
        }

        if (ent.client.pers.weapon != null
                && ent.client.pers.weapon.ammo != null)
            
            ent.client.ammo_index = GameUtil.ITEM_INDEX(GameUtil
                    .FindItem(ent.client.pers.weapon.ammo));
        else
            ent.client.ammo_index = 0;

        if (ent.client.pers.weapon == null) { // dead
            ent.client.ps.gunindex = 0;
            return;
        }

        ent.client.weaponstate = Defines.WEAPON_ACTIVATING;
        ent.client.ps.gunframe = 0;
        ent.client.ps.gunindex = GameBase.gi
                .modelindex(ent.client.pers.weapon.view_model);

        ent.client.anim_priority = Defines.ANIM_PAIN;
        if ((ent.client.ps.pmove.pm_flags & pmove_t.PMF_DUCKED) != 0) {
            ent.s.frame = M_Player.FRAME_crpain1;
            ent.client.anim_end = M_Player.FRAME_crpain4;
        } else {
            ent.s.frame = M_Player.FRAME_pain301;
            ent.client.anim_end = M_Player.FRAME_pain304;

        }
    }

    /*
     * ================= NoAmmoWeaponChange =================
     */
    public static void NoAmmoWeaponChange(edict_t ent) {
        if (0 != ent.client.pers.inventory[GameUtil.ITEM_INDEX(GameUtil
                .FindItem("slugs"))]
                && 0 != ent.client.pers.inventory[GameUtil.ITEM_INDEX(GameUtil
                        .FindItem("railgun"))]) {
            ent.client.newweapon = GameUtil.FindItem("railgun");
            return;
        }
        if (0 != ent.client.pers.inventory[GameUtil.ITEM_INDEX(GameUtil
                .FindItem("cells"))]
                && 0 != ent.client.pers.inventory[GameUtil.ITEM_INDEX(GameUtil
                        .FindItem("hyperblaster"))]) {
            ent.client.newweapon = GameUtil.FindItem("hyperblaster");
            return;
        }
        if (0 != ent.client.pers.inventory[GameUtil.ITEM_INDEX(GameUtil
                .FindItem("bullets"))]
                && 0 != ent.client.pers.inventory[GameUtil.ITEM_INDEX(GameUtil
                        .FindItem("chaingun"))]) {
            ent.client.newweapon = GameUtil.FindItem("chaingun");
            return;
        }
        if (0 != ent.client.pers.inventory[GameUtil.ITEM_INDEX(GameUtil
                .FindItem("bullets"))]
                && 0 != ent.client.pers.inventory[GameUtil.ITEM_INDEX(GameUtil
                        .FindItem("machinegun"))]) {
            ent.client.newweapon = GameUtil.FindItem("machinegun");
            return;
        }
        if (ent.client.pers.inventory[GameUtil.ITEM_INDEX(GameUtil
                .FindItem("shells"))] > 1
                && 0 != ent.client.pers.inventory[GameUtil.ITEM_INDEX(GameUtil
                        .FindItem("super shotgun"))]) {
            ent.client.newweapon = GameUtil.FindItem("super shotgun");
            return;
        }
        if (0 != ent.client.pers.inventory[GameUtil.ITEM_INDEX(GameUtil
                .FindItem("shells"))]
                && 0 != ent.client.pers.inventory[GameUtil.ITEM_INDEX(GameUtil
                        .FindItem("shotgun"))]) {
            ent.client.newweapon = GameUtil.FindItem("shotgun");
            return;
        }
        ent.client.newweapon = GameUtil.FindItem("blaster");
    }

    /*
     * ================= Think_Weapon
     * 
     * Called by ClientBeginServerFrame and ClientThink =================
     */
    public static void Think_Weapon(edict_t ent) {
        // if just died, put the weapon away
        if (ent.health < 1) {
            ent.client.newweapon = null;
            ChangeWeapon(ent);
        }

        // call active weapon think routine
        if (null != ent.client.pers.weapon
                && null != ent.client.pers.weapon.weaponthink) {
            is_quad = (ent.client.quad_framenum > GameBase.level.framenum);
            if (ent.client.silencer_shots != 0)
                is_silenced = (byte) Defines.MZ_SILENCED;
            else
                is_silenced = 0;
            ent.client.pers.weapon.weaponthink.think(ent);
        }
    }

    /*
     * ================ Weapon_Generic
     * 
     * A generic function to handle the basics of weapon thinking
     * ================
     */

    public static void Weapon_Generic(edict_t ent, int FRAME_ACTIVATE_LAST,
            int FRAME_FIRE_LAST, int FRAME_IDLE_LAST,
            int FRAME_DEACTIVATE_LAST, int pause_frames[], int fire_frames[],
            EntThinkAdapter fire) {
        int FRAME_FIRE_FIRST = (FRAME_ACTIVATE_LAST + 1);
        int FRAME_IDLE_FIRST = (FRAME_FIRE_LAST + 1);
        int FRAME_DEACTIVATE_FIRST = (FRAME_IDLE_LAST + 1);

        int n;

        if (ent.deadflag != 0 || ent.s.modelindex != 255) // VWep animations
        // screw up corpses
        {
            return;
        }

        if (ent.client.weaponstate == Defines.WEAPON_DROPPING) {
            if (ent.client.ps.gunframe == FRAME_DEACTIVATE_LAST) {
                ChangeWeapon(ent);
                return;
            } else if ((FRAME_DEACTIVATE_LAST - ent.client.ps.gunframe) == 4) {
                ent.client.anim_priority = Defines.ANIM_REVERSE;
                if ((ent.client.ps.pmove.pm_flags & pmove_t.PMF_DUCKED) != 0) {
                    ent.s.frame = M_Player.FRAME_crpain4 + 1;
                    ent.client.anim_end = M_Player.FRAME_crpain1;
                } else {
                    ent.s.frame = M_Player.FRAME_pain304 + 1;
                    ent.client.anim_end = M_Player.FRAME_pain301;

                }
            }

            ent.client.ps.gunframe++;
            return;
        }

        if (ent.client.weaponstate == Defines.WEAPON_ACTIVATING) {
            if (ent.client.ps.gunframe == FRAME_ACTIVATE_LAST) {
                ent.client.weaponstate = Defines.WEAPON_READY;
                ent.client.ps.gunframe = FRAME_IDLE_FIRST;
                return;
            }

            ent.client.ps.gunframe++;
            return;
        }

        if ((ent.client.newweapon != null)
                && (ent.client.weaponstate != Defines.WEAPON_FIRING)) {
            ent.client.weaponstate = Defines.WEAPON_DROPPING;
            ent.client.ps.gunframe = FRAME_DEACTIVATE_FIRST;

            if ((FRAME_DEACTIVATE_LAST - FRAME_DEACTIVATE_FIRST) < 4) {
                ent.client.anim_priority = Defines.ANIM_REVERSE;
                if ((ent.client.ps.pmove.pm_flags & pmove_t.PMF_DUCKED) != 0) {
                    ent.s.frame = M_Player.FRAME_crpain4 + 1;
                    ent.client.anim_end = M_Player.FRAME_crpain1;
                } else {
                    ent.s.frame = M_Player.FRAME_pain304 + 1;
                    ent.client.anim_end = M_Player.FRAME_pain301;

                }
            }
            return;
        }

        if (ent.client.weaponstate == Defines.WEAPON_READY) {
            if (((ent.client.latched_buttons | ent.client.buttons) & Defines.BUTTON_ATTACK) != 0) {
                ent.client.latched_buttons &= ~Defines.BUTTON_ATTACK;
                if ((0 == ent.client.ammo_index)
                        || (ent.client.pers.inventory[ent.client.ammo_index] >= ent.client.pers.weapon.quantity)) {
                    ent.client.ps.gunframe = FRAME_FIRE_FIRST;
                    ent.client.weaponstate = Defines.WEAPON_FIRING;

                    // start the animation
                    ent.client.anim_priority = Defines.ANIM_ATTACK;
                    if ((ent.client.ps.pmove.pm_flags & pmove_t.PMF_DUCKED) != 0) {
                        ent.s.frame = M_Player.FRAME_crattak1 - 1;
                        ent.client.anim_end = M_Player.FRAME_crattak9;
                    } else {
                        ent.s.frame = M_Player.FRAME_attack1 - 1;
                        ent.client.anim_end = M_Player.FRAME_attack8;
                    }
                } else {
                    if (GameBase.level.time >= ent.pain_debounce_time) {
                        GameBase.gi.sound(ent, Defines.CHAN_VOICE, GameBase.gi
                                .soundindex("weapons/noammo.wav"), 1,
                                Defines.ATTN_NORM, 0);
                        ent.pain_debounce_time = GameBase.level.time + 1;
                    }
                    NoAmmoWeaponChange(ent);
                }
            } else {
                if (ent.client.ps.gunframe == FRAME_IDLE_LAST) {
                    ent.client.ps.gunframe = FRAME_IDLE_FIRST;
                    return;
                }

                if (pause_frames != null) {
                    for (n = 0; pause_frames[n] != 0; n++) {
                        if (ent.client.ps.gunframe == pause_frames[n]) {
                            if ((Lib.rand() & 15) != 0)
                                return;
                        }
                    }
                }

                ent.client.ps.gunframe++;
                return;
            }
        }

        if (ent.client.weaponstate == Defines.WEAPON_FIRING) {
            for (n = 0; fire_frames[n] != 0; n++) {
                if (ent.client.ps.gunframe == fire_frames[n]) {
                    if (ent.client.quad_framenum > GameBase.level.framenum)
                        GameBase.gi.sound(ent, Defines.CHAN_ITEM, GameBase.gi
                                .soundindex("items/damage3.wav"), 1,
                                Defines.ATTN_NORM, 0);

                    fire.think(ent);
                    break;
                }
            }

            if (0 == fire_frames[n])
                ent.client.ps.gunframe++;

            if (ent.client.ps.gunframe == FRAME_IDLE_FIRST + 1)
                ent.client.weaponstate = Defines.WEAPON_READY;
        }
    }

    /*
     * ======================================================================
     * 
     * GRENADE
     * 
     * ======================================================================
     */

    public static void weapon_grenade_fire(edict_t ent, boolean held) {
        float[] offset = { 0, 0, 0 };
        float[] forward = { 0, 0, 0 }, right = { 0, 0, 0 };
        float[] start = { 0, 0, 0 };
        int damage = 125;
        float timer;
        int speed;
        float radius;

        radius = damage + 40;
        if (is_quad)
            damage *= 4;

        Math3D.VectorSet(offset, 8, 8, ent.viewheight - 8);
        Math3D.AngleVectors(ent.client.v_angle, forward, right, null);
        P_ProjectSource(ent.client, ent.s.origin, offset, forward, right, start);

        timer = ent.client.grenade_time - GameBase.level.time;
        speed = (int) (Defines.GRENADE_MINSPEED + (Defines.GRENADE_TIMER - timer)
                * ((Defines.GRENADE_MAXSPEED - Defines.GRENADE_MINSPEED) / Defines.GRENADE_TIMER));
        Fire.fire_grenade2(ent, start, forward, damage, speed, timer, radius,
                held);

        if (0 == ((int) GameBase.dmflags.value & Defines.DF_INFINITE_AMMO))
            ent.client.pers.inventory[ent.client.ammo_index]--;

        ent.client.grenade_time = GameBase.level.time + 1.0f;

        if (ent.deadflag != 0 || ent.s.modelindex != 255) // VWep animations
        // screw up corpses
        {
            return;
        }

        if (ent.health <= 0)
            return;

        if ((ent.client.ps.pmove.pm_flags & pmove_t.PMF_DUCKED) != 0) {
            ent.client.anim_priority = Defines.ANIM_ATTACK;
            ent.s.frame = M_Player.FRAME_crattak1 - 1;
            ent.client.anim_end = M_Player.FRAME_crattak3;
        } else {
            ent.client.anim_priority = Defines.ANIM_REVERSE;
            ent.s.frame = M_Player.FRAME_wave08;
            ent.client.anim_end = M_Player.FRAME_wave01;
        }
    }

    /*
     * ======================================================================
     * 
     * BLASTER / HYPERBLASTER
     * 
     * ======================================================================
     */

    public static void Blaster_Fire(edict_t ent, float[] g_offset, int damage,
            boolean hyper, int effect) {
        float[] forward = { 0, 0, 0 }, right = { 0, 0, 0 };
        float[] start = { 0, 0, 0 };
        float[] offset = { 0, 0, 0 };

        if (is_quad)
            damage *= 4;
        Math3D.AngleVectors(ent.client.v_angle, forward, right, null);
        Math3D.VectorSet(offset, 24, 8, ent.viewheight - 8);
        Math3D.VectorAdd(offset, g_offset, offset);
        P_ProjectSource(ent.client, ent.s.origin, offset, forward, right, start);

        Math3D.VectorScale(forward, -2, ent.client.kick_origin);
        ent.client.kick_angles[0] = -1;

        Fire.fire_blaster(ent, start, forward, damage, 1000, effect, hyper);

        // send muzzle flash
        GameBase.gi.WriteByte(Defines.svc_muzzleflash);
        GameBase.gi.WriteShort(ent.index);
        if (hyper)
            GameBase.gi.WriteByte(Defines.MZ_HYPERBLASTER | is_silenced);
        else
            GameBase.gi.WriteByte(Defines.MZ_BLASTER | is_silenced);
        GameBase.gi.multicast(ent.s.origin, Defines.MULTICAST_PVS);

        GameWeapon.PlayerNoise(ent, start, Defines.PNOISE_WEAPON);
    }
}