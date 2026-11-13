package org.demoth.cake.lwjgl3

import java.nio.file.Path

internal data class ViewerOptions(
    val input: String? = null,
    val capture: Path? = null,
    val width: Int = 1024,
    val height: Int = 768,
    val help: Boolean = false,
) {
    companion object {
        const val USAGE = "Usage: cake-viewer [file-or-folder] [--capture output.png] [--size WIDTHxHEIGHT]\n" +
            "Capture writes the complete Viewer UI after two rendered frames, then exits.\n" +
            "Capture uses a hidden graphics window and requires a graphics context.\n" +
            "Use -- before a path beginning with a dash."

        fun parse(args: Array<String>): ViewerOptions {
            var input: String? = null
            var capture: Path? = null
            var width = 1024
            var height = 768
            var positional = false
            var i = 0
            fun value(option: String): String {
                require(i < args.size && !args[i].startsWith("--")) { "$option requires a value" }
                return args[i++]
            }
            while (i < args.size) {
                val arg = args[i++]
                when {
                    !positional && arg == "--help" -> return ViewerOptions(help = true)
                    !positional && arg == "--" -> positional = true
                    !positional && arg == "--capture" -> {
                        require(capture == null) { "--capture may only be specified once" }
                        val path = value(arg)
                        require(path.endsWith(".png", ignoreCase = true)) { "Capture output must be a .png file" }
                        capture = Path.of(path).toAbsolutePath().normalize()
                    }
                    !positional && arg == "--size" -> {
                        val dimensions = value(arg).split('x')
                        width = dimensions.getOrNull(0)?.toIntOrNull() ?: 0
                        height = dimensions.getOrNull(1)?.toIntOrNull() ?: 0
                        require(dimensions.size == 2 && width > 0 && height > 0) {
                            "--size requires positive dimensions, for example 1024x768"
                        }
                    }
                    !positional && arg.startsWith("-") -> error("Unknown option: $arg")
                    else -> {
                        require(input == null) { "Only one input file or folder can be opened" }
                        input = arg
                    }
                }
            }
            return ViewerOptions(input, capture, width, height)
        }
    }
}
