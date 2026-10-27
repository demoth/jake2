package org.demoth.cake.hosting

import org.demoth.cake.profile.CakeGameProfile

/** Application-thread boundary between Cake and an optional embedded server. */
interface CakeHost {
    val availability: HostAvailability
    val state: HostState

    /** Called after engine bootstrap, before application startup commands. */
    fun initialize(startupRejection: () -> String? = { null })
    /** Select content and save storage while disconnected and idle. */
    fun activateProfile(profile: CakeGameProfile?)
    /** Release the application-owned Host and its command registrations. */
    fun dispose()

    /** Success means the server is ready for local attachment, not that the client has spawned. */
    fun start(request: HostRequest): HostStartResult
    /** Prepare commands/filesystem before the caller queues the content's newgame alias. */
    fun prepareNewGame(profile: CakeGameProfile, skill: Int): HostStartResult =
        HostStartResult.Rejected("Hosting requires the Cake fullgame application.")
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

/** A recoverable server drop; programming errors retain their original exception type. */
class HostFailureException(message: String, cause: Throwable) : RuntimeException(message, cause)

/** Remote-only builds can accept the same application wiring without server side effects. */
class UnavailableCakeHost : CakeHost {
    override val availability = HostAvailability.REMOTE_ONLY
    override val state = HostState.STOPPED
    override fun initialize(startupRejection: () -> String?) = Unit
    override fun activateProfile(profile: CakeGameProfile?) = Unit
    override fun dispose() = Unit
    override fun start(request: HostRequest) =
        HostStartResult.Rejected("Hosting requires the Cake fullgame application.")
    override fun update(elapsedMillis: Long) = Unit
    override fun stop(reason: String) = Unit
    override fun setPaused(paused: Boolean) = Unit
}
