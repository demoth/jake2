package org.demoth.cake.profile

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jake2.qcommon.vfs.DefaultWritableFileSystem
import jake2.qcommon.vfs.VfsOpenOptions
import jake2.qcommon.vfs.VfsWriteOptions
import jake2.qcommon.vfs.WritableFileSystem
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

private const val PROFILES_VERSION_V1: Int = 1
private const val PROFILE_ID_DEFAULT: String = "default"

data class CakeGameProfile(val id: String, val basedir: String, val gamemod: String? = null) {
    fun normalized(): CakeGameProfile = CakeGameProfile(
        id = id.trim(),
        basedir = basedir.trim(),
        gamemod = gamemod?.trim()?.takeIf { it.isNotEmpty() },
    )
}

data class CakeProfilesConfig(
    val version: Int = PROFILES_VERSION_V1,
    val selectedProfileId: String = PROFILE_ID_DEFAULT,
    val profiles: List<CakeGameProfile> = emptyList(),
) {
    fun normalized(): CakeProfilesConfig = CakeProfilesConfig(
        version = version,
        selectedProfileId = selectedProfileId.trim(),
        profiles = profiles.map { it.normalized() },
    )
}

class CakeGameProfileStore(
    private val writableFactory: () -> WritableFileSystem = {
        val home = Path.of(System.getProperty("user.home"))
        DefaultWritableFileSystem(home.resolve(".cake"))
    },
    private val mapper: ObjectMapper = jacksonObjectMapper(),
) {
    private val logicalPath = "profiles.json"
    private val idPattern = Regex("^[A-Za-z0-9]+$")

    fun readConfig(): CakeProfilesConfig? {
        val writable = writableFactory()
        val opened = writable.openReadReal(logicalPath, VfsOpenOptions.DEFAULT)
        if (!opened.success || opened.value == null) {
            return null
        }
        opened.value.use { handle ->
            handle.inputStream().use { input ->
                return mapper.readValue<CakeProfilesConfig>(input).normalized()
            }
        }
    }

    fun writeConfig(config: CakeProfilesConfig): String {
        val normalized = config.normalized()
        validateConfig(normalized)

        val writable = writableFactory()
        val opened = writable.openWrite(logicalPath, VfsWriteOptions.TRUNCATE)
        if (!opened.success || opened.value == null) {
            throw IOException(opened.error ?: "Failed to open profiles config for write: $logicalPath")
        }
        opened.value.use { handle ->
            handle.outputStream().use { output ->
                mapper.writeValue(output, normalized)
            }
        }
        return writable.resolveWritePath(logicalPath)
    }

    fun readSelected(): CakeGameProfile? {
        val config = readConfig() ?: return null
        if (config.version != PROFILES_VERSION_V1) {
            return null
        }
        val selectedId = config.selectedProfileId.trim()
        if (selectedId.isBlank()) {
            return null
        }
        val profile = config.profiles.firstOrNull { it.id == selectedId } ?: return null
        validateProfile(profile)
        return profile
    }

    fun readSelectedProfileId(): String? {
        val config = readConfig() ?: return null
        if (config.version != PROFILES_VERSION_V1) {
            return null
        }
        val selectedId = config.selectedProfileId.trim()
        if (selectedId.isBlank()) {
            return null
        }
        if (config.profiles.none { it.id == selectedId }) {
            return null
        }
        return selectedId
    }

    fun upsertProfile(profile: CakeGameProfile, select: Boolean = true): String {
        val normalized = profile.normalized()
        validateProfile(normalized)

        val config = readConfig()?.normalized()
        val currentProfiles = config?.profiles.orEmpty()
        val updatedProfiles = currentProfiles
            .filterNot { it.id == normalized.id }
            .plus(normalized)

        val selectedId = if (select) normalized.id else (config?.selectedProfileId ?: normalized.id)
        return writeConfig(
            CakeProfilesConfig(
                version = PROFILES_VERSION_V1,
                selectedProfileId = selectedId,
                profiles = updatedProfiles,
            ),
        )
    }

    fun selectProfile(profileId: String): String {
        val normalizedId = profileId.trim()
        require(idPattern.matches(normalizedId)) { "Profile id must be alphanumeric" }

        val config = readConfig()?.normalized()
            ?: throw IllegalStateException("No profiles config found")
        require(config.profiles.any { it.id == normalizedId }) { "Unknown profile id: $normalizedId" }

        return writeConfig(
            config.copy(
                selectedProfileId = normalizedId,
            ),
        )
    }

    fun bootstrapDefault(defaultProfile: CakeGameProfile): CakeGameProfile {
        val existing = readSelected()
        if (existing != null) {
            return existing
        }
        upsertProfile(defaultProfile, select = true)
        return defaultProfile.normalized()
    }

    fun clear() {
        val writablePath = writableFactory().resolveWritePath(logicalPath) ?: return
        runCatching {
            Files.deleteIfExists(Path.of(writablePath))
        }
    }

    private fun validateConfig(config: CakeProfilesConfig) {
        require(config.version == PROFILES_VERSION_V1) {
            "Unsupported profiles config version ${config.version}, expected $PROFILES_VERSION_V1"
        }
        require(config.profiles.isNotEmpty()) { "Profiles list must not be empty" }
        require(config.selectedProfileId.isNotBlank()) { "selectedProfileId must not be blank" }
        val ids = config.profiles.map { it.id }
        require(ids.distinct().size == ids.size) { "Profile ids must be unique" }
        require(ids.contains(config.selectedProfileId)) { "selectedProfileId must reference an existing profile" }
        config.profiles.forEach(::validateProfile)
    }

    private fun validateProfile(profile: CakeGameProfile) {
        require(idPattern.matches(profile.id)) { "Profile id must be alphanumeric" }
        require(profile.basedir.isNotBlank()) { "Profile basedir must not be blank" }
        val basedirPath = try {
            Path.of(profile.basedir)
        } catch (_: Exception) {
            throw IllegalArgumentException("Profile basedir is invalid")
        }
        require(Files.isDirectory(basedirPath)) { "Profile basedir does not exist: ${profile.basedir}" }

        val mod = profile.gamemod
        if (mod != null) {
            require(mod.isNotBlank()) { "Profile gamemod must not be blank when set" }
            require(isSafeDirectoryToken(mod)) { "Profile gamemod must be a directory token, not a path" }
        }
    }

    private fun isSafeDirectoryToken(value: String): Boolean {
        if (value.contains("..")) return false
        if (value.contains('/')) return false
        if (value.contains('\\')) return false
        if (value.contains(':')) return false
        return true
    }

    companion object {
        const val CURRENT_PROFILES_VERSION: Int = PROFILES_VERSION_V1
        const val DEFAULT_PROFILE_ID: String = PROFILE_ID_DEFAULT
    }
}
