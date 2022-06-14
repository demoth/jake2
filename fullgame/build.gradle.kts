plugins {
    application
}

application {
    mainClassName = "jake2.fullgame.Jake2"

    // added to start script
    applicationDefaultJvmArgs = listOf("-Djava.library.path=..")


    distributions {
        main {
            contents {
                // copy to the root folder of the distribution, so that bin/.. (java.library.path) points to the native libs
                from("build/natives")
            }
        }
    }

}



dependencies {
    implementation(project(":qcommon"))
    implementation(project(":client"))
    implementation(project(":server"))

    runtimeOnly(project(":game"))
    testImplementation(project(":game"))
}

task("listNatives") {
    doLast {
        val platforms = listOf("windows", "linux", "osx")
        platforms.forEach { pl ->
            val nativeLibs = configurations["runtimeClasspath"].resolvedConfiguration.resolvedArtifacts.filter {
                it.classifier == "natives-$pl"
            }
            println("[$pl] found libs $nativeLibs")
        }
    }
}


/*
 * thanks to this thread
 * https://discuss.gradle.org/t/how-to-use-lwjgl-or-how-to-use-native-libraries/7498/13
 */
task("copyNatives") {
    doLast {
        copy {
            val platforms = listOf("windows", "linux", "osx")
            duplicatesStrategy = DuplicatesStrategy.WARN
            platforms.forEach { pl ->
                val nativeLibs = configurations["runtimeClasspath"].resolvedConfiguration.resolvedArtifacts.filter {
                    it.classifier == "natives-$pl"
                }
                println("[$pl] found libs $nativeLibs")
                nativeLibs.forEach {
                    println("[$pl] copying ${it.file}")
                    from(zipTree(it.file))
                }
            }
            into("${buildDir}/natives")
        }
    }
}

tasks["installDist"].dependsOn("copyNatives")
tasks["distZip"].dependsOn("copyNatives")
tasks["distTar"].dependsOn("copyNatives")
tasks["run"].dependsOn("copyNatives")
