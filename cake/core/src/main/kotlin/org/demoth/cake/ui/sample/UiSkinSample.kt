package org.demoth.cake.ui.sample

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.List as UiList
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar
import com.badlogic.gdx.scenes.scene2d.ui.Slider
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.scenes.scene2d.ui.Tree
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.Scaling
import org.demoth.cake.ui.EngineUiStyle
import org.demoth.cake.ui.GameUiStyle
import org.demoth.cake.ui.MenuWidgetStyles
import org.demoth.cake.ui.UiViewport
import org.demoth.cake.ui.createEngineMenuWidgetStyles

/** Internal gallery. Owns its Stage/batch; all fonts, styles and drawables are borrowed. */
class UiSkinSample(
    referenceHeight: Float,
    private val engineSkin: Skin,
    private val gameStyle: GameUiStyle,
    private val onMode: (Boolean) -> Unit,
    private val onClose: () -> Unit,
) : Stage(UiViewport().apply { setReferenceHeight(referenceHeight) }) {
    private val engineStyles = createEngineMenuWidgetStyles(engineSkin)
    private val root = Table().apply {
        setFillParent(true)
        pad(16f)
        top()
    }
    private var scroll: ScrollPane? = null
    private var dropdown: SelectBox<String>? = null
    private var inputText = "Player / baseq2/maps/base1.bsp"
    private var passwordText = "secret"
    private var hintText = ""
    private var checked = true
    private var selectedRow = "baseq2"
    private var selectedChoice = "Medium"
    private var actionCount = 0
    private var sliderValue = 50f
    private class FolderNode(val path: String, style: Label.LabelStyle) :
        Tree.Node<FolderNode, String, Label>(Label(path.substringAfterLast('/'), style)) {
        init { value = path }
    }
    private val treeNodes = linkedMapOf<String, FolderNode>()
    private var treeScroll: ScrollPane? = null
    private var selectedFolder = "baseq2/players/male"

    init {
        resize(Gdx.graphics.width, Gdx.graphics.height)
        addActor(root)
    }

    fun resize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        viewport.update(width, height, true)
    }

    fun setReferenceHeight(height: Float) {
        if ((viewport as UiViewport).setReferenceHeight(height)) {
            resize(Gdx.graphics.width, Gdx.graphics.height)
        }
    }

    fun showMode(game: Boolean) {
        val scrollY = scroll?.scrollY ?: 0f
        val treeScrollY = treeScroll?.scrollY ?: 0f
        val expanded = if (treeNodes.isEmpty()) setOf("baseq2", "baseq2/players")
            else treeNodes.values.filter { it.isExpanded }.mapTo(mutableSetOf()) { it.path }
        dropdown?.hideScrollPane()
        // hideScrollPane normally fades out; discard the old popup before replacing its controls.
        dropdown?.scrollPane?.remove()
        unfocusAll()
        root.clearChildren()
        val styles = if (game) gameStyle.menuWidgets else engineStyles
        val content = Table().apply {
            background = styles.containerPanel
            defaults().growX().left().padBottom(3f)
        }
        val heading = when {
            !game -> "Engine UI sample"
            gameStyle is EngineUiStyle -> "Game: engine fallback (content unavailable)"
            else -> "Game UI sample"
        }
        val controls = Table().apply {
            defaults().padRight(8f)
            add(Label(heading, styles.label).apply { setWrap(true) }).growX().minWidth(0f).left()
            add(button("Engine", styles.button) { onMode(false) }).minWidth(90f)
            add(button("Game", styles.button) { onMode(true) }).minWidth(90f)
            add(button("Close", styles.button, onClose)).minWidth(90f)
        }
        content.add(controls).row()
        content.add(Label("Text: ABC xyz 0123456789 !? café …", styles.label).apply { setWrap(true) })
            .minWidth(0f).row()

        val result = Label("Clicks: $actionCount", styles.label)
        content.add(Table().apply {
            defaults().padRight(10f).left()
            add(button("Action", styles.button) { result.setText("Clicks: ${++actionCount}") })
            add(TextButton("Disabled", styles.button).apply { isDisabled = true })
            add(result).growX()
            add(TextButton("x", styles.checkBox).apply {
                isChecked = checked
                changed { checked = isChecked }
            })
            add(Label("Checkbox", styles.label))
        }).row()

        content.add(Table().apply {
            defaults().growX().minWidth(0f).uniformX().pad(4f)
            add(TextField(inputText, styles.textField).apply { changed { inputText = text } })
            add(TextField(hintText, styles.textField).apply {
                messageText = "Empty input hint"
                changed { hintText = text }
            }).row()
            add(TextField(passwordText, styles.textField).apply {
                isPasswordMode = true
                setPasswordCharacter('*')
                changed { passwordText = text }
            })
            add(TextField("Disabled input", styles.textField).apply { isDisabled = true })
        }).row()

        val list = UiList<String>(styles.list).apply {
            setItems("baseq2", "xatrix", "rogue")
            selected = selectedRow
            changed { selectedRow = selected }
        }
        val choice = SelectBox<String>(styles.selectBox).apply {
            setItems("Easy", "Medium", "Hard", "A longer dropdown choice")
            selected = selectedChoice
            changed { selectedChoice = selected }
        }
        dropdown = choice
        val panel = Table().apply {
            pad(8f)
            defaults().left().pad(3f)
            repeat(12) { add(Label("Scrollable row ${it + 1}", styles.label)).row() }
        }
        scroll = scrolling(panel, ScrollPane.ScrollPaneStyle(styles.scrollPane).apply {
            background = styles.containerPanel
        })
        val lower = Table().apply {
            defaults().growX().minWidth(0f).uniformX().top().pad(4f)
            add(Label("List", styles.label))
            add(Label("Dropdown", styles.label))
            add(Label("Panel / scrolling", styles.label)).row()
            add(Table().apply {
                background = styles.controlPanel
                add(list).growX().minWidth(0f)
            })
            add(Table().apply {
                defaults().growX().minWidth(0f).padBottom(8f)
                add(choice).row()
                add(SelectBox<String>(styles.selectBox).apply {
                    setItems("Disabled dropdown")
                    isDisabled = true
                })
            })
            add(scroll).height(112f)
        }
        content.add(lower).row()
        val progress = ProgressBar(0f, 100f, 1f, false, styles.progress).apply { value = sliderValue }
        val valueLabel = Label("${sliderValue.toInt()}%", styles.label)
        // Reserve the widest readout so dragging cannot change the slider's width.
        val percentageWidth = maxOf(48f, GlyphLayout(styles.label.font, "100%").width)
        val slider = Slider(0f, 100f, 1f, false, styles.slider).apply {
            value = sliderValue
            changed {
                sliderValue = value
                progress.value = value
                valueLabel.setText("${value.toInt()}%")
            }
        }
        content.add(Table().apply {
            defaults().left().pad(3f)
            add(Label("Slider", styles.label))
            add(slider).growX().minWidth(0f)
            add(valueLabel).width(percentageWidth).row()
            add(Label("Progress", styles.label))
            add(progress).growX().minWidth(0f)
            add(Label("", styles.label)).width(percentageWidth).row()
            add(Label("Disabled", styles.label))
            add(Slider(0f, 100f, 1f, false, styles.slider).apply {
                value = sliderValue
                isDisabled = true
            }).growX().minWidth(0f)
            add(Label("", styles.label)).width(percentageWidth)
        }).row()
        content.add(Table().apply {
            defaults().top().pad(3f)
            add(treeSample(styles, expanded)).width(300f)
            add(Table().apply {
                defaults().left().pad(3f)
                add(Label("Separators", styles.label)).colspan(2).row()
                add(Image(styles.separatorHorizontal).apply { setScaling(Scaling.stretchX) }).growX().minWidth(24f).height(16f)
                add(Image(styles.separatorVertical).apply { setScaling(Scaling.stretchY) }).width(16f).height(48f)
            }).growX()
        }).growX().row()
        root.add(content).growX().maxWidth(900f).minWidth(0f)
        root.validate()
        scroll!!.scrollY = scrollY
        scroll!!.updateVisualScroll()
        treeScroll!!.scrollY = treeScrollY
        treeScroll!!.updateVisualScroll()
        scrollFocus = scroll
    }

    private fun treeSample(styles: MenuWidgetStyles, expanded: Set<String>): Table {
        val tree = Tree<FolderNode, String>(styles.tree).apply {
            selection.required = true
            selection.setMultiple(false)
            setPadding(4f)
            setIndentSpacing(12f)
        }
        treeNodes.clear()
        for (path in listOf(
            "baseq2", "baseq2/maps", "baseq2/maps/unit1", "baseq2/maps/unit2",
            "baseq2/models", "baseq2/models/weapons", "baseq2/models/monsters",
            "baseq2/players", "baseq2/players/male", "baseq2/players/female", "baseq2/players/cyborg",
            "baseq2/textures", "baseq2/textures/e1u1", "baseq2/sound", "baseq2/sound/world",
        )) {
            val node = FolderNode(path, styles.label)
            treeNodes[path] = node
            val parent = treeNodes[path.substringBeforeLast('/', "")]
            if (parent == null) tree.add(node) else parent.add(node)
            node.isExpanded = path in expanded
        }
        tree.selection.set(treeNodes.getValue(selectedFolder))
        val selected = Label(selectedFolder, styles.label).apply { setWrap(true) }
        tree.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) {
                tree.selectedValue?.let { selectedFolder = it; selected.setText(it) }
            }
        })
        treeScroll = scrolling(tree, ScrollPane.ScrollPaneStyle(styles.scrollPane).apply {
            background = styles.containerPanel
        })
        return Table().apply {
            defaults().left().growX().minWidth(0f).padBottom(3f)
            add(Label("Tree", styles.label)).row()
            add(treeScroll).height(112f).row()
            add(selected)
        }
    }

    private fun button(text: String, style: TextButton.TextButtonStyle, action: () -> Unit) =
        TextButton(text, style).apply { changed(action) }

    private fun scrolling(content: Actor, style: ScrollPane.ScrollPaneStyle) = ScrollPane(content, style).apply {
        setScrollingDisabled(true, false)
        setFadeScrollBars(false)
        setFlickScroll(false)
        addListener(object : InputListener() {
            override fun mouseMoved(event: InputEvent, x: Float, y: Float): Boolean {
                stage?.scrollFocus = this@apply
                event.stop()
                return false
            }
        })
    }

    private fun Actor.changed(action: () -> Unit) {
        addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) = action()
        })
    }
}
