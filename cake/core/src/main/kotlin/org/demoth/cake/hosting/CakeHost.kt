package org.demoth.cake.hosting

import org.demoth.cake.profile.CakeGameProfile

/** Application-thread boundary between Cake and an optional embedded server. */
interface CakeHost {
    val availability: HostAvailability
    val state: HostState

    /** Success means the server is ready for local attachment, not that the client has spawned. */
    fun start(request: HostRequest): HostStartResult
    fun update(elapsedMillis: Long)
    /** Safe to call repeatedly, including before a session has started. */
    fun stop(reason: String)
    /** Implementations only pause singleplayer; multiplayer must keep running. */
    fun setPaused(paused: Boolean)
}

enum class HostAvailability { REMOTE_ONLY, NOT_READY, AVAILABLE }
enum class HostState { STOPPED, STARTING, RUNNING }
enum class HostMode { SINGLEPLAYER, DEATHMATCH, COOP }

/** Values are validated by the host before starting or changing a session. */
data class HostRequest(
    val profile: CakeGameProfile,
    val map: String,
    val mode: HostMode,
    val skill: Int = 1,
    val maxClients: Int = 1,
    val hostname: String = "Cake server",
)

sealed interface HostStartResult {
    data object Started : HostStartResult
    data class Rejected(val reason: String) : HostStartResult
}

/** Remote-only builds can accept the same application wiring without server side effects. */
class UnavailableCakeHost : CakeHost {
    override val availability = HostAvailability.REMOTE_ONLY
    override val state = HostState.STOPPED
    override fun start(request: HostRequest) =
        HostStartResult.Rejected("Hosting requires the Cake fullgame application.")
    override fun update(elapsedMillis: Long) = Unit
    override fun stop(reason: String) = Unit
    override fun setPaused(paused: Boolean) = Unit
}
