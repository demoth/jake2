/*
 * JSoundImpl.java
 * Copyright (C) 2004
 *
 * $Id: JSoundImpl.java,v 1.2 2004-04-26 13:43:33 cwei Exp $
 */
package jake2.sound.jsound;

import jake2.sound.Sound;
import jake2.sound.sfx_t;

/**
 * JSoundImpl
 */
public class JSoundImpl  implements Sound {

	public boolean Init() {
		SND_MIX.InitScaletable();
		return false;
	}

	/* (non-Javadoc)
	 * @see jake2.sound.SoundImpl#Shutdown()
	 */
	public void Shutdown() {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see jake2.sound.SoundImpl#StartSound(float[], int, int, jake2.sound.sfx_t, float, float, float)
	 */
	public void StartSound(float[] origin, int entnum, int entchannel, sfx_t sfx, float fvol, float attenuation, float timeofs) {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see jake2.sound.SoundImpl#StopAllSounds()
	 */
	public void StopAllSounds() {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see jake2.sound.SoundImpl#Update(float[], float[], float[], float[])
	 */
	public void Update(float[] origin, float[] forward, float[] right, float[] up) {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see jake2.sound.Sound#getName()
	 */
	public String getName() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see jake2.sound.Sound#BeginRegistration()
	 */
	public void BeginRegistration() {
		// TODO Auto-generated method stub
		
	}

	/* (non-Javadoc)
	 * @see jake2.sound.Sound#RegisterSound(java.lang.String)
	 */
	public sfx_t RegisterSound(String sample) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see jake2.sound.Sound#EndRegistration()
	 */
	public void EndRegistration() {
		// TODO Auto-generated method stub
		
	}

	/* (non-Javadoc)
	 * @see jake2.sound.Sound#StartLocalSound(java.lang.String)
	 */
	public void StartLocalSound(String sound) {
		// TODO Auto-generated method stub
		
	}

	/* (non-Javadoc)
	 * @see jake2.sound.Sound#RawSamples(int, int, int, int, byte[])
	 */
	public void RawSamples(int samples, int rate, int width, int channels, byte[] data) {
		// TODO Auto-generated method stub
		
	}

}
