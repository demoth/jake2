package org.demoth.cake.ui

import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Scaling
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.SplitPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.Tree
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import jake2.qcommon.vfs.VfsEntry

/** Folder hierarchy derived from a resolved VFS snapshot; files retain their original metadata. */
internal class ContentDirectory(val path: String, val name: String) {
    val folders = sortedMapOf<String, ContentDirectory>()
    val files = mutableListOf<VfsEntry>()

    companion object {
        fun fromEntries(entries: List<VfsEntry>, rootName: String): ContentDirectory {
            val root = ContentDirectory("", rootName)
            entries.sortedBy { it.normalizedPath }.forEach { entry ->
                val parts = entry.normalizedPath.split('/')
                val displayParts = entry.displayPath.replace('\\', '/').split('/').filter { it.isNotEmpty() }
                var directory = root
                parts.dropLast(1).forEachIndexed { index, part ->
                    val path = if (directory.path.isEmpty()) part else "${directory.path}/$part"
                    directory = directory.folders.getOrPut(part) {
                        ContentDirectory(path, displayParts.getOrElse(index) { part })
                    }
                }
                directory.files += entry
            }
            return root
        }
    }
}

/**
 * Browse a resolved entry snapshot. The host owns preview and thumbnail resources.
 * [previewSelection] replaces/releases the full preview; null clears it.
 * [retainThumbnails] releases textures outside the visible set; [loadThumbnail] borrows
 * a texture from the host, which keeps it alive until the next retain call excludes it.
 */
class ContentBrowser(
    entries: List<VfsEntry>, rootName: String, skin: Skin,
    private val previewSelection: (VfsEntry?) -> Actor? = { null },
    private val loadThumbnail: (VfsEntry) -> Texture? = { null },
    private val retainThumbnails: (Set<String>) -> Unit = {},
) : Table(skin) {
    private class FolderNode(val directory: ContentDirectory, skin: Skin) :
        Tree.Node<FolderNode, ContentDirectory, Label>(Label(directory.name, skin)) {
        init { value = directory }
    }

    private class Tile(val directory: ContentDirectory?, val entry: VfsEntry?, skin: Skin) : Button(skin, "toggle") {
        val thumbnail = entry?.normalizedPath?.substringAfterLast('.') in setOf("pcx", "wal", "tga")
        val placeholder = Label(if (directory != null) "Folder" else if (thumbnail) "…" else
            entry!!.normalizedPath.substringAfterLast('.').uppercase(), skin).apply {
            setEllipsis(true)
            setAlignment(Align.center)
        }
        val picture = Container<Actor>(placeholder).apply { fill() }
        var attempted = false

        init {
            val name = directory?.let { "${it.name}/" } ?: entry!!.displayPath.replace('\\', '/').substringAfterLast('/')
            add(picture).size(128f, 96f).row()
            add(Label(name, skin).apply { setEllipsis(true) }).width(128f).padTop(4f)
            pad(6f)
        }

        fun clearThumbnail() {
            picture.actor = placeholder
            attempted = false
        }
    }

    private val tree = Tree<FolderNode, ContentDirectory>(skin)
    private val grid = HorizontalGroup().apply { wrap(); top(); left(); space(8f); wrapSpace(8f); pad(8f) }
    private val tiles = mutableListOf<Tile>()
    private val pathLabel = Label("/", skin).apply { setWrap(true) }
    private val selectionInfo = Label("Select a file to see its path and size.", skin).apply { setWrap(true) }
    private val nodes = mutableMapOf<String, FolderNode>()
    private val gridScroll = scrollPane(grid, skin).apply { setScrollingDisabled(true, false) }
    private val content = Container<Actor>(gridScroll).apply { fill() }
    private val backButton = TextButton("Back to folder", skin).apply { isVisible = false }

    init {
        background = skin.getDrawable("window")
        pad(8f)
        tree.selection.setMultiple(false)
        tree.selection.required = true
        val listing = Table().apply {
            defaults().left().growX()
            add(Table().apply {
                add(pathLabel).growX().left()
                add(backButton).padLeft(8f)
            }).pad(8f).row()
            add(content).grow().row()
            add(selectionInfo).pad(8f).row()
        }
        add(SplitPane(scrollPane(tree, skin), listing, false, skin).apply { splitAmount = 0.3f }).grow()

        val root = addFolder(ContentDirectory.fromEntries(entries, rootName), skin)
        tree.add(root)
        root.isExpanded = true
        tree.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) {
                tree.selectedValue?.let { showDirectory(it) }
            }
        })
        backButton.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) {
                previewSelection(null)
                content.actor = gridScroll
                backButton.isVisible = false
            }
        })
        tree.selection.set(root)
    }

    override fun act(delta: Float) {
        super.act(delta)
        if (content.actor !== gridScroll || stage == null) return
        validate()
        gridScroll.validate()
        grid.validate()
        // Compare in grid coordinates, including the ScrollPane's visual (smoothed) scroll.
        val top = grid.height - gridScroll.visualScrollY
        val bottom = top - gridScroll.scrollHeight
        val visible = tiles.filter { it.thumbnail && it.y < top && it.y + it.height > bottom }.toSet()
        tiles.filter { it.thumbnail && it !in visible }.forEach { it.clearThumbnail() }
        retainThumbnails(visible.map { it.entry!!.normalizedPath }.toSet())
        // Existing PCX/WAL loaders are synchronous: decode at most one image per frame.
        visible.firstOrNull { !it.attempted }?.let { tile ->
            tile.attempted = true
            val texture = loadThumbnail(tile.entry!!)
            tile.picture.actor = texture?.let { Image(it).apply { setScaling(Scaling.fit) } }
                ?: tile.placeholder.apply { setText("Unavailable") }
        }
    }

    private fun addFolder(directory: ContentDirectory, skin: Skin): FolderNode {
        val node = FolderNode(directory, skin)
        nodes[directory.path] = node
        directory.folders.values.forEach { node.add(addFolder(it, skin)) }
        return node
    }

    private fun showDirectory(directory: ContentDirectory) {
        previewSelection(null)
        tiles.forEach { it.clearThumbnail() }
        retainThumbnails(emptySet())
        content.actor = gridScroll
        backButton.isVisible = false
        pathLabel.setText(if (directory.path.isEmpty()) "/" else "/${directory.path}/")
        grid.clearChildren()
        tiles.clear()
        directory.folders.values.forEach { addTile(it, null) }
        directory.files.forEach { addTile(null, it) }
        gridScroll.scrollY = 0f
        gridScroll.updateVisualScroll()
        selectionInfo.setText("Select a file to see its path and size.")
    }

    private fun addTile(directory: ContentDirectory?, entry: VfsEntry?) {
        val tile = Tile(directory, entry, skin)
        tiles += tile
        grid.addActor(tile)
        tile.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent, x: Float, y: Float) {
                tiles.forEach { it.isChecked = it === tile }
                selectionInfo.setText(entry?.let { "${it.displayPath}\n${it.size} bytes" } ?: "${directory!!.path}/")
                if (tapCount != 2) return
                if (directory != null) {
                    val node = nodes.getValue(directory.path)
                    node.expandTo()
                    node.isExpanded = true
                    tree.selection.set(node)
                } else {
                    val preview = previewSelection(entry) ?: return
                    // Clear borrowed drawables before releasing their textures.
                    tiles.forEach { it.clearThumbnail() }
                    retainThumbnails(emptySet())
                    content.actor = scrollPane(preview, skin)
                    backButton.isVisible = true
                }
            }
        })
    }

    private fun scrollPane(actor: Actor, skin: Skin) = ScrollPane(actor, skin).apply {
        setFadeScrollBars(false)
        setFlickScroll(false)
        addListener(object : InputListener() {
            override fun mouseMoved(event: InputEvent, x: Float, y: Float): Boolean {
                stage?.scrollFocus = this@apply
                return false
            }
        })
    }
}
