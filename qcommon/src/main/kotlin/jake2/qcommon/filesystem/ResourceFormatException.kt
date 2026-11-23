package jake2.qcommon.filesystem

/** A decoder rejected invalid or unsupported content. The caller supplies the resource path. */
class ResourceFormatException @JvmOverloads constructor(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

internal inline fun checkResourceFormat(condition: Boolean, message: () -> String) {
    if (!condition) throw ResourceFormatException(message())
}
