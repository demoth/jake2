package jake2.render.opengl;



import java.nio.*;

import org.lwjgl.util.GL;

public class LwjglGL implements QGL {
    
    LwjglGL() {
        // singleton
    }
    
    public final void glAlphaFunc(int func, float ref) {
        GL.glAlphaFunc(func, ref);
    }

    public final void glBegin(int mode) {
        GL.glBegin(mode);
    }

    public final void glBindTexture(int target, int texture) {
        GL.glBindTexture(target, texture);
    }

    public final void glBlendFunc(int sfactor, int dfactor) {
        GL.glBlendFunc(sfactor, dfactor);
    }

    public final void glClear(int mask) {
        GL.glClear(mask);
    }

    public final void glClearColor(float red, float green, float blue, float alpha) {
        GL.glClearColor(red, green, blue, alpha);
    }

    public final void glColor3f(float red, float green, float blue) {
        GL.glColor3f(red, green, blue);
    }

    public final void glColor3ub(byte red, byte green, byte blue) {
        GL.glColor3ub(red, green, blue);
    }

    public final void glColor4f(float red, float green, float blue, float alpha) {
        GL.glColor4f(red, green, blue, alpha);
    }

    public final void glColor4ub(byte red, byte green, byte blue, byte alpha) {
        GL.glColor4ub(red, green, blue, alpha);
    }

    public final void glColorPointer(int size, boolean unsigned, int stride,
            ByteBuffer pointer) {
        GL.glColorPointer(size, unsigned, stride, pointer);
    }
    
    public final void glColorPointer(int size, int stride, FloatBuffer pointer) {
        GL.glColorPointer(size, stride, pointer);
    }

    public final void glCullFace(int mode) {
        GL.glCullFace(mode);
    }

    public final void glDeleteTextures(IntBuffer textures) {
        GL.glDeleteTextures(textures);
    }

    public final void glDepthFunc(int func) {
        GL.glDepthFunc(func);
    }

    public final void glDepthMask(boolean flag) {
        GL.glDepthMask(flag);
    }

    public final void glDepthRange(double zNear, double zFar) {
        GL.glDepthRange(zNear, zFar);
    }

    public final void glDisable(int cap) {
        GL.glDisable(cap);
    }

    public final void glDisableClientState(int cap) {
        GL.glDisableClientState(cap);
    }

    public final void glDrawArrays(int mode, int first, int count) {
        GL.glDrawArrays(mode, first, count);
    }

    public final void glDrawBuffer(int mode) {
        GL.glDrawBuffer(mode);
    }

    public final void glDrawElements(int mode, IntBuffer indices) {
        GL.glDrawElements(mode, indices);
    }

    public final void glEnable(int cap) {
        GL.glEnable(cap);
    }

    public final void glEnableClientState(int cap) {
        GL.glEnableClientState(cap);
    }

    public final void glEnd() {
        GL.glEnd();
    }

    public final void glFinish() {
        GL.glFinish();
    }

    public final void glFlush() {
        GL.glFlush();
    }

    public final void glFrustum(double left, double right, double bottom,
            double top, double zNear, double zFar) {
        GL.glFrustum(left, right, bottom, top, zNear, zFar);
    }

    public final int glGetError() {
        return GL.glGetError();
    }

    public final void glGetFloat(int pname, FloatBuffer params) {
        GL.glGetFloat(pname, params);
    }

    public final String glGetString(int name) {
        return GL.glGetString(name);
    }

    public final void glInterleavedArrays(int format, int stride,
            FloatBuffer pointer) {
        GL.glInterleavedArrays(format, stride, pointer);
    }

    public final void glLoadIdentity() {
        GL.glLoadIdentity();
    }

    public final void glLoadMatrix(FloatBuffer m) {
        GL.glLoadMatrix(m);
    }

    public final void glMatrixMode(int mode) {
        GL.glMatrixMode(mode);
    }

    public final void glOrtho(double left, double right, double bottom,
            double top, double zNear, double zFar) {
        GL.glOrtho(left, right, bottom, top, zNear, zFar);
    }

    public final void glPixelStorei(int pname, int param) {
        GL.glPixelStorei(pname, param);
    }

    public final void glPointSize(float size) {
        GL.glPointSize(size);
    }

    public final void glPolygonMode(int face, int mode) {
        GL.glPolygonMode(face, mode);
    }

    public final void glPopMatrix() {
        GL.glPopMatrix();
    }

    public final void glPushMatrix() {
        GL.glPushMatrix();
    }

    public final void glReadPixels(int x, int y, int width, int height,
            int format, int type, ByteBuffer pixels) {
        GL.glReadPixels(x, y, width, height, format, type, pixels);
    }

    public final void glRotatef(float angle, float x, float y, float z) {
        GL.glRotatef(angle, x, y, z);
    }

    public final void glScalef(float x, float y, float z) {
        GL.glScalef(x, y, z);
    }

    public final void glScissor(int x, int y, int width, int height) {
        GL.glScissor(x, y, width, height);
    }

    public final void glShadeModel(int mode) {
        GL.glShadeModel(mode);
    }

    public final void glTexCoord2f(float s, float t) {
        GL.glTexCoord2f(s, t);
    }

    public final void glTexCoordPointer(int size, int stride, FloatBuffer pointer) {
        GL.glTexCoordPointer(size, stride, pointer);
    }

    public final void glTexEnvi(int target, int pname, int param) {
        GL.glTexEnvi(target, pname, param);
    }

    public final void glTexImage2D(int target, int level, int internalformat,
            int width, int height, int border, int format, int type,
            ByteBuffer pixels) {
        GL.glTexImage2D(target, level, internalformat, width, height, border,
                format, type, pixels);
    }

    public final void glTexImage2D(int target, int level, int internalformat,
            int width, int height, int border, int format, int type,
            IntBuffer pixels) {
        GL.glTexImage2D(target, level, internalformat, width, height, border,
                format, type, pixels);
    }

    public final void glTexParameterf(int target, int pname, float param) {
        GL.glTexParameterf(target, pname, param);
    }

    public final void glTexParameteri(int target, int pname, int param) {
        GL.glTexParameteri(target, pname, param);
    }

    public final void glTexSubImage2D(int target, int level, int xoffset,
            int yoffset, int width, int height, int format, int type,
            IntBuffer pixels) {
        GL.glTexSubImage2D(target, level, xoffset, yoffset, width, height,
                format, type, pixels);
    }

    public final void glTranslatef(float x, float y, float z) {
        GL.glTranslatef(x, y, z);
    }

    public final void glVertex2f(float x, float y) {
        GL.glVertex2f(x, y);
    }

    public final void glVertex3f(float x, float y, float z) {
        GL.glVertex3f(x, y, z);
    }

    public final void glVertexPointer(int size, int stride, FloatBuffer pointer) {
        GL.glVertexPointer(size, stride, pointer);
    }

    public final void glViewport(int x, int y, int width, int height) {
        GL.glViewport(x, y, width, height);
    }

    public final void glColorTable(int target, int internalFormat, int width,
            int format, int type, ByteBuffer data) {
        GL.glColorTable(target, internalFormat, width, format, type, data);
    }

    public final void glActiveTextureARB(int texture) {
        GL.glActiveTextureARB(texture);
    }

    public final void glClientActiveTextureARB(int texture) {
        GL.glClientActiveTextureARB(texture);
    }

    public final void glPointParameterEXT(int pname, FloatBuffer pfParams) {
        GL.glPointParameterEXT(pname, pfParams);
    }

    public final void glPointParameterfEXT(int pname, float param) {
        GL.glPointParameterfEXT(pname, param);
    }

    public final void glLockArraysEXT(int first, int count) {
        GL.glLockArraysEXT(first, count);
    }

    public final void glArrayElement(int index) {
        GL.glArrayElement(index);
    }

    public final void glUnlockArraysEXT() {
        GL.glUnlockArraysEXT();
    }

    public final void glMultiTexCoord2f(int target, float s, float t) {
        GL.glMultiTexCoord2f(target, s, t);
    }

}
