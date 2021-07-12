package jake2.qcommon.network.messages.server;

import jake2.qcommon.sizebuf_t;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

import static jake2.qcommon.Defines.*;

public class PointDirectionTEMessage extends PointTEMessage {

    public static final Collection<Integer> SUBTYPES = Set.of(
            TE_BLOOD,
            TE_GUNSHOT,
            TE_SPARKS,
            TE_BULLET_SPARKS,
            TE_SCREEN_SPARKS,
            TE_SHIELD_SPARKS,
            TE_SHOTGUN,
            TE_BLASTER,
            TE_GREENBLOOD,
            TE_BLASTER2,
            TE_FLECHETTE,
            TE_HEATBEAM_SPARKS,
            TE_HEATBEAM_STEAM,
            TE_MOREBLOOD,
            TE_ELECTRIC_SPARKS
    );

    public PointDirectionTEMessage(int style) {
        super(style);
    }

    public PointDirectionTEMessage(int style, float[] position, float[] direction) {
        super(style, position);
        this.direction = direction;
    }

    public float[] direction;

    @Override
    protected void writeProperties(sizebuf_t buffer) {
        super.writeProperties(buffer);
        buffer.writeDir(direction);
    }

    @Override
    public void parse(sizebuf_t buffer) {
        super.parse(buffer);
        this.direction = buffer.readDir();

    }

    @Override
    public int getSize() {
        return super.getSize() + 1;
    }

    @Override
    Collection<Integer> getSupportedStyles() {
        return SUBTYPES;
    }

    @Override
    public String toString() {
        return "PointDirectionTEMessage{" +
                "direction=" + Arrays.toString(direction) +
                ", position=" + Arrays.toString(position) +
                ", style=" + style +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        PointDirectionTEMessage that = (PointDirectionTEMessage) o;

        return Arrays.equals(direction, that.direction);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + Arrays.hashCode(direction);
        return result;
    }
}
