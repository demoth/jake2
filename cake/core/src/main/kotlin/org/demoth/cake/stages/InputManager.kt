package org.demoth.cake.stages

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputProcessor
import jake2.qcommon.Defines.BUTTON_ATTACK
import jake2.qcommon.Defines.BUTTON_USE
import jake2.qcommon.Defines.CMD_BACKUP
import jake2.qcommon.Defines.PM_NORMAL
import jake2.qcommon.Defines.PM_SPECTATOR
import jake2.qcommon.Defines.PITCH
import jake2.qcommon.Defines.ROLL
import jake2.qcommon.Defines.YAW
import jake2.qcommon.exec.Cmd
import jake2.qcommon.network.messages.client.MoveMessage
import jake2.qcommon.usercmd_t
import jake2.qcommon.util.Math3D
import org.demoth.cake.ClientFrame
import org.demoth.cake.clampPitch
import org.demoth.cake.input.ClientBindings
import org.demoth.cake.wrapSignedAngle
import kotlin.experimental.or
import kotlin.math.abs

/**
 * Builds movement/usercmd packets and bridges bind-driven input into immediate control state.
 *
 * Responsibilities:
 * - Maintain immediate control state used every outgoing `MoveMessage` frame.
 * - Accumulate local view angles (mouse + keyboard turn/look).
 * - Keep local angle basis synchronized with server `pmove.delta_angles`.
 * - Expose `InputProcessor` methods so physical events can be routed through [ClientBindings].
 *
 * Ownership/lifecycle:
 * - Constructed by [org.demoth.cake.Cake] when creating a [Game3dScreen].
 * - Bound [ClientBindings] instance is typically owned by `Cake` and reused across screens.
 *
 * Timing assumptions:
 * - Runs on the LibGDX main thread.
 * - [gatherInput] is called from the network send path; it must remain allocation-light.
 *
 * Invariants:
 * - Immediate controls (`+forward`, `+attack`, etc.) are represented as action counters.
 * - Opposing movement directions cancel on each axis.
 * - `+use` sets `BUTTON_USE`; `+attack` sets `BUTTON_ATTACK`.
 * - `clearInputState()` must be called when input routing/context changes to avoid stuck state.
 */
class InputManager(
    private val bindings: ClientBindings = ClientBindings(),
    private val nowNanosProvider: () -> Long = System::nanoTime,
) : InputProcessor {
    private val immediateStates = IntArray(ImmediateAction.entries.size)
    private val userCommands = Array(CMD_BACKUP) { usercmd_t() }
    private val clientSpeed: Short = 200 // todo: cvar

    // the angle that the player spawned with
    var initialYaw: Float? = null
    var initialPitch: Float? = null

    // local camera angle
    var localYaw: Float = 0f
    var localPitch: Float = 0f

    // Tracks whether previous frame used locally controlled view (PM_NORMAL/PM_SPECTATOR).
    // We need this to force one-shot resync when coming back from server-controlled camera
    // states (PM_DEAD/PM_GIB/PM_FREEZE), where local angles are intentionally ignored.
    private var hadLocalViewControl = false

    private var previousX = 0f
    private var previousY = 0f
    private var deltaX = 0f
    private var deltaY = 0f
    private var hasMouseReference = false
    private val sensitivity = 25f
    private var mouseWasMoved = false

    private val cameraKeyboardRotationSpeed = 140f // degrees per second // todo cvar
    private val commandSequence = IntArray(CMD_BACKUP) { Int.MIN_VALUE }
    private val commandTimestampNanos = LongArray(CMD_BACKUP)
    private var lastCommandBuildNanos: Long? = null
    private val emptyCommand = usercmd_t()

    init {
        registerImmediateActionCommands()
    }

    // called at server rate
    fun gatherInput(outgoingSequence: Int, deltaTime: Float, currentFrame: ClientFrame): MoveMessage {
        val nowNanos = nowNanosProvider()
        syncViewAnglesWithServer(currentFrame)

        // assemble the inputs and commands, then transmit them
        val cmdIndex: Int = outgoingSequence and (userCommands.size - 1)

        val cmd = userCommands[cmdIndex]
        cmd.clear()

        if (isActive(ImmediateAction.ATTACK)) {
            cmd.buttons = cmd.buttons or BUTTON_ATTACK.toByte()
        }

        if (isActive(ImmediateAction.USE)) {
            cmd.buttons = cmd.buttons or BUTTON_USE.toByte()
        }

        // pressing opposite movement keys cancels movement (same for all axes)
        val forwardMove = (if (isActive(ImmediateAction.FORWARD)) clientSpeed.toInt() else 0) +
            (if (isActive(ImmediateAction.BACK)) -clientSpeed.toInt() else 0)
        val sideMove = (if (isActive(ImmediateAction.MOVERIGHT)) clientSpeed.toInt() else 0) +
            (if (isActive(ImmediateAction.MOVELEFT)) -clientSpeed.toInt() else 0)
        val upMove = (if (isActive(ImmediateAction.MOVEUP)) clientSpeed.toInt() else 0) +
            (if (isActive(ImmediateAction.MOVEDOWN)) -clientSpeed.toInt() else 0)

        cmd.forwardmove = forwardMove.toShort()
        cmd.sidemove = sideMove.toShort()
        cmd.upmove = upMove.toShort()

        // update camera direction right on the client side and sent to the server
        if (isActive(ImmediateAction.LEFT) || isActive(ImmediateAction.RIGHT)) {
            var delta = deltaTime * cameraKeyboardRotationSpeed
            if (isActive(ImmediateAction.RIGHT)) {
                delta *= -1
            }
            localYaw += delta
        }

        // Apply pending mouse input before encoding command angles, so the sent move
        // and rendered camera use the same orientation this frame.
        applyPendingMouseLook()

        // set the angles
        cmd.angles[PITCH] = Math3D.ANGLE2SHORT(localPitch - initialPitch!!).toShort()
        cmd.angles[YAW] = Math3D.ANGLE2SHORT(localYaw - initialYaw!!).toShort()
        cmd.angles[ROLL] = 0

        cmd.msec = computeCommandMsec(nowNanos).toByte()
        commandSequence[cmdIndex] = outgoingSequence
        commandTimestampNanos[cmdIndex] = nowNanos

        val oldCmd = getHistoricalCommand(outgoingSequence - 1)
        val oldestCmd = getHistoricalCommand(outgoingSequence - 2)

        // deliver the message
        return MoveMessage(
            false, // todo
            currentFrame.serverframe,
            oldestCmd,
            oldCmd,
            userCommands[cmdIndex],
            outgoingSequence
        )
    }

    private fun registerImmediateActionCommands() {
        for (action in ImmediateAction.entries) {
            Cmd.AddCommand("+${action.command}", true) {
                pressAction(action)
            }
            Cmd.AddCommand("-${action.command}", true) {
                releaseAction(action)
            }
        }
    }

    private fun pressAction(action: ImmediateAction) {
        immediateStates[action.ordinal]++
    }

    private fun releaseAction(action: ImmediateAction) {
        val idx = action.ordinal
        if (immediateStates[idx] > 0) {
            immediateStates[idx]--
        }
    }

    private fun isActive(action: ImmediateAction): Boolean = immediateStates[action.ordinal] > 0

    private fun applyPendingMouseLook() {
        if (mouseWasMoved) {
            mouseWasMoved = false

            localYaw = wrapSignedAngle(localYaw - deltaX)
            // todo: invert mouse cvar
            localPitch = clampPitch(localPitch + deltaY)
        }
    }

    // region INPUT PROCESSOR

    override fun keyDown(keycode: Int): Boolean {
        return bindings.handleKeyDown(keycode)
    }

    override fun keyUp(keycode: Int): Boolean {
        return bindings.handleKeyUp(keycode)
    }

    override fun keyTyped(character: Char): Boolean {
        return false
    }

    override fun touchCancelled(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        return false
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        return bindings.handleMouseButtonDown(button)
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        return bindings.handleMouseButtonUp(button)
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        return processCameraRotation(screenX, screenY)
    }

    override fun mouseMoved(screenX: Int, screenY: Int): Boolean {
        return processCameraRotation(screenX, screenY)
    }

    override fun scrolled(amountX: Float, amountY: Float): Boolean {
        return bindings.handleScroll(amountX, amountY)
    }

    // endregion

    /**
     * Resets all held immediate actions and pending button-release state.
     *
     * Call on menu/console/input-focus transitions before gameplay input resumes.
     */
    fun clearInputState() {
        bindings.releaseAllActiveButtons()
        immediateStates.fill(0)
        resetMouseLookReference()
    }

    private fun computeCommandMsec(nowNanos: Long): Int {
        val previous = lastCommandBuildNanos
        lastCommandBuildNanos = nowNanos
        if (previous == null) {
            return 16 // happens only on the first frame, so ok
        }

        val elapsedMs = ((nowNanos - previous) / 1_000_000L).toInt()
        // CL_input.FinishMove
        return if (elapsedMs > 250) {
            100 // time was unreasonable
        } else {
            elapsedMs.coerceAtLeast(1)
        }
    }

    private fun getHistoricalCommand(sequence: Int): usercmd_t {
        val index = sequence and (CMD_BACKUP - 1)
        return if (commandSequence[index] == sequence) userCommands[index] else emptyCommand
    }

    fun getCommandForSequence(sequence: Int): usercmd_t? {
        val index = sequence and (CMD_BACKUP - 1)
        return if (commandSequence[index] == sequence) userCommands[index] else null
    }

    fun getCommandTimestampNanos(sequence: Int): Long? {
        val index = sequence and (CMD_BACKUP - 1)
        return if (commandSequence[index] == sequence) commandTimestampNanos[index] else null
    }

    private fun processCameraRotation(screenX: Int, screenY: Int): Boolean {
        if (!hasMouseReference) {
            previousX = screenX.toFloat()
            previousY = screenY.toFloat()
            hasMouseReference = true
            deltaX = 0f
            deltaY = 0f
            mouseWasMoved = false
        } else {
            deltaX = sensitivity * (screenX - previousX) / Gdx.graphics.width
            deltaY = sensitivity * (screenY - previousY) / Gdx.graphics.height
            previousX = screenX.toFloat()
            previousY = screenY.toFloat()
            mouseWasMoved = true
        }
        return true // consume the event
    }

    /**
     * Rearms mouse delta tracking so the next mouse event only establishes a baseline position.
     *
     * This prevents a large one-frame jump after input focus/context changes (menu/console toggles,
     * cursor capture changes, etc.) where historical coordinates are no longer meaningful.
     */
    fun resetMouseLookReference() {
        hasMouseReference = false
        mouseWasMoved = false
        deltaX = 0f
        deltaY = 0f
    }

    /**
     * Keeps local camera/cmd angle state aligned with the server's authoritative angle basis.
     *
     * `usercmd.angles` are interpreted relative to `pmove.delta_angles`, not as absolute world angles.
     * Also, server code can change `delta_angles` without local mouse input (e.g. rotating platforms, teleports,
     *   respawn initialization).
     *
     * To prevent desync of the angles:
     * - In server-controlled view modes, we mirror server view and basis directly.
     * - On first frame back to local control, we hard-resync.
     * - During local control, we rebase `local*` angles and `initial*` angles by the same delta when server `delta_angles`
     *   changed, preserving a continuous `(local - initial)` command stream.
     */
    private fun syncViewAnglesWithServer(currentFrame: ClientFrame) {
        /*
         * Cross-reference to old client (`client/`):
         * - Local angle accumulation: `CL_input.AdjustAngles()`
         * - Writing command angles: `CL_input.BaseMove()` + `CL_input.FinishMove()`
         * - Receiving `delta_angles`: `CL_ents.ParsePlayerstate()`
         * - Applying `cmd + delta` to view angles: `CL_pred.PredictMovement()` and `pmove_t.clampAngles()`
         */
        val state = currentFrame.playerstate
        val hasLocalControl = state.pmove.pm_type == PM_NORMAL || state.pmove.pm_type == PM_SPECTATOR

        val serverViewYaw = wrapSignedAngle(state.viewangles[YAW])
        val serverViewPitch = clampPitch(state.viewangles[PITCH])
        val serverDeltaYaw = wrapSignedAngle(Math3D.SHORT2ANGLE(state.pmove.delta_angles[YAW].toInt()))
        val serverDeltaPitch = wrapSignedAngle(Math3D.SHORT2ANGLE(state.pmove.delta_angles[PITCH].toInt()))

        if (!hasLocalControl) {
            initialYaw = serverDeltaYaw
            initialPitch = serverDeltaPitch
            localYaw = serverViewYaw
            localPitch = serverViewPitch
            hadLocalViewControl = false
            return
        }

        // first frame of local control
        if (initialYaw == null || initialPitch == null || !hadLocalViewControl) {
            initialYaw = serverDeltaYaw
            initialPitch = serverDeltaPitch
            localYaw = serverViewYaw
            localPitch = serverViewPitch
            hadLocalViewControl = true
            return
        }

        // Server can change delta angles (e.g. rotating platforms / respawn). Rebase local and initial together.
        val yawRebase = wrapSignedAngle(serverDeltaYaw - initialYaw!!)
        if (abs(yawRebase) > 0.01f) {
            localYaw = wrapSignedAngle(localYaw + yawRebase)
            initialYaw = wrapSignedAngle(initialYaw!! + yawRebase)
        }

        val pitchRebase = wrapSignedAngle(serverDeltaPitch - initialPitch!!)
        if (abs(pitchRebase) > 0.01f) {
            localPitch = clampPitch(localPitch + pitchRebase)
            initialPitch = wrapSignedAngle(initialPitch!! + pitchRebase)
        }
        hadLocalViewControl = true
    }
}

private enum class ImmediateAction(val command: String) {
    MOVEUP("moveup"),
    MOVEDOWN("movedown"),
    LEFT("left"),
    RIGHT("right"),
    FORWARD("forward"),
    BACK("back"),
    LOOKUP("lookup"),
    LOOKDOWN("lookdown"),
    MOVELEFT("moveleft"),
    MOVERIGHT("moveright"),
    ATTACK("attack"),
    USE("use"),
}
