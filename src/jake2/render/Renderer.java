/*
 * Renderer.java
 * Copyright (C) 2003
 *
 * $Id: Renderer.java,v 1.2 2003-11-21 23:28:53 cwei Exp $
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
 * Renderer
 * 
 * @author cwei
 */
public abstract class Renderer {

	private static Renderer impl = null;

	Renderer() {
	}

	/**
	 * Factory method to get the Renderer implementation.
	 * @return Renderer singleton
	 */
	public static Renderer getInstance() {
		if (impl == null) {
			impl = new JoglRenderer();
		}
		return impl;
	}

	// ============================================================================
	// public interface for Renderer implementations
	//
	// ref.h, refexport_t
	// ============================================================================
	//
	// these are the functions exported by the refresh module
	//
	// called when the library is loaded
	public abstract boolean Init();

	// called before the library is unloaded
	public abstract void Shutdown();

	// All data that will be used in a level should be
	// registered before rendering any frames to prevent disk hits,
	// but they can still be registered at a later time
	// if necessary.
	//
	// EndRegistration will free any remaining data that wasn't registered.
	// Any model_s or skin_s pointers from before the BeginRegistration
	// are no longer valid after EndRegistration.
	//
	// Skins and images need to be differentiated, because skins
	// are flood filled to eliminate mip map edge errors, and pics have
	// an implicit "pics/" prepended to the name. (a pic name that starts with a
	// slash will not use the "pics/" prefix or the ".pcx" postfix)
	public abstract void BeginRegistration(String map);
	public abstract model_t RegisterModel(String name);
	public abstract image_t RegisterSkin(String name);
	public abstract image_t RegisterPic(String name);
	public abstract void SetSky(String name, float rotate, /* vec3_t */
	float[] axis);
	public abstract void EndRegistration();

	public abstract void RenderFrame(refdef_t fd);

	public abstract void DrawGetPicSize(int[] w, int[] h, String name);
	// will return 0 0 if not found
	public abstract void DrawPic(int x, int y, String name);
	public abstract void DrawStretchPic(
		int x,
		int y,
		int w,
		int h,
		String name);
	public abstract void DrawChar(int x, int y, int c);
	public abstract void DrawTileClear(int x, int y, int w, int h, String name);
	public abstract void DrawFill(int x, int y, int w, int h, int c);
	public abstract void DrawFadeScreen();

	// Draw images for cinematic rendering (which can have a different palette). Note that calls
	public abstract void DrawStretchRaw(
		int x,
		int y,
		int w,
		int h,
		int cols,
		int rows,
		byte[] data);

	/*
	** video mode and refresh state management entry points
	*/
	/* 256 r,g,b values;	null = game palette */
	public abstract void CinematicSetPalette(final byte[] palette);
	public abstract void BeginFrame(float camera_separation);
	public abstract void EndFrame();

	public abstract void AppActivate(boolean activate);
}
