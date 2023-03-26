val lwjgl_version = "3.3.1"

plugins {
//    kotlin("jvm") version "1.4.0" apply false
    id("org.jetbrains.kotlin.jvm") version "1.7.0"

}


allprojects {

    group = "org.bytonic"
    version = "1.1.1"

    apply(plugin = "org.jetbrains.kotlin.jvm")

//    apply {
//        plugin("org.jetbrains.kotlin.jvm")
//        plugin("java")
//    }

    repositories {
        mavenCentral()
        gradlePluginPortal()
    }

    dependencies {
        implementation("org.lwjgl:lwjgl:$lwjgl_version")
        implementation("org.lwjgl:lwjgl-opengl:$lwjgl_version")
        implementation("org.lwjgl:lwjgl-glfw:$lwjgl_version")
        implementation("org.lwjgl:lwjgl-jemalloc:$lwjgl_version")
        implementation("org.lwjgl:lwjgl-openal:$lwjgl_version")
        testImplementation(group = "junit", name = "junit", version = "4.12")
    }
}

