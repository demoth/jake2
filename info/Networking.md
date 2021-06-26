#Quake 2 Networking Communication Protocol

## Server Messages
see jake2.qcommon.network.messages.server

## Client Messages
see jake2.qcommon.network.messages.client

## Client States
see jake2.server.ClientStates

## "Connectionless" packet
Such messages don't require a running map instance, therefore could be sent by not connected clients.

  * ping
  * ack
  * status
  * info
  * getchallenge
  * connect
  * rcon

# Connection initialization
The following diagram illustrates client/server communication when client connectos to server.
Starting from typing connect 'someaddress' and until client becomes connected and receives the updates from the server. 
![Connection initialization](./connection.svg)