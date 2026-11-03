package org.demoth.cake.profile

import jake2.qcommon.Defines.CVAR_ARCHIVE
import jake2.qcommon.exec.Cvar
import jake2.qcommon.exec.CvarTypes
import jake2.qcommon.exec.find

/** The profile config owns automount; profiles.json owns the relative archive selection. */
object CakePackageSettings {
    const val AUTOMOUNT = "fs_automount"

    fun register() {
        Cvar.getInstance().register(AUTOMOUNT, CvarTypes.BOOLEAN, true, CVAR_ARCHIVE,
            "Automatically load optional archives; changes apply when disconnected and the local host is stopped")
    }

    fun automount(): Boolean {
        val cvars = Cvar.getInstance()
        return !cvars.isRegistered(AUTOMOUNT) || cvars.find<Boolean>(AUTOMOUNT).typedValue
    }

    fun reset() {
        register()
        Cvar.getInstance().find<Boolean>(AUTOMOUNT).forceSet(true)
    }
}
