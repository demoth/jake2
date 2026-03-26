package jake2.qcommon.vfs;

import jake2.qcommon.Com;
import jake2.qcommon.exec.Cmd;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        Cmd.ExecuteFunction("fs_find", List.of("fs_find", ".*\\.bsp"));
        Cmd.ExecuteFunction("fs_mounts", List.of("fs_mounts"));
        Cmd.ExecuteFunction("fs_overrides", List.of("fs_overrides"));

        assertEquals(2, filesCalls.get());
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
        Cmd.ExecuteFunction("fs_find", List.of("fs_find", ".*\\.bsp"));
        Cmd.ExecuteFunction("fs_mounts", List.of("fs_mounts"));
        Cmd.ExecuteFunction("fs_overrides", List.of("fs_overrides"));

        assertEquals(0, filesCalls.get());
        assertEquals(0, mountsCalls.get());
        assertEquals(0, overridesCalls.get());
    }

    @Test
    public void fsFindPrintsMatchingResolvedFiles() {
        StringBuilder output = new StringBuilder();
        Com.ConsoleSink sink = (level, message) -> output.append(message);
        Com.SetConsoleSink(sink);
        try {
            VfsDebugCommands.register(new VfsDebugCommands.Provider() {
                @Override
                public boolean isInitialized() {
                    return true;
                }

                @Override
                public List<String> resolvedFiles() {
                    return List.of("maps/base1.bsp", "maps/base2.bsp", "sound/world/hum.wav");
                }

                @Override
                public List<String> mounts() {
                    return List.of();
                }

                @Override
                public List<String> overrides() {
                    return List.of();
                }
            });

            Cmd.ExecuteFunction("fs_find", List.of("fs_find", ".*\\.bsp"));

            assertTrue(output.toString().contains("maps/base1.bsp\n"));
            assertTrue(output.toString().contains("maps/base2.bsp\n"));
            assertFalse(output.toString().contains("sound/world/hum.wav\n"));
            assertTrue(output.toString().contains("Total matched files: 2\n"));
        } finally {
            Com.ClearConsoleSink(sink);
        }
    }

    @Test
    public void fsFindRejectsInvalidRegex() {
        StringBuilder output = new StringBuilder();
        Com.ConsoleSink sink = (level, message) -> output.append(message);
        Com.SetConsoleSink(sink);
        try {
            VfsDebugCommands.register(new VfsDebugCommands.Provider() {
                @Override
                public boolean isInitialized() {
                    return true;
                }

                @Override
                public List<String> resolvedFiles() {
                    return List.of("maps/base1.bsp");
                }

                @Override
                public List<String> mounts() {
                    return List.of();
                }

                @Override
                public List<String> overrides() {
                    return List.of();
                }
            });

            Cmd.ExecuteFunction("fs_find", List.of("fs_find", "["));

            assertTrue(output.toString().contains("Invalid regex for fs_find"));
        } finally {
            Com.ClearConsoleSink(sink);
        }
    }
}
