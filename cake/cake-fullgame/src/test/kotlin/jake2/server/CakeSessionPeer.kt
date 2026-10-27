package jake2.server

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import jake2.qcommon.Defines
import jake2.qcommon.MainCommon
import jake2.qcommon.exec.Cbuf
import jake2.qcommon.exec.Cvar
import jake2.qcommon.exec.find
import jake2.qcommon.network.NET
import jake2.qcommon.network.Netchan
import org.demoth.cake.Cake
import org.demoth.cake.CakeStartupBootstrap
import org.demoth.cake.fullgame.EmbeddedCakeHost
import org.demoth.cake.hosting.HostMode
import org.demoth.cake.hosting.HostRequest
import org.demoth.cake.hosting.HostStartResult
import org.demoth.cake.profile.CakeGameProfile
import org.demoth.cake.profile.CakeGameProfileStore
import java.nio.file.Files
import java.nio.file.Path

/** Separate JVM peer for the opt-in gameplay test: real thin Cake client or standalone host. */
object CakeSessionPeer {
    @JvmStatic
    fun main(args: Array<String>) {
        val profile = CakeGameProfile("peer", args[1])
        val ready = Path.of(args[2])
        val deadline = System.nanoTime() + 60_000_000_000L
        if (args[0] == "server") {
            MainCommon.registerCvars(false)
            Netchan.Netchan_Init()
            Cvar.getInstance().find<Int>("port").forceSet(0)
            val host = EmbeddedCakeHost()
            try {
                host.initialize()
                check(host.start(HostRequest(profile, "base1", HostMode.DEATHMATCH)) == HostStartResult.Started)
                Files.writeString(ready, NET.udpEndpoint(Defines.NS_SERVER)!!.localAddress.port.toString())
                var previous = System.nanoTime()
                while (System.nanoTime() < deadline) {
                    Thread.sleep(5)
                    val now = System.nanoTime()
                    host.update((now - previous) / 1_000_000)
                    previous = now
                }
            } finally { host.dispose() }
        } else {
            CakeGameProfileStore().upsertProfile(profile)
            val startup = CakeStartupBootstrap.bootstrap()
            Lwjgl3Application(object : ApplicationAdapter() {
                private lateinit var cake: Cake
                override fun create() {
                    cake = Cake(startup) // The same remote-only composition as cake-client.
                    cake.create()
                    Cbuf.AddText("connect 127.0.0.1:${args[3]}")
                }
                override fun render() {
                    cake.render()
                    val state = Cake::class.java.getDeclaredField("networkState").apply {
                        isAccessible = true
                    }.get(cake).toString()
                    if (state == "ACTIVE" && !Files.exists(ready)) Files.writeString(ready, "active")
                    if (System.nanoTime() >= deadline) Gdx.app.exit()
                }
                override fun dispose() { cake.dispose() }
            }, Lwjgl3ApplicationConfiguration().apply {
                setInitialVisible(false)
                disableAudio(true)
                setWindowedMode(320, 240)
                setForegroundFPS(60)
                setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL32, 3, 2)
                setPauseWhenLostFocus(false)
                setPauseWhenMinimized(false)
            })
        }
    }
}
