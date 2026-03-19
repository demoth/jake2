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

                add(Label(state.title.ifBlank { "Options" }, Scene2DSkin.defaultSkin)).left().row()

                state.entries.forEach { entry ->
                    add(Label(entry.name, Scene2DSkin.defaultSkin)).left().row()
                    val field = TextField(entry.value, Scene2DSkin.defaultSkin)
                    fieldsByName[entry.name] = field
                    add(field).minWidth(320f).prefWidth(640f).growX().fillX().row()
                    if (entry.description.isNotBlank()) {
                        add(Label(entry.description, Scene2DSkin.defaultSkin)).growX().fillX().row()
                    }
                    entry.latchedValue?.takeIf { it.isNotBlank() }?.let { latched ->
                        add(Label("Pending restart value: $latched", Scene2DSkin.defaultSkin)).growX().fillX().row()
                    }
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
                add(buttons).left().row()

                add(Label(state.statusMessage, Scene2DSkin.defaultSkin).apply {
                    setWrap(true)
                }).growX().fillX().row()
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
