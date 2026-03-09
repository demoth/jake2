package org.demoth.cake.ui.menu

sealed interface MenuSignal {
    data class StateUpdated(val snapshot: MenuStateSnapshot) : MenuSignal
    data class StatusMessage(val message: String) : MenuSignal
}
