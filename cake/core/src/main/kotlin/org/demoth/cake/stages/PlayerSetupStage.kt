package org.demoth.cake.stages

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.utils.viewport.Viewport
import ktx.actors.onChange
import ktx.scene2d.Scene2DSkin
import org.demoth.cake.ui.ContentBrowserStyles
import org.demoth.cake.ui.SceneResourcePreview
import org.demoth.cake.ui.GameUiStyle
import org.demoth.cake.ui.menu.MenuEventBus
import org.demoth.cake.ui.menu.MenuIntent
import org.demoth.cake.ui.menu.PlayerSetupFormState
import org.demoth.cake.ui.menu.PlayerSetupState

class PlayerSetupStage(
    viewport: Viewport,
    menuEventBus: MenuEventBus,
    style: GameUiStyle,
    private val previewAssets: AssetManager,
) : BackNavigableMenuStage(
    viewport = viewport,
    menuEventBus = menuEventBus,
    parentIntent = MenuIntent.OpenMultiplayerMenu,
) {
    private companion object {
        private val HAND_LABELS = arrayOf("Right hand", "Left hand", "Center")
    }

    private var nameField: TextField
    private var passwordField: TextField
    private var modelSelect: SelectBox<String>
    private var skinSelect: SelectBox<String>
    private var handSelect: SelectBox<String>
    private var statusLabel: Label
    private var suppressDraftEvents: Boolean = false

    private var renderedModels: List<String> = emptyList()
    private var renderedSkins: List<String> = emptyList()
    private var renderedForm: PlayerSetupFormState = PlayerSetupFormState()
    private var renderedStatusMessage: String = ""

    private val preview = SceneResourcePreview(previewAssets, ContentBrowserStyles.fromMenu(style.menuWidgets))
    private val previewHost = Container<Actor>().apply {
        background(style.menuWidgets.containerPanel)
        fill()
    }
    private val previewStatus = Label("", style.menuWidgets.label).apply { setWrap(true) }
    private val previewPanel = Table().apply {
        add(previewHost).width(300f).height(340f).row()
        add(Label("Drag: orbit | Wheel: zoom", style.menuWidgets.label)).pad(4f).row()
        add(previewStatus).width(300f).pad(4f)
    }
    private val formTable = Table()
    private val content = Table()
    private var previewVisible = false
    private var attemptedPreview: Pair<String, String>? = null

    init {
        val labelStyle = style.menuWidgets.label
        val buttonStyle = style.menuWidgets.button
        val textFieldStyle = style.menuWidgets.textField
        val selectBoxStyle = style.menuWidgets.selectBox
        formTable.apply {
            align(com.badlogic.gdx.utils.Align.topLeft)
            pad(12f)
            defaults().top().left().pad(12f)

            add(Label("Player Setup", labelStyle)).colspan(2).left().row()

            add(Label("Name", labelStyle)).minWidth(140f).left()
            nameField = TextField("", textFieldStyle).apply {
                onChange { publishDraft() }
            }
            add(nameField).minWidth(220f).prefWidth(320f).growX().fillX().row()

            add(Label("Password", labelStyle)).minWidth(140f).left()
            passwordField = TextField("", textFieldStyle).apply {
                isPasswordMode = true
                setPasswordCharacter('*')
                onChange { publishDraft() }
            }
            add(passwordField).minWidth(220f).prefWidth(320f).growX().fillX().row()

            add(Label("Model", labelStyle)).minWidth(140f).left()
            modelSelect = SelectBox<String>(selectBoxStyle).apply {
                onChange { publishDraft() }
            }
            add(modelSelect).minWidth(220f).fillX().row()

            add(Label("Skin", labelStyle)).minWidth(140f).left()
            skinSelect = SelectBox<String>(selectBoxStyle).apply {
                onChange { publishDraft() }
            }
            add(skinSelect).minWidth(220f).fillX().row()

            add(Label("Handedness", labelStyle)).minWidth(140f).left()
            handSelect = SelectBox<String>(selectBoxStyle).apply {
                setItems(*HAND_LABELS)
                onChange { publishDraft() }
            }
            add(handSelect).minWidth(220f).fillX().row()

            val buttons = Table(Scene2DSkin.defaultSkin).apply {
                defaults().padRight(12f).left()
                add(createMenuButton("Save", buttonStyle) {
                    menuEventBus.postIntent(MenuIntent.SavePlayerSetup(currentForm()))
                })
                add(createBackButton(style = buttonStyle))
            }
            add(buttons).colspan(2).left().row()

            statusLabel = Label("", labelStyle).apply {
                setWrap(true)
            }
            add(statusLabel).colspan(2).growX().fillX().row()
        }
        content.add(formTable).growX().top()
        content.add(previewPanel).top().pad(12f)
        addCenteredForm(content, style.menuWidgets.scrollPane)

        menuEventBus.postIntent(MenuIntent.RequestStateSync)
        applyState(menuEventBus.latestState().playerSetup, force = true)
    }

    override fun act(delta: Float) {
        super.act(delta)
        applyState(menuEventBus.latestState().playerSetup, force = false)
    }

    private fun applyState(
        state: PlayerSetupState,
        force: Boolean,
    ) {
        if (force || state.availableModels != renderedModels) {
            syncItems(modelSelect, state.availableModels)
        }
        if (force || state.availableSkins != renderedSkins) {
            syncItems(skinSelect, state.availableSkins)
        }
        if (force || state.form != renderedForm) {
            loadForm(
                form = state.form,
                availableModels = state.availableModels,
                availableSkins = state.availableSkins,
            )
        }
        if (force || state.statusMessage != renderedStatusMessage) {
            statusLabel.setText(state.statusMessage)
        }

        if (previewVisible) updatePreview(state.form)
        renderedModels = state.availableModels
        renderedSkins = state.availableSkins
        renderedForm = state.form
        renderedStatusMessage = state.statusMessage
    }

    /** Loading waits for act(), after the controller has supplied the normalized draft. */
    fun show() {
        previewVisible = true
    }

    private fun updatePreview(form: PlayerSetupFormState) {
        val selection = form.model to form.skin
        if (attemptedPreview == selection) return
        attemptedPreview = selection
        val modelPath = "players/${form.model}/tris.md2"
        try {
            if (preview.path != modelPath || preview.md2 == null) {
                previewHost.actor = null
                preview.close()
                // This manager belongs only to this preview, including recovery from failed dependencies.
                previewAssets.clear()
                previewHost.actor = preview.openPlayerModel(modelPath)
            }
            val error = preview.selectExternalSkin("players/${form.model}/${form.skin}.pcx")
            previewStatus.setText(error?.plus(" Preview skin unchanged.").orEmpty())
        } catch (error: Exception) {
            previewHost.actor = null
            preview.close()
            previewAssets.clear()
            previewStatus.setText("Player preview unavailable.")
            Gdx.app.error("Player Setup", "Could not preview $modelPath", error)
        }
    }

    /** Detach borrowed actors before unloading assets or changing the profile resolver. */
    fun hide() {
        previewVisible = false
        unfocusAll()
        previewHost.actor = null
        preview.close()
        previewAssets.clear()
        attemptedPreview = null
        previewStatus.setText("")
    }

    override fun dispose() {
        hide()
        previewAssets.dispose()
        super.dispose()
    }

    private fun syncItems(
        selectBox: SelectBox<String>,
        items: List<String>,
    ) {
        suppressDraftEvents = true
        try {
            selectBox.setItems(*items.toTypedArray())
        } finally {
            suppressDraftEvents = false
        }
    }

    private fun loadForm(
        form: PlayerSetupFormState,
        availableModels: List<String>,
        availableSkins: List<String>,
    ) {
        suppressDraftEvents = true
        try {
            nameField.text = form.name
            passwordField.text = form.password
            if (availableModels.contains(form.model)) {
                modelSelect.selected = form.model
            }
            if (availableSkins.contains(form.skin)) {
                skinSelect.selected = form.skin
            }
            handSelect.selectedIndex = form.hand.coerceIn(0, HAND_LABELS.lastIndex)
        } finally {
            suppressDraftEvents = false
        }
    }

    private fun publishDraft() {
        if (suppressDraftEvents) {
            return
        }
        menuEventBus.postIntent(MenuIntent.UpdatePlayerSetupDraft(currentForm()))
    }

    private fun currentForm(): PlayerSetupFormState = PlayerSetupFormState(
        name = nameField.text,
        password = passwordField.text,
        model = modelSelect.selected ?: "",
        skin = skinSelect.selected ?: "",
        hand = handSelect.selectedIndex.coerceIn(0, HAND_LABELS.lastIndex),
    )
}
