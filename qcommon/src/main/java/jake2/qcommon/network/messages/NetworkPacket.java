package jake2.qcommon.network.messages;

import jake2.qcommon.Com;
import jake2.qcommon.Defines;
import jake2.qcommon.Globals;
import jake2.qcommon.network.NetAddrType;
import jake2.qcommon.network.messages.client.ClientMessage;
import jake2.qcommon.network.messages.client.EndOfClientPacketMessage;
import jake2.qcommon.network.messages.server.EndOfServerPacketMessage;
import jake2.qcommon.network.messages.server.ServerMessage;
import jake2.qcommon.network.netadr_t;
import jake2.qcommon.network.netchan_t;
import jake2.qcommon.sizebuf_t;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class NetworkPacket {

    // should parse port
    private final boolean fromClient;

    // Long, -1 for connectionless
    private int sequence;

    // Long
    private int sequenceAck;

    // read the qport out of the message so we can fix up
    // stupid address translating routers
    // Short
    public int qport;

    public String connectionlessMessage;

    public String connectionlessParameters; // client only

    public netadr_t from = new netadr_t();

    public sizebuf_t buffer = new sizebuf_t();

    public static NetworkPacket fromLoopback(boolean fromClient, byte[] data, int datalen) {
        // localhost by default
        NetworkPacket loopbackPacket = new NetworkPacket(fromClient);
        loopbackPacket.buffer.data = new byte[datalen];
        loopbackPacket.buffer.cursize = datalen;
        System.arraycopy(data, 0, loopbackPacket.buffer.data, 0, datalen);
        loopbackPacket.parseHeader();
        return loopbackPacket;
    }

    public static NetworkPacket fromSocket(boolean fromClient, byte[] data, int datalen, byte[] address, int port) {
        NetworkPacket networkPacket = new NetworkPacket(fromClient);
        networkPacket.from.ip = address;
        networkPacket.from.port = port;
        networkPacket.from.type = NetAddrType.NA_IP;
        networkPacket.buffer.cursize = datalen;
        networkPacket.buffer.data = data;
        networkPacket.buffer.data[datalen] = 0; // set the sentinel
        networkPacket.parseHeader();
        return networkPacket;
    }

    private NetworkPacket(boolean fromClient) {
        this.fromClient = fromClient;
    }

    public boolean isConnectionless() {
        return sequence == -1;
    }

    public void parseHeader() {
        sequence = buffer.readInt();

        if (isConnectionless()) {
            connectionlessMessage = buffer.readString();
            if (!fromClient) {
                switch (connectionlessMessage) {
                    case "info":
                    case "print":
                        connectionlessParameters = buffer.readString();
                        break;
                    default:
                        break;
                }

            }
        } else {
            sequenceAck = buffer.readInt();

            if (fromClient)
                qport = buffer.readShort() & 0xffff;
        }
    }

    public Collection<ServerMessage> parseBodyFromServer() {
        List<ServerMessage> result = new ArrayList<>();
        while (true) {
            ServerMessage msg = ServerMessage.parseFromBuffer(buffer);
            if (msg instanceof EndOfServerPacketMessage) {
                break;
            } else if (msg != null) {
                result.add(msg);
            }
            if (buffer.readcount > buffer.cursize) {
                Com.Error(Defines.ERR_FATAL, "CL_ParseServerMessage: Bad server message:");
                break;
            }
        }
        return result;
    }

    public Collection<ClientMessage> parseBodyFromClient(int incomingSequence) {
        List<ClientMessage> result = new ArrayList<>();
        while (true) {
            ClientMessage msg = ClientMessage.parseFromBuffer(buffer, incomingSequence);
            if (msg == null || msg instanceof EndOfClientPacketMessage) {
                break;
            } else {
                result.add(msg);
            }

            if (buffer.readcount > buffer.cursize) {
                Com.Printf("SV_ReadClientMessage: bad read:\n");
                return null;
            }

        }
        return result;
    }

    // Netchan.Process
    public boolean isValidForClient(netchan_t chan) {
        // achtung unsigned int
        int reliable_message = sequence >>> 31;
        int reliable_ack = sequenceAck >>> 31;

        sequence &= ~(1 << 31);
        sequenceAck &= ~(1 << 31);

        //
        // discard stale or duplicated packets
        //
        if (sequence <= chan.incoming_sequence) {
            return false;
        }

        //
        // dropped packets don't keep the message from being used
        //
        chan.dropped = sequence - (chan.incoming_sequence + 1);

        //
        // if the current outgoing reliable message has been acknowledged
        // clear the buffer to make way for the next
        //
        if (reliable_ack == chan.reliable_sequence)
            chan.reliable_length = 0; // it has been received

        //
        // if this message contains a reliable message, bump
        // incoming_reliable_sequence
        //
        chan.incoming_sequence = sequence;
        chan.incoming_acknowledged = sequenceAck;
        chan.incoming_reliable_acknowledged = reliable_ack;
        if (reliable_message != 0) {
            chan.incoming_reliable_sequence ^= 1;
        }

        //
        // the message can now be read from the current message pointer
        //
        chan.last_received = (int) Globals.curtime;
        return true;
    }
}
