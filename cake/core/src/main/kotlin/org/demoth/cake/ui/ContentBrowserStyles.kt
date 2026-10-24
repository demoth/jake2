package org.demoth.cake.ui

import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.SplitPane
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.Tree
import com.badlogic.gdx.scenes.scene2d.utils.Drawable

/** Borrowed resources; the host owns the Skin and resolved content style. */
data class ContentBrowserStyles(
    val label: Label.LabelStyle,
    val button: TextButton.TextButtonStyle,
    val tree: Tree.TreeStyle,
    val scrollPane: ScrollPane.ScrollPaneStyle,
    val splitPane: SplitPane.SplitPaneStyle,
    val tile: Button.ButtonStyle,
    val background: Drawable,
    val selectBox: SelectBox.SelectBoxStyle,
) {
    companion object {
        /** The standalone viewer keeps its native engine font and artwork sizes. */
        fun fromSkin(skin: Skin) = ContentBrowserStyles(
            skin.get(Label.LabelStyle::class.java),
            skin.get(TextButton.TextButtonStyle::class.java),
            skin.get(Tree.TreeStyle::class.java),
            skin.get(ScrollPane.ScrollPaneStyle::class.java),
            skin.get("default-horizontal", SplitPane.SplitPaneStyle::class.java),
            tileStyle(skin.getDrawable("selection")),
            skin.getDrawable("window"),
            skin.get(SelectBox.SelectBoxStyle::class.java),
        )

        /** Client controls and splitter artwork follow the active menu theme. */
        fun fromMenu(widgets: MenuWidgetStyles) = ContentBrowserStyles(
            widgets.label,
            widgets.button,
            widgets.tree,
            ScrollPane.ScrollPaneStyle(widgets.scrollPane).apply { background = null },
            SplitPane.SplitPaneStyle(widgets.separatorVertical),
            tileStyle(widgets.tree.selection),
            widgets.containerPanel,
            widgets.selectBox,
        )

        private fun tileStyle(selection: Drawable) = Button.ButtonStyle().apply {
            over = selection
            down = selection
            checked = selection
        }
    }
}
