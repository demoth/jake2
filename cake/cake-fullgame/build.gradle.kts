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
    // Opt-in flags and content location affect which tests run and what they load.
    // Changing them must invalidate results that may otherwise contain only skipped tests.
    listOf("CAKE_HOST_GAME_TESTS", "CAKE_HOST_GL_TESTS", "CAKE_TEST_BASEDIR", "CAKE_TEST_DEMO").forEach { name ->
        inputs.property(name, providers.environmentVariable(name).orElse(""))
    }
    providers.environmentVariable("CAKE_TEST_DEMO").orNull?.let { inputs.file(it) }
    if (providers.environmentVariable("CAKE_HOST_GAME_TESTS").orNull == "1") {
        forkEvery = 1
    }
}
