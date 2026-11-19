package jake2.qcommon;

import jake2.qcommon.exec.Cvar;
import jake2.qcommon.exec.CvarTypes;
import jake2.qcommon.exec.cvar_t;
import jake2.qcommon.util.Vargs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class ComMessagingTest {
    private record Message(Com.ConsoleLevel level, String text) {}

    private final List<Message> messages = new ArrayList<>();
    private final Com.ConsoleSink sink = (level, text) -> messages.add(new Message(level, text));
    private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    private PrintStream originalStdout;
    private PrintStream capturedStdout;
    private cvar_t<Boolean> originalDeveloper;

    @BeforeEach
    void setUp() {
        originalDeveloper = Globals.developer;
        Globals.developer = new Cvar().register("developer", CvarTypes.BOOLEAN, true);
        originalStdout = System.out;
        capturedStdout = new PrintStream(stdout, true, StandardCharsets.UTF_8);
        System.setOut(capturedStdout);
        Com.SetConsoleSink(sink);
    }

    @AfterEach
    void tearDown() {
        Com.ClearConsoleSink(sink);
        Globals.developer = originalDeveloper;
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
    void disabledOrUninitializedDeveloperSuppressesDebugOutput() {
        Globals.developer.forceSet(false);
        assertDebugDisabled();
        Globals.developer = null;
        assertDebugDisabled();
        assertTrue(messages.isEmpty());
        assertEquals("", stdout.toString(StandardCharsets.UTF_8));
    }

    private void assertDebugDisabled() {
        Com.Debug("hidden");
        // Invalid legacy format proves formatting is skipped too.
        Com.DPrintf("%invalid", new Vargs().add(1));
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
    void legacyPrintFragmentsRemainRawAndDebugRetainsItsLevel() {
        Com.Printf("column ");
        Com.Printf("%i", new Vargs().add(7));
        Com.DPrintf("debug fragment");
        Com.dprintln("debug line");
        assertEquals("column 7debug fragmentdebug line\n", stdout.toString(StandardCharsets.UTF_8));
        assertEquals(Com.ConsoleLevel.INFO, messages.get(0).level());
        assertEquals(Com.ConsoleLevel.DEBUG, messages.get(2).level());
    }
}
