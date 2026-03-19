package org.demoth.cake.stages

import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.utils.viewport.Viewport
import ktx.actors.onClick
import ktx.scene2d.Scene2DSkin
import ktx.scene2d.actors
import ktx.scene2d.table
import ktx.scene2d.textButton
import org.demoth.cake.ui.menu.MenuEventBus
import org.demoth.cake.ui.menu.MenuIntent
import org.demoth.cake.ui.menu.OptionEditValue
import org.demoth.cake.ui.menu.OptionEntryState
import org.demoth.cake.ui.menu.OptionsSectionState

class OptionsSectionStage(
    viewport: Viewport,
    private val menuEventBus: MenuEventBus,
) : Stage(viewport) {
    private var renderedState: OptionsSectionState = OptionsSectionState()
    private val fieldsByName = linkedMapOf<String, TextField>()

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
        actors {
            table {
                setFillParent(true)
                top().left().pad(12f)
                defaults().top().left().pad(12f)

                add(Label(state.title.ifBlank { "Options" }, Scene2DSkin.defaultSkin)).colspan(3).left().row()

                add(Label("Name", Scene2DSkin.defaultSkin)).left()
                add(Label("Value", Scene2DSkin.defaultSkin)).left()
                add(Label("Description", Scene2DSkin.defaultSkin)).left().growX().fillX().row()

                state.entries.forEach { entry ->
                    add(Label(entry.name, Scene2DSkin.defaultSkin)).minWidth(180f).left()
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
                    add(Label(descriptionText, Scene2DSkin.defaultSkin).apply {
                        setWrap(true)
                    }).minWidth(320f).prefWidth(520f).growX().fillX().row()
                }

                val buttons = Table(Scene2DSkin.defaultSkin).apply {
                    defaults().padRight(12f).left()

                    add(textButton("Apply") {
                        onClick {
                            menuEventBus.postIntent(
                                MenuIntent.SaveOptionsSection(
                                    prefix = state.prefix,
                                    values = currentValues(),
                                ),
                            )
                        }
                    })
                    add(textButton("Back") {
                        onClick {
                            menuEventBus.postIntent(MenuIntent.OpenOptions)
                        }
                    })
                }
                add(buttons).colspan(3).left().row()

                add(Label(state.statusMessage, Scene2DSkin.defaultSkin).apply {
                    setWrap(true)
                }).colspan(3).growX().fillX().row()
            }
        }
        renderedState = state
    }

    private fun currentValues(): List<OptionEditValue> {
        return fieldsByName.entries.map { (name, field) ->
            OptionEditValue(name = name, value = field.text)
        }
    }
}
