/*
 * LittleEndianHandler.java
 * Copyright (C) 2003
 * 
 * $Id$
 */
package jake2.game;


/**
 * LittleEndianHandler</code>
 */
public final class LittleEndianHandler extends EndianHandler {

	/* (non-Javadoc)
	 * @see quake2.EndianHandler#BigFloat(float)
	 */
	public float BigFloat(float f) {
		return swapFloat(f);
	}

	/* (non-Javadoc)
	 * @see quake2.EndianHandler#BigShort(short)
	 */
	public short BigShort(short s) {
		return swapShort(s);
	}

	/* (non-Javadoc)
	 * @see quake2.EndianHandler#BigLong(int)
	 */
	public int BigLong(int i) {
		return swapInt(i);
	}

	/* (non-Javadoc)
	 * @see quake2.EndianHandler#LittleFloat(float)
	 */
	public float LittleFloat(float f) {
		return f;
	}

	/* (non-Javadoc)
	 * @see quake2.EndianHandler#LittleShort(short)
	 */
	public short LittleShort(short s) {
		return s;
	}

	/* (non-Javadoc)
	 * @see quake2.EndianHandler#LittleLong(int)
	 */
	public int LittleLong(int i) {
		return i;
	}

}
