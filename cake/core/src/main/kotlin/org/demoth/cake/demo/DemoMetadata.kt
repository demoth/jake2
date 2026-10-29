package org.demoth.cake.demo

import jake2.qcommon.Defines
import jake2.qcommon.network.demo.DemoMessageDecoder
import jake2.qcommon.network.demo.DemoReader
import jake2.qcommon.network.messages.server.*
import jake2.qcommon.network.protocol.MessageDecodeResult
import java.io.IOException
import java.io.InputStream

/** Startup information only; reading metadata neither loads game assets nor scans the full recording. */
data class DemoMetadata(val protocol: Int, val gameDirectory: String, val levelName: String, val mapPath: String?) {
    companion object {
        const val MAX_STARTUP_RECORDS = 64

        /** Owns and closes [input] on success, rejection or reaching the bounded startup budget. */
        fun read(input: InputStream): DemoMetadata = DemoReader(input).use { reader ->
            val decoder = DemoMessageDecoder()
            var server: ServerDataMessage? = null
            var map: String? = null
            fun result(): DemoMetadata {
                val data = server ?: throw IOException("Demo has no server data in its startup records")
                return DemoMetadata(data.protocol, data.gameName.ifEmpty { "baseq2" }, data.levelString, map)
            }
            repeat(MAX_STARTUP_RECORDS) {
                when (val record = reader.read()) {
                    DemoReader.Record.End -> return result()
                    is DemoReader.Record.Body -> {
                        val messages = when (val decoded = decoder.decode(record.bytes)) {
                            is MessageDecodeResult.Decoded -> decoded.messages
                            is MessageDecodeResult.Rejected -> throw IOException(decoded.detail)
                        }
                        for (message in messages) when (message) {
                            is ServerDataMessage -> server = message
                            is ConfigStringMessage -> if (message.index == Defines.CS_MODELS + 1) map = message.config
                            else -> Unit
                        }
                        if (server != null && (map != null || messages.any { it is FrameHeaderMessage || it is StuffTextMessage }))
                            return result()
                    }
                }
            }
            result()
        }
    }
}
