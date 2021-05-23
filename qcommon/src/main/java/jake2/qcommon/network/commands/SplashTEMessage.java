package jake2.qcommon.network.commands;

import jake2.qcommon.MSG;
import jake2.qcommon.sizebuf_t;

public class SplashTEMessage extends TEMessage {
    public SplashTEMessage(int style, int count, float[] position, float[] direction, int param) {
        super(style);
        this.count = count;
        this.position = position;
        this.direction = direction;
        this.param = param;
    }

    final int count;
    final float[] position;
    final float[] direction;
    final int param;

    @Override
    protected void writeProperties(sizebuf_t buffer) {
        super.writeProperties(buffer);
        MSG.WriteByte(buffer, count);
        MSG.WritePos(buffer, position);
        MSG.WriteDir(buffer, direction);
        MSG.WriteByte(buffer, param);

    }
}
