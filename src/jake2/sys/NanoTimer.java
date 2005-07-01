/*
 * NanoTimer.java
 * Copyright (C) 2005
 * 
 * $Id: NanoTimer.java,v 1.1 2005-07-01 14:11:00 hzi Exp $
 */
package jake2.sys;


public class NanoTimer extends Timer {

	private long base;

	NanoTimer() {
		base = System.nanoTime();
	}
	
	public long currentTimeMillis() {
		long time = System.nanoTime();
		long delta = time - base;
		if (delta < 0) {
			delta += Long.MAX_VALUE + 1;
		}
		return (long)(delta * 0.000001);
	}

}
