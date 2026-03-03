import org.gradle.api.initialization.resolve.RepositoriesMode

rootProject.name = "jake2"

// Archival modules stay out of default builds. Opt-in with -PincludeArchivalModules=true.
val includeArchivalModules = (providers.gradleProperty("includeArchivalModules").orNull ?: "false").toBoolean()

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
        google()
        maven(url = "https://oss.sonatype.org/content/repositories/snapshots/")
        maven(url = "https://s01.oss.sonatype.org/content/repositories/snapshots/")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        mavenCentral()
        mavenLocal()
        google()
        maven(url = "https://s01.oss.sonatype.org")
        maven(url = "https://oss.sonatype.org/content/repositories/snapshots/")
        maven(url = "https://s01.oss.sonatype.org/content/repositories/snapshots/")
        maven(url = "https://jitpack.io")
    }
}

include (":qcommon")
include (":game")
include (":server")
include (":dedicated")
include (":maptools")
include (":cake")
include (":cake:core")
include (":cake:lwjgl3")
include (":cake:engine-tools")

if (includeArchivalModules) {
    include(":client")
    include(":fullgame")
}
