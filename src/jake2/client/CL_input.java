/*
 * java
 * Copyright (C) 2004
 * 
 * $Id: CL_input.java,v 1.5 2005-01-21 01:10:09 cawe Exp $
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

import jake2.Defines;
import jake2.Globals;
import jake2.game.Cmd;
import jake2.game.cvar_t;
import jake2.game.usercmd_t;
import jake2.qcommon.*;
import jake2.sys.IN;
import jake2.util.Math3D;

/**
 * CL_input
 */
public class CL_input {

	static long frame_msec;

	static long old_sys_frame_time;

	static cvar_t cl_nodelta;

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
		c = Cmd.Argv(2);
		b.downtime = Long.parseLong(c);
		if (b.downtime == 0)
			b.downtime = Globals.sys_frame_time - 100;

		b.state |= 3; // down + impulse down
	}

	static void KeyUp(kbutton_t b) {
		int k;
		String c;
		long uptime;

		c = Cmd.Argv(1);
		if (c.length() > 0)
			k = Integer.parseInt(c);
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
		c = Cmd.Argv(2);
		uptime = Long.parseLong(c);
		if (uptime != 0)
			b.msec += uptime - b.downtime;
		else
			b.msec += 10;

		b.state &= ~1; // now up
		b.state |= 4; // impulse up
	}

	static void IN_KLookDown() {
		KeyDown(in_klook);
	}

	static void IN_KLookUp() {
		KeyUp(in_klook);
	}

	static void IN_UpDown() {
		KeyDown(in_up);
	}

	static void IN_UpUp() {
		KeyUp(in_up);
	}

	static void IN_DownDown() {
		KeyDown(in_down);
	}

	static void IN_DownUp() {
		KeyUp(in_down);
	}

	static void IN_LeftDown() {
		KeyDown(in_left);
	}

	static void IN_LeftUp() {
		KeyUp(in_left);
	}

	static void IN_RightDown() {
		KeyDown(in_right);
	}

	static void IN_RightUp() {
		KeyUp(in_right);
	}

	static void IN_ForwardDown() {
		KeyDown(in_forward);
	}

	static void IN_ForwardUp() {
		KeyUp(in_forward);
	}

	static void IN_BackDown() {
		KeyDown(in_back);
	}

	static void IN_BackUp() {
		KeyUp(in_back);
	}

	static void IN_LookupDown() {
		KeyDown(in_lookup);
	}

	static void IN_LookupUp() {
		KeyUp(in_lookup);
	}

	static void IN_LookdownDown() {
		KeyDown(in_lookdown);
	}

	static void IN_LookdownUp() {
		KeyUp(in_lookdown);
	}

	static void IN_MoveleftDown() {
		KeyDown(in_moveleft);
	}

	static void IN_MoveleftUp() {
		KeyUp(in_moveleft);
	}

	static void IN_MoverightDown() {
		KeyDown(in_moveright);
	}

	static void IN_MoverightUp() {
		KeyUp(in_moveright);
	}

	static void IN_SpeedDown() {
		KeyDown(in_speed);
	}

	static void IN_SpeedUp() {
		KeyUp(in_speed);
	}

	static void IN_StrafeDown() {
		KeyDown(in_strafe);
	}

	static void IN_StrafeUp() {
		KeyUp(in_strafe);
	}

	static void IN_AttackDown() {
		KeyDown(in_attack);
	}

	static void IN_AttackUp() {
		KeyUp(in_attack);
	}

	static void IN_UseDown() {
		KeyDown(in_use);
	}

	static void IN_UseUp() {
		KeyUp(in_use);
	}

	static void IN_Impulse() {
		in_impulse = Integer.parseInt(Cmd.Argv(1));
	}

	/*
	 * =============== CL_KeyState
	 * 
	 * Returns the fraction of the frame that the key was down ===============
	 */
	static float KeyState(kbutton_t key) {
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
	static void AdjustAngles() {
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
	static void BaseMove(usercmd_t cmd) {
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

	static void ClampPitch() {

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
	static void FinishMove(usercmd_t cmd) {
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
	static void CreateCmd(usercmd_t cmd) {
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
		Cmd.AddCommand("centerview", new xcommand_t() {
			public void execute() {
				IN.CenterView();
			}
		});

		Cmd.AddCommand("+moveup", new xcommand_t() {
			public void execute() {
				IN_UpDown();
			}
		});
		Cmd.AddCommand("-moveup", new xcommand_t() {
			public void execute() {
				IN_UpUp();
			}
		});
		Cmd.AddCommand("+movedown", new xcommand_t() {
			public void execute() {
				IN_DownDown();
			}
		});
		Cmd.AddCommand("-movedown", new xcommand_t() {
			public void execute() {
				IN_DownUp();
			}
		});
		Cmd.AddCommand("+left", new xcommand_t() {
			public void execute() {
				IN_LeftDown();
			}
		});
		Cmd.AddCommand("-left", new xcommand_t() {
			public void execute() {
				IN_LeftUp();
			}
		});
		Cmd.AddCommand("+right", new xcommand_t() {
			public void execute() {
				IN_RightDown();
			}
		});
		Cmd.AddCommand("-right", new xcommand_t() {
			public void execute() {
				IN_RightUp();
			}
		});
		Cmd.AddCommand("+forward", new xcommand_t() {
			public void execute() {
				IN_ForwardDown();
			}
		});
		Cmd.AddCommand("-forward", new xcommand_t() {
			public void execute() {
				IN_ForwardUp();
			}
		});
		Cmd.AddCommand("+back", new xcommand_t() {
			public void execute() {
				IN_BackDown();
			}
		});
		Cmd.AddCommand("-back", new xcommand_t() {
			public void execute() {
				IN_BackUp();
			}
		});
		Cmd.AddCommand("+lookup", new xcommand_t() {
			public void execute() {
				IN_LookupDown();
			}
		});
		Cmd.AddCommand("-lookup", new xcommand_t() {
			public void execute() {
				IN_LookupUp();
			}
		});
		Cmd.AddCommand("+lookdown", new xcommand_t() {
			public void execute() {
				IN_LookdownDown();
			}
		});
		Cmd.AddCommand("-lookdown", new xcommand_t() {
			public void execute() {
				IN_LookdownUp();
			}
		});
		Cmd.AddCommand("+strafe", new xcommand_t() {
			public void execute() {
				IN_StrafeDown();
			}
		});
		Cmd.AddCommand("-strafe", new xcommand_t() {
			public void execute() {
				IN_StrafeUp();
			}
		});
		Cmd.AddCommand("+moveleft", new xcommand_t() {
			public void execute() {
				IN_MoveleftDown();
			}
		});
		Cmd.AddCommand("-moveleft", new xcommand_t() {
			public void execute() {
				IN_MoveleftUp();
			}
		});
		Cmd.AddCommand("+moveright", new xcommand_t() {
			public void execute() {
				IN_MoverightDown();
			}
		});
		Cmd.AddCommand("-moveright", new xcommand_t() {
			public void execute() {
				IN_MoverightUp();
			}
		});
		Cmd.AddCommand("+speed", new xcommand_t() {
			public void execute() {
				IN_SpeedDown();
			}
		});
		Cmd.AddCommand("-speed", new xcommand_t() {
			public void execute() {
				IN_SpeedUp();
			}
		});
		Cmd.AddCommand("+attack", new xcommand_t() {
			public void execute() {
				IN_AttackDown();
			}
		});
		Cmd.AddCommand("-attack", new xcommand_t() {
			public void execute() {
				IN_AttackUp();
			}
		});
		Cmd.AddCommand("+use", new xcommand_t() {
			public void execute() {
				IN_UseDown();
			}
		});
		Cmd.AddCommand("-use", new xcommand_t() {
			public void execute() {
				IN_UseUp();
			}
		});
		Cmd.AddCommand("impulse", new xcommand_t() {
			public void execute() {
				IN_Impulse();
			}
		});
		Cmd.AddCommand("+klook", new xcommand_t() {
			public void execute() {
				IN_KLookDown();
			}
		});
		Cmd.AddCommand("-klook", new xcommand_t() {
			public void execute() {
				IN_KLookUp();
			}
		});

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