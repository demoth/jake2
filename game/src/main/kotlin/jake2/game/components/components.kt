package jake2.game.components

import jake2.game.GameExportsImpl
import jake2.game.SubgameEntity
import jake2.qcommon.Defines
import jake2.qcommon.lerpI

enum class ComponentType {
    Light,
    LightRamp,
    ItemHealth
}

fun <T> SubgameEntity.getComponent(type: ComponentType): T? {
    return this.components[type] as T?
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
    var fraction: Float = 0f,
    var targetLightStyle: Int = -1
) {
    fun update(delta: Float): Int {
        if (fraction < 1) {
            fraction += delta / duration
        }
        if (fraction > 1) {
            fraction = 1f
        }
        return lerpI(start, end, fraction)
    }

    fun toggle(currentTime: Float) {
        val tmp = start
        start = end
        end = tmp

        targetTime = currentTime + duration
        fraction = 0f
    }
}
