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

// Created on 20.11.2003 by RST.
// $Id: glpoly_t.java,v 1.5 2004-06-09 15:25:12 cwei Exp $

package jake2.render;

import java.nio.FloatBuffer;

import net.java.games.jogl.util.BufferUtils;

public class glpoly_t {
	public final static int VERTEXSIZE = 7;

	public glpoly_t next;
	public glpoly_t chain;
	public int numverts;
	public int flags; // for SURF_UNDERWATER (not needed anymore?)
	public float verts[][] = null; // variable sized (xyz s1t1 s2t2)
	
	public glpoly_t(int numverts) {
		this.verts = new float[numverts][VERTEXSIZE];
	}
	
	boolean precompile = false;
	
	static final int SIZE = 100000;
	
	public static FloatBuffer vertexArray = BufferUtils.newFloatBuffer(SIZE * 3);
	public static FloatBuffer texCoord0Array = BufferUtils.newFloatBuffer(SIZE * 2);
	public static FloatBuffer texCoord1Array = BufferUtils.newFloatBuffer(SIZE * 2);
	
	public int pos = 0;
	
	public void preCompile() {
		
		pos = vertexArray.position() / 3;
		
		for (int i = 0; i < verts.length; i++) {
			vertexArray.put(verts[i], 0, 3);
			texCoord0Array.put(verts[i], 3, 2);
			texCoord1Array.put(verts[i], 5, 2);
		}
	}
	
	public static void resetArrays() {
		vertexArray.rewind();
		texCoord0Array.rewind();
		texCoord1Array.rewind();
	}
}
