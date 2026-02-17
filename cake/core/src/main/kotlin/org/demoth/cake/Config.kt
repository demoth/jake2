package org.demoth.cake

import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.assets.AssetLoaderParameters
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.GdxRuntimeException
import jake2.qcommon.Com
import jake2.qcommon.Defines.*
import jake2.qcommon.exec.Cmd
import org.demoth.cake.assets.BspMapAsset
import org.demoth.cake.assets.Md2Asset
import org.demoth.cake.assets.SkyLoader
import org.demoth.cake.assets.Sp2Asset

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
    companion object {
        const val UNKNOWN_PLAYER_INDEX = -1
    }

    init {
        Cmd.AddCommand("print_configs", true) {
            configStrings.forEachIndexed { i, c ->
                if (c != null) {
                    Com.Printf("ConfigString[$i] = ${c.value}, loaded = ${c.resource != null}\n")
                }
            }
        }
        Cmd.AddCommand("print_asset_errors", true) {
            if (failedAssets.isEmpty()) {
                Com.Printf("No failed assets\n")
                return@AddCommand
            }
            failedAssets.forEach { (path, error) ->
                Com.Printf("Failed asset: $path -> $error\n")
            }
        }
    }

    /**
     * Layout for score and help screens.
     * For some reason, it is not managed by the config strings.
     */
    var layout: String = ""

    /**
     * Current local player slot index from `ServerDataMessage.playerNumber` (0-based).
     *
     * Invariant:
     * value is either [UNKNOWN_PLAYER_INDEX] (before serverdata/after unload) or in
     * `0 until MAX_CLIENTS`.
     *
     */
    var playerIndex: Int
        get() = playerConfiguration.playerIndex
        set(value) {
            playerConfiguration.playerIndex = value
        }

    val inventory: IntArray
        get() = playerConfiguration.inventory

    private val configStrings = Array<Config?>(size) { null }
    private val trackedLoadedAssets: MutableMap<String, Class<*>> = mutableMapOf()
    private val failedAssets: MutableMap<String, String> = mutableMapOf()
    private val playerConfiguration = PlayerConfiguration()
    private val weaponSounds: HashMap<Int, Sound> = hashMapOf()
    private var mapAsset: BspMapAsset? = null

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

    /**
     * Apply a server configstring update and optionally resolve/load its resource immediately.
     *
     * Invariants:
     * - `CS_PLAYERSKINS` updates invalidate per-client player variation cache entries.
     * - `*` sounds remain unresolved here and are handled later by variation-aware sound lookup.
     */
    fun applyConfigString(index: Int, value: String, loadResource: Boolean = false): Config {
        val config = Config(value)
        configStrings[index] = config
        if (index in CS_PLAYERSKINS until (CS_PLAYERSKINS + MAX_CLIENTS)) {
            val clientIndex = index - CS_PLAYERSKINS
            playerConfiguration.onPlayerSkinConfigUpdated(clientIndex, loadResource)
        } else if (loadResource) {
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

    fun getSpriteModel(modelIndex: Int): Sp2Asset? {
        return configStrings.getOrNull(CS_MODELS + modelIndex)?.resource as? Sp2Asset
    }

    fun getModelName(modelIndex: Int): String? {
        return configStrings.getOrNull(CS_MODELS + modelIndex)?.value
    }

    /**
     * Resolve a sound referenced by `CS_SOUNDS`.
     *
     * For regular sounds, this returns the precached configstring resource.
     * For variation-specific sounds (`*name.wav`), this resolves by the emitting player variation.
     *
     * [entityIndex] uses server entity numbering (`1..MAX_CLIENTS` for player entities).
     * Non-player or missing entity indices fall back to default variation (`male/grunt`).
     */
    fun getSound(soundIndex: Int, entityIndex: Int = 0): Sound? {
        val config = configStrings.getOrNull(CS_SOUNDS + soundIndex) ?: return null
        val soundPath = config.value
        if (soundPath.isBlank()) {
            return null
        }
        if (!soundPath.startsWith("*")) {
            return config.resource as? Sound
        }
        val soundName = soundPath.removePrefix("*")
        return playerConfiguration.getPlayerSound(entityIndex, soundName)
    }

    /**
     * Resolve a variation-specific player sound by player [entityIndex] and file [soundName].
     *
     * Typical callers are client-side entity event handlers (`EV_FALL`, `EV_FALLFAR`).
     */
    fun getPlayerSoundPath(entityIndex: Int, soundName: String): Sound? {
        return playerConfiguration.getPlayerSound(entityIndex, soundName)
    }

    /**
     * Resolve and load a sound by loose path.
     *
     * Accepts either `sound/...` or bare paths like `player/land1.wav`.
     */
    fun getNamedSound(soundPath: String): Sound? {
        val normalized = soundPath.trim().removePrefix("/")
        if (normalized.isBlank()) {
            return null
        }
        val candidates = linkedSetOf<String>()
        if (normalized.startsWith("sound/", ignoreCase = true)) {
            candidates += normalized
        } else {
            candidates += "sound/$normalized"
            candidates += normalized
        }
        val assetPath = candidates.firstOrNull { assetExists(it) } ?: return null
        return tryAcquireAsset(assetPath)
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

    /**
     * Resolve and load a named IdTech2 HUD picture (layout `picn`/inventory backgrounds).
     *
     * Legacy counterpart:
     * `client/SCR.ExecuteLayoutString` `picn` branch (`re.DrawPic(x, y, token)`).
     */
    fun getNamedPic(picName: String): Texture? {
        val normalized = picName.trim().removePrefix("/")
        if (normalized.isBlank()) {
            return null
        }

        val candidates = linkedSetOf<String>()
        if (normalized.contains('/')) {
            if (normalized.endsWith(".pcx", ignoreCase = true)) {
                candidates += normalized
            } else {
                candidates += "$normalized.pcx"
            }
        } else {
            candidates += "pics/$normalized.pcx"
        }

        for (path in candidates) {
            if (assetManager.fileHandleResolver.resolve(path) != null) {
                return tryAcquireAsset<Texture>(path)
            }
        }
        return null
    }

    fun getClientName(clientIndex: Int): String? {
        return playerConfiguration.getClientName(clientIndex)
    }

    /**
     * Resolve scoreboard icon matching IdTech2 player skin fallback behavior.
     *
     * Legacy counterpart:
     * `client/CL_parse.LoadClientinfo` icon fallback rules.
     *
     * Resolution is variation-based:
     * 1) `<variation model>/<variation skin>_i`
     * 2) `<default model>/<variation skin>_i` (if non-default model)
     * 3) `<default model>/<default skin>_i`
     */
    fun getClientIcon(clientIndex: Int): Texture? {
        return playerConfiguration.getClientIcon(clientIndex)
    }

    fun getWeaponSound(weaponType: Int): Sound? {
        return weaponSounds[weaponType]
    }

    /**
     * Resolve and load player model+skin from `CS_PLAYERSKINS` using legacy `LoadClientinfo` fallback logic.
     *
     * Legacy counterpart:
     * `client/CL_parse.LoadClientinfo` model+skin fallback and `client/CL_ents.AddPacketEntities`
     * custom-player path (`s1.modelindex == 255`, `s1.skinnum & 0xff`).
     *
     * Invariants:
     * - `skinnum & 0xFF` is treated as the remote client index (as in old client).
     * - Returned model, when non-null, always points to a skin-specific MD2 variant key.
     * - If client-specific assets fail, fallback attempts `male/grunt`.
     */
    fun getPlayerModel(skinnum: Int, renderFx: Int): Model? {
        return playerConfiguration.getPlayerModel(skinnum, renderFx)
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
            tryAcquireAsset<Sound>(assetPath)?.let { loaded ->
                weaponSounds[weaponType] = loaded
            }
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

        playerConfiguration.preloadAssets()
        playerConfiguration.preloadVariationSounds()
        preloadWeaponSounds()
    }

    // endregion

    fun unloadAssets() {
        unloadTrackedAssets()
        mapAsset = null
        weaponSounds.clear()
        playerConfiguration.clearTransientState()
        failedAssets.clear()
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
        if (assetManager.fileHandleResolver.resolve(modelPath) == null) {
            return false
        } // todo: warning if not found!
        return when {
            modelPath.endsWith(".md2", ignoreCase = true) -> {
                val md2Asset = acquireAsset<Md2Asset>(modelPath)
                config.resource = md2Asset.model
                true
            }

            modelPath.endsWith(".sp2", ignoreCase = true) -> {
                config.resource = acquireAsset<Sp2Asset>(modelPath)
                true
            }

            else -> false
        }
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
        config.resource = tryAcquireAsset<Sound>(assetPath)
        return config.resource != null
    }

    private inline fun <reified T> tryAcquireAsset(assetPath: String): T? {
        if (failedAssets.containsKey(assetPath)) {
            return null
        }
        return try {
            acquireAsset<T>(assetPath)
        } catch (e: GdxRuntimeException) {
            val error = rootCauseMessage(e)
            failedAssets[assetPath] = error
            Com.Warn("Failed to load asset $assetPath: $error")
            null
        }
    }

    /** Bundle of per-player model/skin identity used by rendering, icon, and sound resolution. */
    private data class PlayerModelSkin(
        val modelName: String,
        val skinName: String,
    ) {
        fun modelPath(): String = "players/$modelName/tris.md2"
        fun skinPath(): String = "players/$modelName/$skinName.pcx"
        fun iconPath(): String = "players/$modelName/${skinName}_i.pcx"
        fun playerFolderSoundPath(soundName: String): String = "players/$modelName/$soundName"
        fun legacySoundPath(soundName: String): String = "sound/player/$modelName/$soundName"
    }

    private data class ParsedClientInfo(
        val name: String,
        val modelSkin: PlayerModelSkin,
    )

    private inner class PlayerConfiguration {
        private val defaultPlayerModel = "male"
        private val defaultPlayerSkin = "grunt"
        // Synthetic MD2 variant key format used for player skins: "<skinPath>|<modelPath>".
        // The right side keeps the ".md2" suffix so standard Md2Asset loader selection still works.
        private val playerVariantSeparator = "|"

        var playerIndex: Int = UNKNOWN_PLAYER_INDEX
            set(value) {
                field = sanitizePlayerIndex(value)
            }

        val inventory: IntArray = IntArray(MAX_ITEMS) { 0 }

        private val currentPlayerInfos: MutableMap<Int, PlayerModelSkin?> = mutableMapOf()
        private val playerVariationSoundPathCache: MutableMap<String, String?> = mutableMapOf()

        fun onPlayerSkinConfigUpdated(clientIndex: Int, preloadAsset: Boolean) {
            currentPlayerInfos.remove(clientIndex)
            if (preloadAsset) {
                preloadPlayerAsset(clientIndex)
            }
        }

        fun clearTransientState() {
            currentPlayerInfos.clear()
            playerVariationSoundPathCache.clear()
            playerIndex = UNKNOWN_PLAYER_INDEX
        }

        fun preloadAssets() {
            // Legacy prep phase preloads all clients (`CL_view.PrepRefresh -> CL_parse.ParseClientinfo`).
            for (clientIndex in 0 until MAX_CLIENTS) {
                preloadPlayerAsset(clientIndex)
            }
            loadPlayerModelAsset(defaultPlayerModelSkin())
        }

        // Player-specific sounds, start with *
        fun preloadVariationSounds() {
            val modelSkinSpecificSoundNames = buildSet {
                for (i in (CS_SOUNDS + 1)..<(CS_SOUNDS + MAX_SOUNDS)) {
                    val soundPath = configStrings[i]?.value ?: continue
                    if (soundPath.startsWith("*") && soundPath.length > 1) {
                        add(soundPath.removePrefix("*"))
                    }
                }
            }
            if (modelSkinSpecificSoundNames.isEmpty()) {
                return
            }

            val knownVariations = linkedSetOf(defaultPlayerModelSkin())
            for (clientIndex in 0 until MAX_CLIENTS) {
                getPlayerModelSkin(clientIndex)?.let { knownVariations += it }
            }

            for (variation in knownVariations) {
                for (soundName in modelSkinSpecificSoundNames) {
                    loadPlayerSound(variation, soundName)
                }
            }
        }

        fun getPlayerSound(entityIndex: Int, soundName: String): Sound? {
            val variation = resolvePlayerModelSkinForEntity(entityIndex) ?: defaultPlayerModelSkin()
            return loadPlayerSound(variation, soundName)
        }

        fun getClientName(clientIndex: Int): String? {
            return parseClientInfo(clientIndex)?.name
        }

        /**
         * Resolve scoreboard icon matching IdTech2 player skin fallback behavior.
         *
         * Legacy counterpart:
         * `client/CL_parse.LoadClientinfo` icon fallback rules.
         *
         * Resolution is variation-based:
         * 1) `<variation model>/<variation skin>_i`
         * 2) `<default model>/<variation skin>_i` (if non-default model)
         * 3) `<default model>/<default skin>_i`
         */
        fun getClientIcon(clientIndex: Int): Texture? {
            val variation = getPlayerModelSkin(clientIndex) ?: defaultPlayerModelSkin()
            val defaultVariation = defaultPlayerModelSkin()
            val candidatePaths = buildList {
                add(variation.iconPath())
                if (!variation.modelName.equals(defaultVariation.modelName, ignoreCase = true)) {
                    add(defaultVariation.copy(skinName = variation.skinName).iconPath())
                }
                add(defaultVariation.iconPath())
            }

            for (path in candidatePaths) {
                if (assetManager.fileHandleResolver.resolve(path) != null) {
                    return tryAcquireAsset<Texture>(path)
                }
            }
            return null
        }

        /**
         * Resolve and load player model+skin from `CS_PLAYERSKINS` using legacy `LoadClientinfo` fallback logic.
         *
         * Legacy counterpart:
         * `client/CL_parse.LoadClientinfo` model+skin fallback and `client/CL_ents.AddPacketEntities`
         * custom-player path (`s1.modelindex == 255`, `s1.skinnum & 0xff`).
         *
         * Invariants:
         * - `skinnum & 0xFF` is treated as the remote client index (as in old client).
         * - Returned model, when non-null, always points to a skin-specific MD2 variant key.
         * - If client-specific assets fail, fallback attempts `male/grunt`.
         */
        fun getPlayerModel(skinnum: Int, renderFx: Int): Model? {
            val clientIndex = skinnum and 0xFF
            val defaultVariation = defaultPlayerModelSkin()
            val baseVariation = getPlayerModelSkin(clientIndex) ?: defaultVariation
            val effectiveVariation = applyDisguise(baseVariation, renderFx)
            return loadPlayerModelAsset(effectiveVariation) ?: loadPlayerModelAsset(defaultVariation)
        }

        private fun parseClientInfo(clientIndex: Int): ParsedClientInfo? {
            if (clientIndex !in 0 until MAX_CLIENTS) {
                return null
            }
            val rawValue = configStrings.getOrNull(CS_PLAYERSKINS + clientIndex)?.value ?: return null
            if (rawValue.isBlank()) {
                return null
            }

            val splitByName = rawValue.split('\\', limit = 2)
            val name = splitByName.firstOrNull().orEmpty()
            val modelAndSkin = splitByName.getOrNull(1).orEmpty()
            if (modelAndSkin.isBlank()) {
                return ParsedClientInfo(name = name, modelSkin = defaultPlayerModelSkin())
            }

            val splitBySkin = modelAndSkin.split('/', limit = 2)
            val model = splitBySkin.firstOrNull().orEmpty().ifBlank { defaultPlayerModel }
            val skin = splitBySkin.getOrNull(1).orEmpty().ifBlank { defaultPlayerSkin }
            return ParsedClientInfo(name = name, modelSkin = PlayerModelSkin(modelName = model, skinName = skin))
        }

        private fun defaultPlayerModelSkin(): PlayerModelSkin {
            return PlayerModelSkin(defaultPlayerModel, defaultPlayerSkin)
        }

        private fun getPlayerModelSkin(clientIndex: Int): PlayerModelSkin? {
            if (clientIndex !in 0 until MAX_CLIENTS) {
                return null
            }
            if (currentPlayerInfos.containsKey(clientIndex)) {
                return currentPlayerInfos[clientIndex]
            }
            val parsed = parseClientInfo(clientIndex)
            val resolved = parsed?.let { getPlayerModelSkin(it.modelSkin) }
            currentPlayerInfos[clientIndex] = resolved
            return resolved
        }

        /**
         * Mirrors old-client model/skin fallback order from `CL_parse.LoadClientinfo`:
         * 1) requested model
         * 2) if model missing -> male
         * 3) requested skin on selected model
         * 4) if skin missing on non-male -> male + same skin
         * 5) if still missing -> male/grunt
         */
        private fun getPlayerModelSkin(parsed: PlayerModelSkin): PlayerModelSkin {
            var model = parsed.modelName.ifBlank { defaultPlayerModel }
            var skin = parsed.skinName.ifBlank { defaultPlayerSkin }
            val defaultVariation = defaultPlayerModelSkin()

            if (!assetExists(PlayerModelSkin(modelName = model, skinName = skin).modelPath())) {
                model = defaultVariation.modelName
            }

            if (!assetExists(PlayerModelSkin(modelName = model, skinName = skin).skinPath()) &&
                !model.equals(defaultVariation.modelName, ignoreCase = true)
            ) {
                model = defaultVariation.modelName
            }

            if (!assetExists(PlayerModelSkin(modelName = model, skinName = skin).skinPath())) {
                skin = defaultVariation.skinName
            }

            return PlayerModelSkin(model, skin)
        }

        private fun applyDisguise(variation: PlayerModelSkin, renderFx: Int): PlayerModelSkin {
            if ((renderFx and RF_USE_DISGUISE) == 0) {
                return variation
            }
            val disguiseVariation = variation.copy(skinName = "disguise")
            if (!assetExists(disguiseVariation.modelPath()) || !assetExists(disguiseVariation.skinPath())) {
                return variation
            }
            return disguiseVariation
        }

        private fun preloadPlayerAsset(clientIndex: Int) {
            val variation = getPlayerModelSkin(clientIndex) ?: return
            loadPlayerModelAsset(variation)
        }

        private fun resolvePlayerModelSkinForEntity(entityIndex: Int): PlayerModelSkin? {
            if (entityIndex !in 1..MAX_CLIENTS) {
                return null
            }
            return getPlayerModelSkin(entityIndex - 1)
        }

        private fun loadPlayerSound(variation: PlayerModelSkin, soundName: String): Sound? {
            val path = getPlayerSoundPath(variation, soundName) ?: return null
            return tryAcquireAsset(path)
        }

        private fun getPlayerSoundPath(variation: PlayerModelSkin, soundName: String): String? {
            val cacheKey = "${variation.modelName.lowercase()}|$soundName"
            if (playerVariationSoundPathCache.containsKey(cacheKey)) {
                return playerVariationSoundPathCache[cacheKey]
            }

            val resolved = playerVariationSoundCandidates(variation, soundName)
                .firstOrNull { assetExists(it) }
            playerVariationSoundPathCache[cacheKey] = resolved
            return resolved
        }

        /**
         * Candidate order for variation-specific sounds (`*...`), matching legacy behavior while staying
         * variation-name agnostic (no hardcoded non-default model names).
         *
         * Order:
         * 1) `players/<variation model>/<sound>`
         * 2) `sound/player/<variation model>/<sound>`
         * 3) default model equivalents (when variation model is non-default)
         * 4) generic player fallback: `sound/player/<sound>` then `player/<sound>`
         *
         * Legacy counterpart:
         * `client/sound/lwjgl/LWJGLSoundImpl.RegisterSexedSound` plus legacy temp-entity generic fall sounds.
         */
        private fun playerVariationSoundCandidates(variation: PlayerModelSkin, soundName: String): List<String> {
            val defaultVariation = defaultPlayerModelSkin()
            val candidates = linkedSetOf(
                variation.playerFolderSoundPath(soundName),
                variation.legacySoundPath(soundName),
            )
            if (!variation.modelName.equals(defaultVariation.modelName, ignoreCase = true)) {
                candidates += defaultVariation.playerFolderSoundPath(soundName)
                candidates += defaultVariation.legacySoundPath(soundName)
            }
            candidates += "sound/player/$soundName"
            candidates += "player/$soundName"
            return candidates.toList()
        }

        /**
         * Load a player model with a skin-specific synthetic asset key.
         *
         * Why synthetic key:
         * AssetManager caches by path, while player MD2 skin is chosen by key.
         * This method keeps cache entries unique per `(model, skin)` without changing renderer code.
         */
        private fun loadPlayerModelAsset(variation: PlayerModelSkin): Model? {
            val modelPath = variation.modelPath()
            val skinPath = variation.skinPath()
            if (!assetExists(modelPath) || !assetExists(skinPath)) {
                return null
            }
            val modelAssetPath = playerModelVariantAssetPath(modelPath, skinPath)
            val md2Asset = tryAcquireAsset<Md2Asset>(modelAssetPath) ?: return null
            return md2Asset.model
        }

        private fun playerModelVariantAssetPath(modelPath: String, skinPath: String): String {
            return "$skinPath$playerVariantSeparator$modelPath"
        }

        private fun sanitizePlayerIndex(value: Int): Int {
            if (value in 0 until MAX_CLIENTS) {
                return value
            }
            if (value != UNKNOWN_PLAYER_INDEX) {
                Com.Warn("Ignoring invalid playerIndex=$value, using UNKNOWN_PLAYER_INDEX")
            }
            return UNKNOWN_PLAYER_INDEX
        }
    }

    private fun assetExists(path: String): Boolean {
        return assetManager.fileHandleResolver.resolve(path) != null
    }

    private fun rootCauseMessage(t: Throwable): String {
        var root: Throwable = t
        while (root.cause != null) {
            root = root.cause!!
        }
        return root.message ?: root.javaClass.simpleName
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
