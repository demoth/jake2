package org.demoth.cake.assets

import java.util.Locale

enum class ResourceKind(val fallbackAssetPath: String?, val tolerableMissing: Boolean) {
    BSP(null, tolerableMissing = false),
    PCX("_missing.pcx", tolerableMissing = false),
    WAL("_missing.wal", tolerableMissing = false),
    MD2("_missing.md2", tolerableMissing = false),
    SP2("_missing.sp2", tolerableMissing = false),
    SOUND(null, tolerableMissing = true);

    companion object {
        fun fromPath(path: String): ResourceKind? {
            val normalized = path.replace('\\', '/').lowercase(Locale.ROOT)
            return when {
                normalized.endsWith(".bsp") -> BSP
                normalized.endsWith(".pcx") -> PCX
                normalized.endsWith(".wal") -> WAL
                normalized.endsWith(".md2") -> MD2
                normalized.endsWith(".sp2") -> SP2
                normalized.startsWith("sound/") || normalized.endsWith(".wav") || normalized.endsWith(".ogg") -> SOUND
                else -> null
            }
        }
    }
}
