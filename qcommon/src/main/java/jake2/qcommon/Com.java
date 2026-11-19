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
import jake2.qcommon.util.PrintfFormat;
import jake2.qcommon.util.Vargs;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Engine messaging and plain-text redirection.
 */
public final class Com
{
    public enum ConsoleLevel {
        PRINT,
        INFO,
        DEBUG,
        WARN,
        ERROR
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
        emit(ConsoleLevel.PRINT, message, endLine);
    }

    /** Sparse lifecycle and significant system-state messages. */
    public static void Info(String message) {
        emit(ConsoleLevel.INFO, message, true);
    }

    /** Report a warning without changing control flow. */
    public static void Warn(String message) {
        emit(ConsoleLevel.WARN, message, true);
    }

    /** Report a failure only. Recovery belongs to the caller handling that failure. */
    public static void Error(String message) {
        emit(ConsoleLevel.ERROR, message, true);
    }

    /** Developer diagnostics. Class attribution is deferred until a simpler API is chosen. */
    public static void Debug(String message) {
        if (isDeveloperEnabled()) {
            emit(ConsoleLevel.DEBUG, message, true);
        }
    }

    private static boolean isDeveloperEnabled() {
        return Globals.developer != null && Globals.developer.getTypedValue();
    }

    private static void emit(ConsoleLevel level, String message, boolean endLine) {
        // Preserve existing line endings and intentional blank lines, including CR-only input.
        if (endLine && !message.endsWith("\n") && !message.endsWith("\r")) {
            message += "\n";
        }
        printToConsole(level, message);
    }
    
    /** @deprecated Use Print/Info according to message purpose; preserve intentional fragments explicitly. */
    @Deprecated
	public static void Printf(int print_level, String fmt) {
		Printf(print_level, fmt, null);
	}

    /** @deprecated Use Print/Info according to message purpose; preserve intentional fragments explicitly. */
    @Deprecated
	public static void Printf(int print_level, String fmt, Vargs vargs) {
		if (print_level == Defines.PRINT_ALL)
			Printf(fmt, vargs);
		else
			DPrintf(fmt, vargs);
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

	// helper class to replace the pointer-pointer
	@Deprecated
	public static class ParseHelp
	{
		public ParseHelp(String in)
		{
			if (in == null)
			{
				data= null;
				length = 0;
			}
			else
			{
				data= in.toCharArray();
				length = data.length;
			}
			index= 0;
		}

		public ParseHelp(String in, int offset)
		{
			data = in.toCharArray();
			index = offset;
			length = data.length;
		}

		char getchar()
		{
		    if (index < length) {
		        return data[index];
		    }
		    return 0;			
		}

		public char nextchar()
		{
			// faster than if
		    index++;
		    if (index < length) {
		        return data[index];
		    }
		    return 0;
		}
		
		char prevchar() {
			if (index > 0) 
			{
				index--;
				return data[index];
			}
			return 0;					
		}

		public boolean isEof()
		{
			return index >= length;
		}

		public int index;
		public char data[];
		private int length;

		char skipwhites()
		{
			char c = 0;
			while ( index < length && ((c= data[index]) <= ' ') && c != 0)
				index++;
			return c;
		}

		public char skipwhitestoeol()
		{
			char c = 0;
			while ( index < length &&((c= data[index]) <= ' ') && c != '\n' && c != 0)
				index++;
			return c;
		}

		char skiptoeol()
		{
			char c = 0;
			while ( index < length &&(c= data[index]) != '\n' && c != 0)
				index++;
			return c;
		}
	}

	private static char com_token[]= new char[Defines.MAX_TOKEN_CHARS];

	// See GameSpanw.ED_ParseEdict() to see how to use it now.
	@Deprecated
	public static String Parse(ParseHelp hlp) {
		int c;
		int len = 0;

		if (hlp.data == null) {
			return "";
		}

		while (true) {
			//	   skip whitespace
			hlp.skipwhites();
			if (hlp.isEof()) {
			    hlp.data = null;
			    return "";
			}

			//	   skip // comments
			if (hlp.getchar() == '/') {
				if (hlp.nextchar() == '/') {
					hlp.skiptoeol();
					// goto skip whitespace
					continue;
				} else {
					hlp.prevchar();
					break;
				}
			} else
				break;
		}

		//	   handle quoted strings specially
		if (hlp.getchar() == '\"') {
			hlp.nextchar();
			while (true) {
				c = hlp.getchar();
				hlp.nextchar();
				if (c == '\"' || c == 0) {
					return new String(com_token, 0, len);
				}
				if (len < Defines.MAX_TOKEN_CHARS) {
					com_token[len] = (char) c;
					len++;
				}
			}
		}

		//	   parse a regular word
		c = hlp.getchar();
		do {
			if (len < Defines.MAX_TOKEN_CHARS) {
				com_token[len] = (char) c;
				len++;
			}
			c = hlp.nextchar();
		} while (c > 32);

		if (len == Defines.MAX_TOKEN_CHARS) {
			Com.Printf("Token exceeded " + Defines.MAX_TOKEN_CHARS + " chars, discarded.\n");
			len = 0;
		}

		return new String(com_token, 0, len);
	}

    /** @deprecated Legacy recovery API; migrate recovery to the failure handler and report with Error(String). */
    @Deprecated
	public static void Error(int code, String fmt) throws longjmpException
	{
		Error(code, fmt, null);
	}

    /** @deprecated Legacy recovery API; migrate recovery to the failure handler and report with Error(String). */
    @Deprecated
	public static void Error(int code, String fmt, Vargs vargs) throws longjmpException
	{
		if (recursive)
		{
			Sys.Error("recursive error after: " + msg);
		}
		recursive= true;

		msg= sprintf(fmt, vargs);

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

    /** @deprecated Use Debug according to message purpose; preserve intentional fragments explicitly. */
    @Deprecated
	public static void DPrintf(String fmt)
	{
		DPrintf(fmt, null);
	}
	
    /** @deprecated Use Debug according to message purpose; preserve intentional fragments explicitly. */
    @Deprecated
	public static void dprintln(String fmt)
	{
		DPrintf(fmt + "\n", null);
	}

    /** @deprecated Use Print/Info according to message purpose; preserve intentional fragments explicitly. */
    @Deprecated
	public static void Printf(String fmt)
	{
		Printf(fmt, null);
	}

    /** @deprecated Use Debug according to message purpose; preserve intentional fragments explicitly. */
    @Deprecated
	public static void DPrintf(String fmt, Vargs vargs)
	{
		if (!isDeveloperEnabled())
			return; // don't confuse non-developers with techie stuff...
        // Legacy fragments retain their boundaries until callers migrate to Debug.
        printToConsole(ConsoleLevel.DEBUG, sprintf(fmt, vargs));
	}

    /** @deprecated Use Print/Info according to message purpose; preserve intentional fragments explicitly. */
    @Deprecated
	public static void Printf(String fmt, Vargs vargs)
	{
		String msg= sprintf(fmt, vargs);
        printToConsole(ConsoleLevel.INFO, msg);
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

    /** @deprecated Use Print/Info according to message purpose; preserve intentional fragments explicitly. */
    @Deprecated
	public static void Println(String fmt)
	{
		Printf(fmt + "\n");
	}

	@Deprecated
	public static String sprintf(String fmt, Vargs vargs) {
		if (vargs == null || vargs.size() == 0) {
			return fmt;
		} else {
			return new PrintfFormat(fmt).sprintf(vargs.toArray());
		}
	}

	public static String StripExtension(String string) {
		int i = string.lastIndexOf('.');
		if (i < 0)
			return string;
		return string.substring(0, i);
	}
}
