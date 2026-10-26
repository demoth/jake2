package jake2.dedicated;

import jake2.qcommon.Com;
import jake2.qcommon.MainCommon;
import jake2.qcommon.network.Netchan;
import jake2.qcommon.exec.Cbuf;
import jake2.qcommon.exec.Cmd;
import jake2.qcommon.exec.Cvar;
import jake2.qcommon.longjmpException;
import jake2.qcommon.sys.Sys;
import jake2.qcommon.sys.Timer;
import jake2.qcommon.vfs.EngineFilesystemLifecycle;
import jake2.server.ServerHost;

import java.util.Arrays;
import java.util.List;

import static jake2.qcommon.MainCommon.adjustTime;
import static jake2.qcommon.MainCommon.debugLogTraces;

public class Jake2Dedicated {

    @SuppressWarnings("deprecation")
    public static void main(String[] args) {


        MainCommon.registerCvars(true);

        // in C the first arg is the filename
        int argc = args.length + 1;
        String[] c_args = new String[argc];
        c_args[0] = "Jake2";
        if (argc > 1) {
            System.arraycopy(args, 0, c_args, 1, argc - 1);
        }
        ServerHost serverMain = null;
        try {

            // prepare enough of the subsystems to handle
            // cvar and command buffer management
            List<String> args1 = Arrays.asList(c_args);

            Cmd.Init();
            Cvar.Init();

            // we need to add the early commands twice, because
            // basedir needs to be set before execing config files,
            // but we want other parms to override config settings
            Cbuf.AddEarlySetCommands(args1, false);
            Cbuf.Execute();

            EngineFilesystemLifecycle.init();

            Cbuf.reconfigure(args1, false);

            Cbuf.reconfigure(args1, true); // reload default.cfg and config.cfg

            //
            // init commands and vars

            Netchan.Netchan_Init();
            serverMain = new ServerHost();	//ok

            // add + commands from command line
            if (Cbuf.AddLateCommands(args1)) {
                Cbuf.AddText("dedicated_start\n");
                Cbuf.Execute();
            }

            Com.Printf("====== Quake2 Initialized ======\n\n");

            // save config when configuration is completed

        } catch (longjmpException e) {
            Sys.Error("Error during initialization");
        }

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

                } catch (longjmpException e) {
                    Com.DPrintf("longjmp exception:" + e);
                }
            }
            oldtime = newtime;
        }
    }
}
