plugins {
    application
}

dependencies {
    implementation(project(":qcommon"))
    implementation(project(":server"))

    runtimeOnly(project(":game"))
}

application {
    mainClass.set("jake2.dedicated.Jake2Dedicated")
}
