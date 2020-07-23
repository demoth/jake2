/*
 * SV.java
 * Copyright (C) 2003
 * 
 * $Id: SV.java,v 1.12 2006-01-21 21:53:32 salomo Exp $
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

import jake2.qcommon.*;
import jake2.qcommon.exec.cvar_t;
import jake2.qcommon.util.Lib;
import jake2.qcommon.util.Math3D;

/**
 * SV
 */
final class SV {

    private final cvar_t sv_maxvelocity;

    SV(GameImports gameImports) {
        sv_maxvelocity = gameImports.cvar("sv_maxvelocity", "2000", 0);
    }

    ///////////////////////////////////////
    private static edict_t[] SV_TestEntityPosition(edict_t ent) {
        int mask;

        if (ent.clipmask != 0)
            mask = ent.clipmask;
        else
            mask = Defines.MASK_SOLID;

        trace_t trace = GameBase.gameExports.gameImports.trace(ent.s.origin, ent.mins, ent.maxs,
                ent.s.origin, ent, mask);

        if (trace.startsolid)
            return GameBase.gameExports.g_edicts;

        return null;
    }

    ///////////////////////////////////////
    private void SV_CheckVelocity(SubgameEntity ent) {
        int i;

        //
        //	   bound velocity
        //
        for (i = 0; i < 3; i++) {
            if (ent.velocity[i] > sv_maxvelocity.value)
                ent.velocity[i] = sv_maxvelocity.value;
            else if (ent.velocity[i] < -sv_maxvelocity.value)
                ent.velocity[i] = -sv_maxvelocity.value;
        }
    }

    /**
     * Runs thinking code for this frame if necessary.
     */
    private static boolean SV_RunThink(SubgameEntity ent) {
        float thinktime;

        thinktime = ent.nextthink;
        if (thinktime <= 0)
            return true;
        if (thinktime > GameBase.gameExports.level.time + 0.001)
            return true;

        ent.nextthink = 0;

        if (ent.think == null)
            Com.Error(Defines.ERR_FATAL, "NULL ent.think");

        ent.think.think(ent, GameBase.gameExports);

        return false;
    }

    /**
     * Two entities have touched, so run their touch functions.
     */
    private static void SV_Impact(SubgameEntity e1, trace_t trace) {
        // assuming edicts are created only on game side
        SubgameEntity e2 = (SubgameEntity) trace.ent;

        if (e1.touch != null && e1.solid != Defines.SOLID_NOT)
            e1.touch.touch(e1, e2, trace.plane, trace.surface, GameBase.gameExports);

        if (e2.touch != null && e2.solid != Defines.SOLID_NOT)
            e2.touch.touch(e2, e1, GameBase.dummyplane, null, GameBase.gameExports);
    }

    private static int SV_FlyMove(SubgameEntity ent, float time, int mask) {
        edict_t hit;
        int bumpcount, numbumps;
        float[] dir = { 0.0f, 0.0f, 0.0f };
        float d;
        int numplanes;
        float[][] planes = new float[PMove.MAX_CLIP_PLANES][3];
        float[] primal_velocity = { 0.0f, 0.0f, 0.0f };
        float[] original_velocity = { 0.0f, 0.0f, 0.0f };
        float[] new_velocity = { 0.0f, 0.0f, 0.0f };
        int i, j;
        trace_t trace;
        float[] end = { 0.0f, 0.0f, 0.0f };
        float time_left;
        int blocked;

        numbumps = 4;

        blocked = 0;
        Math3D.VectorCopy(ent.velocity, original_velocity);
        Math3D.VectorCopy(ent.velocity, primal_velocity);
        numplanes = 0;

        time_left = time;

        ent.groundentity = null;
        for (bumpcount = 0; bumpcount < numbumps; bumpcount++) {
            for (i = 0; i < 3; i++)
                end[i] = ent.s.origin[i] + time_left * ent.velocity[i];

            trace = GameBase.gameExports.gameImports.trace(ent.s.origin, ent.mins, ent.maxs, end,
                    ent, mask);

            if (trace.allsolid) { // entity is trapped in another solid
                Math3D.VectorCopy(Globals.vec3_origin, ent.velocity);
                return 3;
            }

            if (trace.fraction > 0) { // actually covered some distance
                Math3D.VectorCopy(trace.endpos, ent.s.origin);
                Math3D.VectorCopy(ent.velocity, original_velocity);
                numplanes = 0;
            }

            if (trace.fraction == 1)
                break; // moved the entire distance

            hit = trace.ent;

            if (trace.plane.normal[2] > 0.7) {
                blocked |= 1; // floor
                if (hit.solid == Defines.SOLID_BSP) {
                    ent.groundentity = hit;
                    ent.groundentity_linkcount = hit.linkcount;
                }
            }
            if (trace.plane.normal[2] == 0.0f) {
                blocked |= 2; // step
            }

            //
            //	   run the impact function
            //
            SV_Impact(ent, trace);
            if (!ent.inuse)
                break; // removed by the impact function

            time_left -= time_left * trace.fraction;

            // cliped to another plane
            if (numplanes >= PMove.MAX_CLIP_PLANES) { // this shouldn't
                                                         // really happen
                Math3D.VectorCopy(Globals.vec3_origin, ent.velocity);
                return 3;
            }

            Math3D.VectorCopy(trace.plane.normal, planes[numplanes]);
            numplanes++;

            //
            //	   modify original_velocity so it parallels all of the clip planes
            //
            for (i = 0; i < numplanes; i++) {
                GameBase.ClipVelocity(original_velocity, planes[i],
                        new_velocity, 1);

                for (j = 0; j < numplanes; j++)
                    if ((j != i)
                            && !Math3D.VectorEquals(planes[i], planes[j])) {
                        if (Math3D.DotProduct(new_velocity, planes[j]) < 0)
                            break; // not ok
                    }
                if (j == numplanes)
                    break;
            }

            if (i != numplanes) { // go along this plane
                Math3D.VectorCopy(new_velocity, ent.velocity);
            } else { // go along the crease
                if (numplanes != 2) {
                    //					gi.dprintf ("clip velocity, numplanes ==
                    // %i\n",numplanes);
                    Math3D.VectorCopy(Globals.vec3_origin, ent.velocity);
                    return 7;
                }
                Math3D.CrossProduct(planes[0], planes[1], dir);
                d = Math3D.DotProduct(dir, ent.velocity);
                Math3D.VectorScale(dir, d, ent.velocity);
            }

            //
            //	   if original velocity is against the original velocity, stop dead
            //	   to avoid tiny occilations in sloping corners
            //
            if (Math3D.DotProduct(ent.velocity, primal_velocity) <= 0) {
                Math3D.VectorCopy(Globals.vec3_origin, ent.velocity);
                return blocked;
            }
        }

        return blocked;
    }

    /**
     * SV_AddGravity.
     */
    private static void SV_AddGravity(SubgameEntity ent) {
        ent.velocity[2] -= ent.gravity * GameBase.gameExports.cvarCache.sv_gravity.value
                * Defines.FRAMETIME;
    }

    /**
     * Does not change the entities velocity at all
     */
    private static trace_t SV_PushEntity(SubgameEntity ent, float[] push) {
        trace_t trace;
        float[] start = { 0, 0, 0 };
        float[] end = { 0, 0, 0 };
        int mask;

        Math3D.VectorCopy(ent.s.origin, start);
        Math3D.VectorAdd(start, push, end);

        // FIXME: test this
        // a goto statement was replaced.
        boolean retry = false;

        do {
            if (ent.clipmask != 0)
                mask = ent.clipmask;
            else
                mask = Defines.MASK_SOLID;

            trace = GameBase.gameExports.gameImports
                    .trace(start, ent.mins, ent.maxs, end, ent, mask);

            Math3D.VectorCopy(trace.endpos, ent.s.origin);
            GameBase.gameExports.gameImports.linkentity(ent);

            retry = false;
            if (trace.fraction != 1.0) {
                SV_Impact(ent, trace);

                // if the pushed entity went away and the pusher is still there
                if (!trace.ent.inuse && ent.inuse) {
                    // move the pusher back and try again
                    Math3D.VectorCopy(start, ent.s.origin);
                    GameBase.gameExports.gameImports.linkentity(ent);
                    //goto retry;
                    retry = true;
                }
            }
        } while (retry);

        if (ent.inuse)
            GameBase.G_TouchTriggers(ent);

        return trace;
    }

    /**
     * Objects need to be moved back on a failed push, otherwise riders would
     * continue to slide.
     */
    private static boolean SV_Push(SubgameEntity pusher, float[] move, float[] amove) {
        int i;
        float[] mins = { 0, 0, 0 };
        float[] maxs = { 0, 0, 0 };
        float[] org = { 0, 0, 0 };
        float[] org2 = { 0, 0, 0 };
        float[] move2 = { 0, 0, 0 };
        float[] forward = { 0, 0, 0 };
        float[] right = { 0, 0, 0 };
        float[] up = { 0, 0, 0 };

        // clamp the move to 1/8 units, so the position will
        // be accurate for client side prediction
        for (i = 0; i < 3; i++) {
            float temp;
            temp = move[i] * 8.0f;
            if (temp > 0.0)
                temp += 0.5;
            else
                temp -= 0.5;
            move[i] = 0.125f * (int) temp;
        }

        // find the bounding box
        for (i = 0; i < 3; i++) {
            mins[i] = pusher.absmin[i] + move[i];
            maxs[i] = pusher.absmax[i] + move[i];
        }

        //	   we need this for pushing things later
        Math3D.VectorSubtract(Globals.vec3_origin, amove, org);
        Math3D.AngleVectors(org, forward, right, up);

        //	   save the pusher's original position
        GameBase.pushed[GameBase.pushed_p].ent = pusher;
        Math3D.VectorCopy(pusher.s.origin,
                GameBase.pushed[GameBase.pushed_p].origin);
        Math3D.VectorCopy(pusher.s.angles,
                GameBase.pushed[GameBase.pushed_p].angles);

        if (pusher.getClient() != null)
            GameBase.pushed[GameBase.pushed_p].deltayaw = pusher.getClient().getPlayerState().pmove.delta_angles[Defines.YAW];

        GameBase.pushed_p++;

        //	   move the pusher to it's final position
        Math3D.VectorAdd(pusher.s.origin, move, pusher.s.origin);
        Math3D.VectorAdd(pusher.s.angles, amove, pusher.s.angles);
        GameBase.gameExports.gameImports.linkentity(pusher);

        //	   see if any solid entities are inside the final position

        //check= g_edicts + 1;
        for (int e = 1; e < GameBase.gameExports.num_edicts; e++) {
            SubgameEntity check = GameBase.gameExports.g_edicts[e];
            if (!check.inuse)
                continue;
            if (check.movetype == GameDefines.MOVETYPE_PUSH
                    || check.movetype == GameDefines.MOVETYPE_STOP
                    || check.movetype == GameDefines.MOVETYPE_NONE
                    || check.movetype == GameDefines.MOVETYPE_NOCLIP)
                continue;

            if (check.area.prev == null)
                continue; // not linked in anywhere

            // if the entity is standing on the pusher, it will definitely be
            // moved
            if (check.groundentity != pusher) {
                // see if the ent needs to be tested
                if (check.absmin[0] >= maxs[0] || check.absmin[1] >= maxs[1]
                        || check.absmin[2] >= maxs[2]
                        || check.absmax[0] <= mins[0]
                        || check.absmax[1] <= mins[1]
                        || check.absmax[2] <= mins[2])
                    continue;

                // see if the ent's bbox is inside the pusher's final position
                if (SV_TestEntityPosition(check) == null)
                    continue;
            }

            if ((pusher.movetype == GameDefines.MOVETYPE_PUSH)
                    || (check.groundentity == pusher)) {
                // move this entity
                GameBase.pushed[GameBase.pushed_p].ent = check;
                Math3D.VectorCopy(check.s.origin,
                        GameBase.pushed[GameBase.pushed_p].origin);
                Math3D.VectorCopy(check.s.angles,
                        GameBase.pushed[GameBase.pushed_p].angles);
                GameBase.pushed_p++;

                // try moving the contacted entity
                Math3D.VectorAdd(check.s.origin, move, check.s.origin);
                if (check.getClient() != null) { // FIXME: doesn't rotate monsters?
                    check.getClient().getPlayerState().pmove.delta_angles[Defines.YAW] += amove[Defines.YAW];
                }

                // figure movement due to the pusher's amove
                Math3D.VectorSubtract(check.s.origin, pusher.s.origin, org);
                org2[0] = Math3D.DotProduct(org, forward);
                org2[1] = -Math3D.DotProduct(org, right);
                org2[2] = Math3D.DotProduct(org, up);
                Math3D.VectorSubtract(org2, org, move2);
                Math3D.VectorAdd(check.s.origin, move2, check.s.origin);

                // may have pushed them off an edge
                if (check.groundentity != pusher)
                    check.groundentity = null;

                edict_t[] block = SV_TestEntityPosition(check);
                if (block == null) { // pushed ok
                    GameBase.gameExports.gameImports.linkentity(check);
                    // impact?
                    continue;
                }

                // if it is ok to leave in the old position, do it
                // this is only relevent for riding entities, not pushed
                // FIXME: this doesn't acount for rotation
                Math3D.VectorSubtract(check.s.origin, move, check.s.origin);
                block = SV_TestEntityPosition(check);

                if (block == null) {
                    GameBase.pushed_p--;
                    continue;
                }
            }

            // save off the obstacle so we can call the block function
            GameBase.obstacle = check;

            // move back any entities we already moved
            // go backwards, so if the same entity was pushed
            // twice, it goes back to the original position
            for (int ip = GameBase.pushed_p - 1; ip >= 0; ip--) {
                pushed_t p = GameBase.pushed[ip];
                Math3D.VectorCopy(p.origin, p.ent.s.origin);
                Math3D.VectorCopy(p.angles, p.ent.s.angles);
                if (p.ent.getClient() != null) {
                    p.ent.getClient().getPlayerState().pmove.delta_angles[Defines.YAW] = (short) p.deltayaw;
                }
                GameBase.gameExports.gameImports.linkentity(p.ent);
            }
            return false;
        }

        //	  FIXME: is there a better way to handle this?
        // see if anything we moved has touched a trigger
        for (int ip = GameBase.pushed_p - 1; ip >= 0; ip--) {
            GameBase.G_TouchTriggers(GameBase.pushed[ip].ent);
        }
        return true;
    }

    /**
     * 
     * Bmodel objects don't interact with each other, but push all box objects.
     */
    static void SV_Physics_Pusher(SubgameEntity ent) {
        float[] move = { 0, 0, 0 };
        float[] amove = { 0, 0, 0 };

        // if not a team captain, so movement will be handled elsewhere
        if ((ent.flags & GameDefines.FL_TEAMSLAVE) != 0)
            return;

        // make sure all team slaves can move before commiting
        // any moves or calling any think functions
        // if the move is blocked, all moved objects will be backed out
        //	  retry:
        GameBase.pushed_p = 0;
        SubgameEntity part;
        for (part = ent; part != null; part = part.teamchain) {
            if (part.velocity[0] != 0 || part.velocity[1] != 0
                    || part.velocity[2] != 0 || part.avelocity[0] != 0
                    || part.avelocity[1] != 0 || part.avelocity[2] != 0) { // object
                                                                           // is
                                                                           // moving
                Math3D.VectorScale(part.velocity, Defines.FRAMETIME, move);
                Math3D.VectorScale(part.avelocity, Defines.FRAMETIME, amove);

                if (!SV_Push(part, move, amove))
                    break; // move was blocked
            }
        }
        if (GameBase.pushed_p > Defines.MAX_EDICTS)
            Com.Error(Defines.ERR_FATAL, "pushed_p > &pushed[MAX_EDICTS], memory corrupted");

        if (part != null) {
            // the move failed, bump all nextthink times and back out moves
            for (SubgameEntity mv = ent; mv != null; mv = mv.teamchain) {
                if (mv.nextthink > 0)
                    mv.nextthink += Defines.FRAMETIME;
            }

            // if the pusher has a "blocked" function, call it
            // otherwise, just stay in place until the obstacle is gone
            if (part.blocked != null)
                part.blocked.blocked(part, GameBase.obstacle);
        } else { // the move succeeded, so call all think functions
            for (part = ent; part != null; part = part.teamchain) {
                SV_RunThink(part);
            }
        }
    }


    /**
     * Non moving objects can only think.
     */
    static void SV_Physics_None(SubgameEntity ent) {
        // regular thinking
        SV_RunThink(ent);
    }

    /**
     * A moving object that doesn't obey physics.
     */
    static void SV_Physics_Noclip(SubgameEntity ent) {
        //	   regular thinking
        if (!SV_RunThink(ent))
            return;

        Math3D.VectorMA(ent.s.angles, Defines.FRAMETIME, ent.avelocity,
                ent.s.angles);
        Math3D.VectorMA(ent.s.origin, Defines.FRAMETIME, ent.velocity,
                ent.s.origin);

        GameBase.gameExports.gameImports.linkentity(ent);
    }

    /**
     * Toss, bounce, and fly movement. When onground, do nothing.
     */
    void SV_Physics_Toss(SubgameEntity ent) {

        float[] move = { 0, 0, 0 };
        float[] old_origin = { 0, 0, 0 };

        //	   regular thinking
        SV_RunThink(ent);

        // if not a team captain, so movement will be handled elsewhere
        if ((ent.flags & GameDefines.FL_TEAMSLAVE) != 0)
            return;

        if (ent.velocity[2] > 0)
            ent.groundentity = null;

        //	check for the groundentity going away
        if (ent.groundentity != null)
            if (!ent.groundentity.inuse)
                ent.groundentity = null;

        //	   if onground, return without moving
        if (ent.groundentity != null)
            return;

        Math3D.VectorCopy(ent.s.origin, old_origin);

        SV_CheckVelocity(ent);

        //	   add gravity
        if (ent.movetype != GameDefines.MOVETYPE_FLY
                && ent.movetype != GameDefines.MOVETYPE_FLYMISSILE)
            SV_AddGravity(ent);

        //	   move angles
        Math3D.VectorMA(ent.s.angles, Defines.FRAMETIME, ent.avelocity,
                ent.s.angles);

        //	   move origin
        Math3D.VectorScale(ent.velocity, Defines.FRAMETIME, move);
        trace_t trace = SV_PushEntity(ent, move);
        if (!ent.inuse)
            return;

        if (trace.fraction < 1) {
            float backoff;
            if (ent.movetype == GameDefines.MOVETYPE_BOUNCE)
                backoff = 1.5f;
            else
                backoff = 1;

            GameBase.ClipVelocity(ent.velocity, trace.plane.normal,
                    ent.velocity, backoff);

            // stop if on ground
            if (trace.plane.normal[2] > 0.7) {
                if (ent.velocity[2] < 60
                        || ent.movetype != GameDefines.MOVETYPE_BOUNCE) {
                    ent.groundentity = trace.ent;
                    ent.groundentity_linkcount = trace.ent.linkcount;
                    Math3D.VectorCopy(Globals.vec3_origin, ent.velocity);
                    Math3D.VectorCopy(Globals.vec3_origin, ent.avelocity);
                }
            }

            // if (ent.touch)
            //	ent.touch (ent, trace.ent, &trace.plane, trace.surface);
        }

        //	   check for water transition
        boolean wasinwater = (ent.watertype & Defines.MASK_WATER) != 0;
        ent.watertype = GameBase.gameExports.gameImports.getPointContents(ent.s.origin);
        boolean isinwater = (ent.watertype & Defines.MASK_WATER) != 0;

        if (isinwater)
            ent.waterlevel = 1;
        else
            ent.waterlevel = 0;

        if (!wasinwater && isinwater)
            GameBase.gameExports.gameImports.positioned_sound(old_origin, ent, Defines.CHAN_AUTO,
                    GameBase.gameExports.gameImports.soundindex("misc/h2ohit1.wav"), 1, 1, 0);
        else if (wasinwater && !isinwater)
            GameBase.gameExports.gameImports.positioned_sound(ent.s.origin, ent, Defines.CHAN_AUTO,
                    GameBase.gameExports.gameImports.soundindex("misc/h2ohit1.wav"), 1, 1, 0);

        //	   move teamslaves
        for (SubgameEntity slave = ent.teamchain; slave != null; slave = slave.teamchain) {
            Math3D.VectorCopy(ent.s.origin, slave.s.origin);
            GameBase.gameExports.gameImports.linkentity(slave);
        }
    }


    // FIXME: hacked in for E3 demo
    private static void SV_AddRotationalFriction(SubgameEntity ent) {
        int n;
        float adjustment;

        Math3D.VectorMA(ent.s.angles, Defines.FRAMETIME, ent.avelocity,
                ent.s.angles);
        adjustment = Defines.FRAMETIME * Defines.sv_stopspeed
                * Defines.sv_friction;
        for (n = 0; n < 3; n++) {
            if (ent.avelocity[n] > 0) {
                ent.avelocity[n] -= adjustment;
                if (ent.avelocity[n] < 0)
                    ent.avelocity[n] = 0;
            } else {
                ent.avelocity[n] += adjustment;
                if (ent.avelocity[n] > 0)
                    ent.avelocity[n] = 0;
            }
        }
    }
    
    /**
     * Monsters freefall when they don't have a ground entity, otherwise all
     * movement is done with discrete steps.
     * 
     * This is also used for objects that have become still on the ground, but
     * will fall if the floor is pulled out from under them. FIXME: is this
     * true?
     */

    void SV_Physics_Step(SubgameEntity ent) {
        boolean wasonground;
        boolean hitsound = false;
        float vel[];
        float speed, newspeed, control;
        float friction;
        edict_t groundentity;
        int mask;

        // airborn monsters should always check for ground
        if (ent.groundentity == null)
            M.M_CheckGround(ent);

        groundentity = ent.groundentity;

        SV_CheckVelocity(ent);

        if (groundentity != null)
            wasonground = true;
        else
            wasonground = false;

        if (ent.avelocity[0] != 0 || ent.avelocity[1] != 0
                || ent.avelocity[2] != 0)
            SV_AddRotationalFriction(ent);

        // add gravity except:
        //   flying monsters
        //   swimming monsters who are in the water
        if (!wasonground)
            if (0 == (ent.flags & GameDefines.FL_FLY))
                if (!((ent.flags & GameDefines.FL_SWIM) != 0 && (ent.waterlevel > 2))) {
                    if (ent.velocity[2] < GameBase.gameExports.cvarCache.sv_gravity.value * -0.1)
                        hitsound = true;
                    if (ent.waterlevel == 0)
                        SV_AddGravity(ent);
                }

        // friction for flying monsters that have been given vertical velocity
        if ((ent.flags & GameDefines.FL_FLY) != 0 && (ent.velocity[2] != 0)) {
            speed = Math.abs(ent.velocity[2]);
            control = speed < Defines.sv_stopspeed ? Defines.sv_stopspeed
                    : speed;
            friction = Defines.sv_friction / 3;
            newspeed = speed - (Defines.FRAMETIME * control * friction);
            if (newspeed < 0)
                newspeed = 0;
            newspeed /= speed;
            ent.velocity[2] *= newspeed;
        }

        // friction for flying monsters that have been given vertical velocity
        if ((ent.flags & GameDefines.FL_SWIM) != 0 && (ent.velocity[2] != 0)) {
            speed = Math.abs(ent.velocity[2]);
            control = speed < Defines.sv_stopspeed ? Defines.sv_stopspeed
                    : speed;
            newspeed = speed
                    - (Defines.FRAMETIME * control * Defines.sv_waterfriction * ent.waterlevel);
            if (newspeed < 0)
                newspeed = 0;
            newspeed /= speed;
            ent.velocity[2] *= newspeed;
        }

        if (ent.velocity[2] != 0 || ent.velocity[1] != 0
                || ent.velocity[0] != 0) {
            // apply friction
            // let dead monsters who aren't completely onground slide
            if ((wasonground)
                    || 0 != (ent.flags & (GameDefines.FL_SWIM | GameDefines.FL_FLY)))
                if (!(ent.health <= 0.0 && !M.M_CheckBottom(ent))) {
                    vel = ent.velocity;
                    speed = (float) Math
                            .sqrt(vel[0] * vel[0] + vel[1] * vel[1]);
                    if (speed != 0) {
                        friction = Defines.sv_friction;

                        control = speed < Defines.sv_stopspeed ? Defines.sv_stopspeed
                                : speed;
                        newspeed = speed - Defines.FRAMETIME * control
                                * friction;

                        if (newspeed < 0)
                            newspeed = 0;
                        newspeed /= speed;

                        vel[0] *= newspeed;
                        vel[1] *= newspeed;
                    }
                }

            if ((ent.svflags & Defines.SVF_MONSTER) != 0)
                mask = Defines.MASK_MONSTERSOLID;
            else
                mask = Defines.MASK_SOLID;

            SV_FlyMove(ent, Defines.FRAMETIME, mask);

            GameBase.gameExports.gameImports.linkentity(ent);
            GameBase.G_TouchTriggers(ent);
            if (!ent.inuse)
                return;

            if (ent.groundentity != null)
                if (!wasonground)
                    if (hitsound)
                        GameBase.gameExports.gameImports.sound(ent, 0, 
                        		GameBase.gameExports.gameImports.soundindex("world/land.wav"), 1, 1, 0);
        }

        // regular thinking
        SV_RunThink(ent);
    }

    /**
     * Called by monster program code. The move will be adjusted for slopes and
     * stairs, but if the move isn't possible, no move is done, false is
     * returned, and pr_global_struct.trace_normal is set to the normal of the
     * blocking wall.
     */
    
    // FIXME: since we need to test end position contents here, can we avoid
    // doing it again later in catagorize position?
    static boolean SV_movestep(SubgameEntity ent, float[] move, boolean relink) {
        float dz;
        float[] oldorg = { 0, 0, 0 };
        float[] neworg = { 0, 0, 0 };
        float[] end = { 0, 0, 0 };

        trace_t trace = null; // = new trace_t();
        int i;
        float stepsize;
        float[] test = { 0, 0, 0 };
        int contents;

        //	   try the move
        Math3D.VectorCopy(ent.s.origin, oldorg);
        Math3D.VectorAdd(ent.s.origin, move, neworg);

        //	   flying monsters don't step up
        if ((ent.flags & (GameDefines.FL_SWIM | GameDefines.FL_FLY)) != 0) {
            // try one move with vertical motion, then one without
            for (i = 0; i < 2; i++) {
                Math3D.VectorAdd(ent.s.origin, move, neworg);
                if (i == 0 && ent.enemy != null) {
                    if (ent.goalentity == null)
                        ent.goalentity = ent.enemy;
                    dz = ent.s.origin[2] - ent.goalentity.s.origin[2];
                    if (ent.goalentity.getClient() != null) {
                        if (dz > 40)
                            neworg[2] -= 8;
                        if (!((ent.flags & GameDefines.FL_SWIM) != 0 && (ent.waterlevel < 2)))
                            if (dz < 30)
                                neworg[2] += 8;
                    } else {
                        if (dz > 8)
                            neworg[2] -= 8;
                        else if (dz > 0)
                            neworg[2] -= dz;
                        else if (dz < -8)
                            neworg[2] += 8;
                        else
                            neworg[2] += dz;
                    }
                }
                trace = GameBase.gameExports.gameImports.trace(ent.s.origin, ent.mins, ent.maxs,
                        neworg, ent, Defines.MASK_MONSTERSOLID);

                // fly monsters don't enter water voluntarily
                if ((ent.flags & GameDefines.FL_FLY) != 0) {
                    if (ent.waterlevel == 0) {
                        test[0] = trace.endpos[0];
                        test[1] = trace.endpos[1];
                        test[2] = trace.endpos[2] + ent.mins[2] + 1;
                        contents = GameBase.gameExports.gameImports.getPointContents(test);
                        if ((contents & Defines.MASK_WATER) != 0)
                            return false;
                    }
                }

                // swim monsters don't exit water voluntarily
                if ((ent.flags & GameDefines.FL_SWIM) != 0) {
                    if (ent.waterlevel < 2) {
                        test[0] = trace.endpos[0];
                        test[1] = trace.endpos[1];
                        test[2] = trace.endpos[2] + ent.mins[2] + 1;
                        contents = GameBase.gameExports.gameImports.getPointContents(test);
                        if ((contents & Defines.MASK_WATER) == 0)
                            return false;
                    }
                }

                if (trace.fraction == 1) {
                    Math3D.VectorCopy(trace.endpos, ent.s.origin);
                    if (relink) {
                        GameBase.gameExports.gameImports.linkentity(ent);
                        GameBase.G_TouchTriggers(ent);
                    }
                    return true;
                }

                if (ent.enemy == null)
                    break;
            }

            return false;
        }

        //	   push down from a step height above the wished position
        if ((ent.monsterinfo.aiflags & GameDefines.AI_NOSTEP) == 0)
            stepsize = GameBase.STEPSIZE;
        else
            stepsize = 1;

        neworg[2] += stepsize;
        Math3D.VectorCopy(neworg, end);
        end[2] -= stepsize * 2;

        trace = GameBase.gameExports.gameImports.trace(neworg, ent.mins, ent.maxs, end, ent,
                Defines.MASK_MONSTERSOLID);

        if (trace.allsolid)
            return false;

        if (trace.startsolid) {
            neworg[2] -= stepsize;
            trace = GameBase.gameExports.gameImports.trace(neworg, ent.mins, ent.maxs, end, ent,
                    Defines.MASK_MONSTERSOLID);
            if (trace.allsolid || trace.startsolid)
                return false;
        }

        // don't go in to water
        if (ent.waterlevel == 0) {
            test[0] = trace.endpos[0];
            test[1] = trace.endpos[1];
            test[2] = trace.endpos[2] + ent.mins[2] + 1;
            contents = GameBase.gameExports.gameImports.getPointContents(test);

            if ((contents & Defines.MASK_WATER) != 0)
                return false;
        }

        if (trace.fraction == 1) {
            // if monster had the ground pulled out, go ahead and fall
            if ((ent.flags & GameDefines.FL_PARTIALGROUND) != 0) {
                Math3D.VectorAdd(ent.s.origin, move, ent.s.origin);
                if (relink) {
                    GameBase.gameExports.gameImports.linkentity(ent);
                    GameBase.G_TouchTriggers(ent);
                }
                ent.groundentity = null;
                return true;
            }

            return false; // walked off an edge
        }

        //	   check point traces down for dangling corners
        Math3D.VectorCopy(trace.endpos, ent.s.origin);

        if (!M.M_CheckBottom(ent)) {
            if ((ent.flags & GameDefines.FL_PARTIALGROUND) != 0) {
                // entity had floor mostly pulled out from underneath it
                // and is trying to correct
                if (relink) {
                    GameBase.gameExports.gameImports.linkentity(ent);
                    GameBase.G_TouchTriggers(ent);
                }
                return true;
            }
            Math3D.VectorCopy(oldorg, ent.s.origin);
            return false;
        }

        if ((ent.flags & GameDefines.FL_PARTIALGROUND) != 0) {
            ent.flags &= ~GameDefines.FL_PARTIALGROUND;
        }
        ent.groundentity = trace.ent;
        ent.groundentity_linkcount = trace.ent.linkcount;

        //	   the move is ok
        if (relink) {
            GameBase.gameExports.gameImports.linkentity(ent);
            GameBase.G_TouchTriggers(ent);
        }
        return true;
    }

    /** 
     * Turns to the movement direction, and walks the current distance if facing
     * it.
     */
    static boolean SV_StepDirection(SubgameEntity ent, float yaw, float dist) {
        float[] move = { 0, 0, 0 };
        float[] oldorigin = { 0, 0, 0 };
        float delta;

        ent.ideal_yaw = yaw;
        M.M_ChangeYaw(ent);

        yaw = (float) (yaw * Math.PI * 2 / 360);
        move[0] = (float) Math.cos(yaw) * dist;
        move[1] = (float) Math.sin(yaw) * dist;
        move[2] = 0;

        Math3D.VectorCopy(ent.s.origin, oldorigin);
        if (SV_movestep(ent, move, false)) {
            delta = ent.s.angles[Defines.YAW] - ent.ideal_yaw;
            if (delta > 45 && delta < 315) { // not turned far enough, so don't
                                             // take the step
                Math3D.VectorCopy(oldorigin, ent.s.origin);
            }
            GameBase.gameExports.gameImports.linkentity(ent);
            GameBase.G_TouchTriggers(ent);
            return true;
        }
        GameBase.gameExports.gameImports.linkentity(ent);
        GameBase.G_TouchTriggers(ent);
        return false;
    }

    /**
     * SV_FixCheckBottom
     * 
     */
    private static void SV_FixCheckBottom(SubgameEntity ent) {
        ent.flags |= GameDefines.FL_PARTIALGROUND;
    }

    static void SV_NewChaseDir(SubgameEntity actor, SubgameEntity enemy, float dist) {
        float deltax, deltay;
        float d[] = { 0, 0, 0 };
        float tdir, olddir, turnaround;

        //FIXME: how did we get here with no enemy
        if (enemy == null) {
            Com.DPrintf("SV_NewChaseDir without enemy!\n");
            return;
        }
        olddir = Math3D.anglemod((int) (actor.ideal_yaw / 45) * 45);
        turnaround = Math3D.anglemod(olddir - 180);

        deltax = enemy.s.origin[0] - actor.s.origin[0];
        deltay = enemy.s.origin[1] - actor.s.origin[1];
        int DI_NODIR = -1;
        if (deltax > 10)
            d[1] = 0;
        else if (deltax < -10)
            d[1] = 180;
        else
            d[1] = DI_NODIR;
        if (deltay < -10)
            d[2] = 270;
        else if (deltay > 10)
            d[2] = 90;
        else
            d[2] = DI_NODIR;

        //	   try direct route
        if (d[1] != DI_NODIR && d[2] != DI_NODIR) {
            if (d[1] == 0)
                tdir = d[2] == 90 ? 45 : 315;
            else
                tdir = d[2] == 90 ? 135 : 215;

            if (tdir != turnaround && SV_StepDirection(actor, tdir, dist))
                return;
        }

        //	   try other directions
        if (((Lib.rand() & 3) & 1) != 0 || Math.abs(deltay) > Math.abs(deltax)) {
            tdir = d[1];
            d[1] = d[2];
            d[2] = tdir;
        }

        if (d[1] != DI_NODIR && d[1] != turnaround
                && SV_StepDirection(actor, d[1], dist))
            return;

        if (d[2] != DI_NODIR && d[2] != turnaround
                && SV_StepDirection(actor, d[2], dist))
            return;

        /* there is no direct path to the player, so pick another direction */

        if (olddir != DI_NODIR
                && SV_StepDirection(actor, olddir, dist))
            return;

        if ((Lib.rand() & 1) != 0) /* randomly determine direction of search */{
            for (tdir = 0; tdir <= 315; tdir += 45)
                if (tdir != turnaround && SV_StepDirection(actor, tdir, dist))
                    return;
        } else {
            for (tdir = 315; tdir >= 0; tdir -= 45)
                if (tdir != turnaround && SV_StepDirection(actor, tdir, dist))
                    return;
        }

        if (turnaround != DI_NODIR
                && SV_StepDirection(actor, turnaround, dist))
            return;

        actor.ideal_yaw = olddir; // can't move

        // if a bridge was pulled out from underneath a monster, it may not have
        // a valid standing position at all

        if (!M.M_CheckBottom(actor))
            SV_FixCheckBottom(actor);
    }

    /**
     * SV_CloseEnough - returns true if distance between 2 ents is smaller than
     * given dist.  
     */
    static boolean SV_CloseEnough(SubgameEntity ent, edict_t goal, float dist) {
        int i;

        for (i = 0; i < 3; i++) {
            if (goal.absmin[i] > ent.absmax[i] + dist)
                return false;
            if (goal.absmax[i] < ent.absmin[i] - dist)
                return false;
        }
        return true;
    }

}