/*
 * JoglRenderer.java
 * Copyright (C) 2003
 *
 * $Id: JoglRenderer.java,v 1.17 2004-01-03 03:47:14 cwei Exp $
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

import java.awt.Dimension;
import jake2.render.jogl.*;

import jake2.client.refdef_t;
import jake2.client.refexport_t;
import jake2.client.refimport_t;

/**
 * JoglRenderer
 * 
 * @author cwei
 */
final class JoglRenderer extends Impl implements refexport_t, Ref {

	static {
		Renderer.register(new JoglRenderer());
	};

	private JoglRenderer() {
	}

	// ============================================================================
	// public interface for Renderer implementations
	//
	// refexport_t (ref.h)
	// ============================================================================

	/** 
	 * @see jake2.client.refexport_t#Init()
	 */
	public boolean Init() {
		
		// pre init
		if (!R_Init()) return false;
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
		R_BeginRegistration(map);
	}

	/** 
	 * @see jake2.client.refexport_t#RegisterModel(java.lang.String)
	 */
	public model_t RegisterModel(String name) {
		return R_RegisterModel(name);
	}

	/** 
	 * @see jake2.client.refexport_t#RegisterSkin(java.lang.String)
	 */
	public image_t RegisterSkin(String name) {
		return R_RegisterSkin(name);
	}

	/** 
	 * @see jake2.client.refexport_t#RegisterPic(java.lang.String)
	 */
	public image_t RegisterPic(String name) {
		return Draw_FindPic(name);
	}

	/** 
	 * @see jake2.client.refexport_t#SetSky(java.lang.String, float, float[])
	 */
	public void SetSky(String name, float rotate, float[] axis) {
		R_SetSky(name, rotate, axis);
	}

	/** 
	 * @see jake2.client.refexport_t#EndRegistration()
	 */
	public void EndRegistration() {
		R_EndRegistration();
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

	// ============================================================================
	// Ref interface
	// ============================================================================

	public String getName() {
		return DRIVER_NAME;
	}

	public String toString() {
		return DRIVER_NAME;
	}

	public refexport_t GetRefAPI(refimport_t rimp) {
		this.ri = rimp;
		return this;
	}

}