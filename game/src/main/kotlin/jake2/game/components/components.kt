package jake2.game.components

import jake2.game.GameEntity
import jake2.game.GameExportsImpl
import jake2.qcommon.Defines

inline fun <reified T> GameEntity.getComponent(): T? {
    return this.components[T::class.java.name] as? T
}

inline fun <reified T> GameEntity.addComponent(component: T): T{
    this.components[T::class.java.name] = component
    return component
}

data class Medkit(
    val amount: Int,
    val soundIndex: Int,
    val ignoreMax: Boolean = false,
    val timed: Boolean = false // for mega-health
)

data class Light(
    val lightStyle: Int,
    var switchedOn: Boolean = true
) {

    // transmit light state to the client
    fun sendLightValue(game: GameExportsImpl) {
        game.gameImports.configstring(Defines.CS_LIGHTS + lightStyle, if (switchedOn) "m" else "a")
    }
}

data class LightRamp(
    var start: Int, // codepoint of char a-z
    var end: Int, // codepoint of char a-z
    val duration: Float, // seconds
    var targetTime: Float = 0f,
    val toggleable: Boolean = false,
    var targetLightStyle: Int = -1
) {
    fun styleAt(time: Float): Char {
        val remaining = targetTime - time
        val fraction = if (remaining <= 0f) 1f else (1f - remaining / duration).coerceIn(0f, 1f)
        return ('a'.code + start + (end - start) * fraction).toInt().toChar()
    }

    fun toggle() {
        val previousStart = start
        start = end
        end = previousStart
    }
}

data class AreaPortal(val portalNumber: Int, var open: Boolean)

data class Earthquake(
    val soundIndex: Int,
    val duration: Float = 5f,
    val magnitude: Float = 200f,
    var soundTime: Float = 0f,
    var stopTime: Float = 0f
)
