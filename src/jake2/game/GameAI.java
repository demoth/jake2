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

// Created on 02.11.2003 by RST.
// $Id: GameAI.java,v 1.6 2005-02-06 18:58:51 salomo Exp $
package jake2.game;

import jake2.Defines;
import jake2.Globals;
import jake2.client.M;
import jake2.game.monsters.M_Player;
import jake2.qcommon.Com;
import jake2.server.SV_WORLD;
import jake2.util.Lib;
import jake2.util.Math3D;
import jake2.util.Vargs;

import java.util.Comparator;
import java.util.StringTokenizer;

public class GameAI {

    public static gitem_armor_t jacketarmor_info = new gitem_armor_t(25, 50,
            .30f, .00f, Defines.ARMOR_JACKET);

    public static gitem_armor_t combatarmor_info = new gitem_armor_t(50, 100,
            .60f, .30f, Defines.ARMOR_COMBAT);

    public static gitem_armor_t bodyarmor_info = new gitem_armor_t(100, 200,
            .80f, .60f, Defines.ARMOR_BODY);

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

            oldcount = other.client.pers.inventory[GameUtil
                    .ITEM_INDEX(ent.item)];

            if (!Add_Ammo(other, ent.item, count))
                return false;

            if (weapon && 0 == oldcount) {
                if (other.client.pers.weapon != ent.item
                        && (0 == GameBase.deathmatch.value || other.client.pers.weapon == GameUtil
                                .FindItem("blaster")))
                    other.client.newweapon = ent.item;
            }

            if (0 == (ent.spawnflags & (Defines.DROPPED_ITEM | Defines.DROPPED_PLAYER_ITEM))
                    && (GameBase.deathmatch.value != 0))
                GameUtil.SetRespawn(ent, 30);
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

            old_armor_index = GameUtil.ArmorIndex(other);

            // handle armor shards specially
            if (ent.item.tag == Defines.ARMOR_SHARD) {
                if (0 == old_armor_index)
                    other.client.pers.inventory[GameUtil.jacket_armor_index] = 2;
                else
                    other.client.pers.inventory[old_armor_index] += 2;
            }

            // if player has no armor, just use it
            else if (0 == old_armor_index) {
                other.client.pers.inventory[GameUtil.ITEM_INDEX(ent.item)] = newinfo.base_count;
            }

            // use the better armor
            else {
                // get info on old armor
                if (old_armor_index == GameUtil.jacket_armor_index)
                    oldinfo = jacketarmor_info;

                else if (old_armor_index == GameUtil.combat_armor_index)
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
                    other.client.pers.inventory[GameUtil.ITEM_INDEX(ent.item)] = newcount;
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
                GameUtil.SetRespawn(ent, 20);

            return true;
        }
    };

    public static EntInteractAdapter Pickup_PowerArmor = new EntInteractAdapter() {
        public boolean interact(edict_t ent, edict_t other) {

            int quantity;

            quantity = other.client.pers.inventory[GameUtil
                    .ITEM_INDEX(ent.item)];

            other.client.pers.inventory[GameUtil.ITEM_INDEX(ent.item)]++;

            if (GameBase.deathmatch.value != 0) {
                if (0 == (ent.spawnflags & Defines.DROPPED_ITEM))
                    GameUtil.SetRespawn(ent, ent.item.quantity);
                // auto-use for DM only if we didn't already have one
                if (0 == quantity)
                    ent.item.use.use(other, ent.item);
            }
            return true;
        }
    };

    //	======================================================================

    public static EntInteractAdapter Pickup_Powerup = new EntInteractAdapter() {

        public boolean interact(edict_t ent, edict_t other) {
            int quantity;

            quantity = other.client.pers.inventory[GameUtil
                    .ITEM_INDEX(ent.item)];
            if ((GameBase.skill.value == 1 && quantity >= 2)
                    || (GameBase.skill.value >= 2 && quantity >= 1))
                return false;

            if ((GameBase.coop.value != 0)
                    && (ent.item.flags & Defines.IT_STAY_COOP) != 0
                    && (quantity > 0))
                return false;

            other.client.pers.inventory[GameUtil.ITEM_INDEX(ent.item)]++;

            if (GameBase.deathmatch.value != 0) {
                if (0 == (ent.spawnflags & Defines.DROPPED_ITEM))
                    GameUtil.SetRespawn(ent, ent.item.quantity);
                if (((int) GameBase.dmflags.value & Defines.DF_INSTANT_ITEMS) != 0
                        || ((ent.item.use == GameUtil.Use_Quad) && 0 != (ent.spawnflags & Defines.DROPPED_PLAYER_ITEM))) {
                    if ((ent.item.use == GameUtil.Use_Quad)
                            && 0 != (ent.spawnflags & Defines.DROPPED_PLAYER_ITEM))
                        GameUtil.quad_drop_timeout_hack = (int) ((ent.nextthink - GameBase.level.time) / Defines.FRAMETIME);

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
                GameUtil.SetRespawn(ent, ent.item.quantity);

            return true;

        }
    };

    public static EntInteractAdapter Pickup_AncientHead = new EntInteractAdapter() {
        public boolean interact(edict_t ent, edict_t other) {
            other.max_health += 2;

            if (0 == (ent.spawnflags & Defines.DROPPED_ITEM)
                    && (GameBase.deathmatch.value != 0))
                GameUtil.SetRespawn(ent, ent.item.quantity);

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

            item = GameUtil.FindItem("Bullets");
            if (item != null) {
                index = GameUtil.ITEM_INDEX(item);
                other.client.pers.inventory[index] += item.quantity;
                if (other.client.pers.inventory[index] > other.client.pers.max_bullets)
                    other.client.pers.inventory[index] = other.client.pers.max_bullets;
            }

            item = GameUtil.FindItem("Shells");
            if (item != null) {
                index = GameUtil.ITEM_INDEX(item);
                other.client.pers.inventory[index] += item.quantity;
                if (other.client.pers.inventory[index] > other.client.pers.max_shells)
                    other.client.pers.inventory[index] = other.client.pers.max_shells;
            }

            if (0 == (ent.spawnflags & Defines.DROPPED_ITEM)
                    && (GameBase.deathmatch.value != 0))
                GameUtil.SetRespawn(ent, ent.item.quantity);

            return true;

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

    public static void AttackFinished(edict_t self, float time) {
        self.monsterinfo.attack_finished = GameBase.level.time + time;
    }

    /*
     * ============= ai_turn
     * 
     * don't move, but turn towards ideal_yaw Distance is for slight position
     * adjustments needed by the animations =============
     */
    public static void ai_turn(edict_t self, float dist) {
        if (dist != 0)
            M.M_walkmove(self, self.s.angles[Defines.YAW], dist);

        if (GameUtil.FindTarget(self))
            return;

        M.M_ChangeYaw(self);
    }

    /*
     * 
     * .enemy Will be world if not currently angry at anyone.
     * 
     * .movetarget The next path spot to walk toward. If .enemy, ignore
     * .movetarget. When an enemy is killed, the monster will try to return to
     * it's path.
     * 
     * .hunt_time Set to time + something when the player is in sight, but
     * movement straight for him is blocked. This causes the monster to use wall
     * following code for movement direction instead of sighting on the player.
     * 
     * .ideal_yaw A yaw angle of the intended direction, which will be turned
     * towards at up to 45 deg / state. If the enemy is in view and hunt_time is
     * not active, this will be the exact line towards the enemy.
     * 
     * .pausetime A monster will leave it's stand state and head towards it's
     * .movetarget when time > .pausetime.
     * 
     * walkmove(angle, speed) primitive is all or nothing
     */

    /*
     * ============ FacingIdeal
     * 
     * ============
     */

    public static boolean FacingIdeal(edict_t self) {
        float delta;

        delta = Math3D.anglemod(self.s.angles[Defines.YAW] - self.ideal_yaw);
        if (delta > 45 && delta < 315)
            return false;
        return true;
    }

    /*
     * ============= ai_run_melee
     * 
     * Turn and close until within an angle to launch a melee attack
     * =============
     */
    public static void ai_run_melee(edict_t self) {
        self.ideal_yaw = GameUtil.enemy_yaw;
        M.M_ChangeYaw(self);

        if (FacingIdeal(self)) {
            self.monsterinfo.melee.think(self);
            self.monsterinfo.attack_state = Defines.AS_STRAIGHT;
        }
    }

    /*
     * ============= ai_run_missile
     * 
     * Turn in place until within an angle to launch a missile attack
     * =============
     */
    public static void ai_run_missile(edict_t self) {
        self.ideal_yaw = GameUtil.enemy_yaw;
        M.M_ChangeYaw(self);

        if (FacingIdeal(self)) {
            self.monsterinfo.attack.think(self);
            self.monsterinfo.attack_state = Defines.AS_STRAIGHT;
        }
    };

    /*
     * ============= ai_run_slide
     * 
     * Strafe sideways, but stay at aproximately the same range =============
     */
    public static void ai_run_slide(edict_t self, float distance) {
        float ofs;

        self.ideal_yaw = GameUtil.enemy_yaw;
        M.M_ChangeYaw(self);

        if (self.monsterinfo.lefty != 0)
            ofs = 90;
        else
            ofs = -90;

        if (M.M_walkmove(self, self.ideal_yaw + ofs, distance))
            return;

        self.monsterinfo.lefty = 1 - self.monsterinfo.lefty;
        M.M_walkmove(self, self.ideal_yaw - ofs, distance);
    }

    /*
     * ============= ai_checkattack
     * 
     * Decides if we're going to attack or do something else used by ai_run and
     * ai_stand =============
     */
    public static boolean ai_checkattack(edict_t self, float dist) {
        float temp[] = { 0, 0, 0 };

        boolean hesDeadJim;

        //	   this causes monsters to run blindly to the combat point w/o firing
        if (self.goalentity != null) {
            if ((self.monsterinfo.aiflags & Defines.AI_COMBAT_POINT) != 0)
                return false;

            if ((self.monsterinfo.aiflags & Defines.AI_SOUND_TARGET) != 0) {
                if ((GameBase.level.time - self.enemy.teleport_time) > 5.0) {
                    if (self.goalentity == self.enemy)
                        if (self.movetarget != null)
                            self.goalentity = self.movetarget;
                        else
                            self.goalentity = null;
                    self.monsterinfo.aiflags &= ~Defines.AI_SOUND_TARGET;
                    if ((self.monsterinfo.aiflags & Defines.AI_TEMP_STAND_GROUND) != 0)
                        self.monsterinfo.aiflags &= ~(Defines.AI_STAND_GROUND | Defines.AI_TEMP_STAND_GROUND);
                } else {
                    self.show_hostile = (int) GameBase.level.time + 1;
                    return false;
                }
            }
        }

        GameUtil.enemy_vis = false;

        //	   see if the enemy is dead
        hesDeadJim = false;
        if ((null == self.enemy) || (!self.enemy.inuse)) {
            hesDeadJim = true;
        } else if ((self.monsterinfo.aiflags & Defines.AI_MEDIC) != 0) {
            if (self.enemy.health > 0) {
                hesDeadJim = true;
                self.monsterinfo.aiflags &= ~Defines.AI_MEDIC;
            }
        } else {
            if ((self.monsterinfo.aiflags & Defines.AI_BRUTAL) != 0) {
                if (self.enemy.health <= -80)
                    hesDeadJim = true;
            } else {
                if (self.enemy.health <= 0)
                    hesDeadJim = true;
            }
        }

        if (hesDeadJim) {
            self.enemy = null;
            // FIXME: look all around for other targets
            if (self.oldenemy != null && self.oldenemy.health > 0) {
                self.enemy = self.oldenemy;
                self.oldenemy = null;
                GameUtil.HuntTarget(self);
            } else {
                if (self.movetarget != null) {
                    self.goalentity = self.movetarget;
                    self.monsterinfo.walk.think(self);
                } else {
                    // we need the pausetime otherwise the stand code
                    // will just revert to walking with no target and
                    // the monsters will wonder around aimlessly trying
                    // to hunt the world entity
                    self.monsterinfo.pausetime = GameBase.level.time + 100000000;
                    self.monsterinfo.stand.think(self);
                }
                return true;
            }
        }

        self.show_hostile = (int) GameBase.level.time + 1; // wake up other
        // monsters

        //	   check knowledge of enemy
        GameUtil.enemy_vis = GameUtil.visible(self, self.enemy);
        if (GameUtil.enemy_vis) {
            self.monsterinfo.search_time = GameBase.level.time + 5;
            Math3D.VectorCopy(self.enemy.s.origin,
                    self.monsterinfo.last_sighting);
        }

        //	   look for other coop players here
        //		if (coop && self.monsterinfo.search_time < level.time)
        //		{
        //			if (FindTarget (self))
        //				return true;
        //		}

        GameUtil.enemy_infront = GameUtil.infront(self, self.enemy);
        GameUtil.enemy_range = GameUtil.range(self, self.enemy);
        Math3D.VectorSubtract(self.enemy.s.origin, self.s.origin, temp);
        GameUtil.enemy_yaw = Math3D.vectoyaw(temp);

        // JDC self.ideal_yaw = enemy_yaw;

        if (self.monsterinfo.attack_state == Defines.AS_MISSILE) {
            ai_run_missile(self);
            return true;
        }
        if (self.monsterinfo.attack_state == Defines.AS_MELEE) {
            ai_run_melee(self);
            return true;
        }

        // if enemy is not currently visible, we will never attack
        if (!GameUtil.enemy_vis)
            return false;

        return self.monsterinfo.checkattack.think(self);
    }

    public static void UpdateChaseCam(edict_t ent) {
        float[] o = { 0, 0, 0 }, ownerv = { 0, 0, 0 }, goal = { 0, 0, 0 };
        edict_t targ;
        float[] forward = { 0, 0, 0 }, right = { 0, 0, 0 };
        trace_t trace;
        int i;
        float[] oldgoal = { 0, 0, 0 };
        float[] angles = { 0, 0, 0 };

        // is our chase target gone?
        if (!ent.client.chase_target.inuse
                || ent.client.chase_target.client.resp.spectator) {
            edict_t old = ent.client.chase_target;
            ChaseNext(ent);
            if (ent.client.chase_target == old) {
                ent.client.chase_target = null;
                ent.client.ps.pmove.pm_flags &= ~pmove_t.PMF_NO_PREDICTION;
                return;
            }
        }

        targ = ent.client.chase_target;

        Math3D.VectorCopy(targ.s.origin, ownerv);
        Math3D.VectorCopy(ent.s.origin, oldgoal);

        ownerv[2] += targ.viewheight;

        Math3D.VectorCopy(targ.client.v_angle, angles);
        if (angles[Defines.PITCH] > 56)
            angles[Defines.PITCH] = 56;
        Math3D.AngleVectors(angles, forward, right, null);
        Math3D.VectorNormalize(forward);
        Math3D.VectorMA(ownerv, -30, forward, o);

        if (o[2] < targ.s.origin[2] + 20)
            o[2] = targ.s.origin[2] + 20;

        // jump animation lifts
        if (targ.groundentity == null)
            o[2] += 16;

        trace = GameBase.gi.trace(ownerv, Globals.vec3_origin,
                Globals.vec3_origin, o, targ, Defines.MASK_SOLID);

        Math3D.VectorCopy(trace.endpos, goal);

        Math3D.VectorMA(goal, 2, forward, goal);

        // pad for floors and ceilings
        Math3D.VectorCopy(goal, o);
        o[2] += 6;
        trace = GameBase.gi.trace(goal, Globals.vec3_origin,
                Globals.vec3_origin, o, targ, Defines.MASK_SOLID);
        if (trace.fraction < 1) {
            Math3D.VectorCopy(trace.endpos, goal);
            goal[2] -= 6;
        }

        Math3D.VectorCopy(goal, o);
        o[2] -= 6;
        trace = GameBase.gi.trace(goal, Globals.vec3_origin,
                Globals.vec3_origin, o, targ, Defines.MASK_SOLID);
        if (trace.fraction < 1) {
            Math3D.VectorCopy(trace.endpos, goal);
            goal[2] += 6;
        }

        if (targ.deadflag != 0)
            ent.client.ps.pmove.pm_type = Defines.PM_DEAD;
        else
            ent.client.ps.pmove.pm_type = Defines.PM_FREEZE;

        Math3D.VectorCopy(goal, ent.s.origin);
        for (i = 0; i < 3; i++)
            ent.client.ps.pmove.delta_angles[i] = (short) Math3D
                    .ANGLE2SHORT(targ.client.v_angle[i]
                            - ent.client.resp.cmd_angles[i]);

        if (targ.deadflag != 0) {
            ent.client.ps.viewangles[Defines.ROLL] = 40;
            ent.client.ps.viewangles[Defines.PITCH] = -15;
            ent.client.ps.viewangles[Defines.YAW] = targ.client.killer_yaw;
        } else {
            Math3D.VectorCopy(targ.client.v_angle, ent.client.ps.viewangles);
            Math3D.VectorCopy(targ.client.v_angle, ent.client.v_angle);
        }

        ent.viewheight = 0;
        ent.client.ps.pmove.pm_flags |= pmove_t.PMF_NO_PREDICTION;
        SV_WORLD.SV_LinkEdict(ent);
    }

    public static void ChaseNext(edict_t ent) {
        int i;
        edict_t e;

        if (null == ent.client.chase_target)
            return;

        i = ent.client.chase_target.index;
        do {
            i++;
            if (i > GameBase.maxclients.value)
                i = 1;
            e = GameBase.g_edicts[i];

            if (!e.inuse)
                continue;
            if (!e.client.resp.spectator)
                break;
        } while (e != ent.client.chase_target);

        ent.client.chase_target = e;
        ent.client.update_chase = true;
    }

    public static void ChasePrev(edict_t ent) {
        int i;
        edict_t e;

        if (ent.client.chase_target == null)
            return;

        i = ent.client.chase_target.index;
        do {
            i--;
            if (i < 1)
                i = (int) GameBase.maxclients.value;
            e = GameBase.g_edicts[i];
            if (!e.inuse)
                continue;
            if (!e.client.resp.spectator)
                break;
        } while (e != ent.client.chase_target);

        ent.client.chase_target = e;
        ent.client.update_chase = true;
    }

    public static void GetChaseTarget(edict_t ent) {
        int i;
        edict_t other;

        for (i = 1; i <= GameBase.maxclients.value; i++) {
            other = GameBase.g_edicts[i];
            if (other.inuse && !other.client.resp.spectator) {
                ent.client.chase_target = other;
                ent.client.update_chase = true;
                UpdateChaseCam(ent);
                return;
            }
        }
        GameBase.gi.centerprintf(ent, "No other players to chase.");
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

        GameUtil.jacket_armor_index = GameUtil.ITEM_INDEX(GameUtil
                .FindItem("Jacket Armor"));
        GameUtil.combat_armor_index = GameUtil.ITEM_INDEX(GameUtil
                .FindItem("Combat Armor"));
        GameUtil.body_armor_index = GameUtil.ITEM_INDEX(GameUtil
                .FindItem("Body Armor"));
        GameUtil.power_screen_index = GameUtil.ITEM_INDEX(GameUtil
                .FindItem("Power Screen"));
        GameUtil.power_shield_index = GameUtil.ITEM_INDEX(GameUtil
                .FindItem("Power Shield"));
    }

    public static void SelectNextItem(edict_t ent, int itflags) {
        gclient_t cl;
        int i, index;
        gitem_t it;

        cl = ent.client;

        if (cl.chase_target != null) {
            ChaseNext(ent);
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
            ChasePrev(ent);
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

    public static void ValidateSelectedItem(edict_t ent) {
        gclient_t cl;

        cl = ent.client;

        if (cl.pers.inventory[cl.pers.selected_item] != 0)
            return; // valid

        SelectNextItem(ent, -1);
    }

    //======================================================================

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

        index = GameUtil.ITEM_INDEX(item);

        if (ent.client.pers.inventory[index] == max)
            return false;

        ent.client.pers.inventory[index] += count;

        if (ent.client.pers.inventory[index] > max)
            ent.client.pers.inventory[index] = max;

        return true;
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
            ammo = GameUtil.FindItem(it.ammo);
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
                if (item.pickup == GameUtil.Pickup_Health
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
     * =============== Touch_Item ===============
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
            other.client.ps.stats[Defines.STAT_PICKUP_STRING] = (short) (Defines.CS_ITEMS + GameUtil
                    .ITEM_INDEX(ent.item));
            other.client.pickup_msg_time = GameBase.level.time + 3.0f;

            // change selected item
            if (ent.item.use != null)
                other.client.pers.selected_item = other.client.ps.stats[Defines.STAT_SELECTED_ITEM] = (short) GameUtil
                        .ITEM_INDEX(ent.item);

            if (ent.item.pickup == GameUtil.Pickup_Health) {
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

    /*
     * ================== LookAtKiller ==================
     */
    public static void LookAtKiller(edict_t self, edict_t inflictor,
            edict_t attacker) {
        float dir[] = { 0, 0, 0 };

        edict_t world = GameBase.g_edicts[0];

        if (attacker != null && attacker != world && attacker != self) {
            Math3D.VectorSubtract(attacker.s.origin, self.s.origin, dir);
        } else if (inflictor != null && inflictor != world && inflictor != self) {
            Math3D.VectorSubtract(inflictor.s.origin, self.s.origin, dir);
        } else {
            self.client.killer_yaw = self.s.angles[Defines.YAW];
            return;
        }

        if (dir[0] != 0)
            self.client.killer_yaw = (float) (180 / Math.PI * Math.atan2(
                    dir[1], dir[0]));
        else {
            self.client.killer_yaw = 0;
            if (dir[1] > 0)
                self.client.killer_yaw = 90;
            else if (dir[1] < 0)
                self.client.killer_yaw = -90;
        }
        if (self.client.killer_yaw < 0)
            self.client.killer_yaw += 360;

    }

    public static void TossClientWeapon(edict_t self) {
        gitem_t item;
        edict_t drop;
        boolean quad;
        float spread;

        if (GameBase.deathmatch.value == 0)
            return;

        item = self.client.pers.weapon;
        if (0 == self.client.pers.inventory[self.client.ammo_index])
            item = null;
        if (item != null && (Lib.strcmp(item.pickup_name, "Blaster") == 0))
            item = null;

        if (0 == ((int) (GameBase.dmflags.value) & Defines.DF_QUAD_DROP))
            quad = false;
        else
            quad = (self.client.quad_framenum > (GameBase.level.framenum + 10));

        if (item != null && quad)
            spread = 22.5f;
        else
            spread = 0.0f;

        if (item != null) {
            self.client.v_angle[Defines.YAW] -= spread;
            drop = GameUtil.Drop_Item(self, item);
            self.client.v_angle[Defines.YAW] += spread;
            drop.spawnflags = Defines.DROPPED_PLAYER_ITEM;
        }

        if (quad) {
            self.client.v_angle[Defines.YAW] += spread;
            drop = GameUtil.Drop_Item(self, GameUtil
                    .FindItemByClassname("item_quad"));
            self.client.v_angle[Defines.YAW] -= spread;
            drop.spawnflags |= Defines.DROPPED_PLAYER_ITEM;

            drop.touch = GameUtil.Touch_Item;
            drop.nextthink = GameBase.level.time
                    + (self.client.quad_framenum - GameBase.level.framenum)
                    * Defines.FRAMETIME;
            drop.think = GameUtil.G_FreeEdictA;
        }
    }

    public static void ThrowGib(edict_t self, String gibname, int damage,
            int type) {
        edict_t gib;

        float[] vd = { 0, 0, 0 };
        float[] origin = { 0, 0, 0 };
        float[] size = { 0, 0, 0 };
        float vscale;

        gib = GameUtil.G_Spawn();

        Math3D.VectorScale(self.size, 0.5f, size);
        Math3D.VectorAdd(self.absmin, size, origin);
        gib.s.origin[0] = origin[0] + Lib.crandom() * size[0];
        gib.s.origin[1] = origin[1] + Lib.crandom() * size[1];
        gib.s.origin[2] = origin[2] + Lib.crandom() * size[2];

        GameBase.gi.setmodel(gib, gibname);
        gib.solid = Defines.SOLID_NOT;
        gib.s.effects |= Defines.EF_GIB;
        gib.flags |= Defines.FL_NO_KNOCKBACK;
        gib.takedamage = Defines.DAMAGE_YES;
        gib.die = gib_die;

        if (type == Defines.GIB_ORGANIC) {
            gib.movetype = Defines.MOVETYPE_TOSS;
            gib.touch = gib_touch;
            vscale = 0.5f;
        } else {
            gib.movetype = Defines.MOVETYPE_BOUNCE;
            vscale = 1.0f;
        }

        VelocityForDamage(damage, vd);
        Math3D.VectorMA(self.velocity, vscale, vd, gib.velocity);
        ClipGibVelocity(gib);
        gib.avelocity[0] = Lib.random() * 600;
        gib.avelocity[1] = Lib.random() * 600;
        gib.avelocity[2] = Lib.random() * 600;

        gib.think = GameUtil.G_FreeEdictA;
        gib.nextthink = GameBase.level.time + 10 + Lib.random() * 10;

        GameBase.gi.linkentity(gib);
    }

    public static void ThrowHead(edict_t self, String gibname, int damage,
            int type) {
        float vd[] = { 0, 0, 0 };

        float vscale;

        self.s.skinnum = 0;
        self.s.frame = 0;
        Math3D.VectorClear(self.mins);
        Math3D.VectorClear(self.maxs);

        self.s.modelindex2 = 0;
        GameBase.gi.setmodel(self, gibname);
        self.solid = Defines.SOLID_NOT;
        self.s.effects |= Defines.EF_GIB;
        self.s.effects &= ~Defines.EF_FLIES;
        self.s.sound = 0;
        self.flags |= Defines.FL_NO_KNOCKBACK;
        self.svflags &= ~Defines.SVF_MONSTER;
        self.takedamage = Defines.DAMAGE_YES;
        self.die = gib_die;

        if (type == Defines.GIB_ORGANIC) {
            self.movetype = Defines.MOVETYPE_TOSS;
            self.touch = gib_touch;
            vscale = 0.5f;
        } else {
            self.movetype = Defines.MOVETYPE_BOUNCE;
            vscale = 1.0f;
        }

        VelocityForDamage(damage, vd);
        Math3D.VectorMA(self.velocity, vscale, vd, self.velocity);
        ClipGibVelocity(self);

        self.avelocity[Defines.YAW] = Lib.crandom() * 600f;

        self.think = GameUtil.G_FreeEdictA;
        self.nextthink = GameBase.level.time + 10 + Lib.random() * 10;

        GameBase.gi.linkentity(self);
    }

    public static void VelocityForDamage(int damage, float[] v) {
        v[0] = 100.0f * Lib.crandom();
        v[1] = 100.0f * Lib.crandom();
        v[2] = 200.0f + 100.0f * Lib.random();

        if (damage < 50)
            Math3D.VectorScale(v, 0.7f, v);
        else
            Math3D.VectorScale(v, 1.2f, v);
    }

    public static void ClipGibVelocity(edict_t ent) {
        if (ent.velocity[0] < -300)
            ent.velocity[0] = -300;
        else if (ent.velocity[0] > 300)
            ent.velocity[0] = 300;
        if (ent.velocity[1] < -300)
            ent.velocity[1] = -300;
        else if (ent.velocity[1] > 300)
            ent.velocity[1] = 300;
        if (ent.velocity[2] < 200)
            ent.velocity[2] = 200; // always some upwards
        else if (ent.velocity[2] > 500)
            ent.velocity[2] = 500;
    }

    public static void ThrowClientHead(edict_t self, int damage) {
        float vd[] = { 0, 0, 0 };
        String gibname;

        if ((Lib.rand() & 1) != 0) {
            gibname = "models/objects/gibs/head2/tris.md2";
            self.s.skinnum = 1; // second skin is player
        } else {
            gibname = "models/objects/gibs/skull/tris.md2";
            self.s.skinnum = 0;
        }

        self.s.origin[2] += 32;
        self.s.frame = 0;
        GameBase.gi.setmodel(self, gibname);
        Math3D.VectorSet(self.mins, -16, -16, 0);
        Math3D.VectorSet(self.maxs, 16, 16, 16);

        self.takedamage = Defines.DAMAGE_NO;
        self.solid = Defines.SOLID_NOT;
        self.s.effects = Defines.EF_GIB;
        self.s.sound = 0;
        self.flags |= Defines.FL_NO_KNOCKBACK;

        self.movetype = Defines.MOVETYPE_BOUNCE;
        VelocityForDamage(damage, vd);
        Math3D.VectorAdd(self.velocity, vd, self.velocity);

        if (self.client != null)
        // bodies in the queue don't have a client anymore
        {
            self.client.anim_priority = Defines.ANIM_DEATH;
            self.client.anim_end = self.s.frame;
        } else {
            self.think = null;
            self.nextthink = 0;
        }

        GameBase.gi.linkentity(self);
    }

    public static void ThrowDebris(edict_t self, String modelname, float speed,
            float[] origin) {
        edict_t chunk;
        float[] v = { 0, 0, 0 };

        chunk = GameUtil.G_Spawn();
        Math3D.VectorCopy(origin, chunk.s.origin);
        GameBase.gi.setmodel(chunk, modelname);
        v[0] = 100 * Lib.crandom();
        v[1] = 100 * Lib.crandom();
        v[2] = 100 + 100 * Lib.crandom();
        Math3D.VectorMA(self.velocity, speed, v, chunk.velocity);
        chunk.movetype = Defines.MOVETYPE_BOUNCE;
        chunk.solid = Defines.SOLID_NOT;
        chunk.avelocity[0] = Lib.random() * 600;
        chunk.avelocity[1] = Lib.random() * 600;
        chunk.avelocity[2] = Lib.random() * 600;
        chunk.think = GameUtil.G_FreeEdictA;
        chunk.nextthink = GameBase.level.time + 5 + Lib.random() * 5;
        chunk.s.frame = 0;
        chunk.flags = 0;
        chunk.classname = "debris";
        chunk.takedamage = Defines.DAMAGE_YES;
        chunk.die = debris_die;
        GameBase.gi.linkentity(chunk);
    }

    public static void BecomeExplosion1(edict_t self) {
        GameBase.gi.WriteByte(Defines.svc_temp_entity);
        GameBase.gi.WriteByte(Defines.TE_EXPLOSION1);
        GameBase.gi.WritePosition(self.s.origin);
        GameBase.gi.multicast(self.s.origin, Defines.MULTICAST_PVS);

        GameUtil.G_FreeEdict(self);
    }

    public static void BecomeExplosion2(edict_t self) {
        GameBase.gi.WriteByte(Defines.svc_temp_entity);
        GameBase.gi.WriteByte(Defines.TE_EXPLOSION2);
        GameBase.gi.WritePosition(self.s.origin);
        GameBase.gi.multicast(self.s.origin, Defines.MULTICAST_PVS);

        GameUtil.G_FreeEdict(self);
    }

    /** Returns true, if the players gender flag was set to female . */
    public static boolean IsFemale(edict_t ent) {
        char info;

        if (null == ent.client)
            return false;

        info = Info.Info_ValueForKey(ent.client.pers.userinfo, "gender")
                .charAt(0);
        if (info == 'f' || info == 'F')
            return true;
        return false;
    }

    /**
     * Returns true, if the players gender flag was neither set to female nor to
     * male.
     */
    public static boolean IsNeutral(edict_t ent) {
        char info;

        if (ent.client == null)
            return false;

        info = Info.Info_ValueForKey(ent.client.pers.userinfo, "gender")
                .charAt(0);

        if (info != 'f' && info != 'F' && info != 'm' && info != 'M')
            return true;
        return false;
    }

    /** Some reports about the cause of the players death. */
    public static void ClientObituary(edict_t self, edict_t inflictor,
            edict_t attacker) {
        int mod;
        String message;
        String message2;
        boolean ff;

        // attacker can be null by some mods! bug fix by rene stoeckel        
        if (GameBase.coop.value != 0 && attacker != null && attacker.client != null)
            GameBase.meansOfDeath |= Defines.MOD_FRIENDLY_FIRE;

        if (GameBase.deathmatch.value != 0 || GameBase.coop.value != 0) {
            ff = (GameBase.meansOfDeath & Defines.MOD_FRIENDLY_FIRE) != 0;
            mod = GameBase.meansOfDeath & ~Defines.MOD_FRIENDLY_FIRE;
            message = null;
            message2 = "";

            switch (mod) {
            case Defines.MOD_SUICIDE:
                message = "suicides";
                break;
            case Defines.MOD_FALLING:
                message = "cratered";
                break;
            case Defines.MOD_CRUSH:
                message = "was squished";
                break;
            case Defines.MOD_WATER:
                message = "sank like a rock";
                break;
            case Defines.MOD_SLIME:
                message = "melted";
                break;
            case Defines.MOD_LAVA:
                message = "does a back flip into the lava";
                break;
            case Defines.MOD_EXPLOSIVE:
            case Defines.MOD_BARREL:
                message = "blew up";
                break;
            case Defines.MOD_EXIT:
                message = "found a way out";
                break;
            case Defines.MOD_TARGET_LASER:
                message = "saw the light";
                break;
            case Defines.MOD_TARGET_BLASTER:
                message = "got blasted";
                break;
            case Defines.MOD_BOMB:
            case Defines.MOD_SPLASH:
            case Defines.MOD_TRIGGER_HURT:
                message = "was in the wrong place";
                break;
            }
            if (attacker == self) {
                switch (mod) {
                case Defines.MOD_HELD_GRENADE:
                    message = "tried to put the pin back in";
                    break;
                case Defines.MOD_HG_SPLASH:
                case Defines.MOD_G_SPLASH:
                    if (IsNeutral(self))
                        message = "tripped on its own grenade";
                    else if (IsFemale(self))
                        message = "tripped on her own grenade";
                    else
                        message = "tripped on his own grenade";
                    break;
                case Defines.MOD_R_SPLASH:
                    if (IsNeutral(self))
                        message = "blew itself up";
                    else if (IsFemale(self))
                        message = "blew herself up";
                    else
                        message = "blew himself up";
                    break;
                case Defines.MOD_BFG_BLAST:
                    message = "should have used a smaller gun";
                    break;
                default:
                    if (IsNeutral(self))
                        message = "killed itself";
                    else if (IsFemale(self))
                        message = "killed herself";
                    else
                        message = "killed himself";
                    break;
                }
            }
            if (message != null) {
                GameBase.gi.bprintf(Defines.PRINT_MEDIUM, ""
                        + self.client.pers.netname + " " + message + ".\n");
                if (GameBase.deathmatch.value != 0)
                    self.client.resp.score--;
                self.enemy = null;
                return;
            }

            self.enemy = attacker;

            if (attacker != null && attacker.client != null) {
                switch (mod) {
                case Defines.MOD_BLASTER:
                    message = "was blasted by";
                    break;
                case Defines.MOD_SHOTGUN:
                    message = "was gunned down by";
                    break;
                case Defines.MOD_SSHOTGUN:
                    message = "was blown away by";
                    message2 = "'s super shotgun";
                    break;
                case Defines.MOD_MACHINEGUN:
                    message = "was machinegunned by";
                    break;
                case Defines.MOD_CHAINGUN:
                    message = "was cut in half by";
                    message2 = "'s chaingun";
                    break;
                case Defines.MOD_GRENADE:
                    message = "was popped by";
                    message2 = "'s grenade";
                    break;
                case Defines.MOD_G_SPLASH:
                    message = "was shredded by";
                    message2 = "'s shrapnel";
                    break;
                case Defines.MOD_ROCKET:
                    message = "ate";
                    message2 = "'s rocket";
                    break;
                case Defines.MOD_R_SPLASH:
                    message = "almost dodged";
                    message2 = "'s rocket";
                    break;
                case Defines.MOD_HYPERBLASTER:
                    message = "was melted by";
                    message2 = "'s hyperblaster";
                    break;
                case Defines.MOD_RAILGUN:
                    message = "was railed by";
                    break;
                case Defines.MOD_BFG_LASER:
                    message = "saw the pretty lights from";
                    message2 = "'s BFG";
                    break;
                case Defines.MOD_BFG_BLAST:
                    message = "was disintegrated by";
                    message2 = "'s BFG blast";
                    break;
                case Defines.MOD_BFG_EFFECT:
                    message = "couldn't hide from";
                    message2 = "'s BFG";
                    break;
                case Defines.MOD_HANDGRENADE:
                    message = "caught";
                    message2 = "'s handgrenade";
                    break;
                case Defines.MOD_HG_SPLASH:
                    message = "didn't see";
                    message2 = "'s handgrenade";
                    break;
                case Defines.MOD_HELD_GRENADE:
                    message = "feels";
                    message2 = "'s pain";
                    break;
                case Defines.MOD_TELEFRAG:
                    message = "tried to invade";
                    message2 = "'s personal space";
                    break;
                }
                if (message != null) {
                    GameBase.gi.bprintf(Defines.PRINT_MEDIUM,
                            self.client.pers.netname + " " + message + " "
                                    + attacker.client.pers.netname + ""
                                    + message2);
                    if (GameBase.deathmatch.value != 0) {
                        if (ff)
                            attacker.client.resp.score--;
                        else
                            attacker.client.resp.score++;
                    }
                    return;
                }
            }
        }

        GameBase.gi.bprintf(Defines.PRINT_MEDIUM, self.client.pers.netname
                + " died.\n");
        if (GameBase.deathmatch.value != 0)
            self.client.resp.score--;
    }

    /** 
     * HelpComputer. Draws the help computer.
     */
    public static void HelpComputer(edict_t ent) {
        StringBuffer sb = new StringBuffer(256);
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

        GameBase.gi.WriteByte(Defines.svc_layout);
        GameBase.gi.WriteString(sb.toString());
        GameBase.gi.unicast(ent, true);
    }

    /**
     * Processes the commands the player enters in the quake console.
     *  
     */
    public static void ClientCommand(edict_t ent) {
        String cmd;

        if (ent.client == null)
            return; // not fully in game yet

        cmd = GameBase.gi.argv(0);

        if (Lib.Q_stricmp(cmd, "players") == 0) {
            Cmd.Players_f(ent);
            return;
        }
        if (Lib.Q_stricmp(cmd, "say") == 0) {
            Cmd.Say_f(ent, false, false);
            return;
        }
        if (Lib.Q_stricmp(cmd, "say_team") == 0) {
            Cmd.Say_f(ent, true, false);
            return;
        }
        if (Lib.Q_stricmp(cmd, "score") == 0) {
            Cmd.Score_f(ent);
            return;
        }
        if (Lib.Q_stricmp(cmd, "help") == 0) {
            Cmd.Help_f(ent);
            return;
        }

        if (GameBase.level.intermissiontime != 0)
            return;

        if (Lib.Q_stricmp(cmd, "use") == 0)
            Cmd.Use_f(ent);

        else if (Lib.Q_stricmp(cmd, "drop") == 0)
            Cmd.Drop_f(ent);
        else if (Lib.Q_stricmp(cmd, "give") == 0)
            Cmd.Give_f(ent);
        else if (Lib.Q_stricmp(cmd, "god") == 0)
            Cmd.God_f(ent);
        else if (Lib.Q_stricmp(cmd, "notarget") == 0)
            Cmd.Notarget_f(ent);
        else if (Lib.Q_stricmp(cmd, "noclip") == 0)
            Cmd.Noclip_f(ent);
        else if (Lib.Q_stricmp(cmd, "inven") == 0)
            Cmd.Inven_f(ent);
        else if (Lib.Q_stricmp(cmd, "invnext") == 0)
            SelectNextItem(ent, -1);
        else if (Lib.Q_stricmp(cmd, "invprev") == 0)
            SelectPrevItem(ent, -1);
        else if (Lib.Q_stricmp(cmd, "invnextw") == 0)
            SelectNextItem(ent, Defines.IT_WEAPON);
        else if (Lib.Q_stricmp(cmd, "invprevw") == 0)
            SelectPrevItem(ent, Defines.IT_WEAPON);
        else if (Lib.Q_stricmp(cmd, "invnextp") == 0)
            SelectNextItem(ent, Defines.IT_POWERUP);
        else if (Lib.Q_stricmp(cmd, "invprevp") == 0)
            SelectPrevItem(ent, Defines.IT_POWERUP);
        else if (Lib.Q_stricmp(cmd, "invuse") == 0)
            Cmd.InvUse_f(ent);
        else if (Lib.Q_stricmp(cmd, "invdrop") == 0)
            Cmd.InvDrop_f(ent);
        else if (Lib.Q_stricmp(cmd, "weapprev") == 0)
            Cmd.WeapPrev_f(ent);
        else if (Lib.Q_stricmp(cmd, "weapnext") == 0)
            Cmd.WeapNext_f(ent);
        else if (Lib.Q_stricmp(cmd, "weaplast") == 0)
            Cmd.WeapLast_f(ent);
        else if (Lib.Q_stricmp(cmd, "kill") == 0)
            Cmd.Kill_f(ent);
        else if (Lib.Q_stricmp(cmd, "putaway") == 0)
            Cmd.PutAway_f(ent);
        else if (Lib.Q_stricmp(cmd, "wave") == 0)
            Cmd.Wave_f(ent);
        else if (Lib.Q_stricmp(cmd, "playerlist") == 0)
            Cmd.PlayerList_f(ent);
        else
            // anything that doesn't match a command will be a chat
            Cmd.Say_f(ent, false, true);
    }

    public static boolean Pickup_PowerArmor(edict_t ent, edict_t other) {
        int quantity;

        quantity = other.client.pers.inventory[GameUtil.ITEM_INDEX(ent.item)];

        other.client.pers.inventory[GameUtil.ITEM_INDEX(ent.item)]++;

        if (GameBase.deathmatch.value != 0) {
            if (0 == (ent.spawnflags & Defines.DROPPED_ITEM))
                GameUtil.SetRespawn(ent, ent.item.quantity);
            // auto-use for DM only if we didn't already have one
            if (0 == quantity)
                ent.item.use.use(other, ent.item);
        }

        return true;
    }

    public static void InitItems() {
        GameBase.game.num_items = itemlist.length - 1;
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
        SpawnItem(self, GameUtil.FindItem("Health"));
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
        SpawnItem(self, GameUtil.FindItem("Health"));
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
        SpawnItem(self, GameUtil.FindItem("Health"));
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
        SpawnItem(self, GameUtil.FindItem("Health"));
        GameBase.gi.soundindex("items/m_health.wav");
        self.style = Defines.HEALTH_IGNORE_MAX | Defines.HEALTH_TIMED;
    }

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
                index = GameUtil.ITEM_INDEX(GameUtil.FindItem("cells"));
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

    public static ItemDropAdapter Drop_Ammo = new ItemDropAdapter() {
        public void drop(edict_t ent, gitem_t item) {
            edict_t dropped;
            int index;

            index = GameUtil.ITEM_INDEX(item);
            dropped = GameUtil.Drop_Item(ent, item);
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
            ValidateSelectedItem(ent);
        }
    };

    public static ItemDropAdapter Drop_General = new ItemDropAdapter() {
        public void drop(edict_t ent, gitem_t item) {
            GameUtil.Drop_Item(ent, item);
            ent.client.pers.inventory[GameUtil.ITEM_INDEX(item)]--;
            ValidateSelectedItem(ent);
        }
    };

    public static ItemDropAdapter Drop_PowerArmor = new ItemDropAdapter() {
        public void drop(edict_t ent, gitem_t item) {
            if (0 != (ent.flags & Defines.FL_POWER_ARMOR)
                    && (ent.client.pers.inventory[GameUtil.ITEM_INDEX(item)] == 1))
                Use_PowerArmor.use(ent, item);
            Drop_General.drop(ent, item);
        }
    };

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

            "item_armor_body", Pickup_Armor, null, null, null,
                    "misc/ar1_pkup.wav", "models/items/armor/body/tris.md2",
                    Defines.EF_ROTATE, null,
                    /* icon */
                    "i_bodyarmor",
                    /* pickup */
                    "Body Armor",
                    /* width */
                    3, 0, null, Defines.IT_ARMOR, 0, bodyarmor_info,
                    Defines.ARMOR_BODY,
                    /* precache */
                    ""),

            /*
             * QUAKED item_armor_combat (.3 .3 1) (-16 -16 -16) (16 16 16)
             */
            new gitem_t("item_armor_combat", Pickup_Armor, null, null, null,
                    "misc/ar1_pkup.wav", "models/items/armor/combat/tris.md2",
                    Defines.EF_ROTATE, null,
                    /* icon */
                    "i_combatarmor",
                    /* pickup */
                    "Combat Armor",
                    /* width */
                    3, 0, null, Defines.IT_ARMOR, 0, combatarmor_info,
                    Defines.ARMOR_COMBAT,
                    /* precache */
                    ""),

            /*
             * QUAKED item_armor_jacket (.3 .3 1) (-16 -16 -16) (16 16 16)
             */
            new gitem_t("item_armor_jacket", Pickup_Armor, null, null, null,
                    "misc/ar1_pkup.wav", "models/items/armor/jacket/tris.md2",
                    Defines.EF_ROTATE, null,
                    /* icon */
                    "i_jacketarmor",
                    /* pickup */
                    "Jacket Armor",
                    /* width */
                    3, 0, null, Defines.IT_ARMOR, 0, jacketarmor_info,
                    Defines.ARMOR_JACKET,
                    /* precache */
                    ""),

            /*
             * QUAKED item_armor_shard (.3 .3 1) (-16 -16 -16) (16 16 16)
             */
            new gitem_t("item_armor_shard", Pickup_Armor, null, null, null,
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
            new gitem_t("item_power_screen", Pickup_PowerArmor, Use_PowerArmor,
                    Drop_PowerArmor, null, "misc/ar3_pkup.wav",
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
            new gitem_t("item_power_shield", Pickup_PowerArmor, Use_PowerArmor,
                    Drop_PowerArmor, null, "misc/ar3_pkup.wav",
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
            new gitem_t("weapon_blaster", null, GamePWeapon.Use_Weapon, null,
                    GamePWeapon.Weapon_Blaster, "misc/w_pkup.wav", null, 0,
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
            new gitem_t("weapon_shotgun", GamePWeapon.Pickup_Weapon,
                    GamePWeapon.Use_Weapon, GamePWeapon.Drop_Weapon,
                    GamePWeapon.Weapon_Shotgun, "misc/w_pkup.wav",
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
            new gitem_t("weapon_supershotgun", GamePWeapon.Pickup_Weapon,
                    GamePWeapon.Use_Weapon, GamePWeapon.Drop_Weapon,
                    GamePWeapon.Weapon_SuperShotgun, "misc/w_pkup.wav",
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
                    GamePWeapon.Pickup_Weapon,
                    GamePWeapon.Use_Weapon,
                    GamePWeapon.Drop_Weapon,
                    GamePWeapon.Weapon_Machinegun,
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
                    GamePWeapon.Pickup_Weapon,
                    GamePWeapon.Use_Weapon,
                    GamePWeapon.Drop_Weapon,
                    GamePWeapon.Weapon_Chaingun,
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
                    Pickup_Ammo,
                    GamePWeapon.Use_Weapon,
                    Drop_Ammo,
                    GamePWeapon.Weapon_Grenade,
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
                    GamePWeapon.Pickup_Weapon,
                    GamePWeapon.Use_Weapon,
                    GamePWeapon.Drop_Weapon,
                    GamePWeapon.Weapon_GrenadeLauncher,
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
                    GamePWeapon.Pickup_Weapon,
                    GamePWeapon.Use_Weapon,
                    GamePWeapon.Drop_Weapon,
                    GamePWeapon.Weapon_RocketLauncher,
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
                    GamePWeapon.Pickup_Weapon,
                    GamePWeapon.Use_Weapon,
                    GamePWeapon.Drop_Weapon,
                    GamePWeapon.Weapon_HyperBlaster,
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
            new gitem_t("weapon_railgun", GamePWeapon.Pickup_Weapon,
                    GamePWeapon.Use_Weapon, GamePWeapon.Drop_Weapon,
                    GamePWeapon.Weapon_Railgun, "misc/w_pkup.wav",
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
                    GamePWeapon.Pickup_Weapon,
                    GamePWeapon.Use_Weapon,
                    GamePWeapon.Drop_Weapon,
                    GamePWeapon.Weapon_BFG,
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
            new gitem_t("ammo_shells", Pickup_Ammo, null, Drop_Ammo, null,
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
            new gitem_t("ammo_bullets", Pickup_Ammo, null, Drop_Ammo, null,
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
            new gitem_t("ammo_cells", Pickup_Ammo, null, Drop_Ammo, null,
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
            new gitem_t("ammo_rockets", Pickup_Ammo, null, Drop_Ammo, null,
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
            new gitem_t("ammo_slugs", Pickup_Ammo, null, Drop_Ammo, null,
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
            new gitem_t("item_quad", Pickup_Powerup, GameUtil.Use_Quad,
                    Drop_General, null, "items/pkup.wav",
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
            new gitem_t("item_invulnerability", Pickup_Powerup,
                    GameUtil.Use_Invulnerability, Drop_General, null,
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
            new gitem_t("item_silencer", Pickup_Powerup, GameUtil.Use_Silencer,
                    Drop_General, null, "items/pkup.wav",
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
            new gitem_t("item_breather", Pickup_Powerup, GameUtil.Use_Breather,
                    Drop_General, null, "items/pkup.wav",
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
            new gitem_t("item_enviro", Pickup_Powerup, GameUtil.Use_Envirosuit,
                    Drop_General, null, "items/pkup.wav",
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
            new gitem_t("item_ancient_head", Pickup_AncientHead, null, null,
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
            new gitem_t("item_adrenaline", Pickup_Adrenaline, null, null, null,
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
            new gitem_t("item_bandolier", Pickup_Bandolier, null, null, null,
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
            new gitem_t("item_pack", GameUtil.Pickup_Pack, null, null, null,
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
            new gitem_t("key_data_cd", GameUtil.Pickup_Key, null, Drop_General,
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
            new gitem_t("key_power_cube", GameUtil.Pickup_Key, null,
                    Drop_General, null, "items/pkup.wav",
                    "models/items/keys/power/tris.md2", Defines.EF_ROTATE,
                    null, "k_powercube", "Power Cube", 2, 0, null,
                    Defines.IT_STAY_COOP | Defines.IT_KEY, 0, null, 0,
                    /* precache */
                    ""),

            /*
             * QUAKED key_pyramid (0 .5 .8) (-16 -16 -16) (16 16 16) key for the
             * entrance of jail3
             */
            new gitem_t("key_pyramid", GameUtil.Pickup_Key, null, Drop_General,
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
            new gitem_t("key_data_spinner", GameUtil.Pickup_Key, null,
                    Drop_General, null, "items/pkup.wav",
                    "models/items/keys/spinner/tris.md2", Defines.EF_ROTATE,
                    null, "k_dataspin", "Data Spinner", 2, 0, null,
                    Defines.IT_STAY_COOP | Defines.IT_KEY, 0, null, 0,
                    /* precache */
                    ""),

            /*
             * QUAKED key_pass (0 .5 .8) (-16 -16 -16) (16 16 16) security pass
             * for the security level
             */
            new gitem_t("key_pass", GameUtil.Pickup_Key, null, Drop_General,
                    null, "items/pkup.wav", "models/items/keys/pass/tris.md2",
                    Defines.EF_ROTATE, null, "k_security", "Security Pass", 2,
                    0, null, Defines.IT_STAY_COOP | Defines.IT_KEY, 0, null, 0,
                    /* precache */
                    ""),

            /*
             * QUAKED key_blue_key (0 .5 .8) (-16 -16 -16) (16 16 16) normal
             * door key - blue
             */
            new gitem_t("key_blue_key", GameUtil.Pickup_Key, null,
                    Drop_General, null, "items/pkup.wav",
                    "models/items/keys/key/tris.md2", Defines.EF_ROTATE, null,
                    "k_bluekey", "Blue Key", 2, 0, null, Defines.IT_STAY_COOP
                            | Defines.IT_KEY, 0, null, 0,
                    /* precache */
                    ""),

            /*
             * QUAKED key_red_key (0 .5 .8) (-16 -16 -16) (16 16 16) normal door
             * key - red
             */
            new gitem_t("key_red_key", GameUtil.Pickup_Key, null, Drop_General,
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
            new gitem_t("key_commander_head", GameUtil.Pickup_Key, null,
                    Drop_General, null, "items/pkup.wav",
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
            new gitem_t("key_airstrike_target", GameUtil.Pickup_Key, null,
                    Drop_General, null, "items/pkup.wav",
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
            new gitem_t(null, GameUtil.Pickup_Health, null, null, null,
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

    /** Common Boss explode animation. */

    public static EntThinkAdapter BossExplode = new EntThinkAdapter() {
        public boolean think(edict_t self) {
            float[] org = { 0, 0, 0 };

            int n;

            self.think = BossExplode;
            Math3D.VectorCopy(self.s.origin, org);
            org[2] += 24 + (Lib.rand() & 15);
            switch (self.count++) {
            case 0:
                org[0] -= 24;
                org[1] -= 24;
                break;
            case 1:
                org[0] += 24;
                org[1] += 24;
                break;
            case 2:
                org[0] += 24;
                org[1] -= 24;
                break;
            case 3:
                org[0] -= 24;
                org[1] += 24;
                break;
            case 4:
                org[0] -= 48;
                org[1] -= 48;
                break;
            case 5:
                org[0] += 48;
                org[1] += 48;
                break;
            case 6:
                org[0] -= 48;
                org[1] += 48;
                break;
            case 7:
                org[0] += 48;
                org[1] -= 48;
                break;
            case 8:
                self.s.sound = 0;
                for (n = 0; n < 4; n++)
                    ThrowGib(self, "models/objects/gibs/sm_meat/tris.md2", 500,
                            Defines.GIB_ORGANIC);
                for (n = 0; n < 8; n++)
                    ThrowGib(self, "models/objects/gibs/sm_metal/tris.md2",
                            500, Defines.GIB_METALLIC);
                ThrowGib(self, "models/objects/gibs/chest/tris.md2", 500,
                        Defines.GIB_ORGANIC);
                ThrowHead(self, "models/objects/gibs/gear/tris.md2", 500,
                        Defines.GIB_METALLIC);
                self.deadflag = Defines.DEAD_DEAD;
                return true;
            }

            GameBase.gi.WriteByte(Defines.svc_temp_entity);
            GameBase.gi.WriteByte(Defines.TE_EXPLOSION1);
            GameBase.gi.WritePosition(org);
            GameBase.gi.multicast(self.s.origin, Defines.MULTICAST_PVS);

            self.nextthink = GameBase.level.time + 0.1f;
            return true;
        }
    };

    public static EntThinkAdapter walkmonster_start_go = new EntThinkAdapter() {
        public boolean think(edict_t self) {

            if (0 == (self.spawnflags & 2) && GameBase.level.time < 1) {
                M.M_droptofloor.think(self);

                if (self.groundentity != null)
                    if (!M.M_walkmove(self, 0, 0))
                        GameBase.gi.dprintf(self.classname + " in solid at "
                                + Lib.vtos(self.s.origin) + "\n");
            }

            if (0 == self.yaw_speed)
                self.yaw_speed = 20;
            self.viewheight = 25;

            Monster.monster_start_go(self);

            if ((self.spawnflags & 2) != 0)
                Monster.monster_triggered_start.think(self);
            return true;
        }
    };

    public static EntThinkAdapter walkmonster_start = new EntThinkAdapter() {
        public boolean think(edict_t self) {

            self.think = walkmonster_start_go;
            Monster.monster_start(self);
            return true;
        }
    };

    public static EntThinkAdapter flymonster_start_go = new EntThinkAdapter() {
        public boolean think(edict_t self) {
            if (!M.M_walkmove(self, 0, 0))
                GameBase.gi.dprintf(self.classname + " in solid at "
                        + Lib.vtos(self.s.origin) + "\n");

            if (0 == self.yaw_speed)
                self.yaw_speed = 10;
            self.viewheight = 25;

            Monster.monster_start_go(self);

            if ((self.spawnflags & 2) != 0)
                Monster.monster_triggered_start.think(self);
            return true;
        }
    };

    public static EntThinkAdapter flymonster_start = new EntThinkAdapter() {
        public boolean think(edict_t self) {
            self.flags |= Defines.FL_FLY;
            self.think = flymonster_start_go;
            Monster.monster_start(self);
            return true;
        }
    };

    public static EntThinkAdapter swimmonster_start_go = new EntThinkAdapter() {
        public boolean think(edict_t self) {
            if (0 == self.yaw_speed)
                self.yaw_speed = 10;
            self.viewheight = 10;

            Monster.monster_start_go(self);

            if ((self.spawnflags & 2) != 0)
                Monster.monster_triggered_start.think(self);
            return true;
        }
    };

    public static EntThinkAdapter swimmonster_start = new EntThinkAdapter() {
        public boolean think(edict_t self) {
            self.flags |= Defines.FL_SWIM;
            self.think = swimmonster_start_go;
            Monster.monster_start(self);
            return true;
        }
    };

    /*
     * ============= ai_turn
     * 
     * don't move, but turn towards ideal_yaw Distance is for slight position
     * adjustments needed by the animations =============
     */
    public static AIAdapter ai_turn = new AIAdapter() {
        public void ai(edict_t self, float dist) {

            if (dist != 0)
                M.M_walkmove(self, self.s.angles[Defines.YAW], dist);

            if (GameUtil.FindTarget(self))
                return;

            M.M_ChangeYaw(self);
        }
    };

    /*
     * ============= ai_move
     * 
     * Move the specified distance at current facing. This replaces the QC
     * functions: ai_forward, ai_back, ai_pain, and ai_painforward
     * ==============
     */
    public static AIAdapter ai_move = new AIAdapter() {
        public void ai(edict_t self, float dist) {
            M.M_walkmove(self, self.s.angles[Defines.YAW], dist);
        }
    };

    /*
     * ============= ai_walk
     * 
     * The monster is walking it's beat =============
     */
    public static AIAdapter ai_walk = new AIAdapter() {
        public void ai(edict_t self, float dist) {
            if (self.index == 312)
                self.index = 312;

            M.M_MoveToGoal(self, dist);

            // check for noticing a player
            if (GameUtil.FindTarget(self))
                return;

            if ((self.monsterinfo.search != null)
                    && (GameBase.level.time > self.monsterinfo.idle_time)) {
                if (self.monsterinfo.idle_time != 0) {
                    self.monsterinfo.search.think(self);
                    self.monsterinfo.idle_time = GameBase.level.time + 15
                            + Globals.rnd.nextFloat() * 15;
                } else {
                    self.monsterinfo.idle_time = GameBase.level.time
                            + Globals.rnd.nextFloat() * 15;
                }
            }
        }
    };

    /*
     * ============= ai_stand
     * 
     * Used for standing around and looking for players Distance is for slight
     * position adjustments needed by the animations ==============
     */

    public static AIAdapter ai_stand = new AIAdapter() {
        public void ai(edict_t self, float dist) {
            float[] v = { 0, 0, 0 };

            if (dist != 0)
                M.M_walkmove(self, self.s.angles[Defines.YAW], dist);

            if ((self.monsterinfo.aiflags & Defines.AI_STAND_GROUND) != 0) {
                if (self.enemy != null) {
                    Math3D
                            .VectorSubtract(self.enemy.s.origin, self.s.origin,
                                    v);
                    self.ideal_yaw = Math3D.vectoyaw(v);
                    if (self.s.angles[Defines.YAW] != self.ideal_yaw
                            && 0 != (self.monsterinfo.aiflags & Defines.AI_TEMP_STAND_GROUND)) {
                        self.monsterinfo.aiflags &= ~(Defines.AI_STAND_GROUND | Defines.AI_TEMP_STAND_GROUND);
                        self.monsterinfo.run.think(self);
                    }
                    M.M_ChangeYaw(self);
                    ai_checkattack(self, 0);
                } else
                    GameUtil.FindTarget(self);
                return;
            }

            if (GameUtil.FindTarget(self))
                return;

            if (GameBase.level.time > self.monsterinfo.pausetime) {
                self.monsterinfo.walk.think(self);
                return;
            }

            if (0 == (self.spawnflags & 1) && (self.monsterinfo.idle != null)
                    && (GameBase.level.time > self.monsterinfo.idle_time)) {
                if (self.monsterinfo.idle_time != 0) {
                    self.monsterinfo.idle.think(self);
                    self.monsterinfo.idle_time = GameBase.level.time + 15
                            + Globals.rnd.nextFloat() * 15;
                } else {
                    self.monsterinfo.idle_time = GameBase.level.time
                            + Globals.rnd.nextFloat() * 15;
                }
            }
        }
    };

    /*
     * ============= ai_charge
     * 
     * Turns towards target and advances Use this call with a distnace of 0 to
     * replace ai_face ==============
     */
    public static AIAdapter ai_charge = new AIAdapter() {

        public void ai(edict_t self, float dist) {
            float[] v = { 0, 0, 0 };

            Math3D.VectorSubtract(self.enemy.s.origin, self.s.origin, v);
            self.ideal_yaw = Math3D.vectoyaw(v);
            M.M_ChangeYaw(self);

            if (dist != 0)
                M.M_walkmove(self, self.s.angles[Defines.YAW], dist);
        }
    };

    /*
     * ============= ai_run
     * 
     * The monster has an enemy it is trying to kill =============
     *///ok
    public static AIAdapter ai_run = new AIAdapter() {
        public void ai(edict_t self, float dist) {
            float[] v = { 0, 0, 0 };

            edict_t tempgoal;
            edict_t save;
            boolean new1;
            edict_t marker;
            float d1, d2;
            trace_t tr; // mem
            float[] v_forward = { 0, 0, 0 }, v_right = { 0, 0, 0 };
            float left, center, right;
            float[] left_target = { 0, 0, 0 }, right_target = { 0, 0, 0 };

            // if we're going to a combat point, just proceed
            if ((self.monsterinfo.aiflags & Defines.AI_COMBAT_POINT) != 0) {
                M.M_MoveToGoal(self, dist);
                return;
            }

            if ((self.monsterinfo.aiflags & Defines.AI_SOUND_TARGET) != 0) {
                Math3D.VectorSubtract(self.s.origin, self.enemy.s.origin, v);
                if (Math3D.VectorLength(v) < 64) {
                    self.monsterinfo.aiflags |= (Defines.AI_STAND_GROUND | Defines.AI_TEMP_STAND_GROUND);
                    self.monsterinfo.stand.think(self);
                    return;
                }

                M.M_MoveToGoal(self, dist);

                if (!GameUtil.FindTarget(self))
                    return;
            }

            if (ai_checkattack(self, dist))
                return;

            if (self.monsterinfo.attack_state == Defines.AS_SLIDING) {
                ai_run_slide(self, dist);
                return;
            }

            if (GameUtil.enemy_vis) {
                //			if (self.aiflags & AI_LOST_SIGHT)
                //				dprint("regained sight\n");
                M.M_MoveToGoal(self, dist);
                self.monsterinfo.aiflags &= ~Defines.AI_LOST_SIGHT;
                Math3D.VectorCopy(self.enemy.s.origin,
                        self.monsterinfo.last_sighting);
                self.monsterinfo.trail_time = GameBase.level.time;
                return;
            }

            // coop will change to another enemy if visible
            if (GameBase.coop.value != 0) {
                // FIXME: insane guys get mad with this, which causes crashes!
                if (GameUtil.FindTarget(self))
                    return;
            }

            if ((self.monsterinfo.search_time != 0)
                    && (GameBase.level.time > (self.monsterinfo.search_time + 20))) {
                M.M_MoveToGoal(self, dist);
                self.monsterinfo.search_time = 0;
                //dprint("search timeout\n");
                return;
            }

            save = self.goalentity;
            tempgoal = GameUtil.G_Spawn();
            self.goalentity = tempgoal;

            new1 = false;

            if (0 == (self.monsterinfo.aiflags & Defines.AI_LOST_SIGHT)) {
                // just lost sight of the player, decide where to go first
                //			dprint("lost sight of player, last seen at ");
                // dprint(vtos(self.last_sighting)); dprint("\n");
                self.monsterinfo.aiflags |= (Defines.AI_LOST_SIGHT | Defines.AI_PURSUIT_LAST_SEEN);
                self.monsterinfo.aiflags &= ~(Defines.AI_PURSUE_NEXT | Defines.AI_PURSUE_TEMP);
                new1 = true;
            }

            if ((self.monsterinfo.aiflags & Defines.AI_PURSUE_NEXT) != 0) {
                self.monsterinfo.aiflags &= ~Defines.AI_PURSUE_NEXT;
                //			dprint("reached current goal: "); dprint(vtos(self.origin));
                // dprint(" "); dprint(vtos(self.last_sighting)); dprint(" ");
                // dprint(ftos(vlen(self.origin - self.last_sighting)));
                // dprint("\n");

                // give ourself more time since we got this far
                self.monsterinfo.search_time = GameBase.level.time + 5;

                if ((self.monsterinfo.aiflags & Defines.AI_PURSUE_TEMP) != 0) {
                    //				dprint("was temp goal; retrying original\n");
                    self.monsterinfo.aiflags &= ~Defines.AI_PURSUE_TEMP;
                    marker = null;
                    Math3D.VectorCopy(self.monsterinfo.saved_goal,
                            self.monsterinfo.last_sighting);
                    new1 = true;
                } else if ((self.monsterinfo.aiflags & Defines.AI_PURSUIT_LAST_SEEN) != 0) {
                    self.monsterinfo.aiflags &= ~Defines.AI_PURSUIT_LAST_SEEN;
                    marker = PlayerTrail.PickFirst(self);
                } else {
                    marker = PlayerTrail.PickNext(self);
                }

                if (marker != null) {
                    Math3D.VectorCopy(marker.s.origin,
                            self.monsterinfo.last_sighting);
                    self.monsterinfo.trail_time = marker.timestamp;
                    self.s.angles[Defines.YAW] = self.ideal_yaw = marker.s.angles[Defines.YAW];
                    //				dprint("heading is "); dprint(ftos(self.ideal_yaw));
                    // dprint("\n");

                    //				debug_drawline(self.origin, self.last_sighting, 52);
                    new1 = true;
                }
            }

            Math3D.VectorSubtract(self.s.origin,
                    self.monsterinfo.last_sighting, v);
            d1 = Math3D.VectorLength(v);
            if (d1 <= dist) {
                self.monsterinfo.aiflags |= Defines.AI_PURSUE_NEXT;
                dist = d1;
            }

            Math3D.VectorCopy(self.monsterinfo.last_sighting,
                    self.goalentity.s.origin);

            if (new1) {
                //			gi.dprintf("checking for course correction\n");

                tr = GameBase.gi.trace(self.s.origin, self.mins, self.maxs,
                        self.monsterinfo.last_sighting, self,
                        Defines.MASK_PLAYERSOLID);
                if (tr.fraction < 1) {
                    Math3D.VectorSubtract(self.goalentity.s.origin,
                            self.s.origin, v);
                    d1 = Math3D.VectorLength(v);
                    center = tr.fraction;
                    d2 = d1 * ((center + 1) / 2);
                    self.s.angles[Defines.YAW] = self.ideal_yaw = Math3D
                            .vectoyaw(v);
                    Math3D
                            .AngleVectors(self.s.angles, v_forward, v_right,
                                    null);

                    Math3D.VectorSet(v, d2, -16, 0);
                    Math3D.G_ProjectSource(self.s.origin, v, v_forward,
                            v_right, left_target);
                    tr = GameBase.gi.trace(self.s.origin, self.mins, self.maxs,
                            left_target, self, Defines.MASK_PLAYERSOLID);
                    left = tr.fraction;

                    Math3D.VectorSet(v, d2, 16, 0);
                    Math3D.G_ProjectSource(self.s.origin, v, v_forward,
                            v_right, right_target);
                    tr = GameBase.gi.trace(self.s.origin, self.mins, self.maxs,
                            right_target, self, Defines.MASK_PLAYERSOLID);
                    right = tr.fraction;

                    center = (d1 * center) / d2;
                    if (left >= center && left > right) {
                        if (left < 1) {
                            Math3D.VectorSet(v, d2 * left * 0.5f, -16f, 0f);
                            Math3D.G_ProjectSource(self.s.origin, v, v_forward,
                                    v_right, left_target);
                            //						gi.dprintf("incomplete path, go part way and
                            // adjust again\n");
                        }
                        Math3D.VectorCopy(self.monsterinfo.last_sighting,
                                self.monsterinfo.saved_goal);
                        self.monsterinfo.aiflags |= Defines.AI_PURSUE_TEMP;
                        Math3D
                                .VectorCopy(left_target,
                                        self.goalentity.s.origin);
                        Math3D.VectorCopy(left_target,
                                self.monsterinfo.last_sighting);
                        Math3D.VectorSubtract(self.goalentity.s.origin,
                                self.s.origin, v);
                        self.s.angles[Defines.YAW] = self.ideal_yaw = Math3D
                                .vectoyaw(v);
                        //					gi.dprintf("adjusted left\n");
                        //					debug_drawline(self.origin, self.last_sighting, 152);
                    } else if (right >= center && right > left) {
                        if (right < 1) {
                            Math3D.VectorSet(v, d2 * right * 0.5f, 16f, 0f);
                            Math3D.G_ProjectSource(self.s.origin, v, v_forward,
                                    v_right, right_target);
                            //						gi.dprintf("incomplete path, go part way and
                            // adjust again\n");
                        }
                        Math3D.VectorCopy(self.monsterinfo.last_sighting,
                                self.monsterinfo.saved_goal);
                        self.monsterinfo.aiflags |= Defines.AI_PURSUE_TEMP;
                        Math3D.VectorCopy(right_target,
                                self.goalentity.s.origin);
                        Math3D.VectorCopy(right_target,
                                self.monsterinfo.last_sighting);
                        Math3D.VectorSubtract(self.goalentity.s.origin,
                                self.s.origin, v);
                        self.s.angles[Defines.YAW] = self.ideal_yaw = Math3D
                                .vectoyaw(v);
                        //					gi.dprintf("adjusted right\n");
                        //					debug_drawline(self.origin, self.last_sighting, 152);
                    }
                }
                //			else gi.dprintf("course was fine\n");
            }

            M.M_MoveToGoal(self, dist);

            GameUtil.G_FreeEdict(tempgoal);

            if (self != null)
                self.goalentity = save;
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
                ent.touch = GameUtil.Touch_Item;
            }

            GameBase.gi.linkentity(ent);
        }
    };

    /*
     * ================ droptofloor ================
     */

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
            ent.touch = GameUtil.Touch_Item;

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
                    ent.think = GameUtil.DoRespawn;
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

    public static EntThinkAdapter gib_think = new EntThinkAdapter() {
        public boolean think(edict_t self) {
            self.s.frame++;
            self.nextthink = GameBase.level.time + Defines.FRAMETIME;

            if (self.s.frame == 10) {
                self.think = GameUtil.G_FreeEdictA;
                self.nextthink = GameBase.level.time + 8
                        + Globals.rnd.nextFloat() * 10;
            }
            return true;
        }
    };

    public static EntTouchAdapter gib_touch = new EntTouchAdapter() {
        public void touch(edict_t self, edict_t other, cplane_t plane,
                csurface_t surf) {
            float[] normal_angles = { 0, 0, 0 }, right = { 0, 0, 0 };

            if (null == self.groundentity)
                return;

            self.touch = null;

            if (plane != null) {
                GameBase.gi.sound(self, Defines.CHAN_VOICE, GameBase.gi
                        .soundindex("misc/fhit3.wav"), 1, Defines.ATTN_NORM, 0);

                Math3D.vectoangles(plane.normal, normal_angles);
                Math3D.AngleVectors(normal_angles, null, right, null);
                Math3D.vectoangles(right, self.s.angles);

                if (self.s.modelindex == GameBase.sm_meat_index) {
                    self.s.frame++;
                    self.think = gib_think;
                    self.nextthink = GameBase.level.time + Defines.FRAMETIME;
                }
            }
        }
    };

    public static EntDieAdapter gib_die = new EntDieAdapter() {
        public void die(edict_t self, edict_t inflictor, edict_t attacker,
                int damage, float[] point) {
            GameUtil.G_FreeEdict(self);
        }
    };

    /*
     * ================= debris =================
     */
    public static EntDieAdapter debris_die = new EntDieAdapter() {

        public void die(edict_t self, edict_t inflictor, edict_t attacker,
                int damage, float[] point) {
            GameUtil.G_FreeEdict(self);
        }
    };

    public static int player_die_i = 0;

    /*
     * ================== player_die ==================
     */
    static EntDieAdapter player_die = new EntDieAdapter() {
        public void die(edict_t self, edict_t inflictor, edict_t attacker,
                int damage, float[] point) {
            int n;

            Math3D.VectorClear(self.avelocity);

            self.takedamage = Defines.DAMAGE_YES;
            self.movetype = Defines.MOVETYPE_TOSS;

            self.s.modelindex2 = 0; // remove linked weapon model

            self.s.angles[0] = 0;
            self.s.angles[2] = 0;

            self.s.sound = 0;
            self.client.weapon_sound = 0;

            self.maxs[2] = -8;

            //		self.solid = SOLID_NOT;
            self.svflags |= Defines.SVF_DEADMONSTER;

            if (self.deadflag == 0) {
                self.client.respawn_time = GameBase.level.time + 1.0f;
                LookAtKiller(self, inflictor, attacker);
                self.client.ps.pmove.pm_type = Defines.PM_DEAD;
                ClientObituary(self, inflictor, attacker);
                TossClientWeapon(self);
                if (GameBase.deathmatch.value != 0)
                    Cmd.Help_f(self); // show scores

                // clear inventory
                // this is kind of ugly, but it's how we want to handle keys in
                // coop
                for (n = 0; n < GameBase.game.num_items; n++) {
                    if (GameBase.coop.value != 0
                            && (itemlist[n].flags & Defines.IT_KEY) != 0)
                        self.client.resp.coop_respawn.inventory[n] = self.client.pers.inventory[n];
                    self.client.pers.inventory[n] = 0;
                }
            }

            // remove powerups
            self.client.quad_framenum = 0;
            self.client.invincible_framenum = 0;
            self.client.breather_framenum = 0;
            self.client.enviro_framenum = 0;
            self.flags &= ~Defines.FL_POWER_ARMOR;

            if (self.health < -40) { // gib
                GameBase.gi
                        .sound(self, Defines.CHAN_BODY, GameBase.gi
                                .soundindex("misc/udeath.wav"), 1,
                                Defines.ATTN_NORM, 0);
                for (n = 0; n < 4; n++)
                    ThrowGib(self, "models/objects/gibs/sm_meat/tris.md2",
                            damage, Defines.GIB_ORGANIC);
                ThrowClientHead(self, damage);

                self.takedamage = Defines.DAMAGE_NO;
            } else { // normal death
                if (self.deadflag == 0) {

                    player_die_i = (player_die_i + 1) % 3;
                    // start a death animation
                    self.client.anim_priority = Defines.ANIM_DEATH;
                    if ((self.client.ps.pmove.pm_flags & pmove_t.PMF_DUCKED) != 0) {
                        self.s.frame = M_Player.FRAME_crdeath1 - 1;
                        self.client.anim_end = M_Player.FRAME_crdeath5;
                    } else
                        switch (player_die_i) {
                        case 0:
                            self.s.frame = M_Player.FRAME_death101 - 1;
                            self.client.anim_end = M_Player.FRAME_death106;
                            break;
                        case 1:
                            self.s.frame = M_Player.FRAME_death201 - 1;
                            self.client.anim_end = M_Player.FRAME_death206;
                            break;
                        case 2:
                            self.s.frame = M_Player.FRAME_death301 - 1;
                            self.client.anim_end = M_Player.FRAME_death308;
                            break;
                        }

                    GameBase.gi.sound(self, Defines.CHAN_VOICE, GameBase.gi
                            .soundindex("*death" + ((Lib.rand() % 4) + 1)
                                    + ".wav"), 1, Defines.ATTN_NORM, 0);
                }
            }

            self.deadflag = Defines.DEAD_DEAD;

            GameBase.gi.linkentity(self);
        }
    };

    public static Comparator PlayerSort = new Comparator() {
        public int compare(Object o1, Object o2) {
            int anum = ((Integer) o1).intValue();
            int bnum = ((Integer) o2).intValue();

            int anum1 = GameBase.game.clients[anum].ps.stats[Defines.STAT_FRAGS];
            int bnum1 = GameBase.game.clients[bnum].ps.stats[Defines.STAT_FRAGS];

            if (anum1 < bnum1)
                return -1;
            if (anum1 > bnum1)
                return 1;
            return 0;
        }
    };
}
