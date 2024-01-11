package jake2.game.character

import jake2.game.*
import jake2.game.adapters.SuperAdapter.Companion.registerThink
import jake2.game.components.ThinkComponent
import jake2.qcommon.Com
import jake2.qcommon.Defines
import jake2.qcommon.M_Flash
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
                events = mapOf(0 to "sound-fidget"),
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
            ),
            AnimationSequence(
                name="attack-ranged",
                type = StateType.ATTACK,
                frames = (184..194).toList(), // ready gun
                events = mapOf(
                    1 to "attack-ranged-prepare-event", // should be 0
                    3 to "sound-cock-gun"
                ),
                loop = false,
                nextState = "attack-ranged-middle"
            ),
            AnimationSequence(
                name="attack-ranged-middle",
                type = StateType.ATTACK,
                frames = listOf(194), // firing
                events = mapOf(
                    0 to "attack-ranged-fire-event", // since 0 index event is not triggered while entering the animation sequence, it is triggered after the first animation loop
                ),
                loop = true,
                nextState = "attack-ranged-finish"
            ),
            AnimationSequence(
                name="attack-ranged-finish",
                type = StateType.ATTACK, // recover
                frames = (195..198).toList(),
                events = emptyMap(),
                loop = false,
                nextState = "stand"
            ),

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
        "hit" to (game.gameImports.soundindex("infantry/melee2.wav") to Defines.CHAN_WEAPON),
        "cock-gun" to (game.gameImports.soundindex("infantry/infatck3.wav") to Defines.CHAN_WEAPON),
    )

    private var fireFrames = 0 // how many frames to fire

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
                it == "attack-ranged-prepare-event" -> {
                    //fireFrames =  Random.nextInt(15) + 10
                }
                it == "attack-ranged-fire-event" -> {
                    if (fireFrames-- < 0) {
                        fireFrames = 0
                        stateMachine.attemptStateChange("attack-ranged-finish", true) // othewise transition rules disallow ATTACK -> ATTACK states
                        println("finished firing")
                        return@forEach
                    }
                    // todo: this piece is awful
                    val start = floatArrayOf(0f, 0f, 0f)
                    val target = floatArrayOf(0f, 0f, 0f)
                    val forward = floatArrayOf(0f, 0f, 0f)
                    val right = floatArrayOf(0f, 0f, 0f)
                    val vec = floatArrayOf(0f, 0f, 0f)
                    val flash_number = Defines.MZ2_INFANTRY_MACHINEGUN_1

                    Math3D.AngleVectors(self.s.angles, forward, right, null)
                    Math3D.G_ProjectSource(
                        self.s.origin,
                        M_Flash.monster_flash_offset[flash_number], forward,
                        right, start
                    )
                    if (self.enemy != null) {
                        Math3D.VectorMA(
                            self.enemy.s.origin, -0.2f,
                            self.enemy.velocity, target
                        )
                        target[2] += self.enemy.viewheight.toFloat()
                        Math3D.VectorSubtract(target, start, forward)
                        Math3D.VectorNormalize(forward)
                    } else {
                        Math3D.AngleVectors(self.s.angles, forward, right, null)
                    }

                    Monster.monster_fire_bullet(
                        self, start, forward, 3, 4,
                        GameDefines.DEFAULT_BULLET_HSPREAD,
                        GameDefines.DEFAULT_BULLET_VSPREAD, flash_number, game
                    )
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
            StateType.ATTACK -> to == StateType.DEAD || to == StateType.PAIN // automatically transitions to IDLE // fixme: cannot intentionally exit attack state earlier
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

    fun attackRanged(framesToAttack: Int) {
        // to avoid resetting the attack cycle
        if (stateMachine.currentState.type != StateType.ATTACK) {
            fireFrames = framesToAttack
            stateMachine.attemptStateChange("attack-ranged")
        }
    }

    fun reactToDamage(damage: Int) {
        self.health -= damage
        if (self.health < 50) // injured skin threshold
            self.s.skinnum = 1
        if (self.health > 0)
            stateMachine.attemptStateChange("pain")
        else
            stateMachine.attemptStateChange("dead")
    }

    private fun notDisabled() =
        stateMachine.currentState.type != StateType.PAIN && stateMachine.currentState.type != StateType.DEAD

    // todo: refactor to SoundEvent
    private fun sound(soundIndex: Int,
                      channel: Int,
                      volume: Float = 1f,
                      attenuation: Float = Defines.ATTN_IDLE.toFloat(),
                      timeOffset: Float = 0f) =
        game.gameImports.sound(self, channel, soundIndex, volume, attenuation, timeOffset)


}

fun spawnNewMonster(self: SubgameEntity, game: GameExportsImpl) {
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

    /*
        1. if triggered & has combat point -> go to combat point
        2. if triggered & see enemy & fired at -> duck
        3. if triggered & see enemy -> attack enemy
        4. if triggered & !see enemy -> chase
        5. if !triggered & has path-target -> goto path-target
        6. else: Idle
     */
    self.controller = selector(
        // triggered (attacked or activated from another entity)
        sequence(
            // should hunt the enemy?
            check { self.enemy != null },
            // rotate towards the enemy
            run {
                val distance = floatArrayOf(0f, 0f, 0f)
                Math3D.VectorSubtract(self.enemy.s.origin, self.s.origin, distance)
                val enemyYaw = Math3D.vectoyaw(distance)

                self.character.aim(enemyYaw)
            },
            // attack or chase
            selector(
                sequence(
                    check { SV.SV_CloseEnough(self, self.enemy, 16f) }, // todo: see jake2.game.GameUtil.range
                    run { self.character.attackMelee() }
                ),
                sequence(
                    check { SV.SV_CloseEnough(self, self.enemy, 32f) },
                    runUntil("attack-finished") {
                        val framesToAttack = Random.nextInt(15) + 10
                        self.character.attackRanged(framesToAttack)
                    }
                ),
                sequence(
                    check { SV.SV_CloseEnough(self, self.enemy, 100f) },
                    run { self.character.walk() }
                ),
                run { self.character.run() }
            ),
        ),
        sequence(
            run { self.character.idle() }
        )
    )

    self.think = ThinkComponent().apply {
        nextTime = game.level.time + Defines.FRAMETIME
        // fixme: register think should be called before level loading (due to de/serialization)
        action = registerThink("new_monster_think") { self, game ->
            // YES! new good stuff
            self.controller.run() // todo: don't need to run the controller every frame. Sometimes can be paused
            self.character.update(Defines.FRAMETIME)

            // leftovers
            self.s.frame = self.character.currentFrame
            self.think.nextTime = game.level.time + Defines.FRAMETIME
            true
        }
    }

    game.gameImports.linkentity(self)

}