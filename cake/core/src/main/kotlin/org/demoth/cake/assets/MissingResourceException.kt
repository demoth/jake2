package org.demoth.cake.assets

/** Resolution found no resource. The caller decides whether to download it, use a fallback, or fail. */
class MissingResourceException(
    val path: String,
    val resourceKind: ResourceKind,
    val phase: String,
    cause: Throwable? = null,
) : RuntimeException("Missing ${resourceKind.name.lowercase()} resource '$path' during $phase", cause)
