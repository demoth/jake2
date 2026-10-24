package org.demoth.cake.stages

import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.assets.loaders.FileHandleResolver
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.utils.Disposable
import jake2.qcommon.Com
import ktx.assets.TextAssetLoader
import org.demoth.cake.assets.*
import org.demoth.cake.assets.bsp.BspLoader
import org.demoth.cake.assets.bsp.BspMapAsset
import org.demoth.cake.assets.md2.Md2Asset
import org.demoth.cake.assets.md2.Md2Loader
import org.demoth.cake.ui.GameUiStyle
import org.demoth.cake.ui.ImageThumbnails
import org.demoth.cake.ui.UiViewport
import org.demoth.cake.ui.menu.MenuEventBus
import org.demoth.cake.ui.menu.MenuScreen

/** Owns menu stage lifetimes and presentation; navigation decisions remain in MenuController.
 * The viewport, asset manager, resolver, and content style are borrowed.
 */
class MenuStages(
    private val viewport: UiViewport,
    private val menuEventBus: MenuEventBus,
    private val assetManager: AssetManager,
    private val fileResolver: CakeFileResolver,
    private val showHosting: Boolean,
    private val activeProfileId: () -> String?,
) : Disposable {
    private val stages = linkedMapOf<MenuScreen, BackNavigableMenuStage>()
    var activeScreen: MenuScreen = MenuScreen.MAIN
        private set

    private val activeStage: BackNavigableMenuStage
        get() = stages.getValue(activeScreen)
    val inputProcessor: InputProcessor
        get() = activeStage
    private val contentBrowserStage: ContentBrowserStage
        get() = stages.getValue(MenuScreen.CONTENT_BROWSER) as ContentBrowserStage

    fun rebuild(style: GameUiStyle) {
        dispose()
        for (screen in MenuScreen.entries) {
            stages[screen] = when (screen) {
                MenuScreen.MAIN -> MainMenuStage(viewport, menuEventBus, style, showHosting)
                MenuScreen.PROFILE_EDIT -> ProfileEditStage(viewport, menuEventBus, style)
                MenuScreen.MULTIPLAYER -> MultiplayerMenuStage(viewport, menuEventBus, style, showHosting)
                MenuScreen.SINGLEPLAYER -> HostingSetupStage(viewport, menuEventBus, style, true)
                MenuScreen.HOST_GAME -> HostingSetupStage(viewport, menuEventBus, style, false)
                MenuScreen.JOIN_GAME -> JoinGameStage(viewport, menuEventBus, style)
                MenuScreen.PLAYER_SETUP -> PlayerSetupStage(viewport, menuEventBus, style)
                MenuScreen.OPTIONS -> OptionsMenuStage(viewport, menuEventBus, style)
                MenuScreen.OPTIONS_SECTION -> OptionsSectionStage(viewport, menuEventBus, style)
                MenuScreen.CONTENT_BROWSER -> ContentBrowserStage(viewport, menuEventBus, style,
                    ImageThumbnails(assetManager, FileHandleResolver(fileResolver::resolveContent)),
                    createBrowserPreviewAssets()) { entry ->
                    val file = requireNotNull(fileResolver.resolveContent(entry.normalizedPath)) { "Missing profile sound" }
                    // Preview sounds are owned separately from the shared asset manager.
                    ConvertingSoundLoader(fileResolver).loadSync(assetManager, entry.normalizedPath, file, null)
                }
            }
        }
        if (activeScreen == MenuScreen.CONTENT_BROWSER) refreshContentBrowser()
    }

    /** Returns whether input routing needs to be refreshed. */
    fun select(screen: MenuScreen): Boolean {
        if (screen == activeScreen) return false
        if (activeScreen == MenuScreen.CONTENT_BROWSER) hideContentBrowser()
        activeStage.resetMenuInteractionState()
        activeScreen = screen
        if (activeScreen == MenuScreen.CONTENT_BROWSER) refreshContentBrowser()
        activeStage.resetMenuInteractionState()
        return true
    }

    fun render(deltaSeconds: Float) {
        viewport.apply()
        // Preserve Stage.act()'s capped delta for the main menu.
        if (activeScreen == MenuScreen.MAIN) activeStage.act() else activeStage.act(deltaSeconds)
        activeStage.draw()
    }

    /** Stop previews before replacing the profile or effective server game directory. */
    fun hideContentBrowser() {
        contentBrowserStage.hide()
    }

    override fun dispose() {
        stages.values.forEach { it.dispose() }
        stages.clear()
    }

    private fun refreshContentBrowser() {
        val profileId = activeProfileId()
        if (profileId == null) {
            contentBrowserStage.show(emptyList(), "<unset>", "Select a profile to browse its content.")
            return
        }
        try {
            contentBrowserStage.show(fileResolver.listContentEntries(), profileId)
        } catch (error: Exception) {
            Com.Warn("Could not list profile content: ${error.message}")
            contentBrowserStage.show(emptyList(), profileId, "Could not list profile content.")
        }
    }

    private fun createBrowserPreviewAssets(): AssetManager {
        val resolver = FileHandleResolver { path ->
            // Engine palette/shaders remain available; content dependencies stay within the active profile.
            if (path == "q2palette.bin" || path.startsWith("shaders/")) fileResolver.resolve(path)
            else fileResolver.resolveContent(path)
        }
        return AssetManager(resolver).apply {
            setLoader(Any::class.java, ObjectLoader(resolver))
            setLoader(String::class.java, TextAssetLoader(resolver))
            setLoader(Sp2Asset::class.java, Sp2Loader(resolver))
            setLoader(Md2Asset::class.java, Md2Loader(resolver))
            setLoader(BspMapAsset::class.java, BspLoader(resolver))
            setLoader(Texture::class.java, "pcx", PcxLoader(resolver))
            setLoader(Texture::class.java, "wal", WalLoader(resolver))
        }
    }

}
