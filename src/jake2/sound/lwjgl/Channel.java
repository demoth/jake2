/*
 * Created on Jun 19, 2004
 * 
 * Copyright (C) 2003
 *
 * $Id: Channel.java,v 1.1 2004-12-16 20:17:55 cawe Exp $
 */
/*
Copyright (C) 1997-2001 Id Software, Inc.

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.

*/
package jake2.sound.lwjgl;

/**
 * Channel
 * 
 * @author cwei
 */
public class Channel {

	final static int LISTENER = 0;
	final static int FIXED = 1;
	final static int DYNAMIC = 2;
		
	int entnum;
	int entchannel;
	int bufferId;
	float rolloff;
	boolean autosound = false;
	int sourceId;
	boolean active = false;
	boolean modified = false;
	boolean bufferChanged = false;
	
	// sound attributes
	int type;
	int entity;
	float[] origin = {0, 0, 0};
	
	Channel(int sourceId) {
		this.sourceId = sourceId;
		clear();
	}

	void addListener() {
		type = LISTENER;
	}
		
	void addFixed(float[] origin) {
		type = FIXED;
		this.origin = origin;
	}

	void addDynamic(int entity) {
		type = DYNAMIC;
		this.entity = entity;
	}
	
	void clear() {
		entnum = -1;
		entchannel = -1;
		bufferId = -1;
		bufferChanged = false;
		rolloff = 0;
		autosound = false;
		active = false;
		modified = false;
	}
}
