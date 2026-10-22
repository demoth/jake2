package org.demoth.cake

import jake2.qcommon.MainCommon
import jake2.qcommon.Com
import jake2.qcommon.exec.Cbuf
import jake2.qcommon.exec.Cmd
import jake2.qcommon.exec.Cvar
import org.demoth.cake.input.ClientBindings
import org.demoth.cake.profile.CakeGameProfile
import org.demoth.cake.profile.CakeGameProfileStore
import org.demoth.cake.profile.CakeProfileConfigStore
import java.nio.file.Files
import java.nio.file.Path

data class CakeStartupContext(
    val activeProfile: CakeGameProfile?,
    val videoMode: CakeVideoModeSettings,
    val clientBindings: ClientBindings,
)

object CakeStartupBootstrap {
    private var initialized = false

    @Synchronized
    fun ensureInitialized() {
        if (initialized) return
        Cmd.Init()
        Cvar.Init()
        MainCommon.registerCvars(false)
        CakeCvars.registerAll()
        initialized = true
    }

    fun bootstrap(
        gameProfileStore: CakeGameProfileStore = CakeGameProfileStore(),
        profileConfigStore: CakeProfileConfigStore = CakeProfileConfigStore(),
    ): CakeStartupContext {
        ensureInitialized()
        val clientBindings = ClientBindings()
        val activeProfile = resolveStartupProfile(gameProfileStore)
        if (activeProfile != null) {
            loadProfileConfig(activeProfile.id, profileConfigStore)
        }
        return CakeStartupContext(
            activeProfile = activeProfile,
            videoMode = CakeVideoModeSettings.fromCvars(),
            clientBindings = clientBindings,
        )
    }

    private fun loadProfileConfig(profileId: String, profileConfigStore: CakeProfileConfigStore) {
        try {
            val configText = profileConfigStore.readConfig(profileId) ?: return
            // TODO: cleanup: avoid "after restart" messages for saved video settings applied before window creation.
            Cbuf.AddAndExecuteScript(configText)
            Cvar.getInstance().updateLatchedVars()
            Com.Printf("Loaded Cake profile config for '$profileId'\n")
        } catch (e: Exception) {
            Com.Warn("Failed to load Cake profile config for '$profileId': ${e.message}\n")
        }
    }

    private fun resolveStartupProfile(gameProfileStore: CakeGameProfileStore): CakeGameProfile? {
        val persistedProfile = try {
            gameProfileStore.readSelected()
        } catch (e: Exception) {
            Com.Warn("Failed to read persisted Cake game profile: ${e.message}\n")
            null
        }
        if (persistedProfile != null) {
            return persistedProfile
        }

        val fallbackProfile = autodetectedStartupProfile(autodetectSteamBasedir())
        if (fallbackProfile == null) {
            Com.Warn("No Cake profile is configured and Quake2 basedir autodetect failed. Open the profile editor to configure one.\n")
            return null
        }
        return try {
            gameProfileStore.bootstrapDefault(fallbackProfile)
        } catch (e: Exception) {
            Com.Warn("Failed to bootstrap default Cake profile: ${e.message}\n")
            fallbackProfile
        }
    }

    private fun autodetectedStartupProfile(autodetectedBasedir: String?): CakeGameProfile? {
        val basedir = autodetectedBasedir?.trim().takeIf { !it.isNullOrBlank() } ?: return null
        return CakeGameProfile(
            id = CakeGameProfileStore.DEFAULT_PROFILE_ID,
            basedir = basedir,
            gamemod = null,
        )
    }

    fun autodetectSteamBasedir(): String? {
        val home = System.getProperty("user.home")?.takeIf { it.isNotBlank() }
        val candidates = mutableListOf<Path>()
        if (home != null) {
            candidates.add(Path.of(home, ".steam", "steam", "steamapps", "common", "Quake 2"))
            candidates.add(Path.of(home, "Library", "Application Support", "Steam", "steamapps", "common", "Quake 2"))
        }

        candidates.add(Path.of("c:", "Program Files (x86)", "Steam", "steamapps", "common", "Quake 2"))
        candidates.add(Path.of("c:", "Program Files", "Steam", "steamapps", "common", "Quake 2"))

        return candidates.firstOrNull { isUsableQ2Basedir(it) }?.toAbsolutePath()?.normalize()?.toString()
    }

    private fun isUsableQ2Basedir(path: Path): Boolean {
        if (!Files.isDirectory(path)) return false
        return Files.isDirectory(path.resolve("baseq2"))
    }
}
