package jake2.game

/**
 * Run `block` for all entities on the same team
 */
fun SubgameEntity.forEachTeamMember(block: (SubgameEntity) -> Unit) {
    var team = teammaster
    while (team != null) {
        block.invoke(team)
        team = team.teamchain
    }
}
