/*
 * Cmd.java
 * Copyright (C) 2003
 * 
 * $Id: Cmd.java,v 1.1 2003-11-17 22:25:47 hoz Exp $
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
package jake2.game;

import jake2.Globals;
import jake2.qcommon.*;

/**
 * Cmd
 */
public final class Cmd {
	
	public static cmd_function_t cmd_functions = null;
	static int cmd_argc;
	static String[] cmd_argv = new String[Globals.MAX_STRING_TOKENS];

	/**
	 * register our commands
	 */
	public static void Init() {
    	Cmd.AddCommand ("cmdlist", new Cmd_List_f());
		Cmd.AddCommand ("exec", new Cmd_Exec_f());
		Cmd.AddCommand ("echo", new Cmd_Echo_f());
		Cmd.AddCommand ("alias", new Cmd_Alias_f());
		Cmd.AddCommand ("wait", new Cmd_Wait_f());
	}

	/**
	 * @param cmdname
	 * @param function
	 * TODO implement Cmd.AddCommand()
	 */
	public static void AddCommand(String cmdname, xcommand_t function) { 	
	}
	
	/**
	 * @return number of command arguments
	 */
	public static int Argc() {
		return cmd_argc;	
	}
	
	/**
	 * @param i index
	 * @return command argument at position i
	 */
	public static String Argv(int i) {
		if (i < 0 || i >= cmd_argc) return "";
		return cmd_argv[i];
	}
}
