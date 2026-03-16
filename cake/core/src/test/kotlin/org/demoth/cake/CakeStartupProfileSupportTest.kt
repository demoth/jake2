package org.demoth.cake

import org.demoth.cake.profile.CakeGameProfileStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CakeStartupProfileSupportTest {

    @Test
    fun createsDefaultProfileFromAutodetectedBasedir() {
        val profile = autodetectedStartupProfile(" /quake2 ")

        requireNotNull(profile)
        assertEquals(CakeGameProfileStore.DEFAULT_PROFILE_ID, profile.id)
        assertEquals("/quake2", profile.basedir)
        assertNull(profile.gamemod)
    }

    @Test
    fun returnsNullWhenAutodetectDidNotFindBasedir() {
        assertNull(autodetectedStartupProfile(null))
        assertNull(autodetectedStartupProfile(""))
        assertNull(autodetectedStartupProfile("   "))
    }
}
