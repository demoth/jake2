package jake2.client.render;

import jake2.client.refdef_t;
import jake2.client.render.opengl.GLDriver;

import java.awt.*;

public interface RenderAPI {

    void setGLDriver(GLDriver impl);

    boolean R_Init(int vid_xpos, int vid_ypos);

    boolean R_Init2();

    void R_Shutdown();

    void R_BeginRegistration(String map);

    model_t R_RegisterModel(String name);

    image_t R_RegisterSkin(String name);

    image_t Draw_FindPic(String name);

    void R_SetSky(String name, float rotate, float[] axis);

    void R_EndRegistration();

    void R_RenderFrame(refdef_t fd);

    void Draw_GetPicSize(Dimension dim, String name);

    void Draw_Pic(int x, int y, String name);

    void Draw_StretchPic(int x, int y, int w, int h, String name);

    /**
     * Draw_Char
     * Draws one 8*8 graphics character with 0 being transparent.
     * It can be clipped to the top of the screen to allow the console to be smoothly scrolled off.
     * @param num - 8-bit ASCII, which is also the index of the character in the 'conchars.pcx' (16x16 grid)
     */
    void Draw_Char(int x, int y, int num);

    void Draw_TileClear(int x, int y, int w, int h, String name);

    void Draw_Fill(int x, int y, int w, int h, int c);

    void Draw_FadeScreen();

    void Draw_StretchRaw(int x, int y, int w, int h, int cols, int rows,
            byte[] data);

    void R_SetPalette(byte[] palette);

    void R_BeginFrame(float camera_separation);

    void GL_ScreenShot_f();
}
