/*
 * Cbuf.java
 * Copyright (C) 2003
 * 
 * $Id: Cbuf.java,v 1.8 2005-12-18 22:10:09 cawe Exp $
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
package jake2.qcommon.exec;

import jake2.qcommon.Globals;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static jake2.qcommon.Defines.CVAR_ARCHIVE;

/**
 * Command buffer.
 * Used to accumulate commands before execution
 */
public final class Cbuf {

    private static final Deque<String> buffer = new ArrayDeque<>();

    private static final Deque<String> deferredBuffer = new ArrayDeque<>();

    /**
     * Puts command(s) to the beginning of the command buffer
     */
    public static void InsertText(String text) {
        List<String> commands = splitCommandLine(text);
        for (int i = commands.size() - 1; i >= 0; i--)
            buffer.push(commands.get(i));
    }

    /**
     * Split string by new line or unquoted semicolon
     */
    static List<String> splitCommandLine(String text) {
        List<String> result = new ArrayList<>();
        String[] lines = text.split("[\\n\\r]");
        for (String line : lines) {
            if (line.contains(";")) {
                // split by ; unless it is in quotes
                StringBuilder sb = new StringBuilder();
                boolean inQuotes = false;
                for (int i = 0; i < line.length(); i++) {
                    char c = line.charAt(i);
                    if (c == ';' && !inQuotes) {
                        result.add(sb.toString());
                        sb = new StringBuilder();
                    } else if (c == '"') {
                        inQuotes = !inQuotes;
                    } else {
                        sb.append(c);
                    }
                }
                if (sb.length() > 0)
                    result.add(sb.toString());
            } else {
                result.add(line);
            }
        }
        return result;
    }

    /**
     * Applies +set cvar value commands,
     * Adds command line parameters as script statements Commands lead with
     * a +, and continue until another +
     * <br/>
     * 'set' commands are added early, so they are guaranteed to be set before
     * the client and server initialize for the first time.
     * <br/>
     * Other commands are added late, after all initialization is complete.
     * @param clear - if true - remove such commands from 'args'
     */
    public static void AddEarlySetCommands(List<String> args, boolean clear) {
        for (int i = 0; i < args.size(); i++) {
            if (!args.get(i).equals("+set") || args.size() < i + 2)
                continue;

            // varName must not contain spaces
            final String varName = args.get(i + 1);

            // value can contain spaces
            final String value = args.get(i + 2);

            buffer.add("set " + varName + " \"" + value + '"');

            if (clear) {
                args.set(i, "");
                args.set(i + 1, "");
                args.set(i + 2, "");
            }
        }
    }

    /**
     * Adds command line parameters as script statements
     * Commands lead with a + and continue until another + or -
     * quake +developer 1 +map amlev1
     *
     * Returns false if any late commands were added, which
     * will keep the demoloop from immediately starting
     * Apply + commands (like +map)
     * @return true if nothing was applied
     */
    public static boolean AddLateCommands(List<String> args) {

        // build the combined string to parse from
        int length = 0;
        int argc = args.size();
        for (int i = 1; i < argc; i++) {
            length += args.get(i).length();
        }
        if (length == 0)
            return true;

        StringBuilder text = new StringBuilder();
        for (int i = 1; i < argc; i++) {
            text.append(args.get(i));
            if (i != argc - 1)
                text.append(" ");
        }

        // pull out the commands
        StringBuilder build = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '+') {
                i++;

                int j;
                for (j = i; j < text.length() && (text.charAt(j) != '+') && (text.charAt(j) != '-'); j++) {
                    ;
                }

                build.append(text.substring(i, j));
                build.append("\n");

                i = j - 1;
            }
        }

        boolean isEmpty = (build.length() == 0);
        if (!isEmpty)
            AddText(build.toString());

        return isEmpty;
    }

    /**
     * Adds command(s) to the tail of the command buffer
     */
    public static void AddText(String text) {
        buffer.addAll(splitCommandLine(text));
    }

    /**
     *  Execute all command is the buffer
     */
    public static void Execute() {
        Globals.alias_count = 0; // don't allow infinite alias loops
        while (!buffer.isEmpty()) {
            String command = buffer.pop().trim();
            if (!command.isEmpty())
                Cmd.ExecuteString(command.trim());
            if (Globals.cmd_wait) {
                Globals.cmd_wait = false;
                break;
            }
        }
    }

    public static String contents() {
        return String.join("\n", buffer);
    }

    /**
     * Puts all remaining commands from main buffer to deferredBuffer.
     * Used to delay command execution before loading map (like map q2dm1; give all)
     */
    public static void CopyToDefer() {
        deferredBuffer.addAll(buffer);
        buffer.clear();
    }

    /**
     * Puts all remaining commands from deferredBuffer to main Buffer.
     * Used to delay command execution before loading map (like map q2dm1; give all)
     */
    public static void InsertFromDefer() {
        buffer.addAll(deferredBuffer);
        deferredBuffer.clear();
    }

    public static void reconfigure(List<String> args, boolean clear) {
        String dir = Cvar.getInstance().Get("cddir", "", CVAR_ARCHIVE).string;
        Cbuf.AddText("exec default.cfg\n");
        Cbuf.AddText("bind MWHEELUP weapnext\n");
        Cbuf.AddText("bind MWHEELDOWN weapprev\n");
        Cbuf.AddText("bind w +forward\n");
        Cbuf.AddText("bind s +back\n");
        Cbuf.AddText("bind a +moveleft\n");
        Cbuf.AddText("bind d +moveright\n");
        Cbuf.Execute();
        Cvar.getInstance().Set("vid_fullscreen", "0");
        Cbuf.AddText("exec config.cfg\n");

        Cbuf.AddEarlySetCommands(args, clear);
        Cbuf.Execute();
        if (!("".equals(dir))) Cvar.getInstance().Set("cddir", dir);
    }

}