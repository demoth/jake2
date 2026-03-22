package org.demoth.cake

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ServerStuffTextPolicyTest {

    @Test
    fun acceptsKnownConnectionFlowCommands() {
        val result = filterServerStuffText("changing\nreconnect\ncmd configstrings 3 0\nprecache 3\nspectator 1")

        assertEquals(
            listOf("changing", "reconnect", "cmd configstrings 3 0", "precache 3", "spectator 1"),
            result.acceptedCommands,
        )
        assertEquals(emptyList<String>(), result.rejectedCommands)
    }

    @Test
    fun rejectsUnexpectedCommandsWhilePreservingExpectedOnes() {
        val result = filterServerStuffText("spectator 0; connect; cmd baselines 2 32")

        assertEquals(
            listOf("spectator 0", "cmd baselines 2 32"),
            result.acceptedCommands,
        )
        assertEquals(listOf("connect"), result.rejectedCommands)
    }

    @Test
    fun rejectsMalformedExpectedCommandShapes() {
        val result = filterServerStuffText("precache nope\nreconnect now")

        assertEquals(emptyList<String>(), result.acceptedCommands)
        assertEquals(listOf("precache nope", "reconnect now"), result.rejectedCommands)
    }

    @Test
    fun keepsQuotedSemicolonsInsideSingleCommand() {
        val result = filterServerStuffText("cmd echo \"a;b\"; changing")

        assertEquals(listOf("cmd echo a;b", "changing"), result.acceptedCommands)
        assertEquals(emptyList<String>(), result.rejectedCommands)
    }
}
