package jake2.client;

import jake2.qcommon.Globals;
import jake2.qcommon.exec.Cmd;
import jake2.qcommon.sys.Timer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWScrollCallback;

import static jake2.client.render.Base.window;

/**
 * @author dsanders
 */
public class LWJGLKBD extends KBD {

    private char[] lwjglKeycodeMap = new char[512];
    private int pressed[] = null;


    public void Init() {
        try {
            // Create a new window
            if (window == 0) {
                throw new RuntimeException("Failed to create the GLFW window");
            }

            // Set the current context to the new window
            GLFW.glfwMakeContextCurrent(window);

            // Create keyboard and mouse objects
            GLFWKeyCallback keyCallback = new GLFWKeyCallback() {
                @Override
                public void invoke(long window, int key, int scancode, int action, int mods) {
                    // Handle key events
                }
            };
            GLFW.glfwSetKeyCallback(window, keyCallback);

            if (pressed == null) pressed = new int[512];

            lastRepeat = Timer.Milliseconds();
        } catch (Exception e) {
            // Handle exception
        }
    }

    public void Update() {
        // get events
        HandleEvents();
    }

    public void Close() {
        GLFW.glfwDestroyWindow(window);
        GLFW.glfwTerminate();
        // free the memory for GC
        lwjglKeycodeMap = null;
        pressed = null;
    }

    private void HandleEvents() {
        GLFW.glfwPollEvents();
        long window = GLFW.glfwGetCurrentContext();
        if (GLFW.glfwWindowShouldClose(window)) {
            Cmd.ExecuteString("quit");
        }

        for (int key = GLFW.GLFW_KEY_SPACE; key <= GLFW.GLFW_KEY_LAST; key++) {
            char ch = (char) key;
            boolean down = GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;

            if (down) {
                lwjglKeycodeMap[key] = ch;
                pressed[key] = Globals.sys_frame_time;
            } else {
                pressed[key] = 0;
            }

            Do_Key_Event(XLateKey(key, ch), down);
        }

        if (IN.mouse_active) {
            double[] xPos = new double[1];
            double[] yPos = new double[1];
            GLFW.glfwGetCursorPos(window, xPos, yPos);
            mx = (int) xPos[0] - mx;
            my = (int) yPos[0] - my;
            mx = (int) xPos[0];
            my = (int) yPos[0];
        } else {
            mx = 0;
            my = 0;
        }

        for (int button = GLFW.GLFW_MOUSE_BUTTON_1; button <= GLFW.GLFW_MOUSE_BUTTON_LAST; button++) {
            boolean down = GLFW.glfwGetMouseButton(window, button) == GLFW.GLFW_PRESS;
            if (button >= 0) {
                Do_Key_Event(Key.K_MOUSE1 + button, down);
            }
        }

        GLFW.glfwSetScrollCallback(window, new GLFWScrollCallback() {
            @Override
            public void invoke(long window, double xOffset, double yOffset) {
                if (yOffset > 0) {
                    Do_Key_Event(Key.K_MWHEELUP, true);
                    Do_Key_Event(Key.K_MWHEELUP, false);
                } else if (yOffset < 0) {
                    Do_Key_Event(Key.K_MWHEELDOWN, true);
                    Do_Key_Event(Key.K_MWHEELDOWN, false);
                }
            }
        });
    }

    private static int lastRepeat;

    private void generateRepeats() {
        int time = Globals.sys_frame_time;
        if (time - lastRepeat > 50) {
            for (int i = 0; i < pressed.length; i++) {
                if (pressed[i] > 0 && time - pressed[i] > 500)
                    Do_Key_Event(XLateKey(i, lwjglKeycodeMap[i]), true);
            }
            lastRepeat = time;
        }
    }

    private int XLateKey(int code, int ch) {
        int key = 0;

        switch (code) {
            case GLFW.GLFW_KEY_PAGE_UP:
                key = Key.K_PGUP;
                break;

            case GLFW.GLFW_KEY_PAGE_DOWN:
                key = Key.K_PGDN;
                break;

            case GLFW.GLFW_KEY_HOME:
                key = Key.K_HOME;
                break;

            case GLFW.GLFW_KEY_END:
                key = Key.K_END;
                break;

            case GLFW.GLFW_KEY_LEFT:
                key = Key.K_LEFTARROW;
                break;

            case GLFW.GLFW_KEY_RIGHT:
                key = Key.K_RIGHTARROW;
                break;

            case GLFW.GLFW_KEY_DOWN:
                key = Key.K_DOWNARROW;
                break;

            case GLFW.GLFW_KEY_UP:
                key = Key.K_UPARROW;
                break;

            case GLFW.GLFW_KEY_ESCAPE:
                key = Key.K_ESCAPE;
                break;

            case GLFW.GLFW_KEY_ENTER:
                key = Key.K_ENTER;
                break;

            case GLFW.GLFW_KEY_TAB:
                key = Key.K_TAB;
                break;

            case GLFW.GLFW_KEY_F1:
                key = Key.K_F1;
                break;

            case GLFW.GLFW_KEY_F2:
                key = Key.K_F2;
                break;

            case GLFW.GLFW_KEY_F3:
                key = Key.K_F3;
                break;

            case GLFW.GLFW_KEY_F4:
                key = Key.K_F4;
                break;

            case GLFW.GLFW_KEY_F5:
                key = Key.K_F5;
                break;

            case GLFW.GLFW_KEY_F6:
                key = Key.K_F6;
                break;

            case GLFW.GLFW_KEY_F7:
                key = Key.K_F7;
                break;

            case GLFW.GLFW_KEY_F8:
                key = Key.K_F8;
                break;

            case GLFW.GLFW_KEY_F9:
                key = Key.K_F9;
                break;

            case GLFW.GLFW_KEY_F10:
                key = Key.K_F10;
                break;

            case GLFW.GLFW_KEY_F11:
                key = Key.K_F11;
                break;

            case GLFW.GLFW_KEY_F12:
                key = Key.K_F12;
                break;

            case GLFW.GLFW_KEY_BACKSPACE:
                key = Key.K_BACKSPACE;
                break;

            case GLFW.GLFW_KEY_DELETE:
                key = Key.K_DEL;
                break;

            case GLFW.GLFW_KEY_PAUSE:
                key = Key.K_PAUSE;
                break;

            case GLFW.GLFW_KEY_LEFT_SHIFT:
            case GLFW.GLFW_KEY_RIGHT_SHIFT:
                key = Key.K_SHIFT;
                break;

            case GLFW.GLFW_KEY_LEFT_CONTROL:
            case GLFW.GLFW_KEY_RIGHT_CONTROL:
                key = Key.K_CTRL;
                break;

            case GLFW.GLFW_KEY_LEFT_ALT:
            case GLFW.GLFW_KEY_RIGHT_ALT:
                key = Key.K_ALT;
                break;

            default:
                key = lwjglKeycodeMap[code];
                if (key >= 'A' && key <= 'Z') {
                    key = key - 'A' + 'a';
                }
                break;
        }

        if (key > 255) {
            key = 0;
        }

        return key;
    }

    public void Do_Key_Event(int key, boolean down) {
        Key.Event(key, down, Timer.Milliseconds());
    }

    public void installGrabs() {
        long window = GLFW.glfwGetCurrentContext();
        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
    }

    public void uninstallGrabs() {
        long window = GLFW.glfwGetCurrentContext();
        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
    }
}
