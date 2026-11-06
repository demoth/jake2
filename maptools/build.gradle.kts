plugins {
    application
}

application {
    mainClass.set("jake2.maptools.Jbsp")
}

dependencies {
    implementation(project(":qcommon"))
    implementation(libs.logbackClassic)
    implementation(libs.jacksonDatabind)
}
