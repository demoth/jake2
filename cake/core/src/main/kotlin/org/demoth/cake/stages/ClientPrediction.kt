package org.demoth.cake.stages

import jake2.qcommon.CM
import jake2.qcommon.Defines.CMD_BACKUP
import jake2.qcommon.Defines.CS_AIRACCEL
import jake2.qcommon.Defines.MASK_PLAYERSOLID
import jake2.qcommon.Defines.PMF_NO_PREDICTION
import jake2.qcommon.Defines.PMF_ON_GROUND
import jake2.qcommon.PMove
import jake2.qcommon.ServerEntity
import jake2.qcommon.ServerPlayerInfo
import jake2.qcommon.pmove_t
import jake2.qcommon.trace_t
import org.demoth.cake.ClientFrame
import org.demoth.cake.GameConfiguration
import org.demoth.cake.input.InputManager
import kotlin.math.abs

/**
 * Client side movement prediction.
 *
 * Replays unacknowledged user commands against server pmove state using local collision.
 *
 * Cross-reference (old client):
 * - `client/CL_pred.PredictMovement`
 * - `client/CL_pred.CheckPredictionError`
 * - `client/CL_pred.PMTrace` + `CL_pred.ClipMoveToEntities` + `CL_pred.PMpointcontents`
 */
class ClientPrediction(
    private val collisionModel: CM,
    private val entityManager: ClientEntityManager,
    private val gameConfig: GameConfiguration,
) {
    val predictedOrigin = FloatArray(3)
    val predictedAngles = FloatArray(3)
    val predictionError = FloatArray(3)
    var predictedStep = 0f
        private set
    var predictedStepTimeMs = 0
        private set

    private val predictedOrigins = Array(CMD_BACKUP) { IntArray(3) }
    private val pmoveProcessor = PMove.newLegacyProcessor()
    private val zeroAngles = floatArrayOf(0f, 0f, 0f)
    private val tempBoxMins = floatArrayOf(0f, 0f, 0f)
    private val tempBoxMaxs = floatArrayOf(0f, 0f, 0f)
    private var incomingAcknowledged = 0
    private var outgoingSequence = 0
    private var currentTimeMs = 0
    private var hasPredictedState = false

    // Quirk: PMove expects a non-null touch/ground entity on collisions, but Cake client has no
    // game-side edicts. We use a sentinel ServerEntity only to satisfy PMove's contract.
    private val dummyTraceEntity = object : ServerEntity(-1) {
        override fun getOwner(): ServerEntity = this
        override fun getClient(): ServerPlayerInfo = throw UnsupportedOperationException("dummy")
    }

    fun reset() {
        predictedOrigin.fill(0f)
        predictedAngles.fill(0f)
        predictionError.fill(0f)
        predictedStep = 0f
        predictedStepTimeMs = 0
        predictedOrigins.forEach { it.fill(0) }
        incomingAcknowledged = 0
        outgoingSequence = 0
        currentTimeMs = 0
        hasPredictedState = false
    }

    fun updateNetworkState(incomingAcknowledged: Int, outgoingSequence: Int, currentTimeMs: Int) {
        this.incomingAcknowledged = incomingAcknowledged
        this.outgoingSequence = outgoingSequence
        this.currentTimeMs = currentTimeMs
    }

    fun syncFromServerFrame(currentFrame: ClientFrame) {
        predictedOrigin[0] = currentFrame.playerstate.pmove.origin[0] * 0.125f
        predictedOrigin[1] = currentFrame.playerstate.pmove.origin[1] * 0.125f
        predictedOrigin[2] = currentFrame.playerstate.pmove.origin[2] * 0.125f
        predictedAngles[0] = currentFrame.playerstate.viewangles[0]
        predictedAngles[1] = currentFrame.playerstate.viewangles[1]
        predictedAngles[2] = currentFrame.playerstate.viewangles[2]
        hasPredictedState = true
    }

    fun onServerFrameParsed(currentFrame: ClientFrame) {
        if (!currentFrame.valid || !isPredictionEnabled(currentFrame)) {
            return
        }

        if (!hasPredictedState) {
            syncFromServerFrame(currentFrame)
        }

        val frame = incomingAcknowledged and (CMD_BACKUP - 1)
        val serverOrigin = currentFrame.playerstate.pmove.origin
        val deltaX = serverOrigin[0].toInt() - predictedOrigins[frame][0]
        val deltaY = serverOrigin[1].toInt() - predictedOrigins[frame][1]
        val deltaZ = serverOrigin[2].toInt() - predictedOrigins[frame][2]
        val deltaLen = abs(deltaX) + abs(deltaY) + abs(deltaZ)

        // `CL_pred.CheckPredictionError` treats large deltas as teleports and
        // drops smoothing correction instead of trying to blend.
        if (deltaLen > 640) {
            predictionError.fill(0f)
            return
        }

        predictedOrigins[frame][0] = serverOrigin[0].toInt()
        predictedOrigins[frame][1] = serverOrigin[1].toInt()
        predictedOrigins[frame][2] = serverOrigin[2].toInt()
        predictionError[0] = deltaX * 0.125f
        predictionError[1] = deltaY * 0.125f
        predictionError[2] = deltaZ * 0.125f
    }

    fun predictMovement(currentFrame: ClientFrame, inputManager: InputManager) {
        if (!currentFrame.valid) {
            return
        }

        if (!isPredictionEnabled(currentFrame)) {
            syncFromServerFrame(currentFrame)
            return
        }

        if (outgoingSequence <= incomingAcknowledged) {
            syncFromServerFrame(currentFrame)
            return
        }

        // `CL_pred.PredictMovement` freezes when replay window exceeds CMD_BACKUP.
        if (outgoingSequence - incomingAcknowledged >= CMD_BACKUP) {
            return
        }

        val pm = pmove_t().apply {
            trace = object : pmove_t.TraceAdapter() {
                override fun trace(start: FloatArray, mins: FloatArray, maxs: FloatArray, end: FloatArray): trace_t {
                    return pmTrace(start, mins, maxs, end)
                }
            }
            pointcontents = pmove_t.PointContentsAdapter { point ->
                pmPointContents(point)
            }
            s.set(currentFrame.playerstate.pmove)
        }

        PMove.pm_airaccelerate = gameConfig.getConfigValue(CS_AIRACCEL)?.toFloatOrNull() ?: 0f

        var ack = incomingAcknowledged
        // Key behavior: replay exactly the unacknowledged command range (ack+1 .. outgoing-1).
        // This depends on InputManager sequence-indexed history.
        while (++ack < outgoingSequence) {
            val cmd = inputManager.getCommandForSequence(ack) ?: run {
                // Quirk: if history wrapped or a command is missing, predicted state is no longer
                // trustworthy; snap to server-authoritative frame to avoid drift accumulation.
                syncFromServerFrame(currentFrame)
                return
            }
            pm.cmd.set(cmd)
            pmoveProcessor.move(pm)

            val index = ack and (CMD_BACKUP - 1)
            predictedOrigins[index][0] = pm.s.origin[0].toInt()
            predictedOrigins[index][1] = pm.s.origin[1].toInt()
            predictedOrigins[index][2] = pm.s.origin[2].toInt()
        }

        val oldFrame = (ack - 2) and (CMD_BACKUP - 1)
        val oldZ = predictedOrigins[oldFrame][2]
        val step = pm.s.origin[2].toInt() - oldZ
        // Cross-reference: old stair smoothing heuristic from `CL_pred.PredictMovement`.
        if (step > 63 && step < 160 && (pm.s.pm_flags.toInt() and PMF_ON_GROUND) != 0) {
            predictedStep = step * 0.125f
            predictedStepTimeMs = currentTimeMs
        }

        predictedOrigin[0] = pm.s.origin[0] * 0.125f
        predictedOrigin[1] = pm.s.origin[1] * 0.125f
        predictedOrigin[2] = pm.s.origin[2] * 0.125f
        predictedAngles[0] = pm.viewangles[0]
        predictedAngles[1] = pm.viewangles[1]
        predictedAngles[2] = pm.viewangles[2]
        hasPredictedState = true
    }

    fun smoothedStepOffset(currentTimeMs: Int): Float {
        val delta = currentTimeMs - predictedStepTimeMs
        return if (delta in 0..99) {
            predictedStep * (100 - delta) * 0.01f
        } else {
            0f
        }
    }

    private fun isPredictionEnabled(currentFrame: ClientFrame): Boolean {
        return (currentFrame.playerstate.pmove.pm_flags.toInt() and PMF_NO_PREDICTION) == 0
    }

    // Cross-reference: `CL_pred.PMTrace`.
    private fun pmTrace(start: FloatArray, mins: FloatArray, maxs: FloatArray, end: FloatArray): trace_t {
        val result = collisionModel.BoxTrace(start, end, mins, maxs, 0, MASK_PLAYERSOLID)
        if (result.fraction < 1f) {
            result.ent = dummyTraceEntity
        }
        clipMoveToEntities(start, mins, maxs, end, result)
        return result
    }

    private fun clipMoveToEntities(
        start: FloatArray,
        mins: FloatArray,
        maxs: FloatArray,
        end: FloatArray,
        outTrace: trace_t,
    ) {
        // Cross-reference: `CL_pred.ClipMoveToEntities`.
        entityManager.forEachCurrentEntityState { entity ->
            if (entity.solid == 0 || entity.index == entityManager.playerNumber + 1) { // solid=0 means non-solid network entity
                return@forEachCurrentEntityState
            }

            val headnode: Int
            val angles: FloatArray
            if (entity.solid == 31) { // 31 is the protocol sentinel for inline brush model collision
                val modelName = gameConfig.getModelName(entity.modelindex) ?: return@forEachCurrentEntityState
                if (!modelName.startsWith("*")) {
                    return@forEachCurrentEntityState
                }
                val cmodel = runCatching { collisionModel.InlineModel(modelName) }.getOrNull()
                    ?: return@forEachCurrentEntityState
                headnode = cmodel.headnode
                angles = entity.angles
            } else {
                val x = 8 * (entity.solid and 31) // low 5 bits (mask 31): encoded XY half-extent in 8-unit steps
                val zd = 8 * ((entity.solid ushr 5) and 31) // next 5 bits (mask 31): encoded Z-down extent
                val zu = 8 * ((entity.solid ushr 10) and 63) - 32 // top 6 bits (mask 63): encoded Z-up extent with -32 bias
                tempBoxMins[0] = -x.toFloat()
                tempBoxMins[1] = -x.toFloat()
                tempBoxMins[2] = -zd.toFloat()
                tempBoxMaxs[0] = x.toFloat()
                tempBoxMaxs[1] = x.toFloat()
                tempBoxMaxs[2] = zu.toFloat()

                headnode = collisionModel.HeadnodeForBox(tempBoxMins, tempBoxMaxs)
                angles = zeroAngles
            }

            if (outTrace.allsolid) {
                return@forEachCurrentEntityState
            }

            val entityTrace = collisionModel.TransformedBoxTrace(
                start,
                end,
                mins,
                maxs,
                headnode,
                MASK_PLAYERSOLID,
                entity.origin,
                angles
            )
            // Note: `startsolid` is intentionally part of the main replacement condition to
            // mirror old `CL_pred.ClipMoveToEntities` behavior. Because of that, a separate
            // `else if (entityTrace.startsolid)` branch would be unreachable here.
            if (entityTrace.allsolid || entityTrace.startsolid || entityTrace.fraction < outTrace.fraction) {
                val hadStartSolid = outTrace.startsolid
                entityTrace.ent = dummyTraceEntity
                outTrace.set(entityTrace)
                // Quirk: trace_t.set() copies startsolid from allsolid in this codebase, so we
                // preserve prior startsolid explicitly to keep PMove behavior stable.
                outTrace.startsolid = hadStartSolid || entityTrace.startsolid
            }
        }
    }

    // Cross-reference: `CL_pred.PMpointcontents`.
    private fun pmPointContents(point: FloatArray): Int {
        var contents = collisionModel.PointContents(point, 0)
        entityManager.forEachCurrentEntityState { entity ->
            if (entity.solid != 31) { // only inline brush models (solid=31) contribute transformed point contents
                return@forEachCurrentEntityState
            }

            val modelName = gameConfig.getModelName(entity.modelindex) ?: return@forEachCurrentEntityState
            if (!modelName.startsWith("*")) {
                return@forEachCurrentEntityState
            }
            val cmodel = runCatching { collisionModel.InlineModel(modelName) }.getOrNull()
                ?: return@forEachCurrentEntityState
            contents = contents or collisionModel.TransformedPointContents(
                point,
                cmodel.headnode,
                entity.origin,
                entity.angles
            )
        }
        return contents
    }
}
