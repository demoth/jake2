/*
 * NET.java
 * Copyright (C) 2003
 * 
 * $Id: NET.java,v 1.10 2004-02-01 23:31:38 rst Exp $
 */
/*
Copyright (C) 1997-2001 Id Software, Inc.

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.

*/
package jake2.sys;

import jake2.Defines;
import jake2.Globals;
import jake2.game.cvar_t;
import jake2.qcommon.*;

import java.net.*;

public final class NET extends Defines {

	public static netadr_t net_local_adr = new netadr_t();

	// 127.0.0.1
	public final static int LOOPBACK = 0x7f000001;

	public final static int MAX_LOOPBACK = 4;

	public static class loopmsg_t {
		byte data[] = new byte[Defines.MAX_MSGLEN];
		int datalen;
	};

	public static class loopback_t {
		public loopback_t()
		{
			msgs = new loopmsg_t[MAX_LOOPBACK];
			for (int n=0; n < MAX_LOOPBACK;n++)
			{
				msgs[n] = new loopmsg_t();
			}
		}
		loopmsg_t msgs[];
		
		int get, send;
	};

	public static loopback_t loopbacks[] = new loopback_t[2];
	static {
		loopbacks[0] = new loopback_t();
		loopbacks[1] = new loopback_t();
	}
	public static DatagramSocket ip_sockets[] = {null,null};
	//public static DatagramSocket ipx_sockets[] = new int[2];

	// we dont need beschissene sockaddr_in structs in java !

	//=============================================================================
	
//	void NetadrToSockadr (netadr_t *a, struct sockaddr_in *s)
//	{
//		memset (s, 0, sizeof(*s));
//	
//		if (a.type == NA_BROADCAST)
//		{
//			s.sin_family = AF_INET;
//	
//			s.sin_port = a.port;
//			*(int *)&s.sin_addr = -1;
//		}
//		else if (a.type == NA_IP)
//		{
//			s.sin_family = AF_INET;
//	
//			*(int *)&s.sin_addr = *(int *)&a.ip;
//			s.sin_port = a.port;
//		}
//	}
//	
//	void SockadrToNetadr (struct sockaddr_in *s, netadr_t *a)
//	{
//		*(int *)&a.ip = *(int *)&s.sin_addr;
//		a.port = s.sin_port;
//		a.type = NA_IP;
//	}
//	
	
	public static boolean CompareAdr(netadr_t a, netadr_t b) {
		if (a.ip[0] == b.ip[0] && a.ip[1] == b.ip[1] && a.ip[2] == b.ip[2] && a.ip[3] == b.ip[3] && a.port == b.port)
			return true;
		return false;
	}

	/*
	===================
	NET_CompareBaseAdr
	
	Compares without the port
	===================
	*/
	public static boolean NET_CompareBaseAdr(netadr_t a, netadr_t b) {
		if (a.type != b.type)
			return false;

		if (a.type == Defines.NA_LOOPBACK)
			return true;

		if (a.type == Defines.NA_IP) {
			if (a.ip[0] == b.ip[0] && a.ip[1] == b.ip[1] && a.ip[2] == b.ip[2] && a.ip[3] == b.ip[3])
				return true;
			return false;
		}

		/*
		if (a.type == Defines.NA_IPX) {
			for (int n = 0; n < 10; n++)
				if (a.ipx[n] != b.ipx[n])
					return false;

			//was:
			//if ((memcmp(a.ipx, b.ipx, 10) == 0)) 	return true;
			return true;
		}
		*/

		return false;
	}
	
	
	
	public static String AdrToString(netadr_t a) {
		//was:
		//static	char	s[64];
		//Com_sprintf (s, sizeof(s), "%i.%i.%i.%i:%i", a.ip[0], a.ip[1], a.ip[2], a.ip[3], ntohs(a.port));

		return "" + a.ip[0] + "." + a.ip[1] + "." + a.ip[2] + "." + a.ip[3] + ":" + a.port;
	}



	public static String NET_BaseAdrToString(netadr_t a) {
		//was:
		//static	char	s[64];
		//Com_sprintf (s, sizeof(s), "%i.%i.%i.%i", a.ip[0], a.ip[1], a.ip[2], a.ip[3]);
		return "" + a.ip[0] + "." + a.ip[1] + "." + a.ip[2] + "." + a.ip[3];
	}
	
	/*
	=============
	NET_StringToAdr
	
	localhost
	idnewt
	idnewt:28000
	192.246.40.70
	192.246.40.70:28000
	=============
	*/
//	boolean	NET_StringToSockaddr (char *s, struct sockaddr *sadr)
//	{
//		struct hostent	*h;
//		char	*colon;
//		char	copy[128];
//		
//		memset (sadr, 0, sizeof(*sadr));
//		((struct sockaddr_in *)sadr).sin_family = AF_INET;
//		
//		((struct sockaddr_in *)sadr).sin_port = 0;
//	
//		strcpy (copy, s);
//		// strip off a trailing :port if present
//		for (colon = copy ; *colon ; colon++)
//			if (*colon == ':')
//			{
//				*colon = 0;
//				((struct sockaddr_in *)sadr).sin_port = htons((short)atoi(colon+1));	
//			}
//		
//		if (copy[0] >= '0' && copy[0] <= '9')
//		{
//			*(int *)&((struct sockaddr_in *)sadr).sin_addr = inet_addr(copy);
//		}
//		else
//		{
//			if (! (h = gethostbyname(copy)) )
//				return 0;
//			*(int *)&((struct sockaddr_in *)sadr).sin_addr = *(int *)h.h_addr_list[0];
//		}
//		
//		return true;
//	}
//	
//	/*
//	=============
//	NET_StringToAdr
//	
//	localhost
//	idnewt
//	idnewt:28000
//	192.246.40.70
//	192.246.40.70:28000
//	=============
//	*/
//	boolean	NET_StringToAdr (char *s, netadr_t *a)
//	{
//		struct sockaddr_in sadr;
//		
//		if (!strcmp (s, "localhost"))
//		{
//			memset (a, 0, sizeof(*a));
//			a.type = NA_LOOPBACK;
//			return true;
//		}
//	
//		if (!NET_StringToSockaddr (s, (struct sockaddr *)&sadr))
//			return false;
//		
//		SockadrToNetadr (&sadr, a);
//	
//		return true;
//	}
//	
	
	public static boolean StringToAdr( String s, netadr_t a)
	{
		try
		{
			InetAddress ia = InetAddress.getByName(s);
			a.ip = ia.getAddress();
			return true;
		}
		catch
		(
			Exception e)
		{
			return false;
		}		
	}
	
	
	
	public static boolean	IsLocalAddress (netadr_t adr)
	{
		return CompareAdr (adr, net_local_adr);
	}
	
	/*
	=============================================================================
	
	LOOPBACK BUFFERS FOR LOCAL PLAYER
	
	=============================================================================
	*/
	
	// trivial! this SHOULD work !
	public static boolean	NET_GetLoopPacket (int sock, netadr_t net_from, sizebuf_t net_message)
	{
		int		i;
		loopback_t	loop;
	
		loop = loopbacks[sock];
	
		if (loop.send - loop.get > MAX_LOOPBACK)
			loop.get = loop.send - MAX_LOOPBACK;
	
		if (loop.get >= loop.send)
			return false;
	
		i = loop.get & (MAX_LOOPBACK-1);
		loop.get++;
	
		//memcpy (net_message.data, loop.msgs[i].data, loop.msgs[i].datalen);
		System.arraycopy(loop.msgs[i].data, 0, net_message.data, 0, loop.msgs[i].datalen);
		net_message.cursize = loop.msgs[i].datalen;
		
		net_from.ip = net_local_adr.ip;
		net_from.port = net_local_adr.port;
		net_from.type = net_local_adr.type;
		
		return true;
	
	}
	
	
	// trivial! this SHOULD work !
	public static void NET_SendLoopPacket (int sock, int length, byte [] data, netadr_t to)
	{
		int		i;
		loopback_t	loop;
	
		loop = loopbacks[sock^1];
	
		// modulo 4
		i = loop.send & (MAX_LOOPBACK-1);
		loop.send++;
	
		//memcpy (loop.msgs[i].data, data, length);
		
		System.arraycopy(data,0,loop.msgs[i].data,0,length);
		loop.msgs[i].datalen = length;
	}
	
	
	private static DatagramPacket receivedatagrampacket = new DatagramPacket(new byte[65507], 65507);
	
	//=============================================================================	
	public static boolean GetPacket (int sock, netadr_t net_from, sizebuf_t net_message)
	{
		DatagramSocket	net_socket;
//		int 	ret;
//		struct sockaddr_in	from;
//		int			fromlen;
//		
//		int			protocol;
//		int			err;
	
		if (NET_GetLoopPacket (sock, net_from, net_message))
			return true;
	
		net_socket = ip_sockets[sock];
			
		if (net_socket == null)
			return false;
	
		try
		{
			net_socket.receive(receivedatagrampacket);
			
			// no timeout...

			net_from.ip = receivedatagrampacket.getAddress().getAddress();
			net_from.port = receivedatagrampacket.getPort();

			if (receivedatagrampacket.getLength() > net_message.maxsize)
			{
				Com.Printf ("Oversize packet from " + AdrToString(net_from) + "\n");
				return false;
			}
			System.arraycopy(receivedatagrampacket.getData(), 0, net_message.data, 0 , receivedatagrampacket.getLength());
			
			return true;

		}
		catch(Exception e)
		{
			Com.Printf ("NET_GetPacket: " + e + " from " + 	AdrToString(net_from) + "\n");
			return false;
		}
		
			//fromlen = sizeof(from);
			//ret = recvfrom (net_socket, net_message.data, net_message.maxsize
			//	, 0, (struct sockaddr *)&from, &fromlen);
				
			//SockadrToNetadr (&from, net_from);
	
//			if (ret == -1)
//			{
//				err = errno;
//	
//				if (err == EWOULDBLOCK || err == ECONNREFUSED)
//					continue;
//				Com_Printf ("NET_GetPacket: %s from %s\n", NET_ErrorString(),
//							NET_AdrToString(*net_from));
//				continue;
//			}
//	
//			if (ret == net_message.maxsize)
//			{
//				Com_Printf ("Oversize packet from %s\n", NET_AdrToString (*net_from));
//				continue;
//			}
//	
//			net_message.cursize = ret;
//			return true;
		 
	}
	
//	=============================================================================
	
	public static void NET_SendPacket (int sock, int length, byte [] data, netadr_t to)
	{
		int		ret;
		//struct sockaddr_in	addr;
		
		DatagramSocket	net_socket;
	
		if ( to.type == NA_LOOPBACK )
		{
			NET_SendLoopPacket (sock, length, data, to);
			return;
		}
	
		if (to.type == NA_BROADCAST)
		{
			net_socket = ip_sockets[sock];
			if (net_socket==null)
				return;
		}
		else if (to.type == NA_IP)
		{
			net_socket = ip_sockets[sock];
			if (net_socket==null)
				return;
		}
		/*
		else if (to.type == NA_IPX)
		{
			net_socket = ipx_sockets[sock];
			if (net_socket==null)
				return;
		}
		else if (to.type == NA_BROADCAST_IPX)
		{
			net_socket = ipx_sockets[sock];
			if (!net_socket)
				return;
		}
		*/
		else
		{
			Com.Error (ERR_FATAL, "NET_SendPacket: bad address type");
			return;
		}
	
		//was:
		//NetadrToSockadr (&to, &addr);
	
		try
		{	//was:
			//ret = sendto (net_socket, data, length, 0, (struct sockaddr *)&addr, sizeof(addr) );
			DatagramPacket dp = new DatagramPacket(data, length, to.getInetAddress(), to.port);
			net_socket.send(dp);
		}
		catch(Exception e)
		{
			Com.Printf ("NET_SendPacket ERROR: " + e + " to " + AdrToString (to) + "\n");
		}
	}
	
	
	//=============================================================================
	
	
	
	
	/*
	====================
	NET_OpenIP
	====================
	*/
	public static void NET_OpenIP ()
	{
		cvar_t	port, ip;
	
		port = Cvar.Get ("port", "" + Defines.PORT_SERVER, CVAR_NOSET);
		ip = Cvar.Get ("ip", "localhost", CVAR_NOSET);
	
		if (ip_sockets[NS_SERVER]==null)
			ip_sockets[NS_SERVER] = NET_Socket (ip.string, (int)port.value);
			
		if (ip_sockets[NS_CLIENT]==null)
			ip_sockets[NS_CLIENT] = NET_Socket (ip.string, Defines.PORT_ANY);
	}
	
	/*
	====================
	NET_OpenIPX
	====================
	*/
	public static void NET_OpenIPX ()
	{
	}
	
	
	/*
	====================
	NET_Config
	
	A single player game will only use the loopback code
	====================
	*/
	public static void NET_Config (boolean multiplayer)
	{
		int i;
	
		if (!multiplayer)
		{	// shut down any existing sockets
			for (i=0 ; i<2 ; i++)
			{
				if (ip_sockets[i]!=null)
				{
					ip_sockets[i].close();
					ip_sockets[i] = null;
				}
				/*
				if (ipx_sockets[i])
				{
					ipx_sockets[i].close();
					ipx_sockets[i] = null;
				}
				*/
			}
		}
		else
		{	// open sockets
			NET_OpenIP ();
		}
	}
	
	
	//===================================================================
	
	
	/*
	====================
	NET_Init
	====================
	*/
	public static void NET_Init ()
	{
	}
	
	
	// TODO: check this, im not a network socket expert.
	/*
	====================
	NET_Socket
	====================
	*/
	public static DatagramSocket NET_Socket (String ip, int port)
	{
		
		DatagramSocket newsocket = null;
		try
		{
			if (ip==null || ip.length()==0 || ip.equals("localhost"))
			{
				if (port == PORT_ANY)
				{
					newsocket = new DatagramSocket();
				}
				else
				{
					newsocket = new DatagramSocket(port);
				}
			}
			else
			{
				InetAddress ia = InetAddress.getByName(ip);
				newsocket = new DatagramSocket(port, ia);
			}
			
			newsocket.setBroadcast(true);
			// nonblocking (1 ms), 0== neverending infinite timeout
			newsocket.setSoTimeout(1);			
		}
		catch (Exception e)
		{
			newsocket = null;
		}
		
		return newsocket;
		
//		int newsocket;
//		//struct sockaddr_in address;
//		qboolean _true = true;
//		int	i = 1;
//	
//		if ((newsocket = socket (PF_INET, SOCK_DGRAM, IPPROTO_UDP)) == -1)
//		{
//			Com.Printf ("ERROR: UDP_OpenSocket: socket: ", NET_ErrorString());
//			return 0;
//		}
//	
//		// make it non-blocking
//		if (ioctl (newsocket, FIONBIO, &_true) == -1)
//		{
//			Com_Printf ("ERROR: UDP_OpenSocket: ioctl FIONBIO:%s\n", NET_ErrorString());
//			return 0;
//		}
//	
//		// make it broadcast capable
//		if (setsockopt(newsocket, SOL_SOCKET, SO_BROADCAST, (char *)&i, sizeof(i)) == -1)
//		{
//			Com_Printf ("ERROR: UDP_OpenSocket: setsockopt SO_BROADCAST:%s\n", NET_ErrorString());
//			return 0;
//		}
//	
//		if (!net_interface || !net_interface[0] || !stricmp(net_interface, "localhost"))
//			address.sin_addr.s_addr = INADDR_ANY;
//		else
//			NET_StringToSockaddr (net_interface, (struct sockaddr *)&address);
//	
//		if (port == PORT_ANY)
//			address.sin_port = 0;
//		else
//			address.sin_port = htons((short)port);
//	
//		address.sin_family = AF_INET;
//	
//		if( bind (newsocket, (void *)&address, sizeof(address)) == -1)
//		{
//			Com_Printf ("ERROR: UDP_OpenSocket: bind: %s\n", NET_ErrorString());
//			close (newsocket);
//			return 0;
//		}
//	
//		return newsocket;
	}
	
	
	/*
	====================
	NET_Shutdown
	====================
	*/
	public static void NET_Shutdown ()
	{
		NET_Config (false);	// close sockets
	}
	
	
	/*
	====================
	NET_ErrorString
	====================
	*/
	public static String NET_ErrorString() {
		int code;

		//code = errno;
		//return strerror (code);
		return "errno can not yet resolved in java";
	}
	
	// sleeps msec or until net socket is ready
	public static void NET_Sleep(int msec)
	{
		if (ip_sockets[NS_SERVER]==null || (Globals.dedicated!=null && Globals.dedicated.value == 0))
			return; // we're not a server, just run full speed
		
		//ip_sockets[NS_SERVER].
		
		
		// this should wait up to 100ms until a packet 
		/*
	    struct timeval timeout;
		fd_set	fdset;
		extern cvar_t *dedicated;
		extern qboolean stdin_active;
	
		if (!ip_sockets[NS_SERVER] || (dedicated && !dedicated.value))
			return; // we're not a server, just run full speed
	
		FD_ZERO(&fdset);
		if (stdin_active)
			FD_SET(0, &fdset); // stdin is processed too
		FD_SET(ip_sockets[NS_SERVER], &fdset); // network socket
		timeout.tv_sec = msec/1000;
		timeout.tv_usec = (msec%1000)*1000;
		select(ip_sockets[NS_SERVER]+1, &fdset, NULL, NULL, &timeout);
		*/
	}

}
