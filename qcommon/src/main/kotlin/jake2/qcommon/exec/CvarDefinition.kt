package jake2.qcommon.exec

/** Mutation outcome and optional feedback for callers; rejection leaves the entry unchanged. */
data class CvarSetResult(
    val status: Status,
    val diagnostic: String? = null,
) {
    enum class Status { APPLIED, QUEUED, UNCHANGED, REJECTED }

    val accepted: Boolean get() = status != Status.REJECTED
}

/** Acquires a required native reference, checking the declared type once at lookup. */
inline fun <reified T : Any> Cvar.find(name: String): cvar_t<T> = find(name, T::class.javaObjectType)

/** Input awaiting its owning declaration; suggested flags never grant attributes themselves. */
internal data class EarlyCvarValue(val text: String, val suggestedFlags: Int? = null)
