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

// Created on 17.12.2003 by RST.

package jake2.game;

import jake2.qcommon.Defines;
import jake2.qcommon.network.MulticastTypes;
import jake2.qcommon.network.commands.MuzzleFlash2Message;
import jake2.qcommon.util.Lib;
import jake2.qcommon.util.Math3D;

public class Monster {

    // FIXME monsters should call these with a totally accurate direction
    //	and we can mess it up based on skill. Spread should be for normal
    //	and we can tighten or loosen based on skill. We could muck with
    //	the damages too, but I'm not sure that's such a good idea.
    public static void monster_fire_bullet(SubgameEntity self, float[] start,
                                           float[] dir, int damage, int kick, int hspread, int vspread,
                                           int flashtype, GameExportsImpl gameExports) {
        GameWeapon.fire_bullet(self, start, dir, damage, kick, hspread, vspread,
                GameDefines.MOD_UNKNOWN, gameExports);

        gameExports.gameImports.multicastMessage(start, new MuzzleFlash2Message(self.index, flashtype), MulticastTypes.MULTICAST_PVS);
    }

    /** The Moster fires the shotgun. */
    public static void monster_fire_shotgun(SubgameEntity self, float[] start,
                                            float[] aimdir, int damage, int kick, int hspread, int vspread,
                                            int count, int flashtype, GameExportsImpl gameExports) {
        GameWeapon.fire_shotgun(self, start, aimdir, damage, kick, hspread, vspread,
                count, GameDefines.MOD_UNKNOWN, gameExports);

        gameExports.gameImports.multicastMessage(start, new MuzzleFlash2Message(self.index, flashtype), MulticastTypes.MULTICAST_PVS);
    }

    /** The Moster fires the blaster. */
    public static void monster_fire_blaster(SubgameEntity self, float[] start,
                                            float[] dir, int damage, int speed, int flashtype, int effect, GameExportsImpl gameExports) {
        GameWeapon.fire_blaster(self, start, dir, damage, speed, effect, false, gameExports);

        gameExports.gameImports.multicastMessage(start, new MuzzleFlash2Message(self.index, flashtype), MulticastTypes.MULTICAST_PVS);
    }

    /** The Moster fires the grenade. */
    public static void monster_fire_grenade(SubgameEntity self, float[] start,
                                            float[] aimdir, int damage, int speed, int flashtype, GameExportsImpl gameExports) {
        GameWeapon
                .fire_grenade(self, start, aimdir, damage, speed, 2.5f,
                        damage + 40, gameExports);

        gameExports.gameImports.multicastMessage(start, new MuzzleFlash2Message(self.index, flashtype), MulticastTypes.MULTICAST_PVS);
    }

    /** The Moster fires the rocket. */
    public static void monster_fire_rocket(SubgameEntity self, float[] start,
                                           float[] dir, int damage, int speed, int flashtype, GameExportsImpl gameExports) {
        GameWeapon.fire_rocket(self, start, dir, damage, speed, damage + 20, damage, gameExports);

        gameExports.gameImports.multicastMessage(start, new MuzzleFlash2Message(self.index, flashtype), MulticastTypes.MULTICAST_PVS);
    }

    /** The Moster fires the railgun. */
    public static void monster_fire_railgun(SubgameEntity self, float[] start,
                                            float[] aimdir, int damage, int kick, int flashtype, GameExportsImpl gameExports) {
        GameWeapon.fire_rail(self, start, aimdir, damage, kick, gameExports);

        gameExports.gameImports.multicastMessage(start, new MuzzleFlash2Message(self.index, flashtype), MulticastTypes.MULTICAST_PVS);
    }

    /** The Moster fires the bfg. */
    public static void monster_fire_bfg(SubgameEntity self, float[] start,
                                        float[] aimdir, int damage, int speed, int kick,
                                        float damage_radius, int flashtype, GameExportsImpl gameExports) {
        GameWeapon.fire_bfg(self, start, aimdir, damage, speed, damage_radius, gameExports);

        gameExports.gameImports.multicastMessage(start, new MuzzleFlash2Message(self.index, flashtype), MulticastTypes.MULTICAST_PVS);
    }

    /*
     * ================ monster_death_use
     * 
     * When a monster dies, it fires all of its targets with the current enemy
     * as activator. ================
     */
    public static void monster_death_use(SubgameEntity self, GameExportsImpl gameExports) {
        self.flags &= ~(GameDefines.FL_FLY | GameDefines.FL_SWIM);
        self.monsterinfo.aiflags &= GameDefines.AI_GOOD_GUY;

        if (self.item != null) {
            GameItems.Drop_Item(self, self.item, gameExports);
            self.item = null;
        }

        if (self.deathtarget != null)
            self.target = self.deathtarget;

        if (self.target == null)
            return;

        GameUtil.G_UseTargets(self, self.enemy, gameExports);
    }

    // ============================================================================
    public static boolean monster_start(SubgameEntity self, GameExportsImpl gameExports) {
        if (gameExports.gameCvars.deathmatch.value != 0) {
            GameUtil.G_FreeEdict(self, gameExports);
            return false;
        }

        if ((self.spawnflags & 4) != 0
                && 0 == (self.monsterinfo.aiflags & GameDefines.AI_GOOD_GUY)) {
            self.spawnflags &= ~4;
            self.spawnflags |= 1;
            //		 gi.dprintf("fixed spawnflags on %s at %s\n", self.classname,
            // vtos(self.s.origin));
        }

        if (0 == (self.monsterinfo.aiflags & GameDefines.AI_GOOD_GUY))
            gameExports.level.total_monsters++;

        self.nextthink = gameExports.level.time + Defines.FRAMETIME;
        self.svflags |= Defines.SVF_MONSTER;
        self.s.renderfx |= Defines.RF_FRAMELERP;
        self.takedamage = Defines.DAMAGE_AIM;
        self.air_finished = gameExports.level.time + 12;
        self.use = GameUtil.monster_use;
        self.max_health = self.health;
        self.clipmask = Defines.MASK_MONSTERSOLID;

        self.s.skinnum = 0;
        self.deadflag = GameDefines.DEAD_NO;
        self.svflags &= ~Defines.SVF_DEADMONSTER;

        if (null == self.monsterinfo.checkattack)
            self.monsterinfo.checkattack = GameUtil.M_CheckAttack;
        Math3D.VectorCopy(self.s.origin, self.s.old_origin);

        if (gameExports.st.item != null && gameExports.st.item.length() > 0) {
            self.item = GameItems.FindItemByClassname(gameExports.st.item, gameExports);
            if (self.item == null)
                gameExports.gameImports.dprintf("monster_start:" + self.classname + " at "
                        + Lib.vtos(self.s.origin) + " has bad item: "
                        + gameExports.st.item + "\n");
        }

        // randomize what frame they start on
        if (self.monsterinfo.currentmove != null)
            self.s.frame = self.monsterinfo.currentmove.firstframe
                    + (Lib.rand() % (self.monsterinfo.currentmove.lastframe
                            - self.monsterinfo.currentmove.firstframe + 1));

        return true;
    }

    public static void monster_start_go(SubgameEntity self, GameExportsImpl gameExports) {

        float[] v = { 0, 0, 0 };

        if (self.health <= 0)
            return;

        // check for target to combat_point and change to combattarget
        if (self.target != null) {
            boolean notcombat = false;
            boolean fixup = false;
            /*
             * if (true) { Com.Printf("all entities:\n");
             * 
             * for (int n = 0; n < Game.globals.num_edicts; n++) { edict_t ent =
             * gameExports.g_edicts[n]; Com.Printf( "|%4i | %25s
             * |%8.2f|%8.2f|%8.2f||%8.2f|%8.2f|%8.2f||%8.2f|%8.2f|%8.2f|\n", new
             * Vargs().add(n).add(ent.classname).
             * add(ent.s.origin[0]).add(ent.s.origin[1]).add(ent.s.origin[2])
             * .add(ent.mins[0]).add(ent.mins[1]).add(ent.mins[2])
             * .add(ent.maxs[0]).add(ent.maxs[1]).add(ent.maxs[2])); }
             * sleep(10); }
             */

            EdictIterator edit = null;

            while ((edit = GameBase.G_Find(edit, GameBase.findByTarget,
                    self.target, gameExports)) != null) {
                SubgameEntity target = edit.o;
                if ("point_combat".equals(target.classname)) {
                    self.combattarget = self.target;
                    fixup = true;
                } else {
                    notcombat = true;
                }
            }
            if (notcombat && self.combattarget != null)
                gameExports.gameImports.dprintf(self.classname + " at "
                        + Lib.vtos(self.s.origin)
                        + " has target with mixed types\n");
            if (fixup)
                self.target = null;
        }

        // validate combattarget
        if (self.combattarget != null) {

            EdictIterator edit = null;
            while ((edit = GameBase.G_Find(edit, GameBase.findByTarget,
                    self.combattarget, gameExports)) != null) {
                SubgameEntity target = edit.o;

                if (!"point_combat".equals(target.classname)) {
                    gameExports.gameImports.dprintf(self.classname + " at "
                            + Lib.vtos(self.s.origin)
                            + " has bad combattarget " + self.combattarget
                            + " : " + target.classname + " at "
                            + Lib.vtos(target.s.origin));
                }
            }
        }

        if (self.target != null) {
            self.goalentity = self.movetarget = GameBase
                    .G_PickTarget(self.target, gameExports);
            if (null == self.movetarget) {
                gameExports.gameImports
                        .dprintf(self.classname + " can't find target "
                                + self.target + " at "
                                + Lib.vtos(self.s.origin) + "\n");
                self.target = null;
                self.monsterinfo.pausetime = 100000000;
                self.monsterinfo.stand.think(self, gameExports);
            } else if ("path_corner".equals(self.movetarget.classname)) {
                Math3D.VectorSubtract(self.goalentity.s.origin, self.s.origin,
                        v);
                self.ideal_yaw = self.s.angles[Defines.YAW] = Math3D
                        .vectoyaw(v);
                self.monsterinfo.walk.think(self, gameExports);
                self.target = null;
            } else {
                self.goalentity = self.movetarget = null;
                self.monsterinfo.pausetime = 100000000;
                self.monsterinfo.stand.think(self, gameExports);
            }
        } else {
            self.monsterinfo.pausetime = 100000000;
            self.monsterinfo.stand.think(self, gameExports);
        }

        self.think = Monster.monster_think;
        self.nextthink = gameExports.level.time + Defines.FRAMETIME;
    }

    public static EntThinkAdapter monster_think = new EntThinkAdapter() {
        public String getID() { return "monster_think";}
        public boolean think(SubgameEntity self, GameExportsImpl gameExports) {

            M.M_MoveFrame(self, gameExports);
            if (self.linkcount != self.monsterinfo.linkcount) {
                self.monsterinfo.linkcount = self.linkcount;
                M.M_CheckGround(self, gameExports);
            }
            M.M_CatagorizePosition(self, gameExports);
            M.M_WorldEffects(self, gameExports);
            M.M_SetEffects(self, gameExports.level.time);
            return true;
        }
    };

    public static EntThinkAdapter monster_triggered_spawn = new EntThinkAdapter() {
        public String getID() { return "monster_trigger_spawn";}
        public boolean think(SubgameEntity self, GameExportsImpl gameExports) {

            self.s.origin[2] += 1;
            GameUtil.KillBox(self, gameExports);

            self.solid = Defines.SOLID_BBOX;
            self.movetype = GameDefines.MOVETYPE_STEP;
            self.svflags &= ~Defines.SVF_NOCLIENT;
            self.air_finished = gameExports.level.time + 12;
            gameExports.gameImports.linkentity(self);

            Monster.monster_start_go(self, gameExports);

            if (self.enemy != null && 0 == (self.spawnflags & 1)
                    && 0 == (self.enemy.flags & GameDefines.FL_NOTARGET)) {
                GameUtil.FoundTarget(self, gameExports);
            } else {
                self.enemy = null;
            }
            return true;
        }
    };

    //	we have a one frame delay here so we don't telefrag the guy who activated
    // us
    public static EntUseAdapter monster_triggered_spawn_use = new EntUseAdapter() {
        public String getID() { return "monster_trigger_spawn_use";}
        public void use(SubgameEntity self, SubgameEntity other, SubgameEntity activator, GameExportsImpl gameExports) {
            self.think = monster_triggered_spawn;
            self.nextthink = gameExports.level.time + Defines.FRAMETIME;
            if (activator.getClient() != null)
                self.enemy = activator;
            self.use = GameUtil.monster_use;
        }
    };

    public static EntThinkAdapter monster_triggered_start = new EntThinkAdapter() {
        public String getID() { return "monster_triggered_start";}
        public boolean think(SubgameEntity self, GameExportsImpl gameExports) {
            self.solid = Defines.SOLID_NOT;
            self.movetype = GameDefines.MOVETYPE_NONE;
            self.svflags |= Defines.SVF_NOCLIENT;
            self.nextthink = 0;
            self.use = monster_triggered_spawn_use;
            return true;
        }
    };
}