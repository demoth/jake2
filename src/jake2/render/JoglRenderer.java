/*
 * JoglRenderer.java
 * Copyright (C) 2003
 *
 * $Id: JoglRenderer.java,v 1.4 2003-11-24 15:57:34 cwei Exp $
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
import jake2.client.refdef_t;
import jake2.client.refexport_t;
import jake2.client.refimport_t;

/**
 * JoglRenderer
 * 
 * @author cwei
 */
final class JoglRenderer implements Ref {
	
	static final String DRIVER_NAME = "jogl";

	private refimport_t ri = null; 
	
	static {
		Renderer.register(new JoglRenderer());
	}
	
	private JoglRenderer() {
	}

	// ============================================================================
	// public interface for Renderer implementations
	//
	// refexport_t (ref.h)
	// ============================================================================
	//
	/** 
	 * @see jake2.client.refexport_t#Init()
	 */
	public boolean Init() {
		return R_Init();
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

	// ============================================================================
	// to port from gl_rmain.c, ...
	// ============================================================================
	/**
	 * @return
	 */
	private boolean R_Init() {
		// TODO Auto-generated method stub
		return false;
	}

	/**
	 * 
	 */
	private void R_Shutdown() {
		// TODO Auto-generated method stub
		
	}

	/**
	 * @param map
	 */
	private void R_BeginRegistration(String map) {
		// TODO Auto-generated method stub
		
	}

	/**
	 * @param name
	 * @return
	 */
	private model_t R_RegisterModel(String name) {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * @param name
	 * @return
	 */
	private image_t R_RegisterSkin(String name) {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * @param name
	 * @return
	 */
	private image_t Draw_FindPic(String name) {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * @param name
	 * @param rotate
	 * @param axis
	 */
	private void R_SetSky(String name, float rotate, float[] axis) {
		assert (axis.length == 3) : "vec3_t bug";
		// TODO Auto-generated method stub
		
	}

	/**
	 * 
	 */
	private void R_EndRegistration() {
		// TODO Auto-generated method stub
		
	}

	/**
	 * @param fd
	 */
	private void R_RenderFrame(refdef_t fd) {
		// TODO Auto-generated method stub
		
	}

	/**
	 * @param dim
	 * @param name
	 */
	private void Draw_GetPicSize(Dimension dim, String name) {
		// TODO Auto-generated method stub
		
	}

	/**
	 * @param x
	 * @param y
	 * @param name
	 */
	private void Draw_Pic(int x, int y, String name) {
		// TODO Auto-generated method stub
		
	}

	/**
	 * @param x
	 * @param y
	 * @param w
	 * @param h
	 * @param name
	 */
	private void Draw_StretchPic(int x, int y, int w, int h, String name) {
		// TODO Auto-generated method stub
		
	}

	/**
	 * @param x
	 * @param y
	 * @param num
	 */
	private void Draw_Char(int x, int y, int num) {
		// TODO Auto-generated method stub
		
	}

	/**
	 * @param x
	 * @param y
	 * @param w
	 * @param h
	 * @param name
	 */
	private void Draw_TileClear(int x, int y, int w, int h, String name) {
		// TODO Auto-generated method stub
		
	}

	/**
	 * @param x
	 * @param y
	 * @param w
	 * @param h
	 * @param c
	 */
	private void Draw_Fill(int x, int y, int w, int h, int c) {
		// TODO Auto-generated method stub
		
	}

	/**
	 * 
	 */
	private void Draw_FadeScreen() {
		// TODO Auto-generated method stub
		
	}

	/**
	 * @param x
	 * @param y
	 * @param w
	 * @param h
	 * @param cols
	 * @param rows
	 * @param data
	 */
	private void Draw_StretchRaw(int x, int y, int w, int h, int cols, int rows, byte[] data) {
		// TODO Auto-generated method stub
		
	}

	/**
	 * @param palette
	 */
	private void R_SetPalette(byte[] palette) {
		// TODO Auto-generated method stub
		
	}

	/**
	 * @param camera_separation
	 */
	private void R_BeginFrame(float camera_separation) {
		// TODO Auto-generated method stub
		
	}

	/**
	 * 
	 */
	private void GLimp_EndFrame() {
		// TODO Auto-generated method stub
		
	}

	/**
	 * @param activate
	 */
	private void GLimp_AppActivate(boolean activate) {
		// TODO Auto-generated method stub
		
	}

}
