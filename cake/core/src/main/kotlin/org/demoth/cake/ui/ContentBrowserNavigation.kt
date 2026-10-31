package org.demoth.cake.ui

/** Value state survives preview cleanup and menu/style rebuilds without retaining actors or resources. */
data class ContentBrowserLocation(
    val directory: String = "",
    val selectedPath: String? = null,
    val scrollY: Float = 0f,
    val thumbnailWidth: Float = 128f,
)

class ContentBrowserNavigation {
    private var profile: String? = null
    private var location: ContentBrowserLocation? = null

    fun forProfile(profileId: String): ContentBrowserLocation? {
        if (profile != profileId) {
            profile = profileId
            location = null
        }
        return location
    }

    fun remember(profileId: String, value: ContentBrowserLocation) {
        profile = profileId
        location = value
    }
}
