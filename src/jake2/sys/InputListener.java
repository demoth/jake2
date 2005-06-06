/*
 * InputListener.java
 * Copyright (C) 2004
 * 
 * $Id: InputListener.java,v 1.5 2005-06-06 13:30:37 hzi Exp $
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
package jake2.sys;

import java.awt.event.*;
import java.util.LinkedList;

/**
 * InputListener
 */
public final class InputListener implements KeyListener, MouseListener, 
		MouseMotionListener, ComponentListener, MouseWheelListener {

	// modifications of eventQueue must be thread safe!
	private static LinkedList eventQueue = new LinkedList();

	static void addEvent(Jake2InputEvent ev) {
		synchronized (eventQueue) {
			eventQueue.addLast(ev);
		}
	}

	static Jake2InputEvent nextEvent() {
		Jake2InputEvent ev;
		synchronized (eventQueue) {
			ev = (!eventQueue.isEmpty())?(Jake2InputEvent)eventQueue.removeFirst():null;
		}
		return ev;
	}

	public void keyPressed(KeyEvent e) {
		addEvent(new Jake2InputEvent(Jake2InputEvent.KeyPress, e));
	}

	public void keyReleased(KeyEvent e) {
		addEvent(new Jake2InputEvent(Jake2InputEvent.KeyRelease, e));
	}

	public void keyTyped(KeyEvent e) {
		if ((e.getModifiersEx() & InputEvent.ALT_GRAPH_DOWN_MASK) != 0) {
			addEvent(new Jake2InputEvent(Jake2InputEvent.KeyPress, e));
		}		
	}

	public void mouseClicked(MouseEvent e) {
	}

	public void mouseEntered(MouseEvent e) {
	}

	public void mouseExited(MouseEvent e) {
	}

	public void mousePressed(MouseEvent e) {
		addEvent(new Jake2InputEvent(Jake2InputEvent.ButtonPress, e));
	}

	public void mouseReleased(MouseEvent e) {
		addEvent(new Jake2InputEvent(Jake2InputEvent.ButtonRelease, e));
	}

	public void mouseDragged(MouseEvent e) {
		addEvent(new Jake2InputEvent(Jake2InputEvent.MotionNotify, e));
	}

	public void mouseMoved(MouseEvent e) {
		addEvent(new Jake2InputEvent(Jake2InputEvent.MotionNotify, e));
	}

	public void componentHidden(ComponentEvent e) {
	}

	public void componentMoved(ComponentEvent e) {
		addEvent(new Jake2InputEvent(Jake2InputEvent.ConfigureNotify, e));
	}

	public void componentResized(ComponentEvent e) {
		addEvent(new Jake2InputEvent(Jake2InputEvent.ConfigureNotify, e));
	}

	public void componentShown(ComponentEvent e) {
		JOGLKBD.c = e.getComponent();
		addEvent(new Jake2InputEvent(Jake2InputEvent.CreateNotify, e));
	}

    public void mouseWheelMoved(MouseWheelEvent e) {
        addEvent(new Jake2InputEvent(Jake2InputEvent.WheelMoved, e));
    }	

}

