package jake2.qcommon

import jake2.qcommon.Defines.PITCH
import jake2.qcommon.Defines.ROLL
import jake2.qcommon.Defines.YAW
import jake2.qcommon.util.Math3D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PMoveTest {

    companion object {
        private const val TOLERANCE = 0.0001f
    }

    @Test
    fun `teleport flag forces yaw only view angles`() {
        val pm = defaultPmove(Defines.PM_FREEZE)
        pm.s.pm_flags = Defines.PMF_TIME_TELEPORT.toByte()
        pm.s.delta_angles[YAW] = Math3D.ANGLE2SHORT(30f).toShort()
        pm.cmd.angles[YAW] = Math3D.ANGLE2SHORT(10f).toShort()
        pm.cmd.angles[PITCH] = Math3D.ANGLE2SHORT(45f).toShort()
        pm.cmd.angles[ROLL] = Math3D.ANGLE2SHORT(25f).toShort()

        PMove.Pmove(pm)

        val expectedYaw = Math3D.SHORT2ANGLE((pm.cmd.angles[YAW] + pm.s.delta_angles[YAW]).toShort().toInt())
        assertEquals(expectedYaw, pm.viewangles[YAW], TOLERANCE)
        assertEquals(0f, pm.viewangles[PITCH], TOLERANCE)
        assertEquals(0f, pm.viewangles[ROLL], TOLERANCE)
    }

    @Test
    fun `pitch is clamped to 89 degrees upper bound`() {
        val pm = defaultPmove(Defines.PM_FREEZE)
        pm.cmd.angles[PITCH] = Math3D.ANGLE2SHORT(120f).toShort()

        PMove.Pmove(pm)

        assertEquals(89f, pm.viewangles[PITCH], TOLERANCE)
    }

    @Test
    fun `pitch below minus 89 is currently not clamped`() {
        val pm = defaultPmove(Defines.PM_FREEZE)
        pm.cmd.angles[PITCH] = Math3D.ANGLE2SHORT(300f).toShort()

        PMove.Pmove(pm)

        // Existing behavior: because cmd angles are stored in signed short, 300deg becomes about -60deg
        // before clamp checks run, so the lower-bound clamp branch is not hit.
        val expectedRawPitch = Math3D.SHORT2ANGLE(pm.cmd.angles[PITCH].toInt())
        assertEquals(expectedRawPitch, pm.viewangles[PITCH], TOLERANCE)
    }

    @Test
    fun `command msec is handled as unsigned byte`() {
        val pm = defaultPmove(Defines.PM_FREEZE)
        pm.cmd.msec = 250.toByte() // signed byte value -6

        PMove.Pmove(pm)

        assertEquals(0.250f, PMove.pml.frametime, TOLERANCE)
    }

    @Test
    fun `pm time drops by at least one tick for tiny command msec`() {
        val pm = defaultPmove(Defines.PM_NORMAL)
        pm.s.pm_time = 10.toByte()
        pm.cmd.msec = 1.toByte() // >>> 3 would be 0 without the min-1 safeguard

        PMove.Pmove(pm)

        assertEquals(9, pm.s.pm_time.toInt() and 0xFF)
    }

    @Test
    fun `pm time expiration clears timing flags`() {
        val pm = defaultPmove(Defines.PM_NORMAL)
        pm.s.pm_time = 2.toByte()
        pm.cmd.msec = 16.toByte() // 16 >>> 3 == 2, consumes pm_time fully
        pm.s.pm_flags = (Defines.PMF_TIME_WATERJUMP or Defines.PMF_TIME_LAND or Defines.PMF_TIME_TELEPORT).toByte()

        PMove.Pmove(pm)

        assertEquals(0, pm.s.pm_time.toInt() and 0xFF)
        val timingFlags = Defines.PMF_TIME_WATERJUMP or Defines.PMF_TIME_LAND or Defines.PMF_TIME_TELEPORT
        assertEquals(0, pm.s.pm_flags.toInt() and timingFlags)
    }

    @Test
    fun `dead player move commands are cleared`() {
        val pm = defaultPmove(Defines.PM_DEAD)
        pm.cmd.forwardmove = 100
        pm.cmd.sidemove = -50
        pm.cmd.upmove = 30

        PMove.Pmove(pm)

        assertEquals(0, pm.cmd.forwardmove.toInt())
        assertEquals(0, pm.cmd.sidemove.toInt())
        assertEquals(0, pm.cmd.upmove.toInt())
    }

    @Test
    fun `spectator uses fly move and snap position`() {
        val pm = defaultPmove(Defines.PM_SPECTATOR)
        pm.cmd.forwardmove = 200
        pm.cmd.msec = 100.toByte() // 100ms step

        PMove.Pmove(pm)

        assertEquals(22f, pm.viewheight, TOLERANCE)
        // Non-zero movement proves spectator branch ran PM_FlyMove + PM_SnapPosition and returned.
        assertTrue(pm.s.origin[0].toInt() != 0 || pm.s.origin[1].toInt() != 0 || pm.s.origin[2].toInt() != 0)
    }

    @Test
    fun `waterjump falling clears waterjump flags`() {
        val pm = defaultPmove(Defines.PM_NORMAL)
        pm.s.pm_flags = Defines.PMF_TIME_WATERJUMP.toByte()
        pm.s.pm_time = 0
        pm.s.velocity[2] = (-8).toShort() // negative vertical velocity in 1/8 units

        PMove.Pmove(pm)

        val timingFlags = Defines.PMF_TIME_WATERJUMP or Defines.PMF_TIME_LAND or Defines.PMF_TIME_TELEPORT
        assertEquals(0, pm.s.pm_flags.toInt() and timingFlags)
        assertEquals(0, pm.s.pm_time.toInt() and 0xFF)
    }

    @Test
    fun `waterjump upward keeps waterjump flag`() {
        val pm = defaultPmove(Defines.PM_NORMAL)
        pm.s.pm_flags = Defines.PMF_TIME_WATERJUMP.toByte()
        pm.s.pm_time = 0
        pm.s.velocity[2] = 400.toShort() // upward velocity should avoid immediate cancel

        PMove.Pmove(pm)

        assertTrue((pm.s.pm_flags.toInt() and Defines.PMF_TIME_WATERJUMP) != 0)
    }

    @Test
    fun `watermove branch applies upmove while submerged`() {
        val pm = defaultPmove(Defines.PM_NORMAL)
        pm.cmd.upmove = 200
        pm.cmd.msec = 100.toByte()
        pm.pointcontents = pmove_t.PointContentsAdapter { _ -> Defines.MASK_WATER }

        PMove.Pmove(pm)

        // In water branch, upmove contributes to wishvel[2], producing upward velocity.
        assertTrue(pm.s.velocity[2].toInt() > 0)
    }

    @Test
    fun `snapinitial runs initial snap and can nudge origin to valid grid`() {
        val pm = defaultPmove(Defines.PM_NORMAL)
        pm.snapinitial = true
        pm.s.pm_flags = Defines.PMF_TIME_TELEPORT.toByte() // skip later movement path so snap result stays visible

        val traceAdapter = object : pmove_t.TraceAdapter() {
            override fun trace(start: FloatArray, mins: FloatArray, maxs: FloatArray, end: FloatArray): trace_t {
                val trace = trace_t()
                trace.fraction = 1f
                trace.endpos[0] = end[0]
                trace.endpos[1] = end[1]
                trace.endpos[2] = end[2]
                trace.plane.normal[2] = 1f
                // PM_GoodPosition is called with start==end. Reject (0,0,0), accept any snapped alternative.
                trace.allsolid = start[0] == 0f && start[1] == 0f && start[2] == 0f
                return trace
            }
        }
        pm.trace = traceAdapter

        PMove.Pmove(pm)

        assertTrue(pm.s.origin[0].toInt() != 0 || pm.s.origin[1].toInt() != 0 || pm.s.origin[2].toInt() != 0)
    }

    @Test
    fun `airmove uses airaccelerate when enabled`() {
        val previousAirAccel = PMove.pm_airaccelerate
        try {
            PMove.pm_airaccelerate = 1f // force PM_AirMove -> PM_AirAccelerate branch

            val pm = defaultPmove(Defines.PM_NORMAL)
            pm.cmd.forwardmove = 300
            pm.cmd.msec = 100.toByte() // 100ms frametime

            PMove.Pmove(pm)

            // PM_AirAccelerate caps wishspd to 30, so x-velocity increase is 30 u/s -> 240 in 1/8 units.
            // If PM_Accelerate branch ran instead, this would be much larger (about 2400 here).
            assertEquals(240, pm.s.velocity[0].toInt())
            assertTrue(pm.groundentity == null)
        } finally {
            PMove.pm_airaccelerate = previousAirAccel
        }
    }

    @Test
    fun `Pmove delegates through processor seam`() {
        val pm = defaultPmove(Defines.PM_FREEZE)
        var invoked = false
        PMove.setProcessor { delegated ->
            invoked = true
            delegated.viewheight = 123f
        }
        try {
            PMove.Pmove(pm)
        } finally {
            PMove.resetProcessor()
        }

        assertTrue(invoked)
        assertEquals(123f, pm.viewheight, TOLERANCE)
    }

    private fun defaultPmove(pmType: Int): pmove_t {
        val pm = pmove_t()
        pm.s.pm_type = pmType
        pm.s.gravity = 800
        pm.cmd.msec = 16
        pm.trace = object : pmove_t.TraceAdapter() {
            override fun trace(start: FloatArray, mins: FloatArray, maxs: FloatArray, end: FloatArray): trace_t {
                val trace = trace_t()
                trace.fraction = 1f
                trace.endpos[0] = end[0]
                trace.endpos[1] = end[1]
                trace.endpos[2] = end[2]
                trace.plane.normal[2] = 1f
                return trace
            }
        }
        pm.pointcontents = pmove_t.PointContentsAdapter { _ -> 0 }
        return pm
    }
}
