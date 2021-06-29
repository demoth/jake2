#Quake 2 Networking Communication Protocol

## NetworkPacket

Every frame both client and servers are sending a jake2.qcommon.network.messages.NetworkPacket to each other.
Each such packet contains a header and several (Client|Server)Messages

## "Connectionless" (Out of band) packet
Such packets/messages are sent ad-hoc and don't require a running map instance, therefore could be sent by not connected clients.
They are sent separately, not as a part of jake2.qcommon.network.messages.NetworkPacket.
Mostly such packets contain a single string (though there are some cases when 2 strings are sent - `info` & `print`)

see jake2.qcommon.network.messages.ConnectionlessCommand

## Server Messages
see jake2.qcommon.network.messages.server

## Client Messages
see jake2.qcommon.network.messages.client

## Client States
see jake2.server.ClientStates

# Connection initialization
The following diagram illustrates client/server communication when client connectos to server.
Starting from typing connect 'someaddress' and until client becomes connected and receives the updates from the server. 
![Connection initialization](./connection.svg)
