/*
 * Cvar.java
 * Copyright (C) 2003
 * 
 * $Id: Cvar.java,v 1.6 2003-11-30 21:50:08 rst Exp $
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

// $Id: Cvar.java,v 1.6 2003-11-30 21:50:08 rst Exp $
package jake2.qcommon;

import jake2.*;
import jake2.client.*;
import jake2.game.*;
import jake2.render.*;
import jake2.server.*;

/**
 * Cvar implements console variables. The original code is
 * located in cvar.c
 * TODO complete Cvar interface 
 */
public class Cvar extends GamePWeapon {

	public static final int ARCHIVE = 1;
	// set to cause it to be saved to vars.rc
	static final int USERINFO = 2; // added to userinfo  when changed
	static final int SERVERINFO = 4; // added to serverinfo when changed
	static final int NOSET = 8; // don't allow change from console at all,
	// but can be set from the command line
	static final int LATCH = 16; // save changes until server restart

	static cvar_t cvar_vars;

	/**
	 * @param var_name
	 * @param var_value
	 * @param flags
	 * @return
	 * TODO implement Cvar.Get()
	 */
	public static cvar_t Get(String var_name, String var_value, int flags) {
		cvar_t var;

		if ((flags & (USERINFO | SERVERINFO)) != 0) {
			if (!Cvar.InfoValidate(var_name)) {
				Com.Printf("invalid info cvar name\n");
				return null;
			}
		}

		var = Cvar.FindVar(var_name);
		if (var != null) {
			var.flags |= flags;
			return var;
		}

		if (var_value == null)
			return null;

		if ((flags & (USERINFO | SERVERINFO)) != 0) {
			if (!Cvar.InfoValidate(var_value)) {
				Com.Printf("invalid info cvar value\n");
				return null;
			}
		}
		var = new cvar_t();
		var.name = new String(var_name);
		var.string = new String(var_value);
		var.modified = true;
		// handles atof(var.string)
		try {
			var.value = Float.parseFloat(var.string);
		} catch (NumberFormatException e) {
			var.value = 0.0f;
		}
		// link the variable in
		var.next = cvar_vars;
		cvar_vars = var;

		var.flags = flags;

		return var;
	}

	static boolean InfoValidate(String s) {
		/*
		============
		Cvar_InfoValidate
		============
		*/

		if (s.indexOf('\\') >= 0)
			return false;
		if (s.indexOf('\"') >= 0)
			return false;
		if (s.indexOf(';') >= 0)
			return false;
		return true;
	}

	static void Init() {
	}

	public static String VariableString(String var_name) {
		cvar_t var;
		var = FindVar(var_name);
		return (var == null) ? "" : var.string;
	}

	static cvar_t FindVar(String var_name) {
		cvar_t var;

		for (var = cvar_vars; var != null; var = var.next) {
			if (var_name.equals(var.name))
				return var;
		}

		return null;
	}

	static boolean userinfo_modified = false;
	/*
	============
	Cvar_FullSet
	============
	*/
	static cvar_t FullSet(String var_name, String value, int flags) {
		cvar_t var;

		var = Cvar.FindVar(var_name);
		if (null == var) { // create it
			return Cvar.Get(var_name, value, flags);
		}

		var.modified = true;

		if ((var.flags & CVAR_USERINFO) != 0)
			userinfo_modified = true; // transmit at next oportunity

		//Z_Free(var.string); // free the old value string
		//var.string = CopyString(value);

		var.string = value;
		var.value = atof(var.string);
		var.flags = flags;

		return var;
	}

	/*
	============
	Cvar_Set
	============
	*/
	static cvar_t Set(String var_name, String value) {
		return Set2(var_name, value, false);
	}

	/*
	============
	Cvar_Set2
	============
	*/
	static cvar_t Set2(String var_name, String value, boolean force) {

		cvar_t var = Cvar.FindVar(var_name);
		if (var == null) { // create it
			return Cvar.Get(var_name, value, 0);
		}

		if ((var.flags & (CVAR_USERINFO | CVAR_SERVERINFO)) != 0) {
			if (!Cvar.InfoValidate(value)) {
				Com.Printf("invalid info cvar value\n");
				return var;
			}
		}

		if (!force) {
			if ((var.flags & CVAR_NOSET) != 0) {
				Com.Printf(var_name + " is write protected.\n");
				return var;
			}

			if ((var.flags & CVAR_LATCH) != 0) {
				if (var.latched_string != null) {
					if (strcmp(value, var.latched_string) == 0)
						return var;
					//Z_Free (var.latched_string);
					var.latched_string = null;
				} else {
					if (strcmp(value, var.string) == 0)
						return var;
				}

				if (Com.ServerState()) {
					Com.Printf(var_name + " will be changed for next game.\n");
					//var.latched_string = CopyString(value);
					var.latched_string = value;
				} else {
					//var.string = CopyString(value);
					var.string = value;
					var.value = atof(var.string);
					if (0 == strcmp(var.name, "game")) {
						FS.SetGamedir(var.string);
						FS.ExecAutoexec();
					}
				}
				return var;
			}
		} else {
			if (var.latched_string != null) {
				//Z_Free(var.latched_string);
				var.latched_string = null;
			}
		}

		if (0 == strcmp(value, var.string))
			return var; // not changed

		var.modified = true;

		if ((var.flags & CVAR_USERINFO) != 0)
			userinfo_modified = true; // transmit at next oportunity

		//Z_Free(var.string); // free the old value string

		//var.string = CopyString(value);
		var.string = value;
		var.value = atof(var.string);

		return var;
	}

	static xcommand_t Set_f = new xcommand_t() {
		public void execute() {
			int c;
			int flags;

			c = Cmd.Argc();
			if (c != 3 && c != 4) {
				Com.Printf("usage: set <variable> <value> [u / s]\n");
				return;
			}

			if (c == 4) {
				if (0 == strcmp(Cmd.Argv(3), "u"))
					flags = CVAR_USERINFO;
				else if (0 == strcmp(Cmd.Argv(3), "s"))
					flags = CVAR_SERVERINFO;
				else {
					Com.Printf("flags can only be 'u' or 's'\n");
					return;
				}
				Cvar.FullSet(Cmd.Argv(1), Cmd.Argv(2), flags);
			} else
				Cvar.Set(Cmd.Argv(1), Cmd.Argv(2));

		}

	};
	/*
	============
	Cvar_SetValue
	============
	*/
	public static void SetValue(String var_name, float value) {
		Cvar.Set(var_name, "" + value);
	}

	/*
	============
	Cvar_VariableValue
	============
	*/
	public static float VariableValue(String var_name) {
		cvar_t var = Cvar.FindVar(var_name);
		if (var == null)
			return 0;
		return atof(var.string);
	}

}
