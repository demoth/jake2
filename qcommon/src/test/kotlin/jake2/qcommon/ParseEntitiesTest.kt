package jake2.qcommon

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Test

class ParseEntitiesTest {
    @Test
    fun testEmptyString() {
        assertTrue(parseEntities("").isEmpty())
    }

    @Test
    fun testEmptyEntity() {
        val entities = parseEntities(" { } ")
        assertEquals(1, entities.size)
        assertTrue(entities.first().isEmpty())
    }

    @Test
    fun testSimpleCase() {
        val entities = parseEntities("""
            {
                "hello" "world"
            }
        """.trimIndent())
        assertEquals(1, entities.size)
        assertEquals("world", entities.firstOrNull()?.get("hello"))
    }

    @Test
    fun testComment() {
        val entities = parseEntities("""
            // this is just a comment from 2022
            {
                "game" "baseq2"
                "name" "bitterman" // where is willits?
            } // end of the line
        """.trimIndent())
        assertEquals(1, entities.size)
        assertEquals("baseq2", entities.firstOrNull()?.get("game"))
        assertEquals("bitterman", entities.firstOrNull()?.get("name"))
    }

    @Test
    fun testTwoEntities() {
        val entities = parseEntities("""
            {
                "classname" "worldspawn"
            }
            {
                "classname" "light"
            }
        """.trimIndent())
        assertEquals(2, entities.size)
        assertEquals("worldspawn", entities.firstOrNull()?.get("classname"))
        assertEquals("light", entities.lastOrNull()?.get("classname"))
    }

    @Test
    fun testValueWithSpace() {
        val entities = parseEntities("""
            {
                "message" "hello world"
            }
        """.trimIndent())
        assertEquals(1, entities.size)
        assertEquals("hello world", entities.firstOrNull()?.get("message"))
    }

    @Test
    fun testTwoValues() {
        val entities = parseEntities("""
            {
                "hello" "world"
                "foo" "bar"
            }
        """.trimIndent())
        assertEquals(1, entities.size)
        assertEquals("world", entities.firstOrNull()?.get("hello"))
        assertEquals("bar", entities.firstOrNull()?.get("foo"))
    }

    @Test
    fun base2Test() {
        // thanks to BjossiAlfreds
        val entityString = String(javaClass.getResourceAsStream("base2.ent").readAllBytes())
        val entities = parseEntities(entityString)
        assertEquals(662, entities.size)
        // random assertions
        assertEquals("Installation", entities.find { it["classname"] == "worldspawn" }?.get("message"))
        assertEquals(89, entities.filter { it["classname"] == "target_speaker" }.size)
    }
}
