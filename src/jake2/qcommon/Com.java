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

import jake2.Defines;
import jake2.Globals;
import jake2.client.CL;
import jake2.client.Console;
import jake2.qcommon.filesystem.FS;
import jake2.server.SV_MAIN;
import jake2.sys.Sys;
import jake2.util.PrintfFormat;
import jake2.util.Vargs;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Com
 *
 */
public final class Com
{

    static String debugContext = "";
    static String _debugContext = "";
    
	static int com_argc;
	static String[] com_argv= new String[Defines.MAX_NUM_ARGVS];

	public static void Printf(int print_level, String fmt) {
		Printf(print_level, fmt, null);
	}

	public static void Printf(int print_level, String fmt, Vargs vargs) {
		if (print_level == Defines.PRINT_ALL)
			Printf(fmt, vargs);
		else
			DPrintf(fmt, vargs);
	}

	public abstract static class RD_Flusher
	{
		public abstract void rd_flush(int target, StringBuffer buffer);
	}

	static int rd_target;
	static StringBuffer rd_buffer;
	static int rd_buffersize;
	static RD_Flusher rd_flusher;

	public static void BeginRedirect(int target, StringBuffer buffer, int buffersize, RD_Flusher flush)
	{
		if (0 == target || null == buffer || 0 == buffersize || null == flush)
			return;

		rd_target= target;
		rd_buffer= buffer;
		rd_buffersize= buffersize;
		rd_flusher= flush;

		rd_buffer.setLength(0);
	}

	public static void EndRedirect()
	{
		rd_flusher.rd_flush(rd_target, rd_buffer);

		rd_target= 0;
		rd_buffer= null;
		rd_buffersize= 0;
		rd_flusher= null;
	}

	static boolean recursive= false;

	static String msg= "";

	// helper class to replace the pointer-pointer
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

		public char getchar()
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
		
		public char prevchar() {
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

		public char skipwhites()
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

		public char skiptoeol()
		{
			char c = 0;
			while ( index < length &&(c= data[index]) != '\n' && c != 0)
				index++;
			return c;
		}
	}

	public static char com_token[]= new char[Defines.MAX_TOKEN_CHARS];

	// See GameSpanw.ED_ParseEdict() to see how to use it now.
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

	public static void Error(int code, String fmt) throws longjmpException
	{
		Error(code, fmt, null);
	}

	public static void Error(int code, String fmt, Vargs vargs) throws longjmpException
	{
		// va_list argptr;
		// static char msg[MAXPRINTMSG];

		if (recursive)
		{
			Sys.Error("recursive error after: " + msg);
		}
		recursive= true;

		msg= sprintf(fmt, vargs);

		if (code == Defines.ERR_DISCONNECT)
		{
			CL.Drop();
			recursive= false;
			throw new longjmpException();
		}
		else if (code == Defines.ERR_DROP)
		{
			Com.Printf("********************\nERROR: " + msg + "\n********************\n");
			SV_MAIN.SV_Shutdown("Server crashed: " + msg + "\n", false);
			CL.Drop();
			recursive= false;
			throw new longjmpException();
		}
		else
		{
			SV_MAIN.SV_Shutdown("Server fatal crashed: %s" + msg + "\n", false);
			CL.Shutdown();
		}

		Sys.Error(msg);
	}

	/**
	 * Com_InitArgv checks the number of command line arguments
	 * and copies all arguments with valid length into com_argv.
	 */
	static void InitArgv(String[] args) throws longjmpException
	{

		if (args.length > Defines.MAX_NUM_ARGVS)
		{
			Com.Error(Defines.ERR_FATAL, "argc > MAX_NUM_ARGVS");
		}

		Com.com_argc= args.length;
		for (int i= 0; i < Com.com_argc; i++)
		{
			if (args[i].length() >= Defines.MAX_TOKEN_CHARS)
				Com.com_argv[i]= "";
			else
				Com.com_argv[i]= args[i];
		}
	}

	public static void DPrintf(String fmt)
	{
	    _debugContext = debugContext;
		DPrintf(fmt, null);
		_debugContext = "";
	}
	
	public static void dprintln(String fmt)
	{
		DPrintf(_debugContext + fmt + "\n", null);
	}

	public static void Printf(String fmt)
	{
		Printf(_debugContext + fmt, null);
	}

	public static void DPrintf(String fmt, Vargs vargs)
	{
		if (Globals.developer == null || Globals.developer.value == 0)
			return; // don't confuse non-developers with techie stuff...
		_debugContext = debugContext;
		Printf(fmt, vargs);
		_debugContext="";
	}

	/** Prints out messages, which can also be redirected to a remote client. */
	public static void Printf(String fmt, Vargs vargs)
	{
		String msg= sprintf(_debugContext + fmt, vargs);
		if (rd_target != 0)
		{
			if ((msg.length() + rd_buffer.length()) > (rd_buffersize - 1))
			{
				rd_flusher.rd_flush(rd_target, rd_buffer);
				rd_buffer.setLength(0);
			}
			rd_buffer.append(msg);
			return;
		}

		Console.Print(msg);

		// also echo to debugging console
		Sys.ConsoleOutput(msg);

		// logfile
		if (Globals.logfile_active != null && Globals.logfile_active.value != 0)
		{
			String name;

			if (Globals.logfile == null)
			{
				name= FS.Gamedir() + "/qconsole.log";
				if (Globals.logfile_active.value > 2)
					try
					{
						Globals.logfile = new RandomAccessFile(name, "rw");
						Globals.logfile.seek(Globals.logfile.length());
					}
					catch (Exception e)
					{
						// TODO: do quake2 error handling!
						e.printStackTrace();
					}
				else
					try
					{
						Globals.logfile= new RandomAccessFile(name, "rw");
					}
					catch (FileNotFoundException e1)
					{
						// TODO: do quake2 error handling!
						e1.printStackTrace();
					}
			}
			if (Globals.logfile != null)
				try
				{
					Globals.logfile.writeChars(msg);
				}
				catch (IOException e)
				{
					// TODO: do quake2 error handling!
					e.printStackTrace();
				}
			if (Globals.logfile_active.value > 1); // do nothing
			// fflush (logfile);		// force it to save every time
		}
	}

	public static void Println(String fmt)
	{
		Printf(_debugContext + fmt + "\n");
	}

	public static String sprintf(String fmt, Vargs vargs)
	{
		String msg= "";
		if (vargs == null || vargs.size() == 0)
		{
			msg= fmt;
		}
		else
		{
			msg= new PrintfFormat(fmt).sprintf(vargs.toArray());
		}
		return msg;
	}

	public static int Argc()
	{
		return Com.com_argc;
	}

	public static String Argv(int arg)
	{
		if (arg < 0 || arg >= Com.com_argc || Com.com_argv[arg].length() < 1)
			return "";
		return Com.com_argv[arg];
	}

	public static void ClearArgv(int arg)
	{
		if (arg < 0 || arg >= Com.com_argc || Com.com_argv[arg].length() < 1)
			return;
		Com.com_argv[arg]= "";
	}

	public static void Quit()
	{
		SV_MAIN.SV_Shutdown("Server quit\n", false);
		CL.Shutdown();

		if (Globals.logfile != null)
		{
			try
			{
				Globals.logfile.close();
			}
			catch (IOException e)
			{
			}
			Globals.logfile= null;
		}

		Sys.Quit();
	}

	public static void SetServerState(int i)
	{
		Globals.server_state= i;
	}

	public static int BlockChecksum(byte[] buf, int length)
	{
		return MD4.Com_BlockChecksum(buf, length);
	}

	public static String StripExtension(String string) {
		int i = string.lastIndexOf('.');
		if (i < 0)
			return string;
		return string.substring(0, i);
	}

	/**
	 * CRC table. 
	 */
	static byte chktbl[] = { (byte) 0x84, (byte) 0x47, (byte) 0x51, (byte) 0xc1,
            (byte) 0x93, (byte) 0x22, (byte) 0x21, (byte) 0x24, (byte) 0x2f,
            (byte) 0x66, (byte) 0x60, (byte) 0x4d, (byte) 0xb0, (byte) 0x7c,
            (byte) 0xda, (byte) 0x88, (byte) 0x54, (byte) 0x15, (byte) 0x2b,
            (byte) 0xc6, (byte) 0x6c, (byte) 0x89, (byte) 0xc5, (byte) 0x9d,
            (byte) 0x48, (byte) 0xee, (byte) 0xe6, (byte) 0x8a, (byte) 0xb5,
            (byte) 0xf4, (byte) 0xcb, (byte) 0xfb, (byte) 0xf1, (byte) 0x0c,
            (byte) 0x2e, (byte) 0xa0, (byte) 0xd7, (byte) 0xc9, (byte) 0x1f,
            (byte) 0xd6, (byte) 0x06, (byte) 0x9a, (byte) 0x09, (byte) 0x41,
            (byte) 0x54, (byte) 0x67, (byte) 0x46, (byte) 0xc7, (byte) 0x74,
            (byte) 0xe3, (byte) 0xc8, (byte) 0xb6, (byte) 0x5d, (byte) 0xa6,
            (byte) 0x36, (byte) 0xc4, (byte) 0xab, (byte) 0x2c, (byte) 0x7e,
            (byte) 0x85, (byte) 0xa8, (byte) 0xa4, (byte) 0xa6, (byte) 0x4d,
            (byte) 0x96, (byte) 0x19, (byte) 0x19, (byte) 0x9a, (byte) 0xcc,
            (byte) 0xd8, (byte) 0xac, (byte) 0x39, (byte) 0x5e, (byte) 0x3c,
            (byte) 0xf2, (byte) 0xf5, (byte) 0x5a, (byte) 0x72, (byte) 0xe5,
            (byte) 0xa9, (byte) 0xd1, (byte) 0xb3, (byte) 0x23, (byte) 0x82,
            (byte) 0x6f, (byte) 0x29, (byte) 0xcb, (byte) 0xd1, (byte) 0xcc,
            (byte) 0x71, (byte) 0xfb, (byte) 0xea, (byte) 0x92, (byte) 0xeb,
            (byte) 0x1c, (byte) 0xca, (byte) 0x4c, (byte) 0x70, (byte) 0xfe,
            (byte) 0x4d, (byte) 0xc9, (byte) 0x67, (byte) 0x43, (byte) 0x47,
            (byte) 0x94, (byte) 0xb9, (byte) 0x47, (byte) 0xbc, (byte) 0x3f,
            (byte) 0x01, (byte) 0xab, (byte) 0x7b, (byte) 0xa6, (byte) 0xe2,
            (byte) 0x76, (byte) 0xef, (byte) 0x5a, (byte) 0x7a, (byte) 0x29,
            (byte) 0x0b, (byte) 0x51, (byte) 0x54, (byte) 0x67, (byte) 0xd8,
            (byte) 0x1c, (byte) 0x14, (byte) 0x3e, (byte) 0x29, (byte) 0xec,
            (byte) 0xe9, (byte) 0x2d, (byte) 0x48, (byte) 0x67, (byte) 0xff,
            (byte) 0xed, (byte) 0x54, (byte) 0x4f, (byte) 0x48, (byte) 0xc0,
            (byte) 0xaa, (byte) 0x61, (byte) 0xf7, (byte) 0x78, (byte) 0x12,
            (byte) 0x03, (byte) 0x7a, (byte) 0x9e, (byte) 0x8b, (byte) 0xcf,
            (byte) 0x83, (byte) 0x7b, (byte) 0xae, (byte) 0xca, (byte) 0x7b,
            (byte) 0xd9, (byte) 0xe9, (byte) 0x53, (byte) 0x2a, (byte) 0xeb,
            (byte) 0xd2, (byte) 0xd8, (byte) 0xcd, (byte) 0xa3, (byte) 0x10,
            (byte) 0x25, (byte) 0x78, (byte) 0x5a, (byte) 0xb5, (byte) 0x23,
            (byte) 0x06, (byte) 0x93, (byte) 0xb7, (byte) 0x84, (byte) 0xd2,
            (byte) 0xbd, (byte) 0x96, (byte) 0x75, (byte) 0xa5, (byte) 0x5e,
            (byte) 0xcf, (byte) 0x4e, (byte) 0xe9, (byte) 0x50, (byte) 0xa1,
            (byte) 0xe6, (byte) 0x9d, (byte) 0xb1, (byte) 0xe3, (byte) 0x85,
            (byte) 0x66, (byte) 0x28, (byte) 0x4e, (byte) 0x43, (byte) 0xdc,
            (byte) 0x6e, (byte) 0xbb, (byte) 0x33, (byte) 0x9e, (byte) 0xf3,
            (byte) 0x0d, (byte) 0x00, (byte) 0xc1, (byte) 0xcf, (byte) 0x67,
            (byte) 0x34, (byte) 0x06, (byte) 0x7c, (byte) 0x71, (byte) 0xe3,
            (byte) 0x63, (byte) 0xb7, (byte) 0xb7, (byte) 0xdf, (byte) 0x92,
            (byte) 0xc4, (byte) 0xc2, (byte) 0x25, (byte) 0x5c, (byte) 0xff,
            (byte) 0xc3, (byte) 0x6e, (byte) 0xfc, (byte) 0xaa, (byte) 0x1e,
            (byte) 0x2a, (byte) 0x48, (byte) 0x11, (byte) 0x1c, (byte) 0x36,
            (byte) 0x68, (byte) 0x78, (byte) 0x86, (byte) 0x79, (byte) 0x30,
            (byte) 0xc3, (byte) 0xd6, (byte) 0xde, (byte) 0xbc, (byte) 0x3a,
            (byte) 0x2a, (byte) 0x6d, (byte) 0x1e, (byte) 0x46, (byte) 0xdd,
            (byte) 0xe0, (byte) 0x80, (byte) 0x1e, (byte) 0x44, (byte) 0x3b,
            (byte) 0x6f, (byte) 0xaf, (byte) 0x31, (byte) 0xda, (byte) 0xa2,
            (byte) 0xbd, (byte) 0x77, (byte) 0x06, (byte) 0x56, (byte) 0xc0,
            (byte) 0xb7, (byte) 0x92, (byte) 0x4b, (byte) 0x37, (byte) 0xc0,
            (byte) 0xfc, (byte) 0xc2, (byte) 0xd5, (byte) 0xfb, (byte) 0xa8,
            (byte) 0xda, (byte) 0xf5, (byte) 0x57, (byte) 0xa8, (byte) 0x18,
            (byte) 0xc0, (byte) 0xdf, (byte) 0xe7, (byte) 0xaa, (byte) 0x2a,
            (byte) 0xe0, (byte) 0x7c, (byte) 0x6f, (byte) 0x77, (byte) 0xb1,
            (byte) 0x26, (byte) 0xba, (byte) 0xf9, (byte) 0x2e, (byte) 0x1d,
            (byte) 0x16, (byte) 0xcb, (byte) 0xb8, (byte) 0xa2, (byte) 0x44,
            (byte) 0xd5, (byte) 0x2f, (byte) 0x1a, (byte) 0x79, (byte) 0x74,
            (byte) 0x87, (byte) 0x4b, (byte) 0x00, (byte) 0xc9, (byte) 0x4a,
            (byte) 0x3a, (byte) 0x65, (byte) 0x8f, (byte) 0xe6, (byte) 0x5d,
            (byte) 0xe5, (byte) 0x0a, (byte) 0x77, (byte) 0xd8, (byte) 0x1a,
            (byte) 0x14, (byte) 0x41, (byte) 0x75, (byte) 0xb1, (byte) 0xe2,
            (byte) 0x50, (byte) 0x2c, (byte) 0x93, (byte) 0x38, (byte) 0x2b,
            (byte) 0x6d, (byte) 0xf3, (byte) 0xf6, (byte) 0xdb, (byte) 0x1f,
            (byte) 0xcd, (byte) 0xff, (byte) 0x14, (byte) 0x70, (byte) 0xe7,
            (byte) 0x16, (byte) 0xe8, (byte) 0x3d, (byte) 0xf0, (byte) 0xe3,
            (byte) 0xbc, (byte) 0x5e, (byte) 0xb6, (byte) 0x3f, (byte) 0xcc,
            (byte) 0x81, (byte) 0x24, (byte) 0x67, (byte) 0xf3, (byte) 0x97,
            (byte) 0x3b, (byte) 0xfe, (byte) 0x3a, (byte) 0x96, (byte) 0x85,
            (byte) 0xdf, (byte) 0xe4, (byte) 0x6e, (byte) 0x3c, (byte) 0x85,
            (byte) 0x05, (byte) 0x0e, (byte) 0xa3, (byte) 0x2b, (byte) 0x07,
            (byte) 0xc8, (byte) 0xbf, (byte) 0xe5, (byte) 0x13, (byte) 0x82,
            (byte) 0x62, (byte) 0x08, (byte) 0x61, (byte) 0x69, (byte) 0x4b,
            (byte) 0x47, (byte) 0x62, (byte) 0x73, (byte) 0x44, (byte) 0x64,
            (byte) 0x8e, (byte) 0xe2, (byte) 0x91, (byte) 0xa6, (byte) 0x9a,
            (byte) 0xb7, (byte) 0xe9, (byte) 0x04, (byte) 0xb6, (byte) 0x54,
            (byte) 0x0c, (byte) 0xc5, (byte) 0xa9, (byte) 0x47, (byte) 0xa6,
            (byte) 0xc9, (byte) 0x08, (byte) 0xfe, (byte) 0x4e, (byte) 0xa6,
            (byte) 0xcc, (byte) 0x8a, (byte) 0x5b, (byte) 0x90, (byte) 0x6f,
            (byte) 0x2b, (byte) 0x3f, (byte) 0xb6, (byte) 0x0a, (byte) 0x96,
            (byte) 0xc0, (byte) 0x78, (byte) 0x58, (byte) 0x3c, (byte) 0x76,
            (byte) 0x6d, (byte) 0x94, (byte) 0x1a, (byte) 0xe4, (byte) 0x4e,
            (byte) 0xb8, (byte) 0x38, (byte) 0xbb, (byte) 0xf5, (byte) 0xeb,
            (byte) 0x29, (byte) 0xd8, (byte) 0xb0, (byte) 0xf3, (byte) 0x15,
            (byte) 0x1e, (byte) 0x99, (byte) 0x96, (byte) 0x3c, (byte) 0x5d,
            (byte) 0x63, (byte) 0xd5, (byte) 0xb1, (byte) 0xad, (byte) 0x52,
            (byte) 0xb8, (byte) 0x55, (byte) 0x70, (byte) 0x75, (byte) 0x3e,
            (byte) 0x1a, (byte) 0xd5, (byte) 0xda, (byte) 0xf6, (byte) 0x7a,
            (byte) 0x48, (byte) 0x7d, (byte) 0x44, (byte) 0x41, (byte) 0xf9,
            (byte) 0x11, (byte) 0xce, (byte) 0xd7, (byte) 0xca, (byte) 0xa5,
            (byte) 0x3d, (byte) 0x7a, (byte) 0x79, (byte) 0x7e, (byte) 0x7d,
            (byte) 0x25, (byte) 0x1b, (byte) 0x77, (byte) 0xbc, (byte) 0xf7,
            (byte) 0xc7, (byte) 0x0f, (byte) 0x84, (byte) 0x95, (byte) 0x10,
            (byte) 0x92, (byte) 0x67, (byte) 0x15, (byte) 0x11, (byte) 0x5a,
            (byte) 0x5e, (byte) 0x41, (byte) 0x66, (byte) 0x0f, (byte) 0x38,
            (byte) 0x03, (byte) 0xb2, (byte) 0xf1, (byte) 0x5d, (byte) 0xf8,
            (byte) 0xab, (byte) 0xc0, (byte) 0x02, (byte) 0x76, (byte) 0x84,
            (byte) 0x28, (byte) 0xf4, (byte) 0x9d, (byte) 0x56, (byte) 0x46,
            (byte) 0x60, (byte) 0x20, (byte) 0xdb, (byte) 0x68, (byte) 0xa7,
            (byte) 0xbb, (byte) 0xee, (byte) 0xac, (byte) 0x15, (byte) 0x01,
            (byte) 0x2f, (byte) 0x20, (byte) 0x09, (byte) 0xdb, (byte) 0xc0,
            (byte) 0x16, (byte) 0xa1, (byte) 0x89, (byte) 0xf9, (byte) 0x94,
            (byte) 0x59, (byte) 0x00, (byte) 0xc1, (byte) 0x76, (byte) 0xbf,
            (byte) 0xc1, (byte) 0x4d, (byte) 0x5d, (byte) 0x2d, (byte) 0xa9,
            (byte) 0x85, (byte) 0x2c, (byte) 0xd6, (byte) 0xd3, (byte) 0x14,
            (byte) 0xcc, (byte) 0x02, (byte) 0xc3, (byte) 0xc2, (byte) 0xfa,
            (byte) 0x6b, (byte) 0xb7, (byte) 0xa6, (byte) 0xef, (byte) 0xdd,
            (byte) 0x12, (byte) 0x26, (byte) 0xa4, (byte) 0x63, (byte) 0xe3,
            (byte) 0x62, (byte) 0xbd, (byte) 0x56, (byte) 0x8a, (byte) 0x52,
            (byte) 0x2b, (byte) 0xb9, (byte) 0xdf, (byte) 0x09, (byte) 0xbc,
            (byte) 0x0e, (byte) 0x97, (byte) 0xa9, (byte) 0xb0, (byte) 0x82,
            (byte) 0x46, (byte) 0x08, (byte) 0xd5, (byte) 0x1a, (byte) 0x8e,
            (byte) 0x1b, (byte) 0xa7, (byte) 0x90, (byte) 0x98, (byte) 0xb9,
            (byte) 0xbb, (byte) 0x3c, (byte) 0x17, (byte) 0x9a, (byte) 0xf2,
            (byte) 0x82, (byte) 0xba, (byte) 0x64, (byte) 0x0a, (byte) 0x7f,
            (byte) 0xca, (byte) 0x5a, (byte) 0x8c, (byte) 0x7c, (byte) 0xd3,
            (byte) 0x79, (byte) 0x09, (byte) 0x5b, (byte) 0x26, (byte) 0xbb,
            (byte) 0xbd, (byte) 0x25, (byte) 0xdf, (byte) 0x3d, (byte) 0x6f,
            (byte) 0x9a, (byte) 0x8f, (byte) 0xee, (byte) 0x21, (byte) 0x66,
            (byte) 0xb0, (byte) 0x8d, (byte) 0x84, (byte) 0x4c, (byte) 0x91,
            (byte) 0x45, (byte) 0xd4, (byte) 0x77, (byte) 0x4f, (byte) 0xb3,
            (byte) 0x8c, (byte) 0xbc, (byte) 0xa8, (byte) 0x99, (byte) 0xaa,
            (byte) 0x19, (byte) 0x53, (byte) 0x7c, (byte) 0x02, (byte) 0x87,
            (byte) 0xbb, (byte) 0x0b, (byte) 0x7c, (byte) 0x1a, (byte) 0x2d,
            (byte) 0xdf, (byte) 0x48, (byte) 0x44, (byte) 0x06, (byte) 0xd6,
            (byte) 0x7d, (byte) 0x0c, (byte) 0x2d, (byte) 0x35, (byte) 0x76,
            (byte) 0xae, (byte) 0xc4, (byte) 0x5f, (byte) 0x71, (byte) 0x85,
            (byte) 0x97, (byte) 0xc4, (byte) 0x3d, (byte) 0xef, (byte) 0x52,
            (byte) 0xbe, (byte) 0x00, (byte) 0xe4, (byte) 0xcd, (byte) 0x49,
            (byte) 0xd1, (byte) 0xd1, (byte) 0x1c, (byte) 0x3c, (byte) 0xd0,
            (byte) 0x1c, (byte) 0x42, (byte) 0xaf, (byte) 0xd4, (byte) 0xbd,
            (byte) 0x58, (byte) 0x34, (byte) 0x07, (byte) 0x32, (byte) 0xee,
            (byte) 0xb9, (byte) 0xb5, (byte) 0xea, (byte) 0xff, (byte) 0xd7,
            (byte) 0x8c, (byte) 0x0d, (byte) 0x2e, (byte) 0x2f, (byte) 0xaf,
            (byte) 0x87, (byte) 0xbb, (byte) 0xe6, (byte) 0x52, (byte) 0x71,
            (byte) 0x22, (byte) 0xf5, (byte) 0x25, (byte) 0x17, (byte) 0xa1,
            (byte) 0x82, (byte) 0x04, (byte) 0xc2, (byte) 0x4a, (byte) 0xbd,
            (byte) 0x57, (byte) 0xc6, (byte) 0xab, (byte) 0xc8, (byte) 0x35,
            (byte) 0x0c, (byte) 0x3c, (byte) 0xd9, (byte) 0xc2, (byte) 0x43,
            (byte) 0xdb, (byte) 0x27, (byte) 0x92, (byte) 0xcf, (byte) 0xb8,
            (byte) 0x25, (byte) 0x60, (byte) 0xfa, (byte) 0x21, (byte) 0x3b,
            (byte) 0x04, (byte) 0x52, (byte) 0xc8, (byte) 0x96, (byte) 0xba,
            (byte) 0x74, (byte) 0xe3, (byte) 0x67, (byte) 0x3e, (byte) 0x8e,
            (byte) 0x8d, (byte) 0x61, (byte) 0x90, (byte) 0x92, (byte) 0x59,
            (byte) 0xb6, (byte) 0x1a, (byte) 0x1c, (byte) 0x5e, (byte) 0x21,
            (byte) 0xc1, (byte) 0x65, (byte) 0xe5, (byte) 0xa6, (byte) 0x34,
            (byte) 0x05, (byte) 0x6f, (byte) 0xc5, (byte) 0x60, (byte) 0xb1,
            (byte) 0x83, (byte) 0xc1, (byte) 0xd5, (byte) 0xd5, (byte) 0xed,
            (byte) 0xd9, (byte) 0xc7, (byte) 0x11, (byte) 0x7b, (byte) 0x49,
            (byte) 0x7a, (byte) 0xf9, (byte) 0xf9, (byte) 0x84, (byte) 0x47,
            (byte) 0x9b, (byte) 0xe2, (byte) 0xa5, (byte) 0x82, (byte) 0xe0,
            (byte) 0xc2, (byte) 0x88, (byte) 0xd0, (byte) 0xb2, (byte) 0x58,
            (byte) 0x88, (byte) 0x7f, (byte) 0x45, (byte) 0x09, (byte) 0x67,
            (byte) 0x74, (byte) 0x61, (byte) 0xbf, (byte) 0xe6, (byte) 0x40,
            (byte) 0xe2, (byte) 0x9d, (byte) 0xc2, (byte) 0x47, (byte) 0x05,
            (byte) 0x89, (byte) 0xed, (byte) 0xcb, (byte) 0xbb, (byte) 0xb7,
            (byte) 0x27, (byte) 0xe7, (byte) 0xdc, (byte) 0x7a, (byte) 0xfd,
            (byte) 0xbf, (byte) 0xa8, (byte) 0xd0, (byte) 0xaa, (byte) 0x10,
            (byte) 0x39, (byte) 0x3c, (byte) 0x20, (byte) 0xf0, (byte) 0xd3,
            (byte) 0x6e, (byte) 0xb1, (byte) 0x72, (byte) 0xf8, (byte) 0xe6,
            (byte) 0x0f, (byte) 0xef, (byte) 0x37, (byte) 0xe5, (byte) 0x09,
            (byte) 0x33, (byte) 0x5a, (byte) 0x83, (byte) 0x43, (byte) 0x80,
            (byte) 0x4f, (byte) 0x65, (byte) 0x2f, (byte) 0x7c, (byte) 0x8c,
            (byte) 0x6a, (byte) 0xa0, (byte) 0x82, (byte) 0x0c, (byte) 0xd4,
            (byte) 0xd4, (byte) 0xfa, (byte) 0x81, (byte) 0x60, (byte) 0x3d,
            (byte) 0xdf, (byte) 0x06, (byte) 0xf1, (byte) 0x5f, (byte) 0x08,
            (byte) 0x0d, (byte) 0x6d, (byte) 0x43, (byte) 0xf2, (byte) 0xe3,
            (byte) 0x11, (byte) 0x7d, (byte) 0x80, (byte) 0x32, (byte) 0xc5,
            (byte) 0xfb, (byte) 0xc5, (byte) 0xd9, (byte) 0x27, (byte) 0xec,
            (byte) 0xc6, (byte) 0x4e, (byte) 0x65, (byte) 0x27, (byte) 0x76,
            (byte) 0x87, (byte) 0xa6, (byte) 0xee, (byte) 0xee, (byte) 0xd7,
            (byte) 0x8b, (byte) 0xd1, (byte) 0xa0, (byte) 0x5c, (byte) 0xb0,
            (byte) 0x42, (byte) 0x13, (byte) 0x0e, (byte) 0x95, (byte) 0x4a,
            (byte) 0xf2, (byte) 0x06, (byte) 0xc6, (byte) 0x43, (byte) 0x33,
            (byte) 0xf4, (byte) 0xc7, (byte) 0xf8, (byte) 0xe7, (byte) 0x1f,
            (byte) 0xdd, (byte) 0xe4, (byte) 0x46, (byte) 0x4a, (byte) 0x70,
            (byte) 0x39, (byte) 0x6c, (byte) 0xd0, (byte) 0xed, (byte) 0xca,
            (byte) 0xbe, (byte) 0x60, (byte) 0x3b, (byte) 0xd1, (byte) 0x7b,
            (byte) 0x57, (byte) 0x48, (byte) 0xe5, (byte) 0x3a, (byte) 0x79,
            (byte) 0xc1, (byte) 0x69, (byte) 0x33, (byte) 0x53, (byte) 0x1b,
            (byte) 0x80, (byte) 0xb8, (byte) 0x91, (byte) 0x7d, (byte) 0xb4,
            (byte) 0xf6, (byte) 0x17, (byte) 0x1a, (byte) 0x1d, (byte) 0x5a,
            (byte) 0x32, (byte) 0xd6, (byte) 0xcc, (byte) 0x71, (byte) 0x29,
            (byte) 0x3f, (byte) 0x28, (byte) 0xbb, (byte) 0xf3, (byte) 0x5e,
            (byte) 0x71, (byte) 0xb8, (byte) 0x43, (byte) 0xaf, (byte) 0xf8,
            (byte) 0xb9, (byte) 0x64, (byte) 0xef, (byte) 0xc4, (byte) 0xa5,
            (byte) 0x6c, (byte) 0x08, (byte) 0x53, (byte) 0xc7, (byte) 0x00,
            (byte) 0x10, (byte) 0x39, (byte) 0x4f, (byte) 0xdd, (byte) 0xe4,
            (byte) 0xb6, (byte) 0x19, (byte) 0x27, (byte) 0xfb, (byte) 0xb8,
            (byte) 0xf5, (byte) 0x32, (byte) 0x73, (byte) 0xe5, (byte) 0xcb,
            (byte) 0x32, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0 };
	
	static byte chkb[] = new byte [60 + 4];
	
	/**
	 * Calculates a crc checksum-sequence over an array.
	 */
	public static byte BlockSequenceCRCByte(byte base[], int offset, int length, int sequence)
	{
		if (sequence < 0)
			Sys.Error("sequence < 0, this shouldn't happen\n");

		//p_ndx = (sequence % (sizeof(chktbl) - 4));
		int p_ndx = (sequence % (1024 - 4));
		
		//memcpy(chkb, base, length);
		length = Math.min(60, length);
		System.arraycopy(base, offset , chkb, 0, length);
		
		chkb[length] = chktbl[p_ndx + 0];
		chkb[length + 1] = chktbl[p_ndx + 1];
		chkb[length + 2] = chktbl[p_ndx + 2];
		chkb[length + 3] = chktbl[p_ndx + 3];
		
		length += 4;

		// unsigned short
		int crc = CRC.CRC_Block(chkb, length);

		int x = 0;
		for (int n=0; n < length; n++)
			x += chkb[n] & 0xFF;

		crc ^= x;

		return (byte)(crc & 0xFF);
	}

}