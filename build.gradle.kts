plugins {
//    kotlin("jvm") version "1.4.0" apply false
    id("org.jetbrains.kotlin.jvm") version "1.4.0"

}


allprojects {

    group = "org.bytonic"
    version = "1.1.0"

    apply(plugin = "org.jetbrains.kotlin.jvm")

//    apply {
//        plugin("org.jetbrains.kotlin.jvm")
//        plugin("java")
//    }

    repositories {
        jcenter()
        mavenCentral()
        gradlePluginPortal()
    }

    dependencies {
        testImplementation(group = "junit", name = "junit", version = "4.12")
    }
}

