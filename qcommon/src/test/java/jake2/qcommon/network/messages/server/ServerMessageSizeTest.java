package jake2.qcommon.network.messages.server;

import jake2.qcommon.*;
import jake2.qcommon.network.messages.NetworkMessage;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static jake2.qcommon.Defines.*;
import static org.junit.Assert.assertEquals;

/**
 * This test checks that
 * <ol>
 * <li>size estimation for each server message class is correct</li>
 * <li>size of the message is equal to the bytes in the buffer</li>
 * <li>the message is the same during serialization/deserialization</li>
 * </ol>
 * Validated by writing the message to the buffer and comparing the size.
 */
@RunWith(Parameterized.class)
public class ServerMessageSizeTest {
    private static final int SIZE = 1024;
    sizebuf_t buffer = new sizebuf_t();
    byte[] data = new byte[SIZE];

    ServerMessage message;

    public ServerMessageSizeTest(ServerMessage message) {
        this.message = message;
    }

    @Parameterized.Parameters(name = "{index}, {0}")
    public static Collection<Object[]> createTestData() {

        List<NetworkMessage> testMessages = new ArrayList<>();
        // null/empty markers
        testMessages.add(new NopMessage());
        testMessages.add(new EndOfServerPacketMessage());
        // ordinary messages
        testMessages.add(new DownloadMessage(new byte[]{1, 2, 3}, 50));
        testMessages.add(new DownloadMessage());
        testMessages.add(new DisconnectMessage());
        testMessages.add(new ReconnectMessage());
        testMessages.add(new LayoutMessage("layout"));
        testMessages.add(new ConfigStringMessage(4, "config"));
        // expect only PROTOCOL_VERSION(34)
        testMessages.add(new ServerDataMessage(PROTOCOL_VERSION, 3, false, "hello quake", 1, "q3dm6"));
        testMessages.add(new StuffTextMessage("stuff"));
        testMessages.add(new FrameHeaderMessage(1, 2, 3, 4, new byte[]{1, 1, 1, 1}));
        testMessages.add(new InventoryMessage(new int[MAX_ITEMS]));
        testMessages.add(new MuzzleFlash2Message(1, 2));
        testMessages.add(new PrintCenterMessage("hello"));
        testMessages.add(new PrintMessage(3, "hello"));
        testMessages.add(new WeaponSoundMessage(1, 2));
        // Temp entities
        testMessages.add(new PointTEMessage(TE_BOSSTPORT, new float[3]));
        testMessages.add(new BeamTEMessage(TE_PARASITE_ATTACK, 2, new float[3], new float[3]));
        // this direction is taken from: jake2.qcommon.Globals.bytedirs
        testMessages.add(new SplashTEMessage(TE_LASER_SPARKS, 5, new float[3], new float[]{-0.525731f, 0.000000f, 0.850651f}, 6));
        testMessages.add(new BeamOffsetTEMessage(TE_GRAPPLE_CABLE, 8, new float[3], new float[3], new float[3]));
        testMessages.add(new PointDirectionTEMessage(TE_GUNSHOT, new float[3], new float[]{-0.525731f, 0.000000f, 0.850651f}));
        testMessages.add(new TrailTEMessage(TE_BUBBLETRAIL, new float[3], new float[3]));
        testMessages.add(new SoundMessage(SND_VOLUME | SND_ATTENUATION | SND_OFFSET | SND_ENT | SND_POS, 2, 1, 2, 0.2f, 1, new float[]{1, 2, 3}));
        // delta compressed
        testMessages.add(new SpawnBaselineMessage(new entity_state_t(new edict_t(1))));
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
        testMessages.add(new SpawnBaselineMessage(entityState));
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
            currentState.viewangles = new float[]{1.9995117f, 3.9990234f, 5.998535f};
            currentState.blend = new float[]{0.09803922f, 0.2f, 0.29803923f, 0.4f};
            currentState.fov = 70;
            currentState.rdflags = 2;
            currentState.gunindex = 23;
            currentState.gunframe = 12;
            currentState.gunangles = new float[]{3, 2, 1};
            currentState.gunoffset = new float[]{6, 4, 2};
            currentState.stats = new short[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 1};
        }
        testMessages.add(new PlayerInfoMessage(new player_state_t(), currentState));
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
        testMessages.add(packetEntitiesMessage);

        return testMessages.stream().map(networkMessage -> new Object[]{networkMessage}).collect(Collectors.toList());
    }

    @Before
    public void writeMessage() {
        SZ.Init(buffer, data, SIZE);
        message.writeTo(buffer);
    }

    @Test
    public void testMessageSize() {
        assertEquals("Message size != buffer size", buffer.cursize, message.getSize());
    }

    @Test
    public void testBufferFullyRead() {
        SZ.Init(buffer, data, SIZE);
        message.writeTo(buffer);

        ServerMessage.parseFromBuffer(buffer);
        assertEquals("Buffer is not read fully", buffer.cursize, buffer.readcount);
    }

    @Test
    public void testSerializationDeserializationEquality() {
        ServerMessage parsed = ServerMessage.parseFromBuffer(buffer);
        if (message instanceof NopMessage
                || message instanceof EndOfServerPacketMessage) {
            System.err.println("Skipping test for " + message.getClass());
        } else {
            assertEquals("Message is different after serialization/deserialization", message, parsed);
        }
    }

}