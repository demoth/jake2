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
// $Id: MSG.java,v 1.2 2003-11-30 21:50:08 rst Exp $

package jake2.qcommon;

import java.io.DataOutputStream;

import jake2.*;
import jake2.client.*;
import jake2.game.*;
import jake2.render.*;
import jake2.server.*;

public class MSG extends Globals {

	//
	// writing functions
	//

	static void MSG_WriteChar(sizebuf_t sb, int c) {
		if (c < -128 || c > 127)
			Com.Error(ERR_FATAL, "MSG_WriteChar: range error");

		sb.data[SZ_GetSpace(sb, 1)] = (byte) c;
	}

	public static void MSG_WriteByte(sizebuf_t sb, int c) {
		byte buf;

		if (c < 0 || c > 255)
			Com.Error(ERR_FATAL, "MSG_WriteByte: range error");

		sb.data[SZ_GetSpace(sb, 1)] = (byte) c;
	}

	public static void MSG_WriteShort(sizebuf_t sb, int c) {

		if (c < ((short) 0x8000) || c > (short) 0x7fff)
			Com.Error(ERR_FATAL, "MSG_WriteShort: range error");

		int i = SZ_GetSpace(sb, 2);
		sb.data[i++] = (byte) (c & 0xff);
		sb.data[i] = (byte) (c >> 8);
	}

	public static void MSG_WriteInt(sizebuf_t sb, int c) {

		int i = SZ_GetSpace(sb, 4);
		sb.data[i++] = (byte) ((c & 0xff));
		sb.data[i++] = (byte) ((c >> 8) & 0xff);
		sb.data[i++] = (byte) ((c >> 16) & 0xff);
		sb.data[i++] = (byte) (c >> 24);
	}

	public static void MSG_WriteFloat(sizebuf_t sb, float f) {
		MSG_WriteInt(sb, Float.floatToIntBits(f));
	}

	public static void MSG_WriteString(sizebuf_t sb, String s) {
		String x = s;

		if (s == null)
			x = "";

		SZ_Write(sb, x.getBytes());
	}

	public static void MSG_WriteCoord(sizebuf_t sb, float f) {
		MSG_WriteShort(sb, (int) (f * 8));
	}

	public static void MSG_WritePos(sizebuf_t sb, float[] pos) {
		MSG_WriteShort(sb, (int) (pos[0] * 8));
		MSG_WriteShort(sb, (int) (pos[1] * 8));
		MSG_WriteShort(sb, (int) (pos[2] * 8));
	}

	public static void MSG_WriteAngle(sizebuf_t sb, float f) {
		MSG_WriteByte(sb, (int) (f * 256 / 360) & 255);
	}

	public static void MSG_WriteAngle16(sizebuf_t sb, float f) {
		MSG_WriteShort(sb, ANGLE2SHORT(f));
	}

	public static void MSG_WriteDeltaUsercmd(sizebuf_t buf, usercmd_t from, usercmd_t cmd) {
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

		MSG_WriteByte(buf, bits);

		if ((bits & CM_ANGLE1) != 0)
			MSG_WriteShort(buf, cmd.angles[0]);
		if ((bits & CM_ANGLE2) != 0)
			MSG_WriteShort(buf, cmd.angles[1]);
		if ((bits & CM_ANGLE3) != 0)
			MSG_WriteShort(buf, cmd.angles[2]);

		if ((bits & CM_FORWARD) != 0)
			MSG_WriteShort(buf, cmd.forwardmove);
		if ((bits & CM_SIDE) != 0)
			MSG_WriteShort(buf, cmd.sidemove);
		if ((bits & CM_UP) != 0)
			MSG_WriteShort(buf, cmd.upmove);

		if ((bits & CM_BUTTONS) != 0)
			MSG_WriteByte(buf, cmd.buttons);
		if ((bits & CM_IMPULSE) != 0)
			MSG_WriteByte(buf, cmd.impulse);

		MSG_WriteByte(buf, cmd.msec);
		MSG_WriteByte(buf, cmd.lightlevel);
	}

	public static void MSG_WriteDir(sizebuf_t sb, float[] dir) {
		int i, best;
		float d, bestd;

		if (dir == null) {
			MSG_WriteByte(sb, 0);
			return;
		}

		bestd = 0;
		best = 0;
		for (i = 0; i < NUMVERTEXNORMALS; i++) {
			d = DotProduct(dir, bytedirs[i]);
			if (d > bestd) {
				bestd = d;
				best = i;
			}
		}
		MSG_WriteByte(sb, best);
	}

	public static void MSG_ReadDir(sizebuf_t sb, float[] dir) {
		int b;

		b = MSG_ReadByte(sb);
		if (b >= NUMVERTEXNORMALS)
			Com.Error(ERR_DROP, "MSF_ReadDir: out of range");
		VectorCopy(bytedirs[b], dir);
	}

	/*
	==================
	MSG_WriteDeltaEntity
	
	Writes part of a packetentities message.
	Can delta from either a baseline or a previous packet_entity
	==================
	*/
	public static void MSG_WriteDeltaEntity(entity_state_t from, entity_state_t to, sizebuf_t msg, boolean force, boolean newentity) {
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

		MSG_WriteByte(msg, bits & 255);

		if ((bits & 0xff000000) != 0) {
			MSG_WriteByte(msg, (bits >> 8) & 255);
			MSG_WriteByte(msg, (bits >> 16) & 255);
			MSG_WriteByte(msg, (bits >> 24) & 255);
		} else if ((bits & 0x00ff0000) != 0) {
			MSG_WriteByte(msg, (bits >> 8) & 255);
			MSG_WriteByte(msg, (bits >> 16) & 255);
		} else if ((bits & 0x0000ff00) != 0) {
			MSG_WriteByte(msg, (bits >> 8) & 255);
		}

		//----------

		if ((bits & U_NUMBER16) != 0)
			MSG_WriteShort(msg, to.number);
		else
			MSG_WriteByte(msg, to.number);

		if ((bits & U_MODEL) != 0)
			MSG_WriteByte(msg, to.modelindex);
		if ((bits & U_MODEL2) != 0)
			MSG_WriteByte(msg, to.modelindex2);
		if ((bits & U_MODEL3) != 0)
			MSG_WriteByte(msg, to.modelindex3);
		if ((bits & U_MODEL4) != 0)
			MSG_WriteByte(msg, to.modelindex4);

		if ((bits & U_FRAME8) != 0)
			MSG_WriteByte(msg, to.frame);
		if ((bits & U_FRAME16) != 0)
			MSG_WriteShort(msg, to.frame);

		if ((bits & U_SKIN8) != 0 && (bits & U_SKIN16) != 0) //used for laser colors
			MSG_WriteInt(msg, to.skinnum);
		else if ((bits & U_SKIN8) != 0)
			MSG_WriteByte(msg, to.skinnum);
		else if ((bits & U_SKIN16) != 0)
			MSG_WriteShort(msg, to.skinnum);

		if ((bits & (U_EFFECTS8 | U_EFFECTS16)) == (U_EFFECTS8 | U_EFFECTS16))
			MSG_WriteInt(msg, to.effects);
		else if ((bits & U_EFFECTS8) != 0)
			MSG_WriteByte(msg, to.effects);
		else if ((bits & U_EFFECTS16) != 0)
			MSG_WriteShort(msg, to.effects);

		if ((bits & (U_RENDERFX8 | U_RENDERFX16)) == (U_RENDERFX8 | U_RENDERFX16))
			MSG_WriteInt(msg, to.renderfx);
		else if ((bits & U_RENDERFX8) != 0)
			MSG_WriteByte(msg, to.renderfx);
		else if ((bits & U_RENDERFX16) != 0)
			MSG_WriteShort(msg, to.renderfx);

		if ((bits & U_ORIGIN1) != 0)
			MSG_WriteCoord(msg, to.origin[0]);
		if ((bits & U_ORIGIN2) != 0)
			MSG_WriteCoord(msg, to.origin[1]);
		if ((bits & U_ORIGIN3) != 0)
			MSG_WriteCoord(msg, to.origin[2]);

		if ((bits & U_ANGLE1) != 0)
			MSG_WriteAngle(msg, to.angles[0]);
		if ((bits & U_ANGLE2) != 0)
			MSG_WriteAngle(msg, to.angles[1]);
		if ((bits & U_ANGLE3) != 0)
			MSG_WriteAngle(msg, to.angles[2]);

		if ((bits & U_OLDORIGIN) != 0) {
			MSG_WriteCoord(msg, to.old_origin[0]);
			MSG_WriteCoord(msg, to.old_origin[1]);
			MSG_WriteCoord(msg, to.old_origin[2]);
		}

		if ((bits & U_SOUND) != 0)
			MSG_WriteByte(msg, to.sound);
		if ((bits & U_EVENT) != 0)
			MSG_WriteByte(msg, to.event);
		if ((bits & U_SOLID) != 0)
			MSG_WriteShort(msg, to.solid);
	}

	//============================================================

	//
	// reading functions
	//

	static void MSG_BeginReading(sizebuf_t msg) {
		msg.readcount = 0;
	}

	// returns -1 if no more characters are available
	public static int MSG_ReadChar(sizebuf_t msg_read) {
		int c;

		if (msg_read.readcount + 1 > msg_read.cursize)
			c = -1;
		else
			c = 0xff & msg_read.data[msg_read.readcount];
		msg_read.readcount++;

		return c;
	}

	public static int MSG_ReadByte(sizebuf_t msg_read) {
		int c;

		if (msg_read.readcount + 1 > msg_read.cursize)
			c = -1;
		else
			c = msg_read.data[msg_read.readcount] & 0xff;
		msg_read.readcount++;

		return c;
	}

	public static short MSG_ReadShort(sizebuf_t msg_read) {
		int c;

		if (msg_read.readcount + 2 > msg_read.cursize)
			c = -1;
		else
			c = (short) (msg_read.data[msg_read.readcount] + (msg_read.data[msg_read.readcount + 1] << 8));

		msg_read.readcount += 2;

		return (short) c;
	}

	public static int MSG_ReadLong(sizebuf_t msg_read) {
		int c;

		if (msg_read.readcount + 4 > msg_read.cursize)
			c = -1;
		else
			c =
				msg_read.data[msg_read.readcount]
					+ (msg_read.data[msg_read.readcount + 1] << 8)
					+ (msg_read.data[msg_read.readcount + 2] << 16)
					+ (msg_read.data[msg_read.readcount + 3] << 24);

		msg_read.readcount += 4;

		return c;
	}

	public static float MSG_ReadFloat(sizebuf_t msg_read) {
		int n = MSG_ReadLong(msg_read);
		return Float.intBitsToFloat(n);
	}

	// 2k read  buffer.
	public static byte readbuf[] = new byte[2048];

	// TODO: check
	public static String MSG_ReadString(sizebuf_t msg_read) {
		String string = "";

		byte c;
		int l = 0;
		do {
			c = (byte) MSG_ReadByte(msg_read);
			if (c == -1 || c == 0)
				break;
			readbuf[l] = c;
			l++;
		} while (l < 2047);

		readbuf[l] = 0;
		return new String(readbuf, 0, l);
	}

	// 2k read  buffer.
	public static byte readbuf1[] = new byte[2048];
	public static String MSG_ReadStringLine(sizebuf_t msg_read) {

		int l;
		byte c;

		l = 0;
		do {
			c = (byte) MSG_ReadChar(msg_read);
			if (c == -1 || c == 0 || c == '\n')
				break;
			readbuf1[l] = c;
			l++;
		} while (l < 2047);

		readbuf1[l] = 0;

		return new String(readbuf, 0, l);
	}

	public static float MSG_ReadCoord(sizebuf_t msg_read) {
		return MSG_ReadShort(msg_read) * (1.0f / 8);
	}

	public static void MSG_ReadPos(sizebuf_t msg_read, float pos[]) {
		pos[0] = MSG_ReadShort(msg_read) * (1.0f / 8);
		pos[1] = MSG_ReadShort(msg_read) * (1.0f / 8);
		pos[2] = MSG_ReadShort(msg_read) * (1.0f / 8);
	}

	public static float MSG_ReadAngle(sizebuf_t msg_read) {
		return MSG_ReadChar(msg_read) * (360.0f / 256);
	}

	public static float MSG_ReadAngle16(sizebuf_t msg_read) {
		return SHORT2ANGLE(MSG_ReadShort(msg_read));
	}

	public static void MSG_ReadDeltaUsercmd(sizebuf_t msg_read, usercmd_t from, usercmd_t move) {
		int bits;

		//memcpy(move, from, sizeof(* move));
		move.reset();

		bits = MSG_ReadByte(msg_read);

		// read current angles
		if ((bits & CM_ANGLE1) != 0)
			move.angles[0] = MSG_ReadShort(msg_read);
		if ((bits & CM_ANGLE2) != 0)
			move.angles[1] = MSG_ReadShort(msg_read);
		if ((bits & CM_ANGLE3) != 0)
			move.angles[2] = MSG_ReadShort(msg_read);

		// read movement
		if ((bits & CM_FORWARD) != 0)
			move.forwardmove = MSG_ReadShort(msg_read);
		if ((bits & CM_SIDE) != 0)
			move.sidemove = MSG_ReadShort(msg_read);
		if ((bits & CM_UP) != 0)
			move.upmove = MSG_ReadShort(msg_read);

		// read buttons
		if ((bits & CM_BUTTONS) != 0)
			move.buttons = (byte) MSG_ReadByte(msg_read);

		if ((bits & CM_IMPULSE) != 0)
			move.impulse = (byte) MSG_ReadByte(msg_read);

		// read time to run command
		move.msec = (byte) MSG_ReadByte(msg_read);

		// read the light level
		move.lightlevel = (byte) MSG_ReadByte(msg_read);
	}

	public static void MSG_ReadData(sizebuf_t msg_read, byte data[], int len) {
		int i;

		for (i = 0; i < len; i++)
			data[i] = (byte) MSG_ReadByte(msg_read);
	}

	//===========================================================================

	public static void SZ_Init(sizebuf_t buf, byte data[], int length) {
		//memset (buf, 0, sizeof(*buf));
		buf.data = data;
		buf.maxsize = length;
	}

	public static void SZ_Clear(sizebuf_t buf) {
		buf.cursize = 0;
		buf.overflowed = false;
	}

	/** Ask for the pointer using sizebuf_t.cursize (RST) */
	public static int SZ_GetSpace(sizebuf_t buf, int length) {
		int data;

		if (buf.cursize + length > buf.maxsize) {
			if (!buf.allowoverflow)
				Com.Error(ERR_FATAL, "SZ_GetSpace: overflow without allowoverflow set");

			if (length > buf.maxsize)
				Com.Error(ERR_FATAL, "SZ_GetSpace: " + length + " is > full buffer size");

			Com.Printf("SZ_GetSpace: overflow\n");
			SZ_Clear(buf);
			buf.overflowed = true;
		}

		data = buf.cursize;
		buf.cursize += length;

		return data;
	}

	public static void SZ_Write(sizebuf_t buf, byte data[], int length) {
		//memcpy(SZ_GetSpace(buf, length), data, length);
		System.arraycopy(data, 0, buf.data, SZ_GetSpace(buf, length), length);
	}

	public static void SZ_Write(sizebuf_t buf, byte data[]) {
		int length = data.length;
		//memcpy(SZ_GetSpace(buf, length), data, length);
		System.arraycopy(data, 0, buf.data, SZ_GetSpace(buf, length), length);
	}

	public static void SZ_Print(sizebuf_t buf, String data) {

		int length = data.length() + 1;
		byte str[] = data.getBytes();

		if (buf.cursize != 0) {

			if (buf.data[buf.cursize - 1] != 0) {
				//memcpy( SZ_GetSpace(buf, len), data, len); // no trailing 0
				System.arraycopy(data, 0, buf.data, SZ_GetSpace(buf, length), length);
			} else {
				System.arraycopy(data, 0, buf.data, SZ_GetSpace(buf, length), length);
				//memcpy(SZ_GetSpace(buf, len - 1) - 1, data, len); // write over trailing 0
			}

		} else
			System.arraycopy(data, 0, buf.data, SZ_GetSpace(buf, length), length);
		//memcpy(SZ_GetSpace(buf, len), data, len);
	}

	//============================================================================
}
