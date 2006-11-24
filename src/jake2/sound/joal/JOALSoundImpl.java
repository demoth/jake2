/*
 * JOALSoundImpl.java
 * Copyright (C) 2004
 *
 * $Id: JOALSoundImpl.java,v 1.18 2006-11-24 00:40:30 cawe Exp $
 */
package jake2.sound.joal;

import jake2.Defines;
import jake2.Globals;
import jake2.game.*;
import jake2.qcommon.*;
import jake2.sound.*;
import jake2.util.Lib;
import jake2.util.Vargs;

import java.io.*;
import java.nio.*;

import net.java.games.joal.*;
import net.java.games.joal.eax.EAX;
import net.java.games.joal.eax.EAXFactory;
import net.java.games.joal.util.ALut;

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
	
	private int[] buffers = new int[MAX_SFX + STREAM_QUEUE];

	private JOALSoundImpl() {
	    // singleton 
	}

	/* (non-Javadoc)
	 * @see jake2.sound.SoundImpl#Init()
	 */
	public boolean Init() {
		        
		try {
            ALut.alutInit();
			al = ALFactory.getAL();
            alc = ALFactory.getALC();
			checkError();
			initOpenALExtensions();		
		} catch (ALException e) {
			Com.Printf(e.getMessage() + '\n');
			return false;
		} catch (Throwable e) {
			Com.Printf(e.toString() + '\n');
			return false;
		}
		// set the master volume
		s_volume = Cvar.Get("s_volume", "0.7", Defines.CVAR_ARCHIVE);

		al.alGenBuffers(buffers.length, buffers, 0);
		int count = Channel.init(al, buffers);
		Com.Printf("... using " + count + " channels\n");
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
		
	private void initOpenALExtensions() {
		if (al.alIsExtensionPresent("EAX2.0")) {
			try {
				eax = EAXFactory.getEAX();
				Com.Printf("... using EAX2.0\n");
			} catch (Throwable e) {
				Com.Printf("... EAX2.0 not found\n");
				eax = null;
			}
		} else {
			Com.Printf("... EAX2.0 not found\n");
			eax = null;
		}
	}
	
	void exitOpenAL() {
		// Get the current context.
		ALCcontext curContext = alc.alcGetCurrentContext();
		// Get the device used by that context.
		ALCdevice curDevice = alc.alcGetContextsDevice(curContext);
		// Reset the current context to NULL.
		alc.alcMakeContextCurrent(null);
		// Release the context and the device.
		alc.alcDestroyContext(curContext);
		alc.alcCloseDevice(curDevice);
	}
	
    // TODO check the sfx direct buffer size
    // 2MB sfx buffer
    private ByteBuffer sfxDataBuffer = Lib.newByteBuffer(2 * 1024 * 1024);
    
    /* (non-Javadoc)
     * @see jake2.sound.SoundImpl#RegisterSound(jake2.sound.sfx_t)
     */
    private void initBuffer(byte[] samples, int bufferId, int freq) {
        ByteBuffer data = sfxDataBuffer.slice();
        data.put(samples).flip();
        al.alBufferData(buffers[bufferId], AL.AL_FORMAT_MONO16,
                data, data.limit(), freq);
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
		Channel.shutdown();
		al.alDeleteBuffers(buffers.length, buffers, 0);
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
	}
	
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

		PlaySound.allocate(origin, entnum, entchannel, buffers[sfx.bufferId], fvol, attenuation, timeofs);
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
		Channel.convertVector(origin, listenerOrigin);		
		al.alListenerfv(AL.AL_POSITION, listenerOrigin, 0);

		Channel.convertOrientation(forward, up, listenerOrientation);		
		al.alListenerfv(AL.AL_ORIENTATION, listenerOrientation, 0);
		
		// set the listener (master) volume
		al.alListenerf(AL.AL_GAIN, s_volume.value);
		
		if (eax != null) {
			// workaround for environment initialisation
			if (currentEnv == -1) {
				eaxEnv.put(0, EAX.EAX_ENVIRONMENT_UNDERWATER);
				eax.EAXSet(EAX_LISTENER, EAX.DSPROPERTY_EAXLISTENER_ENVIRONMENT | EAX.DSPROPERTY_EAXLISTENER_DEFERRED, 0, eaxEnv, 4);
				changeEnv = true;
			}

			if ((GameBase.gi.pointcontents.pointcontents(origin)& Defines.MASK_WATER)!= 0) {
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

	    Channel.addLoopSounds();
	    Channel.addPlaySounds();
		Channel.playAllSounds(listenerOrigin);
	}

	/* (non-Javadoc)
	 * @see jake2.sound.SoundImpl#StopAllSounds()
	 */
	public void StopAllSounds() {
		// mute the listener (master)
		al.alListenerf(AL.AL_GAIN, 0);
	    PlaySound.reset();
	    Channel.reset();
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
		String model = null;
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

		if (sfx != null) return sfx;
		
		//
		// fall back strategies
		//
		// not found , so see if it exists
		if (FS.FileLength(sexedFilename.substring(1)) > 0) {
			// yes, register it
			return RegisterSound(sexedFilename);
		}
	    // try it with the female sound in the pak0.pak
		if (model.equalsIgnoreCase("female")) {
			String femaleFilename = "player/female/" + base.substring(1);
			if (FS.FileLength("sound/" + femaleFilename) > 0)
			    return AliasName(sexedFilename, femaleFilename);
		}
		// no chance, revert to the male sound in the pak0.pak
		String maleFilename = "player/male/" + base.substring(1);
		return AliasName(sexedFilename, maleFilename);
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
	    if (s.isCached) return s.cache;
		sfxcache_t sc = WaveLoader.LoadSound(s);
		if (sc != null) {
			initBuffer(sc.data, s.bufferId, sc.speed);
		    s.isCached = true;
		    // free samples for GC
		    s.cache.data = null;
		}
		return sc;
	}

	/* (non-Javadoc)
	 * @see jake2.sound.Sound#StartLocalSound(java.lang.String)
	 */
	public void StartLocalSound(String sound) {
		sfx_t sfx = RegisterSound(sound);
		if (sfx == null) {
			Com.Printf("S_StartLocalSound: can't cache " + sound + "\n");
			return;
		}
		StartSound(null, Globals.cl.playernum + 1, 0, sfx, 1, 1, 0.0f);		
	}

    private ShortBuffer streamBuffer = sfxDataBuffer.slice().order(ByteOrder.BIG_ENDIAN).asShortBuffer();

    /* (non-Javadoc)
     * @see jake2.sound.Sound#RawSamples(int, int, int, int, byte[])
     */
    public void RawSamples(int samples, int rate, int width, int channels, ByteBuffer data) {
        int format;
        if (channels == 2) {
            format = (width == 2) ? AL.AL_FORMAT_STEREO16
                    : AL.AL_FORMAT_STEREO8;
        } else {
            format = (width == 2) ? AL.AL_FORMAT_MONO16
                    : AL.AL_FORMAT_MONO8;
        }
        
        // convert to signed 16 bit samples
        if (format == AL.AL_FORMAT_MONO8) {
            ShortBuffer sampleData = streamBuffer;
            int value;
            for (int i = 0; i < samples; i++) {
                value = (data.get(i) & 0xFF) - 128;
                sampleData.put(i, (short) value);
            }
            format = AL.AL_FORMAT_MONO16;
            width = 2;
            data = sfxDataBuffer.slice();
        }

        Channel.updateStream(data, samples * channels * width, format, rate);
    }
    
    public void disableStreaming() {
        Channel.disableStreaming();
    }
	
	/*
	===============================================================================

	console functions

	===============================================================================
	*/

	void Play() {
        int i = 1;
        String name;
		while (i < Cmd.Argc()) {
			name = new String(Cmd.Argv(i));
			if (name.indexOf('.') == -1)
				name += ".wav";

			RegisterSound(name);
			StartLocalSound(name);
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
