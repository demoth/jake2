package jake2.qcommon.network.commands;

import jake2.qcommon.MSG;
import jake2.qcommon.sizebuf_t;

/**
 * TE_BUBBLETRAIL
 * TE_RAILTRAIL
 * TE_BLUEHYPERBLASTER
 * TE_DEBUGTRAIL
 */
public class TrailTEMessage extends PointTEMessage {
    public TrailTEMessage(int style, float[] position, float[] destination) {
        super(style, position);
        this.destination = destination;
    }

    final float[] destination;

    @Override
    protected void writeProperties(sizebuf_t buffer) {
        super.writeProperties(buffer);
        MSG.WritePos(buffer, destination);
    }
}
