/*
 * JOALSoundImpl.java
 * Copyright (C) 2004
 *
 * $Id: JOALSoundImpl.java,v 1.4 2004-09-19 09:12:20 hzi Exp $
 */
package jake2.sound.joal;

import jake2.Defines;
import jake2.Globals;
import jake2.client.CL;
import jake2.game.*;
import jake2.qcommon.*;
import jake2.sound.*;
import jake2.util.*;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.IntBuffer;
import java.util.*;

import net.java.games.joal.*;
import net.java.games.joal.eax.EAX;
import net.java.games.joal.eax.EAXFactory;

/**
 * JOALSoundImpl
 */
public final class JOALSoundImpl implements Sound {
	
	static {
		S.register(new JOALSoundImpl());
	};

	static AL al;
	static ALC alc;
	static EAX eax;
	
	cvar_t s_volume;
	
	private static final int MAX_SFX = Defines.MAX_SOUNDS * 2;
	private static final int MAX_CHANNELS = 32;
	
	private int[] buffers = new int[MAX_SFX];
	private int[] sources = new int[MAX_CHANNELS];
	private Channel[] channels = null;
	private int num_channels = 0; 

	// singleton 
	private JOALSoundImpl() {
	}

	/* (non-Javadoc)
	 * @see jake2.sound.SoundImpl#Init()
	 */
	public boolean Init() {
		
		// preload OpenAL native library
		String os = System.getProperty("os.name");
		if (os.startsWith("Linux")) {
			System.loadLibrary("openal");	
		} else if (os.startsWith("Windows")) {
			System.loadLibrary("OpenAL32");
		}
		
		try {
			initOpenAL();
			al = ALFactory.getAL();
			checkError();
			initOpenALExtensions();		
		} catch (OpenALException e) {
			Com.Printf(e.getMessage() + '\n');
			return false;
		} catch (Exception e) {
			Com.DPrintf(e.getMessage() + '\n');
			return false;
		}
		al.alGenBuffers(MAX_SFX, buffers);
		s_volume = Cvar.Get("s_volume", "0.7", Defines.CVAR_ARCHIVE);
		initChannels();
		al.alDistanceModel(AL.AL_INVERSE_DISTANCE_CLAMPED);
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

		num_sfx = 0;


		Com.Printf("sound sampling rate: 44100Hz\n");

		StopAllSounds();
		Com.Printf("------------------------------------\n");
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
		String defaultSpecifier = alc.alcGetString(device, ALC.ALC_DEFAULT_DEVICE_SPECIFIER);

		Com.Printf(os + " using " + ((deviceName == null) ? defaultSpecifier : deviceName) + '\n');

		ALC.Context context = alc.alcCreateContext(device, new int[] {0});
		alc.alcMakeContextCurrent(context);
		// Check for an error.
		if (alc.alcGetError(device) != ALC.ALC_NO_ERROR) {
			Com.DPrintf("Error with SoundDevice");
		}
	}
	
	private void initOpenALExtensions() throws OpenALException {
		if (al.alIsExtensionPresent("EAX2.0")) {
			Com.Printf("... using EAX2.0\n");
			eax = EAXFactory.getEAX();
		} else {
			Com.Printf("... EAX2.0 not found\n");
			eax = null;
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
		int[] tmp = {0};
		int error;
		for (int i = 0; i < MAX_CHANNELS; i++) {
			
			al.alGenSources(1, tmp);
			sourceId = tmp[0];
			
			//if ((error = al.alGetError()) != AL.AL_NO_ERROR) break;
			if (sourceId <= 0) break;
			
			sources[i] = sourceId;

			channels[i] = new Channel(sourceId);
			num_channels++;
			
			// set default values for AL sources
			al.alSourcef (sourceId, AL.AL_GAIN, s_volume.value);
			al.alSourcef (sourceId, AL.AL_PITCH, 1.0f);
			al.alSourcei (sourceId, AL.AL_SOURCE_ABSOLUTE,  AL.AL_TRUE);
			al.alSourcefv(sourceId, AL.AL_VELOCITY, NULLVECTOR);
			al.alSourcei (sourceId, AL.AL_LOOPING, AL.AL_FALSE);
			al.alSourcef (sourceId, AL.AL_REFERENCE_DISTANCE, 300.0f);
			al.alSourcef (sourceId, AL.AL_MIN_GAIN, 0.0005f);
			al.alSourcef (sourceId, AL.AL_MAX_GAIN, 1.0f);
		}
		Com.Printf("... using " + num_channels + " channels\n");
	}
	
	
	/* (non-Javadoc)
	 * @see jake2.sound.SoundImpl#RegisterSound(jake2.sound.sfx_t)
	 */
	private void initBuffer(sfx_t sfx) {
		if (sfx.cache == null ) {
			//System.out.println(sfx.name + " " + sfx.cache.length+ " " + sfx.cache.loopstart + " " + sfx.cache.speed + " " + sfx.cache.stereo + " " + sfx.cache.width);
			return;
		}
		
		int format = AL.AL_FORMAT_MONO16;
		byte[] data = sfx.cache.data;
		int freq = sfx.cache.speed;
		int size = data.length;
		
		al.alBufferData( buffers[sfx.bufferId], format, data, size, freq);
	}

	private void checkError() {
		Com.DPrintf("AL Error: " + alErrorString() +'\n');
	}
	
	private String alErrorString(){
		int error;
		String message = "";
		if ((error = al.alGetError()) != AL.AL_NO_ERROR) {
			switch(error) {
				case AL.AL_INVALID_OPERATION: message = "invalid operation"; break;
				case AL.AL_INVALID_VALUE: message = "invalid value"; break;
				case AL.AL_INVALID_ENUM: message = "invalid enum"; break;
				case AL.AL_INVALID_NAME: message = "invalid name"; break;
				default: message = "" + error;
			}
		}
		return message; 
	}

	/* (non-Javadoc)
	 * @see jake2.sound.SoundImpl#Shutdown()
	 */
	public void Shutdown() {
		StopAllSounds();
		al.alDeleteSources(sources.length, sources);
		al.alDeleteBuffers(buffers.length, buffers);
		exitOpenAL();

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
		num_channels = 0;
	}
	
	private final static float[] NULLVECTOR = {0, 0, 0};
	private float[] entityOrigin = {0, 0, 0};
	private float[] sourceOrigin = {0, 0, 0};

	/* (non-Javadoc)
	 * @see jake2.sound.SoundImpl#StartSound(float[], int, int, jake2.sound.sfx_t, float, float, float)
	 */
	public void StartSound(float[] origin, int entnum, int entchannel, sfx_t sfx, float fvol, float attenuation, float timeofs) {

		if (sfx == null)
			return;
			
		if (sfx.name.charAt(0) == '*')
			sfx = RegisterSexedSound(Globals.cl_entities[entnum].current, sfx.name);
		
		if (LoadSound(sfx) == null)
			return; // can't load sound

		if (attenuation != Defines.ATTN_STATIC)
			attenuation *= 0.5f;

		Channel ch = pickChannel(entnum, entchannel, buffers[sfx.bufferId], attenuation);
		
		if (ch == null) return;

		if (entnum == Globals.cl.playernum + 1) {
			ch.addListener();
		} else if (origin != null) {
			ch.addFixed(origin);
		} else {
			ch.addDynamic(entnum);
		}
	}
	
	Channel pickChannel(int entnum, int entchannel, int bufferId, float rolloff) {

		Channel ch = null;
		int state;
		int i;

		for (i = 0; i < num_channels; i++) {
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
		
		if (i == num_channels)
			return null;

		ch.entnum = entnum;
		ch.entchannel = entchannel;
		if (ch.bufferId != bufferId) {
			ch.bufferId = bufferId;
			ch.bufferChanged = true;			
		}
		ch.rolloff = rolloff * 2;
		ch.active = true;
		ch.modified = true;
		
		return ch;
	}
	
	private float[] listenerOrigin = {0, 0, 0};
	private float[] listenerOrientation = {0, 0, 0, 0, 0, 0};
	private IntBuffer eaxEnv = Lib.newIntBuffer(1);
	private int currentEnv = -1;
	private boolean changeEnv = true;
	
	// TODO workaround for JOAL-bug
	// should be EAX.LISTENER
	private final static int EAX_LISTENER = 0;
	// should be EAX.SOURCE
	private final static int EAX_SOURCE = 1;

	/* (non-Javadoc)
	 * @see jake2.sound.SoundImpl#Update(float[], float[], float[], float[])
	 */
	public void Update(float[] origin, float[] forward, float[] right, float[] up) {
		
		convertVector(origin, listenerOrigin);		
		al.alListenerfv(AL.AL_POSITION, listenerOrigin);

		convertOrientation(forward, up, listenerOrientation);		
		al.alListenerfv(AL.AL_ORIENTATION, listenerOrientation);
		
		if (eax != null) {
			// workaround for environment initialisation
			if (currentEnv == -1) {
				eaxEnv.put(0, EAX.EAX_ENVIRONMENT_UNDERWATER);
				eax.EAXSet(EAX_LISTENER, EAX.DSPROPERTY_EAXLISTENER_ENVIRONMENT | EAX.DSPROPERTY_EAXLISTENER_DEFERRED, 0, eaxEnv, 4);
				changeEnv = true;
			}

			if ((Game.gi.pointcontents.pointcontents(origin)& Defines.MASK_WATER)!= 0) {
				changeEnv = currentEnv != EAX.EAX_ENVIRONMENT_UNDERWATER;
				currentEnv = EAX.EAX_ENVIRONMENT_UNDERWATER;
			} else {
				changeEnv = currentEnv != EAX.EAX_ENVIRONMENT_GENERIC;
				currentEnv = EAX.EAX_ENVIRONMENT_GENERIC;
			}
			if (changeEnv) {
				eaxEnv.put(0, currentEnv);
				eax.EAXSet(EAX_LISTENER, EAX.DSPROPERTY_EAXLISTENER_ENVIRONMENT | EAX.DSPROPERTY_EAXLISTENER_DEFERRED, 0, eaxEnv, 4);
			}
		}
		
		AddLoopSounds(origin);
		playChannels(listenerOrigin);
	}
	
	Map looptable = new Hashtable(MAX_CHANNELS);
	
	/*
	==================
	S_AddLoopSounds

	Entities with a ->sound field will generated looped sounds
	that are automatically started, stopped, and merged together
	as the entities are sent to the client
	==================
	*/
	void AddLoopSounds(float[] listener)	{
		
		if (Globals.cl_paused.value != 0.0f) {
			removeUnusedLoopSounds();
			return;
		}

		if (Globals.cls.state != Globals.ca_active) {
			removeUnusedLoopSounds();
			return;
		}

		if (!Globals.cl.sound_prepped) {
			removeUnusedLoopSounds();
			return;
		}
		
		Channel ch;
		sfx_t	sfx;
		sfxcache_t sc;
		int num;
		entity_state_t ent;
		Object key;
		int sound = 0;

		for (int i=0 ; i<Globals.cl.frame.num_entities ; i++) {
			num = (Globals.cl.frame.parse_entities + i)&(Defines.MAX_PARSE_ENTITIES-1);
			ent = Globals.cl_parse_entities[num];
			sound = ent.sound;

			if (sound == 0) continue;

			key = new Integer(ent.number);
			ch = (Channel)looptable.get(key);

			if (ch != null) {
				// keep on looping
				ch.autosound = true;
				ch.origin = ent.origin;
				continue;
			}

			sfx = Globals.cl.sound_precache[sound];
			if (sfx == null)
				continue;		// bad sound effect

			sc = sfx.cache;
			if (sc == null)
				continue;

			// allocate a channel
			ch = pickChannel(0, 0, buffers[sfx.bufferId], 6);
			if (ch == null)
				break;
				
			ch.addFixed(ent.origin);
			ch.autosound = true;
			
			looptable.put(key, ch);
			al.alSourcei(ch.sourceId, AL.AL_LOOPING, AL.AL_TRUE);
		}

		removeUnusedLoopSounds();

	}
	
	void removeUnusedLoopSounds() {
		Channel ch;
		// stop unused loopsounds
		for (Iterator iter = looptable.values().iterator(); iter.hasNext();) {
			ch = (Channel)iter.next();
			if (!ch.autosound) {
				al.alSourceStop(ch.sourceId);
				al.alSourcei(ch.sourceId, AL.AL_LOOPING, AL.AL_FALSE);
				iter.remove();
				ch.clear();
			}
		}
	}

	void playChannels(float[] listenerOrigin) {
		
		float[] sourceOrigin = {0, 0, 0};
		float[] entityOrigin = {0, 0, 0}; 
		Channel ch;
		int sourceId;
		int state;

		for (int i = 0; i < num_channels; i++) {
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
					if (ch.bufferChanged)
						al.alSourcei (sourceId, AL.AL_BUFFER, ch.bufferId);

					al.alSourcef (sourceId, AL.AL_GAIN, s_volume.value);
					al.alSourcef (sourceId, AL.AL_ROLLOFF_FACTOR, ch.rolloff);
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
				ch.autosound = false;
			}
		}
	}

	/* (non-Javadoc)
	 * @see jake2.sound.SoundImpl#StopAllSounds()
	 */
	public void StopAllSounds() {
		for (int i = 0; i < num_channels; i++) {
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
		sfx_t sfx = FindName(name, true);
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
		// TODO configstrings for player male and female are wrong 
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
				String maleFilename = "player/male/" + base.substring(1);
				sfx = AliasName(sexedFilename, maleFilename);
			}
		}
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
		sfx.bufferId = i;

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
		// set the AL bufferId
		sfx.bufferId = i;

		return sfx;
	}

	/*
	==============
	S_LoadSound
	==============
	*/
	public sfxcache_t LoadSound(sfx_t s) {
		sfxcache_t sc = WaveLoader.LoadSound(s);
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
		// TODO implement RawSamples
	}
	
	/*
	===============================================================================

	console functions

	===============================================================================
	*/

	void Play() {
		int i;
		String name;
		sfx_t sfx;

		i = 1;
		while (i < Cmd.Argc()) {
			name = new String(Cmd.Argv(i));
			if (name.indexOf('.') == -1)
				name += ".wav";

			sfx = RegisterSound(name);
			StartSound(null, Globals.cl.playernum + 1, 0, sfx, 1.0f, 1.0f, 0.0f);
			i++;
		}
	}

	void SoundList() {
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
	
	void SoundInfo_f() {

		Com.Printf("%5d stereo\n", new Vargs(1).add(1));
		Com.Printf("%5d samples\n", new Vargs(1).add(22050));
		Com.Printf("%5d samplebits\n", new Vargs(1).add(16));
		Com.Printf("%5d speed\n", new Vargs(1).add(44100));
	}

}
