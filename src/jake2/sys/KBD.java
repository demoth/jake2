/*
 * KBD.java
 * Copyright (C) 2004
 * 
 * $Id: KBD.java,v 1.2 2004-01-07 13:44:36 hoz Exp $
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

import jake2.Defines;
import jake2.client.Key;

import java.awt.AWTEvent;
import java.awt.event.*;
import java.util.LinkedList;

/**
 * KBD
 */
public final class KBD implements KeyListener, MouseListener {
	
	// modifications of eventQueue must be thread safe!
	private static LinkedList eventQueue = new LinkedList(); 
	
	public static void Init() {
//	01260         Key_Event_fp = fp;
	}

	public static void Update() {
		// get events
		HandleEvents();
	}

	public static void Close() {
	}
	
	static void HandleEvents() {
//		00464         XEvent event;
//		00465         int b;
//		00466         qboolean dowarp = false;
//		00467         int mwx = vid.width/2;
//		00468         int mwy = vid.height/2;
//		00469    
//		00470         if (!dpy)
//		00471                 return;
//		00472
		Jake2InputEvent event;
		while ( (event=nextEvent()) != null ) {
//		00473         while (XPending(dpy)) {
//		00474 
//		00475                 XNextEvent(dpy, &event);
//		00476 
			switch(event.type) {
				case Jake2InputEvent.KeyPress:
				case Jake2InputEvent.KeyRelease:
					Do_Key_Event(((KeyEvent)event.ev).getKeyCode(), event.type == Jake2InputEvent.KeyPress);
//		00480                         if (in_state && in_state->Key_Event_fp)
//		00481                                 in_state->Key_Event_fp (XLateKey(&event.xkey), event.type == KeyPress);
					break;

				case Jake2InputEvent.MotionNotify:
//		00485                         if (mouse_active) {
//		00486                                 if (dgamouse) {
//		00487                                         mx += (event.xmotion.x + win_x) * 2;
//		00488                                         my += (event.xmotion.y + win_y) * 2;
//		00489                                 } 
//		00490                                 else 
//		00491                                 {
//		00492                                         mx += ((int)event.xmotion.x - mwx) * 2;
//		00493                                         my += ((int)event.xmotion.y - mwy) * 2;
//		00494                                         mwx = event.xmotion.x;
//		00495                                         mwy = event.xmotion.y;
//		00496 
//		00497                                         if (mx || my)
//		00498                                                 dowarp = true;
//		00499                                 }
//		00500                         }
					break;


				case Jake2InputEvent.ButtonPress:
//		00505                         b=-1;
//		00506                         if (event.xbutton.button == 1)
//		00507                                 b = 0;
//		00508                         else if (event.xbutton.button == 2)
//		00509                                 b = 2;
//		00510                         else if (event.xbutton.button == 3)
//		00511                                 b = 1;
//		00512                         if (b>=0 && in_state && in_state->Key_Event_fp)
//		00513                                 in_state->Key_Event_fp (K_MOUSE1 + b, true);
					break;
 
				case Jake2InputEvent.ButtonRelease:
//		00517                         b=-1;
//		00518                         if (event.xbutton.button == 1)
//		00519                                 b = 0;
//		00520                         else if (event.xbutton.button == 2)
//		00521                                 b = 2;
//		00522                         else if (event.xbutton.button == 3)
//		00523                                 b = 1;
//		00524                         if (b>=0 && in_state && in_state->Key_Event_fp)
//		00525                                 in_state->Key_Event_fp (K_MOUSE1 + b, false);
					break;
 
				case Jake2InputEvent.CreateNotify :
//		00529                         win_x = event.xcreatewindow.x;
//		00530                         win_y = event.xcreatewindow.y;
					break;

				case Jake2InputEvent.ConfigureNotify :
//		00534                         win_x = event.xconfigure.x;
//		00535                         win_y = event.xconfigure.y;
					break;
			}
		}
//		00539            
//		00540         if (dowarp) {
//		00541                 /* move the mouse to the window center again */
//		00542                 XWarpPointer(dpy,None,win,0,0,0,0, vid.width/2,vid.height/2);
//		00543         }		
	}
	
	private static Jake2InputEvent nextEvent() {
		Jake2InputEvent ev;
		synchronized (eventQueue) {
			ev = (Jake2InputEvent)eventQueue.removeFirst();
		}
		return ev;
	}
	
	private static void addEvent(Jake2InputEvent ev) {
		synchronized (eventQueue) {
			eventQueue.addLast(ev);
		}
	}
	
	static void Do_Key_Event(int key, boolean down) {
		Key.Event(key, down, System.currentTimeMillis());
	}
	
	public void keyPressed(KeyEvent e) {
		addEvent(new Jake2InputEvent(Jake2InputEvent.KeyPress, e));
	}

	public void keyReleased(KeyEvent e) {
		addEvent(new Jake2InputEvent(Jake2InputEvent.KeyRelease, e));
	}

	public void keyTyped(KeyEvent e) {
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

}

class Jake2InputEvent {
	static final int KeyPress = 0;
	static final int KeyRelease = 1;
	static final int MotionNotify = 2;
	static final int ButtonPress = 3;
	static final int ButtonRelease = 4;
	static final int CreateNotify = 5;
	static final int ConfigureNotify = 6;
	int type;
	AWTEvent ev;
	
	Jake2InputEvent(int type, AWTEvent ev) {
		this.type = type;
		this.ev = ev;
	}
}
