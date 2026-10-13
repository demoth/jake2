package jake2.qcommon.exec

/** Mutation outcome and optional feedback for callers; rejection leaves the entry unchanged. */
data class CvarSetResult(
    val status: Status,
    val diagnostic: String? = null,
) {
    enum class Status { APPLIED, QUEUED, UNCHANGED, REJECTED }

    val accepted: Boolean get() = status != Status.REJECTED
}

/** Native value and canonical token prepared together for a validated registry write. */
internal class CvarValue(val token: String, val value: Any) {
    // TODO: Remove this conversion together with cvar_t's legacy Float mirror after reader migration.
    val legacyFloat: Float = when (value) {
        is Boolean -> if (value) 1f else 0f
        is Number -> value.toFloat()
        else -> jake2.qcommon.util.Lib.atof(token)
    }

    init {
        require(value is Boolean || value is Int || value is Float || value is String || value is Enum<*>) {
            "unsupported cvar value type"
        }
    }
}

/** Acquires a required native reference, checking the declared type once at lookup. */
inline fun <reified T : Any> Cvar.find(name: String): cvar_t<T> = find(name, T::class.javaObjectType)

/** Input awaiting its owning declaration; suggested flags never grant attributes themselves. */
internal data class EarlyCvarValue(val text: String, val suggestedFlags: Int? = null)
