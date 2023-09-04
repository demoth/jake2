import org.demoth.jake2.game.character.BhSelector
import org.demoth.jake2.game.character.BhSequence
import org.demoth.jake2.game.character.BtNode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BehaviorTreeTest {
    @Test
    fun testBhSequenceSuccess() {
        val sequence = BhSequence(
            BtNode { true },
            BtNode { true }
        )

        val result = sequence.run()

        assertTrue(result)
    }

    @Test
    fun testBhSequenceFailure() {
        val sequence = BhSequence(
            BtNode { true },
            BtNode { false }
        )

        val result = sequence.run()

        assertFalse(result)
    }

    @Test
    fun testBhSelectorSuccess() {
        val selector = BhSelector(
            BtNode { false },
            BtNode { true }
        )

        val result = selector.run()

        assertTrue(result)
    }

    @Test
    fun testBhSelectorFailure() {
        val selector = BhSelector(
            BtNode { false },
            BtNode { false }
        )

        val result = selector.run()

        assertFalse(result)
    }
}
