/*
 * JoglRenderer.java
 * Copyright (C) 2003
 *
 * $Id: JoglRenderer.java,v 1.3 2003-11-24 15:08:45 cwei Exp $
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
	// ref.h, refexport_t
	// ============================================================================
	//
	/* 
	 * @see jake2.client.refexport_t#Init()
	 */
	public boolean Init() {
		return R_Init();
	}

	/**
	 * @return
	 */
	private boolean R_Init() {
		// TODO Auto-generated method stub
		return false;
	}

	/* 
	 * @see jake2.client.refexport_t#Shutdown()
	 */
	public void Shutdown() {
		R_Shutdown();
	}

	/**
	 * 
	 */
	private void R_Shutdown() {
		// TODO Auto-generated method stub
		
	}

	/* 
	 * @see jake2.client.refexport_t#BeginRegistration(java.lang.String)
	 */
	public void BeginRegistration(String map) {
		// TODO Auto-generated method stub
		
	}

	/* 
	 * @see jake2.client.refexport_t#RegisterModel(java.lang.String)
	 */
	public model_t RegisterModel(String name) {
		// TODO Auto-generated method stub
		return null;
	}

	/* 
	 * @see jake2.client.refexport_t#RegisterSkin(java.lang.String)
	 */
	public image_t RegisterSkin(String name) {
		// TODO Auto-generated method stub
		return null;
	}

	/* 
	 * @see jake2.client.refexport_t#RegisterPic(java.lang.String)
	 */
	public image_t RegisterPic(String name) {
		// TODO Auto-generated method stub
		return null;
	}

	/* 
	 * @see jake2.client.refexport_t#SetSky(java.lang.String, float, float[])
	 */
	public void SetSky(String name, float rotate, float[] axis) {
		// TODO Auto-generated method stub
		
	}

	/* 
	 * @see jake2.client.refexport_t#EndRegistration()
	 */
	public void EndRegistration() {
		// TODO Auto-generated method stub
		
	}

	/* 
	 * @see jake2.client.refexport_t#RenderFrame(jake2.client.refdef_t)
	 */
	public void RenderFrame(refdef_t fd) {
		// TODO Auto-generated method stub
		
	}

	/* 
	 * @see jake2.client.refexport_t#DrawGetPicSize(java.awt.Dimension, java.lang.String)
	 */
	public void DrawGetPicSize(Dimension dim, String name) {
		// TODO Auto-generated method stub
		
	}

	/* 
	 * @see jake2.client.refexport_t#DrawPic(int, int, java.lang.String)
	 */
	public void DrawPic(int x, int y, String name) {
		// TODO Auto-generated method stub
		
	}

	/* 
	 * @see jake2.client.refexport_t#DrawStretchPic(int, int, int, int, java.lang.String)
	 */
	public void DrawStretchPic(int x, int y, int w, int h, String name) {
		// TODO Auto-generated method stub
		
	}

	/* 
	 * @see jake2.client.refexport_t#DrawChar(int, int, int)
	 */
	public void DrawChar(int x, int y, int num) {
		// TODO Auto-generated method stub
		
	}

	/* 
	 * @see jake2.client.refexport_t#DrawTileClear(int, int, int, int, java.lang.String)
	 */
	public void DrawTileClear(int x, int y, int w, int h, String name) {
		// TODO Auto-generated method stub
		
	}

	/* 
	 * @see jake2.client.refexport_t#DrawFill(int, int, int, int, int)
	 */
	public void DrawFill(int x, int y, int w, int h, int c) {
		// TODO Auto-generated method stub
		
	}

	/* 
	 * @see jake2.client.refexport_t#DrawFadeScreen()
	 */
	public void DrawFadeScreen() {
		// TODO Auto-generated method stub
		
	}

	/* 
	 * @see jake2.client.refexport_t#DrawStretchRaw(int, int, int, int, int, int, byte[])
	 */
	public void DrawStretchRaw(int x, int y, int w, int h, int cols, int rows, byte[] data) {
		// TODO Auto-generated method stub
		
	}

	/* 
	 * @see jake2.client.refexport_t#CinematicSetPalette(byte[])
	 */
	public void CinematicSetPalette(byte[] palette) {
		// TODO Auto-generated method stub
		
	}

	/* 
	 * @see jake2.client.refexport_t#BeginFrame(float)
	 */
	public void BeginFrame(float camera_separation) {
		// TODO Auto-generated method stub
		
	}

	/* 
	 * @see jake2.client.refexport_t#EndFrame()
	 */
	public void EndFrame() {
		// TODO Auto-generated method stub
		
	}

	/* 
	 * @see jake2.client.refexport_t#AppActivate(boolean)
	 */
	public void AppActivate(boolean activate) {
		// TODO Auto-generated method stub
		
	}
	// ============================================================================
	//
	
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
