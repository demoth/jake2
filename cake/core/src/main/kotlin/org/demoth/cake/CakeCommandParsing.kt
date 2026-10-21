package org.demoth.cake

import org.demoth.cake.hosting.HostMode
import org.demoth.cake.hosting.HostRequest
import org.demoth.cake.profile.CakeGameProfile

// Cake accepts `connect` both from local UI/menu flow and from the shared command buffer.
// Validate/sanitize the target up front so malformed input prints usage instead of throwing
// when the command arrives with missing or blank arguments.
internal fun sanitizeConnectTarget(args: List<String>): String? {
    if (args.size != 2) {
        return null
    }
    return args[1].trim().takeIf { it.isNotEmpty() }
}

internal fun parseHostRequest(args: List<String>, profile: CakeGameProfile): HostRequest? {
    if (args.size !in 2..5) return null
    val mode = when (args.getOrNull(2)?.lowercase() ?: "singleplayer") {
        "singleplayer" -> HostMode.SINGLEPLAYER
        "deathmatch" -> HostMode.DEATHMATCH
        "coop" -> HostMode.COOP
        else -> return null
    }
    val skill = if (args.size >= 4) args[3].toIntOrNull()?.takeIf { it in 0..3 } ?: return null else 1
    val defaultClients = when (mode) {
        HostMode.SINGLEPLAYER -> 1
        HostMode.DEATHMATCH -> 8
        HostMode.COOP -> 4
    }
    val maxClients = if (args.size == 5) args[4].toIntOrNull()?.takeIf { it in 1..256 } ?: return null else defaultClients
    return HostRequest(profile, args[1], mode, skill, maxClients)
}
