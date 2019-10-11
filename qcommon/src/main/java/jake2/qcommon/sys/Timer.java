/*
 * Timer.java
 * Copyright (C) 2005
 */
package jake2.qcommon.sys;

import jake2.qcommon.Globals;

public class Timer {
    private long base;

    public Timer() {
        base = System.currentTimeMillis();
    }

    private long currentTimeMillis() {
        long time = System.currentTimeMillis();
        long delta = time - base;
        if (delta < 0) {
            delta += Long.MAX_VALUE + 1;
        }
        return delta;
    }

    private static final Timer t = new Timer();

    public static int Milliseconds() {
        return Globals.curtime = (int) (t.currentTimeMillis());
    }
}
