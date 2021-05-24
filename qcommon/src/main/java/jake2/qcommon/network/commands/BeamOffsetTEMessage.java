package jake2.qcommon.network.commands;

import jake2.qcommon.MSG;
import jake2.qcommon.sizebuf_t;

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

    @Override
    protected void writeProperties(sizebuf_t buffer) {
        super.writeProperties(buffer);
        MSG.WritePos(buffer, offset);
    }

    @Override
    void parse(sizebuf_t buffer) {
        super.parse(buffer);
        offset = new float[3];
        MSG.ReadPos(buffer, offset);
    }
}
