package jake2.game

import jake2.game.components.*

/** Explicit saved component set. Value-only components are copied; mover callbacks use IDs. */
data class EntityComponentsSnapshot(
    val movement: MoveInfoSnapshot?,
    val medkit: Medkit?,
    val light: Light?,
    val lightRamp: LightRamp?,
    val areaPortal: AreaPortal?,
    val earthquake: Earthquake?
) {
    fun restore(entity: GameEntity) {
        entity.components.clear()
        movement?.let { entity.addComponent(it.restore()) }
        medkit?.let { entity.addComponent(it.copy()) }
        light?.let { entity.addComponent(it.copy()) }
        lightRamp?.let { entity.addComponent(it.copy()) }
        areaPortal?.let { entity.addComponent(it.copy()) }
        earthquake?.let { entity.addComponent(it.copy()) }
    }

    companion object {
        fun capture(entity: GameEntity) = EntityComponentsSnapshot(
            entity.getComponent<MoveInfo>()?.snapshot(),
            entity.getComponent<Medkit>()?.copy(),
            entity.getComponent<Light>()?.copy(),
            entity.getComponent<LightRamp>()?.copy(),
            entity.getComponent<AreaPortal>()?.copy(),
            entity.getComponent<Earthquake>()?.copy()
        )
    }
}
