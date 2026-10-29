package jake2.server

/** A failure of Game-owned state. Lower layers propagate; the Host chooses cleanup. */
class GameFailure(val game: GameImportsImpl, message: String) : RuntimeException(message)
