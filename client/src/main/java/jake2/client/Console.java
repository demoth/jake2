/*
 * Con.java
 * Copyright (C) 2003
 * 
 * $Id: Console.java,v 1.10 2011-07-07 21:10:18 salomo Exp $
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

import jake2.qcommon.Com;
import jake2.qcommon.Defines;
import jake2.qcommon.Globals;
import jake2.qcommon.ServerStates;
import jake2.qcommon.exec.Cbuf;
import jake2.qcommon.exec.Cmd;
import jake2.qcommon.exec.Command;
import jake2.qcommon.exec.Cvar;
import jake2.qcommon.filesystem.FS;
import jake2.qcommon.util.Lib;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.List;

/**
 * Console related functions, todo: merge with console_t
 */
public final class Console extends Globals {
	// ---------
	// console.h
	public static final int NUM_CON_TIMES = 4;
	public static final int CON_TEXTSIZE = 32768;
	// size of the console character on the screen in px
	public static final int CHAR_SIZE_PX = 8;
	// conchars.pcx is 16 by 16
	public static final int CON_CHAR_GRID_SIZE = 16;
	static final int CONSOLE_PADDING = CHAR_SIZE_PX;
	static final char CARET_BLOCK = 11;



	static Command ToggleConsole_f = (List<String> args) -> {
		SCR.EndLoadingPlaque(); // get rid of loading plaque

		if (ClientGlobals.cl.attractloop) {
		Cbuf.AddText("killserver\n");
		return;
		}

		if (ClientGlobals.cls.state == Defines.ca_disconnected) {
		// start the demo loop again
			// todo: intro
		//Cbuf.AddText("d1\n");
		return;
		}

		Key.ClearTyping();
		Console.ClearNotify();

		if (ClientGlobals.cls.key_dest == Defines.key_console) {
		Menu.ForceMenuOff();
		Cvar.getInstance().Set("paused", "0");
		} else {
		Menu.ForceMenuOff();
		ClientGlobals.cls.key_dest = Defines.key_console;

		if (Cvar.getInstance().VariableValue("maxclients") == 1
			&& Globals.server_state != ServerStates.SS_DEAD)
			Cvar.getInstance().Set("paused", "1");
		}
	};

    private static Command Clear_f = (List<String> args) -> Arrays.fill(ClientGlobals.con.text, (byte) ' ');

	/**
	 * Dump console contents to file (file name as a 1st argument)
	 */
	private static Command Dump_f = (List<String> args) -> {

		int l, x;
		int line;
		RandomAccessFile f;
		byte[] buffer = new byte[1024];
		String name;

		if (args.size() != 2) {
			Com.Printf("usage: condump <filename>\n");
			return;
		}

		// Com_sprintf (name, sizeof(name), "%s/%s.txt", FS_Gamedir(),
		// Cmd_Argv(1));
		name = FS.getWriteDir() + "/" + args.get(1) + ".txt";

		Com.Printf("Dumped console text to " + name + ".\n");
		FS.CreatePath(name);
		f = Lib.fopen(name, "rw");
		if (f == null) {
			Com.Printf("ERROR: couldn't open.\n");
			return;
		}

		// skip empty lines
		for (l = ClientGlobals.con.current - ClientGlobals.con.totallines + 1; l <= ClientGlobals.con.current; l++) {
			line = (l % ClientGlobals.con.totallines) * ClientGlobals.con.linewidth;
			for (x = 0; x < ClientGlobals.con.linewidth; x++)
				if (ClientGlobals.con.text[line + x] != ' ')
					break;
			if (x != ClientGlobals.con.linewidth)
				break;
		}

		// write the remaining lines
		buffer[ClientGlobals.con.linewidth] = 0;
		for (; l <= ClientGlobals.con.current; l++) {
			line = (l % ClientGlobals.con.totallines) * ClientGlobals.con.linewidth;
			// strncpy (buffer, line, con.linewidth);
			System.arraycopy(ClientGlobals.con.text, line, buffer, 0, ClientGlobals.con.linewidth);
			for (x = ClientGlobals.con.linewidth - 1; x >= 0; x--) {
				if (buffer[x] == ' ')
					buffer[x] = 0;
				else
					break;
			}
			for (x = 0; buffer[x] != 0; x++)
				buffer[x] &= 0x7f;

			buffer[x] = '\n';
			// fprintf (f, "%s\n", buffer);
			try {
				f.write(buffer, 0, x + 1);
			} catch (IOException e) {
			}
		}

		Lib.fclose(f);

	};

	/*
	 * ================ Con_ToggleChat_f ================
	 */
	private static Command ToggleChat_f = (List<String> args) -> {
		Key.ClearTyping();

		if (ClientGlobals.cls.key_dest == key_console) {
			if (ClientGlobals.cls.state == ca_active) {
				Menu.ForceMenuOff();
				ClientGlobals.cls.key_dest = key_game;
			}
		} else
			ClientGlobals.cls.key_dest = key_console;

		ClearNotify();
	};

	/*
	 * ================ Con_MessageMode_f ================
	 */
	private static Command MessageMode_f = (List<String> args) -> {
		ClientGlobals.chat_team = false;
		ClientGlobals.cls.key_dest = key_message;
	};

	/*
	 * ================ Con_MessageMode2_f ================
	 */
	private static Command MessageMode2_f = (List<String> args) -> {
		ClientGlobals.chat_team = true;
		ClientGlobals.cls.key_dest = key_message;
	};


	public static void Init() {
	ClientGlobals.con.linewidth = -1;
	ClientGlobals.con.backedit = 0;

	CheckResize();


	ClientGlobals.con_notifytime = Cvar.getInstance().Get("con_notifytime", "3", 0);

		//
		// register our commands
		//
	Cmd.AddCommand("toggleconsole", ToggleConsole_f);
	Cmd.AddCommand("togglechat", ToggleChat_f);
	Cmd.AddCommand("messagemode", MessageMode_f);
	Cmd.AddCommand("messagemode2", MessageMode2_f);
	Cmd.AddCommand("clear", Clear_f);
	Cmd.AddCommand("condump", Dump_f);
	Cmd.AddCommand("console_print", args -> Console.Print(args.get(0)));
	ClientGlobals.con.initialized = true;
		Com.Printf("Console initialized.\n");

	}

    /**
     * If the line width has changed, reformat the buffer.
     */
	static void CheckResize() {

		// console width in chars
		int width = (ClientGlobals.viddef.getWidth() * CHAR_SIZE_PX) - 2; // 2 = padding
		if (width > Defines.MAXCMDLINE)
			width = Defines.MAXCMDLINE;

		if (width == ClientGlobals.con.linewidth)
			return;

		if (width < 1) { // video hasn't been initialized yet
			width = 38;
			ClientGlobals.con.linewidth = width;
			ClientGlobals.con.backedit = 0; // sfranzyshen
			ClientGlobals.con.totallines = CON_TEXTSIZE / ClientGlobals.con.linewidth;
			Arrays.fill(ClientGlobals.con.text, (byte) ' ');
		} else {
			int oldwidth = ClientGlobals.con.linewidth;
			ClientGlobals.con.linewidth = width;
			ClientGlobals.con.backedit = 0; // sfranzyshen
			int oldtotallines = ClientGlobals.con.totallines;
			ClientGlobals.con.totallines = CON_TEXTSIZE / ClientGlobals.con.linewidth;
			int numlines = oldtotallines;

			if (ClientGlobals.con.totallines < numlines)
				numlines = ClientGlobals.con.totallines;

			int numchars = oldwidth;

			if (ClientGlobals.con.linewidth < numchars)
				numchars = ClientGlobals.con.linewidth;

			byte[] tbuf = new byte[CON_TEXTSIZE];
			System.arraycopy(ClientGlobals.con.text, 0, tbuf, 0, CON_TEXTSIZE);
			Arrays.fill(ClientGlobals.con.text, (byte) ' ');

			for (int i = 0; i < numlines; i++) {
                if (numchars >= 0)
					System.arraycopy(
							tbuf,
							((ClientGlobals.con.current - i + oldtotallines) % oldtotallines) * oldwidth,
							ClientGlobals.con.text,
							(ClientGlobals.con.totallines - 1 - i) * ClientGlobals.con.linewidth,
							numchars);
			}

			Console.ClearNotify();
		}

		ClientGlobals.con.current = ClientGlobals.con.totallines - 1;
		ClientGlobals.con.display = ClientGlobals.con.current;
	}

	static void ClearNotify() {
		for (int i = 0; i < NUM_CON_TIMES; i++) {
            ClientGlobals.con.times[i] = 0;
        }
	}

    static void DrawString(int x, int y, String s) {
	for (int i = 0; i < s.length(); i++) {
	    ClientGlobals.re.DrawChar(x, y, s.charAt(i));
	    x += Console.CHAR_SIZE_PX;
	}
    }

    static void DrawAltString(int x, int y, String s) {
	for (int i = 0; i < s.length(); i++) {
	    ClientGlobals.re.DrawChar(x, y, s.charAt(i) ^ 0x80);
	    x += Console.CHAR_SIZE_PX;
	}
    }

    /*
     * =============== Con_Linefeed ===============
     */
    private static void Linefeed() {
	ClientGlobals.con.x = 0;
	if (ClientGlobals.con.display == ClientGlobals.con.current)
	    ClientGlobals.con.display++;
	ClientGlobals.con.current++;
	int i = (ClientGlobals.con.current % ClientGlobals.con.totallines)
		* ClientGlobals.con.linewidth;
	int e = i + ClientGlobals.con.linewidth;
	while (i < ClientGlobals.con.text.length - 1 && i++ < e)
	    ClientGlobals.con.text[i] = (byte) ' ';
    }

    /*
     * ================ Con_Print
     * 
     * Handles cursor positioning, line wrapping, etc All console printing must
     * go through this in order to be logged to disk If no console is visible,
     * the text will appear at the top of the game window ================
     */
    private static int cr;

    public static void Print(String txt) {
	int y;
	int c, l;
	int mask;
	int txtpos = 0;

	if (!ClientGlobals.con.initialized)
	    return;

	if (txt.charAt(0) == 1 || txt.charAt(0) == 2) {
	    mask = 128; // go to colored text
	    txtpos++;
	} else
	    mask = 0;

	while (txtpos < txt.length()) {
	    c = txt.charAt(txtpos);
	    // count word length
	    for (l = 0; l < ClientGlobals.con.linewidth && l < (txt.length() - txtpos); l++)
		if (txt.charAt(l + txtpos) <= ' ')
		    break;

	    // word wrap
	    if (l != ClientGlobals.con.linewidth && (ClientGlobals.con.x + l > ClientGlobals.con.linewidth))
		ClientGlobals.con.x = 0;

	    txtpos++;

	    if (cr != 0) {
		ClientGlobals.con.current--;
		cr = 0;
	    }

	    if (ClientGlobals.con.x == 0) {
		Console.Linefeed();
		// mark time for transparent overlay
		if (ClientGlobals.con.current >= 0)
		    ClientGlobals.con.times[ClientGlobals.con.current % NUM_CON_TIMES] = ClientGlobals.cls.realtime;
	    }

	    switch (c) {
	    case '\n':
		ClientGlobals.con.x = 0;
		break;

	    case '\r':
		ClientGlobals.con.x = 0;
		cr = 1;
		break;

	    default: // display character and advance
		y = ClientGlobals.con.current % ClientGlobals.con.totallines;
		ClientGlobals.con.text[y * ClientGlobals.con.linewidth + ClientGlobals.con.x] = (byte) (c | mask | ClientGlobals.con.ormask);
		ClientGlobals.con.x++;
		if (ClientGlobals.con.x >= ClientGlobals.con.linewidth)
		    ClientGlobals.con.x = 0;
		break;
	    }
	}
    }

    /*
     * ================ Con_DrawInput
     * 
     * The input line scrolls horizontally if typing goes beyond the right edge
     * ================
     */
	private static void DrawInput() {

        if (ClientGlobals.cls.key_dest == key_menu)
			return;

		if (ClientGlobals.cls.key_dest != key_console && ClientGlobals.cls.state == ca_active)
			return; // don't draw anything (always draw if not active)

        byte[] text = ClientGlobals.key_lines[ClientGlobals.edit_line];

		// fill out remainder with spaces, fixme: use strings instead of arrays
        for (int i = ClientGlobals.key_linepos; i < ClientGlobals.con.linewidth; i++) // sfranzyshen
			text[i] = ' ';
		/* prestep if horizontally scrolling */
		int start = 0;
		if (ClientGlobals.key_linepos >= ClientGlobals.con.linewidth) {
//			text += 1 + key_linepos - con.linewidth;
			start += 1 + ClientGlobals.key_linepos - ClientGlobals.con.linewidth;
		}

		// draw it
		// sfranzyshen --start
		for (int i = start; i < ClientGlobals.con.linewidth; i++) {
			final int character;
			if (ClientGlobals.con.backedit == ClientGlobals.key_linepos - i && (((ClientGlobals.cls.realtime >> 8) & 1) != 0)) // blink
				character = CARET_BLOCK;
			else
				character = text[i];

			// one CHAR_SIZE_PX higher to fit the version
			ClientGlobals.re.DrawChar((i + 1) * CHAR_SIZE_PX, ClientGlobals.con.vislines - CHAR_SIZE_PX - CONSOLE_PADDING, character);
		}
		// sfranzyshen - stop


		// remove cursor
		ClientGlobals.key_lines[ClientGlobals.edit_line][ClientGlobals.key_linepos] = 0;
	}

    /*
     * ================ Con_DrawNotify
     * 
     * Draws the last few lines of output transparently over the game top
     * ================
     */
	static void DrawNotify() {
		int skip;

		int y = 0;
		int x;
		// draw NUM_CON_TIMES last lines from console
		for (int i = ClientGlobals.con.current - NUM_CON_TIMES + 1; i <= ClientGlobals.con.current; i++) {
			if (i < 0)
				continue;

			int time = (int) ClientGlobals.con.times[i % NUM_CON_TIMES];
			if (time == 0)
				continue;

			time = ClientGlobals.cls.realtime - time;
			if (time > ClientGlobals.con_notifytime.value * 1000)
				continue;

			int text = (i % ClientGlobals.con.totallines) * ClientGlobals.con.linewidth;

			for (x = 0; x < ClientGlobals.con.linewidth; x++)
				ClientGlobals.re.DrawChar((x + 1) * CHAR_SIZE_PX, y, ClientGlobals.con.text[text + x]);

			y += Console.CHAR_SIZE_PX;
		}

		if (ClientGlobals.cls.key_dest == key_message) {
			if (ClientGlobals.chat_team) {
				DrawString(Console.CHAR_SIZE_PX, y, "say_team:");
				skip = 11;
			} else {
				DrawString(Console.CHAR_SIZE_PX, y, "say:");
				skip = 5;
			}

			String chatBuffer = ClientGlobals.chat_buffer;
			if (ClientGlobals.chat_bufferlen > (ClientGlobals.viddef.getWidth() / CHAR_SIZE_PX) - (skip + 1))
				// sfranzyshen -start
				chatBuffer = chatBuffer.substring(ClientGlobals.chat_bufferlen - ((ClientGlobals.viddef.getWidth() / CHAR_SIZE_PX) - (skip + 1)));

			for (x = 0; x < chatBuffer.length(); x++) {
				if (ClientGlobals.chat_backedit > 0 && ClientGlobals.chat_backedit == ClientGlobals.chat_buffer.length() - x && ((ClientGlobals.cls.realtime >> 8) & 1) != 0) {
					ClientGlobals.re.DrawChar((x + skip) * CHAR_SIZE_PX, y, (char) 11);
				} else {
					ClientGlobals.re.DrawChar((x + skip) * CHAR_SIZE_PX, y, chatBuffer.charAt(x));
				}
			}

			if (ClientGlobals.chat_backedit == 0) {
				ClientGlobals.re.DrawChar(
						(x + skip) * CHAR_SIZE_PX,
						y,
						10 + ((ClientGlobals.cls.realtime >> 8) & 1)); // blinking cursor
            }
			// sfranzyshen -stop        

			y += CHAR_SIZE_PX;
		}

		if (y != 0) {
			SCR.AddDirtyPoint(0, 0);
			SCR.AddDirtyPoint(ClientGlobals.viddef.getWidth() - 1, y);
		}
	}

    /**
     * Draws the console text on the solid background.
	 * @param frac - percentage of screen to occupy by console, (from 0 to 1)
     */
	static void DrawConsole(float frac) {



		int screenWidth = ClientGlobals.viddef.getWidth();
		int screenHeight = ClientGlobals.viddef.getHeight();
		int consoleHeight = (int) (screenHeight * frac);
		if (consoleHeight <= 0)
			return;

		if (consoleHeight > screenHeight)
			consoleHeight = screenHeight;

		// draw the background
		ClientGlobals.re.DrawStretchPic(0, -screenHeight + consoleHeight, screenWidth, screenHeight, "conback");
		SCR.AddDirtyPoint(0, 0);
		SCR.AddDirtyPoint(screenWidth - 1, consoleHeight - 1);

		// draw version string at the very bottom right corner of the console
		String version = "v" + VERSION + ", jvm:" + Runtime.version();
		for (int x = 0; x < version.length(); x++) {
			// no padding to avoid overlapping with the input
            ClientGlobals.re.DrawChar(screenWidth - (version.length() - x) * Console.CHAR_SIZE_PX,
					consoleHeight - (CHAR_SIZE_PX),
					128 + version.charAt(x) // Alt char
			);
        }

		// draw the text
		ClientGlobals.con.vislines = consoleHeight;

		int rows = (consoleHeight - 2 * CHAR_SIZE_PX - CONSOLE_PADDING) / CHAR_SIZE_PX; // rows of text to draw
		int y = consoleHeight - 2 * CHAR_SIZE_PX - CONSOLE_PADDING;

		// draw from the bottom up
		if (ClientGlobals.con.display != ClientGlobals.con.current) {
			// draw arrows to show the buffer is backscrolled
			for (int x = 0; x < ClientGlobals.con.linewidth; x += 4)
				ClientGlobals.re.DrawChar((x + 1) * CHAR_SIZE_PX, y, '^');

			y -= Console.CHAR_SIZE_PX;
			rows--;
		}

		int i, j, x, n;

		int row = ClientGlobals.con.display;
		for (i = 0; i < rows; i++, y -= Console.CHAR_SIZE_PX, row--) {
			if (row < 0)
				break;
			if (ClientGlobals.con.current - row >= ClientGlobals.con.totallines)
				break; // past scrollback wrap point

			int first = (row % ClientGlobals.con.totallines) * ClientGlobals.con.linewidth;

			for (x = 0; x < ClientGlobals.con.linewidth; x++)
				ClientGlobals.re.DrawChar((x + 1) * CHAR_SIZE_PX, y, ClientGlobals.con.text[x + first]);
		}

		// ZOID
		// draw the download bar
		// figure out width
		if (ClientGlobals.cls.download != null) {
			int text;
			if ((text = ClientGlobals.cls.downloadname.lastIndexOf('/')) != 0)
				text++;
			else
				text = 0;

			x = ClientGlobals.con.linewidth - ((ClientGlobals.con.linewidth * 7) / 40);
			y = x - (ClientGlobals.cls.downloadname.length() - text) - 8;
			i = ClientGlobals.con.linewidth / 3;
			StringBuffer dlbar = new StringBuffer(512);
			if (ClientGlobals.cls.downloadname.length() - text > i) {
				y = x - i - 11;
				int end = text + i - 1;
				;
				dlbar.append(ClientGlobals.cls.downloadname.substring(text, end));
				dlbar.append("...");
			} else {
				dlbar.append(ClientGlobals.cls.downloadname.substring(text));
			}
			dlbar.append(": ");
			dlbar.append((char) 0x80);

			// where's the dot go?
			if (ClientGlobals.cls.downloadpercent == 0)
				n = 0;
			else
				n = y * ClientGlobals.cls.downloadpercent / 100;

			for (j = 0; j < y; j++) {
				if (j == n)
					dlbar.append((char) 0x83);
				else
					dlbar.append((char) 0x81);
			}
			dlbar.append((char) 0x82);
			dlbar.append((ClientGlobals.cls.downloadpercent < 10) ? " 0" : " ");
			dlbar.append(ClientGlobals.cls.downloadpercent).append('%');
			// draw it
			y = ClientGlobals.con.vislines - 12;
			for (i = 0; i < dlbar.length(); i++)
				ClientGlobals.re.DrawChar((i + 1) * CHAR_SIZE_PX, y, dlbar.charAt(i));
		}
		// ZOID

		// draw the input prompt, user text, and cursor if desired
		DrawInput();
	}
}