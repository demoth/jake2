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

// Created on 19.08.2006 by RST.
// $Id: Base.java,v 1.1 2006-10-31 13:06:32 salomo Exp $

package jake2.render.common;

public class Base {

	public static GenericGL ggl;
	
	// to be initialized!
	protected int GL_TEXTURE0 = -1;
	protected int GL_TEXTURE1 = -1;
	protected int GL_COLOR_INDEX8_EXT = ggl.GL_COLOR_INDEX;
	public static  final String REF_VERSION = "GL 0.01";
	
	
	// up / down
	public static  final int PITCH = 0;
	// left / right
	public static  final int YAW = 1;
	// fall over
	public static  final int ROLL = 2;

	// enum imagetype_t
	public static  final int it_skin = 0;
	public static  final int it_sprite = 1;
	public static  final int it_wall = 2;
	public static  final int it_pic = 3;
	public static  final int it_sky = 4;
	// enum modtype_t
	public static  final int mod_bad = 0;
	public static  final int mod_brush = 1;
	public static  final int mod_sprite = 2;
	public static  final int mod_alias = 3;
	public static  final int TEXNUM_LIGHTMAPS = 1024;
	public static  final int TEXNUM_SCRAPS = 1152;
	public static  final int TEXNUM_IMAGES = 1153;
	public static  final int MAX_GLTEXTURES = 1024;
	public static  final int MAX_LBM_HEIGHT = 480;
	public static  final float BACKFACE_EPSILON = 0.01f;
	/*
	 * * GL config stuff
	 */
	public static  final int GL_RENDERER_VOODOO = 0x00000001;
	public static  final int GL_RENDERER_VOODOO2 = 0x00000002;
	public static  final int GL_RENDERER_VOODOO_RUSH = 0x00000004;
	public static  final int GL_RENDERER_BANSHEE = 0x00000008;
	public static  final int GL_RENDERER_3DFX = 0x0000000F;
	public static  final int GL_RENDERER_PCX1 = 0x00000010;
	public static  final int GL_RENDERER_PCX2 = 0x00000020;
	public static  final int GL_RENDERER_PMX = 0x00000040;
	public static  final int GL_RENDERER_POWERVR = 0x00000070;
	public static  final int GL_RENDERER_PERMEDIA2 = 0x00000100;
	public static  final int GL_RENDERER_GLINT_MX = 0x00000200;
	public static  final int GL_RENDERER_GLINT_TX = 0x00000400;
	public static  final int GL_RENDERER_3DLABS_MISC = 0x00000800;
	public static  final int GL_RENDERER_3DLABS = 0x00000F00;
	public static  final int GL_RENDERER_REALIZM = 0x00001000;
	public static  final int GL_RENDERER_REALIZM2 = 0x00002000;
	public static  final int GL_RENDERER_INTERGRAPH = 0x00003000;
	public static  final int GL_RENDERER_3DPRO = 0x00004000;
	public static  final int GL_RENDERER_REAL3D = 0x00008000;
	public static  final int GL_RENDERER_RIVA128 = 0x00010000;
	public static  final int GL_RENDERER_DYPIC = 0x00020000;
	public static  final int GL_RENDERER_V1000 = 0x00040000;
	public static  final int GL_RENDERER_V2100 = 0x00080000;
	public static  final int GL_RENDERER_V2200 = 0x00100000;
	public static  final int GL_RENDERER_RENDITION = 0x001C0000;
	public static  final int GL_RENDERER_O2 = 0x00100000;
	public static  final int GL_RENDERER_IMPACT = 0x00200000;
	public static  final int GL_RENDERER_RE = 0x00400000;
	public static  final int GL_RENDERER_IR = 0x00800000;
	public static  final int GL_RENDERER_SGI = 0x00F00000;
	public static  final int GL_RENDERER_MCD = 0x01000000;
	public static  final int GL_RENDERER_OTHER = 0x80000000;

}
