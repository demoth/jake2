/*
 * JoglCommon.java
 * Copyright (C) 2004
 * 
 * $Id: JoglBase.java,v 1.9 2004-09-19 20:32:05 cawe Exp $
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
import jake2.client.*;
import jake2.game.cvar_t;
import jake2.qcommon.Cbuf;
import jake2.qcommon.xcommand_t;
import jake2.sys.KBD;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.LinkedList;

import javax.swing.ImageIcon;
import javax.swing.JFrame;

import net.java.games.jogl.*;
import net.java.games.jogl.util.GLUT;

/**
 * JoglCommon
 */
public abstract class JoglBase implements GLEventListener {

	// IMPORTED FUNCTIONS
	protected GraphicsDevice device;
	protected DisplayMode oldDisplayMode; 
	protected GLCanvas canvas;
	JFrame window;

	protected GL gl;
	protected GLU glu;
	protected GLUT glut = new GLUT();

	// window position on the screen
	int window_xpos, window_ypos;
	protected viddef_t vid = new viddef_t();

	// handles the post initialization with JoglRenderer
	protected boolean post_init = false;
	protected boolean contextInUse = false;
	protected abstract boolean R_Init2();
	
	protected final xcommand_t INIT_CALLBACK = new xcommand_t() {
		public void execute() {
			// only used for the first run (initialization)
			// clear the screen
			gl.glClearColor(0, 0, 0, 0);
			gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);

			//
			// check the post init process
			//
			if (!post_init) {
				VID.Printf(Defines.PRINT_ALL, "Missing multi-texturing for FastJOGL renderer\n");
			}

			GLimp_EndFrame();
		}
	};
	protected xcommand_t callback = INIT_CALLBACK;
	
	protected cvar_t vid_fullscreen;

	// enum rserr_t
	protected static final int rserr_ok = 0;
	protected static final int rserr_invalid_fullscreen = 1;
	protected static final int rserr_invalid_mode = 2;
	protected static final int rserr_unknown = 3;
	
	public DisplayMode[] getModeList() {
		DisplayMode[] modes = device.getDisplayModes();
		LinkedList l = new LinkedList();
		l.add(oldDisplayMode);
		
		for (int i = 0; i < modes.length; i++) {
			DisplayMode m = modes[i];
			
			if (m.getBitDepth() != oldDisplayMode.getBitDepth()) continue;
			if (m.getRefreshRate() > oldDisplayMode.getRefreshRate()) continue;
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
			} else if (m.getRefreshRate() > ml.getRefreshRate()){
				l.remove(j);
				l.add(j, m);
			}
		}
		DisplayMode[] ma = new DisplayMode[l.size()];
		l.toArray(ma);
		return ma;
	}
	
	DisplayMode findDisplayMode(Dimension dim) {
		DisplayMode mode = null;
		DisplayMode m = null;
		DisplayMode[] modes = getModeList();
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
		sb.append(m.getBitDepth());
		sb.append('@');
		sb.append(m.getRefreshRate());
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
		GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
		device = env.getDefaultScreenDevice();
       
		if (oldDisplayMode == null) {
			oldDisplayMode = device.getDisplayMode();
		}

		if (!VID.GetModeInfo(newDim, mode)) {
			VID.Printf(Defines.PRINT_ALL, " invalid mode\n");
			return rserr_invalid_mode;
		}

		VID.Printf(Defines.PRINT_ALL, " " + newDim.width + " " + newDim.height + '\n');

		// destroy the existing window
		GLimp_Shutdown();

		window = new JFrame("Jake2");
		ImageIcon icon = new ImageIcon(getClass().getResource("/icon-small.png"));
		window.setIconImage(icon.getImage());
		
		GLCanvas canvas = GLDrawableFactory.getFactory().createGLCanvas(new GLCapabilities());

		// we want keypressed events for TAB key
		canvas.setFocusTraversalKeysEnabled(false);
		
		// TODO Use debug pipeline
		//canvas.setGL(new DebugGL(canvas.getGL()));

		canvas.setNoAutoRedrawMode(true);
// TODO this and a new JOGL-release solves the flickering bug (Loading)
// change also GLimp_EndFrame()
//		canvas.setAutoSwapBufferMode(false);
		canvas.addGLEventListener(this);

		window.getContentPane().add(canvas);	
		canvas.setSize(newDim.width, newDim.height);

		// register event listener
		window.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				Cbuf.ExecuteText(Defines.EXEC_APPEND, "quit");
			}
		});
		
		// D I F F E R E N T   J A K E 2   E V E N T   P R O C E S S I N G      		
		window.addComponentListener(KBD.listener);
		canvas.addKeyListener(KBD.listener);
		canvas.addMouseListener(KBD.listener);
		canvas.addMouseMotionListener(KBD.listener);
				        
		if (fullscreen) {
			
			DisplayMode displayMode = findDisplayMode(newDim);
			
			newDim.width = displayMode.getWidth();
			newDim.height = displayMode.getHeight();
			window.setUndecorated(true);
			window.setResizable(false);
			
			device.setFullScreenWindow(window);
			
			if (device.isFullScreenSupported())
				device.setDisplayMode(displayMode);
			
			window.setLocation(0, 0);
			window.setSize(displayMode.getWidth(), displayMode.getHeight());
			canvas.setSize(displayMode.getWidth(), displayMode.getHeight());

			VID.Printf(Defines.PRINT_ALL, "...setting fullscreen " + getModeString(displayMode) + '\n');

		} else {
			window.setLocation(window_xpos, window_ypos);
			window.pack();
			window.setResizable(false);
			window.setVisible(true);
		}
		
		while (!canvas.isDisplayable()) {
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {}
		}
		canvas.requestFocus();
		
		this.canvas = canvas;

		vid.width = newDim.width;
		vid.height = newDim.height;

		// let the sound and input subsystems know about the new window
		VID.NewWindow(vid.width, vid.height);

		return rserr_ok;
	}

	protected void GLimp_Shutdown() {
		if (oldDisplayMode != null && device.getFullScreenWindow() != null) {
			try {
				if (device.isFullScreenSupported())
					device.setDisplayMode(oldDisplayMode);
				device.setFullScreenWindow(null);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		if (this.window != null) {
			window.dispose();
		}
		post_init = false;
		callback = INIT_CALLBACK;
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
		// swap buffer
//		TODO this and a new JOGL-release solves the flickering bug (Loading)
//		canvas.swapBuffers();
	}
	protected void GLimp_BeginFrame(float camera_separation) {
		// do nothing
	}

	protected void GLimp_AppActivate(boolean activate) {
		// do nothing
	}

	protected void GLimp_EnableLogging(boolean enable) {
		// doesn't need jogl logging
		// do nothing
	}

	protected void GLimp_LogNewFrame() {
		// doesn't need jogl logging
		// do nothing
	}

	/* 
	 * @see jake2.client.refexport_t#updateScreen()
	 */
	public void updateScreen() {
		this.callback = INIT_CALLBACK;
		canvas.display();
	}
	
	public void updateScreen(xcommand_t callback) {
		this.callback = callback;
		canvas.display();
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
		
		contextInUse = true;
		callback.execute();
		contextInUse = false;
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
		// do nothing
	}
	
}
