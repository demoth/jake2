package org.demoth.cake.stages

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.utils.viewport.Viewport
import jake2.qcommon.exec.Cvar
import ktx.scene2d.Scene2DSkin
import java.util.EnumMap
import kotlin.math.max

enum class MetricId {
    DRAW_CALLS,
}

/**
 * Draws scrolling per-frame metric lines (draw calls for now).
 *
 * The graph keeps at most one sample per screen pixel in width and advances by one pixel each frame.
 */
class DrawCallsDebugStage(viewport: Viewport) : Stage(viewport) {
    private data class MetricSeries(
        var history: IntArray = IntArray(0),
        var writeIndex: Int = 0,
        var size: Int = 0,
    )

    private val shapeRenderer = ShapeRenderer()
    private val cvarDebugGraph = Cvar.getInstance().Get("debug_r_graph", "0", 0)
    private val metricColors = EnumMap<MetricId, Color>(MetricId::class.java).apply {
        put(MetricId.DRAW_CALLS, Color(0.2f, 1f, 0.2f, 0.7f))
    }
    private val metricDisplayNames = EnumMap<MetricId, String>(MetricId::class.java).apply {
        put(MetricId.DRAW_CALLS, "draw calls")
    }
    private val metricSeries = EnumMap<MetricId, MetricSeries>(MetricId::class.java)
    private val metricLabels = EnumMap<MetricId, Label>(MetricId::class.java)
    private val metricNameLabels = EnumMap<MetricId, Label>(MetricId::class.java)

    init {
        MetricId.entries.forEach { metricId ->
            metricSeries[metricId] = MetricSeries()
            val metricColor = metricColors.getValue(metricId)
            val label = Label("", Scene2DSkin.defaultSkin).apply {
                isVisible = false
                color = Color(metricColor)
            }
            metricLabels[metricId] = label
            addActor(label)

            val nameLabel = Label(metricDisplayNames.getValue(metricId), Scene2DSkin.defaultSkin).apply {
                isVisible = false
                color = Color(metricColor)
            }
            metricNameLabels[metricId] = nameLabel
            addActor(nameLabel)
        }
        resizeMetricHistory(Gdx.graphics.width.coerceAtLeast(1))
    }

    fun pushMetric(metricId: MetricId, value: Int) {
        val width = Gdx.graphics.width.coerceAtLeast(1)
        ensureMetricHistoryWidth(width)
        val series = metricSeries.getValue(metricId)
        series.history[series.writeIndex] = value.coerceAtLeast(0)
        series.writeIndex = (series.writeIndex + 1) % series.history.size
        series.size = minOf(series.size + 1, series.history.size)
    }

    fun pushDrawCalls(drawCalls: Int) {
        pushMetric(MetricId.DRAW_CALLS, drawCalls)
    }

    override fun draw() {
        if (cvarDebugGraph.value == 0f) {
            hideAllMetricLabels()
            return
        }

        val activeSeries = metricSeries.filterValues { it.size > 0 }
        if (activeSeries.isEmpty()) {
            hideAllMetricLabels()
            return
        }

        viewport.apply()

        val graphBaseY = 8f
        val graphPaddingTop = 8f
        val graphHeight = (viewport.worldHeight - graphBaseY - graphPaddingTop).coerceAtMost(140f).coerceAtLeast(1f)
        val graphWidth = viewport.worldWidth.coerceAtLeast(1f)
        val globalMaxValue = activeSeries.values.maxOf { currentMaxValue(it) }.coerceAtLeast(1)
        val globalMax = globalMaxValue.toFloat()

        shapeRenderer.projectionMatrix = camera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)

        MetricId.entries.forEach { metricId ->
            val series = metricSeries.getValue(metricId)
            val label = metricLabels.getValue(metricId)
            val nameLabel = metricNameLabels.getValue(metricId)
            if (series.size == 0) {
                label.isVisible = false
                nameLabel.isVisible = false
                return@forEach
            }

            val metricColor = metricColors.getValue(metricId)
            shapeRenderer.color.set(metricColor.r, metricColor.g, metricColor.b, metricColor.a)
            if (series.size > 1) {
                for (x in 1 until series.size) {
                    val previousValue = historyValueAt(series, x - 1).toFloat()
                    val currentValue = historyValueAt(series, x).toFloat()
                    val y1 = graphBaseY + (previousValue / globalMax) * graphHeight
                    val y2 = graphBaseY + (currentValue / globalMax) * graphHeight
                    shapeRenderer.line((x - 1).toFloat(), y1, x.toFloat(), y2)
                }
            }

            val metricMax = currentMaxValue(series)
            val metricMaxY = graphBaseY + (metricMax / globalMax) * graphHeight
            shapeRenderer.color.set(metricColor.r, metricColor.g, metricColor.b, 0.35f)
            shapeRenderer.line(0f, metricMaxY, graphWidth - 1f, metricMaxY)

            label.setText(metricMax.toString())
            label.setColor(metricColor)
            label.pack()
            label.setPosition(
                4f,
                (metricMaxY - label.height - 2f).coerceIn(graphBaseY, viewport.worldHeight - label.height)
            )
            label.isVisible = true

            nameLabel.setText(metricDisplayNames.getValue(metricId))
            nameLabel.setColor(metricColor)
            nameLabel.pack()
            nameLabel.setPosition(
                (graphWidth - nameLabel.width - 4f).coerceAtLeast(0f),
                (metricMaxY + 2f).coerceIn(graphBaseY, viewport.worldHeight - nameLabel.height)
            )
            nameLabel.isVisible = true
        }

        shapeRenderer.end()
        super.draw()
    }

    override fun dispose() {
        shapeRenderer.dispose()
        super.dispose()
    }

    private fun historyValueAt(series: MetricSeries, indexFromOldest: Int): Int {
        val oldestIndex = if (series.size == series.history.size) series.writeIndex else 0
        val historyIndex = (oldestIndex + indexFromOldest) % series.history.size
        return series.history[historyIndex]
    }

    private fun currentMaxValue(series: MetricSeries): Int {
        var currentMax = 1
        for (i in 0 until series.size) {
            currentMax = max(currentMax, historyValueAt(series, i))
        }
        return currentMax
    }

    private fun ensureMetricHistoryWidth(width: Int) {
        val anyMismatched = metricSeries.values.any { it.history.size != width }
        if (anyMismatched) {
            resizeMetricHistory(width)
        }
    }

    private fun resizeMetricHistory(width: Int) {
        metricSeries.values.forEach { series ->
            series.history = IntArray(width)
            series.writeIndex = 0
            series.size = 0
        }
    }

    private fun hideAllMetricLabels() {
        metricLabels.values.forEach { it.isVisible = false }
        metricNameLabels.values.forEach { it.isVisible = false }
    }
}
