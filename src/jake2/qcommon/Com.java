/*
 * Com.java
 * Copyright (C) 2003
 * 
 * $Id: Com.java,v 1.5 2003-11-25 13:10:11 cwei Exp $
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

import java.util.logging.Level;
import java.util.logging.Logger;

import jake2.Globals;
import jake2.game.Cmd;
import jake2.util.PrintfFormat;
import jake2.util.Vargs;

/**
 * Com
 * TODO complete Com interface
 */
public final class Com {
	
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
	}
	
	/**
	 * @param s
	 */
	public static void print(String s) {
		
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
	
	public static void Printf(String fmt, Vargs vargs) {
		String msg = "";
		if (vargs == null || vargs.size() == 0) {
			msg = fmt; 
		} else {
			msg = new PrintfFormat(fmt).sprintf(vargs.toArray());
		}
		
		// TODO Com.Printf is only a hack
		
		logger.log(Level.INFO, msg);
		
	}

}
