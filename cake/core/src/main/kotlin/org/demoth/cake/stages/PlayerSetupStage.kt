package org.demoth.cake.stages

import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.utils.viewport.Viewport
import ktx.actors.onChange
import ktx.scene2d.Scene2DSkin
import org.demoth.cake.ui.GameUiStyle
import org.demoth.cake.ui.menu.MenuEventBus
import org.demoth.cake.ui.menu.MenuIntent
import org.demoth.cake.ui.menu.PlayerSetupFormState
import org.demoth.cake.ui.menu.PlayerSetupState

class PlayerSetupStage(
    viewport: Viewport,
    menuEventBus: MenuEventBus,
    style: GameUiStyle,
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

    init {
        val labelStyle = style.menuWidgets.label
        val buttonStyle = style.menuWidgets.button
        val root = Table().apply {
            setFillParent(true)
            align(com.badlogic.gdx.utils.Align.topLeft)
            pad(12f)
            defaults().top().left().pad(12f)

            add(Label("Player Setup", labelStyle)).colspan(2).left().row()

            add(Label("Name", labelStyle)).minWidth(180f).left()
            nameField = TextField("", Scene2DSkin.defaultSkin).apply {
                onChange { publishDraft() }
            }
            add(nameField).minWidth(320f).prefWidth(640f).growX().fillX().row()

            add(Label("Password", labelStyle)).minWidth(180f).left()
            passwordField = TextField("", Scene2DSkin.defaultSkin).apply {
                isPasswordMode = true
                setPasswordCharacter('*')
                onChange { publishDraft() }
            }
            add(passwordField).minWidth(320f).prefWidth(640f).growX().fillX().row()

            add(Label("Model", labelStyle)).minWidth(180f).left()
            modelSelect = SelectBox<String>(Scene2DSkin.defaultSkin).apply {
                onChange { publishDraft() }
            }
            add(modelSelect).minWidth(240f).fillX().row()

            add(Label("Skin", labelStyle)).minWidth(180f).left()
            skinSelect = SelectBox<String>(Scene2DSkin.defaultSkin).apply {
                onChange { publishDraft() }
            }
            add(skinSelect).minWidth(240f).fillX().row()

            add(Label("Handedness", labelStyle)).minWidth(180f).left()
            handSelect = SelectBox<String>(Scene2DSkin.defaultSkin).apply {
                setItems(*HAND_LABELS)
                onChange { publishDraft() }
            }
            add(handSelect).minWidth(240f).fillX().row()

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
        addActor(root)

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

        renderedModels = state.availableModels
        renderedSkins = state.availableSkins
        renderedForm = state.form
        renderedStatusMessage = state.statusMessage
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
