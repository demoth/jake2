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

import java.io.IOException;

import jake2.util.QuakeFile;

public class moveinfo_t {
	// fixed data
	float[] start_origin= { 0, 0, 0 };
	float[] start_angles= { 0, 0, 0 };
	float[] end_origin= { 0, 0, 0 };
	float[] end_angles= { 0, 0, 0 };

	int sound_start;
	int sound_middle;
	int sound_end;

	float accel;
	float speed;
	float decel;
	float distance;

	float wait;

	// state data
	int state;
	float[] dir= { 0, 0, 0 };

	float current_speed;
	float move_speed;
	float next_speed;
	float remaining_distance;
	float decel_distance;
	EntThinkAdapter endfunc;

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

		f.writeInt(state);
		f.writeVector(dir);

		f.writeFloat(current_speed);
		f.writeFloat(move_speed);
		f.writeFloat(next_speed);
		f.writeFloat(remaining_distance);
		f.writeFloat(decel_distance);
		f.writeAdapter(endfunc);
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

		state= f.readInt();
		dir= f.readVector();

		current_speed= f.readFloat();
		move_speed= f.readFloat();
		next_speed= f.readFloat();
		remaining_distance= f.readFloat();
		decel_distance= f.readFloat();
		endfunc= (EntThinkAdapter) f.readAdapter();
	}
}
