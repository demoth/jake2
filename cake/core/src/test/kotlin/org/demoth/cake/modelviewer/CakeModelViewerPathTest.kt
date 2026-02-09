package org.demoth.cake.modelviewer

import org.junit.Assert.assertEquals
import org.junit.Test

class CakeModelViewerPathTest {

    @Test
    fun expandsHomeOnlyPath() {
        val expected = System.getProperty("user.home")
        assertEquals(expected, expandTildePath("~"))
    }

    @Test
    fun expandsHomePrefixedPath() {
        val expected = System.getProperty("user.home") + "/Downloads/q2/baseq2/models/monsters/soldier/tris.md2"
        assertEquals(expected, expandTildePath("~/Downloads/q2/baseq2/models/monsters/soldier/tris.md2"))
    }

    @Test
    fun keepsRegularPathUntouched() {
        val path = "/tmp/model/tris.md2"
        assertEquals(path, expandTildePath(path))
    }
}
