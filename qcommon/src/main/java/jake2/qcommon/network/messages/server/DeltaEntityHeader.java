package jake2.qcommon.network.messages.server;

public class DeltaEntityHeader {
    public final int flags;
    public final int number;

    public DeltaEntityHeader(int flags, int number) {
        this.flags = flags;
        this.number = number;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DeltaEntityHeader that = (DeltaEntityHeader) o;

        if (flags != that.flags) return false;
        return number == that.number;
    }

    @Override
    public int hashCode() {
        int result = flags;
        result = 31 * result + number;
        return result;
    }

    @Override
    public String toString() {
        return "DeltaEntityHeader{" +
                "flags=" + flags +
                ", number=" + number +
                '}';
    }
}
