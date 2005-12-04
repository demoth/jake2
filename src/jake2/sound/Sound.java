/*
 * Created on Apr 25, 2004
 * 
 * Copyright (C) 2003
 *
 * $Id: Sound.java,v 1.3 2005-12-04 20:48:28 cawe Exp $
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

import java.nio.ByteBuffer;

/**
 * Sound
 * 
 * @author cwei
 */
public interface Sound {
    
    static final int MAX_SFX = Defines.MAX_SOUNDS * 2;
    static final int STREAM_QUEUE = 8;
	
	String getName();
	
	boolean Init();
	void Shutdown();
	
	/*
	=====================
	S_BeginRegistration
	=====================
	*/
	void BeginRegistration();
	
	/*
	=====================
	S_RegisterSound
	=====================
	*/
	sfx_t RegisterSound(String sample);
	
	/*
	=====================
	S_EndRegistration
	=====================
	*/
	void EndRegistration();
	
	/*
	==================
	S_StartLocalSound
	==================
	*/
	void StartLocalSound(String sound);
	
	/*
	====================
	S_StartSound

	Validates the parms and ques the sound up
	if pos is NULL, the sound will be dynamically sourced from the entity
	Entchannel 0 will never override a playing sound
	====================
	*/
	void StartSound(float[] origin, int entnum, int entchannel, sfx_t sfx, float fvol, float attenuation, float timeofs);

	/*
	============
	S_Update

	Called once each time through the main loop
	============
	*/
	void Update(float[] origin, float[] forward, float[] right, float[] up);
	/*
	============
	S_RawSamples
	 
	Cinematic streaming and voice over network
	============
	*/
	void RawSamples(int samples, int rate, int width, int channels, ByteBuffer data);

    void disableStreaming();
	/*
	==================
	S_StopAllSounds
	==================
	*/
	void StopAllSounds();

}
