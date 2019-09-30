package jake2.client.render.opengl;

import jake2.qcommon.exec.Command;

import java.awt.*;

public interface GLDriver {
    
    boolean init(int xpos, int ypos);
    
    int setMode(Dimension dim, int mode, boolean fullscreen);
    
    void shutdown();
    
    void beginFrame(float camera_separation);
    
    void endFrame();

    void appActivate(boolean activate);
    
    void enableLogging(boolean enable);
    
    void logNewFrame();
    
    java.awt.DisplayMode[] getModeList();

    void updateScreen(Command callback);

    void screenshot();
    
}
