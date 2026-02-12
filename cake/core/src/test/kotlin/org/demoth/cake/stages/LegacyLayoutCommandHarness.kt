package org.demoth.cake.stages

import jake2.qcommon.Defines
import jake2.qcommon.Defines.MAX_CONFIGSTRINGS

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

            if (parser.tokenEquals("cstring") || parser.tokenEquals("string")) {
                parser.next()
                commands += LayoutExecutor.LayoutCommand.Text(x, y, parser.token(), false)
                continue
            }

            if (parser.tokenEquals("cstring2") || parser.tokenEquals("string2")) {
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
