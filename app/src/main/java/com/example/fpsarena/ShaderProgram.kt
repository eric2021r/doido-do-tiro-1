package com.example.fpsarena

import android.opengl.GLES20

/**
 * Um unico programa GLSL da conta de tudo: blocos texturizados, inimigos
 * coloridos, arma, flash do cano e tracantes. O flag uUnlit desliga a luz.
 */
class ShaderProgram {

    val program: Int

    // Atributos (fixados em 0, 1 e 2 antes do link).
    val aPos = 0
    val aNormal = 1
    val aUV = 2

    // Uniforms.
    val uMvp: Int
    val uModel: Int
    val uUvScale: Int
    val uCamPos: Int
    val uColor: Int
    val uTint: Int
    val uUseTexture: Int
    val uAlpha: Int
    val uUnlit: Int
    val uLightDir: Int
    val uFogStart: Int
    val uFogEnd: Int
    val uFogColor: Int
    val uTexture: Int

    init {
        val vertexShader = compile(GLES20.GL_VERTEX_SHADER, VERTEX_SOURCE)
        val fragmentShader = compile(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SOURCE)

        program = GLES20.glCreateProgram()
        if (program == 0) throw RuntimeException("Nao foi possivel criar o programa GLSL")

        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)

        // Locais conhecidos deixam o codigo de desenho mais simples.
        GLES20.glBindAttribLocation(program, aPos, "aPos")
        GLES20.glBindAttribLocation(program, aNormal, "aNormal")
        GLES20.glBindAttribLocation(program, aUV, "aUV")

        GLES20.glLinkProgram(program)

        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            throw RuntimeException("Erro ao linkar o programa GLSL: $log")
        }

        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)

        uMvp = GLES20.glGetUniformLocation(program, "uMvp")
        uModel = GLES20.glGetUniformLocation(program, "uModel")
        uUvScale = GLES20.glGetUniformLocation(program, "uUvScale")
        uCamPos = GLES20.glGetUniformLocation(program, "uCamPos")
        uColor = GLES20.glGetUniformLocation(program, "uColor")
        uTint = GLES20.glGetUniformLocation(program, "uTint")
        uUseTexture = GLES20.glGetUniformLocation(program, "uUseTexture")
        uAlpha = GLES20.glGetUniformLocation(program, "uAlpha")
        uUnlit = GLES20.glGetUniformLocation(program, "uUnlit")
        uLightDir = GLES20.glGetUniformLocation(program, "uLightDir")
        uFogStart = GLES20.glGetUniformLocation(program, "uFogStart")
        uFogEnd = GLES20.glGetUniformLocation(program, "uFogEnd")
        uFogColor = GLES20.glGetUniformLocation(program, "uFogColor")
        uTexture = GLES20.glGetUniformLocation(program, "uTexture")
    }

    fun use() {
        GLES20.glUseProgram(program)
    }

    fun setColor(r: Float, g: Float, b: Float) {
        GLES20.glUniform3f(uColor, r, g, b)
    }

    fun setTint(r: Float, g: Float, b: Float) {
        GLES20.glUniform3f(uTint, r, g, b)
    }

    fun setAlpha(alpha: Float) {
        GLES20.glUniform1f(uAlpha, alpha)
    }

    fun setUvScale(x: Float, y: Float) {
        GLES20.glUniform2f(uUvScale, x, y)
    }

    fun setCameraPosition(x: Float, y: Float, z: Float) {
        GLES20.glUniform3f(uCamPos, x, y, z)
    }

    fun setFog(start: Float, end: Float, r: Float, g: Float, b: Float) {
        GLES20.glUniform1f(uFogStart, start)
        GLES20.glUniform1f(uFogEnd, end)
        GLES20.glUniform3f(uFogColor, r, g, b)
    }

    fun setLightDirection(x: Float, y: Float, z: Float) {
        GLES20.glUniform3f(uLightDir, x, y, z)
    }

    fun setTextureEnabled(enabled: Boolean) {
        GLES20.glUniform1f(uUseTexture, if (enabled) 1f else 0f)
    }

    fun setUnlit(unlit: Boolean) {
        GLES20.glUniform1f(uUnlit, if (unlit) 1f else 0f)
    }

    fun setMvp(matrix: FloatArray, offset: Int = 0) {
        GLES20.glUniformMatrix4fv(uMvp, 1, false, matrix, offset)
    }

    fun setModel(matrix: FloatArray, offset: Int = 0) {
        GLES20.glUniformMatrix4fv(uModel, 1, false, matrix, offset)
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) throw RuntimeException("Nao foi possivel criar o shader")
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)

        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw RuntimeException("Erro ao compilar o shader: $log")
        }
        return shader
    }

    private companion object {

        // OpenGL ES 2.0 / GLSL ES 1.00 para funcionar no maior numero de aparelhos.
        const val VERTEX_SOURCE = """
            uniform mat4 uMvp;
            uniform mat4 uModel;
            uniform vec2 uUvScale;
            uniform vec3 uCamPos;

            attribute vec3 aPos;
            attribute vec3 aNormal;
            attribute vec2 aUV;

            varying vec3 vNormal;
            varying vec2 vUv;
            varying float vDistance;

            void main() {
                vec4 world = uModel * vec4(aPos, 1.0);
                vUv = aUV * uUvScale;
                // Todos os modelos sao translacao + rotacao + escala diagonal,
                // entao a rotacao normalizada ja da o normal correto.
                vNormal = normalize((uModel * vec4(aNormal, 0.0)).xyz);
                vDistance = length(world.xyz - uCamPos);
                gl_Position = uMvp * vec4(aPos, 1.0);
            }
        """

        const val FRAGMENT_SOURCE = """
            precision mediump float;

            uniform vec3 uColor;
            uniform vec3 uTint;
            uniform float uUseTexture;
            uniform float uAlpha;
            uniform float uUnlit;
            uniform vec3 uLightDir;
            uniform float uFogStart;
            uniform float uFogEnd;
            uniform vec3 uFogColor;
            uniform sampler2D uTexture;

            varying vec3 vNormal;
            varying vec2 vUv;
            varying float vDistance;

            void main() {
                vec3 base = uColor;
                if (uUseTexture > 0.5) {
                    base *= texture2D(uTexture, vUv).rgb;
                }
                base *= uTint;

                float light = 1.0;
                if (uUnlit < 0.5) {
                    vec3 normal = normalize(vNormal);
                    float diffuse = max(dot(normal, uLightDir), 0.0);
                    // Luz ambiente alta para nenhum lugar ficar totalmente preto.
                    light = 0.45 + 0.55 * diffuse;
                }

                vec3 color = base * light;
                float fog = clamp((vDistance - uFogStart) / (uFogEnd - uFogStart), 0.0, 1.0);
                color = mix(color, uFogColor, fog);

                gl_FragColor = vec4(color, uAlpha);
            }
        """
    }
}
