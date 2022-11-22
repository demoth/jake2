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
// $Id: moveinfo_t.java,v 1.2 2004-08-20 21:29:58 salomo Exp $

package jake2.game;

import jake2.game.adapters.EntThinkAdapter;
import jake2.game.adapters.SuperAdapter;
import jake2.game.func.MovementState;
import jake2.qcommon.filesystem.QuakeFile;

import java.io.IOException;

public class moveinfo_t {
	// fixed data
	public float[] start_origin= { 0, 0, 0 };
	public float[] start_angles= { 0, 0, 0 };
	public float[] end_origin= { 0, 0, 0 };
	public float[] end_angles= { 0, 0, 0 };

	public int sound_start;
	public int sound_middle;
	public int sound_end;

	public float accel;
	public float speed;
	public float decel;
	public float distance;

	public float wait;

	// state data
	public MovementState state;
	public float[] dir= { 0, 0, 0 };

	public float current_speed;
	public float move_speed;
	public float next_speed;
	public float remaining_distance;
	public float decel_distance;
	public EntThinkAdapter endfunc;

	/** saves the moveinfo to the file.*/
	public void write(QuakeFile f) throws IOException {
		f.writeVector(start_origin);
		f.writeVector(start_angles);
		f.writeVector(end_origin);
		f.writeVector(end_angles);

		f.writeInt(sound_start);
		f.writeInt(sound_middle);
		f.writeInt(sound_end);

		f.writeFloat(accel);
		f.writeFloat(speed);
		f.writeFloat(decel);
		f.writeFloat(distance);

		f.writeFloat(wait);

		f.writeString(state.name());
		f.writeVector(dir);

		f.writeFloat(current_speed);
		f.writeFloat(move_speed);
		f.writeFloat(next_speed);
		f.writeFloat(remaining_distance);
		f.writeFloat(decel_distance);
		SuperAdapter.writeAdapter(f, endfunc);
	}

	/** Reads the moveinfo from a file. */
	public void read(QuakeFile f) throws IOException {
		start_origin= f.readVector();
		start_angles= f.readVector();
		end_origin= f.readVector();
		end_angles= f.readVector();

		sound_start= f.readInt();
		sound_middle= f.readInt();
		sound_end= f.readInt();

		accel= f.readFloat();
		speed= f.readFloat();
		decel= f.readFloat();
		distance= f.readFloat();

		wait= f.readFloat();

		state = MovementState.valueOf(f.readString());
		dir= f.readVector();

		current_speed= f.readFloat();
		move_speed= f.readFloat();
		next_speed= f.readFloat();
		remaining_distance= f.readFloat();
		decel_distance= f.readFloat();
		endfunc= (EntThinkAdapter) SuperAdapter.readAdapter(f);
	}
}
