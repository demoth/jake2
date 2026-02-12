package org.demoth.cake.stages

import jake2.qcommon.Defines

/**
 * Legacy-compatible layout tokenizer for IdTech2 statusbar scripts.
 * Behavior is intentionally aligned with the old client LayoutParser.
 */
internal class LayoutParserCompat(layout: String?) {
    private var tokenPos = 0
    private var tokenLength = 0
    private var index = 0
    private var data: String = ""

    init {
        reset(layout)
    }

    fun reset(layout: String?) {
        tokenPos = 0
        tokenLength = 0
        index = 0
        data = layout ?: ""
    }

    fun hasNext(): Boolean = index < data.length

    fun next() {
        while (true) {
            skipWhites()
            if (isEof()) {
                tokenLength = 0
                return
            }

            if (peek() == '/') {
                advance()
                if (peek() == '/') {
                    skipToEol()
                    continue
                }
                retreat()
            }
            break
        }

        var len = 0
        if (peek() == '"') {
            advance()
            tokenPos = index
            while (true) {
                val c = peek()
                advance()
                if (c == '"' || c == '\u0000') {
                    tokenLength = len
                    return
                }
                if (len < Defines.MAX_TOKEN_CHARS) {
                    len++
                }
            }
        }

        tokenPos = index
        var c = peek()
        do {
            if (len < Defines.MAX_TOKEN_CHARS) {
                len++
            }
            c = advance()
        } while (c.code > 32)

        tokenLength = if (len == Defines.MAX_TOKEN_CHARS) 0 else len
    }

    fun tokenEquals(other: String): Boolean {
        return tokenLength == other.length && data.regionMatches(tokenPos, other, 0, tokenLength)
    }

    fun tokenAsInt(): Int {
        val token = token()
        return token.toIntOrNull() ?: 0
    }

    fun token(): String {
        if (tokenLength == 0) return ""
        return data.substring(tokenPos, tokenPos + tokenLength)
    }

    private fun isEof(): Boolean = index >= data.length

    private fun skipWhites() {
        while (!isEof()) {
            val c = data[index]
            if (c.code > 32 || c == '\u0000') {
                return
            }
            index++
        }
    }

    private fun skipToEol() {
        while (!isEof()) {
            val c = data[index]
            if (c == '\n' || c == '\u0000') {
                return
            }
            index++
        }
    }

    private fun peek(): Char {
        if (isEof()) return '\u0000'
        return data[index]
    }

    private fun advance(): Char {
        index++
        if (isEof()) return '\u0000'
        return data[index]
    }

    private fun retreat() {
        if (index > 0) {
            index--
        }
    }
}
