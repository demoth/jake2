package org.demoth.cake.lwjgl3

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Graphics
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics
import org.demoth.cake.Cake
import org.demoth.cake.CakeDisplayMode
import org.demoth.cake.CakeStartupBootstrap
import org.demoth.cake.CakeStartupContext
import org.demoth.cake.CakeVideoModeSettings
import org.demoth.cake.CakeVideoPlatform
import org.demoth.cake.publishFullscreenModes
import org.demoth.cake.resolveFullscreenMode
import org.lwjgl.glfw.GLFW
import org.demoth.cake.hosting.CakeHost

/** Launches the desktop (LWJGL3) application. */
object CakeDesktopLauncher {
    @JvmStatic
    fun launch(args: Array<String>, host: CakeHost) {
        if (startNewJvmIfRequired(args)) return
        val startupContext = CakeStartupBootstrap.bootstrap()
        createApplication(startupContext, host)
    }

    private fun createApplication(startupContext: CakeStartupContext, host: CakeHost): Lwjgl3Application {
        return Lwjgl3Application(Cake(startupContext, Lwjgl3VideoPlatform(), host), getDefaultConfiguration(startupContext.videoMode))
    }

    private fun getDefaultConfiguration(videoMode: CakeVideoModeSettings): Lwjgl3ApplicationConfiguration {
        val desktop = Lwjgl3ApplicationConfiguration.getDisplayMode()
        val modes = Lwjgl3ApplicationConfiguration.getDisplayModes().toList()
        publishFullscreenModes(modes.map { it.toCakeMode() })
        val selected = if (videoMode.fullscreen) {
            resolveFullscreenMode(videoMode.fullscreenMode, desktop.toCakeMode(), modes.map { it.toCakeMode() })
        } else desktop.toCakeMode()
        val displayMode = modes.firstOrNull { it.toCakeMode() == selected && it.bitsPerPixel == desktop.bitsPerPixel }
            ?: modes.firstOrNull { it.toCakeMode() == selected } ?: desktop
        return Lwjgl3ApplicationConfiguration().apply {
            setTitle("Cake Engine v1.2.3")
            setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL32, 3, 2)
            setBackBufferConfig(
                /* r = */ 8,
                /* g = */ 8,
                /* b = */ 8,
                /* a = */ 8,
                /* depth = */ videoMode.depthBits,
                /* stencil = */ 0,
                /* samples = */ videoMode.samples // Scene MSAA is configured separately for postprocessing.
            )

            useVsync(videoMode.vsync)
            setForegroundFPS(displayMode.refreshRate)
            setResizable(true)
            // The same thread drives a listen server even when its window is minimized/unfocused.
            setPauseWhenMinimized(false)
            setPauseWhenLostFocus(false)
            setIdleFPS(60)
            setHdpiMode(videoMode.hdpiMode)
            if (videoMode.fullscreen) {
                setFullscreenMode(displayMode)
            } else {
                setWindowedMode(videoMode.width, videoMode.height)
            }
            setWindowIcon("libgdx128.png", "libgdx64.png", "libgdx32.png", "libgdx16.png")
        }
    }

}


private fun Graphics.DisplayMode.toCakeMode() = CakeDisplayMode(width, height, refreshRate)

private class Lwjgl3VideoPlatform : CakeVideoPlatform {
    override fun windowSize(): Pair<Int, Int> {
        val width = IntArray(1)
        val height = IntArray(1)
        GLFW.glfwGetWindowSize((Gdx.graphics as Lwjgl3Graphics).window.windowHandle, width, height)
        return width[0] to height[0]
    }

    override fun describe(): String {
        val mode = Gdx.graphics.displayMode
        val windowMode = if (Gdx.graphics.isFullscreen) "fullscreen" else "windowed"
        return "Active display: ${Gdx.graphics.monitor.name}, ${mode.toCakeMode().id}, $windowMode"
    }
}
