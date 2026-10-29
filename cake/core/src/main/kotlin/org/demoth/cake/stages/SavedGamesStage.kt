package org.demoth.cake.stages

import com.badlogic.gdx.Input
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Stack
import com.badlogic.gdx.scenes.scene2d.ui.List as SceneList
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Scaling
import com.badlogic.gdx.utils.viewport.Viewport
import org.demoth.cake.hosting.HostSaveEntry
import org.demoth.cake.ui.GameUiStyle
import org.demoth.cake.ui.menu.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** List and preview share a selection; IO and confirmation decisions belong to the controller. */
class SavedGamesStage(
    viewport: Viewport,
    menuEventBus: MenuEventBus,
    private val style: GameUiStyle,
    private val saving: Boolean,
    private val readPreview: (String, String) -> ByteArray? = { _, _ -> null },
) : BackNavigableMenuStage(viewport, menuEventBus, MenuIntent.OpenSingleplayerMenu) {
    private data class Choice(val entry: HostSaveEntry) {
        override fun toString(): String {
            val date = entry.savedAtMillis?.let {
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(it))
            } ?: "Unknown date"
            val map = entry.mapName ?: entry.slot
            val title = entry.mapTitle?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() } ?: map
            return "$date - $title - $map"
        }
    }

    private var syncing = false
    private var renderedEntries: List<HostSaveEntry>? = null
    private var renderedSlot: String? = null
    private val profile = Label("", style.menuWidgets.label)
    private val slots = SceneList<Choice>(style.menuWidgets.list).apply { name = "save-slots" }
    private val listScroll = ScrollPane(slots, style.menuWidgets.scrollPane).apply {
        name = "save-list-scroll"
        setScrollingDisabled(false, false)
        setFadeScrollBars(false)
        setFlickScroll(false)
    }
    private val detail = Label("", style.menuWidgets.label).apply { setWrap(true) }
    private val previewImage = Image().apply { name = "save-preview-image"; setScaling(Scaling.fit) }
    private val noPreview = Label("No preview available", style.menuWidgets.label).apply { setAlignment(com.badlogic.gdx.utils.Align.center) }
    private var previewTexture: Texture? = null
    private var previewEntries: List<HostSaveEntry>? = null
    private var previewProfile: String? = null
    private var previewSlot: String? = null
    private val status = Label("", style.menuWidgets.label).apply { name = "save-status"; setWrap(true) }
    private fun action(label: String, name: String, run: () -> Unit) =
        createMenuButton(label, style.menuWidgets.button, run).apply {
            this.name = name
            labelCell.minWidth(0f)
        }
    private val newSave = action("New Save", "save-new") {
        menuEventBus.postIntent(MenuIntent.SaveGameRequested(menuEventBus.latestState().savedGames.profileId))
    }
    private val primary = action(if (saving) "Save" else "Load", if (saving) "save-overwrite" else "load-game") {
        val state = menuEventBus.latestState().savedGames
        state.selectedSlot?.let {
            menuEventBus.postIntent(if (saving) MenuIntent.SaveGameRequested(state.profileId, it)
                else MenuIntent.LoadGameRequested(state.profileId, it))
        }
    }
    private val delete = action("Delete", "delete-save") {
        val state = menuEventBus.latestState().savedGames
        state.selectedSlot?.let { menuEventBus.postIntent(MenuIntent.DeleteSaveRequested(state.profileId, it)) }
    }
    private val confirm = action("Replace Save", "confirm-overwrite") {
        val state = menuEventBus.latestState().savedGames
        state.pendingDeleteSlot?.let { menuEventBus.postIntent(MenuIntent.ConfirmDeleteSave(state.profileId, it)) }
            ?: state.pendingOverwriteSlot?.let { menuEventBus.postIntent(MenuIntent.ConfirmOverwrite(state.profileId, it)) }
    }
    private val confirmation = Table().apply {
        add(confirm).pad(4f)
        add(action("Cancel", "cancel-save-confirmation") {
            menuEventBus.postIntent(MenuIntent.CancelSaveConfirmation)
        }).pad(4f)
    }
    private val confirmationCell: Cell<Table>

    init {
        slots.addListener(object : ChangeListener() {
            override fun changed(event: ChangeEvent, actor: Actor) {
                if (!syncing) slots.selected?.let {
                    menuEventBus.postIntent(MenuIntent.SelectSaveSlot(menuEventBus.latestState().savedGames.profileId, it.entry.slot))
                }
            }
        })
        val preview = Table().apply {
            background = style.menuWidgets.controlPanel
            name = "save-preview"
            add(Stack(previewImage, noPreview)).grow().pad(4f)
        }
        val right = Table().apply {
            top()
            add(preview).growX().height(216f).row()
            add(detail).width(288f).growX().padTop(12f).top()
        }
        val body = Table().apply {
            add(listScroll).width(440f).height(320f).padRight(16f)
            add(right).width(300f).height(320f).top()
        }
        val actions = Table().apply {
            defaults().uniformX().growX().pad(4f)
            if (saving) add(newSave)
            add(primary)
            add(delete)
            add(createBackButton(style = style.menuWidgets.button).apply { labelCell.minWidth(0f) })
        }
        val root = Table().apply {
            defaults().pad(6f).left()
            add(Label(if (saving) "Save Game" else "Load Game", style.menuWidgets.label)).row()
            add(profile).row()
            add(body).growX().row()
            add(actions).growX().row()
            confirmationCell = add(confirmation).growX().height(0f)
            row()
            add(status).width(756f).row()
        }
        addCenteredForm(root, style.menuWidgets.scrollPane)
        scrollFocus = listScroll
        keyboardFocus = slots
    }

    override fun act(delta: Float) {
        super.act(delta)
        val snapshot = menuEventBus.latestState()
        val state = snapshot.savedGames
        syncing = true
        if (renderedEntries !== state.entries) {
            renderedEntries = state.entries
            slots.setItems(*state.entries.map(::Choice).toTypedArray())
        }
        slots.items.firstOrNull { it.entry.slot == state.selectedSlot }?.let { slots.selected = it }
        syncing = false
        if (renderedSlot != state.selectedSlot) {
            renderedSlot = state.selectedSlot
            listScroll.layout()
            listScroll.scrollTo(0f, slots.height - (slots.selectedIndex + 1) * slots.itemHeight, 0f, slots.itemHeight)
        }
        val selected = state.entries.firstOrNull { it.slot == state.selectedSlot }
        if (previewEntries !== state.entries || previewProfile != state.profileId || previewSlot != selected?.slot) {
            hidePreview()
            previewEntries = state.entries
            previewProfile = state.profileId
            previewSlot = selected?.slot
            try {
                val png = selected?.let { readPreview(state.profileId, it.slot) }
                if (png != null) {
                    val pixels = Pixmap(png, 0, png.size)
                    try {
                        require(pixels.width <= 320 && pixels.height <= 240) { "Oversized checkpoint preview" }
                        previewTexture = Texture(pixels).apply { setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear) }
                        previewImage.drawable = TextureRegionDrawable(previewTexture)
                    } finally { pixels.dispose() }
                }
            } catch (failure: Exception) {
                Gdx.app.error("Saved games", "Could not load checkpoint preview", failure)
            }
            noPreview.isVisible = previewTexture == null
        }
        val pending = state.pendingOverwriteSlot != null || state.pendingDeleteSlot != null
        val reason = if (saving) snapshot.saveAccess.saveRejectionReason else snapshot.saveAccess.loadRejectionReason
        profile.setText("Profile: ${state.profileId.ifBlank { "<unset>" }}")
        slots.touchable = if (pending) Touchable.disabled else Touchable.enabled
        slots.selection.isDisabled = pending
        newSave.isDisabled = pending || reason != null
        primary.isDisabled = pending || reason != null || selected == null ||
            (if (saving) selected.autosave else selected.loadRejectionReason != null)
        delete.isDisabled = pending || selected == null || snapshot.saveAccess.loadRejectionReason != null
        confirm.setText(if (state.pendingDeleteSlot != null) "Delete Save" else "Replace Save")
        confirm.name = if (state.pendingDeleteSlot != null) "confirm-delete" else "confirm-overwrite"
        confirmation.isVisible = pending
        confirmationCell.height(if (pending) 68f else 0f)
        detail.setText(when {
            state.entries.isEmpty() -> "No saved games for this profile."
            saving && selected?.autosave == true -> "Autosave is reserved. Choose New Save or another slot."
            else -> listOfNotNull(if (selected?.autosave == true) "Autosave" else null,
                selected?.comment, selected?.loadRejectionReason).joinToString("\n")
        })
        status.setText(state.statusMessage.ifBlank { reason.orEmpty() })
    }

    override fun keyUp(keycode: Int): Boolean {
        val state = menuEventBus.latestState().savedGames
        if (keycode == Input.Keys.ESCAPE && (state.pendingOverwriteSlot != null || state.pendingDeleteSlot != null)) {
            menuEventBus.postIntent(MenuIntent.CancelSaveConfirmation)
            return true
        }
        return super.keyUp(keycode)
    }

    /** Only the selected preview is retained; hide/rebuild/profile changes release its texture. */
    fun hidePreview() {
        if (previewTexture == null && previewEntries == null) return
        previewImage.drawable = null
        previewTexture?.dispose()
        previewTexture = null
        previewEntries = null
        previewProfile = null
        previewSlot = null
        noPreview.isVisible = true
    }

    override fun dispose() {
        hidePreview()
        super.dispose()
    }
}
