package jake2.qcommon.network.messages;

import jake2.qcommon.Com;
import jake2.qcommon.Defines;
import jake2.qcommon.network.Netchan;
import jake2.qcommon.network.NetAddrType;
import jake2.qcommon.network.protocol.PacketEnvelope;
import jake2.qcommon.network.transport.NetworkAddress;
import jake2.qcommon.network.messages.client.ClientMessage;
import jake2.qcommon.network.messages.client.EndOfClientPacketMessage;
import jake2.qcommon.network.messages.client.MoveMessage;
import jake2.qcommon.network.messages.server.*;
import jake2.qcommon.network.netadr_t;
import jake2.qcommon.sizebuf_t;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class NetworkPacket {

    // Long, -1 for connectionless
    public int sequence;

    // Long
    public int sequenceAck;

    // read the qport out of the message so we can fix up
    // stupid address translating routers
    // Short
    public int qport;

    public String connectionlessMessage;

    public String connectionlessParameters; // client only

    public netadr_t from = new netadr_t();

    public sizebuf_t buffer = new sizebuf_t();

    /** Adapt a validated envelope to the mutable legacy channel/message reader. */
    public static NetworkPacket fromEnvelope(NetworkAddress source, PacketEnvelope envelope) {
        NetworkPacket packet = new NetworkPacket();
        packet.buffer.data = envelope.getBytes().toByteArray();
        packet.buffer.cursize = packet.buffer.data.length;
        packet.buffer.maxsize = packet.buffer.data.length;
        if (source instanceof NetworkAddress.Udp udp) {
            packet.from.type = NetAddrType.NA_IP;
            packet.from.ip = udp.getIp().toByteArray();
            packet.from.port = udp.getPort();
        }
        if (envelope instanceof PacketEnvelope.Sequenced sequenced) {
            packet.sequence = sequenced.getSequence();
            packet.sequenceAck = sequenced.getSequenceAck();
            packet.qport = sequenced.getQport() == null ? 0 : sequenced.getQport();
            packet.buffer.readcount = sequenced.getHeaderSize();
        } else if (envelope instanceof PacketEnvelope.Connectionless connectionless) {
            packet.sequence = -1;
            packet.connectionlessMessage = connectionless.getCommandLine();
            packet.connectionlessParameters = connectionless.getParameters();
            packet.buffer.readcount = packet.buffer.cursize;
        }
        return packet;
    }

    private NetworkPacket() {
    }

    public boolean isConnectionless() {
        return sequence == -1;
    }

    public Collection<ServerMessage> parseBodyFromServer() {
        List<ServerMessage> result = new ArrayList<>();
        while (true) {
            ServerMessage msg = ServerMessage.parseFromBuffer(buffer);
            if (msg instanceof EndOfServerPacketMessage) {
                break;
            } else if (msg != null) {
                if (!(msg instanceof FrameHeaderMessage) && !(msg instanceof PlayerInfoMessage) && !(msg instanceof PacketEntitiesMessage)) {
                    if (Netchan.printNetwork.getTypedValue()) {
                        Com.Printf("Network: Received server msg: " + msg + "\n");
                    }
                }
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
                if (!(msg instanceof MoveMessage)) {
                    if (Netchan.printNetwork.getTypedValue()) {
                        Com.Printf("Network: Received client msg: " + msg + "\n");
                    }
                }
                result.add(msg);
            }

            if (buffer.readcount > buffer.cursize) {
                Com.Printf("SV_ReadClientMessage: bad read:\n");
                return null;
            }

        }
        return result;
    }

}
