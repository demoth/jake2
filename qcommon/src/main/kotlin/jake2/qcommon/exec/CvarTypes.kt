package jake2.qcommon.exec

import java.util.Locale

/** A codec and intrinsic constraints. Invalid input throws IllegalArgumentException. */
interface CvarType<T : Any> {
    enum class Kind { BOOLEAN, INTEGER, FLOAT, STRING, ENUM }

    /** A converted value and its canonical interchange token, prepared together on writes. */
    data class Resolved<T : Any>(val value: T, val token: String)

    val kind: Kind
    /** Validate input and prepare its native value and canonical token together. */
    fun parse(text: String): Resolved<T>

    /** Validate a native edit and prepare its token without parsing text. */
    fun validate(value: T): Resolved<T>

    /** An immutable snapshot of native choices; enums use names, other values use toString(). */
    val choices: List<T> get() = emptyList()
}

/** Built-in numeric parsing with intrinsic constraints; String whitespace is preserved. */
object CvarTypes {
    @JvmField val BOOLEAN: CvarType<Boolean> = BooleanType
    @JvmField val INTEGER: CvarType<Int> = IntegerType()
    @JvmField val FLOAT: CvarType<Float> = FloatType()
    @JvmField val STRING: CvarType<String> = StringType()

    data object BooleanType : CvarType<Boolean> {
        override val kind = CvarType.Kind.BOOLEAN
        override fun parse(text: String): CvarType.Resolved<Boolean> {
            val value = when (text.trim().lowercase(Locale.ROOT)) {
                "0", "false" -> false
                "1", "true" -> true
                else -> throw IllegalArgumentException("expected 0, 1, false or true")
            }
            return validate(value)
        }
        override fun validate(value: Boolean) = CvarType.Resolved(value, if (value) "1" else "0")
    }

    data class IntegerType(val min: Int? = null, val max: Int? = null) : CvarType<Int> {
        init {
            require(min == null || max == null || min <= max) { "inverted integer range" }
        }
        override val kind = CvarType.Kind.INTEGER
        override fun parse(text: String): CvarType.Resolved<Int> {
            val value = text.trim().toIntOrNull() ?: throw IllegalArgumentException("invalid integer or overflow")
            return validate(value)
        }
        override fun validate(value: Int): CvarType.Resolved<Int> {
            require((min == null || value >= min) && (max == null || value <= max)) {
                "integer outside range [$min, $max]"
            }
            return CvarType.Resolved(value, value.toString())
        }
    }

    data class FloatType(val min: Float? = null, val max: Float? = null) : CvarType<Float> {
        init {
            require((min == null || min.isFinite()) && (max == null || max.isFinite())
                && (min == null || max == null || min <= max)) { "invalid float range" }
        }
        override val kind = CvarType.Kind.FLOAT
        override fun parse(text: String): CvarType.Resolved<Float> {
            val value = text.trim().toFloatOrNull() ?: throw IllegalArgumentException("invalid float")
            return validate(value)
        }
        override fun validate(value: Float): CvarType.Resolved<Float> {
            require(value.isFinite()) { "expected a finite float" }
            require((min == null || value >= min) && (max == null || value <= max)) {
                "float outside range [$min, $max]"
            }
            val canonical = if (value == 0f) 0f else value // Canonicalize signed zero.
            return CvarType.Resolved(canonical, canonical.toString())
        }
    }

    data class StringType(val regex: String? = null) : CvarType<String> {
        private val pattern = regex?.let(::Regex)
        override val kind = CvarType.Kind.STRING
        override fun parse(text: String): CvarType.Resolved<String> = validate(text)
        override fun validate(value: String): CvarType.Resolved<String> {
            require(pattern == null || pattern.matches(value)) { "expected full match: $regex" }
            return CvarType.Resolved(value, value)
        }
    }

    inline fun <reified E : Enum<E>> enumeration(): CvarType<E> = EnumType(E::class.java)

    data class EnumType<E : Enum<E>>(val enumClass: Class<E>) : CvarType<E> {
        override val kind = CvarType.Kind.ENUM
        override fun parse(text: String): CvarType.Resolved<E> {
            val value = try {
                java.lang.Enum.valueOf(enumClass, text.trim())
            } catch (_: IllegalArgumentException) {
                throw IllegalArgumentException("unknown ${enumClass.simpleName} token")
            }
            return validate(value)
        }
        override fun validate(value: E): CvarType.Resolved<E> {
            require(value.declaringJavaClass == enumClass) { "expected ${enumClass.simpleName}" }
            return CvarType.Resolved(value, value.name)
        }
        override val choices: List<E>
            get() = java.util.List.copyOf(enumClass.enumConstants.toList())
    }

    /** Fixed native values selected by exact persisted tokens, without numeric coercion. */
    class ChoiceType<T : Any>(choices: List<T>) : CvarType<T> {
        override val choices: List<T> = java.util.List.copyOf(choices)
        override val kind = CvarType.Kind.ENUM

        init {
            require(this.choices.isNotEmpty()) { "choices are empty" }
            val valueClass = this.choices.first().javaClass
            require(this.choices.all { valueClass.isInstance(it) }) { "mixed choice types" }
            require(this.choices.map { it.toString() }.toSet().size == this.choices.size) { "duplicate choice token" }
        }

        override fun parse(text: String): CvarType.Resolved<T> {
            val value = choices.firstOrNull { it.toString() == text }
                ?: throw IllegalArgumentException("value is not an available choice")
            return CvarType.Resolved(value, text)
        }

        override fun validate(value: T): CvarType.Resolved<T> {
            val choice = choices.firstOrNull { it == value }
                ?: throw IllegalArgumentException("value is not an available choice")
            return CvarType.Resolved(choice, choice.toString())
        }
    }
}
