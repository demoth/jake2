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

// Created on 07.11.2003 by RST.
// $Id: gitem_armor_t.java,v 1.2 2003-11-29 13:28:29 rst Exp $

package jake2.game;

public class gitem_armor_t {
	public gitem_armor_t(
		int base_count,
		int max_count,
		float normal_protection,
		float energy_protection,
		int armor) {
		this.base_count= base_count;
		this.max_count= max_count;
		this.normal_protection= normal_protection;
		this.energy_protection= energy_protection;
		this.armor= armor;
	}

	int base_count;
	int max_count;
	float normal_protection;
	float energy_protection;
	int armor;
}
