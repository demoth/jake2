/*
 * Timer.java
 * Copyright (C) 2005
 * 
 * $Id: Timer.java,v 1.2 2005-07-01 14:20:54 hzi Exp $
 */
package jake2.sys;

import jake2.Globals;
import jake2.qcommon.Com;


public abstract class Timer {

	abstract public long currentTimeMillis();
	private static long time = 0;
	
	static Timer t;
	
	static {
		try {
			t = new NanoTimer();
		} catch (Throwable e) {
			try {
				t = new HighPrecisionTimer();
			} catch (Throwable e1) {
				t = new StandardTimer();
			}
		}
		Com.Println("using " + t.getClass().getName());
	}
	
	public static int Milliseconds() {
		return Globals.curtime = (int)(t.currentTimeMillis());
	}
}
