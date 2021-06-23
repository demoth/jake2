package jake2.qcommon.network.messages;

import jake2.qcommon.Com;
import jake2.qcommon.Globals;
import jake2.qcommon.MSG;
import jake2.qcommon.network.messages.client.ClientMessage;
import jake2.qcommon.network.messages.client.EndMessage;
import jake2.qcommon.network.netadr_t;
import jake2.qcommon.network.netchan_t;
import jake2.qcommon.sizebuf_t;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ClientPacket {

    // Long, -1 for connectionless
    public int sequence;

    // Long
    public int sequenceAck;

    // read the qport out of the message so we can fix up
    // stupid address translating routers
    // Short
    public int qport;

    public int length;

    public String connectionlessMessage;

    public netadr_t from = new netadr_t();

    public sizebuf_t buffer = new sizebuf_t();

    public boolean isConnectionless() {
        return sequence == -1;
    }

    public void parseHeader() {
        sequence = MSG.ReadLong(buffer);

        if (isConnectionless()) {
            connectionlessMessage = MSG.ReadStringLine(buffer);
        } else {
            sequenceAck = MSG.ReadLong(buffer);
            qport = MSG.ReadShort(buffer) & 0xffff;
        }
    }

    public Collection<ClientMessage> parseBody(int incomingSequence) {
        List<ClientMessage> result = new ArrayList<>();
        while (true) {
            ClientMessage msg = ClientMessage.parseFromBuffer(buffer, incomingSequence);
            if (msg == null || msg instanceof EndMessage) {
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
