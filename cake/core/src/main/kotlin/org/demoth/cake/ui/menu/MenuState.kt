package org.demoth.cake.ui.menu

import org.demoth.cake.hosting.HostSaveAccess
import org.demoth.cake.hosting.HostSaveEntry

enum class MenuScreen {
    MAIN,
    LOAD_GAME,
    SAVE_GAME,
    PROFILE_EDIT,
    MULTIPLAYER,
    SINGLEPLAYER,
    NEW_GAME,
    HOST_GAME,
    JOIN_GAME,
    PLAYER_SETUP,
    OPTIONS,
    OPTIONS_SECTION,
    CONTENT_BROWSER,
}

data class MainMenuState(
    val activeProfileId: String = "",
    val canDisconnect: Boolean = false,
)

data class ProfileFormState(
    val id: String = "",
    val basedir: String = "",
    val gamemod: String = "",
)

data class ProfileEditorState(
    val availableProfileIds: List<String> = emptyList(),
    val selectedProfileId: String? = null,
    val canApplySelectedProfile: Boolean = false,
    val form: ProfileFormState = ProfileFormState(),
    val statusMessage: String = "",
)

data class MultiplayerMenuState(
    val statusMessage: String = "",
)

data class JoinGameFormState(
    val host: String = "localhost",
    val port: String = "27910",
)

data class JoinGameState(
    val form: JoinGameFormState = JoinGameFormState(),
    val statusMessage: String = "",
)

data class PlayerSetupFormState(
    val name: String = "unnamed",
    val password: String = "",
    val model: String = "male",
    val skin: String = "grunt",
    val hand: Int = 0,
)

data class PlayerSetupState(
    val availableModels: List<String> = emptyList(),
    val availableSkins: List<String> = emptyList(),
    val form: PlayerSetupFormState = PlayerSetupFormState(),
    val statusMessage: String = "",
)

data class OptionsHubState(
    val sections: List<OptionsSection> = emptyList(),
)

data class OptionsSectionState(
    val section: OptionsSection? = null,
    val statusMessage: String = "",
)

data class MenuStateSnapshot(
    val activeScreen: MenuScreen = MenuScreen.MAIN,
    val canHostGame: Boolean = false,
    val saveAccess: HostSaveAccess = HostSaveAccess("Save/load is unavailable.", "Save/load is unavailable."),
    val savedGames: SavedGamesState = SavedGamesState(),
    val singleplayer: HostingSetupState = HostingSetupState(),
    val hostGame: HostingSetupState = HostingSetupState(),
    val mainMenu: MainMenuState = MainMenuState(),
    val profileEditor: ProfileEditorState = ProfileEditorState(),
    val multiplayer: MultiplayerMenuState = MultiplayerMenuState(),
    val joinGame: JoinGameState = JoinGameState(),
    val playerSetup: PlayerSetupState = PlayerSetupState(),
    val optionsHub: OptionsHubState = OptionsHubState(),
    val optionsSection: OptionsSectionState = OptionsSectionState(),
)

data class SavedGamesState(
    val profileId: String = "",
    val entries: List<HostSaveEntry> = emptyList(),
    val selectedSlot: String? = null,
    val pendingOverwriteSlot: String? = null,
    val statusMessage: String = "",
    val pendingDeleteSlot: String? = null,
)

/** Raw text stays in the form so rejected input can be corrected without losing it. */
data class HostGameFormState(
    val map: String = "",
    val coop: Boolean = false,
    val maxClients: String = "8",
    val hostname: String = "Cake server",
    val skill: Int = 1,
)

data class HostingSetupState(
    val form: HostGameFormState = HostGameFormState(),
    val statusMessage: String = "",
    val maps: List<HostMap> = emptyList(),
)
