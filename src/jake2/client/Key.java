/*
 * Key.java
 * Copyright (C) 2003
 * 
 * $Id: Key.java,v 1.12 2004-01-18 12:36:02 hoz Exp $
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
import jake2.qcommon.Cbuf;
import jake2.qcommon.Com;
import jake2.util.Lib;


/**
 * Key
 * TODO complete key interface
 */
public final class Key {
	//
	// these are the key numbers that should be passed to Key_Event
	//
	public static final int K_TAB = 9;
	public static final int K_ENTER = 13;
	public static final int K_ESCAPE = 27;
	public static final int K_SPACE = 32;

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

	public static final int K_KP_HOME = 160;
	public static final int K_KP_UPARROW = 161;
	public static final int K_KP_PGUP = 162;
	public static final int K_KP_LEFTARROW = 163;
	public static final int K_KP_5 = 164;
	public static final int K_KP_RIGHTARROW = 165;
	public static final int K_KP_END = 166;
	public static final int K_KP_DOWNARROW = 167;
	public static final int K_KP_PGDN = 168;
	public static final int K_KP_ENTER = 169;
	public static final int K_KP_INS = 170;
	public static final int K_KP_DEL = 171;
	public static final int K_KP_SLASH = 172;
	public static final int K_KP_MINUS = 173;
	public static final int K_KP_PLUS = 174;
 
	public static final int K_PAUSE = 255;
 
	//
	// mouse buttons generate virtual keys
	//
	public static final int K_MOUSE1 = 200;
	public static final int K_MOUSE2 = 201;
	public static final int K_MOUSE3 = 202;
 
	//
	// joystick buttons
	//
	public static final int K_JOY1 = 203;
	public static final int K_JOY2 = 204;
	public static final int K_JOY3 = 205;
	public static final int K_JOY4 = 206;

	public static final int K_MWHEELDOWN = 239;
	public static final int K_MWHEELUP = 240;
	
	static int key_waiting;
	static int history_line = 0;
	static boolean shift_down = false;
	static int[] key_repeats = new int[256];
	static int[] keyshift = new int[256];
	static boolean[] menubound = new boolean[256];
	static boolean[] consolekeys = new boolean[256];
	
	static String[] keynames = new String[256];
	
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
//	00068         {"F1", K_F1},
//	00069         {"F2", K_F2},
//	00070         {"F3", K_F3},
//	00071         {"F4", K_F4},
//	00072         {"F5", K_F5},
//	00073         {"F6", K_F6},
//	00074         {"F7", K_F7},
//	00075         {"F8", K_F8},
//	00076         {"F9", K_F9},
//	00077         {"F10", K_F10},
//	00078         {"F11", K_F11},
//	00079         {"F12", K_F12},
//	00080 
//	00081         {"INS", K_INS},
//	00082         {"DEL", K_DEL},
//	00083         {"PGDN", K_PGDN},
//	00084         {"PGUP", K_PGUP},
//	00085         {"HOME", K_HOME},
//	00086         {"END", K_END},
//	00087 
//	00088         {"MOUSE1", K_MOUSE1},
//	00089         {"MOUSE2", K_MOUSE2},
//	00090         {"MOUSE3", K_MOUSE3},
//	00091 
//	00092         {"JOY1", K_JOY1},
//	00093         {"JOY2", K_JOY2},
//	00094         {"JOY3", K_JOY3},
//	00095         {"JOY4", K_JOY4},
//	00096 
//	00097         {"AUX1", K_AUX1},
//	00098         {"AUX2", K_AUX2},
//	00099         {"AUX3", K_AUX3},
//	00100         {"AUX4", K_AUX4},
//	00101         {"AUX5", K_AUX5},
//	00102         {"AUX6", K_AUX6},
//	00103         {"AUX7", K_AUX7},
//	00104         {"AUX8", K_AUX8},
//	00105         {"AUX9", K_AUX9},
//	00106         {"AUX10", K_AUX10},
//	00107         {"AUX11", K_AUX11},
//	00108         {"AUX12", K_AUX12},
//	00109         {"AUX13", K_AUX13},
//	00110         {"AUX14", K_AUX14},
//	00111         {"AUX15", K_AUX15},
//	00112         {"AUX16", K_AUX16},
//	00113         {"AUX17", K_AUX17},
//	00114         {"AUX18", K_AUX18},
//	00115         {"AUX19", K_AUX19},
//	00116         {"AUX20", K_AUX20},
//	00117         {"AUX21", K_AUX21},
//	00118         {"AUX22", K_AUX22},
//	00119         {"AUX23", K_AUX23},
//	00120         {"AUX24", K_AUX24},
//	00121         {"AUX25", K_AUX25},
//	00122         {"AUX26", K_AUX26},
//	00123         {"AUX27", K_AUX27},
//	00124         {"AUX28", K_AUX28},
//	00125         {"AUX29", K_AUX29},
//	00126         {"AUX30", K_AUX30},
//	00127         {"AUX31", K_AUX31},
//	00128         {"AUX32", K_AUX32},
//	00129 
//	00130         {"KP_HOME",                     K_KP_HOME },
//	00131         {"KP_UPARROW",          K_KP_UPARROW },
//	00132         {"KP_PGUP",                     K_KP_PGUP },
//	00133         {"KP_LEFTARROW",        K_KP_LEFTARROW },
//	00134         {"KP_5",                        K_KP_5 },
//	00135         {"KP_RIGHTARROW",       K_KP_RIGHTARROW },
//	00136         {"KP_END",                      K_KP_END },
//	00137         {"KP_DOWNARROW",        K_KP_DOWNARROW },
//	00138         {"KP_PGDN",                     K_KP_PGDN },
//	00139         {"KP_ENTER",            K_KP_ENTER },
//	00140         {"KP_INS",                      K_KP_INS },
//	00141         {"KP_DEL",                      K_KP_DEL },
//	00142         {"KP_SLASH",            K_KP_SLASH },
//	00143         {"KP_MINUS",            K_KP_MINUS },
//	00144         {"KP_PLUS",                     K_KP_PLUS },
//	00145 
//	00146         {"MWHEELUP", K_MWHEELUP },
//	00147         {"MWHEELDOWN", K_MWHEELDOWN },
//	00148 
//	00149         {"PAUSE", K_PAUSE},
//	00150 
//	00151         {"SEMICOLON", ';'},     // because a raw semicolon seperates commands
//	00152 
//	00153         {NULL,0}	
	}
	/**
	 * 
	 */
	public static void Init() {
	}
	
	public static void ClearTyping() {
//	00058         key_lines[edit_line][1] = 0;    // clear any typing
//	00059         key_linepos = 1;
	}

	/**
	 * Called by the system between frames for both key up and key down events.
	 */
	public static void Event(int key, boolean down, long time) {
		//System.out.println(key +  " " + down);
		return;
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
			if (key != K_BACKSPACE 
				&& key != K_PAUSE 
				&& key != K_PGUP 
				&& key != K_KP_PGUP 
				&& key != K_PGDN
				&& key != K_KP_PGDN
				&& key_repeats[key] > 1)
				return; // ignore most autorepeats
                        
			if (key >= 200 && Globals.keybindings[key] == null)
				Com.Printf( Key.KeynumToString(key) + " is unbound, hit F4 to set.\n");
		} else {
			key_repeats[key] = 0;
		}
 
		if (key == K_SHIFT)
			shift_down = down;
 
		// console key is hardcoded, so the user can never unbind it
		if (key == '`' || key == '~') {
			if (!down)
				return;
			try {
				Console.ToggleConsole_f.execute();
			} catch (Exception e) {
			}
			return;
		}
 
		// any key during the attract mode will bring up the menu
		if (Globals.cl.attractloop && Globals.cls.key_dest != Defines.key_menu &&
			!(key >= K_F1 && key <= K_F12))
			key = K_ESCAPE;

		// menu key is hardcoded, so the user can never unbind it
		if (key == K_ESCAPE) {
			if (!down) return;
 
			if (Globals.cl.frame.playerstate.stats[Defines.STAT_LAYOUTS] != 0 
				&& Globals.cls.key_dest == Defines.key_game) {
				// put away help computer / inventory
				Cbuf.AddText("cmd putaway\n");
				return;
			}
			switch (Globals.cls.key_dest) {
				case Defines.key_message:
					Key.Message(key);
					break;
				case Defines.key_menu:
					M.Keydown(key);
				break;
				case Defines.key_game:
				case Defines.key_console:
					M.Menu_Main_f();
				break;
				default:
					Com.Error(Defines.ERR_FATAL, "Bad cls.key_dest");
			}
			return;
		}
 
		// track if any key is down for BUTTON_ANY
		Globals.keydown[key] = down;
		if (down) {
			if (key_repeats[key] == 1)
			Globals.anykeydown++;
		} else {
			Globals.anykeydown--;
			if (Globals.anykeydown < 0) Globals.anykeydown = 0;
		}
 
		//
		// key up events only generate commands if the game key binding is
		// a button command (leading + sign).  These will occur even in console mode,
		// to keep the character from continuing an action started before a console
		// switch.  Button commands include the kenum as a parameter, so multiple
		// downs can be matched with ups
		//
		if (!down) {
			kb = Globals.keybindings[key];
			if (kb != null && kb.charAt(0) == '+') {
				cmd = "-" + kb.substring(1) + " " + key + " " + time + "\n";
				Cbuf.AddText(cmd);
			}
			if (keyshift[key] != key) {
				kb = Globals.keybindings[keyshift[key]];
				if (kb != null && kb.charAt(0) == '+') {
					cmd = "-" + kb.substring(1) + " " + key + " " + time + "\n";
					Cbuf.AddText(cmd);
				}
			}
			return;
		}
 
		//
		// if not a consolekey, send to the interpreter no matter what mode is
		//
		if ( (Globals.cls.key_dest == Defines.key_menu && menubound[key])
			|| (Globals.cls.key_dest == Defines.key_console && !consolekeys[key])
			|| (Globals.cls.key_dest == Defines.key_game && ( Globals.cls.state == Defines.ca_active || !consolekeys[key] ) ) )
		{
			kb = Globals.keybindings[key];
			if (kb != null) {
				if (kb.charAt(0) == '+') {
					// button commands add keynum and time as a parm
					cmd = kb + " " + key + " " + time + "\n";
					Cbuf.AddText(cmd);
				} else {
					Cbuf.AddText(kb + "\n");
				}
			}
			return;
		}
 
		if (!down)
			return;         // other systems only care about key down events
 
		if (shift_down)
			key = keyshift[key];
 
		switch (Globals.cls.key_dest) {
			case Defines.key_message:
				Key.Message(key);
				break;
			case Defines.key_menu:
				M.Keydown(key);
				break;
 
			case Defines.key_game:
			case Defines.key_console:
				Key.Console(key);
				break;
			default:
			Com.Error(Defines.ERR_FATAL, "Bad cls.key_dest");
		}
	}
	
	/**
	 * Returns a string (either a single ascii char, or a K_* name) for the 
	 * given keynum.
	 */
	public static String KeynumToString(int keynum) {
		if (keynum < 0 || keynum > 255)
			return "<KEY NOT FOUND>";
		if (keynum > 32 && keynum < 127)
			return Character.toString((char)keynum);

		if (keynames[keynum] != null)
			return keynames[keynum];       

		return "<UNKNOWN KEYNUM>";
	}
	
	public static void Message(int key) {
 
		if ( key == K_ENTER || key == K_KP_ENTER ) {
			if (Globals.chat_team)
				Cbuf.AddText("say_team \"");
			else
				Cbuf.AddText("say \"");
				
			Cbuf.AddText(Globals.chat_buffer);
			Cbuf.AddText("\"\n");
 
			Globals.cls.key_dest = Defines.key_game;
			Globals.chat_buffer = "";
			return;
		}

		if (key == K_ESCAPE) {
			Globals.cls.key_dest = Defines.key_game;
			Globals.chat_buffer = "";
			return;
		}

		if (key < 32 || key > 127)
			return; // non printable

		if (key == K_BACKSPACE) {
			if (Globals.chat_buffer.length() > 2) {
				Globals.chat_buffer = Globals.chat_buffer.substring(0, Globals.chat_buffer.length()-2);
			}
			else Globals.chat_buffer = "";
			return;
		}

		if (Globals.chat_buffer.length() > Defines.MAXCMDLINE)
			return; // all full
 
		Globals.chat_buffer += (char)key;
	}

	/**
	 * Interactive line editing and console scrollback.
	 */
	public static void Console(int key) {

		switch ( key ) {
			case K_KP_SLASH:
				key = '/';
				break;
			case K_KP_MINUS:
				key = '-';
				break;
			case K_KP_PLUS:
				key = '+';
				break;
			case K_KP_HOME:
				key = '7';
				break;
			case K_KP_UPARROW:
				key = '8';
				break;
			case K_KP_PGUP:
				key = '9';
				break;
			case K_KP_LEFTARROW:
				key = '4';
				break;
			case K_KP_5:
				key = '5';
				break;
			case K_KP_RIGHTARROW:
				key = '6';
				break;
			case K_KP_END:
				key = '1';
				break;
			case K_KP_DOWNARROW:
				key = '2';
				break;
			case K_KP_PGDN:
				key = '3';
				break;
			case K_KP_INS:
				key = '0';
				break;
			case K_KP_DEL:
				key = '.';
				break;
		}

		if ( key == 'l' ) {
			if ( Globals.keydown[K_CTRL] ) {
				Cbuf.AddText("clear\n");
				return;
			}
		}

		if ( key == K_ENTER || key == K_KP_ENTER ) {
			// backslash text are commands, else chat
			if (Globals.key_lines[Globals.edit_line][1] == '\\' || Globals.key_lines[Globals.edit_line][1] == '/')
				Cbuf.AddText(new String(Globals.key_lines[Globals.edit_line], 2, Lib.strlen(Globals.key_lines[Globals.edit_line])-2));
			else
				Cbuf.AddText(new String(Globals.key_lines[Globals.edit_line], 1, Lib.strlen(Globals.key_lines[Globals.edit_line])-1));
 
			Cbuf.AddText("\n");
			Com.Printf(new String(Globals.key_lines[Globals.edit_line], 0, Lib.strlen(Globals.key_lines[Globals.edit_line])));
			Globals.edit_line = (Globals.edit_line + 1) & 31;
			history_line = Globals.edit_line;
			Globals.key_lines[Globals.edit_line][0] = ']';
			Globals.key_linepos = 1;
			if (Globals.cls.state == Defines.ca_disconnected)
				SCR.UpdateScreen();		// force an update, because the command
										// may take some time
			return;
		}

		if (key == K_TAB) {
			// command completion
			CompleteCommand ();
			return;
		}
         
		if ( ( key == K_BACKSPACE ) || ( key == K_LEFTARROW ) || ( key == K_KP_LEFTARROW ) || ( ( key == 'h' ) && ( Globals.keydown[K_CTRL] ) ) ) {
			if (Globals.key_linepos > 1)
				Globals.key_linepos--;
			return;
		}
 
		if ( ( key == K_UPARROW ) || ( key == K_KP_UPARROW ) ||
				( ( key == 'p' ) && Globals.keydown[K_CTRL] ) ) {
			do {
				history_line = (history_line - 1) & 31;
			} while (history_line != Globals.edit_line && Globals.key_lines[history_line][1]==0);
			if (history_line == Globals.edit_line)
				history_line = (Globals.edit_line+1)&31;
			Lib.strcpy(Globals.key_lines[Globals.edit_line], Globals.key_lines[history_line]);
			Globals.key_linepos = Lib.strlen(Globals.key_lines[Globals.edit_line]);
			return;
		}
 
//	00299         if ( ( key == K_DOWNARROW ) || ( key == K_KP_DOWNARROW ) ||
//	00300                  ( ( key == 'n' ) && keydown[K_CTRL] ) )
//	00301         {
//	00302                 if (history_line == edit_line) return;
//	00303                 do
//	00304                 {
//	00305                         history_line = (history_line + 1) & 31;
//	00306                 }
//	00307                 while (history_line != edit_line
//	00308                         && !key_lines[history_line][1]);
//	00309                 if (history_line == edit_line)
//	00310                 {
//	00311                         key_lines[edit_line][0] = ']';
//	00312                         key_linepos = 1;
//	00313                 }
//	00314                 else
//	00315                 {
//	00316                         strcpy(key_lines[edit_line], key_lines[history_line]);
//	00317                         key_linepos = strlen(key_lines[edit_line]);
//	00318                 }
//	00319                 return;
//	00320         }
//	00321 
		if (key == K_PGUP || key == K_KP_PGUP ) {
			Globals.con.display -= 2;
			return;
		}
//	00327 
//	00328         if (key == K_PGDN || key == K_KP_PGDN ) 
//	00329         {
//	00330                 con.display += 2;
//	00331                 if (con.display > con.current)
//	00332                         con.display = con.current;
//	00333                 return;
//	00334         }
//	00335 
//	00336         if (key == K_HOME || key == K_KP_HOME )
//	00337         {
//	00338                 con.display = con.current - con.totallines + 10;
//	00339                 return;
//	00340         }
//	00341 
//	00342         if (key == K_END || key == K_KP_END )
//	00343         {
//	00344                 con.display = con.current;
//	00345                 return;
//	00346         }
//	00347         
//	00348         if (key < 32 || key > 127)
//	00349                 return; // non printable
//	00350                 
//	00351         if (key_linepos < MAXCMDLINE-1)
//	00352         {
//	00353                 key_lines[edit_line][key_linepos] = key;
//	00354                 key_linepos++;
//	00355                 key_lines[edit_line][key_linepos] = 0;
//	00356         }
//	00357 
	}
	
	static void CompleteCommand() {
//	00166         char    *cmd, *s;
//	00167 
//	00168         s = key_lines[edit_line]+1;
//	00169         if (*s == '\\' || *s == '/')
//	00170                 s++;
//	00171 
//	00172         cmd = Cmd_CompleteCommand (s);
//	00173         if (!cmd)
//	00174                 cmd = Cvar_CompleteVariable (s);
//	00175         if (cmd)
//	00176         {
//	00177                 key_lines[edit_line][1] = '/';
//	00178                 strcpy (key_lines[edit_line]+2, cmd);
//	00179                 key_linepos = strlen(cmd)+2;
//	00180                 key_lines[edit_line][key_linepos] = ' ';
//	00181                 key_linepos++;
//	00182                 key_lines[edit_line][key_linepos] = 0;
//	00183                 return;
//	00184         }
	}	
}
