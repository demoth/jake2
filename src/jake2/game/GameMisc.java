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

// Created on 27.12.2003 by RST.
// $Id: GameMisc.java,v 1.7 2006-01-21 21:53:32 salomo Exp $
package jake2.game;

import jake2.qcommon.*;
import jake2.qcommon.util.Lib;
import jake2.qcommon.util.Math3D;

import java.util.Calendar;

public class GameMisc {
    static void SP_path_corner(edict_t self) {
        if (self.targetname == null) {
            GameBase.gi.dprintf("path_corner with no targetname at "
                    + Lib.vtos(self.s.origin) + "\n");
            GameUtil.G_FreeEdict(self);
            return;
        }

        self.solid = Defines.SOLID_TRIGGER;
        self.touch = path_corner_touch;
        Math3D.VectorSet(self.mins, -8, -8, -8);
        Math3D.VectorSet(self.maxs, 8, 8, 8);
        self.svflags |= Defines.SVF_NOCLIENT;
        GameBase.gi.linkentity(self);
    }

    static void SP_point_combat(edict_t self) {
        if (GameBase.deathmatch.value != 0) {
            GameUtil.G_FreeEdict(self);
            return;
        }
        self.solid = Defines.SOLID_TRIGGER;
        self.touch = point_combat_touch;
        Math3D.VectorSet(self.mins, -8, -8, -16);
        Math3D.VectorSet(self.maxs, 8, 8, 16);
        self.svflags = Defines.SVF_NOCLIENT;
        GameBase.gi.linkentity(self);
    };

    static void SP_viewthing(edict_t ent) {
        GameBase.gi.dprintf("viewthing spawned\n");

        ent.movetype = Defines.MOVETYPE_NONE;
        ent.solid = Defines.SOLID_BBOX;
        ent.s.renderfx = Defines.RF_FRAMELERP;
        Math3D.VectorSet(ent.mins, -16, -16, -24);
        Math3D.VectorSet(ent.maxs, 16, 16, 32);
        ent.s.modelindex = GameBase.gi
                .modelindex("models/objects/banner/tris.md2");
        GameBase.gi.linkentity(ent);
        ent.nextthink = GameBase.level.time + 0.5f;
        ent.think = TH_viewthing;
        return;
    }

    /*
     * QUAKED info_null (0 0.5 0) (-4 -4 -4) (4 4 4) Used as a positional target
     * for spotlights, etc.
     */
    static void SP_info_null(edict_t self) {
        GameUtil.G_FreeEdict(self);
    };

    /*
     * QUAKED info_notnull (0 0.5 0) (-4 -4 -4) (4 4 4) Used as a positional
     * target for lightning.
     */
    static void SP_info_notnull(edict_t self) {
        Math3D.VectorCopy(self.s.origin, self.absmin);
        Math3D.VectorCopy(self.s.origin, self.absmax);
    };

    static void SP_light(edict_t self) {
        // no targeted lights in deathmatch, because they cause global messages
        if (null == self.targetname || GameBase.deathmatch.value != 0) {
            GameUtil.G_FreeEdict(self);
            return;
        }

        if (self.style >= 32) {
            self.use = light_use;
            if ((self.spawnflags & START_OFF) != 0)
                GameBase.gi.configstring(Defines.CS_LIGHTS + self.style, "a");
            else
                GameBase.gi.configstring(Defines.CS_LIGHTS + self.style, "m");
        }
    }

    static void SP_func_wall(edict_t self) {
        self.movetype = Defines.MOVETYPE_PUSH;
        GameBase.gi.setmodel(self, self.model);

        if ((self.spawnflags & 8) != 0)
            self.s.effects |= Defines.EF_ANIM_ALL;
        if ((self.spawnflags & 16) != 0)
            self.s.effects |= Defines.EF_ANIM_ALLFAST;

        // just a wall
        if ((self.spawnflags & 7) == 0) {
            self.solid = Defines.SOLID_BSP;
            GameBase.gi.linkentity(self);
            return;
        }

        // it must be TRIGGER_SPAWN
        if (0 == (self.spawnflags & 1)) {
            GameBase.gi.dprintf("func_wall missing TRIGGER_SPAWN\n");
            self.spawnflags |= 1;
        }

        // yell if the spawnflags are odd
        if ((self.spawnflags & 4) != 0) {
            if (0 == (self.spawnflags & 2)) {
                GameBase.gi.dprintf("func_wall START_ON without TOGGLE\n");
                self.spawnflags |= 2;
            }
        }

        self.use = func_wall_use;
        if ((self.spawnflags & 4) != 0) {
            self.solid = Defines.SOLID_BSP;
        } else {
            self.solid = Defines.SOLID_NOT;
            self.svflags |= Defines.SVF_NOCLIENT;
        }
        GameBase.gi.linkentity(self);
    }

    static void SP_func_object(edict_t self) {
        GameBase.gi.setmodel(self, self.model);

        self.mins[0] += 1;
        self.mins[1] += 1;
        self.mins[2] += 1;
        self.maxs[0] -= 1;
        self.maxs[1] -= 1;
        self.maxs[2] -= 1;

        if (self.dmg == 0)
            self.dmg = 100;

        if (self.spawnflags == 0) {
            self.solid = Defines.SOLID_BSP;
            self.movetype = Defines.MOVETYPE_PUSH;
            self.think = func_object_release;
            self.nextthink = GameBase.level.time + 2 * Defines.FRAMETIME;
        } else {
            self.solid = Defines.SOLID_NOT;
            self.movetype = Defines.MOVETYPE_PUSH;
            self.use = func_object_use;
            self.svflags |= Defines.SVF_NOCLIENT;
        }

        if ((self.spawnflags & 2) != 0)
            self.s.effects |= Defines.EF_ANIM_ALL;
        if ((self.spawnflags & 4) != 0)
            self.s.effects |= Defines.EF_ANIM_ALLFAST;

        self.clipmask = Defines.MASK_MONSTERSOLID;

        GameBase.gi.linkentity(self);
    }

    static void SP_func_explosive(edict_t self) {
        if (GameBase.deathmatch.value != 0) { // auto-remove for deathmatch
            GameUtil.G_FreeEdict(self);
            return;
        }

        self.movetype = Defines.MOVETYPE_PUSH;

        GameBase.gi.modelindex("models/objects/debris1/tris.md2");
        GameBase.gi.modelindex("models/objects/debris2/tris.md2");

        GameBase.gi.setmodel(self, self.model);

        if ((self.spawnflags & 1) != 0) {
            self.svflags |= Defines.SVF_NOCLIENT;
            self.solid = Defines.SOLID_NOT;
            self.use = func_explosive_spawn;
        } else {
            self.solid = Defines.SOLID_BSP;
            if (self.targetname != null)
                self.use = func_explosive_use;
        }

        if ((self.spawnflags & 2) != 0)
            self.s.effects |= Defines.EF_ANIM_ALL;
        if ((self.spawnflags & 4) != 0)
            self.s.effects |= Defines.EF_ANIM_ALLFAST;

        if (self.use != func_explosive_use) {
            if (self.health == 0)
                self.health = 100;
            self.die = func_explosive_explode;
            self.takedamage = Defines.DAMAGE_YES;
        }

        GameBase.gi.linkentity(self);
    }

    static void SP_misc_explobox(edict_t self) {
        if (GameBase.deathmatch.value != 0) { // auto-remove for deathmatch
            GameUtil.G_FreeEdict(self);
            return;
        }

        GameBase.gi.modelindex("models/objects/debris1/tris.md2");
        GameBase.gi.modelindex("models/objects/debris2/tris.md2");
        GameBase.gi.modelindex("models/objects/debris3/tris.md2");

        self.solid = Defines.SOLID_BBOX;
        self.movetype = Defines.MOVETYPE_STEP;

        self.model = "models/objects/barrels/tris.md2";
        self.s.modelindex = GameBase.gi.modelindex(self.model);
        Math3D.VectorSet(self.mins, -16, -16, 0);
        Math3D.VectorSet(self.maxs, 16, 16, 40);

        if (self.mass == 0)
            self.mass = 400;
        if (0 == self.health)
            self.health = 10;
        if (0 == self.dmg)
            self.dmg = 150;

        self.die = barrel_delay;
        self.takedamage = Defines.DAMAGE_YES;
        self.monsterinfo.aiflags = Defines.AI_NOSTEP;

        self.touch = barrel_touch;

        self.think = M.M_droptofloor;
        self.nextthink = GameBase.level.time + 2 * Defines.FRAMETIME;

        GameBase.gi.linkentity(self);
    }

    static void SP_misc_blackhole(edict_t ent) {
        ent.movetype = Defines.MOVETYPE_NONE;
        ent.solid = Defines.SOLID_NOT;
        Math3D.VectorSet(ent.mins, -64, -64, 0);
        Math3D.VectorSet(ent.maxs, 64, 64, 8);
        ent.s.modelindex = GameBase.gi
                .modelindex("models/objects/black/tris.md2");
        ent.s.renderfx = Defines.RF_TRANSLUCENT;
        ent.use = misc_blackhole_use;
        ent.think = misc_blackhole_think;
        ent.nextthink = GameBase.level.time + 2 * Defines.FRAMETIME;
        GameBase.gi.linkentity(ent);
    }

    static void SP_misc_eastertank(edict_t ent) {
        ent.movetype = Defines.MOVETYPE_NONE;
        ent.solid = Defines.SOLID_BBOX;
        Math3D.VectorSet(ent.mins, -32, -32, -16);
        Math3D.VectorSet(ent.maxs, 32, 32, 32);
        ent.s.modelindex = GameBase.gi
                .modelindex("models/monsters/tank/tris.md2");
        ent.s.frame = 254;
        ent.think = misc_eastertank_think;
        ent.nextthink = GameBase.level.time + 2 * Defines.FRAMETIME;
        GameBase.gi.linkentity(ent);
    }

    static void SP_misc_easterchick(edict_t ent) {
        ent.movetype = Defines.MOVETYPE_NONE;
        ent.solid = Defines.SOLID_BBOX;
        Math3D.VectorSet(ent.mins, -32, -32, 0);
        Math3D.VectorSet(ent.maxs, 32, 32, 32);
        ent.s.modelindex = GameBase.gi
                .modelindex("models/monsters/bitch/tris.md2");
        ent.s.frame = 208;
        ent.think = misc_easterchick_think;
        ent.nextthink = GameBase.level.time + 2 * Defines.FRAMETIME;
        GameBase.gi.linkentity(ent);
    }

    static void SP_misc_easterchick2(edict_t ent) {
        ent.movetype = Defines.MOVETYPE_NONE;
        ent.solid = Defines.SOLID_BBOX;
        Math3D.VectorSet(ent.mins, -32, -32, 0);
        Math3D.VectorSet(ent.maxs, 32, 32, 32);
        ent.s.modelindex = GameBase.gi
                .modelindex("models/monsters/bitch/tris.md2");
        ent.s.frame = 248;
        ent.think = misc_easterchick2_think;
        ent.nextthink = GameBase.level.time + 2 * Defines.FRAMETIME;
        GameBase.gi.linkentity(ent);
    }

    static void SP_monster_commander_body(edict_t self) {
        self.movetype = Defines.MOVETYPE_NONE;
        self.solid = Defines.SOLID_BBOX;
        self.model = "models/monsters/commandr/tris.md2";
        self.s.modelindex = GameBase.gi.modelindex(self.model);
        Math3D.VectorSet(self.mins, -32, -32, 0);
        Math3D.VectorSet(self.maxs, 32, 32, 48);
        self.use = commander_body_use;
        self.takedamage = Defines.DAMAGE_YES;
        self.flags = Defines.FL_GODMODE;
        self.s.renderfx |= Defines.RF_FRAMELERP;
        GameBase.gi.linkentity(self);

        GameBase.gi.soundindex("tank/thud.wav");
        GameBase.gi.soundindex("tank/pain.wav");

        self.think = commander_body_drop;
        self.nextthink = GameBase.level.time + 5 * Defines.FRAMETIME;
    }

    static void SP_misc_banner(edict_t ent) {
        ent.movetype = Defines.MOVETYPE_NONE;
        ent.solid = Defines.SOLID_NOT;
        ent.s.modelindex = GameBase.gi
                .modelindex("models/objects/banner/tris.md2");
        ent.s.frame = Lib.rand() % 16;
        GameBase.gi.linkentity(ent);

        ent.think = misc_banner_think;
        ent.nextthink = GameBase.level.time + Defines.FRAMETIME;
    }

    static void SP_misc_deadsoldier(edict_t ent) {
        if (GameBase.deathmatch.value != 0) { // auto-remove for deathmatch
            GameUtil.G_FreeEdict(ent);
            return;
        }

        ent.movetype = Defines.MOVETYPE_NONE;
        ent.solid = Defines.SOLID_BBOX;
        ent.s.modelindex = GameBase.gi
                .modelindex("models/deadbods/dude/tris.md2");

        // Defaults to frame 0
        if ((ent.spawnflags & 2) != 0)
            ent.s.frame = 1;
        else if ((ent.spawnflags & 4) != 0)
            ent.s.frame = 2;
        else if ((ent.spawnflags & 8) != 0)
            ent.s.frame = 3;
        else if ((ent.spawnflags & 16) != 0)
            ent.s.frame = 4;
        else if ((ent.spawnflags & 32) != 0)
            ent.s.frame = 5;
        else
            ent.s.frame = 0;

        Math3D.VectorSet(ent.mins, -16, -16, 0);
        Math3D.VectorSet(ent.maxs, 16, 16, 16);
        ent.deadflag = Defines.DEAD_DEAD;
        ent.takedamage = Defines.DAMAGE_YES;
        ent.svflags |= Defines.SVF_MONSTER | Defines.SVF_DEADMONSTER;
        ent.die = misc_deadsoldier_die;
        ent.monsterinfo.aiflags |= Defines.AI_GOOD_GUY;

        GameBase.gi.linkentity(ent);
    }

    static void SP_misc_viper(edict_t ent) {
        if (null == ent.target) {
            GameBase.gi.dprintf("misc_viper without a target at "
                    + Lib.vtos(ent.absmin) + "\n");
            GameUtil.G_FreeEdict(ent);
            return;
        }

        if (0 == ent.speed)
            ent.speed = 300;

        ent.movetype = Defines.MOVETYPE_PUSH;
        ent.solid = Defines.SOLID_NOT;
        ent.s.modelindex = GameBase.gi
                .modelindex("models/ships/viper/tris.md2");
        Math3D.VectorSet(ent.mins, -16, -16, 0);
        Math3D.VectorSet(ent.maxs, 16, 16, 32);

        ent.think = GameFunc.func_train_find;
        ent.nextthink = GameBase.level.time + Defines.FRAMETIME;
        ent.use = misc_viper_use;
        ent.svflags |= Defines.SVF_NOCLIENT;
        ent.moveinfo.accel = ent.moveinfo.decel = ent.moveinfo.speed = ent.speed;

        GameBase.gi.linkentity(ent);
    }

    /*
     * QUAKED misc_bigviper (1 .5 0) (-176 -120 -24) (176 120 72) This is a
     * large stationary viper as seen in Paul's intro
     */
    static void SP_misc_bigviper(edict_t ent) {
        ent.movetype = Defines.MOVETYPE_NONE;
        ent.solid = Defines.SOLID_BBOX;
        Math3D.VectorSet(ent.mins, -176, -120, -24);
        Math3D.VectorSet(ent.maxs, 176, 120, 72);
        ent.s.modelindex = GameBase.gi
                .modelindex("models/ships/bigviper/tris.md2");
        GameBase.gi.linkentity(ent);
    }

    static void SP_misc_viper_bomb(edict_t self) {
        self.movetype = Defines.MOVETYPE_NONE;
        self.solid = Defines.SOLID_NOT;
        Math3D.VectorSet(self.mins, -8, -8, -8);
        Math3D.VectorSet(self.maxs, 8, 8, 8);

        self.s.modelindex = GameBase.gi
                .modelindex("models/objects/bomb/tris.md2");

        if (self.dmg == 0)
            self.dmg = 1000;

        self.use = misc_viper_bomb_use;
        self.svflags |= Defines.SVF_NOCLIENT;

        GameBase.gi.linkentity(self);
    }

    static void SP_misc_strogg_ship(edict_t ent) {
        if (null == ent.target) {
            GameBase.gi.dprintf(ent.classname + " without a target at "
                    + Lib.vtos(ent.absmin) + "\n");
            GameUtil.G_FreeEdict(ent);
            return;
        }

        if (0 == ent.speed)
            ent.speed = 300;

        ent.movetype = Defines.MOVETYPE_PUSH;
        ent.solid = Defines.SOLID_NOT;
        ent.s.modelindex = GameBase.gi
                .modelindex("models/ships/strogg1/tris.md2");
        Math3D.VectorSet(ent.mins, -16, -16, 0);
        Math3D.VectorSet(ent.maxs, 16, 16, 32);

        ent.think = GameFunc.func_train_find;
        ent.nextthink = GameBase.level.time + Defines.FRAMETIME;
        ent.use = misc_strogg_ship_use;
        ent.svflags |= Defines.SVF_NOCLIENT;
        ent.moveinfo.accel = ent.moveinfo.decel = ent.moveinfo.speed = ent.speed;

        GameBase.gi.linkentity(ent);
    }

    static void SP_misc_satellite_dish(edict_t ent) {
        ent.movetype = Defines.MOVETYPE_NONE;
        ent.solid = Defines.SOLID_BBOX;
        Math3D.VectorSet(ent.mins, -64, -64, 0);
        Math3D.VectorSet(ent.maxs, 64, 64, 128);
        ent.s.modelindex = GameBase.gi
                .modelindex("models/objects/satellite/tris.md2");
        ent.use = misc_satellite_dish_use;
        GameBase.gi.linkentity(ent);
    }

    /*
     * QUAKED light_mine1 (0 1 0) (-2 -2 -12) (2 2 12)
     */
    static void SP_light_mine1(edict_t ent) {
        ent.movetype = Defines.MOVETYPE_NONE;
        ent.solid = Defines.SOLID_BBOX;
        ent.s.modelindex = GameBase.gi
                .modelindex("models/objects/minelite/light1/tris.md2");
        GameBase.gi.linkentity(ent);
    }

    /*
     * QUAKED light_mine2 (0 1 0) (-2 -2 -12) (2 2 12)
     */
    static void SP_light_mine2(edict_t ent) {
        ent.movetype = Defines.MOVETYPE_NONE;
        ent.solid = Defines.SOLID_BBOX;
        ent.s.modelindex = GameBase.gi
                .modelindex("models/objects/minelite/light2/tris.md2");
        GameBase.gi.linkentity(ent);
    }

    /*
     * QUAKED misc_gib_arm (1 0 0) (-8 -8 -8) (8 8 8) Intended for use with the
     * target_spawner
     */
    static void SP_misc_gib_arm(edict_t ent) {
        GameBase.gi.setmodel(ent, "models/objects/gibs/arm/tris.md2");
        ent.solid = Defines.SOLID_NOT;
        ent.s.effects |= Defines.EF_GIB;
        ent.takedamage = Defines.DAMAGE_YES;
        ent.die = gib_die;
        ent.movetype = Defines.MOVETYPE_TOSS;
        ent.svflags |= Defines.SVF_MONSTER;
        ent.deadflag = Defines.DEAD_DEAD;
        ent.avelocity[0] = Lib.random() * 200;
        ent.avelocity[1] = Lib.random() * 200;
        ent.avelocity[2] = Lib.random() * 200;
        ent.think = GameUtil.G_FreeEdictA;
        ent.nextthink = GameBase.level.time + 30;
        GameBase.gi.linkentity(ent);
    }

    /*
     * QUAKED misc_gib_leg (1 0 0) (-8 -8 -8) (8 8 8) Intended for use with the
     * target_spawner
     */
    static void SP_misc_gib_leg(edict_t ent) {
        GameBase.gi.setmodel(ent, "models/objects/gibs/leg/tris.md2");
        ent.solid = Defines.SOLID_NOT;
        ent.s.effects |= Defines.EF_GIB;
        ent.takedamage = Defines.DAMAGE_YES;
        ent.die = gib_die;
        ent.movetype = Defines.MOVETYPE_TOSS;
        ent.svflags |= Defines.SVF_MONSTER;
        ent.deadflag = Defines.DEAD_DEAD;
        ent.avelocity[0] = Lib.random() * 200;
        ent.avelocity[1] = Lib.random() * 200;
        ent.avelocity[2] = Lib.random() * 200;
        ent.think = GameUtil.G_FreeEdictA;
        ent.nextthink = GameBase.level.time + 30;
        GameBase.gi.linkentity(ent);
    }

    /*
     * QUAKED misc_gib_head (1 0 0) (-8 -8 -8) (8 8 8) Intended for use with the
     * target_spawner
     */
    static void SP_misc_gib_head(edict_t ent) {
        GameBase.gi.setmodel(ent, "models/objects/gibs/head/tris.md2");
        ent.solid = Defines.SOLID_NOT;
        ent.s.effects |= Defines.EF_GIB;
        ent.takedamage = Defines.DAMAGE_YES;
        ent.die = gib_die;
        ent.movetype = Defines.MOVETYPE_TOSS;
        ent.svflags |= Defines.SVF_MONSTER;
        ent.deadflag = Defines.DEAD_DEAD;
        ent.avelocity[0] = Lib.random() * 200;
        ent.avelocity[1] = Lib.random() * 200;
        ent.avelocity[2] = Lib.random() * 200;
        ent.think = GameUtil.G_FreeEdictA;
        ent.nextthink = GameBase.level.time + 30;
        GameBase.gi.linkentity(ent);
    }

    //=====================================================

    /*
     * QUAKED target_character (0 0 1) ? used with target_string (must be on
     * same "team") "count" is position in the string (starts at 1)
     */

    static void SP_target_character(edict_t self) {
        self.movetype = Defines.MOVETYPE_PUSH;
        GameBase.gi.setmodel(self, self.model);
        self.solid = Defines.SOLID_BSP;
        self.s.frame = 12;
        GameBase.gi.linkentity(self);
        return;
    }

    static void SP_target_string(edict_t self) {
        if (self.message == null)
            self.message = "";
        self.use = target_string_use;
    }

    // don't let field width of any clock messages change, or it
    // could cause an overwrite after a game load

    private static void func_clock_reset(edict_t self) {
        self.activator = null;
        if ((self.spawnflags & 1) != 0) {
            self.health = 0;
            self.wait = self.count;
        } else if ((self.spawnflags & 2) != 0) {
            self.health = self.count;
            self.wait = 0;
        }
    }

    private static void func_clock_format_countdown(edict_t self) {
        if (self.style == 0) {
            self.message = "" + self.health;
            //Com_sprintf(self.message, CLOCK_MESSAGE_SIZE, "%2i",
            // self.health);
            return;
        }

        if (self.style == 1) {
            self.message = "" + self.health / 60 + ":" + self.health % 60;
            //Com_sprintf(self.message, CLOCK_MESSAGE_SIZE, "%2i:%2i",
            // self.health / 60, self.health % 60);
            /*
             * if (self.message.charAt(3) == ' ') self.message.charAt(3) = '0';
             */
            return;
        }

        if (self.style == 2) {
            self.message = "" + self.health / 3600 + ":"
                    + (self.health - (self.health / 3600) * 3600) / 60 + ":"
                    + self.health % 60;
            /*
             * Com_sprintf( self.message, CLOCK_MESSAGE_SIZE, "%2i:%2i:%2i",
             * self.health / 3600, (self.health - (self.health / 3600) * 3600) /
             * 60, self.health % 60); if (self.message[3] == ' ')
             * self.message[3] = '0'; if (self.message[6] == ' ')
             * self.message[6] = '0';
             */
            return;
        }
    }

    static void SP_func_clock(edict_t self) {
        if (self.target == null) {
            GameBase.gi.dprintf(self.classname + " with no target at "
                    + Lib.vtos(self.s.origin) + "\n");
            GameUtil.G_FreeEdict(self);
            return;
        }

        if ((self.spawnflags & 2) != 0 && (0 == self.count)) {
            GameBase.gi.dprintf(self.classname + " with no count at "
                    + Lib.vtos(self.s.origin) + "\n");
            GameUtil.G_FreeEdict(self);
            return;
        }

        if ((self.spawnflags & 1) != 0 && (0 == self.count))
            self.count = 60 * 60;

        func_clock_reset(self);

        self.message = new String();

        self.think = func_clock_think;

        if ((self.spawnflags & 4) != 0)
            self.use = func_clock_use;
        else
            self.nextthink = GameBase.level.time + 1;
    }

    /**
     * QUAKED misc_teleporter (1 0 0) (-32 -32 -24) (32 32 -16) Stepping onto
     * this disc will teleport players to the targeted misc_teleporter_dest
     * object.
     */
    static void SP_misc_teleporter(edict_t ent) {
        edict_t trig;

        if (ent.target == null) {
            GameBase.gi.dprintf("teleporter without a target.\n");
            GameUtil.G_FreeEdict(ent);
            return;
        }

        GameBase.gi.setmodel(ent, "models/objects/dmspot/tris.md2");
        ent.s.skinnum = 1;
        ent.s.effects = Defines.EF_TELEPORTER;
        ent.s.sound = GameBase.gi.soundindex("world/amb10.wav");
        ent.solid = Defines.SOLID_BBOX;

        Math3D.VectorSet(ent.mins, -32, -32, -24);
        Math3D.VectorSet(ent.maxs, 32, 32, -16);
        GameBase.gi.linkentity(ent);

        trig = GameUtil.G_Spawn();
        trig.touch = teleporter_touch;
        trig.solid = Defines.SOLID_TRIGGER;
        trig.target = ent.target;
        trig.owner = ent;
        Math3D.VectorCopy(ent.s.origin, trig.s.origin);
        Math3D.VectorSet(trig.mins, -8, -8, 8);
        Math3D.VectorSet(trig.maxs, 8, 8, 24);
        GameBase.gi.linkentity(trig);
    }

    /**
     * QUAKED func_group (0 0 0) ? Used to group brushes together just for
     * editor convenience.
     */

    private static void VelocityForDamage(int damage, float[] v) {
        v[0] = 100.0f * Lib.crandom();
        v[1] = 100.0f * Lib.crandom();
        v[2] = 200.0f + 100.0f * Lib.random();
    
        if (damage < 50)
            Math3D.VectorScale(v, 0.7f, v);
        else
            Math3D.VectorScale(v, 1.2f, v);
    }

    public static void BecomeExplosion1(edict_t self) {
        GameBase.gi.WriteByte(Defines.svc_temp_entity);
        GameBase.gi.WriteByte(Defines.TE_EXPLOSION1);
        GameBase.gi.WritePosition(self.s.origin);
        GameBase.gi.multicast(self.s.origin, Defines.MULTICAST_PVS);
    
        GameUtil.G_FreeEdict(self);
    }

    private static void BecomeExplosion2(edict_t self) {
        GameBase.gi.WriteByte(Defines.svc_temp_entity);
        GameBase.gi.WriteByte(Defines.TE_EXPLOSION2);
        GameBase.gi.WritePosition(self.s.origin);
        GameBase.gi.multicast(self.s.origin, Defines.MULTICAST_PVS);
    
        GameUtil.G_FreeEdict(self);
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

    static void ThrowClientHead(edict_t self, int damage) {
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

        gclient_t client = (gclient_t) self.client;
        if (client != null)
        // bodies in the queue don't have a client anymore
        {
            client.anim_priority = Defines.ANIM_DEATH;
            client.anim_end = self.s.frame;
        } else {
            self.think = null;
            self.nextthink = 0;
        }
    
        GameBase.gi.linkentity(self);
    }

    static void ThrowDebris(edict_t self, String modelname, float speed,
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

    private static void ClipGibVelocity(edict_t ent) {
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

    private static EntUseAdapter Use_Areaportal = new EntUseAdapter() {
        public String getID() { return "use_areaportal";}
        public void use(edict_t ent, edict_t other, edict_t activator) {
            ent.count ^= 1; // toggle state
            //	gi.dprintf ("portalstate: %i = %i\n", ent.style, ent.count);
            GameBase.gi.SetAreaPortalState(ent.style, ent.count != 0);
        }
    };

    /**
     * QUAKED func_areaportal (0 0 0) ?
     * 
     * This is a non-visible object that divides the world into areas that are
     * seperated when this portal is not activated. Usually enclosed in the
     * middle of a door.
     */

    static EntThinkAdapter SP_func_areaportal = new EntThinkAdapter() {
        public String getID() { return "sp_func_areaportal";}
        public boolean think(edict_t ent) {
            ent.use = Use_Areaportal;
            ent.count = 0; // always start closed;
            return true;
        }
    };

    /**
     * QUAKED path_corner (.5 .3 0) (-8 -8 -8) (8 8 8) TELEPORT Target: next
     * path corner Pathtarget: gets used when an entity that has this
     * path_corner targeted touches it
     */
    private static EntTouchAdapter path_corner_touch = new EntTouchAdapter() {
        public String getID() { return "path_corner_touch";}
        public void touch(edict_t self, edict_t other, cplane_t plane,
                csurface_t surf) {
            float[] v = { 0, 0, 0 };
            edict_t next;

            if (other.movetarget != self)
                return;

            if (other.enemy != null)
                return;

            if (self.pathtarget != null) {
                String savetarget;

                savetarget = self.target;
                self.target = self.pathtarget;
                GameUtil.G_UseTargets(self, other);
                self.target = savetarget;
            }

            if (self.target != null)
                next = GameBase.G_PickTarget(self.target);
            else
                next = null;

            if ((next != null) && (next.spawnflags & 1) != 0) {
                Math3D.VectorCopy(next.s.origin, v);
                v[2] += next.mins[2];
                v[2] -= other.mins[2];
                Math3D.VectorCopy(v, other.s.origin);
                next = GameBase.G_PickTarget(next.target);
                other.s.event = Defines.EV_OTHER_TELEPORT;
            }

            other.goalentity = other.movetarget = next;

            if (self.wait != 0) {
                other.monsterinfo.pausetime = GameBase.level.time + self.wait;
                other.monsterinfo.stand.think(other);
                return;
            }

            if (other.movetarget == null) {
                other.monsterinfo.pausetime = GameBase.level.time + 100000000;
                other.monsterinfo.stand.think(other);
            } else {
                Math3D.VectorSubtract(other.goalentity.s.origin,
                        other.s.origin, v);
                other.ideal_yaw = Math3D.vectoyaw(v);
            }
        }
    };

    /*
     * QUAKED point_combat (0.5 0.3 0) (-8 -8 -8) (8 8 8) Hold Makes this the
     * target of a monster and it will head here when first activated before
     * going after the activator. If hold is selected, it will stay here.
     */
    private static EntTouchAdapter point_combat_touch = new EntTouchAdapter() {
        public String getID() { return "point_combat_touch";}
        public void touch(edict_t self, edict_t other, cplane_t plane,
                csurface_t surf) {
            edict_t activator;

            if (other.movetarget != self)
                return;

            if (self.target != null) {
                other.target = self.target;
                other.goalentity = other.movetarget = GameBase
                        .G_PickTarget(other.target);
                if (null == other.goalentity) {
                    GameBase.gi.dprintf(self.classname + " at "
                            + Lib.vtos(self.s.origin) + " target "
                            + self.target + " does not exist\n");
                    other.movetarget = self;
                }
                self.target = null;
            } else if ((self.spawnflags & 1) != 0
                    && 0 == (other.flags & (Defines.FL_SWIM | Defines.FL_FLY))) {
                other.monsterinfo.pausetime = GameBase.level.time + 100000000;
                other.monsterinfo.aiflags |= Defines.AI_STAND_GROUND;
                other.monsterinfo.stand.think(other);
            }

            if (other.movetarget == self) {
                other.target = null;
                other.movetarget = null;
                other.goalentity = other.enemy;
                other.monsterinfo.aiflags &= ~Defines.AI_COMBAT_POINT;
            }

            if (self.pathtarget != null) {
                String savetarget;

                savetarget = self.target;
                self.target = self.pathtarget;
                if (other.enemy != null && other.enemy.client != null)
                    activator = other.enemy;
                else if (other.oldenemy != null
                        && other.oldenemy.client != null)
                    activator = other.oldenemy;
                else if (other.activator != null
                        && other.activator.client != null)
                    activator = other.activator;
                else
                    activator = other;
                GameUtil.G_UseTargets(self, activator);
                self.target = savetarget;
            }
        }
    };

    /*
     * QUAKED viewthing (0 .5 .8) (-8 -8 -8) (8 8 8) Just for the debugging
     * level. Don't use
     */
    private static EntThinkAdapter TH_viewthing = new EntThinkAdapter() {
        public String getID() { return "th_viewthing";}
        public boolean think(edict_t ent) {
            ent.s.frame = (ent.s.frame + 1) % 7;
            ent.nextthink = GameBase.level.time + Defines.FRAMETIME;
            return true;
        }
    };

    /*
     * QUAKED light (0 1 0) (-8 -8 -8) (8 8 8) START_OFF Non-displayed light.
     * Default light value is 300. Default style is 0. If targeted, will toggle
     * between on and off. Default _cone value is 10 (used to set size of light
     * for spotlights)
     */

    private static final int START_OFF = 1;

    private static EntUseAdapter light_use = new EntUseAdapter() {
        public String getID() { return "light_use";}
        public void use(edict_t self, edict_t other, edict_t activator) {
            if ((self.spawnflags & START_OFF) != 0) {
                GameBase.gi.configstring(Defines.CS_LIGHTS + self.style, "m");
                self.spawnflags &= ~START_OFF;
            } else {
                GameBase.gi.configstring(Defines.CS_LIGHTS + self.style, "a");
                self.spawnflags |= START_OFF;
            }
        }
    };

    /*
     * QUAKED func_wall (0 .5 .8) ? TRIGGER_SPAWN TOGGLE START_ON ANIMATED
     * ANIMATED_FAST This is just a solid wall if not inhibited
     * 
     * TRIGGER_SPAWN the wall will not be present until triggered it will then
     * blink in to existance; it will kill anything that was in it's way
     * 
     * TOGGLE only valid for TRIGGER_SPAWN walls this allows the wall to be
     * turned on and off
     * 
     * START_ON only valid for TRIGGER_SPAWN walls the wall will initially be
     * present
     */

    private static EntUseAdapter func_wall_use = new EntUseAdapter() {
        public String getID() { return "func_wall_use";}
        public void use(edict_t self, edict_t other, edict_t activator) {
            if (self.solid == Defines.SOLID_NOT) {
                self.solid = Defines.SOLID_BSP;
                self.svflags &= ~Defines.SVF_NOCLIENT;
                GameUtil.KillBox(self);
            } else {
                self.solid = Defines.SOLID_NOT;
                self.svflags |= Defines.SVF_NOCLIENT;
            }
            GameBase.gi.linkentity(self);

            if (0 == (self.spawnflags & 2))
                self.use = null;
        }
    };

    /*
     * QUAKED func_object (0 .5 .8) ? TRIGGER_SPAWN ANIMATED ANIMATED_FAST This
     * is solid bmodel that will fall if it's support it removed.
     */
    private static EntTouchAdapter func_object_touch = new EntTouchAdapter() {
        public String getID() { return "func_object_touch";}
        public void touch(edict_t self, edict_t other, cplane_t plane,
                csurface_t surf) {
            // only squash thing we fall on top of
            if (plane == null)
                return;
            if (plane.normal[2] < 1.0)
                return;
            if (other.takedamage == Defines.DAMAGE_NO)
                return;
            GameCombat.T_Damage(other, self, self, Globals.vec3_origin,
                    self.s.origin, Globals.vec3_origin, self.dmg, 1, 0,
                    Defines.MOD_CRUSH);
        }
    };

    private static EntThinkAdapter func_object_release = new EntThinkAdapter() {
        public String getID() { return "func_object_release";}
        public boolean think(edict_t self) {
            self.movetype = Defines.MOVETYPE_TOSS;
            self.touch = func_object_touch;
            return true;
        }
    };

    private static EntUseAdapter func_object_use = new EntUseAdapter() {
        public String getID() { return "func_object_use";}
        public void use(edict_t self, edict_t other, edict_t activator) {
            self.solid = Defines.SOLID_BSP;
            self.svflags &= ~Defines.SVF_NOCLIENT;
            self.use = null;
            GameUtil.KillBox(self);
            func_object_release.think(self);
        }
    };

    /*
     * QUAKED func_explosive (0 .5 .8) ? Trigger_Spawn ANIMATED ANIMATED_FAST
     * Any brush that you want to explode or break apart. If you want an
     * ex0plosion, set dmg and it will do a radius explosion of that amount at
     * the center of the bursh.
     * 
     * If targeted it will not be shootable.
     * 
     * health defaults to 100.
     * 
     * mass defaults to 75. This determines how much debris is emitted when it
     * explodes. You get one large chunk per 100 of mass (up to 8) and one small
     * chunk per 25 of mass (up to 16). So 800 gives the most.
     */
    private static EntDieAdapter func_explosive_explode = new EntDieAdapter() {
        public String getID() { return "func_explosive_explode";}
        public void die(edict_t self, edict_t inflictor, edict_t attacker,
                int damage, float[] point) {
            float[] origin = { 0, 0, 0 };
            float[] chunkorigin = { 0, 0, 0 };
            float[] size = { 0, 0, 0 };
            int count;
            int mass;

            // bmodel origins are (0 0 0), we need to adjust that here
            Math3D.VectorScale(self.size, 0.5f, size);
            Math3D.VectorAdd(self.absmin, size, origin);
            Math3D.VectorCopy(origin, self.s.origin);

            self.takedamage = Defines.DAMAGE_NO;

            if (self.dmg != 0)
                GameCombat.T_RadiusDamage(self, attacker, self.dmg, null,
                        self.dmg + 40, Defines.MOD_EXPLOSIVE);

            Math3D.VectorSubtract(self.s.origin, inflictor.s.origin,
                    self.velocity);
            Math3D.VectorNormalize(self.velocity);
            Math3D.VectorScale(self.velocity, 150, self.velocity);

            // start chunks towards the center
            Math3D.VectorScale(size, 0.5f, size);

            mass = self.mass;
            if (0 == mass)
                mass = 75;

            // big chunks
            if (mass >= 100) {
                count = mass / 100;
                if (count > 8)
                    count = 8;
                while (count-- != 0) {
                    chunkorigin[0] = origin[0] + Lib.crandom() * size[0];
                    chunkorigin[1] = origin[1] + Lib.crandom() * size[1];
                    chunkorigin[2] = origin[2] + Lib.crandom() * size[2];
                    ThrowDebris(self, "models/objects/debris1/tris.md2",
                            1, chunkorigin);
                }
            }

            // small chunks
            count = mass / 25;
            if (count > 16)
                count = 16;
            while (count-- != 0) {
                chunkorigin[0] = origin[0] + Lib.crandom() * size[0];
                chunkorigin[1] = origin[1] + Lib.crandom() * size[1];
                chunkorigin[2] = origin[2] + Lib.crandom() * size[2];
                ThrowDebris(self, "models/objects/debris2/tris.md2", 2,
                        chunkorigin);
            }

            GameUtil.G_UseTargets(self, attacker);

            if (self.dmg != 0)
                BecomeExplosion1(self);
            else
                GameUtil.G_FreeEdict(self);
        }
    };

    private static EntUseAdapter func_explosive_use = new EntUseAdapter() {
        public String getID() { return "func_explosive_use";}
        public void use(edict_t self, edict_t other, edict_t activator) {
            func_explosive_explode.die(self, self, other, self.health,
                    Globals.vec3_origin);
        }
    };

    private static EntUseAdapter func_explosive_spawn = new EntUseAdapter() {
        public String getID() { return "func_explosive_spawn";}
        public void use(edict_t self, edict_t other, edict_t activator) {
            self.solid = Defines.SOLID_BSP;
            self.svflags &= ~Defines.SVF_NOCLIENT;
            self.use = null;
            GameUtil.KillBox(self);
            GameBase.gi.linkentity(self);
        }
    };

    /*
     * QUAKED misc_explobox (0 .5 .8) (-16 -16 0) (16 16 40) Large exploding
     * box. You can override its mass (100), health (80), and dmg (150).
     */

    private static EntTouchAdapter barrel_touch = new EntTouchAdapter() {
        public String getID() { return "barrel_touch";}
        public void touch(edict_t self, edict_t other, cplane_t plane,
                csurface_t surf) {
            float ratio;
            float[] v = { 0, 0, 0 };

            if ((null == other.groundentity) || (other.groundentity == self))
                return;

            ratio = (float) other.mass / (float) self.mass;
            Math3D.VectorSubtract(self.s.origin, other.s.origin, v);
            M.M_walkmove(self, Math3D.vectoyaw(v), 20 * ratio
                    * Defines.FRAMETIME);
        }
    };

    private static EntThinkAdapter barrel_explode = new EntThinkAdapter() {
        public String getID() { return "barrel_explode";}
        public boolean think(edict_t self) {

            float[] org = { 0, 0, 0 };
            float spd;
            float[] save = { 0, 0, 0 };

            GameCombat.T_RadiusDamage(self, self.activator, self.dmg, null,
                    self.dmg + 40, Defines.MOD_BARREL);

            Math3D.VectorCopy(self.s.origin, save);
            Math3D.VectorMA(self.absmin, 0.5f, self.size, self.s.origin);

            // a few big chunks
            spd = 1.5f * (float) self.dmg / 200.0f;
            org[0] = self.s.origin[0] + Lib.crandom() * self.size[0];
            org[1] = self.s.origin[1] + Lib.crandom() * self.size[1];
            org[2] = self.s.origin[2] + Lib.crandom() * self.size[2];
            ThrowDebris(self, "models/objects/debris1/tris.md2", spd,
                    org);
            org[0] = self.s.origin[0] + Lib.crandom() * self.size[0];
            org[1] = self.s.origin[1] + Lib.crandom() * self.size[1];
            org[2] = self.s.origin[2] + Lib.crandom() * self.size[2];
            ThrowDebris(self, "models/objects/debris1/tris.md2", spd,
                    org);

            // bottom corners
            spd = 1.75f * (float) self.dmg / 200.0f;
            Math3D.VectorCopy(self.absmin, org);
            ThrowDebris(self, "models/objects/debris3/tris.md2", spd,
                    org);
            Math3D.VectorCopy(self.absmin, org);
            org[0] += self.size[0];
            ThrowDebris(self, "models/objects/debris3/tris.md2", spd,
                    org);
            Math3D.VectorCopy(self.absmin, org);
            org[1] += self.size[1];
            ThrowDebris(self, "models/objects/debris3/tris.md2", spd,
                    org);
            Math3D.VectorCopy(self.absmin, org);
            org[0] += self.size[0];
            org[1] += self.size[1];
            ThrowDebris(self, "models/objects/debris3/tris.md2", spd,
                    org);

            // a bunch of little chunks
            spd = 2 * self.dmg / 200;
            org[0] = self.s.origin[0] + Lib.crandom() * self.size[0];
            org[1] = self.s.origin[1] + Lib.crandom() * self.size[1];
            org[2] = self.s.origin[2] + Lib.crandom() * self.size[2];
            ThrowDebris(self, "models/objects/debris2/tris.md2", spd,
                    org);
            org[0] = self.s.origin[0] + Lib.crandom() * self.size[0];
            org[1] = self.s.origin[1] + Lib.crandom() * self.size[1];
            org[2] = self.s.origin[2] + Lib.crandom() * self.size[2];
            ThrowDebris(self, "models/objects/debris2/tris.md2", spd,
                    org);
            org[0] = self.s.origin[0] + Lib.crandom() * self.size[0];
            org[1] = self.s.origin[1] + Lib.crandom() * self.size[1];
            org[2] = self.s.origin[2] + Lib.crandom() * self.size[2];
            ThrowDebris(self, "models/objects/debris2/tris.md2", spd,
                    org);
            org[0] = self.s.origin[0] + Lib.crandom() * self.size[0];
            org[1] = self.s.origin[1] + Lib.crandom() * self.size[1];
            org[2] = self.s.origin[2] + Lib.crandom() * self.size[2];
            ThrowDebris(self, "models/objects/debris2/tris.md2", spd,
                    org);
            org[0] = self.s.origin[0] + Lib.crandom() * self.size[0];
            org[1] = self.s.origin[1] + Lib.crandom() * self.size[1];
            org[2] = self.s.origin[2] + Lib.crandom() * self.size[2];
            ThrowDebris(self, "models/objects/debris2/tris.md2", spd,
                    org);
            org[0] = self.s.origin[0] + Lib.crandom() * self.size[0];
            org[1] = self.s.origin[1] + Lib.crandom() * self.size[1];
            org[2] = self.s.origin[2] + Lib.crandom() * self.size[2];
            ThrowDebris(self, "models/objects/debris2/tris.md2", spd,
                    org);
            org[0] = self.s.origin[0] + Lib.crandom() * self.size[0];
            org[1] = self.s.origin[1] + Lib.crandom() * self.size[1];
            org[2] = self.s.origin[2] + Lib.crandom() * self.size[2];
            ThrowDebris(self, "models/objects/debris2/tris.md2", spd,
                    org);
            org[0] = self.s.origin[0] + Lib.crandom() * self.size[0];
            org[1] = self.s.origin[1] + Lib.crandom() * self.size[1];
            org[2] = self.s.origin[2] + Lib.crandom() * self.size[2];
            ThrowDebris(self, "models/objects/debris2/tris.md2", spd,
                    org);

            Math3D.VectorCopy(save, self.s.origin);
            if (self.groundentity != null)
                BecomeExplosion2(self);
            else
                BecomeExplosion1(self);

            return true;
        }
    };

    private static EntDieAdapter barrel_delay = new EntDieAdapter() {
        public String getID() { return "barrel_delay";}
        public void die(edict_t self, edict_t inflictor, edict_t attacker,
                int damage, float[] point) {

            self.takedamage = Defines.DAMAGE_NO;
            self.nextthink = GameBase.level.time + 2 * Defines.FRAMETIME;
            self.think = barrel_explode;
            self.activator = attacker;
        }
    };

    //
    // miscellaneous specialty items
    //

    /*
     * QUAKED misc_blackhole (1 .5 0) (-8 -8 -8) (8 8 8)
     */

    private static EntUseAdapter misc_blackhole_use = new EntUseAdapter() {
        public String getID() { return "misc_blavkhole_use";}
        public void use(edict_t ent, edict_t other, edict_t activator) {
            /*
             * gi.WriteByte (svc_temp_entity); gi.WriteByte (TE_BOSSTPORT);
             * gi.WritePosition (ent.s.origin); gi.multicast (ent.s.origin,
             * MULTICAST_PVS);
             */
            GameUtil.G_FreeEdict(ent);
        }
    };

    private static EntThinkAdapter misc_blackhole_think = new EntThinkAdapter() {
        public String getID() { return "misc_blackhole_think";}
        public boolean think(edict_t self) {

            if (++self.s.frame < 19)
                self.nextthink = GameBase.level.time + Defines.FRAMETIME;
            else {
                self.s.frame = 0;
                self.nextthink = GameBase.level.time + Defines.FRAMETIME;
            }
            return true;
        }
    };

    /*
     * QUAKED misc_eastertank (1 .5 0) (-32 -32 -16) (32 32 32)
     */

    private static EntThinkAdapter misc_eastertank_think = new EntThinkAdapter() {
        public String getID() { return "misc_eastertank_think";}
        public boolean think(edict_t self) {
            if (++self.s.frame < 293)
                self.nextthink = GameBase.level.time + Defines.FRAMETIME;
            else {
                self.s.frame = 254;
                self.nextthink = GameBase.level.time + Defines.FRAMETIME;
            }
            return true;
        }
    };

    /*
     * QUAKED misc_easterchick (1 .5 0) (-32 -32 0) (32 32 32)
     */

    private static EntThinkAdapter misc_easterchick_think = new EntThinkAdapter() {
        public String getID() { return "misc_easterchick_think";}
        public boolean think(edict_t self) {
            if (++self.s.frame < 247)
                self.nextthink = GameBase.level.time + Defines.FRAMETIME;
            else {
                self.s.frame = 208;
                self.nextthink = GameBase.level.time + Defines.FRAMETIME;
            }
            return true;
        }
    };

    /*
     * QUAKED misc_easterchick2 (1 .5 0) (-32 -32 0) (32 32 32)
     */
    private static EntThinkAdapter misc_easterchick2_think = new EntThinkAdapter() {
        public String getID() { return "misc_easterchick2_think";}
        public boolean think(edict_t self) {
            if (++self.s.frame < 287)
                self.nextthink = GameBase.level.time + Defines.FRAMETIME;
            else {
                self.s.frame = 248;
                self.nextthink = GameBase.level.time + Defines.FRAMETIME;
            }
            return true;
        }
    };

    /*
     * QUAKED monster_commander_body (1 .5 0) (-32 -32 0) (32 32 48) Not really
     * a monster, this is the Tank Commander's decapitated body. There should be
     * a item_commander_head that has this as it's target.
     */

    private static EntThinkAdapter commander_body_think = new EntThinkAdapter() {
        public String getID() { return "commander_body_think";}
        public boolean think(edict_t self) {
            if (++self.s.frame < 24)
                self.nextthink = GameBase.level.time + Defines.FRAMETIME;
            else
                self.nextthink = 0;

            if (self.s.frame == 22)
                GameBase.gi.sound(self, Defines.CHAN_BODY, GameBase.gi
                        .soundindex("tank/thud.wav"), 1, Defines.ATTN_NORM, 0);
            return true;
        }
    };

    private static EntUseAdapter commander_body_use = new EntUseAdapter() {
        public String getID() { return "commander_body_use";}
        public void use(edict_t self, edict_t other, edict_t activator) {
            self.think = commander_body_think;
            self.nextthink = GameBase.level.time + Defines.FRAMETIME;
            GameBase.gi.sound(self, Defines.CHAN_BODY, GameBase.gi
                    .soundindex("tank/pain.wav"), 1, Defines.ATTN_NORM, 0);
        }
    };

    private static EntThinkAdapter commander_body_drop = new EntThinkAdapter() {
        public String getID() { return "commander_body_group";}
        public boolean think(edict_t self) {
            self.movetype = Defines.MOVETYPE_TOSS;
            self.s.origin[2] += 2;
            return true;
        }
    };

    /*
     * QUAKED misc_banner (1 .5 0) (-4 -4 -4) (4 4 4) The origin is the bottom
     * of the banner. The banner is 128 tall.
     */
    private static EntThinkAdapter misc_banner_think = new EntThinkAdapter() {
        public String getID() { return "misc_banner_think";}
        public boolean think(edict_t ent) {
            ent.s.frame = (ent.s.frame + 1) % 16;
            ent.nextthink = GameBase.level.time + Defines.FRAMETIME;
            return true;
        }
    };

    /*
     * QUAKED misc_deadsoldier (1 .5 0) (-16 -16 0) (16 16 16) ON_BACK
     * ON_STOMACH BACK_DECAP FETAL_POS SIT_DECAP IMPALED This is the dead player
     * model. Comes in 6 exciting different poses!
     */
    private static EntDieAdapter misc_deadsoldier_die = new EntDieAdapter() {
        public String getID() { return "misc_deadsoldier_die";}
        public void die(edict_t self, edict_t inflictor, edict_t attacker,
                int damage, float[] point) {
            int n;

            if (self.health > -80)
                return;

            GameBase.gi.sound(self, Defines.CHAN_BODY, GameBase.gi
                    .soundindex("misc/udeath.wav"), 1, Defines.ATTN_NORM, 0);
            for (n = 0; n < 4; n++)
                ThrowGib(self, "models/objects/gibs/sm_meat/tris.md2",
                        damage, Defines.GIB_ORGANIC);
            ThrowHead(self, "models/objects/gibs/head2/tris.md2",
                    damage, Defines.GIB_ORGANIC);
        }
    };

    /*
     * QUAKED misc_viper (1 .5 0) (-16 -16 0) (16 16 32) This is the Viper for
     * the flyby bombing. It is trigger_spawned, so you must have something use
     * it for it to show up. There must be a path for it to follow once it is
     * activated.
     * 
     * "speed" How fast the Viper should fly
     */

    private static EntUseAdapter misc_viper_use = new EntUseAdapter() {
        public String getID() { return "misc_viper_use";}
        public void use(edict_t self, edict_t other, edict_t activator) {
            self.svflags &= ~Defines.SVF_NOCLIENT;
            self.use = GameFunc.train_use;
            GameFunc.train_use.use(self, other, activator);
        }
    };

    /*
     * QUAKED misc_viper_bomb (1 0 0) (-8 -8 -8) (8 8 8) "dmg" how much boom
     * should the bomb make?
     */
    private static EntTouchAdapter misc_viper_bomb_touch = new EntTouchAdapter() {
        public String getID() { return "misc_viper_bomb_touch";}
        public void touch(edict_t self, edict_t other, cplane_t plane,
                csurface_t surf) {
            GameUtil.G_UseTargets(self, self.activator);

            self.s.origin[2] = self.absmin[2] + 1;
            GameCombat.T_RadiusDamage(self, self, self.dmg, null, self.dmg + 40,
                    Defines.MOD_BOMB);
            BecomeExplosion2(self);
        }
    };

    private static EntThinkAdapter misc_viper_bomb_prethink = new EntThinkAdapter() {
        public String getID() { return "misc_viper_bomb_prethink";}
        public boolean think(edict_t self) {

            float[] v = { 0, 0, 0 };
            float diff;

            self.groundentity = null;

            diff = self.timestamp - GameBase.level.time;
            if (diff < -1.0)
                diff = -1.0f;

            Math3D.VectorScale(self.moveinfo.dir, 1.0f + diff, v);
            v[2] = diff;

            diff = self.s.angles[2];
            Math3D.vectoangles(v, self.s.angles);
            self.s.angles[2] = diff + 10;

            return true;
        }
    };

    private static EntUseAdapter misc_viper_bomb_use = new EntUseAdapter() {
        public String getID() { return "misc_viper_bomb_use";}
        public void use(edict_t self, edict_t other, edict_t activator) {
            edict_t viper = null;

            self.solid = Defines.SOLID_BBOX;
            self.svflags &= ~Defines.SVF_NOCLIENT;
            self.s.effects |= Defines.EF_ROCKET;
            self.use = null;
            self.movetype = Defines.MOVETYPE_TOSS;
            self.prethink = misc_viper_bomb_prethink;
            self.touch = misc_viper_bomb_touch;
            self.activator = activator;

            EdictIterator es = null;

            es = GameBase.G_Find(es, GameBase.findByClass, "misc_viper");
            if (es != null)
                viper = es.o;

            Math3D.VectorScale(viper.moveinfo.dir, viper.moveinfo.speed,
                    self.velocity);

            self.timestamp = GameBase.level.time;
            Math3D.VectorCopy(viper.moveinfo.dir, self.moveinfo.dir);
        }
    };

    /*
     * QUAKED misc_strogg_ship (1 .5 0) (-16 -16 0) (16 16 32) This is a Storgg
     * ship for the flybys. It is trigger_spawned, so you must have something
     * use it for it to show up. There must be a path for it to follow once it
     * is activated.
     * 
     * "speed" How fast it should fly
     */

    private static EntUseAdapter misc_strogg_ship_use = new EntUseAdapter() {
        public String getID() { return "misc_strogg_ship_use";}
        public void use(edict_t self, edict_t other, edict_t activator) {
            self.svflags &= ~Defines.SVF_NOCLIENT;
            self.use = GameFunc.train_use;
            GameFunc.train_use.use(self, other, activator);
        }
    };

    /*
     * QUAKED misc_satellite_dish (1 .5 0) (-64 -64 0) (64 64 128)
     */
    private static EntThinkAdapter misc_satellite_dish_think = new EntThinkAdapter() {
        public String getID() { return "misc_satellite_dish_think";}
        public boolean think(edict_t self) {
            self.s.frame++;
            if (self.s.frame < 38)
                self.nextthink = GameBase.level.time + Defines.FRAMETIME;
            return true;
        }
    };

    private static EntUseAdapter misc_satellite_dish_use = new EntUseAdapter() {
        public String getID() { return "misc_satellite_dish_use";}
        public void use(edict_t self, edict_t other, edict_t activator) {
            self.s.frame = 0;
            self.think = misc_satellite_dish_think;
            self.nextthink = GameBase.level.time + Defines.FRAMETIME;
        }
    };

    /*
     * QUAKED target_string (0 0 1) (-8 -8 -8) (8 8 8)
     */

    private static EntUseAdapter target_string_use = new EntUseAdapter() {
        public String getID() { return "target_string_use";}
        public void use(edict_t self, edict_t other, edict_t activator) {
            edict_t e;
            int n, l;
            char c;

            l = self.message.length();
            for (e = self.teammaster; e != null; e = e.teamchain) {
                if (e.count == 0)
                    continue;
                n = e.count - 1;
                if (n >= l) {
                    e.s.frame = 12;
                    continue;
                }

                c = self.message.charAt(n);
                if (c >= '0' && c <= '9')
                    e.s.frame = c - '0';
                else if (c == '-')
                    e.s.frame = 10;
                else if (c == ':')
                    e.s.frame = 11;
                else
                    e.s.frame = 12;
            }
        }
    };

    /*
     * QUAKED func_clock (0 0 1) (-8 -8 -8) (8 8 8) TIMER_UP TIMER_DOWN
     * START_OFF MULTI_USE target a target_string with this
     * 
     * The default is to be a time of day clock
     * 
     * TIMER_UP and TIMER_DOWN run for "count" seconds and the fire "pathtarget"
     * If START_OFF, this entity must be used before it starts
     * 
     * "style" 0 "xx" 1 "xx:xx" 2 "xx:xx:xx"
     */

    public static final int CLOCK_MESSAGE_SIZE = 16;

    private static EntThinkAdapter func_clock_think = new EntThinkAdapter() {
        public String getID() { return "func_clock_think";}
        public boolean think(edict_t self) {
            if (null == self.enemy) {

                EdictIterator es = null;

                es = GameBase.G_Find(es, GameBase.findByTarget, self.target);
                if (es != null)
                    self.enemy = es.o;
                if (self.enemy == null)
                    return true;
            }

            if ((self.spawnflags & 1) != 0) {
                func_clock_format_countdown(self);
                self.health++;
            } else if ((self.spawnflags & 2) != 0) {
                func_clock_format_countdown(self);
                self.health--;
            } else {
                Calendar c = Calendar.getInstance();
                self.message = "" + c.get(Calendar.HOUR_OF_DAY) + ":"
                        + c.get(Calendar.MINUTE) + ":" + c.get(Calendar.SECOND);

                /*
                 * struct tm * ltime; time_t gmtime;
                 * 
                 * time(& gmtime); ltime = localtime(& gmtime);
                 * Com_sprintf(self.message, CLOCK_MESSAGE_SIZE, "%2i:%2i:%2i",
                 * ltime.tm_hour, ltime.tm_min, ltime.tm_sec); if
                 * (self.message[3] == ' ') self.message[3] = '0'; if
                 * (self.message[6] == ' ') self.message[6] = '0';
                 */
            }

            self.enemy.message = self.message;
            self.enemy.use.use(self.enemy, self, self);

            if (((self.spawnflags & 1) != 0 && (self.health > self.wait))
                    || ((self.spawnflags & 2) != 0 && (self.health < self.wait))) {
                if (self.pathtarget != null) {
                    String savetarget;
                    String savemessage;

                    savetarget = self.target;
                    savemessage = self.message;
                    self.target = self.pathtarget;
                    self.message = null;
                    GameUtil.G_UseTargets(self, self.activator);
                    self.target = savetarget;
                    self.message = savemessage;
                }

                if (0 == (self.spawnflags & 8))
                    return true;

                func_clock_reset(self);

                if ((self.spawnflags & 4) != 0)
                    return true;
            }

            self.nextthink = GameBase.level.time + 1;
            return true;

        }
    };

    private static EntUseAdapter func_clock_use = new EntUseAdapter() {
        public String getID() { return "func_clock_use";}
        public void use(edict_t self, edict_t other, edict_t activator) {
            if (0 == (self.spawnflags & 8))
                self.use = null;
            if (self.activator != null)
                return;
            self.activator = activator;
            self.think.think(self);
        }
    };

    //=================================================================================

    private static EntTouchAdapter teleporter_touch = new EntTouchAdapter() {
        public String getID() { return "teleporter_touch";}
        public void touch(edict_t self, edict_t other, cplane_t plane,
                csurface_t surf) {
            edict_t dest;
            int i;

            gclient_t client = (gclient_t) other.client;
            if (client == null)
                return;

            EdictIterator es = null;
            dest = GameBase.G_Find(null, GameBase.findByTarget, self.target).o;

            if (dest == null) {
                GameBase.gi.dprintf("Couldn't find destination\n");
                return;
            }

            // unlink to make sure it can't possibly interfere with KillBox
            GameBase.gi.unlinkentity(other);

            Math3D.VectorCopy(dest.s.origin, other.s.origin);
            Math3D.VectorCopy(dest.s.origin, other.s.old_origin);
            other.s.origin[2] += 10;

            // clear the velocity and hold them in place briefly
            Math3D.VectorClear(other.velocity);
            client.getPlayerState().pmove.pm_time = 160 >> 3; // hold time
            client.getPlayerState().pmove.pm_flags |= Defines.PMF_TIME_TELEPORT;

            // draw the teleport splash at source and on the player
            self.owner.s.event = Defines.EV_PLAYER_TELEPORT;
            other.s.event = Defines.EV_PLAYER_TELEPORT;

            // set angles
            for (i = 0; i < 3; i++) {
                client.getPlayerState().pmove.delta_angles[i] = (short) Math3D
                        .ANGLE2SHORT(dest.s.angles[i]
                                - client.resp.cmd_angles[i]);
            }

            Math3D.VectorClear(other.s.angles);
            Math3D.VectorClear(client.getPlayerState().viewangles);
            Math3D.VectorClear(client.v_angle);

            // kill anything at the destination
            GameUtil.KillBox(other);

            GameBase.gi.linkentity(other);
        }
    };

    /*
     * QUAKED misc_teleporter_dest (1 0 0) (-32 -32 -24) (32 32 -16) Point
     * teleporters at these.
     */

    static EntThinkAdapter SP_misc_teleporter_dest = new EntThinkAdapter() {
        public String getID() { return "SP_misc_teleporter_dest";}
        public boolean think(edict_t ent) {
            GameBase.gi.setmodel(ent, "models/objects/dmspot/tris.md2");
            ent.s.skinnum = 0;
            ent.solid = Defines.SOLID_BBOX;
            //	ent.s.effects |= EF_FLIES;
            Math3D.VectorSet(ent.mins, -32, -32, -24);
            Math3D.VectorSet(ent.maxs, 32, 32, -16);
            GameBase.gi.linkentity(ent);
            return true;
        }
    };

    private static EntThinkAdapter gib_think = new EntThinkAdapter() {
        public String getID() { return "gib_think";}
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

    private static EntTouchAdapter gib_touch = new EntTouchAdapter() {
        public String getID() { return "gib_touch";}
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

    private static EntDieAdapter gib_die = new EntDieAdapter() {
        public String getID() { return "gib_die";}
        public void die(edict_t self, edict_t inflictor, edict_t attacker,
                int damage, float[] point) {
            GameUtil.G_FreeEdict(self);
        }
    };

    /**
     * Debris
     */
    private static EntDieAdapter debris_die = new EntDieAdapter() {
        public String getID() { return "debris_die";}
        public void die(edict_t self, edict_t inflictor, edict_t attacker,
                int damage, float[] point) {
            GameUtil.G_FreeEdict(self);
        }
    };
}