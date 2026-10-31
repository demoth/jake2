package org.demoth.cake.demo

import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.assets.loaders.FileHandleResolver
import com.badlogic.gdx.files.FileHandle
import jake2.qcommon.Defines
import jake2.qcommon.math.Vector3f
import jake2.qcommon.network.demo.DemoReader
import jake2.qcommon.network.demo.DemoWriter
import jake2.qcommon.network.messages.server.*
import jake2.qcommon.network.protocol.*
import jake2.qcommon.network.transport.PacketBytes
import jake2.qcommon.sizebuf_t
import org.demoth.cake.CakeCvars
import org.demoth.cake.config.GameConfiguration
import org.demoth.cake.input.InputManager
import org.demoth.cake.stages.ingame.ClientEntityManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Real body codecs, startup snapshots and client frame reconstruction; no graphics or game resources. */
class LiveDemoRecordingTest {
    private class Client : AutoCloseable {
        val assets = AssetManager(FileHandleResolver(::FileHandle), false)
        var config = GameConfiguration(assets)
        var entities = ClientEntityManager()
        var server: ServerDataMessage? = null
        val positions = mutableListOf<Float>()
        val prints = mutableListOf<String>()
        var disconnected = false

        fun apply(messages: List<ServerMessage>): Boolean {
            var accepted = false
            for (message in messages) when (message) {
                is ServerDataMessage -> {
                    entities.dispose()
                    entities = ClientEntityManager()
                    config = GameConfiguration(assets)
                    server = message
                }
                is ConfigStringMessage -> config.applyConfigString(message.index, message.config)
                is SpawnBaselineMessage -> entities.processBaselineMessage(message)
                is FrameHeaderMessage -> entities.processServerFrameHeader(message)
                is PlayerInfoMessage -> entities.processPlayerInfoMessage(message)
                is PacketEntitiesMessage -> {
                    accepted = entities.processPacketEntitiesMessage(message)
                    if (accepted) positions += entities.getEntityOrigin(1)!!.x
                }
                is PrintMessage -> prints += message.text
                DisconnectMessage -> disconnected = true
                else -> Unit
            }
            return accepted
        }
        fun bootstrap() = DemoBootstrap(checkNotNull(server), config.snapshotConfigStrings(), entities.snapshotBaselines())
        override fun close() { entities.dispose(); assets.dispose() }
    }

    private fun body(messages: List<ServerMessage>): PacketBytes {
        val buffer = sizebuf_t(Defines.MAX_MSGLEN)
        messages.forEach { it.writeTo(buffer) }
        return PacketBytes.copyOf(buffer.data, buffer.cursize)
    }
    private fun state(x: Float) = EntityStateSnapshot(1, origin = Vector3f(x, 0f, 0f), modelIndex = 3)
    private fun frame(number: Int, last: Int, from: EntityStateSnapshot, to: EntityStateSnapshot) = listOf(
        FrameHeaderMessage(number, last, 0, 0, byteArrayOf()), PlayerInfoMessage(fov = 90),
        PacketEntitiesMessage(listOf(EntityUpdate.Change(checkNotNull(EntityDeltaCodec.plan(from, to, true, last <= 0))))))
    private fun startup(map: String, baseline: EntityStateSnapshot): List<ServerMessage> = listOf(
        ServerDataMessage.forClient(1, false, "baseq2", 0, map), ConfigStringMessage(0, map), SpawnBaselineMessage(baseline))

    @Test fun `live packet capture requests a full frame and replays reconstructed entity deltas across maps`() {
        CakeCvars.registerEntities()
        CakeCvars.registerInput()
        val output = ByteArrayOutputStream()
        val capturedBodies = mutableListOf<PacketBytes>()
        Client().use { live ->
            var sequence = 1
            fun receive(messages: List<ServerMessage>, recording: DemoRecording? = null) {
                val raw = body(messages)
                val wire = PacketCodec.encodeSequenced(sequence++, 0, null, raw)
                val packet = (PacketCodec.decode(wire, PacketDirection.FROM_SERVER) as PacketDecodeResult.Decoded).envelope as PacketEnvelope.Sequenced
                val decoded = (MessageBodyCodec.decodeServer(packet) as MessageDecodeResult.Decoded).messages
                val accepted = live.apply(decoded)
                recording?.capture(packet, accepted)
                if (recording != null && messages.any { it is FrameHeaderMessage } && !recording.needsFullSnapshot)
                    capturedBodies += raw
            }
            val firstBaseline = state(32f)
            receive(startup("first", firstBaseline))
            receive(frame(50, -1, firstBaseline, state(64f)))
            DemoRecording(DemoWriter(output), live::bootstrap).use { recording ->
                val input = InputManager(nowNanosProvider = { 1_000_000L })
                input.updateLocalInput(0f, live.entities.currentFrame)
                assertTrue(input.buildMoveMessage(1, live.entities.currentFrame, 16, recording.needsFullSnapshot).lastReceivedFrame <= 0)
                assertTrue(live.entities.currentFrame.valid)
                receive(frame(51, 50, state(64f), state(72f)), recording)
                assertTrue(recording.needsFullSnapshot)
                receive(frame(52, -1, firstBaseline, state(80f)), recording)
                assertEquals(52, input.buildMoveMessage(2, live.entities.currentFrame, 16, recording.needsFullSnapshot).lastReceivedFrame)
                receive(frame(53, 52, state(80f), state(96f)), recording)
                val nextBaseline = state(128f)
                receive(startup("second", nextBaseline), recording)
                assertTrue(recording.needsFullSnapshot)
                receive(frame(1, -1, nextBaseline, state(144f)), recording)
                receive(frame(2, 1, state(144f), state(160f)), recording)
                // The application captures the final accepted payload before closing on disconnect.
                receive(listOf(PrintMessage(2, "goodbye"), DisconnectMessage), recording)
            }
        }
        val records = buildList {
            DemoReader(ByteArrayInputStream(output.toByteArray())).use { reader ->
                while (true) when (val next = reader.read()) {
                    is DemoReader.Record.Body -> add(next.bytes)
                    DemoReader.Record.End -> break
                }
            }
        }
        val savedFrameBodies = records.filter { raw ->
            (MessageBodyCodec.decodeServerBody(raw) as MessageDecodeResult.Decoded).messages.any { it is FrameHeaderMessage }
        }
        assertEquals(capturedBodies, savedFrameBodies, "Channel headers must be stripped without changing payload bytes")
        Client().use { replay ->
            DemoPlayback(DemoReader(ByteArrayInputStream(output.toByteArray()))).use { playback ->
                val dispatch: (List<ServerMessage>) -> Boolean = { replay.apply(it); true }
                var running = playback.update(0f, dispatch)
                repeat(10) { if (running) running = playback.update(.1f, dispatch) }
                assertFalse(running)
            }
            assertEquals(listOf(80f, 96f, 144f, 160f), replay.positions)
            assertEquals(listOf("goodbye"), replay.prints)
            assertTrue(replay.disconnected)
            assertEquals("second", replay.config.getConfigValue(0))
        }
    }
}
