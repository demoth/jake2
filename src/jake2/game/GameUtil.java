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

// Created on 01.11.2003 by RST.
// $Id: GameUtil.java,v 1.12 2005-02-20 16:38:36 salomo Exp $
package jake2.game;

import jake2.Defines;
import jake2.Globals;
import jake2.client.M;
import jake2.qcommon.Com;
import jake2.util.Lib;
import jake2.util.Math3D;

public class GameUtil {

    public static void checkClassname(edict_t ent) {

        if (ent.classname == null) {
            Com.Printf("edict with classname = null: " + ent.index);
        }
    }

    /**
     * the global "activator" should be set to the entity that initiated the
     * firing.
     * 
     * If self.delay is set, a DelayedUse entity will be created that will
     * actually do the SUB_UseTargets after that many seconds have passed.
     * 
     * Centerprints any self.message to the activator.
     * 
     * Search for (string)targetname in all entities that match
     * (string)self.target and call their .use function
     */

    public static void G_UseTargets(edict_t ent, edict_t activator) {
        edict_t t;

        checkClassname(ent);

        //
        //	   check for a delay
        //
        if (ent.delay != 0) {
            // create a temp object to fire at a later time
            t = G_Spawn();
            t.classname = "DelayedUse";
            t.nextthink = GameBase.level.time + ent.delay;
            t.think = GameUtil.Think_Delay;
            t.activator = activator;
            if (activator == null)
                GameBase.gi.dprintf("Think_Delay with no activator\n");
            t.message = ent.message;
            t.target = ent.target;
            t.killtarget = ent.killtarget;
            return;
        }

        //
        //	   print the message
        //
        if ((ent.message != null)
                && (activator.svflags & Defines.SVF_MONSTER) == 0) {
            GameBase.gi.centerprintf(activator, "" + ent.message);
            if (ent.noise_index != 0)
                GameBase.gi.sound(activator, Defines.CHAN_AUTO,
                        ent.noise_index, 1, Defines.ATTN_NORM, 0);
            else
                GameBase.gi.sound(activator, Defines.CHAN_AUTO, GameBase.gi
                        .soundindex("misc/talk1.wav"), 1, Defines.ATTN_NORM, 0);
        }

        //
        // kill killtargets
        //

        EdictIterator edit = null;

        if (ent.killtarget != null) {
            while ((edit = GameBase.G_Find(edit, GameBase.findByTarget,
                    ent.killtarget)) != null) {
                t = edit.o;
                G_FreeEdict(t);
                if (!ent.inuse) {
                    GameBase.gi
                            .dprintf("entity was removed while using killtargets\n");
                    return;
                }
            }
        }

        // fire targets

        if (ent.target != null) {
            edit = null;
            while ((edit = GameBase.G_Find(edit, GameBase.findByTarget,
                    ent.target)) != null) {
                t = edit.o;
                // doors fire area portals in a specific way
                if (Lib.Q_stricmp("func_areaportal", t.classname) == 0
                        && (Lib.Q_stricmp("func_door", ent.classname) == 0 || Lib
                                .Q_stricmp("func_door_rotating", ent.classname) == 0))
                    continue;

                if (t == ent) {
                    GameBase.gi.dprintf("WARNING: Entity used itself.\n");
                } else {
                    if (t.use != null)
                        t.use.use(t, ent, activator);
                }
                if (!ent.inuse) {
                    GameBase.gi
                            .dprintf("entity was removed while using targets\n");
                    return;
                }
            }
        }
    }

    public static void G_InitEdict(edict_t e, int i) {
        e.inuse = true;
        e.classname = "noclass";
        e.gravity = 1.0f;
        //e.s.number= e - g_edicts;
        e.s = new entity_state_t(e);
        e.s.number = i;
        e.index = i;
    }

    /**
     * Either finds a free edict, or allocates a new one. Try to avoid reusing
     * an entity that was recently freed, because it can cause the client to
     * think the entity morphed into something else instead of being removed and
     * recreated, which can cause interpolated angles and bad trails.
     */
    public static edict_t G_Spawn() {
        int i;
        edict_t e = null;

        for (i = (int) GameBase.maxclients.value + 1; i < GameBase.num_edicts; i++) {
            e = GameBase.g_edicts[i];
            // the first couple seconds of server time can involve a lot of
            // freeing and allocating, so relax the replacement policy
            if (!e.inuse
                    && (e.freetime < 2 || GameBase.level.time - e.freetime > 0.5)) {
                e = GameBase.g_edicts[i] = new edict_t(i);
                G_InitEdict(e, i);
                return e;
            }
        }

        if (i == GameBase.game.maxentities)
            GameBase.gi.error("ED_Alloc: no free edicts");

        e = GameBase.g_edicts[i] = new edict_t(i);
        GameBase.num_edicts++;
        G_InitEdict(e, i);
        return e;
    }

    /**
     * Marks the edict as free
     */
    public static void G_FreeEdict(edict_t ed) {
        GameBase.gi.unlinkentity(ed); // unlink from world

        //if ((ed - g_edicts) <= (maxclients.value + BODY_QUEUE_SIZE))
        if (ed.index <= (GameBase.maxclients.value + Defines.BODY_QUEUE_SIZE)) {
            //			gi.dprintf("tried to free special edict\n");
            return;
        }

        //memset(ed, 0, sizeof(* ed));
        GameBase.g_edicts[ed.index] = new edict_t(ed.index);
        //ed.clear();
        ed.classname = "freed";
        ed.freetime = GameBase.level.time;
        ed.inuse = false;
    }

    /**
     * Call after linking a new trigger in during gameplay to force all entities
     * it covers to immediately touch it.
     */

    public static void G_ClearEdict(edict_t ent) {
        int i = ent.index;
        GameBase.g_edicts[i] = new edict_t(i);
    }

    public static void G_TouchSolids(edict_t ent) {
        int i, num;
        edict_t touch[] = new edict_t[Defines.MAX_EDICTS], hit;

        num = GameBase.gi.BoxEdicts(ent.absmin, ent.absmax, touch,
                Defines.MAX_EDICTS, Defines.AREA_SOLID);

        // be careful, it is possible to have an entity in this
        // list removed before we get to it (killtriggered)
        for (i = 0; i < num; i++) {
            hit = touch[i];
            if (!hit.inuse)
                continue;
            if (ent.touch != null) {
                ent.touch.touch(hit, ent, GameBase.dummyplane, null);
            }
            if (!ent.inuse)
                break;
        }
    }

    /**
     * Kills all entities that would touch the proposed new positioning of ent.
     * Ent should be unlinked before calling this!
     */

    public static boolean KillBox(edict_t ent) {
        trace_t tr;

        while (true) {
            tr = GameBase.gi.trace(ent.s.origin, ent.mins, ent.maxs,
                    ent.s.origin, null, Defines.MASK_PLAYERSOLID);
            if (tr.ent == null || tr.ent == GameBase.g_edicts[0])
                break;

            // nail it
            T_Damage(tr.ent, ent, ent, Globals.vec3_origin, ent.s.origin,
                    Globals.vec3_origin, 100000, 0,
                    Defines.DAMAGE_NO_PROTECTION, Defines.MOD_TELEFRAG);

            // if we didn't kill it, fail
            if (tr.ent.solid != 0)
                return false;
        }

        return true; // all clear
    }

    public static boolean OnSameTeam(edict_t ent1, edict_t ent2) {
        if (0 == ((int) (GameBase.dmflags.value) & (Defines.DF_MODELTEAMS | Defines.DF_SKINTEAMS)))
            return false;

        if (ClientTeam(ent1).equals(ClientTeam(ent2)))
            return true;
        return false;
    }

    static String ClientTeam(edict_t ent) {
        String value;

        if (ent.client == null)
            return "";

        value = Info.Info_ValueForKey(ent.client.pers.userinfo, "skin");

        int p = value.indexOf("/");

        if (p == -1)
            return value;

        if (((int) (GameBase.dmflags.value) & Defines.DF_MODELTEAMS) != 0) {
            return value.substring(0, p);
        }

        return value.substring(p + 1, value.length());
    }

    static void SetRespawn(edict_t ent, float delay) {
        ent.flags |= Defines.FL_RESPAWN;
        ent.svflags |= Defines.SVF_NOCLIENT;
        ent.solid = Defines.SOLID_NOT;
        ent.nextthink = GameBase.level.time + delay;
        ent.think = GameUtil.DoRespawn;
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

        dropped = G_Spawn();

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

        dropped.touch = GameUtil.drop_temp_touch;

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

        dropped.think = GameUtil.drop_make_touchable;
        dropped.nextthink = GameBase.level.time + 1;

        GameBase.gi.linkentity(dropped);

        return dropped;
    }

    static void ValidateSelectedItem(edict_t ent) {
        gclient_t cl;

        cl = ent.client;

        if (cl.pers.inventory[cl.pers.selected_item] != 0)
            return; // valid

        GameAI.SelectNextItem(ent, -1);
    }

    static void Use_Item(edict_t ent, edict_t other, edict_t activator) {
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
            if (OnSameTeam(targ, attacker)) {
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
            M.M_ReactToDamage(targ, attacker);
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

    static int PowerArmorType(edict_t ent) {
        if (ent.client == null)
            return Defines.POWER_ARMOR_NONE;

        if (0 == (ent.flags & Defines.FL_POWER_ARMOR))
            return Defines.POWER_ARMOR_NONE;

        if (ent.client.pers.inventory[GameUtil.power_shield_index] > 0)
            return Defines.POWER_ARMOR_SHIELD;

        if (ent.client.pers.inventory[GameUtil.power_screen_index] > 0)
            return Defines.POWER_ARMOR_SCREEN;

        return Defines.POWER_ARMOR_NONE;
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
            power_armor_type = PowerArmorType(ent);
            if (power_armor_type != Defines.POWER_ARMOR_NONE) {
                index = ITEM_INDEX(FindItem("Cells"));
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

    /**
     * The monster is walking it's beat.
     *  
     */
    static void ai_walk(edict_t self, float dist) {
        M.M_MoveToGoal(self, dist);

        // check for noticing a player
        if (FindTarget(self))
            return;

        if ((self.monsterinfo.search != null)
                && (GameBase.level.time > self.monsterinfo.idle_time)) {
            if (self.monsterinfo.idle_time != 0) {
                self.monsterinfo.search.think(self);
                self.monsterinfo.idle_time = GameBase.level.time + 15
                        + Lib.random() * 15;
            } else {
                self.monsterinfo.idle_time = GameBase.level.time + Lib.random()
                        * 15;
            }
        }
    }

    /*
     * ============= range
     * 
     * returns the range catagorization of an entity reletive to self 0 melee
     * range, will become hostile even if back is turned 1 visibility and
     * infront, or visibility and show hostile 2 infront and show hostile 3 only
     * triggered by damage =============
     */
    public static int range(edict_t self, edict_t other) {
        float[] v = { 0, 0, 0 };
        float len;

        Math3D.VectorSubtract(self.s.origin, other.s.origin, v);
        len = Math3D.VectorLength(v);
        if (len < Defines.MELEE_DISTANCE)
            return Defines.RANGE_MELEE;
        if (len < 500)
            return Defines.RANGE_NEAR;
        if (len < 1000)
            return Defines.RANGE_MID;
        return Defines.RANGE_FAR;
    }

    /*
     * =============== FindItemByClassname
     * 
     * ===============
     */
    static gitem_t FindItemByClassname(String classname) {

        for (int i = 1; i < GameBase.game.num_items; i++) {
            gitem_t it = GameAI.itemlist[i];

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
            gitem_t it = GameAI.itemlist[i];

            if (it.pickup_name == null)
                continue;
            if (it.pickup_name.equalsIgnoreCase(pickup_name))
                return it;
        }
        Com.Println("Item not found:" + pickup_name);
        return null;
    }

    static int ArmorIndex(edict_t ent) {
        if (ent.client == null)
            return 0;

        if (ent.client.pers.inventory[GameUtil.jacket_armor_index] > 0)
            return GameUtil.jacket_armor_index;

        if (ent.client.pers.inventory[GameUtil.combat_armor_index] > 0)
            return GameUtil.combat_armor_index;

        if (ent.client.pers.inventory[GameUtil.body_armor_index] > 0)
            return GameUtil.body_armor_index;

        return 0;
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

        index = ArmorIndex(ent);

        if (index == 0)
            return 0;

        armor = GameAI.GetItemByIndex(index);
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

    static void AttackFinished(edict_t self, float time) {
        self.monsterinfo.attack_finished = GameBase.level.time + time;
    }

    /*
     * ============= infront
     * 
     * returns true if the entity is in front (in sight) of self =============
     */
    public static boolean infront(edict_t self, edict_t other) {
        float[] vec = { 0, 0, 0 };
        float dot;
        float[] forward = { 0, 0, 0 };

        Math3D.AngleVectors(self.s.angles, forward, null, null);
        Math3D.VectorSubtract(other.s.origin, self.s.origin, vec);
        Math3D.VectorNormalize(vec);
        dot = Math3D.DotProduct(vec, forward);

        if (dot > 0.3)
            return true;
        return false;
    }

    /*
     * ============= visible
     * 
     * returns 1 if the entity is visible to self, even if not infront ()
     * =============
     */
    public static boolean visible(edict_t self, edict_t other) {
        float[] spot1 = { 0, 0, 0 };
        float[] spot2 = { 0, 0, 0 };
        trace_t trace;

        Math3D.VectorCopy(self.s.origin, spot1);
        spot1[2] += self.viewheight;
        Math3D.VectorCopy(other.s.origin, spot2);
        spot2[2] += other.viewheight;
        trace = GameBase.gi.trace(spot1, Globals.vec3_origin,
                Globals.vec3_origin, spot2, self, Defines.MASK_OPAQUE);

        if (trace.fraction == 1.0)
            return true;
        return false;
    }

    /*
     * ================= AI_SetSightClient
     * 
     * Called once each frame to set level.sight_client to the player to be
     * checked for in findtarget.
     * 
     * If all clients are either dead or in notarget, sight_client will be null.
     * 
     * In coop games, sight_client will cycle between the clients.
     * =================
     */
    static void AI_SetSightClient() {
        edict_t ent;
        int start, check;

        if (GameBase.level.sight_client == null)
            start = 1;
        else
            start = GameBase.level.sight_client.index;

        check = start;
        while (true) {
            check++;
            if (check > GameBase.game.maxclients)
                check = 1;
            ent = GameBase.g_edicts[check];

            if (ent.inuse && ent.health > 0
                    && (ent.flags & Defines.FL_NOTARGET) == 0) {
                GameBase.level.sight_client = ent;
                return; // got one
            }
            if (check == start) {
                GameBase.level.sight_client = null;
                return; // nobody to see
            }
        }
    }

    /*
     * ============= ai_move
     * 
     * Move the specified distance at current facing. This replaces the QC
     * functions: ai_forward, ai_back, ai_pain, and ai_painforward
     * ==============
     */
    static void ai_move(edict_t self, float dist) {
        M.M_walkmove(self, self.s.angles[Defines.YAW], dist);
    }

    /*
     * =========== FindTarget
     * 
     * Self is currently not attacking anything, so try to find a target
     * 
     * Returns TRUE if an enemy was sighted
     * 
     * When a player fires a missile, the point of impact becomes a fakeplayer
     * so that monsters that see the impact will respond as if they had seen the
     * player.
     * 
     * To avoid spending too much time, only a single client (or fakeclient) is
     * checked each frame. This means multi player games will have slightly
     * slower noticing monsters. ============
     */
    static boolean FindTarget(edict_t self) {
        edict_t client;
        boolean heardit;
        int r;

        if ((self.monsterinfo.aiflags & Defines.AI_GOOD_GUY) != 0) 
        {
            if (self.goalentity != null && self.goalentity.inuse
                    && self.goalentity.classname != null) 
            {
                if (self.goalentity.classname.equals("target_actor"))
                    return false;
            }
            
            //FIXME look for monsters?
            return false;
        }

        // if we're going to a combat point, just proceed
        if ((self.monsterinfo.aiflags & Defines.AI_COMBAT_POINT) != 0)
            return false;

        //	   if the first spawnflag bit is set, the monster will only wake up on
        //	   really seeing the player, not another monster getting angry or
        // hearing
        //	   something

        //	   revised behavior so they will wake up if they "see" a player make a
        // noise
        //	   but not weapon impact/explosion noises

        heardit = false;
        if ((GameBase.level.sight_entity_framenum >= (GameBase.level.framenum - 1))
                && 0 == (self.spawnflags & 1)) {
            client = GameBase.level.sight_entity;           
            if (client.enemy == self.enemy)             
                return false;            
        } else if (GameBase.level.sound_entity_framenum >= (GameBase.level.framenum - 1)) {
            client = GameBase.level.sound_entity;
            heardit = true;
        } else if (null != (self.enemy)
                && (GameBase.level.sound2_entity_framenum >= (GameBase.level.framenum - 1))
                && 0 != (self.spawnflags & 1)) {
            client = GameBase.level.sound2_entity;
            heardit = true;
        } else {
            client = GameBase.level.sight_client;
            if (client == null)
                return false; // no clients to get mad at
        }

        // if the entity went away, forget it
        if (!client.inuse)
            return false;

        if (client.client != null) {
            if ((client.flags & Defines.FL_NOTARGET) != 0)
                return false;
        } else if ((client.svflags & Defines.SVF_MONSTER) != 0) {
            if (client.enemy == null)
                return false;
            if ((client.enemy.flags & Defines.FL_NOTARGET) != 0)
                return false;
        } else if (heardit) {
            if ((client.owner.flags & Defines.FL_NOTARGET) != 0)
                return false;
        } else
            return false;

        if (!heardit) {
            r = range(self, client);

            if (r == Defines.RANGE_FAR)
                return false;

            // this is where we would check invisibility
            // is client in an spot too dark to be seen?
            
            if (client.light_level <= 5)
                return false;

            if (!visible(self, client)) 
                return false;
           

            if (r == Defines.RANGE_NEAR) {
                if (client.show_hostile < GameBase.level.time
                        && !infront(self, client))               
                    return false;                
            } else if (r == Defines.RANGE_MID) {
                if (!infront(self, client)) 
                    return false;               
            }

            if (client == self.enemy)
                return true; // JDC false;
            
            self.enemy = client;

            if (!self.enemy.classname.equals("player_noise")) {
                self.monsterinfo.aiflags &= ~Defines.AI_SOUND_TARGET;

                if (self.enemy.client == null) {
                    self.enemy = self.enemy.enemy;
                    if (self.enemy.client == null) {
                        self.enemy = null;
                        return false;
                    }
                }
            }
        } else {
            // heard it
            float[] temp = { 0, 0, 0 };

            if ((self.spawnflags & 1) != 0) {
                if (!visible(self, client))
                    return false;
            } else {
                if (!GameBase.gi.inPHS(self.s.origin, client.s.origin))
                    return false;
            }

            Math3D.VectorSubtract(client.s.origin, self.s.origin, temp);

            if (Math3D.VectorLength(temp) > 1000) // too far to hear
                return false;


            // check area portals - if they are different and not connected then
            // we can't hear it
            if (client.areanum != self.areanum)
                if (!GameBase.gi.AreasConnected(self.areanum, client.areanum))
                    return false;

            self.ideal_yaw = Math3D.vectoyaw(temp);
            M.M_ChangeYaw(self);

            // hunt the sound for a bit; hopefully find the real player
            self.monsterinfo.aiflags |= Defines.AI_SOUND_TARGET;
            
            if (client == self.enemy)
                return true; // JDC false;
             
            self.enemy = client;             
        }

        //
        //	   got one
        //
        FoundTarget(self);

        if (0 == (self.monsterinfo.aiflags & Defines.AI_SOUND_TARGET)
                && (self.monsterinfo.sight != null))
            self.monsterinfo.sight.interact(self, self.enemy);

        return true;
    }

    //	============================================================================
    //ok
    static void HuntTarget(edict_t self) {
        float[] vec = { 0, 0, 0 };

        self.goalentity = self.enemy;
        if ((self.monsterinfo.aiflags & Defines.AI_STAND_GROUND) != 0)
            self.monsterinfo.stand.think(self);
        else
            self.monsterinfo.run.think(self);
        Math3D.VectorSubtract(self.enemy.s.origin, self.s.origin, vec);
        self.ideal_yaw = Math3D.vectoyaw(vec);
        // wait a while before first attack
        if (0 == (self.monsterinfo.aiflags & Defines.AI_STAND_GROUND))
            AttackFinished(self, 1);
    }

    public static void FoundTarget(edict_t self) {
        // let other monsters see this monster for a while
        if (self.enemy.client != null) {
            GameBase.level.sight_entity = self;
            GameBase.level.sight_entity_framenum = GameBase.level.framenum;
            GameBase.level.sight_entity.light_level = 128;
        }

        self.show_hostile = (int) GameBase.level.time + 1; // wake up other
                                                           // monsters

        Math3D.VectorCopy(self.enemy.s.origin, self.monsterinfo.last_sighting);
        self.monsterinfo.trail_time = GameBase.level.time;

        if (self.combattarget == null) {
            HuntTarget(self);
            return;
        }

        self.goalentity = self.movetarget = GameBase
                .G_PickTarget(self.combattarget);
        if (self.movetarget == null) {
            self.goalentity = self.movetarget = self.enemy;
            HuntTarget(self);
            GameBase.gi.dprintf("" + self.classname + "at "
                    + Lib.vtos(self.s.origin) + ", combattarget "
                    + self.combattarget + " not found\n");
            return;
        }

        // clear out our combattarget, these are a one shot deal
        self.combattarget = null;
        self.monsterinfo.aiflags |= Defines.AI_COMBAT_POINT;

        // clear the targetname, that point is ours!
        self.movetarget.targetname = null;
        self.monsterinfo.pausetime = 0;

        // run for it
        self.monsterinfo.run.think(self);
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
                    Math3D
                            .VectorSubtract(ent.s.origin, inflictor.s.origin,
                                    dir);
                    T_Damage(ent, inflictor, attacker, dir, inflictor.s.origin,
                            Globals.vec3_origin, (int) points, (int) points,
                            Defines.DAMAGE_RADIUS, mod);
                }
            }
        }
    }

    public static EntThinkAdapter Think_Delay = new EntThinkAdapter() {
        public boolean think(edict_t ent) {
            GameUtil.G_UseTargets(ent, ent.activator);
            GameUtil.G_FreeEdict(ent);
            return true;
        }
    };

    public static EntThinkAdapter G_FreeEdictA = new EntThinkAdapter() {
        public boolean think(edict_t ent) {
            GameUtil.G_FreeEdict(ent);
            return false;
        }
    };

    static EntThinkAdapter MegaHealth_think = new EntThinkAdapter() {
        public boolean think(edict_t self) {
            if (self.owner.health > self.owner.max_health) {
                self.nextthink = GameBase.level.time + 1;
                self.owner.health -= 1;
                return false;
            }

            if (!((self.spawnflags & Defines.DROPPED_ITEM) != 0)
                    && (GameBase.deathmatch.value != 0))
                GameUtil.SetRespawn(self, 20);
            else
                GameUtil.G_FreeEdict(self);

            return false;
        }
    };

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

            item = GameUtil.FindItem("Cells");
            if (item != null) {
                index = GameUtil.ITEM_INDEX(item);
                other.client.pers.inventory[index] += item.quantity;
                if (other.client.pers.inventory[index] > other.client.pers.max_cells)
                    other.client.pers.inventory[index] = other.client.pers.max_cells;
            }

            item = GameUtil.FindItem("Grenades");
            if (item != null) {
                index = GameUtil.ITEM_INDEX(item);
                other.client.pers.inventory[index] += item.quantity;
                if (other.client.pers.inventory[index] > other.client.pers.max_grenades)
                    other.client.pers.inventory[index] = other.client.pers.max_grenades;
            }

            item = GameUtil.FindItem("Rockets");
            if (item != null) {
                index = GameUtil.ITEM_INDEX(item);
                other.client.pers.inventory[index] += item.quantity;
                if (other.client.pers.inventory[index] > other.client.pers.max_rockets)
                    other.client.pers.inventory[index] = other.client.pers.max_rockets;
            }

            item = GameUtil.FindItem("Slugs");
            if (item != null) {
                index = GameUtil.ITEM_INDEX(item);
                other.client.pers.inventory[index] += item.quantity;
                if (other.client.pers.inventory[index] > other.client.pers.max_slugs)
                    other.client.pers.inventory[index] = other.client.pers.max_slugs;
            }

            if (0 == (ent.spawnflags & Defines.DROPPED_ITEM)
                    && (GameBase.deathmatch.value != 0))
                GameUtil.SetRespawn(ent, ent.item.quantity);

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
                ent.think = MegaHealth_think;
                ent.nextthink = GameBase.level.time + 5f;
                ent.owner = other;
                ent.flags |= Defines.FL_RESPAWN;
                ent.svflags |= Defines.SVF_NOCLIENT;
                ent.solid = Defines.SOLID_NOT;
            } else {
                if (!((ent.spawnflags & Defines.DROPPED_ITEM) != 0)
                        && (GameBase.deathmatch.value != 0))
                    GameUtil.SetRespawn(ent, 30);
            }

            return true;
        }

    };

    /*
     * =============== Touch_Item ===============
     */

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
                other.client.ps.stats[Defines.STAT_PICKUP_STRING] = (short) (Defines.CS_ITEMS + GameUtil
                        .ITEM_INDEX(ent.item));
                other.client.pickup_msg_time = GameBase.level.time + 3.0f;

                // change selected item
                if (ent.item.use != null)
                    other.client.pers.selected_item = other.client.ps.stats[Defines.STAT_SELECTED_ITEM] = (short) GameUtil
                            .ITEM_INDEX(ent.item);

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
                ent.think = G_FreeEdictA;
            }
            return false;
        }
    };

    static int quad_drop_timeout_hack = 0;

    static ItemUseAdapter Use_Quad = new ItemUseAdapter() {

        public void use(edict_t ent, gitem_t item) {
            int timeout;

            ent.client.pers.inventory[GameUtil.ITEM_INDEX(item)]--;
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
            ent.client.pers.inventory[GameUtil.ITEM_INDEX(item)]--;
            GameUtil.ValidateSelectedItem(ent);

            if (ent.client.invincible_framenum > GameBase.level.framenum)
                ent.client.invincible_framenum += 300;
            else
                ent.client.invincible_framenum = GameBase.level.framenum + 300;

            GameBase.gi.sound(ent, Defines.CHAN_ITEM, GameBase.gi
                    .soundindex("items/protect.wav"), 1, Defines.ATTN_NORM, 0);
        }
    };

    //	======================================================================

    static ItemUseAdapter Use_Breather = new ItemUseAdapter() {
        public void use(edict_t ent, gitem_t item) {
            ent.client.pers.inventory[GameUtil.ITEM_INDEX(item)]--;

            GameUtil.ValidateSelectedItem(ent);

            if (ent.client.breather_framenum > GameBase.level.framenum)
                ent.client.breather_framenum += 300;
            else
                ent.client.breather_framenum = GameBase.level.framenum + 300;

            GameBase.gi.sound(ent, Defines.CHAN_ITEM, GameBase.gi
                    .soundindex("items/damage.wav"), 1, Defines.ATTN_NORM, 0);
        }
    };

    //	======================================================================

    static ItemUseAdapter Use_Envirosuit = new ItemUseAdapter() {
        public void use(edict_t ent, gitem_t item) {
            ent.client.pers.inventory[GameUtil.ITEM_INDEX(item)]--;
            GameUtil.ValidateSelectedItem(ent);

            if (ent.client.enviro_framenum > GameBase.level.framenum)
                ent.client.enviro_framenum += 300;
            else
                ent.client.enviro_framenum = GameBase.level.framenum + 300;

            GameBase.gi.sound(ent, Defines.CHAN_ITEM, GameBase.gi
                    .soundindex("items/damage.wav"), 1, Defines.ATTN_NORM, 0);
        }
    };

    //	======================================================================

    static ItemUseAdapter Use_Silencer = new ItemUseAdapter() {
        public void use(edict_t ent, gitem_t item) {

            ent.client.pers.inventory[GameUtil.ITEM_INDEX(item)]--;
            GameUtil.ValidateSelectedItem(ent);
            ent.client.silencer_shots += 30;

            GameBase.gi.sound(ent, Defines.CHAN_ITEM, GameBase.gi
                    .soundindex("items/damage.wav"), 1, Defines.ATTN_NORM, 0);
        }
    };

    //	======================================================================

    static EntInteractAdapter Pickup_Key = new EntInteractAdapter() {
        public boolean interact(edict_t ent, edict_t other) {
            if (GameBase.coop.value != 0) {
                if (Lib.strcmp(ent.classname, "key_power_cube") == 0) {
                    if ((other.client.pers.power_cubes & ((ent.spawnflags & 0x0000ff00) >> 8)) != 0)
                        return false;
                    other.client.pers.inventory[GameUtil.ITEM_INDEX(ent.item)]++;
                    other.client.pers.power_cubes |= ((ent.spawnflags & 0x0000ff00) >> 8);
                } else {
                    if (other.client.pers.inventory[GameUtil
                            .ITEM_INDEX(ent.item)] != 0)
                        return false;
                    other.client.pers.inventory[GameUtil.ITEM_INDEX(ent.item)] = 1;
                }
                return true;
            }
            other.client.pers.inventory[GameUtil.ITEM_INDEX(ent.item)]++;
            return true;
        }
    };

    static int jacket_armor_index;

    static int combat_armor_index;

    static int body_armor_index;

    static int power_screen_index;

    static int power_shield_index;

    /*
     * ============= range
     * 
     * returns the range catagorization of an entity reletive to self. 0 melee
     * range, will become hostile even if back is turned 1 visibility and
     * infront, or visibility and show hostile 2 infront and show hostile 3 only
     * triggered by damage
     *  
     */
    //	static int range(edict_t self, edict_t other)
    //	{
    //		float[] v= { 0, 0, 0 };
    //		float len;
    //
    //		VectorSubtract(self.s.origin, other.s.origin, v);
    //		len= VectorLength(v);
    //		if (len < MELEE_DISTANCE)
    //			return RANGE_MELEE;
    //		if (len < 500)
    //			return RANGE_NEAR;
    //		if (len < 1000)
    //			return RANGE_MID;
    //		return RANGE_FAR;
    //	}
    //	============================================================================
    public static EntThinkAdapter M_CheckAttack = new EntThinkAdapter() {

        public boolean think(edict_t self) {
            float[] spot1 = { 0, 0, 0 };

            float[] spot2 = { 0, 0, 0 };
            float chance;
            trace_t tr;

            if (self.enemy.health > 0) {
                // see if any entities are in the way of the shot
                Math3D.VectorCopy(self.s.origin, spot1);
                spot1[2] += self.viewheight;
                Math3D.VectorCopy(self.enemy.s.origin, spot2);
                spot2[2] += self.enemy.viewheight;

                tr = GameBase.gi.trace(spot1, null, null, spot2, self,
                        Defines.CONTENTS_SOLID | Defines.CONTENTS_MONSTER
                                | Defines.CONTENTS_SLIME
                                | Defines.CONTENTS_LAVA
                                | Defines.CONTENTS_WINDOW);

                // do we have a clear shot?
                if (tr.ent != self.enemy)
                    return false;
            }

            // melee attack
            if (enemy_range == Defines.RANGE_MELEE) {
                // don't always melee in easy mode
                if (GameBase.skill.value == 0 && (Lib.rand() & 3) != 0)
                    return false;
                if (self.monsterinfo.melee != null)
                    self.monsterinfo.attack_state = Defines.AS_MELEE;
                else
                    self.monsterinfo.attack_state = Defines.AS_MISSILE;
                return true;
            }

            //					 missile attack
            if (self.monsterinfo.attack == null)
                return false;

            if (GameBase.level.time < self.monsterinfo.attack_finished)
                return false;

            if (enemy_range == Defines.RANGE_FAR)
                return false;

            if ((self.monsterinfo.aiflags & Defines.AI_STAND_GROUND) != 0) {
                chance = 0.4f;
            } else if (enemy_range == Defines.RANGE_MELEE) {
                chance = 0.2f;
            } else if (enemy_range == Defines.RANGE_NEAR) {
                chance = 0.1f;
            } else if (enemy_range == Defines.RANGE_MID) {
                chance = 0.02f;
            } else {
                return false;
            }

            if (GameBase.skill.value == 0)
                chance *= 0.5;
            else if (GameBase.skill.value >= 2)
                chance *= 2;

            if (Lib.random() < chance) {
                self.monsterinfo.attack_state = Defines.AS_MISSILE;
                self.monsterinfo.attack_finished = GameBase.level.time + 2
                        * Lib.random();
                return true;
            }

            if ((self.flags & Defines.FL_FLY) != 0) {
                if (Lib.random() < 0.3f)
                    self.monsterinfo.attack_state = Defines.AS_SLIDING;
                else
                    self.monsterinfo.attack_state = Defines.AS_STRAIGHT;
            }

            return false;

        }
    };

    static EntUseAdapter monster_use = new EntUseAdapter() {
        public void use(edict_t self, edict_t other, edict_t activator) {
            if (self.enemy != null)
                return;
            if (self.health <= 0)
                return;
            if ((activator.flags & Defines.FL_NOTARGET) != 0)
                return;
            if ((null == activator.client)
                    && 0 == (activator.monsterinfo.aiflags & Defines.AI_GOOD_GUY))
                return;

            // delay reaction so if the monster is teleported, its sound is
            // still heard
            self.enemy = activator;
            GameUtil.FoundTarget(self);
        }
    };

    static boolean enemy_vis;

    static boolean enemy_infront;

    static int enemy_range;

    static float enemy_yaw;
}