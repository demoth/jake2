/*
 * Impl.java
 * Copyright (C) 2003
 *
 * $Id: Impl.java,v 1.1 2003-12-29 01:57:00 cwei Exp $
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

import jake2.Defines;
import jake2.Enum;
import jake2.qcommon.Cvar;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;

import net.java.games.jogl.*;
import net.java.games.jogl.util.GLUT;

/**
 * Impl
 *  
 * @author cwei
 */
public class Impl extends Misc implements GLEventListener {
	
	public static final String DRIVER_NAME = "jogl";
	
	// handles the post initialization with JoglRenderer
	protected boolean post_init = false;
	
	// switch to updateScreen callback
	private boolean switchToCallback = false;
	
	GLCanvas canvas;
	JFrame window;
	
	/**
	 * @return true
	 */
	boolean GLimp_Init() {
		// do nothing
		return true;
	}

	/**
	 * @param dim
	 * @param mode
	 * @param fullscreen
	 * @return enum rserr_t
	 */
	int GLimp_SetMode(Dimension dim, int mode, boolean fullscreen) {

		Dimension newDim = new Dimension();

		ri.Cvar_Get("r_fakeFullscreen", "0", Cvar.ARCHIVE);

		ri.Con_Printf(Defines.PRINT_ALL, "Initializing OpenGL display\n", null);

		if (fullscreen) {
			ri.Con_Printf(Defines.PRINT_ALL, "...setting fullscreen mode " + mode + ":");
		} else
			ri.Con_Printf(Defines.PRINT_ALL, "...setting mode " + mode + ":");

		if (!ri.Vid_GetModeInfo(newDim, mode)) {
			ri.Con_Printf(Defines.PRINT_ALL, " invalid mode\n");
			return Enum.rserr_invalid_mode;
		}
		
		ri.Con_Printf(Defines.PRINT_ALL,	" " + newDim.width + " " + newDim.height + '\n');

		// destroy the existing window
		GLimp_Shutdown();

		window = new JFrame("Jake2");
		GLCanvas canvas =
			GLDrawableFactory.getFactory().createGLCanvas(new GLCapabilities());

		// Use debug pipeline
		canvas.setGL(new DebugGL(canvas.getGL()));
		//canvas.setGL(canvas.getGL());

		//canvas.setRenderingThread(Thread.currentThread());

		canvas.setNoAutoRedrawMode(true);
		canvas.addGLEventListener(this);

		window.getContentPane().add(canvas);
		window.setSize(newDim.width, newDim.height);
		window.setUndecorated(true);
		window.setResizable(false);
		
		window.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				System.exit(0);
			}
		});
		window.show();
		this.canvas = canvas;

		vid.width = newDim.width;
		vid.height = newDim.height;

		// let the sound and input subsystems know about the new window
		ri.Vid_NewWindow(vid.width, vid.height);

		return Enum.rserr_ok;
	}

	protected void GLimp_EndFrame() {
		gl.glFlush();
		// swap buffer
		// but jogl has no method to swap
	}

	protected void GLimp_AppActivate(boolean activate) {
		// TODO impl GLimp_AppActivate(boolean activate)

	}

	
	
	void QGL_Shutdown() {
		// TODO impl QGL_Shutdown()
	}
	
	void GLimp_Shutdown() {
		if (this.window != null) {
			window.dispose();
		}
		post_init = false;
		switchToCallback = false;
	}
	

	// ============================================================================
	// GLEventListener interface
	// ============================================================================

	/* 
	* @see net.java.games.jogl.GLEventListener#init(net.java.games.jogl.GLDrawable)
	*/
	public void init(GLDrawable drawable) {
		this.gl = drawable.getGL();
		this.glu = drawable.getGLU();

		// this is a hack to run R_init() in gl context
		post_init = R_Init2();
	}

	/* 
	 * @see net.java.games.jogl.GLEventListener#display(net.java.games.jogl.GLDrawable)
	 */
	public void display(GLDrawable drawable) {
		this.gl = drawable.getGL();
		this.glu = drawable.getGLU();
		
		if (switchToCallback) {
			ri.updateScreenCallback();		
		} else {

			// after the first run (initialization) switch to callback
			switchToCallback = true;
			
			//
			// Render welcome
			//
			gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
		
			R_SetGL2D();

			int font = GLUT.BITMAP_TIMES_ROMAN_24;
		
			String text;
			if (post_init) {
				text = "Welcome to JOGL renderer :-)";
				gl.glColor3f(0.0f, 0.8f, 0.0f);
			} else {
				text = "Error: can't init JOGL renderer";
				gl.glColor3f(0.8f, 0.0f, 0.0f);
			}

			int len = glut.glutBitmapLength(font, text);
			gl.glWindowPos2i((vid.width - len)/2, vid.height/2);
			glut.glutBitmapString(gl, font, text);
			
			gl.glFlush();
		}
	}
	
	/* 
	* @see net.java.games.jogl.GLEventListener#displayChanged(net.java.games.jogl.GLDrawable, boolean, boolean)
	*/
	public void displayChanged(
		GLDrawable drawable,
		boolean arg1,
		boolean arg2) {
		// do nothing
	}

	/* 
	* @see net.java.games.jogl.GLEventListener#reshape(net.java.games.jogl.GLDrawable, int, int, int, int)
	*/
	public void reshape(
		GLDrawable drawable,
		int x,
		int y,
		int width,
		int height) {
			
		vid.height = height;
		vid.width = width;
		
		ri.Vid_NewWindow(width, height);
	}

	/* 
	 * @see jake2.client.refexport_t#updateScreen()
	 */
	public void updateScreen() {
		if (canvas == null) {
			throw new IllegalStateException(
					"Refresh modul \"" + DRIVER_NAME + "\" have to be initialized.");
		}
		canvas.display();
	}

}
