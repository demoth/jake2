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

// Created on 28.12.2003 by RST.
// $Id: GameTarget.java,v 1.8 2006-01-21 21:53:31 salomo Exp $
package jake2.game;

import jake2.game.adapters.EntThinkAdapter;
import jake2.game.adapters.EntUseAdapter;
import jake2.qcommon.Defines;
import jake2.qcommon.Globals;
import jake2.qcommon.edict_t;
import jake2.qcommon.network.MulticastTypes;
import jake2.qcommon.network.messages.server.PointTEMessage;
import jake2.qcommon.network.messages.server.SplashTEMessage;
import jake2.qcommon.trace_t;
import jake2.qcommon.util.Lib;
import jake2.qcommon.util.Math3D;

class GameTarget {


    static void SP_target_speaker(SubgameEntity ent, GameExportsImpl gameExports) {
        //char buffer[MAX_QPATH];
        String buffer;

        if (ent.st.noise == null) {
            gameExports.gameImports.dprintf("target_speaker with no noise set at "
                    + Lib.vtos(ent.s.origin) + "\n");
            return;
        }
        if (!ent.st.noise.contains(".wav"))
            buffer = "" + ent.st.noise + ".wav";
        else
            buffer = ent.st.noise;

        ent.noise_index = gameExports.gameImports.soundindex(buffer);

        if (ent.volume == 0)
            ent.volume = 1.0f;

        if (ent.attenuation == 0)
            ent.attenuation = 1.0f;
        else if (ent.attenuation == -1) // use -1 so 0 defaults to 1
            ent.attenuation = 0;

        // check for prestarted looping sound
        if ((ent.spawnflags & 1) != 0)
            ent.s.sound = ent.noise_index;

        ent.use = Use_Target_Speaker;

        // must link the entity so we get areas and clusters so
        // the server can determine who to send updates to
        gameExports.gameImports.linkentity(ent);
    }

    /**
     * QUAKED target_help (1 0 1) (-16 -16 -24) (16 16 24) help1 When fired, the
     * "message" key becomes the current personal computer string, and the
     * message light will be set on all clients status bars.
     */
    static void SP_target_help(SubgameEntity ent, GameExportsImpl gameExports) {
        if (gameExports.gameCvars.deathmatch.value != 0) { // auto-remove for deathmatch
            gameExports.freeEntity(ent);
            return;
        }

        if (ent.message == null) {
            gameExports.gameImports.dprintf(ent.classname + " with no message at "
                    + Lib.vtos(ent.s.origin) + "\n");
            gameExports.freeEntity(ent);
            return;
        }
        ent.use = Use_Target_Help;
    }

    static void SP_target_secret(SubgameEntity ent, GameExportsImpl gameExports) {
        if (gameExports.gameCvars.deathmatch.value != 0) { // auto-remove for deathmatch
            gameExports.freeEntity(ent);
            return;
        }

        ent.use = use_target_secret;
        if (ent.st.noise == null)
            ent.st.noise = "misc/secret.wav";
        ent.noise_index = gameExports.gameImports.soundindex(ent.st.noise);
        ent.svflags = Defines.SVF_NOCLIENT;
        gameExports.level.total_secrets++;
        // map bug hack
        if (0 == Lib.Q_stricmp(gameExports.level.mapname, "mine3")
                && ent.s.origin[0] == 280 && ent.s.origin[1] == -2048
                && ent.s.origin[2] == -624)
            ent.message = "You have found a secret area.";
    }

    static void SP_target_goal(SubgameEntity ent, GameExportsImpl gameExports) {
        if (gameExports.gameCvars.deathmatch.value != 0) { // auto-remove for deathmatch
            gameExports.freeEntity(ent);
            return;
        }

        ent.use = use_target_goal;
        if (ent.st.noise == null)
            ent.st.noise = "misc/secret.wav";
        ent.noise_index = gameExports.gameImports.soundindex(ent.st.noise);
        ent.svflags = Defines.SVF_NOCLIENT;
        gameExports.level.total_goals++;
    }

    static void SP_target_explosion(SubgameEntity ent) {
        ent.use = use_target_explosion;
        ent.svflags = Defines.SVF_NOCLIENT;
    }

    static void SP_target_changelevel(SubgameEntity ent, GameExportsImpl gameExports) {
        if (ent.map == null) {
            gameExports.gameImports.dprintf("target_changelevel with no map at "
                    + Lib.vtos(ent.s.origin) + "\n");
            gameExports.freeEntity(ent);
            return;
        }

        // ugly hack because *SOMEBODY* screwed up their map
        if ((Lib.Q_stricmp(gameExports.level.mapname, "fact1") == 0)
                && (Lib.Q_stricmp(ent.map, "fact3") == 0))
            ent.map = "fact3$secret1";

        ent.use = use_target_changelevel;
        ent.svflags = Defines.SVF_NOCLIENT;
    }

    /**
     * QUAKED target_splash (1 0 0) (-8 -8 -8) (8 8 8) Creates a particle splash
     * effect when used.
     *
     * Set "sounds" to one of the following: 1) sparks 2) blue water 3) brown
     * water 4) slime 5) lava 6) blood
     *
     * "count" how many pixels in the splash "dmg" if set, does a radius damage
     * at this location when it splashes useful for lava/sparks
     */
    static void SP_target_splash(SubgameEntity self) {
        self.use = use_target_splash;
        GameBase.G_SetMovedir(self.s.angles, self.movedir);

        if (0 == self.count)
            self.count = 32;

        self.svflags = Defines.SVF_NOCLIENT;
    }

    static void SP_target_spawner(SubgameEntity self) {
        self.use = use_target_spawner;
        self.svflags = Defines.SVF_NOCLIENT;
        if (self.speed != 0) {
            GameBase.G_SetMovedir(self.s.angles, self.movedir);
            Math3D.VectorScale(self.movedir, self.speed, self.movedir);
        }
    }

    static void SP_target_blaster(SubgameEntity self, GameExportsImpl gameExports) {
        self.use = use_target_blaster;
        GameBase.G_SetMovedir(self.s.angles, self.movedir);
        self.noise_index = gameExports.gameImports.soundindex("weapons/laser2.wav");

        if (0 == self.dmg)
            self.dmg = 15;
        if (0 == self.speed)
            self.speed = 1000;

        self.svflags = Defines.SVF_NOCLIENT;
    }

    static void SP_target_crosslevel_trigger(SubgameEntity self) {
        self.svflags = Defines.SVF_NOCLIENT;
        self.use = trigger_crosslevel_trigger_use;
    }

    static void SP_target_crosslevel_target(SubgameEntity self, GameExportsImpl gameExports) {
        if (0 == self.delay)
            self.delay = 1;
        self.svflags = Defines.SVF_NOCLIENT;

        self.think.action = target_crosslevel_target_think;
        self.think.nextTime = gameExports.level.time + self.delay;
    }

    private static void target_laser_on(SubgameEntity self, GameExportsImpl gameExports) {
        if (null == self.activator)
            self.activator = self;
        self.spawnflags |= 0x80000001;
        self.svflags &= ~Defines.SVF_NOCLIENT;
        target_laser_think.think(self, gameExports);
    }

    private static void target_laser_off(SubgameEntity self) {
        self.spawnflags &= ~1;
        self.svflags |= Defines.SVF_NOCLIENT;
        self.think.nextTime = 0;
    }

    static void SP_target_laser(SubgameEntity self, GameExportsImpl gameExports) {
        // let everything else get spawned before we start firing
        self.think.action = target_laser_start;
        self.think.nextTime = gameExports.level.time + 1;
    }

    static void SP_target_lightramp(SubgameEntity self, GameExportsImpl gameExports) {
        if (self.message == null || self.message.length() != 2
                || self.message.charAt(0) < 'a' || self.message.charAt(0) > 'z'
                || self.message.charAt(1) < 'a' || self.message.charAt(1) > 'z'
                || self.message.charAt(0) == self.message.charAt(1)) {
            gameExports.gameImports.dprintf("target_lightramp has bad ramp ("
                    + self.message + ") at " + Lib.vtos(self.s.origin) + "\n");
            gameExports.freeEntity(self);
            return;
        }

        if (gameExports.gameCvars.deathmatch.value != 0) {
            gameExports.freeEntity(self);
            return;
        }

        if (self.target == null) {
            gameExports.gameImports.dprintf(self.classname + " with no target at "
                    + Lib.vtos(self.s.origin) + "\n");
            gameExports.freeEntity(self);
            return;
        }

        self.svflags |= Defines.SVF_NOCLIENT;
        self.use = target_lightramp_use;
        self.think.action = target_lightramp_think;

        self.movedir[0] = self.message.charAt(0) - 'a';
        self.movedir[1] = self.message.charAt(1) - 'a';
        self.movedir[2] = (self.movedir[1] - self.movedir[0])
                / (self.speed / Defines.FRAMETIME);
    }

    static void SP_target_earthquake(SubgameEntity self, GameExportsImpl gameExports) {
        if (null == self.targetname)
            gameExports.gameImports.dprintf("untargeted " + self.classname + " at "
                    + Lib.vtos(self.s.origin) + "\n");

        if (0 == self.count)
            self.count = 5;

        if (0 == self.speed)
            self.speed = 200;

        self.svflags |= Defines.SVF_NOCLIENT;
        self.think.action = target_earthquake_think;
        self.use = target_earthquake_use;

        self.noise_index = gameExports.gameImports.soundindex("world/quake.wav");
    }

    /**
     * QUAKED target_speaker (1 0 0) (-8 -8 -8) (8 8 8) looped-on looped-off
     * reliable "noise" wav file to play "attenuation" -1 = none, send to whole
     * level 1 = normal fighting sounds 2 = idle sound level 3 = ambient sound
     * level "volume" 0.0 to 1.0
     * 
     * Normal sounds play each time the target is used. The reliable flag can be
     * set for crucial voiceovers.
     * 
     * Looped sounds are always atten 3 / vol 1, and the use function toggles it
     * on/off. Multiple identical looping sounds will just increase volume
     * without any speed cost.
     */
    private static EntUseAdapter Use_Target_Speaker = new EntUseAdapter() {
    	public String getID() { return "Use_Target_Speaker"; }
        public void use(SubgameEntity ent, SubgameEntity other, SubgameEntity activator, GameExportsImpl gameExports) {
            int chan;

            if ((ent.spawnflags & 3) != 0) { // looping sound toggles
                if (ent.s.sound != 0)
                    ent.s.sound = 0; // turn it off
                else
                    ent.s.sound = ent.noise_index; // start it
            } else { // normal sound
                if ((ent.spawnflags & 4) != 0)
                    chan = Defines.CHAN_VOICE | Defines.CHAN_RELIABLE;
                else
                    chan = Defines.CHAN_VOICE;
                // use a positioned_sound, because this entity won't normally be
                // sent to any clients because it is invisible
                gameExports.gameImports.positioned_sound(ent.s.origin, ent, chan,
                        ent.noise_index, ent.volume, ent.attenuation, 0);
            }

        }
    };


    private static EntUseAdapter Use_Target_Help = new EntUseAdapter() {
    	public String getID() { return "Use_Target_Help"; }
        public void use(SubgameEntity ent, SubgameEntity other, SubgameEntity activator, GameExportsImpl gameExports) {

            if ((ent.spawnflags & 1) != 0)
                gameExports.game.helpmessage1 = ent.message;
            else
                gameExports.game.helpmessage2 = ent.message;

            gameExports.game.helpchanged++;
        }
    };

    /**
     * QUAKED target_secret (1 0 1) (-8 -8 -8) (8 8 8) Counts a secret found.
     * These are single use targets.
     */
    private static EntUseAdapter use_target_secret = new EntUseAdapter() {
    	public String getID() { return "use_target_secret"; }
        public void use(SubgameEntity ent, SubgameEntity other, SubgameEntity activator, GameExportsImpl gameExports) {
            gameExports.gameImports.sound(ent, Defines.CHAN_VOICE, ent.noise_index, 1,
                    Defines.ATTN_NORM, 0);

            gameExports.level.found_secrets++;

            GameUtil.G_UseTargets(ent, activator, gameExports);
            gameExports.freeEntity(ent);
        }
    };
    
    /**
     * QUAKED target_goal (1 0 1) (-8 -8 -8) (8 8 8) Counts a goal completed.
     * These are single use targets.
     */
    private static EntUseAdapter use_target_goal = new EntUseAdapter() {
    	public String getID() { return "use_target_goal"; }
        public void use(SubgameEntity ent, SubgameEntity other, SubgameEntity activator, GameExportsImpl gameExports) {
            gameExports.gameImports.sound(ent, Defines.CHAN_VOICE, ent.noise_index, 1,
                    Defines.ATTN_NORM, 0);

            gameExports.level.found_goals++;

            if (gameExports.level.found_goals == gameExports.level.total_goals)
                gameExports.gameImports.configstring(Defines.CS_CDTRACK, "0");

            GameUtil.G_UseTargets(ent, activator, gameExports);
            gameExports.freeEntity(ent);
        }
    };


    /**
     * QUAKED target_explosion (1 0 0) (-8 -8 -8) (8 8 8) Spawns an explosion
     * temporary entity when used.
     * 
     * "delay" wait this long before going off "dmg" how much radius damage
     * should be done, defaults to 0
     */
    private static EntThinkAdapter target_explosion_explode = new EntThinkAdapter() {
    	public String getID() { return "target_explosion_explode"; }
        public boolean think(SubgameEntity self, GameExportsImpl gameExports) {

            float save;

            gameExports.gameImports.multicastMessage(self.s.origin, new PointTEMessage(Defines.TE_EXPLOSION1, self.s.origin), MulticastTypes.MULTICAST_PHS);

            GameCombat.T_RadiusDamage(self, self.activator, self.dmg, null,
                    self.dmg + 40, GameDefines.MOD_EXPLOSIVE, gameExports);

            save = self.delay;
            self.delay = 0;
            GameUtil.G_UseTargets(self, self.activator, gameExports);
            self.delay = save;
            return true;
        }
    };

    private static EntUseAdapter use_target_explosion = new EntUseAdapter() {
    	public String getID() { return "use_target_explosion"; }
        public void use(SubgameEntity self, SubgameEntity other, SubgameEntity activator, GameExportsImpl gameExports) {
            self.activator = activator;

            if (0 == self.delay) {
                target_explosion_explode.think(self, gameExports);
                return;
            }

            self.think.action = target_explosion_explode;
            self.think.nextTime = gameExports.level.time + self.delay;
        }
    };

    /**
     * QUAKED target_changelevel (1 0 0) (-8 -8 -8) (8 8 8) Changes level to
     * "map" when fired
     */
    private static EntUseAdapter use_target_changelevel = new EntUseAdapter() {
    	public String getID() { return "use_target_changelevel"; }
        public void use(SubgameEntity self, SubgameEntity other, SubgameEntity activator, GameExportsImpl gameExports) {
            if (gameExports.level.intermissiontime != 0)
                return; // already activated

            if (0 == gameExports.gameCvars.deathmatch.value && 0 == gameExports.gameCvars.coop.value) {
                if (gameExports.g_edicts[1].health <= 0)
                    return;
            }

            // if noexit, do a ton of damage to other
            if (gameExports.gameCvars.deathmatch.value != 0
                    && 0 == ((int) gameExports.gameCvars.dmflags.value & Defines.DF_ALLOW_EXIT)
                    && other != gameExports.g_edicts[0] /* world */
            ) {
                GameCombat.T_Damage(other, self, self, Globals.vec3_origin,
                        other.s.origin, Globals.vec3_origin,
                        10 * other.max_health, 1000, 0, GameDefines.MOD_EXIT, gameExports);
                return;
            }

            // if multiplayer, let everyone know who hit the exit
            if (gameExports.gameCvars.deathmatch.value != 0) {
                if (activator != null) {
                    gclient_t activatorClient = activator.getClient();
                    if (activatorClient != null) gameExports.gameImports.bprintf(Defines.PRINT_HIGH,
                            activatorClient.pers.netname
                                    + " exited the level.\n");
                }
            }

            // if going to a new unit, clear cross triggers
            if (self.map.indexOf('*') > -1)
                gameExports.game.serverflags &= ~(Defines.SFL_CROSS_TRIGGER_MASK);

            PlayerHud.BeginIntermission(self, gameExports);
        }
    };

    private static EntUseAdapter use_target_splash = new EntUseAdapter() {
    	public String getID() { return "use_target_splash"; }
        public void use(SubgameEntity self, SubgameEntity other, SubgameEntity activator, GameExportsImpl gameExports) {
            gameExports.gameImports.multicastMessage(self.s.origin, new SplashTEMessage(Defines.TE_SPLASH, self.count, self.s.origin, self.movedir, self.sounds), MulticastTypes.MULTICAST_PVS);

            if (self.dmg != 0)
                GameCombat.T_RadiusDamage(self, activator, self.dmg, null,
                        self.dmg + 40, GameDefines.MOD_SPLASH, gameExports);
        }
    };

    /**
     * QUAKED target_spawner (1 0 0) (-8 -8 -8) (8 8 8) Set target to the type
     * of entity you want spawned. Useful for spawning monsters and gibs in the
     * factory levels.
     * 
     * For monsters: Set direction to the facing you want it to have.
     * 
     * For gibs: Set direction if you want it moving and speed how fast it
     * should be moving otherwise it will just be dropped
     */
    private static EntUseAdapter use_target_spawner = new EntUseAdapter() {
    	public String getID() { return "use_target_spawner"; }
        public void use(SubgameEntity self, SubgameEntity other, SubgameEntity activator, GameExportsImpl gameExports) {
            SubgameEntity ent;

            ent = gameExports.G_Spawn();
            ent.classname = self.target;
            Math3D.VectorCopy(self.s.origin, ent.s.origin);
            Math3D.VectorCopy(self.s.angles, ent.s.angles);
            GameSpawn.ED_CallSpawn(ent, gameExports);
            gameExports.gameImports.unlinkentity(ent);
            GameUtil.KillBox(ent, gameExports);
            gameExports.gameImports.linkentity(ent);
            if (self.speed != 0)
                Math3D.VectorCopy(self.movedir, ent.velocity);
        }
    };

    /**
     * QUAKED target_blaster (1 0 0) (-8 -8 -8) (8 8 8) NOTRAIL NOEFFECTS Fires
     * a blaster bolt in the set direction when triggered.
     * 
     * dmg default is 15 speed default is 1000
     */
    private static EntUseAdapter use_target_blaster = new EntUseAdapter() {
    	public String getID() { return "use_target_blaster"; }
        public void use(SubgameEntity self, SubgameEntity other, SubgameEntity activator, GameExportsImpl gameExports) {
            int effect;

            if ((self.spawnflags & 2) != 0)
                effect = 0;
            else if ((self.spawnflags & 1) != 0)
                effect = Defines.EF_HYPERBLASTER;
            else
                effect = Defines.EF_BLASTER;

            GameWeapon.fire_blaster(self, self.s.origin, self.movedir, self.dmg,
                    (int) self.speed, Defines.EF_BLASTER,
                    GameDefines.MOD_TARGET_BLASTER != 0, gameExports
                    /* true */
            );
            gameExports.gameImports.sound(self, Defines.CHAN_VOICE, self.noise_index, 1,
                    Defines.ATTN_NORM, 0);
        }
    };

    /**
     * QUAKED target_crosslevel_trigger (.5 .5 .5) (-8 -8 -8) (8 8 8) trigger1
     * trigger2 trigger3 trigger4 trigger5 trigger6 trigger7 trigger8 Once this
     * trigger is touched/used, any trigger_crosslevel_target with the same
     * trigger number is automatically used when a level is started within the
     * same unit. It is OK to check multiple triggers. Message, delay, target,
     * and killtarget also work.
     */
    private static EntUseAdapter trigger_crosslevel_trigger_use = new EntUseAdapter() {
    	public String getID() { return "trigger_crosslevel_trigger_use"; }
        public void use(SubgameEntity self, SubgameEntity other, SubgameEntity activator, GameExportsImpl gameExports) {
            gameExports.game.serverflags |= self.spawnflags;
            gameExports.freeEntity(self);
        }
    };

    /**
     * QUAKED target_crosslevel_target (.5 .5 .5) (-8 -8 -8) (8 8 8) trigger1
     * trigger2 trigger3 trigger4 trigger5 trigger6 trigger7 trigger8 Triggered
     * by a trigger_crosslevel elsewhere within a unit. If multiple triggers are
     * checked, all must be true. Delay, target and killtarget also work.
     * 
     * "delay" delay before using targets if the trigger has been activated
     * (default 1)
     */
    private static EntThinkAdapter target_crosslevel_target_think = new EntThinkAdapter() {
    	public String getID() { return "target_crosslevel_target_think"; }
        public boolean think(SubgameEntity self, GameExportsImpl gameExports) {
            if (self.spawnflags == (gameExports.game.serverflags
                    & Defines.SFL_CROSS_TRIGGER_MASK & self.spawnflags)) {
                GameUtil.G_UseTargets(self, self, gameExports);
                gameExports.freeEntity(self);
            }
            return true;
        }
    };

    /**
     * QUAKED target_laser (0 .5 .8) (-8 -8 -8) (8 8 8) START_ON RED GREEN BLUE
     * YELLOW ORANGE FAT When triggered, fires a laser. You can either set a
     * target or a direction.
     */
    private static EntThinkAdapter target_laser_think = new EntThinkAdapter() {
    	public String getID() { return "target_laser_think"; }
        public boolean think(SubgameEntity self, GameExportsImpl gameExports) {

            edict_t ignore;
            float[] start = { 0, 0, 0 };
            float[] end = { 0, 0, 0 };
            trace_t tr;
            float[] point = { 0, 0, 0 };
            float[] last_movedir = { 0, 0, 0 };
            int count;

            if ((self.spawnflags & 0x80000000) != 0)
                count = 8;
            else
                count = 4;

            if (self.enemy != null) {
                Math3D.VectorCopy(self.movedir, last_movedir);
                Math3D.VectorMA(self.enemy.absmin, 0.5f, self.enemy.size, point);
                Math3D.VectorSubtract(point, self.s.origin, self.movedir);
                Math3D.VectorNormalize(self.movedir);
                if (!Math3D.VectorEquals(self.movedir, last_movedir))
                    self.spawnflags |= 0x80000000;
            }

            ignore = self;
            Math3D.VectorCopy(self.s.origin, start);
            Math3D.VectorMA(start, 2048, self.movedir, end);
            while (true) {
                tr = gameExports.gameImports.trace(start, null, null, end, ignore,
                        Defines.CONTENTS_SOLID | Defines.CONTENTS_MONSTER
                                | Defines.CONTENTS_DEADMONSTER);

                SubgameEntity target = (SubgameEntity) tr.ent;
                if (target == null)
                    break;

                // hurt it if we can
                if ((target.takedamage != 0)
                        && 0 == (target.flags & GameDefines.FL_IMMUNE_LASER))
                    GameCombat.T_Damage((SubgameEntity) target, self, self.activator,
                            self.movedir, tr.endpos, Globals.vec3_origin,
                            self.dmg, 1, DamageFlags.DAMAGE_ENERGY,
                            GameDefines.MOD_TARGET_LASER, gameExports);

                // if we hit something that's not a monster or player or is
                // immune to lasers, we're done
                if (0 == (target.svflags & Defines.SVF_MONSTER)
                        && (null == target.getClient())) {
                    if ((self.spawnflags & 0x80000000) != 0) {
                        self.spawnflags &= ~0x80000000;
                        gameExports.gameImports.multicastMessage(tr.endpos, new SplashTEMessage(Defines.TE_LASER_SPARKS, count, tr.endpos, tr.plane.normal, self.s.skinnum), MulticastTypes.MULTICAST_PVS);
                    }
                    break;
                }

                ignore = target;
                Math3D.VectorCopy(tr.endpos, start);
            }

            Math3D.VectorCopy(tr.endpos, self.s.old_origin);

            self.think.nextTime = gameExports.level.time + Defines.FRAMETIME;
            return true;
        }
    };

    private static EntUseAdapter target_laser_use = new EntUseAdapter() {
    	public String getID() { return "target_laser_use"; }

        public void use(SubgameEntity self, SubgameEntity other, SubgameEntity activator, GameExportsImpl gameExports) {
            self.activator = activator;
            if ((self.spawnflags & 1) != 0)
                target_laser_off(self);
            else
                target_laser_on(self, gameExports);
        }
    };

    private static EntThinkAdapter target_laser_start = new EntThinkAdapter() {
    	public String getID() { return "target_laser_start"; }
        public boolean think(SubgameEntity self, GameExportsImpl gameExports) {

            self.movetype = GameDefines.MOVETYPE_NONE;
            self.solid = Defines.SOLID_NOT;
            self.s.renderfx |= Defines.RF_BEAM | Defines.RF_TRANSLUCENT;
            self.s.modelindex = 1; // must be non-zero

            // set the beam diameter
            if ((self.spawnflags & 64) != 0)
                self.s.frame = 16;
            else
                self.s.frame = 4;

            // set the color
            if ((self.spawnflags & 2) != 0)
                self.s.skinnum = 0xf2f2f0f0;
            else if ((self.spawnflags & 4) != 0)
                self.s.skinnum = 0xd0d1d2d3;
            else if ((self.spawnflags & 8) != 0)
                self.s.skinnum = 0xf3f3f1f1;
            else if ((self.spawnflags & 16) != 0)
                self.s.skinnum = 0xdcdddedf;
            else if ((self.spawnflags & 32) != 0)
                self.s.skinnum = 0xe0e1e2e3;

            if (null == self.enemy) {
                if (self.target != null) {
                    EdictIterator edit = GameBase.G_Find(null, GameBase.findByTargetName,
                            self.target, gameExports);
                    if (edit == null)
                        gameExports.gameImports.dprintf(self.classname + " at "
                                + Lib.vtos(self.s.origin) + ": " + self.target
                                + " is a bad target\n");
                    self.enemy = edit.o;
                } else {
                    GameBase.G_SetMovedir(self.s.angles, self.movedir);
                }
            }
            self.use = target_laser_use;
            self.think.action = target_laser_think;

            if (0 == self.dmg)
                self.dmg = 1;

            Math3D.VectorSet(self.mins, -8, -8, -8);
            Math3D.VectorSet(self.maxs, 8, 8, 8);
            gameExports.gameImports.linkentity(self);

            if ((self.spawnflags & 1) != 0)
                target_laser_on(self, gameExports);
            else
                target_laser_off(self);
            return true;
        }
    };

    /**
     * QUAKED target_lightramp (0 .5 .8) (-8 -8 -8) (8 8 8) TOGGLE speed How
     * many seconds the ramping will take message two letters; starting
     * lightlevel and ending lightlevel
     */
    private static EntThinkAdapter target_lightramp_think = new EntThinkAdapter() {
    	public String getID() { return "target_lightramp_think"; }
        public boolean think(SubgameEntity self, GameExportsImpl gameExports) {

            char tmp[] = {(char) ('a' + (int) (self.movedir[0] + (gameExports.level.time - self.timestamp)
                    / Defines.FRAMETIME * self.movedir[2]))};
            
            gameExports.gameImports.configstring(Defines.CS_LIGHTS + self.enemy.style,
                    new String(tmp));

            if ((gameExports.level.time - self.timestamp) < self.speed) {
                self.think.nextTime = gameExports.level.time + Defines.FRAMETIME;
            } else if ((self.spawnflags & 1) != 0) {
                char temp;

                temp = (char) self.movedir[0];
                self.movedir[0] = self.movedir[1];
                self.movedir[1] = temp;
                self.movedir[2] *= -1;
            }

            return true;
        }
    };

    private static EntUseAdapter target_lightramp_use = new EntUseAdapter() {
    	public String getID() { return "target_lightramp_use"; }
        public void use(SubgameEntity self, SubgameEntity other, SubgameEntity activator, GameExportsImpl gameExports) {
            if (self.enemy == null) {

                // check all the targets
                EdictIterator es = null;

                while (true) {
                    es = GameBase
                            .G_Find(es, GameBase.findByTargetName, self.target, gameExports);
                    
                    if (es == null)
                        break;

                    SubgameEntity e = es.o;

                    if (!"light".equals(e.classname)) {
                        gameExports.gameImports.dprintf(self.classname + " at "
                                + Lib.vtos(self.s.origin));
                        gameExports.gameImports.dprintf("target " + self.target + " ("
                                + e.classname + " at " + Lib.vtos(e.s.origin)
                                + ") is not a light\n");
                    } else {
                        self.enemy = e;
                    }
                }

                if (null == self.enemy) {
                    gameExports.gameImports.dprintf(self.classname + " target "
                            + self.target + " not found at "
                            + Lib.vtos(self.s.origin) + "\n");
                    gameExports.freeEntity(self);
                    return;
                }
            }

            self.timestamp = gameExports.level.time;
            target_lightramp_think.think(self, gameExports);
        }
    };

    /**
     * QUAKED target_earthquake (1 0 0) (-8 -8 -8) (8 8 8) When triggered, this
     * initiates a level-wide earthquake. All players and monsters are affected.
     * "speed" severity of the quake (default:200) "count" duration of the quake
     * (default:5)
     */
    private static EntThinkAdapter target_earthquake_think = new EntThinkAdapter() {
    	public String getID() { return "target_earthquake_think"; }
        public boolean think(SubgameEntity self, GameExportsImpl gameExports) {

            int i;

            if (self.last_move_time < gameExports.level.time) {
                gameExports.gameImports.positioned_sound(self.s.origin, self,
                        Defines.CHAN_AUTO, self.noise_index, 1.0f,
                        Defines.ATTN_NONE, 0);
                self.last_move_time = gameExports.level.time + 0.5f;
            }

            for (i = 1; i < gameExports.num_edicts; i++) {
                SubgameEntity e = gameExports.g_edicts[i];

                if (!e.inuse)
                    continue;
                if (null == e.getClient())
                    continue;
                if (null == e.groundentity)
                    continue;

                e.groundentity = null;
                e.velocity[0] += Lib.crandom() * 150;
                e.velocity[1] += Lib.crandom() * 150;
                e.velocity[2] = self.speed * (100.0f / e.mass);
            }

            if (gameExports.level.time < self.timestamp)
                self.think.nextTime = gameExports.level.time + Defines.FRAMETIME;

            return true;
        }
    };

    private static EntUseAdapter target_earthquake_use = new EntUseAdapter() {
    	public String getID() { return "target_earthquake_use"; }
        public void use(SubgameEntity self, SubgameEntity other, SubgameEntity activator, GameExportsImpl gameExports) {
            self.timestamp = gameExports.level.time + self.count;
            self.think.nextTime = gameExports.level.time + Defines.FRAMETIME;
            self.activator = activator;
            self.last_move_time = 0;
        }
    };
}
