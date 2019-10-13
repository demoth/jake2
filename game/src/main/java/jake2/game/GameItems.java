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
// $Id: GameItems.java,v 1.4 2006-01-21 21:53:32 salomo Exp $

package jake2.game;


import jake2.qcommon.*;
import jake2.qcommon.util.Lib;
import jake2.qcommon.util.Math3D;
import jake2.qcommon.filesystem.QuakeFile;

import java.io.IOException;
import java.util.StringTokenizer;


public class GameItems {

    static gitem_armor_t jacketarmor_info = new gitem_armor_t(25, 50,
    .30f, .00f, GameDefines.ARMOR_JACKET);
    static gitem_armor_t combatarmor_info = new gitem_armor_t(50, 100,
    .60f, .30f, GameDefines.ARMOR_COMBAT);
    static gitem_armor_t bodyarmor_info = new gitem_armor_t(100, 200,
    .80f, .60f, GameDefines.ARMOR_BODY);
    private static int quad_drop_timeout_hack = 0;
    private static int jacket_armor_index;
    private static int combat_armor_index;
    private static int body_armor_index;
    private static int power_screen_index;
    private static int power_shield_index;
    
    private static EntThinkAdapter DoRespawn = new EntThinkAdapter() {
        public String getID() { return "do_respawn";}
        public boolean think(SubgameEntity ent) {
            if (ent.team != null) {
                SubgameEntity master;
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
        public String getID() { return "pickup_pack";}
        public boolean interact(SubgameEntity ent, SubgameEntity other) {
    
            gitem_t item;
            int index;

            client_persistant_t clPers = (other.getClient()).pers;
            if (clPers.max_bullets < 300)
                clPers.max_bullets = 300;
            if (clPers.max_shells < 200)
                clPers.max_shells = 200;
            if (clPers.max_rockets < 100)
                clPers.max_rockets = 100;
            if (clPers.max_grenades < 100)
                clPers.max_grenades = 100;
            if (clPers.max_cells < 300)
                clPers.max_cells = 300;
            if (clPers.max_slugs < 100)
                clPers.max_slugs = 100;
    
            item = FindItem("Bullets");
            if (item != null) {
                index = item.index;
                clPers.inventory[index] += item.quantity;
                if (clPers.inventory[index] > clPers.max_bullets)
                    clPers.inventory[index] = clPers.max_bullets;
            }
    
            item = FindItem("Shells");
            if (item != null) {
                index = item.index;
                clPers.inventory[index] += item.quantity;
                if (clPers.inventory[index] > clPers.max_shells)
                    clPers.inventory[index] = clPers.max_shells;
            }
    
            item = FindItem("Cells");
            if (item != null) {
                index = item.index;
                clPers.inventory[index] += item.quantity;
                if (clPers.inventory[index] > clPers.max_cells)
                    clPers.inventory[index] = clPers.max_cells;
            }
    
            item = FindItem("Grenades");
            if (item != null) {
                index = item.index;
                clPers.inventory[index] += item.quantity;
                if (clPers.inventory[index] > clPers.max_grenades)
                    clPers.inventory[index] = clPers.max_grenades;
            }
    
            item = FindItem("Rockets");
            if (item != null) {
                index = item.index;
                clPers.inventory[index] += item.quantity;
                if (clPers.inventory[index] > clPers.max_rockets)
                    clPers.inventory[index] = clPers.max_rockets;
            }
    
            item = FindItem("Slugs");
            if (item != null) {
                index = item.index;
                clPers.inventory[index] += item.quantity;
                if (clPers.inventory[index] > clPers.max_slugs)
                    clPers.inventory[index] = clPers.max_slugs;
            }
    
            if (0 == (ent.spawnflags & GameDefines.DROPPED_ITEM)
                    && (GameBase.deathmatch.value != 0))
                SetRespawn(ent, ent.item.quantity);
    
            return true;
        }
    };
    final static EntInteractAdapter Pickup_Health = new EntInteractAdapter() {
        public String getID() { return "pickup_health";}
        public boolean interact(SubgameEntity ent, SubgameEntity other) {
    
            if (0 == (ent.style & GameDefines.HEALTH_IGNORE_MAX))
                if (other.health >= other.max_health)
                    return false;
    
            other.health += ent.count;
    
            if (0 == (ent.style & GameDefines.HEALTH_IGNORE_MAX)) {
                if (other.health > other.max_health)
                    other.health = other.max_health;
            }
    
            if (0 != (ent.style & GameDefines.HEALTH_TIMED)) {
                ent.think = GameUtil.MegaHealth_think;
                ent.nextthink = GameBase.level.time + 5f;
                ent.setOwner(other);
                ent.flags |= GameDefines.FL_RESPAWN;
                ent.svflags |= Defines.SVF_NOCLIENT;
                ent.solid = Defines.SOLID_NOT;
            } else {
                if (!((ent.spawnflags & GameDefines.DROPPED_ITEM) != 0)
                        && (GameBase.deathmatch.value != 0))
                    SetRespawn(ent, 30);
            }
    
            return true;
        }
    
    };
    static EntTouchAdapter Touch_Item = new EntTouchAdapter() {
        public String getID() { return "touch_item";}
        public void touch(SubgameEntity ent, SubgameEntity other, cplane_t plane,
                csurface_t surf) {
            boolean taken;
    
            if (ent.classname.equals("item_breather"))
                taken = false;

            gclient_t client = other.getClient();
            if (client == null)
                return;
            if (other.health < 1)
                return; // dead people can't pickup
            if (ent.item.pickup == null)
                return; // not a grabbable item?
    
            taken = ent.item.pickup.interact(ent, other);
    
            if (taken) {
                // flash the screen
                client.bonus_alpha = 0.25f;
    
                // show icon and name on status bar
                client.getPlayerState().stats[Defines.STAT_PICKUP_ICON] = (short) GameBase.gi
                        .imageindex(ent.item.icon);
                client.getPlayerState().stats[Defines.STAT_PICKUP_STRING] = (short) (Defines.CS_ITEMS + ent.item.index);
                client.pickup_msg_time = GameBase.level.time + 3.0f;
    
                // change selected item
                if (ent.item.use != null)
                    client.pers.selected_item = client.getPlayerState().stats[Defines.STAT_SELECTED_ITEM] = (short) ent.item.index;
    
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
    
            if (0 == (ent.spawnflags & GameDefines.ITEM_TARGETS_USED)) {
                GameUtil.G_UseTargets(ent, other);
                ent.spawnflags |= GameDefines.ITEM_TARGETS_USED;
            }
    
            if (!taken)
                return;
            
            Com.dprintln("Picked up:" + ent.classname);
    
            if (!((GameBase.coop.value != 0) && (ent.item.flags & GameDefines.IT_STAY_COOP) != 0)
                    || 0 != (ent.spawnflags & (GameDefines.DROPPED_ITEM | GameDefines.DROPPED_PLAYER_ITEM))) {
                if ((ent.flags & GameDefines.FL_RESPAWN) != 0)
                    ent.flags &= ~GameDefines.FL_RESPAWN;
                else
                    GameUtil.G_FreeEdict(ent);
            }
        }
    };
    private static EntTouchAdapter drop_temp_touch = new EntTouchAdapter() {
        public String getID() { return "drop_temp_touch";}
        public void touch(SubgameEntity ent, SubgameEntity other, cplane_t plane,
                csurface_t surf) {
            if (other == ent.getOwner())
                return;
    
            Touch_Item.touch(ent, other, plane, surf);
        }
    };
    private static EntThinkAdapter drop_make_touchable = new EntThinkAdapter() {
        public String getID() { return "drop_make_touchable";}
        public boolean think(SubgameEntity ent) {
            ent.touch = Touch_Item;
            if (GameBase.deathmatch.value != 0) {
                ent.nextthink = GameBase.level.time + 29;
                ent.think = GameUtil.G_FreeEdictA;
            }
            return false;
        }
    };
    static ItemUseAdapter Use_Quad = new ItemUseAdapter() {
        public String getID() { return "use_quad";}    
        public void use(SubgameEntity ent, gitem_t item) {
            int timeout;

            gclient_t client = ent.getClient();
            client.pers.inventory[item.index]--;
            GameUtil.ValidateSelectedItem(ent);
    
            if (quad_drop_timeout_hack != 0) {
                timeout = quad_drop_timeout_hack;
                quad_drop_timeout_hack = 0;
            } else {
                timeout = 300;
            }
    
            if (client.quad_framenum > GameBase.level.framenum)
                client.quad_framenum += timeout;
            else
                client.quad_framenum = GameBase.level.framenum + timeout;
    
            GameBase.gi.sound(ent, Defines.CHAN_ITEM, GameBase.gi
                    .soundindex("items/damage.wav"), 1, Defines.ATTN_NORM, 0);
        }
    };
    
    static ItemUseAdapter Use_Invulnerability = new ItemUseAdapter() {
        public String getID() { return "use_invulnerability";}
        public void use(SubgameEntity ent, gitem_t item) {
            gclient_t client = ent.getClient();
            client.pers.inventory[item.index]--;
            GameUtil.ValidateSelectedItem(ent);
    
            if (client.invincible_framenum > GameBase.level.framenum)
                client.invincible_framenum += 300;
            else
                client.invincible_framenum = GameBase.level.framenum + 300;
    
            GameBase.gi.sound(ent, Defines.CHAN_ITEM, GameBase.gi
                    .soundindex("items/protect.wav"), 1, Defines.ATTN_NORM, 0);
        }
    };
    static ItemUseAdapter Use_Breather = new ItemUseAdapter() {
        public String getID() { return "use_breather";}
        public void use(SubgameEntity ent, gitem_t item) {
            gclient_t client = ent.getClient();
            client.pers.inventory[item.index]--;
    
            GameUtil.ValidateSelectedItem(ent);
    
            if (client.breather_framenum > GameBase.level.framenum)
                client.breather_framenum += 300;
            else
                client.breather_framenum = GameBase.level.framenum + 300;
    
            GameBase.gi.sound(ent, Defines.CHAN_ITEM, GameBase.gi
                    .soundindex("items/damage.wav"), 1, Defines.ATTN_NORM, 0);
        }
    };
    static ItemUseAdapter Use_Envirosuit = new ItemUseAdapter() {
        public String getID() { return "use_envirosuit";}
        public void use(SubgameEntity ent, gitem_t item) {
            gclient_t client = ent.getClient();
            client.pers.inventory[item.index]--;
            GameUtil.ValidateSelectedItem(ent);
    
            if (client.enviro_framenum > GameBase.level.framenum)
                client.enviro_framenum += 300;
            else
                client.enviro_framenum = GameBase.level.framenum + 300;
    
            GameBase.gi.sound(ent, Defines.CHAN_ITEM, GameBase.gi
                    .soundindex("items/damage.wav"), 1, Defines.ATTN_NORM, 0);
        }
    };
    static ItemUseAdapter Use_Silencer = new ItemUseAdapter() {
        public String getID() { return "use_silencer";}
        public void use(SubgameEntity ent, gitem_t item) {

            gclient_t client = ent.getClient();
            client.pers.inventory[item.index]--;
            GameUtil.ValidateSelectedItem(ent);
            client.silencer_shots += 30;
    
            GameBase.gi.sound(ent, Defines.CHAN_ITEM, GameBase.gi
                    .soundindex("items/damage.wav"), 1, Defines.ATTN_NORM, 0);
        }
    };
    static EntInteractAdapter Pickup_Key = new EntInteractAdapter() {
        public String getID() { return "pickup_key";}
        public boolean interact(SubgameEntity ent, SubgameEntity other) {
            gclient_t client = other.getClient();
            if (GameBase.coop.value != 0) {
                if ("key_power_cube".equals(ent.classname)) {
                    if ((client.pers.power_cubes & ((ent.spawnflags & 0x0000ff00) >> 8)) != 0)
                        return false;
                    client.pers.inventory[ent.item.index]++;
                    client.pers.power_cubes |= ((ent.spawnflags & 0x0000ff00) >> 8);
                } else {
                    if (client.pers.inventory[ent.item.index] != 0)
                        return false;
                    client.pers.inventory[ent.item.index] = 1;
                }
                return true;
            }
            client.pers.inventory[ent.item.index]++;
            return true;
        }
    };
    static EntInteractAdapter Pickup_Ammo = new EntInteractAdapter() {
        public String getID() { return "pickup_ammo";}
        public boolean interact(SubgameEntity ent, SubgameEntity other) {
            int oldcount;
            int count;
            boolean weapon;
    
            weapon = (ent.item.flags & GameDefines.IT_WEAPON) != 0;
            if ((weapon)
                    && ((int) GameBase.dmflags.value & Defines.DF_INFINITE_AMMO) != 0)
                count = 1000;
            else if (ent.count != 0)
                count = ent.count;
            else
                count = ent.item.quantity;

            gclient_t client = other.getClient();
            oldcount = client.pers.inventory[ent.item.index];
    
            if (!Add_Ammo(other, ent.item, count))
                return false;
    
            if (weapon && 0 == oldcount) {
                if (client.pers.weapon != ent.item
                        && (0 == GameBase.deathmatch.value || client.pers.weapon == FindItem("blaster")))
                    client.newweapon = ent.item;
            }
    
            if (0 == (ent.spawnflags & (GameDefines.DROPPED_ITEM | GameDefines.DROPPED_PLAYER_ITEM))
                    && (GameBase.deathmatch.value != 0))
                SetRespawn(ent, 30);
            return true;
        }
    };
    static EntInteractAdapter Pickup_Armor = new EntInteractAdapter() {
        public String getID() { return "pickup_armor";}
        public boolean interact(SubgameEntity ent, SubgameEntity other) {
            int old_armor_index;
            gitem_armor_t oldinfo;
            gitem_armor_t newinfo;
            int newcount;
            float salvage;
            float salvagecount;
    
            // get info on new armor
            newinfo = (gitem_armor_t) ent.item.info;
    
            old_armor_index = ArmorIndex(other);
    
            // handle armor shards specially
            gclient_t client = other.getClient();
            if (ent.item.tag == GameDefines.ARMOR_SHARD) {
                if (0 == old_armor_index)
                    client.pers.inventory[jacket_armor_index] = 2;
                else
                    client.pers.inventory[old_armor_index] += 2;
            }
    
            // if player has no armor, just use it
            else if (0 == old_armor_index) {
                client.pers.inventory[ent.item.index] = newinfo.base_count;
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
                    salvagecount = salvage * client.pers.inventory[old_armor_index];
                    newcount = (int) (newinfo.base_count + salvagecount);
                    if (newcount > newinfo.max_count)
                        newcount = newinfo.max_count;
    
                    // zero count of old armor so it goes away
                    client.pers.inventory[old_armor_index] = 0;
    
                    // change armor to new item with computed value
                    client.pers.inventory[ent.item.index] = newcount;
                } else {
                    // calc new armor values
                    salvage = newinfo.normal_protection
                            / oldinfo.normal_protection;
                    salvagecount =  salvage * newinfo.base_count;
                    newcount = (int) (client.pers.inventory[old_armor_index] + salvagecount);
                    if (newcount > oldinfo.max_count)
                        newcount = oldinfo.max_count;
    
                    // if we're already maxed out then we don't need the new
                    // armor
                    if (client.pers.inventory[old_armor_index] >= newcount)
                        return false;
    
                    // update current armor value
                    client.pers.inventory[old_armor_index] = newcount;
                }
            }
    
            if (0 == (ent.spawnflags & GameDefines.DROPPED_ITEM)
                    && (GameBase.deathmatch.value != 0))
                SetRespawn(ent, 20);
    
            return true;
        }
    };
    static EntInteractAdapter Pickup_PowerArmor = new EntInteractAdapter() {
        public String getID() { return "pickup_powerarmor";}
        public boolean interact(SubgameEntity ent, SubgameEntity other) {
    
            int quantity;

            gclient_t client = other.getClient();
            quantity = client.pers.inventory[ent.item.index];

            client.pers.inventory[ent.item.index]++;
    
            if (GameBase.deathmatch.value != 0) {
                if (0 == (ent.spawnflags & GameDefines.DROPPED_ITEM))
                    SetRespawn(ent, ent.item.quantity);
                // auto-use for DM only if we didn't already have one
                if (0 == quantity)
                    ent.item.use.use(other, ent.item);
            }
            return true;
        }
    };
    static EntInteractAdapter Pickup_Powerup = new EntInteractAdapter() {
        public String getID() { return "pickup_powerup";}
        public boolean interact(SubgameEntity ent, SubgameEntity other) {
            int quantity;

            gclient_t client = other.getClient();
            quantity = client.pers.inventory[ent.item.index];
            if ((GameBase.skill.value == 1 && quantity >= 2)
                    || (GameBase.skill.value >= 2 && quantity >= 1))
                return false;
    
            if ((GameBase.coop.value != 0)
                    && (ent.item.flags & GameDefines.IT_STAY_COOP) != 0
                    && (quantity > 0))
                return false;

            client.pers.inventory[ent.item.index]++;
    
            if (GameBase.deathmatch.value != 0) {
                if (0 == (ent.spawnflags & GameDefines.DROPPED_ITEM))
                    SetRespawn(ent, ent.item.quantity);
                if (((int) GameBase.dmflags.value & Defines.DF_INSTANT_ITEMS) != 0
                        || ((ent.item.use == Use_Quad) && 0 != (ent.spawnflags & GameDefines.DROPPED_PLAYER_ITEM))) {
                    if ((ent.item.use == Use_Quad)
                            && 0 != (ent.spawnflags & GameDefines.DROPPED_PLAYER_ITEM))
                        quad_drop_timeout_hack = (int) ((ent.nextthink - GameBase.level.time) / Defines.FRAMETIME);
    
                    ent.item.use.use(other, ent.item);
                }
            }
    
            return true;
        }
    };
    static EntInteractAdapter Pickup_Adrenaline = new EntInteractAdapter() {
        public String getID() { return "pickup_adrenaline";}
        public boolean interact(SubgameEntity ent, SubgameEntity other) {
            if (GameBase.deathmatch.value == 0)
                other.max_health += 1;
    
            if (other.health < other.max_health)
                other.health = other.max_health;
    
            if (0 == (ent.spawnflags & GameDefines.DROPPED_ITEM)
                    && (GameBase.deathmatch.value != 0))
                SetRespawn(ent, ent.item.quantity);
    
            return true;
    
        }
    };
    static EntInteractAdapter Pickup_AncientHead = new EntInteractAdapter() {
        public String getID() { return "pickup_ancienthead";}
        public boolean interact(SubgameEntity ent, SubgameEntity other) {
            other.max_health += 2;
    
            if (0 == (ent.spawnflags & GameDefines.DROPPED_ITEM)
                    && (GameBase.deathmatch.value != 0))
                SetRespawn(ent, ent.item.quantity);
    
            return true;
        }
    };
    static EntInteractAdapter Pickup_Bandolier = new EntInteractAdapter() {
        public String getID() { return "pickup_bandolier";}
        public boolean interact(SubgameEntity ent, SubgameEntity other) {
            gitem_t item;
            int index;

            gclient_t client = other.getClient();
            if (client.pers.max_bullets < 250)
                client.pers.max_bullets = 250;
            if (client.pers.max_shells < 150)
                client.pers.max_shells = 150;
            if (client.pers.max_cells < 250)
                client.pers.max_cells = 250;
            if (client.pers.max_slugs < 75)
                client.pers.max_slugs = 75;
    
            item = FindItem("Bullets");
            if (item != null) {
                index = item.index;
                client.pers.inventory[index] += item.quantity;
                if (client.pers.inventory[index] > client.pers.max_bullets)
                    client.pers.inventory[index] = client.pers.max_bullets;
            }
    
            item = FindItem("Shells");
            if (item != null) {
                index = item.index;
                client.pers.inventory[index] += item.quantity;
                if (client.pers.inventory[index] > client.pers.max_shells)
                    client.pers.inventory[index] = client.pers.max_shells;
            }
    
            if (0 == (ent.spawnflags & GameDefines.DROPPED_ITEM)
                    && (GameBase.deathmatch.value != 0))
                SetRespawn(ent, ent.item.quantity);
    
            return true;
    
        }
    };
    static ItemDropAdapter Drop_Ammo = new ItemDropAdapter() {
        public String getID() { return "drop_ammo";}
        public void drop(SubgameEntity ent, gitem_t item) {
            int index;

            index = item.index;
            SubgameEntity dropped = Drop_Item(ent, item);
            gclient_t client = ent.getClient();
            if (client.pers.inventory[index] >= item.quantity)
                dropped.count = item.quantity;
            else
                dropped.count = client.pers.inventory[index];
    
            if (client.pers.weapon != null
                    && client.pers.weapon.tag == GameDefines.AMMO_GRENADES
                    && item.tag == GameDefines.AMMO_GRENADES
                    && client.pers.inventory[index] - dropped.count <= 0) {
                GameBase.gi.cprintf(ent, Defines.PRINT_HIGH,
                        "Can't drop current weapon\n");
                GameUtil.G_FreeEdict(dropped);
                return;
            }
    
            client.pers.inventory[index] -= dropped.count;
            ValidateSelectedItem(ent);
        }
    };
    static ItemDropAdapter Drop_General = new ItemDropAdapter() {
        public String getID() { return "drop_general";}
        public void drop(SubgameEntity ent, gitem_t item) {
            Drop_Item(ent, item);
            gclient_t client = ent.getClient();
            client.pers.inventory[item.index]--;
            ValidateSelectedItem(ent);
        }
    };
    
    static ItemDropAdapter Drop_PowerArmor = new ItemDropAdapter() {
        public String getID() { return "drop_powerarmor";}
        public void drop(SubgameEntity ent, gitem_t item) {
            gclient_t client = ent.getClient();
            if (0 != (ent.flags & GameDefines.FL_POWER_ARMOR)
                    && (client.pers.inventory[item.index] == 1))
                Use_PowerArmor.use(ent, item);
            Drop_General.drop(ent, item);
        }
    };
    
    private static EntThinkAdapter droptofloor = new EntThinkAdapter() {
        public String getID() { return "drop_to_floor";}
        public boolean think(SubgameEntity ent) {
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
            ent.movetype = GameDefines.MOVETYPE_TOSS;
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
                ent.flags &= ~GameDefines.FL_TEAMSLAVE;
                ent.chain = ent.teamchain;
                ent.teamchain = null;
    
                ent.svflags |= Defines.SVF_NOCLIENT;
                ent.solid = Defines.SOLID_NOT;
                if (ent == ent.teammaster) {
                    ent.nextthink = GameBase.level.time + Defines.FRAMETIME;
                    ent.think = DoRespawn;
                }
            }
    
            if ((ent.spawnflags & GameDefines.ITEM_NO_TOUCH) != 0) {
                ent.solid = Defines.SOLID_BBOX;
                ent.touch = null;
                ent.s.effects &= ~Defines.EF_ROTATE;
                ent.s.renderfx &= ~Defines.RF_GLOW;
            }
    
            if ((ent.spawnflags & GameDefines.ITEM_TRIGGER_SPAWN) != 0) {
                ent.svflags |= Defines.SVF_NOCLIENT;
                ent.solid = Defines.SOLID_NOT;
                ent.use = Use_Item;
            }
    
            GameBase.gi.linkentity(ent);
            return true;
        }
    };
    static ItemUseAdapter Use_PowerArmor = new ItemUseAdapter() {
        public String getID() { return "use_powerarmor";}
        public void use(SubgameEntity ent, gitem_t item) {
            int index;
    
            if ((ent.flags & GameDefines.FL_POWER_ARMOR) != 0) {
                ent.flags &= ~GameDefines.FL_POWER_ARMOR;
                GameBase.gi
                        .sound(ent, Defines.CHAN_AUTO, GameBase.gi
                                .soundindex("misc/power2.wav"), 1,
                                Defines.ATTN_NORM, 0);
            } else {
                index = FindItem("cells").index;
                gclient_t client = ent.getClient();
                if (0 == client.pers.inventory[index]) {
                    GameBase.gi.cprintf(ent, Defines.PRINT_HIGH,
                            "No cells for power armor.\n");
                    return;
                }
                ent.flags |= GameDefines.FL_POWER_ARMOR;
                GameBase.gi
                        .sound(ent, Defines.CHAN_AUTO, GameBase.gi
                                .soundindex("misc/power1.wav"), 1,
                                Defines.ATTN_NORM, 0);
            }
        }
    };
    private static EntUseAdapter Use_Item = new EntUseAdapter() {
        public String getID() { return "use_item";}
        public void use(SubgameEntity ent, SubgameEntity other, SubgameEntity activator) {
            ent.svflags &= ~Defines.SVF_NOCLIENT;
            ent.use = null;
    
            if ((ent.spawnflags & GameDefines.ITEM_NO_TOUCH) != 0) {
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
    static gitem_t GetItemByIndex(int index) {
        if (index == 0 || index >= GameBase.game.num_items)
            return null;
    
        return GameItemList.itemlist[index];
    }

    /*
     * =============== FindItemByClassname
     * 
     * ===============
     */
    static gitem_t FindItemByClassname(String classname) {
    
        for (int i = 1; i < GameBase.game.num_items; i++) {
            gitem_t it = GameItemList.itemlist[i];
    
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
            gitem_t it = GameItemList.itemlist[i];
    
            if (it.pickup_name == null)
                continue;
            if (it.pickup_name.equalsIgnoreCase(pickup_name))
                return it;
        }
        Com.Println("Item not found:" + pickup_name);
        return null;
    }

    static void SetRespawn(SubgameEntity ent, float delay) {
        ent.flags |= GameDefines.FL_RESPAWN;
        ent.svflags |= Defines.SVF_NOCLIENT;
        ent.solid = Defines.SOLID_NOT;
        ent.nextthink = GameBase.level.time + delay;
        ent.think = DoRespawn;
        GameBase.gi.linkentity(ent);
    }

    static SubgameEntity Drop_Item(SubgameEntity ent, gitem_t item) {
        float[] forward = { 0, 0, 0 };
        float[] right = { 0, 0, 0 };
        float[] offset = { 0, 0, 0 };

        SubgameEntity dropped = GameUtil.G_Spawn();
    
        dropped.classname = item.classname;
        dropped.item = item;
        dropped.spawnflags = GameDefines.DROPPED_ITEM;
        dropped.s.effects = item.world_model_flags;
        dropped.s.renderfx = Defines.RF_GLOW;
        Math3D.VectorSet(dropped.mins, -15, -15, -15);
        Math3D.VectorSet(dropped.maxs, 15, 15, 15);
        GameBase.gi.setmodel(dropped, dropped.item.world_model);
        dropped.solid = Defines.SOLID_TRIGGER;
        dropped.movetype = GameDefines.MOVETYPE_TOSS;
    
        dropped.touch = drop_temp_touch;
    
        dropped.setOwner(ent);

        gclient_t client = ent.getClient();
        if (client != null) {
            trace_t trace;
    
            Math3D.AngleVectors(client.v_angle, forward, right, null);
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

    static void Use_Item(SubgameEntity ent, SubgameEntity other, SubgameEntity activator) {
        ent.svflags &= ~Defines.SVF_NOCLIENT;
        ent.use = null;
    
        if ((ent.spawnflags & GameDefines.ITEM_NO_TOUCH) != 0) {
            ent.solid = Defines.SOLID_BBOX;
            ent.touch = null;
        } else {
            ent.solid = Defines.SOLID_TRIGGER;
            ent.touch = Touch_Item;
        }
    
        GameBase.gi.linkentity(ent);
    }

    static int PowerArmorType(SubgameEntity ent) {
        gclient_t client = ent.getClient();
        if (client == null)
            return GameDefines.POWER_ARMOR_NONE;
    
        if (0 == (ent.flags & GameDefines.FL_POWER_ARMOR))
            return GameDefines.POWER_ARMOR_NONE;
    
        if (client.pers.inventory[power_shield_index] > 0)
            return GameDefines.POWER_ARMOR_SHIELD;
    
        if (client.pers.inventory[power_screen_index] > 0)
            return GameDefines.POWER_ARMOR_SCREEN;
    
        return GameDefines.POWER_ARMOR_NONE;
    }

    static int ArmorIndex(SubgameEntity ent) {
        gclient_t client = ent.getClient();
        if (client == null)
            return 0;
    
        if (client.pers.inventory[jacket_armor_index] > 0)
            return jacket_armor_index;
    
        if (client.pers.inventory[combat_armor_index] > 0)
            return combat_armor_index;
    
        if (client.pers.inventory[body_armor_index] > 0)
            return body_armor_index;
    
        return 0;
    }

    public static boolean Pickup_PowerArmor(SubgameEntity ent, SubgameEntity other) {
        int quantity;

        gclient_t client = other.getClient();
        quantity = client.pers.inventory[ent.item.index];

        client.pers.inventory[ent.item.index]++;
    
        if (GameBase.deathmatch.value != 0) {
            if (0 == (ent.spawnflags & GameDefines.DROPPED_ITEM))
                SetRespawn(ent, ent.item.quantity);
            // auto-use for DM only if we didn't already have one
            if (0 == quantity)
                ent.item.use.use(other, ent.item);
        }
    
        return true;
    }

    static boolean Add_Ammo(SubgameEntity ent, gitem_t item, int count) {
        int index;
        int max;

        gclient_t client = ent.getClient();
        if (client == null)
            return false;
    
        if (item.tag == GameDefines.AMMO_BULLETS)
            max = client.pers.max_bullets;
        else if (item.tag == GameDefines.AMMO_SHELLS)
            max = client.pers.max_shells;
        else if (item.tag == GameDefines.AMMO_ROCKETS)
            max = client.pers.max_rockets;
        else if (item.tag == GameDefines.AMMO_GRENADES)
            max = client.pers.max_grenades;
        else if (item.tag == GameDefines.AMMO_CELLS)
            max = client.pers.max_cells;
        else if (item.tag == GameDefines.AMMO_SLUGS)
            max = client.pers.max_slugs;
        else
            return false;

        index = item.index;
    
        if (client.pers.inventory[index] == max)
            return false;
    
        client.pers.inventory[index] += count;
    
        if (client.pers.inventory[index] > max)
            client.pers.inventory[index] = max;
    
        return true;
    }

    static void InitItems() {
        GameBase.game.num_items = GameItemList.itemlist.length - 1;
    }

    /*
     * =============== SetItemNames
     * 
     * Called by worldspawn ===============
     */
    static void SetItemNames() {
        int i;
        gitem_t it;
    
        for (i = 1; i < GameBase.game.num_items; i++) {
            it = GameItemList.itemlist[i];
            GameBase.gi.configstring(Defines.CS_ITEMS + i, it.pickup_name);
        }

        jacket_armor_index = FindItem("Jacket Armor").index;
        combat_armor_index = FindItem("Combat Armor").index;
        body_armor_index = FindItem("Body Armor").index;
        power_screen_index = FindItem("Power Screen").index;
        power_shield_index = FindItem("Power Shield").index;
    }

    static void SelectNextItem(SubgameEntity ent, int itflags) {
        int i, index;
        gitem_t it;

        gclient_t cl = ent.getClient();
    
        if (cl.chase_target != null) {
            GameChase.ChaseNext(ent);
            return;
        }
    
        // scan for the next valid one
        for (i = 1; i <= Defines.MAX_ITEMS; i++) {
            index = (cl.pers.selected_item + i) % Defines.MAX_ITEMS;
            if (0 == cl.pers.inventory[index])
                continue;
            it = GameItemList.itemlist[index];
            if (it.use == null)
                continue;
            if (0 == (it.flags & itflags))
                continue;
    
            cl.pers.selected_item = index;
            return;
        }
    
        cl.pers.selected_item = -1;
    }

    static void SelectPrevItem(SubgameEntity ent, int itflags) {
        int i, index;
        gitem_t it;

        gclient_t cl = ent.getClient();
    
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
            it = GameItemList.itemlist[index];
            if (null == it.use)
                continue;
            if (0 == (it.flags & itflags))
                continue;
    
            cl.pers.selected_item = index;
            return;
        }
    
        cl.pers.selected_item = -1;
    }

    /**
     * Precaches all data needed for a given item:
     *  - pickup_sound,
     *  - world_model,
     *  - view_model,
     *  - icon,
     *  - ammo related resources
     *  - other resources, specified by item.precaches
     *
     * This will be called for each item spawned in a level, and for each item in each client's inventory.
     */
    static void PrecacheItem(gitem_t it) {

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
            gitem_t ammo = FindItem(it.ammo);
            if (ammo != it)
                PrecacheItem(ammo);
        }
    
        // parse the space separated precache string for other items
        String precacheString = it.precaches;
        if (precacheString == null || precacheString.length() != 0)
            return;
    
        StringTokenizer tk = new StringTokenizer(precacheString);
    
        while (tk.hasMoreTokens()) {
            String file = tk.nextToken();

            int len = file.length();

            if (len >= Defines.MAX_QPATH || len < 5)
                GameBase.gi.error("PrecacheItem: it.classname has bad precache string: " + precacheString);
    
            // determine type based on extension
            if (file.endsWith("md2"))
                GameBase.gi.modelindex(file);
            else if (file.endsWith("sp2"))
                GameBase.gi.modelindex(file);
            else if (file.endsWith("wav"))
                GameBase.gi.soundindex(file);
            else if (file.endsWith("pcx"))
                GameBase.gi.imageindex(file);
            else
                GameBase.gi.error("PrecacheItem: bad precache string: " + file);
        }
    }

    /**
     * Sets the clipping size and plants the object on the floor.
     * 
     * Items can't be immediately dropped to floor, because they might be on an
     * entity that hasn't spawned yet.
     */
    static void SpawnItem(SubgameEntity ent, gitem_t item) {
        PrecacheItem(item);
    
        if (ent.spawnflags != 0) {
            if (!"key_power_cube".equals(ent.classname)) {
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
                if ((item.flags == GameDefines.IT_AMMO)
                        || ("weapon_bfg".equals(ent.classname))) {
                    GameUtil.G_FreeEdict(ent);
                    return;
                }
            }
        }
    
        if (GameBase.coop.value != 0
                && ("key_power_cube".equals(ent.classname))) {
            ent.spawnflags |= (1 << (8 + GameBase.level.power_cubes));
            GameBase.level.power_cubes++;
        }
    
        // don't let them drop items that stay in a coop game
        if ((GameBase.coop.value != 0)
                && (item.flags & GameDefines.IT_STAY_COOP) != 0) {
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
    static void SP_item_health(SubgameEntity self) {
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
    static void SP_item_health_small(SubgameEntity self) {
        if (GameBase.deathmatch.value != 0
                && ((int) GameBase.dmflags.value & Defines.DF_NO_HEALTH) != 0) {
            GameUtil.G_FreeEdict(self);
            return;
        }
    
        self.model = "models/items/healing/stimpack/tris.md2";
        self.count = 2;
        SpawnItem(self, FindItem("Health"));
        self.style = GameDefines.HEALTH_IGNORE_MAX;
        GameBase.gi.soundindex("items/s_health.wav");
    }

    /*
     * QUAKED item_health_large (.3 .3 1) (-16 -16 -16) (16 16 16)
     */
    static void SP_item_health_large(SubgameEntity self) {
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
    static void SP_item_health_mega(SubgameEntity self) {
        if (GameBase.deathmatch.value != 0
                && ((int) GameBase.dmflags.value & Defines.DF_NO_HEALTH) != 0) {
            GameUtil.G_FreeEdict(self);
            return;
        }
    
        self.model = "models/items/mega_h/tris.md2";
        self.count = 100;
        SpawnItem(self, FindItem("Health"));
        GameBase.gi.soundindex("items/m_health.wav");
        self.style = GameDefines.HEALTH_IGNORE_MAX | GameDefines.HEALTH_TIMED;
    }

    /*
     * =============== 
     * Touch_Item 
     * ===============
     */
    static void Touch_Item(SubgameEntity ent, SubgameEntity other, cplane_t plane,
                           csurface_t surf) {
        boolean taken;

        // freed edicts have not items.
        gclient_t client = other.getClient();
        if (client == null || ent.item == null)
            return;
        if (other.health < 1)
            return; // dead people can't pickup
        if (ent.item.pickup == null)
            return; // not a grabbable item?
    
        taken = ent.item.pickup.interact(ent, other);
    
        if (taken) {
            // flash the screen
            client.bonus_alpha = 0.25f;
    
            // show icon and name on status bar
            client.getPlayerState().stats[Defines.STAT_PICKUP_ICON] = (short) GameBase.gi
                    .imageindex(ent.item.icon);
            client.getPlayerState().stats[Defines.STAT_PICKUP_STRING] = (short) (Defines.CS_ITEMS + ent.item.index);
            client.pickup_msg_time = GameBase.level.time + 3.0f;
    
            // change selected item
            if (ent.item.use != null)
                client.pers.selected_item = client.getPlayerState().stats[Defines.STAT_SELECTED_ITEM] = (short) ent.item.index;
    
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
    
        if (0 == (ent.spawnflags & GameDefines.ITEM_TARGETS_USED)) {
            GameUtil.G_UseTargets(ent, other);
            ent.spawnflags |= GameDefines.ITEM_TARGETS_USED;
        }
    
        if (!taken)
            return;
    
        if (!((GameBase.coop.value != 0) && (ent.item.flags & GameDefines.IT_STAY_COOP) != 0)
                || 0 != (ent.spawnflags & (GameDefines.DROPPED_ITEM | GameDefines.DROPPED_PLAYER_ITEM))) {
            if ((ent.flags & GameDefines.FL_RESPAWN) != 0)
                ent.flags &= ~GameDefines.FL_RESPAWN;
            else
                GameUtil.G_FreeEdict(ent);
        }
    }

    static void ValidateSelectedItem(SubgameEntity ent) {
        gclient_t cl = ent.getClient();

        if (cl.pers.inventory[cl.pers.selected_item] != 0)
            return; // valid

        SelectNextItem(ent, -1);
    }

    /** Writes an item reference. */
    public static void writeItem(QuakeFile f, gitem_t item) throws IOException {
        if (item == null)
            f.writeInt(-1);
        else
            f.writeInt(item.index);
    }

    /** Reads the item index and returns the game item. */
    public static gitem_t readItem(QuakeFile f) throws IOException {
        int ndx = f.readInt();
        if (ndx == -1)
            return null;
        else
            return GameItemList.itemlist[ndx];
    }
}
