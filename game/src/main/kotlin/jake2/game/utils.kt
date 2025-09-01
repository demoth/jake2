package jake2.game

infix fun GameEntity.hasSpawnFlag(flag: Int) = spawnflags and flag != 0

fun GameEntity.setSpawnFlag(flag: Int) {
    spawnflags = spawnflags or flag
}

fun GameEntity.unsetSpawnFlag(flag: Int) {
    spawnflags = spawnflags and flag.inv()
}
