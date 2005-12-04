/*
 * Created on Apr 25, 2004
 * 
 * Copyright (C) 2003
 *
 * $Id: DummyDriver.java,v 1.2 2005-12-04 17:26:33 cawe Exp $
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

import java.nio.ByteBuffer;

/**
 * DummyDriver
 * 
 * @author cwei
 */
public final class DummyDriver implements Sound {

	static {
		S.register(new DummyDriver());
	};
	
	private DummyDriver() {
	}

	/* (non-Javadoc)
	 * @see jake2.sound.Sound#Init()
	 */
	public boolean Init() {
		return true;
	}

	/* (non-Javadoc)
	 * @see jake2.sound.Sound#Shutdown()
	 */
	public void Shutdown() {
	}

	/* (non-Javadoc)
	 * @see jake2.sound.Sound#BeginRegistration()
	 */
	public void BeginRegistration() {
	}

	/* (non-Javadoc)
	 * @see jake2.sound.Sound#RegisterSound(java.lang.String)
	 */
	public sfx_t RegisterSound(String sample) {
		return null;
	}

	/* (non-Javadoc)
	 * @see jake2.sound.Sound#EndRegistration()
	 */
	public void EndRegistration() {
	}

	/* (non-Javadoc)
	 * @see jake2.sound.Sound#StartLocalSound(java.lang.String)
	 */
	public void StartLocalSound(String sound) {
	}

	/* (non-Javadoc)
	 * @see jake2.sound.Sound#StartSound(float[], int, int, jake2.sound.sfx_t, float, float, float)
	 */
	public void StartSound(float[] origin, int entnum, int entchannel, sfx_t sfx, float fvol, float attenuation, float timeofs) {
	}

	/* (non-Javadoc)
	 * @see jake2.sound.Sound#Update(float[], float[], float[], float[])
	 */
	public void Update(float[] origin, float[] forward, float[] right, float[] up) {
	}

	/* (non-Javadoc)
	 * @see jake2.sound.Sound#RawSamples(int, int, int, int, byte[])
	 */
	public void RawSamples(int samples, int rate, int width, int channels, ByteBuffer data) {
	}

    public void disableStreaming() {
    }

    /* (non-Javadoc)
	 * @see jake2.sound.Sound#StopAllSounds()
	 */
	public void StopAllSounds() {
	}

	/* (non-Javadoc)
	 * @see jake2.sound.Sound#getName()
	 */
	public String getName() {
		return "dummy";
	}
}
