/*
 * SND_MEM.java
 * Copyright (C) 2004
 * 
 * $Id: SND_MEM.java,v 1.2 2004-02-25 21:30:15 hoz Exp $
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

import jake2.qcommon.Com;
import jake2.qcommon.FS;

/**
 * SND_MEM
 */
public class SND_MEM extends SND_JAVA {

////	   snd_mem.c: sound caching
//
//	#include "client.h"
//	#include "snd_loc.h"
//
//	int			cache_full_cycle;
//
//	byte *S_Alloc (int size);
//
	/*
	================
	ResampleSfx
	================
	*/
	static void ResampleSfx (sfx_t sfx, int inrate, int inwidth, byte[] data, int ofs)
	{
		int		outcount;
		int		srcsample;
		float	stepscale;
		int		i;
		int		sample, samplefrac, fracstep;
		sfxcache_t	sc;
	
		sc = sfx.cache;
		if (sc == null)
			return;

		stepscale = (float)inrate / dma.speed;	// this is usually 0.5, 1, or 2

		outcount = (int)(sc.length / stepscale);
		sc.length = outcount;
		if (sc.loopstart != -1)
			sc.loopstart = (int)(sc.loopstart / stepscale);

		sc.speed = dma.speed;
		if (SND_DMA.s_loadas8bit.value != 0.0f)
			sc.width = 1;
		else
			sc.width = inwidth;
		sc.stereo = 0;

//	   resample / decimate to the current source rate

		if (stepscale == 1 && inwidth == 1 && sc.width == 1)
		{
//	   fast special case
			for (i=0 ; i<outcount ; i++)
				sc.data[i+ofs] = (byte)((data[i+ofs] & 0xFF) - 128);
		}
		else
		{
//	   general case
			samplefrac = 0;
			fracstep = (int)(stepscale*256);
//			for (i=0 ; i<outcount ; i++)
//			{
//				srcsample = samplefrac >> 8;
//				samplefrac += fracstep;
//				if (inwidth == 2)
//					sample = LittleShort ( ((short *)data)[srcsample] );
//				else
//					sample = (int)( (unsigned char)(data[srcsample]) - 128) << 8;
//				if (sc->width == 2)
//					((short *)sc->data)[i] = sample;
//				else
//					((signed char *)sc->data)[i] = sample >> 8;
//			}
		}
	}
//
////	  =============================================================================
//
	/*
	==============
	S_LoadSound
	==============
	*/
	static sfxcache_t LoadSound(sfx_t s) {
		String namebuffer;
		byte[] data;
		wavinfo_t info;
		int len;
		float stepscale;
		sfxcache_t sc = null;
		int size;
		String name;

		if (s.name.charAt(0) == '*')
			return null;

		// see if still in memory
		sc = s.cache;
		if (sc != null)
			return sc;

		// load it in
		if (s.truename != null)
			name = s.truename;
		else
			name = s.name;

		if (name.charAt(0) == '#')
			namebuffer = name.substring(1);
		//strcpy(namebuffer, &name[1]);
		else
			namebuffer = "sound/" + name;
		//Com_sprintf (namebuffer, sizeof(namebuffer), "sound/%s", name);

		data = FS.LoadFile(namebuffer);

		if (data == null) {
			Com.DPrintf("Couldn't load " + namebuffer + "\n");
			return null;
		}
		size = data.length;

		info = GetWavinfo(s.name, data, size);
		if (info.channels != 1) {
			Com.Printf(s.name + " is a stereo sample\n");
			FS.FreeFile(data);
			return null;
		}

		stepscale = ((float)info.rate) / dma.speed;
		len = (int) (info.samples / stepscale);

		len = len * info.width * info.channels;

		//sc = s.cache = Z_Malloc (len + sizeof(sfxcache_t));
		sc = s.cache = new sfxcache_t(len);

		sc.length = info.samples;
		sc.loopstart = info.loopstart;
		sc.speed = info.rate;
		sc.width = info.width;
		sc.stereo = info.channels;

		ResampleSfx(s, sc.speed, sc.width, data, info.dataofs);

		FS.FreeFile(data);

		return sc;
	}
//
//
//
//	/*
//	===============================================================================
//
//	WAV loading
//
//	===============================================================================
//	*/
//
	static byte[] data_b;
	static int data_p;
	static int iff_end;
	static int last_chunk;
	static int iff_data;
	static int iff_chunk_len;


	static short GetLittleShort() {
		short val = 0;
		val = data_b[data_p];
		data_p++;
		val |= (data_b[data_p] << 8);
		data_p++;
		return val;
	}

	static int GetLittleLong() {
		int val = 0;
		val = data_b[data_p];
		data_p++;
		val |= (data_b[data_p] << 8);
		data_p++;
		val |= (data_b[data_p] << 16);
		data_p++;
		val |= (data_b[data_p] << 24);
		data_p++;
		return val;
	}

	static void FindNextChunk(String name) {
		while (true) {
			data_p = last_chunk;

			if (data_p >= iff_end) { // didn't find the chunk
				data_p = 0;
				return;
			}

			data_p += 4;
			iff_chunk_len = GetLittleLong();
			if (iff_chunk_len < 0) {
				data_p = 0;
				return;
			}
			//			if (iff_chunk_len > 1024*1024)
			//				Sys_Error ("FindNextChunk: %i length is past the 1 meg sanity limit", iff_chunk_len);
			data_p -= 8;
			last_chunk = data_p + 8 + ((iff_chunk_len + 1) & ~1);
			String s = new String(data_b, data_p, 4);
			if (s.equals(name))
				return;
		}
	}

	static void FindChunk(String name) {
		last_chunk = iff_data;
		FindNextChunk(name);
	}
//
//
//	void DumpChunks(void)
//	{
//		char	str[5];
//	
//		str[4] = 0;
//		data_p=iff_data;
//		do
//		{
//			memcpy (str, data_p, 4);
//			data_p += 4;
//			iff_chunk_len = GetLittleLong();
//			Com_Printf ("0x%x : %s (%d)\n", (int)(data_p - 4), str, iff_chunk_len);
//			data_p += (iff_chunk_len + 1) & ~1;
//		} while (data_p < iff_end);
//	}
//
	/*
	============
	GetWavinfo
	============
	*/
	static wavinfo_t GetWavinfo(String name, byte[] wav, int wavlength) {
		wavinfo_t info = new wavinfo_t();
		int i;
		int format;
		int samples;

		if (wav == null)
			return info;

		iff_data = 0;
		iff_end = wavlength;
		data_b = wav;

		// find "RIFF" chunk
		FindChunk("RIFF");
		String s = new String(data_b, data_p + 8, 4);
		if (!((data_p != 0) && s.equals("WAVE"))) {
			Com.Printf("Missing RIFF/WAVE chunks\n");
			return info;
		}

		//	   get "fmt " chunk
		iff_data = data_p + 12;
		//	   DumpChunks ();

		FindChunk("fmt ");
		if (data_p == 0) {
			Com.Printf("Missing fmt chunk\n");
			return info;
		}
		data_p += 8;
		format = GetLittleShort();
		if (format != 1) {
			Com.Printf("Microsoft PCM format only\n");
			return info;
		}

		info.channels = GetLittleShort();
		info.rate = GetLittleLong();
		data_p += 4 + 2;
		info.width = GetLittleShort() / 8;

		//	   get cue chunk
		FindChunk("cue ");
		if (data_p != 0) {
			data_p += 32;
			info.loopstart = GetLittleLong();
			//			Com_Printf("loopstart=%d\n", sfx->loopstart);

			// if the next chunk is a LIST chunk, look for a cue length marker
			FindNextChunk("LIST");
			if (data_p != 0) {
				s = new String(data_b, data_p + 28, 4);
				if (s.equals("MARK")) { // this is not a proper parse, but it works with cooledit...
					data_p += 24;
					i = GetLittleLong(); // samples in loop
					info.samples = info.loopstart + i;
					//					Com_Printf("looped length: %i\n", i);
				}
			}
		} else
			info.loopstart = -1;

		//	   find data chunk
		FindChunk("data");
		if (data_p == 0) {
			Com.Printf("Missing data chunk\n");
			return info;
		}

		data_p += 4;
		samples = GetLittleLong() / info.width;

		if (info.samples != 0) {
			if (samples < info.samples)
				Com.Error(ERR_DROP, "Sound " + name + " has a bad loop length");
		} else
			info.samples = samples;

		info.dataofs = data_p;

		return info;
	}

	static class wavinfo_t {
		int rate;
		int width;
		int channels;
		int loopstart;
		int samples;
		int dataofs; // chunk starts this many bytes from file start
	}
}
