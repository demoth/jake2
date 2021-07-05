package jake2.qcommon.network.messages.server;

import jake2.qcommon.MSG;
import jake2.qcommon.sizebuf_t;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

import static jake2.qcommon.Defines.TE_MEDIC_CABLE_ATTACK;
import static jake2.qcommon.Defines.TE_PARASITE_ATTACK;

/**
 * TE_PARASITE_ATTACK
 * TE_MEDIC_CABLE_ATTACK
 */
public class BeamTEMessage extends TEMessage {

    public static final Collection<Integer> SUBTYPES = Set.of(
            TE_PARASITE_ATTACK,
            TE_MEDIC_CABLE_ATTACK
    );

    public BeamTEMessage(int style) {
        super(style);
    }

    public BeamTEMessage(int style, int ownerIndex, float[] origin, float[] destination) {
        super(style);
        this.ownerIndex = ownerIndex;
        this.origin = origin;
        this.destination = destination;
    }

    public int ownerIndex;
    public float[] origin;
    public float[] destination;

    @Override
    protected void writeProperties(sizebuf_t buffer) {
        super.writeProperties(buffer);
        MSG.WriteShort(buffer, ownerIndex);
        MSG.WritePos(buffer, origin);
        MSG.WritePos(buffer, destination);
    }

    @Override
    void parse(sizebuf_t buffer) {
        ownerIndex = MSG.ReadShort(buffer);
        origin = new float[3];
        MSG.ReadPos(buffer, origin);
        destination = new float[3];
        MSG.ReadPos(buffer, destination);
    }

    @Override
    int getSize() {
        return 2 + 2 + 2 * 2 * 3;
    }

    @Override
    Collection<Integer> getSupportedStyles() {
        return SUBTYPES;
    }

    @Override
    public String toString() {
        return "BeamTEMessage{" +
                "ownerIndex=" + ownerIndex +
                ", origin=" + Arrays.toString(origin) +
                ", destination=" + Arrays.toString(destination) +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BeamTEMessage that = (BeamTEMessage) o;

        if (ownerIndex != that.ownerIndex) return false;
        if (!Arrays.equals(origin, that.origin)) return false;
        return Arrays.equals(destination, that.destination);
    }

    @Override
    public int hashCode() {
        int result = ownerIndex;
        result = 31 * result + Arrays.hashCode(origin);
        result = 31 * result + Arrays.hashCode(destination);
        return result;
    }
}
