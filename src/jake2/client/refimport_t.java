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

// Created on 20.11.2003 by RST.
// modified by cwei

package jake2.client;

import java.awt.Dimension;

import jake2.game.*;
import jake2.qcommon.xcommand_t;

public interface refimport_t {
	// ref.h 
	// these are the functions imported by the refresh module
	//
	void Sys_Error(int err_level, String str, Object[] vargs);

	void Cmd_AddCommand(String name, xcommand_t cmd);
	void Cmd_RemoveCommand(String name);
	int Cmd_Argc();
	String Cmd_Argv(int i);
	void Cmd_ExecuteText(int exec_when, String text);

	void Con_Printf(int print_level, String str, Object[] vargs);

	// files will be memory mapped read only
	// the returned buffer may be part of a larger pak file,
	// or a discrete file from anywhere in the quake search path
	// a -1 return means the file does not exist
	// NULL can be passed for buf to just determine existance
	int FS_LoadFile(String name, byte[] buf);
	void FS_FreeFile(byte[] buf);
	// gamedir will be the current directory that generated
	// files should be stored to, ie: "f:\quake\id1"
	String FS_Gamedir();

	cvar_t Cvar_Get(String name, String value, int flags);
	cvar_t Cvar_Set(String name, String value);
	void Cvar_SetValue(String name, float value);

	boolean Vid_GetModeInfo(Dimension dim /* int *w,  *h */, int mode);
	void Vid_MenuInit();
	void Vid_NewWindow(int width, int height);
}