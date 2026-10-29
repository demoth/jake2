package org.demoth.cake.ui

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ContentBrowserNavigationTest {
    @Test fun `bookmark retains only value state for the same profile and resets on profile change`() {
        val navigation = ContentBrowserNavigation()
        assertNull(navigation.forProfile("base"))
        val location = ContentBrowserLocation("demos/series", "demos/series/test.dm2", 250f, 160f)
        navigation.remember("base", location)
        assertEquals(location, navigation.forProfile("base"))
        assertNull(navigation.forProfile("rogue"))
        assertNull(navigation.forProfile("base"))
    }
}
