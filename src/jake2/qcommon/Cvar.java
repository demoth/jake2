/*
 * Cvar.java
 * Copyright (C) 2003
 * 
 * $Id: Cvar.java,v 1.10 2007-05-14 22:29:30 cawe Exp $
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
import jake2.game.Cmd;
import jake2.game.Info;
import jake2.game.cvar_t;
import jake2.qcommon.filesystem.FS;
import jake2.util.Lib;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Cvar implements console variables. The original code is located in cvar.c
 */
public class Cvar extends Globals {

    private static Map<String, cvar_t> cvarMap = new HashMap<>();

    public static cvar_t Get(String var_name, String defaultValue, int flags) {
        cvar_t var;

        if ((flags & (CVAR_USERINFO | CVAR_SERVERINFO)) != 0) {
            if (invalidInfoString(var_name)) {
                Com.Printf("invalid info cvar name\n");
                return null;
            }
        }

        var = Cvar.FindVar(var_name);
        if (var != null) {
            var.flags |= flags;
            return var;
        }

        if (defaultValue == null)
            return null;

        if ((flags & (CVAR_USERINFO | CVAR_SERVERINFO)) != 0) {
            if (invalidInfoString(defaultValue)) {
                Com.Printf("invalid info cvar value\n");
                return null;
            }
        }
        var = new cvar_t();
        var.name = var_name;
        var.string = defaultValue;
        var.modified = true;
        var.value = Lib.atof(var.string);
        var.flags = flags;

        cvarMap.put(var_name, var);

        return var;
    }

    static void Init() {
        Cmd.AddCommand("set", (List<String> args) -> {
            int flags;
            if (args.size() != 3 && args.size() != 4) {
                Com.Printf("usage: set <variable> <value> [u / s]\n");
                return;
            }

            if (args.size() == 4) {
                if (args.get(3).equals("u"))
                    flags = CVAR_USERINFO;
                else if (args.get(3).equals("s"))
                    flags = CVAR_SERVERINFO;
                else {
                    Com.Printf("flags can only be 'u' or 's'\n");
                    return;
                }
                Cvar.FullSet(args.get(1), args.get(2), flags);
            } else
                Cvar.Set(args.get(1), args.get(2));

        });
        Cmd.AddCommand("cvarlist", (List<String> args) -> {
            for (cvar_t var : cvarMap.values()) {
                if ((var.flags & CVAR_ARCHIVE) != 0)
                    Com.Printf("*");
                else
                    Com.Printf(" ");
                if ((var.flags & CVAR_USERINFO) != 0)
                    Com.Printf("U");
                else
                    Com.Printf(" ");
                if ((var.flags & CVAR_SERVERINFO) != 0)
                    Com.Printf("S");
                else
                    Com.Printf(" ");
                if ((var.flags & CVAR_NOSET) != 0)
                    Com.Printf("-");
                else if ((var.flags & CVAR_LATCH) != 0)
                    Com.Printf("L");
                else
                    Com.Printf(" ");
                Com.Printf(" " + var.name + " \"" + var.string + "\"\n");
            }
            Com.Printf(cvarMap.size() + " cvars\n");
        });
    }

    public static String VariableString(String var_name) {
        cvar_t var;
        var = FindVar(var_name);
        return (var == null) ? "" : var.string;
    }

    static cvar_t FindVar(String var_name) {
        return cvarMap.get(var_name);
    }

    /**
     * Creates a variable if not found and sets their value, the parsed float value and their flags.
     */
    public static cvar_t FullSet(String var_name, String value, int flags) {
        cvar_t var;

        var = Cvar.FindVar(var_name);
        if (null == var) { // create it
            return Cvar.Get(var_name, value, flags);
        }

        var.modified = true;

        if ((var.flags & CVAR_USERINFO) != 0)
            Globals.userinfo_modified = true; // transmit at next oportunity

        var.string = value;
        var.value = Lib.atof(var.string);
        var.flags = flags;

        return var;
    }

    /** 
     * Sets the value of the variable without forcing. 
     */
    public static cvar_t Set(String var_name, String value) {
        return Set2(var_name, value, false);
    }

    /** 
     * Sets the value of the variable with forcing. 
     */
    public static cvar_t ForceSet(String var_name, String value) {
        return Cvar.Set2(var_name, value, true);
    }
    
    /**
     * Gereric set function, sets the value of the variable, with forcing its even possible to 
     * override the variables write protection. 
     */
    static cvar_t Set2(String var_name, String value, boolean force) {

        cvar_t var = Cvar.FindVar(var_name);
        if (var == null) { 
        	// create it
            return Cvar.Get(var_name, value, 0);
        }

        if ((var.flags & (CVAR_USERINFO | CVAR_SERVERINFO)) != 0) {
            if (invalidInfoString(value)) {
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
                    if (value.equals(var.latched_string))
                        return var;
                    var.latched_string = null;
                } else {
                    if (value.equals(var.string))
                        return var;
                }

                if (Globals.server_state != 0) {
                    Com.Printf(var_name + " will be changed for next game.\n");
                    var.latched_string = value;
                } else {
                    var.string = value;
                    var.value = Lib.atof(var.string);
                    if (var.name.equals("game")) {
                        FS.SetGamedir(var.string);
                        FS.ExecAutoexec();
                    }
                }
                return var;
            }
        } else {
            if (var.latched_string != null) {
                var.latched_string = null;
            }
        }

        if (value.equals(var.string))
            return var; // not changed

        var.modified = true;

        if ((var.flags & CVAR_USERINFO) != 0)
            Globals.userinfo_modified = true; // transmit at next oportunity

        var.string = value;
        try {
            var.value = Float.parseFloat(var.string);
        } catch (Exception e) {
            var.value = 0.0f;
        }

        return var;
    }


    /**
     * Sets a float value of a variable.
     * 
     * The overloading is very important, there was a problem with 
     * networt "rate" string --> 10000 became "10000.0" and that wasn't right.
     */
    public static void SetValue(String var_name, int value) {
        Cvar.Set(var_name, "" + value);
    }

    public static void SetValue(String var_name, float value) {
        if (value == (int)value) {
            Cvar.Set(var_name, "" + (int)value);
        } else {
            Cvar.Set(var_name, "" + value);
        }
    }

    /**
     * Returns the float value of a variable.
     */
    public static float VariableValue(String var_name) {
        cvar_t var = Cvar.FindVar(var_name);
        if (var == null)
            return 0;
        
        return Lib.atof(var.string);
    }

    /**
     * Handles variable inspection and changing from the console.
     * @param args
     */
    public static boolean printOrSet(List<String> args) {
        cvar_t v;

        if (args.isEmpty())
            return false;

        // check variables
        v = Cvar.FindVar(args.get(0));
        if (v == null)
            return false;

        // perform a variable print or set
        if (args.size() == 1) {
            Com.Printf("\"" + v.name + "\" is \"" + v.string + "\"\n");
            return true;
        }

        Cvar.Set(v.name, args.get(1));
        return true;
    }

    private static String BitInfo(int flags) {
        String info = "";

        for (cvar_t var : cvarMap.values()) {
            if ((var.flags & flags) != 0)
                info = Info.Info_SetValueForKey(info, var.name, var.string);
        }
        return info;
    }

    /**
     * Returns an info string containing all the CVAR_SERVERINFO cvars. 
     */
    public static String Serverinfo() {
        return BitInfo(Defines.CVAR_SERVERINFO);
    }

    public static void eachCvarByFlags(int flags, Consumer<cvar_t> consumer) {
        for (cvar_t var : cvarMap.values()) {
            if (0 != (var.flags & flags)) {
                consumer.accept(var);
            }
        }
    }
    
    /**
     * Any variables with latched values will be updated.
     */
    public static void GetLatchedVars() {
        for (cvar_t var : cvarMap.values()) {
            if (var.latched_string == null || var.latched_string.length() == 0)
                continue;
            var.string = var.latched_string;
            var.latched_string = null;
            var.value = Lib.atof(var.string);
            if (var.name.equals("game")) {
                FS.SetGamedir(var.string);
                FS.ExecAutoexec();
            }
        }
    }

    /**
     * Returns an info string containing all the CVAR_USERINFO cvars.
     */
    public static String Userinfo() {
        return BitInfo(CVAR_USERINFO);
    }
    
    /**
     * Appends lines containing \"set variable value\" for all variables
     * with the archive flag set true. 
     */

    public static void writeArchiveVariables(String path) {
        RandomAccessFile f;
        String buffer;

        f = Lib.fopen(path, "rw");
        if (f == null)
            return;

        try {
            f.seek(f.length());
        } catch (IOException e1) {
            Lib.fclose(f);
            return;
        }
        for (cvar_t var : cvarMap.values()) {
            if ((var.flags & CVAR_ARCHIVE) != 0) {
                buffer = "set " + var.name + " \"" + var.string + "\"\n";
                try {
                    f.writeBytes(buffer);
                } catch (IOException e) {
                    Com.Printf("Could not write cvar " + var + " to " + path);
                }
            }
        }
        Lib.fclose(f);
    }

    /**
     * Variable typing auto completition.
     */
    public static List<String> CompleteVariable(String prefix) {

        List<String> vars = new ArrayList<>();

        for (String cvarName : cvarMap.keySet()) {
            if (cvarName.startsWith(prefix))
                vars.add(cvarName);
        }

        return vars;
    }

    /**
     * Some characters are invalid for info strings.
     */
    private static boolean invalidInfoString(String s) {
        return s.contains("\\") || s.contains("\"") || s.contains(";");
    }
}