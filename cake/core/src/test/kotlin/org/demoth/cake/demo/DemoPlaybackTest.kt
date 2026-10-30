package org.demoth.cake.demo

import jake2.qcommon.network.demo.DemoReader
import jake2.qcommon.network.messages.server.*
import jake2.qcommon.sizebuf_t
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DemoPlaybackTest {
    private fun int(value: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
    private fun record(vararg messages: ServerMessage): ByteArray {
        val buffer = sizebuf_t(1400)
        messages.forEach { it.writeTo(buffer) }
        return int(buffer.cursize) + buffer.data.copyOf(buffer.cursize)
    }
    private fun bootstrap(protocol: Int = 34) = record(ServerDataMessage(protocol, 1, true, "baseq2", 0, "test"), StuffTextMessage("precache\n"))
    private fun frame(number: Int) = record(FrameHeaderMessage(number, -1, 0, 0, byteArrayOf()), PlayerInfoMessage(fov = 90), PacketEntitiesMessage(emptyList()))
    private fun playback(bytes: ByteArray) = DemoPlayback(DemoReader(ByteArrayInputStream(bytes)))

    @Test fun `bootstrap is immediate then frames follow recorded time and last frame remains visible`() {
        playback(bootstrap() + frame(100) + frame(101) + frame(104) + int(-1)).use { demo ->
            val frames = mutableListOf<Int>()
            val dispatch: (List<ServerMessage>) -> Boolean = { messages ->
                frames += messages.filterIsInstance<FrameHeaderMessage>().map { it.frameNumber }; true
            }
            assertTrue(demo.update(0f, dispatch))
            assertEquals(listOf(100), frames)
            assertEquals(9900, demo.presentationTimeMillis)
            assertTrue(demo.update(.05f, dispatch))
            assertEquals(9950, demo.presentationTimeMillis)
            assertTrue(demo.update(.05f, dispatch))
            assertEquals(listOf(100, 101), frames)
            assertEquals(10000, demo.presentationTimeMillis)
            assertTrue(demo.update(.3f, dispatch))
            assertEquals(listOf(100, 101, 104), frames)
            assertFalse(demo.update(.1f, dispatch))
        }
    }

    @Test fun `empty records cannot monopolize a client update`() {
        playback(ByteArray(4 * 1000) + bootstrap() + frame(1) + int(-1)).use { demo ->
            var dispatched = 0
            assertTrue(demo.update(0f) { dispatched++; true })
            assertEquals(DemoPlayback.MAX_RECORDS_PER_UPDATE, dispatched)
        }
    }

    @ParameterizedTest
    @ValueSource(ints = [31, 32])
    fun `classic startup and frame layouts retain suppression visibility and timing`(protocol: Int) {
        // Hand-authored classic layouts from Yamagi CL_ParseServerData/CL_ParseFrame and
        // Q2PRO CL_ParseFrame: only protocol 26 omits suppressCount; 31 and 32 follow the 34 layout.
        val startup = byteArrayOf(12) + int(protocol) + int(1) + byteArrayOf(1) +
            "xatrix\u0000".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0, 0) +
            "legacy32\u0000".toByteArray(Charsets.ISO_8859_1)
        fun snapshot(number: Int, last: Int): ByteArray = byteArrayOf(20) + int(number) + int(last) +
            byteArrayOf(7, 1, 3, // suppressCount, areaBits length, areaBits
                17, 0, 0, 0, 0, 0, 0, // playerinfo: no changed fields or stats
                18, 0, 0) // packetentities terminator
        fun framed(bytes: ByteArray) = int(bytes.size) + bytes
        // Version and first snapshot intentionally share a record.
        playback(framed(startup + snapshot(100, -1)) + framed(snapshot(101, 100)) + int(-1)).use { demo ->
            val messages = mutableListOf<ServerMessage>()
            val dispatch: (List<ServerMessage>) -> Boolean = { messages += it; true }
            assertTrue(demo.update(0f, dispatch))
            val data = messages.filterIsInstance<ServerDataMessage>().single()
            assertEquals(protocol, data.protocol)
            assertEquals("xatrix", data.gameName)
            assertEquals("legacy32", data.levelString)
            assertEquals(9900, demo.presentationTimeMillis)
            repeat(3) { assertTrue(demo.update(0f, dispatch)) }
            assertEquals(1, messages.filterIsInstance<FrameHeaderMessage>().size)
            assertTrue(demo.update(.1f, dispatch))
            val frames = messages.filterIsInstance<FrameHeaderMessage>()
            assertEquals(listOf(100, 101), frames.map { it.frameNumber })
            assertEquals(listOf(-1, 100), frames.map { it.lastFrame })
            frames.forEach {
                assertEquals(7, it.suppressCount)
                assertArrayEquals(byteArrayOf(3), it.areaBits.toByteArray())
            }
            assertFalse(demo.update(.1f, dispatch))
        }
    }

    @Test fun `protocol 26 frames omit suppression while playback preserves delta timing and map version changes`() {
        fun oldFrame(number: Int, last: Int): ByteArray {
            // svc_frame: two ints followed directly by areaBits; no suppressCount in protocol 26.
            val bytes = byteArrayOf(20) + int(number) + int(last) + byteArrayOf(1, 3,
                17, 0, 0, 0, 0, 0, 0, 18, 0, 0)
            return int(bytes.size) + bytes
        }
        playback(bootstrap(26) + oldFrame(100, -1) + oldFrame(101, 100) +
            bootstrap(32) + frame(5) + bootstrap(26) + oldFrame(1, 0) + int(-1)).use { demo ->
            val messages = mutableListOf<ServerMessage>()
            val dispatch: (List<ServerMessage>) -> Boolean = { messages += it; true }
            assertTrue(demo.update(0f, dispatch))
            assertEquals(9900, demo.presentationTimeMillis)
            repeat(3) { assertTrue(demo.update(0f, dispatch)) }
            assertEquals(1, messages.filterIsInstance<FrameHeaderMessage>().size)
            assertTrue(demo.update(.1f, dispatch))
            assertTrue(demo.update(.1f, dispatch))
            assertTrue(demo.update(.1f, dispatch))
            assertFalse(demo.update(.1f, dispatch))
            assertEquals(listOf(26, 32, 26), messages.filterIsInstance<ServerDataMessage>().map { it.protocol })
            val frames = messages.filterIsInstance<FrameHeaderMessage>()
            assertEquals(listOf(100, 101, 5, 1), frames.map { it.frameNumber })
            assertEquals(listOf(-1, 100, -1, 0), frames.map { it.lastFrame })
            for (frame in frames.filter { it.frameNumber != 5 }) {
                assertEquals(0, frame.suppressCount)
                assertArrayEquals(byteArrayOf(3), frame.areaBits.toByteArray())
            }
        }
    }

    @Test fun `map changes can switch between supported classic demo versions`() {
        playback(bootstrap(32) + frame(1000) + bootstrap(34) + frame(5) + int(-1)).use { demo ->
            val protocols = mutableListOf<Int>()
            val frames = mutableListOf<Int>()
            val dispatch: (List<ServerMessage>) -> Boolean = { messages ->
                protocols += messages.filterIsInstance<ServerDataMessage>().map { it.protocol }
                frames += messages.filterIsInstance<FrameHeaderMessage>().map { it.frameNumber }
                true
            }
            assertTrue(demo.update(0f, dispatch))
            assertTrue(demo.update(.1f, dispatch))
            assertFalse(demo.update(.1f, dispatch))
            assertEquals(listOf(32, 34), protocols)
            assertEquals(listOf(1000, 5), frames)
        }
    }

    @Test fun `unsupported protocol and malformed body dispatch no prefix`() {
        for (bytes in listOf(bootstrap(25), bootstrap(33), bootstrap(35),
            record(ServerDataMessage(34, 1, true, "baseq2", 0, "test")) + int(2) + byteArrayOf(6, 7))) {
            playback(bytes).use { demo ->
                assertThrows(IOException::class.java) { demo.update(0f) { true } }
            }
        }
    }

    @Test fun `map bootstrap rebases frame numbers without a new connection`() {
        playback(bootstrap() + frame(1000) + bootstrap() + frame(5) + frame(6) + int(-1)).use { demo ->
            val frames = mutableListOf<Int>()
            val dispatch: (List<ServerMessage>) -> Boolean = { frames += it.filterIsInstance<FrameHeaderMessage>().map { f -> f.frameNumber }; true }
            demo.update(0f, dispatch)
            assertEquals(listOf(1000), frames)
            demo.update(.1f, dispatch)
            assertEquals(listOf(1000, 5), frames)
            demo.update(.1f, dispatch)
            assertEquals(listOf(1000, 5, 6), frames)
        }
    }

    @Test fun `incomplete snapshots invalid player slots and initial delta frames reject`() {
        val header = FrameHeaderMessage(10, -1, 0, 0, byteArrayOf())
        val cases = listOf(
            bootstrap() + record(header),
            bootstrap() + record(PlayerInfoMessage(), PacketEntitiesMessage(emptyList())),
            bootstrap() + record(header.copy(lastFrame = 9), PlayerInfoMessage(), PacketEntitiesMessage(emptyList())),
            record(ServerDataMessage(34, 1, true, "baseq2", 999, "test")),
        )
        for (bytes in cases) playback(bytes + int(-1)).use { demo ->
            assertThrows(IOException::class.java) { demo.update(0f) { true } }
        }
    }

    @Test fun `closing during dispatch stops all further records`() {
        val demo = playback(bootstrap() + frame(1) + frame(2) + int(-1))
        var dispatches = 0
        assertFalse(demo.update(0f) { dispatches++; demo.close(); false })
        assertEquals(1, dispatches)
    }
}
