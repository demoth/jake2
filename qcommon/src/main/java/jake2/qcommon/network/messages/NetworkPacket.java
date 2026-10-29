package jake2.qcommon.network.messages;

import jake2.qcommon.network.protocol.PacketEnvelope;
import jake2.qcommon.network.transport.NetworkAddress;
import jake2.qcommon.network.messages.client.ClientMessage;
import jake2.qcommon.network.messages.server.ServerMessage;
import jake2.qcommon.network.protocol.MessageDecodeResult;
import jake2.qcommon.network.protocol.MessageBodyCodec;
import jake2.qcommon.sizebuf_t;


public class NetworkPacket {
    private PacketEnvelope envelope;

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

    public final NetworkAddress from;

    public sizebuf_t buffer = new sizebuf_t();

    /** Adapt a validated envelope to the engine channel/message reader while retaining the exact immutable source. */
    public static NetworkPacket fromEnvelope(NetworkAddress source, PacketEnvelope envelope) {
        NetworkPacket packet = new NetworkPacket(source);
        packet.envelope = envelope;
        packet.buffer.data = envelope.getBytes().toByteArray();
        packet.buffer.cursize = packet.buffer.data.length;
        packet.buffer.maxsize = packet.buffer.data.length;
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

    private NetworkPacket(NetworkAddress source) {
        this.from = java.util.Objects.requireNonNull(source);
    }

    public boolean isConnectionless() {
        return sequence == -1;
    }

    public PacketEnvelope.Sequenced sequencedEnvelope() {
        if (envelope instanceof PacketEnvelope.Sequenced sequenced) return sequenced;
        throw new IllegalStateException("Connectionless packet has no channel header");
    }

    /** Decode the complete server payload before application dispatch; malformed input is an explicit rejection. */
    public MessageDecodeResult<ServerMessage> parseBodyFromServer() {
        return MessageBodyCodec.decodeServer(envelope);
    }

    /** Decode the complete client payload with the incoming sequence used by movement checksums. */
    public MessageDecodeResult<ClientMessage> parseBodyFromClient(int incomingSequence) {
        return MessageBodyCodec.decodeClient(envelope, incomingSequence);
    }

}
