package org.demoth.cake.lwjgl3

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.graphics.glutils.HdpiMode
import org.demoth.cake.Cake
import org.demoth.cake.CakeStartupBootstrap
import org.demoth.cake.CakeStartupContext
import org.demoth.cake.CakeVideoModeSettings

/** Launches the desktop (LWJGL3) application. */
object Lwjgl3GameLauncher {
    @JvmStatic
    fun main(args: Array<String>) {
        if (startNewJvmIfRequired()) return
        val startupContext = CakeStartupBootstrap.bootstrap()
        createApplication(startupContext)
    }

    private fun createApplication(startupContext: CakeStartupContext): Lwjgl3Application {
        return Lwjgl3Application(Cake(startupContext), getDefaultConfiguration(startupContext.videoMode))
    }

    private fun getDefaultConfiguration(videoMode: CakeVideoModeSettings): Lwjgl3ApplicationConfiguration {
        return Lwjgl3ApplicationConfiguration().apply {
            setTitle("Cake Engine v1.2.0")
            setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL32, 3, 2)
            setBackBufferConfig(
                /* r = */ 8,
                /* g = */ 8,
                /* b = */ 8,
                /* a = */ 8,
                /* depth = */ 24,
                /* stencil = */ 0,
                /* samples = */ 4 // anti-aliasing
            )

            useVsync(videoMode.vsync)
            setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate)
            setResizable(false)
            setHdpiMode(HdpiMode.Pixels)
            if (videoMode.fullscreen) {
                setFullscreenMode(Lwjgl3ApplicationConfiguration.getDisplayMode())
            } else {
                setWindowedMode(videoMode.width, videoMode.height)
            }
            setWindowIcon("libgdx128.png", "libgdx64.png", "libgdx32.png", "libgdx16.png")
        }
    }

}
