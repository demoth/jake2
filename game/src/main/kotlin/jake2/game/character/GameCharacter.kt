package jake2.game.character

import jake2.game.GameDefines
import jake2.game.GameExportsImpl
import jake2.game.SubgameEntity
import jake2.game.adapters.SuperAdapter.Companion.registerThink
import jake2.game.components.ThinkComponent
import jake2.qcommon.Defines
import jake2.qcommon.math.Vector3f
import jake2.qcommon.util.Math3D
import kotlin.random.Random


fun createSequences(name: String): Collection<AnimationSequence> {
    // Enforcer states:
    //
    // stand
    // fidget
    // walk
    // run
    // pain1
    // pain2
    // duck
    // death1
    // death2
    // death3
    // attack1
    // attack2

    // hardcoded or parsed from json file or something
    if (name == "enforcer")
        return listOf(
            AnimationSequence(
                name = "stand",
                frames = (50..71).toList(),
                events = mapOf(1 to "try-fidget"),
                loop = true
            ),
            AnimationSequence(
                name = "fidget",
                frames = (1..49).toList(),
                events = mapOf(1 to "sound-fidget-event"),
                loop = false,
                nextState = "stand"
            ),
            AnimationSequence(
                name="pain",
                frames = (100..109).toList(),
                events = mapOf(1 to "sound-pain-event"),
                loop = false,
                nextState = "stand"
            ),
            AnimationSequence(
                name="dead",
                frames = (125..144).toList(),
                events = mapOf(1 to "sound-dead-event"),
                loop = false
            )
        )
    TODO("Not yet implemented")
}

class GameCharacter(
    private val self: SubgameEntity,
    private val game: GameExportsImpl,
    name: String,
//    private var soundPlayer: (soundName: String) -> Unit
) : AnimationEventProcessor {
    val soundFidget: Int
    val soundPain: Int
    val soundDead: Int

    init {
        // fixme: come up with a better resource precache approach
        soundFidget = game.gameImports.soundindex("infantry/infidle1.wav")
        soundPain = game.gameImports.soundindex("infantry/infpain1.wav")
        soundDead = game.gameImports.soundindex("infantry/infdeth1.wav")
    }

    private var health = 100f
    private var stunThreshold = 0.5f
    private var stunTime = 1f
    val currentFrame: Int
        get() = stateMachine.currentState.currentFrame


    // other properties follow

    private val stateMachine = StateMachine(
        createSequences(name).map {
            // other possible states?
            AnimationSequenceState(it.name, it, this, it.nextState)
        }
    )

    override fun process(events: Collection<String>) {
        events.forEach {
            println("processing event: $it")

            when (it) {
                "try-fidget" -> {
                    if (Random.nextFloat() < 0.2f) {
                        stateMachine.attemptStateChange("fidget")
                    }
                }
                "sound-fidget-event" -> sound(soundFidget)
                "sound-pain-event" -> sound(soundPain)
                "sound-dead-event" -> sound(soundDead)

                else -> {
                    println("unexpected event: $it")
                }
            }
        }
    }

    fun update(time: Float) = stateMachine.update(time)

    //
    // these commands are called either by AI or a Player.
    // could be called continuously
    //
    fun aim(to: Vector3f) {
        TODO()
    }

    fun walk() {
        if (stateMachine.attemptStateChange("walk")) {
            // GameLogic.moveCharacter(...)
            // fixme: if called continuously, continue the same animation sequence
        }
    }

    fun jump() {
        if (stateMachine.attemptStateChange("jump")) {
            // GameLogic.tossCharacter(...)
            // transitions to "stand" once hit the ground // todo: where is this code?
        }
    }

    fun attack() {
        stateMachine.attemptStateChange("attack")
    }

    fun reactToDamage(damage: Int) {
        health -= damage
        if (health > 0)
            stateMachine.attemptStateChange("pain")
        else
            stateMachine.attemptStateChange("dead")
    }

    private fun sound(soundIndex: Int,
                      channel: Int = Defines.CHAN_VOICE,
                      volume: Float = 1f,
                      attenuation: Float = Defines.ATTN_IDLE.toFloat(),
                      timeOffset: Float = 0f) {
        game.gameImports.sound(self, channel, soundIndex, volume, attenuation, timeOffset)
    }

}

fun spawnNewMonster(self: SubgameEntity, game: GameExportsImpl) {

    val entity = game.G_Spawn()
    self.movetype = GameDefines.MOVETYPE_STEP
    self.solid = Defines.SOLID_BBOX
    self.s.modelindex = game.gameImports.modelindex("models/monsters/infantry/tris.md2")
    self.svflags = self.svflags or Defines.SVF_MONSTER
    self.s.renderfx = self.s.renderfx or Defines.RF_FRAMELERP
    self.clipmask = Defines.MASK_MONSTERSOLID
    self.s.skinnum = 0
    self.deadflag = GameDefines.DEAD_NO
    self.svflags = self.svflags and Defines.SVF_DEADMONSTER.inv()
    self.takedamage = Defines.DAMAGE_YES
    self.health = 100

    Math3D.VectorSet(self.mins, -16f, -16f, -24f)
    Math3D.VectorSet(self.maxs, 16f, 16f, 32f)

    self.character = GameCharacter(self, game, "enforcer") // new stuff!!

    self.think = ThinkComponent().apply {
        nextTime = game.level.time + Defines.FRAMETIME
        // fixme: register think should be called before level loading (due to de/serialization)
        action = registerThink("new_monster_think") { self, game ->
            self.character.update(Defines.FRAMETIME) // YES!
            self.s.frame = self.character.currentFrame
            self.think.nextTime = game.level.time + Defines.FRAMETIME
            true
        }
    }

    game.gameImports.linkentity(entity)

}