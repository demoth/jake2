/*
 * Jake2Applet.java
 * Copyright (C)  2008
 * 
 * $Id: Jake2Applet.java,v 1.2 2008-03-02 20:38:04 kbrussel Exp $
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
package jake2;

import jake2.game.Cmd;
import jake2.qcommon.*;
import jake2.sys.Timer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.util.Locale;
import javax.swing.JApplet;

import netscape.javascript.*;

/**
 * Jake2 is the main class of Quake2 for Java.
 */
public class Jake2Applet extends JApplet {

    private JSObject self;
    private volatile boolean shouldShutDown;
    private boolean shutDown;
    private Object shutDownLock = new Object();

    public void init() {
        // Before Jake2 is fully initialized, make the applet black
        // like the rest of the web page
        setBackground(Color.BLACK);
    }

    public void start() {
        setLayout(new BorderLayout());
        new GameThread().start();
    }

    public void stop() {
        synchronized(shutDownLock) {
            shouldShutDown = true;
            while (!shutDown) {
                try {
                    shutDownLock.wait();
                } catch (InterruptedException e) {
                }
            }
        }
        Cmd.ExecuteString("quit");
    }

    class GameThread extends Thread {
        public GameThread() {
            super("Jake2 Game Thread");
        }

        public void run() {
            // TODO: check if dedicated is set in config file
    	
            Globals.dedicated= Cvar.Get("dedicated", "0", Qcommon.CVAR_NOSET);
    
            // Set things up for applet execution
            Qcommon.appletMode = true;
            Qcommon.applet = Jake2Applet.this;
            Qcommon.sizeChangeListener = new SizeChangeListener() {
                    public void sizeChanged(int width, int height) {
                        try {
                            if (self == null) {
                                JSObject win = JSObject.getWindow(Jake2Applet.this);
                                self = (JSObject) win.eval("document.getElementById(\"" +
                                                           getParameter("id") + "\")");
                            }

                            self.setMember("width", new Integer(width));
                            self.setMember("height", new Integer(height));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                };

            // open the q2dialog, if we are not in dedicated mode.
            if (Globals.dedicated.value != 1.0f) {
                Jake2.Q2Dialog = new Q2DataDialog();
                Locale.setDefault(Locale.US);
                Jake2.Q2Dialog.setVisible(true);
            }

            Qcommon.Init(new String[] { "Jake2" });

            Globals.nostdout = Cvar.Get("nostdout", "0", 0);

            try {
                while (!shouldShutDown) {
                    int oldtime = Timer.Milliseconds();
                    int newtime;
                    int time;
                    while (true) {
                        // find time spending rendering last frame
                        newtime = Timer.Milliseconds();
                        time = newtime - oldtime;

                        if (time > 0)
                            Qcommon.Frame(time);
                        oldtime = newtime;
                    }
                }
            } finally {
                synchronized(shutDownLock) {
                    shutDown = true;
                    shutDownLock.notifyAll();
                }
            }
        }
    }
}
