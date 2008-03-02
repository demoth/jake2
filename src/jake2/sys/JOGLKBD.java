package jake2.sys;

import jake2.Globals;
import jake2.client.Key;

import java.awt.*;
import java.awt.event.*;

import javax.swing.ImageIcon;

final public class JOGLKBD extends KBD
{
	static Robot robot;
	public static InputListener listener = new InputListener();
	static Cursor emptyCursor = null;
	static Component c = null;
	
	static int win_w2 = 0;
	static int win_h2 = 0;
	
	static {
		try {
			robot = new Robot();
		} catch (AWTException e) {
                    if (!Globals.appletMode) {
			System.exit(1);
                    }
		}
	}
		
	public void Init() {
	}

        // Used only for the applet case
        public static void Init(Component component) {
            c = component;
            handleCreateAndConfigureNotify(component);
        }

	public void Update() {
		// get events
		HandleEvents();
	}

	public void Close() {
	}
	
	private void HandleEvents() 
	{
		int key;

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
					} else {
						mx = 0;
						my = 0;
					}
					break;
				// see java.awt.MouseEvent
				case Jake2InputEvent.ButtonPress:
					key = mouseEventToKey((MouseEvent)event.ev); 
					Do_Key_Event(key, true);
					break;
 
				case Jake2InputEvent.ButtonRelease:
					key = mouseEventToKey((MouseEvent)event.ev); 
					Do_Key_Event(key, false);
					break;
					
				case Jake2InputEvent.WheelMoved:
					int dir = ((MouseWheelEvent)event.ev).getWheelRotation();
					if (dir > 0) {
						Do_Key_Event(Key.K_MWHEELDOWN, true);
						Do_Key_Event(Key.K_MWHEELDOWN, false);
					} else {
						Do_Key_Event(Key.K_MWHEELUP, true);
						Do_Key_Event(Key.K_MWHEELUP, false);					    
					}
					break;
					 
				case Jake2InputEvent.CreateNotify :
				case Jake2InputEvent.ConfigureNotify :
                                        handleCreateAndConfigureNotify(((ComponentEvent)event.ev).getComponent());
					break;
			}
		}
            
		if (mx != 0 || my != 0) {
			// move the mouse to the window center again
			robot.mouseMove(win_x + win_w2, win_y + win_h2);
		}		
	}

        private static void handleCreateAndConfigureNotify(Component component) {
            // Probably could unify this code better, but for now just
            // leave the two code paths separate
            if (!Globals.appletMode) {
                win_x = 0;
                win_y = 0;
                win_w2 = component.getWidth() / 2;
                win_h2 = component.getHeight() / 2;
                int left = 0; int top = 0;
                while (component != null) {
                    if (component instanceof Container) {
                        Insets insets = ((Container)component).getInsets();
                        left += insets.left;
                        top += insets.top;
                    }
                    win_x += component.getX();
                    win_y += component.getY();
                    component = component.getParent();
                }
                win_x += left; win_y += top;
                win_w2 -= left / 2; win_h2 -= top / 2;
            } else {
                win_x = 0;
                win_y = 0;
                win_w2 = component.getWidth() / 2;
                win_h2 = component.getHeight() / 2;
                Point p = component.getLocationOnScreen();
                win_x = p.x;
                win_y = p.y;
            }
        }

	// strange button numbering in java.awt.MouseEvent
	// BUTTON1(left) BUTTON2(center) BUTTON3(right)
	// K_MOUSE1      K_MOUSE3        K_MOUSE2
	private final int mouseEventToKey(MouseEvent ev) {
	    switch (ev.getButton()) {
	    case MouseEvent.BUTTON3:
	        return Key.K_MOUSE2;
	    case MouseEvent.BUTTON2:
	        return Key.K_MOUSE3;
	    default:
	        return Key.K_MOUSE1;
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
			// toggle console for DE and US keyboards
			case KeyEvent.VK_DEAD_ACUTE:
			case KeyEvent.VK_CIRCUMFLEX:
			case KeyEvent.VK_DEAD_CIRCUMFLEX: key = '`'; break;
			 
			default:
				key = ev.getKeyChar();

				if (key >= 'A' && key <= 'Z')
					key = key - 'A' + 'a';
			break;
		}
		if (key > 255) key = 0;

		return key;
	}	
		
	public void Do_Key_Event(int key, boolean down) {
		Key.Event(key, down, Timer.Milliseconds());
	}
	
	public void centerMouse() {
		robot.mouseMove(win_x + win_w2, win_y + win_h2);
	}
	
	public void installGrabs()
	{
		if (emptyCursor == null) {
			ImageIcon emptyIcon = new ImageIcon(new byte[0]);
			emptyCursor = c.getToolkit().createCustomCursor(emptyIcon.getImage(), new Point(0, 0), "emptyCursor");
		}
		c.setCursor(emptyCursor);
		centerMouse();
	}
	
	public void uninstallGrabs()
	{
		c.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
	}
}
