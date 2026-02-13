package org.demoth.cake.stages

import jake2.qcommon.Defines
import jake2.qcommon.Defines.MAX_CONFIGSTRINGS
import jake2.qcommon.Defines.MAX_CLIENTS
import org.demoth.cake.stages.ingame.hud.LayoutDataProvider
import org.demoth.cake.stages.ingame.hud.LayoutExecutor
import org.demoth.cake.stages.ingame.hud.LayoutParserCompat

/**
 * Legacy-like command compiler used for parity tests against the old client behavior.
 *
 * Scope intentionally matches branches currently implemented in Cake LayoutExecutor.
 */
internal object LegacyLayoutCommandHarness {
    fun compileCommands(
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
                val alt = clientIndex == dataProvider.getCurrentPlayerIndex()
                commands += LayoutExecutor.LayoutCommand.Text(x, y, block, alt = alt)
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
                val value = stats[statIndex]
                commands += LayoutExecutor.LayoutCommand.Number(x, y, value, width, 0)
                continue
            }

            if (parser.tokenEquals("hnum")) {
                val value = stats[Defines.STAT_HEALTH]
                val color = when {
                    value > 25 -> 0
                    value > 0 -> (serverFrame shr 2) and 1
                    else -> 1
                }
                commands += LayoutExecutor.LayoutCommand.Number(x, y, value, 3, color)
                continue
            }

            if (parser.tokenEquals("anum")) {
                val value = stats[Defines.STAT_AMMO]
                if (value < 0) {
                    continue
                }
                val color = if (value > 5) 0 else ((serverFrame shr 2) and 1)
                commands += LayoutExecutor.LayoutCommand.Number(x, y, value, 3, color)
                continue
            }

            if (parser.tokenEquals("rnum")) {
                val value = stats[Defines.STAT_ARMOR]
                if (value < 1) {
                    continue
                }
                commands += LayoutExecutor.LayoutCommand.Number(x, y, value, 3, 0)
                continue
            }

            if (parser.tokenEquals("stat_string")) {
                parser.next()
                var index = parser.tokenAsInt()
                check(index in 0 until MAX_CONFIGSTRINGS) { "Bad stat_string index" }
                index = stats[index].toInt()
                check(index in 0 until MAX_CONFIGSTRINGS) { "Bad stat_string index" }
                commands += LayoutExecutor.LayoutCommand.Text(
                    x = x,
                    y = y,
                    text = dataProvider.getConfigString(index) ?: "",
                    alt = false,
                )
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
                val value = stats[parser.tokenAsInt()]
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
