/*
 * Com.java
 * Copyright (C) 2003
 * 
 * $Id: Com.java,v 1.29 2004-01-31 21:54:11 rst Exp $
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
package jake2.qcommon;

import jake2.Defines;
import jake2.Globals;
import jake2.client.CL;
import jake2.client.Console;
import jake2.game.Cmd;
import jake2.server.SV;
import jake2.sys.Sys;
import jake2.util.Lib;
import jake2.util.PrintfFormat;
import jake2.util.Vargs;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.logging.Logger;

/**
 * Com
 * TODO complete Com interface
 */
public final class Com {

	public static class RD_Flusher {
		public void rd_flush(int target, byte [] buffer) {
		}
	}

	static int rd_target;
	static byte[] rd_buffer;
	static int rd_buffersize;
	static RD_Flusher rd_flusher;

	public static void BeginRedirect(int target, byte [] buffer, int buffersize, RD_Flusher flush) {
		if (0 == target || null == buffer || 0 == buffersize || null == flush)
			return;

		rd_target = target;
		rd_buffer = buffer;
		rd_buffersize = buffersize;
		rd_flusher = flush;

		rd_buffer = null;
	}

	public static void EndRedirect() {
		rd_flusher.rd_flush(rd_target, rd_buffer);

		rd_target = 0;
		rd_buffer = null;
		rd_buffersize = 0;
		rd_flusher = null;
	}

	static boolean recursive = false;

	static String msg = "";

	private static Logger logger = Logger.getLogger(Com.class.getName());

	// helper class to replace the pointer-pointer
	public static class ParseHelp {

		public ParseHelp(String in, int offset) {
			this(in.toCharArray(), offset);
		}

		public ParseHelp(String in) {
			this(in.toCharArray(), 0);
		}

		public ParseHelp(char in[]) {
			this(in, 0);
		}

		public ParseHelp(char in[], int offset) {
			if (in == null || in.length == 0)
				data = null;
			else
				data = in;
			index = offset;
		}

		public char getchar() {
			// faster than if
			try {
				return data[index];
			}
			catch (Exception e) {
				data = null;
				// last char
				return 0;
			}
		}

		public char nextchar() {
			// faster than if
			try {
				index++;
				return data[index];
			}
			catch (Exception e) {
				data = null;
				// avoid int wraps;
				index--;
				// last char
				return 0;
			}
		}

		public boolean isEof() {
			return data == null;
		}

		public int index;
		public char data[];

		public char skipwhites() {
			char c;
			while (((c = getchar()) <= ' ') && c != 0)
				index++;
			return c;
		}

		public char skipwhitestoeol() {
			char c;
			while (((c = getchar()) <= ' ') && c != '\n' && c != 0)
				index++;
			return c;
		}

		public char skiptoeol() {
			char c;
			while ((c = getchar()) != '\n' && c != 0)
				index++;
			return c;
		}
	}

	public static char com_token[] = new char[Defines.MAX_TOKEN_CHARS];

	// See GameSpanw.ED_ParseEdict() to see how to use it now.
	public static String Parse(ParseHelp hlp) {

		int c;
		int len = 0;
		len = 0;

		com_token[0] = 0;

		if (hlp.data == null) {
			return "";
		}

		// skip whitespace
		hlp.skipwhites();

		if (hlp.isEof()) {
			return "";
		}

		// skip // comments
		if (hlp.getchar() == '/') {
			if (hlp.nextchar() == '/') {
				if ((hlp.skiptoeol() == 0) || (hlp.skipwhites() == 0)) {
					return "";
				}
			}
			else {
				com_token[len] = '/';
				len++;
			}
		}
		// handle quoted strings specially
		if (hlp.getchar() == '\"') {
			while (true) {
				c = hlp.nextchar();
				if (c == '\"' || c == 0) {

					char xxx = hlp.nextchar();
					com_token[len] = '?';
					return new String(com_token, 0, len);
				}
				if (len < Defines.MAX_TOKEN_CHARS) {
					com_token[len] = hlp.getchar();
					len++;
				}
			}
		}

		// parse a regular word
		do {
			if (len < Defines.MAX_TOKEN_CHARS) {
				com_token[len] = hlp.getchar();
				len++;
			}

			c = hlp.nextchar();
		}
		while (c > 32);

		if (len == Defines.MAX_TOKEN_CHARS) {
			Printf("Token exceeded " + Defines.MAX_TOKEN_CHARS + " chars, discarded.\n");
			len = 0;
		}

		// trigger the eof
		hlp.skipwhites();

		com_token[len] = 0;
		return new String(com_token, 0, len);
	}

	public static xcommand_t Error_f = new xcommand_t() {
		public void execute() throws longjmpException {
			Error(Globals.ERR_FATAL, Cmd.Argv(1));
		}
	};

	public static void Error(int code, String fmt) throws longjmpException {
		Error(code, fmt, null);
	}

	public static void Error(int code, String fmt, Vargs vargs) throws longjmpException {
		// va_list argptr;
		// static char msg[MAXPRINTMSG];

		if (recursive) {
			Sys.Error("recursive error after: " + msg);
		}
		recursive = true;

		msg = sprintf(fmt, vargs);

		if (code == Defines.ERR_DISCONNECT) {
			CL.Drop();
			recursive = false;
			throw new longjmpException();
		}
		else if (code == Defines.ERR_DROP) {
			Com.Printf("********************\nERROR: " + msg + "\n********************\n");
			SV.Shutdown("Server crashed: " + msg + "\n", false);
			CL.Drop();
			recursive = false;
			throw new longjmpException();
		}
		else {
			SV.Shutdown("Server fatal crashed: %s" + msg + "\n", false);
			CL.Shutdown();
		}

		Sys.Error(msg);
	}

	/**
	 * Com_InitArgv checks the number of command line arguments
	 * and copies all arguments with valid length into com_argv.
	 */
	static void InitArgv(String[] args) throws longjmpException {

		if (args.length > Globals.MAX_NUM_ARGVS) {
			Com.Error(Globals.ERR_FATAL, "argc > MAX_NUM_ARGVS");
		}

		Globals.com_argc = args.length;
		for (int i = 0; i < Globals.com_argc; i++) {
			if (args[i].length() >= Globals.MAX_TOKEN_CHARS)
				Globals.com_argv[i] = "";
			else
				Globals.com_argv[i] = args[i];
		}
	}

	public static void DPrintf(String fmt) {
		DPrintf(fmt, null);
	}

	public static void Printf(String fmt) {
		Printf(fmt, null);
	}

	public static void DPrintf(String fmt, Vargs vargs) {
		if (Globals.developer == null || Globals.developer.value == 0)
			return; // don't confuse non-developers with techie stuff...

		Printf(fmt, vargs);
	}

	public static void Printf(String fmt, Vargs vargs) {
		// TODO Com.Printf ist nur zum testen
		String msg = sprintf(fmt, vargs);

		if (rd_target != 0)
		{
			if ((msg.length() + Lib.strlen(rd_buffer)) > (rd_buffersize - 1))
			{
				rd_flusher.rd_flush(rd_target, rd_buffer);
				// *rd_buffer = 0;
				rd_buffer[rd_buffersize] = '\0';
			}
			// TODO handle rd_buffer
			// strcat(rd_buffer, msg);
			return;
		}

		Console.Print(msg);
		
		// also echo to debugging console
		Sys.ConsoleOutput(msg);

		// logfile
		if (Globals.logfile_active != null && Globals.logfile_active.value != 0)
		{
			String name;
		
			if (Globals.logfile == null)
			{
				name = FS.Gamedir() + "/qconsole.log";
				if (Globals.logfile_active.value > 2)
					try
					{
						Globals.logfile = new RandomAccessFile(name, "a");
					}
					catch (FileNotFoundException e)
					{
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				else
					try
					{
						Globals.logfile = new RandomAccessFile(name, "w");
					}
					catch (FileNotFoundException e1)
					{
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
			}
			if (Globals.logfile != null)
				try
				{
					Globals.logfile.writeChars(msg);
				}
				catch (IOException e)
				{
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			if (Globals.logfile_active.value > 1)
				; // do nothing
				// fflush (logfile);		// force it to save every time
		}
	}

	public static void Println(String fmt) {
		Printf(fmt);
		Printf("\n");
	}

	public static String sprintf(String fmt, Vargs vargs) {
		String msg = "";
		if (vargs == null || vargs.size() == 0) {
			msg = fmt;
		}
		else {
			msg = new PrintfFormat(fmt).sprintf(vargs.toArray());
		}
		return msg;
	}

	public static int ServerState() {
		return Globals.server_state;
	}

	public static int Argc() {
		return Globals.com_argc;
	}

	public static String Argv(int arg) {
		if (arg < 0 || arg >= Globals.com_argc || Globals.com_argv[arg].length() < 1)
			return "";
		return Globals.com_argv[arg];
	}

	public static void ClearArgv(int arg) {
		if (arg < 0 || arg >= Globals.com_argc || Globals.com_argv[arg].length() < 1)
			return;
		Globals.com_argv[arg] = "";
	}

	public static void Quit() {
		SV.Shutdown("Server quit\n", false);
		CL.Shutdown();

		if (Globals.logfile != null) {
			try
			{
				Globals.logfile.close();
			}
			catch (IOException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			Globals.logfile = null;
		}

		Sys.Quit();
	}

 
	public static void SetServerState(int i)
	{
		 Globals.server_state = i;		
	}

	 
	public static int BlockChecksum(byte[] buf, int length)
	{
		Com.Error(Defines.ERR_FATAL, "Com.BlockChecksum not implemented!");
		System.exit(-1);
		return 0;
	}

	public static void StripExtension(String string, String string2) {
		// TODO implement!
	}
}