/*
 * Polygon.java
 * Copyright (C) 2003
 *
 * $Id: Polygon.java,v 1.1 2005-01-09 22:34:21 cawe Exp $
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

import jake2.render.glpoly_t;

/**
 * Polygon
 * 
 * @author cwei
 */
public final class Polygon extends glpoly_t {
    
	private final static int MAXPOLYS = 20000;
	private final static int MAX_BUFFER_VERTICES = 120000;
	
	private static float[] buffer = new float[MAX_BUFFER_VERTICES * STRIDE];
	private static int bufferIndex = 0;
	private static int polyCount = 0;
	private static Polygon[] polyCache = new Polygon[MAXPOLYS];
	static {
	    for (int i = 0; i < polyCache.length; i++) {
            polyCache[i] = new Polygon();
        }
	}
	
	public static glpoly_t create(int numverts) {
	    Polygon poly = polyCache[polyCount++];
	    poly.clear();
	    poly.numverts = numverts;
	    poly.pos = bufferIndex;
	    bufferIndex += numverts;
	    return poly;
	}
	
	public static void reset() {
	    polyCount = 0;
	    bufferIndex = 0;
	}
	
	private Polygon() {
	}
	
	private final void clear() {
	    next = null;
	    chain = null;
	    numverts = 0;
	    flags = 0;
	}

	public final float x(int index) {
	    return buffer[(index + pos) * 7 + 0];
	}
	
	public final void x(int index, float value) {
	    buffer[(index + pos) * 7 + 0] = value;
	}

	public final float y(int index) {
	    return buffer[(index + pos) * 7 + 1];
	}
	
	public final void y(int index, float value) {
	    buffer[(index + pos) * 7 + 1] = value;
	}
	
	public final float z(int index) {
	    return buffer[(index + pos) * 7 + 2];
	}
	
	public final void z(int index, float value) {
	    buffer[(index + pos) * 7 + 2] = value;
	}

	public final float s1(int index) {
	    return buffer[(index + pos) * 7 + 3];
	}
	
	public final void s1(int index, float value) {
	    buffer[(index + pos) * 7 + 3] = value;
	}

	public final float t1(int index) {
	    return buffer[(index + pos) * 7 + 4];
	}
	
	public final void t1(int index, float value) {
	    buffer[(index + pos) * 7 + 4] = value;
	}

	public final float s2(int index) {
	    return buffer[(index + pos) * 7 + 5];
	}
	
	public final void s2(int index, float value) {
	    buffer[(index + pos) * 7 + 5] = value;
	}

	public final float t2(int index) {
	    return buffer[(index + pos) * 7 + 6];
	}
	
	public final void t2(int index, float value) {
	    buffer[(index + pos) * 7 + 6] = value;
	}
	
	public final void beginScrolling(float value) {
	    // not in use
	}

	public final void endScrolling() {
	    // not in use
	}
}
