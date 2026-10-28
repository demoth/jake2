package jake2.server;

import jake2.game.GameEntity;
import jake2.game.GameExportsImpl;
import jake2.game.GamePlayerInfo;
import jake2.game.monsters.M_Gunner;
import jake2.qcommon.GameExports;
import jake2.qcommon.ServerEntity;
import jake2.qcommon.exec.Cvar;
import jake2.qcommon.MainCommon;
import jake2.qcommon.Globals;
import jake2.qcommon.ServerStates;
import jake2.qcommon.Defines;
import jake2.qcommon.network.messages.server.*;
import jake2.qcommon.vfs.EngineVfs;
import jake2.qcommon.vfs.EngineWriteRoot;
import jake2.qcommon.vfs.VfsConfig;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import java.io.File;

public class GameImportsTest {

    @TempDir Path writeRoot;
    private Path previousWriteRoot;
    private ServerHost manager;
    private ServerStates previousServerState;

    @BeforeEach
    public void setup() {
        previousWriteRoot = EngineWriteRoot.path();
        previousServerState = Globals.server_state;
        Globals.server_state = ServerStates.SS_DEAD;
        Cvar.getInstance().clear();
        MainCommon.registerCvars(false);
        EngineVfs.configure(new VfsConfig(new File("src/test/resources").toPath(),
                "baseq2", null, true, false, false, List.of(), Set.of("pak")));
        EngineWriteRoot.setRoot(writeRoot);
    }

    @AfterEach
    public void cleanup() {
        if (manager != null) manager.close();
        Globals.server_state = previousServerState;
        EngineWriteRoot.setRoot(previousWriteRoot);
        Cvar.getInstance().clear();
    }

    @Test
    public void runEmptyInstance() {
        manager = new ServerHost(TransportServerNetwork.standalone());
        GameImportsImpl testInstance = manager.createGameInstance(new ChangeMapInfo("testbox", false, false), null);

        testInstance.runFrame();

        Assertions.assertEquals(1, testInstance.frameNum);
    }

    @Test
    public void gamesKeepIndependentMapStateAndFrameCounters() {
        manager = new ServerHost(TransportServerNetwork.standalone());
        GameImportsImpl first = manager.createGameInstance(new ChangeMapInfo("testbox", false, false), null);
        GameImportsImpl second = manager.createGameInstance(new ChangeMapInfo("testbox2", false, false), null);

        Assertions.assertEquals("testbox", first.mapName);
        Assertions.assertEquals("testbox2", second.mapName);
        Assertions.assertNotEquals(first.name, second.name);
        Assertions.assertEquals(ServerStates.SS_LOADING, second.state);
        Assertions.assertNotSame(first.models, second.models);
        Assertions.assertNotSame(first.baselines[1], second.baselines[1]);
        String secondMapLabel = second.configstrings[jake2.qcommon.Defines.CS_NAME];

        first.configstrings[jake2.qcommon.Defines.CS_NAME] = "changed first map";
        first.baselines[1].modelindex = 7;
        first.state = ServerStates.SS_GAME;
        first.runFrame();

        Assertions.assertEquals(1, first.frameNum);
        Assertions.assertEquals(0, second.frameNum);
        Assertions.assertEquals(secondMapLabel, second.configstrings[jake2.qcommon.Defines.CS_NAME]);
        Assertions.assertEquals(0, second.baselines[1].modelindex);
        Assertions.assertEquals(ServerStates.SS_LOADING, second.state);
    }

    @Test
    public void resourceUpdatesStayWithTheirGameAndOnlySendAfterLoading() {
        manager = new ServerHost(TransportServerNetwork.standalone());
        Cvar.getInstance().find("maxclients", Integer.class).forceSet(2);
        manager.resetClients();
        GameImportsImpl first = manager.createGameInstance(new ChangeMapInfo("testbox", false, false), null);
        GameImportsImpl second = manager.createGameInstance(new ChangeMapInfo("testbox2", false, false), null);
        manager.games.put(first.name, first);
        manager.games.put(second.name, second);
        ClientNetworkInfo firstClient = manager.getClients().get(0);
        ClientNetworkInfo secondClient = manager.getClients().get(1);
        firstClient.gameName = first.name;
        secondClient.gameName = second.name;
        firstClient.state = secondClient.state = ClientStates.CS_CONNECTED;

        try {
            int firstIndex = first.modelindex("models/first.md2");
            int secondIndex = second.modelindex("models/second.md2");
            Assertions.assertEquals(firstIndex, first.modelindex("models/first.md2"));
            Assertions.assertEquals("models/first.md2", first.configstrings[Defines.CS_MODELS + firstIndex]);
            Assertions.assertEquals("models/second.md2", second.configstrings[Defines.CS_MODELS + secondIndex]);
            Assertions.assertTrue(firstClient.netchan.reliablePending.isEmpty());
            Assertions.assertTrue(secondClient.netchan.reliablePending.isEmpty());

            first.state = ServerStates.SS_GAME;
            int soundIndex = first.soundindex("sound/first.wav");
            // Reusing an index should not publish another resource update.
            Assertions.assertEquals(soundIndex, first.soundindex("sound/first.wav"));
            Assertions.assertEquals(1, firstClient.netchan.reliablePending.size());
            Assertions.assertEquals(new ConfigStringMessage(Defines.CS_SOUNDS + soundIndex, "sound/first.wav"),
                    firstClient.netchan.reliablePending.iterator().next());
            Assertions.assertTrue(secondClient.netchan.reliablePending.isEmpty());
        } finally {
            // These clients exercise message queues without establishing network channels.
            firstClient.state = secondClient.state = ClientStates.CS_FREE;
        }
    }

    @Test
    public void spatialQueriesAndClearingStayWithTheirGame() {
        manager = new ServerHost(TransportServerNetwork.standalone());
        GameImportsImpl first = manager.createGameInstance(new ChangeMapInfo("testbox", false, false), null);
        GameImportsImpl second = manager.createGameInstance(new ChangeMapInfo("testbox", false, false), null);
        ServerEntity firstEntity = first.gameExports.getEdict(1);
        ServerEntity secondEntity = second.gameExports.getEdict(1);
        for (ServerEntity entity : new ServerEntity[]{firstEntity, secondEntity}) {
            entity.inuse = true;
            entity.solid = Defines.SOLID_BBOX;
            entity.s.origin[2] = 64;
            java.util.Arrays.fill(entity.mins, -8);
            java.util.Arrays.fill(entity.maxs, 8);
        }
        first.linkentity(firstEntity);
        second.linkentity(secondEntity);
        float[] mins = {-16, -16, 48};
        float[] maxs = {16, 16, 80};
        ServerEntity[] found = new ServerEntity[8];
        Assertions.assertEquals(1, first.BoxEdicts(mins, maxs, found, found.length, Defines.AREA_SOLID));
        Assertions.assertSame(firstEntity, found[0]);
        Assertions.assertEquals(1, second.BoxEdicts(mins, maxs, found, found.length, Defines.AREA_SOLID));
        Assertions.assertSame(secondEntity, found[0]);

        float[] start = {-64, 0, 64};
        float[] end = {64, 0, 64};
        Assertions.assertSame(firstEntity, first.trace(start, null, null, end, null, Defines.MASK_PLAYERSOLID).ent);
        Assertions.assertSame(secondEntity, second.trace(start, null, null, end, null, Defines.MASK_PLAYERSOLID).ent);
        first.unlinkentity(firstEntity);
        Assertions.assertEquals(0, first.BoxEdicts(mins, maxs, found, found.length, Defines.AREA_SOLID));
        first.linkentity(firstEntity);
        first.clearWorld();
        Assertions.assertEquals(0, first.BoxEdicts(mins, maxs, found, found.length, Defines.AREA_SOLID));
        Assertions.assertEquals(1, second.BoxEdicts(mins, maxs, found, found.length, Defines.AREA_SOLID));
        Assertions.assertSame(secondEntity, found[0]);
        Assertions.assertSame(secondEntity, second.trace(start, null, null, end, null, Defines.MASK_PLAYERSOLID).ent);
    }

    @Test
    public void clientSnapshotsKeepIndependentGameHistory() {
        manager = new ServerHost(TransportServerNetwork.standalone());
        GameImportsImpl first = manager.createGameInstance(new ChangeMapInfo("testbox", false, false), null);
        GameImportsImpl second = manager.createGameInstance(new ChangeMapInfo("testbox", false, false), null);
        ClientNetworkInfo firstClient = snapshotClient(first, 11);
        ClientNetworkInfo secondClient = snapshotClient(second, 22);
        first.frameNum = second.frameNum = 1;

        var initial = List.copyOf(first.buildClientFrameMessages(firstClient));
        Assertions.assertEquals(3, initial.size());
        Assertions.assertEquals(-1, ((FrameHeaderMessage) initial.get(0)).lastFrame);
        EntityUpdate initialUpdate = ((PacketEntitiesMessage) initial.get(2)).updates.get(0);
        Assertions.assertTrue(initialUpdate.force);
        Assertions.assertEquals(11, initialUpdate.newState.modelindex);
        second.buildClientFrameMessages(secondClient);

        // Each delta must refer to its own saved snapshot, not the live entity or the other Game.
        firstClient.edict.s.modelindex = 12;
        secondClient.edict.s.modelindex = 23;
        first.frameNum = second.frameNum = 2;
        firstClient.lastReceivedFrame = secondClient.lastReceivedFrame = 1;
        for (GameImportsImpl game : List.of(first, second)) {
            ClientNetworkInfo client = game == first ? firstClient : secondClient;
            int oldModel = game == first ? 11 : 22;
            var messages = List.copyOf(game.buildClientFrameMessages(client));
            FrameHeaderMessage header = (FrameHeaderMessage) messages.get(0);
            Assertions.assertEquals(2, header.frameNumber);
            Assertions.assertEquals(1, header.lastFrame);
            var updates = ((PacketEntitiesMessage) messages.get(2)).updates;
            Assertions.assertEquals(1, updates.size());
            Assertions.assertEquals(oldModel, updates.get(0).oldState.modelindex);
            Assertions.assertEquals(oldModel + 1, updates.get(0).newState.modelindex);
            Assertions.assertFalse(updates.get(0).force);
        }

        firstClient.lastReceivedFrame = 2;
        first.frameNum = 3;
        firstClient.edict.s.modelindex = 0;
        var removed = List.copyOf(first.buildClientFrameMessages(firstClient));
        EntityUpdate removal = ((PacketEntitiesMessage) removed.get(2)).updates.get(0);
        Assertions.assertEquals(1, removal.header.number);
        Assertions.assertNotEquals(0, removal.header.flags & Defines.U_REMOVE);

        // An acknowledgement outside the retained frame window falls back to a baseline update.
        second.frameNum += Defines.UPDATE_BACKUP;
        var stale = List.copyOf(second.buildClientFrameMessages(secondClient));
        Assertions.assertEquals(-1, ((FrameHeaderMessage) stale.get(0)).lastFrame);
        Assertions.assertTrue(((PacketEntitiesMessage) stale.get(2)).updates.get(0).force);
    }

    private ClientNetworkInfo snapshotClient(GameImportsImpl game, int modelIndex) {
        GameEntity entity = (GameEntity) game.gameExports.getEdict(1);
        entity.setClient(new GamePlayerInfo(0));
        entity.getClient().getPlayerState().pmove.origin[2] = 64 * 8;
        entity.s.modelindex = modelIndex;
        ClientNetworkInfo client = new ClientNetworkInfo();
        client.gameName = game.name;
        client.edict = entity;
        return client;
    }

    @Test
    public void queuedSoundsKeepTheirPositionsAndStayWithinTheirGame() {
        manager = new ServerHost(TransportServerNetwork.standalone());
        Cvar.getInstance().find("maxclients", Integer.class).forceSet(2);
        manager.resetClients();
        GameImportsImpl first = manager.createGameInstance(new ChangeMapInfo("testbox", false, false), null);
        GameImportsImpl second = manager.createGameInstance(new ChangeMapInfo("testbox", false, false), null);
        ClientNetworkInfo firstClient = manager.getClients().get(0);
        ClientNetworkInfo secondClient = manager.getClients().get(1);
        firstClient.gameName = first.name;
        secondClient.gameName = second.name;
        firstClient.state = secondClient.state = ClientStates.CS_SPAWNED;
        try {
            ServerEntity entity = first.gameExports.getEdict(1);
            entity.solid = Defines.SOLID_BSP;
            entity.s.origin[0] = 10;
            entity.maxs[0] = 4;
            first.sound(entity, Defines.CHAN_WEAPON, 3, 0.5f, Defines.ATTN_NONE, 0.1f);
            entity.s.origin[0] = 40;
            first.sound(entity, Defines.CHAN_WEAPON, 4, 1, Defines.ATTN_NONE, 0);
            var sounds = List.copyOf(firstClient.unreliable);
            Assertions.assertEquals(2, sounds.size());
            SoundMessage earlier = (SoundMessage) sounds.get(0);
            Assertions.assertArrayEquals(new float[]{12, 0, 0}, earlier.origin);
            Assertions.assertArrayEquals(new float[]{42, 0, 0}, ((SoundMessage) sounds.get(1)).origin);
            Assertions.assertEquals(Defines.SND_ENT | Defines.SND_POS | Defines.SND_VOLUME
                    | Defines.SND_ATTENUATION | Defines.SND_OFFSET, earlier.flags);
            Assertions.assertEquals((entity.index << 3) | Defines.CHAN_WEAPON, earlier.sendchan);

            float[] explicitOrigin = {1, 2, 3};
            first.positioned_sound(explicitOrigin, entity, Defines.CHAN_RELIABLE | Defines.CHAN_VOICE,
                    5, 1, Defines.ATTN_NONE, 0);
            explicitOrigin[0] = 99;
            Assertions.assertEquals(1, firstClient.netchan.reliablePending.size());
            SoundMessage reliable = (SoundMessage) firstClient.netchan.reliablePending.iterator().next();
            Assertions.assertArrayEquals(new float[]{1, 2, 3}, reliable.origin);
            Assertions.assertTrue(secondClient.unreliable.isEmpty());
            Assertions.assertTrue(secondClient.netchan.reliablePending.isEmpty());
        } finally {
            firstClient.state = secondClient.state = ClientStates.CS_FREE;
        }
    }

    @Test
    public void clientPrintQueueRespectsItsMessageLevel() {
        ClientNetworkInfo client = new ClientNetworkInfo();
        client.messagelevel = Defines.PRINT_HIGH;
        client.queuePrint(Defines.PRINT_LOW, "hidden");
        client.queuePrint(Defines.PRINT_HIGH, "visible");
        Assertions.assertEquals(List.of(new PrintMessage(Defines.PRINT_HIGH, "visible")),
                List.copyOf(client.netchan.reliablePending));
        Assertions.assertTrue(client.unreliable.isEmpty());
    }

    @Test
    public void levelInitializationTransfersPersistentStateAndKeepsEventsGameLocal() {
        manager = new ServerHost(TransportServerNetwork.standalone());
        GameImportsImpl first = manager.spawnServerInstance(new ChangeMapInfo("testbox", false, false), 0);
        GameExportsImpl firstGameplay = (GameExportsImpl) first.gameExports;
        Assertions.assertEquals(ServerStates.SS_GAME, first.state);
        Assertions.assertEquals(2, firstGameplay.level.framenum);
        firstGameplay.game.serverflags = 7;
        manager.games.put("default", first);

        GameImportsImpl next = manager.spawnServerInstance(new ChangeMapInfo("testbox2", false, false), 0);
        GameExportsImpl nextGameplay = (GameExportsImpl) next.gameExports;
        Assertions.assertEquals(7, nextGameplay.game.serverflags);
        Assertions.assertNotSame(firstGameplay.game, nextGameplay.game);
        Assertions.assertEquals(ServerStates.SS_GAME, next.state);
        Assertions.assertEquals(2, nextGameplay.level.framenum);

        first.gameExports.getEdict(1).s.event = 1;
        next.gameExports.getEdict(1).s.event = 2;
        first.clearEntityStateEvents();
        Assertions.assertEquals(0, first.gameExports.getEdict(1).s.event);
        Assertions.assertEquals(2, next.gameExports.getEdict(1).s.event);
    }

    @Test
    public void levelInitializationRestoresSavesAndOnlySettlesRevisitedLevels() {
        manager = new ServerHost(TransportServerNetwork.standalone());
        GameImportsImpl original = manager.spawnServerInstance(new ChangeMapInfo("testbox", false, false), 0);
        GameExportsImpl originalGameplay = (GameExportsImpl) original.gameExports;
        originalGameplay.game.serverflags = 9;
        int entityIndex = -1;
        for (int i = 1; i < original.gameExports.getNumEdicts(); i++) {
            GameEntity entity = (GameEntity) original.gameExports.getEdict(i);
            if ("monster_gunner".equals(entity.classname)) {
                entity.health = 73;
                entityIndex = i;
                break;
            }
        }
        Assertions.assertTrue(entityIndex > 0);
        int savedFrame = originalGameplay.level.framenum;
        original.SV_WriteLevelFile();
        original.SV_WriteServerFile(false);
        originalGameplay.game.serverflags = 99;
        manager.games.put("default", original);

        GameImportsImpl loaded = manager.spawnServerInstance(new ChangeMapInfo("testbox", false, true), 0);
        GameExportsImpl loadedGameplay = (GameExportsImpl) loaded.gameExports;
        Assertions.assertEquals(9, loadedGameplay.game.serverflags);
        Assertions.assertEquals(savedFrame, loadedGameplay.level.framenum);
        Assertions.assertEquals(73, ((GameEntity) loaded.gameExports.getEdict(entityIndex)).health);
        Assertions.assertEquals(ServerStates.SS_GAME, loaded.state);
        Assertions.assertEquals(loaded.gameExports.getEdict(entityIndex).s.modelindex,
                loaded.baselines[entityIndex].modelindex);
        Assertions.assertArrayEquals(loaded.gameExports.getEdict(entityIndex).s.origin,
                loaded.baselines[entityIndex].origin);
        Assertions.assertEquals(Float.toString(loaded.airAcceleration), loaded.configstrings[Defines.CS_AIRACCEL]);

        GameImportsImpl revisited = manager.spawnServerInstance(new ChangeMapInfo("testbox", false, false), 0);
        Assertions.assertEquals(savedFrame + 100, ((GameExportsImpl) revisited.gameExports).level.framenum);
        Assertions.assertEquals(ServerStates.SS_GAME, revisited.state);
    }

    @Test
    public void hostProtocolDispatchAndShutdownUseTheirOwnGameRegistry() {
        manager = new ServerHost(TransportServerNetwork.standalone());
        Cvar.getInstance().register("gamedir", jake2.qcommon.exec.CvarTypes.STRING, "", 0, "Test mod directory");
        Cvar.getInstance().find("maxclients", Integer.class).forceSet(2);
        manager.resetClients();
        GameImportsImpl first = manager.createGameInstance(new ChangeMapInfo("testbox", false, false), null);
        GameImportsImpl second = manager.createGameInstance(new ChangeMapInfo("testbox2", false, false), null);
        manager.games.put(first.name, first);
        manager.games.put(second.name, second);
        first.configstrings[Defines.CS_NAME] = "first game";
        second.configstrings[Defines.CS_NAME] = "second game";
        ClientNetworkInfo firstClient = manager.getClients().get(0);
        ClientNetworkInfo secondClient = manager.getClients().get(1);
        firstClient.gameName = first.name;
        secondClient.gameName = second.name;
        firstClient.edict = first.gameExports.getEdict(1);
        secondClient.edict = second.gameExports.getEdict(2);
        firstClient.state = secondClient.state = ClientStates.CS_CONNECTED;
        try {
            manager.executeClientCommand(firstClient, "new");
            manager.executeClientCommand(secondClient, "new");
            Assertions.assertEquals("first game",
                    ((ServerDataMessage) firstClient.netchan.reliablePending.iterator().next()).levelString);
            Assertions.assertEquals("second game",
                    ((ServerDataMessage) secondClient.netchan.reliablePending.iterator().next()).levelString);
            firstClient.netchan.reliablePending.clear();
            manager.executeClientCommand(firstClient, "configstrings " + first.spawncount + " 0");
            Assertions.assertTrue(firstClient.netchan.reliablePending.contains(
                    new ConfigStringMessage(Defines.CS_NAME, "first game")));
        } finally {
            firstClient.state = secondClient.state = ClientStates.CS_FREE;
        }

        // A separate idle Host must not inherit or clear the running Host's registry.
        // Global console/cvar/network isolation is outside this ownership test.
        try (ServerHost other = new ServerHost(TransportServerNetwork.standalone())) {
            Assertions.assertFalse(other.isRunning());
            Assertions.assertEquals(first.name, other.allocateGameName());
        }
        Assertions.assertSame(first, manager.games.get(first.name));
        Assertions.assertSame(second, manager.games.get(second.name));
        Assertions.assertEquals(2, manager.getClients().size());
        Assertions.assertTrue(manager.isRunning());
    }

    @Test
    public void saveCommandResolvesOnlyDefaultGameWithoutReregistration() throws Exception {
        manager = new ServerHost(TransportServerNetwork.standalone());
        Cvar.getInstance().find("maxclients", Integer.class).forceSet(2);
        manager.resetClients();
        var saveRegistration = jake2.qcommon.exec.Cmd.FindCommand("save");
        Assertions.assertNull(jake2.qcommon.exec.Cmd.FindCommand("sv"));
        GameImportsImpl first = manager.spawnServerInstance(new ChangeMapInfo("testbox", false, false), 0);
        manager.games.put("default", first);
        GameImportsImpl second = manager.spawnServerInstance(new ChangeMapInfo("testbox2", false, false), 0);
        manager.games.put(second.name, second);
        Assertions.assertSame(saveRegistration, jake2.qcommon.exec.Cmd.FindCommand("save"));
        Assertions.assertFalse(Cvar.getInstance().isRegistered("filterban"));

        jake2.qcommon.exec.Cmd.ExecuteString("save first");
        jake2.qcommon.exec.Cmd.ExecuteString("save --game " + second.name + " second");
        var store = jake2.server.save.ServerSaveJsonStore.forWriteDir(writeRoot.toString());
        Assertions.assertEquals("testbox", store.readMapCommand("first").mapCommand());
        Assertions.assertFalse(store.hasMapCommand("second"));
        manager.games.put("default", second);
        jake2.qcommon.exec.Cmd.ExecuteString("save replaced");
        Assertions.assertEquals("testbox2", store.readMapCommand("replaced").mapCommand());
        Assertions.assertSame(saveRegistration, jake2.qcommon.exec.Cmd.FindCommand("save"));
        manager.stop("test", false);
        Assertions.assertSame(saveRegistration, jake2.qcommon.exec.Cmd.FindCommand("save"));
        jake2.qcommon.exec.Cmd.ExecuteString("save idle");
        Assertions.assertFalse(store.hasMapCommand("idle"));
    }

    @Test
    public void runGame() {

        final ServerHost sv_main = manager = new ServerHost(TransportServerNetwork.standalone());
        GameImportsImpl game = sv_main.spawnServerInstance(new ChangeMapInfo("testbox", false, false), 0);
        final GameExports gameExports = game.gameExports;
        GameEntity gunner = null;
        GameEntity soldier = null;
        for (int i = 1; i < gameExports.getNumEdicts(); i++) {
            GameEntity e = (GameEntity) gameExports.getEdict(i);
            if ("monster_gunner".equals(e.classname)) {
                gunner = e;
            } else if ("monster_soldier".equals(e.classname)) {
                soldier = e;
            }
        }
        Assertions.assertNotNull(gunner);
        Assertions.assertNotNull(soldier);
        gunner.enemy = soldier;
        gunner.goalentity = soldier;
        gunner.monsterinfo.currentmove = M_Gunner.gunner_move_run;
        for (int i = 0; i < 100; i++) {
            gameExports.G_RunFrame();
            if (soldier.health <= 0) {
                System.out.println("soldier killed on frame: " + i);
                return;
            }
        }
        Assertions.fail("Soldier was not killed");
    }
}
