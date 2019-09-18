/*
 * java
 * Copyright (C) 2004
 * 
 * $Id: CL_input.java,v 1.7 2005-06-26 09:17:33 hzi Exp $
 */
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
package jake2.client;

import jake2.game.Cmd;
import jake2.game.usercmd_t;
import jake2.qcommon.*;
import jake2.qcommon.util.Lib;
import jake2.qcommon.util.Math3D;

import java.util.List;

/**
 * CL_input
 */
public class CL_input {

	private static long frame_msec;

	private static long old_sys_frame_time;

	private static cvar_t cl_nodelta;

	/*
	 * ===============================================================================
	 * 
	 * KEY BUTTONS
	 * 
	 * Continuous button event tracking is complicated by the fact that two
	 * different input sources (say, mouse button 1 and the control key) can
	 * both press the same button, but the button should only be released when
	 * both of the pressing key have been released.
	 * 
	 * When a key event issues a button command (+forward, +attack, etc), it
	 * appends its key number as a parameter to the command so it can be matched
	 * up with the release.
	 * 
	 * state bit 0 is the current state of the key state bit 1 is edge triggered
	 * on the up to down transition state bit 2 is edge triggered on the down to
	 * up transition
	 * 
	 * 
	 * Key_Event (int key, qboolean down, unsigned time);
	 * 
	 * +mlook src time
	 * 
	 * ===============================================================================
	 */

	private static kbutton_t in_klook = new kbutton_t();

	private static kbutton_t in_left = new kbutton_t();

	private static kbutton_t in_right = new kbutton_t();

	private static kbutton_t in_forward = new kbutton_t();

	private static kbutton_t in_back = new kbutton_t();

	private static kbutton_t in_lookup = new kbutton_t();

	private static kbutton_t in_lookdown = new kbutton_t();

	private static kbutton_t in_moveleft = new kbutton_t();

	private static kbutton_t in_moveright = new kbutton_t();

	public static kbutton_t in_strafe = new kbutton_t();

	private static kbutton_t in_speed = new kbutton_t();

	private static kbutton_t in_use = new kbutton_t();

	private static kbutton_t in_attack = new kbutton_t();

	private static kbutton_t in_up = new kbutton_t();

	private static kbutton_t in_down = new kbutton_t();

	private static int in_impulse;

	private static void KeyDown(kbutton_t b, List<String> args) {
		int k;

		if (args.size() >= 2)
			k = Lib.atoi(args.get(1));
		else
			k = -1; // typed manually at the console for continuous down

		if (k == b.down[0] || k == b.down[1])
			return; // repeating key

		if (b.down[0] == 0)
			b.down[0] = k;
		else if (b.down[1] == 0)
			b.down[1] = k;
		else {
			Com.Printf("Three keys down for a button!\n");
			return;
		}

		if ((b.state & 1) != 0)
			return; // still down

		// save timestamp
		if (args.size() >= 3)
			b.downtime = Lib.atoi(args.get(2));
		else
			b.downtime = Globals.sys_frame_time - 100;

		b.state |= 3; // down + impulse down
	}

	private static void KeyUp(kbutton_t b, List<String> args) {
		int k;

		if (args.size() >= 2)
			k = Lib.atoi(args.get(1));
		else {
			// typed manually at the console, assume for unsticking, so clear
			// all
			b.down[0] = b.down[1] = 0;
			b.state = 4; // impulse up
			return;
		}

		if (b.down[0] == k)
			b.down[0] = 0;
		else if (b.down[1] == k)
			b.down[1] = 0;
		else
			return; // key up without coresponding down (menu pass through)
		if (b.down[0] != 0 || b.down[1] != 0)
			return; // some other key is still holding it down

		if ((b.state & 1) == 0)
			return; // still up (this should not happen)

		// save timestamp
		if (args.size() >= 3)
			b.msec += Lib.atoi(args.get(2)) - b.downtime;
		else
			b.msec += 10;

		b.state &= ~1; // now up
		b.state |= 4; // impulse up
	}

	/*
	 * =============== CL_KeyState
	 * 
	 * Returns the fraction of the frame that the key was down ===============
	 */
	private static float KeyState(kbutton_t key) {
		float val;
		long msec;

		key.state &= 1; // clear impulses

		msec = key.msec;
		key.msec = 0;

		if (key.state != 0) {
			// still down
			msec += Globals.sys_frame_time - key.downtime;
			key.downtime = Globals.sys_frame_time;
		}

		val = (float) msec / frame_msec;
		if (val < 0)
			val = 0;
		if (val > 1)
			val = 1;

		return val;
	}

	//	  ==========================================================================

	/*
	 * ================ CL_AdjustAngles
	 * 
	 * Moves the local angle positions ================
	 */
	private static void AdjustAngles() {
		float speed;
		float up, down;

		if ((in_speed.state & 1) != 0)
			speed = Globals.cls.frametime * Globals.cl_anglespeedkey.value;
		else
			speed = Globals.cls.frametime;

		if ((in_strafe.state & 1) == 0) {
			Globals.cl.viewangles[Defines.YAW] -= speed * Globals.cl_yawspeed.value * KeyState(in_right);
			Globals.cl.viewangles[Defines.YAW] += speed * Globals.cl_yawspeed.value * KeyState(in_left);
		}
		if ((in_klook.state & 1) != 0) {
			Globals.cl.viewangles[Defines.PITCH] -= speed * Globals.cl_pitchspeed.value * KeyState(in_forward);
			Globals.cl.viewangles[Defines.PITCH] += speed * Globals.cl_pitchspeed.value * KeyState(in_back);
		}

		up = KeyState(in_lookup);
		down = KeyState(in_lookdown);

		Globals.cl.viewangles[Defines.PITCH] -= speed * Globals.cl_pitchspeed.value * up;
		Globals.cl.viewangles[Defines.PITCH] += speed * Globals.cl_pitchspeed.value * down;
	}

	/*
	 * ================ CL_BaseMove
	 * 
	 * Send the intended movement message to the server ================
	 */
	private static void BaseMove(usercmd_t cmd) {
		AdjustAngles();

		//memset (cmd, 0, sizeof(*cmd));
		cmd.clear();

		Math3D.VectorCopy(Globals.cl.viewangles, cmd.angles);
		if ((in_strafe.state & 1) != 0) {
			cmd.sidemove += Globals.cl_sidespeed.value * KeyState(in_right);
			cmd.sidemove -= Globals.cl_sidespeed.value * KeyState(in_left);
		}

		cmd.sidemove += Globals.cl_sidespeed.value * KeyState(in_moveright);
		cmd.sidemove -= Globals.cl_sidespeed.value * KeyState(in_moveleft);

		cmd.upmove += Globals.cl_upspeed.value * KeyState(in_up);
		cmd.upmove -= Globals.cl_upspeed.value * KeyState(in_down);

		if ((in_klook.state & 1) == 0) {
			cmd.forwardmove += Globals.cl_forwardspeed.value * KeyState(in_forward);
			cmd.forwardmove -= Globals.cl_forwardspeed.value * KeyState(in_back);
		}

		//
		//	   adjust for speed key / running
		//
		if (((in_speed.state & 1) ^ (int) (Globals.cl_run.value)) != 0) {
			cmd.forwardmove *= 2;
			cmd.sidemove *= 2;
			cmd.upmove *= 2;
		}

	}

	private static void ClampPitch() {

		float pitch;

		pitch = Math3D.SHORT2ANGLE(Globals.cl.frame.playerstate.pmove.delta_angles[Defines.PITCH]);
		if (pitch > 180)
			pitch -= 360;

		if (Globals.cl.viewangles[Defines.PITCH] + pitch < -360)
			Globals.cl.viewangles[Defines.PITCH] += 360; // wrapped
		if (Globals.cl.viewangles[Defines.PITCH] + pitch > 360)
			Globals.cl.viewangles[Defines.PITCH] -= 360; // wrapped

		if (Globals.cl.viewangles[Defines.PITCH] + pitch > 89)
			Globals.cl.viewangles[Defines.PITCH] = 89 - pitch;
		if (Globals.cl.viewangles[Defines.PITCH] + pitch < -89)
			Globals.cl.viewangles[Defines.PITCH] = -89 - pitch;
	}

	/*
	 * ============== CL_FinishMove ==============
	 */
	private static void FinishMove(usercmd_t cmd) {
		int ms;
		int i;

		//
		//	   figure button bits
		//	
		if ((in_attack.state & 3) != 0)
			cmd.buttons |= Defines.BUTTON_ATTACK;
		in_attack.state &= ~2;

		if ((in_use.state & 3) != 0)
			cmd.buttons |= Defines.BUTTON_USE;
		in_use.state &= ~2;

		if (Key.anykeydown != 0 && Globals.cls.key_dest == Defines.key_game)
			cmd.buttons |= Defines.BUTTON_ANY;

		// send milliseconds of time to apply the move
		ms = (int) (Globals.cls.frametime * 1000);
		if (ms > 250)
			ms = 100; // time was unreasonable
		cmd.msec = (byte) ms;

		ClampPitch();
		for (i = 0; i < 3; i++)
			cmd.angles[i] = (short) Math3D.ANGLE2SHORT(Globals.cl.viewangles[i]);

		cmd.impulse = (byte) in_impulse;
		in_impulse = 0;

		// send the ambient light level at the player's current position
		cmd.lightlevel = (byte) Globals.cl_lightlevel.value;
	}

	/*
	 * ================= CL_CreateCmd =================
	 */
	private static void CreateCmd(usercmd_t cmd) {
		//usercmd_t cmd = new usercmd_t();

		frame_msec = Globals.sys_frame_time - old_sys_frame_time;
		if (frame_msec < 1)
			frame_msec = 1;
		if (frame_msec > 200)
			frame_msec = 200;

		// get basic movement from keyboard
		BaseMove(cmd);

		// allow mice or other external controllers to add to the move
		IN.Move(cmd);

		FinishMove(cmd);

		old_sys_frame_time = Globals.sys_frame_time;

		//return cmd;
	}

	/*
	 * ============ CL_InitInput ============
	 */
	static void InitInput() {
		Cmd.AddCommand("centerview", (List<String> args) -> IN.CenterView());
		Cmd.AddCommand("+moveup", (List<String> args) -> KeyDown(in_up, args));
		Cmd.AddCommand("-moveup", (List<String> args) -> KeyUp(in_up, args));
		Cmd.AddCommand("+movedown", (List<String> args) -> KeyDown(in_down, args));
		Cmd.AddCommand("-movedown", (List<String> args) -> KeyUp(in_down, args));
		Cmd.AddCommand("+left", (List<String> args) -> KeyDown(in_left, args));
		Cmd.AddCommand("-left", (List<String> args) -> KeyUp(in_left, args));
		Cmd.AddCommand("+right", (List<String> args) -> KeyDown(in_right, args));
		Cmd.AddCommand("-right", (List<String> args) -> KeyUp(in_right, args));
		Cmd.AddCommand("+forward", (List<String> args) -> KeyDown(in_forward, args));
		Cmd.AddCommand("-forward", (List<String> args) -> KeyUp(in_forward, args));
		Cmd.AddCommand("+back", (List<String> args) -> KeyDown(in_back, args));
		Cmd.AddCommand("-back", (List<String> args) -> KeyUp(in_back, args));
		Cmd.AddCommand("+lookup", (List<String> args) -> KeyDown(in_lookup, args));
		Cmd.AddCommand("-lookup", (List<String> args) -> KeyUp(in_lookup, args));
		Cmd.AddCommand("+lookdown", (List<String> args) -> KeyDown(in_lookdown, args));
		Cmd.AddCommand("-lookdown", (List<String> args) -> KeyUp(in_lookdown, args));
		Cmd.AddCommand("+strafe", (List<String> args) -> KeyDown(in_strafe, args));
		Cmd.AddCommand("-strafe", (List<String> args) -> KeyUp(in_strafe, args));
		Cmd.AddCommand("+moveleft", (List<String> args) -> KeyDown(in_moveleft, args));
		Cmd.AddCommand("-moveleft", (List<String> args) -> KeyUp(in_moveleft, args));
		Cmd.AddCommand("+moveright", (List<String> args) -> KeyDown(in_moveright, args));
		Cmd.AddCommand("-moveright", (List<String> args) -> KeyUp(in_moveright, args));
		Cmd.AddCommand("+speed", (List<String> args) -> KeyDown(in_speed, args));
		Cmd.AddCommand("-speed", (List<String> args) -> KeyUp(in_speed, args));
		Cmd.AddCommand("+attack", (List<String> args) -> KeyDown(in_attack, args));
		Cmd.AddCommand("-attack", (List<String> args) -> KeyUp(in_attack, args));
		Cmd.AddCommand("+use", (List<String> args) -> KeyDown(in_use, args));
		Cmd.AddCommand("-use", (List<String> args) -> KeyUp(in_use, args));
		Cmd.AddCommand("impulse", (List<String> args) -> in_impulse = Lib.atoi(args.get(1)));
		Cmd.AddCommand("+klook", (List<String> args) -> KeyDown(in_klook, args));
		Cmd.AddCommand("-klook", (List<String> args) -> KeyUp(in_klook, args));

		cl_nodelta = Cvar.Get("cl_nodelta", "0", 0);
	}

	private static final sizebuf_t buf = new sizebuf_t();
	private static final byte[] data = new byte[128];
	private static final usercmd_t nullcmd = new usercmd_t();
	/*
	 * ================= CL_SendCmd =================
	 */
	static void SendCmd() {
		int i;
		usercmd_t cmd, oldcmd;
		int checksumIndex;

		// build a command even if not connected

		// save this command off for prediction
		i = Globals.cls.netchan.outgoing_sequence & (Defines.CMD_BACKUP - 1);
		cmd = Globals.cl.cmds[i];
		Globals.cl.cmd_time[i] = (int) Globals.cls.realtime; // for netgraph
															 // ping calculation

		// fill the cmd
		CreateCmd(cmd);

		Globals.cl.cmd.set(cmd);

		if (Globals.cls.state == Defines.ca_disconnected || Globals.cls.state == Defines.ca_connecting)
			return;

		if (Globals.cls.state == Defines.ca_connected) {
			if (Globals.cls.netchan.message.cursize != 0 || Globals.curtime - Globals.cls.netchan.last_sent > 1000)
				Netchan.Transmit(Globals.cls.netchan, 0, new byte[0]);
			return;
		}

		// send a userinfo update if needed
		if (Globals.userinfo_modified) {
			CL.FixUpGender();
			Globals.userinfo_modified = false;
			MSG.WriteByte(Globals.cls.netchan.message, Defines.clc_userinfo);
			MSG.WriteString(Globals.cls.netchan.message, Cvar.Userinfo());
		}

		SZ.Init(buf, data, data.length);

		if (cmd.buttons != 0 && Globals.cl.cinematictime > 0 && !Globals.cl.attractloop
				&& Globals.cls.realtime - Globals.cl.cinematictime > 1000) { // skip
																			 // the
																			 // rest
																			 // of
																			 // the
																			 // cinematic
			SCR.FinishCinematic();
		}

		// begin a client move command
		MSG.WriteByte(buf, Defines.clc_move);

		// save the position for a checksum byte
		checksumIndex = buf.cursize;
		MSG.WriteByte(buf, 0);

		// let the server know what the last frame we
		// got was, so the next message can be delta compressed
		if (cl_nodelta.value != 0.0f || !Globals.cl.frame.valid || Globals.cls.demowaiting)
			MSG.WriteLong(buf, -1); // no compression
		else
			MSG.WriteLong(buf, Globals.cl.frame.serverframe);

		// send this and the previous cmds in the message, so
		// if the last packet was dropped, it can be recovered
		i = (Globals.cls.netchan.outgoing_sequence - 2) & (Defines.CMD_BACKUP - 1);
		cmd = Globals.cl.cmds[i];
		//memset (nullcmd, 0, sizeof(nullcmd));
		nullcmd.clear();

		MSG.WriteDeltaUsercmd(buf, nullcmd, cmd);
		oldcmd = cmd;

		i = (Globals.cls.netchan.outgoing_sequence - 1) & (Defines.CMD_BACKUP - 1);
		cmd = Globals.cl.cmds[i];

		MSG.WriteDeltaUsercmd(buf, oldcmd, cmd);
		oldcmd = cmd;

		i = (Globals.cls.netchan.outgoing_sequence) & (Defines.CMD_BACKUP - 1);
		cmd = Globals.cl.cmds[i];

		MSG.WriteDeltaUsercmd(buf, oldcmd, cmd);

		// calculate a checksum over the move commands
		buf.data[checksumIndex] = Com.BlockSequenceCRCByte(buf.data, checksumIndex + 1, buf.cursize - checksumIndex - 1,
				Globals.cls.netchan.outgoing_sequence);

		//
		// deliver the message
		//
		Netchan.Transmit(Globals.cls.netchan, buf.cursize, buf.data);
	}
}