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

// Created on 29.12.2003 by RST.
// $Id: TestCvar.java,v 1.1 2004-07-07 19:59:56 hzi Exp $

package jake2.qcommon.exec;

import jake2.qcommon.Com;
import jake2.qcommon.Info;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;

import static jake2.qcommon.Defines.*;
import static jake2.qcommon.exec.CvarSetResult.Status.*;
import static org.junit.jupiter.api.Assertions.*;

class TestCvar {
    private final Cvar cvars = Cvar.getInstance();

    @BeforeEach
    void setUp() {
        cvars.clear();
        Cvar.Init();
    }

    @Test
    void commandRegistrationProbesQuietlyAndRejectsCvarCollisions() {
        String command = "test_quiet_command_probe";
        String collision = "test_empty_cvar_collision";
        var output = new StringBuilder();
        Com.BeginRedirect(output::append);
        try {
            Cmd.AddCommand(command, true, args -> {});
            assertNotNull(Cmd.FindCommand(command));
            cvars.register(collision, CvarTypes.STRING, "");
            Cmd.AddCommand(collision, args -> {});
            assertNull(Cmd.FindCommand(collision));
        } finally {
            Com.EndRedirect();
            Cmd.RemoveCommand(command);
        }
        assertFalse(output.toString().contains("not registered"));
        assertTrue(output.toString().contains(collision + " already defined as a var"));
    }

    @Test
    void earlyInputAndDefaultsRemainSeparateFromLookup() {
        cvars.setResult("configured", "is cool.");
        assertNull(cvars.find("configured"));
        assertEquals("is cool.", cvars.getRawString("configured"));
        var configured = cvars.register("configured", CvarTypes.STRING, "default");
        assertEquals("is cool.", configured.getTypedValue());
        assertEquals("default", configured.getDefaultValue());
        assertSame(configured, cvars.find("configured"));
        assertEquals("default", cvars.register("untouched", CvarTypes.STRING, "default").getTypedValue());
        assertNull(cvars.find("missing"));
    }

    @Test
    void userinfoContainsOnlyValidNonemptyDeclaredValues() {
        var expected = java.util.Map.of("msg", "1", "gender", "male", "rate", "25000",
                "name", "unnamed", "skin", "male/grunt", "fov", "90", "hand", "0");
        expected.forEach((name, value) -> cvars.register(name, CvarTypes.STRING, value, CVAR_USERINFO));
        for (String value : List.of("value;", "value\"", "value\\")) {
            assertThrows(IllegalArgumentException.class, () -> cvars.register("invalid_value", CvarTypes.STRING, value, CVAR_USERINFO));
        }
        for (String name : List.of("wrong;", "wrong\"", "wrong\\")) {
            assertThrows(IllegalArgumentException.class, () -> cvars.register(name, CvarTypes.STRING, "value", CVAR_USERINFO));
        }
        cvars.register("non_userinfo", CvarTypes.STRING, "asdf");
        cvars.register("password", CvarTypes.STRING, "", CVAR_USERINFO);
        String userinfo = cvars.Userinfo();
        assertEquals(expected.size() * 2 + 1, userinfo.split("\\\\").length);
        expected.forEach((key, value) -> assertEquals(value, Info.Info_ValueForKey(userinfo, key)));
        assertFalse(userinfo.contains("password"));
        assertTrue(Info.Info_ValueForKey(userinfo, "password").isEmpty());
    }

    @Test
    void declarationsOwnDescriptionsAndFlags() {
        var sensitivity = cvars.register("sensitivity", CvarTypes.FLOAT, 3f, CVAR_OPTIONS, "Mouse speed");
        assertEquals("Mouse speed", sensitivity.description);
        assertSame(sensitivity, cvars.register("sensitivity", CvarTypes.FLOAT, 3f, CVAR_OPTIONS, "Mouse speed"));
        assertThrows(IllegalArgumentException.class, () -> cvars.register("sensitivity", CvarTypes.FLOAT, 3f, 0, "Mouse speed"));
        assertThrows(IllegalArgumentException.class, () -> cvars.register("sensitivity", CvarTypes.FLOAT, 3f, CVAR_OPTIONS, "Changed"));
        assertEquals(CVAR_OPTIONS, sensitivity.flags);
        assertEquals("Mouse speed", sensitivity.description);
    }

    @Test
    void listByPrefixAndFlagsFiltersAndSorts() {
        cvars.register("s_volume", CvarTypes.FLOAT, 0.7f, CVAR_OPTIONS);
        cvars.register("s_music", CvarTypes.FLOAT, 0.5f, CVAR_OPTIONS);
        cvars.register("s_hidden", CvarTypes.BOOLEAN, false);
        cvars.register("vid_gamma", CvarTypes.FLOAT, 1.2f, CVAR_OPTIONS);
        assertEquals(List.of("s_music", "s_volume"), cvars.listByPrefixAndFlags("s_", CVAR_OPTIONS).stream().map(var -> var.name).toList());
    }

    @Test
    void aliasesResolveEarlyInputsAndParticipateInCompletion() {
        cvars.AddAlias("sensitivity", "in_sensitivity");
        cvars.setResult("sensitivity", "4");
        var sensitivity = cvars.register("in_sensitivity", CvarTypes.INTEGER, 3, CVAR_OPTIONS, "Mouse speed");
        assertEquals("in_sensitivity", sensitivity.name);
        assertEquals("4", cvars.getRawString("in_sensitivity"));
        assertEquals("4", cvars.getRawString("sensitivity"));
        assertEquals(CVAR_OPTIONS, sensitivity.flags);
        assertEquals("Mouse speed", sensitivity.description);
        assertEquals(List.of("sensitivity"), cvars.CompleteVariable("s"));
    }

    @Test
    void videoLatchIsArchivedBeforeApplication() throws Exception {
        var width = cvars.register("vid_width", CvarTypes.INTEGER, 1024, CVAR_ARCHIVE | CVAR_LATCH);
        assertEquals(QUEUED, width.set(1280).getStatus());
        assertEquals(1024, width.getTypedValue());
        assertEquals("1280", width.getRawPendingString());
        var file = Files.createTempFile("cvar-archive", ".cfg");
        try {
            cvars.writeArchiveVariables(file.toString());
            String configText = Files.readString(file);
            assertTrue(configText.contains("set vid_width \"1280\""));
            assertFalse(configText.contains("set vid_width \"1024\""));
        } finally {
            Files.deleteIfExists(file);
        }
        cvars.updateLatchedVars();
        assertEquals(1280, width.getTypedValue());
        assertNull(width.getPendingValue());
    }

    @Test
    void consoleFlagSuggestionsCannotEscalateFlagsOrBypassProtectionAndLatching() {
        var plain = cvars.register("plain", CvarTypes.STRING, "old");
        var locked = cvars.register("locked", CvarTypes.STRING, "old", CVAR_NOSET | CVAR_USERINFO);
        var video = cvars.register("vid_setting", CvarTypes.STRING, "old", CVAR_LATCH | CVAR_SERVERINFO);
        cvars.AddAlias("old_plain", "plain");
        Cbuf.AddAndExecuteScript("set old_plain changed u\nset locked changed u\nset vid_setting changed s\n");
        assertEquals("old", plain.getTypedValue());
        assertEquals(0, plain.flags);
        assertEquals("old", locked.getTypedValue());
        assertEquals(CVAR_NOSET | CVAR_USERINFO, locked.flags);
        assertEquals("old", video.getTypedValue());
        assertEquals("changed", video.getPendingValue());
        assertEquals(CVAR_LATCH | CVAR_SERVERINFO, video.flags);
    }

    @Test
    void earlyConsoleFlagSuggestionsAreValidatedAndSurvivePlainWritesAndTransfer() {
        Cbuf.AddAndExecuteScript("set matching 2 u\nset matching 3\nset conflict 2 s\nset conflict 3\nset scoped 4 s\n");
        assertEquals(3, cvars.register("matching", CvarTypes.INTEGER, 1, CVAR_USERINFO | CVAR_ARCHIVE).getTypedValue());
        var conflict = cvars.register("conflict", CvarTypes.INTEGER, 1, CVAR_ARCHIVE);
        assertEquals(1, conflict.getTypedValue());
        assertEquals(CVAR_ARCHIVE, conflict.flags);
        var local = new Cvar();
        cvars.transferEarlyValue("scoped", local);
        assertEquals(4, local.register("scoped", CvarTypes.INTEGER, 0, CVAR_SERVERINFO).getTypedValue());
        var second = new Cvar();
        cvars.transferEarlyValue("scoped", second);
        assertEquals(0, second.register("scoped", CvarTypes.INTEGER, 0, CVAR_SERVERINFO).getTypedValue());
    }
}
