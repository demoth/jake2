package jake2.qcommon.vfs;

import jake2.qcommon.exec.Cmd;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class VfsDebugCommandsTest {

    @Test
    public void dispatchesToProviderWhenInitialized() {
        AtomicInteger filesCalls = new AtomicInteger();
        AtomicInteger mountsCalls = new AtomicInteger();
        AtomicInteger overridesCalls = new AtomicInteger();

        VfsDebugCommands.register(new VfsDebugCommands.Provider() {
            @Override
            public boolean isInitialized() {
                return true;
            }

            @Override
            public List<String> resolvedFiles() {
                filesCalls.incrementAndGet();
                return List.of("maps/base1.bsp");
            }

            @Override
            public List<String> mounts() {
                mountsCalls.incrementAndGet();
                return List.of("[BASE_LOOSE] dir /tmp/baseq2 (files=1)");
            }

            @Override
            public List<String> overrides() {
                overridesCalls.incrementAndGet();
                return List.of("pics/colormap.pcx -> pack:/tmp/baseq2/pak0.pak");
            }
        });

        Cmd.ExecuteFunction("fs_files", List.of("fs_files"));
        Cmd.ExecuteFunction("fs_mounts", List.of("fs_mounts"));
        Cmd.ExecuteFunction("fs_overrides", List.of("fs_overrides"));

        assertEquals(1, filesCalls.get());
        assertEquals(1, mountsCalls.get());
        assertEquals(1, overridesCalls.get());
    }

    @Test
    public void doesNotDispatchWhenProviderIsUninitialized() {
        AtomicInteger filesCalls = new AtomicInteger();
        AtomicInteger mountsCalls = new AtomicInteger();
        AtomicInteger overridesCalls = new AtomicInteger();

        VfsDebugCommands.register(new VfsDebugCommands.Provider() {
            @Override
            public boolean isInitialized() {
                return false;
            }

            @Override
            public List<String> resolvedFiles() {
                filesCalls.incrementAndGet();
                return List.of();
            }

            @Override
            public List<String> mounts() {
                mountsCalls.incrementAndGet();
                return List.of();
            }

            @Override
            public List<String> overrides() {
                overridesCalls.incrementAndGet();
                return List.of();
            }
        });

        Cmd.ExecuteFunction("fs_files", List.of("fs_files"));
        Cmd.ExecuteFunction("fs_mounts", List.of("fs_mounts"));
        Cmd.ExecuteFunction("fs_overrides", List.of("fs_overrides"));

        assertEquals(0, filesCalls.get());
        assertEquals(0, mountsCalls.get());
        assertEquals(0, overridesCalls.get());
    }
}
