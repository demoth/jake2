package jake2.render.opengl;


import java.nio.*;

import net.java.games.jogl.GL;

public class JoglGL implements QGL {
    
    private GL jogl;
    
    JoglGL() {
        // singleton
    }
    
    void setGL(GL context) {
        this.jogl = context;
    }
    
    public void glAlphaFunc(int func, float ref) {
        jogl.glAlphaFunc(func, ref);
    }

    public void glBegin(int mode) {
        jogl.glBegin(mode);
    }

    public void glBindTexture(int target, int texture) {
        jogl.glBindTexture(target, texture);
    }

    public void glBlendFunc(int sfactor, int dfactor) {
        jogl.glBlendFunc(sfactor, dfactor);
    }

    public void glClear(int mask) {
        jogl.glClear(mask);
    }

    public void glClearColor(float red, float green, float blue, float alpha) {
        jogl.glClearColor(red, green, blue, alpha);
    }

    public void glColor3f(float red, float green, float blue) {
        jogl.glColor3f(red, green, blue);
    }

    public void glColor3ub(byte red, byte green, byte blue) {
        jogl.glColor3ub(red, green, blue);
    }

    public void glColor4f(float red, float green, float blue, float alpha) {
        jogl.glColor4f(red, green, blue, alpha);
    }

    public void glColor4ub(byte red, byte green, byte blue, byte alpha) {
        jogl.glColor4ub(red, green, blue, alpha);
    }

    public void glColorPointer(int size, boolean unsigned, int stride,
            ByteBuffer pointer) {
        jogl.glColorPointer(size, GL_UNSIGNED_BYTE, stride, pointer);
    }
    
    public void glColorPointer(int size, int stride, FloatBuffer pointer) {
        jogl.glColorPointer(size, GL_FLOAT, stride, pointer);
    }

    public void glCullFace(int mode) {
        jogl.glCullFace(mode);
    }

    public void glDeleteTextures(IntBuffer textures) {
        jogl.glDeleteTextures(textures.limit(), textures);
    }

    public void glDepthFunc(int func) {
        jogl.glDepthFunc(func);
    }

    public void glDepthMask(boolean flag) {
        jogl.glDepthMask(flag);
    }

    public void glDepthRange(double zNear, double zFar) {
        jogl.glDepthRange(zNear, zFar);
    }

    public void glDisable(int cap) {
        jogl.glDisable(cap);
    }

    public void glDisableClientState(int cap) {
        jogl.glDisableClientState(cap);
    }

    public void glDrawArrays(int mode, int first, int count) {
        jogl.glDrawArrays(mode, first, count);
    }

    public void glDrawBuffer(int mode) {
        jogl.glDrawBuffer(mode);
    }

    public void glDrawElements(int mode, IntBuffer indices) {
        jogl.glDrawElements(mode, indices.limit(), GL_UNSIGNED_INT, indices);
    }

    public void glEnable(int cap) {
        jogl.glEnable(cap);
    }

    public void glEnableClientState(int cap) {
        jogl.glEnableClientState(cap);
    }

    public void glEnd() {
        jogl.glEnd();
    }

    public void glFinish() {
        jogl.glFinish();
    }

    public void glFlush() {
        jogl.glFlush();
    }

    public void glFrustum(double left, double right, double bottom,
            double top, double zNear, double zFar) {
        jogl.glFrustum(left, right, bottom, top, zNear, zFar);
    }

    public int glGetError() {
        return jogl.glGetError();
    }

    public void glGetFloat(int pname, FloatBuffer params) {
        jogl.glGetFloatv(pname, params);
    }

    public String glGetString(int name) {
        return jogl.glGetString(name);
    }

    public void glHint(int target, int mode) {
	jogl.glHint(target, mode);
    }

    public void glInterleavedArrays(int format, int stride,
            FloatBuffer pointer) {
        jogl.glInterleavedArrays(format, stride, pointer);
    }

    public void glLoadIdentity() {
        jogl.glLoadIdentity();
    }

    public void glLoadMatrix(FloatBuffer m) {
        jogl.glLoadMatrixf(m);
    }

    public void glMatrixMode(int mode) {
        jogl.glMatrixMode(mode);
    }

    public void glOrtho(double left, double right, double bottom,
            double top, double zNear, double zFar) {
        jogl.glOrtho(left, right, bottom, top, zNear, zFar);
    }

    public void glPixelStorei(int pname, int param) {
        jogl.glPixelStorei(pname, param);
    }

    public void glPointSize(float size) {
        jogl.glPointSize(size);
    }

    public void glPolygonMode(int face, int mode) {
        jogl.glPolygonMode(face, mode);
    }

    public void glPopMatrix() {
        jogl.glPopMatrix();
    }

    public void glPushMatrix() {
        jogl.glPushMatrix();
    }

    public void glReadPixels(int x, int y, int width, int height,
            int format, int type, ByteBuffer pixels) {
        jogl.glReadPixels(x, y, width, height, format, type, pixels);
    }

    public void glRotatef(float angle, float x, float y, float z) {
        jogl.glRotatef(angle, x, y, z);
    }

    public void glScalef(float x, float y, float z) {
        jogl.glScalef(x, y, z);
    }

    public void glScissor(int x, int y, int width, int height) {
        jogl.glScissor(x, y, width, height);
    }

    public void glShadeModel(int mode) {
        jogl.glShadeModel(mode);
    }

    public void glTexCoord2f(float s, float t) {
        jogl.glTexCoord2f(s, t);
    }

    public void glTexCoordPointer(int size, int stride, FloatBuffer pointer) {
        jogl.glTexCoordPointer(size, GL_FLOAT, stride, pointer);
    }

    public void glTexEnvi(int target, int pname, int param) {
        jogl.glTexEnvi(target, pname, param);
    }

    public void glTexImage2D(int target, int level, int internalformat,
            int width, int height, int border, int format, int type,
            ByteBuffer pixels) {
        jogl.glTexImage2D(target, level, internalformat, width, height, border,
                format, type, pixels);
    }

    public void glTexImage2D(int target, int level, int internalformat,
            int width, int height, int border, int format, int type,
            IntBuffer pixels) {
        jogl.glTexImage2D(target, level, internalformat, width, height, border,
                format, type, pixels);
    }

    public void glTexParameterf(int target, int pname, float param) {
        jogl.glTexParameterf(target, pname, param);
    }

    public void glTexParameteri(int target, int pname, int param) {
        jogl.glTexParameteri(target, pname, param);
    }

    public void glTexSubImage2D(int target, int level, int xoffset,
            int yoffset, int width, int height, int format, int type,
            IntBuffer pixels) {
        jogl.glTexSubImage2D(target, level, xoffset, yoffset, width, height,
                format, type, pixels);
    }

    public void glTranslatef(float x, float y, float z) {
        jogl.glTranslatef(x, y, z);
    }

    public void glVertex2f(float x, float y) {
        jogl.glVertex2f(x, y);
    }

    public void glVertex3f(float x, float y, float z) {
        jogl.glVertex3f(x, y, z);
    }

    public void glVertexPointer(int size, int stride, FloatBuffer pointer) {
        jogl.glVertexPointer(size, GL_FLOAT, stride, pointer);
    }

    public void glViewport(int x, int y, int width, int height) {
        jogl.glViewport(x, y, width, height);
    }

    public void glColorTable(int target, int internalFormat, int width,
            int format, int type, ByteBuffer data) {
        jogl.glColorTable(target, internalFormat, width, format, type, data);
    }

    public void glActiveTextureARB(int texture) {
        jogl.glActiveTextureARB(texture);
    }

    public void glClientActiveTextureARB(int texture) {
        jogl.glClientActiveTextureARB(texture);
    }

    public void glPointParameterEXT(int pname, FloatBuffer pfParams) {
        jogl.glPointParameterfvEXT(pname, pfParams);
    }

    public void glPointParameterfEXT(int pname, float param) {
        jogl.glPointParameterfEXT(pname, param);
    }
    public void glLockArraysEXT(int first, int count) {
        jogl.glLockArraysEXT(first, count);
    }

    public void glArrayElement(int index) {
        jogl.glArrayElement(index);
    }

    public void glUnlockArraysEXT() {
        jogl.glUnlockArraysEXT();
    }
    
    public void glMultiTexCoord2f(int target, float s, float t) {
        jogl.glMultiTexCoord2f(target, s, t);
    }


}
