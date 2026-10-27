package org.demoth.cake.ui

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import java.io.ByteArrayOutputStream
import kotlin.math.min

/** Borrow a GL-origin scene image; return an upright PNG bounded to 320x240, without upscaling. */
fun encodeSavePreview(scene: Pixmap): ByteArray {
    val scale = min(1f, min(320f / scene.width, 240f / scene.height))
    val image = Pixmap((scene.width * scale).toInt().coerceAtLeast(1),
        (scene.height * scale).toInt().coerceAtLeast(1), Pixmap.Format.RGB888)
    val encoder = PixmapIO.PNG()
    try {
        image.setBlending(Pixmap.Blending.None)
        image.setFilter(Pixmap.Filter.BiLinear)
        image.drawPixmap(scene, 0, 0, scene.width, scene.height, 0, 0, image.width, image.height)
        encoder.setFlipY(true)
        return ByteArrayOutputStream().use { output -> encoder.write(output, image); output.toByteArray() }
    } finally {
        encoder.dispose()
        image.dispose()
    }
}
