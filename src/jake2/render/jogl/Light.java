/*
 * Light.java
 * Copyright (C) 2003
 *
 * $Id: Light.java,v 1.7 2004-09-22 19:22:16 salomo Exp $
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
package jake2.render.jogl;

import jake2.Defines;
import jake2.Globals;
import jake2.client.dlight_t;
import jake2.client.lightstyle_t;
import jake2.game.cplane_t;
import jake2.qcommon.Com;
import jake2.qcommon.longjmpException;
import jake2.render.mnode_t;
import jake2.render.msurface_t;
import jake2.render.mtexinfo_t;
import jake2.util.Math3D;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;

import net.java.games.jogl.GL;

/**
 * Light
 * 
 * @author cwei
 */
public abstract class Light extends Warp {
    // r_light.c

    int r_dlightframecount;

    static final int DLIGHT_CUTOFF = 64;

    /*
     * =============================================================================
     * 
     * DYNAMIC LIGHTS BLEND RENDERING
     * 
     * =============================================================================
     */

    void R_RenderDlight(dlight_t light) {
        int i, j;
        float a;
        float[] v = { 0, 0, 0 };
        float rad;

        rad = light.intensity * 0.35f;

        Math3D.VectorSubtract(light.origin, r_origin, v);

        gl.glBegin(GL.GL_TRIANGLE_FAN);
        gl.glColor3f(light.color[0] * 0.2f, light.color[1] * 0.2f,
                light.color[2] * 0.2f);
        for (i = 0; i < 3; i++)
            v[i] = light.origin[i] - vpn[i] * rad;
        gl.glVertex3f(v[0], v[1], v[2]);
        gl.glColor3f(0, 0, 0);
        for (i = 16; i >= 0; i--) {
            a = (float) (i / 16.0f * Math.PI * 2);
            for (j = 0; j < 3; j++)
                v[j] = (float) (light.origin[j] + vright[j] * Math.cos(a) * rad + vup[j]
                        * Math.sin(a) * rad);
            gl.glVertex3f(v[0], v[1], v[2]);
        }
        gl.glEnd();
    }

    /*
     * ============= R_RenderDlights =============
     */
    void R_RenderDlights() {
        int i;
        dlight_t l;

        if (gl_flashblend.value == 0)
            return;

        r_dlightframecount = r_framecount + 1; // because the count hasn't
        //  advanced yet for this frame
        gl.glDepthMask(false);
        gl.glDisable(GL.GL_TEXTURE_2D);
        gl.glShadeModel(GL.GL_SMOOTH);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE);

        for (i = 0; i < r_newrefdef.num_dlights; i++) {
            l = r_newrefdef.dlights[i];
            R_RenderDlight(l);
        }

        gl.glColor3f(1, 1, 1);
        gl.glDisable(GL.GL_BLEND);
        gl.glEnable(GL.GL_TEXTURE_2D);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
        gl.glDepthMask(true);
    }

    /*
     * =============================================================================
     * 
     * DYNAMIC LIGHTS
     * 
     * =============================================================================
     */

    /*
     * ============= R_MarkLights =============
     */
    void R_MarkLights(dlight_t light, int bit, mnode_t node) {
        cplane_t splitplane;
        float dist;
        msurface_t surf;
        int i;
        int sidebit;

        if (node.contents != -1)
            return;

        splitplane = node.plane;
        dist = Math3D.DotProduct(light.origin, splitplane.normal)
                - splitplane.dist;

        if (dist > light.intensity - DLIGHT_CUTOFF) {
            R_MarkLights(light, bit, node.children[0]);
            return;
        }
        if (dist < -light.intensity + DLIGHT_CUTOFF) {
            R_MarkLights(light, bit, node.children[1]);
            return;
        }

        // mark the polygons
        for (i = 0; i < node.numsurfaces; i++) {

            surf = r_worldmodel.surfaces[node.firstsurface + i];

            /*
             * cwei bugfix for dlight behind the walls
             */
            dist = Math3D.DotProduct(light.origin, surf.plane.normal)
                    - surf.plane.dist;
            sidebit = (dist >= 0) ? 0 : Defines.SURF_PLANEBACK;
            if ((surf.flags & Defines.SURF_PLANEBACK) != sidebit)
                continue;
            /*
             * cwei bugfix end
             */

            if (surf.dlightframe != r_dlightframecount) {
                surf.dlightbits = 0;
                surf.dlightframe = r_dlightframecount;
            }
            surf.dlightbits |= bit;
        }

        R_MarkLights(light, bit, node.children[0]);
        R_MarkLights(light, bit, node.children[1]);
    }

    /*
     * ============= R_PushDlights =============
     */
    void R_PushDlights() {
        int i;
        dlight_t l;

        if (gl_flashblend.value != 0)
            return;

        r_dlightframecount = r_framecount + 1; // because the count hasn't
        //  advanced yet for this frame
        for (i = 0; i < r_newrefdef.num_dlights; i++) {
            l = r_newrefdef.dlights[i];
            R_MarkLights(l, 1 << i, r_worldmodel.nodes[0]);
        }
    }

    /*
     * =============================================================================
     * 
     * LIGHT SAMPLING
     * 
     * =============================================================================
     */

    float[] pointcolor = { 0, 0, 0 }; // vec3_t

    cplane_t lightplane; // used as shadow plane

    float[] lightspot = { 0, 0, 0 }; // vec3_t

    int RecursiveLightPoint(mnode_t node, float[] start, float[] end) {
        if (node.contents != -1)
            return -1; // didn't hit anything

        msurface_t surf;
        int s, t, ds, dt;
        int i;
        mtexinfo_t tex;
        ByteBuffer lightmap;
        int maps;
        float[] mid = { 0, 0, 0 };

        // calculate mid point

        // FIXME: optimize for axial
        cplane_t plane = node.plane;
        float front = Math3D.DotProduct(start, plane.normal) - plane.dist;
        float back = Math3D.DotProduct(end, plane.normal) - plane.dist;
        boolean side = (front < 0);
        int sideIndex = (side) ? 1 : 0;

        if ((back < 0) == side)
            return RecursiveLightPoint(node.children[sideIndex], start, end);

        float frac = front / (front - back);
        mid[0] = start[0] + (end[0] - start[0]) * frac;
        mid[1] = start[1] + (end[1] - start[1]) * frac;
        mid[2] = start[2] + (end[2] - start[2]) * frac;

        // go down front side
        int r = RecursiveLightPoint(node.children[sideIndex], start, mid);
        if (r >= 0)
            return r; // hit something

        if ((back < 0) == side)
            return -1; // didn't hit anuthing

        // check for impact on this node
        Math3D.VectorCopy(mid, lightspot);
        lightplane = plane;

        int surfIndex = node.firstsurface;
        float[] scale = { 0, 0, 0 };
        for (i = 0; i < node.numsurfaces; i++, surfIndex++) {
            surf = r_worldmodel.surfaces[surfIndex];

            if ((surf.flags & (Defines.SURF_DRAWTURB | Defines.SURF_DRAWSKY)) != 0)
                continue; // no lightmaps

            tex = surf.texinfo;

            s = (int) (Math3D.DotProduct(mid, tex.vecs[0]) + tex.vecs[0][3]);
            t = (int) (Math3D.DotProduct(mid, tex.vecs[1]) + tex.vecs[1][3]);

            if (s < surf.texturemins[0] || t < surf.texturemins[1])
                continue;

            ds = s - surf.texturemins[0];
            dt = t - surf.texturemins[1];

            if (ds > surf.extents[0] || dt > surf.extents[1])
                continue;

            if (surf.samples == null)
                return 0;

            ds >>= 4;
            dt >>= 4;

            lightmap = surf.samples;
            int lightmapIndex = 0;

            Math3D.VectorCopy(Globals.vec3_origin, pointcolor);
            if (lightmap != null) {
                //float[] scale = {0, 0, 0};
                float[] rgb;
                lightmapIndex += 3 * (dt * ((surf.extents[0] >> 4) + 1) + ds);

                for (maps = 0; maps < Defines.MAXLIGHTMAPS
                        && surf.styles[maps] != (byte) 255; maps++) {
                    rgb = r_newrefdef.lightstyles[surf.styles[maps] & 0xFF].rgb;
                    scale[0] = gl_modulate.value * rgb[0];
                    scale[1] = gl_modulate.value * rgb[1];
                    scale[2] = gl_modulate.value * rgb[2];

                    pointcolor[0] += (lightmap.get(lightmapIndex + 0) & 0xFF)
                            * scale[0] * (1.0f / 255);
                    pointcolor[1] += (lightmap.get(lightmapIndex + 1) & 0xFF)
                            * scale[1] * (1.0f / 255);
                    pointcolor[2] += (lightmap.get(lightmapIndex + 2) & 0xFF)
                            * scale[2] * (1.0f / 255);
                    lightmapIndex += 3 * ((surf.extents[0] >> 4) + 1)
                            * ((surf.extents[1] >> 4) + 1);
                }
            }
            return 1;
        }

        // go down back side
        return RecursiveLightPoint(node.children[1 - sideIndex], mid, end);
    }

    /*
     * =============== R_LightPoint ===============
     */
    void R_LightPoint(float[] p, float[] color) {
        assert (p.length == 3) : "vec3_t bug";
        assert (color.length == 3) : "rgb bug";

        float[] end = { 0, 0, 0 };
        dlight_t dl;
        float add;

        if (r_worldmodel.lightdata == null) {
            color[0] = color[1] = color[2] = 1.0f;
            return;
        }

        end[0] = p[0];
        end[1] = p[1];
        end[2] = p[2] - 2048;

        float r = RecursiveLightPoint(r_worldmodel.nodes[0], p, end);

        if (r == -1) {
            Math3D.VectorCopy(Globals.vec3_origin, color);
        } else {
            Math3D.VectorCopy(pointcolor, color);
        }

        //
        // add dynamic lights
        //
        for (int lnum = 0; lnum < r_newrefdef.num_dlights; lnum++) {
            dl = r_newrefdef.dlights[lnum];

            Math3D.VectorSubtract(currententity.origin, dl.origin, end);
            add = dl.intensity - Math3D.VectorLength(end);
            add *= (1.0f / 256);
            if (add > 0) {
                Math3D.VectorMA(color, add, dl.color, color);
            }
        }

        Math3D.VectorScale(color, gl_modulate.value, color);
    }

    //	  ===================================================================

    float[] s_blocklights = new float[34 * 34 * 3];

    /*
     * =============== R_AddDynamicLights ===============
     */
    void R_AddDynamicLights(msurface_t surf) {
        int lnum;
        int sd, td;
        float fdist, frad, fminlight;
        float[] impact = { 0, 0, 0 };
        float[] local = { 0, 0, 0 };
        int s, t;
        int i;
        int smax, tmax;
        mtexinfo_t tex;
        dlight_t dl;
        float[] pfBL;
        float fsacc, ftacc;

        smax = (surf.extents[0] >> 4) + 1;
        tmax = (surf.extents[1] >> 4) + 1;
        tex = surf.texinfo;

        for (lnum = 0; lnum < r_newrefdef.num_dlights; lnum++) {
            if ((surf.dlightbits & (1 << lnum)) == 0)
                continue; // not lit by this light

            dl = r_newrefdef.dlights[lnum];
            frad = dl.intensity;
            fdist = Math3D.DotProduct(dl.origin, surf.plane.normal)
                    - surf.plane.dist;
            frad -= Math.abs(fdist);
            // rad is now the highest intensity on the plane

            fminlight = DLIGHT_CUTOFF; // FIXME: make configurable?
            if (frad < fminlight)
                continue;
            fminlight = frad - fminlight;

            for (i = 0; i < 3; i++) {
                impact[i] = dl.origin[i] - surf.plane.normal[i] * fdist;
            }

            local[0] = Math3D.DotProduct(impact, tex.vecs[0]) + tex.vecs[0][3]
                    - surf.texturemins[0];
            local[1] = Math3D.DotProduct(impact, tex.vecs[1]) + tex.vecs[1][3]
                    - surf.texturemins[1];

            pfBL = s_blocklights;
            int pfBLindex = 0;
            for (t = 0, ftacc = 0; t < tmax; t++, ftacc += 16) {
                td = (int) (local[1] - ftacc);
                if (td < 0)
                    td = -td;

                for (s = 0, fsacc = 0; s < smax; s++, fsacc += 16, pfBLindex += 3) {
                    sd = (int) (local[0] - fsacc);

                    if (sd < 0)
                        sd = -sd;

                    if (sd > td)
                        fdist = sd + (td >> 1);
                    else
                        fdist = td + (sd >> 1);

                    if (fdist < fminlight) {
                        pfBL[pfBLindex + 0] += (frad - fdist) * dl.color[0];
                        pfBL[pfBLindex + 1] += (frad - fdist) * dl.color[1];
                        pfBL[pfBLindex + 2] += (frad - fdist) * dl.color[2];
                    }
                }
            }
        }
    }

    /*
     * * R_SetCacheState
     */
    void R_SetCacheState(msurface_t surf) {
        int maps;

        for (maps = 0; maps < Defines.MAXLIGHTMAPS
                && surf.styles[maps] != (byte) 255; maps++) {
            surf.cached_light[maps] = r_newrefdef.lightstyles[surf.styles[maps] & 0xFF].white;
        }
    }

    /*
     * =============== R_BuildLightMap
     * 
     * Combine and scale multiple lightmaps into the floating format in
     * blocklights ===============
     */
    void R_BuildLightMap(msurface_t surf, IntBuffer dest, int stride) {
        int smax, tmax;
        int r, g, b, a, max;
        int i, j, size;
        ByteBuffer lightmap;
        float[] scale = { 0, 0, 0, 0 };
        int nummaps;
        float[] bl;
        lightstyle_t style;
        int monolightmap;

        if ((surf.texinfo.flags & (Defines.SURF_SKY | Defines.SURF_TRANS33
                | Defines.SURF_TRANS66 | Defines.SURF_WARP)) != 0)
            Com.Error(Defines.ERR_DROP,
                    "R_BuildLightMap called for non-lit surface");

        smax = (surf.extents[0] >> 4) + 1;
        tmax = (surf.extents[1] >> 4) + 1;
        size = smax * tmax;
        if (size > ((s_blocklights.length * Defines.SIZE_OF_FLOAT) >> 4))
            Com.Error(Defines.ERR_DROP, "Bad s_blocklights size");

        try {
            // set to full bright if no light data
            if (surf.samples == null) {
                int maps;

                for (i = 0; i < size * 3; i++)
                    s_blocklights[i] = 255;

                for (maps = 0; maps < Defines.MAXLIGHTMAPS
                        && surf.styles[maps] != (byte) 255; maps++) {
                    style = r_newrefdef.lightstyles[surf.styles[maps] & 0xFF];
                }
                // goto store;
                throw new longjmpException();
            }

            // count the # of maps
            for (nummaps = 0; nummaps < Defines.MAXLIGHTMAPS
                    && surf.styles[nummaps] != (byte) 255; nummaps++)
                ;

            lightmap = surf.samples;
            int lightmapIndex = 0;

            // add all the lightmaps
            if (nummaps == 1) {
                int maps;

                for (maps = 0; maps < Defines.MAXLIGHTMAPS
                        && surf.styles[maps] != (byte) 255; maps++) {
                    bl = s_blocklights;
                    int blp = 0;

                    for (i = 0; i < 3; i++)
                        scale[i] = gl_modulate.value
                                * r_newrefdef.lightstyles[surf.styles[maps] & 0xFF].rgb[i];

                    if (scale[0] == 1.0F && scale[1] == 1.0F
                            && scale[2] == 1.0F) {
                        for (i = 0; i < size; i++) {
                            bl[blp++] = lightmap.get(lightmapIndex++) & 0xFF;
                            bl[blp++] = lightmap.get(lightmapIndex++) & 0xFF;
                            bl[blp++] = lightmap.get(lightmapIndex++) & 0xFF;
                        }
                    } else {
                        for (i = 0; i < size; i++) {
                            bl[blp++] = (lightmap.get(lightmapIndex++) & 0xFF)
                                    * scale[0];
                            bl[blp++] = (lightmap.get(lightmapIndex++) & 0xFF)
                                    * scale[1];
                            bl[blp++] = (lightmap.get(lightmapIndex++) & 0xFF)
                                    * scale[2];
                        }
                    }
                    //lightmap += size*3; // skip to next lightmap
                }
            } else {
                int maps;

                //			memset( s_blocklights, 0, sizeof( s_blocklights[0] ) * size *
                // 3 );

                Arrays.fill(s_blocklights, 0, size * 3, 0.0f);

                for (maps = 0; maps < Defines.MAXLIGHTMAPS
                        && surf.styles[maps] != (byte) 255; maps++) {
                    bl = s_blocklights;
                    int blp = 0;

                    for (i = 0; i < 3; i++)
                        scale[i] = gl_modulate.value
                                * r_newrefdef.lightstyles[surf.styles[maps] & 0xFF].rgb[i];

                    if (scale[0] == 1.0F && scale[1] == 1.0F
                            && scale[2] == 1.0F) {
                        for (i = 0; i < size; i++) {
                            bl[blp++] += lightmap.get(lightmapIndex++) & 0xFF;
                            bl[blp++] += lightmap.get(lightmapIndex++) & 0xFF;
                            bl[blp++] += lightmap.get(lightmapIndex++) & 0xFF;
                        }
                    } else {
                        for (i = 0; i < size; i++) {
                            bl[blp++] += (lightmap.get(lightmapIndex++) & 0xFF)
                                    * scale[0];
                            bl[blp++] += (lightmap.get(lightmapIndex++) & 0xFF)
                                    * scale[1];
                            bl[blp++] += (lightmap.get(lightmapIndex++) & 0xFF)
                                    * scale[2];
                        }
                    }
                    //lightmap += size*3; // skip to next lightmap
                }
            }

            // add all the dynamic lights
            if (surf.dlightframe == r_framecount)
                R_AddDynamicLights(surf);

            // label store:
        } catch (longjmpException store) {
        }

        // put into texture format
        stride -= smax;
        bl = s_blocklights;
        int blp = 0;

        monolightmap = gl_monolightmap.string.charAt(0);

        int destp = 0;

        if (monolightmap == '0') {
            for (i = 0; i < tmax; i++, destp += stride) {
                dest.position(destp);

                for (j = 0; j < smax; j++) {

                    r = (int) bl[blp++];
                    g = (int) bl[blp++];
                    b = (int) bl[blp++];

                    // catch negative lights
                    if (r < 0)
                        r = 0;
                    if (g < 0)
                        g = 0;
                    if (b < 0)
                        b = 0;

                    /*
                     * * determine the brightest of the three color components
                     */
                    if (r > g)
                        max = r;
                    else
                        max = g;
                    if (b > max)
                        max = b;

                    /*
                     * * alpha is ONLY used for the mono lightmap case. For this
                     * reason * we set it to the brightest of the color
                     * components so that * things don't get too dim.
                     */
                    a = max;

                    /*
                     * * rescale all the color components if the intensity of
                     * the greatest * channel exceeds 1.0
                     */
                    if (max > 255) {
                        float t = 255.0F / max;

                        r = (int) (r * t);
                        g = (int) (g * t);
                        b = (int) (b * t);
                        a = (int) (a * t);
                    }
                    r &= 0xFF;
                    g &= 0xFF;
                    b &= 0xFF;
                    a &= 0xFF;
                    dest.put(destp++, (a << 24) | (b << 16) | (g << 8)
                            | (r << 0));
                }
            }
        } else {
            for (i = 0; i < tmax; i++, destp += stride) {
                dest.position(destp);

                for (j = 0; j < smax; j++) {

                    r = (int) bl[blp++];
                    g = (int) bl[blp++];
                    b = (int) bl[blp++];

                    // catch negative lights
                    if (r < 0)
                        r = 0;
                    if (g < 0)
                        g = 0;
                    if (b < 0)
                        b = 0;

                    /*
                     * * determine the brightest of the three color components
                     */
                    if (r > g)
                        max = r;
                    else
                        max = g;
                    if (b > max)
                        max = b;

                    /*
                     * * alpha is ONLY used for the mono lightmap case. For this
                     * reason * we set it to the brightest of the color
                     * components so that * things don't get too dim.
                     */
                    a = max;

                    /*
                     * * rescale all the color components if the intensity of
                     * the greatest * channel exceeds 1.0
                     */
                    if (max > 255) {
                        float t = 255.0F / max;

                        r = (int) (r * t);
                        g = (int) (g * t);
                        b = (int) (b * t);
                        a = (int) (a * t);
                    }

                    /*
                     * * So if we are doing alpha lightmaps we need to set the
                     * R, G, and B * components to 0 and we need to set alpha to
                     * 1-alpha.
                     */
                    switch (monolightmap) {
                    case 'L':
                    case 'I':
                        r = a;
                        g = b = 0;
                        break;
                    case 'C':
                        // try faking colored lighting
                        a = 255 - ((r + g + b) / 3);
                        r *= a / 255.0f;
                        g *= a / 255.0f;
                        b *= a / 255.0f;
                        break;
                    case 'A':
                    default:
                        r = g = b = 0;
                        a = 255 - a;
                        break;
                    }
                    r &= 0xFF;
                    g &= 0xFF;
                    b &= 0xFF;
                    a &= 0xFF;
                    dest.put(destp++, (a << 24) | (b << 16) | (g << 8)
                            | (r << 0));
                }
            }
        }
    }

}