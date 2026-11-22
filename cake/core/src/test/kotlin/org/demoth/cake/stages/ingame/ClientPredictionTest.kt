package org.demoth.cake.stages.ingame

import com.badlogic.gdx.assets.AssetManager
import jake2.qcommon.CM
import org.demoth.cake.CakeCvars
import org.demoth.cake.ClientFrame
import org.demoth.cake.config.GameConfiguration
import org.demoth.cake.input.InputManager
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test

class ClientPredictionTest {
    @Test
    fun `fully acknowledged movement keeps the server view angles`() {
        CakeCvars.registerEntities()
        CakeCvars.registerInput()
        val assets = AssetManager({ null }, false)
        val entities = ClientEntityManager()
        try {
            val prediction = ClientPrediction(CM(), entities, GameConfiguration(assets))
            val serverFrame = ClientFrame().apply {
                valid = true
                playerstate.viewangles[0] = 12f
                playerstate.viewangles[1] = 127f
            }

            // The next outgoing sequence follows the last acknowledgement, leaving no moves to replay.
            prediction.updateNetworkState(incomingAcknowledged = 42, outgoingSequence = 43, currentTimeMs = 0)
            prediction.predictMovement(serverFrame, InputManager(), playerIndex = 0)

            assertArrayEquals(serverFrame.playerstate.viewangles, prediction.predictedAngles, 0f)
        } finally {
            entities.dispose()
            assets.dispose()
        }
    }
}
