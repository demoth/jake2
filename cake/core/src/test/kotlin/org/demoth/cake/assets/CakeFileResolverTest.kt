package org.demoth.cake.assets

import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.headless.HeadlessApplication
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CakeFileResolverTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun resolvesExactCaseInGamemod() {
        val basedir = temp.root.absolutePath
        val resolver = CakeFileResolver(basedir = basedir, gamemod = "rogue")
        val file = createFile("rogue/sound/Alert.wav")

        val resolved = resolver.resolve("sound/Alert.wav")

        assertNotNull(resolved)
        assertEquals(file.absolutePath, resolved!!.file().absolutePath)
    }

    @Test
    fun resolvesCaseInsensitiveInGamemod() {
        val basedir = temp.root.absolutePath
        val resolver = CakeFileResolver(basedir = basedir, gamemod = "rogue")
        val file = createFile("rogue/berserk/RUN.wav")

        val resolved = resolver.resolve("berserk/run.wav")

        assertNotNull(resolved)
        assertTrue(resolved!!.exists())
        assertTrue(resolved.file().name.equals(file.name, ignoreCase = true))
    }

    @Test
    fun gamemodOverridesBasemod() {
        val basedir = temp.root.absolutePath
        val resolver = CakeFileResolver(basedir = basedir, gamemod = "rogue")
        createFile("baseq2/textures/wall.tga")
        val file = createFile("rogue/textures/wall.tga")

        val resolved = resolver.resolve("textures/wall.tga")

        assertNotNull(resolved)
        assertEquals(file.absolutePath, resolved!!.file().absolutePath)
        assertTrue(resolved.file().absolutePath.contains("${File.separator}rogue${File.separator}"))
    }

    @Test
    fun resolvesVirtualSkyAssetWithoutPhysicalFile() {
        val resolver = CakeFileResolver()

        val resolved = resolver.resolve("sky/desert_.sky")

        assertNotNull(resolved)
        assertTrue(resolved!!.exists())
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
