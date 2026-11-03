package org.demoth.cake.profile

import jake2.qcommon.vfs.DefaultVirtualFileSystem
import jake2.qcommon.vfs.VfsConfig
import jake2.qcommon.vfs.VfsEntry
import jake2.qcommon.vfs.VfsPackageSelection
import jake2.qcommon.vfs.VfsPackOrder
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

data class ProfileArchive(
    val path: String,
    val included: Boolean,
    val required: Boolean,
    val missing: Boolean,
)

/** Disk discovery does not mount packages or read their contents. Inspection is explicit. */
object ProfileArchives {
    val extensions = setOf("pak", "pk2", "pk3", "pkz", "zip")

    fun discover(profile: CakeGameProfile, automount: Boolean): List<ProfileArchive> {
        val root = Path.of(profile.basedir)
        val directories = setOfNotNull("baseq2", profile.gamemod)
        val paths = linkedSetOf<String>()
        for (directory in directories) {
            val folder = root.resolve(directory)
            if (!Files.isDirectory(folder)) continue
            Files.list(folder).use { stream ->
                stream.filter { Files.isRegularFile(it) && supported(it) }.forEach {
                    paths += "$directory/${it.fileName}"
                }
            }
        }
        paths += profile.includedArchives
        val order = VfsPackOrder()
        return paths.sortedWith { a, b ->
            val directory = a.substringBefore('/').compareTo(b.substringBefore('/'))
            if (directory != 0) directory else order.compare(a, b)
        }.map { path ->
            val required = VfsPackageSelection.isNumberedPak(path.substringAfterLast('/'))
            val available = path.substringBefore('/') in directories && Files.isRegularFile(root.resolve(path))
            ProfileArchive(path, required || automount || path in profile.includedArchives, required, !available)
        }
    }

    fun resolve(profile: CakeGameProfile, relative: String): Path {
        VfsPackageSelection(false, setOf(relative)) // Reject absolute paths and traversal.
        require(relative.substringBefore('/') in setOfNotNull("baseq2", profile.gamemod)) {
            "Archive is outside this profile's game directories: $relative"
        }
        val file = Path.of(profile.basedir).resolve(relative)
        require(supported(file)) { "Unsupported archive: $relative" }
        return file
    }

    fun inspect(file: Path): List<VfsEntry> {
        val vfs = DefaultVirtualFileSystem()
        vfs.configure(VfsConfig(null, "baseq2", null, false, false, false, emptyList(), extensions))
        val mounted = vfs.mountPackage(file.toString())
        require(mounted.success) { mounted.error ?: "Could not read archive: $file" }
        return vfs.listEntries()
    }

    /** Only validate packages about to become active. Existing missing selections remain editable. */
    fun validateActivation(profile: CakeGameProfile, before: Boolean, after: Boolean, newlyIncluded: String? = null) {
        if (newlyIncluded != null) inspect(resolve(profile, newlyIncluded))
        if (!before && after) discover(profile, true).filter { !it.missing && !it.required }.forEach {
            inspect(resolve(profile, it.path))
        }
    }

    fun readme(entries: List<VfsEntry>): VfsEntry? = entries
        .filter { '/' !in it.normalizedPath() && it.normalizedPath().substringBeforeLast('.').startsWith("readme") }
        .minByOrNull { it.normalizedPath() }

    private fun supported(path: Path) = path.fileName.toString().substringAfterLast('.', "").lowercase(Locale.ROOT) in extensions
}
