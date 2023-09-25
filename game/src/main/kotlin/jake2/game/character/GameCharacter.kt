package jake2.game.character

import jake2.game.*
import jake2.game.adapters.SuperAdapter.Companion.registerThink
import jake2.game.components.ThinkComponent
import jake2.qcommon.Com
import jake2.qcommon.Defines
import jake2.qcommon.util.Lib
import jake2.qcommon.util.Math3D
import kotlin.random.Random


fun createSequences(name: String): Collection<AnimationSequence> {
    // Enforcer states:

    // IDLE states:
    // stand
    // fidget

    // MOVEMENT states:
    // duck
    // walk
    // run

    // PAIN states:
    // pain1
    // pain2

    // DEAD states
    // death1
    // death2
    // death3

    // ATTACK states
    // attack1
    // attack2

    // hardcoded or parsed from json file or something
    if (name == "enforcer")
        return listOf(
            AnimationSequence(
                name = "stand",
                type = StateType.IDLE,
                frames = (50..71).toList(),
                events = mapOf(1 to "try-fidget"),
                loop = true
            ),
            AnimationSequence(
                name = "fidget",
                type = StateType.IDLE,
                frames = (1..49).toList(),
                events = mapOf(1 to "sound-fidget"),
                loop = false,
                nextState = "stand"
            ),
            AnimationSequence(
                name="pain",
                type = StateType.PAIN,
                frames = (100..109).toList(),
                events = mapOf(1 to "sound-pain"),
                loop = false,
                nextState = "stand"
            ),
            AnimationSequence(
                name="dead",
                type = StateType.DEAD,
                frames = (125..144).toList(),
                events = mapOf(1 to "sound-death"),
                loop = false
            ),
            AnimationSequence(
                name="walk",
                type = StateType.MOVEMENT,
                frames = (74..85).toList(),
                events = emptyMap(),
                loop = true
            ),
            AnimationSequence(
                name="run",
                type = StateType.MOVEMENT,
                frames = (92..99).toList(),
                events = emptyMap(),
                loop = true
            ),
            AnimationSequence(
                name="attack-melee",
                type = StateType.ATTACK,
                frames = (199..206).toList(),
                events = mapOf(3 to "sound-swing", 6 to "attack-melee-event"),
                loop = false,
                nextState = "stand"
            )
        )
    TODO("Not yet implemented")
}

class GameCharacter(
    private val self: SubgameEntity,
    private val game: GameExportsImpl,
    name: String,
//    private var soundPlayer: (soundName: String) -> Unit
) : AnimationEventProcessor, StateTransitionRules {
    // fixme: come up with a better resource precache approach

    private val sounds: Map<String, Pair<Int, Int>> = mapOf(
        "fidget" to (game.gameImports.soundindex("infantry/infidle1.wav") to Defines.CHAN_VOICE),
        "pain" to (game.gameImports.soundindex("infantry/infpain1.wav") to Defines.CHAN_VOICE),
        "death" to (game.gameImports.soundindex("infantry/infdeth1.wav") to Defines.CHAN_VOICE),
        "swing" to (game.gameImports.soundindex("infantry/infatck2.wav") to Defines.CHAN_WEAPON),
        "hit" to (game.gameImports.soundindex("infantry/melee2.wav") to Defines.CHAN_WEAPON)
    )

    var health = 100f
    val currentFrame: Int
        get() = stateMachine.currentState.currentFrame


    // other properties follow

    private val stateMachine = StateMachine(createSequences(name).map {
        // other possible states?
        AnimationSequenceState(it.name, it, this, it.nextState, it.type)
    }, this)

    override fun process(events: Collection<String>) {
        events.forEach {
            println("processing event: $it")

            when {
                it == "try-fidget" -> {
                    if (Random.nextFloat() < 0.2f) {
                        stateMachine.attemptStateChange("fidget")
                    }
                }
                it == "attack-melee-event" -> {
                    val aim = floatArrayOf(GameDefines.MELEE_DISTANCE.toFloat(), 0f, 0f)
                    if (GameWeapon.fire_hit(self, aim, 5 + Lib.rand() % 5, 50, game)) { // fixme: assumes self.enemy is set
                        val soundIndex = sounds["hit"]!!
                        sound(soundIndex.first, soundIndex.second)
                    }
                }
                it.startsWith("sound-") -> {
                    val soundIndex = sounds[it.replace("sound-", "")]
                    if (soundIndex != null)
                        sound(soundIndex.first, soundIndex.second)
                    else
                        Com.Error(1, "sound $it not found or not precached")
                }
                else -> {
                    println("unexpected event: $it")
                }
            }
        }
    }

    // ai wants to walk, currently stunned
    // ai wants to walk, currently idle
    override fun transitionAllowed(from: StateType, to: StateType): Boolean {
        return when (from) {
            StateType.DEAD -> false
            StateType.PAIN -> to == StateType.DEAD // automatically transitions to IDLE
            StateType.ATTACK -> to == StateType.DEAD || to == StateType.PAIN // automatically transitions to IDLE
            StateType.IDLE, StateType.MOVEMENT -> true
        }
    }

    fun update(time: Float) = stateMachine.update(time)

    //
    // these commands are called either by AI or a Player.
    // could be called continuously
    //
    // todo: all these actions should check if character is not dead or somehow disabled
    // usually it's verified by the state machine, but when it's not used (like in the aim() method) - should be checked explicitly

    fun aim(enemyYaw: Float) {
        if (notDisabled()) {
            self.ideal_yaw = enemyYaw
            M.rotateToIdealYaw(self)
        }
    }


    fun walk() {
        if (stateMachine.attemptStateChange("walk")) {
            M.M_walkmove(self, self.ideal_yaw, 5f, game)
        }
    }

    fun run() {
        if (stateMachine.attemptStateChange("run")) {
            M.M_walkmove(self, self.ideal_yaw, 15f, game)
        }
    }

    fun idle() {
        if (stateMachine.currentState.type != StateType.IDLE) // not to interrupt the fidget animation
            stateMachine.attemptStateChange("stand")
    }

    fun jump() {
        if (stateMachine.attemptStateChange("jump")) {
            // GameLogic.tossCharacter(...)
            // transitions to "stand" once hit the ground // todo: where is this code? in the M_CheckGround?
        }
    }

    fun attackMelee() {
        stateMachine.attemptStateChange("attack-melee")
    }

    fun reactToDamage(damage: Int) {
        health -= damage
        if (health < 50) // injured skin threshold
            self.s.skinnum = 1
        if (health > 0)
            stateMachine.attemptStateChange("pain")
        else
            stateMachine.attemptStateChange("dead")
    }

    private fun notDisabled() =
        stateMachine.currentState.type != StateType.PAIN && stateMachine.currentState.type != StateType.DEAD

    private fun sound(soundIndex: Int,
                      channel: Int,
                      volume: Float = 1f,
                      attenuation: Float = Defines.ATTN_IDLE.toFloat(),
                      timeOffset: Float = 0f) =
        game.gameImports.sound(self, channel, soundIndex, volume, attenuation, timeOffset)


}

fun spawnNewMonster(self: SubgameEntity, game: GameExportsImpl) {

    val entity = game.G_Spawn()
    self.movetype = GameDefines.MOVETYPE_STEP
    self.solid = Defines.SOLID_BBOX
    self.s.modelindex = game.gameImports.modelindex("models/monsters/infantry/tris.md2")
    self.svflags = self.svflags or Defines.SVF_MONSTER
    self.s.renderfx = self.s.renderfx or Defines.RF_FRAMELERP
    self.clipmask = Defines.MASK_MONSTERSOLID
    self.s.skinnum = 0 // healthy
    self.deadflag = GameDefines.DEAD_NO
    self.svflags = self.svflags and Defines.SVF_DEADMONSTER.inv()
    self.takedamage = Defines.DAMAGE_YES
    self.health = 100
    self.yaw_speed = 40f

    Math3D.VectorSet(self.mins, -16f, -16f, -24f)
    Math3D.VectorSet(self.maxs, 16f, 16f, 32f)

    self.character = GameCharacter(self, game, "enforcer") // new stuff!!

    self.controller = selector(
        sequence(
            // should hunt the enemy?
            node { self.enemy != null },
            // rotate towards the enemy
            finish {
                val distance = floatArrayOf(0f, 0f, 0f)
                Math3D.VectorSubtract(self.enemy.s.origin, self.s.origin, distance)
                val enemyYaw = Math3D.vectoyaw(distance)

                self.character.aim(enemyYaw)
            },
            // move towards the enemy
            finish {
                // todo: see jake2.game.GameUtil.range
                if (SV.SV_CloseEnough(self, self.enemy, 16f)) {
                    self.character.attackMelee()
                } else if (SV.SV_CloseEnough(self, self.enemy, 100f)) {
                    self.character.walk()
                } else {
                    self.character.run()
                }
            }
        ),
        sequence(
            finish { self.character.idle() }
        )
    )

    self.think = ThinkComponent().apply {
        nextTime = game.level.time + Defines.FRAMETIME
        // fixme: register think should be called before level loading (due to de/serialization)
        action = registerThink("new_monster_think") { self, game ->
            // YES! new good stuff
            self.controller.run()
            self.character.update(Defines.FRAMETIME)

            // leftovers
            self.s.frame = self.character.currentFrame
            self.think.nextTime = game.level.time + Defines.FRAMETIME
            true
        }
    }

    game.gameImports.linkentity(entity)

}