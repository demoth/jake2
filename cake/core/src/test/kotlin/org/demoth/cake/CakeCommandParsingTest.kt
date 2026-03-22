package org.demoth.cake

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CakeCommandParsingTest {

    @Test
    fun connectRequiresExactlyOneTargetArgument() {
        assertNull(sanitizeConnectTarget(listOf("connect")))
        assertNull(sanitizeConnectTarget(listOf("connect", "a", "b")))
    }

    @Test
    fun connectTrimsAndAcceptsNonBlankTarget() {
        assertEquals("example.org:27910", sanitizeConnectTarget(listOf("connect", "  example.org:27910  ")))
    }

    @Test
    fun connectRejectsBlankTarget() {
        assertNull(sanitizeConnectTarget(listOf("connect", "   ")))
    }
}
