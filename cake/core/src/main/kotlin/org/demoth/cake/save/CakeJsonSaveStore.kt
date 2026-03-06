package org.demoth.cake.save

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jake2.qcommon.vfs.DefaultWritableFileSystem
import jake2.qcommon.vfs.VfsOpenOptions
import jake2.qcommon.vfs.VfsWriteOptions
import jake2.qcommon.vfs.WritableFileSystem
import java.io.IOException
import java.nio.file.Path

/**
 * JSON save metadata persistence for Cake.
 *
 * Files are written under writable VFS root:
 * `save/<slot>/cake-save.json`.
 */
class CakeJsonSaveStore(
    private val writableFactory: (String) -> WritableFileSystem = { profileId ->
        val home = Path.of(System.getProperty("user.home"))
        DefaultWritableFileSystem(home.resolve(".cake").resolve(profileId))
    },
    private val mapper: ObjectMapper = jacksonObjectMapper(),
) {

    fun write(slot: String, profileId: String, snapshot: CakeSaveSnapshot): String {
        val normalizedSlot = normalizeSlot(slot)
        val logicalPath = "save/$normalizedSlot/cake-save.json"
        val writable = writableFactory(normalizeProfileId(profileId))

        val opened = writable.openWrite(logicalPath, VfsWriteOptions.TRUNCATE)
        if (!opened.success || opened.value == null) {
            throw IOException(opened.error ?: "Failed to open save file for write: $logicalPath")
        }

        opened.value.use { handle ->
            handle.outputStream().use { output ->
                mapper.writeValue(output, snapshot)
            }
        }
        return writable.resolveWritePath(logicalPath)
    }

    fun read(slot: String, profileId: String): CakeSaveSnapshot? {
        val normalizedSlot = normalizeSlot(slot)
        val logicalPath = "save/$normalizedSlot/cake-save.json"
        val writable = writableFactory(normalizeProfileId(profileId))

        val opened = writable.openReadReal(logicalPath, VfsOpenOptions.DEFAULT)
        if (!opened.success || opened.value == null) {
            return null
        }

        opened.value.use { handle ->
            handle.inputStream().use { input ->
                return mapper.readValue(input)
            }
        }
    }

    private fun normalizeSlot(slot: String): String {
        val value = slot.trim().replace('\\', '/')
        require(value.isNotEmpty()) { "Save slot must not be empty" }
        require(!value.contains("..")) { "Save slot must not contain '..'" }
        require(!value.contains("/")) { "Save slot must be a simple name" }
        require(!value.contains(":")) { "Save slot must not contain ':'" }
        return value
    }

    private fun normalizeProfileId(profileId: String): String {
        val value = profileId.trim()
        require(value.isNotEmpty()) { "Profile id must not be empty" }
        require(value.all { it.isLetterOrDigit() }) { "Profile id must be alphanumeric" }
        return value
    }
}

data class CakeSaveSnapshot(
    val map: String,
    val title: String,
    val timestampMillis: Long,
    val autosave: Boolean,
)
