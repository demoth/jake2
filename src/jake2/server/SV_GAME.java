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

// Created on 14.01.2004 by RST.
// $Id: SV_GAME.java,v 1.5 2004-01-25 21:45:45 rst Exp $

package jake2.server;

import jake2.*;
import jake2.client.*;
import jake2.game.*;
import jake2.qcommon.*;
import jake2.render.*;
import jake2.sys.Sys;

public class SV_GAME extends SV_INIT
{

	// sv_game.c -- interface to the game dll

	public static game_export_t ge;

	/*
	===============
	PF_Unicast
	
	Sends the contents of the mutlicast buffer to a single client
	===============
	*/
	public static void PF_Unicast(edict_t ent, boolean reliable)
	{
		int p;
		client_t client;

		if (ent == null)
			return;

		//p = NUM_FOR_EDICT(ent);
		p = ent.s.number;
		if (p < 1 || p > maxclients.value)
			return;

		client = SV_INIT.svs.clients[p - 1];

		if (reliable)
			SZ.Write(client.netchan.message, SV_INIT.sv.multicast.data, SV_INIT.sv.multicast.cursize);
		else
			SZ.Write(client.datagram, sv.multicast.data, sv.multicast.cursize);

		SZ.Clear(sv.multicast);
	}

	/*
	===============
	PF_dprintf
	
	Debug print to server console
	===============
	*/
	public static void PF_dprintf(String fmt)
	{
		/*
		char		msg[1024];
		va_list		argptr;
		
		va_start (argptr,fmt);
		vsprintf (msg, fmt, argptr);
		va_end (argptr);
		
		*/

		Com.Printf(fmt);
	}

	
	/*
	===============
	PF_cprintf
	
	Print to a single client
	===============
	*/
	public static void PF_cprintf (edict_t  ent, int level, String fmt)
	{
		//char		msg[1024];
		//va_list		argptr;
		int			n=0;
	
		if (ent!=null)
		{
			
			//n = NUM_FOR_EDICT(ent);
			n = ent.s.number;
			if (n < 1 || n > maxclients.value)
				Com.Error (ERR_DROP, "cprintf to a non-client");
		}
	
//		va_start (argptr,fmt);
//		vsprintf (msg, fmt, argptr);
//		va_end (argptr);
	
		if (ent!=null)
			SV_SEND.SV_ClientPrintf (svs.clients[n-1], level, fmt);
		else
			Com.Printf (fmt);
	}
	
	
	/*
	===============
	PF_centerprintf
	
	centerprint to a single client
	===============
	*/
	public static void PF_centerprintf (edict_t ent, String fmt)
	{
		//char		msg[1024];
		//va_list		argptr;
		int			n;
		
		//TODO:  NUM_FOR_EDICT
		//n = NUM_FOR_EDICT(ent);
		n = ent.s.number;
		if (n < 1 || n > maxclients.value)
			return;	// Com_Error (ERR_DROP, "centerprintf to a non-client");
	
//		va_start (argptr,fmt);
//		vsprintf (msg, fmt, argptr);
//		va_end (argptr);
	
		MSG.WriteByte (sv.multicast,svc_centerprint);
		MSG.WriteString (sv.multicast,fmt);
		PF_Unicast (ent, true);
	}
	
	
	/*
	===============
	PF_error
	
	Abort the server with a game error
	===============
	*/
	public static void PF_error ( String fmt)
	{
//		char		msg[1024];
//		va_list		argptr;
//		
//		va_start (argptr,fmt);
//		vsprintf (msg, fmt, argptr);
//		va_end (argptr);
	
		Com.Error (ERR_DROP, "Game Error: " + fmt );
	}
	
	public static void PF_error (int level, String fmt)
	{
		Com.Error(level, fmt);
	}
	
	
	/*
	=================
	PF_setmodel
	
	Also sets mins and maxs for inline bmodels
	=================
	*/
	public static void PF_setmodel (edict_t  ent, String name)
	{
		int		i;
		cmodel_t	mod;
	
		if (name==null)
			Com.Error (ERR_DROP, "PF_setmodel: NULL");
	
		i = SV_ModelIndex (name);
			
	//	ent.model = name;
		ent.s.modelindex = i;
	
	// if it is an inline model, get the size information for it
		if (name.charAt(0) == '*')
		{
			mod = CM.CM_InlineModel (name);
			VectorCopy (mod.mins, ent.mins);
			VectorCopy (mod.maxs, ent.maxs);
			SV_WORLD.SV_LinkEdict (ent);
		}
	
	}
	
	/*
	===============
	PF_Configstring
	
	===============
	*/
	public static void PF_Configstring (int index, String val)
	{
		if (index < 0 || index >= MAX_CONFIGSTRINGS)
			Com.Error (ERR_DROP, "configstring: bad index " + index + "\n");
	
		if (val==null)
			val = "";
	
		// change the string in sv
		sv.configstrings[index] = val;
	
		
		if (sv.state != ss_loading)
		{	// send the update to everyone
			SZ.Clear (sv.multicast);
			MSG.WriteChar (sv.multicast, svc_configstring);
			MSG.WriteShort (sv.multicast, index);
			MSG.WriteString (sv.multicast, val);
	
			SV_SEND.SV_Multicast (vec3_origin, MULTICAST_ALL_R);
		}
	}
	
	
	
	public static void PF_WriteChar (int c) {MSG.WriteChar (sv.multicast, c);}
	public static void PF_WriteByte (int c) {MSG.WriteByte (sv.multicast, c);}
	public static void PF_WriteShort (int c) {MSG.WriteShort (sv.multicast, c);}
	public static void PF_WriteLong (int c) {MSG.WriteLong (sv.multicast, c);}
	public static void PF_WriteFloat (float f) {MSG.WriteFloat (sv.multicast, f);}
	public static void PF_WriteString (String s) {MSG.WriteString (sv.multicast, s);}
	public static void PF_WritePos (float []  pos) {MSG.WritePos (sv.multicast, pos);}
	public static void PF_WriteDir (float []  dir) {MSG.WriteDir (sv.multicast, dir);}
	public static void PF_WriteAngle (float f) {MSG.WriteAngle (sv.multicast, f);}
	
	
	/*
	=================
	PF_inPVS
	
	Also checks portalareas so that doors block sight
	=================
	*/
	public static boolean PF_inPVS (float []  p1, float []  p2)
	{
		int		leafnum;
		int		cluster;
		int		area1, area2;
		byte		mask[];
	
		leafnum = CM.CM_PointLeafnum (p1);
		cluster = CM.CM_LeafCluster (leafnum);
		area1 = CM.CM_LeafArea (leafnum);
		mask = CM.CM_ClusterPVS (cluster);
	
		leafnum = CM.CM_PointLeafnum (p2);
		cluster = CM.CM_LeafCluster (leafnum);
		area2 = CM.CM_LeafArea (leafnum);
		
		if ( mask!=null && (0==(mask[cluster>>3] & (1<<(cluster&7)) ) ) )
			return false;
			
		if (!CM.CM_AreasConnected (area1, area2))
			return false;		// a door blocks sight
		return true;
	}
	
	
	/*
	=================
	PF_inPHS
	
	Also checks portalareas so that doors block sound
	=================
	*/
	public static boolean PF_inPHS (float []  p1, float []  p2)
	{
		int		leafnum;
		int		cluster;
		int		area1, area2;
		byte		mask[];
	
		leafnum = CM.CM_PointLeafnum (p1);
		cluster = CM.CM_LeafCluster (leafnum);
		area1 = CM.CM_LeafArea (leafnum);
		mask = CM.CM_ClusterPHS (cluster);
	
		leafnum = CM.CM_PointLeafnum (p2);
		cluster = CM.CM_LeafCluster (leafnum);
		area2 = CM.CM_LeafArea (leafnum);
		
		if ( mask!=null && (0==(mask[cluster>>3] & (1<<(cluster&7)) ) ) )
			return false;		// more than one bounce away
		if (!CM.CM_AreasConnected (area1, area2))
			return false;		// a door blocks hearing
	
		return true;
	}
	
	public static void PF_StartSound (edict_t  entity, int channel, int sound_num, float volume,
	    float attenuation, float timeofs)
	{
		if (null==entity)
			return;
		SV_SEND.SV_StartSound (null, entity, channel, sound_num, volume, attenuation, timeofs);
	}
	
	//==============================================

	/*
	===============
	SV_ShutdownGameProgs
	
	Called when either the entire server is being killed, or
	it is changing to a different game directory.
	===============
	*/
	public static void SV_ShutdownGameProgs()
	{
		if (ge == null)
			return;
		ge.Shutdown();
		Sys.UnloadGame();
		ge = null;
	}

	/*
	===============
	SV_InitGameProgs
	
	Init the game subsystem for a new map
	===============
	*/

	public static void SV_InitGameProgs()
	{

		// unload anything we have now
		if (ge!=null)
			SV_ShutdownGameProgs();

		game_import_t gimport = new game_import_t();
		
		
		// load a new game dll
		/* A L L   S E T   I N  GAME_IMPORT_T !  RST
		gimport.multicast = SV_Multicast;
		gimport.unicast = PF_Unicast;
		gimport.bprintf = SV_BroadcastPrintf;
		gimport.dprintf = PF_dprintf;
		gimport.cprintf = PF_cprintf;
		gimport.centerprintf = PF_centerprintf;
		gimport.error = PF_error;

		gimport.linkentity = SV_LinkEdict;
		gimport.unlinkentity = SV_UnlinkEdict;
		gimport.BoxEdicts = SV_AreaEdicts;
		gimport.trace = SV_Trace;
		gimport.pointcontents = SV_PointContents;
		gimport.setmodel = PF_setmodel;
		gimport.inPVS = PF_inPVS;
		gimport.inPHS = PF_inPHS;
		gimport.Pmove = Pmove;

		gimport.modelindex = SV_ModelIndex;
		gimport.soundindex = SV_SoundIndex;
		gimport.imageindex = SV_ImageIndex;

		gimport.configstring = PF_Configstring;
		gimport.sound = PF_StartSound;
		gimport.positioned_sound = SV_StartSound;

		gimport.WriteChar = PF_WriteChar;
		gimport.WriteByte = PF_WriteByte;
		gimport.WriteShort = PF_WriteShort;
		gimport.WriteLong = PF_WriteLong;
		gimport.WriteFloat = PF_WriteFloat;
		gimport.WriteString = PF_WriteString;
		gimport.WritePosition = PF_WritePos;
		gimport.WriteDir = PF_WriteDir;
		gimport.WriteAngle = PF_WriteAngle;

		gimport.TagMalloc = Z_TagMalloc;
		gimport.TagFree = Z_Free;
		gimport.FreeTags = Z_FreeTags;

		gimport.cvar = Cvar_Get;
		gimport.cvar_set = Cvar_Set;
		gimport.cvar_forceset = Cvar_ForceSet;

		gimport.argc = Cmd_Argc;
		gimport.argv = Cmd_Argv;
		gimport.args = Cmd_Args;
		gimport.AddCommandString = Cbuf_AddText;

		gimport.DebugGraph = SCR_DebugGraph;
		gimport.SetAreaPortalState = CM_SetAreaPortalState;
		gimport.AreasConnected = CM_AreasConnected;
		*/
		ge = Sys.GetGameAPI(gimport);

		if (ge == null)
			Com.Error(ERR_DROP, "failed to load game DLL");
		if (ge.apiversion != GAME_API_VERSION)
			Com.Error(ERR_DROP, "game is version " + ge.apiversion + " not " + GAME_API_VERSION);

		ge.Init();
	}

}
