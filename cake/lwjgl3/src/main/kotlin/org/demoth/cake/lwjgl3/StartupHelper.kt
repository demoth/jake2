/*
 * Copyright 2020 damios
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
//Note, the above license and copyright applies to this file only.

@file:JvmName("StartupHelper")

package org.demoth.cake.lwjgl3

import org.lwjgl.system.macosx.LibC
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.lang.management.ManagementFactory
import java.nio.file.FileSystems
import java.util.*

private const val JVM_RESTARTED_ARG = "jvmIsRestarted"

fun startNewJvmIfRequired(redirectOutput: Boolean = true): Boolean {
    val osName = System.getProperty("os.name").lowercase(Locale.getDefault())
    if (!osName.contains("mac")) {
        if (osName.contains("windows")) {
            System.setProperty(
                "java.io.tmpdir",
                "${System.getenv("ProgramData")}/libGDX-temp"
            )
        }
        return false
    }

    // There is no need for -XstartOnFirstThread on Graal native image
    if (System.getProperty("org.graalvm.nativeimage.imagecode", "").isNotEmpty()) {
        return false
    }

    val pid = LibC.getpid()

    // check whether -XstartOnFirstThread is enabled
    if ("1" == System.getenv("JAVA_STARTED_ON_FIRST_THREAD_$pid")) {
        return false
    }

    // check whether the JVM was previously restarted
    // avoids looping, but most certainly leads to a crash
    if ("true" == System.getProperty(JVM_RESTARTED_ARG)) {
        System.err.println(
            "There was a problem evaluating whether the JVM was started with the -XstartOnFirstThread argument."
        )
        return false
    }

    // Restart the JVM with -XstartOnFirstThread
    val jvmArgs = arrayListOf<String>()
    val separator = FileSystems.getDefault().separator
    val javaExecPath = "${System.getProperty("java.home")}$separator/bin$separator/java"

    if (!File(javaExecPath).exists()) {
        System.err.println("A Java installation could not be found. If you are distributing this app with a bundled JRE, be sure to set the -XstartOnFirstThread argument manually!")
        return false
    }

    jvmArgs.run {
        add(javaExecPath)
        add("-XstartOnFirstThread")
        add("-D$JVM_RESTARTED_ARG=true")
        addAll(ManagementFactory.getRuntimeMXBean().inputArguments)
        add("-cp")
        add(System.getProperty("java.class.path"))
    }
    var mainClass = System.getenv("JAVA_MAIN_CLASS_$pid")
    if (mainClass == null) {
        val trace = Thread.currentThread().stackTrace
        if (trace.isNotEmpty()) {
            mainClass = trace.last().className
        } else {
            System.err.println("The main class could not be determined.")
            return false
        }
    }
    jvmArgs.add(mainClass)

    try {
        if (!redirectOutput) {
            ProcessBuilder(jvmArgs).start()
        } else {
            val process = ProcessBuilder(jvmArgs)
                .redirectErrorStream(true).start()
            BufferedReader(
                InputStreamReader(process.inputStream)
            ).use { processOutput ->
                processOutput.lineSequence().forEach { line ->
                    println(line)
                }
            }

            process.waitFor()
        }
    } catch (e: Exception) {
        System.err.println("There was a problem restarting the JVM.")
        e.printStackTrace()
    }

    return true
}