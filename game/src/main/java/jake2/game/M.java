/*
 * M.java
 * Copyright (C) 2003
 * 
 * $Id: M.java,v 1.9 2006-01-21 21:53:32 salomo Exp $
 */
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
package jake2.game;

import jake2.game.adapters.EntThinkAdapter;
import jake2.qcommon.Defines;
import jake2.qcommon.Globals;
import jake2.qcommon.edict_t;
import jake2.qcommon.trace_t;
import jake2.qcommon.util.Lib;
import jake2.qcommon.util.Math3D;

/**
 * M
 */
public final class M {

    public static void M_CheckGround(SubgameEntity ent, GameExportsImpl gameExports) {
        float[] point = { 0, 0, 0 };

        if ((ent.flags & (GameDefines.FL_SWIM | GameDefines.FL_FLY)) != 0)
            return;

        if (ent.velocity[2] > 100) {
            ent.groundentity = null;
            return;
        }

        // if the hull point one-quarter unit down is solid the entity is on
        // ground
        point[0] = ent.s.origin[0];
        point[1] = ent.s.origin[1];
        point[2] = ent.s.origin[2] - 0.25f;

        trace_t trace = gameExports.gameImports.trace(ent.s.origin, ent.mins, ent.maxs, point, ent,
                Defines.MASK_MONSTERSOLID);

        // check steepness
        if (trace.plane.normal[2] < 0.7 && !trace.startsolid) {
            ent.groundentity = null;
            return;
        }

        // ent.groundentity = trace.ent;
        // ent.groundentity_linkcount = trace.ent.linkcount;
        // if (!trace.startsolid && !trace.allsolid)
        //   VectorCopy (trace.endpos, ent.s.origin);
        if (!trace.startsolid && !trace.allsolid) {
            Math3D.VectorCopy(trace.endpos, ent.s.origin);
            ent.groundentity = trace.ent;
            ent.groundentity_linkcount = trace.ent.linkcount;
            ent.velocity[2] = 0;
        }
    }
    
    /**
     * Returns false if any part of the bottom of the entity is off an edge that
     * is not a staircase.
     */
    public static boolean M_CheckBottom(edict_t ent, GameExportsImpl gameExports) {
        float[] mins = { 0, 0, 0 };
        float[] maxs = { 0, 0, 0 };
        float[] start = { 0, 0, 0 };
        float[] stop = { 0, 0, 0 };

        trace_t trace;
        int x, y;
        float mid, bottom;

        Math3D.VectorAdd(ent.s.origin, ent.mins, mins);
        Math3D.VectorAdd(ent.s.origin, ent.maxs, maxs);

        //	   if all of the points under the corners are solid world, don't bother
        //	   with the tougher checks
        //	   the corners must be within 16 of the midpoint
        start[2] = mins[2] - 1;
        for (x = 0; x <= 1; x++)
            for (y = 0; y <= 1; y++) {
                start[0] = x != 0 ? maxs[0] : mins[0];
                start[1] = y != 0 ? maxs[1] : mins[1];
                if (gameExports.gameImports.getPointContents(start) != Defines.CONTENTS_SOLID) {
                    //
                    //	   check it for real...
                    //
                    start[2] = mins[2];

                    //	   the midpoint must be within 16 of the bottom
                    start[0] = stop[0] = (mins[0] + maxs[0]) * 0.5f;
                    start[1] = stop[1] = (mins[1] + maxs[1]) * 0.5f;
                    stop[2] = start[2] - 2 * GameBase.STEPSIZE;
                    trace = gameExports.gameImports.trace(start, Globals.vec3_origin,
                            Globals.vec3_origin, stop, ent,
                            Defines.MASK_MONSTERSOLID);

                    if (trace.fraction == 1.0)
                        return false;
                    mid = bottom = trace.endpos[2];

                    //	   the corners must be within 16 of the midpoint
                    for (x = 0; x <= 1; x++)
                        for (y = 0; y <= 1; y++) {
                            start[0] = stop[0] = x != 0 ? maxs[0] : mins[0];
                            start[1] = stop[1] = y != 0 ? maxs[1] : mins[1];

                            trace = gameExports.gameImports.trace(start,
                                    Globals.vec3_origin, Globals.vec3_origin,
                                    stop, ent, Defines.MASK_MONSTERSOLID);

                            if (trace.fraction != 1.0
                                    && trace.endpos[2] > bottom)
                                bottom = trace.endpos[2];
                            if (trace.fraction == 1.0
                                    || mid - trace.endpos[2] > GameBase.STEPSIZE)
                                return false;
                        }

                    return true;
                }
            }

        return true; // we got out easy
    }

    /** 
     * M_ChangeYaw.
     */
    public static void M_ChangeYaw(SubgameEntity ent) {
        float ideal;
        float current;
        float move;
        float speed;

        current = Math3D.anglemod(ent.s.angles[Defines.YAW]);
        ideal = ent.ideal_yaw;

        if (current == ideal)
            return;

        move = ideal - current;
        speed = ent.yaw_speed;
        if (ideal > current) {
            if (move >= 180)
                move = move - 360;
        } else {
            if (move <= -180)
                move = move + 360;
        }
        if (move > 0) {
            if (move > speed)
                move = speed;
        } else {
            if (move < -speed)
                move = -speed;
        }

        ent.s.angles[Defines.YAW] = Math3D.anglemod(current + move);
    }

    /**
     * M_MoveToGoal.
     */
    public static void M_MoveToGoal(SubgameEntity ent, float dist, GameExportsImpl gameExports) {
        SubgameEntity goal = ent.goalentity;

        // if we are (helplessly) falling and cannot fly -> return
        if (ent.groundentity == null
                && (ent.flags & (GameDefines.FL_FLY | GameDefines.FL_SWIM)) == 0)
            return;

        //	   if the next step hits the enemy, return immediately
        if (ent.enemy != null && SV.SV_CloseEnough(ent, ent.enemy, dist))
            return;

        //	   bump around...
        if ((Lib.rand() & 3) == 1 || !SV.SV_StepDirection(ent, ent.ideal_yaw, dist, gameExports)) {
            if (ent.inuse)
                SV.SV_NewChaseDir(ent, goal, dist, gameExports);
        }
    }

    /**
     * M_walkmove.
     *
     * @param yaw  - where the entitiy is looking at (in degrees)
     * @param dist - distance to travel during this frame
     */
    public static boolean M_walkmove(SubgameEntity ent, float yaw, float dist, GameExportsImpl gameExports) {

        // if we are falling (or swimming) and cannot fly (swim) -> return
        if ((ent.groundentity == null)
                && (ent.flags & (GameDefines.FL_FLY | GameDefines.FL_SWIM)) == 0)
            return false;

        float yaw_radian = (float) (yaw * Math.PI * 2 / 360);

        float[] move = {
                (float) Math.cos(yaw_radian) * dist,
                (float) Math.sin(yaw_radian) * dist,
                0
        };

        return SV.SV_movestep(ent, move, true, gameExports);
    }

    public static void M_CatagorizePosition(SubgameEntity ent, GameExportsImpl gameExports) {
        float[] point = { 0, 0, 0 };
        int cont;

        //
        //	get waterlevel
        //
        point[0] = ent.s.origin[0];
        point[1] = ent.s.origin[1];
        point[2] = ent.s.origin[2] + ent.mins[2] + 1;
        cont = gameExports.gameImports.getPointContents(point);

        if (0 == (cont & Defines.MASK_WATER)) {
            ent.waterlevel = 0;
            ent.watertype = 0;
            return;
        }

        ent.watertype = cont;
        ent.waterlevel = 1;
        point[2] += 26;
        cont = gameExports.gameImports.getPointContents(point);
        if (0 == (cont & Defines.MASK_WATER))
            return;

        ent.waterlevel = 2;
        point[2] += 22;
        cont = gameExports.gameImports.getPointContents(point);
        if (0 != (cont & Defines.MASK_WATER))
            ent.waterlevel = 3;
    }

    /**
     * Apply water drowning, lava or slime effects
     */
    static void M_WorldEffects(SubgameEntity ent, GameExportsImpl gameExports) {

        if (ent.health > 0) {
            int dmg;
            if (0 == (ent.flags & GameDefines.FL_SWIM)) {
                if (ent.waterlevel < 3) {
                    ent.air_finished = gameExports.level.time + 12;
                } else if (ent.air_finished < gameExports.level.time) {
                    // drown!
                    if (ent.pain_debounce_time < gameExports.level.time) {
                        dmg = (int) (2f + 2f * Math.floor(gameExports.level.time
                                - ent.air_finished));
                        if (dmg > 15)
                            dmg = 15;
                        GameCombat.T_Damage(ent, gameExports.g_edicts[0],
                                gameExports.g_edicts[0], Globals.vec3_origin,
                                ent.s.origin, Globals.vec3_origin, dmg, 0,
                                Defines.DAMAGE_NO_ARMOR, GameDefines.MOD_WATER, gameExports);
                        ent.pain_debounce_time = gameExports.level.time + 1;
                    }
                }
            } else {
                if (ent.waterlevel > 0) {
                    ent.air_finished = gameExports.level.time + 9;
                } else if (ent.air_finished < gameExports.level.time) {
                    // suffocate!
                    if (ent.pain_debounce_time < gameExports.level.time) {
                        dmg = (int) (2 + 2 * Math.floor(gameExports.level.time
                                - ent.air_finished));
                        if (dmg > 15)
                            dmg = 15;
                        GameCombat.T_Damage(ent, gameExports.g_edicts[0],
                                gameExports.g_edicts[0], Globals.vec3_origin,
                                ent.s.origin, Globals.vec3_origin, dmg, 0,
                                Defines.DAMAGE_NO_ARMOR, GameDefines.MOD_WATER, gameExports);
                        ent.pain_debounce_time = gameExports.level.time + 1;
                    }
                }
            }
        }

        if (ent.waterlevel == 0) {
            if ((ent.flags & GameDefines.FL_INWATER) != 0) {
                gameExports.gameImports.sound(ent, Defines.CHAN_BODY, gameExports.gameImports
                        .soundindex("player/watr_out.wav"), 1,
                        Defines.ATTN_NORM, 0);
                ent.flags &= ~GameDefines.FL_INWATER;
            }
            return;
        }

        if ((ent.watertype & Defines.CONTENTS_LAVA) != 0
                && 0 == (ent.flags & GameDefines.FL_IMMUNE_LAVA)) {
            if (ent.damage_debounce_time < gameExports.level.time) {
                ent.damage_debounce_time = gameExports.level.time + 0.2f;
                GameCombat.T_Damage(ent, gameExports.g_edicts[0],
                        gameExports.g_edicts[0], Globals.vec3_origin,
                        ent.s.origin, Globals.vec3_origin, 10 * ent.waterlevel,
                        0, 0, GameDefines.MOD_LAVA, gameExports);
            }
        }
        if ((ent.watertype & Defines.CONTENTS_SLIME) != 0
                && 0 == (ent.flags & GameDefines.FL_IMMUNE_SLIME)) {
            if (ent.damage_debounce_time < gameExports.level.time) {
                ent.damage_debounce_time = gameExports.level.time + 1;
                GameCombat.T_Damage(ent, gameExports.g_edicts[0],
                        gameExports.g_edicts[0], Globals.vec3_origin,
                        ent.s.origin, Globals.vec3_origin, 4 * ent.waterlevel,
                        0, 0, GameDefines.MOD_SLIME, gameExports);
            }
        }

        if (0 == (ent.flags & GameDefines.FL_INWATER)) {
            if (0 == (ent.svflags & Defines.SVF_DEADMONSTER)) {
                if ((ent.watertype & Defines.CONTENTS_LAVA) != 0)
                    if (Globals.rnd.nextFloat() <= 0.5)
                        gameExports.gameImports.sound(ent, Defines.CHAN_BODY, gameExports.gameImports
                                .soundindex("player/lava1.wav"), 1,
                                Defines.ATTN_NORM, 0);
                    else
                        gameExports.gameImports.sound(ent, Defines.CHAN_BODY, gameExports.gameImports
                                .soundindex("player/lava2.wav"), 1,
                                Defines.ATTN_NORM, 0);
                else if ((ent.watertype & Defines.CONTENTS_SLIME) != 0)
                    gameExports.gameImports.sound(ent, Defines.CHAN_BODY, gameExports.gameImports
                            .soundindex("player/watr_in.wav"), 1,
                            Defines.ATTN_NORM, 0);
                else if ((ent.watertype & Defines.CONTENTS_WATER) != 0)
                    gameExports.gameImports.sound(ent, Defines.CHAN_BODY, gameExports.gameImports
                            .soundindex("player/watr_in.wav"), 1,
                            Defines.ATTN_NORM, 0);
            }

            ent.flags |= GameDefines.FL_INWATER;
            ent.damage_debounce_time = 0;
        }
    }

    public static EntThinkAdapter M_droptofloor = new EntThinkAdapter() {
        public String getID() { return "m_drop_to_floor";}
        public boolean think(SubgameEntity ent, GameExportsImpl gameExports) {
            float[] end = { 0, 0, 0 };
            trace_t trace;

            ent.s.origin[2] += 1;
            Math3D.VectorCopy(ent.s.origin, end);
            end[2] -= 256;

            trace = gameExports.gameImports.trace(ent.s.origin, ent.mins, ent.maxs, end,
                    ent, Defines.MASK_MONSTERSOLID);

            if (trace.fraction == 1 || trace.allsolid)
                return true;

            Math3D.VectorCopy(trace.endpos, ent.s.origin);

            gameExports.gameImports.linkentity(ent);
            M.M_CheckGround(ent, gameExports);
            M_CatagorizePosition(ent, gameExports);
            return true;
        }
    };

    public static void M_SetEffects(SubgameEntity ent, float time) {
        ent.s.effects &= ~(Defines.EF_COLOR_SHELL | Defines.EF_POWERSCREEN);
        ent.s.renderfx &= ~(Defines.RF_SHELL_RED | Defines.RF_SHELL_GREEN | Defines.RF_SHELL_BLUE);

        if ((ent.monsterinfo.aiflags & GameDefines.AI_RESURRECTING) != 0) {
            ent.s.effects |= Defines.EF_COLOR_SHELL;
            ent.s.renderfx |= Defines.RF_SHELL_RED;
        }

        if (ent.health <= 0)
            return;

        if (ent.powerarmor_time > time) {
            if (ent.monsterinfo.power_armor_type == GameDefines.POWER_ARMOR_SCREEN) {
                ent.s.effects |= Defines.EF_POWERSCREEN;
            } else if (ent.monsterinfo.power_armor_type == GameDefines.POWER_ARMOR_SHIELD) {
                ent.s.effects |= Defines.EF_COLOR_SHELL;
                ent.s.renderfx |= Defines.RF_SHELL_GREEN;
            }
        }
    };

    //ok
    public static void M_MoveFrame(SubgameEntity self, GameExportsImpl gameExports) {
        mmove_t move; //ptr
        int index;

        move = self.monsterinfo.currentmove;
        self.nextthink = gameExports.level.time + Defines.FRAMETIME;

        if ((self.monsterinfo.nextframe != 0)
                && (self.monsterinfo.nextframe >= move.firstframe)
                && (self.monsterinfo.nextframe <= move.lastframe)) {
            self.s.frame = self.monsterinfo.nextframe;
            self.monsterinfo.nextframe = 0;
        } else {
            if (self.s.frame == move.lastframe) {
                if (move.endfunc != null) {
                    move.endfunc.think(self, gameExports);

                    // regrab move, endfunc is very likely to change it
                    move = self.monsterinfo.currentmove;

                    // check for death
                    if ((self.svflags & Defines.SVF_DEADMONSTER) != 0)
                        return;
                }
            }

            if (self.s.frame < move.firstframe || self.s.frame > move.lastframe) {
                self.monsterinfo.aiflags &= ~GameDefines.AI_HOLD_FRAME;
                self.s.frame = move.firstframe;
            } else {
                if (0 == (self.monsterinfo.aiflags & GameDefines.AI_HOLD_FRAME)) {
                    self.s.frame++;
                    if (self.s.frame > move.lastframe)
                        self.s.frame = move.firstframe;
                }
            }
        }

        index = self.s.frame - move.firstframe;
        if (move.frame[index].ai != null)
            if (0 == (self.monsterinfo.aiflags & GameDefines.AI_HOLD_FRAME))
                move.frame[index].ai.ai(self, move.frame[index].dist
                        * self.monsterinfo.scale, gameExports);
            else
                move.frame[index].ai.ai(self, 0, gameExports);

        if (move.frame[index].think != null)
            move.frame[index].think.think(self, gameExports);
    }

    /** Stops the Flies. */
    public static EntThinkAdapter M_FliesOff = new EntThinkAdapter() {
        public String getID() { return "m_fliesoff";}
        public boolean think(SubgameEntity self, GameExportsImpl gameExports) {
            self.s.effects &= ~Defines.EF_FLIES;
            self.s.sound = 0;
            return true;
        }
    };

    /** Starts the Flies as setting the animation flag in the entity. */
    public static EntThinkAdapter M_FliesOn = new EntThinkAdapter() {
        public String getID() { return "m_flies_on";}
        public boolean think(SubgameEntity self, GameExportsImpl gameExports) {
            if (self.waterlevel != 0)
                return true;

            self.s.effects |= Defines.EF_FLIES;
            self.s.sound = gameExports.gameImports.soundindex("infantry/inflies1.wav");
            self.think = M_FliesOff;
            self.nextthink = gameExports.level.time + 60;
            return true;
        }
    };

    /** Adds some flies after a random time */
    public static EntThinkAdapter M_FlyCheck = new EntThinkAdapter() {
        public String getID() { return "m_fly_check";}
        public boolean think(SubgameEntity self, GameExportsImpl gameExports) {

            if (self.waterlevel != 0)
                return true;

            if (Globals.rnd.nextFloat() > 0.5)
                return true;

            self.think = M_FliesOn;
            self.nextthink = gameExports.level.time + 5 + 10
                    * Globals.rnd.nextFloat();
            return true;
        }
    };
}
