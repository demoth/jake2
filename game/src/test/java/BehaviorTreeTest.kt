import jake2.game.character.BhSelector
import jake2.game.character.BhSequence
import jake2.game.character.BtNode
import jake2.game.character.BtNodeState
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

        assertTrue(result == BtNodeState.Success)
    }

    @Test
    fun testBhSequenceFailure() {
        val sequence = BhSequence(
            BtNode { true },
            BtNode { false }
        )

        val result = sequence.run()

        assertTrue(result == BtNodeState.Failure)
    }

    @Test
    fun testBhSelectorSuccess() {
        val selector = BhSelector(
            BtNode { false },
            BtNode { true }
        )

        val result = selector.run()

        assertTrue(result == BtNodeState.Success)
    }

    @Test
    fun testBhSelectorFailure() {
        val selector = BhSelector(
            BtNode { false },
            BtNode { false }
        )

        val result = selector.run()

        assertTrue(result == BtNodeState.Failure)
    }
}
