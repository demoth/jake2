/*
 * Con.java
 * Copyright (C) 2003
 * 
 * $Id: Console.java,v 1.8 2003-12-28 13:53:38 hoz Exp $
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

import java.util.Arrays;

import jake2.Defines;
import jake2.Globals;
import jake2.game.Cmd;
import jake2.qcommon.*;

/**
 * Con
 * TODO implement Con
 */
public final class Console {
	
	public static xcommand_t ToggleConsole_f;
	public static xcommand_t ToggleChat_f;
	public static xcommand_t MessageMode_f;
	public static xcommand_t MessageMode2_f;
	public static xcommand_t Clear_f;
	public static xcommand_t Dump_f;
	
	/**
	 * 
	 */
	public static void Init() {
		Globals.con.linewidth = -1;

		CheckResize();
 
		Com.Printf("Console initialized.\n");

		//
		// register our commands
		//
		Globals.con_notifytime = Cvar.Get("con_notifytime", "3", 0);

		Cmd.AddCommand("toggleconsole", ToggleConsole_f);
		Cmd.AddCommand("togglechat", ToggleChat_f);
		Cmd.AddCommand("messagemode", MessageMode_f);
		Cmd.AddCommand("messagemode2", MessageMode2_f);
		Cmd.AddCommand("clear", Clear_f);
		Cmd.AddCommand("condump", Dump_f);
		Globals.con.initialized = true;		
	}

	/**
	 * If the line width has changed, reformat the buffer.
	 */
	public static void CheckResize() {
		int i, j, width, oldwidth, oldtotallines, numlines, numchars;
		byte[] tbuf = new byte[Defines.CON_TEXTSIZE];

		width = (Globals.viddef.width >> 3) - 2;
 
		if (width == Globals.con.linewidth)
			return;

		if (width < 1) {	// video hasn't been initialized yet
			width = 38;
			Globals.con.linewidth = width;
			Globals.con.totallines = Defines.CON_TEXTSIZE / Globals.con.linewidth;
			Arrays.fill(Globals.con.text, (byte)' ');
		} 
		else {
			oldwidth = Globals.con.linewidth;
			Globals.con.linewidth = width;
			oldtotallines = Globals.con.totallines;
			Globals.con.totallines = Defines.CON_TEXTSIZE / Globals.con.linewidth;
			numlines = oldtotallines;
 
			if (Globals.con.totallines < numlines)
				numlines = Globals.con.totallines;

			numchars = oldwidth;
      
			if (Globals.con.linewidth < numchars)
				numchars = Globals.con.linewidth;

			System.arraycopy(Globals.con.text, 0, tbuf, 0, Defines.CON_TEXTSIZE);
			Arrays.fill(Globals.con.text, (byte)' ');
 
			for (i=0 ; i<numlines ; i++) {
				for (j=0 ; j<numchars ; j++) {
					Globals.con.text[(Globals.con.totallines - 1 - i) * Globals.con.linewidth + j] =
						tbuf[((Globals.con.current - i + oldtotallines) % oldtotallines) * oldwidth + j];
				}
			}

			Console.ClearNotify();
		}
 
		Globals.con.current = Globals.con.totallines - 1;
		Globals.con.display = Globals.con.current;
	}
	
	public static void ClearNotify() {
		int i;
		for (i=0 ; i<Defines.NUM_CON_TIMES ; i++)
			Globals.con.times[i] = 0;
	}
}
