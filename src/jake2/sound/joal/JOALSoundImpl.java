/*
 * JOALSoundImpl.java
 * Copyright (C) 2004
 *
 * $Id: JOALSoundImpl.java,v 1.8 2004-06-19 17:05:11 cwei Exp $
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
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;

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
	private static final int MAX_CHANNELS = 16;
	
	private int[] buffers = new int[MAX_SFX];
	private int[] sources = new int[MAX_CHANNELS];
	private Channel[] channels = null;

	private JOALSoundImpl() {
	}


	/* (non-Javadoc)
	 * @see jake2.sound.SoundImpl#Init()
	 */
	public boolean Init() {
		
		try {
			initOpenAL();
			al = ALFactory.getAL();
			checkError();
		} catch (OpenALException e) {
			Com.Printf(e.getMessage() + '\n');
			return false;
		}
		
		//al.alGenBuffers(MAX_SFX, buffers);
		checkError();
		al.alGenSources(MAX_CHANNELS, sources);
		checkError();
		s_volume = Cvar.Get("s_volume", "0.7", Defines.CVAR_ARCHIVE);
		initChannels();
		al.alDistanceModel(AL.AL_INVERSE_DISTANCE_CLAMPED);
//		al.alDistanceModel(AL.AL_INVERSE_DISTANCE);	
		return true;
	}
	
	
	private void initOpenAL() throws OpenALException {
		ALFactory.initialize();
		alc = ALFactory.getALC();
		String deviceName = null;

		String os = System.getProperty("os.name");
		if (os.startsWith("Windows")) {
			deviceName = "DirectSound3D";
		}
		ALC.Device device = alc.alcOpenDevice(deviceName);
		String deviceSpecifier = alc.alcGetString(device, ALC.ALC_DEVICE_SPECIFIER);
//		String defaultSpecifier = alc.alcGetString(device, ALC.ALC_DEFAULT_DEVICE_SPECIFIER);

		System.out.println(os + " using " + deviceName + " --> " + deviceSpecifier); //((deviceName == null) ? defaultSpecifier : deviceName));


		ALC.Context context = alc.alcCreateContext(device, new int[] {0});
		alc.alcMakeContextCurrent(context);
		// Check for an error.
		if (alc.alcGetError(device) != ALC.ALC_NO_ERROR) {
			System.err.println("Error with Device");
		}
	}
	
	void exitOpenAL() {
		// Get the current context.
		ALC.Context curContext = alc.alcGetCurrentContext();
		// Get the device used by that context.
		ALC.Device curDevice = alc.alcGetContextsDevice(curContext);
		// Reset the current context to NULL.
		alc.alcMakeContextCurrent(null);
		// Release the context and the device.
		alc.alcDestroyContext(curContext);
		alc.alcCloseDevice(curDevice);
	}
	
	private void initChannels() {
		
		// create channels
		channels = new Channel[MAX_CHANNELS];
		
		int sourceId;
		for (int i = 0; i < MAX_CHANNELS; i++) {
			sourceId = sources[i];
			channels[i] = new Channel(sourceId);
			
			// set default values for AL sources
			al.alSourcef (sourceId, AL.AL_GAIN, s_volume.value);
			al.alSourcef (sourceId, AL.AL_PITCH, 1.0f);
			al.alSourcei (sourceId, AL.AL_SOURCE_ABSOLUTE,  AL.AL_TRUE);
			al.alSourcefv(sourceId, AL.AL_VELOCITY, NULLVECTOR);
			// channel 0 is the looping channel
			al.alSourcei (sourceId, AL.AL_LOOPING, AL.AL_FALSE);
			al.alSourcef (sourceId, AL.AL_MIN_GAIN, 0.003f);
			al.alSourcef (sourceId, AL.AL_MAX_GAIN, 1.0f);
		}
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
		
		Channel ch = pickupChannel(entnum, entchannel, buffers[sfx.id], attenuation);
		
		if (ch == null) return;

		if (entnum == Globals.cl.playernum + 1) {
			ch.addListener();
		} else if (origin != null) {
			ch.addFixed(origin);
		} else {
			ch.addDynamic(entnum);
		}
	}
	
	Channel pickupChannel(int entnum, int entchannel, int bufferId, float attenuation) {
		
		Channel ch = null;
		int state;
		int i;

		for (i = 0; i < MAX_CHANNELS; i++) {
			ch = channels[i];

			if (entchannel != 0 && ch.entnum == entnum && ch.entchannel == entchannel) {
				// always override sound from same entity
				break;
			}

			// don't let monster sounds override player sounds
			if ((ch.entnum == Globals.cl.playernum+1) && (entnum != Globals.cl.playernum+1) && ch.bufferId != -1)
				continue;

			// looking for a free AL source
			if (!ch.active) {
				break;
			}
		}
		
		if (i == MAX_CHANNELS)
			return null;

		ch.entnum = entnum;
		ch.entchannel = entchannel;
		ch.bufferId = bufferId;
		ch.attenuation = attenuation;
		ch.active = true;
		ch.modified = true;
		
		return ch;
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
		
		playChannels(listenerOrigin);
		
	}
	
	void playChannels(float[] listenerOrigin) {
		
		float[] sourceOrigin = {0, 0, 0};
		float[] entityOrigin = {0, 0, 0}; 
		Channel ch;
		int sourceId;
		int state;

		for (int i = 0; i < MAX_CHANNELS; i++) {
			ch = channels[i];
			if (ch.active) {
				sourceId = ch.sourceId;
				
				switch (ch.type) {
					case Channel.LISTENER:
						Math3D.VectorCopy(listenerOrigin, sourceOrigin);
						break;
					case Channel.DYNAMIC:
						CL.GetEntitySoundOrigin(ch.entity, entityOrigin);
						convertVector(entityOrigin, sourceOrigin);
						break;
					case Channel.FIXED:
						convertVector(ch.origin, sourceOrigin);
						break;
				}
			
				if (ch.modified) {
					al.alSourcei (sourceId, AL.AL_BUFFER, ch.bufferId);
					al.alSourcef (sourceId, AL.AL_GAIN, s_volume.value);
					al.alSourcef (sourceId, AL.AL_REFERENCE_DISTANCE, 200.0f - ch.attenuation * 50);
					al.alSourcefv(sourceId, AL.AL_POSITION, sourceOrigin);
					al.alSourcePlay(sourceId);
					ch.modified = false;
				} else {
					state = al.alGetSourcei(ch.sourceId, AL.AL_SOURCE_STATE);
					if (state == AL.AL_PLAYING) {
						al.alSourcefv(sourceId, AL.AL_POSITION, sourceOrigin);
					} else {
						ch.clear();
					}
				}
			}
		}
	}
	

	/* (non-Javadoc)
	 * @see jake2.sound.SoundImpl#StopAllSounds()
	 */
	public void StopAllSounds() {
		for (int i = 0; i < MAX_CHANNELS; i++) {
			al.alSourceStop(sources[i]);
			al.alSourcei(sources[i], AL.AL_BUFFER, 0);
			channels[i].clear();
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
		
		
//		sfxcache_t sc = null;
		
		
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

//		sc = SND_MEM.LoadSound(s);
//		s.cache = sc;
//		
//		initBuffer(s);

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
