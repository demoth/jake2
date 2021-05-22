package jake2.qcommon.network.commands;

import jake2.qcommon.MSG;
import jake2.qcommon.sizebuf_t;

/**
 * TE_BLOOD
 * TE_GUNSHOT
 * TE_SPARKS
 * TE_BULLET_SPARKS
 * TE_SCREEN_SPARKS
 * TE_SHIELD_SPARKS
 * TE_SHOTGUN
 * TE_BLASTER
 * TE_GREENBLOOD
 * TE_BLASTER2
 * TE_FLECHETTE
 * TE_HEATBEAM_SPARKS
 * TE_HEATBEAM_STEAM
 * TE_MOREBLOOD
 * TE_ELECTRIC_SPARKS
 */
public class PointDirectionTEMessage extends PointTEMessage {
    public PointDirectionTEMessage(int style, float[] position, float[] direction) {
        super(style, position);
        this.direction = direction;
    }

    final float[] direction;

    @Override
    protected void writeProperties(sizebuf_t buffer) {
        super.writeProperties(buffer);
        MSG.WriteDir(buffer, direction);
    }
}
