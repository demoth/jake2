package org.demoth.cake.lwjgl3

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter
import com.badlogic.gdx.graphics.glutils.HdpiMode
import org.demoth.cake.viewer.CakeViewer
import kotlin.system.exitProcess

object Lwjgl3ViewerLauncher {
    @JvmStatic
    fun main(args: Array<String>) {
        val options = try {
            ViewerOptions.parse(args)
        } catch (error: Exception) {
            System.err.println(error.message)
            System.err.println(ViewerOptions.USAGE)
            exitProcess(2)
        }
        if (options.help) {
            println(ViewerOptions.USAGE)
            return
        }
        if (startNewJvmIfRequired(args)) return  // This handles macOS support and helps on Windows.

        try {
            createApplication(options)
        } catch (error: Exception) {
            System.err.println("Cake Viewer failed: ${error.message}")
            error.printStackTrace()
            exitProcess(1)
        }
    }
}

private fun createApplication(options: ViewerOptions): Lwjgl3Application {
    val viewer = CakeViewer(options.input?.let { arrayOf(it) } ?: emptyArray(), options.capture)
    val configuration = createConfiguration(options).apply {
        setWindowListener(object : Lwjgl3WindowAdapter() {
            override fun filesDropped(files: Array<String>) {
                files.firstOrNull()?.let { path -> Gdx.app.postRunnable { viewer.openFile(path) } }
            }
        })
    }
    return Lwjgl3Application(viewer, configuration)
}

private fun createConfiguration(options: ViewerOptions) = Lwjgl3ApplicationConfiguration().apply {
    setTitle("Cake Viewer 1.2.8")
    setCakeWindowIcons()
    setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL32, 3, 2)
    useVsync(true)
    //// Limits FPS to the refresh rate of the currently active monitor.
    setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate)
    // increase depth buffer precision 16 -> 24 to avoid polygon flickering
    setBackBufferConfig(8, 8, 8, 8, 24, 0, 4)
    //// If you remove the above line and set Vsync to false, you can get unlimited FPS, which can be
    //// useful for testing performance, but can also be very stressful to some hardware.
    //// You may also need to configure GPU drivers to fully disable Vsync; this can cause screen tearing.
    setWindowedMode(options.width, options.height)
    if (options.capture != null) {
        setInitialVisible(false)
        disableAudio(true)
    }
    setHdpiMode(HdpiMode.Pixels)
//     setFullscreenMode(Lwjgl3ApplicationConfiguration.getDisplayMode())

}
