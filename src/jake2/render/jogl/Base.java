/*
 * Base.java
 * Copyright (C) 2003
 *
 * $Id: Base.java,v 1.1 2004-07-07 19:59:36 hzi Exp $
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
package jake2.render.jogl;

import net.java.games.jogl.GL;

/**
 * Base
 *  
 * @author cwei
 */
public class Base {
	
	static final int GL_COLOR_INDEX8_EXT = GL.GL_COLOR_INDEX;
	static final String REF_VERSION = "GL 0.01";

	// up / down
	static final int PITCH = 0;
	// left / right
	static final int YAW = 1;
	// fall over
	static final int ROLL = 2;

	/*
	  skins will be outline flood filled and mip mapped
	  pics and sprites with alpha will be outline flood filled
	  pic won't be mip mapped

	  model skin
	  sprite frame
	  wall texture
	  pic
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

//	  ===================================================================

	// enum rserr_t
	static final int rserr_ok = 0;
	static final int rserr_invalid_fullscreen = 1;
	static final int rserr_invalid_mode = 2;
	static final int rserr_unknown = 3;

//
//	#include "gl_model.h"
//
//	void GL_BeginRendering (int *x, int *y, int *width, int *height);
//	void GL_EndRendering (void);
//
//	void GL_SetDefaultState( void );
//	void GL_UpdateSwapInterval( void );
	
	static class glvert_t {
		float x, y, z;
		float s, t;
		float r, g, b;
	}

	static final int MAX_LBM_HEIGHT = 480;

	static final float BACKFACE_EPSILON = 0.01f;

//	  ====================================================
//
//	void R_TranslatePlayerSkin (int playernum);
//	void GL_Bind (int texnum);
//	void GL_MBind( GLenum target, int texnum );
//	void GL_TexEnv( GLenum value );
//	void GL_EnableMultitexture( qboolean enable );
//	void GL_SelectTexture( GLenum );
//
//	void R_LightPoint (vec3_t p, vec3_t color);
//	void R_PushDlights (void);
//

//	  ====================================================================
//
//	extern	int		registration_sequence;
//
//
//	void V_AddBlend (float r, float g, float b, float a, float *v_blend);
//
//	int 	R_Init( void *hinstance, void *hWnd );
//	void	R_Shutdown( void );
//
//	void R_RenderView (refdef_t *fd);
//	void GL_ScreenShot_f (void);
//	void R_DrawAliasModel (entity_t *e);
//	void R_DrawBrushModel (entity_t *e);
//	void R_DrawSpriteModel (entity_t *e);
//	void R_DrawBeam( entity_t *e );
//	void R_DrawWorld (void);
//	void R_RenderDlights (void);
//	void R_DrawAlphaSurfaces (void);
//	void R_RenderBrushPoly (msurface_t *fa);
//	void R_InitParticleTexture (void);
//	void Draw_InitLocal (void);
//	void GL_SubdivideSurface (msurface_t *fa);
//	qboolean R_CullBox (vec3_t mins, vec3_t maxs);
//	void R_RotateForEntity (entity_t *e);
//	void R_MarkLeaves (void);
//
//	glpoly_t *WaterWarpPolyVerts (glpoly_t *p);
//	void EmitWaterPolys (msurface_t *fa);
//	void R_AddSkySurface (msurface_t *fa);
//	void R_ClearSkyBox (void);
//	void R_DrawSkyBox (void);
//	void R_MarkLights (dlight_t *light, int bit, mnode_t *node);
//
//
//	void COM_StripExtension (char *in, char *out);
//
//	void	Draw_GetPicSize (int *w, int *h, char *name);
//	void	Draw_Pic (int x, int y, char *name);
//	void	Draw_StretchPic (int x, int y, int w, int h, char *name);
//	void	Draw_Char (int x, int y, int c);
//	void	Draw_TileClear (int x, int y, int w, int h, char *name);
//	void	Draw_Fill (int x, int y, int w, int h, int c);
//	void	Draw_FadeScreen (void);
//	void	Draw_StretchRaw (int x, int y, int w, int h, int cols, int rows, byte *data);
//
//	void	R_BeginFrame( float camera_separation );
//	void	R_SwapBuffers( int );
//	void	R_SetPalette ( const unsigned char *palette);
//
//	int		Draw_GetPalette (void);
//
//	void GL_ResampleTexture (unsigned *in, int inwidth, int inheight, unsigned *out,  int outwidth, int outheight);
//
//	struct image_s *R_RegisterSkin (char *name);
//
//	void LoadPCX (char *filename, byte **pic, byte **palette, int *width, int *height);
//	image_t *GL_LoadPic (char *name, byte *pic, int width, int height, imagetype_t type, int bits);
//	image_t	*GL_FindImage (char *name, imagetype_t type);
//	void	GL_TextureMode( char *string );
//	void	GL_ImageList_f (void);
//
//	void	GL_SetTexturePalette( unsigned palette[256] );
//
//	void	GL_InitImages (void);
//	void	GL_ShutdownImages (void);
//
//	void	GL_FreeUnusedImages (void);
//
//	void GL_TextureAlphaMode( char *string );
//	void GL_TextureSolidMode( char *string );
//
//	/*
//	** GL extension emulation functions
//	*/
//	void GL_DrawParticles( int n, const particle_t particles[], const unsigned colortable[768] );
//

	/*
	** GL config stuff
	*/
	static final int GL_RENDERER_VOODOO = 0x00000001;
	static final int GL_RENDERER_VOODOO2 = 0x00000002;
	static final int GL_RENDERER_VOODOO_RUSH = 0x00000004;
	static final int GL_RENDERER_BANSHEE = 0x00000008;
	static final int		GL_RENDERER_3DFX = 0x0000000F;

	static final int GL_RENDERER_PCX1 = 0x00000010;
	static final int GL_RENDERER_PCX2 = 0x00000020;
	static final int GL_RENDERER_PMX = 0x00000040;
	static final int		GL_RENDERER_POWERVR = 0x00000070;

	static final int GL_RENDERER_PERMEDIA2 = 0x00000100;
	static final int GL_RENDERER_GLINT_MX = 0x00000200;
	static final int GL_RENDERER_GLINT_TX = 0x00000400;
	static final int GL_RENDERER_3DLABS_MISC	= 0x00000800;
	static final int		GL_RENDERER_3DLABS = 0x00000F00;

	static final int GL_RENDERER_REALIZM = 0x00001000;
	static final int GL_RENDERER_REALIZM2 = 0x00002000;
	static final int		GL_RENDERER_INTERGRAPH = 0x00003000;

	static final int GL_RENDERER_3DPRO = 0x00004000;
	static final int GL_RENDERER_REAL3D = 0x00008000;
	static final int GL_RENDERER_RIVA128 = 0x00010000;
	static final int GL_RENDERER_DYPIC = 0x00020000;

	static final int GL_RENDERER_V1000 = 0x00040000;
	static final int GL_RENDERER_V2100 = 0x00080000;
	static final int GL_RENDERER_V2200 = 0x00100000;
	static final int		GL_RENDERER_RENDITION = 0x001C0000;

	static final int GL_RENDERER_O2 = 0x00100000;
	static final int GL_RENDERER_IMPACT = 0x00200000;
	static final int GL_RENDERER_RE = 0x00400000;
	static final int GL_RENDERER_IR = 0x00800000;
	static final int		GL_RENDERER_SGI = 0x00F00000;

	static final int GL_RENDERER_MCD = 0x01000000;
	static final int GL_RENDERER_OTHER = 0x80000000;


//	typedef struct
//	{
//		int         renderer;
//		const char *renderer_string;
//		const char *vendor_string;
//		const char *version_string;
//		const char *extensions_string;
//
//		qboolean	allow_cds;
//	} glconfig_t;
//
//	typedef struct
//	{
//		float inverse_intensity;
//		qboolean fullscreen;
//
//		int     prev_mode;
//
//		unsigned char *d_16to8table;
//
//		int lightmap_textures;
//
//		int	currenttextures[2];
//		int currenttmu;
//
//		float camera_separation;
//		qboolean stereo_enabled;
//
//		unsigned char originalRedGammaTable[256];
//		unsigned char originalGreenGammaTable[256];
//		unsigned char originalBlueGammaTable[256];
//	} glstate_t;
//
//	/*
//	====================================================================
//
//	IMPORTED FUNCTIONS
//
//	====================================================================
//	*/
//
//	extern	refimport_t	ri;
//
//
//	/*
//	====================================================================
//
//	IMPLEMENTATION SPECIFIC FUNCTIONS
//
//	====================================================================
//	*/
//
//	void		GLimp_BeginFrame( float camera_separation );
//	void		GLimp_EndFrame( void );
//	int 		GLimp_Init( void *hinstance, void *hWnd );
//	void		GLimp_Shutdown( void );
//	int     	GLimp_SetMode( int *pwidth, int *pheight, int mode, qboolean fullscreen );
//	void		GLimp_AppActivate( qboolean active );
//	void		GLimp_EnableLogging( qboolean enable );
//	void		GLimp_LogNewFrame( void );
//

}
