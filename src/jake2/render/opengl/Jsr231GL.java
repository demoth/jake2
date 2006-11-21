package jake2.render.opengl;


import java.nio.*;

import javax.media.opengl.GL;

public class Jsr231GL implements QGL {
    
    private GL gl;
    
    Jsr231GL() {
        // singleton
    }
    
    void setGL(GL gl) {
        this.gl = gl;
    }
    
    public void glAlphaFunc(int func, float ref) {
        gl.glAlphaFunc(func, ref);
    }

    public void glBegin(int mode) {
        gl.glBegin(mode);
    }

    public void glBindTexture(int target, int texture) {
        gl.glBindTexture(target, texture);
    }

    public void glBlendFunc(int sfactor, int dfactor) {
        gl.glBlendFunc(sfactor, dfactor);
    }

    public void glClear(int mask) {
        gl.glClear(mask);
    }

    public void glClearColor(float red, float green, float blue, float alpha) {
        gl.glClearColor(red, green, blue, alpha);
    }

    public void glColor3f(float red, float green, float blue) {
        gl.glColor3f(red, green, blue);
    }

    public void glColor3ub(byte red, byte green, byte blue) {
        gl.glColor3ub(red, green, blue);
    }

    public void glColor4f(float red, float green, float blue, float alpha) {
        gl.glColor4f(red, green, blue, alpha);
    }

    public void glColor4ub(byte red, byte green, byte blue, byte alpha) {
        gl.glColor4ub(red, green, blue, alpha);
    }

    public void glColorPointer(int size, boolean unsigned, int stride,
            ByteBuffer pointer) {
        gl.glColorPointer(size, GL_UNSIGNED_BYTE, stride, pointer);
    }
    
    public void glColorPointer(int size, int stride, FloatBuffer pointer) {
        gl.glColorPointer(size, GL_FLOAT, stride, pointer);
    }

    public void glCullFace(int mode) {
        gl.glCullFace(mode);
    }

    public void glDeleteTextures(IntBuffer textures) {
        gl.glDeleteTextures(textures.limit(), textures);
    }

    public void glDepthFunc(int func) {
        gl.glDepthFunc(func);
    }

    public void glDepthMask(boolean flag) {
        gl.glDepthMask(flag);
    }

    public void glDepthRange(double zNear, double zFar) {
        gl.glDepthRange(zNear, zFar);
    }

    public void glDisable(int cap) {
        gl.glDisable(cap);
    }

    public void glDisableClientState(int cap) {
        gl.glDisableClientState(cap);
    }

    public void glDrawArrays(int mode, int first, int count) {
        gl.glDrawArrays(mode, first, count);
    }

    public void glDrawBuffer(int mode) {
        gl.glDrawBuffer(mode);
    }

    public void glDrawElements(int mode, IntBuffer indices) {
        gl.glDrawElements(mode, indices.limit(), GL_UNSIGNED_INT, indices);
    }

    public void glEnable(int cap) {
        gl.glEnable(cap);
    }

    public void glEnableClientState(int cap) {
        gl.glEnableClientState(cap);
    }

    public void glEnd() {
        gl.glEnd();
    }

    public void glFinish() {
        gl.glFinish();
    }

    public void glFlush() {
        gl.glFlush();
    }

    public void glFrustum(double left, double right, double bottom,
            double top, double zNear, double zFar) {
        gl.glFrustum(left, right, bottom, top, zNear, zFar);
    }

    public int glGetError() {
        return gl.glGetError();
    }

    public void glGetFloat(int pname, FloatBuffer params) {
        gl.glGetFloatv(pname, params);
    }

    public String glGetString(int name) {
        return gl.glGetString(name);
    }

    public void glInterleavedArrays(int format, int stride,
            FloatBuffer pointer) {
        gl.glInterleavedArrays(format, stride, pointer);
    }

    public void glLoadIdentity() {
        gl.glLoadIdentity();
    }

    public void glLoadMatrix(FloatBuffer m) {
        gl.glLoadMatrixf(m);
    }

    public void glMatrixMode(int mode) {
        gl.glMatrixMode(mode);
    }

    public void glOrtho(double left, double right, double bottom,
            double top, double zNear, double zFar) {
        gl.glOrtho(left, right, bottom, top, zNear, zFar);
    }

    public void glPixelStorei(int pname, int param) {
        gl.glPixelStorei(pname, param);
    }

    public void glPointSize(float size) {
        gl.glPointSize(size);
    }

    public void glPolygonMode(int face, int mode) {
        gl.glPolygonMode(face, mode);
    }

    public void glPopMatrix() {
        gl.glPopMatrix();
    }

    public void glPushMatrix() {
        gl.glPushMatrix();
    }

    public void glReadPixels(int x, int y, int width, int height,
            int format, int type, ByteBuffer pixels) {
        gl.glReadPixels(x, y, width, height, format, type, pixels);
    }

    public void glRotatef(float angle, float x, float y, float z) {
        gl.glRotatef(angle, x, y, z);
    }

    public void glScalef(float x, float y, float z) {
        gl.glScalef(x, y, z);
    }

    public void glScissor(int x, int y, int width, int height) {
        gl.glScissor(x, y, width, height);
    }

    public void glShadeModel(int mode) {
        gl.glShadeModel(mode);
    }

    public void glTexCoord2f(float s, float t) {
        gl.glTexCoord2f(s, t);
    }

    public void glTexCoordPointer(int size, int stride, FloatBuffer pointer) {
        gl.glTexCoordPointer(size, GL_FLOAT, stride, pointer);
    }

    public void glTexEnvi(int target, int pname, int param) {
        gl.glTexEnvi(target, pname, param);
    }

    public void glTexImage2D(int target, int level, int internalformat,
            int width, int height, int border, int format, int type,
            ByteBuffer pixels) {
        gl.glTexImage2D(target, level, internalformat, width, height, border,
                format, type, pixels);
    }

    public void glTexImage2D(int target, int level, int internalformat,
            int width, int height, int border, int format, int type,
            IntBuffer pixels) {
        gl.glTexImage2D(target, level, internalformat, width, height, border,
                format, type, pixels);
    }

    public void glTexParameterf(int target, int pname, float param) {
        gl.glTexParameterf(target, pname, param);
    }

    public void glTexParameteri(int target, int pname, int param) {
        gl.glTexParameteri(target, pname, param);
    }

    public void glTexSubImage2D(int target, int level, int xoffset,
            int yoffset, int width, int height, int format, int type,
            IntBuffer pixels) {
        gl.glTexSubImage2D(target, level, xoffset, yoffset, width, height,
                format, type, pixels);
    }

    public void glTranslatef(float x, float y, float z) {
        gl.glTranslatef(x, y, z);
    }

    public void glVertex2f(float x, float y) {
        gl.glVertex2f(x, y);
    }

    public void glVertex3f(float x, float y, float z) {
        gl.glVertex3f(x, y, z);
    }

    public void glVertexPointer(int size, int stride, FloatBuffer pointer) {
        gl.glVertexPointer(size, GL_FLOAT, stride, pointer);
    }

    public void glViewport(int x, int y, int width, int height) {
        gl.glViewport(x, y, width, height);
    }

    public void glColorTable(int target, int internalFormat, int width,
            int format, int type, ByteBuffer data) {
        gl.glColorTable(target, internalFormat, width, format, type, data);
    }

    public void glActiveTextureARB(int texture) {
        gl.glActiveTexture(texture);
    }

    public void glClientActiveTextureARB(int texture) {
        gl.glClientActiveTexture(texture);
    }

    public void glPointParameterEXT(int pname, FloatBuffer pfParams) {
        gl.glPointParameterfvEXT(pname, pfParams);
    }

    public void glPointParameterfEXT(int pname, float param) {
        gl.glPointParameterfEXT(pname, param);
    }
    public void glLockArraysEXT(int first, int count) {
        gl.glLockArraysEXT(first, count);
    }

    public void glArrayElement(int index) {
        gl.glArrayElement(index);
    }

    public void glUnlockArraysEXT() {
        gl.glUnlockArraysEXT();
    }
    
    public void glMultiTexCoord2f(int target, float s, float t) {
        gl.glMultiTexCoord2f(target, s, t);
    }


}
