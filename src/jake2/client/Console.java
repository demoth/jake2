/*
 * Con.java
 * Copyright (C) 2003
 * 
 * $Id: Console.java,v 1.7 2003-12-21 13:50:24 hoz Exp $
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
//	00247         int             i, j, width, oldwidth, oldtotallines, numlines, numchars;
//	00248         char    tbuf[CON_TEXTSIZE];
//	00249 
//	00250         width = (viddef.width >> 3) - 2;
//	00251 
//	00252         if (width == con.linewidth)
//	00253                 return;
//	00254 
//	00255         if (width < 1)                  // video hasn't been initialized yet
//	00256         {
//	00257                 width = 38;
//	00258                 con.linewidth = width;
//	00259                 con.totallines = CON_TEXTSIZE / con.linewidth;
//	00260                 memset (con.text, ' ', CON_TEXTSIZE);
//	00261         }
//	00262         else
//	00263         {
//	00264                 oldwidth = con.linewidth;
//	00265                 con.linewidth = width;
//	00266                 oldtotallines = con.totallines;
//	00267                 con.totallines = CON_TEXTSIZE / con.linewidth;
//	00268                 numlines = oldtotallines;
//	00269 
//	00270                 if (con.totallines < numlines)
//	00271                         numlines = con.totallines;
//	00272 
//	00273                 numchars = oldwidth;
//	00274         
//	00275                 if (con.linewidth < numchars)
//	00276                         numchars = con.linewidth;
//	00277 
//	00278                 memcpy (tbuf, con.text, CON_TEXTSIZE);
//	00279                 memset (con.text, ' ', CON_TEXTSIZE);
//	00280 
//	00281                 for (i=0 ; i<numlines ; i++)
//	00282                 {
//	00283                         for (j=0 ; j<numchars ; j++)
//	00284                         {
//	00285                                 con.text[(con.totallines - 1 - i) * con.linewidth + j] =
//	00286                                                 tbuf[((con.current - i + oldtotallines) %
//	00287                                                           oldtotallines) * oldwidth + j];
//	00288                         }
//	00289                 }
//	00290 
//	00291                 Con_ClearNotify ();
//	00292         }
//	00293 
//	00294         con.current = con.totallines - 1;
//	00295         con.display = con.current;
	}
}
