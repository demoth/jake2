/*
 * JoglRenderer.java
 * Copyright (C) 2003
 *
 * $Id: JoglRenderer.java,v 1.2 2003-11-21 23:28:53 cwei Exp $
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

import jake2.client.refdef_t;

/**
 * JoglRenderer
 * 
 * @author cwei
 */
final class JoglRenderer extends Renderer {

	JoglRenderer() {
	}

	/* 
	 * @see jake2.render.Renderer#Init()
	 */
	public boolean Init() {
		// TODO Auto-generated method stub
		return false;
	}

	/* 
	 * @see jake2.render.Renderer#Shutdown()
	 */
	public void Shutdown() {
		// TODO Auto-generated method stub

	}

	/* 
	 * @see jake2.render.Renderer#BeginRegistration(java.lang.String)
	 */
	public void BeginRegistration(String map) {
		// TODO Auto-generated method stub

	}

	/* 
	 * @see jake2.render.Renderer#RegisterModel(java.lang.String)
	 */
	public model_t RegisterModel(String name) {
		// TODO Auto-generated method stub
		return null;
	}

	/* 
	 * @see jake2.render.Renderer#RegisterSkin(java.lang.String)
	 */
	public image_t RegisterSkin(String name) {
		// TODO Auto-generated method stub
		return null;
	}

	/* 
	 * @see jake2.render.Renderer#RegisterPic(java.lang.String)
	 */
	public image_t RegisterPic(String name) {
		// TODO Auto-generated method stub
		return null;
	}

	/* 
	 * @see jake2.render.Renderer#SetSky(java.lang.String, float, float[])
	 */
	public void SetSky(String name, float rotate, float[] axis) {
		// TODO Auto-generated method stub

	}

	/* 
	 * @see jake2.render.Renderer#EndRegistration()
	 */
	public void EndRegistration() {
		// TODO Auto-generated method stub

	}

	/* 
	 * @see jake2.render.Renderer#RenderFrame(jake2.client.refdef_t)
	 */
	public void RenderFrame(refdef_t fd) {
		// TODO Auto-generated method stub

	}

	/* 
	 * @see jake2.render.Renderer#DrawGetPicSize(int[], int[], java.lang.String)
	 */
	public void DrawGetPicSize(int[] w, int[] h, String name) {
		// TODO Auto-generated method stub

	}

	/* 
	 * @see jake2.render.Renderer#DrawPic(int, int, java.lang.String)
	 */
	public void DrawPic(int x, int y, String name) {
		// TODO Auto-generated method stub

	}

	/* 
	 * @see jake2.render.Renderer#DrawStretchPic(int, int, int, int, java.lang.String)
	 */
	public void DrawStretchPic(int x, int y, int w, int h, String name) {
		// TODO Auto-generated method stub

	}

	/* 
	 * @see jake2.render.Renderer#DrawChar(int, int, int)
	 */
	public void DrawChar(int x, int y, int c) {
		// TODO Auto-generated method stub

	}

	/* 
	 * @see jake2.render.Renderer#DrawTileClear(int, int, int, int, java.lang.String)
	 */
	public void DrawTileClear(int x, int y, int w, int h, String name) {
		// TODO Auto-generated method stub

	}

	/* 
	 * @see jake2.render.Renderer#DrawFill(int, int, int, int, int)
	 */
	public void DrawFill(int x, int y, int w, int h, int c) {
		// TODO Auto-generated method stub

	}

	/* 
	 * @see jake2.render.Renderer#DrawFadeScreen()
	 */
	public void DrawFadeScreen() {
		// TODO Auto-generated method stub

	}

	/* 
	 * @see jake2.render.Renderer#DrawStretchRaw(int, int, int, int, int, int, byte[])
	 */
	public void DrawStretchRaw(
		int x,
		int y,
		int w,
		int h,
		int cols,
		int rows,
		byte[] data) {
		// TODO Auto-generated method stub

	}

	/* 
	 * @see jake2.render.Renderer#CinematicSetPalette(byte[])
	 */
	public void CinematicSetPalette(byte[] palette) {
		assert(
			palette == null || palette.length == 768) : "cinematic palette bug";
		// TODO Auto-generated method stub

	}

	/* 
	 * @see jake2.render.Renderer#BeginFrame(float)
	 */
	public void BeginFrame(float camera_separation) {
		// TODO Auto-generated method stub

	}

	/* 
	 * @see jake2.render.Renderer#EndFrame()
	 */
	public void EndFrame() {
		// TODO Auto-generated method stub

	}

	/* 
	 * @see jake2.render.Renderer#AppActivate(boolean)
	 */
	public void AppActivate(boolean activate) {
		// TODO Auto-generated method stub

	}

}
