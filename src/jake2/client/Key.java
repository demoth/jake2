/*
 * Key.java
 * Copyright (C) 2003
 * 
 * $Id: Key.java,v 1.9 2004-01-09 13:35:15 hoz Exp $
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
	static boolean shift_down = false;
	static int[] key_repeats = new int[256];
	static int[] keyshift = new int[256];
	static boolean[] menubound = new boolean[256];
	static boolean[] consolekeys = new boolean[256];

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
		System.out.println(key +  " " + down);
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
	
//	00441 /*
//	00442 ===================
//	00443 Key_KeynumToString
//	00444 
//	00445 Returns a string (either a single ascii char, or a K_* name) for the
//	00446 given keynum.
//	00447 FIXME: handle quote special (general escape sequence?)
//	00448 ===================
//	00449 */
	public static String KeynumToString(int keynum) {
//	00452         keyname_t       *kn;    
//	00453         static  char    tinystr[2];
//	00454         
//	00455         if (keynum == -1)
//	00456                 return "<KEY NOT FOUND>";
//	00457         if (keynum > 32 && keynum < 127)
//	00458         {       // printable ascii
//	00459                 tinystr[0] = keynum;
//	00460                 tinystr[1] = 0;
//	00461                 return tinystr;
//	00462         }
//	00463         
//	00464         for (kn=keynames ; kn->name ; kn++)
//	00465                 if (keynum == kn->keynum)
//	00466                         return kn->name;
//	00467 
		return "<UNKNOWN KEYNUM>";
	}
	
	public static void Message(int key) {
//	00368 
//	00369         if ( key == K_ENTER || key == K_KP_ENTER )
//	00370         {
//	00371                 if (chat_team)
//	00372                         Cbuf_AddText ("say_team \"");
//	00373                 else
//	00374                         Cbuf_AddText ("say \"");
//	00375                 Cbuf_AddText(chat_buffer);
//	00376                 Cbuf_AddText("\"\n");
//	00377 
//	00378                 cls.key_dest = key_game;
//	00379                 chat_bufferlen = 0;
//	00380                 chat_buffer[0] = 0;
//	00381                 return;
//	00382         }
//	00383 
//	00384         if (key == K_ESCAPE)
//	00385         {
//	00386                 cls.key_dest = key_game;
//	00387                 chat_bufferlen = 0;
//	00388                 chat_buffer[0] = 0;
//	00389                 return;
//	00390         }
//	00391 
//	00392         if (key < 32 || key > 127)
//	00393                 return; // non printable
//	00394 
//	00395         if (key == K_BACKSPACE)
//	00396         {
//	00397                 if (chat_bufferlen)
//	00398                 {
//	00399                         chat_bufferlen--;
//	00400                         chat_buffer[chat_bufferlen] = 0;
//	00401                 }
//	00402                 return;
//	00403         }
//	00404 
//	00405         if (chat_bufferlen == sizeof(chat_buffer)-1)
//	00406                 return; // all full
//	00407 
//	00408         chat_buffer[chat_bufferlen++] = key;
//	00409         chat_buffer[chat_bufferlen] = 0;
	}

//	00187 /*
//	00188 ====================
//	00189 Key_Console
//	00190 
//	00191 Interactive line editing and console scrollback
//	00192 ====================
//	00193 */
	public static void Console(int key) {
//	00196 
//	00197         switch ( key )
//	00198         {
//	00199         case K_KP_SLASH:
//	00200                 key = '/';
//	00201                 break;
//	00202         case K_KP_MINUS:
//	00203                 key = '-';
//	00204                 break;
//	00205         case K_KP_PLUS:
//	00206                 key = '+';
//	00207                 break;
//	00208         case K_KP_HOME:
//	00209                 key = '7';
//	00210                 break;
//	00211         case K_KP_UPARROW:
//	00212                 key = '8';
//	00213                 break;
//	00214         case K_KP_PGUP:
//	00215                 key = '9';
//	00216                 break;
//	00217         case K_KP_LEFTARROW:
//	00218                 key = '4';
//	00219                 break;
//	00220         case K_KP_5:
//	00221                 key = '5';
//	00222                 break;
//	00223         case K_KP_RIGHTARROW:
//	00224                 key = '6';
//	00225                 break;
//	00226         case K_KP_END:
//	00227                 key = '1';
//	00228                 break;
//	00229         case K_KP_DOWNARROW:
//	00230                 key = '2';
//	00231                 break;
//	00232         case K_KP_PGDN:
//	00233                 key = '3';
//	00234                 break;
//	00235         case K_KP_INS:
//	00236                 key = '0';
//	00237                 break;
//	00238         case K_KP_DEL:
//	00239                 key = '.';
//	00240                 break;
//	00241         }
//	00242 
//	00243         if ( key == 'l' ) 
//	00244         {
//	00245                 if ( keydown[K_CTRL] )
//	00246                 {
//	00247                         Cbuf_AddText ("clear\n");
//	00248                         return;
//	00249                 }
//	00250         }
//	00251 
//	00252         if ( key == K_ENTER || key == K_KP_ENTER )
//	00253         {       // backslash text are commands, else chat
//	00254                 if (key_lines[edit_line][1] == '\\' || key_lines[edit_line][1] == '/')
//	00255                         Cbuf_AddText (key_lines[edit_line]+2);  // skip the >
//	00256                 else
//	00257                         Cbuf_AddText (key_lines[edit_line]+1);  // valid command
//	00258 
//	00259                 Cbuf_AddText ("\n");
//	00260                 Com_Printf ("%s\n",key_lines[edit_line]);
//	00261                 edit_line = (edit_line + 1) & 31;
//	00262                 history_line = edit_line;
//	00263                 key_lines[edit_line][0] = ']';
//	00264                 key_linepos = 1;
//	00265                 if (cls.state == ca_disconnected)
//	00266                         SCR_UpdateScreen ();    // force an update, because the command
//	00267                                                                         // may take some time
//	00268                 return;
//	00269         }
//	00270 
//	00271         if (key == K_TAB)
//	00272         {       // command completion
//	00273                 CompleteCommand ();
//	00274                 return;
//	00275         }
//	00276         
//	00277         if ( ( key == K_BACKSPACE ) || ( key == K_LEFTARROW ) || ( key == K_KP_LEFTARROW ) || ( ( key == 'h' ) && ( keydown[K_CTRL] ) ) )
//	00278         {
//	00279                 if (key_linepos > 1)
//	00280                         key_linepos--;
//	00281                 return;
//	00282         }
//	00283 
//	00284         if ( ( key == K_UPARROW ) || ( key == K_KP_UPARROW ) ||
//	00285                  ( ( key == 'p' ) && keydown[K_CTRL] ) )
//	00286         {
//	00287                 do
//	00288                 {
//	00289                         history_line = (history_line - 1) & 31;
//	00290                 } while (history_line != edit_line
//	00291                                 && !key_lines[history_line][1]);
//	00292                 if (history_line == edit_line)
//	00293                         history_line = (edit_line+1)&31;
//	00294                 strcpy(key_lines[edit_line], key_lines[history_line]);
//	00295                 key_linepos = strlen(key_lines[edit_line]);
//	00296                 return;
//	00297         }
//	00298 
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
//	00322         if (key == K_PGUP || key == K_KP_PGUP )
//	00323         {
//	00324                 con.display -= 2;
//	00325                 return;
//	00326         }
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
}
