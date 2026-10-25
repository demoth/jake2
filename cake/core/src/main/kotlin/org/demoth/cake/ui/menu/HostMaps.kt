package org.demoth.cake.ui.menu

import jake2.qcommon.Com
import jake2.qcommon.Defines
import jake2.qcommon.filesystem.BspHeader
import jake2.qcommon.parseEntities
import jake2.qcommon.vfs.DefaultVirtualFileSystem
import jake2.qcommon.vfs.VfsConfig
import jake2.qcommon.vfs.VfsOpenOptions
import org.demoth.cake.profile.CakeGameProfile
import java.nio.ByteBuffer
import java.nio.file.Path

data class HostMap(val name: String, val title: String? = null) {
    override fun toString(): String = title?.let { "$name — $it" } ?: name
}

/** Read only BSP metadata, using the hosting profile rather than the connected server's mod. */
fun loadHostMaps(profile: CakeGameProfile?): List<HostMap> {
    if (profile == null) return emptyList()
    val vfs = DefaultVirtualFileSystem()
    vfs.configure(VfsConfig(Path.of(profile.basedir), "baseq2", profile.gamemod,
        false, false, false, emptyList(), setOf("pak", "pk2", "pk3", "pkz", "zip")))
    return vfs.listEntries().mapNotNull { entry ->
        val path = entry.normalizedPath
        if (!path.startsWith("maps/") || !path.endsWith(".bsp")) return@mapNotNull null
        val name = path.removePrefix("maps/").removeSuffix(".bsp")
        if (!name.matches(Regex("[A-Za-z0-9_/-]+"))) return@mapNotNull null
        val title = try {
            val opened = vfs.openRead(path, VfsOpenOptions.DEFAULT)
            val handle = opened.value ?: error("Cannot read $path")
            handle.use {
                val stream = it.inputStream()
                val headerSize = 8 + Defines.HEADER_LUMPS * 8
                val header = BspHeader(ByteBuffer.wrap(stream.readNBytes(headerSize)))
                val entities = header.lumps[Defines.LUMP_ENTITIES]
                require(entities.offset >= headerSize && entities.length >= 0 &&
                    entities.offset.toLong() + entities.length <= it.size()) { "Invalid entity lump" }
                stream.skipNBytes((entities.offset - headerSize).toLong())
                val bytes = stream.readNBytes(entities.length)
                require(bytes.size == entities.length) { "Truncated entity lump" }
                parseEntities(String(bytes, Charsets.ISO_8859_1).trimEnd('\u0000'))
                    .firstOrNull { entity -> entity["classname"] == "worldspawn" }
                    ?.get("message")?.replace(Regex("\\s+"), " ")?.trim()?.takeIf(String::isNotEmpty)
            }
        } catch (error: Exception) {
            Com.Warn("Cannot read map title for '$path': ${error.message}\n")
            null
        }
        HostMap(name, title)
    }.sortedBy { it.name }
}
