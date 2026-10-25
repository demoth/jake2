package org.demoth.cake.stages

import com.badlogic.gdx.Input
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.utils.viewport.Viewport
import jake2.qcommon.vfs.VfsEntry
import org.demoth.cake.ui.ContentBrowser
import org.demoth.cake.ui.ContentBrowserStyles
import org.demoth.cake.ui.GameUiStyle
import org.demoth.cake.ui.ContentPreview
import org.demoth.cake.ui.ImageThumbnails
import org.demoth.cake.ui.menu.MenuEventBus
import org.demoth.cake.ui.menu.MenuIntent

/** Read-only profile browser. MenuStages supplies a fresh VFS snapshot on entry or profile/style change. */
class ContentBrowserStage(
    viewport: Viewport,
    menuEventBus: MenuEventBus,
    style: GameUiStyle,
    private val thumbnails: ImageThumbnails,
    previewAssets: AssetManager,
    loadSound: (VfsEntry) -> Sound,
) : BackNavigableMenuStage(viewport, menuEventBus, MenuIntent.OpenMainMenu) {
    private val styles = ContentBrowserStyles.fromMenu(style.menuWidgets)
    private val media = ContentPreview(styles, thumbnails, previewAssets, loadSound)
    private val title = Label("Content browser", styles.label)
    private val content = Container<Actor>().fill()

    init {
        addActor(Table().apply {
            setFillParent(true)
            pad(16f)
            add(Table().apply {
                add(title).growX().left()
                add(createBackButton(style = styles.button)).right()
            }).growX().padBottom(8f).row()
            add(content).grow()
        })
    }

    fun show(entries: List<VfsEntry>, profileName: String, message: String? = null) {
        hide()
        media.contentPaths = entries.map { it.normalizedPath }
        title.setText("Content: $profileName (${entries.size} files)")
        content.actor = if (entries.isEmpty()) {
            Label(message ?: "No content found in this profile.", styles.label)
        } else {
            ContentBrowser(entries, profileName, styles, previewSelection = media::open, loadThumbnail = thumbnails::load,
                clearThumbnails = thumbnails::clear, onSelect = media::select, showSource = true)
        }
    }

    override fun keyUp(keycode: Int): Boolean {
        if (keycode == Input.Keys.ESCAPE && (content.actor as? ContentBrowser)?.navigateBack() == true) return true
        return super.keyUp(keycode)
    }

    override fun act(delta: Float) {
        media.act(delta)
        super.act(delta)
    }

    override fun keyDown(keycode: Int): Boolean =
        (content.actor as? ContentBrowser)?.keyDown(keycode) == true ||
            media.keyDown(keycode) || super.keyDown(keycode)

    /** Detach actors before releasing their borrowed textures; reopening supplies a new snapshot. */
    fun hide() {
        unfocusAll()
        content.actor = null
        media.clear()
        media.contentPaths = emptyList()
        thumbnails.clear()
    }

    override fun dispose() {
        hide()
        media.dispose()
        super.dispose()
    }
}
