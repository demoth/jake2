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
package jake2.qcommon.exec

import jake2.qcommon.Com
import jake2.qcommon.Com.LogSubsystem.COMMON
import jake2.qcommon.Defines

/** Reads Quake command tokens from a cursor shared by tokenization and macro expansion. */
internal class CommandTokenReader @JvmOverloads constructor(private val text: String, offset: Int = 0) {
    private var index = offset

    fun isEof(): Boolean = index >= text.length

    fun skipWhitespaceToEndOfLine(): Char = skipWhitespace(stopAtEndOfLine = true)

    fun readToken(): String {
        skipWhitespace(stopAtEndOfLine = false)
        while (text.startsWith("//", index)) {
            while (current() != '\u0000' && current() != '\n') {
                index++
            }
            skipWhitespace(stopAtEndOfLine = false)
        }
        if (isEof()) return ""

        if (current() == '"') {
            val start = ++index
            while (current() != '"' && current() != '\u0000') {
                index++
            }
            val token = text.substring(start, minOf(index, start + Defines.MAX_TOKEN_CHARS))
            index++
            return token
        }

        val start = index++
        while (current() > ' ') {
            index++
        }
        if (index - start >= Defines.MAX_TOKEN_CHARS) {
            Com.Warn(COMMON, "Token exceeded ${Defines.MAX_TOKEN_CHARS} chars, discarded.")
            return ""
        }
        return text.substring(start, index)
    }

    private fun current(): Char = if (isEof()) '\u0000' else text[index]

    private fun skipWhitespace(stopAtEndOfLine: Boolean): Char {
        var ch = current()
        while (ch != '\u0000' && ch <= ' ' && !(stopAtEndOfLine && ch == '\n')) {
            index++
            ch = current()
        }
        return ch
    }
}
