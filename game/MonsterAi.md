# Quake 2 Monsters

## Monster Behavior:

Key takeaways:
 * Monsters don't use navmesh - just bumping left/right like blind kittens until the enemy is visible.
 * No code separation into decision-making, state, animation; everything is spaghetti-coded.

Generic monster decision overview:

1. if triggered & has combat point -> go to combat point
2. if triggered & see enemy & fired at -> duck 
3. if triggered & see enemy -> attack enemy
4. if triggered & !see enemy -> chase
5. if !triggered & has path-target -> goto path-target
6. else: Idle

Suggestions:
 - cool down, if the monster haven't seen the enemy for some time
 - wander around when idle
 - use cover during combat (requires navmesh)
