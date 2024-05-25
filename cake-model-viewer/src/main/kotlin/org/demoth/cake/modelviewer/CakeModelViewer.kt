package org.demoth.cake.modelviewer

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.GL20.GL_TRIANGLES
import com.badlogic.gdx.graphics.VertexAttributes.Usage
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight
import com.badlogic.gdx.graphics.g3d.loader.G3dModelLoader
import com.badlogic.gdx.graphics.g3d.loader.ObjLoader
import com.badlogic.gdx.graphics.g3d.model.Node
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.UBJsonReader
import jake2.qcommon.filesystem.PCX
import jake2.qcommon.filesystem.WAL
import ktx.graphics.use
import java.io.File

private const val GRID_SIZE = 16f
private const val GRID_DIVISIONS = 8

private val SUPPORTED_FORMATS = listOf(".bsp", ".pcx", ".md2", ".wal")

/** [com.badlogic.gdx.ApplicationListener] implementation shared by all platforms.  */
class CakeModelViewer(val args: Array<String>) : ApplicationAdapter() {
    private lateinit var batch: SpriteBatch
    private var image: Texture? = null
    private lateinit var modelBatch: ModelBatch
    private lateinit var camera: Camera
    private val models: MutableList<ModelInstance> = mutableListOf()
    private lateinit var environment: Environment


    override fun create() {

        if (args.isEmpty() || SUPPORTED_FORMATS.none { args.first().endsWith(it) }) {
            println("Usage: provide $SUPPORTED_FORMATS file as the first argument")
            Gdx.app.exit()
        }
        val file = File(args[0])
        if (!file.exists() || !file.canRead()) {
            println("File $file does not exist or is unreadable")
            Gdx.app.exit()
        }

        when (file.extension) {
            "pcx" -> {
                image = Texture(PCXTextureData(fromPCX(PCX(file.readBytes()))))
            }
            "wal" -> {
                image = Texture(WalTextureData(fromWal(WAL(file.readBytes()), readPaletteFile("assets/q2palette.bin"))))
            }
            "md2" -> {
                models.add(Md2ModelLoader().loadMd2Model(file).transformQ2toLibgdx())
                models.add(createOriginArrows(GRID_SIZE))
                models.add(createGrid(GRID_SIZE, GRID_DIVISIONS))
            }
            "bsp" -> {
    //            models.add(BspLoader().loadBSPModelWireFrame(file).transformQ2toLibgdx())
                models.add(BspLoader().loadBspModelTextured(file).transformQ2toLibgdx())
                models.add(createOriginArrows(GRID_SIZE))
                models.add(createGrid(GRID_SIZE, GRID_DIVISIONS))
            }
        }

        batch = SpriteBatch()

        modelBatch = ModelBatch()
        camera = PerspectiveCamera(90f, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
        camera.position.set(0f, 0f, -50f);
        camera.lookAt(0f, 0f, 0f);
        camera.near = 0.1f
        camera.far = 4096f

        Gdx.input.inputProcessor = CameraInputController(camera).also {
            it.scrollFactor = -1.5f
            it.translateUnits = 100f
        }
        modelBatch = ModelBatch()

        environment = Environment()
        environment.set(ColorAttribute(ColorAttribute.AmbientLight, 0.8f, 0.8f, 0.8f, 1f))
        environment.add(DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f))
    }

    /**
     * Create a model programmatically using the model builder
     */
    private fun createModel(): Model {
        val modelBuilder = ModelBuilder()
        val box = modelBuilder.createBox(
            2f, 2f, 2f,
            Material(
                ColorAttribute.createDiffuse(Color.BLUE)
//                todo: add texture and check coords
            ),
            (Usage.Position or Usage.Normal).toLong()
        )
        return box
    }

    /**
     * Load an .obj model. todo: try using a material
     */
    private fun loadObjModel(): ModelInstance {
        val suzanneModel = ObjLoader().loadModel(Gdx.files.internal("suzanne.obj"))
        val suzanne = ModelInstance(suzanneModel)
        suzanne.transform.translate(0f, 2f, 0f)
        return suzanne
    }

    /**
     * Example of building the model with the mesh builder class (from geometric shapes)
     */
    private fun loadMeshModel(): ModelInstance {
        val modelBuilder = ModelBuilder()
        modelBuilder.begin()
        var meshBuilder =
            modelBuilder.part("part1", GL_TRIANGLES, (Usage.Position or Usage.Normal).toLong(), Material())
        meshBuilder.cone(5f, 5f, 5f, 10)
        val node: Node = modelBuilder.node()
        node.translation.set(10f, 0f, 0f)
        meshBuilder = modelBuilder.part("part2", GL_TRIANGLES, (Usage.Position or Usage.Normal).toLong(), Material())
        meshBuilder.sphere(5f, 5f, 5f, 10, 10)
        val model = modelBuilder.end()
        return ModelInstance(model)
    }

    /**
     * Load an .g3d model, created with the fbx-conv app from an .fbx file
     */
    private fun loadG3dModel(): ModelInstance {
        val crateModel = G3dModelLoader(UBJsonReader()).loadModel(Gdx.files.internal("crate-wooden.g3db"))
        val crateInstance = ModelInstance(crateModel)
        crateInstance.transform.translate(0f, -2f, 0f)
        crateInstance.transform.scale(0.01f, 0.01f, 0.01f)
        return crateInstance
    }

    private fun createGrid(size: Float, divisions: Int): ModelInstance {
        val modelBuilder = ModelBuilder()
        val lineGrid = modelBuilder.createLineGrid(
            divisions, divisions, size, size, Material(
                ColorAttribute.createDiffuse(Color.DARK_GRAY)
            ), (Usage.Position or Usage.ColorUnpacked).toLong()
        )
        return ModelInstance(lineGrid)
    }

    private fun createOriginArrows(size: Float): ModelInstance {
        val modelBuilder = ModelBuilder()
        val origin = modelBuilder.createXYZCoordinates(size, Material(), (Usage.Position or Usage.ColorUnpacked).toLong())
        return ModelInstance(origin)
    }

    private fun drawLines(): ModelInstance {
        val modelBuilder = ModelBuilder()
        modelBuilder.begin()
        val partBuilder: MeshPartBuilder = modelBuilder.part("lines", GL20.GL_LINES, Usage.Position.toLong(), Material())
        partBuilder.line(0f, 0f, 0f, 64f, 64f, 64f)
        return ModelInstance(modelBuilder.end())
    }

    override fun render() {
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.1f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
        camera.update();

        if (image != null) {
            batch.use {
                it.draw(image, 0f, 0f)
            }
        }

        if (models.isNotEmpty()) {
            modelBatch.begin(camera)
            models.forEach {
                modelBatch.render(it, environment)
            }
            modelBatch.end()

        }


    }

    override fun dispose() {
        batch.dispose()
        image?.dispose()
        models.forEach { it.model.dispose() }
        modelBatch.dispose()
    }
}

// fix axis difference between q2 (z up) and libGDX (y up)
fun ModelInstance.transformQ2toLibgdx(): ModelInstance {
//    this.transform.scale(0.25f, 0.25f, 0.25f)
    this.transform.rotate(Vector3(1f, 0f, 0f), -90f)
    this.transform.rotate(Vector3(0f, 0f, 1f), 90f)
    return this
}