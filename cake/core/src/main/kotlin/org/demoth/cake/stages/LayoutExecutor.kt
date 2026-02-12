package org.demoth.cake.stages

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import jake2.qcommon.Defines
import jake2.qcommon.Defines.MAX_CLIENTS
import jake2.qcommon.Defines.MAX_CONFIGSTRINGS
import jake2.qcommon.Defines.MAX_ITEMS
import jake2.qcommon.player_state_t
import org.demoth.cake.GameConfiguration
import org.demoth.cake.ui.GameUiStyle

internal interface LayoutDataProvider {
    fun getImage(imageIndex: Int): Texture?
    fun getConfigString(configIndex: Int): String?
    fun getNamedPic(picName: String): Texture? = null
    fun getClientInfo(clientIndex: Int): LayoutClientInfo? = null
    fun getCurrentPlayerIndex(): Int = -1
}

internal data class LayoutClientInfo(
    val name: String,
    val icon: Texture?,
)

/**
 * Executes Quake layout scripts and renders them in libGDX.
 *
 * Parsing and command emission happen in Quake screen space (top-left origin).
 * Rendering performs an explicit Quake->libGDX transform (bottom-left origin).
 */
class LayoutExecutor(
    private val spriteBatch: SpriteBatch,
    private val style: GameUiStyle,
) {
    internal sealed interface LayoutCommand {
        data class Image(val x: Int, val y: Int, val texture: Texture?) : LayoutCommand
        data class Text(
            val x: Int,
            val y: Int,
            val text: String,
            val alt: Boolean,
            val centerWidth: Int? = null,
        ) : LayoutCommand
        data class Number(val x: Int, val y: Int, val value: Short, val width: Int, val color: Int) : LayoutCommand
    }

    internal fun compileLayoutCommands(
        layout: String,
        serverFrame: Int,
        stats: ShortArray,
        screenWidth: Int,
        screenHeight: Int,
        dataProvider: LayoutDataProvider,
    ): List<LayoutCommand> = LayoutCommandCompiler.compile(layout, serverFrame, stats, screenWidth, screenHeight, dataProvider)

    fun executeLayoutString(
        layout: String?,
        serverFrame: Int,
        stats: ShortArray,
        screenWidth: Int,
        screenHeight: Int,
        gameConfig: GameConfiguration,
        playerIndex: Int = -1,
    ) {
        if (layout.isNullOrEmpty()) return

        val dataProvider = object : LayoutDataProvider {
            override fun getImage(imageIndex: Int): Texture? = gameConfig.getImage(imageIndex)
            override fun getConfigString(configIndex: Int): String? = gameConfig.getConfigValue(configIndex)
            override fun getNamedPic(picName: String): Texture? = gameConfig.getNamedPic(picName)
            override fun getClientInfo(clientIndex: Int): LayoutClientInfo? {
                val name = gameConfig.getClientName(clientIndex) ?: return null
                return LayoutClientInfo(name = name, icon = gameConfig.getClientIcon(clientIndex))
            }
            override fun getCurrentPlayerIndex(): Int = playerIndex
        }
        val commands = compileLayoutCommands(layout, serverFrame, stats, screenWidth, screenHeight, dataProvider)
        for (command in commands) {
            when (command) {
                is LayoutCommand.Image -> drawImageQuake(command.x, command.y, command.texture, screenHeight)
                is LayoutCommand.Text -> drawTextQuake(
                    command.x,
                    command.y,
                    command.text,
                    command.alt,
                    command.centerWidth,
                    screenHeight
                )
                is LayoutCommand.Number -> drawNumberQuake(
                    command.x,
                    command.y,
                    command.value,
                    command.width,
                    command.color,
                    screenHeight
                )
            }
        }
    }

    private fun drawImageQuake(x: Int, y: Int, texture: Texture?, screenHeight: Int) {
        if (texture == null) return
        val gdxY = LayoutCoordinateMapper.imageY(y, texture.height, screenHeight)
        spriteBatch.draw(texture, x.toFloat(), gdxY.toFloat())
    }

    private fun drawTextQuake(x: Int, y: Int, text: String, alt: Boolean, centerWidth: Int?, screenHeight: Int) {
        if (centerWidth != null) {
            drawHudStringQuake(text, x, y, centerWidth, alt, screenHeight)
            return
        }
        drawTextLineQuake(text, x, y, alt, screenHeight)
    }

    private fun drawHudStringQuake(text: String, x: Int, y: Int, centerWidth: Int, alt: Boolean, screenHeight: Int) {
        var cursorY = y
        val lines = text.split('\n')
        for (line in lines) {
            val lineX = x + (centerWidth - line.length * 8) / 2
            drawTextLineQuake(line, lineX, cursorY, alt, screenHeight)
            cursorY += 8
        }
    }

    private fun drawTextLineQuake(text: String, x: Int, y: Int, alt: Boolean, screenHeight: Int) {
        val gdxY = LayoutCoordinateMapper.textY(y, screenHeight)
        val font = style.hudFont
        val prevR = font.color.r
        val prevG = font.color.g
        val prevB = font.color.b
        val prevA = font.color.a
        font.color.set(1f, 1f, 1f, 1f)
        font.draw(spriteBatch, mapAltText(text, alt), x.toFloat(), gdxY.toFloat())
        font.color.set(prevR, prevG, prevB, prevA)
    }

    private fun drawNumberQuake(x: Int, y: Int, value: Short, width: Int, color: Int, screenHeight: Int) {
        style.hudNumberFont.draw(spriteBatch, x, y, value, width, color, screenHeight)
    }

    fun drawInventory(gameConfig: GameConfiguration, screenWidth: Int, screenHeight: Int, playerstate: player_state_t) {
        val x = screenWidth / 2
        var y = screenHeight / 2
        val selectedIndex = playerstate.stats[Defines.STAT_SELECTED_ITEM].toInt()
        drawTextQuake(x, y, "Inventory:", false, null, screenHeight)
        for (i in 0..<MAX_ITEMS) {
            val amount = gameConfig.inventory[i]
            if (amount > 0) {
                y -= 16
                val selectedPrefix = if (i == selectedIndex) "->" else "  "
                val amountText = amount.toString().padStart(3)
                val text = "$selectedPrefix $amountText ${gameConfig.getItemName(i) ?: ""}"
                drawTextQuake(x, y, text, false, null, screenHeight)
            }
        }
    }

    fun drawCrosshair(screenWidth: Int, screenHeight: Int) {
        drawTextQuake(screenWidth / 2, screenHeight / 2, "+", false, null, screenHeight)
    }

    private fun mapAltText(text: String, alt: Boolean): String {
        if (!alt) return text
        val mapped = CharArray(text.length)
        for (i in text.indices) {
            mapped[i] = ((text[i].code xor 0x80) and 0xFF).toChar()
        }
        return String(mapped)
    }
}

internal object LayoutCommandCompiler {
    fun compile(
        layout: String,
        serverFrame: Int,
        stats: ShortArray,
        screenWidth: Int,
        screenHeight: Int,
        dataProvider: LayoutDataProvider,
    ): List<LayoutExecutor.LayoutCommand> {
        var x = 0
        var y = 0
        val commands = mutableListOf<LayoutExecutor.LayoutCommand>()
        val parser = LayoutParserCompat(layout)

        while (parser.hasNext()) {
            parser.next()
            if (parser.tokenEquals("xl")) {
                parser.next()
                x = parser.tokenAsInt()
                continue
            }
            if (parser.tokenEquals("xr")) {
                parser.next()
                x = screenWidth + parser.tokenAsInt()
                continue
            }
            if (parser.tokenEquals("xv")) {
                parser.next()
                x = screenWidth / 2 - 160 + parser.tokenAsInt()
                continue
            }

            if (parser.tokenEquals("yt")) {
                parser.next()
                y = parser.tokenAsInt()
                continue
            }
            if (parser.tokenEquals("yb")) {
                parser.next()
                y = screenHeight + parser.tokenAsInt()
                continue
            }
            if (parser.tokenEquals("yv")) {
                parser.next()
                y = screenHeight / 2 - 120 + parser.tokenAsInt()
                continue
            }

            if (parser.tokenEquals("pic")) {
                parser.next()
                val statIndex = parser.tokenAsInt()
                val imageIndex = stats[statIndex]
                commands += LayoutExecutor.LayoutCommand.Image(x, y, dataProvider.getImage(imageIndex.toInt()))
                continue
            }

            if (parser.tokenEquals("client")) {
                parser.next()
                x = screenWidth / 2 - 160 + parser.tokenAsInt()

                parser.next()
                y = screenHeight / 2 - 120 + parser.tokenAsInt()

                parser.next()
                val clientIndex = parser.tokenAsInt()
                check(clientIndex in 0 until MAX_CLIENTS) { "client >= MAX_CLIENTS" }
                val clientInfo = dataProvider.getClientInfo(clientIndex)

                parser.next()
                val score = parser.tokenAsInt()

                parser.next()
                val ping = parser.tokenAsInt()

                parser.next()
                val time = parser.tokenAsInt()

                commands += LayoutExecutor.LayoutCommand.Text(x + 32, y, clientInfo?.name ?: "", alt = true)
                commands += LayoutExecutor.LayoutCommand.Text(x + 32, y + 8, "Score: ", alt = false)
                commands += LayoutExecutor.LayoutCommand.Text(x + 32 + 7 * 8, y + 8, "$score", alt = true)
                commands += LayoutExecutor.LayoutCommand.Text(x + 32, y + 16, "Ping:  $ping", alt = false)
                commands += LayoutExecutor.LayoutCommand.Text(x + 32, y + 24, "Time:  $time", alt = false)
                commands += LayoutExecutor.LayoutCommand.Image(x, y, clientInfo?.icon)
                continue
            }

            if (parser.tokenEquals("ctf")) {
                parser.next()
                x = screenWidth / 2 - 160 + parser.tokenAsInt()

                parser.next()
                y = screenHeight / 2 - 120 + parser.tokenAsInt()

                parser.next()
                val clientIndex = parser.tokenAsInt()
                check(clientIndex in 0 until MAX_CLIENTS) { "client >= MAX_CLIENTS" }
                val clientInfo = dataProvider.getClientInfo(clientIndex)

                parser.next()
                val score = parser.tokenAsInt()

                parser.next()
                val ping = parser.tokenAsInt().coerceAtMost(999)

                val block = String.format("%3d %3d %-12.12s", score, ping, clientInfo?.name ?: "")
                val isCurrentPlayer = clientIndex == dataProvider.getCurrentPlayerIndex()
                commands += LayoutExecutor.LayoutCommand.Text(x, y, block, alt = isCurrentPlayer)
                continue
            }

            if (parser.tokenEquals("picn")) {
                parser.next()
                commands += LayoutExecutor.LayoutCommand.Image(x, y, dataProvider.getNamedPic(parser.token()))
                continue
            }

            if (parser.tokenEquals("num")) {
                parser.next()
                val width = parser.tokenAsInt()
                parser.next()
                val statIndex = parser.tokenAsInt()
                commands += LayoutExecutor.LayoutCommand.Number(x, y, stats[statIndex], width, 0)
                continue
            }

            if (parser.tokenEquals("hnum")) {
                val health = stats[Defines.STAT_HEALTH]
                val color = when {
                    health > 25 -> 0
                    health > 0 -> (serverFrame shr 2) and 1
                    else -> 1
                }
                commands += LayoutExecutor.LayoutCommand.Number(x, y, health, 3, color)
                continue
            }

            if (parser.tokenEquals("anum")) {
                val ammo = stats[Defines.STAT_AMMO]
                if (ammo < 0) {
                    continue
                }
                val color = if (ammo > 5) 0 else ((serverFrame shr 2) and 1)
                commands += LayoutExecutor.LayoutCommand.Number(x, y, ammo, 3, color)
                continue
            }

            if (parser.tokenEquals("rnum")) {
                val armor = stats[Defines.STAT_ARMOR]
                if (armor < 1) {
                    continue
                }
                commands += LayoutExecutor.LayoutCommand.Number(x, y, armor, 3, 0)
                continue
            }

            if (parser.tokenEquals("stat_string")) {
                parser.next()
                val statIndex = parser.tokenAsInt()
                if (statIndex !in 0 until MAX_CONFIGSTRINGS) {
                    throw IllegalStateException("stat_string: Invalid player stat index: $statIndex")
                }
                val configIndex = stats[statIndex]
                if (configIndex !in 0 until MAX_CONFIGSTRINGS) {
                    throw IllegalStateException("stat_string: Invalid config string index: $configIndex")
                }
                val value = dataProvider.getConfigString(configIndex.toInt()) ?: ""
                commands += LayoutExecutor.LayoutCommand.Text(x, y, value, false)
                continue
            }

            if (parser.tokenEquals("cstring")) {
                parser.next()
                commands += LayoutExecutor.LayoutCommand.Text(x, y, parser.token(), false, centerWidth = 320)
                continue
            }

            if (parser.tokenEquals("string")) {
                parser.next()
                commands += LayoutExecutor.LayoutCommand.Text(x, y, parser.token(), false)
                continue
            }

            if (parser.tokenEquals("cstring2")) {
                parser.next()
                commands += LayoutExecutor.LayoutCommand.Text(x, y, parser.token(), true, centerWidth = 320)
                continue
            }

            if (parser.tokenEquals("string2")) {
                parser.next()
                commands += LayoutExecutor.LayoutCommand.Text(x, y, parser.token(), true)
                continue
            }

            if (parser.tokenEquals("if")) {
                parser.next()
                val statIndex = parser.tokenAsInt()
                val value = stats[statIndex]
                if (value.toInt() == 0) {
                    parser.next()
                    while (parser.hasNext() && !parser.tokenEquals("endif")) {
                        parser.next()
                    }
                }
                continue
            }
        }

        return commands
    }
}
