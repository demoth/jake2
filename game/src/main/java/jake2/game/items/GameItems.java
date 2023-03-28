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

package jake2.game.items;


import jake2.game.*;
import jake2.game.adapters.*;
import jake2.game.components.Medkit;
import jake2.qcommon.Defines;
import jake2.qcommon.cplane_t;
import jake2.qcommon.csurface_t;
import jake2.qcommon.filesystem.QuakeFile;
import jake2.qcommon.trace_t;
import jake2.qcommon.util.Lib;
import jake2.qcommon.util.Math3D;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;


public class GameItems {

    static gitem_armor_t jacketarmor_info = new gitem_armor_t(25, 50,
            .30f, .00f);
    static gitem_armor_t combatarmor_info = new gitem_armor_t(50, 100,
            .60f, .30f);
    static gitem_armor_t bodyarmor_info = new gitem_armor_t(100, 200,
            .80f, .60f);

    public static final Map<String, gitem_armor_t> TYPES = Map.of(
            "jacket", jacketarmor_info,
            "combat", combatarmor_info,
            "body", bodyarmor_info
    );


    private static EntThinkAdapter DoRespawn = new EntThinkAdapter() {
        public String getID() {
            return "do_respawn";
        }

        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
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
            gameExports.gameImports.linkentity(ent);
    
            // send an effect
            ent.s.event = Defines.EV_ITEM_RESPAWN;
    
            return false;
        }
    };
    static EntInteractAdapter Pickup_Pack = new EntInteractAdapter() {
        public String getID() { return "pickup_pack";}
        public boolean interact(SubgameEntity ent, SubgameEntity other, GameExportsImpl gameExports) {

            GameItem item;
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
    
            item = FindItem("Bullets", gameExports);
            if (item != null) {
                index = item.index;
                clPers.inventory[index] += item.quantity;
                if (clPers.inventory[index] > clPers.max_bullets)
                    clPers.inventory[index] = clPers.max_bullets;
            }
    
            item = FindItem("Shells", gameExports);
            if (item != null) {
                index = item.index;
                clPers.inventory[index] += item.quantity;
                if (clPers.inventory[index] > clPers.max_shells)
                    clPers.inventory[index] = clPers.max_shells;
            }
    
            item = FindItem("Cells", gameExports);
            if (item != null) {
                index = item.index;
                clPers.inventory[index] += item.quantity;
                if (clPers.inventory[index] > clPers.max_cells)
                    clPers.inventory[index] = clPers.max_cells;
            }
    
            item = FindItem("Grenades", gameExports);
            if (item != null) {
                index = item.index;
                clPers.inventory[index] += item.quantity;
                if (clPers.inventory[index] > clPers.max_grenades)
                    clPers.inventory[index] = clPers.max_grenades;
            }
    
            item = FindItem("Rockets", gameExports);
            if (item != null) {
                index = item.index;
                clPers.inventory[index] += item.quantity;
                if (clPers.inventory[index] > clPers.max_rockets)
                    clPers.inventory[index] = clPers.max_rockets;
            }
    
            item = FindItem("Slugs", gameExports);
            if (item != null) {
                index = item.index;
                clPers.inventory[index] += item.quantity;
                if (clPers.inventory[index] > clPers.max_slugs)
                    clPers.inventory[index] = clPers.max_slugs;
            }
    
            if (0 == (ent.spawnflags & GameDefines.DROPPED_ITEM)
                    && (gameExports.gameCvars.deathmatch.value != 0))
                SetRespawn(ent, ent.item.quantity, gameExports);
    
            return true;
        }
    };

    private static EntThinkAdapter MegaHealth_think = new EntThinkAdapter() {
    	public String getID() { return "MegaHealth_think"; }
        public boolean think(SubgameEntity self, GameExportsImpl gameExports) {
            if (self.getOwner().health > self.getOwner().max_health) {
                self.think.nextTime = gameExports.level.time + 1;
                self.getOwner().health -= 1;
                return false;
            }

            if (!((self.spawnflags & GameDefines.DROPPED_ITEM) != 0)
                    && (gameExports.gameCvars.deathmatch.value != 0))
                SetRespawn(self, 20, gameExports);
            else
                gameExports.freeEntity(self);

            return false;
        }
    };

    final static EntInteractAdapter Pickup_Health = new EntInteractAdapter() {
        public String getID() { return "pickup_health";}
        public boolean interact(SubgameEntity self, SubgameEntity other, GameExportsImpl game) {
            Medkit medkit = (Medkit) self.components.get(Medkit.class.getName());

            if (medkit == null)
                return false;

            if (!medkit.getIgnoreMax())
                if (other.health >= other.max_health)
                    return false;
    
            other.health += medkit.getAmount();
    
            if (!medkit.getIgnoreMax()) {
                if (other.health > other.max_health)
                    other.health = other.max_health;
            }
    
            if (medkit.getTimed()) {
                self.think.action = MegaHealth_think;
                self.think.nextTime = game.level.time + 5f;
                self.setOwner(other);
                self.flags |= GameDefines.FL_RESPAWN;
                self.svflags |= Defines.SVF_NOCLIENT;
                self.solid = Defines.SOLID_NOT;
            } else {
                // mega-health is respawned in `MegaHealth_think`, only after it is expired
                if ((self.spawnflags & GameDefines.DROPPED_ITEM) == 0
                        && (game.gameCvars.deathmatch.value != 0))
                    SetRespawn(self, 30, game);
            }
    
            return true;
        }
    
    };
    public static EntTouchAdapter Touch_Item = new EntTouchAdapter() {
        public String getID() { return "touch_item";}
        public void touch(SubgameEntity ent, SubgameEntity other, cplane_t plane,
                          csurface_t surf, GameExportsImpl gameExports) {
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
    
            taken = ent.item.pickup.interact(ent, other, gameExports);
    
            if (taken) {
                // flash the screen
                client.bonus_alpha = 0.25f;
    
                // show icon and name on status bar
                client.getPlayerState().stats[Defines.STAT_PICKUP_ICON] = (short) gameExports.gameImports
                        .imageindex(ent.item.icon);
                client.getPlayerState().stats[Defines.STAT_PICKUP_STRING] = (short) (Defines.CS_ITEMS + ent.item.index);
                client.pickup_msg_time = gameExports.level.time + 3.0f;
    
                // change selected item
                if (ent.item.use != null)
                    client.pers.selected_item = client.getPlayerState().stats[Defines.STAT_SELECTED_ITEM] = (short) ent.item.index;

                // fixme: merge health sound with other items sounds (`pickup_sound`)
                final Object medkit = ent.components.get(Medkit.class.getName());
                if (medkit instanceof Medkit) {
                    gameExports.gameImports.sound(other, Defines.CHAN_ITEM, ((Medkit) medkit).getSoundIndex(), 1, Defines.ATTN_NORM, 0);
                } else if (ent.item.pickup_sound != null) {
                    gameExports.gameImports.sound(other, Defines.CHAN_ITEM, gameExports.gameImports.soundindex(ent.item.pickup_sound), 1, Defines.ATTN_NORM, 0);
                }
            }
    
            if (0 == (ent.spawnflags & GameDefines.ITEM_TARGETS_USED)) {
                GameUtil.G_UseTargets(ent, other, gameExports);
                ent.spawnflags |= GameDefines.ITEM_TARGETS_USED;
            }
    
            if (!taken)
                return;

            gameExports.gameImports.dprintf("Picked up:" + ent.classname + "\n");
    
            if (!((gameExports.gameCvars.coop.value != 0) && (ent.item.flags & GameDefines.IT_STAY_COOP) != 0)
                    || 0 != (ent.spawnflags & (GameDefines.DROPPED_ITEM | GameDefines.DROPPED_PLAYER_ITEM))) {
                if ((ent.flags & GameDefines.FL_RESPAWN) != 0)
                    ent.flags &= ~GameDefines.FL_RESPAWN;
                else
                    gameExports.freeEntity(ent);
            }
        }
    };
    private static EntTouchAdapter drop_temp_touch = new EntTouchAdapter() {
        public String getID() { return "drop_temp_touch";}
        public void touch(SubgameEntity ent, SubgameEntity other, cplane_t plane,
                          csurface_t surf, GameExportsImpl gameExports) {
            if (other == ent.getOwner())
                return;
    
            Touch_Item.touch(ent, other, plane, surf, gameExports);
        }
    };
    private static EntThinkAdapter drop_make_touchable = new EntThinkAdapter() {
        public String getID() { return "drop_make_touchable";}
        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
            ent.touch = Touch_Item;
            if (gameExports.gameCvars.deathmatch.value != 0) {
                ent.think.nextTime = gameExports.level.time + 29;
                ent.think.action = GameUtil.G_FreeEdictA;
            }
            return false;
        }
    };
    static ItemUseAdapter Use_Quad = new ItemUseAdapter() {
        public String getID() {
            return "use_quad";
        }

        public void use(SubgameEntity ent, GameItem item, GameExportsImpl gameExports) {
            int timeout;

            gclient_t client = ent.getClient();
            client.pers.inventory[item.index]--;
            GameUtil.ValidateSelectedItem(ent, gameExports);

            if (gameExports.quad_drop_timeout_hack != 0) {
                timeout = gameExports.quad_drop_timeout_hack;
                gameExports.quad_drop_timeout_hack = 0;
            } else {
                timeout = 300;
            }
    
            if (client.quad_framenum > gameExports.level.framenum)
                client.quad_framenum += timeout;
            else
                client.quad_framenum = gameExports.level.framenum + timeout;
    
            gameExports.gameImports.sound(ent, Defines.CHAN_ITEM, gameExports.gameImports
                    .soundindex("items/damage.wav"), 1, Defines.ATTN_NORM, 0);
        }
    };
    
    static ItemUseAdapter Use_Invulnerability = new ItemUseAdapter() {
        public String getID() {
            return "use_invulnerability";
        }

        public void use(SubgameEntity ent, GameItem item, GameExportsImpl gameExports) {
            gclient_t client = ent.getClient();
            client.pers.inventory[item.index]--;
            GameUtil.ValidateSelectedItem(ent, gameExports);

            if (client.invincible_framenum > gameExports.level.framenum)
                client.invincible_framenum += 300;
            else
                client.invincible_framenum = gameExports.level.framenum + 300;

            gameExports.gameImports.sound(ent, Defines.CHAN_ITEM, gameExports.gameImports
                    .soundindex("items/protect.wav"), 1, Defines.ATTN_NORM, 0);
        }
    };
    static ItemUseAdapter Use_Breather = new ItemUseAdapter() {
        public String getID() {
            return "use_breather";
        }

        public void use(SubgameEntity ent, GameItem item, GameExportsImpl gameExports) {
            gclient_t client = ent.getClient();
            client.pers.inventory[item.index]--;

            GameUtil.ValidateSelectedItem(ent, gameExports);

            if (client.breather_framenum > gameExports.level.framenum)
                client.breather_framenum += 300;
            else
                client.breather_framenum = gameExports.level.framenum + 300;
    
            gameExports.gameImports.sound(ent, Defines.CHAN_ITEM, gameExports.gameImports
                    .soundindex("items/damage.wav"), 1, Defines.ATTN_NORM, 0);
        }
    };
    static ItemUseAdapter Use_Envirosuit = new ItemUseAdapter() {
        public String getID() {
            return "use_envirosuit";
        }

        public void use(SubgameEntity ent, GameItem item, GameExportsImpl gameExports) {
            gclient_t client = ent.getClient();
            client.pers.inventory[item.index]--;
            GameUtil.ValidateSelectedItem(ent, gameExports);

            if (client.enviro_framenum > gameExports.level.framenum)
                client.enviro_framenum += 300;
            else
                client.enviro_framenum = gameExports.level.framenum + 300;

            gameExports.gameImports.sound(ent, Defines.CHAN_ITEM, gameExports.gameImports
                    .soundindex("items/damage.wav"), 1, Defines.ATTN_NORM, 0);
        }
    };
    static ItemUseAdapter Use_Silencer = new ItemUseAdapter() {
        public String getID() {
            return "use_silencer";
        }

        public void use(SubgameEntity ent, GameItem item, GameExportsImpl gameExports) {

            gclient_t client = ent.getClient();
            client.pers.inventory[item.index]--;
            GameUtil.ValidateSelectedItem(ent, gameExports);
            client.silencer_shots += 30;

            gameExports.gameImports.sound(ent, Defines.CHAN_ITEM, gameExports.gameImports
                    .soundindex("items/damage.wav"), 1, Defines.ATTN_NORM, 0);
        }
    };
    static EntInteractAdapter Pickup_Key = new EntInteractAdapter() {
        public String getID() { return "pickup_key";}
        public boolean interact(SubgameEntity ent, SubgameEntity other, GameExportsImpl gameExports) {
            gclient_t client = other.getClient();
            if (gameExports.gameCvars.coop.value != 0) {
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
        public boolean interact(SubgameEntity ent, SubgameEntity other, GameExportsImpl gameExports) {
            int oldcount;
            int count;
            boolean weapon;
    
            weapon = (ent.item.flags & GameDefines.IT_WEAPON) != 0;
            if ((weapon)
                    && ((int) gameExports.gameCvars.dmflags.value & Defines.DF_INFINITE_AMMO) != 0)
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
                        && (0 == gameExports.gameCvars.deathmatch.value || client.pers.weapon == FindItem("blaster", gameExports)))
                    client.newweapon = ent.item;
            }
    
            if (0 == (ent.spawnflags & (GameDefines.DROPPED_ITEM | GameDefines.DROPPED_PLAYER_ITEM))
                    && (gameExports.gameCvars.deathmatch.value != 0))
                SetRespawn(ent, 30, gameExports);
            return true;
        }
    };
    static EntInteractAdapter Pickup_Armor = new EntInteractAdapter() {
        public String getID() { return "pickup_armor";}
        public boolean interact(SubgameEntity ent, SubgameEntity other, GameExportsImpl gameExports) {
            gitem_armor_t oldinfo;
            int newcount;
            float salvage;
            float salvagecount;

            // get info on new armor
            gitem_armor_t newinfo = ent.item.info;

            int old_armor_index = ArmorIndex(other, gameExports);

            // handle armor shards specially
            gclient_t client = other.getClient();
            if ("item_armor_shard".equals(ent.item.classname)) {
                if (-1 == old_armor_index)
                    client.pers.inventory[gameExports.jacket_armor_index] = 2;
                else
                    client.pers.inventory[old_armor_index] += 2;
            }

            // if player has no armor, just use it
            else if (-1 == old_armor_index) {
                client.pers.inventory[ent.item.index] = newinfo.base_count;
            }

            // use the better armor
            else {
                // get info on old armor
                if (old_armor_index == gameExports.jacket_armor_index)
                    oldinfo = jacketarmor_info;

                else if (old_armor_index == gameExports.combat_armor_index)
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
                    && (gameExports.gameCvars.deathmatch.value != 0))
                SetRespawn(ent, 20, gameExports);
    
            return true;
        }
    };
    static EntInteractAdapter Pickup_PowerArmor = new EntInteractAdapter() {
        public String getID() { return "pickup_powerarmor";}
        public boolean interact(SubgameEntity ent, SubgameEntity other, GameExportsImpl gameExports) {
    
            int quantity;

            gclient_t client = other.getClient();
            quantity = client.pers.inventory[ent.item.index];

            client.pers.inventory[ent.item.index]++;
    
            if (gameExports.gameCvars.deathmatch.value != 0) {
                if (0 == (ent.spawnflags & GameDefines.DROPPED_ITEM))
                    SetRespawn(ent, ent.item.quantity, gameExports);
                // auto-use for DM only if we didn't already have one
                if (0 == quantity)
                    ent.item.use.use(other, ent.item, gameExports);
            }
            return true;
        }
    };
    static EntInteractAdapter Pickup_Powerup = new EntInteractAdapter() {
        public String getID() { return "pickup_powerup";}
        public boolean interact(SubgameEntity ent, SubgameEntity other, GameExportsImpl gameExports) {
            int quantity;

            gclient_t client = other.getClient();
            quantity = client.pers.inventory[ent.item.index];
            if ((gameExports.gameCvars.skill.value == 1 && quantity >= 2)
                    || (gameExports.gameCvars.skill.value >= 2 && quantity >= 1))
                return false;
    
            if ((gameExports.gameCvars.coop.value != 0)
                    && (ent.item.flags & GameDefines.IT_STAY_COOP) != 0
                    && (quantity > 0))
                return false;

            client.pers.inventory[ent.item.index]++;
    
            if (gameExports.gameCvars.deathmatch.value != 0) {
                if (0 == (ent.spawnflags & GameDefines.DROPPED_ITEM))
                    SetRespawn(ent, ent.item.quantity, gameExports);
                if (((int) gameExports.gameCvars.dmflags.value & Defines.DF_INSTANT_ITEMS) != 0
                        || ((ent.item.use == Use_Quad) && 0 != (ent.spawnflags & GameDefines.DROPPED_PLAYER_ITEM))) {
                    if ((ent.item.use == Use_Quad)
                            && 0 != (ent.spawnflags & GameDefines.DROPPED_PLAYER_ITEM))
                        gameExports.quad_drop_timeout_hack = (int) ((ent.think.nextTime - gameExports.level.time) / Defines.FRAMETIME);
    
                    ent.item.use.use(other, ent.item, gameExports);
                }
            }
    
            return true;
        }
    };
    static EntInteractAdapter Pickup_Adrenaline = new EntInteractAdapter() {
        public String getID() { return "pickup_adrenaline";}
        public boolean interact(SubgameEntity ent, SubgameEntity other, GameExportsImpl gameExports) {
            if (gameExports.gameCvars.deathmatch.value == 0)
                other.max_health += 1;
    
            if (other.health < other.max_health)
                other.health = other.max_health;
    
            if (0 == (ent.spawnflags & GameDefines.DROPPED_ITEM)
                    && (gameExports.gameCvars.deathmatch.value != 0))
                SetRespawn(ent, ent.item.quantity, gameExports);
    
            return true;
    
        }
    };
    static EntInteractAdapter Pickup_AncientHead = new EntInteractAdapter() {
        public String getID() { return "pickup_ancienthead";}
        public boolean interact(SubgameEntity ent, SubgameEntity other, GameExportsImpl gameExports) {
            other.max_health += 2;
    
            if (0 == (ent.spawnflags & GameDefines.DROPPED_ITEM)
                    && (gameExports.gameCvars.deathmatch.value != 0))
                SetRespawn(ent, ent.item.quantity, gameExports);
    
            return true;
        }
    };
    static EntInteractAdapter Pickup_Bandolier = new EntInteractAdapter() {
        public String getID() { return "pickup_bandolier";}
        public boolean interact(SubgameEntity ent, SubgameEntity other, GameExportsImpl gameExports) {
            GameItem item;
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
    
            item = FindItem("Bullets", gameExports);
            if (item != null) {
                index = item.index;
                client.pers.inventory[index] += item.quantity;
                if (client.pers.inventory[index] > client.pers.max_bullets)
                    client.pers.inventory[index] = client.pers.max_bullets;
            }
    
            item = FindItem("Shells", gameExports);
            if (item != null) {
                index = item.index;
                client.pers.inventory[index] += item.quantity;
                if (client.pers.inventory[index] > client.pers.max_shells)
                    client.pers.inventory[index] = client.pers.max_shells;
            }
    
            if (0 == (ent.spawnflags & GameDefines.DROPPED_ITEM)
                    && (gameExports.gameCvars.deathmatch.value != 0))
                SetRespawn(ent, ent.item.quantity, gameExports);
    
            return true;
    
        }
    };
    static ItemDropAdapter Drop_Ammo = new ItemDropAdapter() {
        public String getID() {
            return "drop_ammo";
        }

        public void drop(SubgameEntity ent, GameItem item, GameExportsImpl gameExports) {
            int index;

            index = item.index;
            SubgameEntity dropped = Drop_Item(ent, item, gameExports);
            gclient_t client = ent.getClient();
            if (client.pers.inventory[index] >= item.quantity)
                dropped.count = item.quantity;
            else
                dropped.count = client.pers.inventory[index];
    
            if (client.pers.weapon != null
                    && client.pers.weapon.classname.equals("ammo_grenades")
                    && item.classname.equals("ammo_grenades")
                    && client.pers.inventory[index] - dropped.count <= 0) {
                gameExports.gameImports.cprintf(ent, Defines.PRINT_HIGH,
                        "Can't drop current weapon\n");
                gameExports.freeEntity(dropped);
                return;
            }
    
            client.pers.inventory[index] -= dropped.count;
            ValidateSelectedItem(ent, gameExports);
        }
    };
    static ItemDropAdapter Drop_General = new ItemDropAdapter() {
        public String getID() {
            return "drop_general";
        }

        public void drop(SubgameEntity ent, GameItem item, GameExportsImpl gameExports) {
            Drop_Item(ent, item, gameExports);
            gclient_t client = ent.getClient();
            client.pers.inventory[item.index]--;
            ValidateSelectedItem(ent, gameExports);
        }
    };
    
    static ItemDropAdapter Drop_PowerArmor = new ItemDropAdapter() {
        public String getID() {
            return "drop_powerarmor";
        }

        public void drop(SubgameEntity ent, GameItem item, GameExportsImpl gameExports) {
            gclient_t client = ent.getClient();
            if (0 != (ent.flags & GameDefines.FL_POWER_ARMOR)
                    && (client.pers.inventory[item.index] == 1))
                Use_PowerArmor.use(ent, item, gameExports);
            Drop_General.drop(ent, item, gameExports);
        }
    };
    
    private static EntThinkAdapter droptofloor = new EntThinkAdapter() {
        public String getID() { return "drop_to_floor";}
        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
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
                gameExports.gameImports.setmodel(ent, ent.model);
            else
                gameExports.gameImports.setmodel(ent, ent.item.world_model);
            ent.solid = Defines.SOLID_TRIGGER;
            ent.movetype = GameDefines.MOVETYPE_TOSS;
            ent.touch = Touch_Item;
    
            float v[] = { 0, 0, -128 };
            Math3D.VectorAdd(ent.s.origin, v, dest);
    
            tr = gameExports.gameImports.trace(ent.s.origin, ent.mins, ent.maxs, dest, ent,
                    Defines.MASK_SOLID);
            if (tr.startsolid) {
                gameExports.gameImports.dprintf("droptofloor: " + ent.classname
                        + " startsolid at " + Lib.vtos(ent.s.origin) + "\n");
                gameExports.freeEntity(ent);
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
                    ent.think.nextTime = gameExports.level.time + Defines.FRAMETIME;
                    ent.think.action = DoRespawn;
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
    
            gameExports.gameImports.linkentity(ent);
            return true;
        }
    };
    static ItemUseAdapter Use_PowerArmor = new ItemUseAdapter() {
        public String getID() {
            return "use_powerarmor";
        }

        public void use(SubgameEntity ent, GameItem item, GameExportsImpl gameExports) {

            if ((ent.flags & GameDefines.FL_POWER_ARMOR) != 0) {
                ent.flags &= ~GameDefines.FL_POWER_ARMOR;
                gameExports.gameImports
                        .sound(ent, Defines.CHAN_AUTO, gameExports.gameImports
                                        .soundindex("misc/power2.wav"), 1,
                                Defines.ATTN_NORM, 0);
            } else {
                    GameItem cells = FindItem("cells", gameExports);
                gclient_t client = ent.getClient();
                if (cells == null || 0 == client.pers.inventory[cells.index]) {
                    gameExports.gameImports.cprintf(ent, Defines.PRINT_HIGH,
                            "No cells for power armor.\n");
                    return;
                }
                ent.flags |= GameDefines.FL_POWER_ARMOR;
                gameExports.gameImports
                        .sound(ent, Defines.CHAN_AUTO, gameExports.gameImports
                                .soundindex("misc/power1.wav"), 1,
                                Defines.ATTN_NORM, 0);
            }
        }
    };
    private static EntUseAdapter Use_Item = new EntUseAdapter() {
        public String getID() { return "use_item";}
        public void use(SubgameEntity ent, SubgameEntity other, SubgameEntity activator, GameExportsImpl gameExports) {
            ent.svflags &= ~Defines.SVF_NOCLIENT;
            ent.use = null;
    
            if ((ent.spawnflags & GameDefines.ITEM_NO_TOUCH) != 0) {
                ent.solid = Defines.SOLID_BBOX;
                ent.touch = null;
            } else {
                ent.solid = Defines.SOLID_TRIGGER;
                ent.touch = Touch_Item;
            }

            gameExports.gameImports.linkentity(ent);
        }
    };

    /*
     * =============== GetItemByIndex ===============
     */
    public static GameItem GetItemByIndex(int index, GameExportsImpl gameExports) {
        if (index < 0 || index >= gameExports.items.size())
            return null;

        return gameExports.items.get(index);
    }

    /*
     * =============== FindItemByClassname
     *
     * ===============
     */
    public static GameItem FindItemByClassname(String classname, GameExportsImpl gameExports) {
        return gameExports.items.stream()
                .filter(it -> classname.equalsIgnoreCase(it.classname))
                .findFirst()
                .orElse(null);
    }

    public static GameItem FindItem(String pickup_name, GameExportsImpl gameExports) {
        var found = gameExports.items.stream()
                .filter(it -> pickup_name.equalsIgnoreCase(it.pickup_name))
                .findFirst();
        if (found.isEmpty())
            gameExports.gameImports.dprintf("Item not found:" + pickup_name + "\n");
        return found.orElse(null);
    }

    public static void SetRespawn(SubgameEntity ent, float delay, GameExportsImpl gameExports) {
        ent.flags |= GameDefines.FL_RESPAWN;
        ent.svflags |= Defines.SVF_NOCLIENT;
        ent.solid = Defines.SOLID_NOT;
        ent.think.nextTime = gameExports.level.time + delay;
        ent.think.action = DoRespawn;
        gameExports.gameImports.linkentity(ent);
    }

    public static SubgameEntity Drop_Item(SubgameEntity ent, GameItem item, GameExportsImpl gameExports) {
        float[] forward = {0, 0, 0};
        float[] right = {0, 0, 0};
        float[] offset = {0, 0, 0};

        SubgameEntity dropped = gameExports.G_Spawn();

        dropped.classname = item.classname;
        dropped.item = item;
        dropped.spawnflags = GameDefines.DROPPED_ITEM;
        dropped.s.effects = item.world_model_flags;
        dropped.s.renderfx = Defines.RF_GLOW;
        Math3D.VectorSet(dropped.mins, -15, -15, -15);
        Math3D.VectorSet(dropped.maxs, 15, 15, 15);
        gameExports.gameImports.setmodel(dropped, dropped.item.world_model);
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
            trace = gameExports.gameImports.trace(ent.s.origin, dropped.mins, dropped.maxs,
                    dropped.s.origin, ent, Defines.CONTENTS_SOLID);
            Math3D.VectorCopy(trace.endpos, dropped.s.origin);
        } else {
            Math3D.AngleVectors(ent.s.angles, forward, right, null);
            Math3D.VectorCopy(ent.s.origin, dropped.s.origin);
        }
    
        Math3D.VectorScale(forward, 100, dropped.velocity);
        dropped.velocity[2] = 300;
    
        dropped.think.action = drop_make_touchable;
        dropped.think.nextTime = gameExports.level.time + 1;
    
        gameExports.gameImports.linkentity(dropped);
    
        return dropped;
    }

    public static int PowerArmorType(SubgameEntity ent, GameExportsImpl gameExports) {
        gclient_t client = ent.getClient();
        if (client == null)
            return GameDefines.POWER_ARMOR_NONE;
    
        if (0 == (ent.flags & GameDefines.FL_POWER_ARMOR))
            return GameDefines.POWER_ARMOR_NONE;
    
        if (client.pers.inventory[gameExports.power_shield_index] > 0)
            return GameDefines.POWER_ARMOR_SHIELD;
    
        if (client.pers.inventory[gameExports.power_screen_index] > 0)
            return GameDefines.POWER_ARMOR_SCREEN;
    
        return GameDefines.POWER_ARMOR_NONE;
    }

    public static int ArmorIndex(SubgameEntity ent, GameExportsImpl gameExports) {
        gclient_t client = ent.getClient();
        if (client == null)
            return -1;
    
        if (client.pers.inventory[gameExports.jacket_armor_index] > 0)
            return gameExports.jacket_armor_index;
    
        if (client.pers.inventory[gameExports.combat_armor_index] > 0)
            return gameExports.combat_armor_index;
    
        if (client.pers.inventory[gameExports.body_armor_index] > 0)
            return gameExports.body_armor_index;

        return -1;
    }

    public static boolean Add_Ammo(SubgameEntity ent, GameItem item, int count) {
        int index;
        int max;

        gclient_t client = ent.getClient();
        if (client == null)
            return false;

        if ("ammo_bullets".equals(item.classname))
            max = client.pers.max_bullets;
        else if ("ammo_shells".equals(item.classname))
            max = client.pers.max_shells;
        else if ("ammo_rockets".equals(item.classname))
            max = client.pers.max_rockets;
        else if ("ammo_grenades".equals(item.classname))
            max = client.pers.max_grenades;
        else if (item.classname.equals("ammo_cells"))
            max = client.pers.max_cells;
        else if (item.classname.equals("ammo_slugs"))
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

    /*
     * =============== SetItemNames
     * 
     * Called by worldspawn ===============
     */
    public static void SetItemNames(GameExportsImpl gameExports) {

        for (GameItem it : gameExports.items) {
            gameExports.gameImports.configstring(Defines.CS_ITEMS + it.index, it.pickup_name);
        }

        gameExports.jacket_armor_index = FindItem("Jacket Armor", gameExports).index;
        gameExports.combat_armor_index = FindItem("Combat Armor", gameExports).index;
        gameExports.body_armor_index = FindItem("Body Armor", gameExports).index;
        gameExports.power_screen_index = FindItem("Power Screen", gameExports).index;
        gameExports.power_shield_index = FindItem("Power Shield", gameExports).index;
    }

    public static void SelectNextItem(SubgameEntity ent, int itflags, GameExportsImpl gameExports) {

        gclient_t cl = ent.getClient();

        if (cl.chase_target != null) {
            GameChase.ChaseNext(ent, gameExports);
            return;
        }

        // scan for the next valid one
        for (int i = 1; i <= cl.pers.inventory.length; i++) {
            int index = (cl.pers.selected_item + i) % cl.pers.inventory.length;
            if (0 == cl.pers.inventory[index])
                continue;
            GameItem it = gameExports.items.get(index);
            if (it.use == null)
                continue;
            if (0 == (it.flags & itflags))
                continue;

            cl.pers.selected_item = index;
            return;
        }
    
        cl.pers.selected_item = -1;
    }

    public static void SelectPrevItem(SubgameEntity ent, int itflags, GameExportsImpl gameExports) {

        gclient_t cl = ent.getClient();

        if (cl.chase_target != null) {
            GameChase.ChasePrev(ent, gameExports);
            return;
        }

        // scan for the next valid one
        for (int i = 1; i <= cl.pers.inventory.length; i++) {
            int index = (cl.pers.selected_item + cl.pers.inventory.length - i) % cl.pers.inventory.length;
            if (0 == cl.pers.inventory[index])
                continue;
            GameItem it = gameExports.items.get(index);
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
    public static void PrecacheItem(GameItem it, GameExportsImpl gameExports) {

        if (it == null)
            return;

        if (it.pickup_sound != null)
            gameExports.gameImports.soundindex(it.pickup_sound);

        if (it.world_model != null)
            gameExports.gameImports.modelindex(it.world_model);
    
        if (it.view_model != null)
            gameExports.gameImports.modelindex(it.view_model);
    
        if (it.icon != null)
            gameExports.gameImports.imageindex(it.icon);
    
        // parse everything for its ammo
        if (it.ammo != null && it.ammo.length() != 0) {
            GameItem ammo = FindItem(it.ammo, gameExports);
            if (ammo != it)
                PrecacheItem(ammo, gameExports);
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
                gameExports.gameImports.error("PrecacheItem: it.classname has bad precache string: " + precacheString);
    
            // determine type based on extension
            if (file.endsWith("md2"))
                gameExports.gameImports.modelindex(file);
            else if (file.endsWith("sp2"))
                gameExports.gameImports.modelindex(file);
            else if (file.endsWith("wav"))
                gameExports.gameImports.soundindex(file);
            else if (file.endsWith("pcx"))
                gameExports.gameImports.imageindex(file);
            else
                gameExports.gameImports.error("PrecacheItem: bad precache string: " + file);
        }
    }

    /**
     * Sets the clipping size and plants the object on the floor.
     * <p>
     * Items can't be immediately dropped to floor, because they might be on an
     * entity that hasn't spawned yet.
     */
    public static void SpawnItem(SubgameEntity ent, GameItem item, GameExportsImpl gameExports) {
        PrecacheItem(item, gameExports);

        if (ent.spawnflags != 0) {
            if (!"key_power_cube".equals(ent.classname)) {
                ent.spawnflags = 0;
                gameExports.gameImports.dprintf("" + ent.classname + " at "
                        + Lib.vtos(ent.s.origin)
                        + " has invalid spawnflags set\n");
            }
        }
    
        // some items will be prevented in deathmatch
        if (gameExports.gameCvars.deathmatch.value != 0) {
            if (((int) gameExports.gameCvars.dmflags.value & Defines.DF_NO_ARMOR) != 0) {
                if (item.pickup == Pickup_Armor
                        || item.pickup == Pickup_PowerArmor) {
                    gameExports.freeEntity(ent);
                    return;
                }
            }
            if (((int) gameExports.gameCvars.dmflags.value & Defines.DF_NO_ITEMS) != 0) {
                if (item.pickup == Pickup_Powerup) {
                    gameExports.freeEntity(ent);
                    return;
                }
            }
            if (((int) gameExports.gameCvars.dmflags.value & Defines.DF_NO_HEALTH) != 0) {
                if (item.pickup == Pickup_Health
                        || item.pickup == Pickup_Adrenaline
                        || item.pickup == Pickup_AncientHead) {
                    gameExports.freeEntity(ent);
                    return;
                }
            }
            if (((int) gameExports.gameCvars.dmflags.value & Defines.DF_INFINITE_AMMO) != 0) {
                if ((item.flags == GameDefines.IT_AMMO)
                        || ("weapon_bfg".equals(ent.classname))) {
                    gameExports.freeEntity(ent);
                    return;
                }
            }
        }
    
        if (gameExports.gameCvars.coop.value != 0
                && ("key_power_cube".equals(ent.classname))) {
            ent.spawnflags |= (1 << (8 + gameExports.level.power_cubes));
            gameExports.level.power_cubes++;
        }
    
        // don't let them drop items that stay in a coop game
        if ((gameExports.gameCvars.coop.value != 0)
                && (item.flags & GameDefines.IT_STAY_COOP) != 0) {
            item.drop = null;
        }
    
        ent.item = item;
        ent.think.nextTime = gameExports.level.time + 2 * Defines.FRAMETIME;
        // items start after other solids
        ent.think.action = droptofloor;
        ent.s.effects = item.world_model_flags;
        ent.s.renderfx = Defines.RF_GLOW;
    
        if (ent.model != null)
            gameExports.gameImports.modelindex(ent.model);
    }




    /*
     * =============== 
     * Touch_Item 
     * ===============
     */
    public static void Touch_Item(SubgameEntity ent, SubgameEntity other, cplane_t plane,
                                  csurface_t surf, GameExportsImpl gameExports) {
        boolean taken;

        // freed edicts have not items.
        gclient_t client = other.getClient();
        if (client == null || ent.item == null)
            return;
        if (other.health < 1)
            return; // dead people can't pickup
        if (ent.item.pickup == null)
            return; // not a grabbable item?
    
        taken = ent.item.pickup.interact(ent, other, gameExports);
    
        if (taken) {
            // flash the screen
            client.bonus_alpha = 0.25f;
    
            // show icon and name on status bar
            client.getPlayerState().stats[Defines.STAT_PICKUP_ICON] = (short) gameExports.gameImports
                    .imageindex(ent.item.icon);
            client.getPlayerState().stats[Defines.STAT_PICKUP_STRING] = (short) (Defines.CS_ITEMS + ent.item.index);
            client.pickup_msg_time = gameExports.level.time + 3.0f;
    
            // change selected item
            if (ent.item.use != null)
                client.pers.selected_item = client.getPlayerState().stats[Defines.STAT_SELECTED_ITEM] = (short) ent.item.index;
    
            if (ent.item.pickup == Pickup_Health) {
                if (ent.count == 2)
                    gameExports.gameImports.sound(other, Defines.CHAN_ITEM, gameExports.gameImports
                            .soundindex("items/s_health.wav"), 1,
                            Defines.ATTN_NORM, 0);
                else if (ent.count == 10)
                    gameExports.gameImports.sound(other, Defines.CHAN_ITEM, gameExports.gameImports
                            .soundindex("items/n_health.wav"), 1,
                            Defines.ATTN_NORM, 0);
                else if (ent.count == 25)
                    gameExports.gameImports.sound(other, Defines.CHAN_ITEM, gameExports.gameImports
                            .soundindex("items/l_health.wav"), 1,
                            Defines.ATTN_NORM, 0);
                else
                    // (ent.count == 100)
                    gameExports.gameImports.sound(other, Defines.CHAN_ITEM, gameExports.gameImports
                            .soundindex("items/m_health.wav"), 1,
                            Defines.ATTN_NORM, 0);
            } else if (ent.item.pickup_sound != null) {
                gameExports.gameImports.sound(other, Defines.CHAN_ITEM, gameExports.gameImports
                        .soundindex(ent.item.pickup_sound), 1,
                        Defines.ATTN_NORM, 0);
            }
        }
    
        if (0 == (ent.spawnflags & GameDefines.ITEM_TARGETS_USED)) {
            GameUtil.G_UseTargets(ent, other, gameExports);
            ent.spawnflags |= GameDefines.ITEM_TARGETS_USED;
        }
    
        if (!taken)
            return;
    
        if (!((gameExports.gameCvars.coop.value != 0) && (ent.item.flags & GameDefines.IT_STAY_COOP) != 0)
                || 0 != (ent.spawnflags & (GameDefines.DROPPED_ITEM | GameDefines.DROPPED_PLAYER_ITEM))) {
            if ((ent.flags & GameDefines.FL_RESPAWN) != 0)
                ent.flags &= ~GameDefines.FL_RESPAWN;
            else
                gameExports.freeEntity(ent);
        }
    }

    public static void ValidateSelectedItem(SubgameEntity ent, GameExportsImpl gameExports) {
        gclient_t cl = ent.getClient();

        if (cl.pers.inventory[cl.pers.selected_item] != 0)
            return; // valid

        SelectNextItem(ent, -1, gameExports);
    }

    /**
     * Writes an item reference.
     */
    public static void writeItem(QuakeFile f, GameItem item) throws IOException {
        if (item == null)
            f.writeInt(-1);
        else
            f.writeInt(item.index);
    }

    /**
     * Reads the item index and returns the game item.
     */
    public static GameItem readItem(QuakeFile f, GameExportsImpl gameExports) throws IOException {
        int index = f.readInt();
        if (index == -1)
            return null;
        else
            return gameExports.items.get(index);
    }

    /**
     * Initialize the items list using a csv table from the classpath (absolute)
     *
     * @param tableName - classpath resource to the item csv table
     */
    public static List<GameItem> createGameItemList(String tableName) {

        try (InputStream in = GameItems.class.getResourceAsStream(tableName)) {
            final CSVFormat format = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setTrim(true).build();
            CSVParser source = CSVParser.parse(in, StandardCharsets.UTF_8, format);
            AtomicInteger index = new AtomicInteger();

            return source.stream()
                    .map(strings -> GameItem.readFromCsv(strings, index.getAndIncrement()))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
