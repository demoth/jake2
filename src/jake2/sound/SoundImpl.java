/*
 * SoundImpl.java
 * Copyright (C) 2004
 * 
 * $Id: SoundImpl.java,v 1.1 2004-04-15 10:31:40 hoz Exp $
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

/**
 * SoundImpl
 */
public abstract class SoundImpl {

	abstract public boolean Init();
	abstract public void Shutdown();
	
	/*
	====================
	S_StartSound

	Validates the parms and ques the sound up
	if pos is NULL, the sound will be dynamically sourced from the entity
	Entchannel 0 will never override a playing sound
	====================
	*/
	abstract public void StartSound(float[] origin, int entnum, int entchannel, sfx_t sfx, float fvol, float attenuation, float timeofs);

	/*
	============
	S_Update

	Called once each time through the main loop
	============
	*/
	abstract public void Update(float[] origin, float[] forward, float[] right, float[] up);

	/*
	==================
	S_StopAllSounds
	==================
	*/
	abstract public void StopAllSounds();

}
