/*
 * Sys.java
 * Copyright (C) 2003
 * 
 * $Id: Sys.java,v 1.8 2004-01-08 22:38:16 rst Exp $
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
package jake2.sys;

import java.io.File;
import java.io.FilenameFilter;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import jake2.client.CL;
import jake2.qcommon.Com;

/**
 * Sys
 */
public final class Sys {
	
	/**
	 * @param error
	 */
	public static void Error(String error) {
//		00099         va_list     argptr;
//		00100         char        string[1024];
//		00101 
//		00102 // change stdin to non blocking
//		00103         fcntl (0, F_SETFL, fcntl (0, F_GETFL, 0) & ~FNDELAY);
//		00104 
		CL.Shutdown();
//		00106     
//		00107         va_start (argptr,error);
//		00108         vsprintf (string,error,argptr);
//		00109         va_end (argptr);
//		00110         fprintf(stderr, "Error: %s\n", string);
		System.err.println("Error: " + error);
//		00111 

		int b = 0;
		try
		{
			throw new Exception("Call Stack:");
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		System.exit(1);
			
	}
	
	public static void Quit() {
		CL.Shutdown();
//	00093         fcntl (0, F_SETFL, fcntl (0, F_GETFL, 0) & ~FNDELAY);
		System.exit(0);
	}
	
	public static File[] FindAll(String path, int musthave, int canthave) {
		String findbase = path;
		String p = null;
		String findpattern = null;
		
		int index = 0;
		if ((index = path.lastIndexOf('/')) > 0) {
			findbase = path.substring(0, index);
			findpattern = path.substring(index+1, path.length());
		} else {
			findpattern = "*";
		}
		
		if (findpattern.equals("*.*")) {
			findpattern = "*";
		}
		
		File fdir = new File(findbase);
		
		if (!fdir.exists()) return null;
		
		FilenameFilter filter = new FileFilter(findpattern, musthave, canthave);
		
		return fdir.listFiles(filter);
	}
	
	/**
	 *  Match the pattern findpattern against the filename.
	 * 
	 *  In the pattern string, `*' matches any sequence of characters,
	 *  `?' matches any character, [SET] matches any character in the specified set,
	 * [!SET] matches any character not in the specified set.
	 * A set is composed of characters or ranges; a range looks like
	 * character hyphen character (as in 0-9 or A-Z).
	 * [0-9a-zA-Z_] is the set of characters allowed in C identifiers.
	 * Any other character in the pattern must be matched exactly.
	 * To suppress the special syntactic significance of any of `[]*?!-\',
	 * and match the character exactly, precede it with a `\'.
	*/
	static class FileFilter implements FilenameFilter {
		
		String regexpr;
		int musthave, canthave;
		
		FileFilter(String findpattern, int musthave, int canthave) {
			this.regexpr = convert2regexpr(findpattern);
			this.musthave = musthave;
			this.canthave = canthave;
			
		}
		
		/* 
		 * @see java.io.FilenameFilter#accept(java.io.File, java.lang.String)
		 */
		public boolean accept(File dir, String name) {
			if (name.matches(regexpr)) {
				return CompareAttributes(dir, musthave, canthave);
			}
			return false;
		}
		
		String convert2regexpr(String pattern) {
			
			StringBuffer sb = new StringBuffer();
			
			char c;
			boolean escape = false;
			
			String subst;
			
			// convert pattern
			for (int i = 0; i < pattern.length(); i++) {
				c = pattern.charAt(i);
				subst = null;
				switch(c) {
					case '*': subst = (!escape) ? ".*" : "*";
					break;
					case '.': subst = (!escape) ? "\\." : ".";
					break;
					case '!':  subst = (!escape) ? "^" : "!";
					break;
					case '?':  subst = (!escape) ? "." : "?";
					break;
					case '\\': escape = !escape;
					break;
					default: escape = false; 
				}
				if (subst != null) {
					sb.append(subst);
					escape = false;
				} else sb.append(c);
			}
			
			// the converted pattern
			String regexpr = sb.toString();

			System.out.println("pattern: " + pattern + " regexpr: " + regexpr);
			try {
				Pattern.compile(regexpr);
			} catch (PatternSyntaxException e) {
				Com.Printf("invalid file pattern ( *.* is used instead )\n");
				return ".*"; // the default
			}
			return regexpr;
		}
		
		boolean CompareAttributes(File dir, int musthave, int canthave) {
			// TODO implement or check the CompareAttributes() function
			return true;
		}
		
	}
	
}
