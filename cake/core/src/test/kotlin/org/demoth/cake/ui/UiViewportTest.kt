package org.demoth.cake.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Graphics
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.GdxNativesLoader
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

class UiViewportTest {
    private val previousGraphics = Gdx.graphics
    private val previousGl = Gdx.gl
    private var windowWidth = 1920
    private var windowHeight = 1080

    @BeforeEach
    fun setup() {
        GdxNativesLoader.load()
        Gdx.graphics = Proxy.newProxyInstance(Graphics::class.java.classLoader, arrayOf(Graphics::class.java)) { _, method, _ ->
            when (method.name) {
                "getWidth", "getBackBufferWidth" -> windowWidth
                "getHeight", "getBackBufferHeight" -> windowHeight
                else -> error("Unexpected graphics call: ${method.name}")
            }
        } as Graphics
        Gdx.gl = Proxy.newProxyInstance(GL20::class.java.classLoader, arrayOf(GL20::class.java)) { _, method, _ ->
            check(method.name == "glViewport") { "Unexpected GL call: ${method.name}" }
            null
        } as GL20
    }

    @AfterEach
    fun restore() {
        Gdx.graphics = previousGraphics
        Gdx.gl = previousGl
    }

    @Test
    fun coversDifferentAspectRatiosWithUniformScaleAndCenteredReferenceArea() {
        val viewport = UiViewport()
        for ((width, height) in listOf(1024 to 768, 1920 to 1080, 3840 to 2160, 3440 to 1440, 900 to 1200)) {
            windowWidth = width
            windowHeight = height
            viewport.update(width, height, true)

            assertEquals(0, viewport.screenX)
            assertEquals(0, viewport.screenY)
            assertEquals(width, viewport.screenWidth)
            assertEquals(height, viewport.screenHeight)
            assertEquals(width / viewport.worldWidth, height / viewport.worldHeight, 0.001f)
            assertTrue(viewport.worldWidth >= viewport.minWorldWidth)
            assertTrue(viewport.worldHeight >= viewport.minWorldHeight)

            val center = viewport.project(Vector2(viewport.worldWidth / 2, viewport.worldHeight / 2))
            assertEquals(width / 2f, center.x, 0.01f)
            assertEquals(height / 2f, center.y, 0.01f)
            val mouse = viewport.unproject(Vector2(width / 2f, height / 2f))
            assertEquals(viewport.worldWidth / 2, mouse.x, 0.01f)
            assertEquals(viewport.worldHeight / 2, mouse.y, 0.01f)
        }
    }

    @Test
    fun referenceSizeChangesUiScaleAndKeepsFourByThreeProportions() {
        val viewport = UiViewport()
        viewport.update(windowWidth, windowHeight, true)
        assertEquals(1.5f, windowHeight / viewport.worldHeight)
        assertTrue(viewport.setReferenceHeight(960f))
        viewport.update(windowWidth, windowHeight, true)
        assertEquals(1280f, viewport.minWorldWidth)
        assertEquals(1.125f, windowHeight / viewport.worldHeight)
        assertFalse(viewport.setReferenceHeight(960f))
        viewport.setReferenceHeight(0f)
        assertEquals(640f, viewport.minWorldHeight)
        viewport.setReferenceHeight(Float.NaN)
        assertEquals(720f, viewport.minWorldHeight)
    }
}
