package jake2.game

import jake2.qcommon.GameImports
import jake2.qcommon.ServerEntity
import jake2.qcommon.exec.Cvar
import jake2.qcommon.exec.CvarType
import jake2.qcommon.exec.CvarTypes
import jake2.qcommon.network.MulticastTypes
import jake2.qcommon.network.messages.server.ServerMessage
import jake2.qcommon.pmove_t
import jake2.qcommon.trace_t

/** Supplies a fresh engine cvar registry and an empty world for gameplay unit tests. */
internal class TestGameImports : GameImports {
    val cvars = Cvar().apply {
        register("deathmatch", CvarTypes.BOOLEAN, false)
        register("coop", CvarTypes.BOOLEAN, false)
        register("cheats", CvarTypes.BOOLEAN, false)
        register("dedicated", CvarTypes.BOOLEAN, false)
        register("maxclients", CvarTypes.INTEGER, 1)
        register("dmflags", CvarTypes.INTEGER, 0)
        register("fraglimit", CvarTypes.INTEGER, 0)
        register("timelimit", CvarTypes.FLOAT, 0f)
        register("password", CvarTypes.STRING, "")
        register("spectator_password", CvarTypes.STRING, "")
        register("needpass", CvarTypes.INTEGER, 0)
    }

    override fun <T : Any> registerCvar(name: String, type: CvarType<T>, defaultValue: T, flags: Int, description: String?) =
        cvars.register(name, type, defaultValue, flags, description)

    override fun <T> findCvar(name: String, type: Class<T>) = cvars.find(name, type)

    override fun bprintf(printlevel: Int, s: String?) = Unit
    override fun cprintf(ent: ServerEntity?, printlevel: Int, s: String?) = Unit
    override fun centerprintf(ent: ServerEntity?, s: String?) = Unit
    override fun sound(ent: ServerEntity?, channel: Int, soundindex: Int, volume: Float, attenuation: Float, timeofs: Float) = Unit
    override fun positioned_sound(origin: FloatArray?, ent: ServerEntity?, channel: Int, soundinedex: Int,
                                  volume: Float, attenuation: Float, timeofs: Float) = Unit
    override fun configstring(num: Int, string: String?) = Unit
    override fun error(err: String?) { throw IllegalStateException(err) }

    override fun modelindex(name: String?) = 0
    override fun soundindex(name: String?) = 0
    override fun imageindex(name: String?) = 0
    override fun setmodel(ent: ServerEntity?, name: String?) = Unit
    override fun trace(start: FloatArray, mins: FloatArray?, maxs: FloatArray?, end: FloatArray,
                       passent: ServerEntity?, contentmask: Int) = trace_t().apply {
        fraction = 1f
        endpos = end.copyOf()
    }

    override fun inPHS(p1: FloatArray?, p2: FloatArray?) = true
    override fun SetAreaPortalState(portalnum: Int, open: Boolean) = Unit
    override fun AreasConnected(area1: Int, area2: Int) = true
    override fun linkentity(ent: ServerEntity?) = Unit
    override fun unlinkentity(ent: ServerEntity?) = Unit
    override fun BoxEdicts(mins: FloatArray?, maxs: FloatArray?, list: Array<out ServerEntity?>?, maxcount: Int, areatype: Int) = 0
    override fun Pmove(pmove: pmove_t?) = Unit
    override fun AddCommandString(text: String?) = Unit
    override fun getPointContents(p: FloatArray?) = 0
    override fun multicastMessage(origin: FloatArray?, msg: ServerMessage?, to: MulticastTypes?) = Unit
    override fun unicastMessage(index: Int, msg: ServerMessage?, reliable: Boolean) = Unit
}
