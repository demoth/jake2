package org.demoth.cake.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.Scaling
import jake2.qcommon.vfs.VfsEntry
import org.demoth.cake.assets.Sp2Asset
import org.demoth.cake.audio.SoundPlayback

/** Owns active media and its private manager; borrows the folder image cache from the host. */
internal class ContentPreview(
    private val styles: ContentBrowserStyles,
    private val thumbnails: ImageThumbnails,
    private val previewAssets: AssetManager,
    private val loadSound: (VfsEntry) -> Sound,
) : Disposable {
    var contentPaths: List<String> = emptyList()

    private val scene = SceneResourcePreview(previewAssets, styles)
    private var sound: Sound? = null
    private var soundPlayback: SoundPlayback? = null
    private var soundPath: String? = null
    private var failedSoundPath: String? = null
    private var sprite: Sp2Asset? = null
    private var spritePlayback: FramePlayback? = null
    private var spritePath: String? = null
    private var failedSpritePath: String? = null

    fun select(entry: VfsEntry?): Actor? {
        when (entry?.normalizedPath?.substringAfterLast('.')) {
            "wav" -> {
                clearVisualPreview()
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
            else -> { clearSound(); clearVisualPreview() }
        }
        return null
    }

    fun open(entry: VfsEntry?): Actor? {
        if (entry == null) {
            clearSound()
            clearVisualPreview()
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
                else scene.openMd2(entry.normalizedPath, playerModelSkinPaths(entry.normalizedPath, contentPaths))
            } catch (error: Exception) {
                clearVisualPreview()
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
        clearVisualPreview()
        return try {
            previewAssets.load(entry.normalizedPath, Sp2Asset::class.java)
            previewAssets.finishLoading()
            sprite = previewAssets.get(entry.normalizedPath, Sp2Asset::class.java)
            spritePlayback = FramePlayback(sprite!!.frames.size)
            spritePath = entry.normalizedPath
            true
        } catch (error: Exception) {
            clearVisualPreview()
            failedSpritePath = entry.normalizedPath
            Gdx.app.error("Content browser", "Could not preview ${entry.displayPath}", error)
            false
        }
    }

    private fun clearVisualPreview() {
        scene.close()
        spritePlayback?.stop()
        sprite = null
        spritePlayback = null
        spritePath = null
        failedSpritePath = null
        // This manager belongs only to the browser; failed dependencies never clear game assets.
        previewAssets.clear()
    }

    fun act(delta: Float) {
        soundPlayback?.update()
        spritePlayback?.update(delta)
        scene.act(delta)
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

    fun keyDown(keycode: Int): Boolean {
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
        return false
    }

    fun clear() {
        clearSound()
        clearVisualPreview()
    }

    override fun dispose() {
        clear()
        previewAssets.dispose()
    }
}
