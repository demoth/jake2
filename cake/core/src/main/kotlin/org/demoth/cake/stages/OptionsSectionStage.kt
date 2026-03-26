package org.demoth.cake.stages

import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.utils.viewport.Viewport
import ktx.scene2d.Scene2DSkin
import org.demoth.cake.ui.GameUiStyle
import org.demoth.cake.ui.menu.MenuEventBus
import org.demoth.cake.ui.menu.MenuIntent
import org.demoth.cake.ui.menu.OptionEditValue
import org.demoth.cake.ui.menu.OptionEntryState
import org.demoth.cake.ui.menu.OptionsSectionState

class OptionsSectionStage(
    viewport: Viewport,
    menuEventBus: MenuEventBus,
    style: GameUiStyle,
) : BackNavigableMenuStage(
    viewport = viewport,
    menuEventBus = menuEventBus,
    parentIntent = MenuIntent.OpenOptions,
) {
    private var renderedState: OptionsSectionState = OptionsSectionState()
    private val fieldsByName = linkedMapOf<String, TextField>()
    private val labelStyle = style.menuWidgets.label
    private val buttonStyle = style.menuWidgets.button

    init {
        menuEventBus.postIntent(MenuIntent.RequestStateSync)
        rebuild(menuEventBus.latestState().optionsSection, force = true)
    }

    override fun act(delta: Float) {
        super.act(delta)
        rebuild(menuEventBus.latestState().optionsSection, force = false)
    }

    private fun rebuild(state: OptionsSectionState, force: Boolean) {
        if (!force && state == renderedState) {
            return
        }
        clear()
        fieldsByName.clear()
        val root = Table().apply {
            setFillParent(true)
            top().left().pad(12f)
            defaults().top().left().pad(12f)

            add(Label(state.title.ifBlank { "Options" }, labelStyle)).colspan(3).left().row()

            add(Label("Name", labelStyle)).left()
            add(Label("Value", labelStyle)).left()
            add(Label("Description", labelStyle)).left().growX().fillX().row()

            state.entries.forEach { entry ->
                add(Label(entry.name, labelStyle)).minWidth(180f).left()
                val field = TextField(entry.value, Scene2DSkin.defaultSkin)
                fieldsByName[entry.name] = field
                add(field).minWidth(220f).prefWidth(280f).fillX()

                val descriptionText = buildString {
                    if (entry.description.isNotBlank()) {
                        append(entry.description)
                    }
                    entry.latchedValue?.takeIf { it.isNotBlank() }?.let { latched ->
                        if (isNotEmpty()) {
                            append('\n')
                        }
                        append("Pending restart value: ")
                        append(latched)
                    }
                }
                add(Label(descriptionText, labelStyle).apply {
                    setWrap(true)
                }).minWidth(320f).prefWidth(520f).growX().fillX().row()
            }

            val buttons = Table(Scene2DSkin.defaultSkin).apply {
                defaults().padRight(12f).left()

                add(
                    createMenuButton("Apply", buttonStyle) {
                        menuEventBus.postIntent(
                            MenuIntent.SaveOptionsSection(
                                prefix = state.prefix,
                                values = currentValues(),
                            ),
                        )
                    },
                )
                add(createBackButton(style = buttonStyle))
            }
            add(buttons).colspan(3).left().row()

            add(Label(state.statusMessage, labelStyle).apply {
                setWrap(true)
            }).colspan(3).growX().fillX().row()
        }
        addActor(root)
        renderedState = state
    }

    private fun currentValues(): List<OptionEditValue> {
        return fieldsByName.entries.map { (name, field) ->
            OptionEditValue(name = name, value = field.text)
        }
    }
}
