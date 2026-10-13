package jake2.qcommon.exec

import jake2.qcommon.Defines.CVAR_LATCH
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Verifies cached reads, native writes without parsing, and coherent active/pending state after rejection. */
class CachedCvarTest {
    private enum class Mode { Pixels, Logical }

    private class CountingType<T : Any>(private val base: CvarType<T>) : CvarType<T> by base {
        var calls = 0
        var nativeCalls = 0
        override fun parse(text: String): CvarType.Resolved<T> { calls++; return base.parse(text) }
        override fun validate(value: T): CvarType.Resolved<T> { nativeCalls++; return base.validate(value) }
    }

    @Test
    fun `reads use cached values without conversion`() {
        val registry = Cvar()
        val booleanType = CountingType(CvarTypes.BOOLEAN)
        val intType = CountingType(CvarTypes.INTEGER)
        val floatType = CountingType(CvarTypes.FLOAT)
        val stringType = CountingType(CvarTypes.STRING)
        val enumType = CountingType(CvarTypes.enumeration<Mode>())
        val boolean = registry.register("boolean", booleanType, false)
        val integer = registry.register("integer", intType, 0)
        val float = registry.register("float", floatType, 0f)
        val string = registry.register("string", stringType, "")
        val enum = registry.register("enum", enumType, Mode.Pixels)
        val types = listOf(booleanType, intType, floatType, stringType, enumType)
        val initialCalls = types.map { it.calls }
        val initialNativeCalls = types.map { it.nativeCalls }
        assertTrue(initialNativeCalls.all { it > 0 })
        assertSame(integer, registry.find<Int>("integer"))
        repeat(2) {
            assertFalse(boolean.typedValue)
            assertEquals(0, integer.typedValue)
            assertEquals(0f, float.typedValue)
            assertEquals("", string.typedValue)
            assertEquals(Mode.Pixels, enum.typedValue)
            assertNull(boolean.pendingValue)
            assertNull(integer.pendingValue)
            assertNull(float.pendingValue)
            assertNull(string.pendingValue)
            assertNull(enum.pendingValue)
            assertEquals("0", integer.rawString)
            assertNull(integer.rawPendingString)
            assertNotNull(integer.type)
        }
        assertThrows(IllegalStateException::class.java) { integer.asBoolean() }
        assertEquals(initialCalls, types.map { it.calls })
        assertEquals(initialNativeCalls, types.map { it.nativeCalls })
        assertEquals(CvarSetResult.Status.APPLIED, registry.setResult(integer.name, "16777217").status)
        assertEquals(16_777_217, integer.typedValue)
        assertTrue(intType.calls > initialCalls[1])
    }

    @Test
    fun `native writes validate without parsing and preserve pending state on rejection`() {
        val registry = Cvar()
        val type = CountingType(CvarTypes.IntegerType(1, 10))
        val variable = registry.register("vid_budget", type, 1, CVAR_LATCH)
        val textCalls = type.calls
        val nativeCalls = type.nativeCalls
        assertEquals(CvarSetResult.Status.QUEUED, variable.set(4).status)
        assertEquals(1, variable.typedValue)
        assertEquals(4, variable.pendingValue)
        assertEquals("4", variable.rawPendingString)
        variable.modified = false
        assertEquals(CvarSetResult.Status.REJECTED, variable.set(11).status)
        assertEquals(1, variable.typedValue)
        assertEquals(4, variable.pendingValue)
        assertEquals("4", variable.rawPendingString)
        assertFalse(variable.modified)
        assertEquals(textCalls, type.calls)
        assertEquals(nativeCalls + 2, type.nativeCalls)
    }

    @Test
    fun `regex validation runs on mutations and rejected writes preserve the cache`() {
        val type = CountingType(CvarTypes.StringType("old|new"))
        val registry = Cvar()
        val variable = registry.register("text", type, "old")
        assertEquals(CvarSetResult.Status.APPLIED, registry.setResult(variable.name, "new").status)
        val before = type.calls
        repeat(2) {
            assertEquals("new", variable.typedValue)
            assertNull(variable.pendingValue)
        }
        assertEquals(before, type.calls)
        variable.modified = false
        assertEquals(CvarSetResult.Status.REJECTED, registry.setResult(variable.name, "rejected").status)
        assertTrue(type.calls > before)
        assertEquals("new", variable.typedValue)
        assertEquals("new", variable.rawString)
        assertFalse(variable.modified)
    }

    @Test
    fun `choice reads use caches and rejected edits preserve pending selection`() {
        val type = CountingType(CvarTypes.ChoiceType(listOf("first", "second")))
        val registry = Cvar()
        val variable = registry.register("vid_choice", type, "first", CVAR_LATCH)
        assertEquals(CvarSetResult.Status.QUEUED, registry.setResult(variable.name, "second").status)
        val beforeReads = type.calls
        repeat(2) {
            assertEquals("first", variable.typedValue)
            assertEquals("second", variable.pendingValue)
        }
        assertEquals(beforeReads, type.calls)
        variable.modified = false
        assertEquals(CvarSetResult.Status.REJECTED, registry.setResult(variable.name, "missing").status)
        assertEquals("first", variable.typedValue)
        assertEquals("second", variable.pendingValue)
        assertFalse(variable.modified)
        registry.updateLatchedVars()
        assertEquals("second", variable.typedValue)
        assertNull(variable.pendingValue)
        assertNull(variable.rawPendingString)
        assertTrue(variable.modified)
    }

    @Test
    fun `nullable pending slots preserve false zero and enum values through commits`() {
        val registry = Cvar()
        val boolean = registry.register("vid_boolean", CvarTypes.BOOLEAN, true, CVAR_LATCH)
        val float = registry.register("vid_float", CvarTypes.FLOAT, 1f, CVAR_LATCH)
        val mode = registry.register("vid_mode", CvarTypes.enumeration<Mode>(), Mode.Pixels, CVAR_LATCH)
        assertEquals(CvarSetResult.Status.QUEUED, registry.setResult(boolean.name, "false").status)
        assertEquals(CvarSetResult.Status.QUEUED, registry.setResult(float.name, "0.0").status)
        assertEquals(CvarSetResult.Status.QUEUED, registry.setResult(mode.name, Mode.Logical.name).status)
        assertTrue(boolean.typedValue)
        assertEquals(1f, float.typedValue)
        assertEquals(Mode.Pixels, mode.typedValue)
        assertEquals(false, boolean.pendingValue)
        assertEquals(0f, float.pendingValue)
        assertEquals(Mode.Logical, mode.pendingValue)
        assertEquals(CvarSetResult.Status.APPLIED, registry.setResult(mode.name, Mode.Pixels.name).status)
        assertNull(mode.pendingValue)
        assertEquals(CvarSetResult.Status.QUEUED, registry.setResult(mode.name, Mode.Logical.name).status)
        registry.updateLatchedVars()
        assertFalse(boolean.typedValue)
        assertEquals(0f, float.typedValue)
        assertEquals(Mode.Logical, mode.typedValue)
        assertNull(boolean.pendingValue)
        assertNull(float.pendingValue)
        assertNull(mode.pendingValue)
    }
}
