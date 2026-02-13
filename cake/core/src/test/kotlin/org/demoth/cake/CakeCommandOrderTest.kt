package org.demoth.cake

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class CakeCommandOrderTest {

    @Test
    fun renderExecutesCommandBufferBeforeSendingUpdates() {
        val source = readCakeSource()

        val readPacketsIdx = source.indexOf("CL_ReadPackets()")
        val executeBufferIdx = source.indexOf("Cbuf.Execute()")
        val sendUpdatesIdx = source.indexOf("sendUpdates()")

        assertTrue("CL_ReadPackets call not found", readPacketsIdx >= 0)
        assertTrue("Cbuf.Execute call not found", executeBufferIdx >= 0)
        assertTrue("sendUpdates call not found", sendUpdatesIdx >= 0)

        assertTrue(
            "Expected CL_ReadPackets() -> Cbuf.Execute() -> sendUpdates() order",
            readPacketsIdx < executeBufferIdx && executeBufferIdx < sendUpdatesIdx
        )
    }

    private fun readCakeSource(): String {
        val candidates = listOf(
            Path.of("src/main/kotlin/org/demoth/cake/Cake.kt"),
            Path.of("cake/core/src/main/kotlin/org/demoth/cake/Cake.kt")
        )

        for (path in candidates) {
            if (Files.exists(path)) {
                return Files.readString(path)
            }
        }

        error("Could not locate Cake.kt source file")
    }
}
