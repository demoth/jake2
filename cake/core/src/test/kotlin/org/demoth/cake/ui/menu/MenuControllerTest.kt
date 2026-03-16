package org.demoth.cake.ui.menu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MenuControllerTest {

    @Test
    fun createProfileDraftUsesProvidedStatusMessage() {
        val backend = object : MenuBackend {
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
        }
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
}
