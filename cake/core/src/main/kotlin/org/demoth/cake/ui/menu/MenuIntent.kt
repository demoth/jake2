package org.demoth.cake.ui.menu

import java.nio.file.Path

sealed interface MenuIntent {
    data class OpenUiSample(val game: Boolean? = null) : MenuIntent
    data object CloseUiSample : MenuIntent
    data class CaptureUiSample(val modes: List<Boolean>, val directory: Path) : MenuIntent

    data object RequestStateSync : MenuIntent
    data object OpenProfileEditor : MenuIntent
    data object OpenMainMenu : MenuIntent
    data object OpenSingleplayerMenu : MenuIntent
    data object OpenLoadGame : MenuIntent
    data object OpenSaveGame : MenuIntent
    data object RefreshSavedGames : MenuIntent
    data class SelectSaveSlot(val profileId: String, val slot: String) : MenuIntent
    data class LoadGameRequested(val profileId: String, val slot: String) : MenuIntent
    data class SaveGameRequested(val profileId: String, val slot: String? = null) : MenuIntent
    data class ConfirmOverwrite(val profileId: String, val slot: String) : MenuIntent
    data object CancelOverwrite : MenuIntent
    data object ResumeGame : MenuIntent
    data object OpenMultiplayerMenu : MenuIntent
    data class OpenHostingSetup(val singleplayer: Boolean) : MenuIntent
    data class StartHosting(val singleplayer: Boolean, val form: HostGameFormState) : MenuIntent
    data class UpdateHostingDraft(val singleplayer: Boolean, val form: HostGameFormState) : MenuIntent
    data class HostingFailed(val reason: String) : MenuIntent
    data object OpenJoinGame : MenuIntent
    data object OpenPlayerSetup : MenuIntent
    data object OpenOptions : MenuIntent
    data object OpenContentBrowser : MenuIntent
    data class OpenOptionsSection(val prefix: String) : MenuIntent
    data object DisconnectRequested : MenuIntent
    data class SelectProfile(val profileId: String) : MenuIntent
    data object ApplySelectedProfile : MenuIntent
    data class CreateProfileDraft(val statusMessage: String? = null) : MenuIntent
    data object AutodetectBasedirRequested : MenuIntent
    data class SaveProfile(val form: ProfileFormState) : MenuIntent
    data class JoinGameRequested(val form: JoinGameFormState) : MenuIntent
    data class UpdatePlayerSetupDraft(val form: PlayerSetupFormState) : MenuIntent
    data class SavePlayerSetup(val form: PlayerSetupFormState) : MenuIntent
    data class SaveOptionsSection(val prefix: String, val values: Map<String, Any>) : MenuIntent
}
