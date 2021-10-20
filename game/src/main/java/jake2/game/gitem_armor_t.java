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
// $Id: gitem_armor_t.java,v 1.1 2004-07-07 19:59:25 hzi Exp $

package jake2.game;

public class gitem_armor_t {
	
	public gitem_armor_t(
			int base_count,
			int max_count,
			float normal_protection,
			float energy_protection) {
		this.base_count = base_count;
		this.max_count = max_count;
		this.normal_protection = normal_protection;
		this.energy_protection = energy_protection;
	}

	int base_count;
	int max_count;
	float normal_protection;
	float energy_protection;

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		gitem_armor_t that = (gitem_armor_t) o;

		if (base_count != that.base_count) return false;
		if (max_count != that.max_count) return false;
		if (Float.compare(that.normal_protection, normal_protection) != 0) return false;
		return Float.compare(that.energy_protection, energy_protection) == 0;
	}

	@Override
	public int hashCode() {
		int result = base_count;
		result = 31 * result + max_count;
		result = 31 * result + (normal_protection != +0.0f ? Float.floatToIntBits(normal_protection) : 0);
		result = 31 * result + (energy_protection != +0.0f ? Float.floatToIntBits(energy_protection) : 0);
		return result;
	}

	@Override
	public String toString() {
		return "gitem_armor_t{" +
				"base_count=" + base_count +
				", max_count=" + max_count +
				", normal_protection=" + normal_protection +
				", energy_protection=" + energy_protection +
				'}';
	}
}
