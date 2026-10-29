package org.demoth.cake.demo

import jake2.qcommon.vfs.VfsOpenOptions
import jake2.qcommon.vfs.VfsWriteOptions
import jake2.qcommon.vfs.WritableFileSystem
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Profile-local recordings take precedence over installed/archived demos with the same logical name. */
internal class DemoFiles(
    private val writable: () -> WritableFileSystem,
    private val openContent: (String) -> InputStream?,
) {
    data class Output(val path: String, val stream: OutputStream)

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
