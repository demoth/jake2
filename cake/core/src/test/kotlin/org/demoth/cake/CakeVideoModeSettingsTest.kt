package org.demoth.cake

import com.badlogic.gdx.graphics.glutils.HdpiMode
import jake2.qcommon.Defines.CVAR_ARCHIVE
import jake2.qcommon.Defines.CVAR_LATCH
import jake2.qcommon.Defines.CVAR_OPTIONS
import jake2.qcommon.exec.Cvar
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class CakeVideoModeSettingsTest {

    @BeforeEach
    fun setUp() {
        Cvar.getInstance().clear()
    }

    @Test
    fun registersVidHidpiAsArchivedLatchedVideoOption() {
        CakeCvars.registerVideoMode()

        val hidpi = Cvar.getInstance().FindVar("vid_hidpi")

        assertNotNull(hidpi)
        assertEquals("Pixels", hidpi?.string)
        assertEquals(CVAR_ARCHIVE or CVAR_OPTIONS or CVAR_LATCH, hidpi?.flags)
    }

    @Test
    fun parsesLogicalHidpiModeFromCvars() {
        val cvars = Cvar.getInstance()
        cvars.Get("vid_fullscreen", "0", 0)
        cvars.Get("vid_hidpi", "logical", 0)
        cvars.Get("vid_windowed_width", "1024", 0)
        cvars.Get("vid_windowed_height", "768", 0)
        cvars.Get("vid_vsync", "1", 0)

        val settings = CakeVideoModeSettings.fromCvars(cvars)

        assertEquals(HdpiMode.Logical, settings.hdpiMode)
    }

    @Test
    fun fallsBackToPixelsForUnknownHidpiMode() {
        val cvars = Cvar.getInstance()
        cvars.Get("vid_fullscreen", "0", 0)
        cvars.Get("vid_hidpi", "unknown", 0)
        cvars.Get("vid_windowed_width", "1024", 0)
        cvars.Get("vid_windowed_height", "768", 0)
        cvars.Get("vid_vsync", "1", 0)

        val settings = CakeVideoModeSettings.fromCvars(cvars)

        assertEquals(HdpiMode.Pixels, settings.hdpiMode)
    }

    @Test
    fun invalidDimensionsAndBufferValuesUseDefaults() {
        val cvars = Cvar.getInstance()
        cvars.Set("vid_windowed_width", "-1")
        cvars.Set("vid_windowed_height", "invalid")
        cvars.Set("vid_depthbits", "0")
        cvars.Set("vid_samples", "-2")
        CakeCvars.registerVideoMode()

        val settings = CakeVideoModeSettings.fromCvars()
        assertEquals(1024, settings.width)
        assertEquals(768, settings.height)
        assertEquals(24, settings.depthBits)
        assertEquals(4, settings.samples)
    }

    @Test
    fun resizingPreservesExplicitPendingDimensionsAndArchivesThem(@TempDir directory: Path) {
        CakeCvars.registerVideoMode()
        val cvars = Cvar.getInstance()
        cvars.Set("vid_windowed_width", "1600")
        rememberWindowedSize(1200, 800, fullscreen = false)
        assertEquals("1200", cvars.VariableString("vid_windowed_width"))
        assertEquals("1600", cvars.FindVar("vid_windowed_width").latched_string)
        assertEquals("800", cvars.VariableString("vid_windowed_height"))
        assertNull(cvars.FindVar("vid_windowed_height").latched_string)

        val config = Files.createFile(directory.resolve("config.cfg"))
        cvars.writeArchiveVariables(config.toString())
        val text = Files.readString(config)
        assertTrue(text.contains("set vid_windowed_width \"1600\""))
        assertTrue(text.contains("set vid_windowed_height \"800\""))
        assertFalse(text.contains("set vid_fullscreen_modes"))
    }

    @Test
    fun fullscreenAndZeroSizedResizesDoNotReplaceWindowDimensions() {
        CakeCvars.registerVideoMode()
        rememberWindowedSize(3840, 2160, fullscreen = true)
        rememberWindowedSize(0, 0, fullscreen = false)
        assertEquals(1024, CakeVideoModeSettings.fromCvars().width)
        assertEquals(768, CakeVideoModeSettings.fromCvars().height)
    }

    @Test
    fun fullscreenSelectionIsIndependentOfEnumerationOrderAndFallsBackToDesktop() {
        val desktop = CakeDisplayMode(3840, 2160, 60)
        val selected = CakeDisplayMode(1920, 1080, 144)
        val modes = listOf(selected.copy(refreshRate = 60), desktop, selected)
        assertEquals(selected, resolveFullscreenMode(selected.id, desktop, modes.reversed()))
        assertEquals(desktop, resolveFullscreenMode("desktop", desktop, modes))
        assertEquals(desktop, resolveFullscreenMode("1920x1080@999", desktop, modes))
        assertEquals(desktop, resolveFullscreenMode("malformed", desktop, emptyList()))
    }

    @Test
    fun supportedModesAreReadOnlyAndNotEditableOptions() {
        CakeCvars.registerVideoMode()
        publishFullscreenModes(listOf(CakeDisplayMode(1920, 1080, 144), CakeDisplayMode(1920, 1080, 144)))
        val cvars = Cvar.getInstance()
        cvars.Set("vid_fullscreen_modes", "fake")
        assertEquals("1920x1080@144", cvars.VariableString("vid_fullscreen_modes"))
        assertFalse(cvars.listByPrefixAndFlags("vid_", CVAR_OPTIONS).any { it.name == "vid_fullscreen_modes" })
    }
}
