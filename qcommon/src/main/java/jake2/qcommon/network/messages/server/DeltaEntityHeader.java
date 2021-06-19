package jake2.qcommon.network.messages.server;

public class DeltaEntityHeader {
    public final int flags;
    public final int number;

    public DeltaEntityHeader(int flags, int number) {
        this.flags = flags;
        this.number = number;
    }
}
