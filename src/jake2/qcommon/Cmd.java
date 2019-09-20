/*
 * Cmd.java
 * Copyright (C) 2003
 * 
 * $Id: Cmd.java,v 1.18 2006-01-21 21:53:32 salomo Exp $
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

import jake2.qcommon.filesystem.FS;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cmd
 */
public final class Cmd {

    private static Command List_f = (List<String> args) -> {

        for (cmd_function_t cmd : Cmd.cmd_functions.values()) {
            Com.Printf(cmd.name + '\n');
        }

        Com.Printf(Cmd.cmd_functions.size() + " commands\n");
    };

    private static Command Exec_f = (List<String> args) -> {
        if (args.size() != 2) {
            Com.Printf("exec <filename> : execute a script file\n");
            return;
        }

        byte[] f = FS.LoadFile(args.get(1));
        if (f == null) {
            Com.Printf("couldn't exec " + args.get(1) + "\n");
            return;
        }
        Com.Printf("execing " + args.get(1) + "\n");

        Cbuf.InsertText(new String(f));

        FS.FreeFile(f);
    };

    private static Command Echo_f = (List<String> args) -> {
        if (args.size() < 2) {
            Com.Printf("usage: echo expression");
            return;
        }
        for (int i = 1; i < args.size(); i++) {
            Com.Printf(args.get(i) + " ");
        }
        Com.Printf("'\n");
    };

    private static Command Alias_f = (List<String> args) -> {
        if (args.size() == 1) {
            Com.Printf("Current alias commands:\n");
            for (cmdalias_t alias : Cmd.cmd_alias.values()) {
                Com.Printf(alias.getName() + " : " + alias.getValue() + "\n");
            }
            return;
        }

        // assume args.size >= 2
        String aliasName = args.get(1).trim();

        if (aliasName.length() > Defines.MAX_ALIAS_NAME) {
            Com.Printf("Alias name is too long\n");
            return;
        }

        // if the alias already exists, reuse it
        cmdalias_t alias = Cmd.cmd_alias.get(aliasName);

        if (alias == null) {
            alias = new cmdalias_t(aliasName);
            Cmd.cmd_alias.put(aliasName, alias);
        }

        if (args.size() >= 3) {
            alias.setValue(getArguments(args, 2));
        } else {
            alias.setValue("");
        }
    };

    private static Command Wait_f = (List<String> args) -> Globals.cmd_wait = true;

    private static Map<String, cmd_function_t> cmd_functions = new HashMap<>();

    private static Map<String, cmdalias_t> cmd_alias = new HashMap<>();

    private static final int ALIAS_LOOP_COUNT = 16;

    /**
     * Register our commands.
     */
    public static void Init() {

        Cmd.AddCommand("exec", Exec_f);
        Cmd.AddCommand("echo", Echo_f);
        Cmd.AddCommand("cmdlist", List_f);
        Cmd.AddCommand("alias", Alias_f);
        Cmd.AddCommand("wait", Wait_f);
    }

    /**
     * Cmd_MacroExpandString.
     */
    public static String MacroExpandString(String text) {

        boolean inquote = false;

        if (text.length() >= Defines.MAX_STRING_CHARS) {
            Com.Printf("Line exceeded " + Defines.MAX_STRING_CHARS
                    + " chars, discarded.\n");
            return null;
        }

        int expanded = 0;
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '"')
                inquote = !inquote;

            if (inquote) {
                result.append(text.charAt(i));
                continue; // don't expand inside quotes
            }

            if (text.charAt(i) != '$') {
                result.append(text.charAt(i));
                continue;
            }

            // scan out the complete macro, without $
            String token = Com.Parse(new Com.ParseHelp(text, i + 1));

            i += token.length();
            token = Cvar.VariableString(token);
            result.append(token);

            if (result.length() >= Defines.MAX_STRING_CHARS) {
                Com.Printf("Expanded line exceeded " + Defines.MAX_STRING_CHARS
                        + " chars, discarded.\n");
                return null;
            }

            if (++expanded == 100) {
                Com.Printf("Macro expansion loop, discarded.\n");
                return null;
            }
        }

        if (inquote) {
            Com.Printf("Line has unmatched quote, discarded.\n");
            return null;
        }

        return result.toString();
    }

    /**
     * Cmd_TokenizeString
     * 
     * Parses the given string into command line tokens. $Cvars will be expanded
     * unless they are in a quoted token.
     */
    public static List<String> TokenizeString(String text, boolean macroExpand) {
        List<String> result = new ArrayList<>();

        // macro expand the text
        if (macroExpand)
            text = MacroExpandString(text);

        if (text == null || text.isEmpty())
            return result;

        Com.ParseHelp ph = new Com.ParseHelp(text);

        while (true) {

            // skip whitespace up to a \n
            char c = ph.skipwhitestoeol();

            if (c == '\n') { // a newline separates commands in the buffer
                c = ph.nextchar();
                break;
            }

            if (c == 0)
                return result;

            String word = Com.Parse(ph);

            result.add(word);

        }
        return result;
    }

    public static void AddCommand(String cmd_name, Command function) {
        // fail if the command is a variable name
        if ((Cvar.VariableString(cmd_name)).length() > 0) {
            Com.Printf("Cmd_AddCommand: " + cmd_name + " already defined as a var\n");
            return;
        }

        // fail if the command already exists
        if (cmd_functions.containsKey(cmd_name)) {
            Com.Printf("Cmd_AddCommand: " + cmd_name + " already defined\n");
            return;
        }

        cmd_functions.put(cmd_name, new cmd_function_t(cmd_name, function));
    }

    /**
     * Cmd_RemoveCommand 
     */
    public static void RemoveCommand(String cmd_name) {
        if (!cmd_functions.containsKey(cmd_name)) {
            Com.Printf("RemoveCommand: " + cmd_name + " does not exist\n");
        } else {
            Com.Printf("RemoveCommand: " + cmd_name + " removed\n");
            cmd_functions.remove(cmd_name);
        }
    }

    public static String getArguments(List<String> args, int startIndex) {
        if (args.isEmpty() || startIndex >= args.size())
            return "";

        StringBuilder value = new StringBuilder();
        for (int i = startIndex; i < args.size(); i++) {
            if (i != startIndex)
                value.append(" ");
            value.append(args.get(i));
        }
        return value.toString();
    }

    public static String getArguments(List<String> args) {
        return getArguments(args, 1);
    }

    /**
     * Cmd_ExecuteString
     * 
     * A complete command line has been parsed, so try to execute it 
     * FIXME: lookupnoadd the token to speed search? 
     */
    public static void ExecuteString(String text) {
        if (text.trim().startsWith("//"))
            return;

        List<String> args = TokenizeString(text, true);

        // execute the command line
        if (args.size() == 0)
            return; // no tokens

        // check functions
        cmd_function_t cmd = cmd_functions.get(args.get(0));
        if (cmd != null) {
            if (cmd.function != null) {
                cmd.function.execute(args);
            } else { // forward to server command
                Cmd.ExecuteString("cmd " + text);
            }
            return;
        }

        // check alias
        for (cmdalias_t alias : cmd_alias.values()) {
            if (args.get(0).equalsIgnoreCase(alias.getName())) {
                if (++Globals.alias_count == ALIAS_LOOP_COUNT) {
                    Com.Printf("ALIAS_LOOP_COUNT\n");
                    return;
                }
                Cbuf.InsertText(alias.getValue());
                return;
            }
        }

        // check cvars
        if (Cvar.printOrSet(args))
            return;

        // send it as a server command if we are connected
        Cmd.ForwardToServer(args);
    }

    /**
     * Adds the current command line as a clc_stringcmd to the client message.
     * things like godmode, noclip, etc, are commands directed to the server, so
     * when they are typed in at the console, they will need to be forwarded.
     */
    private static void ForwardToServer(List<String> args) {
        String cmd;

        cmd = args.get(0);
        if (Globals.cls.state <= Defines.ca_connected || cmd.charAt(0) == '-'
                || cmd.charAt(0) == '+') {
            Com.Printf("Unknown command \"" + cmd + "\"\n");
            return;
        }

        MSG.WriteByte(Globals.cls.netchan.message, Defines.clc_stringcmd);
        SZ.Print(Globals.cls.netchan.message, cmd);
        if (args.size() > 1) {
            SZ.Print(Globals.cls.netchan.message, " ");
            SZ.Print(Globals.cls.netchan.message, getArguments(args));
        }
    }

    /**
     * Find commands or aliases by prefix
     */
    public static List<String> CompleteCommand(String prefix) {
        List<String> cmds = new ArrayList<>();

        for (cmd_function_t cmd : cmd_functions.values())
            if (cmd.name.startsWith(prefix))
                cmds.add(cmd.name);

        for (cmdalias_t a : cmd_alias.values())
            if (a.getName().startsWith(prefix))
                cmds.add(a.getName());

        return cmds;
    }

    static final class cmdalias_t {
        private String name;
        private String value;

        cmdalias_t(String name) {
            this.name = name;
        }

        String getName() {
            return name;
        }

        String getValue() {
            return value;
        }

        void setValue(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return "cmdalias_t{" +
                    "name='" + name + '\'' +
                    ", value='" + value + '\'' +
                    '}';
        }
    }

    /**
     * cmd_function_t
     */
    public static final class cmd_function_t {
        public String name;
        public Command function;

        public cmd_function_t(String name, Command function) {
            this.name = name;
            this.function = function;
        }

        @Override
        public String toString() {
            return "cmd_function_t{" +
                    "name='" + name + '\'' +
                    '}';
        }
    }
}