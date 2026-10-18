package com.example.fpsarena

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

/**
 * Malha indexada com posicao, normal e coordenada de textura.
 * Os dados sobem para a GPU uma unica vez (VBO/IBO).
 */
class Mesh(
    private val positions: FloatArray,
    private val normals: FloatArray,
    private val uvs: FloatArray,
    private val indices: ShortArray
) {

    private var positionBuffer = 0
    private var normalBuffer = 0
    private var uvBuffer = 0
    private var indexBuffer = 0

    private val indexCount = indices.size
    private var uploaded = false

    fun upload() {
        if (uploaded) return
        positionBuffer = createArrayBuffer(toFloatBuffer(positions))
        normalBuffer = createArrayBuffer(toFloatBuffer(normals))
        uvBuffer = createArrayBuffer(toFloatBuffer(uvs))
        indexBuffer = createElementBuffer(toShortBuffer(indices))
        uploaded = true
    }

    fun draw(mode: Int = GLES20.GL_TRIANGLES) {
        if (!uploaded) return

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, positionBuffer)
        GLES20.glVertexAttribPointer(0, 3, GLES20.GL_FLOAT, false, 0, 0)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, normalBuffer)
        GLES20.glVertexAttribPointer(1, 3, GLES20.GL_FLOAT, false, 0, 0)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, uvBuffer)
        GLES20.glVertexAttribPointer(2, 2, GLES20.GL_FLOAT, false, 0, 0)

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBuffer)
        GLES20.glDrawElements(mode, indexCount, GLES20.GL_UNSIGNED_SHORT, 0)
    }

    fun release() {
        if (!uploaded) return
        GLES20.glDeleteBuffers(4, intArrayOf(positionBuffer, normalBuffer, uvBuffer, indexBuffer), 0)
        uploaded = false
    }

    private fun createArrayBuffer(data: FloatBuffer): Int {
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, ids[0])
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, data.capacity() * 4, data, GLES20.GL_STATIC_DRAW)
        return ids[0]
    }

    private fun createElementBuffer(data: ShortBuffer): Int {
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ids[0])
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, data.capacity() * 2, data, GLES20.GL_STATIC_DRAW)
        return ids[0]
    }

    private fun toFloatBuffer(data: FloatArray): FloatBuffer {
        val buffer = ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        buffer.put(data)
        buffer.position(0)
        return buffer
    }

    private fun toShortBuffer(data: ShortArray): ShortBuffer {
        val buffer = ByteBuffer.allocateDirect(data.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
        buffer.put(data)
        buffer.position(0)
        return buffer
    }
}
