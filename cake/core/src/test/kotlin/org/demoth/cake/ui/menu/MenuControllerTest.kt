package org.demoth.cake.ui.menu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MenuControllerTest {

    @Test
    fun createProfileDraftUsesProvidedStatusMessage() {
        val backend = FakeMenuBackend()
        val bus = MenuEventBus()
        val controller = MenuController(backend, bus)

        controller.initialize()
        bus.postIntent(MenuIntent.CreateProfileDraft("Create a profile to tell Cake where Quake II is installed."))
        controller.pumpIntents()

        val state = bus.latestState()
        assertEquals(MenuScreen.PROFILE_EDIT, state.activeScreen)
        assertEquals("Create a profile to tell Cake where Quake II is installed.", state.profileEditor.statusMessage)
        assertEquals("newprofile", state.profileEditor.form.id)
    }

    @Test
    fun openMultiplayerMenuSwitchesActiveScreen() {
        val backend = FakeMenuBackend()
        val bus = MenuEventBus()
        val controller = MenuController(backend, bus)

        controller.initialize()
        bus.postIntent(MenuIntent.OpenMultiplayerMenu)
        controller.pumpIntents()

        assertEquals(MenuScreen.MULTIPLAYER, bus.latestState().activeScreen)
    }

    @Test
    fun openJoinGameSwitchesActiveScreen() {
        val backend = FakeMenuBackend()
        val bus = MenuEventBus()
        val controller = MenuController(backend, bus)

        controller.initialize()
        bus.postIntent(MenuIntent.OpenJoinGame)
        controller.pumpIntents()

        val state = bus.latestState()
        assertEquals(MenuScreen.JOIN_GAME, state.activeScreen)
        assertEquals("localhost", state.joinGame.form.host)
        assertEquals("27910", state.joinGame.form.port)
    }

    @Test
    fun joinGameRejectsBlankHost() {
        val backend = FakeMenuBackend()
        val bus = MenuEventBus()
        val controller = MenuController(backend, bus)

        controller.initialize()
        bus.postIntent(MenuIntent.JoinGameRequested(JoinGameFormState(host = "   ", port = "27910")))
        controller.pumpIntents()

        val state = bus.latestState()
        assertEquals(MenuScreen.JOIN_GAME, state.activeScreen)
        assertEquals("Host name must not be blank", state.joinGame.statusMessage)
        assertTrue(backend.joinedAddresses.isEmpty())
    }

    @Test
    fun joinGameRejectsInvalidPort() {
        val backend = FakeMenuBackend()
        val bus = MenuEventBus()
        val controller = MenuController(backend, bus)

        controller.initialize()
        bus.postIntent(MenuIntent.JoinGameRequested(JoinGameFormState(host = "127.0.0.1", port = "abc")))
        controller.pumpIntents()

        val state = bus.latestState()
        assertEquals(MenuScreen.JOIN_GAME, state.activeScreen)
        assertEquals("Port must be a number between 1 and 65535", state.joinGame.statusMessage)
        assertTrue(backend.joinedAddresses.isEmpty())
    }

    @Test
    fun joinGameUsesDefaultPortWhenPortIsBlank() {
        val backend = FakeMenuBackend()
        val bus = MenuEventBus()
        val controller = MenuController(backend, bus)

        controller.initialize()
        bus.postIntent(MenuIntent.JoinGameRequested(JoinGameFormState(host = "127.0.0.1", port = " ")))
        controller.pumpIntents()

        val state = bus.latestState()
        assertEquals(listOf("127.0.0.1"), backend.joinedAddresses)
        assertEquals("Joining 127.0.0.1...", state.joinGame.statusMessage)
        assertEquals("", state.joinGame.form.port)
    }

    @Test
    fun joinGameDispatchesValidatedAddress() {
        val backend = FakeMenuBackend()
        val bus = MenuEventBus()
        val controller = MenuController(backend, bus)

        controller.initialize()
        bus.postIntent(MenuIntent.JoinGameRequested(JoinGameFormState(host = "127.0.0.1", port = "27911")))
        controller.pumpIntents()

        val state = bus.latestState()
        assertEquals(listOf("127.0.0.1:27911"), backend.joinedAddresses)
        assertEquals("Joining 127.0.0.1:27911...", state.joinGame.statusMessage)
        assertEquals("127.0.0.1", state.joinGame.form.host)
        assertEquals("27911", state.joinGame.form.port)
    }

    @Test
    fun openPlayerSetupLoadsBackendForm() {
        val backend = FakeMenuBackend().apply {
            currentPlayerSetupForm = PlayerSetupFormState(
                name = "Ranger",
                password = "secret",
                model = "female",
                skin = "athena",
                hand = 1,
            )
        }
        val bus = MenuEventBus()
        val controller = MenuController(backend, bus)

        controller.initialize()
        bus.postIntent(MenuIntent.OpenPlayerSetup)
        controller.pumpIntents()

        val state = bus.latestState()
        assertEquals(MenuScreen.PLAYER_SETUP, state.activeScreen)
        assertEquals("Ranger", state.playerSetup.form.name)
        assertEquals("female", state.playerSetup.form.model)
        assertEquals(listOf("female", "male"), state.playerSetup.availableModels)
        assertEquals(listOf("athena"), state.playerSetup.availableSkins)
    }

    @Test
    fun savePlayerSetupRejectsInvalidName() {
        val backend = FakeMenuBackend()
        val bus = MenuEventBus()
        val controller = MenuController(backend, bus)

        controller.initialize()
        bus.postIntent(MenuIntent.SavePlayerSetup(PlayerSetupFormState(name = "bad;name")))
        controller.pumpIntents()

        val state = bus.latestState()
        assertEquals(MenuScreen.PLAYER_SETUP, state.activeScreen)
        assertEquals("""Name must not contain \\ ; or """ + "\"", state.playerSetup.statusMessage)
        assertTrue(backend.savedPlayerSetupForms.isEmpty())
    }

    @Test
    fun savePlayerSetupRejectsInvalidPassword() {
        val backend = FakeMenuBackend()
        val bus = MenuEventBus()
        val controller = MenuController(backend, bus)

        controller.initialize()
        bus.postIntent(MenuIntent.SavePlayerSetup(PlayerSetupFormState(password = "bad\\pass")))
        controller.pumpIntents()

        val state = bus.latestState()
        assertEquals(MenuScreen.PLAYER_SETUP, state.activeScreen)
        assertEquals("""Password must not contain \\ ; or """ + "\"", state.playerSetup.statusMessage)
        assertTrue(backend.savedPlayerSetupForms.isEmpty())
    }

    @Test
    fun savePlayerSetupNormalizesAndDispatchesForm() {
        val backend = FakeMenuBackend()
        val bus = MenuEventBus()
        val controller = MenuController(backend, bus)

        controller.initialize()
        bus.postIntent(
            MenuIntent.SavePlayerSetup(
                PlayerSetupFormState(
                    name = "   ",
                    password = "  secret  ",
                    model = "unknown",
                    skin = "missing",
                    hand = 9,
                ),
            ),
        )
        controller.pumpIntents()

        val saved = backend.savedPlayerSetupForms.single()
        assertEquals(
            PlayerSetupFormState(
                name = "unnamed",
                password = "secret",
                model = "female",
                skin = "athena",
                hand = 2,
            ),
            saved,
        )
        val state = bus.latestState()
        assertEquals(MenuScreen.PLAYER_SETUP, state.activeScreen)
        assertEquals("Saved player setup", state.playerSetup.statusMessage)
        assertEquals(saved, state.playerSetup.form)
    }

    private class FakeMenuBackend : MenuBackend {
        val joinedAddresses = mutableListOf<String>()
        val savedPlayerSetupForms = mutableListOf<PlayerSetupFormState>()
        var currentPlayerSetupForm: PlayerSetupFormState = PlayerSetupFormState(
            name = "Player",
            model = "male",
            skin = "grunt",
        )
        private val catalog = PlayerSetupCatalog(
            models = listOf("female", "male"),
            skinsByModel = mapOf(
                "female" to listOf("athena"),
                "male" to listOf("grunt"),
            ),
        )

        override fun activeProfileId(): String = ""

        override fun canDisconnect(): Boolean = false

        override fun disconnect() = Unit

        override fun listProfileIds(): List<String> = emptyList()

        override fun selectedProfileId(): String? = null

        override fun profileFormById(profileId: String): ProfileFormState? = null

        override fun selectProfile(profileId: String): ProfileFormState? = null

        override fun createProfileDraft(): ProfileFormState = ProfileFormState(
            id = "newprofile",
            basedir = "",
            gamemod = "",
        )

        override fun autodetectBasedir(): String? = null

        override fun saveProfile(form: ProfileFormState): String = ""

        override fun joinServer(address: String): String {
            joinedAddresses.add(address)
            return "Joining $address..."
        }

        override fun playerSetupCatalog(): PlayerSetupCatalog = catalog

        override fun currentPlayerSetupForm(): PlayerSetupFormState = catalog.normalize(currentPlayerSetupForm)

        override fun savePlayerSetup(form: PlayerSetupFormState): String {
            val normalized = catalog.normalize(form)
            savedPlayerSetupForms.add(normalized)
            currentPlayerSetupForm = normalized
            return "Saved player setup"
        }
    }
}
