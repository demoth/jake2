/*
 * CL_newfx.java
 * Copyright (C) 2004
 * 
 * $Id: CL_newfx.java,v 1.5 2004-10-04 12:50:37 hzi Exp $
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

// Created on 31.01.2004 by RST.
package jake2.client;

import jake2.Defines;
import jake2.Globals;
import jake2.util.Lib;
import jake2.util.Math3D;

/**
 * CL_newfx
 */
public class CL_newfx {

    static void Flashlight(int ent, float[] pos) {
        CL_fx.cdlight_t dl;

        dl = CL_fx.AllocDlight(ent);
        Math3D.VectorCopy(pos, dl.origin);
        dl.radius = 400;
        dl.minlight = 250;
        dl.die = Globals.cl.time + 100;
        dl.color[0] = 1;
        dl.color[1] = 1;
        dl.color[2] = 1;
    }

    /*
     * ====== CL_ColorFlash - flash of light ======
     */
    static void ColorFlash(float[] pos, int ent, int intensity, float r,
            float g, float b) {
        CL_fx.cdlight_t dl;

        if ((Globals.vidref_val == Defines.VIDREF_SOFT)
                && ((r < 0) || (g < 0) || (b < 0))) {
            intensity = -intensity;
            r = -r;
            g = -g;
            b = -b;
        }

        dl = CL_fx.AllocDlight(ent);
        Math3D.VectorCopy(pos, dl.origin);
        dl.radius = intensity;
        dl.minlight = 250;
        dl.die = Globals.cl.time + 100;
        dl.color[0] = r;
        dl.color[1] = g;
        dl.color[2] = b;
    }

    /*
     * ====== CL_DebugTrail ======
     */
    static void DebugTrail(float[] start, float[] end) {
        float[] move = new float[3];
        float[] vec = new float[3];

        float len;
        //		int j;
        cparticle_t p;
        float dec;
        float[] right = new float[3];
        float[] up = new float[3];
        //		int i;
        //		float d, c, s;
        //		float[] dir;

        Math3D.VectorCopy(start, move);
        Math3D.VectorSubtract(end, start, vec);
        len = Math3D.VectorNormalize(vec);

        Math3D.MakeNormalVectors(vec, right, up);

        //		VectorScale(vec, RT2_SKIP, vec);

        //		dec = 1.0;
        //		dec = 0.75;
        dec = 3;
        Math3D.VectorScale(vec, dec, vec);
        Math3D.VectorCopy(start, move);

        while (len > 0) {
            len -= dec;

            if (CL_fx.free_particles == null)
                return;
            p = CL_fx.free_particles;
            CL_fx.free_particles = p.next;
            p.next = CL_fx.active_particles;
            CL_fx.active_particles = p;

            p.time = Globals.cl.time;
            Math3D.VectorClear(p.accel);
            Math3D.VectorClear(p.vel);
            p.alpha = 1.0f;
            p.alphavel = -0.1f;
            //			p.alphavel = 0;
            p.color = 0x74 + (Lib.rand() & 7);
            Math3D.VectorCopy(move, p.org);
            /*
             * for (j=0 ; j <3 ; j++) { p.org[j] = move[j] + crand()*2; p.vel[j] =
             * crand()*3; p.accel[j] = 0; }
             */
            Math3D.VectorAdd(move, vec, move);
        }

    }

    /*
     * =============== CL_SmokeTrail ===============
     */
    static void SmokeTrail(float[] start, float[] end, int colorStart,
            int colorRun, int spacing) {
        float[] move = new float[3];
        float[] vec = new float[3];
        float len;
        int j;
        cparticle_t p;

        Math3D.VectorCopy(start, move);
        Math3D.VectorSubtract(end, start, vec);
        len = Math3D.VectorNormalize(vec);

        Math3D.VectorScale(vec, spacing, vec);

        // FIXME: this is a really silly way to have a loop
        while (len > 0) {
            len -= spacing;

            if (CL_fx.free_particles == null)
                return;
            p = CL_fx.free_particles;
            CL_fx.free_particles = p.next;
            p.next = CL_fx.active_particles;
            CL_fx.active_particles = p;
            Math3D.VectorClear(p.accel);

            p.time = Globals.cl.time;

            p.alpha = 1.0f;
            p.alphavel = -1.0f / (1 + Globals.rnd.nextFloat() * 0.5f);
            p.color = colorStart + (Lib.rand() % colorRun);
            for (j = 0; j < 3; j++) {
                p.org[j] = move[j] + Lib.crand() * 3;
                p.accel[j] = 0;
            }
            p.vel[2] = 20 + Lib.crand() * 5;

            Math3D.VectorAdd(move, vec, move);
        }
    }

    static void ForceWall(float[] start, float[] end, int color) {
        float[] move = new float[3];
        float[] vec = new float[3];
        ;
        float len;
        int j;
        cparticle_t p;

        Math3D.VectorCopy(start, move);
        Math3D.VectorSubtract(end, start, vec);
        len = Math3D.VectorNormalize(vec);

        Math3D.VectorScale(vec, 4, vec);

        // FIXME: this is a really silly way to have a loop
        while (len > 0) {
            len -= 4;

            if (CL_fx.free_particles == null)
                return;

            if (Globals.rnd.nextFloat() > 0.3) {
                p = CL_fx.free_particles;
                CL_fx.free_particles = p.next;
                p.next = CL_fx.active_particles;
                CL_fx.active_particles = p;
                Math3D.VectorClear(p.accel);

                p.time = Globals.cl.time;

                p.alpha = 1.0f;
                p.alphavel = -1.0f / (3.0f + Globals.rnd.nextFloat() * 0.5f);
                p.color = color;
                for (j = 0; j < 3; j++) {
                    p.org[j] = move[j] + Lib.crand() * 3;
                    p.accel[j] = 0;
                }
                p.vel[0] = 0;
                p.vel[1] = 0;
                p.vel[2] = -40 - (Lib.crand() * 10);
            }

            Math3D.VectorAdd(move, vec, move);
        }
    }

    //	static void FlameEffects(centity_t ent, float[] origin) {
    //		int n, count;
    //		int j;
    //		cparticle_t p;
    //
    //		count = rand() & 0xF;
    //
    //		for (n = 0; n < count; n++) {
    //			if (free_particles == null)
    //				return;
    //
    //			p = free_particles;
    //			free_particles = p.next;
    //			p.next = active_particles;
    //			active_particles = p;
    //
    //			VectorClear(p.accel);
    //			p.time = cl.time;
    //
    //			p.alpha = 1.0f;
    //			p.alphavel = -1.0f / (1 + frand() * 0.2f);
    //			p.color = 226 + (rand() % 4);
    //			for (j = 0; j < 3; j++) {
    //				p.org[j] = origin[j] + crand() * 5;
    //				p.vel[j] = crand() * 5;
    //			}
    //			p.vel[2] = crand() * -10;
    //			p.accel[2] = -PARTICLE_GRAVITY;
    //		}
    //
    //		count = rand() & 0x7;
    //
    //		for (n = 0; n < count; n++) {
    //			if (free_particles == null)
    //				return;
    //			p = free_particles;
    //			free_particles = p.next;
    //			p.next = active_particles;
    //			active_particles = p;
    //			VectorClear(p.accel);
    //
    //			p.time = cl.time;
    //
    //			p.alpha = 1.0f;
    //			p.alphavel = -1.0f / (1 + frand() * 0.5f);
    //			p.color = 0 + (rand() % 4);
    //			for (j = 0; j < 3; j++) {
    //				p.org[j] = origin[j] + crand() * 3;
    //			}
    //			p.vel[2] = 20 + crand() * 5;
    //		}
    //
    //	}

    /*
     * =============== CL_GenericParticleEffect ===============
     */
    static void GenericParticleEffect(float[] org, float[] dir, int color,
            int count, int numcolors, int dirspread, float alphavel) {
        int i, j;
        cparticle_t p;
        float d;

        for (i = 0; i < count; i++) {
            if (CL_fx.free_particles == null)
                return;
            p = CL_fx.free_particles;
            CL_fx.free_particles = p.next;
            p.next = CL_fx.active_particles;
            CL_fx.active_particles = p;

            p.time = Globals.cl.time;
            if (numcolors > 1)
                p.color = color + (Lib.rand() & numcolors);
            else
                p.color = color;

            d = Lib.rand() & dirspread;
            for (j = 0; j < 3; j++) {
                p.org[j] = org[j] + ((Lib.rand() & 7) - 4) + d * dir[j];
                p.vel[j] = Lib.crand() * 20;
            }

            p.accel[0] = p.accel[1] = 0;
            p.accel[2] = -CL_fx.PARTICLE_GRAVITY;
            //			VectorCopy (accel, p.accel);
            p.alpha = 1.0f;

            p.alphavel = -1.0f / (0.5f + Globals.rnd.nextFloat() * alphavel);
            //			p.alphavel = alphavel;
        }
    }

    /*
     * =============== CL_BubbleTrail2 (lets you control the # of bubbles by
     * setting the distance between the spawns)
     * 
     * ===============
     */
    static void BubbleTrail2(float[] start, float[] end, int dist) {
        float[] move = new float[3];
        float[] vec = new float[3];
        ;
        float len;
        int i, j;
        cparticle_t p;
        float dec;

        Math3D.VectorCopy(start, move);
        Math3D.VectorSubtract(end, start, vec);
        len = Math3D.VectorNormalize(vec);

        dec = dist;
        Math3D.VectorScale(vec, dec, vec);

        for (i = 0; i < len; i += dec) {
            if (CL_fx.free_particles == null)
                return;

            p = CL_fx.free_particles;
            CL_fx.free_particles = p.next;
            p.next = CL_fx.active_particles;
            CL_fx.active_particles = p;

            Math3D.VectorClear(p.accel);
            p.time = Globals.cl.time;

            p.alpha = 1.0f;
            p.alphavel = -1.0f / (1 + Globals.rnd.nextFloat() * 0.1f);
            p.color = 4 + (Lib.rand() & 7);
            for (j = 0; j < 3; j++) {
                p.org[j] = move[j] + Lib.crand() * 2;
                p.vel[j] = Lib.crand() * 10;
            }
            p.org[2] -= 4;
            //			p.vel[2] += 6;
            p.vel[2] += 20;

            Math3D.VectorAdd(move, vec, move);
        }
    }

    static void Heatbeam(float[] start, float[] forward) {
        float[] move = new float[3];
        float[] vec = new float[3];
        float len;
        int j;
        cparticle_t p;
        float[] right = new float[3];
        float[] up = new float[3];
        int i;
        float c, s;
        float[] dir = new float[3];
        float ltime;
        float step = 32.0f, rstep;
        float start_pt;
        float rot;
        float variance;
        float[] end = new float[3];

        Math3D.VectorMA(start, 4096, forward, end);

        Math3D.VectorCopy(start, move);
        Math3D.VectorSubtract(end, start, vec);
        len = Math3D.VectorNormalize(vec);

        // FIXME - pmm - these might end up using old values?
        //		MakeNormalVectors (vec, right, up);
        Math3D.VectorCopy(Globals.cl.v_right, right);
        Math3D.VectorCopy(Globals.cl.v_up, up);
        if (Globals.vidref_val == Defines.VIDREF_GL) { // GL mode
            Math3D.VectorMA(move, -0.5f, right, move);
            Math3D.VectorMA(move, -0.5f, up, move);
        }
        // otherwise assume SOFT

        ltime = (float) Globals.cl.time / 1000.0f;
        start_pt = ltime * 96.0f % step;
        Math3D.VectorMA(move, start_pt, vec, move);

        Math3D.VectorScale(vec, step, vec);

        //		Com_Printf ("%f\n", ltime);
        rstep = (float) (Math.PI / 10.0);
        float M_PI2 = (float) (Math.PI * 2.0);
        for (i = (int) start_pt; i < len; i += step) {
            if (i > step * 5) // don't bother after the 5th ring
                break;

            for (rot = 0; rot < M_PI2; rot += rstep) {

                if (CL_fx.free_particles == null)
                    return;

                p = CL_fx.free_particles;
                CL_fx.free_particles = p.next;
                p.next = CL_fx.active_particles;
                CL_fx.active_particles = p;

                p.time = Globals.cl.time;
                Math3D.VectorClear(p.accel);
                //				rot+= fmod(ltime, 12.0)*M_PI;
                //				c = cos(rot)/2.0;
                //				s = sin(rot)/2.0;
                //				variance = 0.4 + ((float)rand()/(float)RAND_MAX) *0.2;
                variance = 0.5f;
                c = (float) (Math.cos(rot) * variance);
                s = (float) (Math.sin(rot) * variance);

                // trim it so it looks like it's starting at the origin
                if (i < 10) {
                    Math3D.VectorScale(right, c * (i / 10.0f), dir);
                    Math3D.VectorMA(dir, s * (i / 10.0f), up, dir);
                } else {
                    Math3D.VectorScale(right, c, dir);
                    Math3D.VectorMA(dir, s, up, dir);
                }

                p.alpha = 0.5f;
                //		p.alphavel = -1.0 / (1+frand()*0.2);
                p.alphavel = -1000.0f;
                //		p.color = 0x74 + (rand()&7);
                p.color = 223 - (Lib.rand() & 7);
                for (j = 0; j < 3; j++) {
                    p.org[j] = move[j] + dir[j] * 3;
                    //			p.vel[j] = dir[j]*6;
                    p.vel[j] = 0;
                }
            }
            Math3D.VectorAdd(move, vec, move);
        }
    }

    /*
     * =============== CL_ParticleSteamEffect
     * 
     * Puffs with velocity along direction, with some randomness thrown in
     * ===============
     */
    static void ParticleSteamEffect(float[] org, float[] dir, int color,
            int count, int magnitude) {
        int i, j;
        cparticle_t p;
        float d;
        float[] r = new float[3];
        float[] u = new float[3];

        //		vectoangles2 (dir, angle_dir);
        //		AngleVectors (angle_dir, f, r, u);

        Math3D.MakeNormalVectors(dir, r, u);

        for (i = 0; i < count; i++) {
            if (CL_fx.free_particles == null)
                return;
            p = CL_fx.free_particles;
            CL_fx.free_particles = p.next;
            p.next = CL_fx.active_particles;
            CL_fx.active_particles = p;

            p.time = Globals.cl.time;
            p.color = color + (Lib.rand() & 7);

            for (j = 0; j < 3; j++) {
                p.org[j] = org[j] + magnitude * 0.1f * Lib.crand();
                //				p.vel[j] = dir[j]*magnitude;
            }
            Math3D.VectorScale(dir, magnitude, p.vel);
            d = Lib.crand() * magnitude / 3;
            Math3D.VectorMA(p.vel, d, r, p.vel);
            d = Lib.crand() * magnitude / 3;
            Math3D.VectorMA(p.vel, d, u, p.vel);

            p.accel[0] = p.accel[1] = 0;
            p.accel[2] = -CL_fx.PARTICLE_GRAVITY / 2;
            p.alpha = 1.0f;

            p.alphavel = -1.0f / (0.5f + Globals.rnd.nextFloat() * 0.3f);
        }
    }

    static void ParticleSteamEffect2(cl_sustain_t self)
    //	  float[] org, float[] dir, int color, int count, int magnitude)
    {
        int i, j;
        cparticle_t p;
        float d;
        float[] r = new float[3];
        float[] u = new float[3];
        float[] dir = new float[3];

        //		vectoangles2 (dir, angle_dir);
        //		AngleVectors (angle_dir, f, r, u);

        Math3D.VectorCopy(self.dir, dir);
        Math3D.MakeNormalVectors(dir, r, u);

        for (i = 0; i < self.count; i++) {
            if (CL_fx.free_particles == null)
                return;
            p = CL_fx.free_particles;
            CL_fx.free_particles = p.next;
            p.next = CL_fx.active_particles;
            CL_fx.active_particles = p;

            p.time = Globals.cl.time;
            p.color = self.color + (Lib.rand() & 7);

            for (j = 0; j < 3; j++) {
                p.org[j] = self.org[j] + self.magnitude * 0.1f * Lib.crand();
                //				p.vel[j] = dir[j]*magnitude;
            }
            Math3D.VectorScale(dir, self.magnitude, p.vel);
            d = Lib.crand() * self.magnitude / 3;
            Math3D.VectorMA(p.vel, d, r, p.vel);
            d = Lib.crand() * self.magnitude / 3;
            Math3D.VectorMA(p.vel, d, u, p.vel);

            p.accel[0] = p.accel[1] = 0;
            p.accel[2] = -CL_fx.PARTICLE_GRAVITY / 2;
            p.alpha = 1.0f;

            p.alphavel = -1.0f / (0.5f + Globals.rnd.nextFloat() * 0.3f);
        }
        self.nextthink += self.thinkinterval;
    }

    /*
     * =============== CL_TrackerTrail ===============
     */
    static void TrackerTrail(float[] start, float[] end, int particleColor) {
        float[] move = new float[3];
        float[] vec = new float[3];
        float[] forward = new float[3];
        float[] right = new float[3];
        float[] up = new float[3];
        float[] angle_dir = new float[3];
        float len;
        cparticle_t p;
        int dec;
        float dist;

        Math3D.VectorCopy(start, move);
        Math3D.VectorSubtract(end, start, vec);
        len = Math3D.VectorNormalize(vec);

        Math3D.VectorCopy(vec, forward);
        Math3D.vectoangles(forward, angle_dir);
        Math3D.AngleVectors(angle_dir, forward, right, up);

        dec = 3;
        Math3D.VectorScale(vec, 3, vec);

        // FIXME: this is a really silly way to have a loop
        while (len > 0) {
            len -= dec;

            if (CL_fx.free_particles == null)
                return;
            p = CL_fx.free_particles;
            CL_fx.free_particles = p.next;
            p.next = CL_fx.active_particles;
            CL_fx.active_particles = p;
            Math3D.VectorClear(p.accel);

            p.time = Globals.cl.time;

            p.alpha = 1.0f;
            p.alphavel = -2.0f;
            p.color = particleColor;
            dist = Math3D.DotProduct(move, forward);
            Math3D.VectorMA(move, (float) (8 * Math.cos(dist)), up, p.org);
            for (int j = 0; j < 3; j++) {
                p.vel[j] = 0;
                p.accel[j] = 0;
            }
            p.vel[2] = 5;

            Math3D.VectorAdd(move, vec, move);
        }
    }

    static void Tracker_Shell(float[] origin) {
        float[] dir = new float[3];
        int i;
        cparticle_t p;

        for (i = 0; i < 300; i++) {
            if (CL_fx.free_particles == null)
                return;
            p = CL_fx.free_particles;
            CL_fx.free_particles = p.next;
            p.next = CL_fx.active_particles;
            CL_fx.active_particles = p;
            Math3D.VectorClear(p.accel);

            p.time = Globals.cl.time;

            p.alpha = 1.0f;
            p.alphavel = CL_fx.INSTANT_PARTICLE;
            p.color = 0;

            dir[0] = Lib.crand();
            dir[1] = Lib.crand();
            dir[2] = Lib.crand();
            Math3D.VectorNormalize(dir);

            Math3D.VectorMA(origin, 40, dir, p.org);
        }
    }

    static void MonsterPlasma_Shell(float[] origin) {
        float[] dir = new float[3];
        int i;
        cparticle_t p;

        for (i = 0; i < 40; i++) {
            if (CL_fx.free_particles == null)
                return;
            p = CL_fx.free_particles;
            CL_fx.free_particles = p.next;
            p.next = CL_fx.active_particles;
            CL_fx.active_particles = p;
            Math3D.VectorClear(p.accel);

            p.time = Globals.cl.time;

            p.alpha = 1.0f;
            p.alphavel = CL_fx.INSTANT_PARTICLE;
            p.color = 0xe0;

            dir[0] = Lib.crand();
            dir[1] = Lib.crand();
            dir[2] = Lib.crand();
            Math3D.VectorNormalize(dir);

            Math3D.VectorMA(origin, 10, dir, p.org);
            //			VectorMA(origin, 10*(((rand () & 0x7fff) / ((float)0x7fff))),
            // dir, p.org);
        }
    }

    private static int[] wb_colortable = { 2 * 8, 13 * 8, 21 * 8, 18 * 8 };

    static void Widowbeamout(cl_sustain_t self) {
        float[] dir = new float[3];
        int i;
        cparticle_t p;

        float ratio;

        ratio = 1.0f - (((float) self.endtime - (float) Globals.cl.time) / 2100.0f);

        for (i = 0; i < 300; i++) {
            if (CL_fx.free_particles == null)
                return;
            p = CL_fx.free_particles;
            CL_fx.free_particles = p.next;
            p.next = CL_fx.active_particles;
            CL_fx.active_particles = p;
            Math3D.VectorClear(p.accel);

            p.time = Globals.cl.time;

            p.alpha = 1.0f;
            p.alphavel = CL_fx.INSTANT_PARTICLE;
            p.color = wb_colortable[Lib.rand() & 3];

            dir[0] = Lib.crand();
            dir[1] = Lib.crand();
            dir[2] = Lib.crand();
            Math3D.VectorNormalize(dir);

            Math3D.VectorMA(self.org, (45.0f * ratio), dir, p.org);
            //			VectorMA(origin, 10*(((rand () & 0x7fff) / ((float)0x7fff))),
            // dir, p.org);
        }
    }

    private static int[] nb_colortable = { 110, 112, 114, 116 };

    static void Nukeblast(cl_sustain_t self) {
        float[] dir = new float[3];
        int i;
        cparticle_t p;

        float ratio;

        ratio = 1.0f - (((float) self.endtime - (float) Globals.cl.time) / 1000.0f);

        for (i = 0; i < 700; i++) {
            if (CL_fx.free_particles == null)
                return;
            p = CL_fx.free_particles;
            CL_fx.free_particles = p.next;
            p.next = CL_fx.active_particles;
            CL_fx.active_particles = p;
            Math3D.VectorClear(p.accel);

            p.time = Globals.cl.time;

            p.alpha = 1.0f;
            p.alphavel = CL_fx.INSTANT_PARTICLE;
            p.color = nb_colortable[Lib.rand() & 3];

            dir[0] = Lib.crand();
            dir[1] = Lib.crand();
            dir[2] = Lib.crand();
            Math3D.VectorNormalize(dir);

            Math3D.VectorMA(self.org, (200.0f * ratio), dir, p.org);
            //			VectorMA(origin, 10*(((rand () & 0x7fff) / ((float)0x7fff))),
            // dir, p.org);
        }
    }

    private static int[] ws_colortable = { 2 * 8, 13 * 8, 21 * 8, 18 * 8 };

    static void WidowSplash(float[] org) {
        int i;
        cparticle_t p;
        float[] dir = new float[3];

        for (i = 0; i < 256; i++) {
            if (CL_fx.free_particles == null)
                return;
            p = CL_fx.free_particles;
            CL_fx.free_particles = p.next;
            p.next = CL_fx.active_particles;
            CL_fx.active_particles = p;

            p.time = Globals.cl.time;
            p.color = ws_colortable[Lib.rand() & 3];

            dir[0] = Lib.crand();
            dir[1] = Lib.crand();
            dir[2] = Lib.crand();
            Math3D.VectorNormalize(dir);
            Math3D.VectorMA(org, 45.0f, dir, p.org);
            Math3D.VectorMA(Globals.vec3_origin, 40.0f, dir, p.vel);

            p.accel[0] = p.accel[1] = 0;
            p.alpha = 1.0f;

            p.alphavel = -0.8f / (0.5f + Globals.rnd.nextFloat() * 0.3f);
        }

    }

    static void Tracker_Explode(float[] origin) {
        float[] dir = new float[3];
        float[] backdir = new float[3];
        int i;
        cparticle_t p;

        for (i = 0; i < 300; i++) {
            if (CL_fx.free_particles == null)
                return;
            p = CL_fx.free_particles;
            CL_fx.free_particles = p.next;
            p.next = CL_fx.active_particles;
            CL_fx.active_particles = p;
            Math3D.VectorClear(p.accel);

            p.time = Globals.cl.time;

            p.alpha = 1.0f;
            p.alphavel = -1.0f;
            p.color = 0;

            dir[0] = Lib.crand();
            dir[1] = Lib.crand();
            dir[2] = Lib.crand();
            Math3D.VectorNormalize(dir);
            Math3D.VectorScale(dir, -1, backdir);

            Math3D.VectorMA(origin, 64, dir, p.org);
            Math3D.VectorScale(backdir, 64, p.vel);
        }

    }

    /*
     * =============== CL_TagTrail
     * 
     * ===============
     */
    static void TagTrail(float[] start, float[] end, float color) {
        float[] move = new float[3];
        float[] vec = new float[3];
        float len;
        int j;
        cparticle_t p;
        int dec;

        Math3D.VectorCopy(start, move);
        Math3D.VectorSubtract(end, start, vec);
        len = Math3D.VectorNormalize(vec);

        dec = 5;
        Math3D.VectorScale(vec, 5, vec);

        while (len >= 0) {
            len -= dec;

            if (CL_fx.free_particles == null)
                return;
            p = CL_fx.free_particles;
            CL_fx.free_particles = p.next;
            p.next = CL_fx.active_particles;
            CL_fx.active_particles = p;
            Math3D.VectorClear(p.accel);

            p.time = Globals.cl.time;

            p.alpha = 1.0f;
            p.alphavel = -1.0f / (0.8f + Globals.rnd.nextFloat() * 0.2f);
            p.color = color;
            for (j = 0; j < 3; j++) {
                p.org[j] = move[j] + Lib.crand() * 16;
                p.vel[j] = Lib.crand() * 5;
                p.accel[j] = 0;
            }

            Math3D.VectorAdd(move, vec, move);
        }
    }

    /*
     * =============== CL_ColorExplosionParticles ===============
     */
    static void ColorExplosionParticles(float[] org, int color, int run) {
        int i, j;
        cparticle_t p;

        for (i = 0; i < 128; i++) {
            if (CL_fx.free_particles == null)
                return;
            p = CL_fx.free_particles;
            CL_fx.free_particles = p.next;
            p.next = CL_fx.active_particles;
            CL_fx.active_particles = p;

            p.time = Globals.cl.time;
            p.color = color + (Lib.rand() % run);

            for (j = 0; j < 3; j++) {
                p.org[j] = org[j] + ((Lib.rand() % 32) - 16);
                p.vel[j] = (Lib.rand() % 256) - 128;
            }

            p.accel[0] = p.accel[1] = 0;
            p.accel[2] = -CL_fx.PARTICLE_GRAVITY;
            p.alpha = 1.0f;

            p.alphavel = -0.4f / (0.6f + Globals.rnd.nextFloat() * 0.2f);
        }
    }

    /*
     * =============== CL_ParticleSmokeEffect - like the steam effect, but
     * unaffected by gravity ===============
     */
    static void ParticleSmokeEffect(float[] org, float[] dir, int color,
            int count, int magnitude) {
        int i, j;
        cparticle_t p;
        float d;
        float[] r = new float[3];
        float[] u = new float[3];

        Math3D.MakeNormalVectors(dir, r, u);

        for (i = 0; i < count; i++) {
            if (CL_fx.free_particles == null)
                return;
            p = CL_fx.free_particles;
            CL_fx.free_particles = p.next;
            p.next = CL_fx.active_particles;
            CL_fx.active_particles = p;

            p.time = Globals.cl.time;
            p.color = color + (Lib.rand() & 7);

            for (j = 0; j < 3; j++) {
                p.org[j] = org[j] + magnitude * 0.1f * Lib.crand();
                //				p.vel[j] = dir[j]*magnitude;
            }
            Math3D.VectorScale(dir, magnitude, p.vel);
            d = Lib.crand() * magnitude / 3;
            Math3D.VectorMA(p.vel, d, r, p.vel);
            d = Lib.crand() * magnitude / 3;
            Math3D.VectorMA(p.vel, d, u, p.vel);

            p.accel[0] = p.accel[1] = p.accel[2] = 0;
            p.alpha = 1.0f;

            p.alphavel = -1.0f / (0.5f + Globals.rnd.nextFloat() * 0.3f);
        }
    }

    /*
     * =============== CL_BlasterParticles2
     * 
     * Wall impact puffs (Green) ===============
     */
    static void BlasterParticles2(float[] org, float[] dir, long color) {
        int i, j;
        cparticle_t p;
        float d;
        int count;

        count = 40;
        for (i = 0; i < count; i++) {
            if (CL_fx.free_particles == null)
                return;
            p = CL_fx.free_particles;
            CL_fx.free_particles = p.next;
            p.next = CL_fx.active_particles;
            CL_fx.active_particles = p;

            p.time = Globals.cl.time;
            p.color = color + (Lib.rand() & 7);

            d = Lib.rand() & 15;
            for (j = 0; j < 3; j++) {
                p.org[j] = org[j] + ((Lib.rand() & 7) - 4) + d * dir[j];
                p.vel[j] = dir[j] * 30 + Lib.crand() * 40;
            }

            p.accel[0] = p.accel[1] = 0;
            p.accel[2] = -CL_fx.PARTICLE_GRAVITY;
            p.alpha = 1.0f;

            p.alphavel = -1.0f / (0.5f + Globals.rnd.nextFloat() * 0.3f);
        }
    }

    /*
     * =============== CL_BlasterTrail2
     * 
     * Green! ===============
     */
    static void BlasterTrail2(float[] start, float[] end) {
        float[] move = new float[3];
        float[] vec = new float[3];
        float len;
        int j;
        cparticle_t p;
        int dec;

        Math3D.VectorCopy(start, move);
        Math3D.VectorSubtract(end, start, vec);
        len = Math3D.VectorNormalize(vec);

        dec = 5;
        Math3D.VectorScale(vec, 5, vec);

        // FIXME: this is a really silly way to have a loop
        while (len > 0) {
            len -= dec;

            if (CL_fx.free_particles == null)
                return;
            p = CL_fx.free_particles;
            CL_fx.free_particles = p.next;
            p.next = CL_fx.active_particles;
            CL_fx.active_particles = p;
            Math3D.VectorClear(p.accel);

            p.time = Globals.cl.time;

            p.alpha = 1.0f;
            p.alphavel = -1.0f / (0.3f + Globals.rnd.nextFloat() * 0.2f);
            p.color = 0xd0;
            for (j = 0; j < 3; j++) {
                p.org[j] = move[j] + Lib.crand();
                p.vel[j] = Lib.crand() * 5;
                p.accel[j] = 0;
            }

            Math3D.VectorAdd(move, vec, move);
        }
    }
}