package org.demoth.cake.automation

import com.badlogic.gdx.Input
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.utils.Disableable
import jake2.qcommon.Globals
import org.demoth.cake.ui.menu.MenuIntent
import org.demoth.cake.ui.menu.MenuScreen

/** GL-thread UI access for runtime automation and tests. Never caches stages or actors across actions. */
class AutomationUi(
    private val currentStage: () -> Stage?,
    private val navigate: (MenuIntent) -> Unit,
) {
    val stage: Stage
        get() {
            requireDeveloper()
            return checkNotNull(currentStage()) { "No UI stage is currently presented" }
        }

    fun open(screen: MenuScreen, optionsSection: String? = null) {
        requireDeveloper()
        val intent = when (screen) {
            MenuScreen.MAIN -> MenuIntent.OpenMainMenu
            MenuScreen.LOAD_GAME -> MenuIntent.OpenLoadGame
            MenuScreen.SAVE_GAME -> MenuIntent.OpenSaveGame
            MenuScreen.PROFILE_EDIT -> MenuIntent.OpenProfileEditor
            MenuScreen.MODS -> MenuIntent.OpenMods
            MenuScreen.MULTIPLAYER -> MenuIntent.OpenMultiplayerMenu
            MenuScreen.SINGLEPLAYER -> MenuIntent.OpenSingleplayerMenu
            MenuScreen.NEW_GAME -> MenuIntent.OpenHostingSetup(true)
            MenuScreen.HOST_GAME -> MenuIntent.OpenHostingSetup(false)
            MenuScreen.JOIN_GAME -> MenuIntent.OpenJoinGame
            MenuScreen.PLAYER_SETUP -> MenuIntent.OpenPlayerSetup
            MenuScreen.OPTIONS -> MenuIntent.OpenOptions
            MenuScreen.OPTIONS_SECTION -> MenuIntent.OpenOptionsSection(
                requireNotNull(optionsSection?.takeIf { it.isNotBlank() }) { "Options section is required" })
            MenuScreen.CONTENT_BROWSER -> MenuIntent.OpenContentBrowser
        }
        navigate(intent)
    }

    /** Null means absent; duplicate IDs are always an error, including hidden duplicates. */
    fun find(id: String): Actor? = find(stage, id)

    fun element(id: String): Actor = checkNotNull(find(id)) { "UI element '$id' was not found" }

    fun click(id: String) {
        val active = stage
        val target = checkNotNull(find(active, id)) { "UI element '$id' was not found" }
        var ancestor: Actor? = target
        while (ancestor != null) {
            check(ancestor.isVisible) { "UI element '$id' is hidden" }
            check((ancestor as? Disableable)?.isDisabled != true) { "UI element '$id' is disabled" }
            check(ancestor.touchable != Touchable.disabled) { "UI element '$id' does not accept input" }
            ancestor = ancestor.parent
        }
        check(target.width > 0f && target.height > 0f) { "UI element '$id' has no clickable area" }
        val screen = active.stageToScreenCoordinates(target.localToStageCoordinates(
            Vector2(target.width / 2f, target.height / 2f)))
        // Use the rounded input coordinates for hit testing too. Stage handles viewport/HiDPI conversion.
        val point = active.screenToStageCoordinates(Vector2(screen.x.toInt().toFloat(), screen.y.toInt().toFloat()))
        val hit = active.hit(point.x, point.y, true)
        check(hit != null && hit.isDescendantOf(target)) { "UI element '$id' is covered or clipped" }
        val handled = active.touchDown(screen.x.toInt(), screen.y.toInt(), 0, Input.Buttons.LEFT)
        active.touchUp(screen.x.toInt(), screen.y.toInt(), 0, Input.Buttons.LEFT)
        check(handled) { "UI element '$id' did not handle pointer input" }
    }

    private fun find(active: Stage, id: String): Actor? {
        require(id.isNotBlank()) { "UI element ID must not be blank" }
        var match: Actor? = null
        fun visit(actor: Actor) {
            if (actor.name == id) {
                check(match == null) { "Duplicate UI element ID '$id' in the active stage" }
                match = actor
            }
            if (actor is Group) actor.children.forEach(::visit)
        }
        visit(active.root)
        return match
    }

    private fun requireDeveloper() {
        check(Globals.developer?.typedValue == true) { "UI automation requires developer = 1" }
    }
}
