/*
 * CL_input.java
 * Copyright (C) 2004
 * 
 * $Id: CL_input.java,v 1.13 2004-02-12 14:23:15 cwei Exp $
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

import jake2.game.*;
import jake2.game.Cmd;
import jake2.game.usercmd_t;
import jake2.qcommon.*;
import jake2.qcommon.Cvar;
import jake2.qcommon.xcommand_t;
import jake2.sys.IN;

/**
 * CL_input
 */
public class CL_input extends CL_ents {
	
	static long frame_msec;
	static long old_sys_frame_time;

	static cvar_t cl_nodelta;

	/*
	===============================================================================

	KEY BUTTONS

	Continuous button event tracking is complicated by the fact that two different
	input sources (say, mouse button 1 and the control key) can both press the
	same button, but the button should only be released when both of the
	pressing key have been released.

	When a key event issues a button command (+forward, +attack, etc), it appends
	its key number as a parameter to the command so it can be matched up with
	the release.

	state bit 0 is the current state of the key
	state bit 1 is edge triggered on the up to down transition
	state bit 2 is edge triggered on the down to up transition


	Key_Event (int key, qboolean down, unsigned time);

	  +mlook src time

	===============================================================================
	*/

	static kbutton_t in_klook = new kbutton_t();
	static kbutton_t in_left = new kbutton_t();
	static kbutton_t in_right = new kbutton_t();
	static kbutton_t in_forward = new kbutton_t();
	static kbutton_t in_back = new kbutton_t();
	static kbutton_t in_lookup = new kbutton_t();
	static kbutton_t in_lookdown = new kbutton_t();
	static kbutton_t in_moveleft = new kbutton_t();
	static kbutton_t in_moveright = new kbutton_t();
	public static kbutton_t in_strafe = new kbutton_t();
	static kbutton_t in_speed = new kbutton_t();
	static kbutton_t in_use = new kbutton_t();
	static kbutton_t in_attack = new kbutton_t();
	static kbutton_t in_up = new kbutton_t();
	static kbutton_t in_down = new kbutton_t();

	static int in_impulse;

	static void KeyDown(kbutton_t b) {
		int k;
		String c;

		c = Cmd.Argv(1);
		if (c.length() > 0)
			k = Integer.parseInt(c);
		else
			k = -1;		// typed manually at the console for continuous down

		if (k == b.down[0] || k == b.down[1])
			return;		// repeating key

		if (b.down[0] == 0)
			b.down[0] = k;
		else if (b.down[1] == 0)
			b.down[1] = k;
		else {
			Com.Printf("Three keys down for a button!\n");
			return;
		}

		if ((b.state & 1) != 0)
			return;		// still down

		// save timestamp
		c = Cmd.Argv(2);
		b.downtime = Long.parseLong(c);
		if (b.downtime == 0)
			b.downtime = sys_frame_time - 100;

		b.state |= 3;	// down + impulse down
	}

	static void KeyUp(kbutton_t b) {
		int k;
		String c;
		long uptime;

		c = Cmd.Argv(1);
		if (c.length() > 0)
			k = Integer.parseInt(c);
		else {
			// typed manually at the console, assume for unsticking, so clear all
			b.down[0] = b.down[1] = 0;
			b.state = 4;	// impulse up
			return;
		}

		if (b.down[0] == k)
			b.down[0] = 0;
		else if (b.down[1] == k)
			b.down[1] = 0;
		else
			return;		// key up without coresponding down (menu pass through)
		if (b.down[0] != 0 || b.down[1] != 0)
			return;		// some other key is still holding it down

		if ((b.state & 1) == 0)
			return;		// still up (this should not happen)

		// save timestamp
		c = Cmd.Argv(2);
		uptime = Long.parseLong(c);
		if (uptime != 0)
			b.msec += uptime - b.downtime;
		else
			b.msec += 10;

		b.state &= ~1;		// now up
		b.state |= 4; 		// impulse up
	}

	static void IN_KLookDown() {KeyDown(in_klook);}
	static void IN_KLookUp() {KeyUp(in_klook);}
	static void IN_UpDown() {KeyDown(in_up);}
	static void IN_UpUp() {KeyUp(in_up);}
	static void IN_DownDown() {KeyDown(in_down);}
	static void IN_DownUp() {KeyUp(in_down);}
	static void IN_LeftDown() {KeyDown(in_left);}
	static void IN_LeftUp() {KeyUp(in_left);}
	static void IN_RightDown() {KeyDown(in_right);}
	static void IN_RightUp() {KeyUp(in_right);}
	static void IN_ForwardDown() {KeyDown(in_forward);}
	static void IN_ForwardUp() {KeyUp(in_forward);}
	static void IN_BackDown() {KeyDown(in_back);}
	static void IN_BackUp() {KeyUp(in_back);}
	static void IN_LookupDown() {KeyDown(in_lookup);}
	static void IN_LookupUp() {KeyUp(in_lookup);}
	static void IN_LookdownDown() {KeyDown(in_lookdown);}
	static void IN_LookdownUp() {KeyUp(in_lookdown);}
	static void IN_MoveleftDown() {KeyDown(in_moveleft);}
	static void IN_MoveleftUp() {KeyUp(in_moveleft);}
	static void IN_MoverightDown() {KeyDown(in_moveright);}
	static void IN_MoverightUp() {KeyUp(in_moveright);}
	
	static void IN_SpeedDown() {KeyDown(in_speed);}
	static void IN_SpeedUp() {KeyUp(in_speed);}
	static void IN_StrafeDown() {KeyDown(in_strafe);}
	static void IN_StrafeUp() {KeyUp(in_strafe);}
	
	static void IN_AttackDown() {KeyDown(in_attack);}
	static void IN_AttackUp() {KeyUp(in_attack);}
	
	static void IN_UseDown () {KeyDown(in_use);}
	static void IN_UseUp () {KeyUp(in_use);}
	
	static void IN_Impulse () {in_impulse=Integer.parseInt(Cmd.Argv(1));}

	/*
	===============
	CL_KeyState

	Returns the fraction of the frame that the key was down
	===============
	*/
	static float KeyState(kbutton_t key) {
		float val;
		long msec;

		key.state &= 1;		// clear impulses

		msec = key.msec;
		key.msec = 0;

		if (key.state != 0) {
			// still down
			msec += sys_frame_time - key.downtime;
			key.downtime = sys_frame_time;
		}

		val = (float)msec / frame_msec;
		if (val < 0)
			val = 0;
		if (val > 1)
			val = 1;

		return val;
	}

//	  ==========================================================================

	/*
	================
	CL_AdjustAngles

	Moves the local angle positions
	================
	*/
	static void AdjustAngles() {
		float speed;
		float up, down;

		if ((in_speed.state & 1) != 0)
			speed = cls.frametime * cl_anglespeedkey.value;
		else
			speed = cls.frametime;

		if ((in_strafe.state & 1) == 0) {
			cl.viewangles[YAW] -= speed * cl_yawspeed.value * CL.KeyState(in_right);
			cl.viewangles[YAW] += speed * cl_yawspeed.value * CL.KeyState(in_left);
		}
		if ((in_klook.state & 1) != 0) {
			cl.viewangles[PITCH] -= speed * cl_pitchspeed.value * CL.KeyState(in_forward);
			cl.viewangles[PITCH] += speed * cl_pitchspeed.value * CL.KeyState(in_back);
		}

		up = CL.KeyState(in_lookup);
		down = CL.KeyState(in_lookdown);

		cl.viewangles[PITCH] -= speed * cl_pitchspeed.value * up;
		cl.viewangles[PITCH] += speed * cl_pitchspeed.value * down;
	}

	/*
	================
	CL_BaseMove

	Send the intended movement message to the server
	================
	*/
	static void BaseMove(usercmd_t cmd) {	
		CL.AdjustAngles();

		//memset (cmd, 0, sizeof(*cmd));
		cmd.reset();

		VectorCopy(cl.viewangles, cmd.angles);
		if ((in_strafe.state & 1) != 0) {
			cmd.sidemove += cl_sidespeed.value * CL.KeyState(in_right);
			cmd.sidemove -= cl_sidespeed.value * CL.KeyState(in_left);
		}

		cmd.sidemove += cl_sidespeed.value * CL.KeyState(in_moveright);
		cmd.sidemove -= cl_sidespeed.value * CL.KeyState(in_moveleft);

		cmd.upmove += cl_upspeed.value * CL.KeyState(in_up);
		cmd.upmove -= cl_upspeed.value * CL.KeyState(in_down);

		if ((in_klook.state & 1) == 0) {
			cmd.forwardmove += cl_forwardspeed.value * CL.KeyState(in_forward);
			cmd.forwardmove -= cl_forwardspeed.value * CL.KeyState(in_back);
		}

		//
		//	   adjust for speed key / running
		//
		if (((in_speed.state & 1) ^ (int) (cl_run.value)) != 0) {
			cmd.forwardmove *= 2;
			cmd.sidemove *= 2;
			cmd.upmove *= 2;
		}
	}

	static void ClampPitch() {

		float	pitch;

		pitch = SHORT2ANGLE(cl.frame.playerstate.pmove.delta_angles[PITCH]);
		if (pitch > 180)
			pitch -= 360;

		if (cl.viewangles[PITCH] + pitch < -360)
			cl.viewangles[PITCH] += 360; // wrapped
		if (cl.viewangles[PITCH] + pitch > 360)
			cl.viewangles[PITCH] -= 360; // wrapped

		if (cl.viewangles[PITCH] + pitch > 89)
			cl.viewangles[PITCH] = 89 - pitch;
		if (cl.viewangles[PITCH] + pitch < -89)
			cl.viewangles[PITCH] = -89 - pitch;
	}

	/*
	==============
	CL_FinishMove
	==============
	*/
	static void FinishMove(usercmd_t cmd) {
		int ms;
		int i;

		//
		//	   figure button bits
		//	
		if ((in_attack.state & 3) != 0)
			cmd.buttons |= BUTTON_ATTACK;
		in_attack.state &= ~2;

		if ((in_use.state & 3) != 0)
			cmd.buttons |= BUTTON_USE;
		in_use.state &= ~2;

		if (anykeydown != 0 && cls.key_dest == key_game)
			cmd.buttons |= BUTTON_ANY;

		// send milliseconds of time to apply the move
		ms = (int)(cls.frametime * 1000);
		if (ms > 250)
			ms = 100; // time was unreasonable
		cmd.msec = (byte)ms;

		CL.ClampPitch();
		for (i = 0; i < 3; i++)
			cmd.angles[i] = (short)ANGLE2SHORT(cl.viewangles[i]);

		cmd.impulse = (byte)in_impulse;
		in_impulse = 0;

		// send the ambient light level at the player's current position
		cmd.lightlevel = (byte)cl_lightlevel.value;
	}

	/*
	=================
	CL_CreateCmd
	=================
	*/
	static usercmd_t CreateCmd() {
		usercmd_t cmd = new usercmd_t();

		frame_msec = sys_frame_time - old_sys_frame_time;
		if (frame_msec < 1)
			frame_msec = 1;
		if (frame_msec > 200)
			frame_msec = 200;

		// get basic movement from keyboard
		CL.BaseMove(cmd);

		// allow mice or other external controllers to add to the move
		IN.Move(cmd);

		CL.FinishMove(cmd);

		old_sys_frame_time = sys_frame_time;

		return cmd;
	}

	/*
	============
	CL_InitInput
	============
	*/
	static void InitInput() {
		Cmd.AddCommand("centerview", new xcommand_t() {
						public void execute() {IN.CenterView();}});

		Cmd.AddCommand("+moveup", new xcommand_t() {
			public void execute() {IN_UpDown();}});
		Cmd.AddCommand("-moveup", new xcommand_t() {
			public void execute() {IN_UpUp();}});
		Cmd.AddCommand("+movedown", new xcommand_t() {
			public void execute() {IN_DownDown();}});
		Cmd.AddCommand("-movedown", new xcommand_t() {
			public void execute() {IN_DownUp();}});
		Cmd.AddCommand("+left", new xcommand_t() {
			public void execute() {IN_LeftDown();}});
		Cmd.AddCommand("-left", new xcommand_t() {
			public void execute() {IN_LeftUp();}});
		Cmd.AddCommand("+right", new xcommand_t() {
			public void execute() {IN_RightDown();}});
		Cmd.AddCommand("-right", new xcommand_t() {
			public void execute() {IN_RightUp();}});
		Cmd.AddCommand("+forward", new xcommand_t() {
			public void execute() {IN_ForwardDown();}});
		Cmd.AddCommand("-forward", new xcommand_t() {
			public void execute() {IN_ForwardUp();}});
		Cmd.AddCommand("+back", new xcommand_t() {
			public void execute() {IN_BackDown();}});
		Cmd.AddCommand("-back", new xcommand_t() {
			public void execute() {IN_BackUp();}});
		Cmd.AddCommand("+lookup", new xcommand_t() {
			public void execute() {IN_LookupDown();}});
		Cmd.AddCommand("-lookup", new xcommand_t() {
			public void execute() {IN_LookupUp();}});
		Cmd.AddCommand("+lookdown", new xcommand_t() {
			public void execute() {IN_LookdownDown();}});
		Cmd.AddCommand("-lookdown", new xcommand_t() {
			public void execute() {IN_LookdownUp();}});
		Cmd.AddCommand("+strafe", new xcommand_t() {
			public void execute() {IN_StrafeDown();}});
		Cmd.AddCommand("-strafe", new xcommand_t() {
			public void execute() {IN_StrafeUp();}});
		Cmd.AddCommand("+moveleft", new xcommand_t() {
			public void execute() {IN_MoveleftDown();}});
		Cmd.AddCommand("-moveleft", new xcommand_t() {
			public void execute() {IN_MoveleftUp();}});
		Cmd.AddCommand("+moveright", new xcommand_t() {
			public void execute() {IN_MoverightDown();}});
		Cmd.AddCommand("-moveright", new xcommand_t() {
			public void execute() {IN_MoverightUp();}});
		Cmd.AddCommand("+speed", new xcommand_t() {
			public void execute() {IN_SpeedDown();}});
		Cmd.AddCommand("-speed", new xcommand_t() {
			public void execute() {IN_SpeedUp();}});
		Cmd.AddCommand("+attack", new xcommand_t() {
			public void execute() {IN_AttackDown();}});
		Cmd.AddCommand("-attack", new xcommand_t() {
			public void execute() {IN_AttackUp();}});
		Cmd.AddCommand("+use", new xcommand_t() {
			public void execute() {IN_UseDown();}});
		Cmd.AddCommand("-use", new xcommand_t() {
			public void execute() {IN_UseUp();}});
		Cmd.AddCommand("impulse", new xcommand_t() {
			public void execute() {IN_Impulse();}});
		Cmd.AddCommand("+klook", new xcommand_t() {
			public void execute() {IN_KLookDown();}});
		Cmd.AddCommand("-klook", new xcommand_t() {
			public void execute() {IN_KLookUp();}});

		// TODO: nodelta wegmachen
		cl_nodelta = Cvar.Get("cl_nodelta", "1", 0);
	}

	/*
	=================
	CL_SendCmd
	=================
	*/
	static void SendCmd() {
		sizebuf_t buf = new sizebuf_t();
		byte[] data = new byte[128];
		int i;
		usercmd_t cmd, oldcmd;
		usercmd_t nullcmd = new usercmd_t();
		int checksumIndex;

		// build a command even if not connected

		// save this command off for prediction
		i = cls.netchan.outgoing_sequence & (CMD_BACKUP - 1);
		cmd = cl.cmds[i];
		cl.cmd_time[i] = (int)cls.realtime; // for netgraph ping calculation

		cl.cmds[i] = cmd = CL.CreateCmd();

		cl.cmd = new usercmd_t(cmd);

		if (cls.state == ca_disconnected || cls.state == ca_connecting)
			return;
		
		if (cls.state == ca_connected) {
			if (cls.netchan.message.cursize != 0 || curtime - cls.netchan.last_sent > 1000)
				Netchan.Transmit(cls.netchan, 0, new byte[0]);
			return;
		}

		// send a userinfo update if needed
		if (userinfo_modified) {
			CL.FixUpGender();
			userinfo_modified = false;
			MSG.WriteByte(cls.netchan.message, clc_userinfo);
			MSG.WriteString(cls.netchan.message, Cvar.Userinfo());
		}

		SZ.Init(buf, data, data.length);

		if (cmd.buttons != 0
			&& cl.cinematictime > 0
			&& !cl.attractloop
			&& cls.realtime - cl.cinematictime > 1000) { // skip the rest of the cinematic
			SCR.FinishCinematic();
		}

		// begin a client move command
		MSG.WriteByte(buf, clc_move);

		// save the position for a checksum byte
		checksumIndex = buf.cursize;
		MSG.WriteByte(buf, 0);

		// let the server know what the last frame we
		// got was, so the next message can be delta compressed
		if (cl_nodelta.value != 0.0f || !cl.frame.valid || cls.demowaiting)
			MSG.WriteLong(buf, -1); // no compression
		else
			MSG.WriteLong(buf, cl.frame.serverframe);

		// send this and the previous cmds in the message, so
		// if the last packet was dropped, it can be recovered
		i = (cls.netchan.outgoing_sequence - 2) & (CMD_BACKUP - 1);
		cmd = cl.cmds[i];
		//memset (nullcmd, 0, sizeof(nullcmd));
		nullcmd.reset();
		MSG.WriteDeltaUsercmd(buf, nullcmd, cmd);
		oldcmd = cmd;

		i = (cls.netchan.outgoing_sequence - 1) & (CMD_BACKUP - 1);
		cmd = cl.cmds[i];
		MSG.WriteDeltaUsercmd(buf, oldcmd, cmd);
		oldcmd = cmd;

		i = (cls.netchan.outgoing_sequence) & (CMD_BACKUP - 1);
		cmd = cl.cmds[i];
		MSG.WriteDeltaUsercmd(buf, oldcmd, cmd);

		// calculate a checksum over the move commands
		buf.data[checksumIndex] = 0;
		/*COM_BlockSequenceCRCByte(
					buf.data + checksumIndex + 1, buf.cursize - checksumIndex - 1,
					cls.netchan.outgoing_sequence);*/

		//
		// deliver the message
		//
		Netchan.Transmit(cls.netchan, buf.cursize, buf.data);
	}

}
