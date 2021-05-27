package jake2.qcommon.network.messages.server;

import jake2.qcommon.MSG;
import jake2.qcommon.sizebuf_t;

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
        MSG.WritePos(buffer, destination);
    }

    @Override
    void parse(sizebuf_t buffer) {
        super.parse(buffer);
        this.destination = new float[3];
        MSG.ReadPos(buffer, destination);
    }
}
