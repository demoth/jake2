package org.demoth.cake.demo

import jake2.qcommon.vfs.DefaultWritableFileSystem
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class DemoFilesTest {
    @TempDir lateinit var root: Path

    @Test fun `recordings are profile local replayable by name and never overwrite earlier takes`() {
        var profile = "first"
        val files = DemoFiles({ DefaultWritableFileSystem(root.resolve(profile)) }, { null })
        files.create("series/take").let { output ->
            assertEquals(root.resolve("first/demos/series/take.dm2").toString(), output.path)
            output.stream.use { it.write(byteArrayOf(1, 2, 3)) }
        }
        files.open("series/take").use { assertArrayEquals(byteArrayOf(1, 2, 3), it.readBytes()) }
        assertThrows(IOException::class.java) { files.create("series/take") }
        files.open("series/take").use { assertArrayEquals(byteArrayOf(1, 2, 3), it.readBytes()) }
        profile = "second"
        assertThrows(IOException::class.java) { files.open("series/take") }
        files.create("series/take").stream.use { it.write(4) }
        files.open("series/take").use { assertArrayEquals(byteArrayOf(4), it.readBytes()) }
    }

    @Test fun `local demo wins over installed content and missing local demo falls back to content`() {
        val fallbackPaths = mutableListOf<String>()
        val files = DemoFiles({ DefaultWritableFileSystem(root) }, { path ->
            fallbackPaths += path
            if (path == "demos/installed.dm2") ByteArrayInputStream(byteArrayOf(7)) else null
        })
        files.open("installed").use { assertEquals(7, it.read()) }
        assertEquals(listOf("demos/installed.dm2"), fallbackPaths)
        files.create("installed").stream.use { it.write(8) }
        files.open("installed").use { assertEquals(8, it.read()) }
        assertEquals(1, fallbackPaths.size)
        assertThrows(IOException::class.java) { files.open("missing") }
    }

    @Test fun `invalid paths do not reach the filesystem or content lookup`() {
        val files = DemoFiles({ error("Unexpected write filesystem access") }, { error("Unexpected content lookup") })
        for (path in listOf("", "../escape", "/absolute", "demos/../../escape", "C:\\demo")) {
            assertThrows(IOException::class.java) { files.create(path) }
            assertThrows(IOException::class.java) { files.open(path) }
        }
        assertEquals(0L, Files.list(root).use { it.count() })
    }
}
