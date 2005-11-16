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
// $Id: GameCombat.java,v 1.1 2005-11-16 22:24:53 salomo Exp $

package jake2.game;

import jake2.Defines;
import jake2.Globals;
import jake2.qcommon.Com;
import jake2.util.Math3D;

public class GameCombat {

    /*
     * ============ CanDamage
     * 
     * Returns true if the inflictor can directly damage the target. Used for
     * explosions and melee attacks. ============
     */
    static boolean CanDamage(edict_t targ, edict_t inflictor) {
        float[] dest = { 0, 0, 0 };
        trace_t trace;
    
        // bmodels need special checking because their origin is 0,0,0
        if (targ.movetype == Defines.MOVETYPE_PUSH) {
            Math3D.VectorAdd(targ.absmin, targ.absmax, dest);
            Math3D.VectorScale(dest, 0.5f, dest);
            trace = GameBase.gi.trace(inflictor.s.origin, Globals.vec3_origin,
                    Globals.vec3_origin, dest, inflictor, Defines.MASK_SOLID);
            if (trace.fraction == 1.0f)
                return true;
            if (trace.ent == targ)
                return true;
            return false;
        }
    
        trace = GameBase.gi.trace(inflictor.s.origin, Globals.vec3_origin,
                Globals.vec3_origin, targ.s.origin, inflictor,
                Defines.MASK_SOLID);
        if (trace.fraction == 1.0)
            return true;
    
        Math3D.VectorCopy(targ.s.origin, dest);
        dest[0] += 15.0;
        dest[1] += 15.0;
        trace = GameBase.gi.trace(inflictor.s.origin, Globals.vec3_origin,
                Globals.vec3_origin, dest, inflictor, Defines.MASK_SOLID);
        if (trace.fraction == 1.0)
            return true;
    
        Math3D.VectorCopy(targ.s.origin, dest);
        dest[0] += 15.0;
        dest[1] -= 15.0;
        trace = GameBase.gi.trace(inflictor.s.origin, Globals.vec3_origin,
                Globals.vec3_origin, dest, inflictor, Defines.MASK_SOLID);
        if (trace.fraction == 1.0)
            return true;
    
        Math3D.VectorCopy(targ.s.origin, dest);
        dest[0] -= 15.0;
        dest[1] += 15.0;
        trace = GameBase.gi.trace(inflictor.s.origin, Globals.vec3_origin,
                Globals.vec3_origin, dest, inflictor, Defines.MASK_SOLID);
        if (trace.fraction == 1.0)
            return true;
    
        Math3D.VectorCopy(targ.s.origin, dest);
        dest[0] -= 15.0;
        dest[1] -= 15.0;
        trace = GameBase.gi.trace(inflictor.s.origin, Globals.vec3_origin,
                Globals.vec3_origin, dest, inflictor, Defines.MASK_SOLID);
        if (trace.fraction == 1.0)
            return true;
    
        return false;
    }

    /*
     * ============ Killed ============
     */
    public static void Killed(edict_t targ, edict_t inflictor,
            edict_t attacker, int damage, float[] point) {
        Com.DPrintf("Killing a " + targ.classname + "\n");
        if (targ.health < -999)
            targ.health = -999;
    
        //Com.Println("Killed:" + targ.classname);
        targ.enemy = attacker;
    
        if ((targ.svflags & Defines.SVF_MONSTER) != 0
                && (targ.deadflag != Defines.DEAD_DEAD)) {
            //			targ.svflags |= SVF_DEADMONSTER; // now treat as a different
            // content type
            if (0 == (targ.monsterinfo.aiflags & Defines.AI_GOOD_GUY)) {
                GameBase.level.killed_monsters++;
                if (GameBase.coop.value != 0 && attacker.client != null)
                    attacker.client.resp.score++;
                // medics won't heal monsters that they kill themselves
                if (attacker.classname.equals("monster_medic"))
                    targ.owner = attacker;
            }
        }
    
        if (targ.movetype == Defines.MOVETYPE_PUSH
                || targ.movetype == Defines.MOVETYPE_STOP
                || targ.movetype == Defines.MOVETYPE_NONE) { // doors, triggers,
                                                             // etc
            targ.die.die(targ, inflictor, attacker, damage, point);
            return;
        }
    
        if ((targ.svflags & Defines.SVF_MONSTER) != 0
                && (targ.deadflag != Defines.DEAD_DEAD)) {
            targ.touch = null;
            Monster.monster_death_use(targ);
        }
    
        targ.die.die(targ, inflictor, attacker, damage, point);
    }

    /*
     * ================ SpawnDamage ================
     */
    static void SpawnDamage(int type, float[] origin, float[] normal, int damage) {
        if (damage > 255)
            damage = 255;
        GameBase.gi.WriteByte(Defines.svc_temp_entity);
        GameBase.gi.WriteByte(type);
        //		gi.WriteByte (damage);
        GameBase.gi.WritePosition(origin);
        GameBase.gi.WriteDir(normal);
        GameBase.gi.multicast(origin, Defines.MULTICAST_PVS);
    }

    static int CheckPowerArmor(edict_t ent, float[] point, float[] normal,
            int damage, int dflags) {
        gclient_t client;
        int save;
        int power_armor_type;
        int index = 0;
        int damagePerCell;
        int pa_te_type;
        int power = 0;
        int power_used;
    
        if (damage == 0)
            return 0;
    
        client = ent.client;
    
        if ((dflags & Defines.DAMAGE_NO_ARMOR) != 0)
            return 0;
    
        if (client != null) {
            power_armor_type = GameItems.PowerArmorType(ent);
            if (power_armor_type != Defines.POWER_ARMOR_NONE) {
                index = GameItems.ITEM_INDEX(GameItems.FindItem("Cells"));
                power = client.pers.inventory[index];
            }
        } else if ((ent.svflags & Defines.SVF_MONSTER) != 0) {
            power_armor_type = ent.monsterinfo.power_armor_type;
            power = ent.monsterinfo.power_armor_power;
        } else
            return 0;
    
        if (power_armor_type == Defines.POWER_ARMOR_NONE)
            return 0;
        if (power == 0)
            return 0;
    
        if (power_armor_type == Defines.POWER_ARMOR_SCREEN) {
            float[] vec = { 0, 0, 0 };
            float dot;
            float[] forward = { 0, 0, 0 };
    
            // only works if damage point is in front
            Math3D.AngleVectors(ent.s.angles, forward, null, null);
            Math3D.VectorSubtract(point, ent.s.origin, vec);
            Math3D.VectorNormalize(vec);
            dot = Math3D.DotProduct(vec, forward);
            if (dot <= 0.3)
                return 0;
    
            damagePerCell = 1;
            pa_te_type = Defines.TE_SCREEN_SPARKS;
            damage = damage / 3;
        } else {
            damagePerCell = 2;
            pa_te_type = Defines.TE_SHIELD_SPARKS;
            damage = (2 * damage) / 3;
        }
    
        save = power * damagePerCell;
    
        if (save == 0)
            return 0;
        if (save > damage)
            save = damage;
    
        SpawnDamage(pa_te_type, point, normal, save);
        ent.powerarmor_time = GameBase.level.time + 0.2f;
    
        power_used = save / damagePerCell;
    
        if (client != null)
            client.pers.inventory[index] -= power_used;
        else
            ent.monsterinfo.power_armor_power -= power_used;
        return save;
    }

    static int CheckArmor(edict_t ent, float[] point, float[] normal,
            int damage, int te_sparks, int dflags) {
        gclient_t client;
        int save;
        int index;
        gitem_t armor;
    
        if (damage == 0)
            return 0;
    
        client = ent.client;
    
        if (client != null)
            return 0;
    
        if ((dflags & Defines.DAMAGE_NO_ARMOR) != 0)
            return 0;
    
        index = GameItems.ArmorIndex(ent);
    
        if (index == 0)
            return 0;
    
        armor = GameItems.GetItemByIndex(index);
        gitem_armor_t garmor = (gitem_armor_t) armor.info;
    
        if (0 != (dflags & Defines.DAMAGE_ENERGY))
            save = (int) Math.ceil(garmor.energy_protection * damage);
        else
            save = (int) Math.ceil(garmor.normal_protection * damage);
    
        if (save >= client.pers.inventory[index])
            save = client.pers.inventory[index];
    
        if (save == 0)
            return 0;
    
        client.pers.inventory[index] -= save;
        SpawnDamage(te_sparks, point, normal, save);
    
        return save;
    }

    public static void M_ReactToDamage(edict_t targ, edict_t attacker) {
        if ((null != attacker.client)
                && 0 != (attacker.svflags & Defines.SVF_MONSTER))
            return;
    
        if (attacker == targ || attacker == targ.enemy)
            return;
    
        // if we are a good guy monster and our attacker is a player
        // or another good guy, do not get mad at them
        if (0 != (targ.monsterinfo.aiflags & Defines.AI_GOOD_GUY)) {
            if (attacker.client != null
                    || (attacker.monsterinfo.aiflags & Defines.AI_GOOD_GUY) != 0)
                return;
        }
    
        // we now know that we are not both good guys
    
        // if attacker is a client, get mad at them because he's good and we're
        // not
        if (attacker.client != null) {
            targ.monsterinfo.aiflags &= ~Defines.AI_SOUND_TARGET;
    
            // this can only happen in coop (both new and old enemies are
            // clients)
            // only switch if can't see the current enemy
            if (targ.enemy != null && targ.enemy.client != null) {
                if (GameUtil.visible(targ, targ.enemy)) {
                    targ.oldenemy = attacker;
                    return;
                }
                targ.oldenemy = targ.enemy;
            }
            targ.enemy = attacker;
            if (0 == (targ.monsterinfo.aiflags & Defines.AI_DUCKED))
                GameUtil.FoundTarget(targ);
            return;
        }
    
        // it's the same base (walk/swim/fly) type and a different classname and
        // it's not a tank
        // (they spray too much), get mad at them
        if (((targ.flags & (Defines.FL_FLY | Defines.FL_SWIM)) == (attacker.flags & (Defines.FL_FLY | Defines.FL_SWIM)))
                && (!(targ.classname.equals(attacker.classname)))
                && (!(attacker.classname.equals("monster_tank")))
                && (!(attacker.classname.equals("monster_supertank")))
                && (!(attacker.classname.equals("monster_makron")))
                && (!(attacker.classname.equals("monster_jorg")))) {
            if (targ.enemy != null && targ.enemy.client != null)
                targ.oldenemy = targ.enemy;
            targ.enemy = attacker;
            if (0 == (targ.monsterinfo.aiflags & Defines.AI_DUCKED))
                GameUtil.FoundTarget(targ);
        }
        // if they *meant* to shoot us, then shoot back
        else if (attacker.enemy == targ) {
            if (targ.enemy != null && targ.enemy.client != null)
                targ.oldenemy = targ.enemy;
            targ.enemy = attacker;
            if (0 == (targ.monsterinfo.aiflags & Defines.AI_DUCKED))
                GameUtil.FoundTarget(targ);
        }
        // otherwise get mad at whoever they are mad at (help our buddy) unless
        // it is us!
        else if (attacker.enemy != null && attacker.enemy != targ) {
            if (targ.enemy != null && targ.enemy.client != null)
                targ.oldenemy = targ.enemy;
            targ.enemy = attacker.enemy;
            if (0 == (targ.monsterinfo.aiflags & Defines.AI_DUCKED))
                GameUtil.FoundTarget(targ);
        }
    }

    static boolean CheckTeamDamage(edict_t targ, edict_t attacker) {
        //FIXME make the next line real and uncomment this block
        // if ((ability to damage a teammate == OFF) && (targ's team ==
        // attacker's team))
        return false;
    }

    /*
     * ============ T_RadiusDamage ============
     */
    static void T_RadiusDamage(edict_t inflictor, edict_t attacker,
            float damage, edict_t ignore, float radius, int mod) {
        float points;
        EdictIterator edictit = null;
    
        float[] v = { 0, 0, 0 };
        float[] dir = { 0, 0, 0 };
    
        while ((edictit = GameBase.findradius(edictit, inflictor.s.origin,
                radius)) != null) {
            edict_t ent = edictit.o;
            if (ent == ignore)
                continue;
            if (ent.takedamage == 0)
                continue;
    
            Math3D.VectorAdd(ent.mins, ent.maxs, v);
            Math3D.VectorMA(ent.s.origin, 0.5f, v, v);
            Math3D.VectorSubtract(inflictor.s.origin, v, v);
            points = damage - 0.5f * Math3D.VectorLength(v);
            if (ent == attacker)
                points = points * 0.5f;
            if (points > 0) {
                if (CanDamage(ent, inflictor)) {
                    Math3D.VectorSubtract(ent.s.origin, inflictor.s.origin, dir);
                    T_Damage(ent, inflictor, attacker, dir, inflictor.s.origin,
                            Globals.vec3_origin, (int) points, (int) points,
                            Defines.DAMAGE_RADIUS, mod);
                }
            }
        }
    }

    public static void T_Damage(edict_t targ, edict_t inflictor,
            edict_t attacker, float[] dir, float[] point, float[] normal,
            int damage, int knockback, int dflags, int mod) {
        gclient_t client;
        int take;
        int save;
        int asave;
        int psave;
        int te_sparks;
    
        if (targ.takedamage == 0)
            return;
    
        // friendly fire avoidance
        // if enabled you can't hurt teammates (but you can hurt yourself)
        // knockback still occurs
        if ((targ != attacker)
                && ((GameBase.deathmatch.value != 0 && 0 != ((int) (GameBase.dmflags.value) & (Defines.DF_MODELTEAMS | Defines.DF_SKINTEAMS))) || GameBase.coop.value != 0)) {
            if (GameUtil.OnSameTeam(targ, attacker)) {
                if (((int) (GameBase.dmflags.value) & Defines.DF_NO_FRIENDLY_FIRE) != 0)
                    damage = 0;
                else
                    mod |= Defines.MOD_FRIENDLY_FIRE;
            }
        }
        GameBase.meansOfDeath = mod;
    
        // easy mode takes half damage
        if (GameBase.skill.value == 0 && GameBase.deathmatch.value == 0
                && targ.client != null) {
            damage *= 0.5;
            if (damage == 0)
                damage = 1;
        }
    
        client = targ.client;
    
        if ((dflags & Defines.DAMAGE_BULLET) != 0)
            te_sparks = Defines.TE_BULLET_SPARKS;
        else
            te_sparks = Defines.TE_SPARKS;
    
        Math3D.VectorNormalize(dir);
    
        //	   bonus damage for suprising a monster
        if (0 == (dflags & Defines.DAMAGE_RADIUS)
                && (targ.svflags & Defines.SVF_MONSTER) != 0
                && (attacker.client != null) && (targ.enemy == null)
                && (targ.health > 0))
            damage *= 2;
    
        if ((targ.flags & Defines.FL_NO_KNOCKBACK) != 0)
            knockback = 0;
    
        //	   figure momentum add
        if (0 == (dflags & Defines.DAMAGE_NO_KNOCKBACK)) {
            if ((knockback != 0) && (targ.movetype != Defines.MOVETYPE_NONE)
                    && (targ.movetype != Defines.MOVETYPE_BOUNCE)
                    && (targ.movetype != Defines.MOVETYPE_PUSH)
                    && (targ.movetype != Defines.MOVETYPE_STOP)) {
                float[] kvel = { 0, 0, 0 };
                float mass;
    
                if (targ.mass < 50)
                    mass = 50;
                else
                    mass = targ.mass;
    
                if (targ.client != null && attacker == targ)
                    Math3D.VectorScale(dir, 1600.0f * (float) knockback / mass,
                            kvel);
                // the rocket jump hack...
                else
                    Math3D.VectorScale(dir, 500.0f * (float) knockback / mass,
                            kvel);
    
                Math3D.VectorAdd(targ.velocity, kvel, targ.velocity);
            }
        }
    
        take = damage;
        save = 0;
    
        // check for godmode
        if ((targ.flags & Defines.FL_GODMODE) != 0
                && 0 == (dflags & Defines.DAMAGE_NO_PROTECTION)) {
            take = 0;
            save = damage;
            SpawnDamage(te_sparks, point, normal, save);
        }
    
        // check for invincibility
        if ((client != null && client.invincible_framenum > GameBase.level.framenum)
                && 0 == (dflags & Defines.DAMAGE_NO_PROTECTION)) {
            if (targ.pain_debounce_time < GameBase.level.time) {
                GameBase.gi.sound(targ, Defines.CHAN_ITEM, GameBase.gi
                        .soundindex("items/protect4.wav"), 1,
                        Defines.ATTN_NORM, 0);
                targ.pain_debounce_time = GameBase.level.time + 2;
            }
            take = 0;
            save = damage;
        }
    
        psave = CheckPowerArmor(targ, point, normal, take, dflags);
        take -= psave;
    
        asave = CheckArmor(targ, point, normal, take, te_sparks, dflags);
        take -= asave;
    
        // treat cheat/powerup savings the same as armor
        asave += save;
    
        // team damage avoidance
        if (0 == (dflags & Defines.DAMAGE_NO_PROTECTION)
                && CheckTeamDamage(targ, attacker))
            return;
    
        // do the damage
        if (take != 0) {
            if (0 != (targ.svflags & Defines.SVF_MONSTER) || (client != null))
                SpawnDamage(Defines.TE_BLOOD, point, normal, take);
            else
                SpawnDamage(te_sparks, point, normal, take);
    
            targ.health = targ.health - take;
    
            if (targ.health <= 0) {
                if ((targ.svflags & Defines.SVF_MONSTER) != 0
                        || (client != null))
                    targ.flags |= Defines.FL_NO_KNOCKBACK;
                Killed(targ, inflictor, attacker, take, point);
                return;
            }
        }
    
        if ((targ.svflags & Defines.SVF_MONSTER) != 0) {
            M_ReactToDamage(targ, attacker);
            if (0 == (targ.monsterinfo.aiflags & Defines.AI_DUCKED)
                    && (take != 0)) {
                targ.pain.pain(targ, attacker, knockback, take);
                // nightmare mode monsters don't go into pain frames often
                if (GameBase.skill.value == 3)
                    targ.pain_debounce_time = GameBase.level.time + 5;
            }
        } else if (client != null) {
            if (((targ.flags & Defines.FL_GODMODE) == 0) && (take != 0))
                targ.pain.pain(targ, attacker, knockback, take);
        } else if (take != 0) {
            if (targ.pain != null)
                targ.pain.pain(targ, attacker, knockback, take);
        }
    
        // add to the damage inflicted on a player this frame
        // the total will be turned into screen blends and view angle kicks
        // at the end of the frame
        if (client != null) {
            client.damage_parmor += psave;
            client.damage_armor += asave;
            client.damage_blood += take;
            client.damage_knockback += knockback;
            Math3D.VectorCopy(point, client.damage_from);
        }
    }

}
