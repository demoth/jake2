/*
 * Jsr231Renderer.java
 * Copyright (C) 2004
 *
 * $Id: Jsr231Renderer.java,v 1.2 2006-11-21 00:51:22 cawe Exp $
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
import jake2.render.opengl.Jsr231Driver;
import jake2.sys.JOGLKBD;
import jake2.sys.KBD;

import java.awt.Dimension;

/**
 * Jsr231Renderer
 * 
 * @author dsanders/cwei
 */
final class Jsr231Renderer extends Jsr231Driver implements refexport_t, Ref {
	
    	public static final String DRIVER_NAME = "jsr231";
    
    	private KBD kbd = new JOGLKBD();
	
    	// is set from Renderer factory
    	private RenderAPI impl;

    	static {
		Renderer.register(new Jsr231Renderer());
	};

	private Jsr231Renderer() {
        // singleton
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
        // init the OpenGL drivers
        impl.setGLDriver(this);
		// pre init
		if (!impl.R_Init(vid_xpos, vid_ypos)) return false;
        // activates the OpenGL context        
        activate();
        // post init        
		return impl.R_Init2();
	}

	/** 
	 * @see jake2.client.refexport_t#Shutdown()
	 */
	public void Shutdown() {
		impl.R_Shutdown();
	}

	/** 
	 * @see jake2.client.refexport_t#BeginRegistration(java.lang.String)
	 */
	public final void BeginRegistration(String map) {
        activate();
		impl.R_BeginRegistration(map);
	}

	/** 
	 * @see jake2.client.refexport_t#RegisterModel(java.lang.String)
	 */
	public final model_t RegisterModel(String name) {
        activate();
		return impl.R_RegisterModel(name);
	}

	/** 
	 * @see jake2.client.refexport_t#RegisterSkin(java.lang.String)
	 */
	public final image_t RegisterSkin(String name) {
        activate();
		return impl.R_RegisterSkin(name);
	}
	
	/** 
	 * @see jake2.client.refexport_t#RegisterPic(java.lang.String)
	 */
	public final image_t RegisterPic(String name) {
        activate();
		return impl.Draw_FindPic(name);
	}
	/** 
	 * @see jake2.client.refexport_t#SetSky(java.lang.String, float, float[])
	 */
	public final void SetSky(String name, float rotate, float[] axis) {
        activate();
		impl.R_SetSky(name, rotate, axis);
	}

	/** 
	 * @see jake2.client.refexport_t#EndRegistration()
	 */
	public final void EndRegistration() {
        activate();
		impl.R_EndRegistration();
	}

	/** 
	 * @see jake2.client.refexport_t#RenderFrame(jake2.client.refdef_t)
	 */
	public final void RenderFrame(refdef_t fd) {
		impl.R_RenderFrame(fd);
	}

	/** 
	 * @see jake2.client.refexport_t#DrawGetPicSize(java.awt.Dimension, java.lang.String)
	 */
	public final void DrawGetPicSize(Dimension dim, String name) {
		impl.Draw_GetPicSize(dim, name);
	}

	/** 
	 * @see jake2.client.refexport_t#DrawPic(int, int, java.lang.String)
	 */
	public final void DrawPic(int x, int y, String name) {
		impl.Draw_Pic(x, y, name);
	}

	/** 
	 * @see jake2.client.refexport_t#DrawStretchPic(int, int, int, int, java.lang.String)
	 */
	public final void DrawStretchPic(int x, int y, int w, int h, String name) {
		impl.Draw_StretchPic(x, y, w, h, name);
	}

	/** 
	 * @see jake2.client.refexport_t#DrawChar(int, int, int)
	 */
	public final void DrawChar(int x, int y, int num) {
        activate();
		impl.Draw_Char(x, y, num);
	}

	/** 
	 * @see jake2.client.refexport_t#DrawTileClear(int, int, int, int, java.lang.String)
	 */
	public final void DrawTileClear(int x, int y, int w, int h, String name) {
		impl.Draw_TileClear(x, y, w, h, name);
	}

	/** 
	 * @see jake2.client.refexport_t#DrawFill(int, int, int, int, int)
	 */
	public final void DrawFill(int x, int y, int w, int h, int c) {
		impl.Draw_Fill(x, y, w, h, c);
	}

	/** 
	 * @see jake2.client.refexport_t#DrawFadeScreen()
	 */
	public final void DrawFadeScreen() {
		impl.Draw_FadeScreen();
	}

	/** 
	 * @see jake2.client.refexport_t#DrawStretchRaw(int, int, int, int, int, int, byte[])
	 */
	public final void DrawStretchRaw(int x, int y, int w, int h, int cols, int rows, byte[] data) {
		impl.Draw_StretchRaw(x, y, w, h, cols, rows, data);
	}

	/** 
	 * @see jake2.client.refexport_t#CinematicSetPalette(byte[])
	 */
	public final void CinematicSetPalette(byte[] palette) {
		impl.R_SetPalette(palette);
	}

	/** 
	 * @see jake2.client.refexport_t#BeginFrame(float)
	 */
	public final void BeginFrame(float camera_separation) {
		impl.R_BeginFrame(camera_separation);
	}

	/** 
	 * @see jake2.client.refexport_t#EndFrame()
	 */
	public final void EndFrame() {
		endFrame();
	}

	/** 
	 * @see jake2.client.refexport_t#AppActivate(boolean)
	 */
	public final void AppActivate(boolean activate) {
        appActivate(activate);
	}

	public final int apiVersion() {
		return Defines.API_VERSION;
	}
    
    public KBD getKeyboardHandler() {
        return kbd;
    }
	// ============================================================================
	// Ref interface
	// ============================================================================

	public final String getName() {
		return DRIVER_NAME;
	}

	public final String toString() {
		return DRIVER_NAME;
	}

	public final refexport_t GetRefAPI(RenderAPI renderer) {
		this.impl = renderer;
        	return this;
	}
}