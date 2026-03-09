package org.demoth.cake.stages

import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.utils.viewport.Viewport
import ktx.actors.onClick
import ktx.scene2d.Scene2DSkin
import ktx.scene2d.actors
import ktx.scene2d.label
import ktx.scene2d.table
import ktx.scene2d.textButton
import ktx.scene2d.textField
import org.demoth.cake.ui.menu.MenuEventBus
import org.demoth.cake.ui.menu.MenuIntent
import org.demoth.cake.ui.menu.ProfileEditorState
import org.demoth.cake.ui.menu.ProfileFormState

class ProfileEditStage(
    viewport: Viewport,
    private val menuEventBus: MenuEventBus,
) : Stage(viewport) {
    private lateinit var profilesListTable: Table
    private lateinit var createProfileButton: TextButton
    private lateinit var profileIdField: TextField
    private lateinit var basedirField: TextField
    private lateinit var gamemodField: TextField
    private lateinit var autodetectButton: TextButton
    private lateinit var saveButton: TextButton
    private lateinit var statusLabel: Label
    private val profileButtonsById: MutableMap<String, TextButton> = linkedMapOf()

    private var renderedProfileIds: List<String> = emptyList()
    private var renderedSelectedProfileId: String = ""
    private var renderedCanEdit: Boolean = true
    private var renderedForm: ProfileFormState = ProfileFormState()
    private var renderedStatusMessage: String = ""

    init {
        actors {
            table {
                defaults().pad(12f)
                setFillParent(true)

                val leftPane = table {
                    defaults().pad(8f).fillX()
                    add(label("Profiles")).left().row()

                    profilesListTable = table {
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

                    createProfileButton = textButton("Create New Profile") {
                        onClick {
                            menuEventBus.postIntent(MenuIntent.CreateProfileDraft)
                        }
                    }
                    add(createProfileButton).fillX().row()

                    textButton("Back") {
                        onClick {
                            menuEventBus.postIntent(MenuIntent.OpenMainMenu)
                        }
                    }.also { add(it).fillX().row() }
                }
                add(leftPane).left().top().padRight(20f)

                val rightPane = table {
                    defaults().pad(8f).left()
                    add(label("Profile Editor")).left().row()

                    add(label("Profile ID")).left().row()
                    profileIdField = textField("")
                    add(profileIdField).growX().fillX().row()

                    add(label("Basedir")).left().row()
                    basedirField = textField("")
                    add(basedirField).growX().fillX().row()

                    autodetectButton = textButton("Autodetect") {
                        onClick {
                            menuEventBus.postIntent(MenuIntent.AutodetectBasedirRequested)
                        }
                    }
                    add(autodetectButton).left().row()

                    add(label("Gamemod (optional)")).left().row()
                    gamemodField = textField("")
                    add(gamemodField).growX().fillX().row()

                    saveButton = textButton("Save") {
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

                    statusLabel = label("")
                    add(statusLabel).growX().fillX().row()
                }
                add(rightPane).expand().fill().top()
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
