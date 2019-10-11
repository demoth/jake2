package jake2.dedicated;

import jake2.qcommon.*;
import jake2.qcommon.exec.Cbuf;
import jake2.qcommon.exec.Cmd;
import jake2.qcommon.exec.Cvar;
import jake2.qcommon.filesystem.FS;
import jake2.qcommon.network.NET;
import jake2.qcommon.network.Netchan;
import jake2.qcommon.sys.Sys;
import jake2.qcommon.sys.Timer;
import jake2.qcommon.util.Vargs;
import jake2.server.SV_MAIN;

import java.util.Arrays;
import java.util.List;

public class Jake2Dedicated {

    public static void main(String[] args) {


        Globals.dedicated = Cvar.Get("dedicated", "1", Defines.CVAR_NOSET );

        // in C the first arg is the filename
        int argc = args.length + 1;
        String[] c_args = new String[argc];
        c_args[0] = "Jake2";
        if (argc > 1) {
            System.arraycopy(args, 0, c_args, 1, argc - 1);
        }
        try {

            // prepare enough of the subsystems to handle
            // cvar and command buffer management
            List<String> args1 = Arrays.asList(c_args);

            Cmd.Init();
            Cvar.Init();

            // we need to add the early commands twice, because
            // a basedir or cddir needs to be set before execing
            // config files, but we want other parms to override
            // the settings of the config files
            Cbuf.AddEarlyCommands(args1, false);
            Cbuf.Execute();

            FS.InitFilesystem();

            Cbuf.reconfigure(args1, false);

            FS.setCDDir(); // use cddir from config.cfg

            Cbuf.reconfigure(args1, true); // reload default.cfg and config.cfg

            //
            // init commands and vars
            //
            Cmd.AddCommand("error", (List<String> arguments) -> {
                if (arguments.size() >= 2)
                    Com.Error(Defines.ERR_FATAL, arguments.get(1));
                else
                    Com.Error(Defines.ERR_FATAL, "error occurred");
            });

            Globals.host_speeds= Cvar.Get("host_speeds", "0", 0);
            Globals.log_stats= Cvar.Get("log_stats", "0", 0);
            Globals.developer= Cvar.Get("developer", "0", Defines.CVAR_ARCHIVE);
            Globals.timescale= Cvar.Get("timescale", "0", 0);
            Globals.fixedtime= Cvar.Get("fixedtime", "0", 0);
            Globals.logfile_active= Cvar.Get("logfile", "0", 0);
            Globals.showtrace= Cvar.Get("showtrace", "0", 0);
            Globals.dedicated= Cvar.Get("dedicated", "0", Defines.CVAR_NOSET);
            Cvar.Get("version", "1.0.0", Defines.CVAR_SERVERINFO | Defines.CVAR_NOSET);

            NET.Init();	//ok
            Netchan.Netchan_Init();	//ok

            SV_MAIN.SV_Init();	//ok

            // add + commands from command line
            if (Cbuf.AddLateCommands(args1)) {
                // if the user didn't give any commands, run default action
                  if (Globals.dedicated.value == 0)
                      Cbuf.AddText("d1\n");
                  else
                      Cbuf.AddText("dedicated_start\n");

                Cbuf.Execute();
            }

            Com.Printf("====== Quake2 Initialized ======\n\n");

            // save config when configuration is completed

        } catch (longjmpException e) {
            Sys.Error("Error during initialization");
        }

        Globals.nostdout = Cvar.Get("nostdout", "0", 0);

        int oldtime = Timer.Milliseconds();
        int newtime;
        int time;
        while (true) {
            // find time spending rendering last frame
            newtime = Timer.Milliseconds();
            time = newtime - oldtime;

            if (time > 0) {
                int msec = time;
                try {

                    if (Globals.fixedtime.value != 0.0f) {
                        msec= (int) Globals.fixedtime.value;
                    } else if (Globals.timescale.value != 0.0f) {
                        msec *= Globals.timescale.value;
                        if (msec < 1)
                            msec= 1;
                    }

                    if (Globals.showtrace.value != 0.0f) {
                        Com.Printf("%4i traces  %4i points\n", new Vargs(2).add(Globals.c_traces).add(Globals.c_pointcontents));

                        Globals.c_traces= 0;
                        Globals.c_brush_traces= 0;
                        Globals.c_pointcontents= 0;
                    }

                    Cbuf.Execute();

                    int time_before= 0;
                    int time_between= 0;
                    int time_after= 0;

                    if (Globals.host_speeds.value != 0.0f)
                        time_before= Timer.Milliseconds();

                    SV_MAIN.SV_Frame(msec);

                    if (Globals.host_speeds.value != 0.0f)
                        time_between= Timer.Milliseconds();


                    if (Globals.host_speeds.value != 0.0f) {
                        time_after= Timer.Milliseconds();

                        int all= time_after - time_before;
                        int sv= time_between - time_before;
                        int cl= time_after - time_between;
                        int gm= Globals.time_after_game - Globals.time_before_game;
                        int rf= Globals.time_after_ref - Globals.time_before_ref;
                        sv -= gm;
                        cl -= rf;

                        Com.Printf("all:%3i sv:%3i gm:%3i cl:%3i rf:%3i\n",
                            new Vargs(5).add(all).add(sv).add(gm).add(cl).add(rf));
                    }

                } catch (longjmpException e) {
                    Com.DPrintf("longjmp exception:" + e);
                }
            }
            oldtime = newtime;
        }
    }
}
