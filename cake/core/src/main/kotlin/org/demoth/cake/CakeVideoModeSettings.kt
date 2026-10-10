package org.demoth.cake

import com.badlogic.gdx.graphics.glutils.HdpiMode
import jake2.qcommon.Com
import jake2.qcommon.exec.Cvar

data class CakeVideoModeSettings(
    val fullscreen: Boolean,
    val hdpiMode: HdpiMode,
    val width: Int,
    val height: Int,
    val vsync: Boolean,
    val fullscreenMode: String = "desktop",
    val depthBits: Int = 24,
    val samples: Int = 4,
) {
    companion object {
        private const val DEFAULT_WIDTH = 1024
        private const val DEFAULT_HEIGHT = 768
        val DEPTH_BITS_OPTIONS = listOf(16, 24, 32)
        val SAMPLES_OPTIONS = listOf(0, 2, 4, 8)

        fun fromCvars(cvars: Cvar = Cvar.getInstance()): CakeVideoModeSettings {
            return CakeVideoModeSettings(
                fullscreen = parseBoolean(cvars.VariableString("vid_fullscreen")),
                hdpiMode = parseHdpiMode(cvars.VariableString("vid_hidpi")),
                width = parseDimension(cvars.VariableString("vid_windowed_width"), DEFAULT_WIDTH),
                height = parseDimension(cvars.VariableString("vid_windowed_height"), DEFAULT_HEIGHT),
                vsync = parseBoolean(cvars.VariableString("vid_vsync"), defaultValue = true),
                fullscreenMode = cvars.VariableString("vid_fullscreen_mode").trim().ifEmpty { "desktop" },
                depthBits = parseChoice(cvars, "vid_depthbits", DEPTH_BITS_OPTIONS, 24),
                samples = parseChoice(cvars, "vid_samples", SAMPLES_OPTIONS, 4),
            )
        }

        private fun parseHdpiMode(value: String): HdpiMode {
            return HdpiMode.entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) } ?: HdpiMode.Pixels
        }

        private fun parseChoice(cvars: Cvar, name: String, choices: List<Int>, default: Int): Int {
            val raw = cvars.VariableString(name)
            val value = raw.trim().toIntOrNull()
            if (value in choices) return value!!
            if (raw.isNotEmpty()) Com.Warn("Invalid $name '$raw'; using $default\n")
            return default
        }

        private fun parseBoolean(value: String, defaultValue: Boolean = false): Boolean {
            val normalized = value.trim()
            return when {
                normalized.equals("1") || normalized.equals("true", ignoreCase = true) || normalized.equals("yes", ignoreCase = true) -> true
                normalized.equals("0") || normalized.equals("false", ignoreCase = true) || normalized.equals("no", ignoreCase = true) -> false
                else -> defaultValue
            }
        }

        private fun parseDimension(value: String, defaultValue: Int): Int {
            val parsed = value.trim().toIntOrNull()?.takeIf { it > 0 }
            if (parsed == null && value.isNotBlank()) Com.Warn("Invalid window dimension '$value'; using $defaultValue\n")
            return parsed ?: defaultValue
        }
    }
}

/** Choices for the existing cvar-driven options screen; empty means a free-form value. */
fun videoOptionChoices(name: String, cvars: Cvar = Cvar.getInstance()): List<String> = when (name) {
    "vid_fullscreen", "vid_vsync" -> listOf("0", "1")
    "vid_hidpi" -> HdpiMode.entries.map { it.name }
    "vid_depthbits" -> CakeVideoModeSettings.DEPTH_BITS_OPTIONS.map { it.toString() }
    "vid_samples" -> CakeVideoModeSettings.SAMPLES_OPTIONS.map { it.toString() }
    "vid_fullscreen_mode" -> (listOf("desktop") + cvars.VariableString("vid_fullscreen_modes")
        .split(Regex("\\s+")).filter { it.isNotEmpty() }).distinct()
    else -> emptyList()
}

/** Stable persisted identifier, independent of display-mode enumeration order. */
data class CakeDisplayMode(val width: Int, val height: Int, val refreshRate: Int) {
    val id: String get() = "${width}x${height}@${refreshRate}"
}

fun resolveFullscreenMode(requested: String, desktop: CakeDisplayMode, available: List<CakeDisplayMode>): CakeDisplayMode {
    if (requested.equals("desktop", ignoreCase = true)) return desktop
    return available.firstOrNull { it.id.equals(requested, ignoreCase = true) } ?: desktop.also {
        Com.Warn("Fullscreen mode '$requested' is unavailable; using desktop ${desktop.id}\n")
    }
}

/** Desktop-specific window coordinates and display diagnostics, independent of HiDPI mode. */
interface CakeVideoPlatform {
    fun windowSize(): Pair<Int, Int>
    fun describe(): String
}

/** Remember interactive resizes without discarding dimensions explicitly queued for restart. */
fun rememberWindowedSize(width: Int, height: Int, fullscreen: Boolean, cvars: Cvar = Cvar.getInstance()) {
    if (fullscreen || width <= 0 || height <= 0) return
    for ((name, value) in listOf("vid_windowed_width" to width, "vid_windowed_height" to height)) {
        val variable = cvars.FindVar(name) ?: continue
        val pending = variable.latched_string
        cvars.ForceSet(name, value.toString())
        variable.latched_string = pending
    }
}

fun publishFullscreenModes(modes: List<CakeDisplayMode>) {
    Cvar.getInstance().ForceSet("vid_fullscreen_modes", modes.distinct().sortedWith(
        compareBy<CakeDisplayMode> { it.width }.thenBy { it.height }.thenBy { it.refreshRate }
    ).joinToString(" ") { it.id })
}
