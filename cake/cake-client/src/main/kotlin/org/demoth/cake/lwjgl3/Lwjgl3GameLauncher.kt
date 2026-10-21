package org.demoth.cake.lwjgl3

import org.demoth.cake.hosting.UnavailableCakeHost

/** Remote-only desktop client. */
object Lwjgl3GameLauncher {
    @JvmStatic
    fun main(args: Array<String>) {
        CakeDesktopLauncher.launch(args, UnavailableCakeHost())
    }
}
