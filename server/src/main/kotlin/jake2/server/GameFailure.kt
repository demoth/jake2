package jake2.server

/** A failure of Game-owned state. Lower layers propagate; the Host chooses cleanup. */
class GameFailure @JvmOverloads constructor(val game: GameImportsImpl, message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)
