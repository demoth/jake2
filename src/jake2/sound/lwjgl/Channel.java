/*
 * Created on Jun 19, 2004
 * 
 * Copyright (C) 2003
 *
 * $Id: Channel.java,v 1.2 2004-12-23 00:52:12 cawe Exp $
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
package jake2.sound.lwjgl;

import jake2.Defines;
import jake2.Globals;
import jake2.client.CL_ents;
import jake2.game.entity_state_t;
import jake2.sound.sfx_t;
import jake2.sound.sfxcache_t;
import jake2.util.Lib;
import jake2.util.Math3D;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.*;

import org.lwjgl.openal.AL10;

/**
 * Channel
 * 
 * @author dsanders/cwei
 */
public class Channel {

	final static int LISTENER = 0;
	final static int FIXED = 1;
	final static int DYNAMIC = 2;
	final static int MAX_CHANNELS = 32;
	private final static FloatBuffer NULLVECTOR = Lib.newFloatBuffer(3);
	
	private static Channel[] channels = new Channel[MAX_CHANNELS];
	private static IntBuffer sources = Lib.newIntBuffer(MAX_CHANNELS);
	// a reference of L:WJGLSoundImpl.buffers 
	private static IntBuffer buffers;
	private static Map looptable = new Hashtable(MAX_CHANNELS);

	private static boolean isInitialized = false;
	private static int numChannels; 
		
	// sound attributes
	private int type;
	private int entnum;
	private int entchannel;
	private int bufferId;
	private int sourceId;
	// private float volume;
	private float rolloff;
	private float[] origin = {0, 0, 0};

	// update flags
	private boolean autosound = false;
	private boolean active = false;
	private boolean modified = false;
	private boolean bufferChanged = false;

	private Channel(int sourceId) {
		this.sourceId = sourceId;
		clear();
	}

	private void clear() {
		entnum = entchannel = bufferId = -1;
		bufferChanged = false;
		// volume = 1.0f;
		rolloff = 0;
		autosound = false;
		active = false;
		modified = false;
	}
	
	private static IntBuffer tmp = Lib.newIntBuffer(1);

	static int init(IntBuffer buffers, float masterVolume) {
		Channel.buffers = buffers;
	    // create channels
		int sourceId;
		int error;
		for (int i = 0; i < MAX_CHANNELS; i++) {
			
			AL10.alGenSources(tmp);
			sourceId = tmp.get(0);
			
			if (sourceId <= 0) break;
			
			sources.put(i, sourceId);

			channels[i] = new Channel(sourceId);
			numChannels++;
			
			// set default values for AL sources
			AL10.alSourcef (sourceId, AL10.AL_GAIN, masterVolume);
			AL10.alSourcef (sourceId, AL10.AL_PITCH, 1.0f);
			AL10.alSourcei (sourceId, AL10.AL_SOURCE_ABSOLUTE,  AL10.AL_TRUE);
			AL10.nalSourcefv(sourceId, AL10.AL_VELOCITY, NULLVECTOR, 0);
			AL10.alSourcei (sourceId, AL10.AL_LOOPING, AL10.AL_FALSE);
			AL10.alSourcef (sourceId, AL10.AL_REFERENCE_DISTANCE, 300.0f);
			AL10.alSourcef (sourceId, AL10.AL_MIN_GAIN, 0.0005f);
			AL10.alSourcef (sourceId, AL10.AL_MAX_GAIN, 1.0f);
		}
		isInitialized = true;
		return numChannels;
	}
	
	static void reset() {
		for (int i = 0; i < numChannels; i++) {
			AL10.alSourceStop(sources.get(i));
			AL10.alSourcei(sources.get(i), AL10.AL_BUFFER, 0);
			channels[i].clear();
		}
	}
	
	static void shutdown() {
		AL10.alDeleteSources(sources);
		numChannels = 0;
		isInitialized = false;
	}
	
	static void addPlaySounds() {
		while (Channel.assign(PlaySound.nextPlayableSound()));
	}
	
	private static boolean assign(PlaySound ps) {
	    if (ps == null) return false;
		Channel ch = null;
		int i;
		for (i = 0; i < numChannels; i++) {
			ch = channels[i];

			if (ps.entchannel != 0 && ch.entnum == ps.entnum && ch.entchannel == ps.entchannel) {
				// always override sound from same entity
				if (ch.bufferId != ps.bufferId) {
				    AL10.alSourceStop(ch.sourceId);
				}
				break;
			}

			// don't let monster sounds override player sounds
			if ((ch.entnum == Globals.cl.playernum+1) && (ps.entnum != Globals.cl.playernum+1) && ch.bufferId != -1)
				continue;

			// looking for a free AL source
			if (!ch.active) {
				break;
			}
		}
		
		if (i == numChannels)
		    return false;
		
		ch.type = ps.type;
		if (ps.type == Channel.FIXED)
		    Math3D.VectorCopy(ps.origin, ch.origin);
		ch.entnum = ps.entnum;
		ch.entchannel = ps.entchannel;
		ch.bufferChanged = (ch.bufferId != ps.bufferId);			
		ch.bufferId = ps.bufferId;
		ch.rolloff = ps.attenuation * 2;
		//ch.volume = ps.volume;
		ch.active = true;
		ch.modified = true;
		return true;
	}
	
	private static Channel pickForLoop(int bufferId, float attenuation) {
        Channel ch;
        for (int i = 0; i < numChannels; i++) {
            ch = channels[i];
            // looking for a free AL source
            if (!ch.active) {
                ch.entnum = 0;
                ch.entchannel = 0;
        		ch.bufferChanged = (ch.bufferId != bufferId);			
                ch.bufferId = bufferId;
                ch.rolloff = attenuation * 2;
                ch.active = true;
                ch.modified = true;
                return ch;
            }
        }
        return null;
    }
	
	private static FloatBuffer sourceOriginBuffer = Lib.newFloatBuffer(3);

	static void playAllSounds(FloatBuffer listenerOrigin, float masterVolume) {
		float[] entityOrigin = {0, 0, 0};
		FloatBuffer sourceOrigin = sourceOriginBuffer;
		Channel ch;
		int sourceId;
		int state;

		for (int i = 0; i < numChannels; i++) {
			ch = channels[i];
			if (ch.active) {
				sourceId = ch.sourceId;
				switch (ch.type) {
					case Channel.LISTENER:
					    sourceOrigin = listenerOrigin;
						break;
					case Channel.DYNAMIC:
						CL_ents.GetEntitySoundOrigin(ch.entnum, entityOrigin);
						convertVector(entityOrigin, sourceOrigin);
						break;
					case Channel.FIXED:
						convertVector(ch.origin, sourceOrigin);
						break;
				}

				if (ch.modified) {
					if (ch.bufferChanged) {
						AL10.alSourcei(sourceId, AL10.AL_BUFFER, ch.bufferId);
					}
//					AL10.alSourcef (sourceId, AL10.AL_GAIN, masterVolume * ch.volume);
					AL10.alSourcef (sourceId, AL10.AL_GAIN, masterVolume);
					AL10.alSourcef (sourceId, AL10.AL_ROLLOFF_FACTOR, ch.rolloff);
					AL10.nalSourcefv(sourceId, AL10.AL_POSITION, sourceOrigin, 0);
					AL10.alSourcePlay(sourceId);
					ch.modified = false;
				} else {
					state = AL10.alGetSourcei(sourceId, AL10.AL_SOURCE_STATE);
					if (state == AL10.AL_PLAYING) {
						AL10.nalSourcefv(sourceId, AL10.AL_POSITION, sourceOrigin, 0);
					} else {
						ch.clear();
					}
				}
				ch.autosound = false;
			}
		}
	}
	
	/*
	 * 	adddLoopSounds
	 * 	Entities with a ->sound field will generated looped sounds
	 * 	that are automatically started, stopped, and merged together
	 * 	as the entities are sent to the client
	 */
	static void addLoopSounds() {
		
		if ((Globals.cl_paused.value != 0.0f) || (Globals.cls.state != Globals.ca_active) || !Globals.cl.sound_prepped) {
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
				Math3D.VectorCopy(ent.origin, ch.origin);
				continue;
			}

			sfx = Globals.cl.sound_precache[sound];
			if (sfx == null)
				continue;		// bad sound effect

			sc = sfx.cache;
			if (sc == null)
				continue;

			// allocate a channel
			ch = Channel.pickForLoop(buffers.get(sfx.bufferId), 6);
			if (ch == null)
				break;
				
			ch.type = FIXED;
			Math3D.VectorCopy(ent.origin, ch.origin);
			ch.autosound = true;
			
			looptable.put(key, ch);
			AL10.alSourcei(ch.sourceId, AL10.AL_LOOPING, AL10.AL_TRUE);
		}

		removeUnusedLoopSounds();

	}
	
	private static void removeUnusedLoopSounds() {
		Channel ch;
		// stop unused loopsounds
		for (Iterator iter = looptable.values().iterator(); iter.hasNext();) {
			ch = (Channel)iter.next();
			if (!ch.autosound) {
				AL10.alSourceStop(ch.sourceId);
				AL10.alSourcei(ch.sourceId, AL10.AL_LOOPING, AL10.AL_FALSE);
				iter.remove();
				ch.clear();
			}
		}
	}

	static void convertVector(float[] from, FloatBuffer to) {
		to.put(0, from[0]);
		to.put(1, from[2]);
		to.put(2, -from[1]);
	}

	static void convertOrientation(float[] forward, float[] up, FloatBuffer orientation) {
		orientation.put(0, forward[0]);
		orientation.put(1, forward[2]);
		orientation.put(2, -forward[1]);
		orientation.put(3, up[0]);
		orientation.put(4, up[2]);
		orientation.put(5, -up[1]);
	}

}
