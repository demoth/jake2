package org.demoth.cake.ui.menu

enum class MenuUiSoundEffect {
    ENTER_SUBMENU,
    HOVER_BUTTON,
    EXIT_SUBMENU,
}

sealed interface MenuSignal {
    data class StateUpdated(val snapshot: MenuStateSnapshot) : MenuSignal
    data class PlayUiSound(val effect: MenuUiSoundEffect) : MenuSignal
    data class StatusMessage(val message: String) : MenuSignal
}
