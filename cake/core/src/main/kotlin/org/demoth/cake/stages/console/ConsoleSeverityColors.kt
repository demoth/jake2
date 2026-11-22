package org.demoth.cake.stages.console

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import jake2.qcommon.Com.ConsoleLevel

internal class ConsoleSeverityColors(skin: Skin) {
    private val info = skin.getColor("font")
    private val warning = skin.getColor("warning")
    private val error = skin.getColor("error")
    private val debug = skin.getColor("fontSecondary")
    private val trace = skin.getColor("message")

    operator fun get(level: ConsoleLevel): Color = when (level) {
        ConsoleLevel.PRINT, ConsoleLevel.INFO -> info
        ConsoleLevel.ERROR -> error
        ConsoleLevel.WARN -> warning
        ConsoleLevel.DEBUG -> debug
        ConsoleLevel.TRACE -> trace
    }
}
