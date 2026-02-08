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

// Created on 09.12.2003 by RST.
// $Id: Lib.java,v 1.19 2007-05-14 22:18:29 cawe Exp $

package jake2.qcommon.util;

import jake2.qcommon.Com;
import jake2.qcommon.Globals;
import jake2.qcommon.filesystem.FS;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public class Lib {


	/** Converts a vector to a string. */
	public static String vtos(float[] v) {
		return (int) v[0] + " " + (int) v[1] + " " + (int) v[2];
	}
	
	/** Converts a vector to a string. */
	public static String vtofs(float[] v) {
		return v[0] + " " + v[1] + " " + v[2];
	}
	
	/** Converts a vector to a beatiful string. */
	public static String vtofsbeaty(float[] v) {
		return Com.sprintf("%8.2f %8.2f %8.2f", v[0], v[1], v[2]);
	}
	
	/** Like in  libc. */
	public static short rand() {
		return (short)Globals.rnd.nextInt(Short.MAX_VALUE+1);
	}
	
	/** Like in libc. */
	public static float crandom() {
		return (Globals.rnd.nextFloat() - 0.5f) * 2.0f;
	}
	
	/** Like in libc. */
	public static float random() {
		return Globals.rnd.nextFloat();
	}

	public static float atof(String in) {
		if (in == null || in.length() == 0)
			return 0;
		try {
			return Float.parseFloat(in);
		} catch (Exception e) {
			return 0;
		}
	}
	
	/** Like in quake2. */
	public static int Q_stricmp(String in1, String in2) {
		return in1.compareToIgnoreCase(in2);
	}
	
	/** Like in libc. */
	public static int atoi(String in) {
		try {
			return Integer.parseInt(in);
		}
		catch (Exception e) {
			try {
				return (int)Double.parseDouble(in);
			} catch (Exception e1) {
				return 0;
			}
		}
	}
	
	/** Converts a string to a vector. Needs improvement. */
	public static float[] atov(String v) {
		float[] res = { 0, 0, 0 };
		String strres[] = v.split(" ");
		for (int n=0; n < 3 && n < strres.length; n++)
		{
			res[n] = atof(strres[n]);
		}
		return res;
	}

	/** Like in libc. */
	public static int strlen(char in[]) {
		for (int i = 0; i < in.length; i++)
			if (in[i] == 0)
				return i;
		return in.length;
	}
	
	/** Like in libc. */
	public static int strlen(byte in[]) {
		for (int i = 0; i < in.length; i++)
			if (in[i] == 0)
				return i;
		return in.length;
	}

	/** Converts memory to a memory dump string. */
	public static String hexdumpfile(ByteBuffer bb, int len) throws IOException {
	
		ByteBuffer bb1 = bb.slice();
	
		byte buf[] = new byte[len];
	
		bb1.get(buf);
	
		return hexDump(buf, len, false);
	}
	
	/** Converts memory to a memory dump string. */
	public static String hexDump(byte data1[], int len, boolean showAddress) {
		StringBuffer result = new StringBuffer();
		StringBuffer charfield = new StringBuffer();
		int i = 0;
		while (i < len) {
			if ((i & 0xf) == 0) {
				if (showAddress) {
					String address = Integer.toHexString(i);
					address = ("0000".substring(0, 4 - address.length()) + address).toUpperCase();
					result.append(address + ": ");
				}
			}
			int v = data1[i];
	
			result.append(hex2(v));
			result.append(" ");
	
			charfield.append(readableChar(v));
			i++;
	
			// nach dem letzten, newline einfuegen
			if ((i & 0xf) == 0) {
				result.append(charfield);
				result.append("\n");
				charfield.setLength(0);
			}
			//	in der Mitte ein Luecke einfuegen ?
			else if ((i & 0xf) == 8) {
				result.append(" ");
			}
		}
		return result.toString();
	}
	
	/** Formats an hex byte. */
	public static String hex2(int i) {
		String val = Integer.toHexString(i & 0xff);
		return ("00".substring(0, 2 - val.length()) + val).toUpperCase();	
	}
	
	/** Returns true if the char is alphanumeric. */
	public static char readableChar(int i) {
		if ((i < 0x20) || (i > 0x7f))
			return '.';
		else
			return (char) i;
	}
	
	/** Prints a vector to the quake console. */
	public static void printv(String in, float arr[]) {
		for (int n = 0; n < arr.length; n++) {
			Com.Println(in + "[" + n + "]: " + arr[n]);
		}
	}
	
	static final byte nullfiller[] = new byte[8192];
		
	/** Like in libc. */
	public static void fwriteString(String s, int len, RandomAccessFile f) throws IOException {
		if (s ==  null) 
			return;
		int diff = len - s.length();
		if (diff > 0) {
			f.write(stringToBytes(s));
	
			f.write(nullfiller, 0, diff);
		}
		else
			f.write(stringToBytes(s), 0, len);
	}
	
	/** Like in libc */
	public static RandomAccessFile fopen(String name, String mode) {
		try {
			return new RandomAccessFile(name, mode);
		}
		catch (Exception e) {
			Com.DPrintf("Could not open file:" + name);
			return null;
		}
	}
	
	/** Like in libc */
	public static void fclose(RandomAccessFile f) {
		try {
			f.close();
		}
		catch (Exception e) {
		}
	}
	
	/** Like in libc */
	public static String freadString(RandomAccessFile f, int len) {
		byte buffer[] = new byte[len];
		FS.Read(buffer, len, f);
	
		return Lib.CtoJava(buffer);
	}
	
	/** Returns the right part of the string from the last occruence of c. */
	public static String rightFrom(String in, char c) {
		int pos = in.lastIndexOf(c);
		if (pos == -1)
			return "";
		else if (pos < in.length())
			return in.substring(pos + 1, in.length());
		return "";
	}
	
	/** Returns the left part of the string from the last occruence of c. */
	public static String leftFrom(String in, char c) {
		int pos = in.lastIndexOf(c);
		if (pos == -1)
			return "";
		else if (pos < in.length())
			return in.substring(0, pos);
		return "";
	}

	/** Renames a file. */
	public static int rename(String oldn, String newn) {
		try {
			File f1 = new File(oldn);
			File f2 = new File(newn);
			f1.renameTo(f2);
			return 0;
		}
		catch (Exception e) {
			return 1;
		}
	}
	
	/** Converts an int to 4 bytes java representation. */
	public static byte[] getIntBytes(int c) {
		byte b[] = new byte[4];
		b[0] = (byte) ((c & 0xff));
		b[1] = (byte) ((c >>> 8) & 0xff);
		b[2] = (byte) ((c >>> 16) & 0xff);
		b[3] = (byte) ((c >>> 24) & 0xff);
		return b;
	}
	
	/** Converts an 4 bytes java int representation to an int. */
	public static int getInt(byte b[]) {
		return (b[0] & 0xff) | ((b[1] & 0xff) << 8) | ((b[2] & 0xff) << 16) | ((b[3] & 0xff) << 24);
	}
	
	/** Duplicates a float array. */
	public static float[] clone(float in[]) {
		float out[] = new float[in.length];
	
		if (in.length != 0)
			System.arraycopy(in, 0, out, 0, in.length);
	
		return out;
	}
    
    /** 
     * convert a java string to byte[] with 8bit latin 1
     * 
     * avoid String.getBytes() because it is using system specific character encoding.
     */
    public static byte[] stringToBytes(String value) {
        try {
           return value.getBytes("ISO-8859-1");
        } catch (UnsupportedEncodingException e) {
            // can't happen: Latin 1 is a standard encoding
            return null;
        }
    }
    
    /** 
     * convert a byte[] with 8bit latin 1 to java string
     * 
     * avoid new String(bytes) because it is using system specific character encoding.
     */
    public static String bytesToString(byte[] value) {
        try {
           return new String(value, "ISO-8859-1");
        } catch (UnsupportedEncodingException e) {
            // can't happen: Latin 1 is a standard encoding
            return null;
        }
    }
	
	/** Helper method that savely handles the null termination of old C String data. */
	public static String CtoJava(String old) {
	    int index = old.indexOf('\0');
	    if (index == 0) return "";
	    return (index > 0) ? old.substring(0, index) : old; 
	}
	
	/** Helper method that savely handles the null termination of old C String data. */
	public static String CtoJava(byte[] old) {
		return CtoJava(old, 0, old.length);
	}

	/** Helper method that savely handles the null termination of old C String data. */
	public static String CtoJava(byte[] old, int offset, int maxLenght) {
		if (old.length == 0 || old[0] == 0) return "";
	    int i;
	    for (i = offset; (i - offset) < maxLenght && old[i] != 0; i++);
		return new String(old, offset, i - offset);
	}
	
	
	/* java.nio.* Buffer util functions */
	
	public static final int SIZEOF_FLOAT = 4;
	public static final int SIZEOF_INT = 4;
	
	public static FloatBuffer newFloatBuffer(int numElements) {
	  ByteBuffer bb = newByteBuffer(numElements * SIZEOF_FLOAT);
	  return bb.asFloatBuffer();
	}
	public static FloatBuffer newFloatBuffer(int numElements, ByteOrder order) {
	  ByteBuffer bb = newByteBuffer(numElements * SIZEOF_FLOAT, order);
	  return bb.asFloatBuffer();
	}
	public static IntBuffer newIntBuffer(int numElements) {
	  ByteBuffer bb = newByteBuffer(numElements * SIZEOF_INT);
	  return bb.asIntBuffer();
	}
	public static IntBuffer newIntBuffer(int numElements, ByteOrder order) {
	  ByteBuffer bb = newByteBuffer(numElements * SIZEOF_INT, order);
	  return bb.asIntBuffer();
	}
	public static ByteBuffer newByteBuffer(int numElements) {
	  ByteBuffer bb = ByteBuffer.allocateDirect(numElements);
	  bb.order(ByteOrder.nativeOrder());
	  return bb;
	}
	public static ByteBuffer newByteBuffer(int numElements, ByteOrder order) {
	  ByteBuffer bb = ByteBuffer.allocateDirect(numElements);
	  bb.order(order);
	  return bb;
	}

    /**
     * ED_NewString.
	 * Replace two character sequences (\n and \\) into 1 special symbol
     */
    public static String decodeBackslash(String string) {

        int l = string.length();
        StringBuilder newb = new StringBuilder(l);

        for (int i = 0; i < l; i++) {
            char c = string.charAt(i);
            if (c == '\\' && i < l - 1) {
                c = string.charAt(++i);
                if (c == 'n')
                    newb.append('\n');
                else
                    newb.append('\\'); // any special symbol other that \n is decoded into single slash: \
            } else
                newb.append(c);
        }
        return newb.toString();
    }
}
