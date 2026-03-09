package org.demoth.cake.ui.menu

interface MenuBackend {
    fun activeProfileId(): String
    fun canDisconnect(): Boolean
    fun disconnect()
    fun listProfileIds(): List<String>
    fun selectedProfileId(): String?
    fun profileFormById(profileId: String): ProfileFormState?
    fun canEditProfiles(): Boolean
    fun selectProfile(profileId: String): ProfileFormState?
    fun createProfileDraft(): ProfileFormState?
    fun autodetectBasedir(): String?
    fun saveProfile(form: ProfileFormState): String
}

class MenuController(
    private val backend: MenuBackend,
    private val bus: MenuEventBus,
) {
    private var state: MenuStateSnapshot = MenuStateSnapshot()
    private var editingExistingProfileId: String? = null

    fun initialize() {
        editingExistingProfileId = backend.selectedProfileId()?.trim()?.takeIf { it.isNotEmpty() }
        refreshState(
            activeScreen = MenuScreen.MAIN,
            formOverride = currentSelectedProfileForm(),
            statusOverride = "",
        )
    }

    fun pumpIntents(maxItems: Int = MenuEventBus.DEFAULT_DRAIN_LIMIT) {
        bus.drainIntents(maxItems) { intent ->
            handleIntent(intent)
        }
    }

    fun refreshExternalState() {
        refreshState(
            activeScreen = state.activeScreen,
            formOverride = state.profileEditor.form,
        )
    }

    private fun handleIntent(intent: MenuIntent) {
        when (intent) {
            is MenuIntent.RequestStateSync -> {
                bus.postState(state)
            }

            is MenuIntent.OpenProfileEditor -> {
                refreshState(
                    activeScreen = MenuScreen.PROFILE_EDIT,
                    formOverride = state.profileEditor.form,
                )
            }

            is MenuIntent.OpenMainMenu -> {
                refreshState(
                    activeScreen = MenuScreen.MAIN,
                    formOverride = state.profileEditor.form,
                )
            }

            is MenuIntent.DisconnectRequested -> {
                backend.disconnect()
                refreshState()
            }

            is MenuIntent.SelectProfile -> {
                val selected = backend.selectProfile(intent.profileId) ?: return
                editingExistingProfileId = selected.id
                refreshState(
                    formOverride = selected,
                    statusOverride = "Selected profile: ${selected.id}",
                )
            }

            is MenuIntent.CreateProfileDraft -> {
                val draft = backend.createProfileDraft() ?: return
                editingExistingProfileId = null
                refreshState(
                    formOverride = draft,
                    statusOverride = "Editing new profile draft",
                )
            }

            is MenuIntent.AutodetectBasedirRequested -> {
                val detected = backend.autodetectBasedir()
                if (detected.isNullOrBlank()) {
                    return
                }
                refreshState(
                    formOverride = state.profileEditor.form.copy(basedir = detected),
                )
            }

            is MenuIntent.SaveProfile -> {
                val effectiveForm = editingExistingProfileId
                    ?.let { selectedId -> intent.form.copy(id = selectedId) }
                    ?: intent.form
                val status = backend.saveProfile(effectiveForm)
                editingExistingProfileId = backend.selectedProfileId()?.trim()?.takeIf { it.isNotEmpty() }
                refreshState(
                    formOverride = currentSelectedProfileForm(),
                    statusOverride = status,
                )
            }
        }
    }

    private fun refreshState(
        activeScreen: MenuScreen = state.activeScreen,
        formOverride: ProfileFormState? = null,
        statusOverride: String? = null,
    ) {
        val selectedProfileId = backend.selectedProfileId()?.trim()?.takeIf { it.isNotEmpty() }
        val mainMenuState = MainMenuState(
            activeProfileId = backend.activeProfileId(),
            canDisconnect = backend.canDisconnect(),
        )
        val profileEditorState = ProfileEditorState(
            availableProfileIds = backend.listProfileIds()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .sorted(),
            selectedProfileId = selectedProfileId,
            form = formOverride ?: currentSelectedProfileForm(),
            canEdit = backend.canEditProfiles(),
            statusMessage = statusOverride ?: state.profileEditor.statusMessage,
        )
        state = MenuStateSnapshot(
            activeScreen = activeScreen,
            mainMenu = mainMenuState,
            profileEditor = profileEditorState,
        )
        bus.postState(state)
    }

    private fun currentSelectedProfileForm(): ProfileFormState {
        val selectedId = backend.selectedProfileId()?.trim()?.takeIf { it.isNotEmpty() }
        if (selectedId != null) {
            backend.profileFormById(selectedId)?.let { return it }
        }
        return ProfileFormState()
    }
}
