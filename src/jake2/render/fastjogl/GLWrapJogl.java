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

// Created on 28.10.2006 by RST.

// $Id: GLWrapJogl.java,v 1.1 2006-10-31 13:06:31 salomo Exp $

package jake2.render.fastjogl;


import java.nio.*;

import net.java.games.jogl.GL;
import jake2.*;
import jake2.client.*;
import jake2.game.*;
import jake2.qcommon.*;
import jake2.render.*;
import jake2.render.common.GenericGL;
import jake2.server.*;
import net.java.games.jogl.*;

public class GLWrapJogl implements GenericGL{

	protected GL gl;
	
	public GLWrapJogl(GL gl)
	{
		this.gl = gl;
	}
	
	public void glTexParameterf(int gl_texture_2d, int gl_texture_min_filter, int gl_nearest) {
		gl.glTexParameterf(gl_texture_2d, gl_texture_min_filter, gl_nearest);
	}
		public void glBegin(int gl_triangle_fan2) {
		gl.glBegin(gl_triangle_fan2);
	}

	public void glTexCoord2f(float s, float t) {
		gl.glTexCoord2f(s, t);
	}

	public void glVertex3f(float f, float g, float h) {
		gl.glVertex3f(f, g, h);		
	}

	public void glEnd() {
		gl.glEnd();
	}

	public void glPushMatrix() {
		gl.glPushMatrix();
	}

	public void glTranslatef(float f, float g, float h) {
		gl.glTranslatef(f, g, h);
	}

	public void glRotatef(float f, float g, float h, float i) {
		gl.glRotatef(f, g, h, i);
	}

	public void glPopMatrix() {
		gl.glPopMatrix();
	}

	public void glColor3f(float f, float g, float h) {
		gl.glColor3f(f, g, h);		
	}

	public void glDepthMask(boolean b) {
		gl.glDepthMask(b);		
	}

	public void glDisable(int gl_texture_2d2) {
		gl.glDisable(gl_texture_2d2);		
	}

	public void glBlendFunc(int gl_src_alpha2, int gl_one_minus_src_alpha2) {
		gl.glBlendFunc(gl_src_alpha2, gl_one_minus_src_alpha2);
	}

	public void glShadeModel(int gl_smooth2) {
		gl.glShadeModel(gl_smooth2);		
	}

	public void glDrawArrays(int gl_polygon2, int pos, int numverts) {
		gl.glDrawArrays(gl_polygon2, pos, numverts);
	}

	public void glBindTexture(int gl_texture_2d2, int texnum) {
		gl.glBindTexture(gl_texture_2d2, texnum);
	}

	public void glActiveTextureARB(int texture) {
		gl.glActiveTextureARB( texture);		
	}

	public void glClientActiveTextureARB(int texture) {
		gl.glClientActiveTextureARB(texture);		
	}

	public void glTexEnvi(int gl_texture_env2, int gl_texture_env_mode2, int mode) {
		gl.glTexEnvi(gl_texture_env2, gl_texture_env_mode2, mode);		
	}

	public void glVertex2f(int x, int y) {
		gl.glVertex2f(x, y);
	}

	public void glEnable(int gl_alpha_test2) {
		gl.glEnable(gl_alpha_test2);		
	}

	public void glColor3ub(byte b, byte c, byte d) {
		gl.glColor3ub(b, c, d);
	}

	public void glColor4f(int i, int j, int k, float f) {
		gl.glColor4f(i, j, k, f);		
	}

	public void glTexImage2D(int gl_texture_2d2, int i, int gl_tex_solid_format, int j, int k, int l, int gl_rgba3, int gl_unsigned_byte2, IntBuffer image32) {
		gl.glTexImage2D(gl_texture_2d2, i, gl_tex_solid_format, j, k, l, gl_rgba3, gl_unsigned_byte2,  image32);
	}

	public void glTexImage2D(int gl_texture_2d2, int i, int gl_color_index8_ext, int j, int k, int l, int gl_color_index2, int gl_unsigned_byte2, ByteBuffer image8) {
		gl.glTexImage2D(gl_texture_2d2, i, gl_color_index8_ext, j, k, l, gl_color_index2, gl_unsigned_byte2, image8);
	}
	
	public void glColor4ub(byte b, byte c, byte d, byte e) {
		gl.glColor4ub(b, c, d, e);
	}

	public void glTexParameteri(int gl_texture_2d2, int gl_texture_min_filter2, int gl_filter_min) {
		gl.glTexParameteri(gl_texture_2d2, gl_texture_min_filter2, gl_filter_min);
	}
	
	public void glTexSubImage2D(int gl_texture_2d2, int i, int j, int k, int lm_block_width, int height, int gl_lightmap_format, int gl_unsigned_byte2, IntBuffer lightmap_buffer) {
		gl.glTexSubImage2D(gl_texture_2d2, i, j, k, lm_block_width, height, gl_lightmap_format, gl_unsigned_byte2, lightmap_buffer);		
	}

	public final void glColor4f(float intens, float intens2, float intens3, float f) {
		gl.glColor4f(intens, intens2, intens3, f);		
	}

	public final void glLoadMatrixf(float[] r_world_matrix) {
		gl.glLoadMatrixf(r_world_matrix);		
	}

	public final void glInterleavedArrays(int gl_t2f_v3f2, int byte_stride, FloatBuffer globalPolygonInterleavedBuf) {
		gl.glInterleavedArrays(gl_t2f_v3f2, byte_stride, globalPolygonInterleavedBuf);
	}

	public final void glDrawElements(int mode, int count, int gl_unsigned_int2, IntBuffer srcIndexBuf) {
		gl.glDrawElements(mode, count, gl_unsigned_int2, srcIndexBuf);		
	}

	public final void glDisableClientState(int gl_color_array2) {
		gl.glDisableClientState(gl_color_array2);
	}

	public final void glVertexPointer(int i, int gl_float2, int j, FloatBuffer vertexArrayBuf) {
		gl.glVertexPointer(i, gl_float2, j, vertexArrayBuf);		
	}

	public final void glEnableClientState(int gl_color_array2) {
		gl.glEnableClientState(gl_color_array2);
	}

	public final void glColorPointer(int i, int gl_float2, int j, FloatBuffer colorArrayBuf) {
		gl.glColorPointer(i, gl_float2, j, colorArrayBuf);		
	}

	public final void glTexCoordPointer(int i, int gl_float2, int j, FloatBuffer textureArrayBuf) {
		gl.glTexCoordPointer(i, gl_float2, j, textureArrayBuf);
	}

	public final void glDepthRange(float gldepthmin, double d) {
		gl.glDepthRange(gldepthmin, d);		
	}

	public final void glMatrixMode(int gl_projection2) {
		gl.glMatrixMode(gl_projection2);
	}

	public final void glLoadIdentity() {
		gl.glLoadIdentity();		
	}

	public final void glScalef(int i, int j, int k) {
		gl.glScalef(i, j, k);		
	}

	public final void glCullFace(int gl_back2) {
		gl.glCullFace(gl_back2);		
	}
	
	public final void glClear(int gl_color_buffer_bit2) {
		gl.glClear(gl_color_buffer_bit2);		
	}

	public final void glDepthFunc(int gl_lequal2) {
		gl.glDepthFunc(gl_lequal2);
	}
	
	public final void glFrustum(double xmin, double xmax, double ymin, double ymax, double near, double far) {
		gl.glFrustum(xmin, xmax, ymin, ymax, near, far);
	}	
	
	public final void glViewport(int i, int j, int width, int height) {
		gl.glViewport(i, j, width, height);
	}

	public final void glOrtho(int i, int width, int height, int j, int k, int l) {
		gl.glOrtho(i, width, height, j, k, l);
	}
	
	public void glColorPointer(int i, int gl_unsigned_byte2, int j, ByteBuffer bb) {
		gl.glColorPointer(i, gl_unsigned_byte2, j, bb.asIntBuffer());
	}

	public void glPointSize(float value) {
		gl.glPointSize(value);		
	}
	
	public void glClearColor(float f, float g, float h, float i) {
		gl.glClearColor(f, g, h, i);		
	}
}
