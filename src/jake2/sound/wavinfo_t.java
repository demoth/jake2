/*
 * wavinfo_t.java
 * Copyright (C) 2004
 *
 * $Id: wavinfo_t.java,v 1.2 2004-04-26 13:43:33 cwei Exp $
 */
package jake2.sound;

/**
 * wavinfo_t
 */
public class wavinfo_t {
	public int rate;
	public int width;
	public int channels;
	public int loopstart;
	public int samples;
	public int dataofs; // chunk starts this many bytes from file start
}
