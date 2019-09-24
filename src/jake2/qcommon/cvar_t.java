/*
 * cvar_t.java
 * Copyright (C) 2003
 *
 * $Id: cvar_t.java,v 1.2 2004-07-08 15:58:44 hzi Exp $
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
package jake2.qcommon;

/**
 * cvar_t implements the struct cvar_t of the C version
 */
public final class cvar_t {
    public String name;
    public String string;
    // allow changes, but for the next game
    // todo rename to deferred_value
    public String latched_string;
    public int flags = 0;
    public boolean modified = false;
    public float value = 0.0f;

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("cvar_t{");
        if (name != null)
            sb.append("name='").append(name).append('\'');
        if (string != null)
            sb.append(", string='").append(string).append('\'');
        if (latched_string != null)
        	sb.append(", latched_string='").append(latched_string).append('\'');
        if (flags != 0)
        	sb.append(", flags=").append(flags);
        if (modified)
        	sb.append(", modified=").append(modified);
        if (value != 0f)
        	sb.append(", value=").append(value);
        sb.append('}');
        return sb.toString();
    }
}
