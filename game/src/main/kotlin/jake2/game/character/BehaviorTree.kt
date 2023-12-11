package jake2.game.character


enum class DesicionAction {
    
}


interface BehaviorTree {
    fun run(): Boolean // todo: change to DecisionAction
}
abstract class BhAbstractNode(protected val nodes: List<BhAbstractNode>): BehaviorTree {
    abstract override fun run(): Boolean
}

/**
 * Executes all nodes until a failure, then returns failure. Returns success otherwise
 */
class BhSequence(vararg nodes: BhAbstractNode) : BhAbstractNode(nodes.asList()) {
    override fun run(): Boolean {
        nodes.forEach { if (!it.run()) return false }
        return true
    }
}

/**
 * Executes all nodes until a success, then returns success. Returns failure otherwise
 */
class BhSelector(vararg nodes: BhAbstractNode) : BhAbstractNode(nodes.asList()) {
    override fun run(): Boolean {
        nodes.forEach { if (it.run()) return true }
        return false
    }
}

/**
 * Leaf node, Can be condition or action (side effect)
 */
class BtNode(private val condition: () -> Boolean) : BhAbstractNode(emptyList()) {
    override fun run(): Boolean {
        return condition.invoke()
    }
}


// short names (come up with better names?)
fun selector(vararg nodes: BhAbstractNode) = BhSelector(*nodes)

fun sequence(vararg nodes: BhAbstractNode) = BhSequence(*nodes)

fun check(condition: () -> Boolean) = BtNode(condition)

fun run(condition: () -> Unit) = BtNode { condition.invoke(); true }
