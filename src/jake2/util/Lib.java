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
// $Id: Lib.java,v 1.1 2003-12-09 22:12:44 rst Exp $

package jake2.util;


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
		} catch (Exception e) {
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
		} catch (Exception e) {
			return 0;
		}
	}

	public static int strlen(String in) {
		return in.length();
	}

	public static void strcat(String in, String i) {
		in += i;
	}
}