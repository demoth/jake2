package jake2.game.components

import jake2.game.SubgameEntity

enum class ComponentType {
    Turret,
    ItemHealth
}

fun <T> SubgameEntity.getComponent(type: ComponentType): T? {
    return this.components[type] as T?
}

data class ItemHealth(
    val amount: Int,
    val model: String
)
