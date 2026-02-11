package org.demoth.cake.stages

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputProcessor
import jake2.qcommon.Defines.BUTTON_ATTACK
import jake2.qcommon.Defines.CMD_BACKUP
import jake2.qcommon.Defines.PM_NORMAL
import jake2.qcommon.Defines.PM_SPECTATOR
import jake2.qcommon.Defines.PITCH
import jake2.qcommon.Defines.ROLL
import jake2.qcommon.Defines.YAW
import jake2.qcommon.exec.Cbuf
import jake2.qcommon.network.messages.client.MoveMessage
import jake2.qcommon.usercmd_t
import jake2.qcommon.util.Math3D
import org.demoth.cake.ClientFrame
import org.demoth.cake.clampPitch
import org.demoth.cake.stages.ClientCommands.*
import org.demoth.cake.wrapSignedAngle
import kotlin.experimental.or
import kotlin.math.abs

// CL_input
class InputManager(
    private val nowNanosProvider: () -> Long = System::nanoTime
) : InputProcessor {
    private val commandsState = BooleanArray(ClientCommands.entries.size)
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

    // mappings for input command: which are sent on every client update frame
    private val inputKeyMappings: MutableMap<Int, ClientCommands> = mutableMapOf(
        Input.Keys.W to in_forward,
        Input.Keys.S to in_back,
        Input.Keys.A to in_moveleft,
        Input.Keys.D to in_moveright,
        Input.Keys.SPACE to in_moveup,
        Input.Keys.C to in_movedown,
        Input.Keys.LEFT to in_left,
        Input.Keys.RIGHT to in_right,
        Input.Keys.UP to in_lookup,
        Input.Keys.DOWN to in_lookdown,
        Input.Keys.CONTROL_LEFT to in_attack,
    )

    // default.cfg
    // input mapping for string commands - sent on demand
    private val inputBindings: MutableMap<Int, String> = mutableMapOf(
        // fixme: are these commands also sent via 'cmd' ?
        Input.Keys.NUM_1 to "use blaster",
        Input.Keys.NUM_2 to "use shotgun",
        Input.Keys.NUM_3 to "use super shotgun",
        Input.Keys.NUM_4 to "use machinegun",
        Input.Keys.NUM_5 to "use chaingun",
        Input.Keys.NUM_6 to "use grenade launcher",
        Input.Keys.NUM_7 to "use rocket launcher",
        Input.Keys.NUM_8 to "use hyperblaster",
        Input.Keys.NUM_9 to "use railgun",
        Input.Keys.NUM_0 to "use bfg10k",
        Input.Keys.G to "use grenades",
        Input.Keys.TAB to "inven",
        Input.Keys.F2 to "cmd help",
        Input.Keys.F5 to "toggle_skybox",
        Input.Keys.F6 to "toggle_level",
        Input.Keys.F7 to "toggle_entities",
    )

    // called at server rate
    fun gatherInput(outgoingSequence: Int, deltaTime: Float, currentFrame: ClientFrame): MoveMessage {
        val nowNanos = nowNanosProvider()
        syncViewAnglesWithServer(currentFrame)

        // assemble the inputs and commands, then transmit them
        val cmdIndex: Int = outgoingSequence and (userCommands.size - 1)

        val cmd = userCommands[cmdIndex]
        cmd.clear()

        if (isPressed(in_attack)) {
            cmd.buttons = cmd.buttons or BUTTON_ATTACK.toByte()
        }

        // pressing both W and S should cancel the forward movement (same for other axes)
        val forwardMove = (if (isPressed(in_forward)) clientSpeed.toInt() else 0) +
            (if (isPressed(in_back)) -clientSpeed.toInt() else 0)
        val sideMove = (if (isPressed(in_moveright)) clientSpeed.toInt() else 0) +
            (if (isPressed(in_moveleft)) -clientSpeed.toInt() else 0)
        val upMove = (if (isPressed(in_moveup)) clientSpeed.toInt() else 0) +
            (if (isPressed(in_movedown)) -clientSpeed.toInt() else 0)

        cmd.forwardmove = forwardMove.toShort()
        cmd.sidemove = sideMove.toShort()
        cmd.upmove = upMove.toShort()

        // update camera direction right on the client side and sent to the server
        if (isPressed(in_left) || isPressed(in_right)) {
            var delta = deltaTime * cameraKeyboardRotationSpeed
            if (isPressed(in_right)) {
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
        val mappedCommand = inputKeyMappings[keycode] ?: return false
        setPressed(mappedCommand, true)
        return true
    }

    override fun keyUp(keycode: Int): Boolean {
        val mappedCommand = inputKeyMappings[keycode]
        if (mappedCommand != null) {
            setPressed(mappedCommand, false)
            return true
        } else if (inputBindings[keycode] != null) {
            val cmd = inputBindings[keycode]
            if (cmd != null) {
                println("Executing command: $cmd")
                Cbuf.AddText(cmd)
            }
            return true
        }
        return false
    }

    override fun keyTyped(character: Char): Boolean {
        return false
    }

    override fun touchCancelled(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        return false
    }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (button == Input.Buttons.LEFT) {
            setPressed(in_attack, true)
            return true
        }
        return false
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (button == Input.Buttons.LEFT) {
            setPressed(in_attack, false)
            return true
        }
        return false
    }

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        return processCameraRotation(screenX, screenY)
    }

    override fun mouseMoved(screenX: Int, screenY: Int): Boolean {
        return processCameraRotation(screenX, screenY)
    }

    override fun scrolled(amountX: Float, amountY: Float): Boolean {
        return false
    }

    // endregion

    private fun isPressed(command: ClientCommands): Boolean = commandsState[command.ordinal]

    private fun setPressed(command: ClientCommands, pressed: Boolean) {
        commandsState[command.ordinal] = pressed
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
            elapsedMs
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
         * - Applying `cmd + delta` to view angles: `CL_pred.PredictMovement()` and `PMove.PM_ClampAngles()`
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

enum class ClientCommands {
    in_moveup,
    in_movedown,
    in_left,
    in_right,
    in_forward,
    in_back,
    in_lookup,
    in_lookdown,
    in_strafe,
    in_moveleft,
    in_moveright,
    in_speed,
    in_attack,
    in_use,
    in_klook,
}
