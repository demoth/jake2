/*
 * Com.java
 * Copyright (C) 2003
 * 
 * $Id: Com.java,v 1.15 2003-12-02 10:07:36 hoz Exp $
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
	
	private static Logger logger = Logger.getLogger(Com.class.getName());
	
	public static xcommand_t Error_f = new xcommand_t() {
		public void execute() throws longjmpException {
			Error(Globals.ERR_FATAL, Cmd.Argv(1));
		}
	};

	/**
	 * @param code
	 * @param msg
	 */
	static void Error(int code, String msg) throws longjmpException {
//		00180         va_list         argptr;
//		00181         static char             msg[MAXPRINTMSG];
		
//		00183 
		if (recursive) {
			Sys.Error("recursive error after: " + msg);
		}
		recursive = true;
//		00187 
//		00188         va_start (argptr,fmt);
//		00189         vsprintf (msg,fmt,argptr);
//		00190         va_end (argptr);
//		00191         
		if (code == Defines.ERR_DISCONNECT) {
			CL.Drop ();
			recursive = false;
			throw new longjmpException();
		} else if (code == Defines.ERR_DROP) {
			Com.Printf ("********************\nERROR: " + 
				msg + "\n********************\n");
			SV.Shutdown("Server crashed: " + msg + "\n", false);
			CL.Drop();
			recursive = false;
			throw new longjmpException();
		} else {
			SV.Shutdown("Server fatal crashed: %s" + msg + "\n", false);
			CL.Shutdown();
		}
//		00211 
//		00212         if (logfile)
//		00213         {
//		00214                 fclose (logfile);
//		00215                 logfile = NULL;
//		00216         }
//		00217 
		Sys.Error(msg);
	}
		
	/**
	 * Com_InitArgv checks the number of command line arguments
	 * and copies all arguments with valid length into com_argv.
	 * @param args
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
		System.out.print( sprintf(fmt, vargs) );
		
		//logger.log(Level.INFO, msg);
	}
	
	public static String sprintf (String fmt, Vargs vargs) {
		String msg = "";
		if (vargs == null || vargs.size() == 0) {
			msg = fmt; 
		} else {
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
}
