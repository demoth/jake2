package jake2.qcommon.network.commands;

import jake2.qcommon.MSG;
import jake2.qcommon.sizebuf_t;

/**
 * TE_EXPLOSION2
 * TE_GRENADE_EXPLOSION
 * TE_GRENADE_EXPLOSION_WATER
 * TE_PLASMA_EXPLOSION
 * TE_EXPLOSION1
 * TE_EXPLOSION1_BIG
 * TE_ROCKET_EXPLOSION
 * TE_ROCKET_EXPLOSION_WATER
 * TE_EXPLOSION1_NP
 * TE_BFG_EXPLOSION
 * TE_BFG_BIGEXPLOSION
 * TE_BOSSTPORT
 * TE_PLAIN_EXPLOSION
 * TE_CHAINFIST_SMOKE
 * TE_TRACKER_EXPLOSION
 * TE_TELEPORT_EFFECT
 * TE_DBALL_GOAL
 * TE_WIDOWSPLASH
 */
public class PointTEMessage extends TEMessage {
    public PointTEMessage(int style, float[] position) {
        super(style);
        this.position = position;
    }

    final float[] position;

    @Override
    protected void writeProperties(sizebuf_t buffer) {
        super.writeProperties(buffer);
        MSG.WritePos(buffer, position);
    }
}
