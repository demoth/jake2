/*
 * FS.java
 * Copyright (C) 2003
 * 
 * $Id: FS.java,v 1.13 2003-12-04 15:09:20 cwei Exp $
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
package jake2.qcommon;

import jake2.Globals;
import jake2.game.Cmd;
import jake2.game.cvar_t;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.stream.FileImageInputStream;


/**
 * FS
 * TODO complete FS interface
 */
public final class FS {
	
	private static Logger logger = Logger.getLogger(FS.class.getName());

//	#include "qcommon.h"
//
////	   define this to dissalow any data but the demo pak file
////	  #define	NO_ADDONS
//
////	   if a packfile directory differs from this, it is assumed to be hacked
////	   Full version
//	#define	PAK0_CHECKSUM	0x40e614e0
////	   Demo
////	  #define	PAK0_CHECKSUM	0xb2c6d7ea
////	   OEM
////	  #define	PAK0_CHECKSUM	0x78e135c
//
//	/*
//	=============================================================================
//
//	QUAKE FILESYSTEM
//
//	=============================================================================
//	*/
//
//
////
////	   in memory
////
//
	static class packfile_t {
		static final int SIZE = 64;

		String name; // char name[56]
		int filepos, filelen;
		public String toString() {
			return name + " [ length: " + filelen + " pos: " + filepos + " ]";
		}
	}
//
	static class pack_t {
		String filename;
		FileImageInputStream handle;
		int numfiles;
		packfile_t[] files;
	}
//
	static String fs_gamedir;
	static cvar_t	fs_basedir;
	static cvar_t	fs_cddir;
	static cvar_t	fs_gamedirvar;
//
	static class filelink_t {
		filelink_t next;
		String from;
		int fromlength;
		String to;
	}
//
	static filelink_t fs_links;
//
	static class searchpath_t {
		String filename;
		pack_t pack;		// only one of filename / pack will be used
		searchpath_t next;
	}
//
	static searchpath_t fs_searchpaths;
	static searchpath_t fs_base_searchpaths;	// without gamedirs
//
//
//	/*
//
//	All of Quake's data access is through a hierchal file system, but the contents of the file system can be transparently merged from several sources.
//
//	The "base directory" is the path to the directory holding the quake.exe and all game directories.  The sys_* files pass this to host_init in quakeparms_t->basedir.  This can be overridden with the "-basedir" command line parm to allow code debugging in a different directory.  The base directory is
//	only used during filesystem initialization.
//
//	The "game directory" is the first tree on the search path and directory that all generated files (savegames, screenshots, demos, config files) will be saved to.  This can be overridden with the "-game" command line parameter.  The game directory can never be changed while quake is executing.  This is a precacution against having a malicious server instruct clients to write files over areas they shouldn't.
//
//	*/
//
//
//	/*
//	================
//	FS_filelength
//	================
//	*/
	public static int filelength (File f) {
		return (int)f.length();
	}
//
//	/*
//	============
//	FS_CreatePath
//
//	Creates any directories needed to store the given filename
//	============
//	*/
	public static void CreatePath (String path) {
//		char	*ofs;
//	
//		for (ofs = path+1 ; *ofs ; ofs++)
//		{
//			if (*ofs == '/')
//			{	// create the directory
//				*ofs = 0;
//				Sys_Mkdir (path);
//				*ofs = '/';
//			}
//		}
		int index = path.lastIndexOf('/');
		// -1 if not found and 0 means write to root
		if ( index > 0 ) {
			File f = new File(path.substring(0, index));
			if ( !f.mkdirs() ) {
				logger.log(Level.WARNING, "can't create path \"" + path + '"');
			}	
		}
	}
//
//
//	/*
//	==============
//	FS_FCloseFile
//
//	For some reason, other dll's can't just cal fclose()
//	on files returned by FS_FOpenFile...
//	==============
//	*/
	static void FCloseFile (InputStream in) throws IOException
	{
//		fclose (f);
		in.close();
	}
//
//
////	   RAFAEL
//	/*
//		Developer_searchpath
//	*/
//	int	Developer_searchpath (int who)
//	{
//	
//		int		ch;
//		// PMM - warning removal
////		char	*start;
//		searchpath_t	*search;
//	
//		if (who == 1) // xatrix
//			ch = 'x';
//		else if (who == 2)
//			ch = 'r';
//
//		for (search = fs_searchpaths ; search ; search = search->next)
//		{
//			if (strstr (search->filename, "xatrix"))
//				return 1;
//
//			if (strstr (search->filename, "rogue"))
//				return 2;
//	/*
//			start = strchr (search->filename, ch);
//
//			if (start == NULL)
//				continue;
//
//			if (strcmp (start ,"xatrix") == 0)
//				return (1);
//	*/
//		}
//		return (0);
//
//	}
//
//
//	/*
//	===========
//	FS_FOpenFile
//
//	Finds the file in the search path.
//	returns filesize and an open FILE *
//	Used for streaming data out of either a pak file or
//	a seperate file.
//	===========
//	*/
	static int file_from_pak = 0;
//	#ifndef NO_ADDONS
	static int FOpenFile(String filename, File file)
	{
		searchpath_t search;
		String netpath;
		pack_t pak;
		int i;
		filelink_t link;
//
		file_from_pak = 0;
//
//		// check for links first
		for (link = fs_links ; link != null ; link=link.next)
		{
//			if (!strncmp (filename, link->from, link->fromlength))
			if (filename.regionMatches(0, link.from, 0, link.fromlength))
			{
//				Com.sprintf (netpath, sizeof(netpath), "%s%s",link->to, filename+link->fromlength);
				netpath = link.to + filename.substring(link.fromlength);
				file = new File(netpath);
				if (file.canRead())
				{		
					Com.DPrintf ("link file: " + netpath +'\n');
					return FS.filelength(file);
				}
				return -1;
			}
		}
//
////
////	   search through the path, one element at a time
////
		for (search = fs_searchpaths ; search != null ; search = search.next)
		{
//		// is the element a pak file?
			if (search.pack != null)
			{
//			// look through all the pak file elements
				pak = search.pack;
				for (i=0 ; i < pak.numfiles ; i++)
//					if (!Q_strcasecmp (pak->files[i].name, filename))
					if (filename.equalsIgnoreCase(pak.files[i].name))
					{	// found it!
						file_from_pak = 1;
						Com.DPrintf ("PackFile: " + pak.filename + " : " + filename + '\n');
//					// open a new file on the pakfile
						file = new File(pak.filename);
						if (!file.canRead())
							Com.Error(Globals.ERR_FATAL, "Couldn't reopen " + pak.filename);	
//						fseek (*file, pak->files[i].filepos, SEEK_SET);
						// TODO setzte seek() fuer pakfile
						return pak.files[i].filelen;
					}
			}
			else
			{		
//		// check a file in the directory tree
//			
//				Com_sprintf (netpath, sizeof(netpath), "%s/%s",search->filename, filename);
				netpath = search.filename + '/' + filename;
//			
				file = new File(netpath);
				if (!file.canRead()) continue;
//			
				Com.DPrintf("FindFile: " + netpath +'\n');
//
				return FS.filelength(file);
			}
//		
		}
//	
		Com.DPrintf ("FindFile: can't find " + filename + '\n');
//	
		file = null;
		return -1;
	}
//
//
//	/*
//	=================
//	FS_ReadFile
//
//	Properly handles partial reads
//	=================
//	*/
//	void CDAudio_Stop(void);
	static final int MAX_READ	= 0x10000; // read in blocks of 64k
//	void FS_Read (void *buffer, int len, FILE *f)
//	{
//		int		block, remaining;
//		int		read;
//		byte	*buf;
//		int		tries;
//
//		buf = (byte *)buffer;
//
//		// read in chunks for progress bar
//		remaining = len;
//		tries = 0;
//		while (remaining)
//		{
//			block = remaining;
//			if (block > MAX_READ)
//				block = MAX_READ;
//			read = fread (buf, 1, block, f);
//			if (read == 0)
//			{
//				// we might have been trying to read from a CD
//				if (!tries)
//				{
//					tries = 1;
//					CDAudio_Stop();
//				}
//				else
//					Com_Error (ERR_FATAL, "FS_Read: 0 bytes read");
//			}
//
//			if (read == -1)
//				Com_Error (ERR_FATAL, "FS_Read: -1 bytes read");
//
//			// do some progress bar thing here...
//
//			remaining -= read;
//			buf += read;
//		}
//	}
//
//	/*
//	============
//	FS_LoadFile
//
//	Filename are reletive to the quake search path
//	a null buffer will just return the file length without loading
//	============
//	*/
	public static byte[] LoadFile(String path)
	{
//		FILE	*h;

		byte[] buf = null;
		int		len = 0;
		
//
////	   look for it in the filesystem or pack files
//		len = FS_FOpenFile (path, &h);
//		if (!h)
//		{
	// TODO		if (!shouldLoad) return null;
//		}
//	
//		if (!buffer)
//		{
//			fclose (h);
//			return len;
//		}
//
//		buf = Z_Malloc(len);
//		*buffer = buf;
//
//		FS_Read (buf, len, h);
//
//		fclose (h);
//
		return buf;
	}
//
//
//	/*
//	=============
//	FS_FreeFile
//	=============
//	*/
	public static void FreeFile(byte[] buffer)
	{
		Z.Free(buffer);
	}
//

	static final int IDPAKHEADER  =  (('K'<<24)+('C'<<16)+('A'<<8)+'P');
	
	static class dpackheader_t {
		int ident;      // == IDPAKHEADER
		int dirofs;
		int dirlen;
	} 

	static final int MAX_FILES_IN_PACK = 4096;

//	/*
//	=================
//	FS_LoadPackFile
//
//	Takes an explicit (not game tree related) path to a pak file.
//
//	Loads the header and directory, adding the files at the beginning
//	of the list so they override previous pack files.
//	=================
//	*/
	static pack_t LoadPackFile(String packfile) throws IOException
	{
		dpackheader_t header;
		packfile_t[] newfiles;
		int numpackfiles = 0;
		pack_t pack = null;
		FileImageInputStream packhandle;
//		unsigned		checksum;
//
		try {
			packhandle = new FileImageInputStream(new RandomAccessFile(packfile, "r"));
			packhandle.setByteOrder(ByteOrder.LITTLE_ENDIAN);
			
			if (packhandle.length() < 1) return null;
		} catch (IOException e) {
			logger.log(Level.FINEST, e.toString());
			return null;
		}
//
		header = new dpackheader_t();
		header.ident = packhandle.readInt();		
		header.dirofs = packhandle.readInt();
		header.dirlen = packhandle.readInt();
		if (header.ident != IDPAKHEADER)
			Com.Error (Globals.ERR_FATAL, packfile + " is not a packfile");
//
		numpackfiles = header.dirlen / packfile_t.SIZE;
//
		if (numpackfiles > MAX_FILES_IN_PACK)
			Com.Error (Globals.ERR_FATAL, packfile +" has " + numpackfiles +" files");
//
		newfiles = new packfile_t[numpackfiles];
//
		packhandle.seek(header.dirofs);

		// buffer for C-Strings char[56] 
		byte[] text = new byte[56];
		// parse the directory
		for (int i=0; i < numpackfiles; i++) {
			packhandle.readFully(text);
			newfiles[i] = new packfile_t(); 
			newfiles[i].name = new String(text).trim();
			newfiles[i].filepos = packhandle.readInt();
			newfiles[i].filelen = packhandle.readInt();
			// TODO FS.LoadPackFile --> remove sysout 
			logger.log(Level.FINEST, i + ".\t" + newfiles[i]);
		}

		pack = new pack_t();
		pack.filename = new String(packfile);
		pack.handle = packhandle;
		pack.numfiles = numpackfiles;
		pack.files = newfiles;
//	
		Com.Printf ("Added packfile " + packfile +" (" + numpackfiles +" files)\n");
		return pack;
	}
//
//
//	/*
//	================
//	FS_AddGameDirectory
//
//	Sets fs_gamedir, adds the directory to the head of the path,
//	then loads and adds pak1.pak pak2.pak ... 
//	================
//	*/
	static void AddGameDirectory(String dir) throws IOException
	{
		int i;
		searchpath_t	search;
		pack_t pak;
		String pakfile;
//
//		strcpy (fs_gamedir, dir);
		fs_gamedir = new String(dir);
//
//		//
//		// add the directory to the search path
//		//
//		search = Z_Malloc (sizeof(searchpath_t));
		search = new searchpath_t();
//		strcpy (search->filename, dir);
		search.filename = new String(dir);
		search.next = fs_searchpaths;
		fs_searchpaths = search;
//
//		//
//		// add any pak files in the format pak0.pak pak1.pak, ...
//		//
		for (i=0; i<10; i++)
		{
			pakfile = dir + "/pak" + i +".pak";
			if (!(new File(pakfile).canRead())) continue;
			
			pak = LoadPackFile(pakfile);
			if (pak == null) continue;
			
			search = new searchpath_t();
			search.pack = pak;
			search.next = fs_searchpaths;
			fs_searchpaths = search;		
		}
	}
//
//	/*
//	============
//	FS_Gamedir
//
//	Called to find where to write a file (demos, savegames, etc)
//	============
//	*/
	public static String Gamedir()	{
		return (fs_gamedir != null) ? fs_gamedir : Globals.BASEDIRNAME;
	}
//
//	/*
//	=============
//	FS_ExecAutoexec
//	=============
//	*/

	public static void ExecAutoexec() {

		String dir;
		String name;
//
		dir = Cvar.VariableString("gamedir");
		if (dir != null && dir.length() > 0)
			name = fs_basedir.string + '/' + dir + "/autoexec.cfg"; 
		else
			name = fs_basedir.string + '/' + Globals.BASEDIRNAME + "/autoexec.cfg"; 

//		if (Sys_FindFirst(name, 0, SFF_SUBDIR | SFF_HIDDEN | SFF_SYSTEM))
		File f = new File(name);
		if (f.exists())
			Cbuf.AddText("exec autoexec.cfg\n");
//		Sys_FindClose();
	}
//
//
//	/*
//	================
//	FS_SetGamedir
//
//	Sets the gamedir and path to a different directory.
//	================
//	*/
	static void SetGamedir (String dir)
	{
		searchpath_t	next;
//
//		if (strstr(dir, "..") || strstr(dir, "/")
//			|| strstr(dir, "\\") || strstr(dir, ":") )
		{
			Com.Printf ("Gamedir should be a single filename, not a path\n");
			return;
		}
//
//		//
//		// free up any current game dir info
//		//
//		while (fs_searchpaths != fs_base_searchpaths)
//		{
//			if (fs_searchpaths->pack)
//			{
//				fclose (fs_searchpaths->pack->handle);
//				Z_Free (fs_searchpaths->pack->files);
//				Z_Free (fs_searchpaths->pack);
//			}
//			next = fs_searchpaths->next;
//			Z_Free (fs_searchpaths);
//			fs_searchpaths = next;
//		}
//
//		//
//		// flush all data, so it will be forced to reload
//		//
//		if (dedicated && !dedicated->value)
//			Cbuf_AddText ("vid_restart\nsnd_restart\n");
//
//		Com_sprintf (fs_gamedir, sizeof(fs_gamedir), "%s/%s", fs_basedir->string, dir);
//
//		if (!strcmp(dir,BASEDIRNAME) || (*dir == 0))
//		{
//			Cvar_FullSet ("gamedir", "", CVAR_SERVERINFO|CVAR_NOSET);
//			Cvar_FullSet ("game", "", CVAR_LATCH|CVAR_SERVERINFO);
//		}
//		else
//		{
//			Cvar_FullSet ("gamedir", dir, CVAR_SERVERINFO|CVAR_NOSET);
//			if (fs_cddir->string[0])
//				FS_AddGameDirectory (va("%s/%s", fs_cddir->string, dir) );
//			FS_AddGameDirectory (va("%s/%s", fs_basedir->string, dir) );
//		}
	}
//
//
//	/*
//	================
//	FS_Link_f
//
//	Creates a filelink_t
//	================
//	*/
	static void Link_f()
	{
//		filelink_t	*l, **prev;
//
//		if (Cmd_Argc() != 3)
//		{
//			Com_Printf ("USAGE: link <from> <to>\n");
//			return;
//		}
//
//		// see if the link already exists
//		prev = &fs_links;
//		for (l=fs_links ; l ; l=l->next)
//		{
//			if (!strcmp (l->from, Cmd_Argv(1)))
//			{
//				Z_Free (l->to);
//				if (!strlen(Cmd_Argv(2)))
//				{	// delete it
//					*prev = l->next;
//					Z_Free (l->from);
//					Z_Free (l);
//					return;
//				}
//				l->to = CopyString (Cmd_Argv(2));
//				return;
//			}
//			prev = &l->next;
//		}
//
//		// create a new link
//		l = Z_Malloc(sizeof(*l));
//		l->next = fs_links;
//		fs_links = l;
//		l->from = CopyString(Cmd_Argv(1));
//		l->fromlength = strlen(l->from);
//		l->to = CopyString(Cmd_Argv(2));
	}
//
//	/*
//	** FS_ListFiles
//	*/
	public static String[] ListFiles( String findname, int numfiles, int musthave, int canthave )
	{
//		char *s;
//		int nfiles = 0;
		String[] list = null;
//
//		s = Sys_FindFirst( findname, musthave, canthave );
//		while ( s )
//		{
//			if ( s[strlen(s)-1] != '.' )
//				nfiles++;
//			s = Sys_FindNext( musthave, canthave );
//		}
//		Sys_FindClose ();
//
//		if ( !nfiles )
//			return NULL;
//
//		nfiles++; // add space for a guard
//		*numfiles = nfiles;
//
//		list = malloc( sizeof( char * ) * nfiles );
//		memset( list, 0, sizeof( char * ) * nfiles );
//
//		s = Sys_FindFirst( findname, musthave, canthave );
//		nfiles = 0;
//		while ( s )
//		{
//			if ( s[strlen(s)-1] != '.' )
//			{
//				list[nfiles] = strdup( s );
//	#ifdef _WIN32
//				strlwr( list[nfiles] );
//	#endif
//				nfiles++;
//			}
//			s = Sys_FindNext( musthave, canthave );
//		}
//		Sys_FindClose ();
//
		return list;
	}
//
//	/*
//	** FS_Dir_f
//	*/
	static void Dir_f()
	{
//		char	*path = NULL;
//		char	findname[1024];
//		char	wildcard[1024] = "*.*";
//		char	**dirnames;
//		int		ndirs;
//
//		if ( Cmd_Argc() != 1 )
//		{
//			strcpy( wildcard, Cmd_Argv( 1 ) );
//		}
//
//		while ( ( path = FS_NextPath( path ) ) != NULL )
//		{
//			char *tmp = findname;
//
//			Com_sprintf( findname, sizeof(findname), "%s/%s", path, wildcard );
//
//			while ( *tmp != 0 )
//			{
//				if ( *tmp == '\\' ) 
//					*tmp = '/';
//				tmp++;
//			}
//			Com_Printf( "Directory of %s\n", findname );
//			Com_Printf( "----\n" );
//
//			if ( ( dirnames = FS_ListFiles( findname, &ndirs, 0, 0 ) ) != 0 )
//			{
//				int i;
//
//				for ( i = 0; i < ndirs-1; i++ )
//				{
//					if ( strrchr( dirnames[i], '/' ) )
//						Com_Printf( "%s\n", strrchr( dirnames[i], '/' ) + 1 );
//					else
//						Com_Printf( "%s\n", dirnames[i] );
//
//					free( dirnames[i] );
//				}
//				free( dirnames );
//			}
//			Com_Printf( "\n" );
//		};
	}
//
//	/*
//	============
//	FS_Path_f
//
//	============
//	*/
	static void Path_f() {

		searchpath_t	s;
		filelink_t l;
//
		Com.Printf("Current search path:\n");
		for (s=fs_searchpaths ; s != null ; s=s.next)
		{
			if (s == fs_base_searchpaths)
				Com.Printf("----------\n");
			if (s.pack != null)
				Com.Printf(s.pack.filename + " (" + s.pack.numfiles +" files)\n");
			else
				Com.Printf(s.filename + '\n');
		}

		Com.Printf("\nLinks:\n");
		for (l=fs_links ; l != null ; l=l.next)
			Com.Printf(l.from + " : " + l.to + '\n');
	}
//
//	/*
//	================
//	FS_NextPath
//
//	Allows enumerating all of the directories in the search path
//	================
//	*/
	static String NextPath (String prevpath)
	{
		searchpath_t	s;
		String prev;
//
		if (prevpath == null || prevpath.length() == 0 )
			return fs_gamedir;
//
		prev = fs_gamedir;
		for (s=fs_searchpaths ; s != null ; s=s.next)
		{
			if (s.pack != null)
				continue;
			if (prevpath.equals(prev))
				return s.filename;
			prev = s.filename;
		}
//
		return null;
	}
//
//
//	/*
//	================
//	FS_InitFilesystem
//	================
//	*/
	static void InitFilesystem()
	{
		Cmd.AddCommand ("path", new xcommand_t() {
			public void execute() throws Exception {
				Path_f();
			}
		});
		Cmd.AddCommand ("link", new xcommand_t() {
			public void execute() throws Exception {
				Link_f();
			}
		});
		Cmd.AddCommand ("dir", new xcommand_t() {
			public void execute() throws Exception {
				Dir_f();
			}
		});
//
//		//
//		// basedir <path>
//		// allows the game to run from outside the data tree
//		//
		fs_basedir = Cvar.Get("basedir", ".", Cvar.NOSET);
//
//		//
//		// cddir <path>
//		// Logically concatenates the cddir after the basedir for 
//		// allows the game to run from outside the data tree
//		//
		// TODO zur zeit wird auf baseq2 mit ../../ zugegriffen, sonst ""
		fs_cddir = Cvar.Get("cddir", "../..", Cvar.NOSET);
		if (fs_cddir.string.length() > 0)
			try {
				AddGameDirectory(fs_cddir.string +'/' +Globals.BASEDIRNAME);
			} catch (IOException e) {
				logger.log(Level.SEVERE, e.toString());
			}
//
//		//
//		// start up with baseq2 by default
//		//
		try {
			AddGameDirectory(fs_basedir.string +'/' +Globals.BASEDIRNAME);
		} catch (IOException e1) {
			logger.log(Level.SEVERE, e1.toString());
		}
//
//		// any set gamedirs will be freed up to here
		fs_base_searchpaths = fs_searchpaths;
//
//		// check for game override
		fs_gamedirvar = Cvar.Get("game", "", Cvar.LATCH | Cvar.SERVERINFO);
		if (fs_gamedirvar.string.length() > 0)
			SetGamedir (fs_gamedirvar.string);
	}
}