/*
 * CL_view.java
 * Copyright (C) 2004
 * 
 * $Id: CL_view.java,v 1.1 2004-01-28 10:03:06 hoz Exp $
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
package jake2.client;




public class CL_view extends CL_input {
//	/*
//	Copyright (C) 1997-2001 Id Software, Inc.
//
//	This program is free software; you can redistribute it and/or
//	modify it under the terms of the GNU General Public License
//	as published by the Free Software Foundation; either version 2
//	of the License, or (at your option) any later version.
//
//	This program is distributed in the hope that it will be useful,
//	but WITHOUT ANY WARRANTY; without even the implied warranty of
//	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  
//
//	See the GNU General Public License for more details.
//
//	You should have received a copy of the GNU General Public License
//	along with this program; if not, write to the Free Software
//	Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//
//	*/	
//	/*
//	Copyright (C) 1997-2001 Id Software, Inc.
//
//	This program is free software; you can redistribute it and/or
//	modify it under the terms of the GNU General Public License
//	as published by the Free Software Foundation; either version 2
//	of the License, or (at your option) any later version.
//
//	This program is distributed in the hope that it will be useful,
//	but WITHOUT ANY WARRANTY; without even the implied warranty of
//	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  
//
//	See the GNU General Public License for more details.
//
//	You should have received a copy of the GNU General Public License
//	along with this program; if not, write to the Free Software
//	Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//
//	*/
////	   cl_view.c -- player rendering positioning
//
//	#include "client.h"
//
////	  =============
////
////	   development tools for weapons
////
//	int			gun_frame;
//	struct model_s	*gun_model;
//
////	  =============
//
//	cvar_t		*crosshair;
//	cvar_t		*cl_testparticles;
//	cvar_t		*cl_testentities;
//	cvar_t		*cl_testlights;
//	cvar_t		*cl_testblend;
//
//	cvar_t		*cl_stats;
//
//
//	int			r_numdlights;
//	dlight_t	r_dlights[MAX_DLIGHTS];
//
//	int			r_numentities;
//	entity_t	r_entities[MAX_ENTITIES];
//
//	int			r_numparticles;
//	particle_t	r_particles[MAX_PARTICLES];
//
//	lightstyle_t	r_lightstyles[MAX_LIGHTSTYLES];
//
//	char cl_weaponmodels[MAX_CLIENTWEAPONMODELS][MAX_QPATH];
//	int num_cl_weaponmodels;
//



//
////	  ===================================================================
//
//	/*
//	=================
//	CL_PrepRefresh
//
//	Call before entering a new level, or after changing dlls
//	=================
//	*/
	static void PrepRefresh() {
//		char		mapname[32];
//		int			i;
//		char		name[MAX_QPATH];
//		float		rotate;
//		vec3_t		axis;
//
//		if (!cl.configstrings[CS_MODELS+1][0])
//			return;		// no map loaded
//
//		SCR_AddDirtyPoint (0, 0);
//		SCR_AddDirtyPoint (viddef.width-1, viddef.height-1);
//
//		// let the render dll load the map
//		strcpy (mapname, cl.configstrings[CS_MODELS+1] + 5);	// skip "maps/"
//		mapname[strlen(mapname)-4] = 0;		// cut off ".bsp"
//
//		// register models, pics, and skins
//		Com_Printf ("Map: %s\r", mapname); 
//		SCR_UpdateScreen ();
//		re.BeginRegistration (mapname);
//		Com_Printf ("                                     \r");
//
//		// precache status bar pics
//		Com_Printf ("pics\r"); 
//		SCR_UpdateScreen ();
//		SCR_TouchPics ();
//		Com_Printf ("                                     \r");
//
//		CL_RegisterTEntModels ();
//
//		num_cl_weaponmodels = 1;
//		strcpy(cl_weaponmodels[0], "weapon.md2");
//
//		for (i=1 ; i<MAX_MODELS && cl.configstrings[CS_MODELS+i][0] ; i++)
//		{
//			strcpy (name, cl.configstrings[CS_MODELS+i]);
//			name[37] = 0;	// never go beyond one line
//			if (name[0] != '*')
//				Com_Printf ("%s\r", name); 
//			SCR_UpdateScreen ();
//			Sys_SendKeyEvents ();	// pump message loop
//			if (name[0] == '#')
//			{
//				// special player weapon model
//				if (num_cl_weaponmodels < MAX_CLIENTWEAPONMODELS)
//				{
//					strncpy(cl_weaponmodels[num_cl_weaponmodels], cl.configstrings[CS_MODELS+i]+1,
//						sizeof(cl_weaponmodels[num_cl_weaponmodels]) - 1);
//					num_cl_weaponmodels++;
//				}
//			} 
//			else
//			{
//				cl.model_draw[i] = re.RegisterModel (cl.configstrings[CS_MODELS+i]);
//				if (name[0] == '*')
//					cl.model_clip[i] = CM_InlineModel (cl.configstrings[CS_MODELS+i]);
//				else
//					cl.model_clip[i] = NULL;
//			}
//			if (name[0] != '*')
//				Com_Printf ("                                     \r");
//		}
//
//		Com_Printf ("images\r", i); 
//		SCR_UpdateScreen ();
//		for (i=1 ; i<MAX_IMAGES && cl.configstrings[CS_IMAGES+i][0] ; i++)
//		{
//			cl.image_precache[i] = re.RegisterPic (cl.configstrings[CS_IMAGES+i]);
//			Sys_SendKeyEvents ();	// pump message loop
//		}
//	
//		Com_Printf ("                                     \r");
//		for (i=0 ; i<MAX_CLIENTS ; i++)
//		{
//			if (!cl.configstrings[CS_PLAYERSKINS+i][0])
//				continue;
//			Com_Printf ("client %i\r", i); 
//			SCR_UpdateScreen ();
//			Sys_SendKeyEvents ();	// pump message loop
//			CL_ParseClientinfo (i);
//			Com_Printf ("                                     \r");
//		}
//
//		CL_LoadClientinfo (&cl.baseclientinfo, "unnamed\\male/grunt");
//
//		// set sky textures and speed
//		Com_Printf ("sky\r", i); 
//		SCR_UpdateScreen ();
//		rotate = atof (cl.configstrings[CS_SKYROTATE]);
//		sscanf (cl.configstrings[CS_SKYAXIS], "%f %f %f", 
//			&axis[0], &axis[1], &axis[2]);
//		re.SetSky (cl.configstrings[CS_SKY], rotate, axis);
//		Com_Printf ("                                     \r");
//
//		// the renderer can now free unneeded stuff
//		re.EndRegistration ();
//
//		// clear any lines of console text
//		Con_ClearNotify ();
//
//		SCR_UpdateScreen ();
//		cl.refresh_prepped = true;
//		cl.force_refdef = true;	// make sure we have a valid refdef
//
//		// start the cd track
//		CDAudio_Play (atoi(cl.configstrings[CS_CDTRACK]), true);
	}
//
//	/*
//	====================
//	CalcFov
//	====================
//	*/
//	float CalcFov (float fov_x, float width, float height)
//	{
//		float	a;
//		float	x;
//
//		if (fov_x < 1 || fov_x > 179)
//			Com_Error (ERR_DROP, "Bad fov: %f", fov_x);
//
//		x = width/tan(fov_x/360*M_PI);
//
//		a = atan (height/x);
//
//		a = a*360/M_PI;
//
//		return a;
//	}
//
////	  ============================================================================
//
////	   gun frame debugging functions

//

//
////	  ============================================================================
//
//

//

//
//


}
