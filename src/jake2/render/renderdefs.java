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

// Created on 28.11.2003 by RST.
// $Id: renderdefs.java,v 1.4 2003-11-29 13:34:48 rst Exp $

package jake2.render;

public class renderdefs extends jake2.Defines {
	
	
	static final int MAX_DLIGHTS = 32;
	static final int MAX_ENTITIES = 128;
	static final int MAX_PARTICLES = 4096;
	static final int MAX_LIGHTSTYLES = 256;
	static final int MAX_CLIENTS=5;


	static final float POWERSUIT_SCALE = 4.0f;

	static final int SHELL_RED_COLOR =	0xF2;
	static final int SHELL_GREEN_COLOR = 0xD0;
	static final int SHELL_BLUE_COLOR = 0xF3;

	static final int SHELL_RG_COLOR = 0xDC;
//	  static final int SHELL_RB_COLOR = 0x86;
	static final int SHELL_RB_COLOR = 0x68;
	static final int SHELL_BG_COLOR = 0x78;

//	  ROGUE
	static final int SHELL_DOUBLE_COLOR = 0xDF; // 223
	static final int SHELL_HALF_DAM_COLOR	= 0x90;
	static final int SHELL_CYAN_COLOR = 0x72;
//	  ROGUE

	static final int SHELL_WHITE_COLOR = 0xD7;
}
