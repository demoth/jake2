package org.demoth.cake.stages

import com.badlogic.gdx.Input
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.utils.viewport.Viewport
import org.demoth.cake.hosting.HostSaveEntry
import org.demoth.cake.ui.GameUiStyle
import org.demoth.cake.ui.menu.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Shared Load/Save presentation; catalog IO and overwrite decisions belong to the controller. */
class SavedGamesStage(
    viewport: Viewport,
    menuEventBus: MenuEventBus,
    style: GameUiStyle,
    private val saving: Boolean,
) : BackNavigableMenuStage(viewport, menuEventBus, MenuIntent.OpenSingleplayerMenu) {
    private data class Choice(val entry: HostSaveEntry) {
        override fun toString(): String {
            val date = entry.savedAtMillis?.let {
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(it))
            }.orEmpty()
            val title = entry.mapName ?: entry.slot
            return "${if (entry.autosave) "Autosave: " else ""}$title  $date"
        }
    }

    private var syncing = false
    private var renderedEntries: List<HostSaveEntry>? = null
    private val profile = Label("", style.menuWidgets.label)
    private val slots = SelectBox<Choice>(style.menuWidgets.selectBox).apply { name = "save-slots"; maxListCount = 10 }
    private val detail = Label("", style.menuWidgets.label).apply { setWrap(true) }
    private val status = Label("", style.menuWidgets.label).apply { name = "save-status"; setWrap(true) }
    private val primary = createMenuButton(if (saving) "New Save" else "Load Game", style.menuWidgets.button) {
        val state = menuEventBus.latestState().savedGames
        if (saving) menuEventBus.postIntent(MenuIntent.SaveGameRequested(state.profileId))
        else state.selectedSlot?.let { menuEventBus.postIntent(MenuIntent.LoadGameRequested(state.profileId, it)) }
    }.apply { name = if (saving) "save-new" else "load-game" }
    private val overwrite = createMenuButton("Overwrite", style.menuWidgets.button) {
        val state = menuEventBus.latestState().savedGames
        state.selectedSlot?.let { menuEventBus.postIntent(MenuIntent.SaveGameRequested(state.profileId, it)) }
    }.apply { name = "save-overwrite" }
    private val confirmation = Table().apply {
        add(createMenuButton("Replace Save", style.menuWidgets.button) {
            val state = menuEventBus.latestState().savedGames
            state.pendingOverwriteSlot?.let { menuEventBus.postIntent(MenuIntent.ConfirmOverwrite(state.profileId, it)) }
        }.apply { name = "confirm-overwrite" }).pad(4f)
        add(createMenuButton("Cancel", style.menuWidgets.button) {
            menuEventBus.postIntent(MenuIntent.CancelOverwrite)
        }.apply { name = "cancel-overwrite" }).pad(4f)
    }

    init {
        slots.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) {
                if (!syncing) slots.selected?.let {
                    menuEventBus.postIntent(MenuIntent.SelectSaveSlot(menuEventBus.latestState().savedGames.profileId, it.entry.slot))
                }
            }
        })
        val root = Table().apply {
            defaults().pad(6f).left()
            add(Label(if (saving) "Save Game" else "Load Game", style.menuWidgets.label)).colspan(2).row()
            add(profile).colspan(2).row()
            add(slots).colspan(2).width(560f).row()
            add(detail).colspan(2).width(560f).row()
            add(primary).colspan(if (saving) 1 else 2)
            if (saving) add(overwrite)
            row()
            add(confirmation).colspan(2).row()
            add(status).colspan(2).width(560f).row()
            add(createBackButton(style = style.menuWidgets.button)).colspan(2).row()
        }
        addCenteredForm(root, style.menuWidgets.scrollPane)
    }

    override fun act(delta: Float) {
        super.act(delta)
        val snapshot = menuEventBus.latestState()
        val state = snapshot.savedGames
        syncing = true
        if (renderedEntries !== state.entries) {
            renderedEntries = state.entries
            slots.hideScrollPane()
            slots.setItems(*state.entries.map(::Choice).toTypedArray())
        }
        slots.items.firstOrNull { it.entry.slot == state.selectedSlot }?.let { slots.selected = it }
        syncing = false
        val selected = state.entries.firstOrNull { it.slot == state.selectedSlot }
        val pending = state.pendingOverwriteSlot != null
        val reason = if (saving) snapshot.saveAccess.saveRejectionReason else snapshot.saveAccess.loadRejectionReason
        profile.setText("Profile: ${state.profileId.ifBlank { "<unset>" }}")
        slots.isDisabled = state.entries.isEmpty() || pending
        primary.isDisabled = pending || reason != null || (!saving && (selected == null || selected.loadRejectionReason != null))
        overwrite.isDisabled = pending || reason != null || selected == null || selected.autosave
        confirmation.isVisible = pending
        confirmation.cells.forEach { it.height(if (pending) 68f else 0f) }
        detail.setText(when {
            state.entries.isEmpty() -> "No saved games for this profile."
            saving && selected?.autosave == true -> "Autosave is reserved. Choose New Save or another slot."
            else -> listOfNotNull(selected?.comment, selected?.loadRejectionReason).joinToString("\n")
        })
        status.setText(state.statusMessage.ifBlank { reason.orEmpty() })
    }

    override fun keyUp(keycode: Int): Boolean {
        if (keycode == Input.Keys.ESCAPE && menuEventBus.latestState().savedGames.pendingOverwriteSlot != null) {
            menuEventBus.postIntent(MenuIntent.CancelOverwrite)
            return true
        }
        return super.keyUp(keycode)
    }
}
