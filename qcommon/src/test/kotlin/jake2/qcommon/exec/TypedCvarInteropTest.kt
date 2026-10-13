package jake2.qcommon.exec

import jake2.qcommon.Defines.CVAR_LATCH
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Exercises Kotlin properties, direct registration, nullable pending values and the reified enum helper. */
class TypedCvarInteropTest {
    @Test
    fun `Kotlin registration consumes staged input and preserves lookup identity`() {
        val registry = Cvar()
        registry.setResult("count", "0002")
        assertNull(registry.find("count"))
        val variable = registry.register("count", CvarTypes.INTEGER, 0, 0, "Counter")
        assertSame(variable, registry.find("count"))
        assertSame(variable, registry.find<Int>("count"))
        assertEquals(2, variable.typedValue)
        assertEquals("2", variable.rawString)
        assertEquals(0, variable.defaultValue)
    }

    @Test
    fun `string declarations cannot become integer declarations`() {
        val registry = Cvar()
        val raw = registry.register("count", CvarTypes.STRING, "2", 0)
        assertSame(CvarTypes.STRING, raw.type)
        assertThrows(IllegalArgumentException::class.java) {
            registry.register("count", CvarTypes.INTEGER, 0)
        }
        assertSame(raw, registry.find("count"))
        assertEquals("2", raw.rawString)
    }

    @Test
    fun `Kotlin consumers use typed accessors and nonnull declarations`() {
        val cvars = Cvar()
        val toggle = cvars.register("toggle", CvarTypes.BOOLEAN, false)
        val count = cvars.register("count", CvarTypes.INTEGER, 0)
        assertFalse(toggle.typedValue)
        assertEquals(0, count.typedValue)
        assertEquals("0", count.rawString)
        assertEquals(CvarType.Kind.BOOLEAN, toggle.type.kind)
    }

    @Test
    fun `nullable pending distinguishes no change from a queued empty String`() {
        val cvars = Cvar()
        // Video cvars queue changes even without a running server.
        val text = cvars.register("vid_text", CvarTypes.STRING, "old", CVAR_LATCH)
        assertNull(text.pendingValue)
        val result = cvars.setResult(text.name, "")
        assertEquals(CvarSetResult.Status.QUEUED, result.status)
        assertEquals("", text.pendingValue)
        assertEquals("", text.rawPendingString)
        assertEquals("old", text.rawString)
    }

    @Test
    fun `checked lookup preserves identity and rejects mismatched undeclared or missing entries`() {
        val cvars = Cvar()
        cvars.register("raw", CvarTypes.STRING, "true", 0)
        assertThrows(IllegalArgumentException::class.java) { cvars.find<Boolean>("raw") }
        cvars.setResult("toggle", "true")
        val declared: cvar_t<Boolean> = cvars.register("toggle", CvarTypes.BOOLEAN, false)
        val found = cvars.find<Boolean>("toggle")
        assertSame(declared, found)
        val value: Boolean = found.typedValue
        assertTrue(value)
        cvars.AddAlias("alias", "toggle")
        assertSame(found, cvars.find<Boolean>("alias"))
        assertThrows(IllegalArgumentException::class.java) { cvars.find<Int>("toggle") }
        assertThrows(IllegalArgumentException::class.java) { cvars.find<Any>("toggle") }
        assertThrows(IllegalArgumentException::class.java) { cvars.find<Boolean>("missing") }
        val unknown: cvar_t<*> = requireNotNull(cvars.find("toggle"))
        assertEquals("1", unknown.rawString)
    }

    @Test
    fun `checked lookup uses choice native types and exact enum classes`() {
        val cvars = Cvar()
        val choice = cvars.register("samples", CvarTypes.ChoiceType(listOf(0, 4)), 4)
        assertSame(choice, cvars.find<Int>("samples"))
        assertThrows(IllegalArgumentException::class.java) { cvars.find<Float>("samples") }
        val mode = cvars.register("mode", CvarTypes.enumeration<Mode>(), Mode.Pixels)
        assertSame(mode, cvars.find<Mode>("mode"))
        assertThrows(IllegalArgumentException::class.java) { cvars.find<OtherMode>("mode") }
    }

    private enum class OtherMode { Pixels }

    private enum class Mode {
        Pixels, Logical;
        override fun toString() = "Display $name"
    }

    @Test
    fun `Kotlin enum helper and native choice lists need no Java wrapper types`() {
        val modeType = CvarTypes.enumeration<Mode>()
        val cvars = Cvar()
        val mode = cvars.register("mode", modeType, Mode.Pixels)
        assertEquals(Mode.Pixels, mode.defaultValue)
        assertEquals("Pixels", mode.rawString)
        assertEquals(CvarSetResult.Status.APPLIED, cvars.setResult(mode.name, Mode.Logical.name).status)
        assertEquals(Mode.Logical, mode.typedValue)
        val choices = listOf("desktop")
        val displayType = CvarTypes.ChoiceType(choices)
        assertEquals(listOf("desktop"), displayType.choices)
        assertThrows(IllegalArgumentException::class.java) { displayType.parse("missing") }
    }

    @Test
    fun `fixed choice construction validates and snapshots its input`() {
        val choices = mutableListOf(0, 4)
        val type = CvarTypes.ChoiceType(choices)
        choices.clear()
        assertEquals(listOf(0, 4), type.choices)
        assertEquals("4", type.parse("4").token)
        assertThrows(IllegalArgumentException::class.java) { type.parse("2") }
        assertThrows(IllegalArgumentException::class.java) {
            CvarTypes.ChoiceType(emptyList<Int>())
        }
        assertThrows(IllegalArgumentException::class.java) {
            CvarTypes.ChoiceType(listOf(0, 0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CvarTypes.ChoiceType(listOf(0, "mixed"))
        }
        val text = CvarTypes.ChoiceType(listOf(" low ", "high"))
        assertEquals(" low ", text.parse(" low ").value)
        assertThrows(IllegalArgumentException::class.java) { text.parse("low") }
    }
}
