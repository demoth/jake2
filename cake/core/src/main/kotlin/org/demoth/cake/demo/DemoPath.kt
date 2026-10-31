package org.demoth.cake.demo

/** Console names resolve within the active profile's demos directory, including archived entries. */
internal fun demoLogicalPath(name: String): String? {
    val normalized = name.replace('\\', '/')
    if (normalized.isBlank() || normalized.startsWith('/') || ':' in normalized) return null
    if (normalized.split('/').any { it.isEmpty() || it == "." || it == ".." }) return null
    val path = if (normalized.startsWith("demos/", ignoreCase = true)) normalized else "demos/$normalized"
    return if (path.endsWith(".dm2", ignoreCase = true)) path else "$path.dm2"
}
