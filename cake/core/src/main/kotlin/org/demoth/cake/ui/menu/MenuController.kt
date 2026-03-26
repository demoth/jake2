package org.demoth.cake.ui.menu

import jake2.qcommon.Defines.PORT_SERVER
import jake2.qcommon.network.netadr_t

interface MenuBackend {
    fun activeProfileId(): String
    fun canDisconnect(): Boolean
    fun disconnect()
    fun listProfileIds(): List<String>
    fun selectedProfileId(): String?
    fun profileFormById(profileId: String): ProfileFormState?
    fun applyProfile(profileId: String): String
    fun createProfileDraft(): ProfileFormState?
    fun autodetectBasedir(): String?
    fun saveProfile(form: ProfileFormState): String
    fun joinServer(address: String): String
    fun playerSetupCatalog(): PlayerSetupCatalog
    fun currentPlayerSetupForm(): PlayerSetupFormState
    fun savePlayerSetup(form: PlayerSetupFormState): String
    fun optionSections(): List<OptionsSectionSummary>
    fun optionEntries(prefix: String): List<OptionEntryState>
    fun saveOptionEntries(prefix: String, values: List<OptionEditValue>): String
}

class MenuController(
    private val backend: MenuBackend,
    private val bus: MenuEventBus,
) {
    private var state: MenuStateSnapshot = MenuStateSnapshot()
    private var editingExistingProfileId: String? = null
    private var draftProfileId: String? = null

    fun initialize() {
        editingExistingProfileId = backend.selectedProfileId()?.trim()?.takeIf { it.isNotEmpty() }
        draftProfileId = null
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
            includeFormIdInList = draftProfileId != null,
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

            is MenuIntent.OpenMultiplayerMenu -> {
                refreshState(
                    activeScreen = MenuScreen.MULTIPLAYER,
                    formOverride = state.profileEditor.form,
                )
            }

            is MenuIntent.OpenJoinGame -> {
                refreshState(
                    activeScreen = MenuScreen.JOIN_GAME,
                    formOverride = state.profileEditor.form,
                    joinGameFormOverride = state.joinGame.form,
                    joinGameStatusOverride = "",
                )
            }

            is MenuIntent.OpenPlayerSetup -> {
                refreshState(
                    activeScreen = MenuScreen.PLAYER_SETUP,
                    formOverride = state.profileEditor.form,
                    playerSetupFormOverride = backend.currentPlayerSetupForm(),
                    playerSetupStatusOverride = "",
                )
            }

            is MenuIntent.OpenOptions -> {
                refreshState(
                    activeScreen = MenuScreen.OPTIONS,
                    formOverride = state.profileEditor.form,
                    optionsSectionStatusOverride = "",
                )
            }

            is MenuIntent.OpenOptionsSection -> {
                refreshState(
                    activeScreen = MenuScreen.OPTIONS_SECTION,
                    formOverride = state.profileEditor.form,
                    optionsSectionPrefixOverride = intent.prefix,
                    optionsSectionStatusOverride = "",
                )
            }

            is MenuIntent.DisconnectRequested -> {
                backend.disconnect()
                refreshState()
            }

            is MenuIntent.SelectProfile -> {
                val selected = backend.profileFormById(intent.profileId) ?: return
                editingExistingProfileId = selected.id
                draftProfileId = null
                refreshState(
                    formOverride = selected,
                    selectedProfileIdOverride = selected.id,
                    statusOverride = "Loaded profile '${selected.id}'",
                )
            }

            is MenuIntent.ApplySelectedProfile -> {
                val selectedId = editingExistingProfileId?.trim()?.takeIf { it.isNotEmpty() } ?: return
                val status = backend.applyProfile(selectedId)
                refreshState(
                    formOverride = backend.profileFormById(selectedId) ?: state.profileEditor.form,
                    selectedProfileIdOverride = selectedId,
                    statusOverride = status,
                )
            }

            is MenuIntent.CreateProfileDraft -> {
                val draft = backend.createProfileDraft() ?: return
                editingExistingProfileId = null
                draftProfileId = draft.id.trim().takeIf { it.isNotEmpty() }
                refreshState(
                    activeScreen = MenuScreen.PROFILE_EDIT,
                    formOverride = draft,
                    selectedProfileIdOverride = draftProfileId,
                    includeFormIdInList = draftProfileId != null,
                    statusOverride = intent.statusMessage ?: "Editing new profile draft",
                )
            }

            is MenuIntent.AutodetectBasedirRequested -> {
                val detected = backend.autodetectBasedir()
                if (detected.isNullOrBlank()) {
                    refreshState(
                        formOverride = state.profileEditor.form,
                        statusOverride = "Autodetect did not find a Quake2 installation",
                    )
                    return
                }
                refreshState(
                    formOverride = state.profileEditor.form.copy(basedir = detected),
                    statusOverride = "Autodetected basedir: $detected",
                )
            }

            is MenuIntent.SaveProfile -> {
                val effectiveForm = editingExistingProfileId
                    ?.let { selectedId -> intent.form.copy(id = selectedId) }
                    ?: intent.form
                val status = backend.saveProfile(effectiveForm)
                editingExistingProfileId = backend.selectedProfileId()?.trim()?.takeIf { it.isNotEmpty() }
                draftProfileId = null
                refreshState(
                    formOverride = currentSelectedProfileForm(),
                    selectedProfileIdOverride = editingExistingProfileId,
                    statusOverride = status,
                )
            }

            is MenuIntent.JoinGameRequested -> {
                val normalizedHost = intent.form.host.trim()
                val normalizedPort = intent.form.port.trim()
                if (normalizedHost.isBlank()) {
                    refreshState(
                        activeScreen = MenuScreen.JOIN_GAME,
                        formOverride = state.profileEditor.form,
                        joinGameFormOverride = intent.form.copy(host = normalizedHost, port = normalizedPort),
                        joinGameStatusOverride = "Host name must not be blank",
                    )
                    return
                }

                val parsedPort = if (normalizedPort.isBlank()) {
                    null
                } else {
                    normalizedPort.toIntOrNull()?.takeIf { it in 1..65535 } ?: run {
                        refreshState(
                            activeScreen = MenuScreen.JOIN_GAME,
                            formOverride = state.profileEditor.form,
                            joinGameFormOverride = intent.form.copy(host = normalizedHost, port = normalizedPort),
                            joinGameStatusOverride = "Port must be a number between 1 and 65535",
                        )
                        return
                    }
                }

                val address = if (parsedPort == null) {
                    normalizedHost
                } else {
                    "$normalizedHost:$parsedPort"
                }
                if (netadr_t.fromString(address, PORT_SERVER) == null) {
                    refreshState(
                        activeScreen = MenuScreen.JOIN_GAME,
                        formOverride = state.profileEditor.form,
                        joinGameFormOverride = intent.form.copy(
                            host = normalizedHost,
                            port = parsedPort?.toString() ?: normalizedPort,
                        ),
                        joinGameStatusOverride = "Invalid server address",
                    )
                    return
                }

                val status = backend.joinServer(address)
                refreshState(
                    activeScreen = MenuScreen.JOIN_GAME,
                    formOverride = state.profileEditor.form,
                    joinGameFormOverride = JoinGameFormState(
                        host = normalizedHost,
                        port = parsedPort?.toString() ?: "",
                    ),
                    joinGameStatusOverride = status,
                )
            }

            is MenuIntent.UpdatePlayerSetupDraft -> {
                refreshState(
                    activeScreen = MenuScreen.PLAYER_SETUP,
                    formOverride = state.profileEditor.form,
                    playerSetupFormOverride = intent.form,
                )
            }

            is MenuIntent.SavePlayerSetup -> {
                val normalizedName = intent.form.name.trim().ifBlank { "unnamed" }
                val normalizedPassword = intent.form.password.trim()
                if (containsInvalidUserinfoChars(normalizedName)) {
                    refreshState(
                        activeScreen = MenuScreen.PLAYER_SETUP,
                        formOverride = state.profileEditor.form,
                        playerSetupFormOverride = intent.form.copy(
                            name = normalizedName,
                            password = normalizedPassword
                        ),
                        playerSetupStatusOverride = "Name must not contain \\\\ ; or \"",
                    )
                    return
                }
                if (containsInvalidUserinfoChars(normalizedPassword)) {
                    refreshState(
                        activeScreen = MenuScreen.PLAYER_SETUP,
                        formOverride = state.profileEditor.form,
                        playerSetupFormOverride = intent.form.copy(
                            name = normalizedName,
                            password = normalizedPassword
                        ),
                        playerSetupStatusOverride = "Password must not contain \\\\ ; or \"",
                    )
                    return
                }

                val catalog = backend.playerSetupCatalog()
                val normalizedForm = catalog.normalize(
                    intent.form.copy(
                        name = normalizedName,
                        password = normalizedPassword,
                    ),
                )
                val status = backend.savePlayerSetup(normalizedForm)
                refreshState(
                    activeScreen = MenuScreen.PLAYER_SETUP,
                    formOverride = state.profileEditor.form,
                    playerSetupFormOverride = backend.currentPlayerSetupForm(),
                    playerSetupStatusOverride = status,
                )
            }

            is MenuIntent.SaveOptionsSection -> {
                val status = backend.saveOptionEntries(intent.prefix, intent.values)
                refreshState(
                    activeScreen = MenuScreen.OPTIONS_SECTION,
                    formOverride = state.profileEditor.form,
                    optionsSectionPrefixOverride = intent.prefix,
                    optionsSectionStatusOverride = status,
                )
            }
        }
    }

    private fun refreshState(
        activeScreen: MenuScreen = state.activeScreen,
        formOverride: ProfileFormState? = null,
        selectedProfileIdOverride: String? = null,
        includeFormIdInList: Boolean = false,
        statusOverride: String? = null,
        joinGameFormOverride: JoinGameFormState? = null,
        joinGameStatusOverride: String? = null,
        playerSetupFormOverride: PlayerSetupFormState? = null,
        playerSetupStatusOverride: String? = null,
        optionsSectionPrefixOverride: String? = null,
        optionsSectionStatusOverride: String? = null,
    ) {
        val form = formOverride ?: currentSelectedProfileForm()
        val playerSetupCatalog = backend.playerSetupCatalog()
        val optionSections = backend.optionSections()
        val selectedProfileId = selectedProfileIdOverride?.trim()?.takeIf { it.isNotEmpty() }
            ?: editingExistingProfileId
            ?: draftProfileId
            ?: backend.selectedProfileId()?.trim()?.takeIf { it.isNotEmpty() }
        val appliedProfileId = backend.selectedProfileId()?.trim()?.takeIf { it.isNotEmpty() }
        val mainMenuState = MainMenuState(
            activeProfileId = backend.activeProfileId(),
            canDisconnect = backend.canDisconnect(),
        )
        val availableProfileIds = backend.listProfileIds()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toMutableList()
        if (includeFormIdInList) {
            val formId = form.id.trim().takeIf { it.isNotEmpty() }
            if (formId != null && !availableProfileIds.contains(formId)) {
                availableProfileIds.add(formId)
            }
        }
        if (selectedProfileId != null && !availableProfileIds.contains(selectedProfileId)) {
            availableProfileIds.add(selectedProfileId)
        }
        val profileEditorState = ProfileEditorState(
            availableProfileIds = availableProfileIds.sorted(),
            selectedProfileId = selectedProfileId,
            canApplySelectedProfile = draftProfileId == null &&
                selectedProfileId != null &&
                selectedProfileId != appliedProfileId,
            form = form,
            statusMessage = statusOverride ?: state.profileEditor.statusMessage,
        )
        val joinGameState = JoinGameState(
            form = joinGameFormOverride ?: state.joinGame.form,
            statusMessage = joinGameStatusOverride ?: state.joinGame.statusMessage,
        )
        val normalizedPlayerSetupForm = playerSetupCatalog.normalize(
            playerSetupFormOverride ?: state.playerSetup.form.takeIf { state.playerSetup.availableModels.isNotEmpty() }
            ?: backend.currentPlayerSetupForm()
        )
        val playerSetupState = PlayerSetupState(
            availableModels = playerSetupCatalog.models,
            availableSkins = playerSetupCatalog.availableSkins(normalizedPlayerSetupForm.model),
            form = normalizedPlayerSetupForm,
            statusMessage = playerSetupStatusOverride ?: state.playerSetup.statusMessage,
        )
        val optionsHubState = OptionsHubState(
            sections = optionSections,
        )
        val optionsSectionPrefix = optionsSectionPrefixOverride
            ?: state.optionsSection.prefix.takeIf { it.isNotBlank() }
        val selectedOptionsSection = optionSections.firstOrNull { it.prefix == optionsSectionPrefix }
        val optionsSectionState = OptionsSectionState(
            title = selectedOptionsSection?.title.orEmpty(),
            prefix = selectedOptionsSection?.prefix.orEmpty(),
            entries = selectedOptionsSection?.let { backend.optionEntries(it.prefix) }.orEmpty(),
            statusMessage = optionsSectionStatusOverride ?: state.optionsSection.statusMessage,
        )
        state = MenuStateSnapshot(
            activeScreen = activeScreen,
            mainMenu = mainMenuState,
            profileEditor = profileEditorState,
            multiplayer = state.multiplayer,
            joinGame = joinGameState,
            playerSetup = playerSetupState,
            optionsHub = optionsHubState,
            optionsSection = optionsSectionState,
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

    private fun containsInvalidUserinfoChars(value: String): Boolean {
        return value.contains('\\') || value.contains(';') || value.contains('"')
    }
}
