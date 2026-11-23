package jake2.dedicated;

import jake2.qcommon.Com;
import jake2.qcommon.MainCommon;
import jake2.qcommon.network.NetworkSettings;
import jake2.qcommon.exec.Cbuf;
import jake2.qcommon.exec.Cmd;
import jake2.qcommon.exec.Cvar;
import jake2.qcommon.sys.Timer;
import jake2.qcommon.vfs.EngineFilesystemLifecycle;
import jake2.server.ServerHost;
import jake2.server.ServerNetwork;
import jake2.server.TransportServerNetwork;
import jake2.qcommon.network.runtime.NetworkTransport;
import jake2.qcommon.network.transport.LocalSide;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import static jake2.qcommon.Com.LogSubsystem.SERVER;
import static jake2.qcommon.MainCommon.adjustTime;
import static jake2.qcommon.MainCommon.logCollisionTraceCounts;

public class Jake2Dedicated {

    public static void main(String[] args) {


        MainCommon.registerCvars(true);

        // in C the first arg is the filename
        int argc = args.length + 1;
        String[] c_args = new String[argc];
        c_args[0] = "Jake2";
        if (argc > 1) {
            System.arraycopy(args, 0, c_args, 1, argc - 1);
        }
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

        NetworkSettings.registerCvars();
        runHost(args1);
    }

    /** Own the Host through late-command startup and the loop, including exceptional exits. */
    static void runHost(List<String> args) {
        var console = System.console();
        var input = console != null && console.isTerminal() ? new BufferedReader(console.reader()) : null;
        runHost(args, new TransportServerNetwork(NetworkTransport.standalone(LocalSide.SECOND)), input);
    }

    static void runHost(List<String> args, ServerNetwork network) {
        runHost(args, network, null);
    }

    private static void runHost(List<String> args, ServerNetwork network, BufferedReader input) {
        try (ServerHost host = new ServerHost(network)) {
            if (Cbuf.AddLateCommands(args)) {
                Cbuf.AddText("dedicated_start\n");
                Cbuf.Execute();
            }
            Com.Info(SERVER, "====== Quake2 Initialized ======\n\n");
            var terminalCommands = new ArrayBlockingQueue<String>(64);
            Thread reader = input == null ? null : Thread.ofPlatform().daemon().name("dedicated-stdin")
                    .start(() -> readConsoleCommands(input, terminalCommands));
            try {
                runLoop(host, terminalCommands);
            } finally {
                // Release a reader waiting to enqueue. A pending terminal read can live until process exit.
                if (reader != null) reader.interrupt();
            }
        }
    }

    private static void readConsoleCommands(BufferedReader input, BlockingQueue<String> terminalCommands) {
        try {
            String line;
            while ((line = input.readLine()) != null) {
                if (!line.isBlank()) terminalCommands.put(line);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            Com.Warn(SERVER, "Could not read dedicated console input: " + e.getMessage());
        }
    }

    private static void runLoop(ServerHost host, BlockingQueue<String> terminalCommands) {
        int oldtime = Timer.Milliseconds();
        while (!Thread.currentThread().isInterrupted()) {
            // find time spending rendering last frame
            int newtime = Timer.Milliseconds();
            int time = newtime - oldtime;

            if (time > 0) {
                int adjustedTime = adjustTime(time);

                logCollisionTraceCounts();

                String terminalCommand = terminalCommands.poll();
                if (terminalCommand != null) Cbuf.AddText(terminalCommand);
                Cbuf.Execute();

                // A command can request orderly termination by interrupting the loop thread.
                if (!Thread.currentThread().isInterrupted()) host.update(adjustedTime);
            }
            oldtime = newtime;
        }
    }
}
