package org.demoth.cake.stages

import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.utils.viewport.Viewport
import ktx.scene2d.Scene2DSkin
import org.demoth.cake.ui.GameUiStyle
import org.demoth.cake.ui.menu.MenuEventBus
import org.demoth.cake.ui.menu.MenuIntent
import org.demoth.cake.ui.menu.OptionEditValue
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
    private val valuesByName = linkedMapOf<String, () -> String>()
    private val labelStyle = style.menuWidgets.label
    private val buttonStyle = style.menuWidgets.button
    private val textFieldStyle = style.menuWidgets.textField
    private val selectBoxStyle = style.menuWidgets.selectBox
    private val scrollPaneStyle = style.menuWidgets.scrollPane

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
        valuesByName.clear()
        val root = Table().apply {
            top().left().pad(12f)
            defaults().top().left().pad(12f)

            add(Label(state.title.ifBlank { "Options" }, labelStyle)).colspan(3).left().row()

            add(Label("Name", labelStyle)).left()
            add(Label("Value", labelStyle)).left()
            add(Label("Description", labelStyle)).left().growX().fillX().row()

            state.entries.forEach { entry ->
                add(Label(entry.name, labelStyle)).minWidth(180f).left()
                val value = entry.latchedValue ?: entry.value
                val editor = if (entry.choices.isEmpty()) {
                    TextField(value, textFieldStyle).also { field ->
                        valuesByName[entry.name] = { field.text }
                    }
                } else {
                    SelectBox<String>(selectBoxStyle).apply {
                        // Preserve a saved or pending value even if this monitor no longer offers it.
                        setItems(*(entry.choices + value).distinct().toTypedArray())
                        selected = value
                        setMaxListCount(10)
                        valuesByName[entry.name] = { selected }
                    }
                }
                add(editor).minWidth(220f).prefWidth(280f).fillX()

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
        addCenteredForm(root, scrollPaneStyle)
        renderedState = state
    }

    private fun currentValues(): List<OptionEditValue> {
        return valuesByName.entries.map { (name, value) ->
            OptionEditValue(name = name, value = value())
        }
    }
}
