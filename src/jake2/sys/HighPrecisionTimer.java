/*
 * HighPrecisionTimer.java
 * Copyright (C) 2005
 * 
 * $Id: HighPrecisionTimer.java,v 1.1 2005-07-01 14:11:00 hzi Exp $
 */
package jake2.sys;

import jdk.internal.perf.Perf;




class HighPrecisionTimer extends Timer {

	private Perf perf = Perf.getPerf();
	private double f = 1000.0 / perf.highResFrequency();
	private long base;
	
	HighPrecisionTimer() {
		base = perf.highResCounter();
	}
	
	public long currentTimeMillis() {
		long time = perf.highResCounter();
		long delta = time - base;
		if (delta < 0) {
			delta += Long.MAX_VALUE + 1;
		}
		return (long)(delta * f);
	}
}
