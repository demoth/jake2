/*
 * Cbuf.java
 * Copyright (C) 2003
 * 
 * $Id: Cbuf.java,v 1.7 2003-12-02 10:07:36 hoz Exp $
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

import jake2.Globals;

/**
 * Cbuf
 * TODO complete Cbuf interface
 */
public final class Cbuf {

	/**
	 * 
	 */
	public static void Init() {
		SZ.Init(Globals.cmd_text, Globals.cmd_text_buf, Globals.cmd_text_buf.length);
	}

	/**
	 * @param text
	 */
	public static void InsertText(String text) {
	}
	
	/**
	 * @param clear
	 */
	static void AddEarlyCommands(boolean clear) {
 
		for (int i=0 ; i < Com.Argc() ; i++) {
			String s = Com.Argv(i);
				if (!s.equals("+set"))
					continue;
			Cbuf.AddText("set " + Com.Argv(i+1) + " " + Com.Argv(i+2) +"n");
			if (clear) {
				Com.ClearArgv(i);
				Com.ClearArgv(i+1);
				Com.ClearArgv(i+2);
			}
			i+=2;
		}
	}
	
	/**
	 * @return
	 */
	static boolean AddLateCommands() {
		return true;
	}
	
	/**
	 * @param text
	 */
	static void AddText(String text) {
		int l = text.length();

		if (Globals.cmd_text.cursize + l >= Globals.cmd_text.maxsize) {
			Com.Printf("Cbuf_AddText: overflow\n");
			return;
		}
		SZ.Write(Globals.cmd_text, text.getBytes(), l);		
	}
	
	/**
	 * 
	 */
	public static void Execute() {
	}
}
