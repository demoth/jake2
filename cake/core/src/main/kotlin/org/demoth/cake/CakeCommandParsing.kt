package org.demoth.cake

// Cake accepts `connect` both from local UI/menu flow and from the shared command buffer.
// Validate/sanitize the target up front so malformed input prints usage instead of throwing
// when the command arrives with missing or blank arguments.
internal fun sanitizeConnectTarget(args: List<String>): String? {
    if (args.size != 2) {
        return null
    }
    return args[1].trim().takeIf { it.isNotEmpty() }
}
