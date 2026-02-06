package org.demoth.cake.assets

import com.badlogic.gdx.files.FileHandle
import org.junit.Assert.assertEquals
import org.junit.Test

class SkyLoaderTest {

    @Test
    fun declaresAllSkyTextureDependencies() {
        val loader = SkyLoader {  FileHandle(it) }
        val dependencies = loader.getDependencies(
            SkyLoader.assetPath("desert_"),
            null,
            null
        )

        assertEquals(
            listOf(
                "env/desert_rt.pcx",
                "env/desert_bk.pcx",
                "env/desert_lf.pcx",
                "env/desert_ft.pcx",
                "env/desert_up.pcx",
                "env/desert_dn.pcx",
            ),
            dependencies.map { it.fileName }
        )
    }
}
