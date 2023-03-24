package jake2.game.components

import jake2.game.GameExportsImpl
import jake2.game.SubgameEntity
import jake2.qcommon.Defines

enum class ComponentType {
    Light,
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
