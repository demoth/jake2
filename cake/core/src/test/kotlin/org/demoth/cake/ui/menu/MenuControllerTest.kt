package org.demoth.cake.ui.menu

import jake2.qcommon.Defines.CVAR_OPTIONS
import jake2.qcommon.exec.Cvar
import jake2.qcommon.exec.CvarTypes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.demoth.cake.hosting.*
import org.junit.jupiter.api.Test

/** Checks menu intent navigation and deferred option edits using a local cvar registry. */
class MenuControllerTest {

    @Test
    fun singleplayerHubOwnsNewGameLoadAndSaveNavigation() {
        val bus = MenuEventBus()
        val controller = MenuController(FakeMenuBackend(), bus)
        controller.initialize()
        bus.drainSignals { }
        fun open(intent: MenuIntent, screen: MenuScreen, sound: MenuUiSoundEffect) {
            bus.postIntent(intent)
            controller.pumpIntents()
            assertEquals(screen, bus.latestState().activeScreen)
            assertEquals(listOf(sound), drainedSoundEffects(bus))
        }
        open(MenuIntent.OpenSingleplayerMenu, MenuScreen.SINGLEPLAYER, MenuUiSoundEffect.ENTER_SUBMENU)
        open(MenuIntent.OpenHostingSetup(true), MenuScreen.NEW_GAME, MenuUiSoundEffect.ENTER_SUBMENU)
        open(MenuIntent.OpenSingleplayerMenu, MenuScreen.SINGLEPLAYER, MenuUiSoundEffect.EXIT_SUBMENU)
        open(MenuIntent.OpenLoadGame, MenuScreen.LOAD_GAME, MenuUiSoundEffect.ENTER_SUBMENU)
        open(MenuIntent.OpenSingleplayerMenu, MenuScreen.SINGLEPLAYER, MenuUiSoundEffect.EXIT_SUBMENU)
        open(MenuIntent.OpenSaveGame, MenuScreen.SAVE_GAME, MenuUiSoundEffect.ENTER_SUBMENU)
        bus.postIntent(MenuIntent.HostingFailed("Campaign could not start"))
        controller.pumpIntents()
        assertEquals(MenuScreen.NEW_GAME, bus.latestState().activeScreen)
        assertEquals("Campaign could not start", bus.latestState().singleplayer.statusMessage)
    }

    private fun savedEntry(slot: String, reason: String? = null) =
        HostSaveEntry(slot, "Checkpoint", "base1", 1000L, HostMode.SINGLEPLAYER, reason)

    @Test
    fun deletionRequiresCurrentProfileConfirmationAndSelectsTheNextCheckpoint() {
        val backend = FakeMenuBackend().apply { saves = listOf(savedEntry("one"), savedEntry("two"), savedEntry("three")) }
        val bus = MenuEventBus()
        val controller = MenuController(backend, bus)
        controller.initialize()
        fun post(intent: MenuIntent) { bus.postIntent(intent); controller.pumpIntents() }
        post(MenuIntent.OpenLoadGame)
        post(MenuIntent.DeleteSaveRequested("default", "two"))
        assertEquals("two", bus.latestState().savedGames.pendingDeleteSlot)
        assertTrue(backend.deletedSaves.isEmpty())
        post(MenuIntent.CancelSaveConfirmation)
        post(MenuIntent.ConfirmDeleteSave("default", "two"))
        assertTrue(backend.deletedSaves.isEmpty())
        post(MenuIntent.DeleteSaveRequested("default", "two"))
        post(MenuIntent.ConfirmDeleteSave("default", "two"))
        assertEquals(listOf("two"), backend.deletedSaves)
        assertEquals("three", bus.latestState().savedGames.selectedSlot)
        assertEquals("Saved game deleted.", bus.latestState().savedGames.statusMessage)
        post(MenuIntent.DeleteSaveRequested("default", "one"))
        backend.applyProfile("other")
        post(MenuIntent.ConfirmDeleteSave("default", "one"))
        assertEquals(listOf("two"), backend.deletedSaves)
    }

    @Test
    fun deleteFailureRemainsVisibleWithoutLosingSelectionOrClosingMenu() {
        val backend = FakeMenuBackend().apply {
            saves = listOf(savedEntry("corrupt", "Invalid save"))
            deleteOutcome = HostSaveResult.Failed("Permission denied", java.io.IOException())
        }
        val bus = MenuEventBus()
        val controller = MenuController(backend, bus)
        controller.initialize()
        bus.postIntent(MenuIntent.OpenSaveGame)
        bus.postIntent(MenuIntent.DeleteSaveRequested("default", "corrupt"))
        bus.postIntent(MenuIntent.ConfirmDeleteSave("default", "corrupt"))
        controller.pumpIntents()
        assertEquals(MenuScreen.SAVE_GAME, bus.latestState().activeScreen)
        assertEquals("corrupt", bus.latestState().savedGames.selectedSlot)
        assertEquals("Delete failed: Permission denied", bus.latestState().savedGames.statusMessage)
    }

    @Test
    fun saveCatalogRefreshesOnEntryAndProfileChangeWithoutPerFrameIO() {
        val backend = FakeMenuBackend().apply { saves = listOf(savedEntry("manual")) }
        val bus = MenuEventBus()
        val controller = MenuController(backend, bus)
        controller.initialize()
        bus.postIntent(MenuIntent.OpenLoadGame)
        controller.pumpIntents()
        repeat(3) { controller.refreshExternalState() }
        assertEquals(1, backend.saveListReads)
        assertEquals("manual", bus.latestState().savedGames.selectedSlot)
        backend.applyProfile("other")
        backend.saves = emptyList()
        controller.refreshExternalState()
        assertEquals(2, backend.saveListReads)
        assertEquals("other", bus.latestState().savedGames.profileId)
        assertTrue(bus.latestState().savedGames.entries.isEmpty())
        bus.postIntent(MenuIntent.LoadGameRequested("default", "manual"))
        controller.pumpIntents()
        assertTrue(backend.loads.isEmpty())
    }

    @Test
    fun saveRequiresConfirmationAndRejectsStaleProfileAndAutosaveRequests() {
        val backend = FakeMenuBackend().apply { saves = listOf(savedEntry("save0"), savedEntry("manual")) }
        val bus = MenuEventBus()
        val controller = MenuController(backend, bus)
        controller.initialize()
        fun post(intent: MenuIntent) { bus.postIntent(intent); controller.pumpIntents() }
        post(MenuIntent.OpenSaveGame)
        post(MenuIntent.SaveGameRequested("default", "save0"))
        assertNull(bus.latestState().savedGames.pendingOverwriteSlot)
        post(MenuIntent.SaveGameRequested("default", "manual"))
        assertEquals("manual", bus.latestState().savedGames.pendingOverwriteSlot)
        assertTrue(backend.savesWritten.isEmpty())
        post(MenuIntent.CancelSaveConfirmation)
        post(MenuIntent.ConfirmOverwrite("default", "manual"))
        assertTrue(backend.savesWritten.isEmpty())
        post(MenuIntent.SaveGameRequested("default", "manual"))
        post(MenuIntent.ConfirmOverwrite("default", "manual"))
        assertEquals(listOf("manual" to true), backend.savesWritten)
        assertEquals("Game saved.", bus.latestState().savedGames.statusMessage)
        post(MenuIntent.SaveGameRequested("default", "manual"))
        backend.applyProfile("other")
        post(MenuIntent.ConfirmOverwrite("default", "manual"))
        assertEquals(1, backend.savesWritten.size)
    }

    @Test
    fun saveEligibilityIsRecheckedBeforePendingOverwriteAndNewSave() {
        val backend = FakeMenuBackend().apply { saves = listOf(savedEntry("manual")) }
        val bus = MenuEventBus()
        val controller = MenuController(backend, bus)
        controller.initialize()
        bus.postIntent(MenuIntent.OpenSaveGame)
        bus.postIntent(MenuIntent.SaveGameRequested("default", "manual"))
        controller.pumpIntents()
        backend.access = HostSaveAccess(null, "Cannot save while dead")
        bus.postIntent(MenuIntent.ConfirmOverwrite("default", "manual"))
        controller.pumpIntents()
        assertEquals("Cannot save while dead", bus.latestState().savedGames.statusMessage)
        bus.postIntent(MenuIntent.SaveGameRequested("default"))
        controller.pumpIntents()
        assertTrue(backend.savesWritten.isEmpty())
        assertNull(bus.latestState().savedGames.pendingOverwriteSlot)
    }

    @Test
    fun loadFailureStaysVisibleAndSuccessClosesMenuOnlyAfterBackendCompletes() {
        val backend = FakeMenuBackend().apply { saves = listOf(savedEntry("manual"), savedEntry("corrupt", "Invalid save")) }
        val bus = MenuEventBus()
        val controller = MenuController(backend, bus)
        controller.initialize()
        fun post(intent: MenuIntent): List<MenuSignal> {
            bus.drainSignals { }
            bus.postIntent(intent)
            controller.pumpIntents()
            return mutableListOf<MenuSignal>().also { result -> bus.drainSignals { result += it } }
        }
        assertTrue(post(MenuIntent.OpenLoadGame).contains(MenuSignal.ShowMenu))
        post(MenuIntent.LoadGameRequested("default", "corrupt"))
        assertTrue(backend.loads.isEmpty())
        assertEquals("Invalid save", bus.latestState().savedGames.statusMessage)
        backend.loadOutcome = HostSaveResult.Failed("Restore failed", java.io.IOException("disk failure"))
        assertFalse(post(MenuIntent.LoadGameRequested("default", "manual")).contains(MenuSignal.CloseMenu))
        assertEquals(MenuScreen.LOAD_GAME, bus.latestState().activeScreen)
        assertEquals("Load failed: Restore failed", bus.latestState().savedGames.statusMessage)
        backend.loadOutcome = HostSaveResult.Completed("manual")
        assertTrue(post(MenuIntent.LoadGameRequested("default", "manual")).contains(MenuSignal.CloseMenu))
        assertEquals(listOf("manual", "manual"), backend.loads)
    }

    @Test
    fun saveListFailureAndEmptyCatalogAreDistinctAndRepeatedOpenClearsConfirmation() {
        val backend = FakeMenuBackend()
        val bus = MenuEventBus()
        val controller = MenuController(backend, bus)
        controller.initialize()
        bus.postIntent(MenuIntent.OpenLoadGame)
        controller.pumpIntents()
        assertTrue(bus.latestState().savedGames.entries.isEmpty())
        assertEquals("", bus.latestState().savedGames.statusMessage)
        backend.catalogFailure = HostSaveCatalogResult.Failed("disk unavailable", java.io.IOException())
        bus.postIntent(MenuIntent.OpenLoadGame)
        controller.pumpIntents()
        assertEquals("Could not list saved games: disk unavailable", bus.latestState().savedGames.statusMessage)
        backend.catalogFailure = null
        backend.saves = listOf(savedEntry("manual"))
        bus.postIntent(MenuIntent.OpenSaveGame)
        bus.postIntent(MenuIntent.SaveGameRequested("default", "manual"))
        controller.pumpIntents()
        assertEquals("manual", bus.latestState().savedGames.pendingOverwriteSlot)
        bus.postIntent(MenuIntent.OpenSaveGame)
        controller.pumpIntents()
        assertNull(bus.latestState().savedGames.pendingOverwriteSlot)
    }

    @Test
    fun hostingMapsRefreshOnEntryAndInvalidationButNotEveryFrame() {
        val backend = FakeMenuBackend()
        val bus = MenuEventBus()
        val controller = MenuController(backend, bus)
        backend.maps = listOf(HostMap("a", "Arena"), HostMap("b"))
        controller.initialize()
        assertEquals(0, backend.mapReads)
        bus.postIntent(MenuIntent.OpenHostingSetup(false))
        controller.pumpIntents()
        assertEquals("a", bus.latestState().hostGame.form.map)
        repeat(3) { controller.refreshExternalState() }
        assertEquals(1, backend.mapReads)
        bus.postIntent(MenuIntent.UpdateHostingDraft(false, HostGameFormState(map = "b")))
        bus.postIntent(MenuIntent.OpenHostingSetup(false))
        controller.pumpIntents()
        assertEquals("b", bus.latestState().hostGame.form.map)
        backend.maps = listOf(HostMap("new"))
        controller.invalidateHostingMaps()
        controller.refreshExternalState()
        assertEquals("new", bus.latestState().hostGame.form.map)
        backend.maps = emptyList()
        controller.invalidateHostingMaps()
        controller.refreshExternalState()
        assertEquals("", bus.latestState().hostGame.form.map)
        assertTrue(bus.latestState().hostGame.maps.isEmpty())
    }

    @Test
    fun sampleRequestsEmitOrderedSignalsWithoutChangingNavigation() {
        val bus = MenuEventBus()
        val controller = MenuController(FakeMenuBackend(), bus)
        controller.initialize()
        bus.postIntent(MenuIntent.OpenOptions)
        controller.pumpIntents()
        bus.drainSignals { }
        val before = bus.latestState()
        val directory = java.nio.file.Path.of("captures")
        bus.postIntent(MenuIntent.OpenUiSample(true))
        bus.postIntent(MenuIntent.OpenUiSample())
        bus.postIntent(MenuIntent.CloseUiSample)
        bus.postIntent(MenuIntent.CaptureUiSample(listOf(false, true), directory))
        val signals = mutableListOf<MenuSignal>()
        bus.drainSignals { signals += it }
        assertTrue(signals.isEmpty(), "Requests wait for the intent pump")
        controller.pumpIntents()
        bus.drainSignals { signals += it }
        assertEquals(listOf(
            MenuSignal.OpenUiSample(true), MenuSignal.OpenUiSample(), MenuSignal.CloseUiSample,
            MenuSignal.CaptureUiSample(listOf(false, true), directory),
        ), signals)
        assertEquals(before, bus.latestState())
    }


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
        var access = HostSaveAccess(null, null)
        var saves = emptyList<HostSaveEntry>()
        var saveListReads = 0
        var catalogFailure: HostSaveCatalogResult? = null
        var loadOutcome: HostSaveResult = HostSaveResult.Completed("manual")
        val loads = mutableListOf<String>()
        val savesWritten = mutableListOf<Pair<String?, Boolean>>()
        val deletedSaves = mutableListOf<String>()
        var deleteOutcome: HostSaveResult? = null
        override fun deleteSave(slot: String): HostSaveResult {
            deletedSaves += slot
            val result = deleteOutcome ?: HostSaveResult.Completed(slot)
            if (result is HostSaveResult.Completed) saves = saves.filter { it.slot != slot }
            return result
        }
        override fun saveAccess() = access
        override fun listSaves(): HostSaveCatalogResult {
            saveListReads++
            return catalogFailure ?: HostSaveCatalogResult.Available(activeProfileId(), saves)
        }
        override fun saveGame(slot: String?, overwrite: Boolean): HostSaveResult {
            savesWritten += slot to overwrite
            return HostSaveResult.Completed(slot ?: "new")
        }
        override fun loadGame(slot: String): HostSaveResult {
            loads += slot
            return loadOutcome
        }
        var maps = emptyList<HostMap>()
        var mapReads = 0
        override fun hostMaps(): List<HostMap> {
            mapReads++
            return maps
        }
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
