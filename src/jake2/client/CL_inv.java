/*
 * CL_fx.java
 * Copyright (C) 2004
 * 
 * $Id: CL_inv.java,v 1.5 2004-02-03 13:13:01 hoz Exp $
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

// Created on 31.01.2004 by RST.

package jake2.client;

import jake2.qcommon.Com;
import jake2.qcommon.MSG;
import jake2.util.Vargs;

/**
 * CL_inv
 */
public class CL_inv extends CL_newfx {

	/*
	================
	CL_ParseInventory
	================
	*/
	static void ParseInventory() {
		int i;

		for (i = 0; i < MAX_ITEMS; i++)
			cl.inventory[i] = MSG.ReadShort(net_message);
	}

	/*
	================
	Inv_DrawString
	================
	*/
	static void Inv_DrawString(int x, int y, String string) {
		for (int i = 0; i < string.length(); i++) {
			re.DrawChar(x, y, string.charAt(i));
			x += 8;
		}
	}

	static void SetStringHighBit(String s) {
		byte[] b = s.getBytes();
		for (int i = 0; i < b.length; i++) {
			b[i] = (byte) (b[i] | 128);
		}
		s = new String(b);
	}

	/*
	================
	CL_DrawInventory
	================
	*/
	static final int DISPLAY_ITEMS = 17;

	static void DrawInventory() {
		int i, j;
		int num, selected_num, item;
		int[] index = new int[MAX_ITEMS];
		String string;
		int x, y;
		String binding;
		String bind;
		int selected;
		int top;

		selected = cl.frame.playerstate.stats[STAT_SELECTED_ITEM];

		num = 0;
		selected_num = 0;
		for (i = 0; i < MAX_ITEMS; i++) {
			if (i == selected)
				selected_num = num;
			if (cl.inventory[i] != 0) {
				index[num] = i;
				num++;
			}
		}

		// determine scroll point
		top = selected_num - DISPLAY_ITEMS / 2;
		if (num - top < DISPLAY_ITEMS)
			top = num - DISPLAY_ITEMS;
		if (top < 0)
			top = 0;

		x = (viddef.width - 256) / 2;
		y = (viddef.height - 240) / 2;

		// repaint everything next frame
		SCR.DirtyScreen();

		re.DrawPic(x, y + 8, "inventory");

		y += 24;
		x += 24;
		Inv_DrawString(x, y, "hotkey ### item");
		Inv_DrawString(x, y + 8, "------ --- ----");
		y += 16;
		for (i = top; i < num && i < top + DISPLAY_ITEMS; i++) {
			item = index[i];
			// search for a binding
			//Com_sprintf (binding, sizeof(binding), "use %s", cl.configstrings[CS_ITEMS+item]);
			binding = "use " + cl.configstrings[CS_ITEMS + item];
			bind = "";
			for (j = 0; j < 256; j++)
				if (keybindings[j] != null && keybindings[j].equals(binding)) {
					bind = Key.KeynumToString(j);
					break;
				}

			string =
				Com.sprintf(
					"%6s %3i %s",
					new Vargs(3).add(bind).add(cl.inventory[item]).add(cl.configstrings[CS_ITEMS + item]));
			if (item != selected)
				SetStringHighBit(string);
			else // draw a blinky cursor by the selected item
				{
				if (((int) (cls.realtime * 10) & 1) != 0)
					re.DrawChar(x - 8, y, 15);
			}
			Inv_DrawString(x, y, string);
			y += 8;
		}

	}

}
