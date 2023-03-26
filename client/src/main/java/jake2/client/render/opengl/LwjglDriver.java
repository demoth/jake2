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
package jake2.client.render.opengl;

import jake2.client.VID;
import jake2.client.render.Base;
import jake2.qcommon.Com;
import jake2.qcommon.Defines;
import jake2.qcommon.exec.Command;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;

import java.awt.*;
import java.util.Collections;
import java.util.LinkedList;

import static jake2.client.render.Base.window;

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

    private static int oldBitsPerPixel = 32;

    // window position on the screen
    int window_xpos, window_ypos;

    private java.awt.DisplayMode toAwtDisplayMode(GLFWVidMode mode) {
        return new java.awt.DisplayMode(
                mode.width(),
                mode.height(),
                mode.redBits() + mode.greenBits() + mode.blueBits(),
                mode.refreshRate());
    }

    public jake2.client.DisplayMode[] getModeList() {
        long monitor = GLFW.glfwGetPrimaryMonitor();
        GLFWVidMode.Buffer modes = GLFW.glfwGetVideoModes(monitor);

        LinkedList<jake2.client.DisplayMode> l = new LinkedList<>();
        GLFWVidMode currentMode = GLFW.glfwGetVideoMode(GLFW.glfwGetPrimaryMonitor());
        l.add(toJake2DisplayMode(currentMode));

        int oldBitsPerPixel = getCurrentDisplayMode().getBitsPerPixel();
        int oldFrequency = getCurrentDisplayMode().getRefreshRate();

        for (int i = 0; i < modes.limit(); i++) {
            org.lwjgl.glfw.GLFWVidMode m = modes.get(i);

            if (m.redBits() + m.greenBits() + m.blueBits() != oldBitsPerPixel)
                continue;
            if (m.refreshRate() > oldFrequency)
                continue;
            if (m.height() < 240 || m.width() < 320)
                continue;

            int j = 0;
            jake2.client.DisplayMode ml = null;
            for (j = 0; j < l.size(); j++) {
                ml = l.get(j);
                if (ml.getWidth() > m.width())
                    break;
                if (ml.getWidth() == m.width()
                        && ml.getHeight() >= m.height())
                    break;
            }
            jake2.client.DisplayMode displayMode = new jake2.client.DisplayMode(
                    m.width(), m.height(), m.redBits() + m.greenBits() + m.blueBits(), m.refreshRate());

            if (j == l.size()) {
                l.addLast(displayMode);
            } else if (ml.getWidth() > m.width()
                    || ml.getHeight() > m.height()) {
                l.add(j, displayMode);
            } else if (m.refreshRate() > ml.getRefreshRate()) {
                l.remove(j);
                l.add(j, displayMode);
            }
        }
        jake2.client.DisplayMode[] ma = new jake2.client.DisplayMode[l.size()];
        l.toArray(ma);
        return ma;
    }

    private jake2.client.DisplayMode toJake2DisplayMode(org.lwjgl.glfw.GLFWVidMode mode) {
        return new jake2.client.DisplayMode(mode.width(), mode.height(), oldBitsPerPixel, mode.refreshRate());
    }

    private jake2.client.DisplayMode getCurrentDisplayMode() {
        return new jake2.client.DisplayMode(GLFW.glfwGetVideoMode(GLFW.glfwGetPrimaryMonitor()).width(),
                GLFW.glfwGetVideoMode(GLFW.glfwGetPrimaryMonitor()).height(),
                oldBitsPerPixel, GLFW.glfwGetVideoMode(GLFW.glfwGetPrimaryMonitor()).refreshRate());
    }

    private java.awt.DisplayMode getCurrentDisplayModeAwt() {
        long monitor = GLFW.glfwGetPrimaryMonitor();
        if (monitor == 0) {
            throw new NullPointerException("Failed to get primary monitor");
        }
        GLFWVidMode vidMode = GLFW.glfwGetVideoMode(monitor);
        return new DisplayMode(vidMode.width(), vidMode.height(), vidMode.refreshRate(), vidMode.redBits() + vidMode.greenBits() + vidMode.blueBits());
    }

    public DisplayMode[] getLWJGLModeList() {
        long monitor = GLFW.glfwGetPrimaryMonitor();
        GLFWVidMode.Buffer modes = GLFW.glfwGetVideoModes(monitor);

        LinkedList<DisplayMode> l = new LinkedList<>();
        l.add(oldDisplayMode);

        for (int i = 0; i < modes.limit(); i++) {
            GLFWVidMode m = modes.get(i);

            int bitsPerPixel = m.redBits() + m.greenBits() + m.blueBits();
            if (bitsPerPixel != oldDisplayMode.getBitDepth())
                continue;
            if (m.refreshRate() > Math.max(60, oldDisplayMode.getRefreshRate()))
                continue;
            if (m.height() < 240 || m.width() < 320)
                continue;
            if (m.height() > oldDisplayMode.getHeight() || m.width() > oldDisplayMode.getWidth())
                continue;

            int j = 0;
            DisplayMode ml = null;
            for (j = 0; j < l.size(); j++) {
                ml = l.get(j);
                if (ml.getWidth() > m.width())
                    break;
                if (ml.getWidth() == m.width()
                        && ml.getHeight() >= m.height())
                    break;
            }

            DisplayMode lwjglDisplayMode = new DisplayMode(m.width(), m.height(), bitsPerPixel, m.refreshRate());

            if (j == l.size()) {
                l.addLast(lwjglDisplayMode);
            } else if (ml.getWidth() > m.width()
                    || ml.getHeight() > m.height()) {
                l.add(j, lwjglDisplayMode);
            } else if (m.refreshRate() > ml.getRefreshRate()) {
                l.remove(j);
                l.add(j, lwjglDisplayMode);
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
    public int setMode(Dimension dim, int mode, boolean fullscreen) {
        Dimension newDim = new Dimension();

        Com.Printf(Defines.PRINT_ALL, "Initializing OpenGL display\n");

        Com.Printf(Defines.PRINT_ALL, "...setting mode " + mode + ":");

        // Fullscreen handling
        if (oldDisplayMode == null) {
            oldDisplayMode = getCurrentDisplayModeAwt();
        }

        if (!VID.GetModeInfo(newDim, mode)) {
            Com.Printf(Defines.PRINT_ALL, " invalid mode\n");
            return Base.rserr_invalid_mode;
        }

        Com.Printf(Defines.PRINT_ALL, " " + newDim.width + " " + newDim.height + '\n');

        // Destroy the existing window
        shutdown();

        Base.window = GLFW.glfwCreateWindow(newDim.width, newDim.height, "Jake2 (lwjgl)", fullscreen ? GLFW.glfwGetPrimaryMonitor() : 0, 0);

        if (window == 0) {
            return Base.rserr_unknown;
        }

        // Make the window's context current
        GLFW.glfwMakeContextCurrent(window);
        GL.createCapabilities();

        // Set the window position if not fullscreen
        if (!fullscreen) {
            // GLFW.glfwSetWindowPos(window, window_xpos, window_ypos);
        }

        // Store the window handle
        // Replace "long window;" in your class with "long windowHandle;"
        Base.setVid(newDim.width, newDim.height);

        // Let the sound and input subsystems know about the new window
        VID.NewWindow(newDim.width, newDim.height);
        return Base.rserr_ok;
    }

    public void shutdown() {
        if (window != 0) {
            if (GLFW.glfwGetWindowMonitor(window) != 0) {
                GLFW.glfwSetWindowMonitor(window, 0, window_xpos, window_ypos, 800, 600, 60);
            }

            GLFW.glfwDestroyWindow(window);
            window = 0;
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
        GLFW.glfwSwapBuffers(window);

        // Poll events to process input and window events
        GLFW.glfwPollEvents();
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
