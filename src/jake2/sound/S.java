/*
 * S.java
 * Copyright (C) 2003
 * 
 * $Id: S.java,v 1.3 2004-04-15 14:37:37 cwei Exp $
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
package jake2.sound;

import jake2.Defines;
import jake2.Globals;
import jake2.game.Cmd;
import jake2.game.cvar_t;
import jake2.qcommon.*;
import jake2.qcommon.Com;
import jake2.qcommon.Cvar;
import jake2.sound.jsound.JSoundImpl;
import jake2.util.Vargs;

/**
 * S
 */
public class S {
	
	// TODO ist nur voruebergehend: JSoundImpl()
	static SoundImpl sound = new JSoundImpl();
	static boolean sound_started = false;
	
	static int s_registration_sequence;
	static boolean s_registering;
	
	static cvar_t s_testsound;
	static cvar_t s_loadas8bit;
	static cvar_t s_khz;
	static cvar_t s_show;
	static cvar_t s_mixahead;
	static cvar_t s_primary;
	static cvar_t s_volume;

	static final int MAX_SFX = (Defines.MAX_SOUNDS*2);
	static sfx_t[] known_sfx = new sfx_t[MAX_SFX];
	static {
		for (int i = 0; i< known_sfx.length; i++)
			known_sfx[i] = new sfx_t();
	}
	static int num_sfx;
	
	static soundinfo_t sinfo = new soundinfo_t();
	static int soundtime;		// sample PAIRS
	static int paintedtime; 	// sample PAIRS
	
	public static void Init() {
		Com.Printf("\n------- sound initialization -------\n");

		cvar_t cv = Cvar.Get("s_initsound", "0", 0);
		if (cv.value == 0.0f)
			Com.Printf("not initializing.\n");
		else {
			s_volume = Cvar.Get("s_volume", "0.7", Defines.CVAR_ARCHIVE);
			s_khz = Cvar.Get("s_khz", "11", Defines.CVAR_ARCHIVE);
			s_loadas8bit = Cvar.Get("s_loadas8bit", "1", Defines.CVAR_ARCHIVE);
			s_mixahead = Cvar.Get("s_mixahead", "0.2", Defines.CVAR_ARCHIVE);
			s_show = Cvar.Get("s_show", "0", 0);
			s_testsound = Cvar.Get("s_testsound", "0", 0);
			s_primary = Cvar.Get("s_primary", "0", Defines.CVAR_ARCHIVE); // win32 specific

			Cmd.AddCommand("play", new xcommand_t() {
				public void execute() {
					Play();
				}
			});
			Cmd.AddCommand("stopsound", new xcommand_t() {
				public void execute() {
					StopAllSounds();
				}
			});
			Cmd.AddCommand("soundlist", new xcommand_t() {
				public void execute() {
					SoundList();
				}
			});
			Cmd.AddCommand("soundinfo", new xcommand_t() {
				public void execute() {
					SoundInfo_f();
				}
			});

			if (!sound.Init())
				return;

			sound_started = true;
			num_sfx = 0;

			soundtime = 0;
			paintedtime = 0;

			Com.Printf("sound sampling rate: " + sinfo.speed + "\n");

			StopAllSounds();
		}

		Com.Printf("------------------------------------\n");		
	}
	
	public static void Shutdown() {

		if (!sound_started)
			return;

		sound.Shutdown();

		sound_started = false;

		Cmd.RemoveCommand("play");
		Cmd.RemoveCommand("stopsound");
		Cmd.RemoveCommand("soundlist");
		Cmd.RemoveCommand("soundinfo");

		// free all sounds
		sfx_t sfx;
		for (int i = 0; i < num_sfx; i++) {
			sfx = known_sfx[i];
			if (sfx.name == null)
				continue;

			//memset (sfx, 0, sizeof(*sfx));
			sfx.clear();
		}

		num_sfx = 0;
	}
	
	/*
	=====================
	S_BeginRegistration

	=====================
	*/
	public static void BeginRegistration() {
		s_registration_sequence++;
		s_registering = true;
	}

	/*
	=====================
	S_EndRegistration

	=====================
	*/
	public static void EndRegistration() {
//		int i;
//		sfx_t sfx;
//		int size;
//
//		// free any sounds not from this registration sequence
//		for (i = 0; i < num_sfx; i++) {
//			sfx = known_sfx[i];
//			if (sfx.name == null)
//				continue;
//			if (sfx.registration_sequence != s_registration_sequence) { // don't need this sound
//				//memset (sfx, 0, sizeof(*sfx));
//				sfx.clear();
//			} else { 
//				// make sure it is paged in
//				//				if (sfx->cache)
//				//				{
//				//					size = sfx->cache->length*sfx->cache->width;
//				//					Com_PageInMemory ((byte *)sfx->cache, size);
//				//				}
//			}
//
//		}
//
//		// load everything in
//		for (i = 0; i < num_sfx; i++) {
//			sfx = known_sfx[i];
//			if (sfx.name == null)
//				continue;
//			LoadSound(sfx);
//		}
//
//		s_registering = false;
	}
			
	public static sfx_t RegisterSound(String name) {
		return null;
	}

	/*
	==================
	S_StartLocalSound
	==================
	*/
	public static void StartLocalSound(String sound) {
		sfx_t sfx;

		if (!sound_started)
			return;

		sfx = RegisterSound(sound);
		if (sfx == null) {
			Com.Printf("S_StartLocalSound: can't cache " + sound + "\n");
			return;
		}
		StartSound(null, Globals.cl.playernum + 1, 0, sfx, 1, 1, 0);
	}

	static void Play() {
		String name;
		sfx_t sfx;

		int i = 1;
		while (i < Cmd.Argc()) {
			name = new String(Cmd.Argv(i));
			if (name.indexOf('.') == -1)
				name += ".wav";

			sfx = RegisterSound(name);
			StartSound(null, Globals.cl.playernum + 1, 0, sfx, 1.0f, 1.0f, 0.0f);
			i++;
		}
	}

	static void SoundList() {
		int i;
		sfx_t sfx;
		sfxcache_t sc;
		int size, total;

		total = 0;
		for (i = 0; i < num_sfx; i++) {
			sfx = known_sfx[i];
			if (sfx.registration_sequence == 0)
				continue;
			sc = sfx.cache;
			if (sc != null) {
				size = sc.length * sc.width * (sc.stereo + 1);
				total += size;
				if (sc.loopstart >= 0)
					Com.Printf("L");
				else
					Com.Printf(" ");
				Com.Printf("(%2db) %6i : %s\n", new Vargs(3).add(sc.width * 8).add(size).add(sfx.name));
			} else {
				if (sfx.name.charAt(0) == '*')
					Com.Printf("  placeholder : " + sfx.name + "\n");
				else
					Com.Printf("  not loaded  : " + sfx.name + "\n");
			}
		}
		Com.Printf("Total resident: " + total + "\n");
	}

	static void SoundInfo_f() {
		if (!sound_started) {
			Com.Printf("sound system not started\n");
			return;
		}

		Com.Printf("%5d stereo\n", new Vargs(1).add(sinfo.channels - 1));
		Com.Printf("%5d samples\n", new Vargs(1).add(sinfo.samples));
		Com.Printf("%5d samplepos\n", new Vargs(1).add(sinfo.samplepos));
		Com.Printf("%5d samplebits\n", new Vargs(1).add(sinfo.samplebits));
		Com.Printf("%5d submission_chunk\n", new Vargs(1).add(sinfo.submission_chunk));
		Com.Printf("%5d speed\n", new Vargs(1).add(sinfo.speed));
	}
				
	public static void StartSound(float[] origin, int entnum, int entchannel, sfx_t sfx, float fvol, float attenuation, float timeofs) {
		sound.StartSound(origin, entnum, entchannel, sfx, fvol, attenuation, timeofs);
	}

	public static void Update(float[] origin, float[] forward, float[] right, float[] up) {
		sound.Update(origin, forward, right, up);
	}

	public static void StopAllSounds()
	{
		sound.StopAllSounds();
	}
}
