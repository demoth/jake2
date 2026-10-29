package org.demoth.cake.demo

import jake2.qcommon.Defines
import jake2.qcommon.entity_state_t
import jake2.qcommon.network.demo.DemoReader
import jake2.qcommon.network.demo.DemoWriter
import jake2.qcommon.network.messages.server.*
import jake2.qcommon.network.protocol.MessageBodyCodec
import jake2.qcommon.network.protocol.MessageDecodeResult
import jake2.qcommon.network.transport.PacketBytes
import jake2.qcommon.sizebuf_t
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class DemoRecordingTest {
    private fun server(map: String = "first") = ServerDataMessage.forClient(7, false, "baseq2", 0, map)
    private fun bootstrap(map: String = "first") = DemoBootstrap(server(map), listOf(ConfigStringMessage(0, map)), emptyList())
    private fun body(vararg messages: ServerMessage): PacketBytes {
        val buffer = sizebuf_t(Defines.MAX_MSGLEN)
        messages.forEach { it.writeTo(buffer) }
        return PacketBytes.copyOf(buffer.data, buffer.cursize)
    }
    private fun frame(number: Int, last: Int = -1) = body(
        PrintMessage(2, "before $number"), FrameHeaderMessage(number, last, 0, 0, byteArrayOf()),
        PlayerInfoMessage(fov = 90), PacketEntitiesMessage(emptyList()), PrintMessage(2, "after $number"))
    private fun records(bytes: ByteArray): List<PacketBytes> = buildList {
        DemoReader(ByteArrayInputStream(bytes)).use { reader ->
            while (true) when (val record = reader.read()) {
                is DemoReader.Record.Body -> add(record.bytes)
                DemoReader.Record.End -> break
            }
        }
    }
    private fun decode(body: PacketBytes) = (MessageBodyCodec.decodeServerBody(body) as MessageDecodeResult.Decoded).messages

    @Test fun `bootstrap owns startup state sets demo flag and packs whole messages into bounded records`() {
        val config = (0..80).map { ConfigStringMessage(it, "value$it".repeat(6)) }.toMutableList()
        config += ConfigStringMessage(81, "")
        val engine = entity_state_t(1).apply { modelindex = 3; origin[0] = 32f }
        val baseline = SpawnBaselineMessage(engine)
        val baselines = mutableListOf(baseline)
        val startup = DemoBootstrap(server(), config, baselines)
        config.clear(); baselines.clear(); engine.clear()
        val output = ByteArrayOutputStream()
        DemoWriter(output).use(startup::writeTo)
        val bodies = records(output.toByteArray())
        assertTrue(bodies.size > 1)
        assertTrue(bodies.all { it.size in 1..Defines.MAX_MSGLEN })
        val decoded = bodies.flatMap(::decode)
        assertEquals(server().copy(demo = true), decoded.first())
        assertEquals(81, decoded.filterIsInstance<ConfigStringMessage>().size)
        assertEquals((0..80).toList(), decoded.filterIsInstance<ConfigStringMessage>().map { it.index })
        assertEquals(baseline, decoded.filterIsInstance<SpawnBaselineMessage>().single())
        assertEquals(StuffTextMessage("precache\n"), decoded.last())
    }

    @Test fun `bootstrap rejects unsupported startup and oversized messages before writing output`() {
        assertThrows(IllegalArgumentException::class.java) { DemoBootstrap(server().copy(protocol = 26), emptyList(), emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { DemoBootstrap(server().copy(playerNumber = -1), emptyList(), emptyList()) }
        assertThrows(IllegalArgumentException::class.java) {
            DemoBootstrap(server(), listOf(ConfigStringMessage(0, "x".repeat(Defines.MAX_MSGLEN))), emptyList())
        }
    }

    @Test fun `recording waits for an accepted complete full snapshot then preserves raw bodies through playback`() {
        val output = ByteArrayOutputStream()
        var currentMap = "old"
        var bootstraps = 0
        val recording = DemoRecording(DemoWriter(output)) { bootstraps++; bootstrap(currentMap) }
        val full = frame(100)
        val delta = frame(101, 100)
        val final = body(PrintMessage(2, "final message"))
        recording.use {
            assertTrue(it.needsFullSnapshot)
            it.capture(body(PrintMessage(2, "not started")), false)
            it.capture(frame(99, 98), true)
            it.capture(full, false)
            it.capture(body(FrameHeaderMessage(100, -1, 0, 0, byteArrayOf())), true)
            assertEquals(0, output.size())
            assertEquals(0, bootstraps)
            currentMap = "latest"
            it.capture(full, true)
            assertFalse(it.needsFullSnapshot)
            it.capture(delta, true)
            it.capture(final, false)
        }
        assertFalse(recording.needsFullSnapshot)
        val saved = records(output.toByteArray())
        assertEquals(listOf(full, delta, final), saved.drop(1))
        assertEquals("latest", (decode(saved.first()).first() as ServerDataMessage).levelString)
        val presented = mutableListOf<Int>()
        DemoPlayback(DemoReader(ByteArrayInputStream(output.toByteArray()))).use { playback ->
            val dispatch: (List<ServerMessage>) -> Boolean = { messages ->
                presented += messages.filterIsInstance<FrameHeaderMessage>().map { it.frameNumber }; true
            }
            assertTrue(playback.update(0f, dispatch))
            assertTrue(playback.update(.1f, dispatch))
            assertFalse(playback.update(.1f, dispatch))
        }
        assertEquals(listOf(100, 101), presented)
        assertEquals(1, bootstraps)
    }

    @Test fun `a map reset or rejected snapshot waits for a fresh full frame and takes fresh startup state`() {
        val output = ByteArrayOutputStream()
        var map = "first"
        DemoRecording(DemoWriter(output)) { bootstrap(map) }.use { recording ->
            recording.capture(frame(100), true)
            recording.capture(frame(101, 100), false)
            assertTrue(recording.needsFullSnapshot)
            recording.capture(frame(102, 101), true)
            recording.capture(frame(103), true)
            map = "second"
            recording.capture(body(server(map), ConfigStringMessage(0, map)), false)
            assertTrue(recording.needsFullSnapshot)
            recording.capture(frame(1, 10), true)
            recording.capture(frame(2, 0), true)
        }
        val decoded = records(output.toByteArray()).flatMap(::decode)
        assertEquals(listOf(100, 103, 2), decoded.filterIsInstance<FrameHeaderMessage>().map { it.frameNumber })
        assertEquals(listOf("first", "first", "second"), decoded.filterIsInstance<ServerDataMessage>().map { it.levelString })
    }

    @Test fun `live valid deltas must reference frames saved in this recording and its current map`() {
        val output = ByteArrayOutputStream()
        DemoRecording(DemoWriter(output)) { bootstrap() }.use { recording ->
            recording.capture(frame(100), true)
            recording.capture(frame(101, 100), true)
            recording.capture(frame(102, 100), true)
            assertFalse(recording.needsFullSnapshot)
            recording.capture(frame(103, 99), true)
            assertTrue(recording.needsFullSnapshot)
            recording.capture(frame(104, 102), true)
            recording.capture(frame(105), true)
            recording.capture(body(server("next")), false)
            recording.capture(frame(106, 105), true)
            assertTrue(recording.needsFullSnapshot)
            recording.capture(frame(107), true)
        }
        val savedFrames = records(output.toByteArray()).flatMap(::decode).filterIsInstance<FrameHeaderMessage>()
        assertEquals(listOf(100, 101, 102, 105, 107), savedFrames.map { it.frameNumber })
    }

    @Test fun `malformed bodies write no valid prefix and close the recording`() {
        val output = ByteArrayOutputStream()
        val recording = DemoRecording(DemoWriter(output)) { bootstrap() }
        recording.capture(frame(1), true)
        val before = output.size()
        val malformed = PacketBytes.copyOf(body(PrintMessage(2, "prefix")).toByteArray() + byteArrayOf(254.toByte()))
        assertThrows(IOException::class.java) { recording.capture(malformed, false) }
        assertEquals(before + 4, output.size())
        assertThrows(IllegalStateException::class.java) { recording.capture(frame(2), true) }
        recording.close()
        assertEquals(2, records(output.toByteArray()).size)
    }

    @Test fun `stopping before a full frame closes without requesting or fabricating any snapshot`() {
        val output = ByteArrayOutputStream()
        val recording = DemoRecording(DemoWriter(output)) { error("No full frame ready") }
        recording.close()
        recording.close()
        assertFalse(recording.needsFullSnapshot)
        assertTrue(records(output.toByteArray()).isEmpty())
    }
}
