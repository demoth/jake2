/*
 * AbstractEndianHandler.java
 * Copyright (C) 2003
 *
 * $Id: EndianHandler.java,v 1.2 2004-07-08 15:58:43 hzi Exp $
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

public class EndianHandler {
    private static final int mask = 0xFF;

    public static int swapInt(int i) {

        int a = i & mask;
        i >>>= 8;

        a <<= 24;

        int b = i & mask;

        i >>>= 8;
        b <<= 16;

        int c = i & mask;
        i >>>= 8;
        c <<= 8;

        return i | c | b | a;
    }

}
