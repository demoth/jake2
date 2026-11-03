package org.demoth.cake.assets

import com.badlogic.gdx.files.FileHandle
import jake2.qcommon.exec.Cvar
import jake2.qcommon.exec.find
import jake2.qcommon.filesystem.IDALIASHEADER
import org.demoth.cake.assets.md2.Md2Loader
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path

class ModelTextureReplacementsTest {
    @TempDir lateinit var root: Path

    @Test
    fun `model dependencies use first alternate candidate and preserve original when disabled or absent`() {
        val skin = "models/test/skin.pcx"
        // Only header and skin table are needed for dependency discovery.
        val bytes = ByteBuffer.allocate(132).order(ByteOrder.LITTLE_ENDIAN)
        listOf(IDALIASHEADER, 8, 64, 64, 0, 1, 0, 0, 0, 0, 0, 68, 132, 132, 132, 132, 132)
            .forEach { bytes.putInt(it) }
        bytes.put(skin.toByteArray())
        val file = FileHandle(root.resolve("tris.md2").toFile()).apply { writeBytes(bytes.array(), false) }
        var candidates = listOf(skin, "models/test/skin_bump.jpg", "other/skin.jpg", "models/test/skin.jpeg", "models/test/skin.jpg")
        val resolver = object : TextureCandidateResolver {
            override fun resolve(fileName: String): FileHandle? = null
            override fun textureCandidates() = candidates
        }
        val loader = Md2Loader(resolver)
        fun paths(key: String = "models/test/tris.md2", parameters: Md2Loader.Parameters? = null) =
            loader.getDependencies(key, file, parameters)?.map { it.fileName }.orEmpty()
        ModelTextureReplacements.reset()
        try {
            assertTrue(ModelTextureReplacements.enabled())
            assertEquals(listOf("models/test/skin.jpeg"), paths())
            assertEquals(listOf("models/test/skin.jpeg"), paths("$skin|models/test/tris.md2"))
            assertTrue(paths(parameters = Md2Loader.Parameters(loadEmbeddedSkins = false)).isEmpty())
            Cvar.getInstance().find<Boolean>(ModelTextureReplacements.CVAR).forceSet(false)
            assertEquals(listOf(skin), paths())
            ModelTextureReplacements.reset()
            candidates = listOf("models/test/skin_bump.jpg", "other/skin.jpg", skin)
            assertEquals(listOf(skin), paths())
            candidates = emptyList()
            assertEquals(listOf(skin), paths(), "Missing originals still reach the checkerboard loader")
        } finally { ModelTextureReplacements.reset() }
    }
}
