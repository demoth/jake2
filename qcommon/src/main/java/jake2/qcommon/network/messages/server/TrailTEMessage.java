package jake2.qcommon.network.messages.server;

import jake2.qcommon.sizebuf_t;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

import static jake2.qcommon.Defines.*;

public class TrailTEMessage extends PointTEMessage {

    public static final Collection<Integer> SUBTYPES = Set.of(
            TE_BUBBLETRAIL,
            TE_RAILTRAIL,
            TE_BLUEHYPERBLASTER,
            TE_DEBUGTRAIL,
            TE_BFG_LASER
    );

    public TrailTEMessage(int style) {
        super(style);
    }

    public TrailTEMessage(int style, float[] position, float[] destination) {
        super(style, position);
        this.destination = destination;
    }

    public float[] destination;

    @Override
    protected void writeProperties(sizebuf_t buffer) {
        super.writeProperties(buffer);
        sizebuf_t.WritePos(buffer, destination);
    }

    @Override
    public void parse(sizebuf_t buffer) {
        super.parse(buffer);
        this.destination = new float[3];
        sizebuf_t.ReadPos(buffer, destination);
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
        return "TrailTEMessage{" +
                "position=" + Arrays.toString(position) +
                ", style=" + style +
                ", destination=" + Arrays.toString(destination) +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        TrailTEMessage that = (TrailTEMessage) o;

        return Arrays.equals(destination, that.destination);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + Arrays.hashCode(destination);
        return result;
    }
}
