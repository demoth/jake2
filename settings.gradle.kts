pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { url = uri("https://s01.oss.sonatype.org") }
        mavenLocal()
        maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots/") }
        maven { url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/") }
        maven { url = uri("https://jitpack.io") }
        google()
    }
}

rootProject.name = "jake2"
include (":qcommon")
include (":game")
include (":server")
//include (":client")
include (":dedicated")
//include (":fullgame")
include (":maptools")
include (":cake")
include (":cake:core")
include (":cake:lwjgl3")
