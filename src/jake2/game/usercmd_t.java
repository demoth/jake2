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
// $Id: usercmd_t.java,v 1.10 2004-02-15 19:27:29 hoz Exp $

package jake2.game;

import java.util.Arrays;

import jake2.util.Lib;

public class usercmd_t implements Cloneable {
	public byte msec;
	public byte buttons;
	public short angles[] = new short[3];
	public short forwardmove, sidemove, upmove;
	public byte impulse; // remove?
	public byte lightlevel; // light level the player is standing on

	public void reset() {
		forwardmove = sidemove = upmove = msec = buttons = impulse = lightlevel = 0;
		Arrays.fill(angles, (short)0);
	}
	
	public usercmd_t() {};
	
	public usercmd_t(usercmd_t from) {
		msec = from.msec;
		buttons = from.buttons;
		angles[0] = from.angles[0];
		angles[1] = from.angles[1];
		angles[2] = from.angles[2];
		forwardmove = from.forwardmove;
		sidemove = from.sidemove;
		upmove = from.upmove;
		impulse = from.impulse;
		lightlevel = from.lightlevel;
	}
	
	public usercmd_t set(usercmd_t from)
	{
		msec = from.msec;
		buttons = from.buttons;
		angles[0] = from.angles[0];
		angles[1] = from.angles[1];
		angles[2] = from.angles[2];
		forwardmove = from.forwardmove;
		sidemove = from.sidemove;
		upmove = from.upmove;
		impulse = from.impulse;
		lightlevel = from.lightlevel;
		
		return this;
	}
		
	public usercmd_t getClone()
	{
		try {
			usercmd_t u1 = (usercmd_t) this.clone();
			u1.angles = Lib.clone(angles);
			return u1;
		}
		catch (CloneNotSupportedException e) {
			return null;
		}
	}
	
}