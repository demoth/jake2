/*
 * LWJGLBase.java
 * Copyright (C) 2004
 * 
 * $Id: LWJGLBase.java,v 1.4 2006-10-31 13:06:33 salomo Exp $
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
package jake2.render.lwjgl;

import jake2.Defines;
import jake2.client.VID;
import jake2.client.viddef_t;
import jake2.game.cvar_t;
import jake2.qcommon.Com;
import jake2.qcommon.xcommand_t;

import java.awt.Dimension;
import java.util.LinkedList;

import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.*;
import org.lwjgl.util.GLImpl;

/**
 * LWJGLBase
 * 
 * @author dsanders/cwei
 */
public abstract class LWJGLBase extends  jake2.render.common.Misc {
	
	// IMPORTED FUNCTIONS
	protected DisplayMode oldDisplayMode; 

	protected GLImpl gl = new GLImpl();

	// handles the post initialization with LWJGLRenderer
	protected abstract boolean R_Init2();


	private java.awt.DisplayMode toAwtDisplayMode(DisplayMode m)
	{
		return new java.awt.DisplayMode(m.getWidth(),m.getHeight(),m.getBitsPerPixel(),m.getFrequency());
	}

	public java.awt.DisplayMode[] getModeList() 
	{
		DisplayMode[] modes;
		try {
		    modes = Display.getAvailableDisplayModes();
		} catch (LWJGLException e) {
		    Com.Println(e.getMessage());
		    return new java.awt.DisplayMode[0];
		}
		
		LinkedList l = new LinkedList();
		l.add(toAwtDisplayMode(oldDisplayMode));
		
		for (int i = 0; i < modes.length; i++) {
			DisplayMode m = modes[i];
			
			if (m.getBitsPerPixel() != oldDisplayMode.getBitsPerPixel()) continue;
			if (m.getFrequency() > oldDisplayMode.getFrequency()) continue;
			if (m.getHeight() < 240 || m.getWidth() < 320) continue;
			
			int j = 0;
			java.awt.DisplayMode ml = null;
			for (j = 0; j < l.size(); j++) {
				ml = (java.awt.DisplayMode)l.get(j);
				if (ml.getWidth() > m.getWidth()) break;
				if (ml.getWidth() == m.getWidth() && ml.getHeight() >= m.getHeight()) break;
			}
			if (j == l.size()) {
				l.addLast(toAwtDisplayMode(m));
			} else if (ml.getWidth() > m.getWidth() || ml.getHeight() > m.getHeight()) {
				l.add(j, toAwtDisplayMode(m));
			} else if (m.getFrequency() > ml.getRefreshRate()){
				l.remove(j);
				l.add(j, toAwtDisplayMode(m));
			}
		}
		java.awt.DisplayMode[] ma = new java.awt.DisplayMode[l.size()];
		l.toArray(ma);
		return ma;
	}
	
	public DisplayMode[] getLWJGLModeList() {
		DisplayMode[] modes;
		try {
		    modes = Display.getAvailableDisplayModes();
		} catch (LWJGLException e) {
		    Com.Println(e.getMessage());
		    return new DisplayMode[0];
		}
		
		LinkedList l = new LinkedList();
		l.add(oldDisplayMode);
		
		for (int i = 0; i < modes.length; i++) {
			DisplayMode m = modes[i];
			
			if (m.getBitsPerPixel() != oldDisplayMode.getBitsPerPixel()) continue;
			if (m.getFrequency() > oldDisplayMode.getFrequency()) continue;
			if (m.getHeight() < 240 || m.getWidth() < 320) continue;
			
			int j = 0;
			DisplayMode ml = null;
			for (j = 0; j < l.size(); j++) {
				ml = (DisplayMode)l.get(j);
				if (ml.getWidth() > m.getWidth()) break;
				if (ml.getWidth() == m.getWidth() && ml.getHeight() >= m.getHeight()) break;
			}
			if (j == l.size()) {
				l.addLast(m);
			} else if (ml.getWidth() > m.getWidth() || ml.getHeight() > m.getHeight()) {
				l.add(j, m);
			} else if (m.getFrequency() > ml.getFrequency()){
				l.remove(j);
				l.add(j, m);
			}
		}
		DisplayMode[] ma = new DisplayMode[l.size()];
		l.toArray(ma);
		return ma;
	}
	
	private DisplayMode findDisplayMode(Dimension dim) {
		DisplayMode mode = null;
		DisplayMode m = null;
		DisplayMode[] modes = getLWJGLModeList();
		int w = dim.width;
		int h = dim.height;
		
		for (int i = 0; i < modes.length; i++) {
			m = modes[i];
			if (m.getWidth() == w && m.getHeight() == h) {
				mode = m;
				break;
			}
		}
		if (mode == null) mode = oldDisplayMode;
		return mode;		
	}
		
	String getModeString(DisplayMode m) {
		StringBuffer sb = new StringBuffer();
		sb.append(m.getWidth());
		sb.append('x');
		sb.append(m.getHeight());
		sb.append('x');
		sb.append(m.getBitsPerPixel());
		sb.append('@');
		sb.append(m.getFrequency());
		sb.append("Hz");
		return sb.toString();
	}

	/**
	 * @param dim
	 * @param mode
	 * @param fullscreen
	 * @return enum rserr_t
	 */
	protected int GLimp_SetMode(Dimension dim, int mode, boolean fullscreen) {

		Dimension newDim = new Dimension();

		VID.Printf(Defines.PRINT_ALL, "Initializing OpenGL display\n");

		VID.Printf(Defines.PRINT_ALL, "...setting mode " + mode + ":");
		
		/*
		 * fullscreen handling
		 */
		if (oldDisplayMode == null) {
			oldDisplayMode = Display.getDisplayMode();
		}

		if (!VID.GetModeInfo(newDim, mode)) {
			VID.Printf(Defines.PRINT_ALL, " invalid mode\n");
			return rserr_invalid_mode;
		}

		VID.Printf(Defines.PRINT_ALL, " " + newDim.width + " " + newDim.height + '\n');

		// destroy the existing window
		GLimp_Shutdown();

		Display.setTitle("Jake2");

		DisplayMode displayMode = findDisplayMode(newDim);
		newDim.width = displayMode.getWidth();
		newDim.height = displayMode.getHeight();
		
		if (fullscreen) 
		{
			try {
				Display.setDisplayMode(displayMode);
			} 
			catch (LWJGLException e)
			{
				return rserr_invalid_mode; 
			}	
				
			Display.setLocation(0,0);

			try {
				Display.setFullscreen(fullscreen);
			} 
			catch (LWJGLException e)
			{
				return rserr_invalid_fullscreen; 
			}	

			VID.Printf(Defines.PRINT_ALL, "...setting fullscreen " + getModeString(displayMode) + '\n');

		} 
		else 
		{
			try 
			{
				Display.setDisplayMode(displayMode);
			} 
			catch (LWJGLException e)
			{
				return rserr_invalid_mode;
			}

			try {
				Display.setFullscreen(false);
			} 
			catch (LWJGLException e)
			{
				return rserr_invalid_fullscreen; 
			}	
			Display.setLocation(window_xpos, window_ypos);
		}

		vid.width = newDim.width;
		vid.height = newDim.height;
		
		try
		{
			Display.create();
		} 
		catch (LWJGLException e)
		{
			return rserr_unknown; 
		}	
		
		// let the sound and input subsystems know about the new window
		VID.NewWindow(vid.width, vid.height);
		return rserr_ok;
	}

	protected void GLimp_Shutdown() {
		if (oldDisplayMode != null && Display.isFullscreen()) {
			try {
				Display.setDisplayMode(oldDisplayMode);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		while (Display.isCreated()) {
			Display.destroy();
		} 
	}

	/**
	 * @return true
	 */
	protected boolean GLimp_Init(int xpos, int ypos) {
		// do nothing
		window_xpos = xpos;
		window_ypos = ypos;
		return true;
	}

	protected void GLimp_EndFrame() {
		gl.glFlush();
		// swap buffers
		Display.update();
	}

	protected void GLimp_BeginFrame(float camera_separation) {
		// do nothing
	}

	protected void GLimp_AppActivate(boolean activate) {
		// do nothing
	}

	protected void GLimp_EnableLogging(boolean enable) {
		// do nothing
	}

	protected void GLimp_LogNewFrame() {
		// do nothing
	}

	protected void GLimp_Finish() {
		gl.glFinish();
	}
	
	/**
	 * this is a hack for jogl renderers.
	 * @param callback
	 */
	public final void updateScreen(xcommand_t callback) {
		callback.execute();
	}	
}
