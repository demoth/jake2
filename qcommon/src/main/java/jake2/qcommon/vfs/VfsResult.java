package jake2.qcommon.vfs;

/**
 * Simple operation result wrapper for VFS APIs.
 */
public record VfsResult<T>(boolean success, T value, String error) {

    public static <T> VfsResult<T> ok(T value) {
        return new VfsResult<>(true, value, null);
    }

    public static <T> VfsResult<T> fail(String error) {
        return new VfsResult<>(false, null, error);
    }
}

