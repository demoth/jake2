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
import org.demoth.cake.videoOptionChoices
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
    // Widgets hold unapplied edits. These snapshots only detect changes to the shared cvar references.
    private val valuesByName = linkedMapOf<String, () -> String>()
    private var renderedValues: List<Pair<String, String?>> = emptyList()
    private var renderedChoices: List<List<String>> = emptyList()
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
        val section = state.section
        val entries = section?.entries.orEmpty()
        val values = entries.map { it.rawString to it.rawPendingString }
        val choices = entries.map { videoOptionChoices(it.name) }
        if (!force && state == renderedState && values == renderedValues && choices == renderedChoices) {
            return
        }
        clear()
        valuesByName.clear()
        val root = Table().apply {
            top().left().pad(12f)
            defaults().top().left().pad(12f)

            add(Label(section?.title ?: "Options", labelStyle)).colspan(3).left().row()

            add(Label("Name", labelStyle)).left()
            add(Label("Value", labelStyle)).left()
            add(Label("Description", labelStyle)).left().growX().fillX().row()

            entries.forEachIndexed { index, entry ->
                add(Label(entry.name, labelStyle)).minWidth(180f).left()
                val value = entry.rawPendingString ?: entry.rawString
                val entryChoices = choices[index]
                val editor = if (entryChoices.isEmpty()) {
                    TextField(value, textFieldStyle).also { field ->
                        valuesByName[entry.name] = { field.text }
                    }
                } else {
                    SelectBox<String>(selectBoxStyle).apply {
                        // Preserve a saved or pending value even if this monitor no longer offers it.
                        setItems(*(entryChoices + value).distinct().toTypedArray())
                        selected = value
                        setMaxListCount(10)
                        valuesByName[entry.name] = { selected }
                    }
                }
                add(editor).minWidth(220f).prefWidth(280f).fillX()

                val descriptionText = buildString {
                    if (!entry.description.isNullOrBlank()) {
                        append(entry.description)
                    }
                    entry.rawPendingString?.let { latched ->
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
                                prefix = section?.prefix.orEmpty(),
                                values = valuesByName.mapValues { (_, value) -> value() },
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
        renderedValues = values
        renderedChoices = choices
    }
}
