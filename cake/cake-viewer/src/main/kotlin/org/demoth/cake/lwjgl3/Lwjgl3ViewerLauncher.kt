package org.demoth.cake.lwjgl3

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter
import com.badlogic.gdx.graphics.glutils.HdpiMode
import org.demoth.cake.viewer.CakeViewer

object Lwjgl3ViewerLauncher {
    @JvmStatic
    fun main(args: Array<String>) {
        if (startNewJvmIfRequired(args)) return  // This handles macOS support and helps on Windows.

        createApplication(args)
    }
}

private fun createApplication(args: Array<String>): Lwjgl3Application {
    val viewer = CakeViewer(args)
    val configuration = createConfiguration().apply {
        setWindowListener(object : Lwjgl3WindowAdapter() {
            override fun filesDropped(files: Array<String>) {
                files.firstOrNull()?.let { path -> Gdx.app.postRunnable { viewer.openFile(path) } }
            }
        })
    }
    return Lwjgl3Application(viewer, configuration)
}

private fun createConfiguration() = Lwjgl3ApplicationConfiguration().apply {
    setTitle("Cake Viewer 1.2.4")
    setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL32, 3, 2)
    useVsync(true)
    //// Limits FPS to the refresh rate of the currently active monitor.
    setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate)
    // increase depth buffer precision 16 -> 24 to avoid polygon flickering
    setBackBufferConfig(8, 8, 8, 8, 24, 0, 4)
    //// If you remove the above line and set Vsync to false, you can get unlimited FPS, which can be
    //// useful for testing performance, but can also be very stressful to some hardware.
    //// You may also need to configure GPU drivers to fully disable Vsync; this can cause screen tearing.
    setWindowedMode(1024, 768)
    setHdpiMode(HdpiMode.Pixels)
    setWindowIcon("libgdx128.png", "libgdx64.png", "libgdx32.png", "libgdx16.png")
//     setFullscreenMode(Lwjgl3ApplicationConfiguration.getDisplayMode())

}
