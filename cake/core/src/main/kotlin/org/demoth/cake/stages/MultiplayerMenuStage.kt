package org.demoth.cake.stages

import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.utils.viewport.Viewport
import ktx.actors.onClick
import ktx.scene2d.actors
import ktx.scene2d.label
import ktx.scene2d.table
import ktx.scene2d.textButton
import org.demoth.cake.ui.menu.MenuEventBus
import org.demoth.cake.ui.menu.MenuIntent

class MultiplayerMenuStage(
    viewport: Viewport,
    private val menuEventBus: MenuEventBus,
) : Stage(viewport) {
    init {
        actors {
            table {
                defaults().pad(16f).uniformX().fillX()
                setFillParent(true)

                label("Multiplayer")
                row()
                textButton("Host Game (future)").apply {
                    isDisabled = true
                }
                row()
                textButton("Join Game") {
                    onClick {
                        menuEventBus.postIntent(MenuIntent.OpenJoinGame)
                    }
                }
                row()
                textButton("Player Setup") {
                    onClick {
                        menuEventBus.postIntent(MenuIntent.OpenPlayerSetup)
                    }
                }
                row()
                textButton("Back") {
                    onClick {
                        menuEventBus.postIntent(MenuIntent.OpenMainMenu)
                    }
                }
            }
        }
    }
}
