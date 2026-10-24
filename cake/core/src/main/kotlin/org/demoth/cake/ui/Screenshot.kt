package org.demoth.cake.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import jake2.qcommon.Com

/** Called after drawing for automated captures; failures propagate so the process reports failure. */
fun writeScreenshot(file: FileHandle) {
    check(Gdx.graphics.backBufferWidth > 0 && Gdx.graphics.backBufferHeight > 0) {
        "Screenshot unavailable while the framebuffer is zero-sized"
    }
    val pixmap = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
    try {
        PixmapIO.writePNG(file, pixmap, -1, true)
        Com.Println("Screenshot saved: ${file.file().absolutePath}")
    } finally {
        pixmap.dispose()
    }
}
