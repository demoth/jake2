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

// Created on 31.10.2003 by RST.
// $Id: cplane_t.java,v 1.7 2004-03-18 10:05:08 hoz Exp $

package jake2.game;

import jake2.util.Lib;
import jake2.util.Math3D;

public class cplane_t
{
	public float normal[] = new float[3];
	public float dist;
	public byte type; // for fast side tests
	public byte signbits; // signx + (signy<<1) + (signz<<1)
	public byte pad[] = { 0, 0 };

	public cplane_t getClone()
	{
		cplane_t out = new cplane_t();
		Math3D.set(out.normal, normal);
		out.dist = dist;
		out.type = type;
		out.signbits = signbits;
		out.pad[0] = pad[0];
		out.pad[1] = pad[1];

		return out;
	}

	public void set(cplane_t c)
	{

		Math3D.set(normal, c.normal);
		dist = c.dist;
		type = c.type;
		signbits = c.signbits;
		pad[0] = c.pad[0];
		pad[1] = c.pad[1];
	}
}
