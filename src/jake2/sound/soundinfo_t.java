/*
 * soundinfo_t.java
 * Copyright (C) 2004
 *
 * $Id: soundinfo_t.java,v 1.1 2004-04-15 10:31:40 hoz Exp $
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
