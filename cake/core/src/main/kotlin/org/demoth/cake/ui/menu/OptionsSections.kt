package org.demoth.cake.ui.menu

import jake2.qcommon.Defines.CVAR_OPTIONS
import jake2.qcommon.exec.Cvar
import jake2.qcommon.exec.cvar_t

/** Menu grouping with shared live cvars; entry metadata is not copied into UI rows. */
data class OptionsSection(
    val title: String,
    val prefix: String,
    val entries: List<cvar_t<*>> = emptyList(),
)

val DEFAULT_OPTIONS_SECTIONS = listOf(
    OptionsSection(title = "Sound", prefix = "s_"),
    OptionsSection(title = "Video", prefix = "vid_"),
    OptionsSection(title = "Input", prefix = "in_"),
    OptionsSection(title = "Rendering", prefix = "r_"),
    OptionsSection(title = "Client", prefix = "cl_"),
)

/** Populate menu sections with live, opted-in cvars from the registry. */
fun currentOptionSections(cvars: Cvar = Cvar.getInstance()): List<OptionsSection> {
    return DEFAULT_OPTIONS_SECTIONS.map { section ->
        section.copy(entries = cvars.listByPrefixAndFlags(section.prefix, CVAR_OPTIONS))
    }
}

/** Apply submitted edits and describe the result; the caller persists profile configuration. */
fun applyOptionEntries(prefix: String, values: Map<String, Any>, cvars: Cvar = Cvar.getInstance()): String {
    var updatedCount = 0
    var latchedCount = 0
    val errors = mutableListOf<String>()
    values.forEach { (name, value) ->
        val cvar = cvars.find(name) ?: return@forEach
        if (!name.startsWith(prefix) || (cvar.flags and CVAR_OPTIONS) == 0) {
            return@forEach
        }
        val result = if (value is String) cvars.setResult(cvar.name, value)
        else cvars.setNativeResult(cvar.name, value)
        if (!result.accepted) {
            errors += result.diagnostic ?: "Rejected $name"
            return@forEach
        }
        updatedCount++
        if (cvar.rawPendingString != null) {
            latchedCount++
        }
    }
    val summary = if (latchedCount > 0) {
        "Saved $updatedCount option(s). $latchedCount pending restart."
    } else {
        "Saved $updatedCount option(s)."
    }
    return (listOf(summary) + errors).joinToString("\n")
}
