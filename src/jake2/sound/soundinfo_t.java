/*
 * soundinfo_t.java
 * Copyright (C) 2004
 *
 * $Id: soundinfo_t.java,v 1.1 2004-07-08 20:56:49 hzi Exp $
 */
package jake2.sound;

/**
 * soundinfo_t
 */
public class soundinfo_t {
	int channels;
	int samples; // mono samples in buffer
	int submission_chunk; // don't mix less than this #
	int samplepos; // in mono samples
	int samplebits;
	int speed;	
}
