package jake2.game.character


sealed class BtNodeResult

/**
 * Represents a logical outcome of an internal behaviour tree node. Used to control the execution flow (see sequence & selector).
 */
internal class BtFinalResult(val success: Boolean) : BtNodeResult()

/**
 * Represents a decision that the behavior tree has made - an action that can be executed by the character.
 * Usually the last node of a sequence (leaf).
 */
abstract class BtTask<T>(val action: T): BtNodeResult()
class BtActionTask<T>(action: T, var duration: Int = 1): BtTask<T>(action)

class BtWaitingTask<T>(action: T, val waitingFor: String): BtTask<T>(action)

class BtContext(
    private val tree: BehaviorTree
) {
    private var currentTask: BtNodeResult? = null

    fun reset() {
        currentTask = null
    }

    fun update(events: Collection<String>): BtNodeResult? {
        println("Bt events: $events")
        val task = currentTask
        currentTask = if (task != null) {
            when (task) {
                is BtActionTask<*> -> {
                    if (task.duration > 0) {
                        task.duration--
                        task
                    } else {
                        tree.run()
                    }
                }
                is BtWaitingTask<*> -> {
                    if (task.waitingFor in events) {
                        tree.run()
                    } else {
                        task
                    }
                }
                // fixme: these outcomes are errors
                is BtFinalResult -> null
                is BtTask<*> -> null
            }
        } else {
            tree.run()
        }
        return currentTask
    }
}


interface BehaviorTree {
    fun run(): BtNodeResult
}
abstract class BhAbstractNode(protected val nodes: List<BhAbstractNode>): BehaviorTree {
    abstract override fun run(): BtNodeResult
}

/**
 * Executes all nodes until a failure or a task outcome, then returns. Returns success otherwise.
 * Used to represent a series of nodes related to a certain task.
 * Usually consists of checks returning a [BtFinalResult] and a terminal node with a [BtActionTask] decision.
 */
class BhSequence(vararg nodes: BhAbstractNode) : BhAbstractNode(nodes.asList()) {
    override fun run(): BtNodeResult {
        nodes.forEach {
            when (val result = it.run()) {
                is BtFinalResult -> if (!result.success) return result
                is BtTask<*> -> return result
            }
        }
        return BtFinalResult(true)
    }
}

/**
 * Executes all nodes until a success or a task outcome, then returns. Returns failure otherwise.
 * Usually aggregates a group of sequences and defines priorities between them.
 */
class BhSelector(vararg nodes: BhAbstractNode) : BhAbstractNode(nodes.asList()) {
    override fun run(): BtNodeResult {
        nodes.forEach {
            when (val result = it.run()) {
                is BtFinalResult -> if (result.success) return result
                is BtTask<*> -> return result
            }
        }
        return BtFinalResult(false)
    }
}

class BtConditionNode(private val condition: () -> Boolean) : BhAbstractNode(emptyList()) {
    override fun run(): BtNodeResult = BtFinalResult(condition.invoke())
}

class BtLeafNode(private val action: () -> BtTask<*>) : BhAbstractNode(emptyList()) {
    override fun run(): BtNodeResult = action.invoke()
}

// short names (come up with better names?)
fun selector(vararg nodes: BhAbstractNode) = BhSelector(*nodes)

fun sequence(vararg nodes: BhAbstractNode) = BhSequence(*nodes)

fun check(condition: () -> Boolean) = BtConditionNode(condition)

fun run(action: () -> BtTask<*>) = BtLeafNode(action)