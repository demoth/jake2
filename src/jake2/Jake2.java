/*
 * Jake2.java
 * Copyright (C)  2003
 * 
 * $Id: Jake2.java,v 1.5 2004-09-22 19:22:14 salomo Exp $
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

import jake2.client.SCR;
import jake2.qcommon.*;
import jake2.qcommon.Cvar;
import jake2.qcommon.Qcommon;
import jake2.sys.Sys;

/**
 * Jake2 is the main class of Quake2 for Java.
 */
public final class Jake2 {

    static class MemMonitor implements Runnable {
        public void run() {
            while (true) {
                Com.Printf("Memory:"
                        + (Runtime.getRuntime().totalMemory() - Runtime
                                .getRuntime().freeMemory()) + "\n");
                try {
                    Thread.sleep(1000);
                } catch (Exception e) {
                }
            }
        }
    }

    public static Q2DataDialog Q2Dialog = new Q2DataDialog();

    /**
     * main is used to start the game. Quake2 for Java supports the following
     * command line arguments:
     * 
     * @param args
     */
    public static void main(String[] args) {

        Q2Dialog.setVisible(true);

        // uncomment to have a memory debugger (RST).
        //new Thread(new MemMonitor()).start();

        // in C the first arg is the filename
        int argc = (args == null) ? 1 : args.length + 1;
        String[] c_args = new String[argc];
        c_args[0] = "Jake2";
        if (argc > 1) {
            System.arraycopy(args, 0, c_args, 1, argc - 1);
        }
        Qcommon.Init(c_args);

        Globals.nostdout = Cvar.Get("nostdout", "0", 0);

        int oldtime = Sys.Milliseconds();
        int newtime;
        int time;
        while (true) {
            // find time spending rendering last frame
            newtime = Sys.Milliseconds();
            time = newtime - oldtime;

            // TODO this is a timer hack for Win2000
            // System.currentTimeMillis() resolution bug
            if (time == 0
                    && (Globals.cl_timedemo.value != 0 || SCR.fps.value != 0)) {
                time++;
            }

            if (time > 0)
                Qcommon.Frame(time);
            oldtime = newtime;
        }
    }
}