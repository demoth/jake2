package org.demoth.cake.testing

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine

/** Runs a sequential test on the GL thread, yielding to real application frames during awaits. */
class HiddenGlTest private constructor() {
    private var render: () -> Unit = {}
    private val cleanups = mutableListOf<() -> Unit>()
    private var pending: CompletableFuture<*>? = null
    private var resume: (() -> Unit)? = null
    private var finished = false
    private var failure: Throwable? = null

    fun renderWith(render: () -> Unit) { this.render = render }
    fun onCleanup(cleanup: () -> Unit) { cleanups += cleanup }

    /** Resume after rendering, never inline in the runner's completion callback or by blocking GL. */
    suspend fun <T> await(result: CompletableFuture<T>): T = suspendCoroutine { continuation ->
        check(resume == null) { "A test can await only one operation at a time" }
        pending = result
        resume = {
            continuation.resumeWith(runCatching {
                try { result.join() } catch (error: CompletionException) { throw error.cause ?: error }
            })
        }
    }

    private fun frame() {
        render()
        if (pending?.isDone == true) {
            val next = resume
            pending = null
            resume = null
            next?.invoke()
        }
    }

    private fun record(error: Throwable) {
        val first = failure
        if (first == null) failure = error else if (first !== error) first.addSuppressed(error)
    }

    private fun finish(result: Result<Unit>) {
        result.exceptionOrNull()?.let(::record)
        finished = true
        Gdx.app.exit()
    }

    private fun cleanup() {
        pending?.cancel(false)
        pending = null
        resume = null
        val actions = cleanups.toList().asReversed()
        cleanups.clear()
        actions.forEach { action -> try { action() } catch (error: Throwable) { record(error) } }
    }

    companion object {
        /** Fixture setup and all cleanup run with the selected home; caller supplies temporary data. */
        fun run(home: Path? = null, timeoutSeconds: Long = 90, scenario: suspend HiddenGlTest.() -> Unit) {
            require(timeoutSeconds in 1..3600)
            val previousHome = System.getProperty("user.home")
            val test = HiddenGlTest()
            val deadline = System.nanoTime() + timeoutSeconds * 1_000_000_000L
            if (home != null) System.setProperty("user.home", home.toString())
            try {
                Lwjgl3Application(object : ApplicationAdapter() {
                    override fun create() {
                        scenario.startCoroutine(test, object : Continuation<Unit> {
                            override val context = EmptyCoroutineContext
                            override fun resumeWith(result: Result<Unit>) { test.finish(result) }
                        })
                    }

                    override fun render() {
                        if (test.finished) return
                        try {
                            check(System.nanoTime() < deadline) { "Hidden GL test timed out after $timeoutSeconds seconds" }
                            test.frame()
                        } catch (error: Throwable) { test.finish(Result.failure(error)) }
                    }

                    override fun dispose() { test.cleanup() }
                }, Lwjgl3ApplicationConfiguration().apply {
                    setInitialVisible(false)
                    disableAudio(true)
                    setWindowedMode(640, 480)
                    setForegroundFPS(60)
                    setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL32, 3, 2)
                    setPauseWhenLostFocus(false)
                    setPauseWhenMinimized(false)
                })
                if (!test.finished) test.record(IllegalStateException("Hidden GL application exited before the scenario completed"))
            } catch (error: Throwable) { test.record(error) }
            finally {
                test.cleanup()
                if (home != null) {
                    if (previousHome == null) System.clearProperty("user.home")
                    else System.setProperty("user.home", previousHome)
                }
            }
            test.failure?.let { throw it }
        }
    }
}
