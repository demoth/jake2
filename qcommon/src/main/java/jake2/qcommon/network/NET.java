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

import jake2.qcommon.*;
import jake2.qcommon.exec.Cvar;
import jake2.qcommon.exec.cvar_t;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

public final class NET {

    private final static int MAX_LOOPBACK = 4;

    /** Local loopback adress. */
    public static final netadr_t net_local_adr = new netadr_t();

    public static class loopmsg_t {
        byte[] data = new byte[Defines.MAX_MSGLEN];

        int datalen;
    };

    public static class loopback_t {
        public loopback_t() {
            msgs = new loopmsg_t[MAX_LOOPBACK];
            for (int n = 0; n < MAX_LOOPBACK; n++) {
                msgs[n] = new loopmsg_t();
            }
        }

        loopmsg_t[] msgs;

        int get;
        int send;
    };

    public static loopback_t loopbacks[] = new loopback_t[2];
    static {
        loopbacks[0] = new loopback_t();
        loopbacks[1] = new loopback_t();
    }

    private static DatagramChannel[] ip_channels = { null, null };

    private static DatagramSocket[] ip_sockets = { null, null };

    /*
     * ==================================================
     * 
     * LOOPBACK BUFFERS FOR LOCAL PLAYER
     * 
     * ==================================================
     */

    /**
     * Gets a packet from internal loopback.
     */
    public static boolean GetLoopPacket(int sock, netadr_t net_from,
            sizebuf_t net_message) {
	
        loopback_t loop = loopbacks[sock];

        if (loop.send - loop.get > MAX_LOOPBACK)
            loop.get = loop.send - MAX_LOOPBACK;

        if (loop.get >= loop.send)
            return false;

        int i = loop.get & (MAX_LOOPBACK - 1);
        loop.get++;

        System.arraycopy(loop.msgs[i].data, 0, net_message.data, 0,
                loop.msgs[i].datalen);
        net_message.cursize = loop.msgs[i].datalen;

        net_from.set(net_local_adr);
        return true;
    }

    /**
     * Sends a packet via internal loopback.
     */
    public static void SendLoopPacket(int sock, int length, byte[] data, netadr_t to) {

        loopback_t loop = loopbacks[sock ^ 1];

        // modulo 4
        int i = loop.send & (MAX_LOOPBACK - 1);
        loop.send++;

        System.arraycopy(data, 0, loop.msgs[i].data, 0, length);
        loop.msgs[i].datalen = length;
    }

    /**
     * Gets a packet from a network channel
     * @param sock - socket type: server or client;
     * @param net_from - set incoming address to net_from
     * @param net_message - body of the packet
     */
    public static boolean GetPacket(int sock, netadr_t net_from,
            sizebuf_t net_message) {

        if (GetLoopPacket(sock, net_from, net_message)) {
            return true;
        }

        if (ip_sockets[sock] == null)
            return false;

        try {
            ByteBuffer receiveBuffer = ByteBuffer.wrap(net_message.data);

            InetSocketAddress srcSocket = (InetSocketAddress) ip_channels[sock]
                    .receive(receiveBuffer);
            if (srcSocket == null)
                return false;

            net_from.ip = srcSocket.getAddress().getAddress();
            net_from.port = srcSocket.getPort();
            net_from.type = NetAddrType.NA_IP;

            int packetLength = receiveBuffer.position();

            if (packetLength > net_message.maxsize) {
                Com.Println("Oversize packet from " + net_from.toString());
                return false;
            }

            // set the size
            net_message.cursize = packetLength;
            // set the sentinel
            net_message.data[packetLength] = 0;
            return true;

        } catch (IOException e) {
            Com.DPrintf("NET_GetPacket: " + e + " from "
                    + net_from.toString() + "\n");
            return false;
        }
    }

    /**
     * Sends a Packet.
     */
    public static void SendPacket(int sock, int length, byte[] data, netadr_t to) {
        if (to.type == NetAddrType.NA_LOOPBACK) {
            SendLoopPacket(sock, length, data, to);
            return;
        }

        if (ip_sockets[sock] == null)
            return;

        if (to.type != NetAddrType.NA_BROADCAST && to.type != NetAddrType.NA_IP) {
            Com.Error(Defines.ERR_FATAL, "NET_SendPacket: bad address type");
            return;
        }

        try {
            SocketAddress dstSocket = new InetSocketAddress(to.getInetAddress(), to.port);
            ip_channels[sock].send(ByteBuffer.wrap(data, 0, length), dstSocket);
        } catch (Exception e) {
            Com.Println("NET_SendPacket ERROR: " + e + " to " + to.toString());
        }
    }

    /**
     * OpenIP, creates the network sockets. 
     */
    public static void OpenIP() {
        cvar_t port, ip, clientport;

        port = Cvar.getInstance().Get("port", "" + Defines.PORT_SERVER, Defines.CVAR_NOSET);
        ip = Cvar.getInstance().Get("ip", "localhost", Defines.CVAR_NOSET);
        clientport = Cvar.getInstance().Get("clientport", "" + Defines.PORT_CLIENT, Defines.CVAR_NOSET);

        cvar_t thinclient = Cvar.getInstance().Get("thinclient", "0", 0);
        cvar_t dedicated = Cvar.getInstance().Get("dedicated", "0", 0);

        // clients do not need server sockets
        if (thinclient.value == 0.f) {
            if (ip_sockets[Defines.NS_SERVER] == null)
                ip_sockets[Defines.NS_SERVER] = Socket(Defines.NS_SERVER, ip.string, (int) port.value);
        }

        // servers do not need client sockets
        if (dedicated.value == 0) {
            // first try with cvar clientport value
            if (ip_sockets[Defines.NS_CLIENT] == null)
                ip_sockets[Defines.NS_CLIENT] = Socket(Defines.NS_CLIENT, ip.string, (int) clientport.value);
            // try any port
            if (ip_sockets[Defines.NS_CLIENT] == null)
                ip_sockets[Defines.NS_CLIENT] = Socket(Defines.NS_CLIENT, ip.string, Defines.PORT_ANY);
        }
    }

    /**
     * Config multi or singlepalyer - A single player game will only use the loopback code.
     */
    public static void Config(boolean multiplayer) {
        if (!multiplayer) {
            // shut down any existing sockets
            for (int i = 0; i < 2; i++) {
                if (ip_sockets[i] != null) {
                    ip_sockets[i].close();
                    ip_sockets[i] = null;
                }
            }
        } else {
            // open sockets
            OpenIP();
        }
    }

    /**
     * Init
     */
    public static void Init() {
        SZ.Init(Globals.net_message, Globals.net_message_buffer, Globals.net_message_buffer.length);
    }

    /*
     * Socket
     */
    public static DatagramSocket Socket(int sock, String ip, int port) {
        DatagramSocket newsocket = null;
        try {
            if (ip_channels[sock] == null || !ip_channels[sock].isOpen())
                ip_channels[sock] = DatagramChannel.open();

            if (ip == null || ip.length() == 0 || ip.equals("localhost")) {
                if (port == Defines.PORT_ANY) {
                    newsocket = ip_channels[sock].socket();
                    newsocket.bind(new InetSocketAddress(0));
                } else {
                    newsocket = ip_channels[sock].socket();
                    newsocket.bind(new InetSocketAddress(port));
                }
            } else {
                InetAddress ia = InetAddress.getByName(ip);
                newsocket = ip_channels[sock].socket();
                newsocket.bind(new InetSocketAddress(ia, port));
            }

            // nonblocking channel
            ip_channels[sock].configureBlocking(false);
            // the socket have to be broadcastable
            newsocket.setBroadcast(true);
            Com.Println("Opened port: " + port);
        } catch (Exception e) {
            Com.Println("jake2.qcommon.network.NET.Socket: " + e.toString());
            e.printStackTrace();
            newsocket = null;
        }
        return newsocket;
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
        if (ip_sockets[Defines.NS_SERVER] == null
                || (Globals.dedicated != null && Globals.dedicated.value == 0))
            return; // we're not a server, just run full speed

        try {
            //TODO: check for timeout
            Thread.sleep(msec);
        } catch (InterruptedException e) {
        }
        //ip_sockets[NS_SERVER].

        // this should wait up to 100ms until a packet
        /*
         * struct timeval timeout; 
         * fd_set fdset; 
         * extern cvar_t *dedicated;
         * extern qboolean stdin_active;
         * 
         * if (!ip_sockets[NS_SERVER] || (dedicated && !dedicated.value))
         * 		return; // we're not a server, just run full speed
         * 
         * FD_ZERO(&fdset);
         *  
         * if (stdin_active) 
         * 		FD_SET(0, &fdset); // stdin is processed too 
         * 
         * FD_SET(ip_sockets[NS_SERVER], &fdset); // network socket 
         * 
         * timeout.tv_sec = msec/1000; 
         * timeout.tv_usec = (msec%1000)*1000; 
         * 
         * select(ip_sockets[NS_SERVER]+1, &fdset, NULL, NULL, &timeout);
         */
    }
}