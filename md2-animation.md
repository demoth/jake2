# Vertex animation shader for quake2 md2 models

There is no built-in support in libgdx for the vertex animation, so a custom shader will be required.
The approach is to have a custom vertex shader what will create calculate and pass the vertex positions and uv coords.
Most importantly, the shader should support smooth frame interpolation.
Overview:

1. Prepare Vertex Data
    - Store vertex positions of each animation frame in a linear buffer.
    - Structure data sequentially: each vertex position (x, y, z) follows immediately after the previous vertex.
    - UV coordinates do not changed between frames so can be passed as an attribute
2. Create a Texture Buffer Object (TBO)
    - Upload the vertex data to GPU using a TBO for efficient, integer-based indexing.
3. Implement Vertex Shader Interpolation
    - Write a GLSL vertex shader that uses integer indices to fetch vertex positions from two frames.
    - Interpolate positions smoothly using the mix() function based on a lerp_factor.
4. Update Shader Uniforms
    - Continuously pass the current frame, next frame, and interpolation factor (lerp_factor) to the shader in each render cycle.

## Vertex shader

```glsl
#version 330 core

in int vert_id;
in vec2 a_texCoord;
uniform samplerBuffer texture_positions;
uniform int frame1;
uniform int frame2;
uniform float lerp_factor;
uniform int verticesPerFrame;

out vec2 uv;

void main() {
    int indexFrame1 = (frame1 * verticesPerFrame + vert_id) * 3;
    int indexFrame2 = (frame2 * verticesPerFrame + vert_id) * 3;

    vec3 pos1 = texelFetch(texture_positions, indexFrame1).xyz;
    vec3 pos2 = texelFetch(texture_positions, indexFrame2).xyz;

    vec3 interpolated = mix(pos1, pos2, lerp_factor);
    gl_Position = vec4(interpolated, 1.0);
    uv = a_texCoord;
}
```

## Fragment shader

Nothing special is required for the fragment shader, the usual parameters like position and texCoord are passed and processed as usual.

## libgdx code

```kotlin

// create the shader program
val shader = ShaderProgram(vertexShaderCode, fragmentShaderCode)
if (!shader.isCompiled) throw GdxRuntimeException("Shader compile error: ${shader.log}")

// bind and pass the required uniforms
shader.bind()
shader.setUniformi("frame1", currentFrame)
shader.setUniformi("frame2", nextFrame)
shader.setUniformf("lerp_factor", interpolationFactor)
shader.setUniformi("verticesPerFrame", verticesPerFrame)

Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0)
Gdx.gl.glBindTexture(GL30.GL_TEXTURE_BUFFER, tboHandle)
shader.setUniformi("texture_positions", 0)

 
// create the buffer for the vertex positions (texture_positions)
val vertexBuffer = BufferUtils.newFloatBuffer(totalFrames * verticesPerFrame * 3)
frames.forEach { frame ->
    frame.vertices.forEach { vertex ->
        vertexBuffer.put(vertex.x)
        vertexBuffer.put(vertex.y)
        vertexBuffer.put(vertex.z)
    }
}
vertexBuffer.flip()

val vboHandle = Gdx.gl.glGenBuffer()
Gdx.gl.glBindBuffer(GL30.GL_TEXTURE_BUFFER, vboHandle)
Gdx.gl.glBufferData(GL30.GL_TEXTURE_BUFFER, vertexBuffer.capacity() * 4, vertexBuffer, GL30.GL_STATIC_DRAW)

val tboHandle = Gdx.gl.glGenTexture()
Gdx.gl.glBindTexture(GL30.GL_TEXTURE_BUFFER, tboHandle)
Gdx.gl.glTexBuffer(GL30.GL_TEXTURE_BUFFER, GL30.GL_RGB32F, vboHandle)

/// on each frame: update the uniforms
interpolationFactor += deltaTime * animationSpeed
if (interpolationFactor > 1f) {
    interpolationFactor = 0f
    currentFrame = nextFrame
    nextFrame = (nextFrame + 1) % totalFrames
}
```