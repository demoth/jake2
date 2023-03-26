package jake2.client.render.opengl;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

public class LwjglGL implements QGL {
    
    LwjglGL() {
        // singleton
    }
    
    public final void glAlphaFunc(int func, float ref) {
        GL15.glAlphaFunc(func, ref);
    }

    public final void glBegin(int mode) {
        GL15.glBegin(mode);
    }

    public final void glBindTexture(int target, int texture) {
        GL15.glBindTexture(target, texture);
    }

    public final void glBlendFunc(int sfactor, int dfactor) {
        GL15.glBlendFunc(sfactor, dfactor);
    }

    public final void glClear(int mask) {
        GL15.glClear(mask);
    }

    public final void glClearColor(float red, float green, float blue, float alpha) {
        GL15.glClearColor(red, green, blue, alpha);
    }

    public final void glColor3f(float red, float green, float blue) {
        GL15.glColor3f(red, green, blue);
    }

    public final void glColor3ub(byte red, byte green, byte blue) {
        GL15.glColor3ub(red, green, blue);
    }

    public final void glColor4f(float red, float green, float blue, float alpha) {
        GL15.glColor4f(red, green, blue, alpha);
    }

    public final void glColor4ub(byte red, byte green, byte blue, byte alpha) {
        GL15.glColor4ub(red, green, blue, alpha);
    }

    public final void glColorPointer(int size, boolean unsigned, int stride,
            ByteBuffer pointer) {
        GL15.glColorPointer(size, GL_UNSIGNED_SHORT, stride, pointer);
    }
    
    public final void glColorPointer(int size, int stride, FloatBuffer pointer) {
        GL15.glColorPointer(size, GL_UNSIGNED_SHORT, stride, pointer);
    }

    public final void glCullFace(int mode) {
        GL15.glCullFace(mode);
    }

    public final void glDeleteTextures(IntBuffer textures) {
        GL15.glDeleteTextures(textures);
    }

    public final void glDepthFunc(int func) {
        GL15.glDepthFunc(func);
    }

    public final void glDepthMask(boolean flag) {
        GL15.glDepthMask(flag);
    }

    public final void glDepthRange(double zNear, double zFar) {
        GL15.glDepthRange(zNear, zFar);
    }

    public final void glDisable(int cap) {
        GL15.glDisable(cap);
    }

    public final void glDisableClientState(int cap) {
        GL15.glDisableClientState(cap);
    }

    public final void glDrawArrays(int mode, int first, int count) {
        GL15.glDrawArrays(mode, first, count);
    }

    public final void glDrawBuffer(int mode) {
        GL15.glDrawBuffer(mode);
    }

    public final void glDrawElements(int mode, IntBuffer indices) {
        GL15.glDrawElements(mode, indices);
    }

    public final void glEnable(int cap) {
        GL15.glEnable(cap);
    }

    public final void glEnableClientState(int cap) {
        GL15.glEnableClientState(cap);
    }

    public final void glEnd() {
        GL15.glEnd();
    }

    public final void glFinish() {
        GL15.glFinish();
    }

    public final void glFlush() {
        GL15.glFlush();
    }

    public final void glFrustum(double left, double right, double bottom,
            double top, double zNear, double zFar) {
        GL15.glFrustum(left, right, bottom, top, zNear, zFar);
    }

    public final int glGetError() {
        return GL15.glGetError();
    }

    public final void glGetFloat(int pname, FloatBuffer params) {
        GL15.glGetFloat(pname);
    }

    public final String glGetString(int name) {
        return GL15.glGetString(name);
    }

    public void glHint(int target, int mode) {
        GL15.glHint(target, mode);
    }

    public final void glInterleavedArrays(int format, int stride,
            FloatBuffer pointer) {
        GL15.glInterleavedArrays(format, stride, pointer);
    }

    public final void glLoadIdentity() {
        GL15.glLoadIdentity();
    }

    public final void glLoadMatrix(FloatBuffer m) {
        GL15.glLoadTransposeMatrixf(m);
    }

    public final void glMatrixMode(int mode) {
        GL15.glMatrixMode(mode);
    }

    public final void glOrtho(double left, double right, double bottom,
            double top, double zNear, double zFar) {
        GL15.glOrtho(left, right, bottom, top, zNear, zFar);
    }

    public final void glPixelStorei(int pname, int param) {
        GL15.glPixelStorei(pname, param);
    }

    public final void glPointSize(float size) {
        GL15.glPointSize(size);
    }

    public final void glPolygonMode(int face, int mode) {
        GL15.glPolygonMode(face, mode);
    }

    public final void glPopMatrix() {
        GL15.glPopMatrix();
    }

    public final void glPushMatrix() {
        GL15.glPushMatrix();
    }

    public final void glReadPixels(int x, int y, int width, int height,
            int format, int type, ByteBuffer pixels) {
        GL15.glReadPixels(x, y, width, height, format, type, pixels);
    }

    public final void glRotatef(float angle, float x, float y, float z) {
        GL15.glRotatef(angle, x, y, z);
    }

    public final void glScalef(float x, float y, float z) {
        GL15.glScalef(x, y, z);
    }

    public final void glScissor(int x, int y, int width, int height) {
        GL15.glScissor(x, y, width, height);
    }

    public final void glShadeModel(int mode) {
        GL15.glShadeModel(mode);
    }

    public final void glTexCoord2f(float s, float t) {
        GL15.glTexCoord2f(s, t);
    }

    public final void glTexCoordPointer(int size, int stride, FloatBuffer pointer) {
        GL15.glTexCoordPointer(size, GL_UNSIGNED_SHORT, stride, pointer);
    }

    public final void glTexEnvi(int target, int pname, int param) {
        GL15.glTexEnvi(target, pname, param);
    }

    public final void glTexImage2D(int target, int level, int internalformat,
            int width, int height, int border, int format, int type,
            ByteBuffer pixels) {
        GL15.glTexImage2D(target, level, internalformat, width, height, border,
                format, type, pixels);
    }

    public final void glTexImage2D(int target, int level, int internalformat,
            int width, int height, int border, int format, int type,
            IntBuffer pixels) {
        GL15.glTexImage2D(target, level, internalformat, width, height, border,
                format, type, pixels);
    }

    public final void glTexParameterf(int target, int pname, float param) {
        GL15.glTexParameterf(target, pname, param);
    }

    public final void glTexParameteri(int target, int pname, int param) {
        GL15.glTexParameteri(target, pname, param);
    }

    public final void glTexSubImage2D(int target, int level, int xoffset,
            int yoffset, int width, int height, int format, int type,
            IntBuffer pixels) {
        GL15.glTexSubImage2D(target, level, xoffset, yoffset, width, height,
                format, type, pixels);
    }

    public final void glTranslatef(float x, float y, float z) {
        GL15.glTranslatef(x, y, z);
    }

    public final void glVertex2f(float x, float y) {
        GL15.glVertex2f(x, y);
    }

    public final void glVertex3f(float x, float y, float z) {
        GL15.glVertex3f(x, y, z);
    }

    public final void glVertexPointer(int size, int stride, FloatBuffer pointer) {
        GL15.glVertexPointer(size, GL_UNSIGNED_SHORT, stride, pointer);
    }

    public final void glViewport(int x, int y, int width, int height) {
        GL15.glViewport(x, y, width, height);
    }

    public final void glColorTable(int target, int internalFormat, int width, int format, int type, ByteBuffer data) {
        GLCapabilities caps = GL.createCapabilities();
        ARBImaging.glColorTable(target, internalFormat, width, format, type, data);
    }

    public final void glActiveTextureARB(int texture) {
        ARBMultitexture.glActiveTextureARB(texture);
    }

    public final void glClientActiveTextureARB(int texture) {
        ARBMultitexture.glClientActiveTextureARB(texture);
    }

    public final void glPointParameterEXT(int pname, FloatBuffer pfParams) {
        EXTPointParameters.glPointParameterfEXT(pname, pfParams.get());
    }

    public final void glPointParameterfEXT(int pname, float param) {
	EXTPointParameters.glPointParameterfEXT(pname, param);
    }

    public final void glLockArraysEXT(int first, int count) {
        EXTCompiledVertexArray.glLockArraysEXT(first, count);
    }

    public final void glArrayElement(int index) {
        GL15.glArrayElement(index);
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
        GLFW.glfwSwapInterval(interval);
    }

}
