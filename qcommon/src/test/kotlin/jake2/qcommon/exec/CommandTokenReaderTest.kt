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

package jake2.qcommon.exec

import jake2.qcommon.Defines
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommandTokenReaderTest {
    @Test
    fun `empty input has no tokens`() {
        assertTrue(tokens("").isEmpty())
    }

    @Test
    fun `quoted text stays in a single token`() {
        assertEquals(
            listOf("testrene", "=", "ein mal eins", ";", "a=3", ""),
            tokens("testrene = \"ein mal eins\"; a=3 "),
        )
    }

    @Test
    fun `reading starts at the supplied offset`() {
        assertEquals(listOf("rene", "=", "3", ""), tokens("testrene = 3 ", offset = 4))
    }

    @Test
    fun `a lone slash stays in the output`() {
        assertEquals(listOf("testrene", "=", "3", "/"), tokens("testrene = 3 /"))
    }

    @Test
    fun `a double slash starts a comment`() {
        assertEquals(listOf("testrene", "=", "3", ""), tokens("testrene = 3 // important line"))
    }

    @Test
    fun `leading and trailing whitespace preserve legacy token boundaries`() {
        assertEquals(listOf("testrene", "=", "3", ""), tokens("     testrene = 3    "))
    }

    @Test
    fun `an unquoted token at the size limit is discarded`() {
        assertEquals(listOf("a", "=", ""), tokens("a = " + "1".repeat(Defines.MAX_TOKEN_CHARS)))
    }

    private fun tokens(text: String, offset: Int = 0): List<String> {
        val reader = CommandTokenReader(text, offset)
        return buildList {
            while (!reader.isEof()) add(reader.readToken())
        }
    }
}
