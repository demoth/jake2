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

// Created on 30.11.2003 by RST.

// $Id: GameBase.java,v 1.13 2006-01-21 21:53:31 salomo Exp $

/** Father of all GameObjects. */

package jake2.game;

import jake2.qcommon.*;
import jake2.qcommon.util.Lib;
import jake2.qcommon.util.Math3D;

import java.util.StringTokenizer;

public class GameBase {
    @Deprecated
    public static GameExportsImpl gameExports;

    static cplane_t dummyplane = new cplane_t();

    // this is used to store parsed entity fields during map loading
    // todo: pass directly instead of via global static field
    public static spawn_temp_t st = new spawn_temp_t();

    static int meansOfDeath;

    private final static float STOP_EPSILON = 0.1f;

    /**
     * Slide off of the impacting object returns the blocked flags (1 = floor, 2 =
     * step / wall).
     */
    static int ClipVelocity(float[] in, float[] normal, float[] out,
                            float overbounce) {
        float backoff;
        float change;
        int i, blocked;

        blocked = 0;
        if (normal[2] > 0)
            blocked |= 1; // floor
        if (normal[2] == 0.0f)
            blocked |= 2; // step

        backoff = Math3D.DotProduct(in, normal) * overbounce;

        for (i = 0; i < 3; i++) {
            change = normal[i] * backoff;
            out[i] = in[i] - change;
            if (out[i] > -STOP_EPSILON && out[i] < STOP_EPSILON)
                out[i] = 0;
        }

        return blocked;
    }


    /**
     * Searches all active entities for the next one that holds the matching
     * string at fieldofs (use the FOFS() macro) in the structure.
     * 
     * Searches beginning at the edict after from, or the beginning if null null
     * will be returned if the end of the list is reached.
     * 
     */
    static EdictIterator G_Find(EdictIterator from, EdictFindFilter eff,
                                String s) {

        if (from == null)
            from = new EdictIterator(0);
        else
            from.i++;

        for (; from.i < gameExports.num_edicts; from.i++) {
            from.o = gameExports.g_edicts[from.i];
            if (from.o.classname == null) {
                gameExports.gameImports.dprintf("edict with classname = null" + from.o.index);
            }

            if (!from.o.inuse)
                continue;

            if (eff.matches(from.o, s))
                return from;
        }

        return null;
    }

    // comfort version (rst)
    static edict_t G_FindEdict(EdictIterator from, EdictFindFilter eff,
                               String s) {
        EdictIterator ei = G_Find(from, eff, s);
        if (ei == null)
            return null;
        else
            return ei.o;
    }

    /**
     * Returns entities that have origins within a spherical area.
     */
    public static EdictIterator findradius(EdictIterator from, float[] org,
            float rad) {
        float[] eorg = { 0, 0, 0 };
        int j;

        if (from == null)
            from = new EdictIterator(0);
        else
            from.i++;

        for (; from.i < gameExports.num_edicts; from.i++) {
            from.o = gameExports.g_edicts[from.i];
            if (!from.o.inuse)
                continue;

            if (from.o.solid == Defines.SOLID_NOT)
                continue;

            for (j = 0; j < 3; j++)
                eorg[j] = org[j]
                        - (from.o.s.origin[j] + (from.o.mins[j] + from.o.maxs[j]) * 0.5f);

            if (Math3D.VectorLength(eorg) > rad)
                continue;
            return from;
        }

        return null;
    }

    /*
     * Searches all active entities for the next one that holds the matching
     * string at fieldofs (use the FOFS() macro) in the structure.
     *
     * Searches beginning at the edict after from, or the beginning if null null
     * will be returned if the end of the list is reached.
     */

    public static SubgameEntity G_PickTarget(String targetname) {
        int num_choices = 0;
        int MAXCHOICES = 8;
        SubgameEntity[] choice = new SubgameEntity[MAXCHOICES];

        if (targetname == null) {
            gameExports.gameImports.dprintf("G_PickTarget called with null targetname\n");
            return null;
        }

        EdictIterator es = null;

        while ((es = G_Find(es, findByTarget, targetname)) != null) {
            choice[num_choices++] = es.o;
            if (num_choices == MAXCHOICES)
                break;
        }

        if (num_choices == 0) {
            gameExports.gameImports.dprintf("G_PickTarget: target " + targetname + " not found\n");
            return null;
        }

        return choice[Lib.rand() % num_choices];
    }

    private static float[] VEC_UP = { 0, -1, 0 };

    private static float[] MOVEDIR_UP = { 0, 0, 1 };

    private static float[] VEC_DOWN = { 0, -2, 0 };

    private static float[] MOVEDIR_DOWN = { 0, 0, -1 };

    public static void G_SetMovedir(float[] angles, float[] movedir) {
        if (Math3D.VectorEquals(angles, VEC_UP)) {
            Math3D.VectorCopy(MOVEDIR_UP, movedir);
        } else if (Math3D.VectorEquals(angles, VEC_DOWN)) {
            Math3D.VectorCopy(MOVEDIR_DOWN, movedir);
        } else {
            Math3D.AngleVectors(angles, movedir, null, null);
        }

        Math3D.VectorClear(angles);
    }

    // todo: replace with returned collection from gi.BoxEdicts
    private static SubgameEntity touch[] = new SubgameEntity[Defines.MAX_EDICTS];

    static void G_TouchTriggers(SubgameEntity ent) {
        int i, num;
        SubgameEntity hit;

        // dead things don't activate triggers!
        if ((ent.getClient() != null || (ent.svflags & Defines.SVF_MONSTER) != 0)
                && (ent.health <= 0))
            return;

        num = gameExports.gameImports.BoxEdicts(ent.absmin, ent.absmax, touch, Defines.MAX_EDICTS,
                Defines.AREA_TRIGGERS);

        // be careful, it is possible to have an entity in this
        // list removed before we get to it (killtriggered)
        for (i = 0; i < num; i++) {
            hit = touch[i];

            if (!hit.inuse)
                continue;

            if (hit.touch == null)
                continue;

            hit.touch.touch(hit, ent, dummyplane, null);
        }
    }

    static pushed_t pushed[] = new pushed_t[Defines.MAX_EDICTS];
    static {
        for (int n = 0; n < Defines.MAX_EDICTS; n++)
            pushed[n] = new pushed_t();
    }

    static int pushed_p;

    static SubgameEntity obstacle;

    static int c_yes, c_no;

    static int STEPSIZE = 18;


    static EdictFindFilter findByTarget = new EdictFindFilter() {
        public boolean matches(SubgameEntity e, String s) {
            if (e.targetname == null)
                return false;
            return e.targetname.equalsIgnoreCase(s);
        }
    };

    static EdictFindFilter findByClass = new EdictFindFilter() {
        public boolean matches(SubgameEntity e, String s) {
            return e.classname.equalsIgnoreCase(s);
        }
    };



}