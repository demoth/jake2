package org.demoth.cake.ui.menu

sealed interface MenuIntent {
    data object RequestStateSync : MenuIntent
    data object OpenProfileEditor : MenuIntent
    data object OpenMainMenu : MenuIntent
    data object OpenMultiplayerMenu : MenuIntent
    data object OpenJoinGame : MenuIntent
    data object OpenPlayerSetup : MenuIntent
    data object OpenOptions : MenuIntent
    data class OpenOptionsSection(val prefix: String) : MenuIntent
    data object DisconnectRequested : MenuIntent
    data class SelectProfile(val profileId: String) : MenuIntent
    data class CreateProfileDraft(val statusMessage: String? = null) : MenuIntent
    data object AutodetectBasedirRequested : MenuIntent
    data class SaveProfile(val form: ProfileFormState) : MenuIntent
    data class JoinGameRequested(val form: JoinGameFormState) : MenuIntent
    data class UpdatePlayerSetupDraft(val form: PlayerSetupFormState) : MenuIntent
    data class SavePlayerSetup(val form: PlayerSetupFormState) : MenuIntent
    data class SaveOptionsSection(val prefix: String, val values: List<OptionEditValue>) : MenuIntent
}
