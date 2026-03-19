package org.demoth.cake.ui.menu

data class OptionsSectionDefinition(
    val title: String,
    val prefix: String,
)

val DEFAULT_OPTIONS_SECTIONS = listOf(
    OptionsSectionDefinition(title = "Sound", prefix = "s_"),
    OptionsSectionDefinition(title = "Video", prefix = "vid_"),
    OptionsSectionDefinition(title = "Input", prefix = "in_"),
    OptionsSectionDefinition(title = "Rendering", prefix = "r_"),
    OptionsSectionDefinition(title = "Client", prefix = "cl_"),
)
