/*
 * JOALSoundImpl.java
 * Copyright (C) 2004
 *
 * $Id: JOALSoundImpl.java,v 1.7 2004-06-16 12:21:59 cwei Exp $
 */
package jake2.sound.joal;


import jake2.Defines;
import jake2.Globals;
import jake2.client.CL;
import jake2.game.*;
import jake2.qcommon.*;
import jake2.sound.*;
import jake2.util.Math3D;

import java.io.*;
import java.util.Set;
import java.util.TreeSet;

import javax.sound.sampled.*;

import net.java.games.joal.*;

/**
 * JOALSoundImpl
 */
public final class JOALSoundImpl implements Sound {
	
	static {
		S.register(new JOALSoundImpl());
	};

	static AL al;
	static ALC alc;
	
	cvar_t s_volume;
	
	private static final int MAX_SFX = Defines.MAX_SOUNDS * 2;
	
	private int[] buffers = new int[MAX_SFX];
	private int[] sources = new int[MAX_SFX];


	private PlayList playlist = new PlayList();
	
	private Set activeSources = new TreeSet();


	private JOALSoundImpl() {
	}


	/* (non-Javadoc)
	 * @see jake2.sound.SoundImpl#Init()
	 */
	public boolean Init() {
		
		try {
			//ALut.alutInit();
			al = ALFactory.getAL();
			alc = ALFactory.getALC();
			initOpenAL();
			checkError();
		} catch (OpenALException e) {
			Com.Printf(e.getMessage() + '\n');
			return false;
		}
		
		//al.alGenBuffers(MAX_SFX, buffers);
		checkError();
		al.alGenSources(MAX_SFX, sources);
		checkError();
		al.alDistanceModel(AL.AL_INVERSE_DISTANCE_CLAMPED);
//		al.alDistanceModel(AL.AL_INVERSE_DISTANCE);	

		s_volume = Cvar.Get("s_volume", "0.7", Defines.CVAR_ARCHIVE);

		return true;
	}
	
	
	private void initOpenAL() {
		String deviceName = "DirectSound3D";

		// Get handle to device.
		ALC.Device device = alc.alcOpenDevice(deviceName);

		// Get the device specifier.
		String deviceSpecifier = alc.alcGetString(device, ALC.ALC_DEVICE_SPECIFIER);
		String defaultSpecifier = alc.alcGetString(device, ALC.ALC_DEFAULT_DEVICE_SPECIFIER);

		System.out.println("Using device " + deviceSpecifier + " -- Default is " + defaultSpecifier);
		
		// Create audio context.
		ALC.Context context = alc.alcCreateContext(device, null);

		// Set active context.
		alc.alcMakeContextCurrent(context);

		// Check for an error.
		if (alc.alcGetError(device) != ALC.ALC_NO_ERROR) {
			System.err.println("Error with Device");
		}
	}
	
	void exitOpenAL() {
		ALC.Context curContext;
		ALC.Device curDevice;

		// Get the current context.
		curContext = alc.alcGetCurrentContext();

		// Get the device used by that context.
		curDevice = alc.alcGetContextsDevice(curContext);

		// Reset the current context to NULL.
		alc.alcMakeContextCurrent(null);

		// Release the context and the device.
		alc.alcDestroyContext(curContext);
		alc.alcCloseDevice(curDevice);
	}
	
	
	
	/* (non-Javadoc)
	 * @see jake2.sound.SoundImpl#RegisterSound(jake2.sound.sfx_t)
	 */
	private void initBuffer(sfx_t sfx)
	{
		if (sfx.cache == null ) {
			System.out.println(sfx.name + " " + sfx.cache.length+ " " + sfx.cache.loopstart + " " + sfx.cache.speed + " " + sfx.cache.stereo + " " + sfx.cache.width);
			return;
		}
		

		int tmp = sfx.cache.width; // | sfx.cache.stereo;
		int format = AL.AL_FORMAT_MONO16;
		switch (tmp) {
			case 16: format = AL.AL_FORMAT_MONO16; break;
			case 17: format = AL.AL_FORMAT_STEREO16; break;
			case 8: format = AL.AL_FORMAT_MONO8; break;
			case 9: format = AL.AL_FORMAT_STEREO8; break;
		}
		 

		format = AL.AL_FORMAT_MONO16;
		byte[] data = sfx.cache.data;
		int freq = sfx.cache.speed;
		int size = data.length;
		
		if (buffers[sfx.id] != 0)
			al.alDeleteBuffers(1, new int[] {buffers[sfx.id] });			
		
		int[] bid = new int[1];
		al.alGenBuffers(1, bid);
		buffers[sfx.id] = bid[0];
		al.alBufferData( bid[0], format, data, size, freq);

		int error;
		if ((error = al.alGetError()) != AL.AL_NO_ERROR) {
			String message;
			switch(error) {
				case AL.AL_INVALID_OPERATION: message = "invalid operation"; break;
				case AL.AL_INVALID_VALUE: message = "invalid value"; break;
				case AL.AL_INVALID_ENUM: message = "invalid enum"; break;
				case AL.AL_INVALID_NAME: message = "invalid name"; break;
				default: message = "" + error;
			}
			Com.DPrintf("Error Buffer " + sfx.id + ": " + sfx.name + " (" + size + ") --> " + message + '\n');
		}
	}

	private void checkError() {
		int error;
		if ((error = al.alGetError()) != AL.AL_NO_ERROR) {
			String message;
			switch(error) {
				case AL.AL_INVALID_OPERATION: message = "invalid operation"; break;
				case AL.AL_INVALID_VALUE: message = "invalid value"; break;
				case AL.AL_INVALID_ENUM: message = "invalid enum"; break;
				case AL.AL_INVALID_NAME: message = "invalid name"; break;
				default: message = "" + error;
			}
			Com.DPrintf("AL Error: " + message +'\n');
		}
	}


	/* (non-Javadoc)
	 * @see jake2.sound.SoundImpl#Shutdown()
	 */
	public void Shutdown() {
		StopAllSounds();
		al.alDeleteSources(sources.length, sources);
		al.alDeleteBuffers(buffers.length, buffers);
		exitOpenAL();
		//ALut.alutExit();	
		Cmd.RemoveCommand("play");
		Cmd.RemoveCommand("stopsound");
		Cmd.RemoveCommand("soundlist");
		Cmd.RemoveCommand("soundinfo");

		// free all sounds
		for (int i = 0; i < num_sfx; i++) {
			if (known_sfx[i].name == null)
				continue;
			known_sfx[i].clear();
		}
		num_sfx = 0;
	}
	
	private final static float[] NULLVECTOR = {0, 0, 0};
	private float[] entityOrigin = {0, 0, 0};
	private float[] sourceOrigin = {0, 0, 0};

	/* (non-Javadoc)
	 * @see jake2.sound.SoundImpl#StartSound(float[], int, int, jake2.sound.sfx_t, float, float, float)
	 */
	public void StartSound(float[] origin, int entnum, int entchannel, sfx_t sfx, float fvol, float attenuation, float timeofs) {
		
		sfxcache_t sc;
		
		// from quake2
		
		if (sfx == null)
			return;
			
		if (sfx.name.charAt(0) == '*')
			sfx = RegisterSexedSound(Globals.cl_entities[entnum].current, sfx.name);
		
		if (LoadSound(sfx) == null)
			return; // can't load sound

		// for openAL
		
		//System.out.println(sfx.name + " fvol: " + fvol + " atten: " + attenuation);

		al.alSourcei (sources[sfx.id], AL.AL_BUFFER, buffers[sfx.id]);
		al.alSourcef (sources[sfx.id], AL.AL_GAIN, s_volume.value);
		al.alSourcef (sources[sfx.id], AL.AL_PITCH, 1.0f);
		al.alSourcei (sources[sfx.id], AL.AL_SOURCE_ABSOLUTE,  AL.AL_TRUE);
		al.alSourcefv(sources[sfx.id], AL.AL_VELOCITY, NULLVECTOR);
		al.alSourcei (sources[sfx.id], AL.AL_LOOPING,  AL.AL_FALSE);
		al.alSourcef (sources[sfx.id], AL.AL_MIN_GAIN, 0.003f);
		al.alSourcef (sources[sfx.id], AL.AL_MAX_GAIN, 1.0f);

		al.alSourcef (sources[sfx.id], AL.AL_REFERENCE_DISTANCE, 200.0f - attenuation * 50);
			
		if (entnum == Globals.cl.playernum + 1) {
			playlist.addListener(sources[sfx.id]);
		} else if (origin != null) {
			playlist.addFixed(sources[sfx.id], origin);
		} else {
			playlist.addDynamic(sources[sfx.id], entnum);
		}
	}
	
	private float[] listenerOrigin = {0, 0, 0};
	private float[] listenerOrientation = {0, 0, 0, 0, 0, 0};

	/* (non-Javadoc)
	 * @see jake2.sound.SoundImpl#Update(float[], float[], float[], float[])
	 */
	public void Update(float[] origin, float[] forward, float[] right, float[] up) {
		
		convertVector(origin, listenerOrigin);		
			
		al.alListenerfv(AL.AL_POSITION, listenerOrigin);
		al.alListenerfv(AL.AL_VELOCITY, NULLVECTOR);

		//System.out.println("player " + Lib.vtofs(origin));

		convertOrientation(forward, up, listenerOrientation);		
		al.alListenerfv(AL.AL_ORIENTATION, listenerOrientation);
		
		playlist.play(listenerOrigin);
		
	}

	/* (non-Javadoc)
	 * @see jake2.sound.SoundImpl#StopAllSounds()
	 */
	public void StopAllSounds() {
		for (int i = 0; i < sources.length; i++) {
			al.alSourceStop(sources[i]);
			al.alSourcei(sources[i], AL.AL_BUFFER, 0);
		}
	}
	
	static void convertVector(float[] from, float[] to) {
		to[0] = from[0];
		to[1] = from[2];
		to[2] = -from[1];
	}

	static void convertOrientation(float[] forward, float[] up, float[] orientation) {
		orientation[0] = forward[0];
		orientation[1] = forward[2];
		orientation[2] = -forward[1];
		orientation[3] = up[0];
		orientation[4] = up[2];
		orientation[5] = -up[1];
	}
	
	static class PlayList {
		
		final static int LISTENER = 0;
		final static int FIXED = 1;
		final static int DYNAMIC = 2;
		
		int[] playlist = new int[MAX_SFX];
		int[] typelist = new int[MAX_SFX];
		int[] entitylist = new int[MAX_SFX];
		float[][] originlist = new float[MAX_SFX][];
		
		int size = 0;
		
		void addListener(int sourceId) {
			playlist[size] = sourceId;
			typelist[size] = LISTENER;
			size++;
		}
		
		void addFixed(int sourceId, float[] origin) {
			playlist[size] = sourceId;
			typelist[size] = FIXED;
			originlist[size] = origin;
			size++;
		}

		void addDynamic(int sourceId, int entity) {
			playlist[size] = sourceId;
			typelist[size] = DYNAMIC;
			entitylist[size] = entity;
			size++;
		}
		
		void play(float[] listenerOrigin) {
			
			if (size == 0) return;
			
			int entity;
			float[] sourceOrigin = {0, 0, 0};
			float[] entityOrigin = {0, 0, 0}; 
			
			for (int i = 0; i < size; i++) {
				
				switch (typelist[i]) {
					case LISTENER:
						Math3D.VectorCopy(listenerOrigin, sourceOrigin);
						break;
					case DYNAMIC:
						CL.GetEntitySoundOrigin(entitylist[i], entityOrigin);
						convertVector(entityOrigin, sourceOrigin);
						break;
					case FIXED:
						convertVector(originlist[i], sourceOrigin);
						break;
				}
		
				al.alSourcefv(playlist[i], AL.AL_POSITION, sourceOrigin);
				al.alSourceStop(playlist[i]);
				al.alSourceRewind(playlist[i]);
			}
			
			al.alSourcePlayv(size, playlist);
			// reset the playlist
			size = 0;
		}
		 
	}

	/* (non-Javadoc)
	 * @see jake2.sound.Sound#getName()
	 */
	public String getName() {
		return "joal";
	}


	int s_registration_sequence;
	boolean s_registering;

	/* (non-Javadoc)
	 * @see jake2.sound.Sound#BeginRegistration()
	 */
	public void BeginRegistration() {
		s_registration_sequence++;
		s_registering = true;
	}




	/* (non-Javadoc)
	 * @see jake2.sound.Sound#RegisterSound(java.lang.String)
	 */
	public sfx_t RegisterSound(String name) {
		sfx_t sfx = null;


		sfx = FindName(name, true);
		sfx.registration_sequence = s_registration_sequence;

		if (!s_registering)
			LoadSound(sfx);
			
		return sfx;
	}




	/* (non-Javadoc)
	 * @see jake2.sound.Sound#EndRegistration()
	 */
	public void EndRegistration() {
		int i;
		sfx_t sfx;
		int size;


		RegisterSound("misc/menu1.wav");
		RegisterSound("misc/menu2.wav");
		RegisterSound("misc/menu3.wav");
		

		// free any sounds not from this registration sequence
		for (i = 0; i < num_sfx; i++) {
			sfx = known_sfx[i];
			if (sfx.name == null)
				continue;
			if (sfx.registration_sequence != s_registration_sequence) {
				// don't need this sound
				sfx.clear();
			}
		}

		// load everything in
		for (i = 0; i < num_sfx; i++) {
			sfx = known_sfx[i];
			if (sfx.name == null)
				continue;
			LoadSound(sfx);
		}

		s_registering = false;
	}
	
	sfx_t RegisterSexedSound(entity_state_t ent, String base) {
		sfx_t sfx = null;

		// determine what model the client is using
		String model = "male";
		int n = Globals.CS_PLAYERSKINS + ent.number - 1;
		if (Globals.cl.configstrings[n] != null) {
			int p = Globals.cl.configstrings[n].indexOf('\\');
			if (p >= 0) {
				p++;
				model = Globals.cl.configstrings[n].substring(p);
				//strcpy(model, p);
				p = model.indexOf('/');
				if (p > 0)
					model = model.substring(0, p);
			}
		}
		// if we can't figure it out, they're male
		if (model == null || model.length() == 0)
			model = "male";

		// see if we already know of the model specific sound
		String sexedFilename = "#players/" + model + "/" + base.substring(1);
		//Com_sprintf (sexedFilename, sizeof(sexedFilename), "#players/%s/%s", model, base+1);
		sfx = FindName(sexedFilename, false);

		if (sfx == null) {
			// no, so see if it exists
			RandomAccessFile f = null;
			try {
				f = FS.FOpenFile(sexedFilename.substring(1));
			} catch (IOException e) {}
			if (f != null) {
				// yes, close the file and register it
				try {
					FS.FCloseFile(f);
				} catch (IOException e1) {}
				sfx = RegisterSound(sexedFilename);
			} else {
				// no, revert to the male sound in the pak0.pak
				//Com_sprintf (maleFilename, sizeof(maleFilename), "player/%s/%s", "male", base+1);
				String maleFilename = "player/male/" + base.substring(1);
				sfx = AliasName(sexedFilename, maleFilename);
			}
		}

		//System.out.println(sfx.name);
		return sfx;
	}
	

	static sfx_t[] known_sfx = new sfx_t[MAX_SFX];
	static {
		for (int i = 0; i< known_sfx.length; i++)
			known_sfx[i] = new sfx_t();
	}
	static int num_sfx;

	sfx_t FindName(String name, boolean create) {
		int i;
		sfx_t sfx = null;

		if (name == null)
			Com.Error(Defines.ERR_FATAL, "S_FindName: NULL\n");
		if (name.length() == 0)
			Com.Error(Defines.ERR_FATAL, "S_FindName: empty name\n");

		if (name.length() >= Defines.MAX_QPATH)
			Com.Error(Defines.ERR_FATAL, "Sound name too long: " + name);

		// see if already loaded
		for (i = 0; i < num_sfx; i++)
			if (name.equals(known_sfx[i].name)) {
				return known_sfx[i];
			}

		if (!create)
			return null;

		// find a free sfx
		for (i = 0; i < num_sfx; i++)
			if (known_sfx[i].name == null)
				// registration_sequence < s_registration_sequence)
				break;

		if (i == num_sfx) {
			if (num_sfx == MAX_SFX)
				Com.Error(Defines.ERR_FATAL, "S_FindName: out of sfx_t");
			num_sfx++;
		}

		sfx = known_sfx[i];
		sfx.clear();
		sfx.name = name;
		sfx.registration_sequence = s_registration_sequence;
		// cwei
		sfx.id = i;

		return sfx;
	}

	/*
	==================
	S_AliasName

	==================
	*/
	sfx_t AliasName(String aliasname, String truename)
	{
		sfx_t sfx = null;
		String s;
		int i;

		s = new String(truename);

		// find a free sfx
		for (i=0 ; i < num_sfx ; i++)
			if (known_sfx[i].name == null)
				break;

		if (i == num_sfx)
		{
			if (num_sfx == MAX_SFX)
				Com.Error(Defines.ERR_FATAL, "S_FindName: out of sfx_t");
			num_sfx++;
		}
	
		sfx = known_sfx[i];
		sfx.clear();
		sfx.name = new String(aliasname);
		sfx.registration_sequence = s_registration_sequence;
		sfx.truename = s;
		// cwei
		sfx.id = i;

		return sfx;
	}



	/*
	==============
	S_LoadSound
	==============
	*/
	public sfxcache_t LoadSound(sfx_t s) {
		String namebuffer;
		byte[] data;
		wavinfo_t info = new wavinfo_t();
		int len;
//		float stepscale;
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
		
		InputStream is = new ByteArrayInputStream(data);
		AudioFileFormat f = null;
		try {
			f = AudioSystem.getAudioFileFormat(is);
			is.close();
		} catch (UnsupportedAudioFileException e) {
		} catch (IOException e) {
		}
		
		AudioFormat af = f.getFormat();
		info.channels = af.getChannels();
		info.rate = (int)af.getSampleRate();
		info.samples = f.getFrameLength();
		info.width = af.getSampleSizeInBits();
//
//		info = GetWavinfo(s.name, data, size);
		if (info.channels != 1) {
			Com.Printf(s.name + " is a stereo sample\n");
			return null;
		}

		len = data.length;

		//sc = s.cache = Z_Malloc (len + sizeof(sfxcache_t));
		
		sc = s.cache = new sfxcache_t(len);
		sc.length = info.samples;
		sc.loopstart = info.loopstart;
		sc.speed = info.rate;
		sc.width = info.width;
		sc.stereo = info.channels;
		//System.arraycopy(data, len - sc.data.length, sc.data, 0, sc.data.length);
	
		sc.data = data;
		
		// cache the sample in ALBuffer
		initBuffer(s);

		return sc;
	}


	/* (non-Javadoc)
	 * @see jake2.sound.Sound#StartLocalSound(java.lang.String)
	 */
	public void StartLocalSound(String sound) {
		sfx_t sfx;

		sfx = RegisterSound(sound);
		if (sfx == null) {
			Com.Printf("S_StartLocalSound: can't cache " + sound + "\n");
			return;
		}
		StartSound(null, Globals.cl.playernum + 1, 0, sfx, 1, 1, 0);		
	}




	/* (non-Javadoc)
	 * @see jake2.sound.Sound#RawSamples(int, int, int, int, byte[])
	 */
	public void RawSamples(int samples, int rate, int width, int channels, byte[] data) {
	}

}
