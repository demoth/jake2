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
import jake2.qcommon.Defines;
import jake2.qcommon.Globals;
import jake2.qcommon.exec.Cvar;
import jake2.qcommon.exec.CvarTypes;
import jake2.qcommon.network.messages.NetworkPacket;
import jake2.qcommon.network.protocol.PacketCodec;
import jake2.qcommon.network.protocol.PacketDecodeResult;
import jake2.qcommon.network.protocol.PacketDirection;
import jake2.qcommon.network.transport.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import static jake2.qcommon.Defines.NS_SERVER;

public final class NET {

    /** Legacy address bridge; transport endpoints themselves use immutable addresses. */
    public static final netadr_t net_local_adr = new netadr_t();

    // Temporary role owners until ClientNetwork and ServerNetwork receive their endpoints.
    private static final UdpDatagramEndpoint[] udpEndpoints = new UdpDatagramEndpoint[2];
    private static LoopbackLink loopback = new LoopbackLink();

    public static UdpDatagramEndpoint udpEndpoint(int role) {
        return udpEndpoints[role];
    }

    private static LoopbackLink.Endpoint localEndpoint(int role) {
        return role == Defines.NS_CLIENT ? loopback.getFirst() : loopback.getSecond();
    }

    public static void SendLoopPacket(int role, int length, byte[] data, netadr_t to) {
        localEndpoint(role).send(localEndpoint(role ^ 1).getLocalAddress(), PacketBytes.copyOf(data, length));
    }

    /** Receive only local traffic, including final messages after the UDP role has stopped. */
    public static NetworkPacket receiveLoopbackPacket(int role) {
        return adaptReceived(localEndpoint(role).receive(), role == NS_SERVER);
    }

    public static NetworkPacket receiveNetworkPacket(int role) {
        NetworkPacket local = receiveLoopbackPacket(role);
        if (local != null) return local;
        UdpDatagramEndpoint endpoint = udpEndpoints[role];
        return endpoint == null ? null : adaptReceived(endpoint.receive(), role == NS_SERVER);
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

    public static void SendPacket(int role, int length, byte[] data, netadr_t to) {
        if (to.type == NetAddrType.NA_LOOPBACK) {
            SendLoopPacket(role, length, data, to);
            return;
        }
        UdpDatagramEndpoint endpoint = udpEndpoints[role];
        if (endpoint == null) return;
        if (to.type != NetAddrType.NA_BROADCAST && to.type != NetAddrType.NA_IP) {
            throw new IllegalArgumentException("NET_SendPacket: unsupported address type " + to.type);
        }
        try {
            NetworkAddress.Udp destination = new NetworkAddress.Udp(
                    IpAddress.fromInetAddress(to.getInetAddress()), to.port);
            SendResult result = endpoint.send(destination, PacketBytes.copyOf(data, length));
            if (result instanceof SendResult.Failed failure) {
                Com.Println("NET_SendPacket ERROR: " + failure.getCause() + " to " + to);
            }
        } catch (IOException | IllegalArgumentException error) {
            Com.Println("NET_SendPacket ERROR: " + error + " to " + to);
        }
    }

    /**
     * Declare protected network settings after startup config has supplied early values.
     */
    static void registerCvars() {
        Cvar cvars = Cvar.getInstance();
        cvars.register("port", new CvarTypes.IntegerType(Defines.PORT_ANY, 65535), Defines.PORT_SERVER, Defines.CVAR_NOSET, "Server UDP port; 0 lets the OS choose, -1 is the legacy wildcard-bind equivalent");
        cvars.register("ip", CvarTypes.STRING, "localhost", Defines.CVAR_NOSET, "Local bind address; empty or localhost binds all interfaces");
        cvars.register("clientport", new CvarTypes.IntegerType(Defines.PORT_ANY, 65535), Defines.PORT_CLIENT, Defines.CVAR_NOSET, "Client UDP port; 0 lets the OS choose, -1 is the legacy wildcard-bind equivalent");
        cvars.register("thinclient", CvarTypes.BOOLEAN, false, 0, "Do not open a server UDP socket");
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
        if (udpEndpoints[role] != null && udpEndpoints[role].isOpen()) return true;
        closeRole(role);
        Cvar cvars = Cvar.getInstance();
        String ip = cvars.find("ip", String.class).getTypedValue();
        int port = cvars.find(portName, Integer.class).getTypedValue();
        udpEndpoints[role] = openEndpoint(ip, port);
        if (udpEndpoints[role] == null && fallback)
            udpEndpoints[role] = openEndpoint(ip, Defines.PORT_ANY);
        return udpEndpoints[role] != null;
    }

    private static void closeRole(int role) {
        UdpDatagramEndpoint endpoint = udpEndpoints[role];
        udpEndpoints[role] = null;
        if (endpoint != null) {
            try {
                endpoint.close();
            } catch (IOException error) {
                Com.Println("Closing UDP endpoint: " + error.getMessage());
            }
        }
    }

    /** Whole-session boundary only: do not discard reconnect traffic during map changes. */
    public static void clearLoopbacks() {
        loopback.close();
        loopback = new LoopbackLink();
    }

    /** Discard incoming packets for one role without dropping the other role's final messages. */
    public static void clearLoopback(int role) {
        localEndpoint(role).clearIncoming();
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
     * Shutdown - closes the sockets 
     */
    public static void Shutdown() {
        // close sockets
        Config(false);
    }

    /** Sleeps msec or until net socket is ready. */
    public static void Sleep(int msec) {
        if (udpEndpoints[NS_SERVER] == null
                || (Globals.dedicated != null && !Globals.dedicated.getTypedValue()))
            return; // we're not a server, just run full speed

        try {
            //TODO: check for timeout
            Thread.sleep(msec);
        } catch (InterruptedException e) {
        }
    }
}
