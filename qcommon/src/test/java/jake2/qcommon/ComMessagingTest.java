package jake2.qcommon;

import jake2.qcommon.exec.Cmd;
import jake2.qcommon.exec.Cvar;
import jake2.qcommon.exec.CvarTypes;
import jake2.qcommon.exec.cvar_t;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import static jake2.qcommon.Com.LogLevelOverride.GLOBAL;
import static jake2.qcommon.Com.LogLevelOverride.TRACE;
import static org.junit.jupiter.api.Assertions.*;

class ComMessagingTest {
    private record Message(Com.ConsoleLevel level, String text) {}

    private final List<Message> messages = new ArrayList<>();
    private final Com.ConsoleSink sink = (level, text) -> messages.add(new Message(level, text));
    private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    private PrintStream originalStdout;
    private PrintStream capturedStdout;
    private cvar_t<Com.ConsoleLevel> originalLogLevel;

    @BeforeEach
    void setUp() {
        originalLogLevel = Globals.logLevel;
        Globals.logLevel = MainCommon.registerLogLevel(new Cvar());
        Globals.logLevel.set(Com.ConsoleLevel.DEBUG);
        originalStdout = System.out;
        capturedStdout = new PrintStream(stdout, true, StandardCharsets.UTF_8);
        System.setOut(capturedStdout);
        Com.SetConsoleSink(sink);
    }

    @AfterEach
    void tearDown() {
        Com.ClearConsoleSink(sink);
        Globals.logLevel = originalLogLevel;
        System.setOut(originalStdout);
        capturedStdout.close();
    }

    @Test
    void defaultCallsEnsureOneBoundaryWithoutTrimmingContent() {
        List<Consumer<String>> printers = List.of(
                Com::Print, Com::Info, Com::Warn, Com::Error,
                Com::Debug);
        for (Consumer<String> print : printers) {
            for (String text : List.of("message", "message\n", "message\r\n", "message\r",
                    "first\nsecond\n\n", "  spaced  ", "")) {
                messages.clear();
                stdout.reset();
                print.accept(text);
                String boundary = text.endsWith("\n") || text.endsWith("\r") ? text : text + "\n";
                String expected = boundary;
                assertEquals(expected, messages.getFirst().text());
                assertEquals(expected, stdout.toString(StandardCharsets.UTF_8));
            }
        }
    }

    @Test
    void explicitFragmentsAreNotTerminatedOrTrimmed() {
        Com.Print("  fragment\nnext ", false);
        Com.Print("continued", false);
        assertEquals("  fragment\nnext continued", stdout.toString(StandardCharsets.UTF_8));
        assertEquals(List.of(new Message(Com.ConsoleLevel.PRINT, "  fragment\nnext "),
                new Message(Com.ConsoleLevel.PRINT, "continued")), messages);
    }

    @Test
    void allLevelsReachTheSink() {
        Com.Print("player output");
        Com.Info("initialized");
        Com.Debug("details");
        Com.Warn("recoverable");
        assertDoesNotThrow(() -> Com.Error("operation failed"));
        assertEquals(List.of(
                new Message(Com.ConsoleLevel.PRINT, "player output\n"),
                new Message(Com.ConsoleLevel.INFO, "initialized\n"),
                new Message(Com.ConsoleLevel.DEBUG, "details\n"),
                new Message(Com.ConsoleLevel.WARN, "recoverable\n"),
                new Message(Com.ConsoleLevel.ERROR, "operation failed\n")), messages);
    }

    @Test
    void thresholdFiltersDiagnosticsBeforeRoutingAndPrintAlwaysPasses() {
        for (var threshold : List.of(Com.ConsoleLevel.ERROR, Com.ConsoleLevel.WARN, Com.ConsoleLevel.INFO,
                Com.ConsoleLevel.DEBUG, Com.ConsoleLevel.TRACE)) {
            Globals.logLevel.set(threshold);
            var captured = new StringBuilder();
            Com.RedirectOutput(captured::append, () -> {
                Com.Error("error");
                Com.Warn("warn");
                Com.Info("info");
                Com.Debug("debug");
                Com.Trace(() -> {
                    assertEquals(Com.ConsoleLevel.TRACE, threshold);
                    return "trace";
                });
                Com.Print("output");
            });

            String expected = switch (threshold) {
                case ERROR -> "error\n";
                case WARN -> "error\nwarn\n";
                case INFO -> "error\nwarn\ninfo\n";
                case DEBUG -> "error\nwarn\ninfo\ndebug\n";
                case TRACE -> "error\nwarn\ninfo\ndebug\ntrace\n";
                case PRINT -> throw new AssertionError("PRINT is not a threshold");
            };
            assertEquals(expected + "output\n", captured.toString(), threshold.name());
        }
        assertTrue(messages.isEmpty());
        assertEquals("", stdout.toString(StandardCharsets.UTF_8));
    }

    @Test
    void directCvarWritesReportRejectionsOnceAndPreserveTheValue() {
        var cvars = new Cvar();
        var variable = cvars.register("volume", new CvarTypes.IntegerType(0, 10), 5);

        var text = variable.setText("invalid");
        var nativeValue = variable.set(11);

        assertFalse(text.getAccepted());
        assertFalse(nativeValue.getAccepted());
        assertEquals(5, variable.getTypedValue());
        assertEquals(List.of(
                new Message(Com.ConsoleLevel.WARN, text.getDiagnostic() + "\n"),
                new Message(Com.ConsoleLevel.WARN, nativeValue.getDiagnostic() + "\n")), messages);
    }

    @Test
    void consoleSetChangesLogLevelAndRejectsPrintAsAThreshold() {
        var level = MainCommon.registerLogLevel(Cvar.getInstance());
        var previousValue = level.getTypedValue();
        Globals.logLevel = level;
        try {
            Cvar.Init();
            level.set(Com.ConsoleLevel.WARN);
            messages.clear();
            Cmd.ExecuteString("set loglevel PRINT");
            assertEquals(Com.ConsoleLevel.WARN, level.getTypedValue());
            assertEquals(1, messages.size());
            assertEquals(Com.ConsoleLevel.WARN, messages.getFirst().level());
            assertTrue(messages.getFirst().text().contains("Invalid value for loglevel"));

            Cmd.ExecuteString("set loglevel TRACE");
            assertEquals(Com.ConsoleLevel.TRACE, level.getTypedValue());
            Com.Trace(() -> "trace enabled");
            assertEquals(new Message(Com.ConsoleLevel.TRACE, "trace enabled\n"), messages.getLast());
        } finally {
            level.set(previousValue);
        }
    }

    @Test
    void consoleSubsystemOverridesFilterIndependentlyUntilCleared() {
        var previousOverrides = Globals.logLevelOverrides;
        var overrides = MainCommon.registerLogLevelOverrides(Cvar.getInstance());
        var network = overrides.get(Com.LogSubsystem.NETWORK);
        var previousValue = network.getTypedValue();
        Globals.logLevelOverrides = overrides;
        var subsystem = Com.LogSubsystem.NETWORK;
        try {
            Cvar.Init();
            Globals.logLevel.set(Com.ConsoleLevel.INFO);
            messages.clear();
            Cmd.ExecuteString("set loglevel_network TRACE");

            assertFalse(Com.isTraceEnabled());
            assertTrue(Com.isTraceEnabled(subsystem));
            Com.Trace(() -> fail("Global TRACE is disabled"));
            Com.Trace(subsystem, () -> "packet");
            assertEquals(List.of(new Message(Com.ConsoleLevel.TRACE, "packet\n")), messages);

            Cmd.ExecuteString("set loglevel_network PRINT");
            assertEquals(TRACE, network.getTypedValue());
            Cmd.ExecuteString("set loglevel_network \"\"");
            assertEquals(TRACE, network.getTypedValue());
            Cmd.ExecuteString("set loglevel_network GLOBAL");
            assertEquals(GLOBAL, network.getTypedValue());
            assertEquals("GLOBAL", network.getRawString());
            assertFalse(Com.isTraceEnabled(subsystem));
            Com.Trace(subsystem, () -> fail("Network now follows global INFO"));

            for (var selected : Com.LogSubsystem.values()) {
                var override = overrides.get(selected);
                var previous = override.getTypedValue();
                try {
                    assertEquals("loglevel_" + selected.name().toLowerCase(Locale.ROOT), override.name);
                    assertEquals(GLOBAL, override.getDefaultValue());
                    Cmd.ExecuteString("set " + override.name + " TRACE");
                    for (var candidate : Com.LogSubsystem.values()) {
                        assertEquals(candidate == selected, Com.isTraceEnabled(candidate), candidate.name());
                    }
                    Cmd.ExecuteString("set " + override.name + " GLOBAL");
                    assertFalse(Com.isTraceEnabled(selected));
                } finally {
                    override.set(previous);
                }
            }

            Globals.logLevel.set(Com.ConsoleLevel.TRACE);
            assertTrue(Com.isTraceEnabled(subsystem));
            Cmd.ExecuteString("set loglevel_network ERROR");
            messages.clear();
            Com.Warn(subsystem, "filtered network warning");
            Com.Warn("global warning");
            Com.Print("player message");
            assertEquals(List.of(new Message(Com.ConsoleLevel.WARN, "global warning\n"),
                    new Message(Com.ConsoleLevel.PRINT, "player message\n")), messages);
        } finally {
            network.set(previousValue);
            Globals.logLevelOverrides = previousOverrides;
        }
    }

    @Test
    void redirectCapturesNewMessagesWithoutLocalEcho() {
        StringBuilder redirected = new StringBuilder();
        Com.RedirectOutput(redirected::append, () -> {
            Com.Print("first", false);
            Com.Print(" second");
            Com.Debug("details");
            Com.Error("operation failed");
        });
        assertEquals("first second\ndetails\noperation failed\n", redirected.toString());
        assertTrue(messages.isEmpty());
        assertEquals("", stdout.toString(StandardCharsets.UTF_8));
    }

    @Test
    void redirectPreservesCommandFailureAndReleasesCaptureWhenFlushAlsoFails() {
        var commandFailure = new IllegalStateException("command failed");
        var flushFailure = new IllegalStateException("flush failed");
        var thrown = assertThrows(IllegalStateException.class, () ->
                Com.RedirectOutput(chunk -> { throw flushFailure; }, () -> {
                    Com.Print("partial output");
                    throw commandFailure;
                }));

        assertSame(commandFailure, thrown);
        assertArrayEquals(new Throwable[]{flushFailure}, thrown.getSuppressed());
        Com.Print("local again");
        assertEquals("local again\n", stdout.toString(StandardCharsets.UTF_8));
        var chunks = new ArrayList<String>();
        Com.RedirectOutput(chunks::add, () -> {});
        assertEquals(List.of(""), chunks);
    }

    @Test
    void redirectSplitsLongFragmentsWithoutCapturingCallbackLogs() {
        int limit = Defines.SV_OUTPUTBUF_LENGTH - 1;
        String first = "é".repeat(limit - 1);
        String second = "x".repeat(limit * 2);
        var chunks = new ArrayList<String>();
        Com.RedirectOutput(chunk -> {
            chunks.add(chunk);
            Com.Warn("local transport diagnostic");
        }, () -> {
            assertThrows(IllegalStateException.class, () -> Com.RedirectOutput(chunks::add, () -> {}));
            Com.Print(first, false);
            Com.Print(second, false);
        });

        assertEquals(first + second, String.join("", chunks));
        assertTrue(chunks.size() > 1);
        for (String chunk : chunks) {
            assertFalse(chunk.isEmpty());
            int packetSize = 4 + "print\n".length() + chunk.getBytes(StandardCharsets.ISO_8859_1).length;
            assertTrue(packetSize <= Defines.MAX_MSGLEN);
        }
        assertEquals("local transport diagnostic\n".repeat(chunks.size()), stdout.toString(StandardCharsets.UTF_8));
        assertEquals(chunks.size(), messages.size());
    }

    @Test
    void redirectLeavesUnrelatedThreadsOnLocalOutput() throws InterruptedException {
        var chunks = new ArrayList<String>();
        Thread background = new Thread(() -> Com.Info("background diagnostic"));
        Com.RedirectOutput(chunks::add, () -> {
            background.start();
            try {
                background.join();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
            Com.Print("command output");
        });

        assertEquals(List.of("command output\n"), chunks);
        assertEquals("background diagnostic\n", stdout.toString(StandardCharsets.UTF_8));
    }

    @Test
    void redirectReleasesCaptureAfterAnIntermediateFlushFails() {
        var chunks = new ArrayList<String>();
        assertThrows(IllegalStateException.class, () -> Com.RedirectOutput(chunk -> {
            chunks.add(chunk);
            throw new IllegalStateException("send failed");
        }, () -> Com.Print("x".repeat(Defines.MAX_MSGLEN))));
        assertEquals(1, chunks.size());

        Com.Print("local again");
        assertEquals("local again\n", stdout.toString(StandardCharsets.UTF_8));
    }

    @Test
    void printTreatsPercentCharactersAsLiteralText() {
        Com.Print("Loading 100% (%i, %s)");
        assertEquals(List.of(new Message(Com.ConsoleLevel.PRINT, "Loading 100% (%i, %s)\n")), messages);
    }
}
