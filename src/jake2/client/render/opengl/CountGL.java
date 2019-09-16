package jake2.client.render.opengl;


import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public class CountGL implements QGL {
    
    private static int count = 0;
    
    private static QGL self = new CountGL();
    
    private CountGL() {
        // singleton
    }
    
    public static QGL getInstance() {
        return self;
    }
    
    public void glAlphaFunc(int func, float ref) {
        ++count;
    }

    public void glBegin(int mode) {
        ++count;
    }

    public void glBindTexture(int target, int texture) {
        ++count;
    }

    public void glBlendFunc(int sfactor, int dfactor) {
        ++count;
    }

    public void glClear(int mask) {
        ++count;
    }

    public void glClearColor(float red, float green, float blue, float alpha) {
        ++count;
    }

    public void glColor3f(float red, float green, float blue) {
        ++count;
    }

    public void glColor3ub(byte red, byte green, byte blue) {
        ++count;
    }

    public void glColor4f(float red, float green, float blue, float alpha) {
        ++count;
    }

    public void glColor4ub(byte red, byte green, byte blue, byte alpha) {
        ++count;
    }

    public void glColorPointer(int size, boolean unsigned, int stride,
            ByteBuffer pointer) {
        ++count;
    }
    
    public void glColorPointer(int size, int stride, FloatBuffer pointer) {
        ++count;
    }

    public void glCullFace(int mode) {
        ++count;
    }

    public void glDeleteTextures(IntBuffer textures) {
        ++count;
    }

    public void glDepthFunc(int func) {
        ++count;
    }

    public void glDepthMask(boolean flag) {
        ++count;
    }

    public void glDepthRange(double zNear, double zFar) {
        ++count;
    }

    public void glDisable(int cap) {
        ++count;
    }

    public void glDisableClientState(int cap) {
        ++count;
    }

    public void glDrawArrays(int mode, int first, int count) {
        ++count;
    }

    public void glDrawBuffer(int mode) {
        ++count;
    }

    public void glDrawElements(int mode, IntBuffer indices) {
        ++count;
    }

    public void glEnable(int cap) {
        ++count;
    }

    public void glEnableClientState(int cap) {
        ++count;
    }

    public void glEnd() {
        ++count;
    }

    public void glFinish() {
        ++count;
    }

    public void glFlush() {
        System.err.println("GL calls/frame: " + (++count));
        count = 0;
    }

    public void glFrustum(double left, double right, double bottom,
            double top, double zNear, double zFar) {
        ++count;
    }

    public int glGetError() {
        return GL_NO_ERROR;
    }

    public void glGetFloat(int pname, FloatBuffer params) {
        ++count;
    }

    public String glGetString(int name) {
        switch (name) {
        case GL_EXTENSIONS:
            return "GL_ARB_multitexture";
        default:
            return "";
        }
    }

    public void glHint(int target, int mode) {
	++count;
    }

    public void glInterleavedArrays(int format, int stride,
            FloatBuffer pointer) {
        ++count;
    }

    public void glLoadIdentity() {
        ++count;
    }

    public void glLoadMatrix(FloatBuffer m) {
        ++count;
    }

    public void glMatrixMode(int mode) {
        ++count;
    }

    public void glOrtho(double left, double right, double bottom,
            double top, double zNear, double zFar) {
        ++count;
    }

    public void glPixelStorei(int pname, int param) {
        ++count;
    }

    public void glPointSize(float size) {
        ++count;
    }

    public void glPolygonMode(int face, int mode) {
        ++count;
    }

    public void glPopMatrix() {
        ++count;
    }

    public void glPushMatrix() {
        ++count;
    }

    public void glReadPixels(int x, int y, int width, int height,
            int format, int type, ByteBuffer pixels) {
        ++count;
    }

    public void glRotatef(float angle, float x, float y, float z) {
        ++count;
    }

    public void glScalef(float x, float y, float z) {
        ++count;
    }

    public void glScissor(int x, int y, int width, int height) {
        ++count;
    }

    public void glShadeModel(int mode) {
        ++count;
    }

    public void glTexCoord2f(float s, float t) {
        ++count;
    }

    public void glTexCoordPointer(int size, int stride, FloatBuffer pointer) {
        ++count;
    }

    public void glTexEnvi(int target, int pname, int param) {
        ++count;
    }

    public void glTexImage2D(int target, int level, int internalformat,
            int width, int height, int border, int format, int type,
            ByteBuffer pixels) {
        ++count;
    }

    public void glTexImage2D(int target, int level, int internalformat,
            int width, int height, int border, int format, int type,
            IntBuffer pixels) {
        ++count;
    }

    public void glTexParameterf(int target, int pname, float param) {
        ++count;
    }

    public void glTexParameteri(int target, int pname, int param) {
        ++count;
    }

    public void glTexSubImage2D(int target, int level, int xoffset,
            int yoffset, int width, int height, int format, int type,
            IntBuffer pixels) {
        ++count;
    }

    public void glTranslatef(float x, float y, float z) {
        ++count;
    }

    public void glVertex2f(float x, float y) {
        ++count;
    }

    public void glVertex3f(float x, float y, float z) {
        ++count;
    }

    public void glVertexPointer(int size, int stride, FloatBuffer pointer) {
        ++count;
    }

    public void glViewport(int x, int y, int width, int height) {
        ++count;
    }

    public void glColorTable(int target, int internalFormat, int width,
            int format, int type, ByteBuffer data) {
        ++count;
    }

    public void glActiveTextureARB(int texture) {
        ++count;
    }

    public void glClientActiveTextureARB(int texture) {
        ++count;
    }

    public void glPointParameterEXT(int pname, FloatBuffer pfParams) {
        ++count;
    }

    public void glPointParameterfEXT(int pname, float param) {
        ++count;
    }

    public void glLockArraysEXT(int first, int count) {
        ++count;
    }

    public void glArrayElement(int index) {
        ++count;
    }

    public void glUnlockArraysEXT() {
        ++count;
    }

    public void glMultiTexCoord2f(int target, float s, float t) {
        ++count;
    }

    /*
     * util extensions
     */
    public void setSwapInterval(int interval) {
	++count;
    }

}
