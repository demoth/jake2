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
// $Id: Lib.java,v 1.5 2004-01-02 17:40:54 rst Exp $

package jake2.util;

import java.io.IOException;
import java.io.RandomAccessFile;

import jake2.*;
import jake2.client.*;
import jake2.game.*;
import jake2.qcommon.*;
import jake2.render.*;
import jake2.server.*;

public class Lib {

	/*
	=============
	TempVector
	
	This is just a convenience function
	for making temporary vectors for function calls
	=============
	*/
	public static float tv_vecs[][] = new float[8][3];
	public static int tv_index;
	public static float[] tv(float x, float y, float z) {

		float[] v;

		// use an array so that multiple tempvectors won't collide
		// for a while
		v = tv_vecs[tv_index];
		tv_index = (tv_index++) & 7;

		v[0] = x;
		v[1] = y;
		v[2] = z;

		return v;
	}
	/*
	=============
	VectorToString
	
	This is just a convenience function
	for printing vectors
	=============
	*/
	public static String vtos(float[] v) {
		return "(" + (int) v[0] + " " + (int) v[1] + " " + (int) v[2] + ")";
	}
	public static short rand() {
		return (short) (Math.random() * 0x8000);
	}
	public static float crandom() {
		return (float) (Math.random() - 0.5) * 2.0f;
	}
	public static float random() {
		return (float) Math.random();
	}
	//TODO: delete this and clean up quake.
	public static int strcmp(String in1, String in2) {
		return in1.compareTo(in2);
	}
	public static int stricmp(String in1, String in2) {
		return in1.compareToIgnoreCase(in2);
	}
	//TODO: delete this and clean up quake.	
	public static int strncmp(String in1, String in2, int len) {
		int i1 = Math.min(len, in1.length());
		int i2 = Math.min(len, in2.length());

		if (i1 < i2)
			return -1;
		if (i1 > i2)
			return 1;

		for (int n = 0; n < i1; n++) {
			char c1 = in1.charAt(n);
			char c2 = in1.charAt(n);
			if (c1 < c2)
				return -1;
			if (c1 > c2)
				return 1;
		}
		return 0;
	}
	public static float atof(String in) {
		float res = 0;

		try {
			res = Float.parseFloat(in);
		}
		catch (Exception e) {
		}

		return res;
	}

	public static int Q_stricmp(String in1, String in2) {
		return in1.compareToIgnoreCase(in2);
	}

	//	  =================================================================================

	public static int atoi(String in) {
		try {
			return Integer.parseInt(in);
		}
		catch (Exception e) {
			return 0;
		}
	}

	public static float[] atov(String v) {
		float[] res = { 0, 0, 0 };

		int i1 = v.indexOf(" ");
		int i2 = v.indexOf(" ", i1 + 1);

		res[0] = atof(v.substring(0, i1));
		res[1] = atof(v.substring(i1 + 1, i2));
		res[2] = atof(v.substring(i2 + 1, v.length()));

		return res;
	}

	public static int strlen(String in) {
		return in.length();
	}

	public static int strlen(char in[]) {
		for (int i = 0; i < in.length; i++)
			if (in[i] == 0)
				return i;
		return in.length;
	}

	public static int strlen(byte in[]) {
		for (int i = 0; i < in.length; i++)
			if (in[i] == 0)
				return i;
		return in.length;
	}

	public static void strcat(String in, String i) {
		in += i;
	}

	public static void strcpy(char dest[], char src[]) {
		for (int i = 0; i < dest.length && i < src.length; i++)
			if (src[i] == 0) {
				dest[i] = 0;
				return;
			}
			else
				dest[i] = src[i];

	}

	public static void main(String args[]) {

		String v = "0.234 1.23423 7.23423";

		int i1 = v.indexOf(" ");
		int i2 = v.indexOf(" ", i1 + 1);

		System.out.println("parsing...");

		System.out.println("[" + v.substring(0, i1) + "]");
		System.out.println("[" + v.substring(i1 + 1, i2) + "]");
		System.out.println("[" + v.substring(i2 + 1, v.length()) + "]");
	}

	public static String readString(RandomAccessFile file, int len) throws IOException {
		byte buf[] = new byte[len];

		file.read(buf, 0, len);
		return new String(buf, 0, strlen(buf));
	}

	public static String hexdumpfile( RandomAccessFile file, int len) throws IOException
	{
		byte buf[] = new byte[len];

		file.read(buf, 0, len);
		
		return hexDump(buf,len,false);
	}

	// dump data as hexstring
	public static String hexDump(byte data1[], int len, boolean showAddress)
	{
		StringBuffer result= new StringBuffer();
		StringBuffer charfield= new StringBuffer();
		int i= 0;
		while (i < len)
		{
			if ((i & 0xf) == 0)
			{
				if (showAddress)
				{
					String address= Integer.toHexString(i);
					address= ("0000".substring(0, 4 - address.length()) + address).toUpperCase();
					result.append(address + ": ");
				}
			}
			int v= data1[i];

			result.append(hex2(v));
			result.append(" ");

			charfield.append(readableChar(v));
			i++;

			// nach dem letzten, newline einfuegen
			if ((i & 0xf) == 0)
			{
				result.append(charfield);
				result.append("\n");
				charfield.setLength(0);
			}
			//	in der Mitte ein Luecke einfuegen ?
			else if ((i & 0xf) == 8)
			{
				result.append(" ");
			}
		}
		return result.toString();
	}
	
	
	//formats an hex byte
	public static String hex2(int i)
	{
		String val= Integer.toHexString(i & 0xff);
		return ("00".substring(0, 2 - val.length()) + val).toUpperCase();
	}
	
		public static char readableChar(int i)
	{
		if ((i < 0x20) || (i > 0x7f))
			return '.';
		else
			return (char)i;
	}
}
