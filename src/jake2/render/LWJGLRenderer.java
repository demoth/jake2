/*
 * LWJGLRenderer.java
 * Copyright (C) 2004
 *
 * $Id: LWJGLRenderer.java,v 1.2 2004-12-14 12:57:14 cawe Exp $
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
package jake2.render;

import jake2.Defines;
import jake2.client.refdef_t;
import jake2.client.refexport_t;
import jake2.qcommon.xcommand_t;
import jake2.render.lwjgl.Misc;
import jake2.sys.KBD;
import jake2.sys.LWJGLKBD;

import java.awt.Dimension;

/**
 * LWJGLRenderer
 * 
 * @author dsanders/cwei
 */
final class LWJGLRenderer extends Misc implements refexport_t, Ref {
	
	private LWJGLKBD kbd=new LWJGLKBD();
	
	public static final String DRIVER_NAME = "lwjgl";
	
	static {
		Renderer.register(new LWJGLRenderer());
	};

	private LWJGLRenderer() {
	}

	// ============================================================================
	// public interface for Renderer implementations
	//
	// refexport_t (ref.h)
	// ============================================================================

	/** 
	 * @see jake2.client.refexport_t#Init()
	 */
	public boolean Init(int vid_xpos, int vid_ypos) {
		
		// pre init
		if (!R_Init(vid_xpos, vid_ypos)) return false;
		// calls the R_Init2() internally		
		updateScreen();
		// the result from R_Init2()
		return post_init;
	}

	/** 
	 * @see jake2.client.refexport_t#Shutdown()
	 */
	public void Shutdown() {
		R_Shutdown();
	}

	/** 
	 * @see jake2.client.refexport_t#BeginRegistration(java.lang.String)
	 */
	public void BeginRegistration(String map) {
		if (contextInUse) {
			R_BeginRegistration(map);
			return;
		}	
		this.name = map;
			
		updateScreen(new xcommand_t() {
			public void execute() {
				R_BeginRegistration(LWJGLRenderer.this.name);
			}
		});
	}

	
	private model_t model = null;
	private String name = null;
	
	/** 
	 * @see jake2.client.refexport_t#RegisterModel(java.lang.String)
	 */
	public model_t RegisterModel(String name) {
		
		if (contextInUse)
			return R_RegisterModel(name);
		
		model = null;
		this.name = name;
		
		updateScreen(new xcommand_t() {
			public void execute() {
				LWJGLRenderer.this.model = R_RegisterModel(LWJGLRenderer.this.name);
			}
		});
		return model;
	}

	/** 
	 * @see jake2.client.refexport_t#RegisterSkin(java.lang.String)
	 */
	public image_t RegisterSkin(String name) {
		if (contextInUse)
			return R_RegisterSkin(name);
		
		this.image = null;
		this.name = name;

		updateScreen(new xcommand_t() {
			public void execute() {
				LWJGLRenderer.this.image = R_RegisterSkin(LWJGLRenderer.this.name);
			}
		});
		return image;
	}
	
	private image_t image = null;

	/** 
	 * @see jake2.client.refexport_t#RegisterPic(java.lang.String)
	 */
	public image_t RegisterPic(String name) {
		if (contextInUse)
			return Draw_FindPic(name);
		
		this.image = null;
		this.name = name;

		updateScreen(new xcommand_t() {
			public void execute() {
				LWJGLRenderer.this.image = Draw_FindPic(LWJGLRenderer.this.name);
			}
		});
		return image;
	}


	private float[] axis;
	private float rotate;

	/** 
	 * @see jake2.client.refexport_t#SetSky(java.lang.String, float, float[])
	 */
	public void SetSky(String name, float rotate, float[] axis) {
		if (contextInUse) {
			R_SetSky(name, rotate, axis);
			return;
		}

		this.name = name;
		this.rotate = rotate;
		this.axis = axis;

		updateScreen(new xcommand_t() {
			public void execute() {
				R_SetSky(LWJGLRenderer.this.name, LWJGLRenderer.this.rotate, LWJGLRenderer.this.axis);
			}
		});

	}

	/** 
	 * @see jake2.client.refexport_t#EndRegistration()
	 */
	public void EndRegistration() {
		if (contextInUse) {
			R_EndRegistration();
			return;
		}

		updateScreen(new xcommand_t() {
			public void execute() {
				R_EndRegistration();
			}
		});
	}

	/** 
	 * @see jake2.client.refexport_t#RenderFrame(jake2.client.refdef_t)
	 */
	public void RenderFrame(refdef_t fd) {
		R_RenderFrame(fd);
	}

	/** 
	 * @see jake2.client.refexport_t#DrawGetPicSize(java.awt.Dimension, java.lang.String)
	 */
	public void DrawGetPicSize(Dimension dim, String name) {
		Draw_GetPicSize(dim, name);
	}

	/** 
	 * @see jake2.client.refexport_t#DrawPic(int, int, java.lang.String)
	 */
	public void DrawPic(int x, int y, String name) {
		Draw_Pic(x, y, name);
	}

	/** 
	 * @see jake2.client.refexport_t#DrawStretchPic(int, int, int, int, java.lang.String)
	 */
	public void DrawStretchPic(int x, int y, int w, int h, String name) {
		Draw_StretchPic(x, y, w, h, name);
	}

	/** 
	 * @see jake2.client.refexport_t#DrawChar(int, int, int)
	 */
	public void DrawChar(int x, int y, int num) {
		Draw_Char(x, y, num);
	}

	/** 
	 * @see jake2.client.refexport_t#DrawTileClear(int, int, int, int, java.lang.String)
	 */
	public void DrawTileClear(int x, int y, int w, int h, String name) {
		Draw_TileClear(x, y, w, h, name);
	}

	/** 
	 * @see jake2.client.refexport_t#DrawFill(int, int, int, int, int)
	 */
	public void DrawFill(int x, int y, int w, int h, int c) {
		Draw_Fill(x, y, w, h, c);
	}

	/** 
	 * @see jake2.client.refexport_t#DrawFadeScreen()
	 */
	public void DrawFadeScreen() {
		Draw_FadeScreen();
	}

	/** 
	 * @see jake2.client.refexport_t#DrawStretchRaw(int, int, int, int, int, int, byte[])
	 */
	public void DrawStretchRaw(int x, int y, int w, int h, int cols, int rows, byte[] data) {
		Draw_StretchRaw(x, y, w, h, cols, rows, data);
	}

	/** 
	 * @see jake2.client.refexport_t#CinematicSetPalette(byte[])
	 */
	public void CinematicSetPalette(byte[] palette) {
		R_SetPalette(palette);
	}

	/** 
	 * @see jake2.client.refexport_t#BeginFrame(float)
	 */
	public void BeginFrame(float camera_separation) {
		R_BeginFrame(camera_separation);
	}

	/** 
	 * @see jake2.client.refexport_t#EndFrame()
	 */
	public void EndFrame() {
		GLimp_EndFrame();
	}

	/** 
	 * @see jake2.client.refexport_t#AppActivate(boolean)
	 */
	public void AppActivate(boolean activate) {
		GLimp_AppActivate(activate);
	}

	public int apiVersion() {
		return Defines.API_VERSION;
	}

	// ============================================================================
	// Ref interface
	// ============================================================================

	public String getName() {
		return DRIVER_NAME;
	}

	public String toString() {
		return DRIVER_NAME;
	}

	public refexport_t GetRefAPI() {
		return this;
	}
	
	public KBD getKeyboardHandler() { return kbd; }
}