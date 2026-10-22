plugins {
    application
    alias(libs.plugins.beryxRuntime)
    alias(libs.plugins.graalNativeBuildTools) apply false
}

application {
    mainClass.set("jake2.fullgame.Jake2")
}

apply(from = rootProject.file("cake/desktop.gradle"))

dependencies {
    implementation(project(":cake:core"))
    implementation(project(":server"))
    implementation(project(":qcommon"))
    runtimeOnly(project(":game"))
    testImplementation(project(":game"))
}
