package jake2.server

import jake2.qcommon.exec.Cmd
import org.demoth.cake.fullgame.EmbeddedCakeHost
import org.demoth.cake.hosting.CakeHost
import org.demoth.cake.lwjgl3.CakeDesktopLauncher
import org.demoth.cake.profile.CakeGameProfile
import org.demoth.cake.profile.CakeGameProfileStore
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

/** The desktop launcher surfaces failures only after Cake releases its Host. */
@EnabledIfEnvironmentVariable(named = "CAKE_HOST_GL_TESTS", matches = "1")
class CakeLauncherGlTest {
    @TempDir lateinit var home: Path

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `fatal startup and frame failures reach disposal and preserve their cause`(failInStartup: Boolean) {
        val previousHome = System.getProperty("user.home")
        System.setProperty("user.home", home.toString())
        try {
            CakeGameProfileStore().upsertProfile(CakeGameProfile("launcher", Path.of("src/test/resources").toAbsolutePath().toString()))
            val embedded = EmbeddedCakeHost(home.resolve(".cake"))
            val failure = IllegalStateException("fatal Cake failure")
            val cleanupFailure = IllegalStateException("Host disposal failed")
            var updates = 0
            var disposals = 0
            val host = object : CakeHost by embedded {
                override fun initialize(startupRejection: () -> String?) {
                    embedded.initialize(startupRejection)
                    if (failInStartup) throw failure
                }

                override fun update(elapsedMillis: Long) {
                    updates++
                    throw failure
                }

                override fun dispose() {
                    disposals++
                    embedded.dispose()
                    throw cleanupFailure
                }
            }

            val escaped = assertThrows(IllegalStateException::class.java) {
                CakeDesktopLauncher.launch(arrayOf("+set", "vid_hidden", "1"), host, "Cake lifecycle test")
            }
            assertSame(failure, escaped)
            assertArrayEquals(arrayOf(cleanupFailure), escaped.suppressed)
            assertEquals(if (failInStartup) 0 else 1, updates)
            assertEquals(1, disposals)
            assertNull(embedded.server)
            assertNull(Cmd.FindCommand("map"))
        } finally {
            System.setProperty("user.home", previousHome)
        }
    }
}
