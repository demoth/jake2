package org.demoth.cake.stages

import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.utils.viewport.Viewport
import ktx.actors.onChange
import ktx.actors.onClick
import ktx.scene2d.Scene2DSkin
import org.demoth.cake.ui.GameUiStyle
import org.demoth.cake.ui.menu.MenuEventBus
import org.demoth.cake.ui.menu.MenuIntent
import org.demoth.cake.ui.menu.ProfileEditorState
import org.demoth.cake.ui.menu.ProfileFormState
import com.badlogic.gdx.scenes.scene2d.ui.List as Scene2dList

class ProfileEditStage(
    viewport: Viewport,
    menuEventBus: MenuEventBus,
    style: GameUiStyle,
) : BackNavigableMenuStage(
    viewport = viewport,
    menuEventBus = menuEventBus,
    parentIntent = MenuIntent.OpenMainMenu,
) {
    companion object {
        private const val DEBUG_LAYOUT: Boolean = false
        private const val FORM_FIELD_MIN_WIDTH: Float = 320f
        private const val FORM_FIELD_PREF_WIDTH: Float = 640f
    }

    private var profilesListWidget: Scene2dList<String>
    private var profilesEmptyLabel: Label
    private var createProfileButton: TextButton
    private var applyProfileButton: TextButton
    private var profileIdField: TextField
    private var basedirField: TextField
    private var gamemodField: TextField
    private var autodetectButton: TextButton
    private var saveButton: TextButton
    private var statusLabel: Label
    private var suppressProfileSelectionEvents: Boolean = false

    private var renderedProfileIds: List<String> = emptyList()
    private var renderedSelectedProfileId: String = ""
    private var renderedForm: ProfileFormState = ProfileFormState()
    private var renderedStatusMessage: String = ""

    init {
        val labelStyle = style.menuWidgets.label
        val buttonStyle = style.menuWidgets.button
        val textFieldStyle = style.menuWidgets.textField
        val listStyle = style.menuWidgets.list
        val scrollPaneStyle = style.menuWidgets.scrollPane
        val root = Table().apply {
            setFillParent(true)
            align(com.badlogic.gdx.utils.Align.topLeft)
            pad(12f)
            defaults().top().left().pad(12f)

            val leftPane = Table(Scene2DSkin.defaultSkin).apply {
                align(com.badlogic.gdx.utils.Align.topLeft)
                defaults().pad(8f).fillX()
                add(Label("Profiles", labelStyle)).left().row()

                profilesListWidget = Scene2dList<String>(listStyle).apply {
                    onChange {
                        if (suppressProfileSelectionEvents) return@onChange
                        selected?.let { menuEventBus.postIntent(MenuIntent.SelectProfile(it)) }
                    }
                }
                val profilesScrollPane = ScrollPane(profilesListWidget, scrollPaneStyle).apply {
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

                profilesEmptyLabel = Label("No profiles", labelStyle).apply {
                    isVisible = false
                }
                add(profilesEmptyLabel).left().row()

                applyProfileButton = createMenuButton("Apply Selected Profile", buttonStyle) {
                    menuEventBus.postIntent(MenuIntent.ApplySelectedProfile)
                }
                add(applyProfileButton).fillX().row()

                createProfileButton = createMenuButton("Create New Profile", buttonStyle) {
                    menuEventBus.postIntent(MenuIntent.CreateProfileDraft())
                }
                add(createProfileButton).fillX().row()

                add(createBackButton(style = buttonStyle)).fillX().row()
            }

            val rightPane = Table(Scene2DSkin.defaultSkin).apply {
                align(com.badlogic.gdx.utils.Align.topLeft)
                defaults().pad(8f).left()
                add(Label("Profile Editor", labelStyle)).left().row()

                add(Label("Profile ID", labelStyle)).left().row()
                profileIdField = TextField("", textFieldStyle)
                add(profileIdField).minWidth(FORM_FIELD_MIN_WIDTH).prefWidth(FORM_FIELD_PREF_WIDTH).growX().fillX().row()

                add(Label("Basedir", labelStyle)).left().row()
                basedirField = TextField("", textFieldStyle)
                add(basedirField).minWidth(FORM_FIELD_MIN_WIDTH).prefWidth(FORM_FIELD_PREF_WIDTH).growX().fillX().row()

                autodetectButton = createMenuButton("Autodetect", buttonStyle) {
                    menuEventBus.postIntent(MenuIntent.AutodetectBasedirRequested)
                }
                add(autodetectButton).left().row()

                add(Label("Gamemod (optional)", labelStyle)).left().row()
                gamemodField = TextField("", textFieldStyle)
                add(gamemodField).minWidth(FORM_FIELD_MIN_WIDTH).prefWidth(FORM_FIELD_PREF_WIDTH).growX().fillX().row()

                saveButton = createMenuButton("Save", buttonStyle) {
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
                add(saveButton).left().row()

                statusLabel = Label("", labelStyle).apply {
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
        addActor(root)
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
            normalizedIds != renderedProfileIds
        ) {
            syncProfileItems(profileIds = normalizedIds)
        }

        if (force || normalizedSelectedId != renderedSelectedProfileId) {
            syncProfileSelection(
                profileIds = normalizedIds,
                selectedProfileId = normalizedSelectedId,
            )
        }

        if (force || state.form != renderedForm) {
            loadProfileIntoForm(state.form)
        }

        if (force || state.statusMessage != renderedStatusMessage) {
            statusLabel.setText(state.statusMessage)
        }
        applyProfileButton.isDisabled = !state.canApplySelectedProfile

        renderedProfileIds = normalizedIds
        renderedSelectedProfileId = normalizedSelectedId
        renderedForm = state.form
        renderedStatusMessage = state.statusMessage
    }

    private fun syncProfileItems(profileIds: List<String>) {
        suppressProfileSelectionEvents = true
        try {
            profilesListWidget.setItems(*profileIds.toTypedArray())
            profilesEmptyLabel.isVisible = profileIds.isEmpty()
        } finally {
            suppressProfileSelectionEvents = false
        }
    }

    private fun syncProfileSelection(
        profileIds: List<String>,
        selectedProfileId: String,
    ) {
        suppressProfileSelectionEvents = true
        try {
            when {
                selectedProfileId.isBlank() -> profilesListWidget.selection.clear()
                profileIds.contains(selectedProfileId) -> profilesListWidget.selected = selectedProfileId
                else -> profilesListWidget.selection.clear()
            }
        } finally {
            suppressProfileSelectionEvents = false
        }
    }

    private fun loadProfileIntoForm(form: ProfileFormState) {
        profileIdField.text = form.id
        basedirField.text = form.basedir
        gamemodField.text = form.gamemod
    }
}
