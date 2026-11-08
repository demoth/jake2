package org.demoth.cake.lwjgl3

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Path

class ViewerOptionsTest {
    @Test
    fun `interactive launch keeps defaults and accepts a filename containing spaces`() {
        assertEquals(ViewerOptions(), ViewerOptions.parse(emptyArray()))
        assertEquals("a folder/model.md2", ViewerOptions.parse(arrayOf("a folder/model.md2")).input)
    }

    @Test
    fun `capture accepts options before or after input and normalizes output`() {
        for (args in listOf(
            arrayOf("model.md2", "--capture", "build/output image.png", "--size", "800x600"),
            arrayOf("--size", "800x600", "--capture", "build/output image.png", "model.md2"),
        )) {
            val options = ViewerOptions.parse(args)
            assertEquals("model.md2", options.input)
            assertEquals(Path.of("build/output image.png").toAbsolutePath().normalize(), options.capture)
            assertEquals(800, options.width)
            assertEquals(600, options.height)
        }
    }

    @Test
    fun `capture can render the empty viewer and help requires no file`() {
        assertNull(ViewerOptions.parse(arrayOf("--capture", "empty.png")).input)
        assertTrue(ViewerOptions.parse(arrayOf("--help")).help)
    }

    @Test
    fun `separator allows a filename that starts with a dash`() {
        assertEquals("--model.md2", ViewerOptions.parse(arrayOf("--", "--model.md2")).input)
    }

    @Test
    fun `invalid arguments fail rather than silently opening the wrong file`() {
        for (args in listOf(
            arrayOf("--capture"), arrayOf("--capture", "--size", "800x600"),
            arrayOf("--capture", "output.jpg"), arrayOf("--capture", "a.png", "--capture", "b.png"),
            arrayOf("--size"), arrayOf("--size", "0x600"), arrayOf("--size", "800x-2"),
            arrayOf("--size", "800x600x2"), arrayOf("--size", "huge"),
            arrayOf("--size", "999999999999x600"), arrayOf("--unknown"), arrayOf("a.md2", "b.md2"),
        )) assertThrows(Exception::class.java, { ViewerOptions.parse(args) }, args.joinToString(" "))
    }
}
