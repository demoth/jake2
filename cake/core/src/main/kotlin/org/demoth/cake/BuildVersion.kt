package org.demoth.cake

import java.util.Properties

object BuildVersion {
    val displayVersion: String by lazy {
        val properties = loadGitProperties() ?: return@lazy "dev"
        val version = properties.getProperty("git.build.version")?.trim().orEmpty()
        val commit = properties.getProperty("git.commit.id.abbrev")?.trim().orEmpty()
        val dirty = properties.getProperty("git.dirty")?.trim().equals("true", ignoreCase = true)

        when {
            version.isNotBlank() && commit.isNotBlank() -> {
                if (dirty) "$version+$commit-dirty" else "$version+$commit"
            }
            version.isNotBlank() -> version
            commit.isNotBlank() -> if (dirty) "$commit-dirty" else commit
            else -> "dev"
        }
    }

    private fun loadGitProperties(): Properties? {
        val stream = javaClass.classLoader.getResourceAsStream("git.properties") ?: return null
        return Properties().apply {
            stream.use(::load)
        }
    }
}
