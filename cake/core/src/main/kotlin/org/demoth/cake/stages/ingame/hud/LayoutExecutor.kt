package org.demoth.cake.stages.ingame.hud

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import jake2.qcommon.Globals
import jake2.qcommon.Defines
import jake2.qcommon.Defines.MAX_CLIENTS
import jake2.qcommon.Defines.MAX_CONFIGSTRINGS
import jake2.qcommon.Defines.MAX_ITEMS
import jake2.qcommon.player_state_t
import org.demoth.cake.GameConfiguration
import org.demoth.cake.ui.GameUiStyle

/**
 * Executes IdTech2 layout scripts and renders them in libGDX.
 *
 * Parsing and command emission happen in IdTech2 screen space (top-left origin).
 * Rendering performs an explicit IdTech2->libGDX transform (bottom-left origin).
 *
 * Legacy counterparts:
 * - `client/SCR.ExecuteLayoutString` (status/layout script execution)
 * - `client/CL_inv.DrawInventory` (inventory panel metrics and rows)
 */
class LayoutExecutor(
    private val spriteBatch: SpriteBatch,
    private val style: GameUiStyle,
) {
    private companion object {
        const val INVENTORY_DISPLAY_ITEMS = 17
        const val INVENTORY_WIDTH = 256
        const val INVENTORY_HEIGHT = 240
    }

    sealed interface LayoutCommand {
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

    /**
     * Execute a precompiled layout program for the current frame.
     */
    fun executeLayoutProgram(
        program: LayoutProgram?,
        serverFrame: Int,
        stats: ShortArray,
        screenWidth: Int,
        screenHeight: Int,
        gameConfig: GameConfiguration,
        playerIndex: Int = -1,
    ) {
        if (program == null) return
        val commands = LayoutCommandCompiler.evaluate(
            program = program,
            serverFrame = serverFrame,
            stats = stats,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            gameConfig = gameConfig,
            playerIndex = playerIndex,
        )
        drawCommands(commands, screenHeight)
    }

    private fun drawCommands(commands: List<LayoutCommand>, screenHeight: Int) {
        for (command in commands) {
            when (command) {
                is LayoutCommand.Image -> drawImageIdTech2(command.x, command.y, command.texture, screenHeight)
                is LayoutCommand.Text -> drawTextIdTech2(
                    command.x,
                    command.y,
                    command.text,
                    command.alt,
                    command.centerWidth,
                    screenHeight
                )
                is LayoutCommand.Number -> drawNumberIdTech2(
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

    private fun drawImageIdTech2(x: Int, y: Int, texture: Texture?, screenHeight: Int) {
        if (texture == null) return
        val gdxY = LayoutCoordinateMapper.imageY(y, texture.height, screenHeight)
        spriteBatch.draw(texture, x.toFloat(), gdxY.toFloat())
    }

    private fun drawTextIdTech2(x: Int, y: Int, text: String, alt: Boolean, centerWidth: Int?, screenHeight: Int) {
        if (centerWidth != null) {
            drawHudStringIdTech2(text, x, y, centerWidth, alt, screenHeight)
            return
        }
        drawTextLineIdTech2(text, x, y, alt, screenHeight)
    }

    private fun drawHudStringIdTech2(text: String, x: Int, y: Int, centerWidth: Int, alt: Boolean, screenHeight: Int) {
        var cursorY = y
        val lines = text.split('\n')
        for (line in lines) {
            val lineX = x + (centerWidth - line.length * 8) / 2
            drawTextLineIdTech2(line, lineX, cursorY, alt, screenHeight)
            cursorY += 8
        }
    }

    private fun drawTextLineIdTech2(text: String, x: Int, y: Int, alt: Boolean, screenHeight: Int) {
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

    private fun drawNumberIdTech2(x: Int, y: Int, value: Short, width: Int, color: Int, screenHeight: Int) {
        style.hudNumberFont.draw(spriteBatch, x, y, value, width, color, screenHeight)
    }

    /**
     * Draws inventory panel with legacy IdTech2 layout metrics (256x240 panel + 17 visible rows).
     *
     * Quirk:
     * hotkey bindings are not integrated with current input subsystem yet, so the hotkey column
     * is intentionally rendered blank.
     */
    fun drawInventory(gameConfig: GameConfiguration, screenWidth: Int, screenHeight: Int, playerstate: player_state_t) {
        val selected = playerstate.stats[Defines.STAT_SELECTED_ITEM].toInt()
        val visibleItems = mutableListOf<Int>()
        var selectedVisibleIndex = 0
        for (i in 0 until MAX_ITEMS) {
            if (i == selected) {
                selectedVisibleIndex = visibleItems.size
            }
            if (gameConfig.inventory[i] != 0) {
                visibleItems += i
            }
        }

        var top = selectedVisibleIndex - INVENTORY_DISPLAY_ITEMS / 2
        if (visibleItems.size - top < INVENTORY_DISPLAY_ITEMS) {
            top = visibleItems.size - INVENTORY_DISPLAY_ITEMS
        }
        if (top < 0) {
            top = 0
        }

        val panelX = (screenWidth - INVENTORY_WIDTH) / 2
        val panelY = (screenHeight - INVENTORY_HEIGHT) / 2
        drawImageIdTech2(panelX, panelY + 8, gameConfig.getNamedPic("inventory"), screenHeight)

        val textX = panelX + 24
        var textY = panelY + 24
        drawTextIdTech2(textX, textY, "hotkey ### item", false, null, screenHeight)
        drawTextIdTech2(textX, textY + 8, "------ --- ----", false, null, screenHeight)
        textY += 16

        val last = minOf(visibleItems.size, top + INVENTORY_DISPLAY_ITEMS)
        for (visibleIndex in top until last) {
            val itemIndex = visibleItems[visibleIndex]
            val amount = gameConfig.inventory[itemIndex]
            val itemName = gameConfig.getItemName(itemIndex) ?: ""
            val line = String.format("%6s %3d %s", "", amount, itemName)
            if (itemIndex != selected) {
                drawTextIdTech2(textX, textY, line, true, null, screenHeight)
            } else {
                if (((Globals.curtime / 100) and 1) != 0) {
                    drawTextIdTech2(textX - 8, textY, Char(15).toString(), false, null, screenHeight)
                }
                drawTextIdTech2(textX, textY, line, false, null, screenHeight)
            }
            textY += 8
        }
    }

    fun drawCrosshair(screenWidth: Int, screenHeight: Int) {
        drawTextIdTech2(screenWidth / 2, screenHeight / 2, "+", false, null, screenHeight)
    }

    /**
     * Draw plain HUD text in IdTech2 screen coordinates.
     *
     * This is used by non-layout UI flows (for example center-print messages) that still need
     * the same active game style fonts and IdTech2 coordinate transform.
     */
    fun drawText(
        x: Int,
        y: Int,
        text: String,
        screenHeight: Int,
        alt: Boolean = false,
        centerWidth: Int? = null,
    ) {
        drawTextIdTech2(x, y, text, alt, centerWidth, screenHeight)
    }

    /**
     * Apply legacy high-bit toggle used by IdTech2 alternate HUD text (`DrawAltString` / `^ 0x80`).
     */
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
    private data class Cursor(var x: Int = 0, var y: Int = 0)

    fun evaluate(
        program: LayoutProgram,
        serverFrame: Int,
        stats: ShortArray,
        screenWidth: Int,
        screenHeight: Int,
        gameConfig: GameConfiguration,
        playerIndex: Int = -1,
    ): List<LayoutExecutor.LayoutCommand> {
        val commands = mutableListOf<LayoutExecutor.LayoutCommand>()
        val cursor = Cursor()
        evaluateOps(
            ops = program.ops,
            cursor = cursor,
            serverFrame = serverFrame,
            stats = stats,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            gameConfig = gameConfig,
            playerIndex = playerIndex,
            commands = commands,
        )
        return commands
    }

    private fun evaluateOps(
        ops: List<LayoutOp>,
        cursor: Cursor,
        serverFrame: Int,
        stats: ShortArray,
        screenWidth: Int,
        screenHeight: Int,
        gameConfig: GameConfiguration,
        playerIndex: Int,
        commands: MutableList<LayoutExecutor.LayoutCommand>,
    ) {
        for (op in ops) {
            when (op) {
                is LayoutOp.SetX -> {
                    cursor.x = when (op.anchor) {
                        LayoutXAnchor.LEFT -> op.value
                        LayoutXAnchor.RIGHT -> screenWidth + op.value
                        LayoutXAnchor.VIEW -> screenWidth / 2 - 160 + op.value
                    }
                }

                is LayoutOp.SetY -> {
                    cursor.y = when (op.anchor) {
                        LayoutYAnchor.TOP -> op.value
                        LayoutYAnchor.BOTTOM -> screenHeight + op.value
                        LayoutYAnchor.VIEW -> screenHeight / 2 - 120 + op.value
                    }
                }

                is LayoutOp.Pic -> {
                    val imageIndex = stats[op.statIndex]
                    commands += LayoutExecutor.LayoutCommand.Image(cursor.x, cursor.y, gameConfig.getImage(imageIndex.toInt()))
                }

                is LayoutOp.Client -> {
                    cursor.x = screenWidth / 2 - 160 + op.xOffset
                    cursor.y = screenHeight / 2 - 120 + op.yOffset
                    val clientIndex = op.clientIndex
                    check(clientIndex in 0 until MAX_CLIENTS) { "client >= MAX_CLIENTS" }
                    val clientName = gameConfig.getClientName(clientIndex) ?: ""
                    val clientIcon = gameConfig.getClientIcon(clientIndex)
                    commands += LayoutExecutor.LayoutCommand.Text(cursor.x + 32, cursor.y, clientName, alt = true)
                    commands += LayoutExecutor.LayoutCommand.Text(cursor.x + 32, cursor.y + 8, "Score: ", alt = false)
                    commands += LayoutExecutor.LayoutCommand.Text(cursor.x + 32 + 7 * 8, cursor.y + 8, "${op.score}", alt = true)
                    commands += LayoutExecutor.LayoutCommand.Text(cursor.x + 32, cursor.y + 16, "Ping:  ${op.ping}", alt = false)
                    commands += LayoutExecutor.LayoutCommand.Text(cursor.x + 32, cursor.y + 24, "Time:  ${op.time}", alt = false)
                    commands += LayoutExecutor.LayoutCommand.Image(cursor.x, cursor.y, clientIcon)
                }

                is LayoutOp.Ctf -> {
                    cursor.x = screenWidth / 2 - 160 + op.xOffset
                    cursor.y = screenHeight / 2 - 120 + op.yOffset
                    val clientIndex = op.clientIndex
                    check(clientIndex in 0 until MAX_CLIENTS) { "client >= MAX_CLIENTS" }
                    val clientName = gameConfig.getClientName(clientIndex) ?: ""
                    val ping = op.ping.coerceAtMost(999)
                    val block = String.format("%3d %3d %-12.12s", op.score, ping, clientName)
                    val isCurrentPlayer = clientIndex == playerIndex
                    commands += LayoutExecutor.LayoutCommand.Text(cursor.x, cursor.y, block, alt = isCurrentPlayer)
                }

                is LayoutOp.Picn -> {
                    commands += LayoutExecutor.LayoutCommand.Image(cursor.x, cursor.y, gameConfig.getNamedPic(op.picName))
                }

                is LayoutOp.Num -> {
                    commands += LayoutExecutor.LayoutCommand.Number(cursor.x, cursor.y, stats[op.statIndex], op.width, 0)
                }

                LayoutOp.HNum -> {
                    val health = stats[Defines.STAT_HEALTH]
                    val color = when {
                        health > 25 -> 0
                        health > 0 -> (serverFrame shr 2) and 1
                        else -> 1
                    }
                    commands += LayoutExecutor.LayoutCommand.Number(cursor.x, cursor.y, health, 3, color)
                }

                LayoutOp.ANum -> {
                    val ammo = stats[Defines.STAT_AMMO]
                    if (ammo < 0) {
                        continue
                    }
                    val color = if (ammo > 5) 0 else ((serverFrame shr 2) and 1)
                    commands += LayoutExecutor.LayoutCommand.Number(cursor.x, cursor.y, ammo, 3, color)
                }

                LayoutOp.RNum -> {
                    val armor = stats[Defines.STAT_ARMOR]
                    if (armor < 1) {
                        continue
                    }
                    commands += LayoutExecutor.LayoutCommand.Number(cursor.x, cursor.y, armor, 3, 0)
                }

                is LayoutOp.StatString -> {
                    val statIndex = op.statIndex
                    if (statIndex !in 0 until MAX_CONFIGSTRINGS) {
                        throw IllegalStateException("stat_string: Invalid player stat index: $statIndex")
                    }
                    val configIndex = stats[statIndex]
                    if (configIndex !in 0 until MAX_CONFIGSTRINGS) {
                        throw IllegalStateException("stat_string: Invalid config string index: $configIndex")
                    }
                    val value = gameConfig.getConfigValue(configIndex.toInt()) ?: ""
                    commands += LayoutExecutor.LayoutCommand.Text(cursor.x, cursor.y, value, false)
                }

                is LayoutOp.Text -> {
                    commands += LayoutExecutor.LayoutCommand.Text(
                        x = cursor.x,
                        y = cursor.y,
                        text = op.text,
                        alt = op.alt,
                        centerWidth = if (op.centered) 320 else null,
                    )
                }

                is LayoutOp.IfStat -> {
                    val statIndex = op.statIndex
                    if (stats[statIndex].toInt() != 0) {
                        evaluateOps(
                            ops = op.body,
                            cursor = cursor,
                            serverFrame = serverFrame,
                            stats = stats,
                            screenWidth = screenWidth,
                            screenHeight = screenHeight,
                            gameConfig = gameConfig,
                            playerIndex = playerIndex,
                            commands = commands,
                        )
                    }
                }
            }
        }
    }
}
