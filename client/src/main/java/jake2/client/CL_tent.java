/*
 * java
 * Copyright (C) 2004
 * 
 * $Id: CL_tent.java,v 1.10 2005-02-20 21:50:52 salomo Exp $
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

import jake2.client.render.model_t;
import jake2.client.sound.S;
import jake2.client.sound.sfx_t;
import jake2.qcommon.*;
import jake2.qcommon.network.messages.server.*;
import jake2.qcommon.util.Lib;
import jake2.qcommon.util.Math3D;

import static jake2.qcommon.Defines.*;

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
            ent.clear();
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
            ent.clear();
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
        cl_mod_explode = ClientGlobals.re
                .RegisterModel("models/objects/explode/tris.md2");
        cl_mod_smoke = ClientGlobals.re
                .RegisterModel("models/objects/smoke/tris.md2");
        cl_mod_flash = ClientGlobals.re
                .RegisterModel("models/objects/flash/tris.md2");
        cl_mod_parasite_segment = ClientGlobals.re
                .RegisterModel("models/monsters/parasite/segment/tris.md2");
        cl_mod_grapple_cable = ClientGlobals.re
                .RegisterModel("models/ctf/segment/tris.md2");
        cl_mod_parasite_tip = ClientGlobals.re
                .RegisterModel("models/monsters/parasite/tip/tris.md2");
        cl_mod_explo4 = ClientGlobals.re
                .RegisterModel("models/objects/r_explode/tris.md2");
        cl_mod_bfg_explo = ClientGlobals.re.RegisterModel("sprites/s_bfg2.sp2");
        cl_mod_powerscreen = ClientGlobals.re
                .RegisterModel("models/items/armor/effect/tris.md2");

        ClientGlobals.re.RegisterModel("models/objects/laser/tris.md2");
        ClientGlobals.re.RegisterModel("models/objects/grenade2/tris.md2");
        ClientGlobals.re.RegisterModel("models/weapons/v_machn/tris.md2");
        ClientGlobals.re.RegisterModel("models/weapons/v_handgr/tris.md2");
        ClientGlobals.re.RegisterModel("models/weapons/v_shotg2/tris.md2");
        ClientGlobals.re.RegisterModel("models/objects/gibs/bone/tris.md2");
        ClientGlobals.re.RegisterModel("models/objects/gibs/sm_meat/tris.md2");
        ClientGlobals.re.RegisterModel("models/objects/gibs/bone2/tris.md2");
        //	   RAFAEL
        //	   re.RegisterModel ("models/objects/blaser/tris.md2");

        ClientGlobals.re.RegisterPic("w_machinegun");
        ClientGlobals.re.RegisterPic("a_bullets");
        ClientGlobals.re.RegisterPic("i_health");
        ClientGlobals.re.RegisterPic("a_grenades");

        //	  ROGUE
        cl_mod_explo4_big = ClientGlobals.re
                .RegisterModel("models/objects/r_explode2/tris.md2");
        cl_mod_lightning = ClientGlobals.re
                .RegisterModel("models/proj/lightning/tris.md2");
        cl_mod_heatbeam = ClientGlobals.re.RegisterModel("models/proj/beam/tris.md2");
        cl_mod_monster_heatbeam = ClientGlobals.re
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
        time = ClientGlobals.cl.time;
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
        ex.start = ClientGlobals.cl.frame.servertime - 100;
        ex.ent.model = cl_mod_smoke;

        ex = AllocExplosion();
        Math3D.VectorCopy(origin, ex.ent.origin);
        ex.type = ex_flash;
        ex.ent.flags = Defines.RF_FULLBRIGHT;
        ex.frames = 2;
        ex.start = ClientGlobals.cl.frame.servertime - 100;
        ex.ent.model = cl_mod_flash;
    }

    /*
     * =================
     * CL_ParseBeam
     * =================
     */
    static int ParseBeam(model_t model, BeamTEMessage beam) {

        //	   override any beam with the same entity
        beam_t[] b = cl_beams;
        for (int i = 0; i < MAX_BEAMS; i++)
            if (b[i].entity == beam.ownerIndex) {
                b[i].entity = beam.ownerIndex;
                b[i].model = model;
                b[i].endtime = ClientGlobals.cl.time + 200;
                Math3D.VectorCopy(beam.origin, b[i].start);
                Math3D.VectorCopy(beam.destination, b[i].end);
                Math3D.VectorClear(b[i].offset);
                return beam.ownerIndex;
            }

        //	   find a free beam
        b = cl_beams;
        for (int i = 0; i < MAX_BEAMS; i++) {
            if (b[i].model == null || b[i].endtime < ClientGlobals.cl.time) {
                b[i].entity = beam.ownerIndex;
                b[i].model = model;
                b[i].endtime = ClientGlobals.cl.time + 200;
                Math3D.VectorCopy(beam.origin, b[i].start);
                Math3D.VectorCopy(beam.destination, b[i].end);
                Math3D.VectorClear(b[i].offset);
                return beam.ownerIndex;
            }
        }
        Com.Printf("beam list overflow!\n");
        return beam.ownerIndex;
    }

    /*
     * ================= CL_ParseBeam2 =================
     */
    static int ParseBeam2(model_t model, BeamOffsetTEMessage beam) {
        //	   override any beam with the same entity
        beam_t[] b = cl_beams;
        for (int i = 0; i < MAX_BEAMS; i++)
            if (b[i].entity == beam.ownerIndex) {
                b[i].entity = beam.ownerIndex;
                b[i].model = model;
                b[i].endtime = ClientGlobals.cl.time + 200;
                Math3D.VectorCopy(beam.origin, b[i].start);
                Math3D.VectorCopy(beam.destination, b[i].end);
                Math3D.VectorCopy(beam.offset, b[i].offset);
                return beam.ownerIndex;
            }

        //	   find a free beam
        b = cl_beams;
        for (int i = 0; i < MAX_BEAMS; i++) {
            if (b[i].model == null || b[i].endtime < ClientGlobals.cl.time) {
                b[i].entity = beam.ownerIndex;
                b[i].model = model;
                b[i].endtime = ClientGlobals.cl.time + 200;
                Math3D.VectorCopy(beam.origin, b[i].start);
                Math3D.VectorCopy(beam.destination, b[i].end);
                Math3D.VectorCopy(beam.offset, b[i].offset);
                return beam.ownerIndex;
            }
        }
        Com.Printf("beam list overflow!\n");
        return beam.ownerIndex;
    }

    //	   ROGUE
    /*
     * ================= CL_ParsePlayerBeam - adds to the cl_playerbeam array
     * instead of the cl_beams array =================
     */
    static int ParsePlayerBeam(model_t model, BeamTEMessage m) {
        float[] start = m.origin;
        float[] end = m.destination;
        float[] offset = new float[3];

        // PMM - network optimization
        if (model == cl_mod_heatbeam) {
            Math3D.VectorSet(offset, 2, 7, -3);
        } else if (model == cl_mod_monster_heatbeam) {
            model = cl_mod_heatbeam;
            Math3D.VectorSet(offset, 0, 0, 0);
        }
        // else {
            // fixme: unreachable
            // MSG.ReadPos(Globals.net_message, offset);
        //}

        //		Com_Printf ("end- %f %f %f\n", end[0], end[1], end[2]);

        //	   override any beam with the same entity
        //	   PMM - For player beams, we only want one per player (entity) so..
        beam_t[] b = cl_playerbeams;
        for (int i = 0; i < MAX_BEAMS; i++) {
            if (b[i].entity == m.ownerIndex) {
                b[i].entity = m.ownerIndex;
                b[i].model = model;
                b[i].endtime = ClientGlobals.cl.time + 200;
                Math3D.VectorCopy(start, b[i].start);
                Math3D.VectorCopy(end, b[i].end);
                Math3D.VectorCopy(offset, b[i].offset);
                return m.ownerIndex;
            }
        }

        //	   find a free beam
        b = cl_playerbeams;
        for (int i = 0; i < MAX_BEAMS; i++) {
            if (b[i].model == null || b[i].endtime < ClientGlobals.cl.time) {
                b[i].entity = m.ownerIndex;
                b[i].model = model;
                b[i].endtime = ClientGlobals.cl.time + 100; // PMM - this needs to be
                                                      // 100 to prevent multiple
                                                      // heatbeams
                Math3D.VectorCopy(start, b[i].start);
                Math3D.VectorCopy(end, b[i].end);
                Math3D.VectorCopy(offset, b[i].offset);
                return m.ownerIndex;
            }
        }
        Com.Printf("beam list overflow!\n");
        return m.ownerIndex;
    }

    //	  rogue

    // stack variable
    private static final float[] start = new float[3];
    private static final float[] end = new float[3];
    /*
     * ================= CL_ParseLightning =================
     */
    @Deprecated
    static int ParseLightning(model_t model, sizebuf_t net_message) {
        int srcEnt, destEnt;
        beam_t[] b;
        int i;

        srcEnt = MSG.ReadShort(net_message);
        destEnt = MSG.ReadShort(net_message);

        MSG.ReadPos(net_message, start);
        MSG.ReadPos(net_message, end);

        //	   override any beam with the same source AND destination entities
        b = cl_beams;
        for (i = 0; i < MAX_BEAMS; i++)
            if (b[i].entity == srcEnt && b[i].dest_entity == destEnt) {
                //				Com_Printf("%d: OVERRIDE %d . %d\n", cl.time, srcEnt,
                // destEnt);
                b[i].entity = srcEnt;
                b[i].dest_entity = destEnt;
                b[i].model = model;
                b[i].endtime = ClientGlobals.cl.time + 200;
                Math3D.VectorCopy(start, b[i].start);
                Math3D.VectorCopy(end, b[i].end);
                Math3D.VectorClear(b[i].offset);
                return srcEnt;
            }

        //	   find a free beam
        b = cl_beams;
        for (i = 0; i < MAX_BEAMS; i++) {
            if (b[i].model == null || b[i].endtime < ClientGlobals.cl.time) {
                //				Com_Printf("%d: NORMAL %d . %d\n", cl.time, srcEnt, destEnt);
                b[i].entity = srcEnt;
                b[i].dest_entity = destEnt;
                b[i].model = model;
                b[i].endtime = ClientGlobals.cl.time + 200;
                Math3D.VectorCopy(start, b[i].start);
                Math3D.VectorCopy(end, b[i].end);
                Math3D.VectorClear(b[i].offset);
                return srcEnt;
            }
        }
        Com.Printf("beam list overflow!\n");
        return srcEnt;
    }

    // stack variable
    // start, end
    /*
     * ================= CL_ParseLaser =================
     */
    static void ParseLaser(int colors, float[] start, float[] end) {

        laser_t[] l = cl_lasers;
        for (int i = 0; i < MAX_LASERS; i++) {
            if (l[i].endtime < ClientGlobals.cl.time) {
                l[i].ent.flags = Defines.RF_TRANSLUCENT | Defines.RF_BEAM;
                Math3D.VectorCopy(start, l[i].ent.origin);
                Math3D.VectorCopy(end, l[i].ent.oldorigin);
                l[i].ent.alpha = 0.30f;
                l[i].ent.skinnum = (colors >> ((Lib.rand() % 4) * 8)) & 0xff;
                l[i].ent.model = null;
                l[i].ent.frame = 4;
                l[i].endtime = ClientGlobals.cl.time + 100;
                return;
            }
        }
    }

    // stack variable
    private static final float[] pos = new float[3];
    private static final float[] dir = new float[3];
    //	  =============
    //	  ROGUE
    @Deprecated
    static void ParseSteam(sizebuf_t net_message) {
        int id, i;
        int r;
        int cnt;
        int color;
        int magnitude;
        cl_sustain_t[] s;
        cl_sustain_t free_sustain;

        id = MSG.ReadShort(net_message); // an id of -1 is an instant
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
                s[i].count = MSG.ReadByte(net_message);
                MSG.ReadPos(net_message, s[i].org);
                MSG.ReadDir(net_message, s[i].dir);
                r = MSG.ReadByte(net_message);
                s[i].color = r & 0xff;
                s[i].magnitude = MSG.ReadShort(net_message);
                s[i].endtime = ClientGlobals.cl.time
                        + MSG.ReadLong(net_message);
                s[i].think = new cl_sustain_t.ThinkAdapter() {
                    void think(cl_sustain_t self) {
                        CL_newfx.ParticleSteamEffect2(self);
                    }
                };
                s[i].thinkinterval = 100;
                s[i].nextthink = ClientGlobals.cl.time;
            } else {
                //					Com_Printf ("No free sustains!\n");
                // FIXME - read the stuff anyway
                cnt = MSG.ReadByte(net_message);
                MSG.ReadPos(net_message, pos);
                MSG.ReadDir(net_message, dir);
                r = MSG.ReadByte(net_message);
                magnitude = MSG.ReadShort(net_message);
                magnitude = MSG.ReadLong(net_message); // really
                                                               // interval
            }
        } else // instant
        {
            cnt = MSG.ReadByte(net_message);
            MSG.ReadPos(net_message, pos);
            MSG.ReadDir(net_message, dir);
            r = MSG.ReadByte(net_message);
            magnitude = MSG.ReadShort(net_message);
            color = r & 0xff;
            CL_newfx.ParticleSteamEffect(pos, dir, color, cnt, magnitude);
            //			S_StartSound (pos, 0, 0, cl_sfx_lashit, 1, ATTN_NORM, 0);
        }
    }
    
    // stack variable
    // pos
    @Deprecated
    static void ParseWidow(sizebuf_t net_message) {
        int id, i;
        cl_sustain_t[] s;
        cl_sustain_t free_sustain;

        id = MSG.ReadShort(net_message);

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
            MSG.ReadPos(net_message, s[i].org);
            s[i].endtime = ClientGlobals.cl.time + 2100;
            s[i].think = new cl_sustain_t.ThinkAdapter() {
                void think(cl_sustain_t self) {
                    CL_newfx.Widowbeamout(self);
                }
            };
            s[i].thinkinterval = 1;
            s[i].nextthink = ClientGlobals.cl.time;
        } else // no free sustains
        {
            // FIXME - read the stuff anyway
            MSG.ReadPos(net_message, pos);
        }
    }

    // stack variable
    // pos
    @Deprecated
    static void ParseNuke(sizebuf_t net_message) {
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
            MSG.ReadPos(net_message, s[i].org);
            s[i].endtime = ClientGlobals.cl.time + 1000;
            s[i].think = new cl_sustain_t.ThinkAdapter() {
                void think(cl_sustain_t self) {
                    CL_newfx.Nukeblast(self);
                }
            };
            s[i].thinkinterval = 1;
            s[i].nextthink = ClientGlobals.cl.time;
        } else // no free sustains
        {
            // FIXME - read the stuff anyway
            MSG.ReadPos(net_message, pos);
        }
    }

    //	  ROGUE
    //	  =============

    /*
     * ================= CL_ParseTEnt =================
     */
    static int[] splash_color = { 0x00, 0xe0, 0xb0, 0x50, 0xd0, 0xe0, 0xe8 };
    // stack variable
    // pos, dir
    private static final float[] pos2 = {0, 0, 0};

    static void ParseTEnt(TEMessage msg) {
        if (msg instanceof PointDirectionTEMessage) {
            PointDirectionTEMessage m = (PointDirectionTEMessage) msg;
            switch (m.style) {
                case TE_BLOOD:
                    CL_fx.ParticleEffect(m.position, m.direction, 0xe8, 60);
                    break;
                case TE_GUNSHOT:
                case TE_SPARKS:
                case TE_BULLET_SPARKS:
                    if (m.style == Defines.TE_GUNSHOT)
                        CL_fx.ParticleEffect(m.position, m.direction, 0, 40);
                    else
                        CL_fx.ParticleEffect(m.position, m.direction, 0xe0, 6);

                    if (m.style != Defines.TE_SPARKS) {
                        SmokeAndFlash(m.position);

                        // impact sound
                        int cnt = Lib.rand() & 15;
                        if (cnt == 1)
                            S.StartSound(m.position, 0, 0, cl_sfx_ric1, 1, Defines.ATTN_NORM,0);
                        else if (cnt == 2)
                            S.StartSound(m.position, 0, 0, cl_sfx_ric2, 1, Defines.ATTN_NORM,0);
                        else if (cnt == 3)
                            S.StartSound(m.position, 0, 0, cl_sfx_ric3, 1, Defines.ATTN_NORM,0);
                    }

                    break;
                case TE_SCREEN_SPARKS:
                case TE_SHIELD_SPARKS:
                    if (m.style == Defines.TE_SCREEN_SPARKS)
                        CL_fx.ParticleEffect(m.position, m.direction, 0xd0, 40);
                    else
                        CL_fx.ParticleEffect(m.position, m.direction, 0xb0, 40);
                    //FIXME : replace or remove this sound
                    S.StartSound(m.position, 0, 0, cl_sfx_lashit, 1, Defines.ATTN_NORM, 0);
                    break;

                case TE_SHOTGUN:
                    CL_fx.ParticleEffect(m.position, m.direction, 0, 20);
                    SmokeAndFlash(m.position);
                    break;
                case TE_BLASTER:
                    CL_fx.BlasterParticles(m.position, m.direction);
                    explosion_t ex = AllocExplosion();
                    Math3D.VectorCopy(m.position, ex.ent.origin);
                    ex.ent.angles[0] = (float) (Math.acos(m.direction[2]) / Math.PI * 180);
                    // PMM - fixed to correct for pitch of 0
                    if (m.direction[0] != 0.0f)
                        ex.ent.angles[1] = (float) (Math.atan2(m.direction[1], m.direction[0]) / Math.PI * 180);
                    else if (m.direction[1] > 0)
                        ex.ent.angles[1] = 90;
                    else if (m.direction[1] < 0)
                        ex.ent.angles[1] = 270;
                    else
                        ex.ent.angles[1] = 0;

                    ex.type = ex_misc;
                    ex.ent.flags = Defines.RF_FULLBRIGHT | Defines.RF_TRANSLUCENT;
                    ex.start = ClientGlobals.cl.frame.servertime - 100;
                    ex.light = 150;
                    ex.lightcolor[0] = 1;
                    ex.lightcolor[1] = 1;
                    ex.ent.model = cl_mod_explode;
                    ex.frames = 4;
                    S.StartSound(m.position, 0, 0, cl_sfx_lashit, 1, Defines.ATTN_NORM, 0);
                    break;
                case TE_GREENBLOOD:
                    CL_fx.ParticleEffect2(m.position, m.direction, 0xdf, 30);
                    break;

                case TE_BLASTER2:
                case TE_FLECHETTE:
                    // PGM PMM -following code integrated for flechette (different color)
                    if (m.style == Defines.TE_BLASTER2)
                        CL_newfx.BlasterParticles2(m.position, m.direction, 0xd0);
                    else
                        CL_newfx.BlasterParticles2(m.position, m.direction, 0x6f); // 75

                    explosion_t ex1 = AllocExplosion();
                    Math3D.VectorCopy(m.position, ex1.ent.origin);
                    ex1.ent.angles[0] = (float) (Math.acos(m.direction[2]) / Math.PI * 180);
                    // PMM - fixed to correct for pitch of 0
                    if (m.direction[0] != 0.0f)
                        ex1.ent.angles[1] = (float) (Math.atan2(m.direction[1], m.direction[0])
                                / Math.PI * 180);
                    else if (m.direction[1] > 0)
                        ex1.ent.angles[1] = 90;
                    else if (m.direction[1] < 0)
                        ex1.ent.angles[1] = 270;
                    else
                        ex1.ent.angles[1] = 0;

                    ex1.type = ex_misc;
                    ex1.ent.flags = Defines.RF_FULLBRIGHT | Defines.RF_TRANSLUCENT;

                    // PMM
                    if (m.style == Defines.TE_BLASTER2)
                        ex1.ent.skinnum = 1;
                    else
                        // flechette
                        ex1.ent.skinnum = 2;

                    ex1.start = ClientGlobals.cl.frame.servertime - 100;
                    ex1.light = 150;
                    // PMM
                    if (m.style == Defines.TE_BLASTER2)
                        ex1.lightcolor[1] = 1;
                    else // flechette
                    {
                        ex1.lightcolor[0] = 0.19f;
                        ex1.lightcolor[1] = 0.41f;
                        ex1.lightcolor[2] = 0.75f;
                    }
                    ex1.ent.model = cl_mod_explode;
                    ex1.frames = 4;
                    S.StartSound(m.position, 0, 0, cl_sfx_lashit, 1, Defines.ATTN_NORM, 0);
                    break;

                case TE_HEATBEAM_SPARKS:
                    CL_newfx.ParticleSteamEffect(m.position, m.direction, 8 & 0xff, 50, 60);
                    S.StartSound(m.position, 0, 0, cl_sfx_lashit, 1, Defines.ATTN_NORM, 0);
                    break;

                case TE_HEATBEAM_STEAM:
                    CL_newfx.ParticleSteamEffect(m.position, m.direction, 0xe0, 20, 60);
                    S.StartSound(m.position, 0, 0, cl_sfx_lashit, 1, Defines.ATTN_NORM, 0);
                    break;
                case TE_MOREBLOOD:
                    CL_fx.ParticleEffect(m.position, m.direction, 0xe8, 250);
                    break;
                case TE_ELECTRIC_SPARKS:
                    CL_fx.ParticleEffect(m.position, m.direction, 0x75, 40);
                    //FIXME : replace or remove this sound
                    S.StartSound(m.position, 0, 0, cl_sfx_lashit, 1, Defines.ATTN_NORM, 0);
                    break;
            }
        } else if (msg instanceof TrailTEMessage) {
            TrailTEMessage m = (TrailTEMessage) msg;
            switch (msg.style) {
                case TE_BUBBLETRAIL:
                    CL_fx.BubbleTrail(m.position, m.destination);
                    break;
                case TE_RAILTRAIL:
                    CL_fx.RailTrail(m.position, m.destination);
                    S.StartSound(m.destination, 0, 0, cl_sfx_railg, 1, Defines.ATTN_NORM, 0);
                    break;
                case TE_BLUEHYPERBLASTER:
                    CL_fx.BlasterParticles(m.position, m.destination);
                    break;
                case TE_DEBUGTRAIL:
                    CL_newfx.DebugTrail(m.position, m.destination);
                    break;
                case TE_BFG_LASER:
                    ParseLaser(0xd0d1d2d3, m.position, m.destination);
                    break;
            }
        } else if (msg instanceof SplashTEMessage) {
            SplashTEMessage m = (SplashTEMessage) msg;
            switch (msg.style) {
                case TE_SPLASH:
                    // bullet hitting water
                    int color;
                    if (m.param > 6)
                        color = 0x00;
                    else
                        color = splash_color[m.param];
                    CL_fx.ParticleEffect(m.position, m.direction, color, m.count);

                    if (m.param == Defines.SPLASH_SPARKS) {
                        int r = Lib.rand() & 3;
                        if (r == 0)
                            S.StartSound(m.position, 0, 0, cl_sfx_spark5, 1, Defines.ATTN_STATIC, 0);
                        else if (r == 1)
                            S.StartSound(m.position, 0, 0, cl_sfx_spark6, 1, Defines.ATTN_STATIC, 0);
                        else
                            S.StartSound(m.position, 0, 0, cl_sfx_spark7, 1, Defines.ATTN_STATIC, 0);
                    }
                    break;
                case TE_LASER_SPARKS:
                    CL_fx.ParticleEffect2(m.position, m.direction, m.param, m.count);
                    break;
                case TE_WELDING_SPARKS:
                    CL_fx.ParticleEffect2(m.position, m.direction, m.param, m.count);
                    explosion_t ex = AllocExplosion();
                    Math3D.VectorCopy(m.position, ex.ent.origin);
                    ex.type = ex_flash;
                    // note to self
                    // we need a better no draw flag
                    ex.ent.flags = Defines.RF_BEAM;
                    ex.start = ClientGlobals.cl.frame.servertime - 0.1f;
                    ex.light = 100 + (Lib.rand() % 75);
                    ex.lightcolor[0] = 1.0f;
                    ex.lightcolor[1] = 1.0f;
                    ex.lightcolor[2] = 0.3f;
                    ex.ent.model = cl_mod_flash;
                    ex.frames = 2;
                    break;
                case TE_TUNNEL_SPARKS:
                    CL_fx.ParticleEffect3(m.position, m.direction, m.param, m.count);
                    break;
            }
        } else if (msg instanceof PointTEMessage) {
            PointTEMessage m = (PointTEMessage) msg;
            switch (msg.style) {
                case TE_EXPLOSION2:
                case TE_GRENADE_EXPLOSION:
                case TE_GRENADE_EXPLOSION_WATER:
                    explosion_t ex = AllocExplosion();
                    Math3D.VectorCopy(m.position, ex.ent.origin);
                    ex.type = ex_poly;
                    ex.ent.flags = Defines.RF_FULLBRIGHT;
                    ex.start = ClientGlobals.cl.frame.servertime - 100;
                    ex.light = 350;
                    ex.lightcolor[0] = 1.0f;
                    ex.lightcolor[1] = 0.5f;
                    ex.lightcolor[2] = 0.5f;
                    ex.ent.model = cl_mod_explo4;
                    ex.frames = 19;
                    ex.baseframe = 30;
                    ex.ent.angles[1] = Lib.rand() % 360;
                    CL_fx.ExplosionParticles(m.position);
                    if (m.style == Defines.TE_GRENADE_EXPLOSION_WATER)
                        S.StartSound(m.position, 0, 0, cl_sfx_watrexp, 1, Defines.ATTN_NORM, 0);
                    else
                        S.StartSound(m.position, 0, 0, cl_sfx_grenexp, 1, Defines.ATTN_NORM, 0);
                    break;
                case TE_PLASMA_EXPLOSION:
                    explosion_t plasma = AllocExplosion();
                    Math3D.VectorCopy(m.position, plasma.ent.origin);
                    plasma.type = ex_poly;
                    plasma.ent.flags = Defines.RF_FULLBRIGHT;
                    plasma.start = ClientGlobals.cl.frame.servertime - 100;
                    plasma.light = 350;
                    plasma.lightcolor[0] = 1.0f;
                    plasma.lightcolor[1] = 0.5f;
                    plasma.lightcolor[2] = 0.5f;
                    plasma.ent.angles[1] = Lib.rand() % 360;
                    plasma.ent.model = cl_mod_explo4;
                    if (Globals.rnd.nextFloat() < 0.5)
                        plasma.baseframe = 15;
                    plasma.frames = 15;
                    CL_fx.ExplosionParticles(m.position);
                    S.StartSound(m.position, 0, 0, cl_sfx_rockexp, 1, Defines.ATTN_NORM, 0);
                    break;

                case TE_EXPLOSION1:
                case TE_EXPLOSION1_BIG:
                case TE_ROCKET_EXPLOSION:
                case TE_ROCKET_EXPLOSION_WATER:
                case TE_EXPLOSION1_NP:

                    explosion_t ex1 = AllocExplosion();
                    Math3D.VectorCopy(m.position, ex1.ent.origin);
                    ex1.type = ex_poly;
                    ex1.ent.flags = Defines.RF_FULLBRIGHT;
                    ex1.start = ClientGlobals.cl.frame.servertime - 100;
                    ex1.light = 350;
                    ex1.lightcolor[0] = 1.0f;
                    ex1.lightcolor[1] = 0.5f;
                    ex1.lightcolor[2] = 0.5f;
                    ex1.ent.angles[1] = Lib.rand() % 360;
                    if (m.style != Defines.TE_EXPLOSION1_BIG) // PMM
                        ex1.ent.model = cl_mod_explo4; // PMM
                    else
                        ex1.ent.model = cl_mod_explo4_big;
                    if (Globals.rnd.nextFloat() < 0.5)
                        ex1.baseframe = 15;
                    ex1.frames = 15;
                    if (m.style != Defines.TE_EXPLOSION1_BIG && m.style != Defines.TE_EXPLOSION1_NP) // PMM
                        CL_fx.ExplosionParticles(m.position); // PMM
                    if (m.style == Defines.TE_ROCKET_EXPLOSION_WATER)
                        S.StartSound(m.position, 0, 0, cl_sfx_watrexp, 1, Defines.ATTN_NORM, 0);
                    else
                        S.StartSound(m.position, 0, 0, cl_sfx_rockexp, 1, Defines.ATTN_NORM, 0);
                    break;

                case TE_BFG_EXPLOSION:
                    explosion_t bfg = AllocExplosion();
                    Math3D.VectorCopy(m.position, bfg.ent.origin);
                    bfg.type = ex_poly;
                    bfg.ent.flags = Defines.RF_FULLBRIGHT;
                    bfg.start = ClientGlobals.cl.frame.servertime - 100;
                    bfg.light = 350;
                    bfg.lightcolor[0] = 0.0f;
                    bfg.lightcolor[1] = 1.0f;
                    bfg.lightcolor[2] = 0.0f;
                    bfg.ent.model = cl_mod_bfg_explo;
                    bfg.ent.flags |= Defines.RF_TRANSLUCENT;
                    bfg.ent.alpha = 0.30f;
                    bfg.frames = 4;
                    break;

                case TE_BFG_BIGEXPLOSION:
                    CL_fx.BFGExplosionParticles(m.position);
                    break;
                case TE_BOSSTPORT:
                    CL_fx.BigTeleportParticles(m.position);
                    S.StartSound(m.position, 0, 0, S.RegisterSound("misc/bigtele.wav"), 1, Defines.ATTN_NONE, 0);
                    break;

                case TE_PLAIN_EXPLOSION:
                    explosion_t ex2 = AllocExplosion();
                    Math3D.VectorCopy(m.position, ex2.ent.origin);
                    ex2.type = ex_poly;
                    ex2.ent.flags = Defines.RF_FULLBRIGHT;
                    ex2.start = ClientGlobals.cl.frame.servertime - 100;
                    ex2.light = 350;
                    ex2.lightcolor[0] = 1.0f;
                    ex2.lightcolor[1] = 0.5f;
                    ex2.lightcolor[2] = 0.5f;
                    ex2.ent.angles[1] = Lib.rand() % 360;
                    ex2.ent.model = cl_mod_explo4;
                    if (Globals.rnd.nextFloat() < 0.5)
                        ex2.baseframe = 15;
                    ex2.frames = 15;
                    // fixme: unreachable
                    if (msg.style == Defines.TE_ROCKET_EXPLOSION_WATER)
                        S.StartSound(m.position, 0, 0, cl_sfx_watrexp, 1, Defines.ATTN_NORM, 0);
                    else
                        S.StartSound(m.position, 0, 0, cl_sfx_rockexp, 1, Defines.ATTN_NORM, 0);
                    break;

                case TE_CHAINFIST_SMOKE:
                    CL_newfx.ParticleSmokeEffect(m.position, new float[]{0, 0, 1}, 0, 20, 20);
                    break;

                case TE_TRACKER_EXPLOSION:
                    CL_newfx.ColorFlash(m.position, 0, 150, -1, -1, -1);
                    CL_newfx.ColorExplosionParticles(m.position, 0, 1);
                    S.StartSound(m.position, 0, 0, cl_sfx_disrexp, 1, Defines.ATTN_NORM, 0);
                    break;

                case TE_TELEPORT_EFFECT:
                case TE_DBALL_GOAL:
                    CL_fx.TeleportParticles(m.position);
                    break;

                case TE_WIDOWSPLASH:
                    CL_newfx.WidowSplash(m.position);
                    break;
            }
        } else if (msg instanceof BeamOffsetTEMessage) {
            BeamOffsetTEMessage m = (BeamOffsetTEMessage) msg;
            ParseBeam2(cl_mod_grapple_cable, m);
        } else if (msg instanceof BeamTEMessage) {
            BeamTEMessage m = (BeamTEMessage) msg;
            switch (m.style) {
                case TE_PARASITE_ATTACK:
                case TE_MEDIC_CABLE_ATTACK:
                    ParseBeam(cl_mod_parasite_segment, m);
                    break;
                case Defines.TE_HEATBEAM:
                    ParsePlayerBeam(cl_mod_heatbeam, m);
                    break;
                case Defines.TE_MONSTER_HEATBEAM:
                    ParsePlayerBeam(cl_mod_monster_heatbeam, m);
                    break;
            }
        }
    }

    /*
    private static void unsupportedTents(int type) {
        int cnt;
        int color;
        int ent;
        switch (type) {

            case Defines.TE_LIGHTNING:
                ent = ParseLightning(cl_mod_lightning);
                S.StartSound(null, ent, Defines.CHAN_WEAPON, cl_sfx_lightning, 1,
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


            case Defines.TE_WIDOWBEAMOUT:
                ParseWidow();
                break;

            case Defines.TE_NUKEBLAST:
                ParseNuke();
                break;


            default:
                Com.Error(Defines.ERR_DROP, "CL_ParseTEnt: bad type");
        }

    }
*/
    // stack variable
    // dist, org
    private static final entity_t ent = new entity_t();
    /*
     * ================= CL_AddBeams =================
     */
    static void AddBeams() {
        int i, j;
        beam_t[] b;
        float d;
        float yaw, pitch;
        float forward;
        float len, steps;
        float model_length;

        //	   update beams
        b = cl_beams;
        for (i = 0; i < MAX_BEAMS; i++) {
            if (b[i].model == null || b[i].endtime < ClientGlobals.cl.time)
                continue;

            // if coming from the player, update the start position
            if (b[i].entity == ClientGlobals.cl.playernum + 1) // entity 0 is the
                                                         // world
            {
                Math3D.VectorCopy(ClientGlobals.cl.refdef.vieworg, b[i].start);
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
            ent.clear();
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

    // stack variable
    private static final float[] dist = new float[3];
    private static final float[] org = new float[3];
    private static final float[] f = new float[3];
    private static final float[] u = new float[3];
    private static final float[] r = new float[3];
    /*
     * ================= ROGUE - draw player locked beams CL_AddPlayerBeams
     * =================
     */
    static void AddPlayerBeams() {
        float d;
        //entity_t ent = new entity_t();
        float yaw, pitch;
        float forward;
        float len, steps;
        int framenum = 0;
        float model_length;

        float hand_multiplier;
        frame_t oldframe;
        player_state_t ps, ops;

        //	  PMM
        if (ClientGlobals.hand != null) {
            if (ClientGlobals.hand.value == 2)
                hand_multiplier = 0;
            else if (ClientGlobals.hand.value == 1)
                hand_multiplier = -1;
            else
                hand_multiplier = 1;
        } else {
            hand_multiplier = 1;
        }
        //	  PMM

        //	   update beams
        beam_t[] b = cl_playerbeams;
        for (int i = 0; i < MAX_BEAMS; i++) {

            if (b[i].model == null || b[i].endtime < ClientGlobals.cl.time)
                continue;

            if (cl_mod_heatbeam != null && (b[i].model == cl_mod_heatbeam)) {

                // if coming from the player, update the start position
                if (b[i].entity == ClientGlobals.cl.playernum + 1) // entity 0 is the
                                                             // world
                {
                    // set up gun position
                    // code straight out of CL_AddViewWeapon
                    ps = ClientGlobals.cl.frame.playerstate;
                    int j = (ClientGlobals.cl.frame.serverframe - 1)
                            & Defines.UPDATE_MASK;
                    oldframe = ClientGlobals.cl.frames[j];

                    if (oldframe.serverframe != ClientGlobals.cl.frame.serverframe - 1
                            || !oldframe.valid)
                        oldframe = ClientGlobals.cl.frame; // previous frame was
                                                     // dropped or involid

                    ops = oldframe.playerstate;
                    for (j = 0; j < 3; j++) {
                        b[i].start[j] = ClientGlobals.cl.refdef.vieworg[j]
                                + ops.gunoffset[j] + ClientGlobals.cl.lerpfrac
                                * (ps.gunoffset[j] - ops.gunoffset[j]);
                    }
                    Math3D.VectorMA(b[i].start,
                            (hand_multiplier * b[i].offset[0]),
                            ClientGlobals.cl.v_right, org);
                    Math3D.VectorMA(org, b[i].offset[1], ClientGlobals.cl.v_forward,
                            org);
                    Math3D.VectorMA(org, b[i].offset[2], ClientGlobals.cl.v_up, org);
                    if ((ClientGlobals.hand != null) && (ClientGlobals.hand.value == 2)) {
                        Math3D.VectorMA(org, -1, ClientGlobals.cl.v_up, org);
                    }
                    // FIXME - take these out when final
                    Math3D.VectorCopy(ClientGlobals.cl.v_right, r);
                    Math3D.VectorCopy(ClientGlobals.cl.v_forward, f);
                    Math3D.VectorCopy(ClientGlobals.cl.v_up, u);

                } else
                    Math3D.VectorCopy(b[i].start, org);
            } else {
                // if coming from the player, update the start position
                if (b[i].entity == ClientGlobals.cl.playernum + 1) // entity 0 is the
                                                             // world
                {
                    Math3D.VectorCopy(ClientGlobals.cl.refdef.vieworg, b[i].start);
                    b[i].start[2] -= 22; // adjust for view height
                }
                Math3D.VectorAdd(b[i].start, b[i].offset, org);
            }

            // calculate pitch and yaw
            Math3D.VectorSubtract(b[i].end, org, dist);

            //	  PMM
            if (cl_mod_heatbeam != null && (b[i].model == cl_mod_heatbeam)
                    && (b[i].entity == ClientGlobals.cl.playernum + 1)) {

                len = Math3D.VectorLength(dist);
                Math3D.VectorScale(f, len, dist);
                Math3D.VectorMA(dist, (hand_multiplier * b[i].offset[0]), r,
                        dist);
                Math3D.VectorMA(dist, b[i].offset[1], f, dist);
                Math3D.VectorMA(dist, b[i].offset[2], u, dist);
                if ((ClientGlobals.hand != null) && (ClientGlobals.hand.value == 2)) {
                    Math3D.VectorMA(org, -1, ClientGlobals.cl.v_up, org);
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
                if (b[i].entity != ClientGlobals.cl.playernum + 1) {
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
                    if (!Math3D.VectorEquals(b[i].offset, Globals.vec3_origin)) {
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
            if ((cl_mod_heatbeam != null && (b[i].model == cl_mod_heatbeam) && (b[i].entity == ClientGlobals.cl.playernum + 1))) {
                CL_newfx.Heatbeam(org, dist);
            }

            // add new entities for the beams
            d = Math3D.VectorNormalize(dist);

            //memset (&ent, 0, sizeof(ent));
            ent.clear();

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
                    ent.angles[2] = (ClientGlobals.cl.time) % 360;
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
            frac = (ClientGlobals.cl.time - ex[i].start) / 100.0f;
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
            ent.backlerp = 1.0f - ClientGlobals.cl.lerpfrac;

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
            if (l[i].endtime >= ClientGlobals.cl.time)
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
                if ((s[i].endtime >= ClientGlobals.cl.time)
                        && (ClientGlobals.cl.time >= s[i].nextthink)) {
                    s[i].think.think(s[i]);
                } else if (s[i].endtime < ClientGlobals.cl.time)
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