/*
 * Sys.java
 * Copyright (C) 2003
 * 
 * $Id: Sys.java,v 1.18 2004-02-01 23:31:38 rst Exp $
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

import jake2.Defines;
import jake2.Globals;
import jake2.client.CL;
import jake2.game.Game;
import jake2.game.game_export_t;
import jake2.game.game_import_t;
import jake2.qcommon.Com;
import jake2.util.Lib;

/**
 * Sys
 */
public final class Sys extends Defines {

	public static void StackTrace() {

		StackTraceElement trace[] = new Throwable().getStackTrace();

		Com.Println("StackTrace:");

		for (int i = 0; i < trace.length; i++)
			Com.Println("" + trace[i]);
	}

	/**
	 * @param error
	 */
	public static void Error(String error) {

		CL.Shutdown();
		//System.err.println("Error: " + error);

		//StackTrace();
		new Exception(error).printStackTrace();
		System.exit(1);

	}

	public static void Quit() {
		CL.Shutdown();

		System.exit(0);
	}

	public static File[] FindAll(String path, int musthave, int canthave) {

		int index = path.lastIndexOf('/');

		if (index != -1) {
			findbase = path.substring(0, index);
			findpattern = path.substring(index + 1, path.length());
		}
		else {
			findbase = path;
			findpattern = "*";
		}

		if (findpattern.equals("*.*")) {
			findpattern = "*";
		}

		File fdir = new File(findbase);

		if (!fdir.exists())
			return null;

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
				switch (c) {
					case '*' :
						subst = (!escape) ? ".*" : "*";
						break;
					case '.' :
						subst = (!escape) ? "\\." : ".";
						break;
					case '!' :
						subst = (!escape) ? "^" : "!";
						break;
					case '?' :
						subst = (!escape) ? "." : "?";
						break;
					case '\\' :
						escape = !escape;
						break;
					default :
						escape = false;
				}
				if (subst != null) {
					sb.append(subst);
					escape = false;
				}
				else
					sb.append(c);
			}

			// the converted pattern
			String regexpr = sb.toString();

			System.out.println("pattern: " + pattern + " regexpr: " + regexpr);
			try {
				Pattern.compile(regexpr);
			}
			catch (PatternSyntaxException e) {
				Com.Printf("invalid file pattern ( *.* is used instead )\n");
				return ".*"; // the default
			}
			return regexpr;
		}

		boolean CompareAttributes(File dir, int musthave, int canthave) {
			// . and .. never match
			String name = dir.getName();

			if (Lib.strcmp(name, ".") == 0 || Lib.strcmp(name, "..") == 0)
				return false;

			return true;

			//was commented out, but you can implement file-attrib searching. rst.
			//use the flags FILE_ISREADABLE, FILE_ISWRITABLE ... 
			//
			//	if (stat(fn, &st) == -1)
			//		return false; // shouldn't happen
			//
			//	if ( ( st.st_mode & S_IFDIR ) && ( canthave & SFF_SUBDIR ) )
			//		return false;
			//
			//	if ( ( musthave & SFF_SUBDIR ) && !( st.st_mode & S_IFDIR ) )
			//		return false;
			//
			//	return true;
		}

	}

	public static long Milliseconds() {
		return Globals.curtime = System.currentTimeMillis();
	}

	//============================================

	static File[] fdir;
	static int fileindex;
	static String findbase;
	static String findpattern;

	public static File FindFirst(String path, int musthave, int canthave) {

		if (fdir != null)
			Sys.Error("Sys_BeginFind without close");

		//	COM_FilePath (path, findbase);

		fdir = FindAll(path, canthave, musthave);
		fileindex =0;
		
		if (fdir == null) return null;

		return FindNext();

		//strcpy(findbase, path);
		//
		//	if ((p = strrchr(findbase, '/')) != NULL) {
		//		*p = 0;
		//		strcpy(findpattern, p + 1);
		//	} else
		//		strcpy(findpattern, "*");
		//
		//	if (strcmp(findpattern, "*.*") == 0)
		//		strcpy(findpattern, "*");

		//		if ((fdir = opendir(findbase)) == NULL)
		//			return NULL;
		//		while ((d = readdir(fdir)) != NULL) {
		//			if (!* findpattern || glob_match(findpattern, d - > d_name)) {
		//				//			if (*findpattern)
		//				//				printf("%s matched %s\n", findpattern, d->d_name);
		//				if (CompareAttributes(findbase, d - > d_name, musthave, canhave)) {
		//					sprintf(findpath, "%s/%s", findbase, d - > d_name);
		//					return findpath;
		//				}
		//			}
		//		}
		//		return null;
	}

	public static File FindNext() {

		if (fileindex >= fdir.length)
			return null;

		return fdir[fileindex++];
	}

	public static void FindClose() {

		if (fdir != null)
			fdir = null;

	}


	public static void UnloadGame()
	{
		//TODO:implement !
		//Com.Error(Defines.ERR_FATAL, "UnloadGame not implemented!");
		
	}
	
	public static void SendKeyEvents() {
		KBD.Update();
 
		// grab frame time 
		Globals.sys_frame_time = Sys.Milliseconds();
	}

	public static game_export_t GetGameAPI(game_import_t gimport)
	{
		//TODO:implement !
		Com.Printf( "scheisse GetGameAPI not implemented!");
		return Game.GetGameApi(gimport);
	}

	public static String GetClipboardData() {
		// TODO: implement!
		return null;
	}

	public static void ConsoleOutput(String msg)
	{
		if (Globals.nostdout != null && Globals.nostdout.value != 0)
			return;

		System.out.print(msg);
	}

}
