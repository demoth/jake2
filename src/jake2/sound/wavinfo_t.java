/*
 * wavinfo_t.java
 * Copyright (C) 2004
 *
 * $Id: wavinfo_t.java,v 1.1 2004-04-16 09:28:04 hoz Exp $
 */
package jake2.sound;

/**
 * wavinfo_t
 */
public class wavinfo_t {
	int rate;
	int width;
	int channels;
	int loopstart;
	int samples;
	int dataofs; // chunk starts this many bytes from file start
}
