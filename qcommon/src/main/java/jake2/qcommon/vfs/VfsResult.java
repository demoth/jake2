package jake2.qcommon.vfs;

/**
 * Simple operation result wrapper for VFS APIs.
 */
public final class VfsResult<T> {
    public final boolean success;
    public final T value;
    public final String error;

    private VfsResult(boolean success, T value, String error) {
        this.success = success;
        this.value = value;
        this.error = error;
    }

    public static <T> VfsResult<T> ok(T value) {
        return new VfsResult<>(true, value, null);
    }

    public static <T> VfsResult<T> fail(String error) {
        return new VfsResult<>(false, null, error);
    }
}

