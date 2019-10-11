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

// Created on 20.11.2003 by RST.
package jake2.client;

public class viddef_t {
    private int width;
    private int height;
    private int newWidth;
    private int newHeight;

    /**
     * This method hasn't an effect on <code>width</code> and <code>height</code>
     * directly. The new values will be active after an <code>update()</code>
     * call.
     * 
     * @param width
     *                the visible render screen width.
     * @param height
     *                the visible render screen height.
     */
    public synchronized void setSize(int width, int height) {
	newWidth = width;
	newHeight = height;
    }

    /**
     * Updates the buffered <code>width</code> and <code>height</code>. The
     * method should be called once at the beginning of a frame.
     */
    public synchronized void update() {
	width = newWidth;
	height = newHeight;
    }

    public int getWidth() {
	return width;
    }

    public int getHeight() {
	return height;
    }
}
