/*
 * Impl.java
 * Copyright (C) 2003
 *
 * $Id: Impl.java,v 1.17 2004-02-22 21:45:47 hoz Exp $
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
import jake2.Globals;
import jake2.qcommon.xcommand_t;
import jake2.sys.KBD;

import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;

import net.java.games.jogl.*;

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
	private xcommand_t callback = null;

	GLCanvas canvas;
	JFrame window;
	
	// window position on the screen
	int window_xpos, window_ypos;

	/**
	 * @return true
	 */
	boolean GLimp_Init(int xpos, int ypos) {
		// do nothing
		window_xpos = xpos;
		window_ypos = ypos;
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

		ri.Cvar_Get("r_fakeFullscreen", "0", Globals.CVAR_ARCHIVE);

		ri.Con_Printf(Defines.PRINT_ALL, "Initializing OpenGL display\n", null);

		if (fullscreen) {
			ri.Con_Printf(Defines.PRINT_ALL, "...setting fullscreen mode " + mode + ":");
		}
		else
			ri.Con_Printf(Defines.PRINT_ALL, "...setting mode " + mode + ":");

		if (!ri.Vid_GetModeInfo(newDim, mode)) {
			ri.Con_Printf(Defines.PRINT_ALL, " invalid mode\n");
			return rserr_invalid_mode;
		}

		ri.Con_Printf(Defines.PRINT_ALL, " " + newDim.width + " " + newDim.height + '\n');

		// destroy the existing window
		GLimp_Shutdown();

		window = new JFrame("Jake2");
		
		GLCanvas canvas = GLDrawableFactory.getFactory().createGLCanvas(new GLCapabilities());

		// TODO Use debug pipeline
		//canvas.setGL(new DebugGL(canvas.getGL()));

		//canvas.setRenderingThread(Thread.currentThread());

		canvas.setNoAutoRedrawMode(true);
		canvas.addGLEventListener(this);

		window.getContentPane().add(canvas);		
		
		
		canvas.setSize(newDim.width, newDim.height);
		window.setLocation(window_xpos, window_ypos);
		//window.setUndecorated(true);
		window.setResizable(false);

		// register event listener
		window.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				R_Shutdown();
				System.out.println("Received event " + e.paramString() + ", exiting...\n");
				System.exit(0);
			}
		});
		
		// D I F F E R E N T   J A K E 2   E V E N T   P R O C E S S I N G      		
		canvas.addKeyListener(KBD.listener);
		canvas.addMouseListener(KBD.listener);
		canvas.addMouseMotionListener(KBD.listener);
		window.addComponentListener(KBD.listener);

		window.pack();
		window.show();
		canvas.requestFocus();

		this.canvas = canvas;

		vid.width = newDim.width;
		vid.height = newDim.height;

		// let the sound and input subsystems know about the new window
		ri.Vid_NewWindow(vid.width, vid.height);

		return rserr_ok;
	}

	void GLimp_BeginFrame(float camera_separation) {
		// do nothing
	}

	protected void GLimp_EndFrame() {
		gl.glFlush();
		// swap buffer
		// but jogl has no method to swap
	}

	protected void GLimp_AppActivate(boolean activate) {
		// do nothing
	}

	boolean QGL_Init(String dll_name) {
		// doesn't need libGL.so or .dll loading
		return true;
	}

	void QGL_Shutdown() {
		// doesn't need libGL.so or .dll loading
		// do nothing
	}

	void GLimp_Shutdown() {
		if (this.window != null) {
			window.dispose();
		}
		post_init = false;
		switchToCallback = false;
	}

	void GLimp_EnableLogging(boolean enable) {
		// doesn't need jogl logging
		// do nothing
	}

	void GLimp_LogNewFrame() {
		// doesn't need jogl logging
		// do nothing
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
			if (callback == null)
				ri.updateScreenCallback();
			else
				callback.execute();
		}
		else {

			// after the first run (initialization) switch to callback
			switchToCallback = true;

			// clear the screen
			gl.glClearColor(0, 0, 0, 0);
			gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);

			//
			// check the post init process
			//
			if (!post_init) {
				ri.Sys_Error(Defines.ERR_FATAL, "Error: can't init JOGL renderer");
			}

			GLimp_EndFrame();
		}
	}

	/* 
	* @see net.java.games.jogl.GLEventListener#displayChanged(net.java.games.jogl.GLDrawable, boolean, boolean)
	*/
	public void displayChanged(GLDrawable drawable, boolean arg1, boolean arg2) {
		// do nothing
	}

	/* 
	* @see net.java.games.jogl.GLEventListener#reshape(net.java.games.jogl.GLDrawable, int, int, int, int)
	*/
	public void reshape(GLDrawable drawable, int x, int y, int width, int height) {

		vid.height = height;
		vid.width = width;

		ri.Vid_NewWindow(width, height);
	}

	/* 
	 * @see jake2.client.refexport_t#updateScreen()
	 */
	public void updateScreen(xcommand_t callback) {
		if (canvas == null) {
			throw new IllegalStateException("Refresh modul \"" + DRIVER_NAME + "\" have to be initialized.");
		}
		this.callback = callback;
		canvas.display();
	}
}
