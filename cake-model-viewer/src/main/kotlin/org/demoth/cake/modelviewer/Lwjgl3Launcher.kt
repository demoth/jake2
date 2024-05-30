package org.demoth.cake.modelviewer

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration

object Lwjgl3Launcher {
    @JvmStatic
    fun main(args: Array<String>) {
        if (StartupHelper.startNewJvmIfRequired()) return  // This handles macOS support and helps on Windows.

        createApplication(args)
    }
}

private fun createApplication(args: Array<String>): Lwjgl3Application {
    return Lwjgl3Application(CakeModelViewer(args), defaultConfiguration)
}

private val defaultConfiguration: Lwjgl3ApplicationConfiguration
    get() {
        val configuration = Lwjgl3ApplicationConfiguration()
        configuration.setTitle("Cake Model Viewer 1.2")
        configuration.useVsync(true)
        //// Limits FPS to the refresh rate of the currently active monitor.
        configuration.setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate)
        // increase depth buffer precision 16 -> 24 to avoid polygon flickering
        configuration.setBackBufferConfig(8, 8, 8, 8, 24, 0, 0)
        //// If you remove the above line and set Vsync to false, you can get unlimited FPS, which can be
        //// useful for testing performance, but can also be very stressful to some hardware.
        //// You may also need to configure GPU drivers to fully disable Vsync; this can cause screen tearing.
        configuration.setWindowedMode(1280, 720)
        configuration.setWindowIcon("icons/logo.png", "icons/logo-32.png")
        return configuration
    }
