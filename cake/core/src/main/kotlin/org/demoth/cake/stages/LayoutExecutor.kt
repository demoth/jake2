package org.demoth.cake.stages

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import jake2.qcommon.Defines
import jake2.qcommon.Defines.MAX_ITEMS
import jake2.qcommon.Defines.MAX_STATS
import jake2.qcommon.player_state_t
import org.demoth.cake.GameConfiguration
import org.demoth.cake.ui.GameUiStyle

internal interface LayoutDataProvider {
    fun getImage(imageIndex: Int): Texture?
    fun getConfigString(configIndex: Int): String?
}

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
        data class Text(val x: Int, val y: Int, val text: String, val alt: Boolean) : LayoutCommand
        data class Number(val x: Int, val y: Int, val value: Short, val width: Int, val color: Int) : LayoutCommand
    }

    internal fun compileLayoutCommands(
        layout: String,
        serverFrame: Int,
        stats: ShortArray,
        screenWidth: Int,
        screenHeight: Int,
        dataProvider: LayoutDataProvider,
    ): List<LayoutCommand> {
        var x = 0
        var y = 0
        val commands = mutableListOf<LayoutCommand>()
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
                commands += LayoutCommand.Image(x, y, dataProvider.getImage(imageIndex.toInt()))
                continue
            }

            if (parser.tokenEquals("client")) {
                // to be implemented
                continue
            }

            if (parser.tokenEquals("ctf")) {
                // to be implemented
                continue
            }

            if (parser.tokenEquals("picn")) {
                // to be implemented
                continue
            }

            if (parser.tokenEquals("num")) {
                parser.next()
                val width = parser.tokenAsInt()
                parser.next()
                val statIndex = parser.tokenAsInt()
                commands += LayoutCommand.Number(x, y, stats[statIndex], width, 0)
                continue
            }

            if (parser.tokenEquals("hnum")) {
                val health = stats[Defines.STAT_HEALTH]
                val color = when {
                    health > 25 -> 0
                    health > 0 -> (serverFrame shr 2) and 1
                    else -> 1
                }
                commands += LayoutCommand.Number(x, y, health, 3, color)
                continue
            }

            if (parser.tokenEquals("anum")) {
                val ammo = stats[Defines.STAT_AMMO]
                if (ammo < 0) {
                    continue
                }
                val color = if (ammo > 5) 0 else ((serverFrame shr 2) and 1)
                commands += LayoutCommand.Number(x, y, ammo, 3, color)
                continue
            }

            if (parser.tokenEquals("rnum")) {
                val armor = stats[Defines.STAT_ARMOR]
                if (armor < 1) {
                    continue
                }
                commands += LayoutCommand.Number(x, y, armor, 3, 0)
                continue
            }

            if (parser.tokenEquals("stat_string")) {
                parser.next()
                val statIndex = parser.tokenAsInt()
                if (statIndex !in (0..MAX_STATS)) {
                    throw IllegalStateException("stat_string: Invalid player stat index: $statIndex")
                }
                val configIndex = stats[statIndex]
                val value = dataProvider.getConfigString(configIndex.toInt()) ?: ""
                commands += LayoutCommand.Text(x, y, value, false)
                continue
            }

            if (parser.tokenEquals("cstring") || parser.tokenEquals("string")) {
                parser.next()
                commands += LayoutCommand.Text(x, y, parser.token(), false)
                continue
            }

            if (parser.tokenEquals("cstring2") || parser.tokenEquals("string2")) {
                parser.next()
                commands += LayoutCommand.Text(x, y, parser.token(), true)
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

    fun executeLayoutString(
        layout: String?,
        serverFrame: Int,
        stats: ShortArray,
        screenWidth: Int,
        screenHeight: Int,
        gameConfig: GameConfiguration
    ) {
        if (layout.isNullOrEmpty()) return

        val dataProvider = object : LayoutDataProvider {
            override fun getImage(imageIndex: Int): Texture? = gameConfig.getImage(imageIndex)
            override fun getConfigString(configIndex: Int): String? = gameConfig.getConfigValue(configIndex)
        }
        val commands = compileLayoutCommands(layout, serverFrame, stats, screenWidth, screenHeight, dataProvider)
        for (command in commands) {
            when (command) {
                is LayoutCommand.Image -> drawImageQuake(command.x, command.y, command.texture, screenHeight)
                is LayoutCommand.Text -> drawTextQuake(command.x, command.y, command.text, command.alt, screenHeight)
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
        val gdxY = screenHeight - y - texture.height
        spriteBatch.draw(texture, x.toFloat(), gdxY.toFloat())
    }

    private fun drawTextQuake(x: Int, y: Int, text: String, alt: Boolean, screenHeight: Int) {
        val gdxY = screenHeight - y
        style.hudFont.draw(spriteBatch, text, x.toFloat(), gdxY.toFloat())
    }

    private fun drawNumberQuake(x: Int, y: Int, value: Short, width: Int, color: Int, screenHeight: Int) {
        style.hudNumberFont.draw(spriteBatch, x, y, value, width, color, screenHeight)
    }

    fun drawInventory(gameConfig: GameConfiguration, screenWidth: Int, screenHeight: Int, playerstate: player_state_t) {
        val x = screenWidth / 2
        var y = screenHeight / 2
        val selectedIndex = playerstate.stats[Defines.STAT_SELECTED_ITEM].toInt()
        drawTextQuake(x, y, "Inventory:", false, screenHeight)
        for (i in 0..<MAX_ITEMS) {
            val amount = gameConfig.inventory[i]
            if (amount > 0) {
                y -= 16
                val selectedPrefix = if (i == selectedIndex) "->" else "  "
                val amountText = amount.toString().padStart(3)
                val text = "$selectedPrefix $amountText ${gameConfig.getItemName(i) ?: ""}"
                drawTextQuake(x, y, text, false, screenHeight)
            }
        }
    }

    fun drawCrosshair(screenWidth: Int, screenHeight: Int) {
        drawTextQuake(screenWidth / 2, screenHeight / 2, "+", false, screenHeight)
    }
}
