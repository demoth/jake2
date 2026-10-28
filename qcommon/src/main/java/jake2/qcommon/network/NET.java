/*
 * NET.java Copyright (C) 2003
 */
/*
 * Copyright (C) 1997-2001 Id Software, Inc.
 * 
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.
 * 
 * See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 59 Temple
 * Place - Suite 330, Boston, MA 02111-1307, USA.
 *  
 */
package jake2.qcommon.network;

import jake2.qcommon.Com;
import jake2.qcommon.network.messages.ConnectionlessCommand;
import jake2.qcommon.sizebuf_t;
import jake2.qcommon.util.Lib;
import jake2.qcommon.Defines;
import jake2.qcommon.Globals;
import jake2.qcommon.exec.Cvar;
import jake2.qcommon.network.messages.NetworkPacket;
import jake2.qcommon.network.runtime.NetworkTransport;
import jake2.qcommon.network.protocol.PacketCodec;
import jake2.qcommon.network.protocol.PacketDecodeResult;
import jake2.qcommon.network.protocol.PacketDirection;
import jake2.qcommon.network.transport.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;

import static jake2.qcommon.Defines.NS_SERVER;

/** @deprecated Compatibility facade; inject an owned NetworkTransport through the client/server network boundary. */
@Deprecated
public final class NET {

    // Archived-client compatibility owner; active applications supply their own transports.
    private static NetworkTransport[] transports = newTransports();

    private static NetworkTransport[] newTransports() {
        LoopbackLink link = new LoopbackLink();
        return new NetworkTransport[] {
                new NetworkTransport(link.getFirst(), link.getSecond().getLocalAddress()),
                new NetworkTransport(link.getSecond(), link.getFirst().getLocalAddress())
        };
    }

    /** Archived-client compatibility: explicitly borrow the facade's current role transport. */
    public static netchan_t newChannel(int role) {
        return new netchan_t(transports[role], () -> System.nanoTime() / 1_000_000L);
    }

    public static UdpDatagramEndpoint udpEndpoint(int role) {
        return transports[role].getUdpEndpoint();
    }

    public static void SendLoopPacket(int role, int length, byte[] data, netadr_t to) {
        transports[role].send(transports[role].getLocalPeer(), PacketBytes.copyOf(data, length));
    }

    /** Receive only local traffic, including final messages after the UDP role has stopped. */
    public static NetworkPacket receiveLoopbackPacket(int role) {
        return adaptReceived(transports[role].receiveLocal(), role == NS_SERVER);
    }

    public static NetworkPacket receiveNetworkPacket(int role) {
        return adaptReceived(transports[role].receive(), role == NS_SERVER);
    }

    // Legacy nullable API and trace output; decoding itself has explicit, side-effect-free outcomes.
    private static NetworkPacket adaptReceived(ReceiveResult result, boolean fromClient) {
        if (result instanceof ReceiveResult.Failed failure) {
            Com.Println("NET receive ERROR: " + failure.getCause());
        }
        if (!(result instanceof ReceiveResult.Received received)) return null;
        Datagram datagram = received.getDatagram();
        PacketDecodeResult decoded = PacketCodec.decode(datagram.getPayload(),
                fromClient ? PacketDirection.FROM_CLIENT : PacketDirection.FROM_SERVER);
        if (decoded instanceof PacketDecodeResult.Rejected rejected) {
            if (Netchan.showdrop != null && Netchan.showdrop.getTypedValue()) {
                Com.Println("NET dropped packet from " + datagram.getSource() + ": " + rejected.getReason());
            }
            return null;
        }
        NetworkPacket packet = NetworkPacket.fromEnvelope(datagram.getSource(),
                ((PacketDecodeResult.Decoded) decoded).getEnvelope());
        if (packet.isConnectionless() && Netchan.printNetwork != null && Netchan.printNetwork.getTypedValue()) {
            Com.Printf("Network: Received from " + (fromClient ? "client" : "server")
                    + " connectionless: " + packet.connectionlessMessage + "\n");
        }
        return packet;
    }

    public static SendResult SendPacket(int role, int length, byte[] data, netadr_t to) {
        return sendPacket(role, PacketBytes.copyOf(data, length), to);
    }

    public static SendResult sendPacket(int role, PacketBytes payload, netadr_t to) {
        if (to.type == NetAddrType.NA_LOOPBACK) {
            return transports[role].send(transports[role].getLocalPeer(), payload);
        }
        UdpDatagramEndpoint endpoint = udpEndpoint(role);
        if (endpoint == null) return new SendResult.Failed(new ClosedChannelException());
        if (to.type != NetAddrType.NA_BROADCAST && to.type != NetAddrType.NA_IP) {
            throw new IllegalArgumentException("NET_SendPacket: unsupported address type " + to.type);
        }
        try {
            NetworkAddress.Udp destination = new NetworkAddress.Udp(
                    IpAddress.fromInetAddress(to.getInetAddress()), to.port);
            SendResult result = transports[role].send(destination, payload);
            if (result instanceof SendResult.Failed failure) {
                Com.Println("NET_SendPacket ERROR: " + failure.getCause() + " to " + to);
            }
            return result;
        } catch (IOException | IllegalArgumentException error) {
            Com.Println("NET_SendPacket ERROR: " + error + " to " + to);
            return new SendResult.Failed(error instanceof IOException io ? io : new IOException(error));
        }
    }

    /** Legacy combined socket policy. New session owners configure each role explicitly. */
    public static void OpenIP() {
        Cvar cvars = Cvar.getInstance();
        if (!cvars.find("thinclient", Boolean.class).getTypedValue()) configureServer(true);
        if (!cvars.find("dedicated", Boolean.class).getTypedValue()) configureClient(true);
    }

    /** Configure only the server UDP endpoint; loopback never needs a socket. */
    public static boolean configureServer(boolean enabled) {
        return configureRole(NS_SERVER, enabled, "port", false);
    }

    /** Configure only the client UDP endpoint, retaining the legacy ephemeral-port fallback. */
    public static boolean configureClient(boolean enabled) {
        return configureRole(Defines.NS_CLIENT, enabled, "clientport", true);
    }

    private static boolean configureRole(int role, boolean enabled, String portName, boolean fallback) {
        if (!enabled) {
            closeRole(role);
            return true;
        }
        if (udpEndpoint(role) != null && udpEndpoint(role).isOpen()) return true;
        closeRole(role);
        Cvar cvars = Cvar.getInstance();
        String ip = cvars.find("ip", String.class).getTypedValue();
        int port = cvars.find(portName, Integer.class).getTypedValue();
        UdpDatagramEndpoint endpoint = openEndpoint(ip, port);
        if (endpoint == null && fallback) endpoint = openEndpoint(ip, Defines.PORT_ANY);
        try {
            transports[role].replaceUdp(endpoint);
        } catch (IOException error) {
            Com.Println("Closing previous UDP endpoint: " + error.getMessage());
        }
        return endpoint != null;
    }

    private static void closeRole(int role) {
        try {
            transports[role].replaceUdp(null);
        } catch (IOException error) {
            Com.Println("Closing UDP endpoint: " + error.getMessage());
        }
    }

    /** Whole-session boundary only: preserve UDP bindings while retiring the local pair. */
    public static void clearLoopbacks() {
        NetworkTransport[] previous = transports;
        transports = newTransports();
        for (int role = 0; role < previous.length; role++) {
            // UDP ownership remains with the role across a local-session reset.
            try {
                transports[role].replaceUdp(previous[role].detachUdp());
                previous[role].close();
            } catch (IOException error) {
                Com.Println("Closing network transport: " + error.getMessage());
            }
        }
    }

    /** Discard incoming packets for one role without dropping the other role's final messages. */
    public static void clearLoopback(int role) {
        transports[role].clearLocalIncoming();
    }

    /** Legacy combined policy for callers that own both socket roles. */
    public static void Config(boolean multiplayer) {
        if (multiplayer) OpenIP();
        else {
            configureClient(false);
            configureServer(false);
        }
    }

    private static UdpDatagramEndpoint openEndpoint(String ip, int port) {
        try {
            int bindPort = port == Defines.PORT_ANY ? 0 : port;
            InetSocketAddress bindAddress = ip == null || ip.isEmpty() || ip.equals("localhost")
                    ? new InetSocketAddress(bindPort)
                    : new InetSocketAddress(InetAddress.getByName(ip), bindPort);
            UdpDatagramEndpoint endpoint = UdpDatagramEndpoint.bind(bindAddress);
            Com.Println("Opened port: " + endpoint.getLocalAddress().getPort());
            return endpoint;
        } catch (IOException error) {
            Com.Println("NET bind ERROR: " + error);
            return null;
        }
    }

    /**
     * Out Of Band
     */
    public static void sendConnectionlessPacket(int net_socket, netadr_t adr, ConnectionlessCommand cmd, String payload) {
        String msg = cmd.name() + payload;

        sizebuf_t packet = new sizebuf_t(Defines.MAX_MSGLEN);

        // write the packet header
        packet.writeInt(-1); // -1 sequence means connectionless (out of band)
        packet.writeBytes(Lib.stringToBytes(msg), msg.length());

        // send the datagram
        NET.SendPacket(net_socket, packet.cursize, packet.data, adr);
    }

    /**
     * Shutdown - closes the sockets 
     */
    public static void Shutdown() {
        // close sockets
        Config(false);
    }

    /** Sleeps msec or until net socket is ready. */
    public static void Sleep(int msec) {
        if (udpEndpoint(NS_SERVER) == null
                || (Globals.dedicated != null && !Globals.dedicated.getTypedValue()))
            return; // we're not a server, just run full speed

        try {
            //TODO: check for timeout
            Thread.sleep(msec);
        } catch (InterruptedException e) {
        }
    }
}
