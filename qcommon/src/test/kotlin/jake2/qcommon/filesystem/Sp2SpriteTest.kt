package jake2.qcommon.filesystem

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Sp2SpriteTest {
    @Test
    fun testLoadSpite() {
        val fileName = "sprites/plasmarifle/s_pls1.sp2"
        val bytes: ByteArray = this::class.java.getResourceAsStream(fileName)?.readAllBytes()
            ?: throw AssertionError("Could not load $fileName")
        val buffer = ByteBuffer.wrap(bytes)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        val sprite = qfiles.Sp2Sprite(buffer, fileName)
        assertEquals(qfiles.Sp2Sprite.SPRITE_VERSION, sprite.version)
        assertEquals(qfiles.Sp2Sprite.IDSPRITEHEADER, sprite.ident)
        assertEquals(2, sprite.frames.size)
        val firstFrame = sprite.frames[0]
        assertEquals(16, firstFrame.width)
        assertEquals(16, firstFrame.height)
        assertEquals(8, firstFrame.origin_x)
        assertEquals(8, firstFrame.origin_y)
        assertEquals("sprites/s_pls1_0.pcx", firstFrame.imageFileName)

        assertEquals("sprites/s_pls1_1.pcx", sprite.frames[1].imageFileName)
    }
}
