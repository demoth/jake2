package org.demoth.cake.demo

import jake2.qcommon.Defines
import jake2.qcommon.network.demo.DemoWriter
import jake2.qcommon.network.messages.server.*
import jake2.qcommon.network.transport.PacketBytes
import jake2.qcommon.sizebuf_t

/** Owned startup records for a protocol-34 client recording (Yamagi CL_Record_f). */
class DemoBootstrap(
    serverData: ServerDataMessage,
    configStrings: Iterable<ConfigStringMessage>,
    baselines: Iterable<SpawnBaselineMessage>,
) {
    private val records: List<PacketBytes>

    init {
        require(serverData.protocol == Defines.PROTOCOL_VERSION) { "Unsupported recording protocol ${serverData.protocol}" }
        require(serverData.playerNumber in 0 until Defines.MAX_CLIENTS) { "Recording requires a player view" }
        val messages = buildList {
            add(serverData.copy(demo = true))
            addAll(configStrings.filter { it.config.isNotEmpty() }.sortedBy { it.index })
            addAll(baselines.sortedBy { it.entityState.index })
            add(StuffTextMessage("precache\n"))
        }
        val buffer = sizebuf_t(Defines.MAX_MSGLEN)
        records = buildList {
            for (message in messages) {
                require(message.size <= buffer.maxsize) { "Demo bootstrap message exceeds record capacity" }
                if (buffer.cursize + message.size > buffer.maxsize) {
                    add(PacketBytes.copyOf(buffer.data, buffer.cursize))
                    buffer.cursize = 0
                }
                message.writeTo(buffer)
            }
            if (buffer.cursize > 0) add(PacketBytes.copyOf(buffer.data, buffer.cursize))
        }
    }

    /** All records were encoded at construction; later source collection changes cannot affect them. */
    fun writeTo(writer: DemoWriter) { records.forEach(writer::write) }
}
