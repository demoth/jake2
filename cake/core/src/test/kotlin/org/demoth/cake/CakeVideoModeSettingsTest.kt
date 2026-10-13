package org.demoth.cake

import com.badlogic.gdx.graphics.glutils.HdpiMode
import jake2.qcommon.Defines.CVAR_ARCHIVE
import jake2.qcommon.Defines.CVAR_LATCH
import jake2.qcommon.Defines.CVAR_OPTIONS
import jake2.qcommon.exec.Cvar
import jake2.qcommon.exec.Cbuf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** Checks Cake video definitions, settings/choice consumers and pending selections through config reload. */
class CakeVideoModeSettingsTest {
    private val cvars = Cvar()

    @Test
    fun registersVidHidpiAsArchivedLatchedVideoOption() {
        CakeCvars.registerVideoMode(cvars)

        val hidpi = cvars.FindVar("vid_hidpi")

        assertNotNull(hidpi)
        assertEquals("Pixels", hidpi?.string)
        assertEquals(CVAR_ARCHIVE or CVAR_OPTIONS or CVAR_LATCH, hidpi?.flags)
    }

    @Test
    fun parsesLogicalHidpiModeFromCvars() {
        cvars.Get("vid_hidpi", "Logical", 0)

        CakeCvars.registerVideoMode(cvars)
        val settings = CakeVideoModeSettings.fromCvars(cvars)

        assertEquals(HdpiMode.Logical, settings.hdpiMode)
    }

    @Test
    fun firstRegistrationRepairsUnknownHidpiModeToPixels() {
        cvars.Get("vid_hidpi", "unknown", 0)

        CakeCvars.registerVideoMode(cvars)
        val settings = CakeVideoModeSettings.fromCvars(cvars)

        assertEquals(HdpiMode.Pixels, settings.hdpiMode)
    }

    @Test
    fun invalidDimensionsAndBufferValuesUseDefaults() {
        cvars.Set("vid_windowed_width", "-1")
        cvars.Set("vid_windowed_height", "invalid")
        cvars.Set("vid_depthbits", "0")
        cvars.Set("vid_samples", "-2")
        CakeCvars.registerVideoMode(cvars)

        val settings = CakeVideoModeSettings.fromCvars(cvars)
        assertEquals(1024, settings.width)
        assertEquals(768, settings.height)
        assertEquals(24, settings.depthBits)
        assertEquals(4, settings.samples)
    }

    @Test
    fun resizingPreservesExplicitPendingDimensionsAndArchivesThem(@TempDir directory: Path) {
        CakeCvars.registerVideoMode(cvars)
        cvars.Set("vid_windowed_width", "1600")
        rememberWindowedSize(1200, 800, fullscreen = false, cvars = cvars)
        assertEquals(1200, cvars.FindVar("vid_windowed_width").asInt())
        assertEquals(1600, cvars.FindVar("vid_windowed_width").pendingIntValue)
        assertEquals(800, cvars.FindVar("vid_windowed_height").asInt())
        assertNull(cvars.FindVar("vid_windowed_height").pendingIntValue)

        val config = directory.resolve("config.cfg")
        cvars.writeArchiveVariables(config.toString())
        val text = Files.readString(config)
        assertTrue(text.contains("set vid_windowed_width \"1600\""))
        assertTrue(text.contains("set vid_windowed_height \"800\""))
        assertFalse(text.contains("set vid_fullscreen_modes"))
        cvars.updateLatchedVars()
        assertEquals(1600, cvars.FindVar("vid_windowed_width").asInt())
        assertNull(cvars.FindVar("vid_windowed_width").pendingIntValue)
    }

    @Test
    fun resizingToPendingDimensionsSatisfiesTheQueuedChange() {
        CakeCvars.registerVideoMode(cvars)
        cvars.Set("vid_windowed_width", "1600")
        rememberWindowedSize(1600, 900, fullscreen = false, cvars = cvars)
        assertEquals(1600, cvars.FindVar("vid_windowed_width").asInt())
        assertNull(cvars.FindVar("vid_windowed_width").rawPendingString)
        assertNull(cvars.FindVar("vid_windowed_width").pendingIntValue)
    }

    @Test
    fun fullscreenAndZeroSizedResizesDoNotReplaceWindowDimensions() {
        CakeCvars.registerVideoMode(cvars)
        rememberWindowedSize(3840, 2160, fullscreen = true, cvars = cvars)
        rememberWindowedSize(0, 0, fullscreen = false, cvars = cvars)
        assertEquals(1024, CakeVideoModeSettings.fromCvars(cvars).width)
        assertEquals(768, CakeVideoModeSettings.fromCvars(cvars).height)
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
        CakeCvars.registerVideoMode(cvars)
        publishFullscreenModes(listOf(CakeDisplayMode(1920, 1080, 144), CakeDisplayMode(1920, 1080, 144)), cvars)
        cvars.Set("vid_fullscreen_modes", "fake")
        assertEquals("1920x1080@144", cvars.VariableString("vid_fullscreen_modes"))
        assertFalse(cvars.listByPrefixAndFlags("vid_", CVAR_OPTIONS).any { it.name == "vid_fullscreen_modes" })
    }

    @Test
    fun invalidEnumEditsPreserveActiveAndPendingValues() {
        CakeCvars.registerVideoMode(cvars)
        val hidpi = cvars.FindVar("vid_hidpi")
        assertTrue(cvars.setResult(hidpi.name, HdpiMode.Logical.name).accepted)
        assertFalse(cvars.setResult(hidpi.name, "logical").accepted)
        assertFalse(cvars.setResult(hidpi.name, "unknown").accepted)
        assertEquals(HdpiMode.Pixels, hidpi.asEnum(HdpiMode::class.java))
        assertEquals(HdpiMode.Logical, hidpi.getPendingEnumValue(HdpiMode::class.java))
        assertEquals(HdpiMode.values().map { it.name }, videoOptionChoices("vid_hidpi", cvars))
        cvars.updateLatchedVars()
        assertEquals(HdpiMode.Logical, CakeVideoModeSettings.fromCvars(cvars).hdpiMode)
    }

    @Test
    fun dynamicSelectionWorksBeforeEnumerationAndRetainsUnavailableModes() {
        CakeCvars.registerVideoMode(cvars)
        val selection = cvars.FindVar("vid_fullscreen_mode")
        assertEquals(listOf("desktop"), videoOptionChoices(selection.name, cvars))
        assertTrue(cvars.setResult(selection.name, "1920x1080@144").accepted)
        for (invalid in listOf("", "1920x1080", "0x1080@144", "Desktop", "1920x1080@-1")) {
            assertFalse(cvars.setResult(selection.name, invalid).accepted, invalid)
        }
        publishFullscreenModes(listOf(CakeDisplayMode(2560, 1440, 60)), cvars)
        assertEquals("2560x1440@60", cvars.FindVar("vid_fullscreen_modes").asString())
        assertEquals(listOf("desktop", "2560x1440@60"), videoOptionChoices(selection.name, cvars))
        assertEquals("1920x1080@144", selection.pendingStringValue)
        cvars.updateLatchedVars()
        assertEquals("1920x1080@144", CakeVideoModeSettings.fromCvars(cvars).fullscreenMode)
    }

    @Test
    fun fullscreenSuggestionsUseTheirOwnRegistry() {
        val first = Cvar()
        val second = Cvar()
        CakeCvars.registerVideoMode(first)
        CakeCvars.registerVideoMode(second)
        publishFullscreenModes(listOf(CakeDisplayMode(1920, 1080, 60)), first)
        publishFullscreenModes(listOf(CakeDisplayMode(2560, 1440, 144)), second)
        assertEquals(listOf("desktop", "1920x1080@60"), videoOptionChoices("vid_fullscreen_mode", first))
        assertEquals(listOf("desktop", "2560x1440@144"), videoOptionChoices("vid_fullscreen_mode", second))
    }

    @Test
    fun pendingSelectionsRoundTripThroughConfigBeforeMonitorEnumeration(@TempDir directory: Path) {
        // The registered console `set` command writes to the singleton registry.
        val cvars = Cvar.getInstance()
        cvars.clear()
        try {
            CakeCvars.registerVideoMode(cvars)
            cvars.setResult("vid_hidpi", HdpiMode.Logical.name)
            cvars.setResult("vid_fullscreen_mode", "1920x1080@144")
            val config = directory.resolve("config.cfg")
            cvars.writeArchiveVariables(config.toString())
            val script = Files.readString(config)
            cvars.clear()
            Cvar.Init()
            CakeCvars.registerVideoMode(cvars)
            Cbuf.AddAndExecuteScript(script)
            assertEquals(HdpiMode.Pixels, CakeVideoModeSettings.fromCvars(cvars).hdpiMode)
            assertEquals("Logical", cvars.FindVar("vid_hidpi").rawPendingString)
            assertEquals("1920x1080@144", cvars.FindVar("vid_fullscreen_mode").rawPendingString)
            cvars.updateLatchedVars()
            val settings = CakeVideoModeSettings.fromCvars(cvars)
            assertEquals(HdpiMode.Logical, settings.hdpiMode)
            assertEquals("1920x1080@144", settings.fullscreenMode)
        } finally {
            cvars.clear()
        }
    }
}
