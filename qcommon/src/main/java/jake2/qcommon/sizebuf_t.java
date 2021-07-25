/*
 * sizebuf_t.java
 * Copyright (C) 2003
 *
 * $Id: sizebuf_t.java,v 1.1 2004-07-07 19:59:34 hzi Exp $
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
package jake2.qcommon;

import jake2.qcommon.util.Lib;
import jake2.qcommon.util.Math3D;

import java.util.Arrays;
import java.util.Objects;

/**
 * sizebuf_t
 */
public final class sizebuf_t {
    public boolean allowoverflow = false;
    public boolean overflowed = false;
    public byte[] data = null;
    public int maxsize = 0;
    public int cursize = 0;
    public int readcount = 0;

    public sizebuf_t() {
    }

    public sizebuf_t(int length) {
        this.data = new byte[length];
        this.maxsize = length;
    }

    /**
     * Ask for the pointer using sizebuf_t.cursize (RST)
     */
    private int GetSpace(int length) {
        int oldsize;

        if (cursize + length > maxsize) {
            if (!allowoverflow)
                Com.Error(Defines.ERR_FATAL, "SZ_GetSpace: overflow without allowoverflow set");

            if (length > maxsize)
                Com.Error(Defines.ERR_FATAL, "SZ_GetSpace: " + length + " is > full buffer size");

            Com.Printf("SZ_GetSpace: overflow\n");
            clear();
            overflowed = true;
        }

        oldsize = cursize;
        cursize += length;

        return oldsize;
    }

    public void init(byte[] data, int length) {
        // TODO check this. cwei
        readcount = 0;
        this.data = data;
        maxsize = length;
        cursize = 0;
        allowoverflow = overflowed = false;
    }

    public void writeByte(byte c) {
        data[GetSpace(1)] = (byte) (c & 0xFF);
    }

    // fixme: should return byte type
    public int readByte() {
        int c;

        if (readcount + 1 > cursize)
            c = -1;
        else
            c = data[readcount] & 0xff;

        readcount++;
        return c;
    }

    // legitimate signed byte [-128 , 127]
    // returns -1 if no more characters are available
    public byte readSignedByte() {
        byte c;

        if (readcount + 1 > cursize)
            c = (byte) -1;
        else
            c = data[readcount];
        readcount++;
        return c;
    }

    public void writeShort(int c) {
        int i = GetSpace(2);
        data[i++] = (byte) (c & 0xff);
        data[i] = (byte) (c >>> 8 & 0xFF);
    }

    public short readShort() {
        final short c;
        if (readcount + 2 > cursize)
            c = -1;
        else
            c = (short) ((readByte() & 0xff) + (readByte() << 8));

        return c;
    }

    public void writeInt(int c) {
        int i = GetSpace(4);
        data[i++] = (byte) (c & 0xff);
        data[i++] = (byte) (c >>> 8 & 0xff);
        data[i++] = (byte) (c >>> 16 & 0xff);
        data[i] = (byte) (c >>> 24 & 0xff);
    }

    public int readInt() {
        final int c;

        if (readcount + 4 > cursize) {
            Com.Printf("buffer underrun in ReadInt!");
            c = -1;
        } else
            c = readByte() & 0xff
                    | (readByte() & 0xff) << 8
                    | (readByte() & 0xff) << 16
                    | (readByte() & 0xff) << 24;

        return c;
    }

    public void writeFloat(float f) {
        writeInt(Float.floatToIntBits(f));
    }

    public float readFloat() {
        return Float.intBitsToFloat(readInt());
    }

    public void writeString(String s) {
        final String x = Objects.requireNonNullElse(s, "");
        writeBytes(Lib.stringToBytes(x));
        writeByte((byte) 0);
    }

    /**
     * Read at most 2048 single byte characters
     */
    public String readString() {
        // 2k read buffer.
        byte[] readbuf = new byte[2048];
        byte c;
        int l = 0;
        do {
            c = (byte) readByte();
            if (c == -1 || c == 0)
                break;

            readbuf[l] = c;
            l++;
        } while (l < 2047);

        return new String(readbuf, 0, l);
    }

    public void writeCoord(float f) {
        writeShort((int) (f * 8));
    }

    public float readCoord() {
        return readShort() * (1.0f / 8);
    }

    public void writePos(float[] pos) {
        writeShort((int) (pos[0] * 8));
        writeShort((int) (pos[1] * 8));
        writeShort((int) (pos[2] * 8));
    }

    public void readPos(float[] pos) {
        pos[0] = readShort() * (1.0f / 8);
        pos[1] = readShort() * (1.0f / 8);
        pos[2] = readShort() * (1.0f / 8);
    }

    /**
     * @param f degrees, (-180, 180) mapped into one byte
     */
    public void writeAngleByte(float f) {
        writeByte((byte) ((int) (f * 256 / 360) & 255));
    }

    /**
     * @return degrees, (-180, 180) mapped from one byte
     */
    public float readAngleByte() {
        return readSignedByte() * (180f / 128);
    }

    /**
     * @param f degrees, (-180, 180) mapped into two bytes
     */
    public void writeAngleShort(float f) {
        writeShort(Math3D.ANGLE2SHORT(f));
    }

    /**
     * @return degrees, (-180, 180) mapped from two bytes
     */
    public float readAngleShort() {
        return Math3D.SHORT2ANGLE(readShort());
    }

    public void writeDir(float[] dir) {

        if (dir == null) {
            writeByte((byte) 0);
            return;
        }

        float bestd = 0;
        int best = 0;
        for (int i = 0; i < Defines.NUMVERTEXNORMALS; i++) {
            float d = Math3D.DotProduct(dir, Globals.bytedirs[i]);
            if (d > bestd) {
                bestd = d;
                best = i;
            }
        }
        writeByte((byte) best);
    }

    public float[] readDir() {
        float[] dir = new float[3];
        int b = readByte();
        if (b >= Defines.NUMVERTEXNORMALS)
            Com.Error(Defines.ERR_DROP, "MSF_ReadDir: out of range");
        Math3D.VectorCopy(Globals.bytedirs[b], dir);
        return dir;
    }

    /**
     * Read 'len' bytes into 'data'
     */
    public void readData(byte[] data, int len) {
        for (int i = 0; i < len; i++)
            data[i] = (byte) readByte();
    }

    public void writeBytes(byte[] data, int length) {
        System.arraycopy(data, 0, this.data, GetSpace(length), length);
    }

    public void writeBytes(byte[] data, int offset, int length) {
        System.arraycopy(data, offset, this.data, GetSpace(length), length);
    }

    public void writeBytes(byte[] data) {
        int length = data.length;
        System.arraycopy(data, 0, this.data, GetSpace(length), length);
    }

    public void clear() {
        if (data != null)
            Arrays.fill(data, (byte) 0);
        cursize = 0;
        overflowed = false;
    }

}