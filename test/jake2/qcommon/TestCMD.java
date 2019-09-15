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

package jake2.qcommon;

import jake2.game.Cmd;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TestCMD {

    @Test
    public void testSampleCommand() {
        List<String> result = new ArrayList<>();
        Cmd.AddCommand("test", () -> {
            result.add("success");
        });
        Cmd.ExecuteString("test");
        assertEquals("success", result.get(0));
    }

    @Test
    public void testTokenizeString() {
        Cmd.TokenizeString("echo test", false);
        assertEquals(2, Cmd.Argc());
        assertEquals("echo", Cmd.Argv(0));
        assertEquals("test", Cmd.Argv(1));
    }

    @Test
    public void testExpandMacro() {
        Cvar.Get("name", "world", 0);
        String result = Cmd.MacroExpandString("hello $name");
        assertEquals("hello world", result);
    }

    @Test
    public void testExpandMacroMiddle() {
        Cvar.Get("name1", "world", 0);
        String result = Cmd.MacroExpandString("hello $name1 again");
        assertEquals("hello world again", result);
    }


    @Test
    public void testCommandWithVariable() {
        StringBuilder result = new StringBuilder();
        Cvar.Get("test_var", "value", 0);
        Cmd.AddCommand("test_cmd", () -> result.append("success ").append(Cmd.Args()));

        Cmd.ExecuteString("test_cmd $test_var");
        assertEquals("success value", result.toString());
    }
}
