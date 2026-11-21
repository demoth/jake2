package org.demoth.cake.stages.console

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextTooltip
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import jake2.qcommon.Com.ConsoleLevel
import jake2.qcommon.Com.LogLevelOverride
import jake2.qcommon.Com.LogLevelOverride.GLOBAL
import jake2.qcommon.Com.LogSubsystem
import jake2.qcommon.Globals

/** A live view of the process log cvars. GLOBAL follows the current global threshold. */
internal class ConsoleLogSettings(
    private val stage: Stage,
    skin: Skin,
    private val restoreInputFocus: () -> Unit,
) {
    private val globalLevel = Globals.logLevel
    private val overrides = Globals.logLevelOverrides
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

    private val globalSelector = SelectBox<ConsoleLevel>(skin).apply {
        name = "console.logLevel.global"
        setItems(*globalLevel.type.choices.toTypedArray())
        selection.setProgrammaticChangeEvents(false)
        onChange {
            globalLevel.set(selected)
            overrides.values.forEach { it.set(GLOBAL) }
            refresh()
        }
    }

    private val subsystemSelectors = LogSubsystem.entries.associateWith { subsystem ->
        val level = overrides.getValue(subsystem)
        SelectBox<LogLevelOverride>(skin).apply {
            name = "console.logLevel.${subsystem.name.lowercase()}"
            setItems(*level.type.choices.toTypedArray())
            selection.setProgrammaticChangeEvents(false)
            onChange {
                level.set(selected)
                refresh()
            }
        }
    }
    private val selectors = listOf(globalSelector) + subsystemSelectors.values

    private val severityColors = ConsoleSeverityColors(skin)
    private val rowLabelStyle = Label.LabelStyle(skin.get(Label.LabelStyle::class.java)).apply {
        // The actor tint supplies the exact console severity color.
        fontColor = null
    }
    private val globalLabel = Label("Global", rowLabelStyle).apply { name = "${globalSelector.name}.label" }
    private val subsystemLabels = subsystemSelectors.mapValues { (subsystem, selector) ->
        val text = if (subsystem == LogSubsystem.FILESYSTEM) "FileSystem"
            else subsystem.name.lowercase().replaceFirstChar { it.titlecase() }
        Label(text, rowLabelStyle).apply { name = "${selector.name}.label" }
    }

    private val rows = Table(skin).apply {
        defaults().pad(4f)
        add(globalLabel).left()
        add(globalSelector).growX()
        subsystemSelectors.forEach { (subsystem, selector) ->
            row()
            add(subsystemLabels.getValue(subsystem)).left()
            add(selector).growX()
        }
    }
    private val scroller = ScrollPane(rows, skin, "clean").apply {
        setScrollingDisabled(true, false)
        setFadeScrollBars(false)
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
        add(scroller).grow()
    }

    init {
        button.onChange(::toggle)
        stage.addCaptureListener(object : InputListener() {
            override fun keyDown(event: InputEvent, keycode: Int): Boolean {
                if (keycode == Input.Keys.F4) return consume(event, ::toggle)
                if (!panel.hasParent()) return false
                val dropdownOpen = selectors.any { it.list.isTouchable }
                return when {
                    dropdownOpen -> false // Leave selection and Escape to the open list.
                    keycode == Input.Keys.ESCAPE -> consume(event, ::close)
                    keycode == Input.Keys.TAB -> consume(event) {
                        val current = selectors.indexOfFirst { it === stage.keyboardFocus }
                        val backwards = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)
                        val next = if (backwards) current - 1 else current + 1
                        focusSelector(selectors[Math.floorMod(next, selectors.size)])
                    }
                    keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE -> consume(event) {
                        val box = selectors.firstOrNull { it === stage.keyboardFocus } ?: globalSelector
                        box.showScrollPane()
                    }
                    else -> false
                }
            }

            override fun touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, buttonCode: Int): Boolean {
                val target = event.target
                if (panel.hasParent() && !panel.isAscendantOf(target) && !button.isAscendantOf(target) &&
                    selectors.none { it.scrollPane.isAscendantOf(target) }
                ) close()
                return false
            }
        })
    }

    fun close() {
        if (!panel.hasParent()) return
        selectors.forEach { it.hideScrollPane() }
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
            stage.scrollFocus = scroller
            focusSelector(globalSelector)
        }
        button.isChecked = false
    }

    private fun refresh() {
        val global = globalLevel.typedValue
        globalSelector.selected = global
        globalLabel.color.set(severityColors[global])
        subsystemSelectors.forEach { (subsystem, selector) ->
            val value = overrides.getValue(subsystem).typedValue
            selector.selected = value
            subsystemLabels.getValue(subsystem).color.set(severityColors[value.explicitLevel ?: global])
        }
    }

    private fun focusSelector(selector: SelectBox<*>) {
        stage.keyboardFocus = selector
        panel.validate()
        scroller.validate()
        scroller.scrollTo(selector.x, selector.y, selector.width, selector.height)
        scroller.updateVisualScroll()
    }

    private fun positionPanel() {
        panel.setSize(minOf(panel.prefWidth, stage.width), minOf(panel.prefHeight, stage.height))
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
