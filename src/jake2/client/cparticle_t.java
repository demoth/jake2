/*
 * cparticle_t.java
 * Copyright (C) 2003
 *
 * $Id: cparticle_t.java,v 1.1 2004-01-12 12:13:32 cwei Exp $
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
package jake2.client;

/**
 * cparticle_t
 *  
 * @author cwei
 */
public class cparticle_t {

	public cparticle_t next;
	public float time;

	public float[] org = {0, 0, 0}; // vec3_t
	public float[] vel = {0, 0, 0}; // vec3_t
	public float[] accel = {0, 0, 0}; // vec3_t

	public float color;
	public float colorvel;
	public float alpha;
	public float alphavel;
}
