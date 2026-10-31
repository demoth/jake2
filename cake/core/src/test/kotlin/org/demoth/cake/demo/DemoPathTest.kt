package org.demoth.cake.demo

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DemoPathTest {
    @Test fun `console demo names resolve inside profile demos directory`() {
        assertEquals("demos/test.dm2", demoLogicalPath("test"))
        assertEquals("demos/test.dm2", demoLogicalPath("demos/test.dm2"))
        assertEquals("demos/series/test.dm2", demoLogicalPath("series\\test"))
        for (path in listOf("", "/tmp/test", "../test", "demos/../../test", "C:\\demo", "demos//test")) {
            assertNull(demoLogicalPath(path), path)
        }
    }
}
