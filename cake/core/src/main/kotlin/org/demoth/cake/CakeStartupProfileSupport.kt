package org.demoth.cake

import org.demoth.cake.profile.CakeGameProfile
import org.demoth.cake.profile.CakeGameProfileStore

internal fun autodetectedStartupProfile(autoDetectedBasedir: String?): CakeGameProfile? {
    val basedir = autoDetectedBasedir?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return CakeGameProfile(
        id = CakeGameProfileStore.DEFAULT_PROFILE_ID,
        basedir = basedir,
        gamemod = null,
    )
}
