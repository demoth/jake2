/*
 * Base.java
 * Copyright (C) 2003
 *
 * $Id: Base.java,v 1.3 2005-06-08 20:43:07 cawe Exp $
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
package jake2.render.fastjogl;

import jake2.render.JoglBase;
import net.java.games.jogl.GL;

/**
 * Base
 * 
 * @author cwei
 */
public abstract class Base extends JoglBase {
    
    static final int GL_COLOR_INDEX8_EXT = GL.GL_COLOR_INDEX;
    
    static final String REF_VERSION = "GL 0.01";
    
    // up / down
    static final int PITCH = 0;
    
    // left / right
    static final int YAW = 1;
    
    // fall over
    static final int ROLL = 2;
    
    /*
     * skins will be outline flood filled and mip mapped pics and sprites with
     * alpha will be outline flood filled pic won't be mip mapped
     * 
     * model skin sprite frame wall texture pic
     */
    // enum imagetype_t
    static final int it_skin = 0;
    
    static final int it_sprite = 1;
    
    static final int it_wall = 2;
    
    static final int it_pic = 3;
    
    static final int it_sky = 4;
    
    // enum modtype_t
    static final int mod_bad = 0;
    
    static final int mod_brush = 1;
    
    static final int mod_sprite = 2;
    
    static final int mod_alias = 3;
    
    static final int TEXNUM_LIGHTMAPS = 1024;
    
    static final int TEXNUM_SCRAPS = 1152;
    
    static final int TEXNUM_IMAGES = 1153;
    
    static final int MAX_GLTEXTURES = 1024;
    
    static final int MAX_LBM_HEIGHT = 480;
    
    static final float BACKFACE_EPSILON = 0.01f;
    
    /*
     * * GL config stuff
     */
    static final int GL_RENDERER_VOODOO = 0x00000001;
    
    static final int GL_RENDERER_VOODOO2 = 0x00000002;
    
    static final int GL_RENDERER_VOODOO_RUSH = 0x00000004;
    
    static final int GL_RENDERER_BANSHEE = 0x00000008;
    
    static final int GL_RENDERER_3DFX = 0x0000000F;
    
    static final int GL_RENDERER_PCX1 = 0x00000010;
    
    static final int GL_RENDERER_PCX2 = 0x00000020;
    
    static final int GL_RENDERER_PMX = 0x00000040;
    
    static final int GL_RENDERER_POWERVR = 0x00000070;
    
    static final int GL_RENDERER_PERMEDIA2 = 0x00000100;
    
    static final int GL_RENDERER_GLINT_MX = 0x00000200;
    
    static final int GL_RENDERER_GLINT_TX = 0x00000400;
    
    static final int GL_RENDERER_3DLABS_MISC = 0x00000800;
    
    static final int GL_RENDERER_3DLABS = 0x00000F00;
    
    static final int GL_RENDERER_REALIZM = 0x00001000;
    
    static final int GL_RENDERER_REALIZM2 = 0x00002000;
    
    static final int GL_RENDERER_INTERGRAPH = 0x00003000;
    
    static final int GL_RENDERER_3DPRO = 0x00004000;
    
    static final int GL_RENDERER_REAL3D = 0x00008000;
    
    static final int GL_RENDERER_RIVA128 = 0x00010000;
    
    static final int GL_RENDERER_DYPIC = 0x00020000;
    
    static final int GL_RENDERER_V1000 = 0x00040000;
    
    static final int GL_RENDERER_V2100 = 0x00080000;
    
    static final int GL_RENDERER_V2200 = 0x00100000;
    
    static final int GL_RENDERER_RENDITION = 0x001C0000;
    
    static final int GL_RENDERER_O2 = 0x00100000;
    
    static final int GL_RENDERER_IMPACT = 0x00200000;
    
    static final int GL_RENDERER_RE = 0x00400000;
    
    static final int GL_RENDERER_IR = 0x00800000;
    
    static final int GL_RENDERER_SGI = 0x00F00000;
    
    static final int GL_RENDERER_MCD = 0x01000000;
    
    static final int GL_RENDERER_OTHER = 0x80000000;
}