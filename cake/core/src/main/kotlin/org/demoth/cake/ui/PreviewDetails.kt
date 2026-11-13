package org.demoth.cake.ui

import com.badlogic.gdx.Input
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.Window
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener

internal const val RESOURCE_PREVIEW_HELP = "Browse: select to inspect; double-click or Enter to open.\n" +
    "Selecting a sound or sprite auditions it.\n" +
    "Space: Play/Pause. S: Stop. Loop is an independent setting.\n" +
    "Left/Right: step one model or sprite frame.\n" +
    "Model: drag to orbit; wheel to zoom.\n" +
    "Image/sprite: drag to pan; wheel to zoom.\n" +
    "Map: click to focus; WASD to fly; Q/E down/up; drag to look.\n" +
    "Thumbnail size: -/+ in the folder listing."

/** Secondary help/metadata never reserve space in the resource layout. */
internal fun showPreviewDetails(anchor: Actor, title: String, text: String, styles: ContentBrowserStyles) {
    val stage = anchor.stage ?: return
    stage.root.findActor<Actor>("resource-details")?.remove()
    val previousFocus = stage.keyboardFocus
    val window = Window(title, Window.WindowStyle(styles.label.font, styles.label.fontColor, styles.background)).apply {
        name = "resource-details"
        isModal = true
        isMovable = false
        defaults().pad(8f)
    }
    fun close() {
        window.remove()
        stage.keyboardFocus = previousFocus?.takeIf { it.stage === stage }
    }
    val width = minOf(stage.width - 24f, styles.label.font.data.lineHeight * 28f)
    window.add(Label(text, styles.label).apply { setWrap(true) }).width(width - 32f).row()
    window.add(TextButton("Close", styles.button).apply {
        addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) { close() }
        })
    }).right()
    window.addListener(object : InputListener() {
        override fun keyDown(event: InputEvent, keycode: Int): Boolean {
            return true
        }
        override fun keyUp(event: InputEvent, keycode: Int): Boolean {
            if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.ENTER) close()
            return true
        }
    })
    window.pack()
    window.setPosition((stage.width - window.width) / 2f, (stage.height - window.height) / 2f)
    stage.addActor(window)
    stage.keyboardFocus = window
}
