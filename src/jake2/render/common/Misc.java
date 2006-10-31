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

// Created on 25.08.2006 by RST.
// $Id: Misc.java,v 1.1 2006-10-31 13:06:32 salomo Exp $

package jake2.render.common;


import jake2.*;
import jake2.client.*;
import jake2.game.*;
import jake2.qcommon.*;
import jake2.render.*;
import jake2.server.*;


public abstract class Misc extends Mesh {

	/*
	================== 
	R_InitParticleTexture
	==================
	*/
	byte[][] dottexture = { 
			{ 0, 0, 0, 0, 0, 0, 0, 0 },	{ 0, 0, 1, 1, 0, 0, 0, 0 }, 
			{ 0, 1, 1, 1, 1, 0, 0, 0 },	{ 0, 1, 1, 1, 1, 0, 0, 0 },
			{ 0, 0, 1, 1, 0, 0, 0, 0 },	{ 0, 0, 0, 0, 0, 0, 0, 0 }, 
			{ 0, 0, 0, 0, 0, 0, 0, 0 },	{ 0, 0, 0, 0, 0, 0, 0, 0 }
	};

	/**
	 * GL_Strings_f
	 */
	protected void GL_Strings_f() {
		VID.Printf(Defines.PRINT_ALL, "GL_VENDOR: " + gl_config.vendor_string + '\n');
		VID.Printf(Defines.PRINT_ALL, "GL_RENDERER: " + gl_config.renderer_string + '\n');
		VID.Printf(Defines.PRINT_ALL, "GL_VERSION: " + gl_config.version_string + '\n');
		VID.Printf(Defines.PRINT_ALL, "GL_EXTENSIONS: " + gl_config.extensions_string + '\n');
	}
}
