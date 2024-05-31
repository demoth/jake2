package org.demoth.cake.modelviewer

import jake2.qcommon.filesystem.PCX
import java.io.File
import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

// store palette in file in binary form
fun writePaletteFile() {
    val colorMapPCX = PCX(File("quake2/baseq2/pics/colormap.pcx").readBytes())
    ObjectOutputStream(File("assets/q2palette.bin").outputStream()).use {
        it.writeObject(colorMapPCX.colors)
    }
}

fun readPaletteFile(input: InputStream): IntArray {
    ObjectInputStream(input).use {
        return it.readObject() as IntArray
    }
}

fun main() {
    writePaletteFile()
    readPaletteFile(File("assets/q2palette.bin").inputStream())
}