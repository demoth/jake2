/*
 * SND_JAVA.java
 * Copyright (C) 2004
 * 
 * $Id: SND_JAVA.java,v 1.3 2004-02-09 23:18:33 hoz Exp $
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

import jake2.Globals;
import jake2.game.cvar_t;
import jake2.qcommon.Cvar;
import jake2.qcommon.FS;

import java.io.*;
import java.io.FileInputStream;
import java.io.IOException;

import javax.sound.sampled.*;

/**
 * SND_JAVA
 */
public class SND_JAVA extends Globals {

	static boolean snd_inited= false;

	static cvar_t sndbits;
	static cvar_t sndspeed;
	static cvar_t sndchannels;

//	static int tryrates[] = { 11025, 22051, 44100, 8000 };
	static class dma_t {
		int channels;
		int samples; // mono samples in buffer
		int submission_chunk; // don't mix less than this #
		int samplepos; // in mono samples
		int samplebits;
		int speed;
		byte[] buffer;
	}
  	static SND_DMA.dma_t dma = new dma_t();
  	
	static SourceDataLine line;
	static AudioFormat format;


	static boolean SNDDMA_Init() {

		if (snd_inited)
			return true;

		if (sndbits == null) {
			sndbits = Cvar.Get("sndbits", "16", CVAR_ARCHIVE);
			sndspeed = Cvar.Get("sndspeed", "0", CVAR_ARCHIVE);
			sndchannels = Cvar.Get("sndchannels", "2", CVAR_ARCHIVE);
		}
		
		byte[] sound = FS.LoadFile("sound/misc/menu1.wav");
		AudioInputStream stream;
		try {
			stream = AudioSystem.getAudioInputStream(new ByteArrayInputStream(sound));
		} catch (UnsupportedAudioFileException e) {
			return false;
		} catch (IOException e) {
			return false;
		}
		format = stream.getFormat();

		DataLine.Info dinfo = new DataLine.Info(SourceDataLine.class, format);
		//format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, format.getSampleRate(), format.getSampleSizeInBits(), 2, 2*format.getFrameSize(), format.getFrameRate(), format.isBigEndian());

		try {
			line = (SourceDataLine)AudioSystem.getLine(dinfo);
		} catch (LineUnavailableException e4) {
			return false; 
		}
		dma.buffer = new byte[65536];
//		try {
//			stream.read(dma.buffer);
//		} catch (IOException e3) {
//			// TODO Auto-generated catch block
//			e3.printStackTrace();
//		}
		
		dma.channels = format.getChannels();
		dma.samplebits = format.getSampleSizeInBits();
		dma.samples = dma.buffer.length / format.getFrameSize();
		dma.speed = (int)format.getSampleRate();
		dma.samplepos = 0;
		dma.submission_chunk = 1;
		
		try {
			line.open(format, 4096);
		} catch (LineUnavailableException e5) {
			return false;
		}

		line.start();
		runLine();
		
		snd_inited = true;
		return true;

	}

	static int SNDDMA_GetDMAPos() {		
		dma.samplepos = line.getFramePosition() % dma.samples;
		return dma.samplepos;
	}

	static void SNDDMA_Shutdown() {
		line.stop();
		line.flush();
		line.close();
		line=null;
		snd_inited = false;		
	}

	/*
	==============
	SNDDMA_Submit

	Send sound to device if buffer isn't really the dma buffer
	===============
	*/
	public static void SNDDMA_Submit() {
		runLine();
	}

	void SNDDMA_BeginPainting() {}

	private static int pos = 0;
	static void runLine() {
		
		int p = line.getFramePosition() * format.getFrameSize() % dma.buffer.length;
		System.out.println("run " + p + " " + pos);	
		if (p == 0) {
			writeLine();	
		}
		else if (pos - p < 2048 ) writeLine();		
	}
	
	static void writeLine() {
		line.write(dma.buffer, pos, 4096);
		pos+=4096;
		if (pos>=dma.buffer.length) pos = 0;		
	}

}
