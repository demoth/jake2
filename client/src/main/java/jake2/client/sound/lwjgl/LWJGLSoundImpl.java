/*
 * LWJGLSoundImpl.java
 * Copyright (C) 2004
 *
 * $Id: LWJGLSoundImpl.java,v 1.10 2007-05-11 20:33:53 cawe Exp $
 */
package jake2.client.sound.lwjgl;

import jake2.client.ClientGlobals;
import jake2.client.sound.*;
import jake2.qcommon.Com;
import jake2.qcommon.Defines;
import jake2.qcommon.Globals;
import jake2.qcommon.entity_state_t;
import jake2.qcommon.exec.Cmd;
import jake2.qcommon.exec.Cvar;
import jake2.qcommon.exec.cvar_t;
import jake2.qcommon.filesystem.FS;
import jake2.qcommon.util.Lib;
import org.lwjgl.LWJGLException;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.OpenALException;

import java.nio.*;
import java.util.List;


/**
 * LWJGLSoundImpl
 * 
 * @author dsanders/cwei
 */
public final class LWJGLSoundImpl implements Sound {

    static {
	S.register(new LWJGLSoundImpl());
    };

    private cvar_t s_volume;

    // the last 4 buffers are used for cinematics streaming
    private IntBuffer buffers = Lib.newIntBuffer(MAX_SFX + STREAM_QUEUE);

    // singleton 
    private LWJGLSoundImpl() {
    }

    /* (non-Javadoc)
     * @see jake2.client.sound.SoundImpl#Init()
     */
    public boolean Init() {

	try {
	    initOpenAL();
	    checkError();	
	} catch (OpenALException e) {
	    Com.Printf(e.getMessage() + '\n');
	    return false;
	} catch (Exception e) {
	    Com.DPrintf(e.getMessage() + '\n');
	    return false;
	}

	// set the listerner (master) volume
	s_volume = Cvar.getInstance().Get("s_volume", "0.7", Defines.CVAR_ARCHIVE);
	AL10.alGenBuffers(buffers);
	int count = Channel.init(buffers);
	Com.Printf("... using " + count + " channels\n");
	AL10.alDistanceModel(AL10.AL_INVERSE_DISTANCE_CLAMPED);
	Cmd.AddCommand("play", this::Play);
	Cmd.AddCommand("stopsound", (List<String> args) -> StopAllSounds());
	Cmd.AddCommand("soundlist", (List<String> args) -> SoundList());
	Cmd.AddCommand("soundinfo", (List<String> args) -> SoundInfo_f());

	num_sfx = 0;

	Com.Printf("sound sampling rate: 44100Hz\n");

	StopAllSounds();
	Com.Printf("------------------------------------\n");
	return true;
    }


    private void initOpenAL() throws OpenALException 
    {
	try { AL.create(); } catch (LWJGLException e) { throw new OpenALException(e); }
	String deviceName = null;

	String os = System.getProperty("os.name");
	if (os.startsWith("Windows")) {
	    deviceName = "DirectSound3D";
	}

	String defaultSpecifier = ALC10.alcGetString(AL.getDevice(), ALC10.ALC_DEFAULT_DEVICE_SPECIFIER);

	Com.Printf(os + " using " + ((deviceName == null) ? defaultSpecifier : deviceName) + '\n');

	// Check for an error.
	if (ALC10.alcGetError(AL.getDevice()) != ALC10.ALC_NO_ERROR) 
	{
	    Com.DPrintf("Error with SoundDevice");
	}
    }

    void exitOpenAL() 
    {
	AL.destroy();
    }

    // TODO check the sfx direct buffer size
    // 2MB sfx buffer
    private ByteBuffer sfxDataBuffer = Lib.newByteBuffer(2 * 1024 * 1024);

    /* (non-Javadoc)
     * @see jake2.client.sound.SoundImpl#RegisterSound(jake2.client.sound.sfx_t)
     */
    private void initBuffer(byte[] samples, int bufferId, int freq) {
	ByteBuffer data = sfxDataBuffer.slice();
	data.put(samples).flip();
	AL10.alBufferData(buffers.get(bufferId), AL10.AL_FORMAT_MONO16,
		data, freq);
    }

    private void checkError() {
	Com.DPrintf("AL Error: " + alErrorString() +'\n');
    }

    private String alErrorString(){
	int error;
	String message = "";
	if ((error = AL10.alGetError()) != AL10.AL_NO_ERROR) {
	    switch(error) {
	    case AL10.AL_INVALID_OPERATION: message = "invalid operation"; break;
	    case AL10.AL_INVALID_VALUE: message = "invalid value"; break;
	    case AL10.AL_INVALID_ENUM: message = "invalid enum"; break;
	    case AL10.AL_INVALID_NAME: message = "invalid name"; break;
	    default: message = "" + error;
	    }
	}
	return message; 
    }

    /* (non-Javadoc)
     * @see jake2.client.sound.SoundImpl#Shutdown()
     */
    public void Shutdown() {
	StopAllSounds();
	Channel.shutdown();
	AL10.alDeleteBuffers(buffers);
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
     * @see jake2.client.sound.SoundImpl#StartSound(float[], int, int, jake2.client.sound.sfx_t, float, float, float)
     */
    public void StartSound(float[] origin, int entnum, int entchannel, sfx_t sfx, float fvol, float attenuation, float timeofs) {

	if (sfx == null)
	    return;

	if (sfx.name.charAt(0) == '*')
	    sfx = RegisterSexedSound(ClientGlobals.cl_entities[entnum].current, sfx.name);

	if (LoadSound(sfx) == null)
	    return; // can't load sound

	if (attenuation != Defines.ATTN_STATIC)
	    attenuation *= 0.5f;

	PlaySound.allocate(origin, entnum, entchannel, buffers.get(sfx.bufferId), fvol, attenuation, timeofs);
    }

    private FloatBuffer listenerOrigin = Lib.newFloatBuffer(3);
    private FloatBuffer listenerOrientation = Lib.newFloatBuffer(6);

    /* (non-Javadoc)
     * @see jake2.client.sound.SoundImpl#Update(float[], float[], float[], float[])
     */
    public void Update(float[] origin, float[] forward, float[] right, float[] up) {

	Channel.convertVector(origin, listenerOrigin);		
	AL10.alListener(AL10.AL_POSITION, listenerOrigin);

	Channel.convertOrientation(forward, up, listenerOrientation);		
	AL10.alListener(AL10.AL_ORIENTATION, listenerOrientation);

	// set the master volume
	AL10.alListenerf(AL10.AL_GAIN, s_volume.value);

	Channel.addLoopSounds();
	Channel.addPlaySounds();
	Channel.playAllSounds(listenerOrigin);
    }

    /* (non-Javadoc)
     * @see jake2.client.sound.SoundImpl#StopAllSounds()
     */
    public void StopAllSounds() {
	// mute the listener (master)
	AL10.alListenerf(AL10.AL_GAIN, 0);
	PlaySound.reset();
	Channel.reset();
    }

    /* (non-Javadoc)
     * @see jake2.client.sound.Sound#getName()
     */
    public String getName() {
	return "lwjgl";
    }

    int s_registration_sequence;
    boolean s_registering;

    /* (non-Javadoc)
     * @see jake2.client.sound.Sound#BeginRegistration()
     */
    public void BeginRegistration() {
	s_registration_sequence++;
	s_registering = true;
    }

    /* (non-Javadoc)
     * @see jake2.client.sound.Sound#RegisterSound(java.lang.String)
     */
    public sfx_t RegisterSound(String name) {
	sfx_t sfx = FindName(name, true);
	sfx.registration_sequence = s_registration_sequence;

	if (!s_registering)
	    LoadSound(sfx);

	return sfx;
    }

    /* (non-Javadoc)
     * @see jake2.client.sound.Sound#EndRegistration()
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
	if (ClientGlobals.cl.configstrings[n] != null) {
	    int p = ClientGlobals.cl.configstrings[n].indexOf('\\');
	    if (p >= 0) {
		p++;
		model = ClientGlobals.cl.configstrings[n].substring(p);
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
	if (FS.FileExists(sexedFilename.substring(1))) {
	    // yes, register it
	    return RegisterSound(sexedFilename);
	}
	// try it with the female sound in the pak0.pak
	if (model.equalsIgnoreCase("female")) {
	    String femaleFilename = "player/female/" + base.substring(1);
	    if (FS.FileExists("sound/" + femaleFilename))
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
     * @see jake2.client.sound.Sound#StartLocalSound(java.lang.String)
     */
    public void StartLocalSound(String sound) {
	sfx_t sfx;

	sfx = RegisterSound(sound);
	if (sfx == null) {
	    Com.Printf("S_StartLocalSound: can't cache " + sound + "\n");
	    return;
	}
	StartSound(null, ClientGlobals.cl.playernum + 1, 0, sfx, 1, 1, 0);
    }

    private ShortBuffer streamBuffer = sfxDataBuffer.slice().order(ByteOrder.BIG_ENDIAN).asShortBuffer();

    /* (non-Javadoc)
     * @see jake2.client.sound.Sound#RawSamples(int, int, int, int, byte[])
     */
    public void RawSamples(int samples, int rate, int width, int channels, ByteBuffer data) {
	int format;
	if (channels == 2) {
	    format = (width == 2) ? AL10.AL_FORMAT_STEREO16
		    : AL10.AL_FORMAT_STEREO8;
	} else {
	    format = (width == 2) ? AL10.AL_FORMAT_MONO16
		    : AL10.AL_FORMAT_MONO8;
	}

	// convert to signed 16 bit samples
	if (format == AL10.AL_FORMAT_MONO8) {
	    ShortBuffer sampleData = streamBuffer;
	    int value;
	    for (int i = 0; i < samples; i++) {
		value = (data.get(i) & 0xFF) - 128;
		sampleData.put(i, (short) value);
	    }
	    format = AL10.AL_FORMAT_MONO16;
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

	private void Play(List<String> args) {
		if (args.size() < 2) {
			Com.Printf("usage: play <file_name>");
			return;
		}

		int i = 1;
		while (i < args.size()) {
			String name = args.get(i);
			if (!name.contains("."))
				name += ".wav";

			sfx_t sfx = RegisterSound(name);
			StartSound(null, ClientGlobals.cl.playernum + 1, 0, sfx, 1.0f, 1.0f, 0.0f);
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
		Com.Printf("(%2db) %6i : %s\n", sc.width * 8, size, sfx.name);
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

	Com.Printf("%5d stereo\n", 1);
	Com.Printf("%5d samples\n", 22050);
	Com.Printf("%5d samplebits\n", 16);
	Com.Printf("%5d speed\n", 44100);
    }

}
