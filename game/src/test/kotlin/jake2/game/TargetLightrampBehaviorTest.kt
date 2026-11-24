package jake2.game

import jake2.qcommon.Defines
import jake2.qcommon.GameImports
import jake2.qcommon.vfs.EngineWriteRoot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class TargetLightrampBehaviorTest {
    private val writes = mutableListOf<Pair<Int, String?>>()
    private val imports = object : GameImports by TestGameImports() {
        override fun configstring(num: Int, string: String?) {
            if (num == Defines.CS_LIGHTS + 33) writes.add(game.level.framenum to string)
        }
    }
    private val scenario = EntityScenario(imports)
    private val game: GameExportsImpl = scenario.game

    private fun spawnRamp(toggle: Boolean = false): GameEntity {
        scenario.spawn("light") {
            targetname = "t149"
            style = 33
        }
        val ramp = scenario.spawn("target_lightramp") {
            targetname = "t2"
            target = "t149"
            message = "az"
            speed = 2f
            spawnflags = if (toggle) 1 else 0
        }
        writes.clear()
        return ramp
    }

    private fun fireAndObserve(ramp: GameEntity, styles: String) {
        val start = game.level.framenum
        writes.clear()
        scenario.fire(ramp)
        repeat(25) { game.G_RunFrame() }
        assertEquals(styles.mapIndexed { frame, style -> start + frame to style.toString() }, writes)
    }

    @Test
    fun immediateActivation() {
        fireAndObserve(spawnRamp(), forward)
    }

    @Test
    fun delayedActivation() {
        val ramp = spawnRamp()
        repeat(30) { game.G_RunFrame() }
        fireAndObserve(ramp, forward)
    }

    @Test
    fun toggleReversesOnNextUse() {
        val ramp = spawnRamp(toggle = true)
        fireAndObserve(ramp, forward)
        fireAndObserve(ramp, reverse)
    }

    @Test
    fun midRampLevelLoadResumesAtTheNextFrame(@TempDir directory: Path) {
        val ramp = spawnRamp()
        scenario.fire(ramp)
        repeat(10) { game.G_RunFrame() }
        val originalRoot = EngineWriteRoot.path()
        try {
            EngineWriteRoot.setRoot(directory)
            game.WriteLevel("ramp.sav.json")
            writes.clear()
            repeat(15) { game.G_RunFrame() }
            val uninterrupted = writes.toList()
            assertEquals(20 to "z", uninterrupted.last())
            game.ReadLevel("ramp.sav.json")
            writes.clear()
            repeat(15) { game.G_RunFrame() }
            assertEquals(uninterrupted, writes)
        } finally {
            EngineWriteRoot.setRoot(originalRoot)
        }
    }

    private companion object {
        // Yamagi's two-second ramp spans 20 game frames and truncates each style to a letter.
        val forward = (0..20).map { 'a' + (25 * it / 20) }.joinToString("")
        val reverse = (0..20).map { 'a' + (25 * (20 - it) / 20) }.joinToString("")
    }
}
