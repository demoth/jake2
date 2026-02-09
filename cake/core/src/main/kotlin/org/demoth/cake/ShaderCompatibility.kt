package org.demoth.cake

import com.badlogic.gdx.graphics.glutils.ShaderProgram

/**
 * Configures LibGDX shader source prefixes for the GL3 pipeline used by Cake.
 * Shared between the game client and the standalone model viewer.
 */
fun initializeShaderCompatibility() {
    ShaderProgram.prependVertexCode = """
        #version 150
        #define GLSL3
        #define attribute in
        #define varying out
        #define texture2D texture
        #define textureCube texture
    """.trimIndent() + "\n"
    ShaderProgram.prependFragmentCode = """
        #version 150
        #define GLSL3
        #define varying in
        #define texture2D texture
        #define textureCube texture
        out vec4 fragColor;
        #define gl_FragColor fragColor
    """.trimIndent() + "\n"
}
