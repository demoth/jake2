/*
 * V.java
 * Copyright (C) 2003
 * 
 * $Id: V.java,v 1.7 2011-07-08 16:01:46 salomo Exp $
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
import jake2.game.Cmd;
import jake2.game.cvar_t;
import jake2.qcommon.Com;
import jake2.qcommon.Command;
import jake2.qcommon.Cvar;
import jake2.sys.Timer;
import jake2.util.Math3D;
import jake2.util.Vargs;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.List;

/**
 * V
 */
public final class V extends Globals {

    private static cvar_t cl_testblend;

    private static cvar_t cl_testparticles;

    private static cvar_t cl_testentities;

    private static cvar_t cl_testlights;

    private static cvar_t cl_stats;

    private static int r_numdlights;

    private static dlight_t[] r_dlights = new dlight_t[MAX_DLIGHTS];

    private static int r_numentities;

    private static entity_t[] r_entities = new entity_t[MAX_ENTITIES];

    private static int r_numparticles;

    //static particle_t[] r_particles = new particle_t[MAX_PARTICLES];

    private static lightstyle_t[] r_lightstyles = new lightstyle_t[MAX_LIGHTSTYLES];
    static {
        for (int i = 0; i < r_dlights.length; i++)
            r_dlights[i] = new dlight_t();
        for (int i = 0; i < r_entities.length; i++)
            r_entities[i] = new entity_t();
        for (int i = 0; i < r_lightstyles.length; i++)
            r_lightstyles[i] = new lightstyle_t();
    }

    /*
     * ==================== V_ClearScene
     * 
     * Specifies the model that will be used as the world ====================
     */
    private static void ClearScene() {
        r_numdlights = 0;
        r_numentities = 0;
        r_numparticles = 0;
    }

    /*
     * ===================== 
     * V_AddEntity
     * =====================
     */
    static void AddEntity(entity_t ent) {
    	
    	if ((ent.flags & Defines.RF_VIEWERMODEL) != 0) { //here is our client
    		int i;
    		for (i=0;i < 3;i++)
    			ent.oldorigin[i] = ent.origin[i] = cl.predicted_origin[i];
    		if (cl_3rd.value == 1)
    			ent.flags &=~ Defines.RF_VIEWERMODEL;
    	}
    	        
        if (r_numentities >= MAX_ENTITIES)
            return;
        r_entities[r_numentities++].set(ent);
    }

    /*
     * ===================== 
     * V_AddParticle
     * =====================
     */
    static void AddParticle(float[] org, int color, float alpha) {
        if (r_numparticles >= MAX_PARTICLES)
            return;

        int i = r_numparticles++;

        int c = particle_t.colorTable[color];
        c |= (int) (alpha * 255) << 24;
        particle_t.colorArray.put(i, c);

        i *= 3;
        FloatBuffer vertexBuf = particle_t.vertexArray;
        vertexBuf.put(i++, org[0]);
        vertexBuf.put(i++, org[1]);
        vertexBuf.put(i++, org[2]);
    }

    /*
     * ===================== 
     * V_AddLight
     * =====================
     */
    static void AddLight(float[] org, float intensity, float r, float g, float b) {
        dlight_t dl;

        if (r_numdlights >= MAX_DLIGHTS)
            return;
        dl = r_dlights[r_numdlights++];
        Math3D.VectorCopy(org, dl.origin);
        dl.intensity = intensity;
        dl.color[0] = r;
        dl.color[1] = g;
        dl.color[2] = b;
    }

    /*
     * ===================== 
     * V_AddLightStyle
     * =====================
     */
    static void AddLightStyle(int style, float r, float g, float b) {
        lightstyle_t ls;

        if (style < 0 || style > MAX_LIGHTSTYLES)
            Com.Error(ERR_DROP, "Bad light style " + style);
        ls = r_lightstyles[style];

        ls.white = r + g + b;
        ls.rgb[0] = r;
        ls.rgb[1] = g;
        ls.rgb[2] = b;
    }

    // stack variable
    private static final float[] origin = { 0, 0, 0 };
    
    /*
     * ================ 
     * V_TestParticles
     * 
     * If cl_testparticles is set, create 4096 particles in the view
     * ================
     */
    private static void TestParticles() {
        int i, j;
        float d, r, u;

        r_numparticles = 0;
        for (i = 0; i < MAX_PARTICLES; i++) {
            d = i * 0.25f;
            r = 4 * ((i & 7) - 3.5f);
            u = 4 * (((i >> 3) & 7) - 3.5f);

            for (j = 0; j < 3; j++)
                origin[j] = cl.refdef.vieworg[j] + cl.v_forward[j] * d
                        + cl.v_right[j] * r + cl.v_up[j] * u;

            AddParticle(origin, 8, cl_testparticles.value);
        }
    }

    /*
     * ================ 
     * V_TestEntities
     * 
     * If cl_testentities is set, create 32 player models
     * ================
     */
    private static void TestEntities() {
        int i, j;
        float f, r;
        entity_t ent;

        r_numentities = 32;
        //memset (r_entities, 0, sizeof(r_entities));
        for (i = 0; i < r_entities.length; i++)
        	r_entities[i].clear();

        for (i = 0; i < r_numentities; i++) {
            ent = r_entities[i];

            r = 64 * ((i % 4) - 1.5f);
            f = 64 * (i / 4) + 128;

            for (j = 0; j < 3; j++)
                ent.origin[j] = cl.refdef.vieworg[j] + cl.v_forward[j] * f
                        + cl.v_right[j] * r;

            ent.model = cl.baseclientinfo.model;
            ent.skin = cl.baseclientinfo.skin;
        }
    }

    /*
     * ================ 
     * V_TestLights
     * 
     * If cl_testlights is set, create 32 lights models 
     * ================
     */
    private static void TestLights() {
        int i, j;
        float f, r;
        dlight_t dl;

        r_numdlights = 32;
        //memset (r_dlights, 0, sizeof(r_dlights));
        for (i = 0; i < r_dlights.length; i++)
            r_dlights[i] = new dlight_t();

        for (i = 0; i < r_numdlights; i++) {
            dl = r_dlights[i];

            r = 64 * ((i % 4) - 1.5f);
            f = 64 * (i / 4) + 128;

            for (j = 0; j < 3; j++)
                dl.origin[j] = cl.refdef.vieworg[j] + cl.v_forward[j] * f
                        + cl.v_right[j] * r;
            dl.color[0] = ((i % 6) + 1) & 1;
            dl.color[1] = (((i % 6) + 1) & 2) >> 1;
            dl.color[2] = (((i % 6) + 1) & 4) >> 2;
            dl.intensity = 200;
        }
    }

    private static Command Gun_Next_f = (List<String> args) -> {
        gun_frame++;
        Com.Printf("frame " + gun_frame + "\n");
    };

    private static Command Gun_Prev_f = (List<String> args) -> {
        gun_frame--;
        if (gun_frame < 0)
            gun_frame = 0;
        Com.Printf("frame " + gun_frame + "\n");
    };

    private static Command Gun_Model_f = (List<String> args) -> {
        if (Cmd.Argc() != 2) {
            gun_model = null;
            return;
        }
        String name = "models/" + Cmd.Argv(1) + "/tris.md2";
        gun_model = re.RegisterModel(name);
    };

    /*
     * ================== 
     * V_RenderView
     * 
     * ==================
     */
    static void RenderView(float stereo_separation) {
        //		extern int entitycmpfnc( const entity_t *, const entity_t * );
        //
        if (cls.state != ca_active)
            return;

        if (!cl.refresh_prepped)
            return; // still loading

        if (cl_timedemo.value != 0.0f) {
            if (cl.timedemo_start == 0)
                cl.timedemo_start = Timer.Milliseconds();
            cl.timedemo_frames++;
        }

        // an invalid frame will just use the exact previous refdef
        // we can't use the old frame if the video mode has changed, though...
        if (cl.frame.valid && (cl.force_refdef || cl_paused.value == 0.0f)) {
            cl.force_refdef = false;

            V.ClearScene();

            // build a refresh entity list and calc cl.sim*
            // this also calls CL_CalcViewValues which loads
            // v_forward, etc.
            CL_ents.AddEntities();

            if (cl_testparticles.value != 0.0f)
                TestParticles();
            if (cl_testentities.value != 0.0f)
                TestEntities();
            if (cl_testlights.value != 0.0f)
                TestLights();
            if (cl_testblend.value != 0.0f) {
                cl.refdef.blend[0] = 1.0f;
                cl.refdef.blend[1] = 0.5f;
                cl.refdef.blend[2] = 0.25f;
                cl.refdef.blend[3] = 0.5f;
            }

            // offset vieworg appropriately if we're doing stereo separation
            if (stereo_separation != 0) {
                float[] tmp = new float[3];

                Math3D.VectorScale(cl.v_right, stereo_separation, tmp);
                Math3D.VectorAdd(cl.refdef.vieworg, tmp, cl.refdef.vieworg);
            }

            // never let it sit exactly on a node line, because a water plane
            // can
            // dissapear when viewed with the eye exactly on it.
            // the server protocol only specifies to 1/8 pixel, so add 1/16 in
            // each axis
            cl.refdef.vieworg[0] += 1.0 / 16;
            cl.refdef.vieworg[1] += 1.0 / 16;
            cl.refdef.vieworg[2] += 1.0 / 16;

            cl.refdef.x = scr_vrect.x;
            cl.refdef.y = scr_vrect.y;
            cl.refdef.width = scr_vrect.width;
            cl.refdef.height = scr_vrect.height;
            cl.refdef.fov_y = Math3D.CalcFov(cl.refdef.fov_x, cl.refdef.width,
                    cl.refdef.height);
            cl.refdef.time = cl.time * 0.001f;

            cl.refdef.areabits = cl.frame.areabits;
            
            // CDawg hud map 
            Math3D.VectorCopy (cl.refdef.viewangles, cl.mapdef.viewangles); 
            Math3D.VectorCopy (cl.refdef.vieworg, cl.mapdef.vieworg); 
            cl.mapdef.vieworg[0] += 1.0/16; 
            cl.mapdef.vieworg[1] += 1.0/16; 
            cl.mapdef.vieworg[2] += 1.0/16; 
            cl.mapdef.x = (int) (scr_vrect.x + viddef.getWidth() - (((viddef.getWidth() - (viddef.getWidth() * .80))/2) + (viddef.getWidth() * .80))); // sfranzyshen 
            cl.mapdef.y = (int) (scr_vrect.y + ((viddef.getHeight() - (viddef.getHeight() * .80))/2)); // sfranzyshen 
            cl.mapdef.width = (int) (viddef.getWidth() * .80); // sfranzyshen 
            cl.mapdef.height = (int) (viddef.getHeight() * .80); // sfranzyshen 
            cl.mapdef.map_zoom = (int) Globals.cl_map_zoom.value; 
            cl.mapdef.vieworg[2] += cl.mapdef.map_zoom; 
            cl.mapdef.blend[3] = 0.5f; //alpha black background 
            cl.mapdef.fov_y = Math3D.CalcFov (cl.refdef.fov_x, cl.refdef.width, cl.refdef.height); 
            cl.mapdef.time = (float) (cl.time *0.001); 
            cl.mapdef.viewangles[PITCH] = 90; //look down obviously ;) 
            cl.mapdef.areabits = cl.frame.areabits; 
            cl.mapdef.lightstyles = r_lightstyles; 
            // CDawg hud map 

            if (cl_add_entities.value == 0.0f)
                r_numentities = 0;
            if (cl_add_particles.value == 0.0f)
                r_numparticles = 0;
            if (cl_add_lights.value == 0.0f)
                r_numdlights = 0;
            if (cl_add_blend.value == 0) {
                Math3D.VectorClear(cl.refdef.blend);
            }

            cl.refdef.num_entities = r_numentities;
            cl.refdef.entities = r_entities;
            cl.refdef.num_particles = r_numparticles;
            cl.refdef.num_dlights = r_numdlights;
            cl.refdef.dlights = r_dlights;
            cl.refdef.lightstyles = r_lightstyles;

            cl.refdef.rdflags = cl.frame.playerstate.rdflags;

            // sort entities for better cache locality
            // !!! useless in Java !!!
            //Arrays.sort(cl.refdef.entities, entitycmpfnc);
        }

        re.RenderFrame(cl.refdef);
        
        // CDawg 
        cl.mapdef.map_view = (int) Globals.cl_map.value; 
        
        if (Globals.cl.mapdef.map_view != 0) 
        	re.RenderFrame (cl.mapdef); 
        
        // CDawg 
        
        if (cl_stats.value != 0.0f)
            Com.Printf("ent:%i  lt:%i  part:%i\n", new Vargs(3).add(
                    r_numentities).add(r_numdlights).add(r_numparticles));
        if (log_stats.value != 0.0f && (log_stats_file != null))
            try {
                log_stats_file.write(r_numentities + "," + r_numdlights + ","
                        + r_numparticles);
            } catch (IOException e) {
            }

        SCR.AddDirtyPoint(scr_vrect.x, scr_vrect.y);
        SCR.AddDirtyPoint(scr_vrect.x + scr_vrect.width - 1, scr_vrect.y
                + scr_vrect.height - 1);

        SCR.DrawCrosshair();
    }

    /*
     * ============= V_Viewpos_f =============
     */
    private static Command Viewpos_f = (List<String> args) -> Com.Printf("(%i %i %i) : %i\n", new Vargs(4).add(
            (int) cl.refdef.vieworg[0]).add((int) cl.refdef.vieworg[1])
            .add((int) cl.refdef.vieworg[2]).add(
                    (int) cl.refdef.viewangles[YAW]));

    public static void Init() {
        Cmd.AddCommand("gun_next", Gun_Next_f);
        Cmd.AddCommand("gun_prev", Gun_Prev_f);
        Cmd.AddCommand("gun_model", Gun_Model_f);

        Cmd.AddCommand("viewpos", Viewpos_f);

        crosshair = Cvar.Get("crosshair", "0", CVAR_ARCHIVE);

        cl_testblend = Cvar.Get("cl_testblend", "0", 0);
        cl_testparticles = Cvar.Get("cl_testparticles", "0", 0);
        cl_testentities = Cvar.Get("cl_testentities", "0", 0);
        cl_testlights = Cvar.Get("cl_testlights", "0", 0);

        cl_stats = Cvar.Get("cl_stats", "0", 0);
    }
}