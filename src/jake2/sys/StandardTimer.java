/*
 * StandardTimer.java
 * Copyright (C) 2005
 * 
 * $Id: StandardTimer.java,v 1.1 2005-07-01 14:11:00 hzi Exp $
 */
package jake2.sys;


class StandardTimer extends Timer {

	private long base;
	
	StandardTimer() {
		base = System.currentTimeMillis();
	}
	
	public long currentTimeMillis() {
		long time = System.currentTimeMillis();
		long delta = time - base;
		if (delta < 0) {
			delta += Long.MAX_VALUE + 1;
		}
		return delta;
	}

}
