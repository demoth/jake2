/*
 * Cmd.java
 * Copyright (C) 2003
 * 
 * $Id: Cmd.java,v 1.12 2003-12-04 15:09:20 cwei Exp $
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
	
	static xcommand_t List_f = new xcommand_t() {
		public void execute() {
			cmd_function_t  cmd = Cmd.cmd_functions;
			int i = 0;

			while (cmd != null) {
				Com.Printf(cmd.name + '\n');
				i++;
				cmd = cmd.next;
			}
			Com.Printf(i + " commands\n");
		}
	};
	
	static xcommand_t Exec_f = new xcommand_t() {
		public void execute() {
			if (Cmd.Argc() != 2) {
				Com.Printf("exec <filename> : execute a script file\n");
				return;
			}

			byte[] f = null;
			f = FS.LoadFile(Cmd.Argv(1));
			if (f == null) {
				Com.Printf("couldn't exec " + Cmd.Argv(1) + "\n");
				return;
			}
			Com.Printf("execing " + Cmd.Argv(1) + "\n");

			Cbuf.InsertText(new String(f));
			
			FS.FreeFile(f);
		}
	};
	static xcommand_t Echo_f = new xcommand_t() {
		public void execute() {
			for (int i  = 1;  i < Cmd.Argc(); i++) {
				Com.Printf(Cmd.Argv(i) + " ");
			}
			Com.Printf("'\n");
		}
	};
	
	static xcommand_t Alias_f = new xcommand_t() {
		public void execute() {
			cmdalias_t a = null;
			if (Cmd.Argc() == 1) {
				Com.Printf("Current alias commands:\n");
				for (a = Globals.cmd_alias; a != null; a = a.next) {
					Com.Printf(a.name + " : " + a.value);
				}
				return;
			}

			String s = Cmd.Argv(1);
			if (s.length() > Globals.MAX_ALIAS_NAME) {
				Com.Printf("Alias name is too long\n");
				return;
			}

			// if the alias already exists, reuse it
			for (a = Globals.cmd_alias; a != null; a = a.next) {
				if (s.equalsIgnoreCase(a.name)) {
					a.value = null;
					break;
				}
			}
			
			if (a == null) {
				a = new cmdalias_t();
				a.next = Globals.cmd_alias;
				Globals.cmd_alias = a;
			}
			a.name = s;
			
			// copy the rest of the command line
			String cmd = "";
			int c = Cmd.Argc();
			for (int i = 2; i < c; i++) {
				cmd = cmd + Cmd.Argv(i);
				if (i != (c-1)) cmd = cmd + " ";
			}
			cmd = cmd + "\n";
			
			a.value = cmd;
		}
	};
	static xcommand_t Wait_f = new xcommand_t() {
		public void execute() {
			Globals.cmd_wait = true;
		}
	};
	
	public static cmd_function_t cmd_functions = null;
	static int cmd_argc;
	static String[] cmd_argv = new String[Globals.MAX_STRING_TOKENS];
	static String cmd_args;

	/**
	 * register our commands
	 */
	public static void Init() {
    	Cmd.AddCommand ("cmdlist", List_f);
		Cmd.AddCommand ("exec", Exec_f);
		Cmd.AddCommand ("echo", Echo_f);
		Cmd.AddCommand ("alias",Alias_f);
		Cmd.AddCommand ("wait", Wait_f);
	}

	/**
	 * @param cmdname
	 * @param function
	 */
	public static void AddCommand(String cmd_name, xcommand_t function) {
		cmd_function_t  cmd;
         
		// fail if the command is a variable name
		if ((Cvar.VariableString(cmd_name)).length() > 0) {
			Com.Printf("Cmd_AddCommand: " + cmd_name + " already defined as a var\n");
			return;
		}
		
		// fail if the command already exists
		for (cmd=cmd_functions ; cmd != null ; cmd=cmd.next) {
			if (cmd_name.equals(cmd.name)) {
				Com.Printf("Cmd_AddCommand: " + cmd_name + " already defined\n");
				return;
			}
		}
		
		cmd = new cmd_function_t();
		cmd.name = cmd_name;
		cmd.function = function;
		cmd.next = cmd_functions;
		cmd_functions = cmd; 
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

	/**
	 * 
	 */
	public static String Args() {
		return cmd_args;
	}
	
	public static void ExecuteString (String text) {       
//	00813         cmd_function_t  *cmd;
//	00814         cmdalias_t              *a;
//	00815 
//	00816         Cmd_TokenizeString (text, true);
//	00817                         
//	00818         // execute the command line
//	00819         if (!Cmd_Argc())
//	00820                 return;         // no tokens
//	00821 
//	00822         // check functions
//	00823         for (cmd=cmd_functions ; cmd ; cmd=cmd->next)
//	00824         {
//	00825                 if (!Q_strcasecmp (cmd_argv[0],cmd->name))
//	00826                 {
//	00827                         if (!cmd->function)
//	00828                         {       // forward to server command
//	00829                                 Cmd_ExecuteString (va("cmd %s", text));
//	00830                         }
//	00831                         else
//	00832                                 cmd->function ();
//	00833                         return;
//	00834                 }
//	00835         }
//	00836 
//	00837         // check alias
//	00838         for (a=cmd_alias ; a ; a=a->next)
//	00839         {
//	00840                 if (!Q_strcasecmp (cmd_argv[0], a->name))
//	00841                 {
//	00842                         if (++alias_count == ALIAS_LOOP_COUNT)
//	00843                         {
//	00844                                 Com_Printf ("ALIAS_LOOP_COUNT\n");
//	00845                                 return;
//	00846                         }
//	00847                         Cbuf_InsertText (a->value);
//	00848                         return;
//	00849                 }
//	00850         }
//	00851         
//	00852         // check cvars
//	00853         if (Cvar_Command ())
//	00854                 return;
//	00855 
//	00856         // send it as a server command if we are connected
//	00857         Cmd_ForwardToServer ();
	}
}
