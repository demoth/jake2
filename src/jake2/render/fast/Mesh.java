/*
 * Mesh.java
 * Copyright (C) 2003
 *
 * $Id: Mesh.java,v 1.4 2007-02-27 13:32:34 cawe Exp $
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
package jake2.render.fast;

import jake2.Defines;
import jake2.client.VID;
import jake2.client.entity_t;
import jake2.qcommon.qfiles;
import jake2.render.Anorms;
import jake2.render.image_t;
import jake2.util.Lib;
import jake2.util.Math3D;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Mesh
 * 
 * @author cwei
 */
public abstract class Mesh extends Light {

    // g_mesh.c: triangle model functions
    /*
     * =============================================================
     * 
     * ALIAS MODELS
     * 
     * =============================================================
     */

    static final int NUMVERTEXNORMALS = 162;

    float[][] r_avertexnormals = Anorms.VERTEXNORMALS;

    float[] shadevector = { 0, 0, 0 };

    float[] shadelight = { 0, 0, 0 };

    // precalculated dot products for quantized angles
    static final int SHADEDOT_QUANT = 16;

    float[][] r_avertexnormal_dots = Anorms.VERTEXNORMAL_DOTS;

    float[] shadedots = r_avertexnormal_dots[0];

    /**
     * GL_LerpVerts
     * 
     * @param nverts
     * @param ov
     * @param verts
     * @param move
     * @param frontv
     * @param backv
     */
    void GL_LerpVerts(int nverts, int[] ov, int[] v, float[] move,
	    float[] frontv, float[] backv) {
	FloatBuffer lerp = vertexArrayBuf;
	lerp.limit((nverts << 2) - nverts); // nverts * 3

	int ovv, vv;
	// PMM -- added RF_SHELL_DOUBLE, RF_SHELL_HALF_DAM
	if ((currententity.flags & (Defines.RF_SHELL_RED
		| Defines.RF_SHELL_GREEN | Defines.RF_SHELL_BLUE
		| Defines.RF_SHELL_DOUBLE | Defines.RF_SHELL_HALF_DAM)) != 0) {
	    float[] normal;
	    int j = 0;
	    for (int i = 0; i < nverts; i++/* , v++, ov++, lerp+=4 */) {
		vv = v[i];
		normal = r_avertexnormals[(vv >>> 24) & 0xFF];
		ovv = ov[i];
		lerp.put(j, move[0] + (ovv & 0xFF) * backv[0] + (vv & 0xFF)
			* frontv[0] + normal[0] * Defines.POWERSUIT_SCALE);
		lerp.put(j + 1, move[1] + ((ovv >>> 8) & 0xFF) * backv[1]
			+ ((vv >>> 8) & 0xFF) * frontv[1] + normal[1]
			* Defines.POWERSUIT_SCALE);
		lerp.put(j + 2, move[2] + ((ovv >>> 16) & 0xFF) * backv[2]
			+ ((vv >>> 16) & 0xFF) * frontv[2] + normal[2]
			* Defines.POWERSUIT_SCALE);
		j += 3;
	    }
	} else {
	    int j = 0;
	    for (int i = 0; i < nverts; i++ /* , v++, ov++, lerp+=4 */) {
		ovv = ov[i];
		vv = v[i];

		lerp.put(j, move[0] + (ovv & 0xFF) * backv[0] + (vv & 0xFF)
			* frontv[0]);
		lerp.put(j + 1, move[1] + ((ovv >>> 8) & 0xFF) * backv[1]
			+ ((vv >>> 8) & 0xFF) * frontv[1]);
		lerp.put(j + 2, move[2] + ((ovv >>> 16) & 0xFF) * backv[2]
			+ ((vv >>> 16) & 0xFF) * frontv[2]);
		j += 3;
	    }
	}
    }

    private final FloatBuffer colorArrayBuf = Lib.newFloatBuffer(qfiles.MAX_VERTS * 4);

    private final FloatBuffer vertexArrayBuf = Lib.newFloatBuffer(qfiles.MAX_VERTS * 3);

    private final FloatBuffer textureArrayBuf = Lib.newFloatBuffer(qfiles.MAX_VERTS * 2);

    boolean isFilled = false;

    float[] tmpVec = { 0, 0, 0 };

    float[][] vectors = { { 0, 0, 0 }, { 0, 0, 0 }, { 0, 0, 0 } // 3 mal vec3_t
    };

    // stack variable
    private final float[] move = { 0, 0, 0 }; // vec3_t

    private final float[] frontv = { 0, 0, 0 }; // vec3_t

    private final float[] backv = { 0, 0, 0 }; // vec3_t

    /**
     * GL_DrawAliasFrameLerp
     * 
     * interpolates between two frames and origins FIXME: batch lerp all
     * vertexes
     */
    void GL_DrawAliasFrameLerp(qfiles.dmdl_t paliashdr, float backlerp) {
	qfiles.daliasframe_t frame = paliashdr.aliasFrames[currententity.frame];

	int[] verts = frame.verts;

	qfiles.daliasframe_t oldframe = paliashdr.aliasFrames[currententity.oldframe];

	int[] ov = oldframe.verts;

	float alpha;
	if ((currententity.flags & Defines.RF_TRANSLUCENT) != 0)
	    alpha = currententity.alpha;
	else
	    alpha = 1.0f;

	// PMM - added double shell
	if ((currententity.flags & (Defines.RF_SHELL_RED
		| Defines.RF_SHELL_GREEN | Defines.RF_SHELL_BLUE
		| Defines.RF_SHELL_DOUBLE | Defines.RF_SHELL_HALF_DAM)) != 0)
	    gl.glDisable(GL_TEXTURE_2D);

	float frontlerp = 1.0f - backlerp;

	// move should be the delta back to the previous frame * backlerp
	Math3D.VectorSubtract(currententity.oldorigin, currententity.origin,
		frontv);
	Math3D.AngleVectors(currententity.angles, vectors[0], vectors[1],
		vectors[2]);

	move[0] = Math3D.DotProduct(frontv, vectors[0]); // forward
	move[1] = -Math3D.DotProduct(frontv, vectors[1]); // left
	move[2] = Math3D.DotProduct(frontv, vectors[2]); // up

	Math3D.VectorAdd(move, oldframe.translate, move);

	for (int i = 0; i < 3; i++) {
	    move[i] = backlerp * move[i] + frontlerp * frame.translate[i];
	    frontv[i] = frontlerp * frame.scale[i];
	    backv[i] = backlerp * oldframe.scale[i];
	}

	// ab hier wird optimiert

	GL_LerpVerts(paliashdr.num_xyz, ov, verts, move, frontv, backv);

	// gl.gl.glEnableClientState( GL_VERTEX_ARRAY );
	gl.glVertexPointer(3, 0, vertexArrayBuf);

	// PMM - added double damage shell
	if ((currententity.flags & (Defines.RF_SHELL_RED
		| Defines.RF_SHELL_GREEN | Defines.RF_SHELL_BLUE
		| Defines.RF_SHELL_DOUBLE | Defines.RF_SHELL_HALF_DAM)) != 0) {
	    gl.glColor4f(shadelight[0], shadelight[1], shadelight[2], alpha);
	} else {
	    gl.glEnableClientState(GL_COLOR_ARRAY);
	    gl.glColorPointer(4, 0, colorArrayBuf);

	    //
	    // pre light everything
	    //
	    FloatBuffer color = colorArrayBuf;
	    float l;
	    int size = paliashdr.num_xyz;
	    int j = 0;
	    for (int i = 0; i < size; i++) {
		l = shadedots[(verts[i] >>> 24) & 0xFF];
		color.put(j, l * shadelight[0]);
		color.put(j + 1, l * shadelight[1]);
		color.put(j + 2, l * shadelight[2]);
		color.put(j + 3, alpha);
		j += 4;
	    }
	}

	gl.glClientActiveTextureARB(TEXTURE0);
	gl.glTexCoordPointer(2, 0, textureArrayBuf);
	// gl.gl.glEnableClientState( GL_TEXTURE_COORD_ARRAY);

	int pos = 0;
	int[] counts = paliashdr.counts;

	IntBuffer srcIndexBuf = null;

	FloatBuffer dstTextureCoords = textureArrayBuf;
	FloatBuffer srcTextureCoords = paliashdr.textureCoordBuf;

	int dstIndex = 0;
	int srcIndex = 0;
	int count;
	int mode;
	int size = counts.length;
	for (int j = 0; j < size; j++) {

	    // get the vertex count and primitive type
	    count = counts[j];
	    if (count == 0)
		break; // done

	    srcIndexBuf = paliashdr.indexElements[j];

	    mode = GL_TRIANGLE_STRIP;
	    if (count < 0) {
		mode = GL_TRIANGLE_FAN;
		count = -count;
	    }
	    srcIndex = pos << 1;
	    srcIndex--;
	    for (int k = 0; k < count; k++) {
		dstIndex = srcIndexBuf.get(k) << 1;
		dstTextureCoords
			.put(dstIndex, srcTextureCoords.get(++srcIndex));
		dstTextureCoords.put(++dstIndex, srcTextureCoords
			.get(++srcIndex));
	    }

	    gl.glDrawElements(mode, srcIndexBuf);
	    pos += count;
	}

	// PMM - added double damage shell
	if ((currententity.flags & (Defines.RF_SHELL_RED
		| Defines.RF_SHELL_GREEN | Defines.RF_SHELL_BLUE
		| Defines.RF_SHELL_DOUBLE | Defines.RF_SHELL_HALF_DAM)) != 0)
	    gl.glEnable(GL_TEXTURE_2D);

	gl.glDisableClientState(GL_COLOR_ARRAY);
    }

    private final float[] point = { 0, 0, 0 };

    /**
     * GL_DrawAliasShadow
     */
    void GL_DrawAliasShadow(qfiles.dmdl_t paliashdr, int posenum) {
	float lheight = currententity.origin[2] - lightspot[2];
	int[] order = paliashdr.glCmds;
	float height = -lheight + 1.0f;

	int orderIndex = 0;
	int index = 0;

	// TODO shadow drawing with vertex arrays

	int count;
	while (true) {
	    // get the vertex count and primitive type
	    count = order[orderIndex++];
	    if (count == 0)
		break; // done
	    if (count < 0) {
		count = -count;
		gl.glBegin(GL_TRIANGLE_FAN);
	    } else
		gl.glBegin(GL_TRIANGLE_STRIP);

	    do {
		index = order[orderIndex + 2] * 3;
		point[0] = vertexArrayBuf.get(index);
		point[1] = vertexArrayBuf.get(index + 1);
		point[2] = vertexArrayBuf.get(index + 2);

		point[0] -= shadevector[0] * (point[2] + lheight);
		point[1] -= shadevector[1] * (point[2] + lheight);
		point[2] = height;
		gl.glVertex3f(point[0], point[1], point[2]);

		orderIndex += 3;

	    } while (--count != 0);

	    gl.glEnd();
	}
    }

    // TODO sync with jogl renderer. hoz
    // stack variable
    private final float[] mins = { 0, 0, 0 };

    private final float[] maxs = { 0, 0, 0 };

    /**
     * R_CullAliasModel
     */
    boolean R_CullAliasModel(entity_t e) {
	qfiles.dmdl_t paliashdr = (qfiles.dmdl_t) currentmodel.extradata;

	if ((e.frame >= paliashdr.num_frames) || (e.frame < 0)) {
	    VID.Printf(Defines.PRINT_ALL, "R_CullAliasModel "
		    + currentmodel.name + ": no such frame " + e.frame + '\n');
	    e.frame = 0;
	}
	if ((e.oldframe >= paliashdr.num_frames) || (e.oldframe < 0)) {
	    VID.Printf(Defines.PRINT_ALL, "R_CullAliasModel "
		    + currentmodel.name + ": no such oldframe " + e.oldframe
		    + '\n');
	    e.oldframe = 0;
	}

	qfiles.daliasframe_t pframe = paliashdr.aliasFrames[e.frame];
	qfiles.daliasframe_t poldframe = paliashdr.aliasFrames[e.oldframe];

	/*
	 * * compute axially aligned mins and maxs
	 */
	if (pframe == poldframe) {
	    for (int i = 0; i < 3; i++) {
		mins[i] = pframe.translate[i];
		maxs[i] = mins[i] + pframe.scale[i] * 255;
	    }
	} else {
	    float thismaxs, oldmaxs;
	    for (int i = 0; i < 3; i++) {
		thismaxs = pframe.translate[i] + pframe.scale[i] * 255;

		oldmaxs = poldframe.translate[i] + poldframe.scale[i] * 255;

		if (pframe.translate[i] < poldframe.translate[i])
		    mins[i] = pframe.translate[i];
		else
		    mins[i] = poldframe.translate[i];

		if (thismaxs > oldmaxs)
		    maxs[i] = thismaxs;
		else
		    maxs[i] = oldmaxs;
	    }
	}

	/*
	 * * compute a full bounding box
	 */
	float[] tmp;
	for (int i = 0; i < 8; i++) {
	    tmp = bbox[i];
	    if ((i & 1) != 0)
		tmp[0] = mins[0];
	    else
		tmp[0] = maxs[0];

	    if ((i & 2) != 0)
		tmp[1] = mins[1];
	    else
		tmp[1] = maxs[1];

	    if ((i & 4) != 0)
		tmp[2] = mins[2];
	    else
		tmp[2] = maxs[2];
	}

	/*
	 * * rotate the bounding box
	 */
	tmp = mins;
	Math3D.VectorCopy(e.angles, tmp);
	tmp[YAW] = -tmp[YAW];
	Math3D.AngleVectors(tmp, vectors[0], vectors[1], vectors[2]);

	for (int i = 0; i < 8; i++) {
	    Math3D.VectorCopy(bbox[i], tmp);

	    bbox[i][0] = Math3D.DotProduct(vectors[0], tmp);
	    bbox[i][1] = -Math3D.DotProduct(vectors[1], tmp);
	    bbox[i][2] = Math3D.DotProduct(vectors[2], tmp);

	    Math3D.VectorAdd(e.origin, bbox[i], bbox[i]);
	}

	int f, mask;
	int aggregatemask = ~0; // 0xFFFFFFFF

	for (int p = 0; p < 8; p++) {
	    mask = 0;

	    for (f = 0; f < 4; f++) {
		float dp = Math3D.DotProduct(frustum[f].normal, bbox[p]);

		if ((dp - frustum[f].dist) < 0) {
		    mask |= (1 << f);
		}
	    }

	    aggregatemask &= mask;
	}

	if (aggregatemask != 0) {
	    return true;
	}

	return false;
    }

    // bounding box
    float[][] bbox = { { 0, 0, 0 }, { 0, 0, 0 }, { 0, 0, 0 }, { 0, 0, 0 },
	    { 0, 0, 0 }, { 0, 0, 0 }, { 0, 0, 0 }, { 0, 0, 0 } };

    // TODO sync with jogl renderer. hoz
    /**
     * R_DrawAliasModel
     */
    void R_DrawAliasModel(entity_t e) {
	if ((e.flags & Defines.RF_WEAPONMODEL) == 0) {
	    if (R_CullAliasModel(e))
		return;
	}

	if ((e.flags & Defines.RF_WEAPONMODEL) != 0) {
	    if (r_lefthand.value == 2.0f)
		return;
	}

	qfiles.dmdl_t paliashdr = (qfiles.dmdl_t) currentmodel.extradata;

	//
	// get lighting information
	//
	// PMM - rewrote, reordered to handle new shells & mixing
	// PMM - 3.20 code .. replaced with original way of doing it to keep mod
	// authors happy
	//
	int i;
	if ((currententity.flags & (Defines.RF_SHELL_HALF_DAM
		| Defines.RF_SHELL_GREEN | Defines.RF_SHELL_RED
		| Defines.RF_SHELL_BLUE | Defines.RF_SHELL_DOUBLE)) != 0) {
	    Math3D.VectorClear(shadelight);
	    if ((currententity.flags & Defines.RF_SHELL_HALF_DAM) != 0) {
		shadelight[0] = 0.56f;
		shadelight[1] = 0.59f;
		shadelight[2] = 0.45f;
	    }
	    if ((currententity.flags & Defines.RF_SHELL_DOUBLE) != 0) {
		shadelight[0] = 0.9f;
		shadelight[1] = 0.7f;
	    }
	    if ((currententity.flags & Defines.RF_SHELL_RED) != 0)
		shadelight[0] = 1.0f;
	    if ((currententity.flags & Defines.RF_SHELL_GREEN) != 0)
		shadelight[1] = 1.0f;
	    if ((currententity.flags & Defines.RF_SHELL_BLUE) != 0)
		shadelight[2] = 1.0f;
	}

	else if ((currententity.flags & Defines.RF_FULLBRIGHT) != 0) {
	    for (i = 0; i < 3; i++)
		shadelight[i] = 1.0f;
	} else {
	    R_LightPoint(currententity.origin, shadelight);

	    // player lighting hack for communication back to server
	    // big hack!
	    if ((currententity.flags & Defines.RF_WEAPONMODEL) != 0) {
		// pick the greatest component, which should be the same
		// as the mono value returned by software
		if (shadelight[0] > shadelight[1]) {
		    if (shadelight[0] > shadelight[2])
			r_lightlevel.value = 150 * shadelight[0];
		    else
			r_lightlevel.value = 150 * shadelight[2];
		} else {
		    if (shadelight[1] > shadelight[2])
			r_lightlevel.value = 150 * shadelight[1];
		    else
			r_lightlevel.value = 150 * shadelight[2];
		}
	    }

	    if (gl_monolightmap.string.charAt(0) != '0') {
		float s = shadelight[0];

		if (s < shadelight[1])
		    s = shadelight[1];
		if (s < shadelight[2])
		    s = shadelight[2];

		shadelight[0] = s;
		shadelight[1] = s;
		shadelight[2] = s;
	    }
	}

	if ((currententity.flags & Defines.RF_MINLIGHT) != 0) {
	    for (i = 0; i < 3; i++)
		if (shadelight[i] > 0.1f)
		    break;
	    if (i == 3) {
		shadelight[0] = 0.1f;
		shadelight[1] = 0.1f;
		shadelight[2] = 0.1f;
	    }
	}

	if ((currententity.flags & Defines.RF_GLOW) != 0) { // bonus items
	    // will pulse
	    // with time
	    float scale;
	    float min;

	    scale = (float) (0.1f * Math.sin(r_newrefdef.time * 7));
	    for (i = 0; i < 3; i++) {
		min = shadelight[i] * 0.8f;
		shadelight[i] += scale;
		if (shadelight[i] < min)
		    shadelight[i] = min;
	    }
	}

	// =================
	// PGM ir goggles color override
	if ((r_newrefdef.rdflags & Defines.RDF_IRGOGGLES) != 0
		&& (currententity.flags & Defines.RF_IR_VISIBLE) != 0) {
	    shadelight[0] = 1.0f;
	    shadelight[1] = 0.0f;
	    shadelight[2] = 0.0f;
	}
	// PGM
	// =================

	shadedots = r_avertexnormal_dots[((int) (currententity.angles[1] * (SHADEDOT_QUANT / 360.0)))
		& (SHADEDOT_QUANT - 1)];

	float an = (float) (currententity.angles[1] / 180 * Math.PI);
	shadevector[0] = (float) Math.cos(-an);
	shadevector[1] = (float) Math.sin(-an);
	shadevector[2] = 1;
	Math3D.VectorNormalize(shadevector);

	//
	// locate the proper data
	//

	c_alias_polys += paliashdr.num_tris;

	//
	// draw all the triangles
	//
	if ((currententity.flags & Defines.RF_DEPTHHACK) != 0) // hack the
	    // depth range
	    // to prevent
	    // view model
	    // from poking
	    // into walls
	    gl.glDepthRange(gldepthmin, gldepthmin + 0.3
		    * (gldepthmax - gldepthmin));

	if ((currententity.flags & Defines.RF_WEAPONMODEL) != 0
		&& (r_lefthand.value == 1.0f)) {
	    gl.glMatrixMode(GL_PROJECTION);
	    gl.glPushMatrix();
	    gl.glLoadIdentity();
	    gl.glScalef(-1, 1, 1);
	    MYgluPerspective(r_newrefdef.fov_y, (float) r_newrefdef.width
		    / r_newrefdef.height, 4, 4096);
	    gl.glMatrixMode(GL_MODELVIEW);

	    gl.glCullFace(GL_BACK);
	}

	gl.glPushMatrix();
	e.angles[PITCH] = -e.angles[PITCH]; // sigh.
	R_RotateForEntity(e);
	e.angles[PITCH] = -e.angles[PITCH]; // sigh.

	image_t skin;
	// select skin
	if (currententity.skin != null)
	    skin = currententity.skin; // custom player skin
	else {
	    if (currententity.skinnum >= qfiles.MAX_MD2SKINS)
		skin = currentmodel.skins[0];
	    else {
		skin = currentmodel.skins[currententity.skinnum];
		if (skin == null)
		    skin = currentmodel.skins[0];
	    }
	}
	if (skin == null)
	    skin = r_notexture; // fallback...
	GL_Bind(skin.texnum);

	// draw it

	gl.glShadeModel(GL_SMOOTH);

	GL_TexEnv(GL_MODULATE);
	if ((currententity.flags & Defines.RF_TRANSLUCENT) != 0) {
	    gl.glEnable(GL_BLEND);
	}

	if ((currententity.frame >= paliashdr.num_frames)
		|| (currententity.frame < 0)) {
	    VID.Printf(Defines.PRINT_ALL, "R_DrawAliasModel "
		    + currentmodel.name + ": no such frame "
		    + currententity.frame + '\n');
	    currententity.frame = 0;
	    currententity.oldframe = 0;
	}

	if ((currententity.oldframe >= paliashdr.num_frames)
		|| (currententity.oldframe < 0)) {
	    VID.Printf(Defines.PRINT_ALL, "R_DrawAliasModel "
		    + currentmodel.name + ": no such oldframe "
		    + currententity.oldframe + '\n');
	    currententity.frame = 0;
	    currententity.oldframe = 0;
	}

	if (r_lerpmodels.value == 0.0f)
	    currententity.backlerp = 0;

	GL_DrawAliasFrameLerp(paliashdr, currententity.backlerp);

	GL_TexEnv(GL_REPLACE);
	gl.glShadeModel(GL_FLAT);

	gl.glPopMatrix();

	if ((currententity.flags & Defines.RF_WEAPONMODEL) != 0
		&& (r_lefthand.value == 1.0F)) {
	    gl.glMatrixMode(GL_PROJECTION);
	    gl.glPopMatrix();
	    gl.glMatrixMode(GL_MODELVIEW);
	    gl.glCullFace(GL_FRONT);
	}

	if ((currententity.flags & Defines.RF_TRANSLUCENT) != 0) {
	    gl.glDisable(GL_BLEND);
	}

	if ((currententity.flags & Defines.RF_DEPTHHACK) != 0)
	    gl.glDepthRange(gldepthmin, gldepthmax);

	if (gl_shadows.value != 0.0f
		&& (currententity.flags & (Defines.RF_TRANSLUCENT | Defines.RF_WEAPONMODEL)) == 0) {
	    gl.glPushMatrix();
	    R_RotateForEntity(e);
	    gl.glDisable(GL_TEXTURE_2D);
	    gl.glEnable(GL_BLEND);
	    gl.glColor4f(0, 0, 0, 0.5f);
	    GL_DrawAliasShadow(paliashdr, currententity.frame);
	    gl.glEnable(GL_TEXTURE_2D);
	    gl.glDisable(GL_BLEND);
	    gl.glPopMatrix();
	}
	gl.glColor4f(1, 1, 1, 1);
    }
}