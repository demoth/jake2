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

package jake2.qcommon;


import java.util.Arrays;

public class usercmd_t implements Cloneable {
	public byte msec;
	public byte buttons;
	public short angles[]= new short[3];
	public short forwardmove, sidemove, upmove;
	public byte impulse; // remove?
	public byte lightlevel; // light level the player is standing on

	public void clear() {
		forwardmove= sidemove= upmove= msec= buttons= impulse= lightlevel= 0;
		angles[0] = angles[1] = angles[2] = 0;
	}

	public usercmd_t() {
	};

	// fixme: Copy constructor does not copy field 'angles'
	public usercmd_t(usercmd_t from) {
		msec= from.msec;
		buttons= from.buttons;
		angles[0]= from.angles[0];
		angles[1]= from.angles[1];
		angles[2]= from.angles[2];
		forwardmove= from.forwardmove;
		sidemove= from.sidemove;
		upmove= from.upmove;
		impulse= from.impulse;
		lightlevel= from.lightlevel;
	}

	public usercmd_t set(usercmd_t from) {
		msec= from.msec;
		buttons= from.buttons;
		angles[0]= from.angles[0];
		angles[1]= from.angles[1];
		angles[2]= from.angles[2];
		forwardmove= from.forwardmove;
		sidemove= from.sidemove;
		upmove= from.upmove;
		impulse= from.impulse;
		lightlevel= from.lightlevel;

		return this;
	}

	public usercmd_t(byte msec, byte buttons, short[] angles, short forwardmove, short sidemove, short upmove, byte impulse, byte lightlevel) {
		this.msec = msec;
		this.buttons = buttons;
		this.angles = angles;
		this.forwardmove = forwardmove;
		this.sidemove = sidemove;
		this.upmove = upmove;
		this.impulse = impulse;
		this.lightlevel = lightlevel;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		usercmd_t usercmd_t = (usercmd_t) o;

		if (msec != usercmd_t.msec) return false;
		if (buttons != usercmd_t.buttons) return false;
		if (forwardmove != usercmd_t.forwardmove) return false;
		if (sidemove != usercmd_t.sidemove) return false;
		if (upmove != usercmd_t.upmove) return false;
		if (impulse != usercmd_t.impulse) return false;
		if (lightlevel != usercmd_t.lightlevel) return false;
		return Arrays.equals(angles, usercmd_t.angles);
	}

	@Override
	public int hashCode() {
		int result = msec;
		result = 31 * result + (int) buttons;
		result = 31 * result + Arrays.hashCode(angles);
		result = 31 * result + (int) forwardmove;
		result = 31 * result + (int) sidemove;
		result = 31 * result + (int) upmove;
		result = 31 * result + (int) impulse;
		result = 31 * result + (int) lightlevel;
		return result;
	}

	@Override
	public String toString() {
		return "usercmd_t{" +
				"msec=" + msec +
				", buttons=" + buttons +
				", angles=" + Arrays.toString(angles) +
				", forwardmove=" + forwardmove +
				", sidemove=" + sidemove +
				", upmove=" + upmove +
				", impulse=" + impulse +
				", lightlevel=" + lightlevel +
				'}';
	}
}