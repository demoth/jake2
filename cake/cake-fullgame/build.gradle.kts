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

// Real-application tests bootstrap process-wide engine state and replace user.home.
// Keep each opt-in test class in its own application process.
tasks.test {
    if (providers.environmentVariable("CAKE_HOST_GAME_TESTS").orNull == "1") {
        forkEvery = 1
    }
}
