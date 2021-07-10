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

    public static void WriteByte(sizebuf_t sb, byte c) {
        sb.data[SZ.GetSpace(sb, 1)] = (byte) (c & 0xFF);
    }

    public void WriteShort(int c) {
        int i = SZ.GetSpace(this, 2);
        data[i++] = (byte) (c & 0xff);
        data[i] = (byte) (c >>> 8 & 0xFF);
    }

    //ok.
    public static void WriteInt(sizebuf_t sb, int c) {
        int i = SZ.GetSpace(sb, 4);
        sb.data[i++] = (byte) (c & 0xff);
        sb.data[i++] = (byte) (c >>> 8 & 0xff);
        sb.data[i++] = (byte) (c >>> 16 & 0xff);
        sb.data[i] = (byte) (c >>> 24 & 0xff);
    }

    //ok.
    public static void WriteFloat(sizebuf_t sb, float f) {
        WriteInt(sb, Float.floatToIntBits(f));
    }

    // had a bug, now its ok.
    public static void WriteString(sizebuf_t sb, String s) {
        String x = s;

        if (s == null)
            x = "";

        SZ.Write(sb, Lib.stringToBytes(x));
        WriteByte(sb, (byte) 0);
    }

    public static void WriteCoord(sizebuf_t sb, float f) {
        sb.WriteShort((int) (f * 8));
    }

    public static void WritePos(sizebuf_t sb, float[] pos) {
        assert pos.length == 3 : "vec3_t bug";
        sb.WriteShort((int) (pos[0] * 8));
        sb.WriteShort((int) (pos[1] * 8));
        sb.WriteShort((int) (pos[2] * 8));
    }

    public static void WriteAngle(sizebuf_t sb, float f) {
        WriteByte(sb, (byte) ((int) (f * 256 / 360) & 255));
    }

    public static void WriteAngle16(sizebuf_t sb, float f) {
        sb.WriteShort(Math3D.ANGLE2SHORT(f));
    }

    //should be ok.
    public static void WriteDir(sizebuf_t sb, float[] dir) {
        int i, best;
        float d, bestd;

        if (dir == null) {
            WriteByte(sb, (byte) 0);
            return;
        }

        bestd = 0;
        best = 0;
        for (i = 0; i < Defines.NUMVERTEXNORMALS; i++) {
            d = Math3D.DotProduct(dir, Globals.bytedirs[i]);
            if (d > bestd) {
                bestd = d;
                best = i;
            }
        }
        WriteByte(sb, (byte) best);
    }

    //should be ok.
    public static void ReadDir(sizebuf_t sb, float[] dir) {
        int b;

        b = ReadByte(sb);
        if (b >= Defines.NUMVERTEXNORMALS)
            Com.Error(Defines.ERR_DROP, "MSF_ReadDir: out of range");
        Math3D.VectorCopy(Globals.bytedirs[b], dir);
    }

    // legitimate signed byte [-128 , 127]
    // returns -1 if no more characters are available
    public static byte ReadSignedByte(sizebuf_t msg_read) {
        byte c;

        if (msg_read.readcount + 1 > msg_read.cursize)
            c = (byte) -1;
        else
            c = msg_read.data[msg_read.readcount];
        msg_read.readcount++;
        return c;
    }

    public static int ReadByte(sizebuf_t msg_read) {
        int c;

        if (msg_read.readcount + 1 > msg_read.cursize)
            c = -1;
        else
            c = msg_read.data[msg_read.readcount] & 0xff;

        msg_read.readcount++;
        return c;
    }

    public static short ReadShort(sizebuf_t msg_read) {
        final short c;

        if (msg_read.readcount + 2 > msg_read.cursize)
            c = -1;
        else
            c = (short) ((msg_read.data[msg_read.readcount] & 0xff) + (msg_read.data[msg_read.readcount + 1] << 8));

        msg_read.readcount += 2;

        return c;
    }

    public static int ReadInt(sizebuf_t msg_read) {
        final int c;

        if (msg_read.readcount + 4 > msg_read.cursize) {
            Com.Printf("buffer underrun in ReadInt!");
            c = -1;
        }

        else
            c = msg_read.data[msg_read.readcount] & 0xff
                    | (msg_read.data[msg_read.readcount + 1] & 0xff) << 8
                    | (msg_read.data[msg_read.readcount + 2] & 0xff) << 16
                    | (msg_read.data[msg_read.readcount + 3] & 0xff) << 24;

        msg_read.readcount += 4;

        return c;
    }

    public static float ReadFloat(sizebuf_t msg_read) {
        return Float.intBitsToFloat(ReadInt(msg_read));
    }

    public static String ReadString(sizebuf_t msg_read) {
        // 2k read buffer.
        byte[] readbuf = new byte[2048];
        byte c;
        int l = 0;
        do {
            c = (byte) ReadByte(msg_read);
            if (c == -1 || c == 0)
                break;

            readbuf[l] = c;
            l++;
        } while (l < 2047);

        return new String(readbuf, 0, l);
    }

    public static float ReadCoord(sizebuf_t msg_read) {
        return ReadShort(msg_read) * (1.0f / 8);
    }

    public static void ReadPos(sizebuf_t msg_read, float pos[]) {
        assert pos.length == 3 : "vec3_t bug";
        pos[0] = ReadShort(msg_read) * (1.0f / 8);
        pos[1] = ReadShort(msg_read) * (1.0f / 8);
        pos[2] = ReadShort(msg_read) * (1.0f / 8);
    }

    public static float ReadAngleByte(sizebuf_t msg_read) {
        return ReadSignedByte(msg_read) * (180f / 128);
    }

    public static float ReadAngleShort(sizebuf_t msg_read) {
        return Math3D.SHORT2ANGLE(ReadShort(msg_read));
    }

    public static void ReadData(sizebuf_t msg_read, byte data[], int len) {
        for (int i = 0; i < len; i++)
            data[i] = (byte) ReadByte(msg_read);
    }

    public void clear() {
        if (data != null)
            Arrays.fill(data, (byte) 0);
        cursize = 0;
        overflowed = false;
    }

}