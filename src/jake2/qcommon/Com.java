/*
 * Com.java
 * Copyright (C) 2003
 * 
 * $Id: Com.java,v 1.20 2003-12-28 16:53:01 rst Exp $
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
import jake2.game.Cmd;
import jake2.server.SV;
import jake2.sys.Sys;
import jake2.util.PrintfFormat;
import jake2.util.Vargs;

import java.util.logging.Logger;

/**
 * Com
 * TODO complete Com interface
 */
public final class Com {

	static boolean recursive = false;

	static String msg = "";

	private static Logger logger = Logger.getLogger(Com.class.getName());

	// helper class to replace the pointer-pointer
	public static class ParseHelp {

		public ParseHelp(String in) {
			eof = false;
			if (in == null || in.length() == 0)
				data = null;
			else
				data = in.toCharArray();
			index = 0;
		}

		public char getchar() {
			// faster than if
			try {
				return data[index];
			}
			catch (Exception e) {
				eof = true;
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
				eof = true;
				// avoid int wraps;
				index--;
				// last char
				return 0;
			}
		}

		public boolean isEof() {
			return eof;
		}

		private boolean eof = false;

		public int index;
		public char data[];

		public char skipwhites() {
			char c;
			while (((c = getchar()) <= ' ') && c != 0)
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

		if ((hlp.skipwhites()) == 0) {
			hlp.data = null;
			return "";
		}

		// skip // comments
		if (hlp.getchar() == '/')
			if (hlp.nextchar() == '/') {
				if ((hlp.skiptoeol() == 0) || (hlp.skipwhites() == 0)) {
					hlp.data = null;
					return "";
				}
			}
			else {
				com_token[len] = '/';
				len++;
			}

		// handle quoted strings specially
		if (hlp.getchar() == '\"') {
			while (true) {
				c = hlp.nextchar();
				if (c == '\"' || c == 0) {

					char xxx = hlp.nextchar();
					com_token[len] = '§';
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
		// TODO impl the developer check 
		Printf(fmt, vargs);
	}

	public static void Printf(String fmt, Vargs vargs) {
		// TODO Com.Printf ist nur zum testen
		// hier ist System.out mal erlaubt
		System.out.print(sprintf(fmt, vargs));

		//logger.log(Level.INFO, msg);
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

		//		if (logfile) {
		//	00237                 fclose (logfile);
		//	00238                 logfile = NULL;
		//		}

		Sys.Quit();
	}

	public static void main(String args[]) {
		String test = "testrene = \"ein mal eins\"; a=3 ";
		ParseHelp ph = new ParseHelp(test);

		while (!ph.isEof())
			System.out.println("[" + Parse(ph) + "]");

		System.out.println("OK!");

		test = " testrene = \"ein mal eins\"; a=3";

		ph = new ParseHelp(test);

		while (!ph.isEof())
			System.out.println("[" + Parse(ph) + "]");

		System.out.println("OK!");
	}
}