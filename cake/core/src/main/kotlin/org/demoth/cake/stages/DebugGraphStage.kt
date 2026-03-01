package org.demoth.cake.stages

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.profiling.GLProfiler
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.utils.viewport.Viewport
import jake2.qcommon.exec.Cmd
import jake2.qcommon.exec.Cvar
import ktx.scene2d.Scene2DSkin
import java.util.EnumMap
import kotlin.math.max

enum class MetricId {
    CALLS,
    DRAW_CALLS,
    TEXTURE_BINDINGS,
    VERTEX_COUNT,
    SHADER_SWITCHES,
}

data class MetricDefinition(
    val id: MetricId,
    val name: String,
    val color: Color,
    val collectValue: (GLProfiler) -> Int,
)

/**
 * Draws scrolling per-frame metric lines.
 *
 * The graph keeps at most one sample per screen pixel in width and advances by one pixel each frame.
 */
class DebugGraphStage(viewport: Viewport) : Stage(viewport) {
    companion object {
        private const val SEGMENT_TOP_PADDING = 8f
        private const val SEGMENT_BOTTOM_PADDING = 8f
        private const val SEGMENT_LABEL_PADDING = 2f
        private const val LABEL_LEFT_MARGIN = 4f
        private const val LABEL_LINE_GAP = 6f
        private const val MAX_LINE_ALPHA = 0.35f

        val metricDefinitions: List<MetricDefinition> = listOf(
            MetricDefinition(
                id = MetricId.CALLS,
                name = "r_debug_calls",
                color = Color(1f, 0.85f, 0.2f, 0.7f),
                collectValue = { profiler -> profiler.calls },
            ),
            MetricDefinition(
                id = MetricId.DRAW_CALLS,
                name = "r_debug_drawcalls",
                color = Color(0.2f, 1f, 0.2f, 0.7f),
                collectValue = { profiler -> profiler.drawCalls },
            ),
            MetricDefinition(
                id = MetricId.TEXTURE_BINDINGS,
                name = "r_debug_texturebindings",
                color = Color(1f, 0.45f, 0.2f, 0.7f),
                collectValue = { profiler -> profiler.textureBindings },
            ),
            MetricDefinition(
                id = MetricId.VERTEX_COUNT,
                name = "r_debug_vertexcount",
                color = Color(0.2f, 0.7f, 1f, 0.7f),
                collectValue = { profiler -> profiler.vertexCount.total.toInt().coerceAtLeast(0) },
            ),
            MetricDefinition(
                id = MetricId.SHADER_SWITCHES,
                name = "r_debug_shaderswitches",
                color = Color(0.9f, 0.35f, 1f, 0.7f),
                collectValue = { profiler -> profiler.shaderSwitches },
            ),
        )
    }

    private class MetricSeries(
        var history: IntArray = IntArray(0),
        var writeIndex: Int = 0,
        var size: Int = 0,
    )

    private val shapeRenderer = ShapeRenderer()
    private val metricEnabledCvars = EnumMap<MetricId, jake2.qcommon.exec.cvar_t>(MetricId::class.java)
    private val metricSeries = EnumMap<MetricId, MetricSeries>(MetricId::class.java)
    private val metricLabels = EnumMap<MetricId, Label>(MetricId::class.java)
    private val metricNameLabels = EnumMap<MetricId, Label>(MetricId::class.java)

    init {
        metricDefinitions.forEach { definition ->
            val metricId = definition.id
            metricEnabledCvars[metricId] = Cvar.getInstance().Get(definition.name, "1", 0)
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

        Cmd.AddCommand("r_debug_hideall") {
            metricDefinitions.forEach { Cvar.getInstance().Set(it.name, "0") }
        }
        Cmd.AddCommand("r_debug_showall") {
            metricDefinitions.forEach { Cvar.getInstance().Set(it.name, "1") }
        }
    }

    fun collectMetrics(profiler: GLProfiler) {
        metricDefinitions.forEach { definition ->
            if (isMetricEnabled(definition.id)) {
                pushMetric(definition.id, definition.collectValue(profiler))
            } else {
                clearMetricHistory(definition.id)
            }
        }
    }

    fun resetMetrics() {
        resizeMetricHistory(Gdx.graphics.width.coerceAtLeast(1))
        hideAllMetricLabels()
    }

    override fun draw() {
        if (metricDefinitions.none { isMetricEnabled(it.id) }) {
            hideAllMetricLabels()
            return
        }

        val enabledMetricDefinitions = metricDefinitions.filter { isMetricEnabled(it.id) }
        if (enabledMetricDefinitions.isEmpty()) {
            hideAllMetricLabels()
            return
        }

        val activeSeries = enabledMetricDefinitions.mapNotNull { definition ->
            metricSeries[definition.id]?.takeIf { it.size > 0 }
        }
        if (activeSeries.isEmpty()) {
            hideAllMetricLabels()
            return
        }

        viewport.apply()

        val graphWidth = viewport.worldWidth.coerceAtLeast(1f)
        val metricCount = enabledMetricDefinitions.size.coerceAtLeast(1)
        val segmentHeight = (viewport.worldHeight / metricCount).coerceAtLeast(1f)

        shapeRenderer.projectionMatrix = camera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)

        metricDefinitions
            .filterNot { isMetricEnabled(it.id) }
            .forEach { definition ->
                metricLabels.getValue(definition.id).isVisible = false
                metricNameLabels.getValue(definition.id).isVisible = false
            }

        enabledMetricDefinitions.forEachIndexed { metricIndex, definition ->
            val metricId = definition.id
            val series = metricSeries.getValue(metricId)
            val label = metricLabels.getValue(metricId)
            val nameLabel = metricNameLabels.getValue(metricId)
            if (series.size == 0) {
                label.isVisible = false
                nameLabel.isVisible = false
                return@forEachIndexed
            }

            val segmentTop = viewport.worldHeight - metricIndex * segmentHeight
            val segmentBottom = segmentTop - segmentHeight
            val metricMax = currentMaxValue(series)
            val metricScaleMax = metricMax.toFloat().coerceAtLeast(1f)
            val metricColor = definition.color

            label.setText(metricMax.toString())
            label.setColor(metricColor)
            label.pack()

            nameLabel.setText(definition.name)
            nameLabel.setColor(metricColor)
            nameLabel.pack()

            val graphBaseY = segmentBottom + SEGMENT_BOTTOM_PADDING + label.height + LABEL_LINE_GAP
            val graphTopY = segmentTop - SEGMENT_TOP_PADDING - nameLabel.height - LABEL_LINE_GAP
            val graphHeight = (graphTopY - graphBaseY).coerceAtLeast(1f)
            shapeRenderer.color.set(metricColor.r, metricColor.g, metricColor.b, metricColor.a)
            if (series.size > 1) {
                for (x in 1 until series.size) {
                    val previousValue = historyValueAt(series, x - 1).toFloat()
                    val currentValue = historyValueAt(series, x).toFloat()
                    val y1 = graphBaseY + (previousValue / metricScaleMax) * graphHeight
                    val y2 = graphBaseY + (currentValue / metricScaleMax) * graphHeight
                    shapeRenderer.line((x - 1).toFloat(), y1, x.toFloat(), y2)
                }
            }

            val metricMaxY = graphBaseY + (metricMax / metricScaleMax) * graphHeight
            shapeRenderer.color.set(metricColor.r, metricColor.g, metricColor.b, MAX_LINE_ALPHA)
            shapeRenderer.line(0f, metricMaxY, graphWidth - 1f, metricMaxY)

            label.setPosition(
                LABEL_LEFT_MARGIN,
                (metricMaxY - label.height - LABEL_LINE_GAP)
                    .coerceIn(
                        segmentBottom + SEGMENT_LABEL_PADDING,
                        segmentTop - label.height - SEGMENT_LABEL_PADDING
                    )
            )
            label.isVisible = true

            nameLabel.setPosition(
                LABEL_LEFT_MARGIN,
                (metricMaxY + LABEL_LINE_GAP)
                    .coerceIn(
                        segmentBottom + SEGMENT_LABEL_PADDING,
                        segmentTop - nameLabel.height - SEGMENT_LABEL_PADDING
                    )
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

    private fun clearMetricHistory(metricId: MetricId) {
        val series = metricSeries.getValue(metricId)
        series.writeIndex = 0
        series.size = 0
    }

    private fun isMetricEnabled(metricId: MetricId): Boolean =
        metricEnabledCvars.getValue(metricId).value != 0f

    private fun hideAllMetricLabels() {
        metricLabels.values.forEach { it.isVisible = false }
        metricNameLabels.values.forEach { it.isVisible = false }
    }
}
