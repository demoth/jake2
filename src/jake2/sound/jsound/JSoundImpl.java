/*
 * JSoundImpl.java
 * Copyright (C) 2004
 *
 * $Id: JSoundImpl.java,v 1.1 2004-04-15 10:31:40 hoz Exp $
 */
package jake2.sound.jsound;

import jake2.sound.SoundImpl;
import jake2.sound.sfx_t;

/**
 * JSoundImpl
 */
public class JSoundImpl extends SoundImpl {

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

}
