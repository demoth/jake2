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

// Created on 29.12.2003 by RST.
// $Id: TestCMD.java,v 1.1 2003-12-29 16:56:23 rst Exp $

package jake2.qcommon;

import java.io.*;

import jake2.*;
import jake2.client.*;
import jake2.game.*;
import jake2.qcommon.*;
import jake2.render.*;
import jake2.server.*;

public class TestCMD {

	public static void main(String args[]) {
		try {
			Cmd.Init();
			Cmd.RemoveCommand("exec");

			BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
			System.out.println("Give some commands:");

			while (true) {
				System.out.println("#");
				String line = br.readLine();
				Cmd.ExecuteString(line);
			}
		}
		catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
	}
}
