/*
 * Key.java
 * Copyright (C) 2003
 * 
 * $Id: Key.java,v 1.13 2011-07-07 21:10:18 salomo Exp $
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

import jake2.qcommon.*;
import jake2.qcommon.sys.Sys;
import jake2.qcommon.util.Lib;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Collections;
import java.util.List;

/**
 * Key
 */
public class Key extends Globals {
	//
	// these are the key numbers that should be passed to Key_Event
	//
	public static final int K_TAB = 9;
	public static final int K_ENTER = 13;
	public static final int K_ESCAPE = 27;
	static final int K_SPACE = 32;
	static final int K_CTRLV = 22;


	// normal keys should be passed as lowercased ascii

	public static final int K_BACKSPACE = 127;
	public static final int K_UPARROW = 128;
	public static final int K_DOWNARROW = 129;
	public static final int K_LEFTARROW = 130;
	public static final int K_RIGHTARROW = 131;

	public static final int K_ALT = 132;
	public static final int K_CTRL = 133;
	public static final int K_SHIFT = 134;
	public static final int K_F1 = 135;
	public static final int K_F2 = 136;
	public static final int K_F3 = 137;
	public static final int K_F4 = 138;
	public static final int K_F5 = 139;
	public static final int K_F6 = 140;
	public static final int K_F7 = 141;
	public static final int K_F8 = 142;
	public static final int K_F9 = 143;
	public static final int K_F10 = 144;
	public static final int K_F11 = 145;
	public static final int K_F12 = 146;
	public static final int K_INS = 147;
	public static final int K_DEL = 148;
	public static final int K_PGDN = 149;
	public static final int K_PGUP = 150;
	public static final int K_HOME = 151;
	public static final int K_END = 152;

	static final int K_KP_HOME = 160;
	static final int K_KP_UPARROW = 161;
	static final int K_KP_PGUP = 162;
	static final int K_KP_LEFTARROW = 163;
	static final int K_KP_5 = 164;
	static final int K_KP_RIGHTARROW = 165;
	static final int K_KP_END = 166;
	static final int K_KP_DOWNARROW = 167;
	static final int K_KP_PGDN = 168;
	static final int K_KP_ENTER = 169;
	static final int K_KP_INS = 170;
	static final int K_KP_DEL = 171;
	static final int K_KP_SLASH = 172;
	static final int K_KP_MINUS = 173;
	static final int K_KP_PLUS = 174;

	public static final int K_PAUSE = 255;

	//
	// mouse buttons generate virtual keys
	//
	public static final int K_MOUSE1 = 200;
	static final int K_MOUSE2 = 201;
	static final int K_MOUSE3 = 202;

	//
	// joystick buttons
	//
	static final int K_JOY1 = 203;
	static final int K_JOY2 = 204;
	static final int K_JOY3 = 205;
	static final int K_JOY4 = 206;

	public static final int K_MWHEELDOWN = 239;
	public static final int K_MWHEELUP = 240;

	static int anykeydown = 0;
	private static int key_waiting;
	private static int history_line = 0;
	private static boolean shift_down = false;
	private static int[] key_repeats = new int[256];
	//static int[] keyshift = new int[256];
	private static boolean[] menubound = new boolean[256];
	private static boolean[] consolekeys = new boolean[256];

	private static String[] keynames = new String[256];

	static {
		keynames[K_TAB] = "TAB";
		keynames[K_ENTER] = "ENTER";
		keynames[K_ESCAPE] = "ESCAPE";
		keynames[K_SPACE] = "SPACE";
		keynames[K_BACKSPACE] = "BACKSPACE";
		keynames[K_UPARROW] = "UPARROW";
		keynames[K_DOWNARROW] = "DOWNARROW";
		keynames[K_LEFTARROW] = "LEFTARROW";
		keynames[K_RIGHTARROW] = "RIGHTARROW";
		keynames[K_ALT] = "ALT";
		keynames[K_CTRL] = "CTRL";
		keynames[K_SHIFT] = "SHIFT";

		keynames[K_F1] = "F1";
		keynames[K_F2] = "F2";
		keynames[K_F3] = "F3";
		keynames[K_F4] = "F4";
		keynames[K_F5] = "F5";
		keynames[K_F6] = "F6";
		keynames[K_F7] = "F7";
		keynames[K_F8] = "F8";
		keynames[K_F9] = "F9";
		keynames[K_F10] = "F10";
		keynames[K_F11] = "F11";
		keynames[K_F12] = "F12";

		keynames[K_INS] = "INS";
		keynames[K_DEL] = "DEL";
		keynames[K_PGDN] = "PGDN";
		keynames[K_PGUP] = "PGUP";
		keynames[K_HOME] = "HOME";
		keynames[K_END] = "END";

		keynames[K_MOUSE1] = "MOUSE1";
		keynames[K_MOUSE2] = "MOUSE2";
		keynames[K_MOUSE3] = "MOUSE3";

		//	00092         {"JOY1", K_JOY1},
		//	00093         {"JOY2", K_JOY2},
		//	00094         {"JOY3", K_JOY3},
		//	00095         {"JOY4", K_JOY4},

		keynames[K_KP_HOME] = "KP_HOME";
		keynames[K_KP_UPARROW] = "KP_UPARROW";
		keynames[K_KP_PGUP] = "KP_PGUP";
		keynames[K_KP_LEFTARROW] = "KP_LEFTARROW";
		keynames[K_KP_5] = "KP_5";
		keynames[K_KP_RIGHTARROW] = "KP_RIGHTARROW";
		keynames[K_KP_END] = "KP_END";
		keynames[K_KP_DOWNARROW] = "KP_DOWNARROW";
		keynames[K_KP_PGDN] = "KP_PGDN";
		keynames[K_KP_ENTER] = "KP_ENTER";
		keynames[K_KP_INS] = "KP_INS";
		keynames[K_KP_DEL] = "KP_DEL";
		keynames[K_KP_SLASH] = "KP_SLASH";

		keynames[K_KP_PLUS] = "KP_PLUS";
		keynames[K_KP_MINUS] = "KP_MINUS";

		keynames[K_MWHEELUP] = "MWHEELUP";
		keynames[K_MWHEELDOWN] = "MWHEELDOWN";

		keynames[K_PAUSE] = "PAUSE";
		keynames[';'] = "SEMICOLON"; // because a raw semicolon seperates commands

		keynames[0] = "NULL";
	}

	/**
	 * 
	 */
	public static void Init() {
		for (int i = 0; i < 32; i++) {
			ClientGlobals.key_lines[i][0] = ']';
			ClientGlobals.key_lines[i][1] = 0;
		}
		ClientGlobals.key_linepos = 1;

		//
		// init ascii characters in console mode
		//
		for (int i = 32; i < 128; i++)
			consolekeys[i] = true;
		consolekeys[K_ENTER] = true;
		consolekeys[K_CTRLV] = true;

		consolekeys[K_KP_ENTER] = true;
		consolekeys[K_TAB] = true;
		consolekeys[K_LEFTARROW] = true;
		consolekeys[K_KP_LEFTARROW] = true;
		consolekeys[K_RIGHTARROW] = true;
		consolekeys[K_KP_RIGHTARROW] = true;
		consolekeys[K_UPARROW] = true;
		consolekeys[K_KP_UPARROW] = true;
		consolekeys[K_DOWNARROW] = true;
		consolekeys[K_KP_DOWNARROW] = true;
		consolekeys[K_BACKSPACE] = true;
		consolekeys[K_HOME] = true;
		consolekeys[K_KP_HOME] = true;
		consolekeys[K_END] = true;
		consolekeys[K_KP_END] = true;
		consolekeys[K_PGUP] = true;
		consolekeys[K_KP_PGUP] = true;
		consolekeys[K_PGDN] = true;
		consolekeys[K_KP_PGDN] = true;
		consolekeys[K_SHIFT] = true;
		consolekeys[K_INS] = true;
		consolekeys[K_DEL] = true; // sfranzyshen
		consolekeys[K_KP_INS] = true;
		consolekeys[K_KP_DEL] = true;
		consolekeys[K_KP_SLASH] = true;
		consolekeys[K_KP_PLUS] = true;
		consolekeys[K_KP_MINUS] = true;
		consolekeys[K_KP_5] = true;
		consolekeys[K_MWHEELUP] = true; // sfranzyshen
		consolekeys[K_MWHEELDOWN] = true; // sfranzyshen
		consolekeys[K_CTRL] = true; // sfranzyshen

		consolekeys['`'] = false;
		consolekeys['~'] = false;

//		for (int i = 0; i < 256; i++)
//			keyshift[i] = i;
//		for (int i = 'a'; i <= 'z'; i++)
//			keyshift[i] = i - 'a' + 'A';
//		keyshift['1'] = '!';
//		keyshift['2'] = '@';
//		keyshift['3'] = '#';
//		keyshift['4'] = '$';
//		keyshift['5'] = '%';
//		keyshift['6'] = '^';
//		keyshift['7'] = '&';
//		keyshift['8'] = '*';
//		keyshift['9'] = '(';
//		keyshift['0'] = ')';
//		keyshift['-'] = '_';
//		keyshift['='] = '+';
//		keyshift[','] = '<';
//		keyshift['.'] = '>';
//		keyshift['/'] = '?';
//		keyshift[';'] = ':';
//		keyshift['\''] = '"';
//		keyshift['['] = '{';
//		keyshift[']'] = '}';
//		keyshift['`'] = '~';
//		keyshift['\\'] = '|';

		menubound[K_ESCAPE] = true;
		for (int i = 0; i < 12; i++)
			menubound[K_F1 + i] = true;

		//
		// register our functions
		//
		Cmd.AddCommand("bind", Key::Key_Bind_f);
		Cmd.AddCommand("unbind", Key::Key_Unbind_f);
		Cmd.AddCommand("unbindall", (List<String> args) -> Key_Unbindall_f());
		Cmd.AddCommand("bindlist", (List<String> args) -> Key_Bindlist_f());
	}

	static void ClearTyping() {
		ClientGlobals.key_lines[ClientGlobals.edit_line][1] = 0; // clear any typing
		ClientGlobals.key_linepos = 1;
		ClientGlobals.con.backedit = 0; // sfranzyshen
	}

	/**
	 * Called by the system between frames for both key up and key down events.
	 */
	public static void Event(int key, boolean down, int time) {		
		String kb;
		String cmd;

		// hack for modal presses
		if (key_waiting == -1) {
			if (down)
				key_waiting = key;
			return;
		}

		// update auto-repeat status
		if (down) {
			key_repeats[key]++;
			if (key_repeats[key] > 1
				&& ClientGlobals.cls.key_dest == Defines.key_game
				&& !(ClientGlobals.cls.state == Defines.ca_disconnected))
				return; // ignore most autorepeats

			if (key >= 200 && ClientGlobals.keybindings[key] == null)
				Com.Printf(Key.KeynumToString(key) + " is unbound, hit F4 to set.\n");
		}
		else {
			key_repeats[key] = 0;
		}

		if (key == K_SHIFT)
			shift_down = down;

		// console key is hardcoded, so the user can never unbind it
		if (key == '`' || key == '~') {
			if (!down)
				return;

			Console.ToggleConsole_f.execute(Collections.emptyList());
			return;
		}

		// any key during the attract mode will bring up the menu
		if (ClientGlobals.cl.attractloop && ClientGlobals.cls.key_dest != Defines.key_menu && !(key >= K_F1 && key <= K_F12))
			key = K_ESCAPE;

		// menu key is hardcoded, so the user can never unbind it
		if (key == K_ESCAPE) {
			if (!down)
				return;

			if (ClientGlobals.cl.frame.playerstate.stats[Defines.STAT_LAYOUTS] != 0 && ClientGlobals.cls.key_dest == Defines.key_game) {
				// put away help computer / inventory
				Cbuf.AddText("cmd putaway\n");
				return;
			}
			switch (ClientGlobals.cls.key_dest) {
				case Defines.key_message :
					Key.Message(key);
					break;
				case Defines.key_menu :
					Menu.Keydown(key);
					break;
				case Defines.key_game :
				case Defines.key_console :
					Menu.Menu_Main_f();
					break;
				default :
					Com.Error(Defines.ERR_FATAL, "Bad cls.key_dest");
			}
			return;
		}

		// track if any key is down for BUTTON_ANY
		ClientGlobals.keydown[key] = down;
		if (down) {
			if (key_repeats[key] == 1)
				Key.anykeydown++;
		}
		else {
			Key.anykeydown--;
			if (Key.anykeydown < 0)
				Key.anykeydown = 0;
		}

		//
		// key up events only generate commands if the game key binding is
		// a button command (leading + sign).  These will occur even in console mode,
		// to keep the character from continuing an action started before a console
		// switch.  Button commands include the kenum as a parameter, so multiple
		// downs can be matched with ups
		//
		if (!down) {
			kb = ClientGlobals.keybindings[key];
			if (kb != null && kb.length()>0 && kb.charAt(0) == '+') {
				cmd = "-" + kb.substring(1) + " " + key + " " + time + "\n";
				Cbuf.AddText(cmd);
			}
//			if (keyshift[key] != key) {
//				kb = Globals.keybindings[keyshift[key]];
//				if (kb != null && kb.length()>0 && kb.charAt(0) == '+') {
//					cmd = "-" + kb.substring(1) + " " + key + " " + time + "\n";
//					Cbuf.AddText(cmd);
//				}
//			}
			return;
		}

		//
		// if not a consolekey, send to the interpreter no matter what mode is
		//
		if ((ClientGlobals.cls.key_dest == Defines.key_menu && menubound[key])
			|| (ClientGlobals.cls.key_dest == Defines.key_console && !consolekeys[key])
			|| (ClientGlobals.cls.key_dest == Defines.key_game && (ClientGlobals.cls.state == Defines.ca_active || !consolekeys[key]))) {
			kb = ClientGlobals.keybindings[key];
			if (kb != null) {
				if (kb.length()>0 && kb.charAt(0) == '+') {
					// button commands add keynum and time as a parm
					cmd = kb + " " + key + " " + time + "\n";
					Cbuf.AddText(cmd);
				}
				else {
					Cbuf.AddText(kb + "\n");
				}
			}
			return;
		}

		if (!down)
			return; // other systems only care about key down events

//		if (shift_down)
//			key = keyshift[key];

		switch (ClientGlobals.cls.key_dest) {
			case Defines.key_message :
				Key.Message(key);
				break;
			case Defines.key_menu :
				Menu.Keydown(key);
				break;

			case Defines.key_game :
			case Defines.key_console :
				Key.Console(key);
				break;
			default :
				Com.Error(Defines.ERR_FATAL, "Bad cls.key_dest");
		}
	}

	/**
	 * Returns a string (either a single ascii char, or a K_* name) for the 
	 * given keynum.
	 */
	static String KeynumToString(int keynum) {
		if (keynum < 0 || keynum > 255)
			return "<KEY NOT FOUND>";
		if (keynum > 32 && keynum < 127)
			return Character.toString((char) keynum);

		if (keynames[keynum] != null)
			return keynames[keynum];

		return "<UNKNOWN KEYNUM>";
	}

	/**
	 * Returns a key number to be used to index keybindings[] by looking at
	 * the given string. Single ascii characters return themselves, while
	 * the K_* names are matched up.
	 */
	private static int StringToKeynum(String str) {

		if (str == null)
			return -1;

		if (str.length() == 1)
			return str.charAt(0);

		for (int i = 0; i < keynames.length; i++) {
			if (str.equalsIgnoreCase(keynames[i]))
				return i;
		}

		return -1;
	}

	private static void Message(int key) {

		// sfranzyshen -- start         
		StringBuffer buffer = new StringBuffer();
		buffer.append(ClientGlobals.chat_buffer);
		int offset = buffer.length() - ClientGlobals.chat_backedit;
			
		if (key == K_ENTER || key == K_KP_ENTER) {
			if (ClientGlobals.chat_team)
				Cbuf.AddText("say_team \"");
			else
				Cbuf.AddText("say \"");
			
			Cbuf.AddText(ClientGlobals.chat_buffer);
			Cbuf.AddText("\"\n");
			
			ClientGlobals.cls.key_dest = Defines.key_game;
			ClientGlobals.chat_buffer = "";
			ClientGlobals.chat_backedit = 0;
			return;
		}
		 
		
		if (key == K_ESCAPE) {
			ClientGlobals.cls.key_dest = Defines.key_game;
			ClientGlobals.chat_buffer = "";
			ClientGlobals.chat_backedit = 0;
			return;
		}
		if (key == K_END || key == K_KP_END) {
			ClientGlobals.chat_backedit = 0;
			return;
		}
		if (key == K_HOME || key == K_KP_HOME) {
			ClientGlobals.chat_backedit = ClientGlobals.chat_buffer.length();
			return;
		}
		if (key == K_BACKSPACE) {
			if (buffer.length() > 0) { // we have a buffer to edit
				if (ClientGlobals.chat_backedit > 0) { // we are somewhere mid line
					if (offset == 0) { // we are at the start of the line
						return;
					}
					buffer.deleteCharAt(offset-1);
				}
				else { // we are at the end of line
					buffer.deleteCharAt(buffer.length()-1);
				}
			}
			ClientGlobals.chat_buffer = buffer.toString();
			return;
		}
			
		if (key == K_DEL || key == K_KP_DEL) {
			if (buffer.length() > 0 && ClientGlobals.chat_backedit > 0) { // we have a buffer & we are somewhere mid line
				buffer.deleteCharAt(offset);
				ClientGlobals.chat_backedit--;
				if (ClientGlobals.chat_backedit < 0) {
					ClientGlobals.chat_backedit = 0;
				}
			}
			ClientGlobals.chat_buffer = buffer.toString();
			return;
		}
			
		if (key == K_LEFTARROW) {
			if (buffer.length() > 0 && offset > 0) { //we have a buffer & we are not at the start of line
				ClientGlobals.chat_backedit++;
				if (ClientGlobals.chat_backedit > buffer.length()) {
					ClientGlobals.chat_backedit = buffer.length();
				}
			}
			ClientGlobals.chat_buffer = buffer.toString();
			return;
		}
			
		if (key == K_RIGHTARROW) {
			if (buffer.length() > 0 && ClientGlobals.chat_backedit > 0) { // we have a buffer & we are not at the end of line
				ClientGlobals.chat_backedit--;
				if (ClientGlobals.chat_backedit < 0) {
					ClientGlobals.chat_backedit = 0;
				}
			}
			ClientGlobals.chat_buffer = buffer.toString();
			return;
		}
			
		if (key < 32 || key > 127)
			return; // non printable
			
		if (ClientGlobals.chat_backedit > 0) {
			buffer.insert(offset, (char) key);
			//Globals.chat_backedit++;
		}
		else {
			buffer.append((char) key);
		}
		ClientGlobals.chat_buffer = buffer.toString();
		// sfranzyshen -- stop    
	}	
	
	/**
	 * Interactive line editing and console scrollback.
	 */
	private static void Console(int key) {
		int i;
		
		switch (key) {
			case K_KP_SLASH :
				key = '/';
				break;
			case K_KP_MINUS :
				key = '-';
				break;
			case K_KP_PLUS :
				key = '+';
				break;
			case K_KP_HOME :
				key = '7';
				break;
			case K_KP_UPARROW :
				key = '8';
				break;
			case K_KP_PGUP :
				key = '9';
				break;
			case K_KP_LEFTARROW :
				key = '4';
				break;
			case K_KP_5 :
				key = '5';
				break;
			case K_KP_RIGHTARROW :
				key = '6';
				break;
			case K_KP_END :
				key = '1';
				break;
			case K_KP_DOWNARROW :
				key = '2';
				break;
			case K_KP_PGDN :
				key = '3';
				break;
			case K_KP_INS :
				key = '0';
				break;
			case K_KP_DEL :
				key = '.';
				break;
		}
		// sfranzyshen -start
		if ((key == K_CTRLV && ClientGlobals.keydown[K_CTRL] ) || ((( key == K_INS ) || ( key == K_KP_INS )) && ClientGlobals.keydown[K_SHIFT] )) {
			String cbd;
			
			if (( cbd = Sys.GetClipboardData()) != null) {
				int x;
				
				x = cbd.length();
				
				if ( x + ClientGlobals.key_linepos >= MAXCMDLINE )
					x = MAXCMDLINE - ClientGlobals.key_linepos;
				
				if ( x > 0 )
				{
					if (ClientGlobals.con.backedit > 0) {
						for (i = ClientGlobals.key_linepos - ClientGlobals.con.backedit; i < ClientGlobals.key_linepos; i++)
							ClientGlobals.key_lines[ClientGlobals.edit_line][i + x] = ClientGlobals.key_lines[ClientGlobals.edit_line][i];
						
						for (i = ClientGlobals.key_linepos - ClientGlobals.con.backedit; i < ClientGlobals.key_linepos - ClientGlobals.con.backedit + x; i++)
							ClientGlobals.key_lines[ClientGlobals.edit_line][i] = (byte) cbd.charAt(i - (ClientGlobals.key_linepos - ClientGlobals.con.backedit));
						ClientGlobals.con.backedit += x;
					} else {
						for (i = ClientGlobals.key_linepos; i < ClientGlobals.key_linepos + x; i++)
							ClientGlobals.key_lines[ClientGlobals.edit_line][i] = (byte) cbd.charAt(i - ClientGlobals.key_linepos);
					}
					ClientGlobals.key_linepos += x;
				}
			}
			return;
		}
		// sfranzyshen -stop 
				

		if (key == 'l') {
			if (ClientGlobals.keydown[K_CTRL]) {
				Cbuf.AddText("clear\n");
				ClientGlobals.con.backedit = 0; // sfranzyshen
				return;
			}
		}

		if (key == K_ENTER || key == K_KP_ENTER) {
			// backslash text are commands, else chat
			if (ClientGlobals.key_lines[ClientGlobals.edit_line][1] == '\\' || ClientGlobals.key_lines[ClientGlobals.edit_line][1] == '/')
				Cbuf.AddText(
					new String(ClientGlobals.key_lines[ClientGlobals.edit_line], 2, Lib.strlen(ClientGlobals.key_lines[ClientGlobals.edit_line]) - 2));
			else
				Cbuf.AddText(
					new String(ClientGlobals.key_lines[ClientGlobals.edit_line], 1, Lib.strlen(ClientGlobals.key_lines[ClientGlobals.edit_line]) - 1));

			
			Cbuf.AddText("\n");
		
			Com.Printf(new String(ClientGlobals.key_lines[ClientGlobals.edit_line], 0, Lib.strlen(ClientGlobals.key_lines[ClientGlobals.edit_line])) + "\n");
			ClientGlobals.edit_line = (ClientGlobals.edit_line + 1) & 31;
			history_line = ClientGlobals.edit_line;
		
			ClientGlobals.key_lines[ClientGlobals.edit_line][0] = ']';
			ClientGlobals.key_linepos = 1;
			ClientGlobals.con.backedit = 0; // sfranzyshen

			if (ClientGlobals.cls.state == Defines.ca_disconnected)
				SCR.UpdateScreen(); // force an update, because the command may take some time
			return;
		}

		if (key == K_TAB) {
			// command completion
			CompleteCommand();
			ClientGlobals.con.backedit = 0; // sfranzyshen
			return;
		}

		// sfranzyshen - start
		if (key == K_BACKSPACE)
		{
			if (ClientGlobals.key_linepos > 1)
			{
				if (ClientGlobals.con.backedit > 0 && ClientGlobals.con.backedit < ClientGlobals.key_linepos)
				{
					if (ClientGlobals.key_linepos - ClientGlobals.con.backedit <= 1)
						return;
					
					for (i = ClientGlobals.key_linepos - ClientGlobals.con.backedit - 1; i < ClientGlobals.key_linepos; i++)
						ClientGlobals.key_lines[ClientGlobals.edit_line][i] = ClientGlobals.key_lines[ClientGlobals.edit_line][i+1];
					
					if (ClientGlobals.key_linepos > 1)
						ClientGlobals.key_linepos--;
				}
				else
				{
					ClientGlobals.key_linepos--;
				}
			}
			return;
		}
		
		if (key == K_DEL || key == K_KP_DEL)
		{
			if (ClientGlobals.key_linepos > 1 && ClientGlobals.con.backedit > 0)
			{
				for (i = ClientGlobals.key_linepos - ClientGlobals.con.backedit; i < ClientGlobals.key_linepos; i++)
					ClientGlobals.key_lines[ClientGlobals.edit_line][i] = ClientGlobals.key_lines[ClientGlobals.edit_line][i+1];
				
				ClientGlobals.con.backedit--;
				ClientGlobals.key_linepos--;
			}
			return;
		}
		
		if (key == K_LEFTARROW || key == K_KP_LEFTARROW)
		{
			if (ClientGlobals.key_linepos>1)
			{
				ClientGlobals.con.backedit++;
				if (ClientGlobals.con.backedit > ClientGlobals.key_linepos -1) ClientGlobals.con.backedit = ClientGlobals.key_linepos-1;
			}
			return;
		}
		
		if (key == K_RIGHTARROW || key == K_KP_RIGHTARROW)
		{
			if (ClientGlobals.key_linepos > 1)
			{
				ClientGlobals.con.backedit--;
				if (ClientGlobals.con.backedit<0) ClientGlobals.con.backedit = 0;
			}
			return;
		}
		// sfranzyshen - stop
		


		if ((key == K_UPARROW) || (key == K_KP_UPARROW) || ((key == 'p') && ClientGlobals.keydown[K_CTRL])) {
			do {
				history_line = (history_line - 1) & 31;
			}
			while (history_line != ClientGlobals.edit_line && ClientGlobals.key_lines[history_line][1] == 0);
			if (history_line == ClientGlobals.edit_line)
				history_line = (ClientGlobals.edit_line + 1) & 31;
			//Lib.strcpy(Globals.key_lines[Globals.edit_line], Globals.key_lines[history_line]);
			System.arraycopy(ClientGlobals.key_lines[history_line], 0, ClientGlobals.key_lines[ClientGlobals.edit_line], 0, ClientGlobals.key_lines[ClientGlobals.edit_line].length);
			ClientGlobals.key_linepos = Lib.strlen(ClientGlobals.key_lines[ClientGlobals.edit_line]);
			ClientGlobals.con.backedit = 0; // sfranzyshen
			return;
		}

		if ((key == K_DOWNARROW) || (key == K_KP_DOWNARROW) || ((key == 'n') && ClientGlobals.keydown[K_CTRL])) {
			if (history_line == ClientGlobals.edit_line)
				return;
			do {
				history_line = (history_line + 1) & 31;
			}
			while (history_line != ClientGlobals.edit_line && ClientGlobals.key_lines[history_line][1] == 0);
			if (history_line == ClientGlobals.edit_line) {
				ClientGlobals.key_lines[ClientGlobals.edit_line][0] = ']';
				ClientGlobals.key_linepos = 1;
			}
			else {
				//Lib.strcpy(Globals.key_lines[Globals.edit_line], Globals.key_lines[history_line]);
				System.arraycopy(ClientGlobals.key_lines[history_line], 0, ClientGlobals.key_lines[ClientGlobals.edit_line], 0, ClientGlobals.key_lines[ClientGlobals.edit_line].length);
				ClientGlobals.key_linepos = Lib.strlen(ClientGlobals.key_lines[ClientGlobals.edit_line]);
				ClientGlobals.con.backedit = 0; // sfranzyshen
			}
			return;
		}

		if (key == K_MWHEELUP || key == K_PGUP || key == K_KP_PGUP) {
			ClientGlobals.con.display -= 2;
			return;
		}

		if (key == K_MWHEELDOWN || key == K_PGDN || key == K_KP_PGDN) {
			ClientGlobals.con.display += 2;
			if (ClientGlobals.con.display > ClientGlobals.con.current)
				ClientGlobals.con.display = ClientGlobals.con.current;
			return;
		}

		// sfranzyshen - start
		if (key == K_HOME || key == K_KP_HOME) {
			//Globals.con.display = Globals.con.current - Globals.con.totallines + 10;
			ClientGlobals.con.backedit = ClientGlobals.key_linepos -1;
			return;
		}

		if (key == K_END || key == K_KP_END) {
			//Globals.con.display = Globals.con.current;
			ClientGlobals.con.backedit = 0;
			return;
		}

		// sfranzyshen - stop


		if (key < 32 || key > 127)
			return; // non printable

		// sfranzyshen -start
		if (ClientGlobals.key_linepos < Defines.MAXCMDLINE - 1) {
			if (ClientGlobals.con.backedit > 0) { //insert character...
				for (i = ClientGlobals.key_linepos; i > ClientGlobals.key_linepos - ClientGlobals.con.backedit; i--)
					ClientGlobals.key_lines[ClientGlobals.edit_line][i] = ClientGlobals.key_lines[ClientGlobals.edit_line][i -1];
				ClientGlobals.key_lines[ClientGlobals.edit_line][i] = (byte) key;
				ClientGlobals.key_linepos++;
				ClientGlobals.key_lines[ClientGlobals.edit_line][ClientGlobals.key_linepos] = 0;
			} else {                       
				ClientGlobals.key_lines[ClientGlobals.edit_line][ClientGlobals.key_linepos++] = (byte) key;
				ClientGlobals.key_lines[ClientGlobals.edit_line][ClientGlobals.key_linepos] = 0;
			}
		}
		// sfranzyshen -stop


	}

	private static void printCompletions(String type, List<String> names) {
		Com.Printf(type);
		for (String name : names) {
			Com.Printf(name + " ");
		}
		Com.Printf("\n");
	}
	
	private static void CompleteCommand() {
		
		int start = 1;
		if (ClientGlobals.key_lines[ClientGlobals.edit_line][start] == '\\' ||  ClientGlobals.key_lines[ClientGlobals.edit_line][start] == '/')
			start++;
		
		int end = start;
		while (ClientGlobals.key_lines[ClientGlobals.edit_line][end] != 0) end++;
			
		String s = new String(ClientGlobals.key_lines[ClientGlobals.edit_line], start, end-start);
		
		List<String> cmds = Cmd.CompleteCommand(s);
		List<String> vars = Cvar.CompleteVariable(s);
		
		int commandsSize = cmds.size();
		int cvarsSize = vars.size();
		
		if ((commandsSize + cvarsSize) > 1) {
			if (commandsSize > 0) printCompletions("\nCommands:\n", cmds);
			if (cvarsSize > 0) printCompletions("\nVariables:\n", vars);
			return;
		} else if (commandsSize == 1) {
			s = cmds.get(0);
		} else if (cvarsSize == 1) {
			s = vars.get(0);
		} else return;
		
		ClientGlobals.key_lines[ClientGlobals.edit_line][1] = '/';
		byte[] bytes = Lib.stringToBytes(s);
		System.arraycopy(bytes, 0, ClientGlobals.key_lines[ClientGlobals.edit_line], 2, bytes.length);
		ClientGlobals.key_linepos = bytes.length + 2;
		ClientGlobals.key_lines[ClientGlobals.edit_line][ClientGlobals.key_linepos++] = ' ';
		ClientGlobals.key_lines[ClientGlobals.edit_line][ClientGlobals.key_linepos] = 0;
	}

	private static void Key_Bind_f(List<String> args) {

		if (args.size() < 2) {
			Com.Printf("bind <key> [command] : attach a command to a key\n");
			return;
		}
		int key = StringToKeynum(args.get(1));
		if (key == -1) {
			Com.Printf("\"" + args.get(1) + "\" isn't a valid key\n");
			return;
		}

		// show current binding
		if (args.size() == 2) {
			if (ClientGlobals.keybindings[key] != null)
				Com.Printf("\"" + args.get(1) + "\" = \"" + ClientGlobals.keybindings[key] + "\"\n");
			else
				Com.Printf("\"" + args.get(1) + "\" is not bound\n");
			return;
		}

		// copy the rest of the command line
		String cmd = ""; // start out with a null string
		for (int i = 2; i < args.size(); i++) {
			cmd += args.get(i);
			if (i != (args.size() - 1))
				cmd += " ";
		}

		SetBinding(key, cmd);
	}

	static void SetBinding(int keynum, String binding) {
		if (keynum == -1)
			return;

		// free old bindings
		ClientGlobals.keybindings[keynum] = null;

		ClientGlobals.keybindings[keynum] = binding;
	}

	private static void Key_Unbind_f(List<String> args) {

		if (args.size() != 2) {
			Com.Printf("unbind <key> : remove commands from a key\n");
			return;
		}

		int b = Key.StringToKeynum(args.get(1));
		if (b == -1) {
			Com.Printf("\"" + args.get(1) + "\" isn't a valid key\n");
			return;
		}

		Key.SetBinding(b, null);
	}

	private static void Key_Unbindall_f() {
		for (int i = 0; i < 256; i++)
			Key.SetBinding(i, null);
	}

	private static void Key_Bindlist_f() {
		for (int i = 0; i < 256; i++)
			if (ClientGlobals.keybindings[i] != null && ClientGlobals.keybindings[i].length() != 0)
				Com.Printf(Key.KeynumToString(i) + " \"" + ClientGlobals.keybindings[i] + "\"\n");
	}

	static void ClearStates() {
		int i;

		Key.anykeydown = 0;

		for (i = 0; i < 256; i++) {
			if (ClientGlobals.keydown[i] || key_repeats[i]!=0)
				Event(i, false, 0);
			ClientGlobals.keydown[i] = false;
			key_repeats[i] = 0;
		}
	}

	static void WriteBindings(RandomAccessFile f) {
		for (int i = 0; i < 256; i++)
			if (ClientGlobals.keybindings[i] != null && ClientGlobals.keybindings[i].length() > 0)
				try {
					f.writeBytes("bind " + KeynumToString(i) + " \"" + ClientGlobals.keybindings[i] + "\"\n");
				} catch (IOException e) {}
	}

}
