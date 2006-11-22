package jake2.render.opengl;

import jake2.qcommon.xcommand_t;

import java.awt.Dimension;

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

    void updateScreen(xcommand_t callback);

    void screenshot();
    
}
