package org.demoth.cake.ui.menu

sealed interface MenuIntent {
    data object RequestStateSync : MenuIntent
    data object OpenProfileEditor : MenuIntent
    data object OpenMainMenu : MenuIntent
    data object OpenMultiplayerMenu : MenuIntent
    data object DisconnectRequested : MenuIntent
    data class SelectProfile(val profileId: String) : MenuIntent
    data class CreateProfileDraft(val statusMessage: String? = null) : MenuIntent
    data object AutodetectBasedirRequested : MenuIntent
    data class SaveProfile(val form: ProfileFormState) : MenuIntent
}
