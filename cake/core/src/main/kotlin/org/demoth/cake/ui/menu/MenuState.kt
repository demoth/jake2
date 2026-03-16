package org.demoth.cake.ui.menu

enum class MenuScreen {
    MAIN,
    PROFILE_EDIT,
    MULTIPLAYER,
    JOIN_GAME,
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

data class MenuStateSnapshot(
    val activeScreen: MenuScreen = MenuScreen.MAIN,
    val mainMenu: MainMenuState = MainMenuState(),
    val profileEditor: ProfileEditorState = ProfileEditorState(),
    val multiplayer: MultiplayerMenuState = MultiplayerMenuState(),
    val joinGame: JoinGameState = JoinGameState(),
)
