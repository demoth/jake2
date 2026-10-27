package org.demoth.cake.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.utils.GdxNativesLoader
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SavePreviewTest {
    @Test
    fun `scene capture becomes an upright bounded PNG`() {
        GdxNativesLoader.load()
        val scene = Pixmap(640, 480, Pixmap.Format.RGB888)
        try {
            scene.setColor(Color.RED); scene.fillRectangle(0, 0, 640, 240)
            scene.setColor(Color.BLUE); scene.fillRectangle(0, 240, 640, 240)
            val png = encodeSavePreview(scene)
            val decoded = Pixmap(png, 0, png.size)
            try {
                assertEquals(320, decoded.width)
                assertEquals(240, decoded.height)
                assertEquals(Color.rgba8888(Color.BLUE), decoded.getPixel(10, 10))
                assertEquals(Color.rgba8888(Color.RED), decoded.getPixel(10, 230))
                assertEquals(640, scene.width, "Encoding borrows the source")
            } finally { decoded.dispose() }
        } finally { scene.dispose() }
    }

    @Test
    fun `preview preserves aspect ratio and does not upscale small captures`() {
        GdxNativesLoader.load()
        for ((source, expected) in listOf((640 to 360) to (320 to 180), (480 to 640) to (180 to 240),
            (32 to 24) to (32 to 24))) {
            val scene = Pixmap(source.first, source.second, Pixmap.Format.RGB888)
            try {
                val png = encodeSavePreview(scene)
                val decoded = Pixmap(png, 0, png.size)
                try { assertEquals(expected, decoded.width to decoded.height) } finally { decoded.dispose() }
            } finally { scene.dispose() }
        }
    }
}
