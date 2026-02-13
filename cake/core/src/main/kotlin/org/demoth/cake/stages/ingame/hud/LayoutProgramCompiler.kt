package org.demoth.cake.stages.ingame.hud

/**
 * Parses textual IdTech2 layout script into a reusable [LayoutProgram].
 */
object LayoutProgramCompiler {
    fun compile(layout: String): LayoutProgram {
        val parser = LayoutParserCompat(layout)
        return LayoutProgram(parseOps(parser))
    }

    private fun parseOps(parser: LayoutParserCompat): List<LayoutOp> {
        val ops = mutableListOf<LayoutOp>()

        while (parser.hasNext()) {
            parser.next()
            if (parser.tokenEquals("endif")) {
                break
            }

            if (parser.tokenEquals("xl")) {
                parser.next()
                ops += LayoutOp.SetX(LayoutXAnchor.LEFT, parser.tokenAsInt())
                continue
            }
            if (parser.tokenEquals("xr")) {
                parser.next()
                ops += LayoutOp.SetX(LayoutXAnchor.RIGHT, parser.tokenAsInt())
                continue
            }
            if (parser.tokenEquals("xv")) {
                parser.next()
                ops += LayoutOp.SetX(LayoutXAnchor.VIEW, parser.tokenAsInt())
                continue
            }

            if (parser.tokenEquals("yt")) {
                parser.next()
                ops += LayoutOp.SetY(LayoutYAnchor.TOP, parser.tokenAsInt())
                continue
            }
            if (parser.tokenEquals("yb")) {
                parser.next()
                ops += LayoutOp.SetY(LayoutYAnchor.BOTTOM, parser.tokenAsInt())
                continue
            }
            if (parser.tokenEquals("yv")) {
                parser.next()
                ops += LayoutOp.SetY(LayoutYAnchor.VIEW, parser.tokenAsInt())
                continue
            }

            if (parser.tokenEquals("pic")) {
                parser.next()
                ops += LayoutOp.Pic(parser.tokenAsInt())
                continue
            }

            if (parser.tokenEquals("client")) {
                parser.next()
                val xOffset = parser.tokenAsInt()
                parser.next()
                val yOffset = parser.tokenAsInt()
                parser.next()
                val clientIndex = parser.tokenAsInt()
                parser.next()
                val score = parser.tokenAsInt()
                parser.next()
                val ping = parser.tokenAsInt()
                parser.next()
                val time = parser.tokenAsInt()
                ops += LayoutOp.Client(xOffset, yOffset, clientIndex, score, ping, time)
                continue
            }

            if (parser.tokenEquals("ctf")) {
                parser.next()
                val xOffset = parser.tokenAsInt()
                parser.next()
                val yOffset = parser.tokenAsInt()
                parser.next()
                val clientIndex = parser.tokenAsInt()
                parser.next()
                val score = parser.tokenAsInt()
                parser.next()
                val ping = parser.tokenAsInt()
                ops += LayoutOp.Ctf(xOffset, yOffset, clientIndex, score, ping)
                continue
            }

            if (parser.tokenEquals("picn")) {
                parser.next()
                ops += LayoutOp.Picn(parser.token())
                continue
            }

            if (parser.tokenEquals("num")) {
                parser.next()
                val width = parser.tokenAsInt()
                parser.next()
                val statIndex = parser.tokenAsInt()
                ops += LayoutOp.Num(width, statIndex)
                continue
            }

            if (parser.tokenEquals("hnum")) {
                ops += LayoutOp.HNum
                continue
            }

            if (parser.tokenEquals("anum")) {
                ops += LayoutOp.ANum
                continue
            }

            if (parser.tokenEquals("rnum")) {
                ops += LayoutOp.RNum
                continue
            }

            if (parser.tokenEquals("stat_string")) {
                parser.next()
                ops += LayoutOp.StatString(parser.tokenAsInt())
                continue
            }

            if (parser.tokenEquals("cstring")) {
                parser.next()
                ops += LayoutOp.Text(text = parser.token(), alt = false, centered = true)
                continue
            }

            if (parser.tokenEquals("string")) {
                parser.next()
                ops += LayoutOp.Text(text = parser.token(), alt = false, centered = false)
                continue
            }

            if (parser.tokenEquals("cstring2")) {
                parser.next()
                ops += LayoutOp.Text(text = parser.token(), alt = true, centered = true)
                continue
            }

            if (parser.tokenEquals("string2")) {
                parser.next()
                ops += LayoutOp.Text(text = parser.token(), alt = true, centered = false)
                continue
            }

            if (parser.tokenEquals("if")) {
                parser.next()
                val statIndex = parser.tokenAsInt()
                ops += LayoutOp.IfStat(statIndex = statIndex, body = parseOps(parser))
                continue
            }
        }

        return ops
    }
}
