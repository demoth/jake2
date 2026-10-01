import org.gradle.api.initialization.resolve.RepositoriesMode

rootProject.name = "jake2"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

include (":qcommon")
include (":game")
include (":server")
include (":dedicated")
include (":maptools")
include (":cake")
include (":cake:core")
include (":cake:cake-client")
include (":cake:cake-modelviewer")
include (":cake:engine-tools")
