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

import jake2.game.adapters.EntDieAdapter;
import jake2.game.adapters.EntThinkAdapter;
import jake2.game.adapters.EntTouchAdapter;
import jake2.game.adapters.EntUseAdapter;
import jake2.qcommon.Defines;
import jake2.qcommon.Globals;
import jake2.qcommon.cplane_t;
import jake2.qcommon.csurface_t;
import jake2.qcommon.network.MulticastTypes;
import jake2.qcommon.network.messages.server.PointTEMessage;
import jake2.qcommon.util.Lib;
import jake2.qcommon.util.Math3D;

import static jake2.qcommon.Defines.EF_FLIES;

public class GameMisc {


    static void SP_misc_eastertank(GameEntity ent, GameExportsImpl gameExports) {
        ent.movetype = GameDefines.MOVETYPE_NONE;
        ent.solid = Defines.SOLID_BBOX;
        Math3D.VectorSet(ent.mins, -32, -32, -16);
        Math3D.VectorSet(ent.maxs, 32, 32, 32);
        ent.s.modelindex = gameExports.gameImports
                .modelindex("models/monsters/tank/tris.md2");
        ent.s.frame = 254;
        ent.think.action = misc_eastertank_think;
        ent.think.nextTime = gameExports.level.time + 2 * Defines.FRAMETIME;
        gameExports.gameImports.linkentity(ent);
    }

    static void SP_misc_easterchick(GameEntity ent, GameExportsImpl gameExports) {
        ent.movetype = GameDefines.MOVETYPE_NONE;
        ent.solid = Defines.SOLID_BBOX;
        Math3D.VectorSet(ent.mins, -32, -32, 0);
        Math3D.VectorSet(ent.maxs, 32, 32, 32);
        ent.s.modelindex = gameExports.gameImports
                .modelindex("models/monsters/bitch/tris.md2");
        ent.s.frame = 208;
        ent.think.action = misc_easterchick_think;
        ent.think.nextTime = gameExports.level.time + 2 * Defines.FRAMETIME;
        gameExports.gameImports.linkentity(ent);
    }

    static void SP_misc_easterchick2(GameEntity ent, GameExportsImpl gameExports) {
        ent.movetype = GameDefines.MOVETYPE_NONE;
        ent.solid = Defines.SOLID_BBOX;
        Math3D.VectorSet(ent.mins, -32, -32, 0);
        Math3D.VectorSet(ent.maxs, 32, 32, 32);
        ent.s.modelindex = gameExports.gameImports
                .modelindex("models/monsters/bitch/tris.md2");
        ent.s.frame = 248;
        ent.think.action = misc_easterchick2_think;
        ent.think.nextTime = gameExports.level.time + 2 * Defines.FRAMETIME;
        gameExports.gameImports.linkentity(ent);
    }

    static void SP_monster_commander_body(GameEntity self, GameExportsImpl gameExports) {
        self.movetype = GameDefines.MOVETYPE_NONE;
        self.solid = Defines.SOLID_BBOX;
        self.model = "models/monsters/commandr/tris.md2";
        self.s.modelindex = gameExports.gameImports.modelindex(self.model);
        Math3D.VectorSet(self.mins, -32, -32, 0);
        Math3D.VectorSet(self.maxs, 32, 32, 48);
        self.use = commander_body_use;
        self.takedamage = Defines.DAMAGE_YES;
        self.flags = GameDefines.FL_GODMODE;
        self.s.renderfx |= Defines.RF_FRAMELERP;
        gameExports.gameImports.linkentity(self);

        gameExports.gameImports.soundindex("tank/thud.wav");
        gameExports.gameImports.soundindex("tank/pain.wav");

        self.think.action = commander_body_drop;
        self.think.nextTime = gameExports.level.time + 5 * Defines.FRAMETIME;
    }

    private static void VelocityForDamage(int damage, float[] v) {
        v[0] = 100.0f * Lib.crandom();
        v[1] = 100.0f * Lib.crandom();
        v[2] = 200.0f + 100.0f * Lib.random();
    
        if (damage < 50)
            Math3D.VectorScale(v, 0.7f, v);
        else
            Math3D.VectorScale(v, 1.2f, v);
    }

    public static void BecomeExplosion1(GameEntity self, GameExportsImpl gameExports) {
        gameExports.gameImports.multicastMessage(self.s.origin, new PointTEMessage(Defines.TE_EXPLOSION1, self.s.origin), MulticastTypes.MULTICAST_PVS);
        gameExports.freeEntity(self);
    }

    public static void BecomeExplosion2(GameEntity self, GameExportsImpl gameExports) {
        gameExports.gameImports.multicastMessage(self.s.origin, new PointTEMessage(Defines.TE_EXPLOSION2, self.s.origin), MulticastTypes.MULTICAST_PVS);
        gameExports.freeEntity(self);
    }

    public static void ThrowGib(GameEntity self, String gibname, int damage, int type, GameExportsImpl gameExports) {

        float[] vd = { 0, 0, 0 };
        float[] origin = { 0, 0, 0 };
        float[] size = { 0, 0, 0 };

        GameEntity gib = gameExports.G_Spawn();
    
        Math3D.VectorScale(self.size, 0.5f, size);
        Math3D.VectorAdd(self.absmin, size, origin);
        gib.s.origin[0] = origin[0] + Lib.crandom() * size[0];
        gib.s.origin[1] = origin[1] + Lib.crandom() * size[1];
        gib.s.origin[2] = origin[2] + Lib.crandom() * size[2];
    
        gameExports.gameImports.setmodel(gib, gibname);
        gib.solid = Defines.SOLID_NOT;
        gib.s.effects |= Defines.EF_GIB;
        gib.flags |= GameDefines.FL_NO_KNOCKBACK;
        gib.takedamage = Defines.DAMAGE_YES;
        gib.die = gib_die;

        float vscale;
        if (type == GameDefines.GIB_ORGANIC) {
            gib.movetype = GameDefines.MOVETYPE_TOSS;
            gib.touch = gib_touch;
            vscale = 0.5f;
        } else {
            gib.movetype = GameDefines.MOVETYPE_BOUNCE;
            vscale = 1.0f;
        }
    
        VelocityForDamage(damage, vd);
        Math3D.VectorMA(self.velocity, vscale, vd, gib.velocity);
        ClipGibVelocity(gib);
        gib.avelocity[0] = Lib.random() * 600;
        gib.avelocity[1] = Lib.random() * 600;
        gib.avelocity[2] = Lib.random() * 600;
    
        gib.think.action = GameUtil.G_FreeEdictA;
        gib.think.nextTime = gameExports.level.time + 10 + Lib.random() * 10;
    
        gameExports.gameImports.linkentity(gib);
    }

    public static void ThrowHead(GameEntity self, String gibname, int damage,
                                 int type, GameExportsImpl gameExports) {
        float vd[] = { 0, 0, 0 };
    
        float vscale;
    
        self.s.skinnum = 0;
        self.s.frame = 0;
        Math3D.VectorClear(self.mins);
        Math3D.VectorClear(self.maxs);
    
        self.s.modelindex2 = 0;
        gameExports.gameImports.setmodel(self, gibname);
        self.solid = Defines.SOLID_NOT;
        self.s.effects |= Defines.EF_GIB;
        self.s.effects &= ~EF_FLIES;
        self.s.sound = 0;
        self.flags |= GameDefines.FL_NO_KNOCKBACK;
        self.svflags &= ~Defines.SVF_MONSTER;
        self.takedamage = Defines.DAMAGE_YES;
        self.die = gib_die;
    
        if (type == GameDefines.GIB_ORGANIC) {
            self.movetype = GameDefines.MOVETYPE_TOSS;
            self.touch = gib_touch;
            vscale = 0.5f;
        } else {
            self.movetype = GameDefines.MOVETYPE_BOUNCE;
            vscale = 1.0f;
        }
    
        VelocityForDamage(damage, vd);
        Math3D.VectorMA(self.velocity, vscale, vd, self.velocity);
        ClipGibVelocity(self);
    
        self.avelocity[Defines.YAW] = Lib.crandom() * 600f;
    
        self.think.action = GameUtil.G_FreeEdictA;
        self.think.nextTime = gameExports.level.time + 10 + Lib.random() * 10;
    
        gameExports.gameImports.linkentity(self);
    }

    static void ThrowClientHead(GameEntity self, int damage, GameExportsImpl gameExports) {
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
        gameExports.gameImports.setmodel(self, gibname);
        Math3D.VectorSet(self.mins, -16, -16, 0);
        Math3D.VectorSet(self.maxs, 16, 16, 16);
    
        self.takedamage = Defines.DAMAGE_NO;
        self.solid = Defines.SOLID_NOT;
        self.s.effects = Defines.EF_GIB;
        self.s.sound = 0;
        self.flags |= GameDefines.FL_NO_KNOCKBACK;
    
        self.movetype = GameDefines.MOVETYPE_BOUNCE;
        VelocityForDamage(damage, vd);
        Math3D.VectorAdd(self.velocity, vd, self.velocity);

        gclient_t client = self.getClient();
        if (client != null)
        // bodies in the queue don't have a client anymore
        {
            client.anim_priority = Defines.ANIM_DEATH;
            client.anim_end = self.s.frame;
        } else {
            self.think.action = null;
            self.think.nextTime = 0;
        }
    
        gameExports.gameImports.linkentity(self);
    }

    public static void ThrowDebris(GameEntity self, String modelname, float speed,
                                   float[] origin, GameExportsImpl gameExports) {
        float[] v = { 0, 0, 0 };

        GameEntity chunk = gameExports.G_Spawn();
        Math3D.VectorCopy(origin, chunk.s.origin);
        gameExports.gameImports.setmodel(chunk, modelname);
        v[0] = 100 * Lib.crandom();
        v[1] = 100 * Lib.crandom();
        v[2] = 100 + 100 * Lib.crandom();
        Math3D.VectorMA(self.velocity, speed, v, chunk.velocity);
        chunk.movetype = GameDefines.MOVETYPE_BOUNCE;
        chunk.solid = Defines.SOLID_NOT;
        chunk.avelocity[0] = Lib.random() * 600;
        chunk.avelocity[1] = Lib.random() * 600;
        chunk.avelocity[2] = Lib.random() * 600;
        chunk.think.action = GameUtil.G_FreeEdictA;
        chunk.think.nextTime = gameExports.level.time + 5 + Lib.random() * 5;
        chunk.s.frame = 0;
        chunk.flags = 0;
        chunk.classname = "debris";
        chunk.takedamage = Defines.DAMAGE_YES;
        chunk.die = debris_die;
        gameExports.gameImports.linkentity(chunk);
    }

    private static void ClipGibVelocity(GameEntity ent) {
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

    //
    // miscellaneous specialty items
    //


    /*
     * QUAKED misc_eastertank (1 .5 0) (-32 -32 -16) (32 32 32)
     */

    private static EntThinkAdapter misc_eastertank_think = new EntThinkAdapter() {
        public String getID() { return "misc_eastertank_think";}
        public boolean think(GameEntity self, GameExportsImpl gameExports) {
            if (++self.s.frame < 293)
                self.think.nextTime = gameExports.level.time + Defines.FRAMETIME;
            else {
                self.s.frame = 254;
                self.think.nextTime = gameExports.level.time + Defines.FRAMETIME;
            }
            return true;
        }
    };

    /*
     * QUAKED misc_easterchick (1 .5 0) (-32 -32 0) (32 32 32)
     */

    private static EntThinkAdapter misc_easterchick_think = new EntThinkAdapter() {
        public String getID() { return "misc_easterchick_think";}
        public boolean think(GameEntity self, GameExportsImpl gameExports) {
            if (++self.s.frame < 247)
                self.think.nextTime = gameExports.level.time + Defines.FRAMETIME;
            else {
                self.s.frame = 208;
                self.think.nextTime = gameExports.level.time + Defines.FRAMETIME;
            }
            return true;
        }
    };

    /*
     * QUAKED misc_easterchick2 (1 .5 0) (-32 -32 0) (32 32 32)
     */
    private static EntThinkAdapter misc_easterchick2_think = new EntThinkAdapter() {
        public String getID() { return "misc_easterchick2_think";}
        public boolean think(GameEntity self, GameExportsImpl gameExports) {
            if (++self.s.frame < 287)
                self.think.nextTime = gameExports.level.time + Defines.FRAMETIME;
            else {
                self.s.frame = 248;
                self.think.nextTime = gameExports.level.time + Defines.FRAMETIME;
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
        public boolean think(GameEntity self, GameExportsImpl gameExports) {
            if (++self.s.frame < 24)
                self.think.nextTime = gameExports.level.time + Defines.FRAMETIME;
            else
                self.think.nextTime = 0;

            if (self.s.frame == 22)
                gameExports.gameImports.sound(self, Defines.CHAN_BODY, gameExports.gameImports
                        .soundindex("tank/thud.wav"), 1, Defines.ATTN_NORM, 0);
            return true;
        }
    };

    private static EntUseAdapter commander_body_use = new EntUseAdapter() {
        public String getID() { return "commander_body_use";}
        public void use(GameEntity self, GameEntity other, GameEntity activator, GameExportsImpl gameExports) {
            self.think.action = commander_body_think;
            self.think.nextTime = gameExports.level.time + Defines.FRAMETIME;
            gameExports.gameImports.sound(self, Defines.CHAN_BODY, gameExports.gameImports
                    .soundindex("tank/pain.wav"), 1, Defines.ATTN_NORM, 0);
        }
    };

    private static EntThinkAdapter commander_body_drop = new EntThinkAdapter() {
        public String getID() { return "commander_body_group";}
        public boolean think(GameEntity self, GameExportsImpl gameExports) {
            self.movetype = GameDefines.MOVETYPE_TOSS;
            self.s.origin[2] += 2;
            return true;
        }
    };


    private static EntThinkAdapter gib_think = new EntThinkAdapter() {
        public String getID() { return "gib_think";}
        public boolean think(GameEntity self, GameExportsImpl gameExports) {
            self.s.frame++;
            self.think.nextTime = gameExports.level.time + Defines.FRAMETIME;
    
            if (self.s.frame == 10) {
                self.think.action = GameUtil.G_FreeEdictA;
                self.think.nextTime = gameExports.level.time + 8
                        + Globals.rnd.nextFloat() * 10;
            }
            return true;
        }
    };

    private static EntTouchAdapter gib_touch = new EntTouchAdapter() {
        public String getID() { return "gib_touch";}
        public void touch(GameEntity self, GameEntity other, cplane_t plane,
                          csurface_t surf, GameExportsImpl gameExports) {
            float[] normal_angles = { 0, 0, 0 }, right = { 0, 0, 0 };
    
            if (null == self.groundentity)
                return;
    
            self.touch = null;
    
            if (plane != null) {
                gameExports.gameImports.sound(self, Defines.CHAN_VOICE, gameExports.gameImports
                        .soundindex("misc/fhit3.wav"), 1, Defines.ATTN_NORM, 0);
    
                Math3D.vectoangles(plane.normal, normal_angles);
                Math3D.AngleVectors(normal_angles, null, right, null);
                Math3D.vectoangles(right, self.s.angles);
    
                if (self.s.modelindex == gameExports.gameImports.modelindex("models/objects/gibs/sm_meat/tris.md2")) {
                    self.s.frame++;
                    self.think.action = gib_think;
                    self.think.nextTime = gameExports.level.time + Defines.FRAMETIME;
                }
            }
        }
    };

    private static EntDieAdapter gib_die = new EntDieAdapter() {
        public String getID() { return "gib_die";}
        public void die(GameEntity self, GameEntity inflictor, GameEntity attacker,
                        int damage, float[] point, GameExportsImpl gameExports) {
        }
    };

    /**
     * Debris
     */
    private static EntDieAdapter debris_die = new EntDieAdapter() {
        public String getID() { return "debris_die";}
        public void die(GameEntity self, GameEntity inflictor, GameEntity attacker,
                        int damage, float[] point, GameExportsImpl gameExports) {
            gameExports.freeEntity(self);
        }
    };
}
