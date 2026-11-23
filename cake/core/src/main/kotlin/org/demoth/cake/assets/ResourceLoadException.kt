package org.demoth.cake.assets

import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.GdxRuntimeException
import jake2.qcommon.filesystem.ResourceFormatException
import java.io.IOException
import java.nio.BufferUnderflowException

/** Reports a known read or format failure with its resource path and any underlying cause. */
internal class ResourceLoadException(val path: String, detail: String, cause: Throwable? = null) :
    RuntimeException("Could not load resource '$path': $detail", cause)

internal fun Throwable.findResourceFailure(): RuntimeException? =
    generateSequence(this) { it.cause }.filterIsInstance<RuntimeException>().firstOrNull {
        it is MissingResourceException || it is ResourceLoadException
    }

internal inline fun <T> decodeResource(path: String, decode: () -> T): T = try {
    readResource(path, decode)
} catch (error: ResourceFormatException) {
    throw ResourceLoadException(path, error.message ?: "Invalid format", error)
} catch (error: BufferUnderflowException) {
    throw ResourceLoadException(path, "Truncated data", error)
}

internal inline fun <T> readResource(path: String, read: () -> T): T = try {
    read()
} catch (error: IOException) {
    throw ResourceLoadException(path, error.message ?: "Read failed", error)
} catch (error: GdxRuntimeException) {
    val io = generateSequence(error.cause) { it.cause }.filterIsInstance<IOException>().firstOrNull() ?: throw error
    throw ResourceLoadException(path, io.message ?: "Read failed", error)
}

internal fun FileHandle.readResourceBytes(path: String): ByteArray = readResource(path) { readBytes() }
