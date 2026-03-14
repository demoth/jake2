package org.demoth.cake.ui.menu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MenuControllerTest {

    @Test
    fun initializePublishesBackendState() {
        val backend = FakeMenuBackend().apply {
            activeProfileIdValue = "default"
            canDisconnectValue = false
            selectedProfileIdValue = "default"
            formsById["default"] = ProfileFormState(
                id = "default",
                basedir = "/q2",
                gamemod = "",
            )
        }
        val bus = MenuEventBus()
        val controller = MenuController(backend, bus)

        controller.initialize()

        val state = bus.latestState()
        assertEquals(MenuScreen.MAIN, state.activeScreen)
        assertEquals("default", state.mainMenu.activeProfileId)
        assertFalse(state.mainMenu.canDisconnect)
        assertEquals("default", state.profileEditor.form.id)
        assertEquals("/q2", state.profileEditor.form.basedir)
    }

    @Test
    fun selectProfileIntentUpdatesSelectedFormAndStatus() {
        val backend = FakeMenuBackend().apply {
            formsById["default"] = ProfileFormState("default", "/q2", "")
            formsById["mod"] = ProfileFormState("mod", "/mods/q2", "ctf")
            selectedProfileIdValue = "default"
            activeProfileIdValue = "default"
        }
        val bus = MenuEventBus()
        val controller = MenuController(backend, bus)
        controller.initialize()

        bus.postIntent(MenuIntent.SelectProfile("mod"))
        controller.pumpIntents()

        val state = bus.latestState()
        assertEquals("mod", state.profileEditor.selectedProfileId)
        assertEquals("mod", state.profileEditor.form.id)
        assertEquals("/mods/q2", state.profileEditor.form.basedir)
        assertEquals("Selected profile: mod", state.profileEditor.statusMessage)
    }

    @Test
    fun autodetectFailureDoesNotOverwriteBasedir() {
        val backend = FakeMenuBackend().apply {
            formsById["default"] = ProfileFormState("default", "/old/path", "")
            selectedProfileIdValue = "default"
            activeProfileIdValue = "default"
            autodetectValue = null
        }
        val bus = MenuEventBus()
        val controller = MenuController(backend, bus)
        controller.initialize()

        bus.postIntent(MenuIntent.AutodetectBasedirRequested)
        controller.pumpIntents()

        val state = bus.latestState()
        assertEquals("/old/path", state.profileEditor.form.basedir)
        assertEquals("Autodetect did not find a Quake2 installation", state.profileEditor.statusMessage)
    }

    @Test
    fun saveProfileIntentForwardsFormAndStatus() {
        val backend = FakeMenuBackend().apply {
            formsById["default"] = ProfileFormState("default", "/q2", "")
            selectedProfileIdValue = "default"
            activeProfileIdValue = "default"
            saveStatus = "Saved profile 'custom'"
        }
        val bus = MenuEventBus()
        val controller = MenuController(backend, bus)
        controller.initialize()

        bus.postIntent(MenuIntent.CreateProfileDraft)
        controller.pumpIntents()

        val form = ProfileFormState(
            id = "custom",
            basedir = "/q2/custom",
            gamemod = "coop",
        )
        bus.postIntent(MenuIntent.SaveProfile(form))
        controller.pumpIntents()

        assertNotNull(backend.lastSavedForm)
        assertEquals(form, backend.lastSavedForm)
        val state = bus.latestState()
        assertEquals("custom", state.profileEditor.selectedProfileId)
        assertEquals("Saved profile 'custom'", state.profileEditor.statusMessage)
    }

    @Test
    fun createProfileDraftAddsUnsavedProfileToList() {
        val backend = FakeMenuBackend().apply {
            formsById["default"] = ProfileFormState("default", "/q2", "")
            selectedProfileIdValue = "default"
            activeProfileIdValue = "default"
            createDraftForm = ProfileFormState("newprofile", "/q2", "")
        }
        val bus = MenuEventBus()
        val controller = MenuController(backend, bus)
        controller.initialize()

        bus.postIntent(MenuIntent.CreateProfileDraft)
        controller.pumpIntents()

        val state = bus.latestState()
        assertEquals("newprofile", state.profileEditor.selectedProfileId)
        assertEquals("newprofile", state.profileEditor.form.id)
        assertTrue(state.profileEditor.availableProfileIds.contains("newprofile"))
    }

    @Test
    fun saveWhileEditingExistingProfileOverwritesSelectedId() {
        val backend = FakeMenuBackend().apply {
            formsById["q2"] = ProfileFormState("q2", "/q2", "")
            selectedProfileIdValue = "q2"
            activeProfileIdValue = "q2"
            saveStatus = "Saved profile 'q2'"
        }
        val bus = MenuEventBus()
        val controller = MenuController(backend, bus)
        controller.initialize()

        bus.postIntent(
            MenuIntent.SaveProfile(
                ProfileFormState(
                    id = "q2_renamed_unexpectedly",
                    basedir = "/q2/updated",
                    gamemod = "",
                ),
            ),
        )
        controller.pumpIntents()

        assertNotNull(backend.lastSavedForm)
        assertEquals("q2", backend.lastSavedForm?.id)
        assertEquals("/q2/updated", backend.lastSavedForm?.basedir)
        val state = bus.latestState()
        assertEquals("q2", state.profileEditor.selectedProfileId)
        assertEquals("q2", state.profileEditor.form.id)
    }

    private class FakeMenuBackend : MenuBackend {
        var activeProfileIdValue: String = "default"
        var canDisconnectValue: Boolean = false
        var disconnectedCalls: Int = 0
        var selectedProfileIdValue: String? = null
        val formsById: MutableMap<String, ProfileFormState> = linkedMapOf()
        var autodetectValue: String? = null
        var saveStatus: String = "Saved"
        var lastSavedForm: ProfileFormState? = null
        var createDraftForm: ProfileFormState? = ProfileFormState(
            id = "",
            basedir = "",
            gamemod = "",
        )

        override fun activeProfileId(): String = activeProfileIdValue

        override fun canDisconnect(): Boolean = canDisconnectValue

        override fun disconnect() {
            disconnectedCalls++
            canDisconnectValue = false
        }

        override fun listProfileIds(): List<String> = formsById.keys.toList()

        override fun selectedProfileId(): String? = selectedProfileIdValue

        override fun profileFormById(profileId: String): ProfileFormState? = formsById[profileId]

        override fun selectProfile(profileId: String): ProfileFormState? {
            val form = formsById[profileId] ?: return null
            selectedProfileIdValue = form.id
            activeProfileIdValue = form.id
            return form
        }

        override fun createProfileDraft(): ProfileFormState? = createDraftForm

        override fun autodetectBasedir(): String? = autodetectValue

        override fun saveProfile(form: ProfileFormState): String {
            lastSavedForm = form
            formsById[form.id] = form
            selectedProfileIdValue = form.id
            activeProfileIdValue = form.id
            return saveStatus
        }
    }
}
