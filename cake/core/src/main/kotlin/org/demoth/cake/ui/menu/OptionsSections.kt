package org.demoth.cake.ui.menu

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
