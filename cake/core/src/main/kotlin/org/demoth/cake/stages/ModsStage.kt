package org.demoth.cake.stages

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.assets.loaders.FileHandleResolver
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.utils.viewport.Viewport
import ktx.actors.onChange
import ktx.actors.onClick
import org.demoth.cake.assets.CakeVfsAssetSource
import org.demoth.cake.assets.ConvertingSoundLoader
import org.demoth.cake.profile.ProfileArchives
import org.demoth.cake.ui.*
import org.demoth.cake.ui.menu.*

/** Owns an isolated inspection context; Include/Exclude are application intents, never mounts here. */
class ModsStage(
    viewport: Viewport,
    menuEventBus: MenuEventBus,
    style: GameUiStyle,
    thumbnailAssets: AssetManager,
    createPreviewAssets: (FileHandleResolver) -> AssetManager,
) : BackNavigableMenuStage(viewport, menuEventBus, MenuIntent.OpenProfileEditor) {
    private val widgets = style.menuWidgets
    private val styles = ContentBrowserStyles.fromMenu(widgets)
    private val source = CakeVfsAssetSource()
    private val resolver = object : org.demoth.cake.assets.TextureCandidateResolver {
        override fun textureCandidates() = source.listEntries().map { it.normalizedPath }
        override fun resolve(path: String) =
            if (path == "q2palette.bin" || path.startsWith("shaders/")) {
                Gdx.files.classpath(path).takeIf { it.exists() } ?: Gdx.files.internal(path)
            } else source.resolve(path)
    }
    private val thumbnails = ImageThumbnails(thumbnailAssets, resolver)
    private val media = ContentPreview(styles, thumbnails, createPreviewAssets(resolver)) { entry ->
        val file = requireNotNull(source.resolve(entry.normalizedPath)) { "Missing archive sound" }
        ConvertingSoundLoader(resolver).loadSync(thumbnailAssets, entry.normalizedPath, file, null)
    }
    private val title = Label("Mods", styles.label)
    private val archiveTitle = Label("Select an archive to inspect it.", styles.label).apply { setWrap(true) }
    private val summary = Label("", styles.label).apply { setWrap(true) }
    private val notice = Label("", styles.label).apply { setWrap(true) }
    private val archiveList = Table().apply { top(); left() }
    private val content = Container<Actor>().fill()
    private val automatic = TextButton("x", widgets.checkBox)
    private var rendering = false
    private var rendered: ModsState? = null
    private var inspected: List<Any?>? = null

    init {
        automatic.onChange {
            if (!rendering) current().profile?.let {
                menuEventBus.postIntent(MenuIntent.SetModAutomount(it.id, automatic.isChecked))
            }
        }
        val left = Table().apply {
            top()
            add(Label("Archives", styles.label)).left().growX()
            add(createMenuButton("Refresh", styles.button) { menuEventBus.postIntent(MenuIntent.RefreshMods) }).row()
            add(ScrollPane(archiveList, styles.scrollPane).apply {
                setFadeScrollBars(false); setScrollingDisabled(true, false)
            }).colspan(2).grow().row()
        }
        val right = Table().apply {
            add(archiveTitle).growX().left().row()
            add(summary).growX().left().padTop(4f).padBottom(8f).row()
            add(content).grow()
        }
        addActor(Table().apply {
            setFillParent(true); pad(16f)
            add(Table().apply {
                add(title).growX().left()
                add(automatic).size(24f).padRight(8f)
                add(Label("Automatically load archives", styles.label)).padRight(16f)
                add(createBackButton(style = styles.button))
            }).growX().padBottom(8f).row()
            add(notice).growX().left().padBottom(8f).row()
            add(SplitPane(left, right, false, styles.splitPane).apply {
                splitAmount = 0.28f; setMinSplitAmount(0.18f); setMaxSplitAmount(0.5f)
            }).grow()
        })
    }

    private fun current() = menuEventBus.latestState().mods

    override fun act(delta: Float) {
        val state = current()
        if (state != rendered) show(state)
        media.act(delta)
        super.act(delta)
    }

    private fun show(state: ModsState) {
        rendering = true
        try {
            title.setText("Mods: ${state.profile?.id ?: "<unset>"}")
            automatic.isChecked = state.automount
            automatic.isDisabled = !state.canEdit
            notice.setText(state.statusMessage.ifBlank {
                when {
                    state.profile == null -> "Select and apply a profile to manage its mods."
                    !state.canEdit -> "Disconnect and stop the local game to change mods."
                    state.automount -> "Archives load automatically. Disable this setting to select archives with the checkboxes."
                    else -> "Included archives override base PAKs. Later filenames win within each game directory."
                }
            })
            archiveList.clearChildren()
            for (archive in state.archives) {
                val checkbox = TextButton("x", widgets.checkBox).apply {
                    name = "mod-checkbox:${archive.path}"
                    isChecked = archive.included
                    isDisabled = !state.canEdit || state.automount || archive.required || (archive.missing && !archive.included)
                    onChange {
                        if (!rendering) state.profile?.let {
                            menuEventBus.postIntent(MenuIntent.SetModIncluded(it.id, archive.path, isChecked))
                        }
                    }
                }
                val suffix = if (archive.missing) " (missing)" else ""
                val row = TextButton(archive.path + suffix, styles.button).apply {
                    onClick {
                        state.profile?.let { menuEventBus.postIntent(MenuIntent.SelectMod(it.id, archive.path)) }
                    }
                    isChecked = archive.path == state.selectedArchive
                    label.setEllipsis(true)
                }
                archiveList.add(checkbox).size(24f).padRight(6f)
                archiveList.add(row).minWidth(0f).growX().padBottom(4f).row()
            }
            if (state.archives.isEmpty()) archiveList.add(Label("No archives found.", styles.label)).left()
            val key = listOf(state.profile?.basedir, state.profile?.gamemod, state.selectedArchive, state.revision)
            if (key != inspected) {
                inspect(state)
                inspected = key
            }
            rendered = state
        } finally { rendering = false }
    }

    private fun inspect(state: ModsState) {
        clearPreview()
        val profile = state.profile
        val path = state.selectedArchive
        archiveTitle.setText(path?.let { "Archive: $it" } ?: "Select an archive to inspect it.")
        summary.setText("")
        if (profile == null || path == null) return
        try {
            val file = ProfileArchives.resolve(profile, path)
            val entries = source.configureArchive(file.toString())
            media.contentPaths = entries.map { it.normalizedPath }
            val counts = entries.groupingBy { it.normalizedPath.substringBefore('/') }.eachCount()
            summary.setText("${entries.size} files | " + listOf("maps", "textures", "models", "sound", "pics")
                .joinToString(" | ") { "$it: ${counts[it] ?: 0}" })
            val browser = ContentBrowser(entries, file.fileName.toString(), styles,
                previewSelection = media::open, loadThumbnail = thumbnails::load,
                clearThumbnails = thumbnails::clear, onSelect = media::select)
            content.actor = browser
            ProfileArchives.readme(entries)?.let { browser.openPath(it.normalizedPath) }
        } catch (error: Exception) {
            clearPreview()
            content.actor = Label("Could not inspect archive: ${error.message}", styles.label).apply { setWrap(true) }
        }
    }

    private fun clearPreview() {
        unfocusAll()
        content.actor = null
        media.clear()
        media.contentPaths = emptyList()
        thumbnails.clear()
        source.configure(null, null, false)
    }

    fun hide() {
        clearPreview()
        rendered = null
        inspected = null
    }

    override fun keyUp(keycode: Int): Boolean {
        if (keycode == Input.Keys.ESCAPE && (content.actor as? ContentBrowser)?.navigateBack() == true) return true
        return super.keyUp(keycode)
    }

    override fun keyDown(keycode: Int): Boolean =
        (content.actor as? ContentBrowser)?.keyDown(keycode) == true || media.keyDown(keycode) || super.keyDown(keycode)

    override fun dispose() {
        hide()
        media.dispose()
        super.dispose()
    }
}
