val lwjgl_version = "2.9.3"

dependencies {
    implementation(project(":qcommon"))

    implementation("org.lwjgl.lwjgl:lwjgl:${lwjgl_version}")
    runtimeOnly("org.lwjgl.lwjgl:lwjgl-platform:${lwjgl_version}")
    implementation("org.lwjgl.lwjgl:lwjgl_util:${lwjgl_version}")
    implementation("javazoom:jlayer:1.0.1")
}
