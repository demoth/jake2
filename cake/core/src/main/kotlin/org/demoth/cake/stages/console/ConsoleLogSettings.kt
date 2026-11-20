package org.demoth.cake.stages.console

import com.badlogic.gdx.Input
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextTooltip
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import jake2.qcommon.Globals

/** A live view of the process log cvars. An empty Network choice follows the global threshold. */
internal class ConsoleLogSettings(
    private val stage: Stage,
    skin: Skin,
    private val restoreInputFocus: () -> Unit,
) {
    private val globalLevel = Globals.logLevel
    private val networkLevel = Globals.networkLogLevel
    private val anchor = Vector2()

    val button = ImageButton(ImageButton.ImageButtonStyle(skin.get(ImageButton.ImageButtonStyle::class.java)).apply {
        up = skin.newDrawable("control-panel", skin.getColor("strawberry"))
        over = skin.newDrawable("control-panel", skin.getColor("highlight"))
        down = skin.newDrawable("control-panel", skin.getColor("down"))
        disabled = skin.newDrawable("control-panel", skin.getColor("disabled"))
        imageUp = skin.newDrawable("console-gear", skin.getColor("font"))
    }).apply {
        name = "console.logSettings"
        addListener(TextTooltip("Log levels (F4)", skin))
        setProgrammaticChangeEvents(false)
    }

    private val all = object : SelectBox<String>(skin) {
        override fun toString(item: String): String = item.ifEmpty { "Set all to..." }
    }.apply {
        name = "console.logLevel.all"
        setItems(*networkLevel.type.choices.toTypedArray())
        selection.setProgrammaticChangeEvents(false)
        onChange {
            if (selected.isNotEmpty()) {
                globalLevel.setText(selected)
                networkLevel.set("")
            }
            refresh()
        }
    }

    private val network = object : SelectBox<String>(skin) {
        override fun toString(item: String): String = item.ifEmpty { "Use global (${globalLevel.typedValue})" }
    }.apply {
        name = "console.logLevel.network"
        setItems(*networkLevel.type.choices.toTypedArray())
        selection.setProgrammaticChangeEvents(false)
        onChange {
            networkLevel.set(selected)
            refresh()
        }
    }

    private val panel = object : Table(skin) {
        override fun act(delta: Float) {
            super.act(delta)
            refresh()
            positionPanel()
        }
    }.apply {
        name = "console.logLevels"
        background = skin.newDrawable("control-panel", skin.getColor("panelBorder"))
        defaults().pad(4f)
        add(Label("All", skin)).left()
        add(all).growX()
        row()
        add(Label("Network", skin)).left()
        add(network).growX()
    }

    init {
        button.onChange(::toggle)
        stage.addCaptureListener(object : InputListener() {
            override fun keyDown(event: InputEvent, keycode: Int): Boolean {
                if (keycode == Input.Keys.F4) return consume(event, ::toggle)
                if (!panel.hasParent()) return false
                val dropdownOpen = all.list.isTouchable || network.list.isTouchable
                return when {
                    dropdownOpen -> false // Leave selection and Escape to the open list.
                    keycode == Input.Keys.ESCAPE -> consume(event, ::close)
                    keycode == Input.Keys.TAB -> consume(event) {
                        stage.keyboardFocus = if (stage.keyboardFocus === all) network else all
                    }
                    keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE -> consume(event) {
                        val box = if (stage.keyboardFocus === network) network else all
                        box.showScrollPane()
                    }
                    else -> false
                }
            }

            override fun touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, buttonCode: Int): Boolean {
                val target = event.target
                if (panel.hasParent() && !panel.isAscendantOf(target) && !button.isAscendantOf(target) &&
                    !all.scrollPane.isAscendantOf(target) && !network.scrollPane.isAscendantOf(target)
                ) close()
                return false
            }
        })
    }

    fun close() {
        if (!panel.hasParent()) return
        all.hideScrollPane()
        network.hideScrollPane()
        panel.remove()
        restoreInputFocus()
    }

    private fun toggle() {
        if (panel.hasParent()) {
            close()
        } else {
            refresh()
            stage.addActor(panel)
            positionPanel()
            stage.keyboardFocus = all
        }
        button.isChecked = false
    }

    private fun refresh() {
        val override = networkLevel.typedValue
        all.selected = if (override.isEmpty()) globalLevel.rawString else ""
        network.selected = override
    }

    private fun positionPanel() {
        panel.pack()
        button.localToStageCoordinates(anchor.set(button.width, button.height))
        panel.setPosition((anchor.x - panel.width).coerceAtLeast(0f),
            anchor.y.coerceAtMost((stage.height - panel.height).coerceAtLeast(0f)))
    }

    private fun Actor.onChange(action: () -> Unit) {
        addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) = action()
        })
    }

    private fun consume(event: InputEvent, action: () -> Unit): Boolean {
        event.stop()
        action()
        return true
    }
}
