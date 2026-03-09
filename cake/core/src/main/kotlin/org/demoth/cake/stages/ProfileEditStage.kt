package org.demoth.cake.stages

import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.utils.viewport.Viewport
import ktx.actors.onClick
import ktx.scene2d.Scene2DSkin
import ktx.scene2d.actors
import ktx.scene2d.table
import org.demoth.cake.ui.menu.MenuEventBus
import org.demoth.cake.ui.menu.MenuIntent
import org.demoth.cake.ui.menu.ProfileEditorState
import org.demoth.cake.ui.menu.ProfileFormState

class ProfileEditStage(
    viewport: Viewport,
    private val menuEventBus: MenuEventBus,
) : Stage(viewport) {
    companion object {
        private const val DEBUG_LAYOUT: Boolean = false
        private const val FORM_FIELD_MIN_WIDTH: Float = 320f
        private const val FORM_FIELD_PREF_WIDTH: Float = 640f
    }

    private var profilesListTable: Table
    private var createProfileButton: TextButton
    private var profileIdField: TextField
    private var basedirField: TextField
    private var gamemodField: TextField
    private var autodetectButton: TextButton
    private var saveButton: TextButton
    private var statusLabel: Label
    private val profileButtonsById: MutableMap<String, TextButton> = linkedMapOf()

    private var renderedProfileIds: List<String> = emptyList()
    private var renderedSelectedProfileId: String = ""
    private var renderedCanEdit: Boolean = true
    private var renderedForm: ProfileFormState = ProfileFormState()
    private var renderedStatusMessage: String = ""

    init {
        actors {
            table {
                setFillParent(true)
                align(com.badlogic.gdx.utils.Align.topLeft)
                pad(12f)
                defaults().top().left().pad(12f)

                val leftPane = Table(Scene2DSkin.defaultSkin).apply {
                    align(com.badlogic.gdx.utils.Align.topLeft)
                    defaults().pad(8f).fillX()
                    add(Label("Profiles", Scene2DSkin.defaultSkin)).left().row()

                    profilesListTable = Table(Scene2DSkin.defaultSkin).apply {
                        defaults().pad(4f).fillX()
                    }
                    val profilesScrollPane = ScrollPane(profilesListTable, Scene2DSkin.defaultSkin).apply {
                        setFadeScrollBars(false)
                        setScrollingDisabled(true, false)
                    }
                    add(profilesScrollPane)
                        .minWidth(180f)
                        .prefWidth(220f)
                        .maxHeight(420f)
                        .growY()
                        .fillY()
                        .row()

                    createProfileButton = TextButton("Create New Profile", Scene2DSkin.defaultSkin).apply {
                        onClick {
                            menuEventBus.postIntent(MenuIntent.CreateProfileDraft)
                        }
                    }
                    add(createProfileButton).fillX().row()

                    val backButton = TextButton("Back", Scene2DSkin.defaultSkin).apply {
                        onClick {
                            menuEventBus.postIntent(MenuIntent.OpenMainMenu)
                        }
                    }
                    add(backButton).fillX().row()
                }

                val rightPane = Table(Scene2DSkin.defaultSkin).apply {
                    align(com.badlogic.gdx.utils.Align.topLeft)
                    defaults().pad(8f).left()
                    add(Label("Profile Editor", Scene2DSkin.defaultSkin)).left().row()

                    add(Label("Profile ID", Scene2DSkin.defaultSkin)).left().row()
                    profileIdField = TextField("", Scene2DSkin.defaultSkin)
                    add(profileIdField).minWidth(FORM_FIELD_MIN_WIDTH).prefWidth(FORM_FIELD_PREF_WIDTH).growX().fillX().row()

                    add(Label("Basedir", Scene2DSkin.defaultSkin)).left().row()
                    basedirField = TextField("", Scene2DSkin.defaultSkin)
                    add(basedirField).minWidth(FORM_FIELD_MIN_WIDTH).prefWidth(FORM_FIELD_PREF_WIDTH).growX().fillX().row()

                    autodetectButton = TextButton("Autodetect", Scene2DSkin.defaultSkin).apply {
                        onClick {
                            menuEventBus.postIntent(MenuIntent.AutodetectBasedirRequested)
                        }
                    }
                    add(autodetectButton).left().row()

                    add(Label("Gamemod (optional)", Scene2DSkin.defaultSkin)).left().row()
                    gamemodField = TextField("", Scene2DSkin.defaultSkin)
                    add(gamemodField).minWidth(FORM_FIELD_MIN_WIDTH).prefWidth(FORM_FIELD_PREF_WIDTH).growX().fillX().row()

                    saveButton = TextButton("Save", Scene2DSkin.defaultSkin).apply {
                        onClick {
                            menuEventBus.postIntent(
                                MenuIntent.SaveProfile(
                                    ProfileFormState(
                                        id = profileIdField.text,
                                        basedir = basedirField.text,
                                        gamemod = gamemodField.text,
                                    ),
                                ),
                            )
                        }
                    }
                    add(saveButton).left().row()

                    statusLabel = Label("", Scene2DSkin.defaultSkin).apply {
                        setWrap(true)
                    }
                    add(statusLabel).growX().fillX().row()
                }

                add(leftPane).top().left().padRight(20f)
                add(rightPane).top().left().expandX().fillX()

                if (DEBUG_LAYOUT) {
                    debugAll()
                }
            }
        }
        menuEventBus.postIntent(MenuIntent.RequestStateSync)
        applyState(menuEventBus.latestState().profileEditor, force = true)
    }

    override fun act(delta: Float) {
        super.act(delta)
        applyState(menuEventBus.latestState().profileEditor, force = false)
    }

    private fun applyState(
        state: ProfileEditorState,
        force: Boolean,
    ) {
        val normalizedIds = state.availableProfileIds
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
        val normalizedSelectedId = state.selectedProfileId?.trim().orEmpty()

        if (
            force ||
            normalizedIds != renderedProfileIds ||
            normalizedSelectedId != renderedSelectedProfileId ||
            state.canEdit != renderedCanEdit
        ) {
            rebuildProfilesList(
                profileIds = normalizedIds,
                selectedProfileId = normalizedSelectedId,
                canEdit = state.canEdit,
            )
        }

        if (force || state.form != renderedForm) {
            loadProfileIntoForm(state.form)
        }

        if (force || state.statusMessage != renderedStatusMessage) {
            statusLabel.setText(state.statusMessage)
        }

        if (force || state.canEdit != renderedCanEdit) {
            refreshEditState(state.canEdit)
        }

        renderedProfileIds = normalizedIds
        renderedSelectedProfileId = normalizedSelectedId
        renderedCanEdit = state.canEdit
        renderedForm = state.form
        renderedStatusMessage = state.statusMessage
    }

    private fun refreshEditState(canEdit: Boolean) {
        createProfileButton.isDisabled = !canEdit
        profileIdField.isDisabled = !canEdit
        basedirField.isDisabled = !canEdit
        gamemodField.isDisabled = !canEdit
        autodetectButton.isDisabled = !canEdit
        saveButton.isDisabled = !canEdit
        profileButtonsById.values.forEach { it.isDisabled = !canEdit }
    }

    private fun rebuildProfilesList(
        profileIds: List<String>,
        selectedProfileId: String,
        canEdit: Boolean,
    ) {
        profilesListTable.clearChildren()
        profileButtonsById.clear()
        val buttonGroup = ButtonGroup<TextButton>().apply {
            setMinCheckCount(0)
            setMaxCheckCount(1)
        }
        if (profileIds.isEmpty()) {
            profilesListTable.add(Label("No profiles", Scene2DSkin.defaultSkin)).left().row()
            return
        }

        for (id in profileIds) {
            val profileButton = TextButton(id, Scene2DSkin.defaultSkin)
            profileButton.isDisabled = !canEdit
            profileButton.isChecked = (id == selectedProfileId)
            buttonGroup.add(profileButton)
            profileButtonsById[id] = profileButton
            profileButton.onClick {
                menuEventBus.postIntent(MenuIntent.SelectProfile(id))
            }
            profilesListTable.add(profileButton).fillX().row()
        }
    }

    private fun loadProfileIntoForm(form: ProfileFormState) {
        profileIdField.text = form.id
        basedirField.text = form.basedir
        gamemodField.text = form.gamemod
    }
}
