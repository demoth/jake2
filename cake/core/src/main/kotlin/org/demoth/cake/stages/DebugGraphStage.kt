package org.demoth.cake.stages

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.profiling.GLProfiler
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

data class MetricDefinition(
    val id: MetricId,
    val name: String,
    val color: Color,
    val collectValue: (GLProfiler) -> Int,
)

/**
 * Draws scrolling per-frame metric lines (draw calls for now).
 *
 * The graph keeps at most one sample per screen pixel in width and advances by one pixel each frame.
 */
class DebugGraphStage(viewport: Viewport) : Stage(viewport) {
    companion object {
        private const val GRAPH_BASE_Y = 8f
        private const val GRAPH_PADDING_TOP = 8f
        private const val GRAPH_MAX_HEIGHT = 140f
        private const val LABEL_LEFT_MARGIN = 4f
        private const val LABEL_LINE_GAP = 2f
        private const val MAX_LINE_ALPHA = 0.35f

        val metricDefinitions: List<MetricDefinition> = listOf(
            MetricDefinition(
            id = MetricId.DRAW_CALLS,
            name = "draw calls",
            color = Color(0.2f, 1f, 0.2f, 0.7f),
            collectValue = { profiler -> profiler.drawCalls }))
    }

    private data class MetricSeries(
        var history: IntArray = IntArray(0),
        var writeIndex: Int = 0,
        var size: Int = 0,
    )

    private val shapeRenderer = ShapeRenderer()
    private val cvarDebugGraph = Cvar.getInstance().Get("debug_r_graph", "1", 0)
    private val metricSeries = EnumMap<MetricId, MetricSeries>(MetricId::class.java)
    private val metricLabels = EnumMap<MetricId, Label>(MetricId::class.java)
    private val metricNameLabels = EnumMap<MetricId, Label>(MetricId::class.java)

    init {
        metricDefinitions.forEach { definition ->
            val metricId = definition.id
            metricSeries[metricId] = MetricSeries()
            val label = Label("", Scene2DSkin.defaultSkin).apply {
                isVisible = false
                color = Color(definition.color)
            }
            metricLabels[metricId] = label
            addActor(label)

            val nameLabel = Label(definition.name, Scene2DSkin.defaultSkin).apply {
                isVisible = false
                color = Color(definition.color)
            }
            metricNameLabels[metricId] = nameLabel
            addActor(nameLabel)
        }
        resizeMetricHistory(Gdx.graphics.width.coerceAtLeast(1))
    }

    fun collectMetrics(profiler: GLProfiler) {
        metricDefinitions.forEach { definition ->
            pushMetric(definition.id, definition.collectValue(profiler))
        }
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

        val graphBaseY = GRAPH_BASE_Y
        val graphPaddingTop = GRAPH_PADDING_TOP
        val graphHeight = (viewport.worldHeight - graphBaseY - graphPaddingTop).coerceAtMost(GRAPH_MAX_HEIGHT).coerceAtLeast(1f)
        val graphWidth = viewport.worldWidth.coerceAtLeast(1f)
        val globalMaxValue = activeSeries.values.maxOf { currentMaxValue(it) }.coerceAtLeast(1)
        val globalMax = globalMaxValue.toFloat()

        shapeRenderer.projectionMatrix = camera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)

        metricDefinitions.forEach { definition ->
            val metricId = definition.id
            val series = metricSeries.getValue(metricId)
            val label = metricLabels.getValue(metricId)
            val nameLabel = metricNameLabels.getValue(metricId)
            if (series.size == 0) {
                label.isVisible = false
                nameLabel.isVisible = false
                return@forEach
            }

            val metricColor = definition.color
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
            shapeRenderer.color.set(metricColor.r, metricColor.g, metricColor.b, MAX_LINE_ALPHA)
            shapeRenderer.line(0f, metricMaxY, graphWidth - 1f, metricMaxY)

            label.setText(metricMax.toString())
            label.setColor(metricColor)
            label.pack()
            label.setPosition(
                LABEL_LEFT_MARGIN,
                (metricMaxY - label.height - LABEL_LINE_GAP).coerceIn(graphBaseY, viewport.worldHeight - label.height)
            )
            label.isVisible = true

            nameLabel.setText(definition.name)
            nameLabel.setColor(metricColor)
            nameLabel.pack()
            nameLabel.setPosition(
                LABEL_LEFT_MARGIN,
                (metricMaxY + LABEL_LINE_GAP).coerceIn(graphBaseY, viewport.worldHeight - nameLabel.height)
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

    private fun pushMetric(metricId: MetricId, value: Int) {
        val width = Gdx.graphics.width.coerceAtLeast(1)
        ensureMetricHistoryWidth(width)
        val series = metricSeries.getValue(metricId)
        series.history[series.writeIndex] = value.coerceAtLeast(0)
        series.writeIndex = (series.writeIndex + 1) % series.history.size
        series.size = minOf(series.size + 1, series.history.size)
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
        val anyMismatched = metricDefinitions.any { definition ->
            metricSeries.getValue(definition.id).history.size != width
        }
        if (anyMismatched) {
            resizeMetricHistory(width)
        }
    }

    private fun resizeMetricHistory(width: Int) {
        metricDefinitions.forEach { definition ->
            val series = metricSeries.getValue(definition.id)
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
