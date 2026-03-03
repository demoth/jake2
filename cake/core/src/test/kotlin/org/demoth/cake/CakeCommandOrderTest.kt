package org.demoth.cake

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class CakeCommandOrderTest {

    @Test
    fun renderExecutesCommandBufferBeforeSendingUpdates() {
        val source = readCakeSource()

        val readPacketsIdx = source.indexOf("CL_ReadPackets()")
        val executeBufferIdx = source.indexOf("Cbuf.Execute()")
        val sendUpdatesIdx = source.indexOf("sendUpdates()")

        assertTrue(readPacketsIdx >= 0, "CL_ReadPackets call not found")
        assertTrue(executeBufferIdx >= 0, "Cbuf.Execute call not found")
        assertTrue(sendUpdatesIdx >= 0, "sendUpdates call not found")

        assertTrue(
            readPacketsIdx < executeBufferIdx && executeBufferIdx < sendUpdatesIdx,
            "Expected CL_ReadPackets() -> Cbuf.Execute() -> sendUpdates() order",
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
