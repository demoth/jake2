/*
 * java
 * Copyright (C) 2004
 * 
 * $Id: CL_tent.java,v 1.7 2004-10-11 14:04:16 hzi Exp $
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
package jake2.client;

import jake2.Defines;
import jake2.Globals;
import jake2.game.player_state_t;
import jake2.qcommon.Com;
import jake2.qcommon.MSG;
import jake2.render.model_t;
import jake2.sound.S;
import jake2.sound.sfx_t;
import jake2.util.Lib;
import jake2.util.Math3D;

/**
 * CL_tent
 */
public class CL_tent {

    static class explosion_t {
        int type;

        entity_t ent = new entity_t();

        int frames;

        float light;

        float[] lightcolor = new float[3];

        float start;

        int baseframe;

        void clear() {
            lightcolor[0] = lightcolor[1] = lightcolor[2] = light = start = type = frames = baseframe = 0;
            ent = new entity_t();
        }
    }

    static final int MAX_EXPLOSIONS = 32;

    static explosion_t[] cl_explosions = new explosion_t[MAX_EXPLOSIONS];

    static final int MAX_BEAMS = 32;

    static beam_t[] cl_beams = new beam_t[MAX_BEAMS];

    //	  PMM - added this for player-linked beams. Currently only used by the
    // plasma beam
    static beam_t[] cl_playerbeams = new beam_t[MAX_BEAMS];

    static final int MAX_LASERS = 32;

    static laser_t[] cl_lasers = new laser_t[MAX_LASERS];

    //	  ROGUE
    static final int MAX_SUSTAINS = 32;

    static cl_sustain_t[] cl_sustains = new cl_sustain_t[MAX_SUSTAINS];

    static class beam_t {
        int entity;

        int dest_entity;

        model_t model;

        int endtime;

        float[] offset = new float[3];

        float[] start = new float[3];

        float[] end = new float[3];

        void clear() {
            offset[0] = offset[1] = offset[2] = start[0] = start[1] = start[2] = end[0] = end[1] = end[2] = entity = dest_entity = endtime = 0;
            model = null;
        }
    }

    static {
        for (int i = 0; i < cl_explosions.length; i++)
            cl_explosions[i] = new explosion_t();
    }
    static {
        for (int i = 0; i < cl_beams.length; i++)
            cl_beams[i] = new beam_t();
        for (int i = 0; i < cl_playerbeams.length; i++)
            cl_playerbeams[i] = new beam_t();
    }

    static class laser_t {
        entity_t ent = new entity_t();

        int endtime;

        void clear() {
            endtime = 0;
            ent = new entity_t();
        }
    }

    static {
        for (int i = 0; i < cl_lasers.length; i++)
            cl_lasers[i] = new laser_t();
    }

    static {
        for (int i = 0; i < cl_sustains.length; i++)
            cl_sustains[i] = new cl_sustain_t();
    }

    static final int ex_free = 0;

    static final int ex_explosion = 1;

    static final int ex_misc = 2;

    static final int ex_flash = 3;

    static final int ex_mflash = 4;

    static final int ex_poly = 5;

    static final int ex_poly2 = 6;

    //	  ROGUE

    // all are references;
    static sfx_t cl_sfx_ric1;

    static sfx_t cl_sfx_ric2;

    static sfx_t cl_sfx_ric3;

    static sfx_t cl_sfx_lashit;

    static sfx_t cl_sfx_spark5;

    static sfx_t cl_sfx_spark6;

    static sfx_t cl_sfx_spark7;

    static sfx_t cl_sfx_railg;

    static sfx_t cl_sfx_rockexp;

    static sfx_t cl_sfx_grenexp;

    static sfx_t cl_sfx_watrexp;

    // RAFAEL
    static sfx_t cl_sfx_plasexp;

    static sfx_t cl_sfx_footsteps[] = new sfx_t[4];

    static model_t cl_mod_explode;

    static model_t cl_mod_smoke;

    static model_t cl_mod_flash;

    static model_t cl_mod_parasite_segment;

    static model_t cl_mod_grapple_cable;

    static model_t cl_mod_parasite_tip;

    static model_t cl_mod_explo4;

    static model_t cl_mod_bfg_explo;

    static model_t cl_mod_powerscreen;

    //	   RAFAEL
    static model_t cl_mod_plasmaexplo;

    //	  ROGUE
    static sfx_t cl_sfx_lightning;

    static sfx_t cl_sfx_disrexp;

    static model_t cl_mod_lightning;

    static model_t cl_mod_heatbeam;

    static model_t cl_mod_monster_heatbeam;

    static model_t cl_mod_explo4_big;

    //	  ROGUE
    /*
     * ================= CL_RegisterTEntSounds =================
     */
    static void RegisterTEntSounds() {
        int i;
        String name;

        // PMM - version stuff
        //		Com_Printf ("%s\n", ROGUE_VERSION_STRING);
        // PMM
        cl_sfx_ric1 = S.RegisterSound("world/ric1.wav");
        cl_sfx_ric2 = S.RegisterSound("world/ric2.wav");
        cl_sfx_ric3 = S.RegisterSound("world/ric3.wav");
        cl_sfx_lashit = S.RegisterSound("weapons/lashit.wav");
        cl_sfx_spark5 = S.RegisterSound("world/spark5.wav");
        cl_sfx_spark6 = S.RegisterSound("world/spark6.wav");
        cl_sfx_spark7 = S.RegisterSound("world/spark7.wav");
        cl_sfx_railg = S.RegisterSound("weapons/railgf1a.wav");
        cl_sfx_rockexp = S.RegisterSound("weapons/rocklx1a.wav");
        cl_sfx_grenexp = S.RegisterSound("weapons/grenlx1a.wav");
        cl_sfx_watrexp = S.RegisterSound("weapons/xpld_wat.wav");
        // RAFAEL
        // cl_sfx_plasexp = S.RegisterSound ("weapons/plasexpl.wav");
        S.RegisterSound("player/land1.wav");

        S.RegisterSound("player/fall2.wav");
        S.RegisterSound("player/fall1.wav");

        for (i = 0; i < 4; i++) {
            //Com_sprintf (name, sizeof(name), "player/step%i.wav", i+1);
            name = "player/step" + (i + 1) + ".wav";
            cl_sfx_footsteps[i] = S.RegisterSound(name);
        }

        //	  PGM
        cl_sfx_lightning = S.RegisterSound("weapons/tesla.wav");
        cl_sfx_disrexp = S.RegisterSound("weapons/disrupthit.wav");
        // version stuff
        //		sprintf (name, "weapons/sound%d.wav", ROGUE_VERSION_ID);
        //		if (name[0] == 'w')
        //			name[0] = 'W';
        //	  PGM
    }

    /*
     * ================= CL_RegisterTEntModels =================
     */
    static void RegisterTEntModels() {
        cl_mod_explode = Globals.re
                .RegisterModel("models/objects/explode/tris.md2");
        cl_mod_smoke = Globals.re
                .RegisterModel("models/objects/smoke/tris.md2");
        cl_mod_flash = Globals.re
                .RegisterModel("models/objects/flash/tris.md2");
        cl_mod_parasite_segment = Globals.re
                .RegisterModel("models/monsters/parasite/segment/tris.md2");
        cl_mod_grapple_cable = Globals.re
                .RegisterModel("models/ctf/segment/tris.md2");
        cl_mod_parasite_tip = Globals.re
                .RegisterModel("models/monsters/parasite/tip/tris.md2");
        cl_mod_explo4 = Globals.re
                .RegisterModel("models/objects/r_explode/tris.md2");
        cl_mod_bfg_explo = Globals.re.RegisterModel("sprites/s_bfg2.sp2");
        cl_mod_powerscreen = Globals.re
                .RegisterModel("models/items/armor/effect/tris.md2");

        Globals.re.RegisterModel("models/objects/laser/tris.md2");
        Globals.re.RegisterModel("models/objects/grenade2/tris.md2");
        Globals.re.RegisterModel("models/weapons/v_machn/tris.md2");
        Globals.re.RegisterModel("models/weapons/v_handgr/tris.md2");
        Globals.re.RegisterModel("models/weapons/v_shotg2/tris.md2");
        Globals.re.RegisterModel("models/objects/gibs/bone/tris.md2");
        Globals.re.RegisterModel("models/objects/gibs/sm_meat/tris.md2");
        Globals.re.RegisterModel("models/objects/gibs/bone2/tris.md2");
        //	   RAFAEL
        //	   re.RegisterModel ("models/objects/blaser/tris.md2");

        Globals.re.RegisterPic("w_machinegun");
        Globals.re.RegisterPic("a_bullets");
        Globals.re.RegisterPic("i_health");
        Globals.re.RegisterPic("a_grenades");

        //	  ROGUE
        cl_mod_explo4_big = Globals.re
                .RegisterModel("models/objects/r_explode2/tris.md2");
        cl_mod_lightning = Globals.re
                .RegisterModel("models/proj/lightning/tris.md2");
        cl_mod_heatbeam = Globals.re.RegisterModel("models/proj/beam/tris.md2");
        cl_mod_monster_heatbeam = Globals.re
                .RegisterModel("models/proj/widowbeam/tris.md2");
        //	  ROGUE
    }

    /*
     * ================= CL_ClearTEnts =================
     */
    static void ClearTEnts() {
        //		memset (cl_beams, 0, sizeof(cl_beams));
        for (int i = 0; i < cl_beams.length; i++)
            cl_beams[i].clear();
        //		memset (cl_explosions, 0, sizeof(cl_explosions));
        for (int i = 0; i < cl_explosions.length; i++)
            cl_explosions[i].clear();
        //		memset (cl_lasers, 0, sizeof(cl_lasers));
        for (int i = 0; i < cl_lasers.length; i++)
            cl_lasers[i].clear();
        //
        //	  ROGUE
        //		memset (cl_playerbeams, 0, sizeof(cl_playerbeams));
        for (int i = 0; i < cl_playerbeams.length; i++)
            cl_playerbeams[i].clear();
        //		memset (cl_sustains, 0, sizeof(cl_sustains));
        for (int i = 0; i < cl_sustains.length; i++)
            cl_sustains[i].clear();
        //	  ROGUE
    }

    /*
     * ================= CL_AllocExplosion =================
     */
    static explosion_t AllocExplosion() {
        int i;
        int time;
        int index;

        for (i = 0; i < MAX_EXPLOSIONS; i++) {
            if (cl_explosions[i].type == ex_free) {
                //memset (&cl_explosions[i], 0, sizeof (cl_explosions[i]));
                cl_explosions[i].clear();
                return cl_explosions[i];
            }
        }
        //	   find the oldest explosion
        time = Globals.cl.time;
        index = 0;

        for (i = 0; i < MAX_EXPLOSIONS; i++)
            if (cl_explosions[i].start < time) {
                time = (int) cl_explosions[i].start;
                index = i;
            }
        //memset (&cl_explosions[index], 0, sizeof (cl_explosions[index]));
        cl_explosions[index].clear();
        return cl_explosions[index];
    }

    /*
     * ================= CL_SmokeAndFlash =================
     */
    static void SmokeAndFlash(float[] origin) {
        explosion_t ex;

        ex = AllocExplosion();
        Math3D.VectorCopy(origin, ex.ent.origin);
        ex.type = ex_misc;
        ex.frames = 4;
        ex.ent.flags = Defines.RF_TRANSLUCENT;
        ex.start = Globals.cl.frame.servertime - 100;
        ex.ent.model = cl_mod_smoke;

        ex = AllocExplosion();
        Math3D.VectorCopy(origin, ex.ent.origin);
        ex.type = ex_flash;
        ex.ent.flags = Defines.RF_FULLBRIGHT;
        ex.frames = 2;
        ex.start = Globals.cl.frame.servertime - 100;
        ex.ent.model = cl_mod_flash;
    }

    /*
     * =================
     * CL_ParseBeam
     * =================
     */
    static int ParseBeam(model_t model) {
        int ent;
        float[] start = new float[3];
        float[] end = new float[3];
        beam_t[] b;
        int i;

        ent = MSG.ReadShort(Globals.net_message);

        MSG.ReadPos(Globals.net_message, start);
        MSG.ReadPos(Globals.net_message, end);

        //	   override any beam with the same entity
        b = cl_beams;
        for (i = 0; i < MAX_BEAMS; i++)
            if (b[i].entity == ent) {
                b[i].entity = ent;
                b[i].model = model;
                b[i].endtime = Globals.cl.time + 200;
                Math3D.VectorCopy(start, b[i].start);
                Math3D.VectorCopy(end, b[i].end);
                Math3D.VectorClear(b[i].offset);
                return ent;
            }

        //	   find a free beam
        b = cl_beams;
        for (i = 0; i < MAX_BEAMS; i++) {
            if (b[i].model == null || b[i].endtime < Globals.cl.time) {
                b[i].entity = ent;
                b[i].model = model;
                b[i].endtime = Globals.cl.time + 200;
                Math3D.VectorCopy(start, b[i].start);
                Math3D.VectorCopy(end, b[i].end);
                Math3D.VectorClear(b[i].offset);
                return ent;
            }
        }
        Com.Printf("beam list overflow!\n");
        return ent;
    }

    /*
     * ================= CL_ParseBeam2 =================
     */
    static int ParseBeam2(model_t model) {
        int ent;
        float[] start = new float[3];
        float[] end = new float[3];
        float[] offset = new float[3];
        beam_t[] b;
        int i;

        ent = MSG.ReadShort(Globals.net_message);

        MSG.ReadPos(Globals.net_message, start);
        MSG.ReadPos(Globals.net_message, end);
        MSG.ReadPos(Globals.net_message, offset);

        //		Com_Printf ("end- %f %f %f\n", end[0], end[1], end[2]);

        //	   override any beam with the same entity
        b = cl_beams;
        for (i = 0; i < MAX_BEAMS; i++)
            if (b[i].entity == ent) {
                b[i].entity = ent;
                b[i].model = model;
                b[i].endtime = Globals.cl.time + 200;
                Math3D.VectorCopy(start, b[i].start);
                Math3D.VectorCopy(end, b[i].end);
                Math3D.VectorCopy(offset, b[i].offset);
                return ent;
            }

        //	   find a free beam
        b = cl_beams;
        for (i = 0; i < MAX_BEAMS; i++) {
            if (b[i].model == null || b[i].endtime < Globals.cl.time) {
                b[i].entity = ent;
                b[i].model = model;
                b[i].endtime = Globals.cl.time + 200;
                Math3D.VectorCopy(start, b[i].start);
                Math3D.VectorCopy(end, b[i].end);
                Math3D.VectorCopy(offset, b[i].offset);
                return ent;
            }
        }
        Com.Printf("beam list overflow!\n");
        return ent;
    }

    //	   ROGUE
    /*
     * ================= CL_ParsePlayerBeam - adds to the cl_playerbeam array
     * instead of the cl_beams array =================
     */
    static int ParsePlayerBeam(model_t model) {
        int ent;
        float[] start = new float[3];
        float[] end = new float[3];
        float[] offset = new float[3];
        beam_t[] b;
        int i;

        ent = MSG.ReadShort(Globals.net_message);

        MSG.ReadPos(Globals.net_message, start);
        MSG.ReadPos(Globals.net_message, end);
        // PMM - network optimization
        if (model == cl_mod_heatbeam)
            Math3D.VectorSet(offset, 2, 7, -3);
        else if (model == cl_mod_monster_heatbeam) {
            model = cl_mod_heatbeam;
            Math3D.VectorSet(offset, 0, 0, 0);
        } else
            MSG.ReadPos(Globals.net_message, offset);

        //		Com_Printf ("end- %f %f %f\n", end[0], end[1], end[2]);

        //	   override any beam with the same entity
        //	   PMM - For player beams, we only want one per player (entity) so..
        b = cl_playerbeams;
        for (i = 0; i < MAX_BEAMS; i++) {
            if (b[i].entity == ent) {
                b[i].entity = ent;
                b[i].model = model;
                b[i].endtime = Globals.cl.time + 200;
                Math3D.VectorCopy(start, b[i].start);
                Math3D.VectorCopy(end, b[i].end);
                Math3D.VectorCopy(offset, b[i].offset);
                return ent;
            }
        }

        //	   find a free beam
        b = cl_playerbeams;
        for (i = 0; i < MAX_BEAMS; i++) {
            if (b[i].model == null || b[i].endtime < Globals.cl.time) {
                b[i].entity = ent;
                b[i].model = model;
                b[i].endtime = Globals.cl.time + 100; // PMM - this needs to be
                                                      // 100 to prevent multiple
                                                      // heatbeams
                Math3D.VectorCopy(start, b[i].start);
                Math3D.VectorCopy(end, b[i].end);
                Math3D.VectorCopy(offset, b[i].offset);
                return ent;
            }
        }
        Com.Printf("beam list overflow!\n");
        return ent;
    }

    //	  rogue

    /*
     * ================= CL_ParseLightning =================
     */
    static int ParseLightning(model_t model) {
        int srcEnt, destEnt;
        float[] start = new float[3];
        float[] end = new float[3];
        beam_t[] b;
        int i;

        srcEnt = MSG.ReadShort(Globals.net_message);
        destEnt = MSG.ReadShort(Globals.net_message);

        MSG.ReadPos(Globals.net_message, start);
        MSG.ReadPos(Globals.net_message, end);

        //	   override any beam with the same source AND destination entities
        b = cl_beams;
        for (i = 0; i < MAX_BEAMS; i++)
            if (b[i].entity == srcEnt && b[i].dest_entity == destEnt) {
                //				Com_Printf("%d: OVERRIDE %d . %d\n", cl.time, srcEnt,
                // destEnt);
                b[i].entity = srcEnt;
                b[i].dest_entity = destEnt;
                b[i].model = model;
                b[i].endtime = Globals.cl.time + 200;
                Math3D.VectorCopy(start, b[i].start);
                Math3D.VectorCopy(end, b[i].end);
                Math3D.VectorClear(b[i].offset);
                return srcEnt;
            }

        //	   find a free beam
        b = cl_beams;
        for (i = 0; i < MAX_BEAMS; i++) {
            if (b[i].model == null || b[i].endtime < Globals.cl.time) {
                //				Com_Printf("%d: NORMAL %d . %d\n", cl.time, srcEnt, destEnt);
                b[i].entity = srcEnt;
                b[i].dest_entity = destEnt;
                b[i].model = model;
                b[i].endtime = Globals.cl.time + 200;
                Math3D.VectorCopy(start, b[i].start);
                Math3D.VectorCopy(end, b[i].end);
                Math3D.VectorClear(b[i].offset);
                return srcEnt;
            }
        }
        Com.Printf("beam list overflow!\n");
        return srcEnt;
    }

    /*
     * ================= CL_ParseLaser =================
     */
    static void ParseLaser(int colors) {
        float[] start = new float[3];
        float[] end = new float[3];
        laser_t[] l;
        int i;

        MSG.ReadPos(Globals.net_message, start);
        MSG.ReadPos(Globals.net_message, end);

        l = cl_lasers;
        for (i = 0; i < MAX_LASERS; i++) {
            if (l[i].endtime < Globals.cl.time) {
                l[i].ent.flags = Defines.RF_TRANSLUCENT | Defines.RF_BEAM;
                Math3D.VectorCopy(start, l[i].ent.origin);
                Math3D.VectorCopy(end, l[i].ent.oldorigin);
                l[i].ent.alpha = 0.30f;
                l[i].ent.skinnum = (colors >> ((Lib.rand() % 4) * 8)) & 0xff;
                l[i].ent.model = null;
                l[i].ent.frame = 4;
                l[i].endtime = Globals.cl.time + 100;
                return;
            }
        }
    }

    //	  =============
    //	  ROGUE
    static void ParseSteam() {
        float[] pos = new float[3];
        float[] dir = new float[3];
        int id, i;
        int r;
        int cnt;
        int color;
        int magnitude;
        cl_sustain_t[] s;
        cl_sustain_t free_sustain;

        id = MSG.ReadShort(Globals.net_message); // an id of -1 is an instant
                                                 // effect
        if (id != -1) // sustains
        {
            //				Com_Printf ("Sustain effect id %d\n", id);
            free_sustain = null;
            s = cl_sustains;
            for (i = 0; i < MAX_SUSTAINS; i++) {
                if (s[i].id == 0) {
                    free_sustain = s[i];
                    break;
                }
            }
            if (free_sustain != null) {
                s[i].id = id;
                s[i].count = MSG.ReadByte(Globals.net_message);
                MSG.ReadPos(Globals.net_message, s[i].org);
                MSG.ReadDir(Globals.net_message, s[i].dir);
                r = MSG.ReadByte(Globals.net_message);
                s[i].color = r & 0xff;
                s[i].magnitude = MSG.ReadShort(Globals.net_message);
                s[i].endtime = Globals.cl.time
                        + MSG.ReadLong(Globals.net_message);
                s[i].think = new cl_sustain_t.ThinkAdapter() {
                    void think(cl_sustain_t self) {
                        CL_newfx.ParticleSteamEffect2(self);
                    }
                };
                s[i].thinkinterval = 100;
                s[i].nextthink = Globals.cl.time;
            } else {
                //					Com_Printf ("No free sustains!\n");
                // FIXME - read the stuff anyway
                cnt = MSG.ReadByte(Globals.net_message);
                MSG.ReadPos(Globals.net_message, pos);
                MSG.ReadDir(Globals.net_message, dir);
                r = MSG.ReadByte(Globals.net_message);
                magnitude = MSG.ReadShort(Globals.net_message);
                magnitude = MSG.ReadLong(Globals.net_message); // really
                                                               // interval
            }
        } else // instant
        {
            cnt = MSG.ReadByte(Globals.net_message);
            MSG.ReadPos(Globals.net_message, pos);
            MSG.ReadDir(Globals.net_message, dir);
            r = MSG.ReadByte(Globals.net_message);
            magnitude = MSG.ReadShort(Globals.net_message);
            color = r & 0xff;
            CL_newfx.ParticleSteamEffect(pos, dir, color, cnt, magnitude);
            //			S_StartSound (pos, 0, 0, cl_sfx_lashit, 1, ATTN_NORM, 0);
        }
    }

    static void ParseWidow() {
        float[] pos = new float[3];
        int id, i;
        cl_sustain_t[] s;
        cl_sustain_t free_sustain;

        id = MSG.ReadShort(Globals.net_message);

        free_sustain = null;
        s = cl_sustains;
        for (i = 0; i < MAX_SUSTAINS; i++) {
            if (s[i].id == 0) {
                free_sustain = s[i];
                break;
            }
        }
        if (free_sustain != null) {
            s[i].id = id;
            MSG.ReadPos(Globals.net_message, s[i].org);
            s[i].endtime = Globals.cl.time + 2100;
            s[i].think = new cl_sustain_t.ThinkAdapter() {
                void think(cl_sustain_t self) {
                    CL_newfx.Widowbeamout(self);
                }
            };
            s[i].thinkinterval = 1;
            s[i].nextthink = Globals.cl.time;
        } else // no free sustains
        {
            // FIXME - read the stuff anyway
            MSG.ReadPos(Globals.net_message, pos);
        }
    }

    static void ParseNuke() {
        float[] pos = new float[3];
        int i;
        cl_sustain_t[] s;
        cl_sustain_t free_sustain;

        free_sustain = null;
        s = cl_sustains;
        for (i = 0; i < MAX_SUSTAINS; i++) {
            if (s[i].id == 0) {
                free_sustain = s[i];
                break;
            }
        }
        if (free_sustain != null) {
            s[i].id = 21000;
            MSG.ReadPos(Globals.net_message, s[i].org);
            s[i].endtime = Globals.cl.time + 1000;
            s[i].think = new cl_sustain_t.ThinkAdapter() {
                void think(cl_sustain_t self) {
                    CL_newfx.Nukeblast(self);
                }
            };
            s[i].thinkinterval = 1;
            s[i].nextthink = Globals.cl.time;
        } else // no free sustains
        {
            // FIXME - read the stuff anyway
            MSG.ReadPos(Globals.net_message, pos);
        }
    }

    //	  ROGUE
    //	  =============

    /*
     * ================= CL_ParseTEnt =================
     */
    static int[] splash_color = { 0x00, 0xe0, 0xb0, 0x50, 0xd0, 0xe0, 0xe8 };

    static void ParseTEnt() {
        int type;
        float[] pos = new float[3];
        float[] pos2 = new float[3];
        float[] dir = new float[3];
        explosion_t ex;
        int cnt;
        int color;
        int r;
        int ent;
        int magnitude;

        type = MSG.ReadByte(Globals.net_message);

        switch (type) {
        case Defines.TE_BLOOD: // bullet hitting flesh
            MSG.ReadPos(Globals.net_message, pos);
            MSG.ReadDir(Globals.net_message, dir);
            CL_fx.ParticleEffect(pos, dir, 0xe8, 60);
            break;

        case Defines.TE_GUNSHOT: // bullet hitting wall
        case Defines.TE_SPARKS:
        case Defines.TE_BULLET_SPARKS:
            MSG.ReadPos(Globals.net_message, pos);
            MSG.ReadDir(Globals.net_message, dir);
            if (type == Defines.TE_GUNSHOT)
                CL_fx.ParticleEffect(pos, dir, 0, 40);
            else
                CL_fx.ParticleEffect(pos, dir, 0xe0, 6);

            if (type != Defines.TE_SPARKS) {
                SmokeAndFlash(pos);

                // impact sound
                cnt = Lib.rand() & 15;
                if (cnt == 1)
                    S.StartSound(pos, 0, 0, cl_sfx_ric1, 1, Defines.ATTN_NORM,
                            0);
                else if (cnt == 2)
                    S.StartSound(pos, 0, 0, cl_sfx_ric2, 1, Defines.ATTN_NORM,
                            0);
                else if (cnt == 3)
                    S.StartSound(pos, 0, 0, cl_sfx_ric3, 1, Defines.ATTN_NORM,
                            0);
            }

            break;

        case Defines.TE_SCREEN_SPARKS:
        case Defines.TE_SHIELD_SPARKS:
            MSG.ReadPos(Globals.net_message, pos);
            MSG.ReadDir(Globals.net_message, dir);
            if (type == Defines.TE_SCREEN_SPARKS)
                CL_fx.ParticleEffect(pos, dir, 0xd0, 40);
            else
                CL_fx.ParticleEffect(pos, dir, 0xb0, 40);
            //FIXME : replace or remove this sound
            S.StartSound(pos, 0, 0, cl_sfx_lashit, 1, Defines.ATTN_NORM, 0);
            break;

        case Defines.TE_SHOTGUN: // bullet hitting wall
            MSG.ReadPos(Globals.net_message, pos);
            MSG.ReadDir(Globals.net_message, dir);
            CL_fx.ParticleEffect(pos, dir, 0, 20);
            SmokeAndFlash(pos);
            break;

        case Defines.TE_SPLASH: // bullet hitting water
            cnt = MSG.ReadByte(Globals.net_message);
            MSG.ReadPos(Globals.net_message, pos);
            MSG.ReadDir(Globals.net_message, dir);
            r = MSG.ReadByte(Globals.net_message);
            if (r > 6)
                color = 0x00;
            else
                color = splash_color[r];
            CL_fx.ParticleEffect(pos, dir, color, cnt);

            if (r == Defines.SPLASH_SPARKS) {
                r = Lib.rand() & 3;
                if (r == 0)
                    S.StartSound(pos, 0, 0, cl_sfx_spark5, 1,
                            Defines.ATTN_STATIC, 0);
                else if (r == 1)
                    S.StartSound(pos, 0, 0, cl_sfx_spark6, 1,
                            Defines.ATTN_STATIC, 0);
                else
                    S.StartSound(pos, 0, 0, cl_sfx_spark7, 1,
                            Defines.ATTN_STATIC, 0);
            }
            break;

        case Defines.TE_LASER_SPARKS:
            cnt = MSG.ReadByte(Globals.net_message);
            MSG.ReadPos(Globals.net_message, pos);
            MSG.ReadDir(Globals.net_message, dir);
            color = MSG.ReadByte(Globals.net_message);
            CL_fx.ParticleEffect2(pos, dir, color, cnt);
            break;

        // RAFAEL
        case Defines.TE_BLUEHYPERBLASTER:
            MSG.ReadPos(Globals.net_message, pos);
            MSG.ReadPos(Globals.net_message, dir);
            CL_fx.BlasterParticles(pos, dir);
            break;

        case Defines.TE_BLASTER: // blaster hitting wall
            MSG.ReadPos(Globals.net_message, pos);
            MSG.ReadDir(Globals.net_message, dir);
            CL_fx.BlasterParticles(pos, dir);

            ex = AllocExplosion();
            Math3D.VectorCopy(pos, ex.ent.origin);
            ex.ent.angles[0] = (float) (Math.acos(dir[2]) / Math.PI * 180);
            // PMM - fixed to correct for pitch of 0
            if (dir[0] != 0.0f)
                ex.ent.angles[1] = (float) (Math.atan2(dir[1], dir[0])
                        / Math.PI * 180);
            else if (dir[1] > 0)
                ex.ent.angles[1] = 90;
            else if (dir[1] < 0)
                ex.ent.angles[1] = 270;
            else
                ex.ent.angles[1] = 0;

            ex.type = ex_misc;
            ex.ent.flags = Defines.RF_FULLBRIGHT | Defines.RF_TRANSLUCENT;
            ex.start = Globals.cl.frame.servertime - 100;
            ex.light = 150;
            ex.lightcolor[0] = 1;
            ex.lightcolor[1] = 1;
            ex.ent.model = cl_mod_explode;
            ex.frames = 4;
            S.StartSound(pos, 0, 0, cl_sfx_lashit, 1, Defines.ATTN_NORM, 0);
            break;

        case Defines.TE_RAILTRAIL: // railgun effect
            MSG.ReadPos(Globals.net_message, pos);
            MSG.ReadPos(Globals.net_message, pos2);
            CL_fx.RailTrail(pos, pos2);
            S.StartSound(pos2, 0, 0, cl_sfx_railg, 1, Defines.ATTN_NORM, 0);
            break;

        case Defines.TE_EXPLOSION2:
        case Defines.TE_GRENADE_EXPLOSION:
        case Defines.TE_GRENADE_EXPLOSION_WATER:
            MSG.ReadPos(Globals.net_message, pos);

            ex = AllocExplosion();
            Math3D.VectorCopy(pos, ex.ent.origin);
            ex.type = ex_poly;
            ex.ent.flags = Defines.RF_FULLBRIGHT;
            ex.start = Globals.cl.frame.servertime - 100;
            ex.light = 350;
            ex.lightcolor[0] = 1.0f;
            ex.lightcolor[1] = 0.5f;
            ex.lightcolor[2] = 0.5f;
            ex.ent.model = cl_mod_explo4;
            ex.frames = 19;
            ex.baseframe = 30;
            ex.ent.angles[1] = Lib.rand() % 360;
            CL_fx.ExplosionParticles(pos);
            if (type == Defines.TE_GRENADE_EXPLOSION_WATER)
                S
                        .StartSound(pos, 0, 0, cl_sfx_watrexp, 1,
                                Defines.ATTN_NORM, 0);
            else
                S
                        .StartSound(pos, 0, 0, cl_sfx_grenexp, 1,
                                Defines.ATTN_NORM, 0);
            break;

        // RAFAEL
        case Defines.TE_PLASMA_EXPLOSION:
            MSG.ReadPos(Globals.net_message, pos);
            ex = AllocExplosion();
            Math3D.VectorCopy(pos, ex.ent.origin);
            ex.type = ex_poly;
            ex.ent.flags = Defines.RF_FULLBRIGHT;
            ex.start = Globals.cl.frame.servertime - 100;
            ex.light = 350;
            ex.lightcolor[0] = 1.0f;
            ex.lightcolor[1] = 0.5f;
            ex.lightcolor[2] = 0.5f;
            ex.ent.angles[1] = Lib.rand() % 360;
            ex.ent.model = cl_mod_explo4;
            if (Globals.rnd.nextFloat() < 0.5)
                ex.baseframe = 15;
            ex.frames = 15;
            CL_fx.ExplosionParticles(pos);
            S.StartSound(pos, 0, 0, cl_sfx_rockexp, 1, Defines.ATTN_NORM, 0);
            break;

        case Defines.TE_EXPLOSION1:
        case Defines.TE_EXPLOSION1_BIG: // PMM
        case Defines.TE_ROCKET_EXPLOSION:
        case Defines.TE_ROCKET_EXPLOSION_WATER:
        case Defines.TE_EXPLOSION1_NP: // PMM
            MSG.ReadPos(Globals.net_message, pos);

            ex = AllocExplosion();
            Math3D.VectorCopy(pos, ex.ent.origin);
            ex.type = ex_poly;
            ex.ent.flags = Defines.RF_FULLBRIGHT;
            ex.start = Globals.cl.frame.servertime - 100;
            ex.light = 350;
            ex.lightcolor[0] = 1.0f;
            ex.lightcolor[1] = 0.5f;
            ex.lightcolor[2] = 0.5f;
            ex.ent.angles[1] = Lib.rand() % 360;
            if (type != Defines.TE_EXPLOSION1_BIG) // PMM
                ex.ent.model = cl_mod_explo4; // PMM
            else
                ex.ent.model = cl_mod_explo4_big;
            if (Globals.rnd.nextFloat() < 0.5)
                ex.baseframe = 15;
            ex.frames = 15;
            if ((type != Defines.TE_EXPLOSION1_BIG)
                    && (type != Defines.TE_EXPLOSION1_NP)) // PMM
                CL_fx.ExplosionParticles(pos); // PMM
            if (type == Defines.TE_ROCKET_EXPLOSION_WATER)
                S
                        .StartSound(pos, 0, 0, cl_sfx_watrexp, 1,
                                Defines.ATTN_NORM, 0);
            else
                S
                        .StartSound(pos, 0, 0, cl_sfx_rockexp, 1,
                                Defines.ATTN_NORM, 0);
            break;

        case Defines.TE_BFG_EXPLOSION:
            MSG.ReadPos(Globals.net_message, pos);
            ex = AllocExplosion();
            Math3D.VectorCopy(pos, ex.ent.origin);
            ex.type = ex_poly;
            ex.ent.flags = Defines.RF_FULLBRIGHT;
            ex.start = Globals.cl.frame.servertime - 100;
            ex.light = 350;
            ex.lightcolor[0] = 0.0f;
            ex.lightcolor[1] = 1.0f;
            ex.lightcolor[2] = 0.0f;
            ex.ent.model = cl_mod_bfg_explo;
            ex.ent.flags |= Defines.RF_TRANSLUCENT;
            ex.ent.alpha = 0.30f;
            ex.frames = 4;
            break;

        case Defines.TE_BFG_BIGEXPLOSION:
            MSG.ReadPos(Globals.net_message, pos);
            CL_fx.BFGExplosionParticles(pos);
            break;

        case Defines.TE_BFG_LASER:
            ParseLaser(0xd0d1d2d3);
            break;

        case Defines.TE_BUBBLETRAIL:
            MSG.ReadPos(Globals.net_message, pos);
            MSG.ReadPos(Globals.net_message, pos2);
            CL_fx.BubbleTrail(pos, pos2);
            break;

        case Defines.TE_PARASITE_ATTACK:
        case Defines.TE_MEDIC_CABLE_ATTACK:
            ent = ParseBeam(cl_mod_parasite_segment);
            break;

        case Defines.TE_BOSSTPORT: // boss teleporting to station
            MSG.ReadPos(Globals.net_message, pos);
            CL_fx.BigTeleportParticles(pos);
            S.StartSound(pos, 0, 0, S.RegisterSound("misc/bigtele.wav"), 1,
                    Defines.ATTN_NONE, 0);
            break;

        case Defines.TE_GRAPPLE_CABLE:
            ent = ParseBeam2(cl_mod_grapple_cable);
            break;

        // RAFAEL
        case Defines.TE_WELDING_SPARKS:
            cnt = MSG.ReadByte(Globals.net_message);
            MSG.ReadPos(Globals.net_message, pos);
            MSG.ReadDir(Globals.net_message, dir);
            color = MSG.ReadByte(Globals.net_message);
            CL_fx.ParticleEffect2(pos, dir, color, cnt);

            ex = AllocExplosion();
            Math3D.VectorCopy(pos, ex.ent.origin);
            ex.type = ex_flash;
            // note to self
            // we need a better no draw flag
            ex.ent.flags = Defines.RF_BEAM;
            ex.start = Globals.cl.frame.servertime - 0.1f;
            ex.light = 100 + (Lib.rand() % 75);
            ex.lightcolor[0] = 1.0f;
            ex.lightcolor[1] = 1.0f;
            ex.lightcolor[2] = 0.3f;
            ex.ent.model = cl_mod_flash;
            ex.frames = 2;
            break;

        case Defines.TE_GREENBLOOD:
            MSG.ReadPos(Globals.net_message, pos);
            MSG.ReadDir(Globals.net_message, dir);
            CL_fx.ParticleEffect2(pos, dir, 0xdf, 30);
            break;

        // RAFAEL
        case Defines.TE_TUNNEL_SPARKS:
            cnt = MSG.ReadByte(Globals.net_message);
            MSG.ReadPos(Globals.net_message, pos);
            MSG.ReadDir(Globals.net_message, dir);
            color = MSG.ReadByte(Globals.net_message);
            CL_fx.ParticleEffect3(pos, dir, color, cnt);
            break;

        //	  =============
        //	  PGM
        // PMM -following code integrated for flechette (different color)
        case Defines.TE_BLASTER2: // green blaster hitting wall
        case Defines.TE_FLECHETTE: // flechette
            MSG.ReadPos(Globals.net_message, pos);
            MSG.ReadDir(Globals.net_message, dir);

            // PMM
            if (type == Defines.TE_BLASTER2)
                CL_newfx.BlasterParticles2(pos, dir, 0xd0);
            else
                CL_newfx.BlasterParticles2(pos, dir, 0x6f); // 75

            ex = AllocExplosion();
            Math3D.VectorCopy(pos, ex.ent.origin);
            ex.ent.angles[0] = (float) (Math.acos(dir[2]) / Math.PI * 180);
            // PMM - fixed to correct for pitch of 0
            if (dir[0] != 0.0f)
                ex.ent.angles[1] = (float) (Math.atan2(dir[1], dir[0])
                        / Math.PI * 180);
            else if (dir[1] > 0)
                ex.ent.angles[1] = 90;
            else if (dir[1] < 0)
                ex.ent.angles[1] = 270;
            else
                ex.ent.angles[1] = 0;

            ex.type = ex_misc;
            ex.ent.flags = Defines.RF_FULLBRIGHT | Defines.RF_TRANSLUCENT;

            // PMM
            if (type == Defines.TE_BLASTER2)
                ex.ent.skinnum = 1;
            else
                // flechette
                ex.ent.skinnum = 2;

            ex.start = Globals.cl.frame.servertime - 100;
            ex.light = 150;
            // PMM
            if (type == Defines.TE_BLASTER2)
                ex.lightcolor[1] = 1;
            else // flechette
            {
                ex.lightcolor[0] = 0.19f;
                ex.lightcolor[1] = 0.41f;
                ex.lightcolor[2] = 0.75f;
            }
            ex.ent.model = cl_mod_explode;
            ex.frames = 4;
            S.StartSound(pos, 0, 0, cl_sfx_lashit, 1, Defines.ATTN_NORM, 0);
            break;

        case Defines.TE_LIGHTNING:
            ent = ParseLightning(cl_mod_lightning);
            S.StartSound(null, ent, Defines.CHAN_WEAPON, cl_sfx_lightning, 1,
                    Defines.ATTN_NORM, 0);
            break;

        case Defines.TE_DEBUGTRAIL:
            MSG.ReadPos(Globals.net_message, pos);
            MSG.ReadPos(Globals.net_message, pos2);
            CL_newfx.DebugTrail(pos, pos2);
            break;

        case Defines.TE_PLAIN_EXPLOSION:
            MSG.ReadPos(Globals.net_message, pos);

            ex = AllocExplosion();
            Math3D.VectorCopy(pos, ex.ent.origin);
            ex.type = ex_poly;
            ex.ent.flags = Defines.RF_FULLBRIGHT;
            ex.start = Globals.cl.frame.servertime - 100;
            ex.light = 350;
            ex.lightcolor[0] = 1.0f;
            ex.lightcolor[1] = 0.5f;
            ex.lightcolor[2] = 0.5f;
            ex.ent.angles[1] = Lib.rand() % 360;
            ex.ent.model = cl_mod_explo4;
            if (Globals.rnd.nextFloat() < 0.5)
                ex.baseframe = 15;
            ex.frames = 15;
            if (type == Defines.TE_ROCKET_EXPLOSION_WATER)
                S
                        .StartSound(pos, 0, 0, cl_sfx_watrexp, 1,
                                Defines.ATTN_NORM, 0);
            else
                S
                        .StartSound(pos, 0, 0, cl_sfx_rockexp, 1,
                                Defines.ATTN_NORM, 0);
            break;

        case Defines.TE_FLASHLIGHT:
            MSG.ReadPos(Globals.net_message, pos);
            ent = MSG.ReadShort(Globals.net_message);
            CL_newfx.Flashlight(ent, pos);
            break;

        case Defines.TE_FORCEWALL:
            MSG.ReadPos(Globals.net_message, pos);
            MSG.ReadPos(Globals.net_message, pos2);
            color = MSG.ReadByte(Globals.net_message);
            CL_newfx.ForceWall(pos, pos2, color);
            break;

        case Defines.TE_HEATBEAM:
            ent = ParsePlayerBeam(cl_mod_heatbeam);
            break;

        case Defines.TE_MONSTER_HEATBEAM:
            ent = ParsePlayerBeam(cl_mod_monster_heatbeam);
            break;

        case Defines.TE_HEATBEAM_SPARKS:
            //			cnt = MSG.ReadByte (net_message);
            cnt = 50;
            MSG.ReadPos(Globals.net_message, pos);
            MSG.ReadDir(Globals.net_message, dir);
            //			r = MSG.ReadByte (net_message);
            //			magnitude = MSG.ReadShort (net_message);
            r = 8;
            magnitude = 60;
            color = r & 0xff;
            CL_newfx.ParticleSteamEffect(pos, dir, color, cnt, magnitude);
            S.StartSound(pos, 0, 0, cl_sfx_lashit, 1, Defines.ATTN_NORM, 0);
            break;

        case Defines.TE_HEATBEAM_STEAM:
            //			cnt = MSG.ReadByte (net_message);
            cnt = 20;
            MSG.ReadPos(Globals.net_message, pos);
            MSG.ReadDir(Globals.net_message, dir);
            //			r = MSG.ReadByte (net_message);
            //			magnitude = MSG.ReadShort (net_message);
            //			color = r & 0xff;
            color = 0xe0;
            magnitude = 60;
            CL_newfx.ParticleSteamEffect(pos, dir, color, cnt, magnitude);
            S.StartSound(pos, 0, 0, cl_sfx_lashit, 1, Defines.ATTN_NORM, 0);
            break;

        case Defines.TE_STEAM:
            ParseSteam();
            break;

        case Defines.TE_BUBBLETRAIL2:
            //			cnt = MSG.ReadByte (net_message);
            cnt = 8;
            MSG.ReadPos(Globals.net_message, pos);
            MSG.ReadPos(Globals.net_message, pos2);
            CL_newfx.BubbleTrail2(pos, pos2, cnt);
            S.StartSound(pos, 0, 0, cl_sfx_lashit, 1, Defines.ATTN_NORM, 0);
            break;

        case Defines.TE_MOREBLOOD:
            MSG.ReadPos(Globals.net_message, pos);
            MSG.ReadDir(Globals.net_message, dir);
            CL_fx.ParticleEffect(pos, dir, 0xe8, 250);
            break;

        case Defines.TE_CHAINFIST_SMOKE:
            dir[0] = 0;
            dir[1] = 0;
            dir[2] = 1;
            MSG.ReadPos(Globals.net_message, pos);
            CL_newfx.ParticleSmokeEffect(pos, dir, 0, 20, 20);
            break;

        case Defines.TE_ELECTRIC_SPARKS:
            MSG.ReadPos(Globals.net_message, pos);
            MSG.ReadDir(Globals.net_message, dir);
            //			CL_ParticleEffect (pos, dir, 109, 40);
            CL_fx.ParticleEffect(pos, dir, 0x75, 40);
            //FIXME : replace or remove this sound
            S.StartSound(pos, 0, 0, cl_sfx_lashit, 1, Defines.ATTN_NORM, 0);
            break;

        case Defines.TE_TRACKER_EXPLOSION:
            MSG.ReadPos(Globals.net_message, pos);
            CL_newfx.ColorFlash(pos, 0, 150, -1, -1, -1);
            CL_newfx.ColorExplosionParticles(pos, 0, 1);
            S.StartSound(pos, 0, 0, cl_sfx_disrexp, 1, Defines.ATTN_NORM, 0);
            break;

        case Defines.TE_TELEPORT_EFFECT:
        case Defines.TE_DBALL_GOAL:
            MSG.ReadPos(Globals.net_message, pos);
            CL_fx.TeleportParticles(pos);
            break;

        case Defines.TE_WIDOWBEAMOUT:
            ParseWidow();
            break;

        case Defines.TE_NUKEBLAST:
            ParseNuke();
            break;

        case Defines.TE_WIDOWSPLASH:
            MSG.ReadPos(Globals.net_message, pos);
            CL_newfx.WidowSplash(pos);
            break;
        //	  PGM
        //	  ==============

        default:
            Com.Error(Defines.ERR_DROP, "CL_ParseTEnt: bad type");
        }
    }

    /*
     * ================= CL_AddBeams =================
     */
    static void AddBeams() {
        int i, j;
        beam_t[] b;
        float[] dist = new float[3];
        float[] org = new float[3];
        float d;
        entity_t ent = new entity_t();
        float yaw, pitch;
        float forward;
        float len, steps;
        float model_length;

        //	   update beams
        b = cl_beams;
        for (i = 0; i < MAX_BEAMS; i++) {
            if (b[i].model == null || b[i].endtime < Globals.cl.time)
                continue;

            // if coming from the player, update the start position
            if (b[i].entity == Globals.cl.playernum + 1) // entity 0 is the
                                                         // world
            {
                Math3D.VectorCopy(Globals.cl.refdef.vieworg, b[i].start);
                b[i].start[2] -= 22; // adjust for view height
            }
            Math3D.VectorAdd(b[i].start, b[i].offset, org);

            // calculate pitch and yaw
            Math3D.VectorSubtract(b[i].end, org, dist);

            if (dist[1] == 0 && dist[0] == 0) {
                yaw = 0;
                if (dist[2] > 0)
                    pitch = 90;
                else
                    pitch = 270;
            } else {
                // PMM - fixed to correct for pitch of 0
                if (dist[0] != 0.0f)
                    yaw = (float) (Math.atan2(dist[1], dist[0]) * 180 / Math.PI);
                else if (dist[1] > 0)
                    yaw = 90;
                else
                    yaw = 270;
                if (yaw < 0)
                    yaw += 360;

                forward = (float) Math.sqrt(dist[0] * dist[0] + dist[1]
                        * dist[1]);
                pitch = (float) (Math.atan2(dist[2], forward) * -180.0 / Math.PI);
                if (pitch < 0)
                    pitch += 360.0;
            }

            // add new entities for the beams
            d = Math3D.VectorNormalize(dist);

            //memset (&ent, 0, sizeof(ent));
            ent = new entity_t();
            if (b[i].model == cl_mod_lightning) {
                model_length = 35.0f;
                d -= 20.0; // correction so it doesn't end in middle of tesla
            } else {
                model_length = 30.0f;
            }
            steps = (float) Math.ceil(d / model_length);
            len = (d - model_length) / (steps - 1);

            // PMM - special case for lightning model .. if the real length is
            // shorter than the model,
            // flip it around & draw it from the end to the start. This prevents
            // the model from going
            // through the tesla mine (instead it goes through the target)
            if ((b[i].model == cl_mod_lightning) && (d <= model_length)) {
                //				Com_Printf ("special case\n");
                Math3D.VectorCopy(b[i].end, ent.origin);
                // offset to push beam outside of tesla model (negative because
                // dist is from end to start
                // for this beam)
                //				for (j=0 ; j<3 ; j++)
                //					ent.origin[j] -= dist[j]*10.0;
                ent.model = b[i].model;
                ent.flags = Defines.RF_FULLBRIGHT;
                ent.angles[0] = pitch;
                ent.angles[1] = yaw;
                ent.angles[2] = Lib.rand() % 360;
                V.AddEntity(ent);
                return;
            }
            while (d > 0) {
                Math3D.VectorCopy(org, ent.origin);
                ent.model = b[i].model;
                if (b[i].model == cl_mod_lightning) {
                    ent.flags = Defines.RF_FULLBRIGHT;
                    ent.angles[0] = -pitch;
                    ent.angles[1] = yaw + 180.0f;
                    ent.angles[2] = Lib.rand() % 360;
                } else {
                    ent.angles[0] = pitch;
                    ent.angles[1] = yaw;
                    ent.angles[2] = Lib.rand() % 360;
                }

                //				Com_Printf("B: %d . %d\n", b[i].entity, b[i].dest_entity);
                V.AddEntity(ent);

                for (j = 0; j < 3; j++)
                    org[j] += dist[j] * len;
                d -= model_length;
            }
        }
    }

    //extern cvar_t *hand;

    /*
     * ================= ROGUE - draw player locked beams CL_AddPlayerBeams
     * =================
     */
    static void AddPlayerBeams() {
        float[] dist = new float[3];
        float[] org = new float[3];
        float d;
        entity_t ent = new entity_t();
        float yaw, pitch;
        float forward;
        float len, steps;
        int framenum = 0;
        float model_length;

        float hand_multiplier;
        frame_t oldframe;
        player_state_t ps, ops;

        //	  PMM
        if (Globals.hand != null) {
            if (Globals.hand.value == 2)
                hand_multiplier = 0;
            else if (Globals.hand.value == 1)
                hand_multiplier = -1;
            else
                hand_multiplier = 1;
        } else {
            hand_multiplier = 1;
        }
        //	  PMM

        //	   update beams
        beam_t[] b = cl_playerbeams;
        float[] f = new float[3];
        float[] u = new float[3];
        float[] r = new float[3];
        for (int i = 0; i < MAX_BEAMS; i++) {

            if (b[i].model == null || b[i].endtime < Globals.cl.time)
                continue;

            if (cl_mod_heatbeam != null && (b[i].model == cl_mod_heatbeam)) {

                // if coming from the player, update the start position
                if (b[i].entity == Globals.cl.playernum + 1) // entity 0 is the
                                                             // world
                {
                    // set up gun position
                    // code straight out of CL_AddViewWeapon
                    ps = Globals.cl.frame.playerstate;
                    int j = (Globals.cl.frame.serverframe - 1)
                            & Defines.UPDATE_MASK;
                    oldframe = Globals.cl.frames[j];

                    if (oldframe.serverframe != Globals.cl.frame.serverframe - 1
                            || !oldframe.valid)
                        oldframe = Globals.cl.frame; // previous frame was
                                                     // dropped or involid

                    ops = oldframe.playerstate;
                    for (j = 0; j < 3; j++) {
                        b[i].start[j] = Globals.cl.refdef.vieworg[j]
                                + ops.gunoffset[j] + Globals.cl.lerpfrac
                                * (ps.gunoffset[j] - ops.gunoffset[j]);
                    }
                    Math3D.VectorMA(b[i].start,
                            (hand_multiplier * b[i].offset[0]),
                            Globals.cl.v_right, org);
                    Math3D.VectorMA(org, b[i].offset[1], Globals.cl.v_forward,
                            org);
                    Math3D.VectorMA(org, b[i].offset[2], Globals.cl.v_up, org);
                    if ((Globals.hand != null) && (Globals.hand.value == 2)) {
                        Math3D.VectorMA(org, -1, Globals.cl.v_up, org);
                    }
                    // FIXME - take these out when final
                    Math3D.VectorCopy(Globals.cl.v_right, r);
                    Math3D.VectorCopy(Globals.cl.v_forward, f);
                    Math3D.VectorCopy(Globals.cl.v_up, u);

                } else
                    Math3D.VectorCopy(b[i].start, org);
            } else {
                // if coming from the player, update the start position
                if (b[i].entity == Globals.cl.playernum + 1) // entity 0 is the
                                                             // world
                {
                    Math3D.VectorCopy(Globals.cl.refdef.vieworg, b[i].start);
                    b[i].start[2] -= 22; // adjust for view height
                }
                Math3D.VectorAdd(b[i].start, b[i].offset, org);
            }

            // calculate pitch and yaw
            Math3D.VectorSubtract(b[i].end, org, dist);

            //	  PMM
            if (cl_mod_heatbeam != null && (b[i].model == cl_mod_heatbeam)
                    && (b[i].entity == Globals.cl.playernum + 1)) {

                len = Math3D.VectorLength(dist);
                Math3D.VectorScale(f, len, dist);
                Math3D.VectorMA(dist, (hand_multiplier * b[i].offset[0]), r,
                        dist);
                Math3D.VectorMA(dist, b[i].offset[1], f, dist);
                Math3D.VectorMA(dist, b[i].offset[2], u, dist);
                if ((Globals.hand != null) && (Globals.hand.value == 2)) {
                    Math3D.VectorMA(org, -1, Globals.cl.v_up, org);
                }
            }
            //	  PMM

            if (dist[1] == 0 && dist[0] == 0) {
                yaw = 0;
                if (dist[2] > 0)
                    pitch = 90;
                else
                    pitch = 270;
            } else {
                // PMM - fixed to correct for pitch of 0
                if (dist[0] != 0.0f)
                    yaw = (float) (Math.atan2(dist[1], dist[0]) * 180 / Math.PI);
                else if (dist[1] > 0)
                    yaw = 90;
                else
                    yaw = 270;
                if (yaw < 0)
                    yaw += 360;

                forward = (float) Math.sqrt(dist[0] * dist[0] + dist[1]
                        * dist[1]);
                pitch = (float) (Math.atan2(dist[2], forward) * -180.0 / Math.PI);
                if (pitch < 0)
                    pitch += 360.0;
            }

            if (cl_mod_heatbeam != null && (b[i].model == cl_mod_heatbeam)) {
                if (b[i].entity != Globals.cl.playernum + 1) {
                    framenum = 2;
                    //					Com_Printf ("Third person\n");
                    ent.angles[0] = -pitch;
                    ent.angles[1] = yaw + 180.0f;
                    ent.angles[2] = 0;
                    //					Com_Printf ("%f %f - %f %f %f\n", -pitch, yaw+180.0,
                    // b[i].offset[0], b[i].offset[1], b[i].offset[2]);
                    Math3D.AngleVectors(ent.angles, f, r, u);

                    // if it's a non-origin offset, it's a player, so use the
                    // hardcoded player offset
                    if (Math3D.VectorCompare(b[i].offset, Globals.vec3_origin) == 0) {
                        Math3D.VectorMA(org, -(b[i].offset[0]) + 1, r, org);
                        Math3D.VectorMA(org, -(b[i].offset[1]), f, org);
                        Math3D.VectorMA(org, -(b[i].offset[2]) - 10, u, org);
                    } else {
                        // if it's a monster, do the particle effect
                        CL_newfx.MonsterPlasma_Shell(b[i].start);
                    }
                } else {
                    framenum = 1;
                }
            }

            // if it's the heatbeam, draw the particle effect
            if ((cl_mod_heatbeam != null && (b[i].model == cl_mod_heatbeam) && (b[i].entity == Globals.cl.playernum + 1))) {
                CL_newfx.Heatbeam(org, dist);
            }

            // add new entities for the beams
            d = Math3D.VectorNormalize(dist);

            //memset (&ent, 0, sizeof(ent));
            //ent = new entity_t();
            // this is not required. hoz

            if (b[i].model == cl_mod_heatbeam) {
                model_length = 32.0f;
            } else if (b[i].model == cl_mod_lightning) {
                model_length = 35.0f;
                d -= 20.0; // correction so it doesn't end in middle of tesla
            } else {
                model_length = 30.0f;
            }
            steps = (float) Math.ceil(d / model_length);
            len = (d - model_length) / (steps - 1);

            // PMM - special case for lightning model .. if the real length is
            // shorter than the model,
            // flip it around & draw it from the end to the start. This prevents
            // the model from going
            // through the tesla mine (instead it goes through the target)
            if ((b[i].model == cl_mod_lightning) && (d <= model_length)) {
                //				Com_Printf ("special case\n");
                Math3D.VectorCopy(b[i].end, ent.origin);
                // offset to push beam outside of tesla model (negative because
                // dist is from end to start
                // for this beam)
                //				for (j=0 ; j<3 ; j++)
                //					ent.origin[j] -= dist[j]*10.0;
                ent.model = b[i].model;
                ent.flags = Defines.RF_FULLBRIGHT;
                ent.angles[0] = pitch;
                ent.angles[1] = yaw;
                ent.angles[2] = Lib.rand() % 360;
                V.AddEntity(ent);
                return;
            }
            while (d > 0) {
                Math3D.VectorCopy(org, ent.origin);
                ent.model = b[i].model;
                if (cl_mod_heatbeam != null && (b[i].model == cl_mod_heatbeam)) {
                    //					ent.flags = RF_FULLBRIGHT|RF_TRANSLUCENT;
                    //					ent.alpha = 0.3;
                    ent.flags = Defines.RF_FULLBRIGHT;
                    ent.angles[0] = -pitch;
                    ent.angles[1] = yaw + 180.0f;
                    ent.angles[2] = (Globals.cl.time) % 360;
                    //					ent.angles[2] = rand()%360;
                    ent.frame = framenum;
                } else if (b[i].model == cl_mod_lightning) {
                    ent.flags = Defines.RF_FULLBRIGHT;
                    ent.angles[0] = -pitch;
                    ent.angles[1] = yaw + 180.0f;
                    ent.angles[2] = Lib.rand() % 360;
                } else {
                    ent.angles[0] = pitch;
                    ent.angles[1] = yaw;
                    ent.angles[2] = Lib.rand() % 360;
                }

                //				Com_Printf("B: %d . %d\n", b[i].entity, b[i].dest_entity);
                V.AddEntity(ent);

                for (int j = 0; j < 3; j++)
                    org[j] += dist[j] * len;
                d -= model_length;
            }
        }
    }

    /*
     * ================= CL_AddExplosions =================
     */
    static void AddExplosions() {
        entity_t ent;
        int i;
        explosion_t[] ex;
        float frac;
        int f;

        //memset (&ent, 0, sizeof(ent)); Pointer!
        ent = null;
        ex = cl_explosions;
        for (i = 0; i < MAX_EXPLOSIONS; i++) {
            if (ex[i].type == ex_free)
                continue;
            frac = (Globals.cl.time - ex[i].start) / 100.0f;
            f = (int) Math.floor(frac);

            ent = ex[i].ent;

            switch (ex[i].type) {
            case ex_mflash:
                if (f >= ex[i].frames - 1)
                    ex[i].type = ex_free;
                break;
            case ex_misc:
                if (f >= ex[i].frames - 1) {
                    ex[i].type = ex_free;
                    break;
                }
                ent.alpha = 1.0f - frac / (ex[i].frames - 1);
                break;
            case ex_flash:
                if (f >= 1) {
                    ex[i].type = ex_free;
                    break;
                }
                ent.alpha = 1.0f;
                break;
            case ex_poly:
                if (f >= ex[i].frames - 1) {
                    ex[i].type = ex_free;
                    break;
                }

                ent.alpha = (16.0f - (float) f) / 16.0f;

                if (f < 10) {
                    ent.skinnum = (f >> 1);
                    if (ent.skinnum < 0)
                        ent.skinnum = 0;
                } else {
                    ent.flags |= Defines.RF_TRANSLUCENT;
                    if (f < 13)
                        ent.skinnum = 5;
                    else
                        ent.skinnum = 6;
                }
                break;
            case ex_poly2:
                if (f >= ex[i].frames - 1) {
                    ex[i].type = ex_free;
                    break;
                }

                ent.alpha = (5.0f - (float) f) / 5.0f;
                ent.skinnum = 0;
                ent.flags |= Defines.RF_TRANSLUCENT;
                break;
            }

            if (ex[i].type == ex_free)
                continue;
            if (ex[i].light != 0.0f) {
                V.AddLight(ent.origin, ex[i].light * ent.alpha,
                        ex[i].lightcolor[0], ex[i].lightcolor[1],
                        ex[i].lightcolor[2]);
            }

            Math3D.VectorCopy(ent.origin, ent.oldorigin);

            if (f < 0)
                f = 0;
            ent.frame = ex[i].baseframe + f + 1;
            ent.oldframe = ex[i].baseframe + f;
            ent.backlerp = 1.0f - Globals.cl.lerpfrac;

            V.AddEntity(ent);
        }
    }

    /*
     * ================= CL_AddLasers =================
     */
    static void AddLasers() {
        laser_t[] l;
        int i;

        l = cl_lasers;
        for (i = 0; i < MAX_LASERS; i++) {
            if (l[i].endtime >= Globals.cl.time)
                V.AddEntity(l[i].ent);
        }
    }

    /* PMM - CL_Sustains */
    static void ProcessSustain() {
        cl_sustain_t[] s;
        int i;

        s = cl_sustains;
        for (i = 0; i < MAX_SUSTAINS; i++) {
            if (s[i].id != 0)
                if ((s[i].endtime >= Globals.cl.time)
                        && (Globals.cl.time >= s[i].nextthink)) {
                    s[i].think.think(s[i]);
                } else if (s[i].endtime < Globals.cl.time)
                    s[i].id = 0;
        }
    }

    /*
     * ================= CL_AddTEnts =================
     */
    static void AddTEnts() {
        AddBeams();
        // PMM - draw plasma beams
        AddPlayerBeams();
        AddExplosions();
        AddLasers();
        // PMM - set up sustain
        ProcessSustain();
    }
}