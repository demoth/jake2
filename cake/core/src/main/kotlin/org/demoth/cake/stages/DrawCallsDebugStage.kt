package org.demoth.cake.stages

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.utils.viewport.Viewport
import jake2.qcommon.exec.Cvar
import kotlin.math.max

/**
 * Draws a scrolling line graph of per-frame draw calls.
 *
 * The graph keeps at most one sample per screen pixel in width and advances by one pixel each frame.
 */
class DrawCallsDebugStage(viewport: Viewport) : Stage(viewport) {
    private val shapeRenderer = ShapeRenderer()
    private var drawCallHistory = IntArray(0)
    private var historyWriteIndex = 0
    private var historySize = 0
    private val color = Color(0.2f, 1f, 0.2f, 0.7f)
    private val cvarDebugGraph = Cvar.getInstance().Get("debug_r_graph", "0", 0)

    init {
        resizeHistory(Gdx.graphics.width.coerceAtLeast(1))
    }

    fun pushDrawCalls(drawCalls: Int) {
        val width = Gdx.graphics.width.coerceAtLeast(1)
        if (drawCallHistory.size != width) {
            resizeHistory(width)
        }

        drawCallHistory[historyWriteIndex] = drawCalls.coerceAtLeast(0)
        historyWriteIndex = (historyWriteIndex + 1) % drawCallHistory.size
        historySize = minOf(historySize + 1, drawCallHistory.size)
    }

    override fun draw() {
        if (cvarDebugGraph.value == 0f) {
            return
        }
        super.draw()
        if (historySize < 2) {
            return
        }

        viewport.apply()

        val graphBaseY = 8f
        val graphPaddingTop = 8f
        val graphHeight = (viewport.worldHeight - graphBaseY - graphPaddingTop).coerceAtMost(140f).coerceAtLeast(1f)
        val maxDrawCalls = currentMaxDrawCalls().toFloat()

        shapeRenderer.projectionMatrix = camera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = color

        for (x in 1 until historySize) {
            val previousValue = historyValueAt(x - 1).toFloat()
            val currentValue = historyValueAt(x).toFloat()
            val y1 = graphBaseY + (previousValue / maxDrawCalls) * graphHeight
            val y2 = graphBaseY + (currentValue / maxDrawCalls) * graphHeight
            shapeRenderer.line((x - 1).toFloat(), y1, x.toFloat(), y2)
        }

        shapeRenderer.end()
    }

    override fun dispose() {
        shapeRenderer.dispose()
        super.dispose()
    }

    private fun historyValueAt(indexFromOldest: Int): Int {
        val oldestIndex = if (historySize == drawCallHistory.size) historyWriteIndex else 0
        val historyIndex = (oldestIndex + indexFromOldest) % drawCallHistory.size
        return drawCallHistory[historyIndex]
    }

    private fun currentMaxDrawCalls(): Int {
        var currentMax = 1
        for (i in 0 until historySize) {
            currentMax = max(currentMax, historyValueAt(i))
        }
        return currentMax
    }

    private fun resizeHistory(width: Int) {
        drawCallHistory = IntArray(width)
        historyWriteIndex = 0
        historySize = 0
    }
}
