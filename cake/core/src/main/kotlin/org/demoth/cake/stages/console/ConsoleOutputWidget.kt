package org.demoth.cake.stages.console

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack
import jake2.qcommon.Com
import kotlin.math.floor

class ConsoleOutputWidget(
    private val consoleBuffer: ConsoleBuffer,
    skin: Skin,
) : Widget() {
    private val font: BitmapFont = skin.getFont("default")
    private val infoColor: Color = Color(skin.getColor("font"))
    private val warningColor: Color = Color(skin.getColor("warning"))
    private val errorColor: Color = Color(skin.getColor("error"))
    private val clipBounds = Rectangle()
    private val scissors = Rectangle()
    private val wrappedLines = mutableListOf<WrappedLine>()
    private var cachedColumns = -1
    private var cachedVersion = -1L
    private var topLine = 0

    init {
        touchable = Touchable.enabled
    }

    fun scrollLines(delta: Int) {
        refreshWrappedLines()
        topLine = (topLine + delta).coerceIn(0, maxTopLine())
    }

    fun scrollPage(deltaPages: Int) {
        val pageSize = (visibleLineCapacity() - 1).coerceAtLeast(1)
        scrollLines(pageSize * deltaPages)
    }

    fun scrollToBottom() {
        refreshWrappedLines()
        topLine = maxTopLine()
    }

    override fun draw(batch: Batch, parentAlpha: Float) {
        refreshWrappedLines()

        if (width <= 0f || height <= 0f) {
            return
        }

        clipBounds.set(x, y, width, height)
        stage ?: return
        ScissorStack.calculateScissors(stage.camera, batch.transformMatrix, clipBounds, scissors)
        if (!ScissorStack.pushScissors(scissors)) {
            return
        }

        val oldColor = Color(font.color)
        val startX = x + HORIZONTAL_PADDING
        var drawY = y + height - VERTICAL_PADDING
        val endExclusive = (topLine + visibleLineCapacity()).coerceAtMost(wrappedLines.size)

        try {
            for (index in topLine until endExclusive) {
                font.color = colorFor(wrappedLines[index].severity)
                font.draw(batch, wrappedLines[index].text, startX, drawY)
                drawY -= font.lineHeight
            }
        } finally {
            font.color = oldColor
            batch.flush()
            ScissorStack.popScissors()
        }
    }

    private fun refreshWrappedLines() {
        val columns = maxColumns()
        val version = consoleBuffer.version
        if (columns == cachedColumns && version == cachedVersion) {
            return
        }

        val wasAtBottom = topLine >= maxTopLine()
        wrappedLines.clear()

        consoleBuffer.entries().forEach { entry ->
            appendWrappedLines(entry, columns)
        }

        cachedColumns = columns
        cachedVersion = version
        topLine = if (wasAtBottom) {
            maxTopLine()
        } else {
            topLine.coerceIn(0, maxTopLine())
        }
    }

    private fun appendWrappedLines(entry: ConsoleEntry, columns: Int) {
        val normalized = entry.text.replace("\r\n", "\n").replace('\r', '\n')
        val segments = normalized.split('\n')
        val endExclusive = if (normalized.endsWith('\n')) segments.size - 1 else segments.size

        for (index in 0 until endExclusive) {
            appendWrappedVisualLines(entry.severity, segments[index], columns)
        }

        if (endExclusive == 0) {
            wrappedLines.add(WrappedLine("", entry.severity))
        }
    }

    private fun appendWrappedVisualLines(severity: Com.ConsoleLevel, line: String, columns: Int) {
        if (line.isEmpty()) {
            wrappedLines.add(WrappedLine("", severity))
            return
        }

        var start = 0
        while (start < line.length) {
            val end = (start + columns).coerceAtMost(line.length)
            wrappedLines.add(WrappedLine(line.substring(start, end), severity))
            start = end
        }
    }

    private fun maxColumns(): Int {
        val contentWidth = (width - HORIZONTAL_PADDING * 2f).coerceAtLeast(font.spaceXadvance)
        return floor(contentWidth / font.spaceXadvance).toInt().coerceAtLeast(1)
    }

    private fun visibleLineCapacity(): Int {
        val contentHeight = (height - VERTICAL_PADDING * 2f).coerceAtLeast(font.lineHeight)
        return floor(contentHeight / font.lineHeight).toInt().coerceAtLeast(1)
    }

    private fun maxTopLine(): Int = (wrappedLines.size - visibleLineCapacity()).coerceAtLeast(0)

    private fun colorFor(severity: Com.ConsoleLevel): Color = when (severity) {
        Com.ConsoleLevel.INFO -> infoColor
        Com.ConsoleLevel.WARN -> warningColor
        Com.ConsoleLevel.ERROR -> errorColor
    }

    private data class WrappedLine(
        val text: String,
        val severity: Com.ConsoleLevel,
    )

    companion object {
        private const val HORIZONTAL_PADDING = 8f
        private const val VERTICAL_PADDING = 8f
    }
}
