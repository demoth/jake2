/*
 * FS.java
 * Copyright (C) 2003
 * 
 * $Id: FS.java,v 1.16 2003-12-25 18:19:59 cwei Exp $
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
import jake2.sys.CDAudio;
import jake2.sys.Sys;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.stream.FileImageInputStream;


/**
 * FS
 * TODO complete FS
 */
public final class FS {
	
	private static Logger logger = Logger.getLogger(FS.class.getName());

/*
=============================================================================

	QUAKE FILESYSTEM

=============================================================================
*/

	static class packfile_t {
		static final int SIZE = 64;
		static final int NAME_SIZE = 56;

		String name; // char name[56]
		int filepos, filelen;
		
		public String toString() {
			return name + " [ length: " + filelen + " pos: " + filepos + " ]";
		}
	}

	static class pack_t {
		String filename;
		RandomAccessFile handle;
		int numfiles;
		Hashtable files; // with packfile_t entries
	}

	static String fs_gamedir;
	static cvar_t	fs_basedir;
	static cvar_t	fs_cddir;
	static cvar_t	fs_gamedirvar;

	static class filelink_t {
		String from;
		int fromlength;
		String to;
	}

	static List fs_links = new LinkedList(); // with filelink_t entries

	static class searchpath_t {
		String filename;
		pack_t pack;		// only one of filename / pack will be used
		searchpath_t next;
	}

	static searchpath_t fs_searchpaths;
	static searchpath_t fs_base_searchpaths;	// without gamedirs


/*

	All of Quake's data access is through a hierchal file system, but the contents of the file system can be transparently merged from several sources.

	The "base directory" is the path to the directory holding the quake.exe and all game directories.  The sys_* files pass this to host_init in quakeparms_t->basedir.  This can be overridden with the "-basedir" command line parm to allow code debugging in a different directory.  The base directory is
	only used during filesystem initialization.

	The "game directory" is the first tree on the search path and directory that all generated files (savegames, screenshots, demos, config files) will be saved to.  This can be overridden with the "-game" command line parameter.  The game directory can never be changed while quake is executing.  This is a precacution against having a malicious server instruct clients to write files over areas they shouldn't.

*/


	/*
	================
	FS_filelength
	================
	*/
	static int filelength (File f) {
		return (int)f.length();
	}

	/*
	============
	FS_CreatePath

	Creates any directories needed to store the given filename
	============
	*/
	public static void CreatePath (String path) {
		int index = path.lastIndexOf('/');
		// -1 if not found and 0 means write to root
		if ( index > 0 ) {
			File f = new File(path.substring(0, index));
			if ( !f.mkdirs() ) {
				logger.log(Level.WARNING, "can't create path \"" + path + '"');
			}	
		}
	}


	/*
	==============
	FS_FCloseFile

	For some reason, other dll's can't just cal fclose()
	on files returned by FS_FOpenFile...
	==============
	*/
	static void FCloseFile (InputStream in) throws IOException
	{
		in.close();
	}

	public static int FileLength(String filename) {
		searchpath_t search;
		String netpath;
		pack_t pak;
		int i;
		filelink_t link;

		file_from_pak = 0;

		// check for links first
		for (Iterator it = fs_links.iterator(); it.hasNext();) {
			link = (filelink_t)it.next();
			
			if (filename.regionMatches(0, link.from, 0, link.fromlength)) {
				netpath = link.to + filename.substring(link.fromlength);
				File file = new File(netpath);
				if (file.canRead()) {
					Com.DPrintf("link file: " + netpath + '\n');
					return (int) file.length();
				}
				return -1;
			}
		}

		//	   search through the path, one element at a time

		for (search = fs_searchpaths; search != null; search = search.next) {
			// is the element a pak file?
			if (search.pack != null) {
				// look through all the pak file elements
				pak = search.pack;
				packfile_t entry = (packfile_t) pak.files.get(filename);

				if (entry != null && filename.equalsIgnoreCase(entry.name)) {
					// found it!
					file_from_pak = 1;
					Com.DPrintf("PackFile: " + pak.filename + " : " + filename + '\n');
					// open a new file on the pakfile
					File file = new File(pak.filename);
					if (!file.canRead()) {
						Com.Error(Globals.ERR_FATAL, "Couldn't reopen " + pak.filename);
					}
					return entry.filelen;
				}
			} else {
				// check a file in the directory tree
				netpath = search.filename + '/' + filename;

				File file = new File(netpath);
				if (!file.canRead()) continue;

				Com.DPrintf("FindFile: " + netpath + '\n');

				return (int) file.length();
			}
		}
		Com.DPrintf("FindFile: can't find " + filename + '\n');
		return -1;
	}


	/*
	===========
	FS_FOpenFile

	Finds the file in the search path.
	returns filesize and an open FILE *
	Used for streaming data out of either a pak file or
	a seperate file.
	===========
	*/
	static int file_from_pak = 0;

	static InputStream FOpenFile(String filename) throws IOException
	{
		searchpath_t search;
		String netpath;
		pack_t pak;
		int i;
		filelink_t link;
		File file = null;

		file_from_pak = 0;
		
		// check for links first
		for (Iterator it = fs_links.iterator(); it.hasNext();) {
			link = (filelink_t)it.next();
			
//			if (!strncmp (filename, link->from, link->fromlength))
			if (filename.regionMatches(0, link.from, 0, link.fromlength))
			{
				netpath = link.to + filename.substring(link.fromlength);
				file = new File(netpath);
				if (file.canRead())
				{		
					//Com.DPrintf ("link file: " + netpath +'\n');
					return new FileInputStream(file);
				}
				return null;
			}
		}

		//
		//	   search through the path, one element at a time
		//
		for (search = fs_searchpaths; search != null; search = search.next) {
			// is the element a pak file?
			if (search.pack != null) {
				// look through all the pak file elements
				pak = search.pack;
				packfile_t entry = (packfile_t) pak.files.get(filename);

				if (entry != null && filename.equalsIgnoreCase(entry.name)) {
					// found it!
					file_from_pak = 1;
					//Com.DPrintf ("PackFile: " + pak.filename + " : " + filename + '\n');
					file = new File(pak.filename);
					if (!file.canRead())
						Com.Error(Globals.ERR_FATAL, "Couldn't reopen " + pak.filename);
					if (pak.handle == null || !pak.handle.getFD().valid()) {
						// hold the pakfile handle open
						pak.handle = new RandomAccessFile(pak.filename, "r");
					}
					// open a new file on the pakfile
					
					byte[] buf = new byte[entry.filelen];
					pak.handle.seek(entry.filepos);
					pak.handle.readFully(buf);

					return new ByteArrayInputStream(buf);
				}
			} else {
				// check a file in the directory tree
				netpath = search.filename + '/' + filename;

				file = new File(netpath);
				if (!file.canRead())
					continue;

				//Com.DPrintf("FindFile: " + netpath +'\n');

				return new FileInputStream(file);
			}
		}
		//Com.DPrintf ("FindFile: can't find " + filename + '\n');
		return null;
	}


	/*
	=================
	FS_ReadFile

	Properly handles partial reads
	=================
	*/
	static final int MAX_READ	= 0x10000; // read in blocks of 64k

	static void Read(byte[] buffer, int len, InputStream f) {

		int block, remaining;
		int offset = 0;
		int read = 0;
		boolean tries = true;

		// read in chunks for progress bar
		remaining = len;

		while (remaining != 0) {
			block = Math.min(remaining, MAX_READ);
			try {
				read = f.read(buffer, offset, block);
			} catch (IOException e) {
				Com.Error(Globals.ERR_FATAL, e.toString());
			}

			if (read == 0) {
				// we might have been trying to read from a CD
				if (tries) {
					tries = false;
					CDAudio.Stop();
				} else {
					Com.Error(Globals.ERR_FATAL, "FS_Read: 0 bytes read");
				}
			} else if (read == -1) {
				Com.Error(Globals.ERR_FATAL, "FS_Read: -1 bytes read");
			}
			//
			//			do some progress bar thing here...
			//
			remaining -= read;
			offset += read;
		}
	}

	/*
	============
	FS_LoadFile

	Filename are reletive to the quake search path
	a null buffer will just return the file length without loading
	============
	*/
	public static byte[] LoadFile(String path)
	{
		InputStream h;

		byte[] buf = null;
		int len = 0;
		
		// look for it in the filesystem or pack files
		len = FileLength(path);

		if (len < 1) return null;

		try {
			h = FOpenFile(path);
			Read(buf = new byte[len], len, h);
			h.close();
		} catch (IOException e) {
			Com.Error(Globals.ERR_FATAL, e.toString());
		}
		return buf;
	}


	/*
	=============
	FS_FreeFile
	=============
	*/
	public static void FreeFile(byte[] buffer)
	{
		Z.Free(buffer);
	}


	static final int IDPAKHEADER  =  (('K'<<24)+('C'<<16)+('A'<<8)+'P');
	
	static class dpackheader_t {
		int ident;      // == IDPAKHEADER
		int dirofs;
		int dirlen;
	} 

	static final int MAX_FILES_IN_PACK = 4096;

	/*
	=================
	FS_LoadPackFile

	Takes an explicit (not game tree related) path to a pak file.

	Loads the header and directory, adding the files at the beginning
	of the list so they override previous pack files.
	=================
	*/
	static pack_t LoadPackFile(String packfile) {
		
		dpackheader_t header;
		Hashtable newfiles;
		int numpackfiles = 0;
		pack_t pack = null;
		RandomAccessFile file;
		FileImageInputStream packhandle;
		//		unsigned		checksum;
		//
		try {
			packhandle =
				new FileImageInputStream(
					file = new RandomAccessFile(packfile, "r"));
			packhandle.setByteOrder(ByteOrder.LITTLE_ENDIAN);

			if (packhandle.length() < 1) return null;
			//
			header = new dpackheader_t();
			header.ident = packhandle.readInt();
			header.dirofs = packhandle.readInt();
			header.dirlen = packhandle.readInt();
			
			if (header.ident != IDPAKHEADER)
				Com.Error(Globals.ERR_FATAL, packfile + " is not a packfile");
			
			numpackfiles = header.dirlen / packfile_t.SIZE;
			
			if (numpackfiles > MAX_FILES_IN_PACK)
				Com.Error(Globals.ERR_FATAL, packfile + " has " + numpackfiles + " files");
			
			newfiles = new Hashtable(numpackfiles);

			packhandle.seek(header.dirofs);

			// buffer for C-Strings char[56] 
			byte[] text = new byte[packfile_t.NAME_SIZE];
			// parse the directory
			packfile_t entry = null;
			
			for (int i = 0; i < numpackfiles; i++) {
				packhandle.readFully(text);

				entry = new packfile_t();
				entry.name = new String(text).trim();
				entry.filepos = packhandle.readInt();
				entry.filelen = packhandle.readInt();
				
				newfiles.put(entry.name, entry);
				
				logger.log(Level.FINEST, i + ".\t" + entry);
			}

		} catch (IOException e) {
			logger.log(Level.WARNING, e.toString());
			return null;
		}

		pack = new pack_t();
		pack.filename = new String(packfile);
		pack.handle = file;
		pack.numfiles = numpackfiles;
		pack.files = newfiles;

		Com.Printf("Added packfile " + packfile + " (" + numpackfiles + " files)\n");

		return pack;
	}


	/*
	================
	FS_AddGameDirectory

	Sets fs_gamedir, adds the directory to the head of the path,
	then loads and adds pak1.pak pak2.pak ... 
	================
	*/
	static void AddGameDirectory(String dir)
	{
		int i;
		searchpath_t	search;
		pack_t pak;
		String pakfile;

		fs_gamedir = new String(dir);

		//
		// add the directory to the search path
		//
		search = new searchpath_t();
		search.filename = new String(dir);
		search.next = fs_searchpaths;
		fs_searchpaths = search;

		//
		// add any pak files in the format pak0.pak pak1.pak, ...
		//
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

	/*
	============
	FS_Gamedir

	Called to find where to write a file (demos, savegames, etc)
	============
	*/
	public static String Gamedir()	{
		return (fs_gamedir != null) ? fs_gamedir : Globals.BASEDIRNAME;
	}

	/*
	=============
	FS_ExecAutoexec
	=============
	*/

	public static void ExecAutoexec() {

		String dir;
		String name;

		dir = Cvar.VariableString("gamedir");

		if (dir != null && dir.length() > 0) {
			name = fs_basedir.string + '/' + dir + "/autoexec.cfg"; 
		} else {
			name = fs_basedir.string + '/' + Globals.BASEDIRNAME + "/autoexec.cfg";
		}
			
		int canthave = Globals.SFF_SUBDIR | Globals.SFF_HIDDEN | Globals.SFF_SYSTEM;
								
		if (Sys.FindAll(name, 0, canthave) != null) {
			Cbuf.AddText("exec autoexec.cfg\n");
		}
	}

	/*
	================
	FS_SetGamedir

	Sets the gamedir and path to a different directory.
	================
	*/
	static void SetGamedir (String dir)
	{
		searchpath_t	next;

//		if (strstr(dir, "..") || strstr(dir, "/")
//			|| strstr(dir, "\\") || strstr(dir, ":") )

		if (dir.indexOf("..") != -1
			|| dir.indexOf("/") != -1
			|| dir.indexOf("\\") != -1
			|| dir.indexOf(":") != -1) {
			Com.Printf("Gamedir should be a single filename, not a path\n");
			return;
		}

		//
		// free up any current game dir info
		//
		while (fs_searchpaths != fs_base_searchpaths)
		{
			if (fs_searchpaths.pack != null)
			{
				try {
					fs_searchpaths.pack.handle.close();
				} catch (IOException e) {
					logger.log(Level.WARNING, e.toString());
				}
				// clear the hashtable
				fs_searchpaths.pack.files.clear();
				fs_searchpaths.pack.files = null;
				fs_searchpaths.pack = null;
			}
			next = fs_searchpaths.next;
			fs_searchpaths = null;
			fs_searchpaths = next;
		}

		//
		// flush all data, so it will be forced to reload
		//
		if ((Globals.dedicated != null) && (Globals.dedicated.value == 0.0f))
			Cbuf.AddText ("vid_restart\nsnd_restart\n");

		fs_gamedir = fs_basedir.string + '/' + dir;

		if (!dir.equals(Globals.BASEDIRNAME) || (dir.length() == 0))
		{
			Cvar.FullSet ("gamedir", "", Cvar.SERVERINFO | Cvar.NOSET);
			Cvar.FullSet ("game", "", Cvar.LATCH | Cvar.SERVERINFO);
		}
		else
		{
			Cvar.FullSet ("gamedir", dir, Cvar.SERVERINFO | Cvar.NOSET);
			if (fs_cddir.string != null && fs_cddir.string.length() > 0)
				AddGameDirectory (fs_cddir.string + '/' + dir);
				
			AddGameDirectory (fs_basedir.string + '/' + dir);
		}
	}


	/*
	================
	FS_Link_f

	Creates a filelink_t
	================
	*/
	static void Link_f() {
		filelink_t entry = null;

		if (Cmd.Argc() != 3) {
			Com.Printf("USAGE: link <from> <to>\n");
			return;
		}

		// see if the link already exists
		for (Iterator it = fs_links.iterator(); it.hasNext();) {
			entry = (filelink_t) it.next();

			if (entry.from.equals(Cmd.Argv(1))) {
				if (Cmd.Argv(2).length() < 1) {
					// delete it
					it.remove();
					return;
				}
				entry.to = new String(Cmd.Argv(2));
				return;
			}
		}

		// create a new link if the <to> is not empty
		if (Cmd.Argv(2).length() > 0) {
			entry = new filelink_t();
			entry.from = new String(Cmd.Argv(1));
			entry.fromlength = entry.from.length();
			entry.to = new String(Cmd.Argv(2));
			fs_links.add(entry);
		}
	}


	/*
	** FS_ListFiles
	*/
	public static String[] ListFiles( String findname, int musthave, int canthave )
	{
		String[] list = null;
		
		File[] files = Sys.FindAll(findname, musthave, canthave);
		
		if (files != null) {
			list = new String[files.length];
			for (int i = 0; i < files.length; i++) {
				list[i] = files[i].getPath();
			}
		}
		
		return list;
	}


	/*
	** FS_Dir_f
	*/
	static void Dir_f()
	{
		String path = null;
		String findname = null;
		String wildcard = "*.*";
		String[] dirnames;

		if ( Cmd.Argc() != 1 )
		{
			wildcard = Cmd.Argv(1);
		}

		while ( ( path = NextPath( path ) ) != null )
		{
			String tmp = findname;

			findname = path + '/' + wildcard;

			if (tmp != null) tmp.replaceAll("\\\\", "/");

			Com.Printf( "Directory of " + findname +'\n' );
			Com.Printf( "----\n" );
			
			dirnames = ListFiles(findname, 0, 0);
			
			if (dirnames != null) {
				int index = 0;
				for (int i = 0; i < dirnames.length; i++) {
					if ((index = dirnames[i].lastIndexOf('/')) > 0) {
						Com.Printf(dirnames[i].substring(index + 1, dirnames[i].length()) + '\n');
					} else {
						Com.Printf(dirnames[i] + '\n');
					}
				}		
			}

			Com.Printf( "\n" );
		}
	}

	/*
	============
	FS_Path_f

	============
	*/
	static void Path_f() {

		searchpath_t	s;
		filelink_t link;

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
		for (Iterator it = fs_links.iterator(); it.hasNext();) {
			link = (filelink_t)it.next();
			Com.Printf(link.from + " : " + link.to + '\n');
		}
	}


	/*
	================
	FS_NextPath

	Allows enumerating all of the directories in the search path
	================
	*/
	static String NextPath (String prevpath)
	{
		searchpath_t	s;
		String prev;

		if (prevpath == null || prevpath.length() == 0 )	return fs_gamedir;

		prev = fs_gamedir;
		for (s=fs_searchpaths ; s != null ; s=s.next)
		{
			if (s.pack != null) continue;
			
			if (prevpath == prev) return s.filename;
				
			prev = s.filename;
		}

		return null;
	}


	/*
	================
	FS_InitFilesystem
	================
	*/
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
		// basedir <path>
		// allows the game to run from outside the data tree
		//
		fs_basedir = Cvar.Get("basedir", ".", Cvar.NOSET);

		//
		// cddir <path>
		// Logically concatenates the cddir after the basedir for 
		// allows the game to run from outside the data tree
		//
		
		// TODO zur zeit wird auf baseq2 mit ../../ zugegriffen, sonst ""
		fs_cddir = Cvar.Get("cddir", "../..", Cvar.NOSET);
		if (fs_cddir.string.length() > 0)
			AddGameDirectory(fs_cddir.string +'/' +Globals.BASEDIRNAME);

		//
		// start up with baseq2 by default
		//
		AddGameDirectory(fs_basedir.string +'/' +Globals.BASEDIRNAME);

		// any set gamedirs will be freed up to here
		fs_base_searchpaths = fs_searchpaths;

		// check for game override
		fs_gamedirvar = Cvar.Get("game", "", Cvar.LATCH | Cvar.SERVERINFO);
		
		if (fs_gamedirvar.string.length() > 0) SetGamedir (fs_gamedirvar.string);
	}
}