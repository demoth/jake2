package jake2.qcommon.network.commands;

import jake2.qcommon.MSG;
import jake2.qcommon.network.NetworkCommandType;
import jake2.qcommon.sizebuf_t;

/**
 * Temp entity
 */
public abstract class TEMessage extends NetworkMessage {
    public TEMessage(int style) {
        super(NetworkCommandType.svc_temp_entity);
        this.style = style;
    }

    protected final int style;

    @Override
    protected void writeProperties(sizebuf_t buffer) {
        MSG.WriteByte(buffer, style);
    }

    @Override
    void parse(sizebuf_t buffer) {

    }
}
/*

WriteByte(Defines.TE_EXPLOSION1);
WritePosition(self.s.origin);

WriteByte(te_impact);
WritePosition(tr.endpos);
WriteDir(tr.plane.normal);

WriteByte(Defines.TE_BUBBLETRAIL);
WritePosition(water_start);
WritePosition(tr.endpos);

WriteByte(Defines.TE_LASER_SPARKS);
WriteByte(count);
WritePosition(tr.endpos);
WriteDir(tr.plane.normal);
WriteByte(self.s.skinnum);

WriteByte(Defines.TE_SPLASH);
WriteByte(self.count);
WritePosition(self.s.origin);
WriteDir(self.movedir);
WriteByte(self.sounds);

WriteByte(Defines.TE_MEDIC_CABLE_ATTACK);
WriteShort(self.index);
WritePosition(start);
WritePosition(end);







 */