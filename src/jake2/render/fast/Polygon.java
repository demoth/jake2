/*
 * Polygon.java
 * Copyright (C) 2003
 *
 * $Id: Polygon.java,v 1.2 2006-11-21 00:50:46 cawe Exp $
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

import jake2.render.glpoly_t;
import jake2.util.Lib;

import java.nio.FloatBuffer;

/**
 * Polygon
 * 
 * @author cwei
 */
public final class Polygon extends glpoly_t {

    private final static int MAX_POLYS = 20000;

    private final static int MAX_BUFFER_VERTICES = 120000;

    // backup for s1 scrolling
    private static float[] s1_old = new float[MAX_VERTICES];

    private static FloatBuffer buffer = Lib.newFloatBuffer(MAX_BUFFER_VERTICES
            * STRIDE);

    private static int bufferIndex = 0;

    private static int polyCount = 0;

    private static Polygon[] polyCache = new Polygon[MAX_POLYS];
    static {
        for (int i = 0; i < polyCache.length; i++) {
            polyCache[i] = new Polygon();
        }
    }

    static glpoly_t create(int numverts) {
        Polygon poly = polyCache[polyCount++];
        poly.clear();
        poly.numverts = numverts;
        poly.pos = bufferIndex;
        bufferIndex += numverts;
        return poly;
    }

    static void reset() {
        polyCount = 0;
        bufferIndex = 0;
    }

    static FloatBuffer getInterleavedBuffer() {
        return (FloatBuffer) buffer.rewind();
    }

    private Polygon() {
    }

    private final void clear() {
        next = null;
        chain = null;
        numverts = 0;
        flags = 0;
    }

    // the interleaved buffer has the format:
    // textureCoord0 (index 0, 1)
    // vertex (index 2, 3, 4)
    // textureCoord1 (index 5, 6)

    public final float s1(int index) {
        return buffer.get((index + pos) * STRIDE);
    }

    public final void s1(int index, float value) {
        buffer.put((index + pos) * STRIDE, value);
    }

    public final float t1(int index) {
        return buffer.get((index + pos) * STRIDE + 1);
    }

    public final void t1(int index, float value) {
        buffer.put((index + pos) * STRIDE + 1, value);
    }

    public final float x(int index) {
        return buffer.get((index + pos) * STRIDE + 2);
    }

    public final void x(int index, float value) {
        buffer.put((index + pos) * STRIDE + 2, value);
    }

    public final float y(int index) {
        return buffer.get((index + pos) * STRIDE + 3);
    }

    public final void y(int index, float value) {
        buffer.put((index + pos) * STRIDE + 3, value);
    }

    public final float z(int index) {
        return buffer.get((index + pos) * STRIDE + 4);
    }

    public final void z(int index, float value) {
        buffer.put((index + pos) * STRIDE + 4, value);
    }

    public final float s2(int index) {
        return buffer.get((index + pos) * STRIDE + 5);
    }

    public final void s2(int index, float value) {
        buffer.put((index + pos) * STRIDE + 5, value);
    }

    public final float t2(int index) {
        return buffer.get((index + pos) * STRIDE + 6);
    }

    public final void t2(int index, float value) {
        buffer.put((index + pos) * STRIDE + 6, value);
    }

    public final void beginScrolling(float scroll) {
        int index = pos * STRIDE;
        for (int i = 0; i < numverts; i++, index += STRIDE) {
            scroll += s1_old[i] = buffer.get(index);
            buffer.put(index, scroll);
        }
    }

    public final void endScrolling() {
        int index = pos * STRIDE;
        for (int i = 0; i < numverts; i++, index += STRIDE) {
            buffer.put(index, s1_old[i]);
        }
    }
}