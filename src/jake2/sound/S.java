/*
 * S.java
 * Copyright (C) 2003
 * 
 * $Id: S.java,v 1.12 2004-06-17 12:12:43 hoz Exp $
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
import jake2.Globals;
import jake2.game.Cmd;
import jake2.game.cvar_t;
import jake2.game.entity_state_t;
import jake2.qcommon.*;
import jake2.sound.joal.JOALSoundImpl;
import jake2.sound.jsound.JSoundImpl;
import jake2.util.Vargs;

import java.io.*;
import java.util.Vector;

import javax.sound.sampled.*;

/**
 * S
 */
public class S {
	
	static Sound impl;
	static cvar_t s_impl;
	
	static Vector drivers = new Vector(3);
	 
	static {
		try {
			Class.forName("jake2.sound.DummyDriver");
			Class.forName("jake2.sound.joal.JOALSoundImpl");
			Class.forName("jake2.sound.jsound.JSoundImpl");
		}
		catch (ClassNotFoundException e) {
		}
	};
	
	public static void register(Sound driver) {
		if (driver == null) {
			throw new IllegalArgumentException("Sound implementation can't be null");
		}
		if (!drivers.contains(driver)) {
			drivers.add(driver);
		}
	}
	
	public static void useDriver(String driverName) {
		Sound driver = null;
		int count = drivers.size();
		for (int i = 0; i < count; i++) {
			driver = (Sound) drivers.get(i);
			if (driver.getName().equals(driverName)) {
				impl = driver;
				return;
			}
		}
		// if driver not found use dummy
		impl = (Sound)drivers.get(0);
	}
	
	public static void Init() {
		
		Com.Printf("\n------- sound initialization -------\n");

		cvar_t cv = Cvar.Get("s_initsound", "1", 0);
		if (cv.value == 0.0f) {
			Com.Printf("not initializing.\n");
			useDriver("dummy");
			return;			
		}

		s_impl = Cvar.Get("s_impl", "dummy", Defines.CVAR_ARCHIVE);
		useDriver(s_impl.string);

		if (impl.Init()) {
			// driver ok
			Cvar.Set("s_impl", impl.getName());
		} else {
			// fallback
			useDriver("dummy");
		}
		
		Com.Printf("\n------- use sound driver \"" + impl.getName() + "\" -------\n");
		StopAllSounds();
	}
	
	public static void Shutdown() {
		impl.Shutdown();
	}
	
	/*
	=====================
	S_BeginRegistration
	=====================
	*/
	public static void BeginRegistration() {
		impl.BeginRegistration();		
	}
	
	/*
	=====================
	S_RegisterSound
	=====================
	*/
	public static sfx_t RegisterSound(String sample) {
		return impl.RegisterSound(sample);
	}
	
	/*
	=====================
	S_EndRegistration
	=====================
	*/
	public static void EndRegistration() {
		impl.EndRegistration();
	}
	
	/*
	==================
	S_StartLocalSound
	==================
	*/
	public static void StartLocalSound(String sound) {
		impl.StartLocalSound(sound);		
	}
	
	/*
	====================
	S_StartSound

	Validates the parms and ques the sound up
	if pos is NULL, the sound will be dynamically sourced from the entity
	Entchannel 0 will never override a playing sound
	====================
	*/
	public static void StartSound(float[] origin, int entnum, int entchannel, sfx_t sfx, float fvol, float attenuation, float timeofs) {
		impl.StartSound(origin, entnum, entchannel, sfx, fvol, attenuation, timeofs);
	}

	/*
	============
	S_Update

	Called once each time through the main loop
	============
	*/
	public static void Update(float[] origin, float[] forward, float[] right, float[] up) {
		impl.Update(origin, forward, right, up);
	}

	/*
	============
	S_RawSamples
	 
	Cinematic streaming and voice over network
	============
	*/
	public static void RawSamples(int samples, int rate, int width, int channels, byte[] data) {
		impl.RawSamples(samples, rate, width, channels, data);
	}

	/*
	==================
	S_StopAllSounds
	==================
	*/
	public static void StopAllSounds() {
		impl.StopAllSounds();
	}
}
