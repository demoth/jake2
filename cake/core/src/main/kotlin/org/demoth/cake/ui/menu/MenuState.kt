package org.demoth.cake.ui.menu

enum class MenuScreen {
    MAIN,
    PROFILE_EDIT,
    MULTIPLAYER,
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

data class OptionsSectionSummary(
    val title: String,
    val prefix: String,
    val optionCount: Int = 0,
)

data class OptionEntryState(
    val name: String,
    val description: String = "",
    val value: String = "",
    val latchedValue: String? = null,
)

data class OptionEditValue(
    val name: String,
    val value: String,
)

data class OptionsHubState(
    val sections: List<OptionsSectionSummary> = emptyList(),
)

data class OptionsSectionState(
    val title: String = "",
    val prefix: String = "",
    val entries: List<OptionEntryState> = emptyList(),
    val statusMessage: String = "",
)

data class MenuStateSnapshot(
    val activeScreen: MenuScreen = MenuScreen.MAIN,
    val mainMenu: MainMenuState = MainMenuState(),
    val profileEditor: ProfileEditorState = ProfileEditorState(),
    val multiplayer: MultiplayerMenuState = MultiplayerMenuState(),
    val joinGame: JoinGameState = JoinGameState(),
    val playerSetup: PlayerSetupState = PlayerSetupState(),
    val optionsHub: OptionsHubState = OptionsHubState(),
    val optionsSection: OptionsSectionState = OptionsSectionState(),
)
