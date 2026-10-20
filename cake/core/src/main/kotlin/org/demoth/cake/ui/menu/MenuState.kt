package org.demoth.cake.ui.menu

enum class MenuScreen {
    MAIN,
    PROFILE_EDIT,
    MULTIPLAYER,
    SINGLEPLAYER,
    HOST_GAME,
    JOIN_GAME,
    PLAYER_SETUP,
    OPTIONS,
    OPTIONS_SECTION,
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
)
