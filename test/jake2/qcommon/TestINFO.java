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

package jake2.qcommon;

import jake2.game.Info;

public class TestINFO {

	public static void main(String args[]) {
		String test = "\\key1\\value 1\\key 2 \\value2\\key3\\ v a l u e 3\\key4\\val ue 4";
		Info.Print(test);
		test = Info.Info_RemoveKey(test, "key1");
		Info.Print(test);
	}
}
