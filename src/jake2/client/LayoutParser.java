/*
 * LayoutParser.java
 * Copyright (C) 2003
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

import jake2.Defines;
import jake2.qcommon.Com;

final class LayoutParser {
    int tokenPos;

    int tokenLength;

    int index;

    int length;

    String data;

    LayoutParser() {
	init(null);
    }

    public void init(String layout) {
	tokenPos = 0;
	tokenLength = 0;
	index = 0;
	data = (layout != null) ? layout : "";
	length = (layout != null) ? layout.length() : 0;
    }

    public boolean hasNext() {
	return !isEof();
    }

    public void next() {
	if (data == null) {
	    tokenLength = 0;
	    return;
	}

	while (true) {
	    // skip whitespace
	    skipwhites();
	    if (isEof()) {
		tokenLength = 0;
		return;
	    }

	    // skip // comments
	    if (getchar() == '/') {
		if (nextchar() == '/') {
		    skiptoeol();
		    // goto skip whitespace
		    continue;
		} else {
		    prevchar();
		    break;
		}
	    } else
		break;
	}

	int c;
	int len = 0;
	// handle quoted strings specially
	if (getchar() == '\"') {
	    nextchar();
	    tokenPos = index;
	    while (true) {
		c = getchar();
		nextchar();
		if (c == '\"' || c == 0) {
		    tokenLength = len;
		    return;
		}
		if (len < Defines.MAX_TOKEN_CHARS) {
		    ++len;
		}
	    }
	}

	// parse a regular word
	c = getchar();
	tokenPos = index;
	do {
	    if (len < Defines.MAX_TOKEN_CHARS) {
		++len;
	    }
	    c = nextchar();
	} while (c > 32);

	if (len == Defines.MAX_TOKEN_CHARS) {
	    Com.Printf("Token exceeded " + Defines.MAX_TOKEN_CHARS
		    + " chars, discarded.\n");
	    len = 0;
	}

	tokenLength = len;
	return;
    }

    public boolean tokenEquals(String other) {
	if (tokenLength != other.length())
	    return false;
	return data.regionMatches(tokenPos, other, 0, tokenLength);
    }

    public int tokenAsInt() {
	if (tokenLength == 0)
	    return 0;
	return atoi();
    }

    public String token() {
	if (tokenLength == 0)
	    return "";
	return data.substring(tokenPos, tokenPos + tokenLength);
    }

    private int atoi() {
	int result = 0;
	boolean negative = false;
	int i = 0, max = tokenLength;
	String s = data;
	int limit;
	int multmin;
	int digit;

	if (max > 0) {
	    if (s.charAt(tokenPos) == '-') {
		negative = true;
		limit = Integer.MIN_VALUE;
		i++;
	    } else {
		limit = -Integer.MAX_VALUE;
	    }
	    multmin = limit / 10;
	    if (i < max) {
		digit = Character.digit(s.charAt(tokenPos + i++), 10);
		if (digit < 0) {
		    return 0; // wrong format
		} else {
		    result = -digit;
		}
	    }
	    while (i < max) {
		// Accumulating negatively avoids surprises near MAX_VALUE
		digit = Character.digit(s.charAt(tokenPos + i++), 10);
		if (digit < 0) {
		    return 0; // wrong format
		}
		if (result < multmin) {
		    return 0; // wrong format
		}
		result *= 10;
		if (result < limit + digit) {
		    return 0; // wrong format
		}
		result -= digit;
	    }
	} else {
	    return 0; // wrong format
	}
	if (negative) {
	    if (i > 1) {
		return result;
	    } else { /* Only got "-" */
		return 0; // wrong format
	    }
	} else {
	    return -result;
	}
    }

    private char getchar() {
	if (index < length) {
	    return data.charAt(index);
	}
	return 0;
    }

    private char nextchar() {
	++index;
	if (index < length) {
	    return data.charAt(index);
	}
	return 0;
    }

    private char prevchar() {
	if (index > 0) {
	    --index;
	    return data.charAt(index);
	}
	return 0;
    }

    private boolean isEof() {
	return index >= length;
    }

    private char skipwhites() {
	char c = 0;
	while (index < length && ((c = data.charAt(index)) <= ' ') && c != 0)
	    ++index;
	return c;
    }

    private char skiptoeol() {
	char c = 0;
	while (index < length && (c = data.charAt(index)) != '\n' && c != 0)
	    ++index;
	return c;
    }
}
