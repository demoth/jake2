val lwjgl_version = "3.3.1"
val lwjglNatives = "natives-linux"

plugins {
    id("org.jetbrains.kotlin.jvm")
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://repo.maven.apache.org/maven2/")
    }
}
dependencies {
    implementation(project(":qcommon"))
    implementation("org.lwjgl:lwjgl:$lwjgl_version")
    implementation("org.lwjgl:lwjgl-opengl:$lwjgl_version")
    implementation("org.lwjgl:lwjgl-glfw:$lwjgl_version")
//    implementation("org.lwjgl:lwjgl-jemalloc:$lwjgl_version")
    implementation("org.lwjgl:lwjgl-openal:$lwjgl_version")
    implementation("javazoom:jlayer:1.0.1")

    runtimeOnly("org.lwjgl", "lwjgl", classifier = lwjglNatives)
    runtimeOnly("org.lwjgl", "lwjgl-glfw", classifier = lwjglNatives)
    runtimeOnly("org.lwjgl", "lwjgl-openal", classifier = lwjglNatives)
    runtimeOnly("org.lwjgl", "lwjgl-opengl", classifier = lwjglNatives)
}