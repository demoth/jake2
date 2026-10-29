package jake2.qcommon.network.messages.server

import jake2.qcommon.Defines.*
import jake2.qcommon.entity_state_t
import jake2.qcommon.math.Vector3f

/** Information conveyed by an entity update that the client needs to render an entity. Owns all vector values. */
data class EntityStateSnapshot(
    /** Edict index; identity is explicit and must be retained when copying into engine storage. */
    val index: Int = 0,
    val origin: Vector3f = Vector3f.zero,
    val angles: Vector3f = Vector3f.zero,
    /** Previous origin for lerping. */
    val oldOrigin: Vector3f = Vector3f.zero,
    val modelIndex: Int = 0,
    /** Additional models such as weapons and CTF flags. */
    val modelIndex2: Int = 0,
    val modelIndex3: Int = 0,
    val modelIndex4: Int = 0,
    val frame: Int = 0,
    val skinNumber: Int = 0,
    /** Effects use a 32-bit bit pattern even though engine storage is a signed Int. */
    val effects: Int = 0,
    val renderEffects: Int = 0,
    /** Prediction bounds: 8*(bits 0..4) is x/y radius, 8*(bits 5..9) is z down, 8*(bits 10..15) is z up; linkentity sets this. */
    val solid: Int = 0,
    /** Looping sound index, transmitted to guarantee shutoff. */
    val sound: Int = 0,
    /** Impulse events (muzzle flashes, footsteps, etc.) last one frame and are automatically cleared each frame. */
    val event: Int = 0,
) {
    init { require(index in 0 until MAX_EDICTS) { "Invalid entity index $index" } }

    fun copyTo(target: entity_state_t) {
        target.index = index
        copy(origin, target.origin); copy(angles, target.angles); copy(oldOrigin, target.old_origin)
        target.modelindex = modelIndex; target.modelindex2 = modelIndex2
        target.modelindex3 = modelIndex3; target.modelindex4 = modelIndex4
        target.frame = frame; target.skinnum = skinNumber; target.effects = effects; target.renderfx = renderEffects
        target.solid = solid; target.sound = sound; target.event = event
    }

    private fun copy(value: Vector3f, target: FloatArray) { target[0] = value.x; target[1] = value.y; target[2] = value.z }

    companion object {
        @JvmStatic fun fromEngine(state: entity_state_t): EntityStateSnapshot = EntityStateSnapshot(
            state.index, Vector3f(state.origin), Vector3f(state.angles), Vector3f(state.old_origin),
            state.modelindex, state.modelindex2, state.modelindex3, state.modelindex4,
            state.frame, state.skinnum, state.effects, state.renderfx, state.solid, state.sound, state.event,
        )
    }
}

/** Field presence and widths are part of the wire value; unselected fields are zero in planned and received states. */
data class EntityDelta(val flags: Int, val state: EntityStateSnapshot) {
    init { require(state.index < 256 || flags and U_NUMBER16 != 0) { "Entity number requires a 16-bit header" } }

    /** Copies selected fields into a reconstructed engine state, controlled by the serialization flags. */
    fun applyTo(target: entity_state_t) {
        target.index = state.index
        if (flags and U_ORIGIN1 != 0) target.origin[0] = state.origin.x
        if (flags and U_ORIGIN2 != 0) target.origin[1] = state.origin.y
        if (flags and U_ORIGIN3 != 0) target.origin[2] = state.origin.z
        if (flags and U_ANGLE1 != 0) target.angles[0] = state.angles.x
        if (flags and U_ANGLE2 != 0) target.angles[1] = state.angles.y
        if (flags and U_ANGLE3 != 0) target.angles[2] = state.angles.z
        if (flags and U_MODEL != 0) target.modelindex = state.modelIndex
        if (flags and U_MODEL2 != 0) target.modelindex2 = state.modelIndex2
        if (flags and U_MODEL3 != 0) target.modelindex3 = state.modelIndex3
        if (flags and U_MODEL4 != 0) target.modelindex4 = state.modelIndex4
        if (flags and (U_FRAME8 or U_FRAME16) != 0) target.frame = state.frame
        if (flags and (U_SKIN8 or U_SKIN16) != 0) target.skinnum = state.skinNumber
        if (flags and (U_EFFECTS8 or U_EFFECTS16) != 0) target.effects = state.effects
        if (flags and (U_RENDERFX8 or U_RENDERFX16) != 0) target.renderfx = state.renderEffects
        if (flags and U_SOLID != 0) target.solid = state.solid
        if (flags and U_SOUND != 0) target.sound = state.sound
        if (flags and U_EVENT != 0) target.event = state.event
        if (flags and U_OLDORIGIN != 0) {
            target.old_origin[0] = state.oldOrigin.x
            target.old_origin[1] = state.oldOrigin.y
            target.old_origin[2] = state.oldOrigin.z
        }
    }
}
