package org.demoth.cake.assets

class MissingResourceException(
    val path: String,
    val resourceKind: ResourceKind,
    val phase: String,
    cause: Throwable? = null,
) : RuntimeException("Missing ${resourceKind.name.lowercase()} resource '$path' during $phase", cause)
