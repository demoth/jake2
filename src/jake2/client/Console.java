/*
 * Con.java
 * Copyright (C) 2003
 * 
 * $Id: Console.java,v 1.11 2004-01-25 13:26:06 hoz Exp $
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
				Menu.ForceMenuOff();
				Cvar.Set("paused", "0");
			}
			else {
				Menu.ForceMenuOff();
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
	
//	00001 /*
//	00002 Copyright (C) 1997-2001 Id Software, Inc.
//	00003 
//	00004 This program is free software; you can redistribute it and/or
//	00005 modify it under the terms of the GNU General Public License
//	00006 as published by the Free Software Foundation; either version 2
//	00007 of the License, or (at your option) any later version.
//	00008 
//	00009 This program is distributed in the hope that it will be useful,
//	00010 but WITHOUT ANY WARRANTY; without even the implied warranty of
//	00011 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  
//	00012 
//	00013 See the GNU General Public License for more details.
//	00014 
//	00015 You should have received a copy of the GNU General Public License
//	00016 along with this program; if not, write to the Free Software
//	00017 Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//	00018 
//	00019 */
//	00020 // console.c
//	00021 
//	00022 #include "client.h"
//	00023 
//	00024 console_t       con;
//	00025 
//	00026 cvar_t          *con_notifytime;
//	00027 
//	00028 
//	00029 #define         MAXCMDLINE      256
//	00030 extern  char    key_lines[32][MAXCMDLINE];
//	00031 extern  int             edit_line;
//	00032 extern  int             key_linepos;
//	00033                 
//	00034 
//	00035 void DrawString (int x, int y, char *s)
//	00036 {
//	00037         while (*s)
//	00038         {
//	00039                 re.DrawChar (x, y, *s);
//	00040                 x+=8;
//	00041                 s++;
//	00042         }
//	00043 }
//	00044 
//	00045 void DrawAltString (int x, int y, char *s)
//	00046 {
//	00047         while (*s)
//	00048         {
//	00049                 re.DrawChar (x, y, *s ^ 0x80);
//	00050                 x+=8;
//	00051                 s++;
//	00052         }
//	00053 }
//	00054 
//	00055 
//	00056 void Key_ClearTyping (void)
//	00057 {
//	00058         key_lines[edit_line][1] = 0;    // clear any typing
//	00059         key_linepos = 1;
//	00060 }
//	00061 
//	00062 /*
//	00063 ================
//	00064 Con_ToggleConsole_f
//	00065 ================
//	00066 */
//	00067 void Con_ToggleConsole_f (void)
//	00068 {
//	00069         SCR_EndLoadingPlaque ();        // get rid of loading plaque
//	00070 
//	00071         if (cl.attractloop)
//	00072         {
//	00073                 Cbuf_AddText ("killserver\n");
//	00074                 return;
//	00075         }
//	00076 
//	00077         if (cls.state == ca_disconnected)
//	00078         {       // start the demo loop again
//	00079                 Cbuf_AddText ("d1\n");
//	00080                 return;
//	00081         }
//	00082 
//	00083         Key_ClearTyping ();
//	00084         Con_ClearNotify ();
//	00085 
//	00086         if (cls.key_dest == key_console)
//	00087         {
//	00088                 M_ForceMenuOff ();
//	00089                 Cvar_Set ("paused", "0");
//	00090         }
//	00091         else
//	00092         {
//	00093                 M_ForceMenuOff ();
//	00094                 cls.key_dest = key_console;     
//	00095 
//	00096                 if (Cvar_VariableValue ("maxclients") == 1 
//	00097                         && Com_ServerState ())
//	00098                         Cvar_Set ("paused", "1");
//	00099         }
//	00100 }
//	00101 
//	00102 /*
//	00103 ================
//	00104 Con_ToggleChat_f
//	00105 ================
//	00106 */
//	00107 void Con_ToggleChat_f (void)
//	00108 {
//	00109         Key_ClearTyping ();
//	00110 
//	00111         if (cls.key_dest == key_console)
//	00112         {
//	00113                 if (cls.state == ca_active)
//	00114                 {
//	00115                         M_ForceMenuOff ();
//	00116                         cls.key_dest = key_game;
//	00117                 }
//	00118         }
//	00119         else
//	00120                 cls.key_dest = key_console;
//	00121         
//	00122         Con_ClearNotify ();
//	00123 }
//	00124 
//	00125 /*
//	00126 ================
//	00127 Con_Clear_f
//	00128 ================
//	00129 */
//	00130 void Con_Clear_f (void)
//	00131 {
//	00132         memset (con.text, ' ', CON_TEXTSIZE);
//	00133 }
//	00134 
//	00135                                                 
//	00136 /*
//	00137 ================
//	00138 Con_Dump_f
//	00139 
//	00140 Save the console contents out to a file
//	00141 ================
//	00142 */
//	00143 void Con_Dump_f (void)
//	00144 {
//	00145         int             l, x;
//	00146         char    *line;
//	00147         FILE    *f;
//	00148         char    buffer[1024];
//	00149         char    name[MAX_OSPATH];
//	00150 
//	00151         if (Cmd_Argc() != 2)
//	00152         {
//	00153                 Com_Printf ("usage: condump <filename>\n");
//	00154                 return;
//	00155         }
//	00156 
//	00157         Com_sprintf (name, sizeof(name), "%s/%s.txt", FS_Gamedir(), Cmd_Argv(1));
//	00158 
//	00159         Com_Printf ("Dumped console text to %s.\n", name);
//	00160         FS_CreatePath (name);
//	00161         f = fopen (name, "w");
//	00162         if (!f)
//	00163         {
//	00164                 Com_Printf ("ERROR: couldn't open.\n");
//	00165                 return;
//	00166         }
//	00167 
//	00168         // skip empty lines
//	00169         for (l = con.current - con.totallines + 1 ; l <= con.current ; l++)
//	00170         {
//	00171                 line = con.text + (l%con.totallines)*con.linewidth;
//	00172                 for (x=0 ; x<con.linewidth ; x++)
//	00173                         if (line[x] != ' ')
//	00174                                 break;
//	00175                 if (x != con.linewidth)
//	00176                         break;
//	00177         }
//	00178 
//	00179         // write the remaining lines
//	00180         buffer[con.linewidth] = 0;
//	00181         for ( ; l <= con.current ; l++)
//	00182         {
//	00183                 line = con.text + (l%con.totallines)*con.linewidth;
//	00184                 strncpy (buffer, line, con.linewidth);
//	00185                 for (x=con.linewidth-1 ; x>=0 ; x--)
//	00186                 {
//	00187                         if (buffer[x] == ' ')
//	00188                                 buffer[x] = 0;
//	00189                         else
//	00190                                 break;
//	00191                 }
//	00192                 for (x=0; buffer[x]; x++)
//	00193                         buffer[x] &= 0x7f;
//	00194 
//	00195                 fprintf (f, "%s\n", buffer);
//	00196         }
//	00197 
//	00198         fclose (f);
//	00199 }
//	00200 
//	00201                                                 
//	00202 /*
//	00203 ================
//	00204 Con_ClearNotify
//	00205 ================
//	00206 */
//	00207 void Con_ClearNotify (void)
//	00208 {
//	00209         int             i;
//	00210         
//	00211         for (i=0 ; i<NUM_CON_TIMES ; i++)
//	00212                 con.times[i] = 0;
//	00213 }
//	00214 
//	00215                                                 
//	00216 /*
//	00217 ================
//	00218 Con_MessageMode_f
//	00219 ================
//	00220 */
//	00221 void Con_MessageMode_f (void)
//	00222 {
//	00223         chat_team = false;
//	00224         cls.key_dest = key_message;
//	00225 }
//	00226 
//	00227 /*
//	00228 ================
//	00229 Con_MessageMode2_f
//	00230 ================
//	00231 */
//	00232 void Con_MessageMode2_f (void)
//	00233 {
//	00234         chat_team = true;
//	00235         cls.key_dest = key_message;
//	00236 }
//	00237 
//	00238 /*
//	00239 ================
//	00240 Con_CheckResize
//	00241 
//	00242 If the line width has changed, reformat the buffer.
//	00243 ================
//	00244 */
//	00245 void Con_CheckResize (void)
//	00246 {
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
//	00296 }
//	00297 
//	00298 
//	00299 /*
//	00300 ================
//	00301 Con_Init
//	00302 ================
//	00303 */
//	00304 void Con_Init (void)
//	00305 {
//	00306         con.linewidth = -1;
//	00307 
//	00308         Con_CheckResize ();
//	00309         
//	00310         Com_Printf ("Console initialized.\n");
//	00311 
//	00312 //
//	00313 // register our commands
//	00314 //
//	00315         con_notifytime = Cvar_Get ("con_notifytime", "3", 0);
//	00316 
//	00317         Cmd_AddCommand ("toggleconsole", Con_ToggleConsole_f);
//	00318         Cmd_AddCommand ("togglechat", Con_ToggleChat_f);
//	00319         Cmd_AddCommand ("messagemode", Con_MessageMode_f);
//	00320         Cmd_AddCommand ("messagemode2", Con_MessageMode2_f);
//	00321         Cmd_AddCommand ("clear", Con_Clear_f);
//	00322         Cmd_AddCommand ("condump", Con_Dump_f);
//	00323         con.initialized = true;
//	00324 }
//	00325 
//	00326 
//	00327 /*
//	00328 ===============
//	00329 Con_Linefeed
//	00330 ===============
//	00331 */
//	00332 void Con_Linefeed (void)
//	00333 {
//	00334         con.x = 0;
//	00335         if (con.display == con.current)
//	00336                 con.display++;
//	00337         con.current++;
//	00338         memset (&con.text[(con.current%con.totallines)*con.linewidth]
//	00339         , ' ', con.linewidth);
//	00340 }
//	00341 
//	00342 /*
//	00343 ================
//	00344 Con_Print
//	00345 
//	00346 Handles cursor positioning, line wrapping, etc
//	00347 All console printing must go through this in order to be logged to disk
//	00348 If no console is visible, the text will appear at the top of the game window
//	00349 ================
//	00350 */
//	00351 void Con_Print (char *txt)
//	00352 {
//	00353         int             y;
//	00354         int             c, l;
//	00355         static int      cr;
//	00356         int             mask;
//	00357 
//	00358         if (!con.initialized)
//	00359                 return;
//	00360 
//	00361         if (txt[0] == 1 || txt[0] == 2)
//	00362         {
//	00363                 mask = 128;             // go to colored text
//	00364                 txt++;
//	00365         }
//	00366         else
//	00367                 mask = 0;
//	00368 
//	00369 
//	00370         while ( (c = *txt) )
//	00371         {
//	00372         // count word length
//	00373                 for (l=0 ; l< con.linewidth ; l++)
//	00374                         if ( txt[l] <= ' ')
//	00375                                 break;
//	00376 
//	00377         // word wrap
//	00378                 if (l != con.linewidth && (con.x + l > con.linewidth) )
//	00379                         con.x = 0;
//	00380 
//	00381                 txt++;
//	00382 
//	00383                 if (cr)
//	00384                 {
//	00385                         con.current--;
//	00386                         cr = false;
//	00387                 }
//	00388 
//	00389                 
//	00390                 if (!con.x)
//	00391                 {
//	00392                         Con_Linefeed ();
//	00393                 // mark time for transparent overlay
//	00394                         if (con.current >= 0)
//	00395                                 con.times[con.current % NUM_CON_TIMES] = cls.realtime;
//	00396                 }
//	00397 
//	00398                 switch (c)
//	00399                 {
//	00400                 case '\n':
//	00401                         con.x = 0;
//	00402                         break;
//	00403 
//	00404                 case '\r':
//	00405                         con.x = 0;
//	00406                         cr = 1;
//	00407                         break;
//	00408 
//	00409                 default:        // display character and advance
//	00410                         y = con.current % con.totallines;
//	00411                         con.text[y*con.linewidth+con.x] = c | mask | con.ormask;
//	00412                         con.x++;
//	00413                         if (con.x >= con.linewidth)
//	00414                                 con.x = 0;
//	00415                         break;
//	00416                 }
//	00417                 
//	00418         }
//	00419 }
//	00420 
//	00421 
//	00422 /*
//	00423 ==============
//	00424 Con_CenteredPrint
//	00425 ==============
//	00426 */
//	00427 void Con_CenteredPrint (char *text)
//	00428 {
//	00429         int             l;
//	00430         char    buffer[1024];
//	00431 
//	00432         l = strlen(text);
//	00433         l = (con.linewidth-l)/2;
//	00434         if (l < 0)
//	00435                 l = 0;
//	00436         memset (buffer, ' ', l);
//	00437         strcpy (buffer+l, text);
//	00438         strcat (buffer, "\n");
//	00439         Con_Print (buffer);
//	00440 }
//	00441 
//	00442 /*
//	00443 ==============================================================================
//	00444 
//	00445 DRAWING
//	00446 
//	00447 ==============================================================================
//	00448 */
//	00449 
//	00450 
//	00451 /*
//	00452 ================
//	00453 Con_DrawInput
//	00454 
//	00455 The input line scrolls horizontally if typing goes beyond the right edge
//	00456 ================
//	00457 */
//	00458 void Con_DrawInput (void)
//	00459 {
//	00460         int             y;
//	00461         int             i;
//	00462         char    *text;
//	00463 
//	00464         if (cls.key_dest == key_menu)
//	00465                 return;
//	00466         if (cls.key_dest != key_console && cls.state == ca_active)
//	00467                 return;         // don't draw anything (always draw if not active)
//	00468 
//	00469         text = key_lines[edit_line];
//	00470         
//	00471 // add the cursor frame
//	00472         text[key_linepos] = 10+((int)(cls.realtime>>8)&1);
//	00473         
//	00474 // fill out remainder with spaces
//	00475         for (i=key_linepos+1 ; i< con.linewidth ; i++)
//	00476                 text[i] = ' ';
//	00477                 
//	00478 //      prestep if horizontally scrolling
//	00479         if (key_linepos >= con.linewidth)
//	00480                 text += 1 + key_linepos - con.linewidth;
//	00481                 
//	00482 // draw it
//	00483         y = con.vislines-16;
//	00484 
//	00485         for (i=0 ; i<con.linewidth ; i++)
//	00486                 re.DrawChar ( (i+1)<<3, con.vislines - 22, text[i]);
//	00487 
//	00488 // remove cursor
//	00489         key_lines[edit_line][key_linepos] = 0;
//	00490 }
//	00491 
//	00492 
//	00493 /*
//	00494 ================
//	00495 Con_DrawNotify
//	00496 
//	00497 Draws the last few lines of output transparently over the game top
//	00498 ================
//	00499 */
//	00500 void Con_DrawNotify (void)
//	00501 {
//	00502         int             x, v;
//	00503         char    *text;
//	00504         int             i;
//	00505         int             time;
//	00506         char    *s;
//	00507         int             skip;
//	00508 
//	00509         v = 0;
//	00510         for (i= con.current-NUM_CON_TIMES+1 ; i<=con.current ; i++)
//	00511         {
//	00512                 if (i < 0)
//	00513                         continue;
//	00514                 time = con.times[i % NUM_CON_TIMES];
//	00515                 if (time == 0)
//	00516                         continue;
//	00517                 time = cls.realtime - time;
//	00518                 if (time > con_notifytime->value*1000)
//	00519                         continue;
//	00520                 text = con.text + (i % con.totallines)*con.linewidth;
//	00521                 
//	00522                 for (x = 0 ; x < con.linewidth ; x++)
//	00523                         re.DrawChar ( (x+1)<<3, v, text[x]);
//	00524 
//	00525                 v += 8;
//	00526         }
//	00527 
//	00528 
//	00529         if (cls.key_dest == key_message)
//	00530         {
//	00531                 if (chat_team)
//	00532                 {
//	00533                         DrawString (8, v, "say_team:");
//	00534                         skip = 11;
//	00535                 }
//	00536                 else
//	00537                 {
//	00538                         DrawString (8, v, "say:");
//	00539                         skip = 5;
//	00540                 }
//	00541 
//	00542                 s = chat_buffer;
//	00543                 if (chat_bufferlen > (viddef.width>>3)-(skip+1))
//	00544                         s += chat_bufferlen - ((viddef.width>>3)-(skip+1));
//	00545                 x = 0;
//	00546                 while(s[x])
//	00547                 {
//	00548                         re.DrawChar ( (x+skip)<<3, v, s[x]);
//	00549                         x++;
//	00550                 }
//	00551                 re.DrawChar ( (x+skip)<<3, v, 10+((cls.realtime>>8)&1));
//	00552                 v += 8;
//	00553         }
//	00554         
//	00555         if (v)
//	00556         {
//	00557                 SCR_AddDirtyPoint (0,0);
//	00558                 SCR_AddDirtyPoint (viddef.width-1, v);
//	00559         }
//	00560 }
//	00561 
//	00562 /*
//	00563 ================
//	00564 Con_DrawConsole
//	00565 
//	00566 Draws the console with the solid background
//	00567 ================
//	00568 */
//	00569 void Con_DrawConsole (float frac)
//	00570 {
//	00571         int                             i, j, x, y, n;
//	00572         int                             rows;
//	00573         char                    *text;
//	00574         int                             row;
//	00575         int                             lines;
//	00576         char                    version[64];
//	00577         char                    dlbar[1024];
//	00578 
//	00579         lines = viddef.height * frac;
//	00580         if (lines <= 0)
//	00581                 return;
//	00582 
//	00583         if (lines > viddef.height)
//	00584                 lines = viddef.height;
//	00585 
//	00586 // draw the background
//	00587         re.DrawStretchPic (0, -viddef.height+lines, viddef.width, viddef.height, "conback");
//	00588         SCR_AddDirtyPoint (0,0);
//	00589         SCR_AddDirtyPoint (viddef.width-1,lines-1);
//	00590 
//	00591         Com_sprintf (version, sizeof(version), "v%4.2f", VERSION);
//	00592         for (x=0 ; x<5 ; x++)
//	00593                 re.DrawChar (viddef.width-44+x*8, lines-12, 128 + version[x] );
//	00594 
//	00595 // draw the text
//	00596         con.vislines = lines;
//	00597         
//	00598 #if 0
//	00599         rows = (lines-8)>>3;            // rows of text to draw
//	00600 
//	00601         y = lines - 24;
//	00602 #else
//	00603         rows = (lines-22)>>3;           // rows of text to draw
//	00604 
//	00605         y = lines - 30;
//	00606 #endif
//	00607 
//	00608 // draw from the bottom up
//	00609         if (con.display != con.current)
//	00610         {
//	00611         // draw arrows to show the buffer is backscrolled
//	00612                 for (x=0 ; x<con.linewidth ; x+=4)
//	00613                         re.DrawChar ( (x+1)<<3, y, '^');
//	00614         
//	00615                 y -= 8;
//	00616                 rows--;
//	00617         }
//	00618         
//	00619         row = con.display;
//	00620         for (i=0 ; i<rows ; i++, y-=8, row--)
//	00621         {
//	00622                 if (row < 0)
//	00623                         break;
//	00624                 if (con.current - row >= con.totallines)
//	00625                         break;          // past scrollback wrap point
//	00626                         
//	00627                 text = con.text + (row % con.totallines)*con.linewidth;
//	00628 
//	00629                 for (x=0 ; x<con.linewidth ; x++)
//	00630                         re.DrawChar ( (x+1)<<3, y, text[x]);
//	00631         }
//	00632 
//	00633 //ZOID
//	00634         // draw the download bar
//	00635         // figure out width
//	00636         if (cls.download) {
//	00637                 if ((text = strrchr(cls.downloadname, '/')) != NULL)
//	00638                         text++;
//	00639                 else
//	00640                         text = cls.downloadname;
//	00641 
//	00642                 x = con.linewidth - ((con.linewidth * 7) / 40);
//	00643                 y = x - strlen(text) - 8;
//	00644                 i = con.linewidth/3;
//	00645                 if (strlen(text) > i) {
//	00646                         y = x - i - 11;
//	00647                         strncpy(dlbar, text, i);
//	00648                         dlbar[i] = 0;
//	00649                         strcat(dlbar, "...");
//	00650                 } else
//	00651                         strcpy(dlbar, text);
//	00652                 strcat(dlbar, ": ");
//	00653                 i = strlen(dlbar);
//	00654                 dlbar[i++] = '\x80';
//	00655                 // where's the dot go?
//	00656                 if (cls.downloadpercent == 0)
//	00657                         n = 0;
//	00658                 else
//	00659                         n = y * cls.downloadpercent / 100;
//	00660                         
//	00661                 for (j = 0; j < y; j++)
//	00662                         if (j == n)
//	00663                                 dlbar[i++] = '\x83';
//	00664                         else
//	00665                                 dlbar[i++] = '\x81';
//	00666                 dlbar[i++] = '\x82';
//	00667                 dlbar[i] = 0;
//	00668 
//	00669                 sprintf(dlbar + strlen(dlbar), " %02d%%", cls.downloadpercent);
//	00670 
//	00671                 // draw it
//	00672                 y = con.vislines-12;
//	00673                 for (i = 0; i < strlen(dlbar); i++)
//	00674                         re.DrawChar ( (i+1)<<3, y, dlbar[i]);
//	00675         }
//	00676 //ZOID
//	00677 
//	00678 // draw the input prompt, user text, and cursor if desired
//	00679         Con_DrawInput ();
//	00680 }	
}
