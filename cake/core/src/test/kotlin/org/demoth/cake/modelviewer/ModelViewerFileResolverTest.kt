package org.demoth.cake.modelviewer

import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.headless.HeadlessApplication
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ModelViewerFileResolverTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun resolvesFromOpenedFileDirectory() {
        val openedFile = createFile("viewer/tris.md2")
        val skin = createFile("viewer/skin.pcx")
        val resolver = ModelViewerFileResolver(openedFile.absolutePath)

        val resolved = resolver.resolve("skin.pcx")

        assertNotNull(resolved)
        assertEquals(skin.absolutePath, resolved!!.file().absolutePath)
    }

    @Test
    fun resolvesCaseInsensitiveFromOpenedFileDirectory() {
        val openedFile = createFile("viewer/tris.md2")
        val skin = createFile("viewer/Skin.PCX")
        val resolver = ModelViewerFileResolver(openedFile.absolutePath)

        val resolved = resolver.resolve("skin.pcx")

        assertNotNull(resolved)
        assertTrue(resolved!!.exists())
        assertTrue(resolved.file().name.equals(skin.name, ignoreCase = true))
    }

    @Test
    fun resolvesByFileNameWhenDependencyUsesGameRelativePath() {
        val openedFile = createFile("viewer/tris.md2")
        val skin = createFile("viewer/grunt.pcx")
        val resolver = ModelViewerFileResolver(openedFile.absolutePath)

        val resolved = resolver.resolve("players/male/grunt.pcx")

        assertNotNull(resolved)
        assertEquals(skin.absolutePath, resolved!!.file().absolutePath)
    }

    @Test
    fun fallsBackToBasedirBaseq2WhenProvided() {
        val openedFile = createFile("viewer/tris.md2")
        val baseSkin = createFile("baseq2/models/monsters/berserk/skin.pcx")
        val resolver = ModelViewerFileResolver(
            openedFilePath = openedFile.absolutePath,
            basedir = temp.root.absolutePath,
        )

        val resolved = resolver.resolve("models/monsters/berserk/skin.pcx")

        assertNotNull(resolved)
        assertEquals(baseSkin.absolutePath, resolved!!.file().absolutePath)
    }

    @Test
    fun returnsNullForMissingAsset() {
        val openedFile = createFile("viewer/tris.md2")
        val resolver = ModelViewerFileResolver(openedFile.absolutePath)

        val resolved = resolver.resolve("missing/asset/file.pcx")

        assertNull(resolved)
    }

    private fun createFile(relativePath: String): File {
        val file = File(temp.root, relativePath)
        file.parentFile.mkdirs()
        file.writeText("")
        return file
    }

    companion object {
        private var app: HeadlessApplication? = null

        @BeforeClass
        @JvmStatic
        fun setupGdx() {
            if (Gdx.app == null) {
                app = HeadlessApplication(EmptyListener(), HeadlessApplicationConfiguration())
            }
        }

        @AfterClass
        @JvmStatic
        fun teardownGdx() {
            app?.exit()
            app = null
        }
    }

    private class EmptyListener : ApplicationListener {
        override fun create() = Unit
        override fun resize(width: Int, height: Int) = Unit
        override fun render() = Unit
        override fun pause() = Unit
        override fun resume() = Unit
        override fun dispose() = Unit
    }
}
