package jake2.qcommon.filesystem

/** A decoder rejected invalid or unsupported content. The caller supplies the resource path. */
class ResourceFormatException(message: String) : IllegalStateException(message)

internal inline fun checkResourceFormat(condition: Boolean, message: () -> String) {
    if (!condition) throw ResourceFormatException(message())
}
