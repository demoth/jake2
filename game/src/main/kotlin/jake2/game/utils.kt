package jake2.game

fun SubgameEntity.hasSpawnFlag(flag: Int) = spawnflags and flag != 0

fun SubgameEntity.setSpawnFlag(flag: Int) {
    spawnflags = spawnflags or flag
}

fun SubgameEntity.unsetSpawnFlag(flag: Int) {
    spawnflags = spawnflags and flag.inv()
}
