package jake2.game

fun SubgameEntity.hasSpawnFlag(flag: Int) = spawnflags and flag != 0

fun SubgameEntity.addSpawnFlag(flag: Int) {
    spawnflags = spawnflags or flag
}

fun SubgameEntity.removeSpawnFlag(flag: Int) { 
    spawnflags = spawnflags and flag.inv()
}