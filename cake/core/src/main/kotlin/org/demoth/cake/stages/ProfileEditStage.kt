package org.demoth.cake.stages

import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.utils.viewport.Viewport
import ktx.actors.onClick
import ktx.scene2d.actors
import ktx.scene2d.label
import ktx.scene2d.table
import ktx.scene2d.textButton

/**
 * Placeholder stage for dedicated profile management.
 *
 * A full two-pane editor is added in follow-up commits.
 */
class ProfileEditStage(
    viewport: Viewport,
    private val activeProfileIdProvider: () -> String,
    private val onBackRequested: () -> Unit,
) : Stage(viewport) {
    private lateinit var titleLabel: Label
    private lateinit var selectedProfileLabel: Label
    private var renderedProfileId: String = ""

    init {
        actors {
            table {
                defaults().pad(16f).uniformX().fillX()
                setFillParent(true)

                titleLabel = label("Game Configuration")
                row()
                selectedProfileLabel = label("")
                row()
                textButton("Back") {
                    onClick { onBackRequested() }
                }
            }
        }
        refreshSelectedProfile()
    }

    override fun act(delta: Float) {
        super.act(delta)
        val active = activeProfileIdProvider().trim()
        if (active != renderedProfileId) {
            refreshSelectedProfile()
        }
    }

    private fun refreshSelectedProfile() {
        renderedProfileId = activeProfileIdProvider().trim()
        val shown = renderedProfileId.ifBlank { "<unset>" }
        selectedProfileLabel.setText("Selected profile: $shown")
    }
}

