package jake2.qcommon.network.messages.server;

import jake2.qcommon.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

import static jake2.qcommon.Defines.*;
import static org.junit.Assert.assertEquals;

/**
 * This test checks that size estimation for each server message class is correct.
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
        return Arrays.asList(new Object[][]{
                // null/empty markers
                {new NopMessage()},
                {new EndOfServerPacketMessage()},
                // ordinary messages
                {new DownloadMessage(new byte[]{1, 2, 3}, 50)},
                {new DownloadMessage()},
                {new DisconnectMessage()},
                {new ReconnectMessage()},
                {new LayoutMessage("layout")},
                {new ConfigStringMessage(4, "config")},
                // expect only PROTOCOL_VERSION(34)
                {new ServerDataMessage(PROTOCOL_VERSION, 3, false, "hello quake", 1, "q3dm6")},
                {new StuffTextMessage("stuff")},
                {new FrameHeaderMessage(1, 2, 3, 4, new byte[]{1, 1, 1, 1})},
                {new InventoryMessage(new int[MAX_ITEMS])},
                {new MuzzleFlash2Message(1, 2)},
                {new PrintCenterMessage("hello")},
                {new PrintMessage(3, "hello")},
                {new WeaponSoundMessage(1, 2)},
                // Temp entities
                {new PointTEMessage(TE_BOSSTPORT, new float[3])},
                {new BeamTEMessage(TE_PARASITE_ATTACK, 2, new float[3], new float[3])},
                // this direction is taken from: jake2.qcommon.Globals.bytedirs
                {new SplashTEMessage(TE_LASER_SPARKS, 5, new float[3], new float[]{-0.525731f, 0.000000f, 0.850651f}, 6)},
                {new BeamOffsetTEMessage(TE_GRAPPLE_CABLE, 8, new float[3], new float[3], new float[3])},
                // this direction is taken from: jake2.qcommon.Globals.bytedirs
                {new PointDirectionTEMessage(TE_GUNSHOT, new float[3], new float[]{-0.525731f, 0.000000f, 0.850651f})},
                {new TrailTEMessage(TE_BUBBLETRAIL, new float[3], new float[3])},
                {new SoundMessage(SND_VOLUME | SND_ATTENUATION | SND_OFFSET | SND_ENT | SND_POS, 2, 1, 2, 0.2f, 1, new float[]{1, 2, 3})},
                // delta compressed
                {new SpawnBaselineMessage(new entity_state_t(new edict_t(1)))},
                {new SpawnBaselineMessage(new entity_state_t(new edict_t(1)) {{
                    origin = new float[]{1, 2, 3};
                    number = 1000;
                    // used these values due to rounding during serialization
                    angles = new float[]{0.0f, 2.8125f, 1.40625f};
                    skinnum = 2222;
                    frame = 3333;
                    effects = 4444;
                    renderfx = RF_BEAM;
                    solid = 4;
                    event = 5;
                    modelindex = 123;
                    modelindex2 = 234;
                    modelindex3 = 113;
                    modelindex4 = 87;
                    sound = 32;
                    old_origin = new float[]{2, 3, 4};
                }})},
                {new PlayerInfoMessage(new player_state_t(), new player_state_t() {{
                    pmove = new pmove_state_t() {{
                        origin = new short[]{0, 1, 2};
                        velocity = new short[]{1, 1, 1};
                        pm_type = 2;
                        pm_time = 50;
                        pm_flags = 1;
                        gravity = 600;
                        delta_angles = new short[]{3, 4, 5};
                    }};
                    kick_angles = new float[]{2, 2, 2};
                    viewoffset = new float[]{1, 3, 5};
                    viewangles = new float[]{1.9995117f, 3.9990234f, 5.998535f};
                    blend = new float[]{0.09803922f, 0.2f, 0.29803923f, 0.4f};
                    fov = 70;
                    rdflags = 2;
                    gunindex = 23;
                    gunframe = 12;
                    gunangles = new float[]{3, 2, 1};
                    gunoffset = new float[]{6, 4, 2};
                    stats = new short[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 1};
                }})},
                {new PacketEntitiesMessage() {{
                    updates.add(new EntityUpdate(new DeltaEntityHeader(U_REMOVE, 32)));
                    //updates.add(new EntityUpdate(new entity_state_t(new edict_t(1)), new entity_state_t(new edict_t(1)), false, true));
                }}}
        });
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
