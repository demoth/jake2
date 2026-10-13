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


import jake2.qcommon.Info;
import jake2.qcommon.Com;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static jake2.qcommon.Defines.CVAR_ARCHIVE;
import static jake2.qcommon.Defines.CVAR_LATCH;
import static jake2.qcommon.Defines.CVAR_OPTIONS;
import static jake2.qcommon.Defines.CVAR_USERINFO;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestCvar {

    @Test
    void commandRegistrationProbesQuietlyAndRejectsEmptyCvarNames() {
        String command = "test_quiet_command_probe";
        String collision = "test_empty_cvar_collision";
        var output = new StringBuilder();
        Com.BeginRedirect(output::append);
        try {
            Cmd.AddCommand(command, true, args -> {});
            Assertions.assertNotNull(Cmd.FindCommand(command));
            Cvar.getInstance().Get(collision, "", 0);
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
	public void testInit() {
		Cvar.Init();
	}

	@BeforeEach
	public void setUp() {
		Cvar.getInstance().clear();
	}

	@Test
	public void testGet() {
		Cvar.getInstance().Set("rene", "is cool.");

		Assertions.assertEquals("is cool.", Cvar.getInstance().Get("rene", "default", 0).string);
	}

	@Test
	public void testGetDefault() {
		Cvar.getInstance().Set("rene1", "is cool.");

		Assertions.assertEquals("default", Cvar.getInstance().Get("hello", "default", 0).string);
	}

	@Test
	public void testGetDefaultNull() {
		Cvar.getInstance().Set("rene2", "is cool.");

		assertNull(Cvar.getInstance().Get("hello2", null, 0));
	}

	@Test
	public void testFind() {
		Cvar.getInstance().Get("rene3", "is cool.", 0);

		Assertions.assertEquals("is cool.", Cvar.getInstance().FindVar("rene3").string);
	}

	@Test
	public void testVariableString() {
		Cvar.getInstance().Set("rene4", "is cool.");
		Assertions.assertEquals("is cool.", Cvar.getInstance().VariableString("rene4"));
	}

	@Test
	public void testFullSetCreateNew() {
		assertNull(Cvar.getInstance().FullSet("rene5", "0.56", 0));
		assertNull(Cvar.getInstance().FindVar("rene5"));
		Cvar.getInstance().Get("rene5", "default", 0);

		cvar_t rene5 = Cvar.getInstance().FindVar("rene5");
		Assertions.assertNotNull(rene5);
		Assertions.assertEquals("0.56", rene5.string);
		assertTrue(rene5.value > 0.5f);
	}

	@Test
	public void testEarlyFullSetCannotGrantFlagsToTheRawOwner() {
		Cvar cvar = Cvar.getInstance();
		cvar.FullSet("matching", "configured", CVAR_ARCHIVE);
		cvar_t matching = cvar.Get("matching", "default", CVAR_ARCHIVE | CVAR_OPTIONS);
		assertEquals("configured", matching.string);
		assertEquals(CVAR_ARCHIVE | CVAR_OPTIONS, matching.flags);

		cvar.FullSet("conflict", "configured", CVAR_USERINFO);
		cvar_t conflict = cvar.Get("conflict", "default", CVAR_ARCHIVE);
		assertEquals("default", conflict.string);
		assertEquals(CVAR_ARCHIVE, conflict.flags);
	}

	@Test
	public void testFullSetOverwrite() {
		Cvar.getInstance().Get("rene6", "0.56", 0);
		Cvar.getInstance().FullSet("rene6", "10.6", 0);

		cvar_t rene6 = Cvar.getInstance().FindVar("rene6");
		Assertions.assertNotNull(rene6);
		assertTrue(rene6.modified);
		Assertions.assertEquals("10.6", rene6.string);
		assertTrue(rene6.value > 0.5f);

	}

	@Test
	public void testUserInfo() {
		Cvar cvar = Cvar.getInstance();
		cvar.Get("name", "unnamed", CVAR_USERINFO );
		cvar.Get("skin", "male/grunt", CVAR_USERINFO);
		cvar.Get("rate", "25000", CVAR_USERINFO);
		cvar.Get("msg", "1", CVAR_USERINFO );
		cvar.Get("hand", "0", CVAR_USERINFO );
		cvar.Get("fov", "90", CVAR_USERINFO );
		cvar.Get("gender", "male", CVAR_USERINFO );

		cvar.Get("wrongcharacter1", "value;", CVAR_USERINFO);
		cvar.Get("wrongcharacter2", "value\"", CVAR_USERINFO);
		cvar.Get("wrongcharacter3", "value\\", CVAR_USERINFO);
		cvar.Get("wrongcharacter4;", "value1", CVAR_USERINFO);
		cvar.Get("wrongcharacter5\"", "value2", CVAR_USERINFO);
		cvar.Get("wrongcharacter6\\", "value3", CVAR_USERINFO);
		cvar.Get("non_userinfo", "asdf", 0);

		Info.Print(cvar.Userinfo());

        String userinfo = cvar.Userinfo();
        var expected = java.util.Map.of("msg", "1", "gender", "male", "rate", "25000",
                "name", "unnamed", "skin", "male/grunt", "fov", "90", "hand", "0");
        assertEquals(expected.size() * 2 + 1, userinfo.split("\\\\").length);
        expected.forEach((key, value) -> assertEquals(value, Info.Info_ValueForKey(userinfo, key)));
	}

	@Test
	public void testEmptyValue() {
		Cvar cvar = Cvar.getInstance();
		cvar.Get("name", "asdf", CVAR_USERINFO );
		cvar.Get("password", "", CVAR_USERINFO );

		String userinfo = cvar.Userinfo();
		assertFalse(userinfo.contains("password")); // empty cvars should be ignored

		String empty = Info.Info_ValueForKey(userinfo, "password");
		assertTrue(empty.isEmpty());
	}

	@Test
	public void testGetStoresDescription() {
		cvar_t sensitivity = Cvar.getInstance().Get("sensitivity", "3", CVAR_OPTIONS, "Mouse speed");

		assertEquals("Mouse speed", sensitivity.description);
	}

	@Test
	public void testGetBackfillsDescriptionForExistingCvar() {
		Cvar cvar = Cvar.getInstance();
		cvar.Get("crosshair", "1", CVAR_OPTIONS);

		cvar_t crosshair = cvar.Get("crosshair", "1", CVAR_OPTIONS, "Crosshair style");

		assertEquals("Crosshair style", crosshair.description);
	}

	@Test
	public void testListByPrefixAndFlagsFiltersAndSorts() {
		Cvar cvar = Cvar.getInstance();
		cvar.Get("s_volume", "0.7", CVAR_OPTIONS);
		cvar.Get("s_music", "0.5", CVAR_OPTIONS);
		cvar.Get("s_hidden", "0", 0);
		cvar.Get("vid_gamma", "1.2", CVAR_OPTIONS);

		List<cvar_t> sound = cvar.listByPrefixAndFlags("s_", CVAR_OPTIONS);

		assertEquals(List.of("s_music", "s_volume"), sound.stream().map(var -> var.name).toList());
	}

	@Test
	public void testAliasResolvesToCanonicalCvar() {
		Cvar cvar = Cvar.getInstance();
		cvar.AddAlias("sensitivity", "in_sensitivity");

		assertNull(cvar.Set("sensitivity", "4"));
		cvar_t sensitivity = cvar.Get("in_sensitivity", "3", 0);

		assertEquals("in_sensitivity", sensitivity.name);
		assertEquals("4", cvar.VariableString("in_sensitivity"));
		assertEquals("4", cvar.VariableString("sensitivity"));
	}

	@Test
	public void testCanonicalGetBackfillsFlagsAfterAliasSet() {
		Cvar cvar = Cvar.getInstance();
		cvar.AddAlias("crosshair", "cl_crosshair");
		cvar.Set("crosshair", "2");

		cvar_t crosshair = cvar.Get("cl_crosshair", "1", CVAR_OPTIONS, "Crosshair style");

		assertEquals("cl_crosshair", crosshair.name);
		assertEquals("2", crosshair.string);
		assertEquals("Crosshair style", crosshair.description);
		assertEquals(CVAR_OPTIONS, crosshair.flags & CVAR_OPTIONS);
	}

	@Test
	public void testCompleteVariableIncludesAliases() {
		Cvar cvar = Cvar.getInstance();
		cvar.Get("in_sensitivity", "3", CVAR_OPTIONS);
		cvar.AddAlias("sensitivity", "in_sensitivity");

		List<String> completions = cvar.CompleteVariable("s");

		assertEquals(List.of("sensitivity"), completions);
	}

	@Test
	public void testVideoCvarLatchesWhileServerIsDead() {
		Cvar cvar = Cvar.getInstance();
		cvar.Get("vid_width", "1024", CVAR_ARCHIVE | CVAR_LATCH);

		cvar_t vidWidth = cvar.Set("vid_width", "1280");

		assertEquals("1024", vidWidth.string);
		assertEquals("1280", vidWidth.latched_string);
		cvar.updateLatchedVars();
		assertEquals("1280", vidWidth.string);
		assertNull(vidWidth.latched_string);
	}

	@Test
	public void testWriteArchiveVariablesPrefersLatchedValue() throws IOException {
		Cvar cvar = Cvar.getInstance();
		cvar_t vidWidth = cvar.Get("vid_width", "1024", CVAR_ARCHIVE | CVAR_LATCH);
		vidWidth.latched_string = "1280";

		Path file = Files.createTempFile("cvar-archive", ".cfg");
		try {
			cvar.writeArchiveVariables(file.toString());

			String configText = Files.readString(file);
			assertTrue(configText.contains("set vid_width \"1280\""));
			assertFalse(configText.contains("set vid_width \"1024\""));
		} finally {
			Files.deleteIfExists(file);
		}
	}
}
