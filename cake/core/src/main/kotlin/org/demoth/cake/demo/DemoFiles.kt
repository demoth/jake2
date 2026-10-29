package org.demoth.cake.demo

import jake2.qcommon.vfs.VfsOpenOptions
import jake2.qcommon.vfs.VfsWriteOptions
import jake2.qcommon.vfs.WritableFileSystem
import jake2.qcommon.vfs.VfsEntry
import jake2.qcommon.vfs.VfsLayer
import jake2.qcommon.vfs.VfsSource
import jake2.qcommon.vfs.VfsSourceType
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.Locale

/** Profile-local recordings take precedence over installed/archived demos with the same logical name. */
internal class DemoFiles(
    private val writable: () -> WritableFileSystem,
    private val openContent: (String) -> InputStream?,
) {
    data class Output(val path: String, val stream: OutputStream)

    /** Fresh recordings overlay the installed snapshot; no package extraction or media loading. */
    fun entries(installed: List<VfsEntry>, caseSensitive: Boolean = false): List<VfsEntry> {
        val root = Path.of(writable().writeRoot()).toAbsolutePath().normalize()
        val directory = root.resolve("demos")
        val merged = installed.associateByTo(linkedMapOf()) { it.normalizedPath }
        if (Files.isDirectory(directory)) Files.walk(directory).use { paths ->
            paths.filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) && it.fileName.toString().endsWith(".dm2", true) }
                .sorted().forEach { file ->
                    val display = root.relativize(file).toString().replace('\\', '/')
                    val logical = if (caseSensitive) display else display.lowercase(Locale.ROOT)
                    merged[logical] = VfsEntry(logical, display, VfsLayer.PROFILE_LOOSE, 0,
                        VfsSource(VfsSourceType.DIRECTORY, root, display, null, false, false),
                        Files.size(file), Files.getLastModifiedTime(file).toMillis())
                }
        }
        return merged.values.sortedBy { it.normalizedPath }
    }

    /** Open the selected winner exactly, including mixed-case saved names and demos outside demos/. */
    fun open(entry: VfsEntry): InputStream {
        if (entry.layer != VfsLayer.PROFILE_LOOSE)
            return openContent(entry.normalizedPath) ?: throw IOException("Demo not found: ${entry.displayPath}")
        val files = writable()
        val root = Path.of(files.writeRoot()).toAbsolutePath().normalize()
        if (entry.source.containerPath != root) throw IOException("Demo belongs to another profile")
        val handle = files.openReadReal(entry.displayPath, VfsOpenOptions.DEFAULT).value
            ?: throw IOException("Demo not found: ${entry.displayPath}")
        try {
            return object : FilterInputStream(handle.inputStream()) { override fun close() = handle.close() }
        } catch (failure: Exception) {
            try { handle.close() } catch (closing: Exception) { failure.addSuppressed(closing) }
            throw failure
        }
    }

    fun create(name: String): Output {
        val path = demoLogicalPath(name) ?: throw IOException("Invalid demo path: $name")
        val files = writable()
        // Recording names are explicit; don't silently overwrite a previous take.
        val opened = files.openWrite(path, VfsWriteOptions(false, false))
        val handle = opened.value ?: throw IOException(opened.error ?: "Could not create $path")
        try {
            val output = object : FilterOutputStream(handle.outputStream()) {
                override fun write(bytes: ByteArray, offset: Int, length: Int) = out.write(bytes, offset, length)
                override fun close() = handle.close()
            }
            return Output(files.resolveWritePath(path), output)
        } catch (failure: Exception) {
            try { handle.close() } catch (closing: Exception) { failure.addSuppressed(closing) }
            throw failure
        }
    }

    fun open(name: String): InputStream {
        val path = demoLogicalPath(name) ?: throw IOException("Invalid demo path: $name")
        val local = writable().openReadReal(path, VfsOpenOptions.DEFAULT).value
        if (local != null) {
            try {
                return object : FilterInputStream(local.inputStream()) { override fun close() = local.close() }
            } catch (failure: Exception) {
                try { local.close() } catch (closing: Exception) { failure.addSuppressed(closing) }
                throw failure
            }
        }
        return openContent(path) ?: throw IOException("Demo not found in active profile: $path")
    }
}
