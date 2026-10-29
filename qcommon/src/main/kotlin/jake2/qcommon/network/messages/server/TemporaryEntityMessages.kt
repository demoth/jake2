package jake2.qcommon.network.messages.server

import jake2.qcommon.Defines.*
import jake2.qcommon.math.Vector3f
import jake2.qcommon.network.protocol.DirectionCodec
import java.util.Collections

/** Temp entity: a subtype selects one of six payload layouts. */
sealed class TEMessage : ServerMessage(ServerMessageType.svc_temp_entity) {
    abstract val style: Int
}

private fun styles(vararg values: Int): Set<Int> = Collections.unmodifiableSet(values.toSet())

/**
 * Position-only effects: TE_EXPLOSION2, TE_GRENADE_EXPLOSION, TE_GRENADE_EXPLOSION_WATER,
 * TE_PLASMA_EXPLOSION, TE_EXPLOSION1, TE_EXPLOSION1_BIG, TE_ROCKET_EXPLOSION,
 * TE_ROCKET_EXPLOSION_WATER, TE_EXPLOSION1_NP, TE_BFG_EXPLOSION, TE_BFG_BIGEXPLOSION,
 * TE_BOSSTPORT, TE_PLAIN_EXPLOSION, TE_CHAINFIST_SMOKE, TE_TRACKER_EXPLOSION,
 * TE_TELEPORT_EFFECT, TE_DBALL_GOAL, TE_WIDOWSPLASH.
 */
data class PointTEMessage(override val style: Int, val position: Vector3f) : TEMessage() {
    init { require(style in SUBTYPES) { "Wrong point effect style: $style" } }
    constructor(style: Int, position: FloatArray) : this(style, Vector3f(position))

    companion object {
        @JvmField val SUBTYPES = styles(TE_EXPLOSION2, TE_GRENADE_EXPLOSION, TE_GRENADE_EXPLOSION_WATER,
            TE_PLASMA_EXPLOSION, TE_EXPLOSION1, TE_EXPLOSION1_BIG, TE_ROCKET_EXPLOSION,
            TE_ROCKET_EXPLOSION_WATER, TE_EXPLOSION1_NP, TE_BFG_EXPLOSION, TE_BFG_BIGEXPLOSION,
            TE_BOSSTPORT, TE_PLAIN_EXPLOSION, TE_CHAINFIST_SMOKE, TE_TRACKER_EXPLOSION,
            TE_TELEPORT_EFFECT, TE_DBALL_GOAL, TE_WIDOWSPLASH)
    }
}

/** TE_PARASITE_ATTACK and TE_MEDIC_CABLE_ATTACK carry an owner and two endpoints. */
data class BeamTEMessage(override val style: Int, val ownerIndex: Int, val origin: Vector3f, val destination: Vector3f) : TEMessage() {
    init { require(style in SUBTYPES) { "Wrong beam effect style: $style" } }
    constructor(style: Int, ownerIndex: Int, origin: FloatArray, destination: FloatArray) :
        this(style, ownerIndex, Vector3f(origin), Vector3f(destination))
    companion object { @JvmField val SUBTYPES = styles(TE_PARASITE_ATTACK, TE_MEDIC_CABLE_ATTACK) }
}

data class BeamOffsetTEMessage(override val style: Int, val ownerIndex: Int, val origin: Vector3f,
                             val destination: Vector3f, val offset: Vector3f) : TEMessage() {
    init { require(style in SUBTYPES) { "Wrong offset beam effect style: $style" } }
    constructor(style: Int, ownerIndex: Int, origin: FloatArray, destination: FloatArray, offset: FloatArray) :
        this(style, ownerIndex, Vector3f(origin), Vector3f(destination), Vector3f(offset))
    companion object { @JvmField val SUBTYPES = styles(TE_GRAPPLE_CABLE) }
}

/** Direction is retained as its normal-table index, avoiding repeated lossy quantization. */
data class PointDirectionTEMessage(override val style: Int, val position: Vector3f, val directionIndex: Int) : TEMessage() {
    init {
        require(style in SUBTYPES) { "Wrong directional point effect style: $style" }
        require(directionIndex in 0 until NUMVERTEXNORMALS) { "Invalid direction index" }
    }
    val direction: Vector3f get() = DirectionCodec.vector(directionIndex)
    constructor(style: Int, position: FloatArray, direction: FloatArray?) :
        this(style, Vector3f(position), DirectionCodec.index(direction?.let(::Vector3f)))

    companion object {
        @JvmField val SUBTYPES = styles(TE_BLOOD, TE_GUNSHOT, TE_SPARKS, TE_BULLET_SPARKS,
            TE_SCREEN_SPARKS, TE_SHIELD_SPARKS, TE_SHOTGUN, TE_BLASTER, TE_GREENBLOOD,
            TE_BLASTER2, TE_FLECHETTE, TE_HEATBEAM_SPARKS, TE_HEATBEAM_STEAM,
            TE_MOREBLOOD, TE_ELECTRIC_SPARKS)
    }
}

data class TrailTEMessage(override val style: Int, val position: Vector3f, val destination: Vector3f) : TEMessage() {
    init { require(style in SUBTYPES) { "Wrong trail effect style: $style" } }
    constructor(style: Int, position: FloatArray, destination: FloatArray) : this(style, Vector3f(position), Vector3f(destination))
    companion object { @JvmField val SUBTYPES = styles(TE_BUBBLETRAIL, TE_RAILTRAIL, TE_BLUEHYPERBLASTER, TE_DEBUGTRAIL, TE_BFG_LASER) }
}

data class SplashTEMessage(override val style: Int, val count: Int, val position: Vector3f,
                         val directionIndex: Int, val param: Int) : TEMessage() {
    init {
        require(style in SUBTYPES) { "Wrong splash effect style: $style" }
        require(directionIndex in 0 until NUMVERTEXNORMALS) { "Invalid direction index" }
        require(count in 0..255 && param in 0..255) { "Splash fields exceed unsigned byte range" }
    }
    val direction: Vector3f get() = DirectionCodec.vector(directionIndex)
    constructor(style: Int, count: Int, position: FloatArray, direction: FloatArray?, param: Int) :
        this(style, count and 255, Vector3f(position), DirectionCodec.index(direction?.let(::Vector3f)), param and 255)
    companion object { @JvmField val SUBTYPES = styles(TE_SPLASH, TE_LASER_SPARKS, TE_WELDING_SPARKS, TE_TUNNEL_SPARKS) }
}
