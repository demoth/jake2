/*
 * KBD.java
 * Copyright (C) 2004
 * 
 * $Id: KBD.java,v 1.7 2004-01-12 21:52:52 hoz Exp $
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

import jake2.client.Key;

import java.awt.*;
import java.awt.event.*;

/**
 * KBD
 */
public final class KBD {
	
	static int win_x = 0;
	static int win_y = 0;
	static int win_w2 = 0;
	static int win_h2 = 0;
		
	// motion values
	public static int mx = 0;
	public static int my = 0;
	
	static Robot robot;
	public static InputListener listener = new InputListener();
		
	static {
		try {
			robot = new Robot();
		} catch (AWTException e) {
			System.exit(1);
		}
	}
		
	public static void Init() {
	}

	public static void Update() {
		// get events
		HandleEvents();
	}

	public static void Close() {
	}
	
	static void HandleEvents() {
		int b;

		Jake2InputEvent event;
		while ( (event=InputListener.nextEvent()) != null ) {
			switch(event.type) {
				case Jake2InputEvent.KeyPress:
				case Jake2InputEvent.KeyRelease:
					Do_Key_Event(XLateKey((KeyEvent)event.ev), event.type == Jake2InputEvent.KeyPress);
					break;

				case Jake2InputEvent.MotionNotify:
//					if (IN.ignorefirst) {
//						IN.ignorefirst = false;
//						break;
//					}
					if (IN.mouse_active) {
						mx = (((MouseEvent)event.ev).getX() - win_w2) * 2;
						my = (((MouseEvent)event.ev).getY() - win_h2) * 2;
					}
					break;

				case Jake2InputEvent.ButtonPress:
					b=((MouseEvent)event.ev).getButton()-1;
					Do_Key_Event(Key.K_MOUSE1 + b, true);
					break;
 
				case Jake2InputEvent.ButtonRelease:
					b=((MouseEvent)event.ev).getButton()-1;
					Do_Key_Event(Key.K_MOUSE1 + b, false);
					break;
 
				case Jake2InputEvent.CreateNotify :
				case Jake2InputEvent.ConfigureNotify :
					Component c = ((ComponentEvent)event.ev).getComponent();
					win_x = 0;
					win_y = 0;
					win_w2 = c.getWidth() / 2;
					win_h2 = c.getHeight() / 2;
					while (c != null) {
						if (c instanceof Container) {
							Insets insets = ((Container)c).getInsets();
							win_x += insets.left;
							win_y += insets.top;
						}
						win_x += c.getX();
						win_y += c.getY();
						c = c.getParent();
					}
					break;
			}
		}
            
		if (mx != 0 || my != 0) {
			// move the mouse to the window center again
			robot.mouseMove(win_x + win_w2, win_y + win_h2);
//		00542                 XWarpPointer(dpy,None,win,0,0,0,0, vid.width/2,vid.height/2);
		}		
	}

	private static int XLateKey(KeyEvent ev) {
 
		int key = 0;
		int code = ev.getKeyCode();

		switch(code) {
//	00626                 case XK_KP_Page_Up:      key = K_KP_PGUP; break;
			case KeyEvent.VK_PAGE_UP: key = Key.K_PGUP; break;
 
//	00629                 case XK_KP_Page_Down: key = K_KP_PGDN; break;
			case KeyEvent.VK_PAGE_DOWN: key = Key.K_PGDN; break;

//	00632                 case XK_KP_Home: key = K_KP_HOME; break;
			case KeyEvent.VK_HOME: key = Key.K_HOME; break;

//	00635                 case XK_KP_End:  key = K_KP_END; break;
			case KeyEvent.VK_END: key = Key.K_END; break;
 
			case KeyEvent.VK_KP_LEFT: key = Key.K_KP_LEFTARROW; break;
			case KeyEvent.VK_LEFT: key = Key.K_LEFTARROW; break;
 
			case KeyEvent.VK_KP_RIGHT: key = Key.K_KP_RIGHTARROW; break;
			case KeyEvent.VK_RIGHT: key = Key.K_RIGHTARROW; break;

			case KeyEvent.VK_KP_DOWN: key = Key.K_KP_DOWNARROW; break;
			case KeyEvent.VK_DOWN: key = Key.K_DOWNARROW; break;

			case KeyEvent.VK_KP_UP: key = Key.K_KP_UPARROW; break;
			case KeyEvent.VK_UP: key = Key.K_UPARROW; break; 

			case KeyEvent.VK_ESCAPE: key = Key.K_ESCAPE; break; 

			
			case KeyEvent.VK_ENTER: key = Key.K_ENTER; break; 
//	00652                 case XK_KP_Enter: key = K_KP_ENTER;     break;

			case KeyEvent.VK_TAB: key = Key.K_TAB; break; 

			case KeyEvent.VK_F1: key = Key.K_F1; break;
			case KeyEvent.VK_F2: key = Key.K_F2; break;
			case KeyEvent.VK_F3: key = Key.K_F3; break;
			case KeyEvent.VK_F4: key = Key.K_F4; break;
			case KeyEvent.VK_F5: key = Key.K_F5; break;
			case KeyEvent.VK_F6: key = Key.K_F6; break;
			case KeyEvent.VK_F7: key = Key.K_F7; break;
			case KeyEvent.VK_F8: key = Key.K_F8; break;
			case KeyEvent.VK_F9: key = Key.K_F9; break;
			case KeyEvent.VK_F10: key = Key.K_F10; break;
			case KeyEvent.VK_F11: key = Key.K_F11; break;
			case KeyEvent.VK_F12: key = Key.K_F12; break; 

			case KeyEvent.VK_BACK_SPACE: key = Key.K_BACKSPACE; break; 

			case KeyEvent.VK_DELETE: key = Key.K_DEL; break; 
//	00683                 case XK_KP_Delete: key = K_KP_DEL; break;

			case KeyEvent.VK_PAUSE: key = Key.K_PAUSE; break; 
	
			case KeyEvent.VK_SHIFT: key = Key.K_SHIFT; break; 
			case KeyEvent.VK_CONTROL: key = Key.K_CTRL; break; 
			
			case KeyEvent.VK_ALT:
			case KeyEvent.VK_ALT_GRAPH: key = Key.K_ALT; break;
 
//	00700                 case XK_KP_Begin: key = K_KP_5; break;
//	00701
			case KeyEvent.VK_INSERT: key = Key.K_INS; break;

			case KeyEvent.VK_MULTIPLY: key = '*'; break;
			case KeyEvent.VK_PLUS: key = Key.K_KP_PLUS; break;
			case KeyEvent.VK_MINUS: key = Key.K_KP_MINUS; break;
			case KeyEvent.VK_DIVIDE: key = Key.K_KP_SLASH; break;
 
			default:
				key = ev.getKeyChar();
				if (key >= 'A' && key <= 'Z')
					key = key - 'A' + 'a';
			break;
		} 
		return key;
	}	
		
	static void Do_Key_Event(int key, boolean down) {
		// TODO remove hard wired mouse toggle
		if (down && key == 't') IN.toggleMouse();
		Key.Event(key, down, System.currentTimeMillis());
	}
	
	static void centerMouse() {
		robot.mouseMove(win_x + win_w2, win_y + win_h2);
	}
	
}

