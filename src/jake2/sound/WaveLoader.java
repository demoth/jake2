/*
 * SND_MEM.java
 * Copyright (C) 2004
 * 
 * $Id: WaveLoader.java,v 1.3 2004-11-03 20:15:02 hzi Exp $
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
import jake2.qcommon.Com;
import jake2.qcommon.FS;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteOrder;

import javax.sound.sampled.*;

/**
 * SND_MEM
 */
public class WaveLoader {

	private static AudioFormat sampleFormat;
	static {
		if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
			sampleFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 22050, 16, 1, 2, 22050, false);
		} else {
			sampleFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 22050, 16, 1, 2, 22050, true);
		}
		
	}

	/*
	==============
	S_LoadSound
	==============
	*/
	public static sfxcache_t LoadSound(sfx_t s) {
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

		else
			namebuffer = "sound/" + name;

		data = FS.LoadFile(namebuffer);

		if (data == null) {
			Com.DPrintf("Couldn't load " + namebuffer + "\n");
			return null;
		}
		size = data.length;

		info = GetWavinfo(s.name, data, size);
		
		AudioInputStream in = null;
		AudioInputStream out = null;
		try {
			in = AudioSystem.getAudioInputStream(new ByteArrayInputStream(data));
			if (in.getFormat().getSampleSizeInBits() == 8) {
				in = convertTo16bit(in);
			}
			out = AudioSystem.getAudioInputStream(sampleFormat, in);
			int l = (int)out.getFrameLength();
			sc = s.cache = new sfxcache_t(l*2);
			sc.length = l;
			int c = out.read(sc.data, 0, l * 2);
			out.close();
			in.close();
		} catch (Exception e) {
			Com.Printf("Couldn't load " + namebuffer + "\n");
			return null;
		}
		
		sc.loopstart = info.loopstart * ((int)sampleFormat.getSampleRate() / info.rate);
		sc.speed = (int)sampleFormat.getSampleRate();
		sc.width = sampleFormat.getSampleSizeInBits() / 8;
		sc.stereo = 0;

		data = null;

		return sc;
	}

	static AudioInputStream convertTo16bit(AudioInputStream in) throws IOException {
		AudioFormat format = in.getFormat();
		int length = (int)in.getFrameLength();
		byte[] samples = new byte[2*length];
		
		for (int i = 0; i < length; i++) {
			in.read(samples, 2*i+1, 1);
			samples[2*i+1] -= 128;
		}
		in.close();			

		AudioFormat newformat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, format.getSampleRate(), 16, format.getChannels(), 2, format.getFrameRate(), false);
		return new AudioInputStream(new ByteArrayInputStream(samples), newformat, length);
	}
	
	/*
	===============================================================================

	WAV loading

	===============================================================================
	*/

	static byte[] data_b;
	static int data_p;
	static int iff_end;
	static int last_chunk;
	static int iff_data;
	static int iff_chunk_len;


	static short GetLittleShort() {
		int val = 0;
		val = data_b[data_p] & 0xFF;
		data_p++;
		val |= ((data_b[data_p] & 0xFF) << 8);
		data_p++;
		return (short)val;
	}

	static int GetLittleLong() {
		int val = 0;
		val = data_b[data_p] & 0xFF;
		data_p++;
		val |= ((data_b[data_p] & 0xFF) << 8);
		data_p++;
		val |= ((data_b[data_p] & 0xFF) << 16);
		data_p++;
		val |= ((data_b[data_p] & 0xFF) << 24);
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
			if (iff_chunk_len > 1024*1024) {
				Com.Println(" Warning: FindNextChunk: length is past the 1 meg sanity limit");
			}
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
		if (!s.equals("WAVE")) {
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
				Com.Error(Defines.ERR_DROP, "Sound " + name + " has a bad loop length");
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
