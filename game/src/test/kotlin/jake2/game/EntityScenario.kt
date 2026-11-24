package jake2.game

import jake2.qcommon.GameImports

/** A headless map with entities spawned and activated by classname and targetname. */
internal class EntityScenario(imports: GameImports) {
    val game = GameExportsImpl(imports)
    private val source: GameEntity

    init {
        repeat(GameDefines.BODY_QUEUE_SIZE) { game.G_Spawn() }
        source = game.G_Spawn()
    }

    fun spawn(name: String, configure: GameEntity.() -> Unit = {}): GameEntity =
        game.G_Spawn().apply {
            classname = name
            targetname = name
            configure()
            GameSpawn.ED_CallSpawn(this, game)
        }

    fun fire(entity: GameEntity, activator: GameEntity? = source) {
        source.target = entity.targetname
        GameUtil.G_UseTargets(source, activator, game)
    }
}
