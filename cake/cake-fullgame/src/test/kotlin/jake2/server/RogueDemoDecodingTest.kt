package jake2.server

import jake2.qcommon.Defines
import jake2.qcommon.network.demo.DemoReader
import jake2.qcommon.network.messages.server.FrameHeaderMessage
import jake2.qcommon.network.messages.server.TEMessage
import jake2.qcommon.network.protocol.MessageBodyCodec
import jake2.qcommon.network.protocol.MessageDecodeResult
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.nio.file.Files
import java.nio.file.Path

/** Read-only interoperability check against installed Ground Zero demos; no graphics or gameplay. */
@EnabledIfEnvironmentVariable(named = "CAKE_HOST_GAME_TESTS", matches = "1")
class RogueDemoDecodingTest {
    @Test fun `all three Ground Zero demos decode through the terminator`() {
        val basedir = Path.of(requireNotNull(System.getenv("CAKE_TEST_BASEDIR")))
        val demos = (1..3).map { basedir.resolve("rogue/demos/demo$it.dm2") }
        assumeTrue(demos.all(Files::isRegularFile), "Ground Zero demo data is not installed")
        val effects = mutableSetOf<Int>()
        for (demo in demos) {
            var frames = 0
            DemoReader(Files.newInputStream(demo)).use { reader ->
                while (true) {
                    val record = reader.read()
                    if (record == DemoReader.Record.End) break
                    val result = MessageBodyCodec.decodeServerBody((record as DemoReader.Record.Body).bytes)
                    assertInstanceOf(MessageDecodeResult.Decoded::class.java, result, "$demo at byte ${reader.offset}: $result")
                    val messages = (result as MessageDecodeResult.Decoded).messages
                    frames += messages.count { it is FrameHeaderMessage }
                    effects += messages.filterIsInstance<TEMessage>().map { it.style }
                }
            }
            assertTrue(frames > 0, "$demo must contain world snapshots")
        }
        assertTrue(Defines.TE_HEATBEAM in effects, "Exercise the heatbeam regression")
        assertTrue(Defines.TE_STEAM in effects, "Exercise the steam regression")
    }
}
