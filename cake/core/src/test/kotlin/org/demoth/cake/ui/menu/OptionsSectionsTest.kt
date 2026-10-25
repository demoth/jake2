package org.demoth.cake.ui.menu

import jake2.qcommon.Defines.CVAR_LATCH
import jake2.qcommon.Defines.CVAR_NOSET
import jake2.qcommon.Defines.CVAR_OPTIONS
import jake2.qcommon.exec.Cvar
import jake2.qcommon.exec.CvarTypes
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OptionsSectionsTest {
    private val cvars = Cvar()

    @Test
    fun sectionsContainOnlyOptedInCanonicalCvarsAndKeepLiveReferences() {
        val volume = cvars.register("s_volume", CvarTypes.FLOAT, 0.7f, CVAR_OPTIONS)
        cvars.register("s_hidden", CvarTypes.BOOLEAN, false)
        cvars.register("name", CvarTypes.STRING, "player", CVAR_OPTIONS)
        cvars.AddAlias("s_loudness", "s_volume")

        val sections = currentOptionSections(cvars)
        assertEquals(DEFAULT_OPTIONS_SECTIONS.map { it.title to it.prefix }, sections.map { it.title to it.prefix })
        assertEquals(listOf("s_volume"), sections.flatMap { it.entries }.map { it.name })
        assertSame(volume, sections.first { it.prefix == "s_" }.entries.single())
        assertTrue(DEFAULT_OPTIONS_SECTIONS.all { it.entries.isEmpty() })
    }

    @Test
    fun editsIgnoreUnknownUnrelatedAndNonOptionCvars() {
        val volume = cvars.register("s_volume", CvarTypes.FLOAT, 0.7f, CVAR_OPTIONS)
        val hidden = cvars.register("s_hidden", CvarTypes.BOOLEAN, false)
        val video = cvars.register("vid_fullscreen", CvarTypes.BOOLEAN, false, CVAR_OPTIONS)

        val result = applyOptionEntries("s_", linkedMapOf(
            "s_volume" to 0.5f,
            "s_hidden" to true,
            "vid_fullscreen" to true,
            "s_unknown" to "value",
        ), cvars)

        assertEquals("Saved 1 option(s).", result)
        assertEquals(0.5f, volume.typedValue)
        assertFalse(hidden.typedValue)
        assertFalse(video.typedValue)
        assertNull(cvars.find("s_unknown"))
        // Ignored input must not be staged as a value for future registration.
        assertEquals("default", cvars.register("s_unknown", CvarTypes.STRING, "default").typedValue)
    }

    @Test
    fun invalidAndProtectedValuesReportDiagnosticsWhileValidEditsStillApply() {
        val count = cvars.register("r_count", CvarTypes.INTEGER, 1, CVAR_OPTIONS)
        val locked = cvars.register("r_locked", CvarTypes.INTEGER, 2, CVAR_OPTIONS or CVAR_NOSET)
        val toggle = cvars.register("r_toggle", CvarTypes.BOOLEAN, false, CVAR_OPTIONS)
        val result = applyOptionEntries("r_", linkedMapOf(
            "r_count" to "not-an-integer", "r_locked" to 3, "r_toggle" to true,
        ), cvars)

        assertTrue(result.startsWith("Saved 1 option(s).\n"))
        assertTrue(result.contains("r_count"))
        assertTrue(result.contains("r_locked"))
        assertEquals(1, count.typedValue)
        assertEquals(2, locked.typedValue)
        assertTrue(toggle.typedValue)
    }

    @Test
    fun stringAndNativeChangesReportPendingRestartWithoutApplyingLatchedValue() {
        val width = cvars.register("vid_width", CvarTypes.INTEGER, 640, CVAR_OPTIONS or CVAR_LATCH)
        val gamma = cvars.register("vid_gamma", CvarTypes.FLOAT, 1f, CVAR_OPTIONS)
        val result = applyOptionEntries("vid_", linkedMapOf("vid_width" to "800", "vid_gamma" to 1.2f), cvars)

        assertEquals("Saved 2 option(s). 1 pending restart.", result)
        assertEquals(640, width.typedValue)
        assertEquals("800", width.rawPendingString)
        assertEquals(1.2f, gamma.typedValue)
        assertEquals("Saved 0 option(s).", applyOptionEntries("vid_", emptyMap(), cvars))
    }
}
