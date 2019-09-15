/*
 * LWJGLBase.java
 * Copyright (C) 2004
 * 
 * $Id: LwjglDriver.java,v 1.5 2007-11-03 13:04:23 cawe Exp $
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
import jake2.qcommon.Com;
import jake2.qcommon.Command;
import jake2.render.Base;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;

import java.awt.*;
import java.util.Collections;
import java.util.LinkedList;

/**
 * LWJGLBase
 * 
 * @author dsanders/cwei
 */
public abstract class LwjglDriver extends LwjglGL implements GLDriver {

    protected LwjglDriver() {
        // see LwjglRenderer
    }

    private DisplayMode oldDisplayMode;

    // window position on the screen
    int window_xpos, window_ypos;

    private java.awt.DisplayMode toAwtDisplayMode(DisplayMode m) {
        return new java.awt.DisplayMode(m.getWidth(), m.getHeight(), m
                .getBitsPerPixel(), m.getFrequency());
    }

    public java.awt.DisplayMode[] getModeList() {
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

            if (m.getBitsPerPixel() != oldDisplayMode.getBitsPerPixel())
                continue;
            if (m.getFrequency() > oldDisplayMode.getFrequency())
                continue;
            if (m.getHeight() < 240 || m.getWidth() < 320)
                continue;

            int j = 0;
            java.awt.DisplayMode ml = null;
            for (j = 0; j < l.size(); j++) {
                ml = (java.awt.DisplayMode) l.get(j);
                if (ml.getWidth() > m.getWidth())
                    break;
                if (ml.getWidth() == m.getWidth()
                        && ml.getHeight() >= m.getHeight())
                    break;
            }
            if (j == l.size()) {
                l.addLast(toAwtDisplayMode(m));
            } else if (ml.getWidth() > m.getWidth()
                    || ml.getHeight() > m.getHeight()) {
                l.add(j, toAwtDisplayMode(m));
            } else if (m.getFrequency() > ml.getRefreshRate()) {
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

            if (m.getBitsPerPixel() != oldDisplayMode.getBitsPerPixel())
                continue;
            if (m.getFrequency() > Math.max(60, oldDisplayMode.getFrequency()))
                continue;
            if (m.getHeight() < 240 || m.getWidth() < 320)
                continue;
            if (m.getHeight() > oldDisplayMode.getHeight() || m.getWidth() > oldDisplayMode.getWidth())
                continue;

            int j = 0;
            DisplayMode ml = null;
            for (j = 0; j < l.size(); j++) {
                ml = (DisplayMode) l.get(j);
                if (ml.getWidth() > m.getWidth())
                    break;
                if (ml.getWidth() == m.getWidth()
                        && ml.getHeight() >= m.getHeight())
                    break;
            }
            if (j == l.size()) {
                l.addLast(m);
            } else if (ml.getWidth() > m.getWidth()
                    || ml.getHeight() > m.getHeight()) {
                l.add(j, m);
            } else if (m.getFrequency() > ml.getFrequency()) {
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
        if (mode == null)
            mode = oldDisplayMode;
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
    public int setMode(Dimension dim, int mode, boolean fullscreen) {

        Dimension newDim = new Dimension();

        Com.Printf(Defines.PRINT_ALL, "Initializing OpenGL display\n");

        Com.Printf(Defines.PRINT_ALL, "...setting mode " + mode + ":");

        /*
         * fullscreen handling
         */
        if (oldDisplayMode == null) {
            oldDisplayMode = Display.getDisplayMode();
        }

        if (!VID.GetModeInfo(newDim, mode)) {
            Com.Printf(Defines.PRINT_ALL, " invalid mode\n");
            return Base.rserr_invalid_mode;
        }

        Com.Printf(Defines.PRINT_ALL, " " + newDim.width + " " + newDim.height
                + '\n');

        // destroy the existing window
        shutdown();

        Display.setTitle("Jake2 (lwjgl)");

        DisplayMode displayMode = findDisplayMode(newDim);
        newDim.width = displayMode.getWidth();
        newDim.height = displayMode.getHeight();

        if (fullscreen) {
            try {
                Display.setDisplayMode(displayMode);
            } catch (LWJGLException e) {
                return Base.rserr_invalid_mode;
            }

            Display.setLocation(0, 0);

            try {
                Display.setFullscreen(fullscreen);
            } catch (LWJGLException e) {
                return Base.rserr_invalid_fullscreen;
            }

            Com.Printf(Defines.PRINT_ALL, "...setting fullscreen "
                    + getModeString(displayMode) + '\n');

        } else {
            try {
                Display.setDisplayMode(displayMode);
            } catch (LWJGLException e) {
                return Base.rserr_invalid_mode;
            }

            try {
                Display.setFullscreen(false);
            } catch (LWJGLException e) {
                return Base.rserr_invalid_fullscreen;
            }
            //Display.setLocation(window_xpos, window_ypos);
        }

        Base.setVid(newDim.width, newDim.height);

        // vid.width = newDim.width;
        // vid.height = newDim.height;

        try {
            Display.create();
        } catch (LWJGLException e) {
            return Base.rserr_unknown;
        }

        // let the sound and input subsystems know about the new window
        VID.NewWindow(newDim.width, newDim.height);
        return Base.rserr_ok;
    }

    public void shutdown() {
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
    public boolean init(int xpos, int ypos) {
        // do nothing
        window_xpos = xpos;
        window_ypos = ypos;
        return true;
    }

    public void beginFrame(float camera_separation) {
        // do nothing
    }

    public void endFrame() {
        glFlush();
        // swap buffers
        Display.update();
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

    /**
     * this is a hack for jogl renderers.
     * 
     * @param callback
     */
    public final void updateScreen(Command callback) {
        callback.execute(Collections.emptyList());
    }

}
