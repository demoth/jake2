package org.demoth.cake

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.GL30
import com.badlogic.gdx.utils.BufferUtils

/** Query the bound default framebuffer; libGDX's BufferFormat contains requested attributes. */
internal fun describeWindowBuffer(): String {
    val result = BufferUtils.newIntBuffer(1)
    fun integer(parameter: Int): Int {
        result.clear()
        Gdx.gl.glGetIntegerv(parameter, result)
        return result[0]
    }
    val gl30 = Gdx.gl30 ?: return "attribute queries require GL30"
    val previous = integer(GL20.GL_FRAMEBUFFER_BINDING)
    // This is called from the console while the default framebuffer is bound. Preserve it
    // explicitly so later changes to command execution cannot query a scene FBO by mistake.
    com.badlogic.gdx.graphics.glutils.FrameBuffer.unbind()
    try {
        fun attachment(target: Int, parameter: Int): Int {
            result.clear()
            gl30.glGetFramebufferAttachmentParameteriv(GL20.GL_FRAMEBUFFER, target, GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE, result)
            if (result[0] == GL20.GL_NONE) return 0
            result.clear()
            gl30.glGetFramebufferAttachmentParameteriv(GL20.GL_FRAMEBUFFER, target, parameter, result)
            return result[0]
        }
        val red = attachment(0x0402 /* GL_BACK_LEFT (desktop OpenGL) */, GL30.GL_FRAMEBUFFER_ATTACHMENT_RED_SIZE)
        val green = attachment(0x0402 /* GL_BACK_LEFT (desktop OpenGL) */, GL30.GL_FRAMEBUFFER_ATTACHMENT_GREEN_SIZE)
        val blue = attachment(0x0402 /* GL_BACK_LEFT (desktop OpenGL) */, GL30.GL_FRAMEBUFFER_ATTACHMENT_BLUE_SIZE)
        val alpha = attachment(0x0402 /* GL_BACK_LEFT (desktop OpenGL) */, GL30.GL_FRAMEBUFFER_ATTACHMENT_ALPHA_SIZE)
        val depth = attachment(GL30.GL_DEPTH, GL30.GL_FRAMEBUFFER_ATTACHMENT_DEPTH_SIZE)
        val stencil = attachment(GL30.GL_STENCIL, GL30.GL_FRAMEBUFFER_ATTACHMENT_STENCIL_SIZE)
        return "RGBA=$red/$green/$blue/$alpha depth=$depth stencil=$stencil samples=${integer(GL20.GL_SAMPLES)}"
    } finally {
        Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER, previous)
    }
}
