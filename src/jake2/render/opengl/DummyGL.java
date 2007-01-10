package jake2.render.opengl;


import java.nio.*;

public class DummyGL implements QGL {
    
    private static QGL self = new DummyGL();
    
    private DummyGL() {
        // singleton
    }
    
    public static QGL getInstance() {
        return self;
    }
    
    public void glAlphaFunc(int func, float ref) {
        // do nothing
    }

    public void glBegin(int mode) {
        // do nothing
    }

    public void glBindTexture(int target, int texture) {
        // do nothing
    }

    public void glBlendFunc(int sfactor, int dfactor) {
        // do nothing
    }

    public void glClear(int mask) {
        // do nothing
    }

    public void glClearColor(float red, float green, float blue, float alpha) {
        // do nothing
    }

    public void glColor3f(float red, float green, float blue) {
        // do nothing
    }

    public void glColor3ub(byte red, byte green, byte blue) {
        // do nothing
    }

    public void glColor4f(float red, float green, float blue, float alpha) {
        // do nothing
    }

    public void glColor4ub(byte red, byte green, byte blue, byte alpha) {
        // do nothing
    }

    public void glColorPointer(int size, boolean unsigned, int stride,
            ByteBuffer pointer) {
        // do nothing
    }
    
    public void glColorPointer(int size, int stride, FloatBuffer pointer) {
        // do nothing
    }

    public void glCullFace(int mode) {
        // do nothing
    }

    public void glDeleteTextures(IntBuffer textures) {
        // do nothing
    }

    public void glDepthFunc(int func) {
        // do nothing
    }

    public void glDepthMask(boolean flag) {
        // do nothing
    }

    public void glDepthRange(double zNear, double zFar) {
        // do nothing
    }

    public void glDisable(int cap) {
        // do nothing
    }

    public void glDisableClientState(int cap) {
        // do nothing
    }

    public void glDrawArrays(int mode, int first, int count) {
        // do nothing
    }

    public void glDrawBuffer(int mode) {
        // do nothing
    }

    public void glDrawElements(int mode, IntBuffer indices) {
        // do nothing
    }

    public void glEnable(int cap) {
        // do nothing
    }

    public void glEnableClientState(int cap) {
        // do nothing
    }

    public void glEnd() {
        // do nothing
    }

    public void glFinish() {
        // do nothing
    }

    public void glFlush() {
        // do nothing
    }

    public void glFrustum(double left, double right, double bottom,
            double top, double zNear, double zFar) {
        // do nothing
    }

    public int glGetError() {
        return GL_NO_ERROR;
    }

    public void glGetFloat(int pname, FloatBuffer params) {
        // do nothing
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
        // do nothing
    }

    public void glInterleavedArrays(int format, int stride,
            FloatBuffer pointer) {
        // do nothing
    }

    public void glLoadIdentity() {
        // do nothing
    }

    public void glLoadMatrix(FloatBuffer m) {
        // do nothing
    }

    public void glMatrixMode(int mode) {
        // do nothing
    }

    public void glOrtho(double left, double right, double bottom,
            double top, double zNear, double zFar) {
        // do nothing
    }

    public void glPixelStorei(int pname, int param) {
        // do nothing
    }

    public void glPointSize(float size) {
        // do nothing
    }

    public void glPolygonMode(int face, int mode) {
        // do nothing
    }

    public void glPopMatrix() {
        // do nothing
    }

    public void glPushMatrix() {
        // do nothing
    }

    public void glReadPixels(int x, int y, int width, int height,
            int format, int type, ByteBuffer pixels) {
        // do nothing
    }

    public void glRotatef(float angle, float x, float y, float z) {
        // do nothing
    }

    public void glScalef(float x, float y, float z) {
        // do nothing
    }

    public void glScissor(int x, int y, int width, int height) {
        // do nothing
    }

    public void glShadeModel(int mode) {
        // do nothing
    }

    public void glTexCoord2f(float s, float t) {
        // do nothing
    }

    public void glTexCoordPointer(int size, int stride, FloatBuffer pointer) {
        // do nothing
    }

    public void glTexEnvi(int target, int pname, int param) {
        // do nothing
    }

    public void glTexImage2D(int target, int level, int internalformat,
            int width, int height, int border, int format, int type,
            ByteBuffer pixels) {
        // do nothing
    }

    public void glTexImage2D(int target, int level, int internalformat,
            int width, int height, int border, int format, int type,
            IntBuffer pixels) {
        // do nothing
    }

    public void glTexParameterf(int target, int pname, float param) {
        // do nothing
    }

    public void glTexParameteri(int target, int pname, int param) {
        // do nothing
    }

    public void glTexSubImage2D(int target, int level, int xoffset,
            int yoffset, int width, int height, int format, int type,
            IntBuffer pixels) {
        // do nothing
    }

    public void glTranslatef(float x, float y, float z) {
        // do nothing
    }

    public void glVertex2f(float x, float y) {
        // do nothing
    }

    public void glVertex3f(float x, float y, float z) {
        // do nothing
    }

    public void glVertexPointer(int size, int stride, FloatBuffer pointer) {
        // do nothing
    }

    public void glViewport(int x, int y, int width, int height) {
        // do nothing
    }

    public void glColorTable(int target, int internalFormat, int width,
            int format, int type, ByteBuffer data) {
        // do nothing
    }

    public void glActiveTextureARB(int texture) {
        // do nothing
    }

    public void glClientActiveTextureARB(int texture) {
        // do nothing
    }

    public void glPointParameterEXT(int pname, FloatBuffer pfParams) {
        // do nothing
    }

    public void glPointParameterfEXT(int pname, float param) {
        // do nothing
    }

    public void glLockArraysEXT(int first, int count) {
        // do nothing
    }

    public void glArrayElement(int index) {
        // do nothing
    }

    public void glUnlockArraysEXT() {
        // do nothing
    }

    public void glMultiTexCoord2f(int target, float s, float t) {
        // do nothing
    }

}
