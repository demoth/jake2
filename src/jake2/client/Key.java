/*
 * Key.java
 * Copyright (C) 2003
 * 
 * $Id: Key.java,v 1.7 2004-01-07 13:44:36 hoz Exp $
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
//	00715         char    *kb;
//	00716         char    cmd[1024];
//	00717 
//	00718         // hack for modal presses
//	00719         if (key_waiting == -1)
//	00720         {
//	00721                 if (down)
//	00722                         key_waiting = key;
//	00723                 return;
//	00724         }
//	00725 
//	00726         // update auto-repeat status
//	00727         if (down)
//	00728         {
//	00729                 key_repeats[key]++;
//	00730                 if (key != K_BACKSPACE 
//	00731                         && key != K_PAUSE 
//	00732                         && key != K_PGUP 
//	00733                         && key != K_KP_PGUP 
//	00734                         && key != K_PGDN
//	00735                         && key != K_KP_PGDN
//	00736                         && key_repeats[key] > 1)
//	00737                         return; // ignore most autorepeats
//	00738                         
//	00739                 if (key >= 200 && !keybindings[key])
//	00740                         Com_Printf ("%s is unbound, hit F4 to set.\n", Key_KeynumToString (key) );
//	00741         }
//	00742         else
//	00743         {
//	00744                 key_repeats[key] = 0;
//	00745         }
//	00746 
//	00747         if (key == K_SHIFT)
//	00748                 shift_down = down;
//	00749 
//	00750         // console key is hardcoded, so the user can never unbind it
//	00751         if (key == '`' || key == '~')
//	00752         {
//	00753                 if (!down)
//	00754                         return;
//	00755                 Con_ToggleConsole_f ();
//	00756                 return;
//	00757         }
//	00758 
//	00759         // any key during the attract mode will bring up the menu
//	00760         if (cl.attractloop && cls.key_dest != key_menu &&
//	00761                 !(key >= K_F1 && key <= K_F12))
//	00762                 key = K_ESCAPE;
//	00763 
//	00764         // menu key is hardcoded, so the user can never unbind it
//	00765         if (key == K_ESCAPE)
//	00766         {
//	00767                 if (!down)
//	00768                         return;
//	00769 
//	00770                 if (cl.frame.playerstate.stats[STAT_LAYOUTS] && cls.key_dest == key_game)
//	00771                 {       // put away help computer / inventory
//	00772                         Cbuf_AddText ("cmd putaway\n");
//	00773                         return;
//	00774                 }
//	00775                 switch (cls.key_dest)
//	00776                 {
//	00777                 case key_message:
//	00778                         Key_Message (key);
//	00779                         break;
//	00780                 case key_menu:
//	00781                         M_Keydown (key);
//	00782                         break;
//	00783                 case key_game:
//	00784                 case key_console:
//	00785                         M_Menu_Main_f ();
//	00786                         break;
//	00787                 default:
//	00788                         Com_Error (ERR_FATAL, "Bad cls.key_dest");
//	00789                 }
//	00790                 return;
//	00791         }
//	00792 
//	00793         // track if any key is down for BUTTON_ANY
//	00794         keydown[key] = down;
//	00795         if (down)
//	00796         {
//	00797                 if (key_repeats[key] == 1)
//	00798                         anykeydown++;
//	00799         }
//	00800         else
//	00801         {
//	00802                 anykeydown--;
//	00803                 if (anykeydown < 0)
//	00804                         anykeydown = 0;
//	00805         }
//	00806 
//	00807 //
//	00808 // key up events only generate commands if the game key binding is
//	00809 // a button command (leading + sign).  These will occur even in console mode,
//	00810 // to keep the character from continuing an action started before a console
//	00811 // switch.  Button commands include the kenum as a parameter, so multiple
//	00812 // downs can be matched with ups
//	00813 //
//	00814         if (!down)
//	00815         {
//	00816                 kb = keybindings[key];
//	00817                 if (kb && kb[0] == '+')
//	00818                 {
//	00819                         Com_sprintf (cmd, sizeof(cmd), "-%s %i %i\n", kb+1, key, time);
//	00820                         Cbuf_AddText (cmd);
//	00821                 }
//	00822                 if (keyshift[key] != key)
//	00823                 {
//	00824                         kb = keybindings[keyshift[key]];
//	00825                         if (kb && kb[0] == '+')
//	00826                         {
//	00827                                 Com_sprintf (cmd, sizeof(cmd), "-%s %i %i\n", kb+1, key, time);
//	00828                                 Cbuf_AddText (cmd);
//	00829                         }
//	00830                 }
//	00831                 return;
//	00832         }
//	00833 
//	00834 //
//	00835 // if not a consolekey, send to the interpreter no matter what mode is
//	00836 //
//	00837         if ( (cls.key_dest == key_menu && menubound[key])
//	00838         || (cls.key_dest == key_console && !consolekeys[key])
//	00839         || (cls.key_dest == key_game && ( cls.state == ca_active || !consolekeys[key] ) ) )
//	00840         {
//	00841                 kb = keybindings[key];
//	00842                 if (kb)
//	00843                 {
//	00844                         if (kb[0] == '+')
//	00845                         {       // button commands add keynum and time as a parm
//	00846                                 Com_sprintf (cmd, sizeof(cmd), "%s %i %i\n", kb, key, time);
//	00847                                 Cbuf_AddText (cmd);
//	00848                         }
//	00849                         else
//	00850                         {
//	00851                                 Cbuf_AddText (kb);
//	00852                                 Cbuf_AddText ("\n");
//	00853                         }
//	00854                 }
//	00855                 return;
//	00856         }
//	00857 
//	00858         if (!down)
//	00859                 return;         // other systems only care about key down events
//	00860 
//	00861         if (shift_down)
//	00862                 key = keyshift[key];
//	00863 
//	00864         switch (cls.key_dest)
//	00865         {
//	00866         case key_message:
//	00867                 Key_Message (key);
//	00868                 break;
//	00869         case key_menu:
//	00870                 M_Keydown (key);
//	00871                 break;
//	00872 
//	00873         case key_game:
//	00874         case key_console:
//	00875                 Key_Console (key);
//	00876                 break;
//	00877         default:
//	00878                 Com_Error (ERR_FATAL, "Bad cls.key_dest");
//	00879         }
	}	
}
