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
// $Id: glpoly_t.java,v 1.6 2004-06-13 21:42:27 cwei Exp $

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
	
	/*
	 * vertex array extension
	 */
	 
	// the array position (glDrawArrays) 
	public int pos = 0;
	
}
