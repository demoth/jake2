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

// Created on 24.07.2004 by RST.

// $Id: QuakeFile.java,v 1.6 2005-11-20 22:18:34 salomo Exp $

package jake2.qcommon.filesystem;

import jake2.qcommon.Com;
import jake2.qcommon.Defines;
import jake2.qcommon.ServerEntity;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * RandomAccessFile, but handles readString/WriteString specially and offers
 * other helper functions
 */
public class QuakeFile extends RandomAccessFile {

    public final boolean fromPack;
    public final long length;

    public QuakeFile(String filename, String mode) throws FileNotFoundException {
        this(filename, mode, false, -1);
    }

    public QuakeFile(String filename, String mode, boolean fromPack, long length) throws FileNotFoundException {
        super(filename, mode);
        this.fromPack = fromPack;
        this.length = length;
    }

    public QuakeFile(File file, String mode, boolean fromPack, long length) throws FileNotFoundException {
        super(file, mode);
        this.fromPack = fromPack;
        this.length = length;
    }

    public byte[] toBytes() throws IOException {
        byte[] buf = new byte[(int) length];
        readFully(buf);
        close();
        return buf;
    }

    /** Writes a Vector to a RandomAccessFile. */
    public void writeVector(float v[]) throws IOException {
        for (int n = 0; n < 3; n++)
            writeFloat(v[n]);
    }

    /** Writes a Vector to a RandomAccessFile. */
    public float[] readVector() throws IOException {
        float res[] = { 0, 0, 0 };
        for (int n = 0; n < 3; n++)
            res[n] = readFloat();

        return res;
    }

    /** Reads a length specified string from a file. */
    public String readString() throws IOException {
        int len = readInt();

        if (len == -1)
            return null;

        if (len == 0)
            return "";

        byte bb[] = new byte[len];

        super.read(bb, 0, len);

        return new String(bb, 0, len);
    }

    /** Writes a length specified string to a file. */
    public void writeString(String s) throws IOException {
        if (s == null) {
            writeInt(-1);
            return;
        }

        writeInt(s.length());
        if (s.length() != 0)
            writeBytes(s);
    }

    /** Writes the edict reference. */
    public void writeEdictRef(ServerEntity ent) throws IOException {
        if (ent == null)
            writeInt(-1);
        else {
            writeInt(ent.s.index);
        }
    }

    /**
     * Reads an edict index from a file and returns the edict.
     */

    public ServerEntity readEdictRef(ServerEntity[] g_edicts) throws IOException {
        int i = readInt();

        // handle -1
        if (i < 0)
            return null;

        if (i > Defines.MAX_EDICTS) {
            Com.DPrintf("jake2: illegal edict num:" + i + "\n");
            return null;
        }

        // valid edict.
        return g_edicts[i];
    }

}