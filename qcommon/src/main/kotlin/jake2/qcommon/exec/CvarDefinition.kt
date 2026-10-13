package jake2.qcommon.exec

/** Immutable owning definition. Re-registration must preserve the complete contract. */
data class CvarSpec<T : Any>(
    val name: String,
    val type: CvarType<T>,
    val defaultValue: T,
    val flags: Int = 0,
    val description: String? = null,
) {
    val defaultString: String

    init {
        require(NAME.matches(name)) { "invalid cvar name" }
        val token = if (defaultValue is Enum<*>) defaultValue.name else defaultValue.toString()
        defaultString = type.resolve(token).token
    }

    private companion object {
        val NAME = Regex("[A-Za-z_][A-Za-z0-9_]*")
    }
}

/** Mutation outcome and optional feedback for callers; rejection leaves the entry unchanged. */
data class CvarSetResult(
    val status: Status,
    val diagnostic: String? = null,
) {
    enum class Status { APPLIED, QUEUED, UNCHANGED, REJECTED }

    val accepted: Boolean get() = status != Status.REJECTED
}

/** Resolved mutation payload. Typed slots are populated once, then copied into the existing entry. */
internal class CvarValue(val token: String, val value: Any) {
    val legacyFloat: Float = jake2.qcommon.util.Lib.atof(token)

    init {
        require(value is Boolean || value is Int || value is Float || value is String || value is Enum<*>) {
            "unsupported cvar value type"
        }
    }
}

/** Acquires a required native reference, checking the declared type once at lookup. */
inline fun <reified T : Any> Cvar.find(name: String): cvar_t<T> = find(name, T::class.javaObjectType)
