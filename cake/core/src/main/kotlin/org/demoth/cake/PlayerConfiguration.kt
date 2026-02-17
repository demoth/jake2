package org.demoth.cake

import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g3d.Model
import jake2.qcommon.Com
import jake2.qcommon.Defines.CS_PLAYERSKINS
import jake2.qcommon.Defines.CS_SOUNDS
import jake2.qcommon.Defines.MAX_CLIENTS
import jake2.qcommon.Defines.MAX_ITEMS
import jake2.qcommon.Defines.MAX_SOUNDS
import jake2.qcommon.Defines.RF_USE_DISGUISE
import org.demoth.cake.assets.Md2Asset

/**
 * Player-scoped runtime configuration derived from server configstrings.
 *
 * Purpose:
 * encapsulate player slot state, inventory snapshot, and player variation resolution
 * (name/icon/model/sound) for the active map configuration.
 *
 * Ownership/Lifecycle:
 * owned by one [GameConfiguration]; created once per game configuration and reset by
 * [clearTransientState] when that configuration unloads.
 *
 * Threading/Timing:
 * called from the same runtime flow as [GameConfiguration] message parsing/render updates;
 * no cross-thread synchronization is provided.
 *
 * Boundary note:
 * this class intentionally depends on [GameConfiguration] for configstring reads and
 * asset ownership helpers (`operator get`, `tryAcquireAsset`) so all assets remain tracked by
 * a single owner during map transitions.
 *
 * Related components:
 * `Game3dScreen`, `ClientEntityManager`, `Hud` (all consume this API via `gameConfig.playerConfiguration`).
 */
class PlayerConfiguration(
    private val gameConfiguration: GameConfiguration,
    private val assetManager: AssetManager,
) {
    private val defaultPlayerModel = "male"
    private val defaultPlayerSkin = "grunt"
    // Synthetic MD2 variant key format used for player skins: "<skinPath>|<modelPath>".
    // The right side keeps the ".md2" suffix so standard Md2Asset loader selection still works.
    // see Md2
    private val playerVariantSeparator = "|"

    /**
     * Current local player slot index from `ServerDataMessage.playerNumber` (0-based).
     *
     * Invariant:
     * value is either [GameConfiguration.UNKNOWN_PLAYER_INDEX] (before serverdata/after unload)
     * or in `0 until MAX_CLIENTS`.
     */
    var playerIndex: Int = GameConfiguration.UNKNOWN_PLAYER_INDEX
        set(value) {
            field = sanitizePlayerIndex(value)
        }

    val inventory: IntArray = IntArray(MAX_ITEMS) { 0 }

    private val currentPlayerInfos: MutableMap<Int, PlayerModelSkin?> = mutableMapOf()
    private val playerVariationSoundPathCache: MutableMap<String, String?> = mutableMapOf()

    /**
     * Apply `CS_PLAYERSKINS` update side effects for one client slot.
     *
     * Invariant:
     * cached model/skin resolution for [clientIndex] must be invalidated before next lookup.
     */
    fun onPlayerSkinConfigUpdated(clientIndex: Int, preloadAsset: Boolean) {
        currentPlayerInfos.remove(clientIndex)
        if (preloadAsset) {
            preloadPlayerAsset(clientIndex)
        }
    }

    /** Clear non-configstring runtime state when parent [GameConfiguration] unloads. */
    fun clearTransientState() {
        currentPlayerInfos.clear()
        playerVariationSoundPathCache.clear()
        playerIndex = GameConfiguration.UNKNOWN_PLAYER_INDEX
    }

    /**
     * Preload known player models and variation-specific `*` sounds for this map config.
     *
     * Must run after relevant configstrings are received and before gameplay frame rendering starts.
     */
    fun preload() {
        preloadAssets()
        preloadVariationSounds()
    }

    private fun preloadAssets() {
        // Legacy prep phase preloads all clients (`CL_view.PrepRefresh -> CL_parse.ParseClientinfo`).
        for (clientIndex in 0 until MAX_CLIENTS) {
            preloadPlayerAsset(clientIndex)
        }
        loadPlayerModelAsset(defaultPlayerModelSkin())
    }

    // Player-specific sounds, start with *
    private fun preloadVariationSounds() {
        val modelSkinSpecificSoundNames = buildSet {
            for (i in (CS_SOUNDS + 1)..<(CS_SOUNDS + MAX_SOUNDS)) {
                val soundPath = gameConfiguration[i]?.value ?: continue
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

    /**
     * Resolve a variation-specific player sound by player [entityIndex] and file [soundName].
     *
     * `entityIndex` follows server entity numbering (`1..MAX_CLIENTS` for players).
     * Non-player indices fallback to default variation (`male/grunt`).
     */
    fun getPlayerSound(entityIndex: Int, soundName: String): Sound? {
        val variation = resolvePlayerModelSkinForEntity(entityIndex) ?: defaultPlayerModelSkin()
        return loadPlayerSound(variation, soundName)
    }

    /** Read client display name from `CS_PLAYERSKINS` payload. */
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
            if (assetExists(path)) {
                return gameConfiguration.tryAcquireAsset(path)
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
        val rawValue = gameConfiguration[CS_PLAYERSKINS + clientIndex]?.value ?: return null
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
        return gameConfiguration.tryAcquireAsset(path)
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
        val modelAssetPath = "$skinPath${playerVariantSeparator}$modelPath"
        val md2Asset = gameConfiguration.tryAcquireAsset<Md2Asset>(modelAssetPath) ?: return null
        return md2Asset.model
    }

    private fun sanitizePlayerIndex(value: Int): Int {
        if (value in 0 until MAX_CLIENTS) {
            return value
        }
        if (value != GameConfiguration.UNKNOWN_PLAYER_INDEX) {
            Com.Warn("Ignoring invalid playerIndex=$value, using UNKNOWN_PLAYER_INDEX")
        }
        return GameConfiguration.UNKNOWN_PLAYER_INDEX
    }

    private fun assetExists(path: String): Boolean {
        return assetManager.fileHandleResolver.resolve(path) != null
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
}
