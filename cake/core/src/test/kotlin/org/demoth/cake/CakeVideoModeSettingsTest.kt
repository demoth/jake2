package org.demoth.cake

import jake2.qcommon.Defines.CVAR_ARCHIVE
import jake2.qcommon.Defines.CVAR_LATCH
import jake2.qcommon.Defines.CVAR_OPTIONS
import jake2.qcommon.exec.Cvar
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

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
        cvars.Get("vid_width", "1024", 0)
        cvars.Get("vid_height", "768", 0)
        cvars.Get("vid_vsync", "1", 0)

        val settings = CakeVideoModeSettings.fromCvars(cvars)

        assertEquals(CakeHdpiMode.LOGICAL, settings.hdpiMode)
    }

    @Test
    fun fallsBackToPixelsForUnknownHidpiMode() {
        val cvars = Cvar.getInstance()
        cvars.Get("vid_fullscreen", "0", 0)
        cvars.Get("vid_hidpi", "unknown", 0)
        cvars.Get("vid_width", "1024", 0)
        cvars.Get("vid_height", "768", 0)
        cvars.Get("vid_vsync", "1", 0)

        val settings = CakeVideoModeSettings.fromCvars(cvars)

        assertEquals(CakeHdpiMode.PIXELS, settings.hdpiMode)
    }
}
