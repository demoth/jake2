package jake2.qcommon.filesystem

import jake2.qcommon.Defines
import jake2.qcommon.texinfo_t
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CStringLoaderTest {
    @Test
    fun walNamesIgnoreTrailingGarbageInBothLoaders() {
        val buffer = ByteBuffer.allocate(101).order(ByteOrder.LITTLE_ENDIAN)
        putName(buffer, "texture", 32)
        buffer.putInt(1).putInt(1)
        repeat(4) { buffer.putInt(100) }
        putName(buffer, "", 32) // Empty animation name must stay empty despite garbage.
        buffer.putInt(7).putInt(8).putInt(9).put(42.toByte())

        val wal = WAL(buffer.array())
        val legacy = qfiles.miptex_t(buffer.array())
        assertEquals("texture", wal.name)
        assertEquals("texture", legacy.name)
        assertEquals("", wal.nextName)
        assertEquals("", legacy.animname)
        assertEquals(7, wal.flags)
        assertEquals(7, legacy.flags)
        assertEquals(42.toByte(), wal.imageData.single())
    }

    @Test
    fun spriteNamesIgnoreTrailingGarbageAndConsumeWholeField() {
        val buffer = ByteBuffer.allocate(160).order(ByteOrder.LITTLE_ENDIAN)
        repeat(2) { index ->
            buffer.putInt(16 + index).putInt(32).putInt(8).putInt(16)
            putName(buffer, "sprites/frame$index.pcx", 64)
        }
        buffer.flip()
        repeat(2) { index ->
            val frame = qfiles.Sp2SpriteFrame(buffer)
            assertEquals("sprites/frame$index.pcx", frame.imageFileName)
            assertEquals(16 + index, frame.width)
        }
        assertEquals(160, buffer.position())
    }

    @Test
    fun md2SkinsUseTheWholeNameBeforeNullRatherThanTheFirstPcxSuffix() {
        for (name in listOf("players/test.pcx/skin.pcx", "players/test/skin.png")) {
            val bytes = javaClass.getResourceAsStream("models/blade/tris.md2")!!.readBytes()
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            buffer.position(buffer.getInt(44)) // ofs_skins
            putName(buffer, name, 64)
            buffer.rewind()

            val model = Md2Model(buffer)
            assertEquals(name, model.skinNames.first())
            assertEquals("players/tekk-blade/blograde.pcx", model.skinNames[1])
        }
    }

    @Test
    fun bspTextureAndEntityStringsIgnoreTrailingGarbage() {
        val entities = "{\"classname\" \"worldspawn\"}"
        val entityBytes = "$entities\u0000invalid trailing data".toByteArray(Charsets.ISO_8859_1)
        val map = minimalBsp(entityBytes, withTexture = true)
        assertEquals(entities, map.entityString)
        assertEquals("worldspawn", map.entities.single()["classname"])
        assertEquals("texture", map.textures.single().name)
        assertEquals(-1, map.textures.single().next)
    }

    @Test
    fun bspEntityLumpWithoutNullKeepsItsLastCharacter() {
        val entities = "{\"classname\" \"worldspawn\"}"
        val map = minimalBsp(entities.toByteArray(Charsets.ISO_8859_1))
        assertEquals(entities, map.entityString)
        assertEquals("worldspawn", map.entities.single()["classname"])
    }

    private fun minimalBsp(entityBytes: ByteArray, withTexture: Boolean = false): Bsp {
        val headerSize = 8 + Defines.HEADER_LUMPS * 8
        val buffer = ByteBuffer.allocate(headerSize + entityBytes.size + if (withTexture) 76 else 0)
            .order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(IDBSPHEADER).putInt(38)
        repeat(Defines.HEADER_LUMPS) { lump ->
            when {
                lump == Defines.LUMP_ENTITIES -> buffer.putInt(headerSize).putInt(entityBytes.size)
                lump == Defines.LUMP_TEXINFO && withTexture ->
                    buffer.putInt(headerSize + entityBytes.size).putInt(76)
                else -> buffer.putInt(headerSize).putInt(0)
            }
        }
        buffer.put(entityBytes)
        if (withTexture) {
            val textureStart = buffer.position()
            repeat(10) { buffer.putInt(0) } // axes, offsets, flags, value
            putName(buffer, "texture", 32)
            buffer.putInt(-1)
            val legacy = texinfo_t(buffer.array(), textureStart, 76)
            assertEquals("texture", legacy.texture)
            assertEquals(-1, legacy.nexttexinfo)
        }
        buffer.rewind()
        return Bsp(buffer)
    }

    private fun putName(buffer: ByteBuffer, name: String, width: Int) {
        val bytes = ByteArray(width) { 'X'.code.toByte() }
        val text = name.toByteArray(Charsets.ISO_8859_1)
        text.copyInto(bytes)
        bytes[text.size] = 0
        buffer.put(bytes)
    }
}
