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
 * Data source used by HUD layout parse/execute flow to resolve assets/config values.
 */
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
 * Shared IdTech2 HUD layout parser used by runtime rendering.
 *
 * Runtime path:
 * [Hud.executeLayout] wires parser callbacks directly to draw calls.
 */
internal fun executeLayoutScript(
    layout: String,
    serverFrame: Int,
    stats: ShortArray,
    screenWidth: Int,
    screenHeight: Int,
    dataProvider: LayoutDataProvider,
    onImage: (x: Int, y: Int, texture: Texture?) -> Unit,
    onText: (x: Int, y: Int, text: String, alt: Boolean, centerWidth: Int?) -> Unit,
    onNumber: (x: Int, y: Int, value: Short, width: Int, color: Int) -> Unit,
) {
    if (layout.isEmpty()) return

    fun readStatOrNull(statIndex: Int): Short? {
        if (statIndex in stats.indices) {
            return stats[statIndex]
        }
        return null
    }

    fun isValidClientIndex(clientIndex: Int): Boolean = clientIndex in 0 until MAX_CLIENTS

    var x = 0
    var y = 0
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
            val imageIndex = readStatOrNull(statIndex) ?: continue
            onImage(x, y, dataProvider.getImage(imageIndex.toInt()))
            continue
        }

        if (parser.tokenEquals("client")) {
            parser.next()
            x = screenWidth / 2 - 160 + parser.tokenAsInt()

            parser.next()
            y = screenHeight / 2 - 120 + parser.tokenAsInt()

            parser.next()
            val clientIndex = parser.tokenAsInt()
            if (!isValidClientIndex(clientIndex)) {
                parser.next()
                parser.next()
                parser.next()
                continue
            }
            val clientInfo = dataProvider.getClientInfo(clientIndex)

            parser.next()
            val score = parser.tokenAsInt()

            parser.next()
            val ping = parser.tokenAsInt()

            parser.next()
            val time = parser.tokenAsInt()

            onText(x + 32, y, clientInfo?.name ?: "", true, null)
            onText(x + 32, y + 8, "Score: ", false, null)
            onText(x + 32 + 7 * 8, y + 8, "$score", true, null)
            onText(x + 32, y + 16, "Ping:  $ping", false, null)
            onText(x + 32, y + 24, "Time:  $time", false, null)
            onImage(x, y, clientInfo?.icon)
            continue
        }

        if (parser.tokenEquals("ctf")) {
            parser.next()
            x = screenWidth / 2 - 160 + parser.tokenAsInt()

            parser.next()
            y = screenHeight / 2 - 120 + parser.tokenAsInt()

            parser.next()
            val clientIndex = parser.tokenAsInt()
            if (!isValidClientIndex(clientIndex)) {
                parser.next()
                parser.next()
                continue
            }
            val clientInfo = dataProvider.getClientInfo(clientIndex)

            parser.next()
            val score = parser.tokenAsInt()

            parser.next()
            val ping = parser.tokenAsInt().coerceAtMost(999)

            val block = String.format("%3d %3d %-12.12s", score, ping, clientInfo?.name ?: "")
            val isCurrentPlayer = clientIndex == dataProvider.getCurrentPlayerIndex()
            onText(x, y, block, isCurrentPlayer, null)
            continue
        }

        if (parser.tokenEquals("picn")) {
            parser.next()
            onImage(x, y, dataProvider.getNamedPic(parser.token()))
            continue
        }

        if (parser.tokenEquals("num")) {
            parser.next()
            val width = parser.tokenAsInt()
            parser.next()
            val statIndex = parser.tokenAsInt()
            val value = readStatOrNull(statIndex) ?: continue
            onNumber(x, y, value, width, 0)
            continue
        }

        if (parser.tokenEquals("hnum")) {
            val health = readStatOrNull(Defines.STAT_HEALTH) ?: continue
            val color = when {
                health > 25 -> 0
                health > 0 -> (serverFrame shr 2) and 1
                else -> 1
            }
            onNumber(x, y, health, 3, color)
            continue
        }

        if (parser.tokenEquals("anum")) {
            val ammo = readStatOrNull(Defines.STAT_AMMO) ?: continue
            if (ammo < 0) {
                continue
            }
            val color = if (ammo > 5) 0 else ((serverFrame shr 2) and 1)
            onNumber(x, y, ammo, 3, color)
            continue
        }

        if (parser.tokenEquals("rnum")) {
            val armor = readStatOrNull(Defines.STAT_ARMOR) ?: continue
            if (armor < 1) {
                continue
            }
            onNumber(x, y, armor, 3, 0)
            continue
        }

        if (parser.tokenEquals("stat_string")) {
            parser.next()
            val statIndex = parser.tokenAsInt()
            if (statIndex !in stats.indices) {
                continue
            }
            val configIndex = stats[statIndex]
            if (configIndex !in 0 until MAX_CONFIGSTRINGS) {
                continue
            }
            val value = dataProvider.getConfigString(configIndex.toInt()) ?: ""
            onText(x, y, value, false, null)
            continue
        }

        if (parser.tokenEquals("cstring")) {
            parser.next()
            onText(x, y, parser.token(), false, 320)
            continue
        }

        if (parser.tokenEquals("string")) {
            parser.next()
            onText(x, y, parser.token(), false, null)
            continue
        }

        if (parser.tokenEquals("cstring2")) {
            parser.next()
            onText(x, y, parser.token(), true, 320)
            continue
        }

        if (parser.tokenEquals("string2")) {
            parser.next()
            onText(x, y, parser.token(), true, null)
            continue
        }

        if (parser.tokenEquals("if")) {
            parser.next()
            val statIndex = parser.tokenAsInt()
            val value = readStatOrNull(statIndex) ?: 0
            if (value.toInt() == 0) {
                parser.next()
                while (parser.hasNext() && !parser.tokenEquals("endif")) {
                    parser.next()
                }
            }
            continue
        }
    }
}

/**
 * Default data provider backed by the active [GameConfiguration].
 *
 * Ownership:
 * created and owned by `Game3dScreen` for the lifetime of a running game screen.
 */
internal class GameConfigLayoutDataProvider(
    private val gameConfig: GameConfiguration,
) : LayoutDataProvider {
    override fun getImage(imageIndex: Int): Texture? = gameConfig.getImage(imageIndex)
    override fun getConfigString(configIndex: Int): String? = gameConfig.getConfigValue(configIndex)
    override fun getNamedPic(picName: String): Texture? = gameConfig.getNamedPic(picName)
    override fun getClientInfo(clientIndex: Int): LayoutClientInfo? {
        val name = gameConfig.getClientName(clientIndex) ?: return null
        return LayoutClientInfo(name = name, icon = gameConfig.getClientIcon(clientIndex))
    }

    override fun getCurrentPlayerIndex(): Int = gameConfig.playerIndex
}

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
internal class Hud(
    private val spriteBatch: SpriteBatch,
    private val style: GameUiStyle,
    private val dataProvider: LayoutDataProvider,
) {
    private companion object {
        // Legacy `scr_centertime` default value from `client/SCR.Init`.
        const val CENTER_PRINT_TIMEOUT_SECONDS = 2.5f
        const val CENTER_PRINT_SHORT_MAX_LINES = 4
        // Legacy center-print vertical anchors from `client/SCR.DrawCenterString`.
        const val CENTER_PRINT_SHORT_Y_RATIO = 0.35f
        const val CENTER_PRINT_LONG_TOP_Y = 48

        const val INVENTORY_DISPLAY_ITEMS = 17
        const val INVENTORY_WIDTH = 256
        const val INVENTORY_HEIGHT = 240
    }

    private var centerPrintText: String = ""
    private var centerPrintLineCount: Int = 0
    private var centerPrintTimeLeftSeconds: Float = 0f

    /**
     * Parse and execute one server-provided layout string for the current frame.
     *
     * Invariant:
     * all command coordinates are interpreted as IdTech2 top-left pixels before transform.
     */
    internal fun executeLayout(
        layout: String?,
        serverFrame: Int,
        stats: ShortArray,
        screenWidth: Int,
        screenHeight: Int,
    ) {
        executeLayoutScript(
            layout = layout ?: "",
            serverFrame = serverFrame,
            stats = stats,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            dataProvider = dataProvider,
            onImage = { x, y, texture -> drawImageIdTech2(x, y, texture, screenHeight) },
            onText = { x, y, text, alt, centerWidth -> drawTextIdTech2(x, y, text, alt, centerWidth, screenHeight) },
            onNumber = { x, y, value, width, color -> drawNumberIdTech2(x, y, value, width, color, screenHeight) },
        )
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
     * Register a server center-print message for timed rendering.
     */
    fun showCenterPrint(text: String) {
        centerPrintText = text
        centerPrintLineCount = if (text.isEmpty()) 0 else text.count { it == '\n' } + 1
        centerPrintTimeLeftSeconds = if (centerPrintLineCount == 0) 0f else CENTER_PRINT_TIMEOUT_SECONDS
    }

    /**
     * Update and render timed HUD elements (for now: center-print).
     *
     * Call this once per rendered frame while `spriteBatch` is active.
     */
    fun update(delta: Float, screenWidth: Int, screenHeight: Int) {
        if (centerPrintTimeLeftSeconds <= 0f || centerPrintText.isEmpty()) {
            return
        }

        centerPrintTimeLeftSeconds -= delta
        if (centerPrintTimeLeftSeconds <= 0f) {
            centerPrintText = ""
            centerPrintLineCount = 0
            return
        }

        val idTech2TopY = if (centerPrintLineCount <= CENTER_PRINT_SHORT_MAX_LINES) {
            (screenHeight * CENTER_PRINT_SHORT_Y_RATIO).toInt()
        } else {
            CENTER_PRINT_LONG_TOP_Y
        }
        drawTextIdTech2(
            x = 0,
            y = idTech2TopY,
            text = centerPrintText,
            alt = false,
            centerWidth = screenWidth,
            screenHeight = screenHeight,
        )
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
