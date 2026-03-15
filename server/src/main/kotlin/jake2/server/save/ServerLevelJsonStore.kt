package jake2.server.save

import jake2.qcommon.CM
import jake2.qcommon.Defines
import jake2.qcommon.save.SaveJson
import jake2.qcommon.vfs.DefaultWritableFileSystem
import jake2.qcommon.vfs.VfsOpenOptions
import jake2.qcommon.vfs.VfsReadableHandle
import jake2.qcommon.vfs.VfsResult
import jake2.qcommon.vfs.VfsWritableHandle
import jake2.qcommon.vfs.VfsWriteOptions
import jake2.qcommon.vfs.WritableFileSystem
import java.io.IOException
import java.nio.file.Path
import java.util.Arrays

/**
 * JSON-backed persistence for per-level server state previously stored in `.sv2`.
 */
class ServerLevelJsonStore(private val writable: WritableFileSystem) {
    fun readLevelState(slot: String, mapName: String): ServerLevelStateSnapshot {
        return read(levelStatePath(slot, mapName), ServerLevelStateSnapshot::class.java)
    }

    fun writeLevelState(slot: String, mapName: String, configstrings: Array<String?>, portalOpen: BooleanArray) {
        write(
            levelStatePath(slot, mapName),
            ServerLevelStateSnapshot(
                schemaVersion = SCHEMA_VERSION,
                configstrings = configstrings.copyOf().toList(),
                portalOpen = portalOpen.copyOf()
            )
        )
    }

    fun applyLevelState(snapshot: ServerLevelStateSnapshot, configstrings: Array<String?>, cm: CM) {
        Arrays.fill(configstrings, null)
        val limit = minOf(snapshot.configstrings.size, Defines.MAX_CONFIGSTRINGS)
        for (i in 0 until limit) {
            configstrings[i] = snapshot.configstrings[i]
        }

        Arrays.fill(cm.portalopen, false)
        val portalLimit = minOf(snapshot.portalOpen.size, cm.portalopen.size)
        for (i in 0 until portalLimit) {
            cm.portalopen[i] = snapshot.portalOpen[i]
        }
        cm.FloodAreaConnections()
    }

    private fun <T> read(logicalPath: String, type: Class<T>): T {
        val opened: VfsResult<VfsReadableHandle> = writable.openReadReal(logicalPath, VfsOpenOptions.DEFAULT)
        if (!opened.success() || opened.value() == null) {
            throw IOException(opened.error() ?: "Failed to open $logicalPath")
        }
        opened.value().use { handle ->
            return SaveJson.read(handle.inputStream(), type)
        }
    }

    private fun write(logicalPath: String, value: Any) {
        val opened: VfsResult<VfsWritableHandle> = writable.openWrite(logicalPath, VfsWriteOptions.TRUNCATE)
        if (!opened.success() || opened.value() == null) {
            throw IOException(opened.error() ?: "Failed to open $logicalPath")
        }
        opened.value().use { handle ->
            SaveJson.write(handle.outputStream(), value)
        }
    }

    private fun levelStatePath(slot: String, mapName: String): String {
        return "save/${normalizeSegment(slot, "slot")}/${normalizeSegment(mapName, "mapName")}.sv2.json"
    }

    private fun normalizeSegment(value: String, label: String): String {
        val normalized = value.trim().replace('\\', '/')
        if (normalized.isEmpty() || normalized.contains("..") || normalized.contains("/") || normalized.contains(":")) {
            throw IllegalArgumentException("Invalid $label: $value")
        }
        return normalized
    }

    companion object {
        const val SCHEMA_VERSION: Int = 1

        @JvmStatic
        fun forWriteDir(writeDir: String): ServerLevelJsonStore =
            ServerLevelJsonStore(DefaultWritableFileSystem(Path.of(writeDir)))
    }
}

data class ServerLevelStateSnapshot(
    val schemaVersion: Int,
    val configstrings: List<String?>,
    val portalOpen: BooleanArray
)
