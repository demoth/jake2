package org.demoth.cake.ui.menu

import jake2.qcommon.Defines.CVAR_OPTIONS
import jake2.qcommon.exec.Cvar
import jake2.qcommon.exec.CvarTypes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Checks menu intent navigation and deferred option edits using a local cvar registry. */
class MenuControllerTest {

    @Test
    fun contentBrowserNavigationUsesMainMenuParentAndSounds() {
        val bus = MenuEventBus()
        val controller = MenuController(FakeMenuBackend(), bus)
        controller.initialize()
        bus.drainSignals { }

        bus.postIntent(MenuIntent.OpenContentBrowser)
        controller.pumpIntents()
        assertEquals(MenuScreen.CONTENT_BROWSER, bus.latestState().activeScreen)
        assertEquals(listOf(MenuUiSoundEffect.ENTER_SUBMENU), drainedSoundEffects(bus))

        bus.postIntent(MenuIntent.OpenMainMenu)
        controller.pumpIntents()
        assertEquals(MenuScreen.MAIN, bus.latestState().activeScreen)
        assertEquals(listOf(MenuUiSoundEffect.EXIT_SUBMENU), drainedSoundEffects(bus))
    }

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
    fun openingSubmenuEmitsEnterSoundSignal() {
        val backend = FakeMenuBackend()
        val bus = MenuEventBus()
        val controller = MenuController(backend, bus)

        controller.initialize()
        bus.drainSignals { }
        bus.postIntent(MenuIntent.OpenMultiplayerMenu)
        controller.pumpIntents()

        assertEquals(listOf(MenuUiSoundEffect.ENTER_SUBMENU), drainedSoundEffects(bus))
    }

    @Test
    fun returningToParentMenuEmitsExitSoundSignal() {
        val backend = FakeMenuBackend()
        val bus = MenuEventBus()
        val controller = MenuController(backend, bus)

        controller.initialize()
        bus.drainSignals { }
        bus.postIntent(MenuIntent.OpenMultiplayerMenu)
        controller.pumpIntents()
        bus.drainSignals { }

        bus.postIntent(MenuIntent.OpenMainMenu)
        controller.pumpIntents()

        assertEquals(listOf(MenuUiSoundEffect.EXIT_SUBMENU), drainedSoundEffects(bus))
    }

    @Test
    fun lateralNavigationDoesNotEmitMenuDepthSound() {
        val backend = FakeMenuBackend()
        val bus = MenuEventBus()
        val controller = MenuController(backend, bus)

        controller.initialize()
        bus.drainSignals { }
        bus.postIntent(MenuIntent.OpenMultiplayerMenu)
        controller.pumpIntents()
        bus.drainSignals { }

        bus.postIntent(MenuIntent.OpenOptions)
        controller.pumpIntents()

        assertTrue(drainedSoundEffects(bus).isEmpty())
    }

    @Test
    fun selectProfileLoadsFormButDoesNotApplyIt() {
        val backend = FakeMenuBackend().apply {
            profiles["rogue"] = ProfileFormState(
                id = "rogue",
                basedir = "/games/quake2",
                gamemod = "rogue",
            )
        }
        val bus = MenuEventBus()
        val controller = MenuController(backend, bus)

        controller.initialize()
        bus.postIntent(MenuIntent.SelectProfile("rogue"))
        controller.pumpIntents()

        val state = bus.latestState()
        assertEquals("default", backend.selectedProfileId())
        assertEquals("rogue", state.profileEditor.selectedProfileId)
        assertEquals("rogue", state.profileEditor.form.id)
        assertTrue(state.profileEditor.canApplySelectedProfile)
    }

    @Test
    fun applySelectedProfileDelegatesToBackend() {
        val backend = FakeMenuBackend().apply {
            profiles["rogue"] = ProfileFormState(
                id = "rogue",
                basedir = "/games/quake2",
                gamemod = "rogue",
            )
        }
        val bus = MenuEventBus()
        val controller = MenuController(backend, bus)

        controller.initialize()
        bus.postIntent(MenuIntent.SelectProfile("rogue"))
        bus.postIntent(MenuIntent.ApplySelectedProfile)
        controller.pumpIntents()

        val state = bus.latestState()
        assertEquals("rogue", backend.selectedProfileId())
        assertEquals("rogue", state.mainMenu.activeProfileId)
        assertEquals("Applied profile 'rogue'", state.profileEditor.statusMessage)
        assertTrue(!state.profileEditor.canApplySelectedProfile)
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
    fun openOptionsSwitchesActiveScreen() {
        val backend = FakeMenuBackend()
        val bus = MenuEventBus()
        val controller = MenuController(backend, bus)

        controller.initialize()
        bus.postIntent(MenuIntent.OpenOptions)
        controller.pumpIntents()

        val state = bus.latestState()
        assertEquals(MenuScreen.OPTIONS, state.activeScreen)
        assertEquals(listOf("Sound", "Rendering", "Client"), state.optionsHub.sections.map { it.title })
    }

    @Test
    fun openOptionsSectionLoadsEntries() {
        val backend = FakeMenuBackend()
        val bus = MenuEventBus()
        val controller = MenuController(backend, bus)

        controller.initialize()
        bus.postIntent(MenuIntent.OpenOptionsSection("cl_"))
        controller.pumpIntents()

        val state = bus.latestState()
        assertEquals(MenuScreen.OPTIONS_SECTION, state.activeScreen)
        val section = requireNotNull(state.optionsSection.section)
        assertEquals("Client", section.title)
        assertEquals(listOf("cl_showfps"), section.entries.map { it.name })
    }

    @Test
    fun optionDraftStaysLocalUntilSaveIsProcessed() {
        val backend = FakeMenuBackend()
        val bus = MenuEventBus()
        val controller = MenuController(backend, bus)

        controller.initialize()
        bus.postIntent(MenuIntent.OpenOptionsSection("cl_"))
        controller.pumpIntents()
        val entry = bus.latestState().optionsSection.section!!.entries.single()
        val draft = mutableMapOf<String, Any>(entry.name to entry.typedValue)
        draft[entry.name] = 2
        assertEquals("0", entry.rawString)
        bus.postIntent(
            MenuIntent.SaveOptionsSection(
                prefix = "cl_",
                values = draft,
            ),
        )
        assertEquals("0", entry.rawString)
        controller.pumpIntents()

        assertEquals(mapOf("cl_showfps" to 2), backend.savedOptionsValues)
        val state = bus.latestState()
        assertEquals(MenuScreen.OPTIONS_SECTION, state.activeScreen)
        assertEquals("Saved options", state.optionsSection.statusMessage)
        assertEquals("2", state.optionsSection.section!!.entries.single().rawString)
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
        val savedOptionsValues = mutableMapOf<String, Any>()
        val profiles = linkedMapOf(
            "default" to ProfileFormState(
                id = "default",
                basedir = "/games/quake2",
                gamemod = "",
            ),
        )
        private var selectedProfileId: String? = "default"
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
        private val cvars = Cvar().apply {
            register("cl_showfps", CvarTypes.ChoiceType(listOf(0, 1, 2, 3)), 0, CVAR_OPTIONS, "FPS overlay mode")
            register("r_particles", CvarTypes.IntegerType(min = 0), 4096, CVAR_OPTIONS, "Particle budget")
            register("s_volume", CvarTypes.FloatType(min = 0f, max = 1f), 0.7f, CVAR_OPTIONS, "Effects volume")
        }

        override fun activeProfileId(): String = selectedProfileId.orEmpty()

        override fun canDisconnect(): Boolean = false

        override fun disconnect() = Unit

        override fun listProfileIds(): List<String> = profiles.keys.toList()

        override fun selectedProfileId(): String? = selectedProfileId

        override fun profileFormById(profileId: String): ProfileFormState? = profiles[profileId]

        override fun applyProfile(profileId: String): String {
            selectedProfileId = profileId
            return "Applied profile '$profileId'"
        }

        override fun createProfileDraft(): ProfileFormState = ProfileFormState(
            id = "newprofile",
            basedir = "",
            gamemod = "",
        )

        override fun autodetectBasedir(): String? = null

        override fun saveProfile(form: ProfileFormState): String {
            profiles[form.id] = form
            selectedProfileId = form.id
            return "Saved profile '${form.id}'"
        }

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

        override fun optionSections(): List<OptionsSection> = DEFAULT_OPTIONS_SECTIONS.mapNotNull { section ->
            val entries = cvars.listByPrefixAndFlags(section.prefix, CVAR_OPTIONS)
            if (entries.isEmpty()) null else section.copy(entries = entries)
        }

        override fun saveOptionEntries(prefix: String, values: Map<String, Any>): String {
            savedOptionsValues.clear()
            savedOptionsValues.putAll(values)
            values.forEach { (name, value) ->
                if (name.startsWith(prefix)) {
                    check((if (value is String) cvars.setResult(name, value) else cvars.setNativeResult(name, value)).accepted)
                }
            }
            return "Saved options"
        }
    }

    private fun drainedSoundEffects(bus: MenuEventBus): List<MenuUiSoundEffect> {
        val effects = mutableListOf<MenuUiSoundEffect>()
        bus.drainSignals { signal ->
            if (signal is MenuSignal.PlayUiSound) {
                effects += signal.effect
            }
        }
        return effects
    }
}
