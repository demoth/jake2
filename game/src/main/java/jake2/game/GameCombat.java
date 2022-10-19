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
// $Id: GameCombat.java,v 1.3 2006-01-21 21:53:32 salomo Exp $

package jake2.game;

import jake2.game.items.GameItem;
import jake2.qcommon.Defines;
import jake2.qcommon.Globals;
import jake2.qcommon.edict_t;
import jake2.qcommon.network.MulticastTypes;
import jake2.qcommon.network.messages.server.PointDirectionTEMessage;
import jake2.qcommon.trace_t;
import jake2.qcommon.util.Math3D;

import static jake2.game.GameDefines.FL_NOTARGET;

public class GameCombat {

    /**
     * CanDamage
     * 
     * Returns true if the inflictor can directly damage the target. Used for
     * explosions and melee attacks.
     */
    static boolean CanDamage(SubgameEntity targ, edict_t inflictor, GameExportsImpl gameExports) {
        float[] dest = { 0, 0, 0 };
        trace_t trace;
    
        // bmodels need special checking because their origin is 0,0,0
        if (targ.movetype == GameDefines.MOVETYPE_PUSH) {
            Math3D.VectorAdd(targ.absmin, targ.absmax, dest);
            Math3D.VectorScale(dest, 0.5f, dest);
            trace = gameExports.gameImports.trace(inflictor.s.origin, Globals.vec3_origin,
                    Globals.vec3_origin, dest, inflictor, Defines.MASK_SOLID);
            if (trace.fraction == 1.0f)
                return true;
            if (trace.ent == targ)
                return true;
            return false;
        }
    
        trace = gameExports.gameImports.trace(inflictor.s.origin, Globals.vec3_origin,
                Globals.vec3_origin, targ.s.origin, inflictor,
                Defines.MASK_SOLID);
        if (trace.fraction == 1.0)
            return true;
    
        Math3D.VectorCopy(targ.s.origin, dest);
        dest[0] += 15.0;
        dest[1] += 15.0;
        trace = gameExports.gameImports.trace(inflictor.s.origin, Globals.vec3_origin,
                Globals.vec3_origin, dest, inflictor, Defines.MASK_SOLID);
        if (trace.fraction == 1.0)
            return true;
    
        Math3D.VectorCopy(targ.s.origin, dest);
        dest[0] += 15.0;
        dest[1] -= 15.0;
        trace = gameExports.gameImports.trace(inflictor.s.origin, Globals.vec3_origin,
                Globals.vec3_origin, dest, inflictor, Defines.MASK_SOLID);
        if (trace.fraction == 1.0)
            return true;
    
        Math3D.VectorCopy(targ.s.origin, dest);
        dest[0] -= 15.0;
        dest[1] += 15.0;
        trace = gameExports.gameImports.trace(inflictor.s.origin, Globals.vec3_origin,
                Globals.vec3_origin, dest, inflictor, Defines.MASK_SOLID);
        if (trace.fraction == 1.0)
            return true;
    
        Math3D.VectorCopy(targ.s.origin, dest);
        dest[0] -= 15.0;
        dest[1] -= 15.0;
        trace = gameExports.gameImports.trace(inflictor.s.origin, Globals.vec3_origin,
                Globals.vec3_origin, dest, inflictor, Defines.MASK_SOLID);
        if (trace.fraction == 1.0)
            return true;
    
        return false;
    }

    /**
     * Killed.
     */
    private static void Killed(SubgameEntity targ, SubgameEntity inflictor,
                               SubgameEntity attacker, int damage, float[] point, GameExportsImpl gameExports) {
        gameExports.gameImports.dprintf("Killing a " + targ.classname + "\n");
        if (targ.health < -999)
            targ.health = -999;
    
        targ.enemy = attacker;
    
        if ((targ.svflags & Defines.SVF_MONSTER) != 0
                && (targ.deadflag != GameDefines.DEAD_DEAD)) {
            //			targ.svflags |= SVF_DEADMONSTER; // now treat as a different
            // content type
            if (0 == (targ.monsterinfo.aiflags & GameDefines.AI_GOOD_GUY)) {
                gameExports.level.killed_monsters++;
                gclient_t attackerClient = attacker.getClient();
                if (gameExports.gameCvars.coop.value != 0 && attackerClient != null)
                    attackerClient.resp.score++;
                // medics won't heal monsters that they kill themselves
                if (attacker.classname.equals("monster_medic"))
                    targ.setOwner(attacker);
            }
        }
    
        if (targ.movetype == GameDefines.MOVETYPE_PUSH
                || targ.movetype == GameDefines.MOVETYPE_STOP
                || targ.movetype == GameDefines.MOVETYPE_NONE) { // doors, triggers,
                                                             // etc
            targ.die.die(targ, inflictor, attacker, damage, point, gameExports);
            return;
        }
    
        if ((targ.svflags & Defines.SVF_MONSTER) != 0
                && (targ.deadflag != GameDefines.DEAD_DEAD)) {
            targ.touch = null;
            Monster.monster_death_use(targ, gameExports);
        }
    
        targ.die.die(targ, inflictor, attacker, damage, point, gameExports);
    }

    /**
     * SpawnDamage.
     */
    private static void SpawnDamage(int type, float[] origin, float[] normal, int damage, GameExportsImpl gameExports) {
        gameExports.gameImports.multicastMessage(origin, new PointDirectionTEMessage(type, origin, normal), MulticastTypes.MULTICAST_PVS);
    }

    private static int CheckPowerArmor(SubgameEntity ent, float[] point, float[] normal,
                                       int damage, int dflags, GameExportsImpl gameExports) {
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
    
        client = ent.getClient();
    
        if ((dflags & Defines.DAMAGE_NO_ARMOR) != 0)
            return 0;
    
        if (client != null) {
            power_armor_type = GameItems.PowerArmorType(ent, gameExports);
            if (power_armor_type != GameDefines.POWER_ARMOR_NONE) {
                index = GameItems.FindItem("Cells", gameExports).index;
                power = client.pers.inventory[index];
            }
        } else if ((ent.svflags & Defines.SVF_MONSTER) != 0) {
            power_armor_type = ent.monsterinfo.power_armor_type;
            power = ent.monsterinfo.power_armor_power;
        } else
            return 0;
    
        if (power_armor_type == GameDefines.POWER_ARMOR_NONE)
            return 0;
        if (power == 0)
            return 0;
    
        if (power_armor_type == GameDefines.POWER_ARMOR_SCREEN) {
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
    
        SpawnDamage(pa_te_type, point, normal, save, gameExports);
        ent.powerarmor_time = gameExports.level.time + 0.2f;

        power_used = save / damagePerCell;
    
        if (client != null)
            client.pers.inventory[index] -= power_used;
        else
            ent.monsterinfo.power_armor_power -= power_used;
        return save;
    }

    private static int CheckArmor(SubgameEntity ent, float[] point, float[] normal,
                                  int damage, int te_sparks, int dflags, GameExportsImpl gameExports) {
        gclient_t client;
        int save;

        if (damage == 0)
            return 0;

        client = ent.getClient();

        if (client == null)
            return 0;

        if ((dflags & Defines.DAMAGE_NO_ARMOR) != 0)
            return 0;

        int index = GameItems.ArmorIndex(ent, gameExports);

        if (index == -1)
            return 0;

        GameItem armor = GameItems.GetItemByIndex(index, gameExports);
        gitem_armor_t garmor = armor.info;

        if (0 != (dflags & Defines.DAMAGE_ENERGY))
            save = (int) Math.ceil(garmor.energy_protection * damage);
        else
            save = (int) Math.ceil(garmor.normal_protection * damage);

        if (save >= client.pers.inventory[index])
            save = client.pers.inventory[index];

        if (save == 0)
            return 0;
    
        client.pers.inventory[index] -= save;
        SpawnDamage(te_sparks, point, normal, save, gameExports);
    
        return save;
    }

    private static void M_ReactToDamage(SubgameEntity targ, SubgameEntity attacker, GameExportsImpl gameExports) {
        if ((null != attacker.getClient())
                && 0 != (attacker.svflags & Defines.SVF_MONSTER))
            return;
    
        if (attacker == targ || attacker == targ.enemy)
            return;

        // do not react to notargets
        if ((attacker.flags & FL_NOTARGET) != 0) {
            return;
        }

        // if we are a good guy monster and our attacker is a player
        // or another good guy, do not get mad at them
        if (0 != (targ.monsterinfo.aiflags & GameDefines.AI_GOOD_GUY)) {
            if (attacker.getClient() != null
                    || (attacker.monsterinfo.aiflags & GameDefines.AI_GOOD_GUY) != 0)
                return;
        }
    
        // we now know that we are not both good guys
    
        // if attacker is a client, get mad at them because he's good and we're
        // not
        if (attacker.getClient() != null) {
            targ.monsterinfo.aiflags &= ~GameDefines.AI_SOUND_TARGET;
    
            // this can only happen in coop (both new and old enemies are
            // clients)
            // only switch if can't see the current enemy
            if (targ.enemy != null && targ.enemy.getClient() != null) {
                if (GameUtil.visible(targ, targ.enemy, gameExports)) {
                    targ.oldenemy = attacker;
                    return;
                }
                targ.oldenemy = targ.enemy;
            }
            targ.enemy = attacker;
            if (0 == (targ.monsterinfo.aiflags & GameDefines.AI_DUCKED))
                GameUtil.FoundTarget(targ, gameExports);
            return;
        }
    
        // it's the same base (walk/swim/fly) type and a different classname and
        // it's not a tank
        // (they spray too much), get mad at them
        if (((targ.flags & (GameDefines.FL_FLY | GameDefines.FL_SWIM)) == (attacker.flags & (GameDefines.FL_FLY | GameDefines.FL_SWIM)))
                && (!(targ.classname.equals(attacker.classname)))
                && (!(attacker.classname.equals("monster_tank")))
                && (!(attacker.classname.equals("monster_supertank")))
                && (!(attacker.classname.equals("monster_makron")))
                && (!(attacker.classname.equals("monster_jorg")))) {
            if (targ.enemy != null && targ.enemy.getClient() != null)
                targ.oldenemy = targ.enemy;
            targ.enemy = attacker;
            if (0 == (targ.monsterinfo.aiflags & GameDefines.AI_DUCKED))
                GameUtil.FoundTarget(targ, gameExports);
        }
        // if they *meant* to shoot us, then shoot back
        else if (attacker.enemy == targ) {
            if (targ.enemy != null && targ.enemy.getClient() != null)
                targ.oldenemy = targ.enemy;
            targ.enemy = attacker;
            if (0 == (targ.monsterinfo.aiflags & GameDefines.AI_DUCKED))
                GameUtil.FoundTarget(targ, gameExports);
        }
        // otherwise get mad at whoever they are mad at (help our buddy) unless
        // it is us!
        else if (attacker.enemy != null && attacker.enemy != targ) {
            if (targ.enemy != null && targ.enemy.getClient() != null)
                targ.oldenemy = targ.enemy;
            targ.enemy = attacker.enemy;
            if (0 == (targ.monsterinfo.aiflags & GameDefines.AI_DUCKED))
                GameUtil.FoundTarget(targ, gameExports);
        }
    }

    private static boolean CheckTeamDamage(edict_t targ, edict_t attacker) {
        //FIXME make the next line real and uncomment this block
        // if ((ability to damage a teammate == OFF) && (targ's team ==
        // attacker's team))
        return false;
    }

    /**
     * T_RadiusDamage.
     */
    static void T_RadiusDamage(SubgameEntity inflictor, SubgameEntity attacker,
                               float damage, edict_t ignore, float radius, int mod, GameExportsImpl gameExports) {
        float points;
        EdictIterator edictit = null;
    
        float[] v = { 0, 0, 0 };
        float[] dir = { 0, 0, 0 };
    
        while ((edictit = GameBase.findradius(edictit, inflictor.s.origin,
                radius, gameExports)) != null) {
            SubgameEntity ent = edictit.o;
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
                if (CanDamage(ent, inflictor, gameExports)) {
                    Math3D.VectorSubtract(ent.s.origin, inflictor.s.origin, dir);
                    T_Damage(ent, inflictor, attacker, dir, inflictor.s.origin,
                            Globals.vec3_origin, (int) points, (int) points,
                            Defines.DAMAGE_RADIUS, mod, gameExports);
                }
            }
        }
    }

    public static void T_Damage(SubgameEntity targ, SubgameEntity inflictor,
                                SubgameEntity attacker, float[] dir, float[] point, float[] normal,
                                int damage, int knockback, int dflags, int mod, GameExportsImpl gameExports) {
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
        final GameCvars cvars = gameExports.gameCvars;
        if ((targ != attacker)
                && ((cvars.deathmatch.value != 0 && 0 != ((int) (cvars.dmflags.value) & (Defines.DF_MODELTEAMS | Defines.DF_SKINTEAMS))) || cvars.coop.value != 0)) {
            if (GameUtil.OnSameTeam(targ, attacker, gameExports.gameCvars.dmflags.value)) {
                if (((int) (cvars.dmflags.value) & Defines.DF_NO_FRIENDLY_FIRE) != 0)
                    damage = 0;
                else
                    mod |= GameDefines.MOD_FRIENDLY_FIRE;
            }
        }
        targ.meansOfDeath = mod;
    
        // easy mode takes half damage
        if (cvars.skill.value == 0 && cvars.deathmatch.value == 0
                && targ.getClient() != null) {
            damage *= 0.5;
            if (damage == 0)
                damage = 1;
        }
    
        client = targ.getClient();
    
        if ((dflags & Defines.DAMAGE_BULLET) != 0)
            te_sparks = Defines.TE_BULLET_SPARKS;
        else
            te_sparks = Defines.TE_SPARKS;
    
        Math3D.VectorNormalize(dir);
    
        // bonus damage for suprising a monster
        if (0 == (dflags & Defines.DAMAGE_RADIUS)
                && (targ.svflags & Defines.SVF_MONSTER) != 0
                && (attacker.getClient() != null) && (targ.enemy == null)
                && (targ.health > 0))
            damage *= 2;
    
        if ((targ.flags & GameDefines.FL_NO_KNOCKBACK) != 0)
            knockback = 0;
    
        // figure momentum add
        if (0 == (dflags & Defines.DAMAGE_NO_KNOCKBACK)) {
            if ((knockback != 0) && (targ.movetype != GameDefines.MOVETYPE_NONE)
                    && (targ.movetype != GameDefines.MOVETYPE_BOUNCE)
                    && (targ.movetype != GameDefines.MOVETYPE_PUSH)
                    && (targ.movetype != GameDefines.MOVETYPE_STOP)) {
                float[] kvel = { 0, 0, 0 };
                float mass;
    
                if (targ.mass < 50)
                    mass = 50;
                else
                    mass = targ.mass;
    
                if (targ.getClient() != null && attacker == targ)
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
        if ((targ.flags & GameDefines.FL_GODMODE) != 0
                && 0 == (dflags & Defines.DAMAGE_NO_PROTECTION)) {
            take = 0;
            save = damage;
            SpawnDamage(te_sparks, point, normal, save, gameExports);
        }
    
        // check for invincibility
        if ((client != null && client.invincible_framenum > gameExports.level.framenum)
                && 0 == (dflags & Defines.DAMAGE_NO_PROTECTION)) {
            if (targ.pain_debounce_time < gameExports.level.time) {
                gameExports.gameImports.sound(targ, Defines.CHAN_ITEM, gameExports.gameImports
                        .soundindex("items/protect4.wav"), 1,
                        Defines.ATTN_NORM, 0);
                targ.pain_debounce_time = gameExports.level.time + 2;
            }
            take = 0;
            save = damage;
        }
    
        psave = CheckPowerArmor(targ, point, normal, take, dflags, gameExports);
        take -= psave;
    
        asave = CheckArmor(targ, point, normal, take, te_sparks, dflags, gameExports);
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
                SpawnDamage(Defines.TE_BLOOD, point, normal, take, gameExports);
            else
                SpawnDamage(te_sparks, point, normal, take, gameExports);
    
            targ.health = targ.health - take;
    
            if (targ.health <= 0) {
                if ((targ.svflags & Defines.SVF_MONSTER) != 0
                        || (client != null))
                    targ.flags |= GameDefines.FL_NO_KNOCKBACK;
                Killed(targ, inflictor, attacker, take, point, gameExports);
                return;
            }
        }
    
        if ((targ.svflags & Defines.SVF_MONSTER) != 0) {
            M_ReactToDamage(targ, attacker, gameExports);
            if (0 == (targ.monsterinfo.aiflags & GameDefines.AI_DUCKED)
                    && (take != 0)) {
                targ.pain.pain(targ, attacker, knockback, take, gameExports);
                // nightmare mode monsters don't go into pain frames often
                if (cvars.skill.value == 3)
                    targ.pain_debounce_time = gameExports.level.time + 5;
            }
        } else if (client != null) {
            if (((targ.flags & GameDefines.FL_GODMODE) == 0) && (take != 0))
                targ.pain.pain(targ, attacker, knockback, take, gameExports);
        } else if (take != 0) {
            if (targ.pain != null)
                targ.pain.pain(targ, attacker, knockback, take, gameExports);
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
