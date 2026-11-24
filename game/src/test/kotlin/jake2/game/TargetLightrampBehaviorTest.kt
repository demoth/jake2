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
    private val game: GameExportsImpl = GameExportsImpl(imports)

    private fun spawnRamp(toggle: Boolean = false): GameEntity {
        val light = game.G_Spawn().apply {
            classname = "light"
            targetname = "t149"
            style = 33
        }
        GameSpawn.ED_CallSpawn(light, game)
        val ramp = game.G_Spawn().apply {
            classname = "target_lightramp"
            targetname = "t2"
            target = "t149"
            message = "az"
            speed = 2f
            spawnflags = if (toggle) 1 else 0
        }
        GameSpawn.ED_CallSpawn(ramp, game)
        writes.clear()
        return game.G_Spawn().apply { target = "t2" }
    }

    private fun fireAndObserve(source: GameEntity, styles: String) {
        val start = game.level.framenum
        writes.clear()
        GameUtil.G_UseTargets(source, source, game)
        repeat(25) { game.G_RunFrame() }
        assertEquals(styles.mapIndexed { frame, style -> start + frame to style.toString() }, writes)
    }

    @Test
    fun immediateActivation() {
        fireAndObserve(spawnRamp(), forward)
    }

    @Test
    fun delayedActivation() {
        val source = spawnRamp()
        repeat(30) { game.G_RunFrame() }
        fireAndObserve(source, forward)
    }

    @Test
    fun toggleReversesOnNextUse() {
        val source = spawnRamp(toggle = true)
        fireAndObserve(source, forward)
        fireAndObserve(source, reverse)
    }

    @Test
    fun midRampLevelLoadResumesAtTheNextFrame(@TempDir directory: Path) {
        val source = spawnRamp()
        GameUtil.G_UseTargets(source, source, game)
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
