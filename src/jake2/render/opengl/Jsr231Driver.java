/*
 * Jsr231Driver.java
 * Copyright (C) 2004
 * 
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
import jake2.Globals;
import jake2.SizeChangeListener;
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
	private volatile Display display;
	private volatile Frame window;

        // This is either the above Window reference or the global
        // applet if we're running in applet mode
        private volatile Container container;

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

		final Dimension newDim = new Dimension();

		VID.Printf(Defines.PRINT_ALL, "Initializing OpenGL display\n");

		VID.Printf(Defines.PRINT_ALL, "...setting mode " + mode + ":");
		
                if (Globals.appletMode && container == null) {
                    container = (Container) Globals.applet;
                }

		/*
		 * full screen handling
		 */
		if (device == null) {
			GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
			device = env.getDefaultScreenDevice();
		}
       
                if (oldDisplayMode == null) {
                    oldDisplayMode = device.getDisplayMode();
                }

                if (!VID.GetModeInfo(newDim, mode)) {
                    VID.Printf(Defines.PRINT_ALL, " invalid mode\n");
                    return Base.rserr_invalid_mode;
                }

                VID.Printf(Defines.PRINT_ALL, " " + newDim.width + " " + newDim.height + '\n');

                if (!Globals.appletMode) {
                    // destroy the existing window
                    if (window != null) shutdown();

                    window = new Frame("Jake2 (jsr231)");
                    container = window;
                    ImageIcon icon = new ImageIcon(getClass().getResource("/icon-small.png"));
                    window.setIconImage(icon.getImage());
                    window.setLayout(new GridBagLayout());
                    // register event listener
                    window.addWindowListener(new WindowAdapter() {
                            public void windowClosing(WindowEvent e) {
                                Cbuf.ExecuteText(Defines.EXEC_APPEND, "quit");
                            }
                        });
                }
		
                if (Globals.appletMode) {
                    // Destroy the previous display if there is one
                    shutdown();

                    // We don't support full-screen mode
                    fullscreen = false;

                    // We need to feed the container to the JOGL
                    // keyboard class manually because we'll never get
                    // a component shown event for it
                    JOGLKBD.Init(container);
                }

                Display canvas = new Display(new GLCapabilities());
                // we want keypressed events for TAB key
                canvas.setFocusTraversalKeysEnabled(false);
                canvas.setSize(newDim.width, newDim.height);

                // the OpenGL canvas grows and shrinks with the window
                final GridBagConstraints gbc = new GridBagConstraints();
                gbc.fill = GridBagConstraints.BOTH;
                gbc.weightx = gbc.weighty = 1;
		
                // D I F F E R E N T   J A K E 2   E V E N T   P R O C E S S I N G      		
                container.addComponentListener(JOGLKBD.listener);
                canvas.addKeyListener(JOGLKBD.listener);
                canvas.addMouseListener(JOGLKBD.listener);
                canvas.addMouseMotionListener(JOGLKBD.listener);
                canvas.addMouseWheelListener(JOGLKBD.listener);
				        
		if (fullscreen) {

                    container.add(canvas, gbc);

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
                    if (!Globals.appletMode) {
                        container.add(canvas, gbc);
                        final Frame f2 = window;
                        try {
                            EventQueue.invokeAndWait(new Runnable() {
                                    public void run() {
                                        //f2.setLocation(window_xpos, window_ypos);
                                        f2.pack();
                                        f2.setResizable(false);
                                        f2.setVisible(true);
                                    }
                                });
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else {
                        final Display fd = canvas;
                        try {
                            EventQueue.invokeAndWait(new Runnable() {
                                    public void run() {
                                        container.add(fd, BorderLayout.CENTER);
                                        // Notify the size listener about the change
                                        SizeChangeListener listener = Globals.sizeChangeListener;
                                        if (listener != null) {
                                            listener.sizeChanged(newDim.width, newDim.height);
                                        }
                                        fd.setSize(newDim.width, newDim.height);
                                    }
                                });
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
		}
		
                if (!Globals.appletMode) {
                    while (!canvas.isDisplayable() || !window.isDisplayable()) {
			try {
                            Thread.sleep(100);
			} catch (InterruptedException e) {}
                    }
                }
		canvas.requestFocus();
		
		this.display = canvas;

		setGL(display.getGL());
		init(0, 0);
        
		return Base.rserr_ok;
	}

	public void shutdown() {
                if (!Globals.appletMode) {
                    try {
			EventQueue.invokeAndWait(new Runnable() {
				public void run() {
                                    if (oldDisplayMode != null
                                        && device.getFullScreenWindow() != null) {
                                        try {
                                            if (device.isFullScreenSupported()) {
                                                if (!device.getDisplayMode().equals(oldDisplayMode))
                                                    device.setDisplayMode(oldDisplayMode);

                                            }
                                            device.setFullScreenWindow(null);
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    }
				}
                            });
                    } catch (Exception e) {
			e.printStackTrace();
                    }

                    if (window != null) {
			if (display != null) display.destroy();
			window.dispose();
			while (window.isDisplayable()) {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                            }

			}
                    }
                } else {
                    if (display != null) {
                        display.destroy();
                        // Remove the old display if there is one
                        container.remove(display);
                    }
                }
		display = null;
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
	    glViewport(0, 0, display.getWidth(), display.getHeight());
	    glClearColor(0, 0, 0, 0);
	    glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
	    endFrame();
	    // second buffer
	    beginFrame(0.0f);
	    glViewport(0, 0, display.getWidth(), display.getHeight());
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
        
        
    /**
	 * @see java.awt.Component#setBounds(int, int, int, int)
	 */
	public void setBounds(int x, int y, int width, int height) {
	    final int mask = ~0x03;
	    if ((width & 0x03) != 0) {
		width &= mask;
		width += 4;
	    }

//	    System.out.println("display bounds: " + x + ", " + y + ", " +  width + ", " + height);
	    super.setBounds(x, y, width, height);
	    Base.setVid(width, height);
	    // let the sound and input subsystems know about the new window
	    VID.NewWindow(width, height);
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

        public void addNotify() {
            super.addNotify();
            super.setBackground(Color.BLACK);
            drawable.setRealized(true);
        }

        public void removeNotify() {
            if (drawable != null) {
            	drawable.setRealized(false);
                drawable = null;
            }
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
        	if (context != null) {
        		release();
        		context.destroy();
        		context = null;
        	}
        }
        
        private static GraphicsConfiguration unwrap(AWTGraphicsConfiguration config) {
            if (config == null) {
              return null;
            }
            return config.getGraphicsConfiguration();
        }
    }
}
