package org.demoth.cake.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import org.demoth.cake.assets.md2.Md2SkinTexturesAttribute
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.Renderable
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import org.demoth.cake.assets.CakeShaderProvider
import org.demoth.cake.assets.getLoaded
import org.demoth.cake.assets.md2.Md2Asset
import org.demoth.cake.assets.md2.Md2CustomData
import org.demoth.cake.assets.md2.Md2Loader
import org.demoth.cake.assets.md2.Md2Shader
import org.demoth.cake.assets.bsp.BspMapAsset
import org.demoth.cake.assets.bsp.BspWorldBatchRenderer
import org.demoth.cake.md2VertexShader
import org.demoth.cake.md2FragmentShader

/** Owns the active 3D preview's asset reference, renderer and render target. The host owns the manager. */
internal class SceneResourcePreview(private val assets: AssetManager, private val styles: ContentBrowserStyles) {
    private val resources = PreviewResources(assets)
    private var externalSkin: PreviewResources? = null
    val path get() = resources.path
    var actor: Actor? = null
        private set
    var md2: Md2Asset? = null
        private set
    var animation: FramePlayback? = null
        private set
    private var instance: ModelInstance? = null
    private var batch: ModelBatch? = null
    var map: BspMapAsset? = null
        private set
    private var world: BspWorldBatchRenderer? = null
    private var visibleSurfaces = BooleanArray(0)
    private val instances = mutableListOf<ModelInstance>()
    private var time = 0f

    fun openBsp(path: String): Actor {
        close()
        try {
            val loaded = resources.load(path, BspMapAsset::class.java)
            map = loaded
            batch = resources.own(ModelBatch())
            world = resources.own(BspWorldBatchRenderer(loaded.worldRenderData, loaded.worldBatchData,
                loaded.lightmapAtlasPages, assets))
            visibleSurfaces = BooleanArray(loaded.worldRenderData.surfaces.size) { true }
            val hidden = loaded.entities.filter {
                it["classname"]?.startsWith("trigger_") == true || it["classname"] == "func_areaportal"
            }.mapNotNull { it["model"]?.removePrefix("*")?.toIntOrNull() }.toSet()
            loaded.models.forEachIndexed { index, model -> if (index !in hidden) instances += ModelInstance(model) }
            val pane = resources.own(BspPreviewPane(loaded, ::render))
            return Table().apply {
                add(pane).grow().row()
                add(Label("Surfaces: ${loaded.worldRenderData.surfaces.size} | Brush models: ${loaded.inlineRenderData.size}",
                    styles.label).apply { setWrap(true) }).minWidth(0f).growX().pad(4f).row()
                add(Label("Click to focus | WASD: fly | Q/E: down/up | Drag: look", styles.label).apply {
                    setWrap(true)
                }).minWidth(0f).growX().pad(4f)
            }.also { actor = it }
        } catch (error: Exception) {
            close()
            throw error
        }
    }

    fun openMd2(path: String, externalSkins: List<String>? = null): Actor {
        close()
        try {
            val model = resources.load(path, Md2Asset::class.java, Md2Loader.Parameters(
                loadEmbeddedSkins = externalSkins == null, useDefaultSkinIfMissing = true))
            md2 = model
            val data = Md2CustomData.empty()
            val modelInstance = ModelInstance(model.model).apply { userData = data }
            instance = modelInstance
            val playback = FramePlayback(model.frames)
            animation = playback
            val shader = Md2Shader(modelInstance.getRenderable(Renderable()), DefaultShader.Config(
                assets.getLoaded(md2VertexShader), assets.getLoaded(md2FragmentShader)))
            batch = resources.own(ModelBatch(CakeShaderProvider(shader)))
            shader.init()
            val pane = resources.own(Md2PreviewPane(model.bounds, ::render))
            return Table().apply {
                add(pane).grow().row()
                if (externalSkins == null) {
                    add(Md2SkinSelector(model, data, styles.selectBox)).minWidth(0f).growX().pad(4f).row()
                } else {
                    add(externalSkinControls(externalSkins)).minWidth(0f).growX().pad(4f).row()
                }
                add(FramePlaybackControls(playback, styles.label, styles.button) {
                    model.frameNames[playback.frameIndex]
                }).growX().row()
                add(Label("Drag: orbit | Wheel: zoom", styles.label)).pad(4f)
            }.also { actor = it }
        } catch (error: Exception) {
            close()
            throw error
        }
    }

    private fun externalSkinControls(paths: List<String>): Actor {
        if (paths.isEmpty()) return Label("No player skins found; showing default skin.", styles.label)
        val status = Label("", styles.label).apply { setWrap(true) }
        val selector = SelectBox<String>(styles.selectBox).apply {
            setItems(*paths.map { it.substringAfterLast('/') }.toTypedArray())
            maxListCount = 12
            isDisabled = paths.size == 1
        }
        var selected = -1
        fun applySelection() {
            val error = selectExternalSkin(paths[selector.selectedIndex])
            if (error == null) {
                selected = selector.selectedIndex
                status.setText("")
            } else {
                status.setText(error + if (selected < 0) " Showing default skin." else " Keeping previous skin.")
                if (selected >= 0) {
                    selector.selection.setProgrammaticChangeEvents(false)
                    selector.selectedIndex = selected
                    selector.selection.setProgrammaticChangeEvents(true)
                }
            }
        }
        selector.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) = applySelection()
        })
        applySelection()
        return Table().apply {
            add(selector).minWidth(0f).growX().row()
            add(status).minWidth(0f).growX()
        }
    }

    /** Acquire before rebinding, then release the previous skin. Geometry, camera and playback stay intact. */
    fun selectExternalSkin(path: String): String? {
        val modelInstance = checkNotNull(instance) { "Open an MD2 preview before selecting a skin" }
        if (externalSkin?.path == path) return null
        val replacement = PreviewResources(assets)
        val texture = try {
            // PCX skins have no AssetManager dependencies; a failed texture load leaves the model intact.
            replacement.load(path, Texture::class.java)
        } catch (error: Exception) {
            replacement.close()
            Gdx.app.error("Model preview", "Could not load skin $path", error)
            return "Could not load skin ${path.substringAfterLast('/')}."
        }
        modelInstance.materials.forEach { it.set(Md2SkinTexturesAttribute(listOf(texture))) }
        (modelInstance.userData as Md2CustomData).skinIndex = 0
        val previous = externalSkin
        externalSkin = replacement
        previous?.close()
        return null
    }

    fun act(delta: Float) {
        time += delta
        animation?.let { playback ->
            playback.update(delta)
            (instance?.userData as? Md2CustomData)?.apply {
                frame1 = playback.frameIndex
                frame2 = playback.nextFrameIndex
                interpolation = playback.interpolation
            }
        }
    }

    fun render(camera: Camera) {
        val currentTime = (time * 1000f).toInt()
        world?.render(camera, visibleSurfaces, currentTime, { 1f }, emptyList())
        batch?.let { renderer ->
            renderer.begin(camera)
            instance?.let { renderer.render(it) }
            instances.forEach { renderer.render(it) }
            renderer.end()
        }
        world?.renderTranslucent(camera, visibleSurfaces, currentTime)
    }

    fun close() {
        actor = null
        resources.close()
        externalSkin?.close()
        externalSkin = null
        animation = null
        instance = null
        batch = null
        md2 = null
        map = null
        world = null
        visibleSurfaces = BooleanArray(0)
        instances.clear()
        time = 0f
    }
}
