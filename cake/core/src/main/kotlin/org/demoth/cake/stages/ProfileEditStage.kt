package org.demoth.cake.stages

import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup
import com.badlogic.gdx.scenes.scene2d.ui.Label
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
import org.demoth.cake.profile.CakeGameProfile

/**
 * Dedicated profile configuration stage.
 *
 * This stage owns the layout and basic interaction widgets.
 * Data operations are delegated via callbacks to Cake.
 */
class ProfileEditStage(
    viewport: Viewport,
    private val availableProfileIdsProvider: () -> List<String>,
    private val selectedProfileIdProvider: () -> String?,
    private val profileByIdProvider: (String) -> CakeGameProfile?,
    private val canEditProvider: () -> Boolean,
    private val onSelectProfileRequested: (String) -> CakeGameProfile?,
    private val onCreateNewRequested: () -> CakeGameProfile?,
    private val onAutodetectRequested: () -> String?,
    private val onSaveRequested: (CakeGameProfile) -> String,
    private val onBackRequested: () -> Unit,
) : Stage(viewport) {
    private var profilesListTable: Table
    private var createProfileButton: TextButton
    private var backButton: TextButton
    private var profileIdField: TextField
    private var basedirField: TextField
    private var gamemodField: TextField
    private var autodetectButton: TextButton
    private var saveButton: TextButton
    private var statusLabel: Label
    private val profileButtonsById: MutableMap<String, TextButton> = linkedMapOf()

    private var renderedSelectedProfileId: String = ""
    private var renderedCanEdit: Boolean = true

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
                    add(profilesListTable).growX().row()

                    createProfileButton = textButton("Create New Profile") {
                        onClick {
                            val created = onCreateNewRequested()
                            if (created != null) {
                                loadProfileIntoForm(created)
                                statusLabel.setText("Editing new profile draft")
                            } else {
                                statusLabel.setText("Failed to create new profile draft")
                            }
                        }
                    }
                    add(createProfileButton).fillX().row()

                    backButton = textButton("Back") {
                        onClick { onBackRequested() }
                    }
                    add(backButton).fillX().row()
                }
                add(leftPane).left().top().fillY()

                val rightPane = table {
                    defaults().pad(8f).fillX()
                    add(label("Profile Editor")).left().row()

                    add(label("Profile ID")).left().row()
                    profileIdField = textField("")
                    add(profileIdField).fillX().row()

                    add(label("Basedir")).left().row()
                    basedirField = textField("")
                    add(basedirField).fillX().row()

                    autodetectButton = textButton("Autodetect") {
                        onClick {
                            val detected = onAutodetectRequested()
                            if (detected.isNullOrBlank()) return@onClick
                            basedirField.text = detected
                            statusLabel.setText("Autodetected basedir: $detected")
                        }
                    }
                    add(autodetectButton).left().row()

                    add(label("Gamemod (optional)")).left().row()
                    gamemodField = textField("")
                    add(gamemodField).fillX().row()

                    saveButton = textButton("Save") {
                        onClick {
                            statusLabel.setText(
                                onSaveRequested(
                                    CakeGameProfile(
                                        id = profileIdField.text,
                                        basedir = basedirField.text,
                                        gamemod = gamemodField.text.takeIf { it.isNotBlank() },
                                    ),
                                ),
                            )
                            refreshSelectedProfile()
                        }
                    }
                    add(saveButton).left().row()

                    statusLabel = label("")
                    add(statusLabel).left().row()
                }
                add(rightPane).grow().top()
            }
        }
        rebuildProfilesList()
        refreshSelectedProfile()
        refreshEditState(force = true)
    }

    override fun act(delta: Float) {
        super.act(delta)
        val selected = selectedProfileIdProvider()?.trim().orEmpty()
        if (selected != renderedSelectedProfileId) {
            refreshSelectedProfile()
        }
        val canEdit = canEditProvider()
        if (canEdit != renderedCanEdit) {
            refreshEditState(force = false)
        }
    }

    private fun refreshSelectedProfile() {
        renderedSelectedProfileId = selectedProfileIdProvider()?.trim().orEmpty()
        if (renderedSelectedProfileId.isNotBlank()) {
            profileByIdProvider(renderedSelectedProfileId)?.let { loadProfileIntoForm(it) }
        }
        rebuildProfilesList()
        updateCheckedProfileButton(renderedSelectedProfileId)
    }

    private fun refreshEditState(force: Boolean) {
        val canEdit = canEditProvider()
        if (!force && canEdit == renderedCanEdit) return
        renderedCanEdit = canEdit

        createProfileButton.isDisabled = !canEdit
        profileIdField.isDisabled = !canEdit
        basedirField.isDisabled = !canEdit
        gamemodField.isDisabled = !canEdit
        autodetectButton.isDisabled = !canEdit
        saveButton.isDisabled = !canEdit
        profileButtonsById.values.forEach { it.isDisabled = !canEdit }
        if (!canEdit) {
            statusLabel.setText("Disconnect first to edit profiles")
        }
    }

    private fun rebuildProfilesList() {
        profilesListTable.clearChildren()
        profileButtonsById.clear()
        val buttonGroup = ButtonGroup<TextButton>().apply {
            setMinCheckCount(0)
            setMaxCheckCount(1)
        }

        val profileIds = availableProfileIdsProvider()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
        if (profileIds.isEmpty()) {
            profilesListTable.add(Label("No profiles", Scene2DSkin.defaultSkin)).left().row()
            return
        }

        val selectedId = selectedProfileIdProvider()?.trim().orEmpty()
        for (id in profileIds) {
            val profileButton = TextButton(id, Scene2DSkin.defaultSkin)
            profileButton.isDisabled = !renderedCanEdit
            profileButton.isChecked = (id == selectedId)
            buttonGroup.add(profileButton)
            profileButtonsById[id] = profileButton
            profileButton.onClick {
                val selected = onSelectProfileRequested(id)
                if (selected != null) {
                    loadProfileIntoForm(selected)
                    statusLabel.setText("Selected profile: ${selected.id}")
                    updateCheckedProfileButton(selected.id)
                }
            }
            profilesListTable.add(profileButton).fillX().row()
        }
    }

    private fun loadProfileIntoForm(profile: CakeGameProfile) {
        profileIdField.text = profile.id
        basedirField.text = profile.basedir
        gamemodField.text = profile.gamemod ?: ""
    }

    private fun updateCheckedProfileButton(selectedId: String) {
        profileButtonsById.forEach { (profileId, button) ->
            button.isChecked = profileId == selectedId
        }
    }
}
