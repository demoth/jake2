package org.demoth.cake.stages

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import jake2.qcommon.Defines
import jake2.qcommon.Defines.MAX_ITEMS
import jake2.qcommon.Defines.MAX_STATS
import jake2.qcommon.player_state_t
import org.demoth.cake.GameConfiguration

/**
 * A simplified and more readable function that consumes a layout string and a stats array.
 * It outputs draw calls (here represented as `drawImage(...)` and `drawText(...)`)
 * while ignoring "dirty" calls or error handling.
 *
 *
 * The idea is to parse layout instructions to place images or text on the screen.
 * The function no longer depends on external global structures like `ClientGlobals`.
 */
class LayoutExecutor(
    private val spriteBatch: SpriteBatch,
    private val skin: Skin
) {
    private val skinFont = skin.getFont("default") as BitmapFont

    // Example stub for an image-drawing operation.
    // In real code, you might pass in your own rendering or context.
    private fun drawImage(x: Int, y: Int, texture: Texture?) {
        if (texture == null) {
            return
        }
        // fixme: quake hud coordinates have different texture origin
        spriteBatch.draw(texture, x.toFloat(), y.toFloat() - texture.height)
    }

    // Example stub for a text-drawing operation.
    private fun drawText(x: Int, y: Int, text: String, alt: Boolean) {
        skinFont.draw(spriteBatch, text, x.toFloat(), y.toFloat())
    }

    // Example stub for drawing a numeric field.
    private fun drawNumber(x: Int, y: Int, value: Short, width: Int, color: Int) {
        skinFont.draw(spriteBatch, "$value", x.toFloat(), y.toFloat())
    }

    /**
     * Simplified layout execution that:
     * 1) Reads positional tokens (xl, xr, xv, yt, yb, yv)
     * 2) Handles draw commands (like pic, picn, num, etc.)
     * 3) Relies on the provided `stats` array for values.
     * 4) Ignores any global or dirty calls.
     *
     * Should be run inside a spriteBatch begin/end
     *
     * @param layout       The layout string containing drawing instructions
     * @param serverFrame  The current server frame for blinking logic
     * @param stats        Array of player stats
     * @param screenWidth  Width of the screen
     * @param screenHeight Height of the screen
     */
    fun executeLayoutString(
        layout: String?,
        serverFrame: Int, // used for blinking
        stats: ShortArray,
        screenWidth: Int,
        screenHeight: Int,
        gameConfig: GameConfiguration
    ) {
        // If layout is invalid, do nothing.
        if (layout == null || layout.isEmpty()) {
            return
        }

        // Variables to track current position and field width.
        var x = 0
        var y = 0
        var width = 3

        val tokens = ArrayDeque(layout.split("\\s+".toRegex()))

        while (tokens.isNotEmpty()) {
            when (tokens.removeFirst()) {
                // left
                "xl" -> x = tokens.removeFirst().toInt()
                // right
                "xr" -> x = screenWidth + tokens.removeFirst().toInt()
                // center
                "xv" -> x = (screenWidth / 2) - 160 + tokens.removeFirst().toInt()

                // top
                "yt" -> y = screenHeight - tokens.removeFirst().toInt()
                // bottom - had to mirror the vertical coordinate because of the difference in quake / libgdx
                "yb" -> y = -tokens.removeFirst().toInt()
                // center
                "yv" -> y = (screenHeight / 2) - 120 + tokens.removeFirst().toInt()

                // draw a pic from a stat number
                "pic" -> {
                    // Next token is a stat index used as an image reference.
                    val statIndex = tokens.removeFirst().toInt()
                    val imageIndex = stats[statIndex]
                    gameConfig[Defines.CS_IMAGES + imageIndex.toInt()]?.let {
                        drawImage(x, y, it.resource as? Texture)
                    }
                }

                "client" -> { // draw a deathmatch client block
                    // todo
                }

                "ctf" -> { // draw a ctf client block
                    // todo
                }

                "picn" -> {
                    // Next token is a string name for an image.
                    // drawImage(x, y, tokens.removeFirst())
                    // todo
                }

                "num" -> {
                    // Expect 2 subsequent tokens: width, statIndex.
                    // Then draw that number.
                    width = tokens.removeFirst().toInt()
                    val statIndex = tokens.removeFirst().toInt()
                    // color 0 for now.
                    drawNumber(x, y, stats[statIndex], width, 0)
                }

                "hnum" -> {
                    // Health number.
                    val health = stats[Defines.STAT_HEALTH]
                    val color: Int = if (health > 25) {
                        0 // green.
                    } else if (health > 0) {
                        // flash.
                        (serverFrame shr 2) and 1
                    } else {
                        1 // e.g., red.
                    }
                    drawNumber(x, y, health, 3, color)
                }

                "anum" -> {
                    // Ammo.
                    val ammo = stats[Defines.STAT_AMMO]
                    if (ammo < 0) {
                        // do not draw.
                        break
                    }
                    val color = if (ammo > 5) 0 else ((serverFrame shr 2) and 1)
                    drawNumber(x, y, ammo, 3, color)
                }

                "rnum" -> {
                    // Armor.
                    val armor = stats[Defines.STAT_ARMOR]
                    if (armor < 1) {
                        break
                    }
                    drawNumber(x, y, armor, 3, 0)
                }

                "stat_string" -> {
                    val statIndex = tokens.removeFirst().toInt()
                    if (statIndex !in (0..MAX_STATS)) {
                        throw IllegalStateException("stat_string: Invalid player stat index: $statIndex")
                    }
                    val configIndex = stats[statIndex]
                    val value = gameConfig.get(configIndex.toInt())?.value ?: ""
                    drawText(x, y, value, false)
                }

                // what is a 'cstring' here?
                "cstring", "string" -> {
                    drawText(x, y, tokens.removeFirst(), false)
                }

                // same but with alternate font
                "cstring2", "string2" -> {
                    drawText(x, y, tokens.removeFirst(), true)
                }

                // conditional statement based on a player stat
                "if" -> {
                    val statIndex = tokens.removeFirst().toInt()
                    val value = stats[statIndex]
                    if (value == 0.toShort()) {
                        // skip to endif
                        do {
                            val token = tokens.removeFirst()
                        } while (token != "endif")
                    }
                }

                else -> {
                    // todo: warning
                }
            }
        }
    }

    /**
     * Stub inventory implementation
     */
    fun drawInventory(gameConfig: GameConfiguration, screenWidth: Int, screenHeight: Int, playerstate: player_state_t) {
        val x = screenWidth / 2
        var y = screenHeight / 2
        val selectedIndex = playerstate.stats[Defines.STAT_SELECTED_ITEM].toInt()
        drawText(x, y, "Inventory:", false)
        for (i in 0..<MAX_ITEMS) {
            val amount = gameConfig.inventory[i]
            if (amount > 0) {
                y -= 16;
                val selectedPrefix = if (i == selectedIndex) "->" else "  "
                val amountText = amount.toString().padStart(3)
                val text = "$selectedPrefix $amountText ${gameConfig[Defines.CS_ITEMS + i]?.value ?: ""}"
                drawText(x, y, text, false)
            }
        }
    }
}
