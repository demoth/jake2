package org.demoth.cake

import com.badlogic.gdx.files.FileHandle
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * This extension of `FileHandle` was required because `Audio.newSound(...)` requires a File, which is not always possible:
 * For example, when loading a sound from an archive.
 */
class ByteArrayFileHandle(data: ByteArray, val fileName: String) : FileHandle() {
    val stream = ByteArrayInputStream(data)

    override fun read(): InputStream? {
        return stream
    }

    override fun read(bufferSize: Int): BufferedInputStream? {
        return BufferedInputStream(stream, bufferSize)
    }

    override fun extension(): String? {
        return fileName.substringAfterLast('.', "")
    }

    override fun nameWithoutExtension(): String? {
        return fileName.substringBeforeLast('.')
    }

    override fun toString(): String {
        return fileName
    }

    override fun exists(): Boolean {
        return true
    }
}