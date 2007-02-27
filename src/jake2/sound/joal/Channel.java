/*
 * Created on Jun 19, 2004
 * 
 * Copyright (C) 2003
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
package jake2.sound.joal;

import jake2.Defines;
import jake2.Globals;
import jake2.client.CL_ents;
import jake2.game.entity_state_t;
import jake2.qcommon.Com;
import jake2.sound.*;
import jake2.util.Math3D;

import java.nio.ByteBuffer;
import java.util.*;

import net.java.games.joal.AL;
import net.java.games.joal.ALException;

/**
 * Channel
 * 
 * @author cwei
 */
public class Channel {

	final static int LISTENER = 0;
	final static int FIXED = 1;
	final static int DYNAMIC = 2;
	final static int MAX_CHANNELS = 32;
	final static float[] NULLVECTOR = {0, 0, 0};
	
	private static AL al;
	private static Channel[] channels = new Channel[MAX_CHANNELS];
	private static int[] sources = new int[MAX_CHANNELS];
	// a reference of JOALSoundImpl.buffers 
	private static int[] buffers;
	private static Map looptable = new Hashtable(MAX_CHANNELS);

	private static int numChannels; 

    // stream handling
    private static boolean streamingEnabled = false;
    private static int streamQueue = 0;
    
	// sound attributes
	private int type;
	private int entnum;
	private int entchannel;
	private int bufferId;
	private int sourceId;
	private float volume;
	private float rolloff;
	private float[] origin = {0, 0, 0};

	// update flags
	private boolean autosound;
	private boolean active;
	private boolean modified;
	private boolean bufferChanged;
	private boolean volumeChanged;

	private Channel(int sourceId) {
		this.sourceId = sourceId;
		clear();
		this.volumeChanged = false;
		this.volume = 1.0f;
	}

	private void clear() {
		entnum = entchannel = bufferId = -1;
		bufferChanged = false;
		rolloff = 0;
		autosound = false;
		active = false;
		modified = false;
	}
    
    private static int[] tmp = new int[1];
	
    static int init(AL al, int[] buffers) {
	    Channel.al = al;
		Channel.buffers = buffers;
	    // create channels
		int sourceId;
		numChannels = 0;
		for (int i = 0; i < MAX_CHANNELS; i++) {
			
		    try {
			al.alGenSources(1, tmp, 0);
			sourceId = tmp[0];
			// can't generate more sources 
			if (sourceId <= 0) break;
		    } catch (ALException e) {
			// can't generate more sources 
			break;
		    }
			
			sources[i] = sourceId;

			channels[i] = new Channel(sourceId);
			numChannels++;
			
			// set default values for AL sources
			al.alSourcef (sourceId, AL.AL_GAIN, 1.0f);
			al.alSourcef (sourceId, AL.AL_PITCH, 1.0f);
			al.alSourcei (sourceId, AL.AL_SOURCE_RELATIVE,  AL.AL_FALSE);
			al.alSourcefv(sourceId, AL.AL_VELOCITY, NULLVECTOR, 0);
			al.alSourcei (sourceId, AL.AL_LOOPING, AL.AL_FALSE);
			al.alSourcef (sourceId, AL.AL_REFERENCE_DISTANCE, 200.0f);
			al.alSourcef (sourceId, AL.AL_MIN_GAIN, 0.0005f);
			al.alSourcef (sourceId, AL.AL_MAX_GAIN, 1.0f);
		}
		return numChannels;
	}
	
	static void reset() {
		for (int i = 0; i < numChannels; i++) {
			al.alSourceStop(sources[i]);
			al.alSourcei(sources[i], AL.AL_BUFFER, 0);
			channels[i].clear();
		}
	}
	
	static void shutdown() {
		al.alDeleteSources(numChannels, sources, 0);
		numChannels = 0;
	}
    
    static void enableStreaming() {
        if (streamingEnabled) return;
        
        // use the last source
        numChannels--;
        streamingEnabled = true;
        streamQueue = 0;

        int source = channels[numChannels].sourceId;
        al.alSourcei (source, AL.AL_SOURCE_RELATIVE,  AL.AL_TRUE);
        al.alSourcef(source, AL.AL_GAIN, 1.0f);
        channels[numChannels].volumeChanged = true;

        Com.DPrintf("streaming enabled\n");
    }

    static void disableStreaming() {
        if (!streamingEnabled) return;
        unqueueStreams();
        int source = channels[numChannels].sourceId;
        al.alSourcei (source, AL.AL_SOURCE_RELATIVE,  AL.AL_FALSE);

        // free the last source
        numChannels++;
        streamingEnabled = false;
        Com.DPrintf("streaming disabled\n");
    }
    
    static void unqueueStreams() {
        if (!streamingEnabled) return;
        int source = channels[numChannels].sourceId;

        // stop streaming
        al.alSourceStop(source);
        int[] tmpCount = new int[]{0};
        al.alGetSourcei(source, AL.AL_BUFFERS_QUEUED, tmpCount, 0);
        int count = tmpCount[0];
        Com.DPrintf("unqueue " + count + " buffers\n");
        while (count-- > 0) {
            al.alSourceUnqueueBuffers(source, 1, tmp, 0);
        }
        streamQueue = 0;
    }

    static void updateStream(ByteBuffer samples, int count, int format, int rate) {
        enableStreaming();
        int[] buffer = tmp;
        int source = channels[numChannels].sourceId;
        
        int[] tmp = new int[]{0};
        al.alGetSourcei(source, AL.AL_BUFFERS_PROCESSED, tmp, 0);
        int processed = tmp[0];
        al.alGetSourcei(source, AL.AL_SOURCE_STATE, tmp, 0);
        int state = tmp[0];
        boolean playing = ( state == AL.AL_PLAYING);
        boolean interupted = !playing && streamQueue > 2;
        
        if (interupted) {
            unqueueStreams();
            buffer[0] = buffers[Sound.MAX_SFX + streamQueue++];
            Com.DPrintf("queue " + (streamQueue - 1) + '\n');
        } else if (processed < 2) {
            // check queue overrun
            if (streamQueue >= Sound.STREAM_QUEUE) return;
            buffer[0] = buffers[Sound.MAX_SFX + streamQueue++];
            Com.DPrintf("queue " + (streamQueue - 1) + '\n');
        } else {
            // reuse the buffer
            al.alSourceUnqueueBuffers(source, 1, buffer, 0);
        }

        samples.position(0);
        samples.limit(count);
        al.alBufferData(buffer[0], format, samples, count, rate);
        al.alSourceQueueBuffers(source, 1, buffer, 0);
        
        if (streamQueue > 1 && !playing) {
            Com.DPrintf("start sound\n");
            al.alSourcePlay(source);
        }
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
				    al.alSourceStop(ch.sourceId);
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
		ch.volumeChanged = (ch.volume != ps.volume);			
		ch.volume = ps.volume;
		ch.rolloff = ps.attenuation * 2;
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
           		ch.volumeChanged = (ch.volume < 1.0f);			
                ch.volume = 1.0f;
                ch.rolloff = attenuation * 2;
                ch.active = true;
                ch.modified = true;
                return ch;
            }
        }
        return null;
    }

	// stack variables
	private static float[] entityOrigin = {0, 0, 0};
	private static float[] sourceOrigin = {0, 0, 0};

	static void playAllSounds(float[] listenerOrigin) {
		Channel ch;
		int sourceId;
		int state;
        int[] tmp = new int[]{0};

		for (int i = 0; i < numChannels; i++) {
			ch = channels[i];
			if (ch.active) {
				sourceId = ch.sourceId;
				switch (ch.type) {
					case Channel.LISTENER:
						Math3D.VectorCopy(listenerOrigin, sourceOrigin);
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
					    try {
						al.alSourcei(sourceId, AL.AL_BUFFER, ch.bufferId);
					    } catch (ALException e) {
						// fallback for buffer changing
						al.alSourceStop(sourceId);
						al.alSourcei(sourceId, AL.AL_BUFFER, ch.bufferId);
					    }
					}
					if (ch.volumeChanged) {
						al.alSourcef (sourceId, AL.AL_GAIN, ch.volume);
					}
					al.alSourcef (sourceId, AL.AL_ROLLOFF_FACTOR, ch.rolloff);
					al.alSourcefv(sourceId, AL.AL_POSITION, sourceOrigin, 0);
					al.alSourcePlay(sourceId);
					ch.modified = false;
				} else {
					al.alGetSourcei(sourceId, AL.AL_SOURCE_STATE, tmp , 0);
					state = tmp[0];
                    if (state == AL.AL_PLAYING) {
						al.alSourcefv(sourceId, AL.AL_POSITION, sourceOrigin, 0);
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
			ch = Channel.pickForLoop(buffers[sfx.bufferId], 6);
			if (ch == null)
				break;
				
			ch.type = FIXED;
			Math3D.VectorCopy(ent.origin, ch.origin);
			ch.autosound = true;
			
			looptable.put(key, ch);
			al.alSourcei(ch.sourceId, AL.AL_LOOPING, AL.AL_TRUE);
		}

		removeUnusedLoopSounds();

	}
	
	private static void removeUnusedLoopSounds() {
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
}
