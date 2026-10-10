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
internal class CvarValue(val token: String, value: Any) {
    val booleanValue: Boolean? = value as? Boolean
    val intValue: Int? = value as? Int
    val floatValue: Float? = value as? Float
    val stringValue: String? = value as? String
    val enumValue: Enum<*>? = value as? Enum<*>
    val legacyFloat: Float = jake2.qcommon.util.Lib.atof(token)

    init {
        require(booleanValue != null || intValue != null || floatValue != null ||
            stringValue != null || enumValue != null) { "unsupported cvar value type" }
    }
}
