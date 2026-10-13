package jake2.qcommon.exec;

import jake2.qcommon.Com;
import jake2.qcommon.Globals;
import jake2.qcommon.ServerStates;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.util.List;

import static jake2.qcommon.Defines.*;
import static jake2.qcommon.exec.CvarSetResult.Status.*;
import static org.junit.jupiter.api.Assertions.*;

/** Covers typed registry behavior through legacy writes, early initialization, latches and config reloads. */
class TypedCvarTest {
    private Cvar registry;
    private boolean previousUserinfoModified;

    @BeforeEach
    void setup() {
        registry = new Cvar();
        // Registries share this notification flag; isolate tests that assert or trigger it.
        previousUserinfoModified = Globals.userinfo_modified;
        Globals.userinfo_modified = false;
    }

    @AfterEach
    void restoreGlobals() {
        Globals.userinfo_modified = previousUserinfoModified;
    }

    private <T> cvar_t<T> register(String name, CvarType<T> type, T value, int flags) {
        return registry.register(name, type, value, flags, "Test setting");
    }

    @Test
    void javaLookupAcceptsPrimitiveAndWrapperTokensWithoutChangingLegacyAccess() {
        cvar_t<Integer> registered = registry.register("number", CvarTypes.INTEGER, 7);
        cvar_t<Integer> found = registry.find("number", int.class);
        assertSame(registered, found);
        assertSame(found, registry.find("number", Integer.class));
        int nativeValue = found.getTypedValue();
        assertEquals(7, nativeValue);
        assertEquals(7f, registry.FindVar("number").value);
        assertSame(registry.register("toggle", CvarTypes.BOOLEAN, false), registry.find("toggle", boolean.class));
        assertSame(registry.register("scale", CvarTypes.FLOAT, 1f), registry.find("scale", float.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2", "-1", "1.0", "yes", "NaN", "", "truthy"})
    void booleansRejectNumericTruthinessAndUnknownWords(String text) {
        var variable = register("toggle", CvarTypes.BOOLEAN, false, 0);
        assertEquals(REJECTED, registry.setResult("toggle", text).getStatus());
        assertFalse(variable.asBoolean());
    }

    @Test
    void booleanCanonicalizationAndNotifications() {
        var variable = register("toggle", CvarTypes.BOOLEAN, false, CVAR_USERINFO);
        registry.FindVar("toggle").modified = false;
        Globals.userinfo_modified = false;
        assertEquals(APPLIED, registry.setResult("toggle", " TrUe ").getStatus());
        assertTrue(variable.asBoolean());
        assertEquals("1", registry.VariableString("toggle"));
        assertTrue(registry.FindVar("toggle").modified);
        assertTrue(Globals.userinfo_modified);
        registry.FindVar("toggle").modified = false;
        Globals.userinfo_modified = false;
        assertEquals(UNCHANGED, registry.setResult(variable.name, "true").getStatus());
        assertFalse(registry.FindVar("toggle").modified);
        assertFalse(Globals.userinfo_modified);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.0", "1.9", "1e2", "true", "2147483648", "-2147483649", "0x10", ""})
    void integerGrammarIsStrict(String text) {
        register("number", CvarTypes.INTEGER, 0, 0);
        assertEquals(REJECTED, registry.setResult("number", text).getStatus());
    }

    @Test
    void integersPreservePrecisionAndCanonicalWireEncoding() {
        var variable = register("number", CvarTypes.INTEGER, 0, CVAR_USERINFO);
        assertEquals(APPLIED, registry.setResult("number", "\u00a0 +016777217 \u00a0").getStatus());
        assertEquals(16777217, variable.asInt());
        assertEquals("16777217", registry.VariableString("number"));
        assertTrue(registry.Userinfo().contains("\\number\\16777217"));
        registry.SetValue("number", 2f);
        assertEquals(16777217, variable.asInt()); // No implicit float-to-integer conversion, even for 2.0.
    }

    @Test
    void rangesRejectInsteadOfClamping() {
        var integer = registry.register("budget", new CvarTypes.IntegerType(1, 128), 32, 0, "Test setting");
        assertEquals(REJECTED, registry.setResult(integer.name, "129").getStatus());
        assertEquals(REJECTED, registry.setResult("budget", "0").getStatus());
        assertEquals(32, integer.asInt());
        assertEquals(APPLIED, registry.setResult(integer.name, "1").getStatus());
        assertEquals(APPLIED, registry.setResult(integer.name, "128").getStatus());
        assertThrows(IllegalArgumentException.class, () -> register("bad", new CvarTypes.IntegerType(1, 2), 0, 0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"NaN", "Infinity", "-Infinity", "1e99", "true", "", "1.2.3"})
    void floatsRejectNonfiniteAndInvalidInput(String text) {
        var variable = register("gain", CvarTypes.FLOAT, 1f, 0);
        assertEquals(REJECTED, registry.setResult("gain", text).getStatus());
        assertEquals(1f, variable.asFloat());
    }

    @Test
    void floatsHaveFiniteRangesAndOneZero() {
        var variable = register("gain", new CvarTypes.FloatType(0f, 2f), 1f, 0);
        assertEquals(APPLIED, registry.setResult("gain", " .2e1 ").getStatus());
        assertEquals(2f, variable.asFloat());
        assertEquals(APPLIED, registry.setResult("gain", "0x1.0p0").getStatus());
        assertEquals(1f, variable.asFloat());
        assertEquals("1.0", variable.getRawString());
        assertEquals(APPLIED, registry.setResult("gain", "2f").getStatus());
        assertEquals(2f, variable.asFloat());
        assertEquals("2.0", variable.getRawString());
        assertEquals(REJECTED, registry.setResult(variable.name, "NaN").getStatus());
        assertEquals(REJECTED, registry.setResult(variable.name, "-1.0").getStatus());
        assertEquals(APPLIED, registry.setResult("gain", "-0.0").getStatus());
        assertEquals("0.0", registry.VariableString("gain"));
        assertEquals(UNCHANGED, registry.setResult(variable.name, "0.0").getStatus());
    }

    @Test
    void regexUsesFullMatchAndStringWhitespaceIsPreserved() {
        var variable = register("name", new CvarTypes.StringType("[a-z ]+"), "bob", 0);
        assertEquals(APPLIED, registry.setResult(variable.name, " bob ").getStatus());
        assertEquals(" bob ", variable.asString());
        assertEquals(REJECTED, registry.setResult(variable.name, "bob123").getStatus());
        assertEquals(APPLIED, registry.setResult(variable.name, "longerthaneight").getStatus());
        assertEquals(REJECTED, registry.setResult(variable.name, null).getStatus());
    }

    enum Mode { Pixels, Logical }

    @Test
    void enumTokensAreNamesAndNumericChoicesRemainTypedIntegers() {
        var mode = register("mode", new CvarTypes.EnumType<>(Mode.class), Mode.Pixels, 0);
        assertEquals(APPLIED, registry.setResult(mode.name, "Logical").getStatus());
        assertEquals("Logical", registry.VariableString("mode"));
        assertEquals(REJECTED, registry.setResult("mode", "1").getStatus());
        assertEquals(REJECTED, registry.setResult("mode", "logical").getStatus());
        var choices = new CvarTypes.ChoiceType<>(List.of(0, 4));
        var samples = register("samples", choices, 0, 0);
        assertEquals(APPLIED, registry.setResult(samples.name, "4").getStatus());
        assertEquals(4, samples.asInt());
        assertEquals(REJECTED, registry.setResult(samples.name, "004").getStatus());
        assertEquals(REJECTED, registry.setResult(samples.name, " 4 ").getStatus());
        assertEquals(REJECTED, registry.setResult(samples.name, "2").getStatus());
    }

    @Test
    void earlyInputIsValidatedWhenTheCompleteEntryIsRegistered() {
        assertNull(registry.Set("number", "not an integer"));
        assertTrue(registry.listByPrefixAndFlags("number", 0).isEmpty());
        var variable = register("number", CvarTypes.INTEGER, 7, 0);
        assertSame(variable, registry.FindVar("number"));
        assertEquals(7, variable.asInt());
        assertNull(variable.getPendingValue());
        assertNull(registry.Set("valid", " +0009 "));
        var valid = register("valid", CvarTypes.INTEGER, 7, CVAR_LATCH);
        assertEquals(9, valid.asInt());
        assertEquals("9", valid.getRawString());
        assertNull(valid.getPendingValue());
    }

    @Test
    void earlyWritesStayOutOfRegisteredViewsAndProduceDiagnostics() {
        var output = new StringBuilder();
        Com.BeginRedirect(output::append);
        try {
            assertNull(registry.FullSet("early", "private-invalid-value", CVAR_USERINFO));
            assertNull(registry.FindVar("early"));
            assertTrue(registry.listByPrefixAndFlags("", 0).isEmpty());
            assertEquals("", registry.Userinfo());
            assertFalse(Globals.userinfo_modified);
            assertEquals("private-invalid-value", registry.VariableString("early"));
            assertNull(registry.FindVar("missing"));
            assertEquals(7, register("early", CvarTypes.INTEGER, 7, CVAR_USERINFO).asInt());
        } finally {
            Com.EndRedirect();
        }
        assertFalse(output.toString().contains("private-invalid-value"));
        assertTrue(output.toString().contains("Cvar 'missing' is not registered"));
        assertTrue(output.toString().contains("storing early input"));
        assertTrue(output.toString().contains("before cvar registration"));
        assertTrue(output.toString().contains("Rejected early input"));
    }

    @Test
    void earlyFullSetFlagsMustAgreeWithTheOwnerAndPlainSetPreservesThem() {
        registry.FullSet("matching", "2", CVAR_USERINFO);
        registry.Set("matching", "3");
        var matching = register("matching", CvarTypes.INTEGER, 1, CVAR_USERINFO | CVAR_ARCHIVE);
        assertEquals(3, matching.asInt());
        assertEquals(CVAR_USERINFO | CVAR_ARCHIVE, matching.flags);

        registry.FullSet("conflict", "2", CVAR_USERINFO);
        registry.Set("conflict", "3");
        var conflict = register("conflict", CvarTypes.INTEGER, 1, CVAR_ARCHIVE);
        assertEquals(1, conflict.asInt());
        assertEquals(CVAR_ARCHIVE, conflict.flags);

        registry.FullSet("replaced", "2", CVAR_USERINFO);
        registry.FullSet("replaced", "4", 0);
        assertEquals(4, register("replaced", CvarTypes.INTEGER, 1, 0).asInt());
    }

    @Test
    void aliasesRouteEarlyInputInLastWriteOrder() {
        registry.Set("old_number", "2");
        registry.Set("number", "3");
        registry.Set("old_number", "4");
        var output = new StringBuilder();
        Com.BeginRedirect(output::append);
        try {
            registry.AddAlias("old_number", "number");
        } finally {
            Com.EndRedirect();
        }
        assertTrue(output.toString().contains("Alias 'old_number' was used before alias registration"));
        var number = register("number", CvarTypes.INTEGER, 1, 0);
        assertEquals(4, number.asInt());
        assertSame(number, registry.FindVar("old_number"));

        registry.Set("old_other", "5");
        registry.Set("other", "6");
        registry.AddAlias("old_other", "other");
        assertEquals(6, register("other", CvarTypes.INTEGER, 1, 0).asInt());
    }

    @Test
    void aLateAliasUsesNormalWritesForAnAlreadyRegisteredTarget() {
        registry.Set("old_video", "2");
        var video = register("vid_number", CvarTypes.INTEGER, 1, CVAR_LATCH);
        registry.AddAlias("old_video", "vid_number");
        assertEquals(1, video.asInt());
        assertEquals(2, video.getPendingValue());
        registry.updateLatchedVars();
        assertEquals(2, video.asInt());
    }

    @Test
    void registrationOwnsTheTypeAndRejectsOtherDeclarations() {
        var variable = register("number", new CvarTypes.IntegerType(0, 10), 1, 0);
        assertThrows(IllegalArgumentException.class, () -> register("number", CvarTypes.BOOLEAN, true, 0));
        assertThrows(IllegalArgumentException.class, () -> register("number", new CvarTypes.IntegerType(0, 11), 1, 0));
        assertEquals(1, variable.asInt());
        var raw = registry.Get("raw", "3", 0);
        assertNull(raw.getType());
        assertThrows(IllegalArgumentException.class, () -> register("raw", CvarTypes.INTEGER, 1, 0));
    }

    @Test
    void registriesAreIndependentAndAliasesUseTheOwningDefinition() {
        var first = register("number", CvarTypes.INTEGER, 7, 0);
        var other = new Cvar();
        other.Get("number", "not typed", 0);
        assertEquals("not typed", other.VariableString("number"));
        assertEquals(7, first.asInt());
        registry.AddAlias("old_number", "number");
        assertSame(registry.FindVar("number"), registry.FindVar("old_number"));
        assertEquals(REJECTED, registry.setResult("old_number", "bad").getStatus());
        assertEquals(APPLIED, registry.setResult("old_number", "8").getStatus());
        assertEquals(8, first.asInt());
        assertEquals("not typed", other.VariableString("number"));
        assertThrows(IllegalArgumentException.class, () -> register("old_number", CvarTypes.INTEGER, 7, 0));
    }

    @Test
    void everyRegistryWritePathValidatesAndCannotDiscardPendingOnFailure() {
        var variable = register("vid_number", new CvarTypes.IntegerType(0, 10), 1, CVAR_LATCH | CVAR_USERINFO);
        var entry = registry.FindVar("vid_number");
        assertEquals(QUEUED, registry.setResult(variable.name, "2").getStatus());
        entry.modified = false;
        Globals.userinfo_modified = false;
        for (var write : List.<Runnable>of(
                () -> registry.Set("vid_number", "bad"),
                () -> registry.ForceSet("vid_number", "bad"),
                () -> registry.FullSet("vid_number", "bad", 0),
                () -> registry.SetValue("vid_number", 11),
                () -> assertEquals(REJECTED, registry.forceSetResult("vid_number", "bad").getStatus()))) {
            write.run();
            // Check each path before another write can mask a partial mutation.
            assertEquals(1, variable.asInt());
            assertEquals(2, variable.getPendingValue());
            assertEquals(CVAR_LATCH | CVAR_USERINFO, entry.flags);
            assertFalse(entry.modified);
            assertFalse(Globals.userinfo_modified);
        }
        registry.updateLatchedVars();
        assertEquals(2, variable.asInt());
        assertNull(variable.getPendingValue());
        assertTrue(entry.modified);
        assertTrue(Globals.userinfo_modified);
    }

    @Test
    void forceAndFullSetPreserveValidationAndReplaceFlags() {
        var variable = register("number", CvarTypes.INTEGER, 1, CVAR_NOSET);
        assertEquals(REJECTED, registry.setResult(variable.name, "2").getStatus());
        assertEquals(APPLIED, registry.forceSetResult("number", "2").getStatus());
        assertEquals(REJECTED, registry.forceSetResult("number", "bad").getStatus());
        registry.FullSet("number", "3", CVAR_ARCHIVE);
        assertEquals(CVAR_ARCHIVE, registry.FindVar("number").flags);
        assertEquals(3, variable.asInt());
        assertSame(CvarTypes.INTEGER, variable.getType());
    }

    @Test
    void pendingChangesUseCanonicalEqualityAndCanBeCancelled() {
        var variable = register("vid_toggle", CvarTypes.BOOLEAN, false, CVAR_LATCH);
        assertEquals(QUEUED, registry.setResult("vid_toggle", "true").getStatus());
        assertEquals(UNCHANGED, registry.setResult("vid_toggle", "1").getStatus());
        assertEquals(APPLIED, registry.setResult(variable.name, "false").getStatus());
        assertNull(variable.getPendingValue());
        assertEquals(UNCHANGED, registry.setResult(variable.name, "false").getStatus());
        assertEquals(QUEUED, registry.setResult(variable.name, "true").getStatus());
        assertEquals(APPLIED, registry.forceSetResult("vid_toggle", "false").getStatus());
        assertFalse(variable.asBoolean());
        assertNull(variable.getPendingValue());
    }

    @Test
    void ordinaryLatchDependsOnServerState() {
        var previousState = Globals.server_state;
        try {
            Globals.server_state = ServerStates.SS_DEAD;
            var variable = register("number", CvarTypes.INTEGER, 1, CVAR_LATCH);
            assertEquals(APPLIED, registry.setResult(variable.name, "2").getStatus());
            assertEquals(2, variable.asInt());
            Globals.server_state = ServerStates.SS_GAME;
            assertEquals(QUEUED, registry.setResult(variable.name, "3").getStatus());
            assertEquals(2, variable.asInt());
            registry.updateLatchedVars();
            assertEquals(3, variable.asInt());
        } finally {
            Globals.server_state = previousState;
        }
    }

    @Test
    void invalidRawPendingIsRevalidatedAtApplication() {
        var variable = register("vid_number", CvarTypes.INTEGER, 1, CVAR_LATCH);
        registry.FindVar("vid_number").latched_string = "invalid";
        registry.updateLatchedVars();
        assertEquals(1, variable.asInt());
        assertEquals("invalid", registry.FindVar("vid_number").latched_string);
    }

    @Test
    void newInfoFlagsValidateActiveAndPendingWithoutPartialMutation() {
        register("name", CvarTypes.STRING, "safe", CVAR_LATCH);
        registry.ForceSet("name", "unsafe;value");
        registry.Get("name", "ignored", CVAR_USERINFO);
        assertEquals(CVAR_LATCH, registry.FindVar("name").flags);
        registry.GetForceFlags("name", "ignored", CVAR_USERINFO);
        assertEquals(CVAR_LATCH, registry.FindVar("name").flags);
        registry.FullSet("name", "unsafe;value", CVAR_USERINFO);
        assertEquals("unsafe;value", registry.FindVar("name").asString());
        assertEquals(CVAR_LATCH, registry.FindVar("name").flags);
        registry.FullSet("name", "safe", CVAR_USERINFO);
        assertEquals("safe", registry.FindVar("name").asString());
        assertEquals(CVAR_USERINFO, registry.FindVar("name").flags);
        assertEquals(REJECTED, registry.forceSetResult("name", "unsafe\\value").getStatus());
        register("other", CvarTypes.STRING, "bad;default", 0);
        // Defaults belong to the declaration's flags; FullSet validates its proposed value/flags.
        registry.FullSet("other", "safe", CVAR_USERINFO);
        assertEquals("safe", registry.FindVar("other").asString());
        assertEquals(CVAR_USERINFO, registry.FindVar("other").flags);
        var pending = register("vid_name", CvarTypes.STRING, "safe", CVAR_LATCH);
        registry.setResult(pending.name, "unsafe;pending");
        registry.Get("vid_name", "ignored", CVAR_USERINFO);
        assertEquals(CVAR_LATCH, registry.FindVar("vid_name").flags);
        assertEquals("unsafe;pending", pending.getPendingValue());
    }

    @Test
    void flagChangesNotifyUserinfo() {
        registry.Set("toggle", "0");
        register("toggle", CvarTypes.BOOLEAN, false, CVAR_USERINFO);
        assertTrue(Globals.userinfo_modified);
        Globals.userinfo_modified = false;
        registry.GetForceFlags("toggle", "ignored", 0);
        assertTrue(Globals.userinfo_modified);
        Globals.userinfo_modified = false;
        registry.Get("toggle", "ignored", CVAR_USERINFO);
        assertTrue(Globals.userinfo_modified);
    }

    @Test
    void archiveRejectsTokensThatTheCommandParserCannotRepresent() {
        var variable = register("text", CvarTypes.STRING, "", CVAR_ARCHIVE);
        assertEquals(REJECTED, registry.setResult(variable.name, "has\"quote").getStatus());
        assertEquals(REJECTED, registry.setResult(variable.name, "two\nlines").getStatus());
        assertEquals(REJECTED, registry.setResult(variable.name, "has\0nul").getStatus());
        int maxLength = MAX_STRING_CHARS - "text".length() - 8;
        assertEquals(APPLIED, registry.setResult(variable.name, "a".repeat(maxLength)).getStatus());
        assertEquals(REJECTED, registry.setResult(variable.name, "a".repeat(maxLength + 1)).getStatus());
        assertEquals(APPLIED, registry.setResult(variable.name, "C:\\quake;test").getStatus());
    }

    @Test
    void archiveAndCommandReloadPreserveEmptyPendingAndExactIntegers() throws Exception {
        var singleton = Cvar.getInstance();
        singleton.clear();
        Cvar.Init();
        var text = singleton.register("vid_text", CvarTypes.STRING, "old", CVAR_LATCH | CVAR_ARCHIVE, "Test setting");
        var number = singleton.register("rate", CvarTypes.INTEGER, 25000, CVAR_ARCHIVE | CVAR_USERINFO, "Test setting");
        var path = Files.createTempFile("typed-cvars", ".cfg");
        try {
            assertEquals(QUEUED, singleton.setResult(text.name, "").getStatus());
            assertEquals("", text.getPendingValue());
            singleton.setResult(number.name, "16777217");
            var unicode = singleton.register("unicode", CvarTypes.STRING, "", CVAR_ARCHIVE, "Test setting");
            singleton.setResult(unicode.name, "Grüße; C:\\quake");
            singleton.Get("raw_name", "", CVAR_ARCHIVE);
            singleton.Set("raw_name", "Jäger 日本語");
            singleton.writeArchiveVariables(path.toString());
            String script = Files.readString(path);
            assertTrue(script.contains("set vid_text \"\""));
            assertTrue(script.contains("set rate \"16777217\""));
            assertTrue(script.contains("Grüße; C:\\quake"));
            assertTrue(script.contains("Jäger 日本語"));
            singleton.clear();
            text = singleton.register("vid_text", CvarTypes.STRING, "old", CVAR_LATCH | CVAR_ARCHIVE, "Test setting");
            number = singleton.register("rate", CvarTypes.INTEGER, 25000, CVAR_ARCHIVE | CVAR_USERINFO, "Test setting");
            unicode = singleton.register("unicode", CvarTypes.STRING, "", CVAR_ARCHIVE, "Test setting");
            singleton.Get("raw_name", "", CVAR_ARCHIVE);
            Cbuf.AddAndExecuteScript(script);
            singleton.updateLatchedVars();
            assertEquals("", text.asString());
            assertNull(text.getPendingValue());
            assertEquals(16777217, number.asInt());
            assertEquals("Grüße; C:\\quake", unicode.asString());
            assertEquals("Jäger 日本語", singleton.VariableString("raw_name"));
            assertNull(singleton.FindVar("raw_name").getType());
        } finally {
            Files.deleteIfExists(path);
            singleton.clear();
        }
    }

    @Test
    void registeredEntriesKeepTheirTypeAndRegistryWritesCannotBypassIt() {
        registry.Set("budget", " 0003 ");
        var type = new CvarTypes.IntegerType(1, 10);
        var variable = register("budget", type, 1, 0);
        assertSame(type, variable.getType());
        assertEquals(3, variable.asInt());
        assertEquals("3", variable.getRawString());
        variable.modified = false;
        assertEquals(REJECTED, registry.setResult(variable.name, "11").getStatus());
        assertEquals(REJECTED, registry.setResult(variable.name, null).getStatus());
        assertEquals(3, variable.asInt());
        assertFalse(variable.modified);
        assertEquals(APPLIED, registry.setResult(variable.name, " +0004 ").getStatus());
        assertEquals(4, variable.asInt());
        assertEquals("4", variable.getRawString());
        registry.AddAlias("old_budget", "budget");
        assertSame(variable, registry.FindVar("old_budget"));
        assertSame(variable, registry.Get("old_budget", "ignored", 0));
    }

    enum OtherMode { Pixels, Logical }

    @Test
    void accessorsCheckDeclaredTypesWithoutBooleanOrNumericCoercion() {
        var toggle = register("toggle", CvarTypes.BOOLEAN, false, 0);
        var number = register("number", CvarTypes.INTEGER, 0, 0);
        var mode = register("mode", new CvarTypes.EnumType<>(Mode.class), Mode.Pixels, 0);
        assertThrows(IllegalStateException.class, toggle::asInt);
        assertThrows(IllegalStateException.class, number::asBoolean);
        assertThrows(IllegalStateException.class, number::asFloat);
        assertThrows(IllegalStateException.class, number::asString);
        assertThrows(IllegalStateException.class, () -> mode.asEnum(OtherMode.class));
        assertFalse(toggle.asBoolean());
        assertEquals(0, number.asInt());
        assertEquals(Mode.Pixels, mode.asEnum(Mode.class));
    }

    @Test
    void arbitraryUntypedCvarsKeepLegacySemantics() {
        var variable = registry.Get("script_var", "anything", 0);
        assertNull(variable.getType());
        assertEquals("anything", variable.getRawString());
        assertEquals(APPLIED, registry.setResult(variable.name, "other arbitrary text").getStatus());
        assertEquals("other arbitrary text", variable.getRawString());
        assertThrows(IllegalStateException.class, variable::asString);
        registry.setResult(variable.name, "anything");
        assertNull(variable.getType());
        assertEquals("anything", registry.VariableString("script_var"));
        registry.Set("script_var", "2.5");
        assertEquals(2.5f, registry.FindVar("script_var").value);
        registry.FullSet("script_var", "has;semicolon", CVAR_USERINFO);
        assertEquals("has;semicolon", registry.VariableString("script_var"));
    }
}
