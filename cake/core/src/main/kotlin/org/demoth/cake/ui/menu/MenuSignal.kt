package org.demoth.cake.ui.menu

import java.nio.file.Path
import jake2.qcommon.vfs.VfsEntry

enum class MenuUiSoundEffect {
    ENTER_SUBMENU,
    HOVER_BUTTON,
    EXIT_SUBMENU,
}

sealed interface MenuSignal {
    data object ShowMenu : MenuSignal
    data object CloseMenu : MenuSignal
    data class PlayDemo(val profileId: String, val entry: VfsEntry) : MenuSignal
    data class OpenUiSample(val game: Boolean? = null) : MenuSignal
    data object CloseUiSample : MenuSignal
    data class CaptureUiSample(val modes: List<Boolean>, val directory: Path) : MenuSignal

    data class StateUpdated(val snapshot: MenuStateSnapshot) : MenuSignal
    data class PlayUiSound(val effect: MenuUiSoundEffect) : MenuSignal
    data class StatusMessage(val message: String) : MenuSignal
}
