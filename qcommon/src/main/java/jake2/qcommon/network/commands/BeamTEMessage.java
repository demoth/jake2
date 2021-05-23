package jake2.qcommon.network.commands;

import jake2.qcommon.MSG;
import jake2.qcommon.sizebuf_t;

/**
 * TE_PARASITE_ATTACK
 * TE_MEDIC_CABLE_ATTACK
 */
public class BeamTEMessage extends TEMessage {
    public BeamTEMessage(int style, int ownerIndex, float[] origin, float[] destination) {
        super(style);
        this.ownerIndex = ownerIndex;
        this.origin = origin;
        this.destination = destination;
    }

    final int ownerIndex;
    final float[] origin;
    final float[] destination;

    @Override
    protected void writeProperties(sizebuf_t buffer) {
        super.writeProperties(buffer);
        MSG.WriteShort(buffer, ownerIndex);
        MSG.WritePos(buffer, origin);
        MSG.WritePos(buffer, destination);
    }
}
