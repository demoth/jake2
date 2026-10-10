package jake2.qcommon.exec

import jake2.qcommon.Defines.CVAR_LATCH
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Verifies read-time codec independence and coherent active/pending caches after writes. */
class CachedCvarTest {
    private enum class Mode { Pixels, Logical }

    private class CountingType<T : Any>(private val base: CvarType<T>) : CvarType<T> by base {
        var calls = 0
        override fun resolve(text: String): CvarType.Resolved<T> { calls++; return base.resolve(text) }
    }

    @Test
    fun `reads use cached values without conversion`() {
        val registry = Cvar()
        val booleanType = CountingType(CvarTypes.BOOLEAN)
        val intType = CountingType(CvarTypes.INTEGER)
        val floatType = CountingType(CvarTypes.FLOAT)
        val stringType = CountingType(CvarTypes.STRING)
        val enumType = CountingType(CvarTypes.enumeration<Mode>())
        val boolean = registry.register(CvarSpec("boolean", booleanType, false))
        val integer = registry.register(CvarSpec("integer", intType, 0))
        val float = registry.register(CvarSpec("float", floatType, 0f))
        val string = registry.register(CvarSpec("string", stringType, ""))
        val enum = registry.register(CvarSpec("enum", enumType, Mode.Pixels))
        val types = listOf(booleanType, intType, floatType, stringType, enumType)
        val initialCalls = types.map { it.calls }
        assertTrue(initialCalls.all { it > 0 })
        repeat(2) {
            assertFalse(boolean.asBoolean())
            assertEquals(0, integer.asInt())
            assertEquals(0f, float.asFloat())
            assertEquals("", string.asString())
            assertEquals(Mode.Pixels, enum.asEnum(Mode::class.java))
            assertNull(boolean.pendingBooleanValue)
            assertNull(integer.pendingIntValue)
            assertNull(float.pendingFloatValue)
            assertNull(string.pendingStringValue)
            assertNull(enum.getPendingEnumValue(Mode::class.java))
            assertEquals("0", integer.rawString)
            assertNull(integer.rawPendingString)
            assertNotNull(integer.definition)
        }
        assertThrows(IllegalStateException::class.java) { integer.asBoolean() }
        assertThrows(IllegalStateException::class.java) { boolean.pendingStringValue }
        assertEquals(initialCalls, types.map { it.calls })
        assertEquals(CvarSetResult.Status.APPLIED, registry.setResult(integer.name, "16777217").status)
        assertEquals(16_777_217, integer.asInt())
        assertTrue(intType.calls > initialCalls[1])
    }

    @Test
    fun `regex validation runs on mutations and rejected writes preserve the cache`() {
        val type = CountingType(CvarTypes.StringType("old|new"))
        val registry = Cvar()
        val variable = registry.register(CvarSpec("text", type, "old"))
        assertEquals(CvarSetResult.Status.APPLIED, registry.setResult(variable.name, "new").status)
        val before = type.calls
        repeat(2) {
            assertEquals("new", variable.asString())
            assertNull(variable.pendingStringValue)
        }
        assertEquals(before, type.calls)
        variable.modified = false
        assertEquals(CvarSetResult.Status.REJECTED, registry.setResult(variable.name, "rejected").status)
        assertTrue(type.calls > before)
        assertEquals("new", variable.asString())
        assertEquals("new", variable.rawString)
        assertFalse(variable.modified)
    }

    @Test
    fun `choice reads use caches and rejected edits preserve pending selection`() {
        val type = CountingType(CvarTypes.ChoiceType(listOf("first", "second")))
        val registry = Cvar()
        val variable = registry.register(CvarSpec("vid_choice", type, "first", CVAR_LATCH))
        assertEquals(CvarSetResult.Status.QUEUED, registry.setResult(variable.name, "second").status)
        val beforeReads = type.calls
        repeat(2) {
            assertEquals("first", variable.asString())
            assertEquals("second", variable.pendingStringValue)
        }
        assertEquals(beforeReads, type.calls)
        variable.modified = false
        assertEquals(CvarSetResult.Status.REJECTED, registry.setResult(variable.name, "missing").status)
        assertEquals("first", variable.asString())
        assertEquals("second", variable.pendingStringValue)
        assertFalse(variable.modified)
        registry.updateLatchedVars()
        assertEquals("second", variable.asString())
        assertNull(variable.pendingStringValue)
        assertNull(variable.rawPendingString)
        assertTrue(variable.modified)
    }

    @Test
    fun `nullable pending slots preserve false zero and enum values through commits`() {
        val registry = Cvar()
        val boolean = registry.register(CvarSpec("vid_boolean", CvarTypes.BOOLEAN, true, CVAR_LATCH))
        val float = registry.register(CvarSpec("vid_float", CvarTypes.FLOAT, 1f, CVAR_LATCH))
        val mode = registry.register(CvarSpec("vid_mode", CvarTypes.enumeration<Mode>(), Mode.Pixels, CVAR_LATCH))
        assertEquals(CvarSetResult.Status.QUEUED, registry.setResult(boolean.name, "false").status)
        assertEquals(CvarSetResult.Status.QUEUED, registry.setResult(float.name, "0.0").status)
        assertEquals(CvarSetResult.Status.QUEUED, registry.setResult(mode.name, Mode.Logical.name).status)
        assertTrue(boolean.asBoolean())
        assertEquals(1f, float.asFloat())
        assertEquals(Mode.Pixels, mode.asEnum(Mode::class.java))
        assertEquals(false, boolean.pendingBooleanValue)
        assertEquals(0f, float.pendingFloatValue)
        assertEquals(Mode.Logical, mode.getPendingEnumValue(Mode::class.java))
        assertEquals(CvarSetResult.Status.APPLIED, registry.setResult(mode.name, Mode.Pixels.name).status)
        assertNull(mode.getPendingEnumValue(Mode::class.java))
        assertEquals(CvarSetResult.Status.QUEUED, registry.setResult(mode.name, Mode.Logical.name).status)
        registry.updateLatchedVars()
        assertFalse(boolean.asBoolean())
        assertEquals(0f, float.asFloat())
        assertEquals(Mode.Logical, mode.asEnum(Mode::class.java))
        assertNull(boolean.pendingBooleanValue)
        assertNull(float.pendingFloatValue)
        assertNull(mode.getPendingEnumValue(Mode::class.java))
    }
}
