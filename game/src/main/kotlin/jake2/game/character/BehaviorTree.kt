package jake2.game.character


enum class BtNodeState {
    Success,
    Failure,
    Running
}


interface BehaviorTree {
    fun run(): BtNodeState
}
abstract class BhAbstractNode(protected val nodes: List<BhAbstractNode>): BehaviorTree {
    abstract override fun run(): BtNodeState
}

/**
 * Executes all nodes until a failure, then returns failure. Returns success otherwise
 */
class BhSequence(vararg nodes: BhAbstractNode) : BhAbstractNode(nodes.asList()) {
    override fun run(): BtNodeState {
        nodes.forEach {
            var result = it.run()
            while (result == BtNodeState.Running) {
                result = it.run()
            }
            if (result == BtNodeState.Failure) return BtNodeState.Failure
        }
        return BtNodeState.Success
    }
}

/**
 * Executes all nodes until a success, then returns success. Returns failure otherwise
 */
class BhSelector(vararg nodes: BhAbstractNode) : BhAbstractNode(nodes.asList()) {
    override fun run(): BtNodeState {
        nodes.forEach {
            var result = it.run()
            while (result == BtNodeState.Running) {
                result = it.run()
            }
            if (result == BtNodeState.Success) return BtNodeState.Success
        }
        return BtNodeState.Failure
    }
}

/**
 * Leaf node, Can be condition or action (side effect)
 */
class BtNode(private val condition: () -> Boolean) : BhAbstractNode(emptyList()) {
    override fun run(): BtNodeState {
        return if (condition.invoke())
            BtNodeState.Success
        else
            BtNodeState.Failure
    }
}

class BtEventNode(private val event: String, private val condition: () -> Boolean): BhAbstractNode(emptyList()) {
    val events: Set<String> = TODO()

    override fun run(): BtNodeState {
        return if (!events.contains(event))
            BtNodeState.Running
        else if (condition.invoke())
            BtNodeState.Success
        else
            BtNodeState.Failure
    }
}

// short names (come up with better names?)
fun selector(vararg nodes: BhAbstractNode) = BhSelector(*nodes)

fun sequence(vararg nodes: BhAbstractNode) = BhSequence(*nodes)

fun check(condition: () -> Boolean) = BtNode(condition)

fun run(condition: () -> Unit) = BtNode { condition.invoke(); true }

fun runUntil(event: String, condition: () -> Unit) = BtEventNode(event) {
    condition.invoke(); true
}