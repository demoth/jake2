package org.demoth.cake

import com.badlogic.gdx.graphics.glutils.HdpiMode
import jake2.qcommon.Com
import jake2.qcommon.exec.Cvar
import jake2.qcommon.exec.find
import jake2.qcommon.exec.CvarType

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
        /** Snapshot active settings after video cvars have been registered at startup. */
        fun fromCvars(cvars: Cvar = Cvar.getInstance()): CakeVideoModeSettings {
            return CakeVideoModeSettings(
                fullscreen = cvars.find<Boolean>("vid_fullscreen").typedValue,
                hdpiMode = cvars.find<HdpiMode>("vid_hidpi").typedValue,
                width = cvars.find<Int>("vid_windowed_width").typedValue,
                height = cvars.find<Int>("vid_windowed_height").typedValue,
                vsync = cvars.find<Boolean>("vid_vsync").typedValue,
                fullscreenMode = cvars.find<String>("vid_fullscreen_mode").typedValue,
                depthBits = cvars.find<Int>("vid_depthbits").typedValue,
                samples = cvars.find<Int>("vid_samples").typedValue,
            )
        }
    }
}

/** Choices for the existing cvar-driven options screen; empty means a free-form value. */
fun videoOptionChoices(name: String, cvars: Cvar = Cvar.getInstance()): List<String> {
    // These are hardware suggestions; saved mode IDs remain valid when absent from this monitor.
    if (name == "vid_fullscreen_mode") {
        return (listOf("desktop") + cvars.find<String>("vid_fullscreen_modes").typedValue
            .split(Regex("\\s+")).filter { it.isNotEmpty() }).distinct()
    }
    val type = cvars.FindVar(name)?.type
    if (type != null) {
        return if (type.kind == CvarType.Kind.BOOLEAN) listOf("0", "1") else type.choices.map { if (it is Enum<*>) it.name else it.toString() }
    }
    return emptyList()
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
        val pending = variable.rawPendingString
        cvars.ForceSet(name, value.toString())
        if (pending != null) cvars.Set(name, pending)
    }
}

fun publishFullscreenModes(modes: List<CakeDisplayMode>, cvars: Cvar = Cvar.getInstance()) {
    cvars.forceSetResult("vid_fullscreen_modes", modes.distinct().sortedWith(
        compareBy<CakeDisplayMode> { it.width }.thenBy { it.height }.thenBy { it.refreshRate }
    ).joinToString(" ") { it.id })
}
