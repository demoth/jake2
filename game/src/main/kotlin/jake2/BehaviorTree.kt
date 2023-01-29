package org.demoth

abstract class BhAbstractNode(protected val nodes: List<BhAbstractNode>) {
    abstract fun run(): Boolean
}

/**
 * executes all nodes until a failure, then returns failure. Returns success otherwise
 */
class BhSequence(vararg nodes: BhAbstractNode) : BhAbstractNode(nodes.asList()) {
    override fun run(): Boolean {
        nodes.forEach { if (!it.run()) return false }
        return true
    }
}
/**
 * executes all nodes until a success, then returns success. Returns failure otherwise
 */

class BhSelector(vararg nodes: BhAbstractNode) : BhAbstractNode(nodes.asList()) {
    override fun run(): Boolean {
        nodes.forEach { if (it.run()) return true }
        return false
    }
}

/**
 * Can be condition or action (side effect)
 */
class BtNode(private val condition: () -> Boolean) : BhAbstractNode(emptyList()) {
    override fun run(): Boolean {
        return condition.invoke()
    }
}

fun main() {
    // example of usage:
    val controller: Map<String, () -> Boolean> = hashMapOf()
    val blackboard = hashMapOf("health" to 100, "enemy-found" to 0)

    val simpleMonster = BhSelector(
        BhSequence(
            // check if we can attack
            BtNode { blackboard["enemy-found"] != 0 },
            BtNode { controller["attack"]!!.invoke() }
        ),
        BhSequence(
            // heal
            BtNode { blackboard["health"]!! < 25 },
            BtNode { controller["find-medkit"]!!.invoke() }

        ),
        BhSequence(
            // try to find a target
            BtNode { controller["search-target"]!!.invoke() }
        )
    )
    simpleMonster.run()
}
