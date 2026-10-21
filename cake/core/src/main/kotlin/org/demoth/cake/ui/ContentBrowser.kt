package org.demoth.cake.ui

import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.SplitPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.Tree
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.Array as GdxArray
import com.badlogic.gdx.scenes.scene2d.ui.List as Scene2dList
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

/** Browse a resolved entry snapshot using the engine skin. Does not own VFS or preview resources. */
class ContentBrowser(entries: List<VfsEntry>, rootName: String, skin: Skin) : Table(skin) {
    private class FolderNode(val directory: ContentDirectory, skin: Skin) :
        Tree.Node<FolderNode, ContentDirectory, Label>(Label(directory.name, skin)) {
        init { value = directory }
    }

    private class Item(val directory: ContentDirectory? = null, val entry: VfsEntry? = null) {
        override fun toString(): String = directory?.let { "${it.name}/" }
            ?: entry!!.let { "${it.displayPath.replace('\\', '/').substringAfterLast('/')}  (${it.size} bytes)" }
    }

    private val tree = Tree<FolderNode, ContentDirectory>(skin)
    private val items = Scene2dList<Item>(skin)
    private val pathLabel = Label("/", skin).apply { setWrap(true) }
    private val selectionInfo = Label("Select a file to see its path and size.", skin).apply { setWrap(true) }
    private val nodes = mutableMapOf<String, FolderNode>()
    private val listScroll = scrollPane(items, skin)

    init {
        background = skin.getDrawable("window")
        pad(8f)
        items.selection.required = false
        tree.selection.setMultiple(false)
        tree.selection.required = true
        val treeScroll = scrollPane(tree, skin)
        val listing = Table().apply {
            defaults().left().growX()
            add(pathLabel).pad(8f).row()
            add(listScroll).grow().row()
            add(selectionInfo).pad(8f).row()
        }
        add(SplitPane(treeScroll, listing, false, skin).apply { splitAmount = 0.3f }).grow()

        val root = addFolder(ContentDirectory.fromEntries(entries, rootName), skin)
        tree.add(root)
        root.isExpanded = true
        tree.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) {
                tree.selectedValue?.let { showDirectory(it) }
            }
        })
        items.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) {
                val selected = items.selected
                selectionInfo.setText(selected?.entry?.let { "${it.displayPath}\n${it.size} bytes" }
                    ?: selected?.directory?.let { "${it.path}/" }
                    ?: "Select a file to see its path and size.")
            }
        })
        items.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent, x: Float, y: Float) {
                stage?.keyboardFocus = items
                if (tapCount == 2) {
                    items.selected?.directory?.let { directory ->
                        val node = nodes.getValue(directory.path)
                        node.expandTo()
                        node.isExpanded = true
                        tree.selection.set(node)
                    }
                }
            }
        })
        tree.selection.set(root)
    }

    private fun addFolder(directory: ContentDirectory, skin: Skin): FolderNode {
        val node = FolderNode(directory, skin)
        nodes[directory.path] = node
        directory.folders.values.forEach { node.add(addFolder(it, skin)) }
        return node
    }

    private fun showDirectory(directory: ContentDirectory) {
        pathLabel.setText(if (directory.path.isEmpty()) "/" else "/${directory.path}/")
        items.setItems(GdxArray<Item>().apply {
            directory.folders.values.forEach { add(Item(directory = it)) }
            directory.files.forEach { add(Item(entry = it)) }
        })
        items.selection.clear()
        listScroll.scrollY = 0f
        selectionInfo.setText("Select a file to see its path and size.")
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
