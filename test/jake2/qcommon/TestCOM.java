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

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

public class TestCOM {

    @Test
    public void testParseHelpNull() {
        Com.ParseHelp ph = new Com.ParseHelp(null);

        Collection<String> result = new ArrayList<>();
        while (!ph.isEof())
            result.add(Com.Parse(ph));
        Assert.assertTrue(result.isEmpty());
    }

    @Test
    public void testParseHelpCharArray() {
        Com.ParseHelp ph = new Com.ParseHelp("testrene = \"ein mal eins\"; a=3 ");

        Collection<String> result = new ArrayList<>();
        while (!ph.isEof())
            result.add(Com.Parse(ph));
        Assert.assertEquals(Arrays.asList("testrene", "=", "ein mal eins", ";", "a=3", ""), result);
    }

    @Test
    public void testParseHelpCharArrayWithOffset() {
        Com.ParseHelp ph = new Com.ParseHelp("testrene = 3 ", 4);

        Collection<String> result = new ArrayList<>();
        while (!ph.isEof())
            result.add(Com.Parse(ph));
        Assert.assertEquals(Arrays.asList("rene", "=", "3", ""), result);
    }

    @Test
    public void testParseHelp() {
        String test = "testrene = \"ein mal eins\"; a=3 ";
        Com.ParseHelp ph = new Com.ParseHelp(test);

        Collection<String> result = new ArrayList<>();
        while (!ph.isEof())
            result.add(Com.Parse(ph));
        Assert.assertEquals(Arrays.asList("testrene", "=", "ein mal eins", ";", "a=3", ""), result);
    }

    @Test
    public void testParseHelpWithSlash() {
        String test = "testrene = 3 /";
        Com.ParseHelp ph = new Com.ParseHelp(test);

        Collection<String> result = new ArrayList<>();
        while (!ph.isEof())
            result.add(Com.Parse(ph));
        Assert.assertEquals(Arrays.asList("testrene", "=", "3", "/"), result);
    }

    @Test
    public void testParseHelpWithComments() {
        String test = "testrene = 3 // important line";
        Com.ParseHelp ph = new Com.ParseHelp(test);

        Collection<String> result = new ArrayList<>();
        while (!ph.isEof())
            result.add(Com.Parse(ph));
        Assert.assertEquals(Arrays.asList("testrene", "=", "3", ""), result);
    }

    @Test
    public void testParseHelpWhitespace() {
        String test = "     testrene = 3    ";
        Com.ParseHelp ph = new Com.ParseHelp(test);

        Collection<String> result = new ArrayList<>();
        while (!ph.isEof())
            result.add(Com.Parse(ph));
        Assert.assertEquals(Arrays.asList("testrene", "=", "3", ""), result);
    }

    @Test
    public void testParseHelpExceedMaxToken() {
        StringBuilder sb = new StringBuilder("a = ");
        for (int i = 0; i < Defines.MAX_TOKEN_CHARS; i++)
            sb.append("1");
        Com.ParseHelp ph = new Com.ParseHelp(sb.toString());

        Collection<String> result = new ArrayList<>();
        while (!ph.isEof())
            result.add(Com.Parse(ph));
        Assert.assertEquals(Arrays.asList("a", "=", ""), result);
    }
}
