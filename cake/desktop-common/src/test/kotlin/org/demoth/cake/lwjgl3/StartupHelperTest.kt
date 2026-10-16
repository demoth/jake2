package org.demoth.cake.lwjgl3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StartupHelperTest {
    @Test
    fun `restart preserves JVM options entrypoint and application arguments as separate tokens`() {
        val command = buildRestartCommand(
            "/Java Home/bin/java",
            listOf("--enable-native-access=ALL-UNNAMED", "-Duser.home=/a home"),
            "/a path/client.jar:/other.jar",
            "jake2.fullgame.Jake2",
            arrayOf("+map", "base1", "/a path/model.md2"),
        )
        assertEquals(listOf(
            "/Java Home/bin/java", "-XstartOnFirstThread", "-DjvmIsRestarted=true",
            "--enable-native-access=ALL-UNNAMED", "-Duser.home=/a home",
            "-cp", "/a path/client.jar:/other.jar", "jake2.fullgame.Jake2",
            "+map", "base1", "/a path/model.md2",
        ), command)
    }
}
