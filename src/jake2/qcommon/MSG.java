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

// Created on 29.11.2003 by RST.
// $Id: MSG.java,v 1.12 2004-02-01 00:35:00 rst Exp $

package jake2.qcommon;

import jake2.game.*;
import jake2.util.*;

public class MSG extends GameBase {

	//
	// writing functions
	//

	public static void WriteChar(sizebuf_t sb, int c) {
		if (c < -128 || c > 127)
			Com.Error(ERR_FATAL, "WriteChar: range error");

		sb.data[SZ.GetSpace(sb, 1)] = (byte) c;
	}
	public static void WriteChar(sizebuf_t sb, float c) {

		WriteChar(sb, (int) c);
	}
	public static void WriteByte(sizebuf_t sb, int c) {
		byte buf;

		if (c < 0 || c > 255)
			Com.Error(ERR_FATAL, "WriteByte: range error");

		sb.data[SZ.GetSpace(sb, 1)] = (byte) c;
	}

	public static void WriteByte(sizebuf_t sb, float c) {
		WriteByte(sb, (int) c);
	}

	public static void WriteShort(sizebuf_t sb, int c) {

		if (c < -32768 || c > 32767)
			Com.Error(ERR_FATAL, "WriteShort: range error");

		int i = SZ.GetSpace(sb, 2);
		sb.data[i++] = (byte) (c & 0xff);
		sb.data[i] = (byte) (c >>> 8);
	}

	public static void WriteInt(sizebuf_t sb, int c) {

		int i = SZ.GetSpace(sb, 4);
		sb.data[i++] = (byte) ((c & 0xff));
		sb.data[i++] = (byte) ((c >>> 8) & 0xff);
		sb.data[i++] = (byte) ((c >>> 16) & 0xff);
		sb.data[i++] = (byte) ((c >>> 24) &0xff);
	}

	public static void WriteLong(sizebuf_t sb, int c) {
		WriteInt(sb, c);
	}

	public static void WriteFloat(sizebuf_t sb, float f) {
		WriteInt(sb, Float.floatToIntBits(f));
	}

	public static void WriteString(sizebuf_t sb, String s) {
		String x = s;

		if (s == null)
			x = "";

		SZ.Write(sb, x.getBytes());
	}

	public static void WriteString(sizebuf_t sb, byte s[]) {
		if (s == null)
			s = new byte[0];
		SZ.Write(sb, s);
	}

	public static void WriteCoord(sizebuf_t sb, float f) {
		WriteShort(sb, (int) (f * 8));
	}

	public static void WritePos(sizebuf_t sb, float[] pos) {
		assert(pos.length == 3) : "vec3_t bug";
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

	public static void WriteDeltaUsercmd(sizebuf_t buf, usercmd_t from, usercmd_t cmd) {
		int bits;

		//
		// send the movement message
		//
		bits = 0;
		if (cmd.angles[0] != from.angles[0])
			bits |= CM_ANGLE1;
		if (cmd.angles[1] != from.angles[1])
			bits |= CM_ANGLE2;
		if (cmd.angles[2] != from.angles[2])
			bits |= CM_ANGLE3;
		if (cmd.forwardmove != from.forwardmove)
			bits |= CM_FORWARD;
		if (cmd.sidemove != from.sidemove)
			bits |= CM_SIDE;
		if (cmd.upmove != from.upmove)
			bits |= CM_UP;
		if (cmd.buttons != from.buttons)
			bits |= CM_BUTTONS;
		if (cmd.impulse != from.impulse)
			bits |= CM_IMPULSE;

		WriteByte(buf, bits);

		if ((bits & CM_ANGLE1) != 0)
			WriteShort(buf, cmd.angles[0]);
		if ((bits & CM_ANGLE2) != 0)
			WriteShort(buf, cmd.angles[1]);
		if ((bits & CM_ANGLE3) != 0)
			WriteShort(buf, cmd.angles[2]);

		if ((bits & CM_FORWARD) != 0)
			WriteShort(buf, cmd.forwardmove);
		if ((bits & CM_SIDE) != 0)
			WriteShort(buf, cmd.sidemove);
		if ((bits & CM_UP) != 0)
			WriteShort(buf, cmd.upmove);

		if ((bits & CM_BUTTONS) != 0)
			WriteByte(buf, cmd.buttons);
		if ((bits & CM_IMPULSE) != 0)
			WriteByte(buf, cmd.impulse);

		WriteByte(buf, cmd.msec);
		WriteByte(buf, cmd.lightlevel);
	}

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

	public static void ReadDir(sizebuf_t sb, float[] dir) {
		int b;

		b = ReadByte(sb);
		if (b >= NUMVERTEXNORMALS)
			Com.Error(ERR_DROP, "MSF_ReadDir: out of range");
		Math3D.VectorCopy(bytedirs[b], dir);
	}

	/*
	==================
	WriteDeltaEntity
	
	Writes part of a packetentities message.
	Can delta from either a baseline or a previous packet_entity
	==================
	*/
	public static void WriteDeltaEntity(
		entity_state_t from,
		entity_state_t to,
		sizebuf_t msg,
		boolean force,
		boolean newentity) {
		int bits;

		if (0 == to.number)
			Com.Error(ERR_FATAL, "Unset entity number");
		if (to.number >= MAX_EDICTS)
			Com.Error(ERR_FATAL, "Entity number >= MAX_EDICTS");

		// send an update
		bits = 0;

		if (to.number >= 256)
			bits |= U_NUMBER16; // number8 is implicit otherwise

		if (to.origin[0] != from.origin[0])
			bits |= U_ORIGIN1;
		if (to.origin[1] != from.origin[1])
			bits |= U_ORIGIN2;
		if (to.origin[2] != from.origin[2])
			bits |= U_ORIGIN3;

		if (to.angles[0] != from.angles[0])
			bits |= U_ANGLE1;
		if (to.angles[1] != from.angles[1])
			bits |= U_ANGLE2;
		if (to.angles[2] != from.angles[2])
			bits |= U_ANGLE3;

		if (to.skinnum != from.skinnum) {
			if (to.skinnum < 256)
				bits |= U_SKIN8;
			else if (to.skinnum < 0x10000)
				bits |= U_SKIN16;
			else
				bits |= (U_SKIN8 | U_SKIN16);
		}

		if (to.frame != from.frame) {
			if (to.frame < 256)
				bits |= U_FRAME8;
			else
				bits |= U_FRAME16;
		}

		if (to.effects != from.effects) {
			if (to.effects < 256)
				bits |= U_EFFECTS8;
			else if (to.effects < 0x8000)
				bits |= U_EFFECTS16;
			else
				bits |= U_EFFECTS8 | U_EFFECTS16;
		}

		if (to.renderfx != from.renderfx) {
			if (to.renderfx < 256)
				bits |= U_RENDERFX8;
			else if (to.renderfx < 0x8000)
				bits |= U_RENDERFX16;
			else
				bits |= U_RENDERFX8 | U_RENDERFX16;
		}

		if (to.solid != from.solid)
			bits |= U_SOLID;

		// event is not delta compressed, just 0 compressed
		if (to.event != 0)
			bits |= U_EVENT;

		if (to.modelindex != from.modelindex)
			bits |= U_MODEL;
		if (to.modelindex2 != from.modelindex2)
			bits |= U_MODEL2;
		if (to.modelindex3 != from.modelindex3)
			bits |= U_MODEL3;
		if (to.modelindex4 != from.modelindex4)
			bits |= U_MODEL4;

		if (to.sound != from.sound)
			bits |= U_SOUND;

		if (newentity || (to.renderfx & RF_BEAM) != 0)
			bits |= U_OLDORIGIN;

		//
		// write the message
		//
		if (bits == 0 && !force)
			return; // nothing to send!

		//----------

		if ((bits & 0xff000000) != 0)
			bits |= U_MOREBITS3 | U_MOREBITS2 | U_MOREBITS1;
		else if ((bits & 0x00ff0000) != 0)
			bits |= U_MOREBITS2 | U_MOREBITS1;
		else if ((bits & 0x0000ff00) != 0)
			bits |= U_MOREBITS1;

		WriteByte(msg, bits & 255);

		if ((bits & 0xff000000) != 0) {
			WriteByte(msg, (bits >>> 8) & 255);
			WriteByte(msg, (bits >>> 16) & 255);
			WriteByte(msg, (bits >>> 24) & 255);
		}
		else if ((bits & 0x00ff0000) != 0) {
			WriteByte(msg, (bits >>> 8) & 255);
			WriteByte(msg, (bits >>> 16) & 255);
		}
		else if ((bits & 0x0000ff00) != 0) {
			WriteByte(msg, (bits >>> 8) & 255);
		}

		//----------

		if ((bits & U_NUMBER16) != 0)
			WriteShort(msg, to.number);
		else
			WriteByte(msg, to.number);

		if ((bits & U_MODEL) != 0)
			WriteByte(msg, to.modelindex);
		if ((bits & U_MODEL2) != 0)
			WriteByte(msg, to.modelindex2);
		if ((bits & U_MODEL3) != 0)
			WriteByte(msg, to.modelindex3);
		if ((bits & U_MODEL4) != 0)
			WriteByte(msg, to.modelindex4);

		if ((bits & U_FRAME8) != 0)
			WriteByte(msg, to.frame);
		if ((bits & U_FRAME16) != 0)
			WriteShort(msg, to.frame);

		if ((bits & U_SKIN8) != 0 && (bits & U_SKIN16) != 0) //used for laser colors
			WriteInt(msg, to.skinnum);
		else if ((bits & U_SKIN8) != 0)
			WriteByte(msg, to.skinnum);
		else if ((bits & U_SKIN16) != 0)
			WriteShort(msg, to.skinnum);

		if ((bits & (U_EFFECTS8 | U_EFFECTS16)) == (U_EFFECTS8 | U_EFFECTS16))
			WriteInt(msg, to.effects);
		else if ((bits & U_EFFECTS8) != 0)
			WriteByte(msg, to.effects);
		else if ((bits & U_EFFECTS16) != 0)
			WriteShort(msg, to.effects);

		if ((bits & (U_RENDERFX8 | U_RENDERFX16)) == (U_RENDERFX8 | U_RENDERFX16))
			WriteInt(msg, to.renderfx);
		else if ((bits & U_RENDERFX8) != 0)
			WriteByte(msg, to.renderfx);
		else if ((bits & U_RENDERFX16) != 0)
			WriteShort(msg, to.renderfx);

		if ((bits & U_ORIGIN1) != 0)
			WriteCoord(msg, to.origin[0]);
		if ((bits & U_ORIGIN2) != 0)
			WriteCoord(msg, to.origin[1]);
		if ((bits & U_ORIGIN3) != 0)
			WriteCoord(msg, to.origin[2]);

		if ((bits & U_ANGLE1) != 0)
			WriteAngle(msg, to.angles[0]);
		if ((bits & U_ANGLE2) != 0)
			WriteAngle(msg, to.angles[1]);
		if ((bits & U_ANGLE3) != 0)
			WriteAngle(msg, to.angles[2]);

		if ((bits & U_OLDORIGIN) != 0) {
			WriteCoord(msg, to.old_origin[0]);
			WriteCoord(msg, to.old_origin[1]);
			WriteCoord(msg, to.old_origin[2]);
		}

		if ((bits & U_SOUND) != 0)
			WriteByte(msg, to.sound);
		if ((bits & U_EVENT) != 0)
			WriteByte(msg, to.event);
		if ((bits & U_SOLID) != 0)
			WriteShort(msg, to.solid);
	}

	//============================================================

	//
	// reading functions
	//

	public static void BeginReading(sizebuf_t msg) {
		msg.readcount = 0;
	}

	// returns -1 if no more characters are available
	public static int ReadChar(sizebuf_t msg_read) {
		int c;

		if (msg_read.readcount + 1 > msg_read.cursize)
			c = -1;
		else
			c = 0xff & msg_read.data[msg_read.readcount];
		msg_read.readcount++;

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

		if (msg_read.readcount + 4 > msg_read.cursize)
			c = -1;
		else
			c =
				msg_read.data[msg_read.readcount] &0xff
					+ ((msg_read.data[msg_read.readcount + 1] & 0xff) << 8)
					+ ((msg_read.data[msg_read.readcount + 2] & 0xff) << 16)
					+ ((msg_read.data[msg_read.readcount + 3] & 0xff) << 24);

		msg_read.readcount += 4;

		return c;
	}

	public static float ReadFloat(sizebuf_t msg_read) {
		int n = ReadLong(msg_read);
		return Float.intBitsToFloat(n);
	}

	// 2k read  buffer.
	public static byte readbuf[] = new byte[2048];

	public static String ReadString(sizebuf_t msg_read) {
		String string = "";

		byte c;
		int l = 0;
		do {
			c = (byte) ReadByte(msg_read);
			if (c == -1 || c == 0)
				break;
				
			readbuf[l] = c;
			l++;
		}
		while (l < 2047);

		//readbuf[l] = 0;
		return new String(readbuf, 0, l);
	}

	// 2k read  buffer.
	public static byte readbuf1[] = new byte[2048];
	public static String ReadStringLine(sizebuf_t msg_read) {

		int l;
		byte c;

		l = 0;
		do {
			c = (byte) ReadChar(msg_read);
			if (c == -1 || c == 0 || c == '\n')
				break;
			readbuf1[l] = c;
			l++;
		}
		while (l < 2047);

		readbuf1[l] = 0;

		return new String(readbuf, 0, l);
	}

	public static float ReadCoord(sizebuf_t msg_read) {
		return ReadShort(msg_read) * (1.0f / 8);
	}

	public static void ReadPos(sizebuf_t msg_read, float pos[]) {
		assert(pos.length == 3) : "vec3_t bug";
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

	public static void ReadDeltaUsercmd(sizebuf_t msg_read, usercmd_t from, usercmd_t move) {
		int bits;

		//memcpy(move, from, sizeof(* move));
		move.reset();

		bits = ReadByte(msg_read);

		// read current angles
		if ((bits & CM_ANGLE1) != 0)
			move.angles[0] = ReadShort(msg_read);
		if ((bits & CM_ANGLE2) != 0)
			move.angles[1] = ReadShort(msg_read);
		if ((bits & CM_ANGLE3) != 0)
			move.angles[2] = ReadShort(msg_read);

		// read movement
		if ((bits & CM_FORWARD) != 0)
			move.forwardmove = ReadShort(msg_read);
		if ((bits & CM_SIDE) != 0)
			move.sidemove = ReadShort(msg_read);
		if ((bits & CM_UP) != 0)
			move.upmove = ReadShort(msg_read);

		// read buttons
		if ((bits & CM_BUTTONS) != 0)
			move.buttons = (byte) ReadByte(msg_read);

		if ((bits & CM_IMPULSE) != 0)
			move.impulse = (byte) ReadByte(msg_read);

		// read time to run command
		move.msec = (byte) ReadByte(msg_read);

		// read the light level
		move.lightlevel = (byte) ReadByte(msg_read);
	}

	public static void ReadData(sizebuf_t msg_read, byte data[], int len) {
		int i;

		for (i = 0; i < len; i++)
			data[i] = (byte) ReadByte(msg_read);
	}

	//============================================================================
}
