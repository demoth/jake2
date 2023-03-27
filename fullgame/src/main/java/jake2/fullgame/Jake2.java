/*
 * Jake2.java
 * Copyright (C)  2003
 * 
 * $Id: Jake2.java,v 1.9 2005-12-03 19:43:15 salomo Exp $
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
package jake2.fullgame;

import jake2.client.CL;
import jake2.client.Key;
import jake2.client.SCR;
import jake2.qcommon.Com;
import jake2.qcommon.Defines;
import jake2.qcommon.Globals;
import jake2.qcommon.exec.Cbuf;
import jake2.qcommon.exec.Cmd;
import jake2.qcommon.exec.Cvar;
import jake2.qcommon.filesystem.FS;
import jake2.qcommon.longjmpException;
import jake2.qcommon.network.Netchan;
import jake2.qcommon.sys.Sys;
import jake2.qcommon.sys.Timer;
import jake2.server.JakeServer;
import jake2.server.SV_MAIN;

import java.util.Arrays;
import java.util.List;

import static jake2.qcommon.MainCommon.adjustTime;
import static jake2.qcommon.MainCommon.debugLogTraces;

/**
 * Jake2 is the main class of Quake2 for Java.
 */
public final class Jake2 {

    private static final String BUILDSTRING = "Java " + System.getProperty("java.version");

    private static final String CPUSTRING = System.getProperty("os.arch");

    /**
     * main is used to start the game. Quake2 for Java supports the following
     * command line arguments:
     * 
     * @param args
     */
    public static void main(String[] args) {

        final String libPath = "java.library.path";
        System.out.println(libPath + "=" + System.getProperty(libPath));

        final String lwjglPath = "org.lwjgl.librarypath";
        System.out.println(lwjglPath + "=" + System.getProperty(lwjglPath));

        final String currentDir = System.getProperty("user.dir");
        System.out.println("working directory=" + currentDir);

    	boolean dedicated = false;

    	// check if we are in dedicated mode to hide the java dialog.
    	for (int n = 0; n <  args.length; n++)
    	{
    		if (args[n].equals("+set"))
    		{
    			if (n++ >= args.length)
    				break;
    			
    			if (!args[n].equals("dedicated"))
    				continue;

    			if (n++ >= args.length)
    				break;

    			if (args[n].equals("1") || args[n].equals("\"1\""))
    			{
    				Com.Printf("Starting in dedicated mode.\n");
    				dedicated = true;
    			}
    		}    		
    	}
    	
    	// TODO: check if dedicated is set in config file
    	
		Globals.dedicated= Cvar.getInstance().Get("dedicated", "0", Defines.CVAR_NOSET );
    
    	if (dedicated)
    		Globals.dedicated.value = 1.0f;
    	    	
    	
        // in C the first arg is the filename
        int argc = args.length + 1;
        String[] c_args = new String[argc];
        c_args[0] = "Jake2";
        if (argc > 1) {
            System.arraycopy(args, 0, c_args, 1, argc - 1);
        }
        JakeServer serverMain = Init(c_args);

        int oldtime = Timer.Milliseconds();
        while (true) {
            // find time spending rendering last frame
            int newtime = Timer.Milliseconds();
            int time = newtime - oldtime;

            if (time > 0) {
                try {

                    int adjustedTime = adjustTime(time);

                    debugLogTraces();

                    Cbuf.Execute();

                    serverMain.update(adjustedTime);

                    CL.Frame(adjustedTime);


                } catch (longjmpException e) {
                    Com.DPrintf("longjmp exception:" + e);
                }
            }
            oldtime = newtime;
        }
    }

    /**
     * This function initializes the different subsystems of
     * the game engine. The setjmp/longjmp mechanism of the original
     * was replaced with exceptions.
     * @param argsMain the original unmodified command line arguments
     * @return
     */
    public static JakeServer Init(String[] argsMain) {
        JakeServer result = null;
        try {

            // prepare enough of the subsystems to handle
            // cvar and command buffer management
            List<String> args = Arrays.asList(argsMain);

            Cmd.Init();
            Cvar.getInstance().Init();

            Key.Init();

            // we need to add the early commands twice, because
            // a basedir or cddir needs to be set before execing
            // config files, but we want other parms to override
            // the settings of the config files
            Cbuf.AddEarlySetCommands(args, false);
            Cbuf.Execute();


            FS.InitFilesystem();

            Cbuf.reconfigure(args, false);

            FS.setCDDir(); // use cddir from config.cfg

            Cbuf.reconfigure(args, true); // reload default.cfg and config.cfg

            Globals.host_speeds= Cvar.getInstance().Get("host_speeds", "0", 0);
            Globals.developer= Cvar.getInstance().Get("developer", "0", Defines.CVAR_ARCHIVE);
            Globals.timescale= Cvar.getInstance().Get("timescale", "0", 0);
            Globals.fixedtime= Cvar.getInstance().Get("fixedtime", "0", 0);
            Globals.showtrace= Cvar.getInstance().Get("showtrace", "0", 0);
            Globals.dedicated= Cvar.getInstance().Get("dedicated", "0", Defines.CVAR_NOSET);
            String version = Globals.VERSION + " " + CPUSTRING + " " + BUILDSTRING;
            Cvar.getInstance().Get("version", version, Defines.CVAR_SERVERINFO | Defines.CVAR_NOSET);

            Netchan.Netchan_Init();	//ok

            result = new SV_MAIN(); //SV_MAIN.SV_Init();	//ok

            CL.Init();

            // add + commands from command line
            if (Cbuf.AddLateCommands(args)) {
                // if the user didn't give any commands, run default action
                  if (Globals.dedicated.value == 0)
                        Cbuf.AddText("d1\n");
                  else
                      Cbuf.AddText("dedicated_start\n");

                Cbuf.Execute();
            } else {
                // the user asked for something explicit
                // so drop the loading plaque
                SCR.EndLoadingPlaque();
            }

            Com.Printf("====== Quake2 Initialized ======\n\n");

            // save config when configuration is completed
            CL.WriteConfiguration();

        } catch (longjmpException e) {
            Sys.Error("Error during initialization");
        }
        return result;
    }
}
