package jake2.qcommon.network.messages.server;

import jake2.qcommon.SZ;
import jake2.qcommon.sizebuf_t;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.awt.*;
import java.util.Arrays;
import java.util.Collection;

import static jake2.qcommon.Defines.MAX_ITEMS;
import static org.junit.Assert.*;

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
    public static Collection<Object[]> primeNumbers() {
        return Arrays.asList(new Object[][]{
                {new DownloadMessage(new byte[]{1, 2, 3}, 50)},
                {new DownloadMessage()},
                {new DisconnectMessage()},
                {new NopMessage()},
                {new EndOfServerPacketMessage()},
                {new LayoutMessage("layout")},
                {new ConfigStringMessage(4, "config")},
                {new ServerDataMessage(4, 3, false, "hello quake", 1, "q3dm6")},
                {new StuffTextMessage("stuff")},
                {new FrameHeaderMessage(1, 2, 3, 4, new byte[]{1, 1, 1, 1})},
                {new InventoryMessage(new int[MAX_ITEMS])},
                {new MuzzleFlash2Message(1, 2)},
                {new PrintCenterMessage("hello")},
                {new PrintMessage(3, "hello")},
                {new WeaponSoundMessage(1, 2)},
                {new BeamTEMessage(1, 2, new float[3], new float[3])},
                {new PointTEMessage(4, new float[3])},
                {new SplashTEMessage(4, 5, new float[3], new float[3], 6)},
                {new BeamOffsetTEMessage(5, 8, new float[3], new float[3], new float[3])},
                {new PointDirectionTEMessage(4, new float[3], new float[3])},
                {new TrailTEMessage(4, new float[3], new float[3])}
        });
    }

    @Test
    public void testMessageSize() {
        SZ.Init(buffer, data, SIZE);
        message.writeTo(buffer);
        assertEquals(buffer.cursize, message.getSize());
    }

    //
//    @Test
//    public void frameHeaderMesssageSize() {
//        var msg = ;
//        checkMessageSize(new InventoryMessage(new int[MAX_ITEMS]));
//    }
//
//    @Test
//    public void inventoryMesssageSize() {
//        checkMessageSize();
//    }
//
    private void checkMessageSize(ServerMessage msg) {
        msg.writeTo(buffer);
        assertEquals(buffer.cursize, msg.getSize());
    }

}