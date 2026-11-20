/*
 * Com.java
 * Copyright (C) 2003
 * 
 * $Id: Com.java,v 1.14 2005-12-16 21:17:08 salomo Exp $
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

import jake2.qcommon.exec.Cmd;
import jake2.qcommon.sys.Sys;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Engine messaging and plain-text redirection.
 */
public final class Com
{
    public enum ConsoleLevel {
        // Always printed, part of the game
        PRINT,
        // Diagnostics are ordered from least to most verbose.
        ERROR,
        WARN,
        INFO,
        DEBUG,
        TRACE,
    }

    public enum LogSubsystem {
        NETWORK
    }

    @FunctionalInterface
    public interface ConsoleSink {
        void print(ConsoleLevel level, String message);
    }

    private static ConsoleSink consoleSink;

    /** User-facing gameplay and command output. */
    public static void Print(String message) {
        Print(message, true);
    }

    public static void Print(String message, boolean endLine) {
        write(ConsoleLevel.PRINT, message, endLine);
    }

    /** Sparse lifecycle and significant system-state messages. */
    public static void Info(String message) {
        Info(null, message);
    }

    public static void Info(LogSubsystem subsystem, String message) {
        emit(subsystem, ConsoleLevel.INFO, message);
    }

    /** Report a warning without changing control flow. */
    public static void Warn(String message) {
        Warn(null, message);
    }

    public static void Warn(LogSubsystem subsystem, String message) {
        emit(subsystem, ConsoleLevel.WARN, message);
    }

    /** Report a failure only. Recovery belongs to the caller handling that failure. */
    public static void Error(String message) {
        Error(null, message);
    }

    public static void Error(LogSubsystem subsystem, String message) {
        emit(subsystem, ConsoleLevel.ERROR, message);
    }

    /** Developer diagnostics. Class attribution is deferred until a simpler API is chosen. */
    public static void Debug(String message) {
        Debug(null, message);
    }

    public static void Debug(LogSubsystem subsystem, String message) {
        emit(subsystem, ConsoleLevel.DEBUG, message);
    }

    /** High-volume diagnostics. Build the message only when TRACE is enabled. */
    public static void Trace(Supplier<String> message) {
        Trace(null, message);
    }

    public static void Trace(LogSubsystem subsystem, Supplier<String> message) {
        if (isTraceEnabled(subsystem)) {
            write(ConsoleLevel.TRACE, message.get(), true);
        }
    }

    public static boolean isTraceEnabled() {
        return isTraceEnabled(null);
    }

    public static boolean isTraceEnabled(LogSubsystem subsystem) {
        return ConsoleLevel.TRACE == threshold(subsystem);
    }

    private static ConsoleLevel threshold(LogSubsystem subsystem) {
        var override = switch (subsystem) {
            case NETWORK -> Globals.networkLogLevel;
            case null -> null;
        };
        if (override != null) {
            String value = override.getTypedValue();
            if (!value.isEmpty()) return ConsoleLevel.valueOf(value);
        }
        var logLevel = Globals.logLevel;
        return logLevel == null ? ConsoleLevel.INFO : logLevel.getTypedValue();
    }

    private static void emit(LogSubsystem subsystem, ConsoleLevel level, String message) {
        if (level.compareTo(threshold(subsystem)) <= 0) {
            write(level, message, true);
        }
    }

    private static void write(ConsoleLevel level, String message, boolean endLine) {
        // Preserve existing line endings and intentional blank lines, including CR-only input.
        if (endLine && !message.endsWith("\n") && !message.endsWith("\r")) {
            message += "\n";
        }
        printToConsole(level, message);
    }
    
    /**
     * Render control bytes in a log-friendly form so protocol/debug output remains readable.
     */
    public static String makePrintable(String text) {
        if (text == null) {
            return "null";
        }

        StringBuilder result = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '\n':
                    result.append("\\n");
                    break;
                case '\r':
                    result.append("\\r");
                    break;
                case '\t':
                    result.append("\\t");
                    break;
                default:
                    if (ch >= 0x20 && ch <= 0x7E) {
                        result.append(ch);
                    } else if (ch <= 0xFF) {
                        result.append("\\x")
                            .append(String.format(Locale.ROOT, "%02X", (int) ch));
                    } else {
                        result.append("\\u")
                            .append(String.format(Locale.ROOT, "%04X", (int) ch));
                    }
                    break;
            }
        }
        return result.toString();
    }

    private static volatile Redirect redirect;

    /** Capture this thread's messages for the duration of a command. */
    public static void RedirectOutput(Consumer<String> flush, Runnable command) {
        Objects.requireNonNull(command, "command");
        try (var _ = beginRedirect(flush)) {
            command.run();
        }
    }

    private static synchronized Redirect beginRedirect(Consumer<String> flush) {
        Objects.requireNonNull(flush, "flush");
        if (redirect != null) {
            throw new IllegalStateException("Output redirection is already active");
        }
        redirect = new Redirect(flush);
        return redirect;
    }

    private static final class Redirect implements AutoCloseable {
        // Latin-1 uses at most one byte per UTF-16 unit, including replacement characters.
        // The existing limit leaves room for the connectionless header and "print\n".
        private static final int LIMIT = Defines.SV_OUTPUTBUF_LENGTH - 1;

        private final Thread owner = Thread.currentThread();
        private final Consumer<String> sink;
        private final StringBuilder buffer = new StringBuilder(LIMIT);
        private boolean flushing;
        private boolean sentChunk;

        private Redirect(Consumer<String> sink) {
            this.sink = sink;
        }

        private void append(String text) {
            int offset = 0;
            while (offset < text.length()) {
                if (buffer.length() == LIMIT) {
                    flush();
                }
                int count = Math.min(LIMIT - buffer.length(), text.length() - offset);
                buffer.append(text, offset, offset + count);
                offset += count;
            }
        }

        private void flush() {
            String chunk = buffer.toString();
            buffer.setLength(0);
            sentChunk = true;
            flushing = true;
            try {
                sink.accept(chunk);
            } finally {
                flushing = false;
            }
        }

        @Override
        public void close() {
            try {
                // Preserve the empty reply for commands that produce no output.
                if (!sentChunk || !buffer.isEmpty()) {
                    flush();
                }
            } finally {
                redirect = null;
            }
        }
    }

	// Detect recursion during shutdown
	private static boolean recursive= false;

	private static String msg= "";

    /** @deprecated Legacy recovery API; migrate recovery to the failure handler and report with Error(String). */
    @Deprecated
	public static void Error(int code, String message) throws longjmpException
	{
		if (recursive)
		{
			Sys.Error("recursive error after: " + msg);
		}
		recursive= true;

		msg= message;

		if (code == Defines.ERR_DISCONNECT)
		{
            printToConsole(ConsoleLevel.ERROR, "Error: " + msg + "\n");
			Cmd.ExecuteFunction("cl_drop");
			recursive= false;
			throw new longjmpException("Error: (disconnect) " + msg);
		}
		else if (code == Defines.ERR_DROP)
		{
            printToConsole(ConsoleLevel.ERROR, "********************\nERROR: " + msg + "\n********************\n");
			Cmd.ExecuteFunction("sv_shutdown", "Server crashed: " + msg, "false");
			Cmd.ExecuteFunction("cl_drop");
			recursive= false;
			throw new longjmpException("Error: (drop) " + msg);
		}
		else
		{
            printToConsole(ConsoleLevel.ERROR, "ERROR: " + msg + "\n");
			Cmd.ExecuteFunction("sv_shutdown", "Server fatal crashed: " + msg, "false");
			Cmd.ExecuteFunction("cl_shutdown");
		}

		Sys.Error(msg);
	}

    public static void SetConsoleSink(ConsoleSink sink) {
        consoleSink = sink;
    }

    public static void ClearConsoleSink(ConsoleSink sink) {
        if (consoleSink == sink) {
            consoleSink = null;
        }
    }

    private static void printToConsole(ConsoleLevel level, String msg) {
        Redirect capture = redirect;
        if (capture != null && capture.owner == Thread.currentThread() && !capture.flushing) {
            capture.append(msg);
            return;
        }

        if (consoleSink != null) {
            consoleSink.print(level, msg);
        } else {
		    Cmd.ExecuteFunction("console_print", msg);
        }

		// also echo to debugging console
		// todo: use proper logging
		System.out.print(msg);
	}

	public static String StripExtension(String string) {
		int i = string.lastIndexOf('.');
		if (i < 0)
			return string;
		return string.substring(0, i);
	}
}
