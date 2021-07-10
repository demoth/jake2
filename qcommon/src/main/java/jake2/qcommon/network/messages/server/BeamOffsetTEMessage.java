package jake2.qcommon.network.messages.server;

import jake2.qcommon.sizebuf_t;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

import static jake2.qcommon.Defines.TE_GRAPPLE_CABLE;

public class BeamOffsetTEMessage extends BeamTEMessage {
    public static final Collection<Integer> SUBTYPES = Set.of(
            TE_GRAPPLE_CABLE
    );

    public float[] offset;

    public BeamOffsetTEMessage(int style) {
        super(style);
    }

    public BeamOffsetTEMessage(int style, int ownerIndex, float[] origin, float[] destination, float[] offset) {
        this(style);
        this.ownerIndex = ownerIndex;
        this.origin = origin;
        this.destination = destination;
        this.offset = offset;
    }

    @Override
    protected void writeProperties(sizebuf_t buffer) {
        super.writeProperties(buffer);
        buffer.writePos(offset);
    }

    @Override
    public void parse(sizebuf_t buffer) {
        super.parse(buffer);
        offset = new float[3];
        buffer.readPos(offset);
    }

    @Override
    public int getSize() {
        return super.getSize() + 6;
    }

    @Override
    Collection<Integer> getSupportedStyles() {
        return SUBTYPES;
    }

    @Override
    public String toString() {
        return "BeamOffsetTEMessage{" +
                "offset=" + Arrays.toString(offset) +
                ", ownerIndex=" + ownerIndex +
                ", origin=" + Arrays.toString(origin) +
                ", destination=" + Arrays.toString(destination) +
                ", style=" + style +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        BeamOffsetTEMessage that = (BeamOffsetTEMessage) o;

        return Arrays.equals(offset, that.offset);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + Arrays.hashCode(offset);
        return result;
    }
}
