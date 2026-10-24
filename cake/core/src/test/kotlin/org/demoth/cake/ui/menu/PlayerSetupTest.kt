package org.demoth.cake.ui.menu

import jake2.qcommon.MainCommon
import jake2.qcommon.exec.Cvar
import jake2.qcommon.exec.find
import org.demoth.cake.CakeCvars
import org.demoth.cake.assets.CakeFileResolver
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class PlayerSetupTest {
    @TempDir
    lateinit var temp: Path
    private val cvars = Cvar.getInstance()

    @BeforeEach
    fun setUp() {
        cvars.clear()
        MainCommon.registerCvars(false)
        CakeCvars.registerAll()
    }

    @AfterEach
    fun tearDown() {
        cvars.clear()
    }

    @Test
    fun readingFormNormalizesUnavailableSkinWithoutChangingUserinfo() {
        cvars.find<String>("name").set(" ")
        cvars.find<String>("skin").set("missing/unknown")
        cvars.find<String>("password").set(" secret ")
        val setup = PlayerSetup(CakeFileResolver(), cvars)

        assertEquals(PlayerSetupFormState(name = "unnamed", password = " secret ",
            model = "male", skin = "grunt", hand = 0), setup.currentForm())
        assertEquals("missing/unknown", cvars.find<String>("skin").typedValue)
        assertEquals(" ", cvars.find<String>("name").typedValue)
    }

    @Test
    fun applyingFormTrimsIdentityAndNormalizesSkinAndHand() {
        val setup = PlayerSetup(CakeFileResolver(), cvars)
        val saved = setup.apply(PlayerSetupFormState(name = " Marine ", password = " secret ",
            model = "missing", skin = "unknown", hand = 99))

        assertEquals(PlayerSetupFormState(name = "Marine", password = "secret",
            model = "male", skin = "grunt", hand = 2), saved)
        assertEquals(saved, setup.currentForm())
        assertEquals("male/grunt", cvars.find<String>("skin").typedValue)
        assertEquals("male", cvars.find<String>("gender").typedValue)
        assertTrue(cvars.Userinfo().contains("\\name\\Marine"))
        assertEquals("unnamed", setup.apply(saved.copy(name = " ")).name)
    }

    @Test
    fun applyingModelsDerivesGenderFromTheNormalizedSelection() {
        val genders = mapOf("male" to "male", "cyborg" to "male", "female" to "female",
            "crackhor" to "female", "robot" to "none")
        genders.keys.forEach { model -> addPlayer(temp, "baseq2", model, "default") }
        val setup = PlayerSetup(CakeFileResolver(temp.toString()), cvars)

        for ((model, gender) in genders) {
            setup.apply(PlayerSetupFormState(model = " ${model.uppercase()} ", skin = " DEFAULT "))
            assertEquals("$model/default", cvars.find<String>("skin").typedValue)
            assertEquals(gender, cvars.find<String>("gender").typedValue)
        }
    }

    @Test
    fun invalidationRefreshesCatalogAfterProfileAndModChanges() {
        val other = temp.resolve("other")
        addPlayer(temp, "baseq2", "male", "grunt")
        addPlayer(other, "baseq2", "female", "athena")
        addPlayer(other, "rogue", "robot", "steel")
        val resolver = CakeFileResolver(temp.toString())
        val setup = PlayerSetup(resolver, cvars)
        val first = setup.catalog()
        assertEquals(listOf("male"), first.models)
        assertSame(first, setup.catalog())

        resolver.basedir = other.toString()
        setup.invalidateCatalog()
        assertEquals(listOf("female"), setup.catalog().models)
        resolver.gamemod = "rogue"
        setup.invalidateCatalog()
        assertEquals(listOf("female", "robot"), setup.catalog().models)
        resolver.basedir = null
        setup.invalidateCatalog()
        assertEquals(PlayerSetupCatalog.fallback(), setup.catalog())
    }

    private fun addPlayer(root: Path, mod: String, model: String, skin: String) {
        val directory = root.resolve("$mod/players/$model")
        Files.createDirectories(directory)
        Files.write(directory.resolve("tris.md2"), byteArrayOf())
        Files.write(directory.resolve("$skin.pcx"), byteArrayOf())
    }
}
