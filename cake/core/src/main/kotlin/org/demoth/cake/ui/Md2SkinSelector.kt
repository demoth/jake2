package org.demoth.cake.ui

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import org.demoth.cake.assets.md2.MAX_MD2_SKIN_TEXTURES
import org.demoth.cake.assets.md2.Md2Asset
import org.demoth.cake.assets.md2.Md2CustomData

/** Selects an already-bound texture on this model instance without loading or replacing assets. */
internal class Md2SkinSelector(model: Md2Asset, data: Md2CustomData, style: SelectBoxStyle) : SelectBox<String>(style) {
    constructor(model: Md2Asset, data: Md2CustomData, skin: Skin) : this(model, data, skin.get(SelectBoxStyle::class.java))

    init {
        val choices = model.skins.indices.take(MAX_MD2_SKIN_TEXTURES).map { index ->
            val name = model.skinNames.getOrNull(index)?.substringAfterLast('/') ?: "Default skin"
            "Skin ${index + 1}: $name"
        }
        setItems(*choices.toTypedArray())
        selectedIndex = data.skinIndex.coerceIn(0, choices.lastIndex)
        isDisabled = choices.size == 1
        addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) {
                data.skinIndex = selectedIndex
            }
        })
    }
}
