package org.demoth.cake.lwjgl3

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Files.FileType
import com.badlogic.gdx.Graphics
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics
import org.demoth.cake.Cake
import org.demoth.cake.CakeDisplayMode
import org.demoth.cake.CakeStartupBootstrap
import org.demoth.cake.CakeVideoModeSettings
import org.demoth.cake.CakeVideoPlatform
import org.demoth.cake.publishFullscreenModes
import org.demoth.cake.resolveFullscreenMode
import org.lwjgl.glfw.GLFW
import org.demoth.cake.hosting.CakeHost

/** Launches the desktop (LWJGL3) application. */
object CakeDesktopLauncher {
    @JvmStatic
    fun launch(args: Array<String>, host: CakeHost, windowTitle: String) {
        if (startNewJvmIfRequired(args)) return
        val startupContext = CakeStartupBootstrap.bootstrap(arguments = args.toList())
        val lifecycle = ExitOnFailureListener { Cake(startupContext, Lwjgl3VideoPlatform(), host) }
        try {
            Lwjgl3Application(lifecycle, getDefaultConfiguration(startupContext.videoMode, windowTitle))
        } catch (error: Exception) {
            val failure = lifecycle.failure ?: throw error
            if (failure !== error) failure.addSuppressed(error)
        }
        lifecycle.failure?.let { throw it }
    }

    private fun getDefaultConfiguration(videoMode: CakeVideoModeSettings, windowTitle: String): Lwjgl3ApplicationConfiguration {
        val desktop = Lwjgl3ApplicationConfiguration.getDisplayMode()
        val modes = Lwjgl3ApplicationConfiguration.getDisplayModes().toList()
        publishFullscreenModes(modes.map { it.toCakeMode() })
        val selected = if (videoMode.fullscreen) {
            resolveFullscreenMode(videoMode.fullscreenMode, desktop.toCakeMode(), modes.map { it.toCakeMode() })
        } else desktop.toCakeMode()
        val displayMode = modes.firstOrNull { it.toCakeMode() == selected && it.bitsPerPixel == desktop.bitsPerPixel }
            ?: modes.firstOrNull { it.toCakeMode() == selected } ?: desktop
        return Lwjgl3ApplicationConfiguration().apply {
            setTitle(windowTitle)
            setCakeWindowIcons()
            setInitialVisible(!videoMode.hidden)
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
        }
    }

}

/** Wrapper class, lets LWJGL leave its loop normally so Application can release resources while the GL context is alive. */
private class ExitOnFailureListener(private val appCreation: () -> ApplicationListener) : ApplicationAdapter() {
    private var app: ApplicationListener? = null
    var failure: Exception? = null
        private set

    override fun create() = runUntilExit {
        val application = appCreation()
        app = application
        application.create()
    }
    override fun render() = runUntilExit { app?.render() }
    override fun resize(width: Int, height: Int) = runUntilExit { app?.resize(width, height) }
    override fun pause() = runUntilExit { app?.pause() }
    override fun resume() = runUntilExit { app?.resume() }

    override fun dispose() {
        try {
            app?.dispose()
        } catch (error: Exception) {
            val first = failure
            if (first == null) failure = error
            else if (first !== error) first.addSuppressed(error)
        }
    }

    private inline fun runUntilExit(action: () -> Unit) {
        if (failure != null) return
        try {
            action()
        } catch (error: Exception) {
            failure = error
            Gdx.app.exit()
        }
    }
}


/** Each executable supplies its own icons at the same classpath paths. */
fun Lwjgl3ApplicationConfiguration.setCakeWindowIcons() {
    setWindowIcon(FileType.Classpath,
        "icons/icon-256.png", "icons/icon-128.png", "icons/icon-64.png",
        "icons/icon-32.png", "icons/icon-16.png")
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
