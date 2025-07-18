package org.demoth.cake.stages

import com.badlogic.gdx.Input
import jake2.qcommon.Defines.BUTTON_ATTACK
import jake2.qcommon.Defines.CMD_BACKUP
import jake2.qcommon.Defines.PITCH
import jake2.qcommon.Defines.ROLL
import jake2.qcommon.Defines.YAW
import jake2.qcommon.exec.Cbuf
import jake2.qcommon.network.messages.client.MoveMessage
import jake2.qcommon.usercmd_t
import jake2.qcommon.util.Math3D
import org.demoth.cake.ClientFrame
import java.util.EnumMap
import org.demoth.cake.stages.ClientCommands.*
import kotlin.experimental.or

// CL_input
class InputManager {
    private val commandsState: EnumMap<ClientCommands, Boolean> = EnumMap(ClientCommands::class.java)
    private val userCommands = Array(CMD_BACKUP) { usercmd_t() }
    private val clientSpeed: Short = 100 // todo: cvar

    // the angle that the player spawned with
    var initialYaw: Float? = null
    var initialPitch: Float? = null
    // local camera angle
    var localYaw: Float = 0f
    var localPitch: Float = 0f

    private val cameraKeyboardRotationSpeed = 140f // degrees per second
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
        Input.Keys.NUM_3 to "use sshotgun",
        Input.Keys.NUM_4 to "use machinegun",
        Input.Keys.NUM_5 to "use chaingun",
        Input.Keys.NUM_6 to "use grenade launcher",
        Input.Keys.NUM_7 to "use rocket launcher",
        Input.Keys.NUM_8 to "use hyperblaster",
        Input.Keys.NUM_9 to "use railgun",
        Input.Keys.NUM_0 to "use bfg",
        Input.Keys.G to "use grenades",
        Input.Keys.TAB to "inven",
        Input.Keys.F2 to "cmd help",
        Input.Keys.F5 to "toggle_skybox",
        Input.Keys.F6 to "toggle_level",
        Input.Keys.F7 to "toggle_entities",
    )

    init {
        ClientCommands.entries.forEach { commandsState[it] = false }
    }

    // called at server rate
    fun gatherInput(outgoingSequence: Int, deltaTime: Float, currentFrame: ClientFrame): MoveMessage {
        // assemble the inputs and commands, then transmit them
        val cmdIndex: Int = outgoingSequence and (userCommands.size - 1)
        val oldCmdIndex: Int = (outgoingSequence - 1) and (userCommands.size - 1)
        val oldestCmdIndex: Int = (outgoingSequence - 2) and (userCommands.size - 1)

        val cmd = userCommands[cmdIndex]
        cmd.clear()

        if (commandsState[in_attack] == true) {
            cmd.buttons = cmd.buttons or BUTTON_ATTACK.toByte()
        }

        if (commandsState[in_forward] == true) {
            cmd.forwardmove = clientSpeed;
        }

        if (commandsState[in_back] == true) {
            cmd.forwardmove = (-clientSpeed).toShort()
        }

        if (commandsState[in_moveleft] == true) {
            cmd.sidemove = (-clientSpeed).toShort()
        }

        if (commandsState[in_moveright] == true) {
            cmd.sidemove = clientSpeed
        }

        if (commandsState[in_moveup] == true) {
            cmd.upmove = clientSpeed
        }

        if (commandsState[in_movedown] == true) {
            cmd.upmove = (-clientSpeed).toShort()
        }

        // degrees
        // If we haven't initialized yet, do so
        if (initialYaw == null) {
            initialYaw = currentFrame.playerstate.viewangles[YAW]
            localYaw = initialYaw!!
        }

        if (initialPitch == null) {
            initialPitch = currentFrame.playerstate.viewangles[PITCH]
            localPitch = initialPitch!!
        }

        // update camera direction right on the client side and sent to the server
        if (commandsState[in_left] == true || commandsState[in_right] == true) {
            var delta = deltaTime * cameraKeyboardRotationSpeed
            if (commandsState[in_right] == true) {
                delta *= -1
            }

            localYaw += delta

        }


        // set the angles
        cmd.angles[PITCH] = Math3D.ANGLE2SHORT(localPitch - initialPitch!!).toShort()
        cmd.angles[YAW] = Math3D.ANGLE2SHORT(localYaw - initialYaw!!).toShort()
        cmd.angles[ROLL] = 0

        cmd.msec = 16 // todo: calculate based on time between client frames (actually between "sending" frames)
        // deliver the message
        return MoveMessage(
            false, // todo
            currentFrame.serverframe,
            userCommands[oldestCmdIndex],
            userCommands[oldCmdIndex],
            userCommands[cmdIndex],
            outgoingSequence
        )
    }

    fun updateAngles(deltaX: Float, deltaY: Float) {
        localYaw -= deltaX
        localPitch += deltaY

        // wrap yaw
        if (localYaw <= -180f) localYaw += 360f
        if (localYaw >= 180f) localYaw -= 360f

        // first wrap the pitch
        if (localPitch <= -180f) localPitch += 360f
        if (localPitch >= 180f) localPitch -= 360f

        // clamp pitch
        if (localPitch >= 89f) localPitch = 89f
        if (localPitch <= -89f) localPitch = -89f

    }

    fun keyDown(keycode: Int): Boolean {
        if (inputKeyMappings[keycode] != null) {
            commandsState[inputKeyMappings[keycode]] = true
            return true
        } else {
            return false
        }
    }

    fun keyUp(keycode: Int): Boolean {
        if (inputKeyMappings[keycode] != null) {
            commandsState[inputKeyMappings[keycode]] = false
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

    fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (button == Input.Buttons.LEFT) {
            commandsState[in_attack] = true
            return true
        }
        return false
    }

    fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        if (button == Input.Buttons.LEFT) {
            commandsState[in_attack] = false
            return true
        }
        return false
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