/*
 * Con.java
 * Copyright (C) 2003
 * 
 * $Id: Console.java,v 1.10 2004-01-02 14:20:06 hoz Exp $
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
	
	public static xcommand_t ToggleConsole_f = new xcommand_t() {
		public void execute() {
			SCR.EndLoadingPlaque();        // get rid of loading plaque
 
			if (Globals.cl.attractloop) {
				Cbuf.AddText("killserver\n");
				return;
			}
 
			if (Globals.cls.state == Defines.ca_disconnected) {
				// start the demo loop again
				Cbuf.AddText("d1\n");
				return;
			}

			Key.ClearTyping(); 
			Console.ClearNotify();

			if (Globals.cls.key_dest == Defines.key_console) {
				M.ForceMenuOff();
				Cvar.Set("paused", "0");
			}
			else {
				M.ForceMenuOff();
				Globals.cls.key_dest = Defines.key_console;     

				if (Cvar.VariableValue("maxclients") == 1 && Com.ServerState()!= 0)
					Cvar.Set("paused", "1");
			}
		}
	};
	public static xcommand_t ToggleChat_f = new xcommand_t() {
		public void execute() {
		}
	};
	public static xcommand_t MessageMode_f = new xcommand_t() {
		public void execute() {
		}
	};
	public static xcommand_t MessageMode2_f = new xcommand_t() {
		public void execute() {
		}
	};
	public static xcommand_t Clear_f = new xcommand_t() {
		public void execute() {
			Arrays.fill(Globals.con.text, (byte)' ');
		}
	};
	public static xcommand_t Dump_f = new xcommand_t() {
		public void execute() {
//			00136 /*
//			00137 ================
//			00138 Con_Dump_f
//			00139 
//			00140 Save the console contents out to a file
//			00141 ================
//			00142 */
//			00143 void Con_Dump_f (void)
//			00144 {
//			00145         int             l, x;
//			00146         char    *line;
//			00147         FILE    *f;
//			00148         char    buffer[1024];
//			00149         char    name[MAX_OSPATH];
//			00150 
//			00151         if (Cmd_Argc() != 2)
//			00152         {
//			00153                 Com_Printf ("usage: condump <filename>\n");
//			00154                 return;
//			00155         }
//			00156 
//			00157         Com_sprintf (name, sizeof(name), "%s/%s.txt", FS_Gamedir(), Cmd_Argv(1));
//			00158 
//			00159         Com_Printf ("Dumped console text to %s.\n", name);
//			00160         FS_CreatePath (name);
//			00161         f = fopen (name, "w");
//			00162         if (!f)
//			00163         {
//			00164                 Com_Printf ("ERROR: couldn't open.\n");
//			00165                 return;
//			00166         }
//			00167 
//			00168         // skip empty lines
//			00169         for (l = con.current - con.totallines + 1 ; l <= con.current ; l++)
//			00170         {
//			00171                 line = con.text + (l%con.totallines)*con.linewidth;
//			00172                 for (x=0 ; x<con.linewidth ; x++)
//			00173                         if (line[x] != ' ')
//			00174                                 break;
//			00175                 if (x != con.linewidth)
//			00176                         break;
//			00177         }
//			00178 
//			00179         // write the remaining lines
//			00180         buffer[con.linewidth] = 0;
//			00181         for ( ; l <= con.current ; l++)
//			00182         {
//			00183                 line = con.text + (l%con.totallines)*con.linewidth;
//			00184                 strncpy (buffer, line, con.linewidth);
//			00185                 for (x=con.linewidth-1 ; x>=0 ; x--)
//			00186                 {
//			00187                         if (buffer[x] == ' ')
//			00188                                 buffer[x] = 0;
//			00189                         else
//			00190                                 break;
//			00191                 }
//			00192                 for (x=0; buffer[x]; x++)
//			00193                         buffer[x] &= 0x7f;
//			00194 
//			00195                 fprintf (f, "%s\n", buffer);
//			00196         }
//			00197 
//			00198         fclose (f);
//			00199 }			
		}
	};
	
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
