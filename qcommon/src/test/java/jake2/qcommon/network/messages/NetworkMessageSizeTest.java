package jake2.qcommon.network.messages;

import jake2.qcommon.*;
import jake2.qcommon.network.messages.client.*;
import jake2.qcommon.network.messages.server.*;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static jake2.qcommon.Defines.*;
import static org.junit.Assert.assertEquals;

/**
 * This test checks that
 * <ol>
 * <li>size estimation for each server/client message class is correct</li>
 * <li>size of the message is equal to the bytes written to the buffer when the message is serialized</li>
 * <li>the message stays the same (by equals) during serialization/deserialization</li>
 * <li>quake 34 protocol compatibility test - the message is the same as expected (compared with binary file)</li>
 * </ol>
 * Validated by writing the message to the buffer and comparing the size.
 */
@RunWith(Parameterized.class)
public class NetworkMessageSizeTest {
    private static final int INVALID_FRAME_INDEX = 99;
    private static final int SIZE = 1024;
    sizebuf_t buffer = new sizebuf_t();
    byte[] data = new byte[SIZE];

    final NetworkMessage message;
    final String testName;

    public NetworkMessageSizeTest(String testName, NetworkMessage message) {
        this.testName = testName;
        this.message = message;
    }

    @Parameterized.Parameters(name = "{index}, {0} {1}")
    public static Collection<Object[]> createTestData() {

        //////////////////
        // SERVER MESSAGES
        //////////////////
        List<Object[]> testMessages = new ArrayList<>();
        // null/empty markers
        testMessages.add(new Object[]{"server.NopMessage", new NopMessage()});
        testMessages.add(new Object[]{"server.EndOfServerPacketMessage", new EndOfServerPacketMessage()});
        // ordinary messages
        testMessages.add(new Object[]{"server.DownloadMessage.data", new DownloadMessage(new byte[]{1, 2, 3}, (byte) 50)});
        testMessages.add(new Object[]{"server.DownloadMessage.empty", new DownloadMessage()});
        testMessages.add(new Object[]{"server.DisconnectMessage", new DisconnectMessage()});
        testMessages.add(new Object[]{"server.ReconnectMessage", new ReconnectMessage()});
        testMessages.add(new Object[]{"server.LayoutMessage", new LayoutMessage("layout")});
        testMessages.add(new Object[]{"server.ConfigStringMessage", new ConfigStringMessage(4, "config")});
        // expect only PROTOCOL_VERSION(34)
        testMessages.add(new Object[]{"server.ServerDataMessage", new ServerDataMessage(PROTOCOL_VERSION, 3, false, "hello quake", 1, "q3dm6")});
        testMessages.add(new Object[]{"server.StuffTextMessage", new StuffTextMessage("stuff")});
        testMessages.add(new Object[]{"server.FrameHeaderMessage", new FrameHeaderMessage(1, 2, 3, 4, new byte[]{1, 1, 1, 1})});
        testMessages.add(new Object[]{"server.InventoryMessage", new InventoryMessage(new int[MAX_ITEMS])});
        testMessages.add(new Object[]{"server.MuzzleFlash2Message", new MuzzleFlash2Message(1, 2)});
        testMessages.add(new Object[]{"server.PrintCenterMessage", new PrintCenterMessage("hello")});
        testMessages.add(new Object[]{"server.PrintMessage", new PrintMessage(3, "hello")});
        testMessages.add(new Object[]{"server.WeaponSoundMessage", new WeaponSoundMessage(1, 2)});
        // Temp entities
        testMessages.add(new Object[]{"server.PointTEMessage", new PointTEMessage(TE_BOSSTPORT, new float[3])});
        testMessages.add(new Object[]{"server.BeamTEMessage", new BeamTEMessage(TE_PARASITE_ATTACK, 2, new float[3], new float[3])});
        // this direction is taken from: jake2.qcommon.Globals.bytedirs
        testMessages.add(new Object[]{"server.SplashTEMessage", new SplashTEMessage(TE_LASER_SPARKS, 5, new float[3], new float[]{-0.525731f, 0.000000f, 0.850651f}, 6)});
        testMessages.add(new Object[]{"server.BeamOffsetTEMessage", new BeamOffsetTEMessage(TE_GRAPPLE_CABLE, 8, new float[3], new float[3], new float[3])});
        testMessages.add(new Object[]{"server.PointDirectionTEMessage", new PointDirectionTEMessage(TE_GUNSHOT, new float[3], new float[]{-0.525731f, 0.000000f, 0.850651f})});
        testMessages.add(new Object[]{"server.TrailTEMessage", new TrailTEMessage(TE_BUBBLETRAIL, new float[3], new float[3])});
        testMessages.add(new Object[]{"server.SoundMessage", new SoundMessage(SND_VOLUME | SND_ATTENUATION | SND_OFFSET | SND_ENT | SND_POS, 2, 1, 2, 0.2f, 1, new float[]{1, 2, 3})});
        // delta compressed
        testMessages.add(new Object[]{"server.SpawnBaselineMessage.empty", new SpawnBaselineMessage(new entity_state_t(new edict_t(1)))});
        entity_state_t entityState = new entity_state_t(new edict_t(1));
        {
            entityState.origin = new float[]{1, 2, 3};
            entityState.number = 1000;
            // used these values due to rounding during serialization
            entityState.angles = new float[]{0.0f, 2.8125f, 1.40625f};
            entityState.skinnum = 2222;
            entityState.frame = 3333;
            entityState.effects = 4444;
            entityState.renderfx = RF_BEAM;
            entityState.solid = 4;
            entityState.event = 5;
            entityState.modelindex = 123;
            entityState.modelindex2 = 234;
            entityState.modelindex3 = 113;
            entityState.modelindex4 = 87;
            entityState.sound = 32;
            entityState.old_origin = new float[]{2, 3, 4};
        }
        testMessages.add(new Object[]{"server.SpawnBaselineMessage.full", new SpawnBaselineMessage(entityState)});
        player_state_t currentState = new player_state_t();
        {
            pmove_state_t pmove = new pmove_state_t();
            {
                pmove.origin = new short[]{0, 1, 2};
                pmove.velocity = new short[]{1, 1, 1};
                pmove.pm_type = 2;
                pmove.pm_time = 50;
                pmove.pm_flags = 1;
                pmove.gravity = 600;
                pmove.delta_angles = new short[]{3, 4, 5};
            }
            currentState.pmove = pmove;
            currentState.kick_angles = new float[]{2, 2, 2};
            currentState.viewoffset = new float[]{1, 3, 5};
            currentState.viewangles = new float[]{49.994812f, 99.99512f, 149.99542f};
            currentState.blend = new float[]{0.09803922f, 0.2f, 0.29803923f, 0.4f};
            currentState.fov = 70;
            currentState.rdflags = 2;
            currentState.gunindex = 23;
            currentState.gunframe = 12;
            currentState.gunangles = new float[]{3, 2, 1};
            currentState.gunoffset = new float[]{6, 4, 2};
            currentState.stats = new short[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 1};
        }
        testMessages.add(new Object[]{"server.PlayerInfoMessage", new PlayerInfoMessage(new player_state_t(), currentState)});
        PacketEntitiesMessage packetEntitiesMessage = new PacketEntitiesMessage();
        {
            packetEntitiesMessage.updates.add(new EntityUpdate(new DeltaEntityHeader(U_REMOVE, 32)));
            entity_state_t newState = new entity_state_t(new edict_t(1));
            {
                newState.modelindex = 3;
                newState.origin = new float[]{2, 3, 4};
            }
            packetEntitiesMessage.updates.add(new EntityUpdate(new entity_state_t(new edict_t(1)), newState, false, true));
        }
        testMessages.add(new Object[]{"server.PacketEntitiesMessage", packetEntitiesMessage});
        //////////////////
        // CLIENT MESSAGES
        //////////////////
        testMessages.add(new Object[]{"client.EndOfClientPacketMessage", new EndOfClientPacketMessage()});
        testMessages.add(new Object[]{"client.NoopMessage", new NoopMessage()});
        testMessages.add(new Object[]{"client.UserInfoMessage", new UserInfoMessage("test/user/info")});
        testMessages.add(new Object[]{"client.StringCmdMessage", new StringCmdMessage("test command")});
        // delta compressed
        // empty one
        testMessages.add(new Object[]{"client.MoveMessage.empty", new MoveMessage(false, 1, new usercmd_t(), new usercmd_t(), new usercmd_t(), 1)});
        // full blown
        usercmd_t oldestCmd = new usercmd_t((byte) 50, (byte) 5, new short[]{(short) 1, (short) 2, (short) 3}, (short) 10, (short) 20, (short) 30, (byte) 1, (byte) 3);
        usercmd_t oldCmd = new usercmd_t((byte) 100, (byte) 15, new short[]{(short) 4, (short) 5, (short) 6}, (short) 11, (short) 21, (short) 31, (byte) 5, (byte) 7);
        usercmd_t newCmd = new usercmd_t((byte) 150, (byte) 25, new short[]{(short) 8, (short) 9, (short) 0}, (short) 12, (short) 22, (short) 32, (byte) 9, (byte) 0);
        testMessages.add(new Object[]{"client.MoveMessage.full", new MoveMessage(false, 1, oldestCmd, oldCmd, newCmd, 1)});
        // invalid one (currentSequence during serialization != currentSequence during deserialization)
        testMessages.add(new Object[]{"client.MoveMessage.invalid", new MoveMessage(false, INVALID_FRAME_INDEX, oldestCmd, oldCmd, newCmd, 2)});
        return testMessages;
    }

    @Before
    public void writeMessage() {
        buffer.init(data, SIZE);
        message.writeTo(buffer);
    }

    @Test
    public void testMessageSize() {
        assertEquals("Message size != buffer size", buffer.cursize, message.getSize());
    }

    @Test
    public void testBufferFullyRead() {
        if (message instanceof ServerMessage)
            ServerMessage.parseFromBuffer(buffer);
        else { //if (message instanceof ClientMessage)
            ClientMessage.parseFromBuffer(buffer, 1);
        }
        assertEquals("Buffer is not read fully", buffer.cursize, buffer.readcount);
    }

    @Test
    public void testSerializationDeserializationEquality() {
        final NetworkMessage parsed;

        if (message instanceof ServerMessage)
            parsed = ServerMessage.parseFromBuffer(buffer);
        else
            parsed = ClientMessage.parseFromBuffer(buffer, 1);

        if (message instanceof NopMessage
                || message instanceof EndOfServerPacketMessage
                || message instanceof EndOfClientPacketMessage
                || message instanceof NoopMessage) {
            System.err.println("Skipping test for " + message.getClass());
        } else {
            assertEquals("Message is different after serialization/deserialization", message, parsed);
        }

        // client move message crc validation
        if (parsed instanceof MoveMessage) {
            assertEquals((((MoveMessage) parsed).lastReceivedFrame != INVALID_FRAME_INDEX), ((MoveMessage) parsed).valid);
        }
    }

    @Test
    public void testQuakeNetworkProtocol34Compatibility() {
        try (InputStream inputStream = getClass().getResourceAsStream(testName)) {
            final byte[] quake34data = inputStream.readAllBytes();
            assertEquals("Message size is different from quake34 protocol", quake34data.length, buffer.cursize);
            for (int i = 0; i < quake34data.length; i++) {
                assertEquals("Message data is different from quake34 protocol at position: " + i, quake34data[i], buffer.data[i]);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Enable to save binary message files
     */
    @Test
    @Ignore
    public void saveBinaryMessage() {
        try (OutputStream out = new FileOutputStream(testName)) {
            out.write(buffer.data, 0, buffer.cursize);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}