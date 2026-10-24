package org.demoth.cake.stages

import com.badlogic.gdx.Input
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.utils.viewport.Viewport
import com.badlogic.gdx.utils.Scaling
import jake2.qcommon.vfs.VfsEntry
import org.demoth.cake.ui.ContentBrowser
import org.demoth.cake.ui.ContentBrowserStyles
import org.demoth.cake.ui.GameUiStyle
import org.demoth.cake.ui.ImageThumbnails
import org.demoth.cake.ui.ImagePreviewPane
import org.demoth.cake.ui.SoundPreviewControls
import org.demoth.cake.audio.SoundPlayback
import org.demoth.cake.assets.Sp2Asset
import org.demoth.cake.ui.FramePlayback
import org.demoth.cake.ui.SpriteFrameImage
import org.demoth.cake.ui.SpritePreview
import org.demoth.cake.ui.SceneResourcePreview
import org.demoth.cake.ui.menu.MenuEventBus
import org.demoth.cake.ui.menu.MenuIntent

/** Read-only profile browser. Cake supplies a fresh VFS snapshot on entry or profile/style change. */
class ContentBrowserStage(
    viewport: Viewport,
    menuEventBus: MenuEventBus,
    style: GameUiStyle,
    private val thumbnails: ImageThumbnails,
    private val previewAssets: AssetManager,
    private val loadSound: (VfsEntry) -> Sound,
) : BackNavigableMenuStage(viewport, menuEventBus, MenuIntent.OpenMainMenu) {
    private val styles = ContentBrowserStyles.fromMenu(style.menuWidgets)
    private val scene = SceneResourcePreview(previewAssets, styles)
    private val title = Label("Content browser", styles.label)
    private val content = Container<Actor>().fill()
    private var sound: Sound? = null
    private var soundPlayback: SoundPlayback? = null
    private var soundPath: String? = null
    private var failedSoundPath: String? = null
    private var sprite: Sp2Asset? = null
    private var spritePlayback: FramePlayback? = null
    private var spritePath: String? = null
    private var failedSpritePath: String? = null

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
        title.setText("Content: $profileName (${entries.size} files)")
        content.actor = if (entries.isEmpty()) {
            Label(message ?: "No content found in this profile.", styles.label)
        } else {
            ContentBrowser(entries, profileName, styles, previewSelection = ::previewResource, loadThumbnail = thumbnails::load,
                clearThumbnails = thumbnails::clear, onSelect = ::selectResource, showSource = true)
        }
    }

    private fun selectResource(entry: VfsEntry?): Actor? {
        when (entry?.normalizedPath?.substringAfterLast('.')) {
            "wav" -> {
                clearSprite()
                if (ensureSound(entry)) soundPlayback!!.replay()
            }
            "sp2" -> {
                clearSound()
                if (ensureSprite(entry)) {
                    spritePlayback!!.stop()
                    spritePlayback!!.play()
                    return SpriteFrameImage(sprite!!, spritePlayback!!, Scaling.fit)
                }
            }
            else -> { clearSound(); clearSprite() }
        }
        return null
    }

    private fun previewResource(entry: VfsEntry?): Actor? {
        if (entry == null) {
            clearSound()
            clearSprite()
            return null
        }
        if (entry.normalizedPath.substringAfterLast('.') == "wav") {
            return if (ensureSound(entry)) SoundPreviewControls(sound!!, soundPlayback!!, styles.label, styles.button)
            else Label("Could not load sound.", styles.label)
        }
        if (entry.normalizedPath.substringAfterLast('.') == "sp2") {
            return if (ensureSprite(entry)) SpritePreview(sprite!!, spritePlayback!!, styles.label, styles.button)
            else Label("Could not load sprite.", styles.label)
        }
        if (entry.normalizedPath.substringAfterLast('.') in setOf("md2", "bsp")) {
            return try {
                if (entry.normalizedPath.endsWith(".bsp")) scene.openBsp(entry.normalizedPath)
                else scene.openMd2(entry.normalizedPath)
            } catch (error: Exception) {
                clearSprite()
                Gdx.app.error("Content browser", "Could not preview ${entry.displayPath}", error)
                Label(if (entry.normalizedPath.endsWith(".bsp")) "Could not load map." else "Could not load model.", styles.label)
            }
        }
        return previewImage(entry)
    }

    private fun ensureSound(entry: VfsEntry): Boolean {
        if (soundPath == entry.normalizedPath) return true
        if (failedSoundPath == entry.normalizedPath) return false
        clearSound()
        return try {
            sound = loadSound(entry)
            soundPlayback = SoundPlayback(sound!!)
            soundPath = entry.normalizedPath
            true
        } catch (error: Exception) {
            clearSound()
            failedSoundPath = entry.normalizedPath
            Gdx.app.error("Content browser", "Could not preview ${entry.displayPath}", error)
            false
        }
    }

    private fun clearSound() {
        soundPlayback?.stop()
        sound?.dispose()
        sound = null
        soundPlayback = null
        soundPath = null
        failedSoundPath = null
    }

    private fun ensureSprite(entry: VfsEntry): Boolean {
        if (spritePath == entry.normalizedPath) return true
        if (failedSpritePath == entry.normalizedPath) return false
        clearSprite()
        return try {
            previewAssets.load(entry.normalizedPath, Sp2Asset::class.java)
            previewAssets.finishLoading()
            sprite = previewAssets.get(entry.normalizedPath, Sp2Asset::class.java)
            spritePlayback = FramePlayback(sprite!!.frames.size)
            spritePath = entry.normalizedPath
            true
        } catch (error: Exception) {
            clearSprite()
            failedSpritePath = entry.normalizedPath
            Gdx.app.error("Content browser", "Could not preview ${entry.displayPath}", error)
            false
        }
    }

    private fun clearSprite() {
        scene.close()
        spritePlayback?.stop()
        sprite = null
        spritePlayback = null
        spritePath = null
        failedSpritePath = null
        // This manager belongs only to the browser; failed dependencies never clear game assets.
        previewAssets.clear()
    }

    override fun act(delta: Float) {
        soundPlayback?.update()
        spritePlayback?.update(delta)
        scene.act(delta)
        super.act(delta)
    }

    private fun previewImage(entry: VfsEntry?): Actor? {
        if (entry == null || entry.normalizedPath.substringAfterLast('.') !in setOf("pcx", "wal", "tga")) return null
        val texture = thumbnails.load(entry) ?: return Label("Could not load image.", styles.label)
        return Table().apply {
            add(ImagePreviewPane(Image(texture))).grow().row()
            add(Label("${texture.width} x ${texture.height} | Drag: pan | Wheel: zoom", styles.label).apply {
                setWrap(true)
            }).minWidth(0f).growX().pad(4f)
        }
    }

    override fun keyUp(keycode: Int): Boolean {
        if (keycode == Input.Keys.ESCAPE && (content.actor as? ContentBrowser)?.navigateBack() == true) return true
        return super.keyUp(keycode)
    }

    override fun keyDown(keycode: Int): Boolean {
        val playback = soundPlayback
        if (playback != null) {
            when (keycode) {
                Input.Keys.SPACE -> { playback.toggle(); return true }
                Input.Keys.S -> { playback.stop(); return true }
            }
        }
        (spritePlayback ?: scene.animation)?.let {
            when (keycode) {
                Input.Keys.SPACE -> { it.toggle(); return true }
                Input.Keys.LEFT -> { it.step(-1); return true }
                Input.Keys.RIGHT -> { it.step(1); return true }
            }
        }
        return super.keyDown(keycode)
    }

    /** Detach actors before releasing their borrowed textures; reopening supplies a new snapshot. */
    fun hide() {
        unfocusAll()
        content.actor = null
        clearSound()
        clearSprite()
        thumbnails.clear()
    }

    override fun dispose() {
        hide()
        previewAssets.dispose()
        super.dispose()
    }
}
