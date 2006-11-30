/*
 * JoglCommon.java
 * Copyright (C) 2004
 * 
 * $Id: Jsr231Driver.java,v 1.11 2006-11-30 17:21:39 cawe Exp $
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

package jake2.render.opengl;

import jake2.Defines;
import jake2.client.VID;
import jake2.qcommon.Cbuf;
import jake2.qcommon.xcommand_t;
import jake2.render.Base;
import jake2.sys.JOGLKBD;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.LinkedList;

import javax.media.opengl.*;
import javax.swing.ImageIcon;

/**
 * JoglCommon
 */
public abstract class Jsr231Driver extends Jsr231GL implements GLDriver {

    protected Jsr231Driver() {
        // singleton
    }
    
	private GraphicsDevice device;
	private DisplayMode oldDisplayMode; 
	private Display display;
	Frame window;

	// window position on the screen
	int window_xpos, window_ypos;

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
	 * @return enum Base.rserr_t
	 */
	public int setMode(Dimension dim, int mode, boolean fullscreen) {

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
			return Base.rserr_invalid_mode;
		}

		VID.Printf(Defines.PRINT_ALL, " " + newDim.width + " " + newDim.height + '\n');

		// destroy the existing window
		shutdown();

		window = new Frame("Jake2 (jsr231)");
		ImageIcon icon = new ImageIcon(getClass().getResource("/icon-small.png"));
		window.setIconImage(icon.getImage());
		
		Display canvas = new Display(new GLCapabilities());
		// we want keypressed events for TAB key
		canvas.setFocusTraversalKeysEnabled(false);

		window.add(canvas);	
		canvas.setSize(newDim.width, newDim.height);

		// register event listener
		window.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
                Cbuf.ExecuteText(Defines.EXEC_APPEND, "quit");
			}
		});
		
		// D I F F E R E N T   J A K E 2   E V E N T   P R O C E S S I N G      		
		window.addComponentListener(JOGLKBD.listener);
		canvas.addKeyListener(JOGLKBD.listener);
		canvas.addMouseListener(JOGLKBD.listener);
		canvas.addMouseMotionListener(JOGLKBD.listener);
		canvas.addMouseWheelListener(JOGLKBD.listener);
				        
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
		
		this.display = canvas;

		Base.setVid(newDim.width, newDim.height);

		// let the sound and input subsystems know about the new window
		VID.NewWindow(newDim.width, newDim.height);
		setGL(display.getGL());
		init(0, 0);
        
		return Base.rserr_ok;
	}

	public void shutdown() {
		if (oldDisplayMode != null && device.getFullScreenWindow() != null) {
			try {
				if (device.isFullScreenSupported())
					device.setDisplayMode(oldDisplayMode);
				device.setFullScreenWindow(null);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		if (window != null) {
		    display.destroy();
		    window.dispose();
		}
	}

	/**
	 * @return true
	 */
	public boolean init(int xpos, int ypos) {
	    // set window position
	    window_xpos = xpos;
	    window_ypos = ypos;
	    // clear the screen
	    // first buffer
	    beginFrame(0.0f);
	    glClearColor(0, 0, 0, 0);
	    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
	    endFrame();
	    // second buffer
	    beginFrame(0.0f);
	    glClearColor(0, 0, 0, 0);
	    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
	    endFrame();
	    return true;
	}

    public void beginFrame(float camera_separation) {
        display.activate();
    }
    
    public void endFrame() {
	glFlush();
	display.update();
    }

    public void appActivate(boolean activate) {
        // do nothing
    }

    public void enableLogging(boolean enable) {
        // do nothing
    }

    public void logNewFrame() {
        // do nothing
    }
    
    /*
     * @see jake2.client.refexport_t#updateScreen()
     */

    public void updateScreen(xcommand_t callback) {
	callback.execute();
    }

    protected void activate() {
        display.activate();
    }
    
    // --------------------------------------------------------------------------
    
    private static class Display extends Canvas {
	private GLDrawable drawable;

        private GLContext context;

        public Display(GLCapabilities capabilities) {
            super(unwrap((AWTGraphicsConfiguration)GLDrawableFactory.getFactory().chooseGraphicsConfiguration(capabilities, null, null)));
            drawable = GLDrawableFactory.getFactory().getGLDrawable(this, capabilities, null);
            context = drawable.createContext(null);
        }

        GL getGL() {
            activate();
            return context.getGL();
        }
        /** <B>Overrides:</B>
        <DL><DD><CODE>paint</CODE> in class <CODE>java.awt.Component</CODE></DD></DL> */
        // Overridden from Canvas to prevent the AWT's clearing of the
        // canvas from interfering with the OpenGL rendering.
        public void paint(Graphics g) {
            // do nothing
        }
        
        /** <B>Overrides:</B>
        <DL><DD><CODE>update</CODE> in class <CODE>java.awt.Component</CODE></DD></DL> */
        // Overridden from Canvas to prevent the AWT's clearing of the
        // canvas from interfering with the OpenGL rendering.
        public void update(Graphics g) {
            // do nothing
            //paint(g);
        }

        @Override
	public void resize(int width, int height) {
	    super.resize(width, height);
	    getGL().glViewport(0, 0, width, width);
	}

        public void addNotify() {
            super.addNotify();
            super.setBackground(Color.BLACK);
            drawable.setRealized(true);
        }

        public void removeNotify() {
            drawable.setRealized(false);
            super.removeNotify();
        }

        void activate() {
            if (GLContext.getCurrent() != context)
                context.makeCurrent();
        }

        private void release() {
            if (GLContext.getCurrent() == context)
                context.release();
        }

        void update() {
            release();
            drawable.swapBuffers();
        }

        void destroy() {
            release();
            context.destroy();
            drawable.setRealized(false);
        }

        private static GraphicsConfiguration unwrap(AWTGraphicsConfiguration config) {
            if (config == null) {
              return null;
            }
            return config.getGraphicsConfiguration();
        }
    }
}
