package org.demoth.cake.profile

import jake2.qcommon.Com
import jake2.qcommon.exec.Cbuf
import jake2.qcommon.exec.Cvar
import jake2.qcommon.exec.find
import jake2.qcommon.vfs.DefaultWritableFileSystem
import jake2.qcommon.vfs.WritableFileSystem
import org.demoth.cake.input.ClientBindings
import java.nio.file.Path

/** Profile-local config/history policy and writable paths, independent of the server's game directory.
 * The application supplies its active profile; persisted selection retains precedence for writes.
 */
class CakeProfilePersistence(
    private val gameProfileStore: CakeGameProfileStore,
    private val clientBindings: ClientBindings,
    private val activeProfile: () -> CakeGameProfile?,
    private val writableFactory: (String) -> WritableFileSystem = { id ->
        DefaultWritableFileSystem(Path.of(System.getProperty("user.home"), ".cake", id))
    },
) {
    private val profileConfigStore = CakeProfileConfigStore(writableFactory)
    private val consoleHistoryStore = CakeConsoleHistoryStore(writableFactory)

    fun activeProfileId(): String =
        selectedProfileId() ?: activeProfile()?.id ?: CakeGameProfileStore.DEFAULT_PROFILE_ID

    private fun currentConfigProfileId(): String? =
        selectedProfileId()?.takeIf { it.isNotBlank() }
            ?: activeProfile()?.id?.takeIf { it.isNotBlank() }

    fun selectedProfileId(): String? = try {
        gameProfileStore.readSelectedProfileId()
    } catch (_: Exception) {
        null
    }

    fun writable(): WritableFileSystem {
        return writableFactory(activeProfileId())
    }

    fun loadConfig() {
        val profileId = currentConfigProfileId() ?: return
        try {
            val configText = profileConfigStore.readConfig(profileId) ?: return
            Cbuf.AddAndExecuteScript(configText)
            Cvar.getInstance().updateLatchedVars()
            Com.Printf("Loaded Cake profile config for '$profileId'\n")
        } catch (e: Exception) {
            Com.Warn("Failed to load Cake profile config for '$profileId': ${e.message}\n")
        }
    }

    fun saveConfig(explicit: Boolean = false) {
        if (!explicit && !Cvar.getInstance().find<Boolean>("cfg_autosave").typedValue) return
        val profileId = currentConfigProfileId() ?: return
        try {
            val path = profileConfigStore.writeConfig(
                profileId = profileId,
                bindings = clientBindings.listBindings(),
            )
            Com.Printf("Saved Cake profile config: $path\n")
        } catch (e: Exception) {
            Com.Warn("Failed to save Cake profile config for '$profileId': ${e.message}\n")
        }
    }

    fun loadHistory(): List<String> {
        val profileId = activeProfileId()
        return try {
            consoleHistoryStore.readHistory(profileId)
        } catch (e: Exception) {
            Com.Warn("Failed to load console history for '$profileId': ${e.message}\n")
            emptyList()
        }
    }

    fun saveHistory(commands: List<String>) {
        if (!Cvar.getInstance().find<Boolean>("cfg_autosave").typedValue) return
        val profileId = activeProfileId()
        try {
            consoleHistoryStore.writeHistory(profileId, commands)
        } catch (e: Exception) {
            Com.Warn("Failed to save console history for '$profileId': ${e.message}\n")
        }
    }
}
