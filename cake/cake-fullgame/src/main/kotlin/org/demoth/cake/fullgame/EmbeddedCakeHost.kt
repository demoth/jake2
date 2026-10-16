package org.demoth.cake.fullgame

import org.demoth.cake.hosting.*

/**
 * Composition boundary for the embedded server. Lifecycle and network integration follow in
 * JK2-TSK-17–20. Construction deliberately does not register server commands or open sockets.
 */
class EmbeddedCakeHost : CakeHost {
    override val availability = HostAvailability.NOT_READY
    override val state = HostState.STOPPED
    override fun start(request: HostRequest) =
        HostStartResult.Rejected("Embedded hosting is not implemented yet.")
    override fun update(elapsedMillis: Long) = Unit
    override fun stop(reason: String) = Unit
    override fun setPaused(paused: Boolean) = Unit
}
