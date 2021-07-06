/*
 * Copyright (C) 1997-2001 Id Software, Inc.
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.
 * 
 * See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place - Suite 330, Boston, MA 02111-1307, USA.
 *  
 */

// Created on 29.11.2003 by RST.
// $Id: MSG.java,v 1.8 2005-12-18 22:10:02 cawe Exp $
package jake2.qcommon;

import jake2.qcommon.util.Lib;
import jake2.qcommon.util.Math3D;

public class MSG extends Globals {

    //
    // writing functions
    //

    //ok.
    public static void WriteChar(sizebuf_t sb, float c) {

        WriteByte(sb, (int) c);
    }

    //ok.
    public static void WriteByte(sizebuf_t sb, int c) {
        sb.data[SZ.GetSpace(sb, 1)] = (byte) (c & 0xFF);
    }

    //ok.
    public static void WriteByte(sizebuf_t sb, float c) {
        WriteByte(sb, (int) c);
    }

    public static void WriteShort(sizebuf_t sb, int c) {
        int i = SZ.GetSpace(sb, 2);
        sb.data[i++] = (byte) (c & 0xff);
        sb.data[i] = (byte) ((c >>> 8) & 0xFF);
    }

    //ok.
    public static void WriteInt(sizebuf_t sb, int c) {
        int i = SZ.GetSpace(sb, 4);
        sb.data[i++] = (byte) ((c & 0xff));
        sb.data[i++] = (byte) ((c >>> 8) & 0xff);
        sb.data[i++] = (byte) ((c >>> 16) & 0xff);
        sb.data[i++] = (byte) ((c >>> 24) & 0xff);
    }

    //ok.
    public static void WriteLong(sizebuf_t sb, int c) {
        WriteInt(sb, c);
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
        WriteByte(sb, 0);
        //Com.dprintln("MSG.WriteString:" + s.replace('\0', '@'));
    }

    //ok.
    public static void WriteString(sizebuf_t sb, byte s[]) {
        WriteString(sb, new String(s).trim());
    }

    public static void WriteCoord(sizebuf_t sb, float f) {
        WriteShort(sb, (int) (f * 8));
    }

    public static void WritePos(sizebuf_t sb, float[] pos) {
        assert (pos.length == 3) : "vec3_t bug";
        WriteShort(sb, (int) (pos[0] * 8));
        WriteShort(sb, (int) (pos[1] * 8));
        WriteShort(sb, (int) (pos[2] * 8));
    }

    public static void WriteAngle(sizebuf_t sb, float f) {
        WriteByte(sb, (int) (f * 256 / 360) & 255);
    }

    public static void WriteAngle16(sizebuf_t sb, float f) {
        WriteShort(sb, Math3D.ANGLE2SHORT(f));
    }

    //should be ok.
    public static void WriteDir(sizebuf_t sb, float[] dir) {
        int i, best;
        float d, bestd;

        if (dir == null) {
            WriteByte(sb, 0);
            return;
        }

        bestd = 0;
        best = 0;
        for (i = 0; i < NUMVERTEXNORMALS; i++) {
            d = Math3D.DotProduct(dir, bytedirs[i]);
            if (d > bestd) {
                bestd = d;
                best = i;
            }
        }
        WriteByte(sb, best);
    }

    //should be ok.
    public static void ReadDir(sizebuf_t sb, float[] dir) {
        int b;

        b = ReadByte(sb);
        if (b >= NUMVERTEXNORMALS)
            Com.Error(ERR_DROP, "MSF_ReadDir: out of range");
        Math3D.VectorCopy(bytedirs[b], dir);
    }

    /*
     * ================== WriteDeltaEntity
     * 
     * Writes part of a packetentities message. Can delta from either a baseline
     * or a previous packet_entity ==================
     */
    public static void WriteDeltaEntity(entity_state_t from, entity_state_t to,
            sizebuf_t msg, boolean force, boolean newentity) {

        int deltaFlags = getDeltaFlags(from, to, newentity);

        //
        // write the message
        //
        if (deltaFlags == 0 && !force)
            return; // nothing to send!

        WriteByte(msg, deltaFlags & 255);

        if ((deltaFlags & 0xff000000) != 0) {
            WriteByte(msg, (deltaFlags >>> 8) & 255);
            WriteByte(msg, (deltaFlags >>> 16) & 255);
            WriteByte(msg, (deltaFlags >>> 24) & 255);
        } else if ((deltaFlags & 0x00ff0000) != 0) {
            WriteByte(msg, (deltaFlags >>> 8) & 255);
            WriteByte(msg, (deltaFlags >>> 16) & 255);
        } else if ((deltaFlags & 0x0000ff00) != 0) {
            WriteByte(msg, (deltaFlags >>> 8) & 255);
        }

        //----------

        if ((deltaFlags & U_NUMBER16) != 0)
            WriteShort(msg, to.number);
        else
            WriteByte(msg, to.number);

        if ((deltaFlags & U_MODEL) != 0)
            WriteByte(msg, to.modelindex);
        if ((deltaFlags & U_MODEL2) != 0)
            WriteByte(msg, to.modelindex2);
        if ((deltaFlags & U_MODEL3) != 0)
            WriteByte(msg, to.modelindex3);
        if ((deltaFlags & U_MODEL4) != 0)
            WriteByte(msg, to.modelindex4);

        if ((deltaFlags & U_FRAME8) != 0)
            WriteByte(msg, to.frame);
        if ((deltaFlags & U_FRAME16) != 0)
            WriteShort(msg, to.frame);

        if ((deltaFlags & U_SKIN8) != 0 && (deltaFlags & U_SKIN16) != 0) //used for laser
                                                             // colors
            WriteInt(msg, to.skinnum);
        else if ((deltaFlags & U_SKIN8) != 0)
            WriteByte(msg, to.skinnum);
        else if ((deltaFlags & U_SKIN16) != 0)
            WriteShort(msg, to.skinnum);

        if ((deltaFlags & (U_EFFECTS8 | U_EFFECTS16)) == (U_EFFECTS8 | U_EFFECTS16))
            WriteInt(msg, to.effects);
        else if ((deltaFlags & U_EFFECTS8) != 0)
            WriteByte(msg, to.effects);
        else if ((deltaFlags & U_EFFECTS16) != 0)
            WriteShort(msg, to.effects);

        if ((deltaFlags & (U_RENDERFX8 | U_RENDERFX16)) == (U_RENDERFX8 | U_RENDERFX16))
            WriteInt(msg, to.renderfx);
        else if ((deltaFlags & U_RENDERFX8) != 0)
            WriteByte(msg, to.renderfx);
        else if ((deltaFlags & U_RENDERFX16) != 0)
            WriteShort(msg, to.renderfx);

        if ((deltaFlags & U_ORIGIN1) != 0)
            WriteCoord(msg, to.origin[0]);
        if ((deltaFlags & U_ORIGIN2) != 0)
            WriteCoord(msg, to.origin[1]);
        if ((deltaFlags & U_ORIGIN3) != 0)
            WriteCoord(msg, to.origin[2]);

        if ((deltaFlags & U_ANGLE1) != 0)
            WriteAngle(msg, to.angles[0]);
        if ((deltaFlags & U_ANGLE2) != 0)
            WriteAngle(msg, to.angles[1]);
        if ((deltaFlags & U_ANGLE3) != 0)
            WriteAngle(msg, to.angles[2]);

        if ((deltaFlags & U_OLDORIGIN) != 0) {
            WriteCoord(msg, to.old_origin[0]);
            WriteCoord(msg, to.old_origin[1]);
            WriteCoord(msg, to.old_origin[2]);
        }

        if ((deltaFlags & U_SOUND) != 0)
            WriteByte(msg, to.sound);
        if ((deltaFlags & U_EVENT) != 0)
            WriteByte(msg, to.event);
        if ((deltaFlags & U_SOLID) != 0)
            WriteShort(msg, to.solid);
    }

    private static int getDeltaFlags(entity_state_t from, entity_state_t to, boolean newentity) {
        if (0 == to.number)
            Com.Error(ERR_FATAL, "Unset entity number");
        if (to.number >= MAX_EDICTS)
            Com.Error(ERR_FATAL, "Entity number >= MAX_EDICTS");

        // send an update
        int deltaFlags = 0;

        if (to.number >= 256)
            deltaFlags |= U_NUMBER16; // number8 is implicit otherwise

        if (to.origin[0] != from.origin[0])
            deltaFlags |= U_ORIGIN1;
        if (to.origin[1] != from.origin[1])
            deltaFlags |= U_ORIGIN2;
        if (to.origin[2] != from.origin[2])
            deltaFlags |= U_ORIGIN3;

        if (to.angles[0] != from.angles[0])
            deltaFlags |= U_ANGLE1;
        if (to.angles[1] != from.angles[1])
            deltaFlags |= U_ANGLE2;
        if (to.angles[2] != from.angles[2])
            deltaFlags |= U_ANGLE3;

        if (to.skinnum != from.skinnum) {
            if (to.skinnum < 256)
                deltaFlags |= U_SKIN8;
            else if (to.skinnum < 0x10000)
                deltaFlags |= U_SKIN16;
            else
                deltaFlags |= (U_SKIN8 | U_SKIN16);
        }

        if (to.frame != from.frame) {
            if (to.frame < 256)
                deltaFlags |= U_FRAME8;
            else
                deltaFlags |= U_FRAME16;
        }

        if (to.effects != from.effects) {
            if (to.effects < 256)
                deltaFlags |= U_EFFECTS8;
            else if (to.effects < 0x8000)
                deltaFlags |= U_EFFECTS16;
            else
                deltaFlags |= U_EFFECTS8 | U_EFFECTS16;
        }

        if (to.renderfx != from.renderfx) {
            if (to.renderfx < 256)
                deltaFlags |= U_RENDERFX8;
            else if (to.renderfx < 0x8000)
                deltaFlags |= U_RENDERFX16;
            else
                deltaFlags |= U_RENDERFX8 | U_RENDERFX16;
        }

        if (to.solid != from.solid)
            deltaFlags |= U_SOLID;

        // event is not delta compressed, just 0 compressed
        if (to.event != 0)
            deltaFlags |= U_EVENT;

        if (to.modelindex != from.modelindex)
            deltaFlags |= U_MODEL;
        if (to.modelindex2 != from.modelindex2)
            deltaFlags |= U_MODEL2;
        if (to.modelindex3 != from.modelindex3)
            deltaFlags |= U_MODEL3;
        if (to.modelindex4 != from.modelindex4)
            deltaFlags |= U_MODEL4;

        if (to.sound != from.sound)
            deltaFlags |= U_SOUND;

        if (newentity || (to.renderfx & RF_BEAM) != 0)
            deltaFlags |= U_OLDORIGIN;

        //----------

        if ((deltaFlags & 0xff000000) != 0)
            deltaFlags |= U_MOREBITS3 | U_MOREBITS2 | U_MOREBITS1;
        else if ((deltaFlags & 0x00ff0000) != 0)
            deltaFlags |= U_MOREBITS2 | U_MOREBITS1;
        else if ((deltaFlags & 0x0000ff00) != 0)
            deltaFlags |= U_MOREBITS1;
        return deltaFlags;
    }

    public static int getDeltaSize(entity_state_t from, entity_state_t to, boolean newentity) {
        int flags = getDeltaFlags(from, to, newentity);
        if (flags == 0)
            return 0;

        int result = 1;

        if ((flags & 0xff000000) != 0) {
            result += 3;
        } else if ((flags & 0x00ff0000) != 0) {
            result += 2;
        } else if ((flags & 0x0000ff00) != 0) {
            result += 1;
        }

        //----------

        if ((flags & U_NUMBER16) != 0)
            result += 2;
        else
            result +=1;

        if ((flags & U_MODEL) != 0)
            result += 1;
        if ((flags & U_MODEL2) != 0)
            result += 1;
        if ((flags & U_MODEL3) != 0)
            result += 1;
        if ((flags & U_MODEL4) != 0)
            result += 1;

        if ((flags & U_FRAME8) != 0)
            result += 1;
        if ((flags & U_FRAME16) != 0)
            result += 2;

        if ((flags & U_SKIN8) != 0 && (flags & U_SKIN16) != 0) //used for laser
            // colors
            result += 4;
        else if ((flags & U_SKIN8) != 0)
            result += 1;
        else if ((flags & U_SKIN16) != 0)
            result += 2;

        if ((flags & (U_EFFECTS8 | U_EFFECTS16)) == (U_EFFECTS8 | U_EFFECTS16))
            result += 4;
        else if ((flags & U_EFFECTS8) != 0)
            result += 1;
        else if ((flags & U_EFFECTS16) != 0)
            result += 2;

        if ((flags & (U_RENDERFX8 | U_RENDERFX16)) == (U_RENDERFX8 | U_RENDERFX16))
            result += 4;
        else if ((flags & U_RENDERFX8) != 0)
            result += 1;
        else if ((flags & U_RENDERFX16) != 0)
            result += 2;

        if ((flags & U_ORIGIN1) != 0)
            result += 2;
        if ((flags & U_ORIGIN2) != 0)
            result += 2;
        if ((flags & U_ORIGIN3) != 0)
            result += 2;

        if ((flags & U_ANGLE1) != 0)
            result += 1;
        if ((flags & U_ANGLE2) != 0)
            result += 1;
        if ((flags & U_ANGLE3) != 0)
            result += 1;

        if ((flags & U_OLDORIGIN) != 0) {
            result += 6;
        }

        if ((flags & U_SOUND) != 0)
            result += 1;
        if ((flags & U_EVENT) != 0)
            result += 1;
        if ((flags & U_SOLID) != 0)
            result += 2;

        return result;
    }

    //============================================================

    // returns -1 if no more characters are available, but also [-128 , 127]
    public static int ReadChar(sizebuf_t msg_read) {
        int c;

        if (msg_read.readcount + 1 > msg_read.cursize)
            c = -1;
        else
            c = msg_read.data[msg_read.readcount];
        msg_read.readcount++;
        // kickangles bugfix (rst)
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
        int c;

        if (msg_read.readcount + 2 > msg_read.cursize)
            c = -1;
        else
            c = (short) ((msg_read.data[msg_read.readcount] & 0xff) + (msg_read.data[msg_read.readcount + 1] << 8));

        msg_read.readcount += 2;

        return (short) c;
    }

    public static int ReadLong(sizebuf_t msg_read) {
        int c;

        if (msg_read.readcount + 4 > msg_read.cursize) {
            Com.Printf("buffer underrun in ReadLong!");
            c = -1;
        }

        else
            c = (msg_read.data[msg_read.readcount] & 0xff)
                    | ((msg_read.data[msg_read.readcount + 1] & 0xff) << 8)
                    | ((msg_read.data[msg_read.readcount + 2] & 0xff) << 16)
                    | ((msg_read.data[msg_read.readcount + 3] & 0xff) << 24);

        msg_read.readcount += 4;

        return c;
    }

    public static float ReadFloat(sizebuf_t msg_read) {
        int n = ReadLong(msg_read);
        return Float.intBitsToFloat(n);
    }

    // 2k read buffer.
    public static byte readbuf[] = new byte[2048];

    public static String ReadString(sizebuf_t msg_read) {

        byte c;
        int l = 0;
        do {
            c = (byte) ReadByte(msg_read);
            if (c == -1 || c == 0)
                break;

            readbuf[l] = c;
            l++;
        } while (l < 2047);
        
        String ret = new String(readbuf, 0, l);
        // Com.dprintln("MSG.ReadString:[" + ret + "]");
        return ret;
    }

    public static String ReadStringLine(sizebuf_t msg_read) {

        int l;
        byte c;

        l = 0;
        do {
            c = (byte) ReadChar(msg_read);
            if (c == -1 || c == 0 || c == 0x0a)
                break;
            readbuf[l] = c;
            l++;
        } while (l < 2047);
        
        String ret = new String(readbuf, 0, l).trim();
        Com.dprintln("MSG.ReadStringLine:[" + ret.replace('\0', '@') + "]");
        return ret;
    }

    public static float ReadCoord(sizebuf_t msg_read) {
        return ReadShort(msg_read) * (1.0f / 8);
    }

    public static void ReadPos(sizebuf_t msg_read, float pos[]) {
        assert (pos.length == 3) : "vec3_t bug";
        pos[0] = ReadShort(msg_read) * (1.0f / 8);
        pos[1] = ReadShort(msg_read) * (1.0f / 8);
        pos[2] = ReadShort(msg_read) * (1.0f / 8);
    }

    public static float ReadAngle(sizebuf_t msg_read) {
        return ReadChar(msg_read) * (360.0f / 256);
    }

    public static float ReadAngle16(sizebuf_t msg_read) {
        return Math3D.SHORT2ANGLE(ReadShort(msg_read));
    }

    public static void ReadData(sizebuf_t msg_read, byte data[], int len) {
        for (int i = 0; i < len; i++)
            data[i] = (byte) ReadByte(msg_read);
    }    
            
}