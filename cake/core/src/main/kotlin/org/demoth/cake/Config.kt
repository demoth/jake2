package org.demoth.cake

import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.assets.AssetLoaderParameters
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.utils.Disposable
import jake2.qcommon.Com
import jake2.qcommon.Defines.*
import jake2.qcommon.exec.Cmd
import org.demoth.cake.assets.BspMapAsset
import org.demoth.cake.assets.Md2Asset
import org.demoth.cake.assets.Md2Loader
import org.demoth.cake.assets.SkyLoader

/**
 * Store all configuration related to the current map.
 * Updated from server.
 *
 * Asset ownership model:
 * - This object owns gameplay/map assets acquired from configstrings and fixed gameplay lookups
 *   (map BSP, models, sounds, HUD images, sky model, player model, weapon sounds).
 * - Ownership is expressed via AssetManager reference counting: each GameConfiguration acquires each
 *   path once and unloads it once in [unloadAssets].
 * - This allows old/new configurations to overlap during map transition without dropping shared assets.
 *
 * Lifetime:
 * 1. receive configstrings via [applyConfigString]
 * 2. [loadMapAsset] and [loadAssets] during precache
 * 3. read via typed getters during gameplay
 * 4. [unloadAssets] when this configuration is retired
 *
 * tothink: This could be moved to common and reused on the server side?
 */
data class Config(
    val value: String,
    var resource: Disposable? = null
)

// may not be great to have a big array of nulls, but configs are accessed by the index (maybe use map int->config?)
class GameConfiguration(
    private val assetManager: AssetManager,
    size: Int = MAX_CONFIGSTRINGS
) {

    init {
        Cmd.AddCommand("print_configs", true) {
            configStrings.forEachIndexed { i, c ->
                if (c != null) {
                    Com.Printf("ConfigString[$i] = ${c.value}, loaded = ${c.resource != null}\n")
                }
            }
        }

    }

    /**
     * Layout for score and help screens.
     * For some reason, it is not managed by the config strings.
     */
    var layout: String = ""

    val inventory: IntArray = IntArray(MAX_ITEMS) { 0 }

    private val configStrings = Array<Config?>(size) { null }
    private val trackedLoadedAssets: MutableMap<String, Class<*>> = mutableMapOf()
    private val weaponSounds: HashMap<Int, Sound> = hashMapOf()
    private var mapAsset: BspMapAsset? = null
    private var playerModel: Model? = null
    private val playerModelPath = "players/male/tris.md2"
    private val playerSkinPath = "players/male/grunt.pcx"

    private val weaponSoundPaths = mapOf(
        MZ_BLASTER to "weapons/blastf1a.wav",
        MZ_MACHINEGUN to "weapons/machgf1b.wav", // todo: random
        MZ_SHOTGUN to "weapons/shotgf1b.wav",
        MZ_CHAINGUN1 to "weapons/machgf1b.wav",
        MZ_CHAINGUN2 to "weapons/machgf1b.wav",
        MZ_CHAINGUN3 to "weapons/machgf1b.wav",
        MZ_RAILGUN to "weapons/railgf1a.wav",
        MZ_ROCKET to "weapons/rocklf1a.wav",
        MZ_GRENADE to "weapons/grenlf1a.wav",
        MZ_LOGIN to "weapons/grenlf1a.wav",
        MZ_LOGOUT to "weapons/grenlf1a.wav",
        MZ_RESPAWN to "weapons/grenlf1a.wav",
        MZ_BFG to "weapons/bfg__f1y.wav",
        MZ_SSHOTGUN to "weapons/sshotf1b.wav",
        MZ_HYPERBLASTER to "weapons/hyprbf1a.wav",
        MZ_ITEMRESPAWN to null,
        MZ_IONRIPPER to "weapons/rippfire.wav",
        MZ_BLUEHYPERBLASTER to "weapons/hyprbf1a.wav",
        MZ_PHALANX to "weapons/plasshot.wav",
        MZ_ETF_RIFLE to "weapons/nail1.wav",
        MZ_UNUSED to null,
        MZ_SHOTGUN2 to "weapons/shotg2.wav",
        MZ_HEATBEAM to null,
        MZ_BLASTER2 to "weapons/blastf1a.wav",
        MZ_TRACKER to "weapons/disint2.wav",
        MZ_NUKE1 to null,
        MZ_NUKE2 to null,
        MZ_NUKE4 to null,
        MZ_NUKE8 to null,
    )

    fun applyConfigString(index: Int, value: String, loadResource: Boolean = false): Config {
        val config = Config(value)
        configStrings[index] = config
        if (loadResource) {
            loadConfigResource(index, config)
        }
        return config
    }

    // region GET ASSETS

    operator fun get(index: Int) = configStrings[index]

    operator fun set(index: Int, config: Config) {
        configStrings[index] = config
    }

    fun getMapName(): String? {
        return configStrings[CS_MODELS + 1]?.value
    }

    fun getStatusBarLayout(): String? {
        return configStrings[CS_STATUSBAR]?.value
    }

    fun getSkyModel(): Model? {
        return configStrings[CS_SKY]?.resource as? Model
    }

    fun getModel(modelIndex: Int): Model? {
        return configStrings.getOrNull(CS_MODELS + modelIndex)?.resource as? Model
    }

    fun getModelName(modelIndex: Int): String? {
        return configStrings.getOrNull(CS_MODELS + modelIndex)?.value
    }

    fun getSound(soundIndex: Int): Sound? {
        return configStrings.getOrNull(CS_SOUNDS + soundIndex)?.resource as? Sound
    }

    fun getSoundPath(soundIndex: Int): String? {
        return configStrings.getOrNull(CS_SOUNDS + soundIndex)?.value
    }

    fun getImage(imageIndex: Int): Texture? {
        return configStrings.getOrNull(CS_IMAGES + imageIndex)?.resource as? Texture
    }

    fun getItemName(itemIndex: Int): String? {
        return configStrings.getOrNull(CS_ITEMS + itemIndex)?.value
    }

    fun getConfigValue(configIndex: Int): String? {
        return configStrings.getOrNull(configIndex)?.value
    }

    fun getWeaponSound(weaponType: Int): Sound? {
        return weaponSounds[weaponType]
    }

    fun getPlayerModel(): Model? {
        return playerModel
    }
    // endregion

    // region LOAD ASSETS

    fun loadMapAsset(): BspMapAsset {
        val mapPath = getMapName()
            ?: error("Map config string is missing at index ${CS_MODELS + 1}")
        val loaded = acquireAsset<BspMapAsset>(mapPath)
        mapAsset = loaded
        return loaded
    }


    fun preloadPlayerAssets() {
        val playerMd2Asset = acquireAsset(
            playerModelPath,
            Md2Loader.Parameters(playerSkinPath),
        )
        playerModel = playerMd2Asset.model
    }


    fun preloadWeaponSounds() {
        weaponSounds.clear()
        weaponSoundPaths.forEach { (weaponType, soundPath) ->
            if (soundPath == null) {
                return@forEach
            }
            val assetPath = "sound/$soundPath"
            if (assetManager.fileHandleResolver.resolve(assetPath) == null) {
                return@forEach
            }
            weaponSounds[weaponType] = acquireAsset<Sound>(assetPath)
        }
    }


    private fun loadConfigResource(index: Int, config: Config): Boolean {
        return when (index) {
            in (CS_MODELS + 1)..<(CS_MODELS + MAX_MODELS) -> loadModelConfigResource(config)
            in (CS_SOUNDS + 1)..<(CS_SOUNDS + MAX_SOUNDS) -> loadSoundConfigResource(config)
            in (CS_IMAGES + 1)..<(CS_IMAGES + MAX_IMAGES) -> loadImageConfigResource(config)
            CS_SKY -> loadSkyConfigResource(config)
            else -> false
        }
    }

    fun loadAssets(firstNonInlineModelConfigIndex: Int) {
        val lastModelConfigIndex = CS_MODELS + MAX_MODELS - 1
        for (i in firstNonInlineModelConfigIndex..lastModelConfigIndex) {
            val config = configStrings[i] ?: continue
            if (!loadConfigResource(i, config)) {
                val modelPath = config.value
                if (modelPath.isNotBlank() && !modelPath.startsWith("*") && !modelPath.startsWith("#")) {
                    Com.Warn("Failed to load model data for config $modelPath")
                }
            }
        }

        for (i in (CS_SOUNDS + 1)..<(CS_SOUNDS + MAX_SOUNDS)) {
            val config = configStrings[i] ?: continue
            loadConfigResource(i, config)
        }

        for (i in (CS_IMAGES + 1)..<(CS_IMAGES + MAX_IMAGES)) {
            val config = configStrings[i] ?: continue
            loadConfigResource(i, config)
        }

        configStrings[CS_SKY]?.let { loadConfigResource(CS_SKY, it) }

        preloadPlayerAssets()
        preloadWeaponSounds()
    }

    // endregion

    fun unloadAssets() {
        unloadTrackedAssets()
        mapAsset = null
        weaponSounds.clear()
        playerModel = null
        configStrings.forEach { config -> config?.resource = null }
    }

    private inline fun <reified T> acquireAsset(path: String): T {
        return acquireAsset(path, T::class.java) {
            assetManager.load(path, T::class.java)
        }
    }

    private inline fun <reified T> acquireAsset(path: String, parameter: AssetLoaderParameters<T>): T {
        return acquireAsset(path, T::class.java) {
            assetManager.load(path, T::class.java, parameter)
        }
    }

    /**
     * Acquire this configuration's ownership for an asset path and return the loaded instance.
     *
     * Why this exists:
     * each [GameConfiguration] must contribute exactly one AssetManager reference per path it uses.
     * We rely on this during map/screen handover: old and new configs can coexist, both referencing
     * shared assets, and unloading the old config must not drop assets still needed by the new one.
     *
     * Rules enforced here:
     * - First acquisition of a path by this config: call [loadAsset], finish loading, remember ownership.
     * - Re-acquisition of the same path by this config: do not load again, just return the asset.
     *
     * This gives deterministic "one config -> one ownership slot per path", so [unloadAssets] can
     * safely release all tracked paths once and let AssetManager refcounting decide the final lifetime.
     */
    private fun <T> acquireAsset(path: String, assetClass: Class<T>, loadAsset: () -> Unit): T {
        val trackedType = trackedLoadedAssets[path]
        if (trackedType == null || !assetManager.isLoaded(path, assetClass)) {
            if (trackedType != null) {
                // Defensive recovery: something external unloaded this path, so reacquire ownership.
                Com.Warn("Reacquiring unexpectedly unloaded asset: $path")
            }
            loadAsset()
            assetManager.finishLoadingAsset<T>(path)
            trackedLoadedAssets[path] = assetClass
        }
        return assetManager.get(path, assetClass)
    }

    @Suppress("UNCHECKED_CAST")
    private fun unloadTrackedAssets() {
        trackedLoadedAssets.forEach { (path, assetClass) ->
            if (assetManager.isLoaded(path, assetClass as Class<Any>)) {
                assetManager.unload(path)
            }
        }
        trackedLoadedAssets.clear()
    }

    private fun loadModelConfigResource(config: Config): Boolean {
        val modelPath = config.value
        if (modelPath.isBlank() || modelPath.startsWith("*") || modelPath.startsWith("#")) {
            return false
        }
        if (!modelPath.endsWith(".md2", ignoreCase = true)) {
            // sprite models (.sp2) and others are handled by dedicated render paths.
            return false
        }
        if (assetManager.fileHandleResolver.resolve(modelPath) == null) {
            return false
        } // todo: warning if not found!
        val md2Asset = acquireAsset<Md2Asset>(modelPath)
        config.resource = md2Asset.model
        return true
    }

    private fun loadSoundConfigResource(config: Config): Boolean {
        val soundPath = config.value
        if (soundPath.isBlank() || soundPath.startsWith("*")) {
            return false
        }
        val assetPath = "sound/$soundPath"
        if (assetManager.fileHandleResolver.resolve(assetPath) == null) {
            return false
        } // todo: warning if not found!
        config.resource = acquireAsset<Sound>(assetPath)
        return true
    }

    private fun loadImageConfigResource(config: Config): Boolean {
        val imageName = config.value
        if (imageName.isBlank()) {
            return false
        }
        val assetPath = "pics/$imageName.pcx"
        if (assetManager.fileHandleResolver.resolve(assetPath) == null) {
            return false
        } // todo: warning if not found!
        config.resource = acquireAsset<Texture>(assetPath)
        return true
    }

    private fun loadSkyConfigResource(config: Config): Boolean {
        val skyName = config.value
        if (skyName.isBlank()) {
            config.resource = null
            return false
        }
        val skyAssetPath = SkyLoader.assetPath(skyName)
        if (assetManager.fileHandleResolver.resolve(skyAssetPath) == null) {
            return false
        }
        config.resource = acquireAsset<Model>(skyAssetPath)
        return true
    }
}
