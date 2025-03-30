package org.demoth.cake.lwjgl3

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import org.demoth.cake.modelviewer.CakeModelViewer

object Lwjgl3ModelViewerLauncher {
    @JvmStatic
    fun main(args: Array<String>) {
        if (startNewJvmIfRequired()) return  // This handles macOS support and helps on Windows.

        createApplication(args)
    }
}

private fun createApplication(args: Array<String>): Lwjgl3Application {
    return Lwjgl3Application(CakeModelViewer(args), createConfiguration())
}

private fun createConfiguration() = Lwjgl3ApplicationConfiguration().apply {
    setTitle("Cake Model Viewer 1.3")
    useVsync(true)
    //// Limits FPS to the refresh rate of the currently active monitor.
    setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate)
    // increase depth buffer precision 16 -> 24 to avoid polygon flickering
    setBackBufferConfig(8, 8, 8, 8, 24, 0, 0)
    //// If you remove the above line and set Vsync to false, you can get unlimited FPS, which can be
    //// useful for testing performance, but can also be very stressful to some hardware.
    //// You may also need to configure GPU drivers to fully disable Vsync; this can cause screen tearing.
    setWindowedMode(1280, 720)
    setWindowIcon("icons/logo.png", "icons/logo-32.png")
}
