package org.demoth.cake.stages.ingame.effects

import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.utils.Disposable
import org.demoth.cake.assets.Md2Asset

/**
 * Effect-specific assets that are not guaranteed to be referenced by config strings.
 *
 * Ownership model:
 * - This catalog tracks only assets loaded through [precache] in [ownedAssetTypes].
 * - On [dispose], it unloads only those tracked assets, preserving unrelated `AssetManager` users.
 *
 * Extension point:
 * add new effect resources to [EFFECT_MD2_MODELS] / [EFFECT_SOUNDS] and keep paths in
 * game-relative form (for sounds, include `sound/` prefix).
 */
class EffectAssetCatalog(
    private val assetManager: AssetManager
) : Disposable {
    private val ownedAssetTypes = mutableMapOf<String, Class<*>>()

    /**
     * Best-effort preload:
     * missing files are skipped without logging to keep optional mod assets non-fatal.
     */
    fun precache() {
        EFFECT_MD2_MODELS.forEach { path ->
            loadIfExists(path, Md2Asset::class.java)
        }
        EFFECT_SOUNDS.forEach { path ->
            loadIfExists(path, Sound::class.java)
        }
    }

    fun getModel(path: String): Md2Asset? {
        return if (assetManager.isLoaded(path, Md2Asset::class.java)) {
            assetManager.get(path, Md2Asset::class.java)
        } else {
            null
        }
    }

    fun getSound(path: String): Sound? {
        return if (assetManager.isLoaded(path, Sound::class.java)) {
            assetManager.get(path, Sound::class.java)
        } else {
            null
        }
    }

    override fun dispose() {
        ownedAssetTypes.forEach { (path, type) ->
            @Suppress("UNCHECKED_CAST")
            val typed = type as Class<Any>
            if (assetManager.isLoaded(path, typed)) {
                assetManager.unload(path)
            }
        }
        ownedAssetTypes.clear()
    }

    private fun <T> loadIfExists(path: String, type: Class<T>) {
        if (ownedAssetTypes[path] != null) {
            return
        }
        if (assetManager.fileHandleResolver.resolve(path) == null) {
            return
        }
        if (!assetManager.isLoaded(path, type)) {
            assetManager.load(path, type)
            assetManager.finishLoadingAsset<T>(path)
        }
        ownedAssetTypes[path] = type
    }
}

private val EFFECT_MD2_MODELS = listOf(
    "models/objects/explode/tris.md2",
    "models/objects/smoke/tris.md2",
    "models/objects/flash/tris.md2",
    "models/monsters/parasite/segment/tris.md2",
    "models/ctf/segment/tris.md2",
    "models/monsters/parasite/tip/tris.md2",
    "models/objects/r_explode/tris.md2",
    "models/objects/r_explode2/tris.md2",
)

private val EFFECT_SOUNDS = listOf(
    "sound/world/ric1.wav",
    "sound/world/ric2.wav",
    "sound/world/ric3.wav",
    "sound/weapons/lashit.wav",
    "sound/world/spark5.wav",
    "sound/world/spark6.wav",
    "sound/world/spark7.wav",
    "sound/weapons/railgf1a.wav",
    "sound/weapons/rocklx1a.wav",
    "sound/weapons/grenlx1a.wav",
    "sound/weapons/xpld_wat.wav",
    "sound/misc/bigtele.wav",
    "sound/weapons/disrupthit.wav",
    "sound/infantry/infatck1.wav",
    "sound/soldier/solatck1.wav",
    "sound/soldier/solatck2.wav",
    "sound/soldier/solatck3.wav",
    "sound/gunner/gunatck2.wav",
    "sound/gunner/gunatck3.wav",
    "sound/flyer/flyatck3.wav",
    "sound/medic/medatck1.wav",
    "sound/hover/hovatck1.wav",
    "sound/floater/fltatck1.wav",
    "sound/tank/tnkatck1.wav",
    "sound/tank/tnkatck3.wav",
    "sound/tank/tnkatk2a.wav",
    "sound/tank/tnkatk2b.wav",
    "sound/tank/tnkatk2c.wav",
    "sound/tank/tnkatk2d.wav",
    "sound/tank/tnkatk2e.wav",
    "sound/tank/rocket.wav",
    "sound/chick/chkatck2.wav",
    "sound/makron/blaster.wav",
    "sound/boss3/xfire.wav",
    "sound/weapons/disint2.wav",
)
