package jake2.render.opengl;

import java.nio.*;

import org.lwjgl.opengl.*;

public class LwjglGL implements QGL {
    
    LwjglGL() {
        // singleton
    }
    
    public final void glAlphaFunc(int func, float ref) {
        GL11.glAlphaFunc(func, ref);
    }

    public final void glBegin(int mode) {
        GL11.glBegin(mode);
    }

    public final void glBindTexture(int target, int texture) {
        GL11.glBindTexture(target, texture);
    }

    public final void glBlendFunc(int sfactor, int dfactor) {
        GL11.glBlendFunc(sfactor, dfactor);
    }

    public final void glClear(int mask) {
        GL11.glClear(mask);
    }

    public final void glClearColor(float red, float green, float blue, float alpha) {
        GL11.glClearColor(red, green, blue, alpha);
    }

    public final void glColor3f(float red, float green, float blue) {
        GL11.glColor3f(red, green, blue);
    }

    public final void glColor3ub(byte red, byte green, byte blue) {
        GL11.glColor3ub(red, green, blue);
    }

    public final void glColor4f(float red, float green, float blue, float alpha) {
        GL11.glColor4f(red, green, blue, alpha);
    }

    public final void glColor4ub(byte red, byte green, byte blue, byte alpha) {
        GL11.glColor4ub(red, green, blue, alpha);
    }

    public final void glColorPointer(int size, boolean unsigned, int stride,
            ByteBuffer pointer) {
        GL11.glColorPointer(size, unsigned, stride, pointer);
    }
    
    public final void glColorPointer(int size, int stride, FloatBuffer pointer) {
        GL11.glColorPointer(size, stride, pointer);
    }

    public final void glCullFace(int mode) {
        GL11.glCullFace(mode);
    }

    public final void glDeleteTextures(IntBuffer textures) {
        GL11.glDeleteTextures(textures);
    }

    public final void glDepthFunc(int func) {
        GL11.glDepthFunc(func);
    }

    public final void glDepthMask(boolean flag) {
        GL11.glDepthMask(flag);
    }

    public final void glDepthRange(double zNear, double zFar) {
        GL11.glDepthRange(zNear, zFar);
    }

    public final void glDisable(int cap) {
        GL11.glDisable(cap);
    }

    public final void glDisableClientState(int cap) {
        GL11.glDisableClientState(cap);
    }

    public final void glDrawArrays(int mode, int first, int count) {
        GL11.glDrawArrays(mode, first, count);
    }

    public final void glDrawBuffer(int mode) {
        GL11.glDrawBuffer(mode);
    }

    public final void glDrawElements(int mode, IntBuffer indices) {
        GL11.glDrawElements(mode, indices);
    }

    public final void glEnable(int cap) {
        GL11.glEnable(cap);
    }

    public final void glEnableClientState(int cap) {
        GL11.glEnableClientState(cap);
    }

    public final void glEnd() {
        GL11.glEnd();
    }

    public final void glFinish() {
        GL11.glFinish();
    }

    public final void glFlush() {
        GL11.glFlush();
    }

    public final void glFrustum(double left, double right, double bottom,
            double top, double zNear, double zFar) {
        GL11.glFrustum(left, right, bottom, top, zNear, zFar);
    }

    public final int glGetError() {
        return GL11.glGetError();
    }

    public final void glGetFloat(int pname, FloatBuffer params) {
        GL11.glGetFloat(pname, params);
    }

    public final String glGetString(int name) {
        return GL11.glGetString(name);
    }

    public void glHint(int target, int mode) {
	GL11.glHint(target, mode);
    }

    public final void glInterleavedArrays(int format, int stride,
            FloatBuffer pointer) {
        GL11.glInterleavedArrays(format, stride, pointer);
    }

    public final void glLoadIdentity() {
        GL11.glLoadIdentity();
    }

    public final void glLoadMatrix(FloatBuffer m) {
        GL11.glLoadMatrix(m);
    }

    public final void glMatrixMode(int mode) {
        GL11.glMatrixMode(mode);
    }

    public final void glOrtho(double left, double right, double bottom,
            double top, double zNear, double zFar) {
        GL11.glOrtho(left, right, bottom, top, zNear, zFar);
    }

    public final void glPixelStorei(int pname, int param) {
        GL11.glPixelStorei(pname, param);
    }

    public final void glPointSize(float size) {
        GL11.glPointSize(size);
    }

    public final void glPolygonMode(int face, int mode) {
        GL11.glPolygonMode(face, mode);
    }

    public final void glPopMatrix() {
        GL11.glPopMatrix();
    }

    public final void glPushMatrix() {
        GL11.glPushMatrix();
    }

    public final void glReadPixels(int x, int y, int width, int height,
            int format, int type, ByteBuffer pixels) {
        GL11.glReadPixels(x, y, width, height, format, type, pixels);
    }

    public final void glRotatef(float angle, float x, float y, float z) {
        GL11.glRotatef(angle, x, y, z);
    }

    public final void glScalef(float x, float y, float z) {
        GL11.glScalef(x, y, z);
    }

    public final void glScissor(int x, int y, int width, int height) {
        GL11.glScissor(x, y, width, height);
    }

    public final void glShadeModel(int mode) {
        GL11.glShadeModel(mode);
    }

    public final void glTexCoord2f(float s, float t) {
        GL11.glTexCoord2f(s, t);
    }

    public final void glTexCoordPointer(int size, int stride, FloatBuffer pointer) {
        GL11.glTexCoordPointer(size, stride, pointer);
    }

    public final void glTexEnvi(int target, int pname, int param) {
        GL11.glTexEnvi(target, pname, param);
    }

    public final void glTexImage2D(int target, int level, int internalformat,
            int width, int height, int border, int format, int type,
            ByteBuffer pixels) {
        GL11.glTexImage2D(target, level, internalformat, width, height, border,
                format, type, pixels);
    }

    public final void glTexImage2D(int target, int level, int internalformat,
            int width, int height, int border, int format, int type,
            IntBuffer pixels) {
        GL11.glTexImage2D(target, level, internalformat, width, height, border,
                format, type, pixels);
    }

    public final void glTexParameterf(int target, int pname, float param) {
        GL11.glTexParameterf(target, pname, param);
    }

    public final void glTexParameteri(int target, int pname, int param) {
        GL11.glTexParameteri(target, pname, param);
    }

    public final void glTexSubImage2D(int target, int level, int xoffset,
            int yoffset, int width, int height, int format, int type,
            IntBuffer pixels) {
        GL11.glTexSubImage2D(target, level, xoffset, yoffset, width, height,
                format, type, pixels);
    }

    public final void glTranslatef(float x, float y, float z) {
        GL11.glTranslatef(x, y, z);
    }

    public final void glVertex2f(float x, float y) {
        GL11.glVertex2f(x, y);
    }

    public final void glVertex3f(float x, float y, float z) {
        GL11.glVertex3f(x, y, z);
    }

    public final void glVertexPointer(int size, int stride, FloatBuffer pointer) {
        GL11.glVertexPointer(size, stride, pointer);
    }

    public final void glViewport(int x, int y, int width, int height) {
        GL11.glViewport(x, y, width, height);
    }

    public final void glColorTable(int target, int internalFormat, int width,
            int format, int type, ByteBuffer data) {
        EXTPalettedTexture.glColorTableEXT(target, internalFormat, width, format, type, data);
    }

    public final void glActiveTextureARB(int texture) {
        ARBMultitexture.glActiveTextureARB(texture);
    }

    public final void glClientActiveTextureARB(int texture) {
	ARBMultitexture.glClientActiveTextureARB(texture);
    }

    public final void glPointParameterEXT(int pname, FloatBuffer pfParams) {
        EXTPointParameters.glPointParameterEXT(pname, pfParams);
    }

    public final void glPointParameterfEXT(int pname, float param) {
	EXTPointParameters.glPointParameterfEXT(pname, param);
    }

    public final void glLockArraysEXT(int first, int count) {
        EXTCompiledVertexArray.glLockArraysEXT(first, count);
    }

    public final void glArrayElement(int index) {
        GL11.glArrayElement(index);
    }

    public final void glUnlockArraysEXT() {
	EXTCompiledVertexArray.glUnlockArraysEXT();
    }

    public final void glMultiTexCoord2f(int target, float s, float t) {
	GL13.glMultiTexCoord2f(target, s, t);
    }

    /*
     * util extensions
     */
    public void setSwapInterval(int interval) {
	Display.setSwapInterval(interval);
    }

}
