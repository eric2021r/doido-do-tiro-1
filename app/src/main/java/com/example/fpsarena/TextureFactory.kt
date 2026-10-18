package com.example.fpsarena

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.opengl.GLES20
import android.opengl.GLUtils
import kotlin.random.Random

/**
 * Texturas geradas em codigo com Canvas, entao o APK nao carrega nenhum bitmap.
 * Todas tem 128x128 (potencia de dois) para permitir mipmap no OpenGL ES 2.0.
 */
object TextureFactory {

    private const val SIZE = 128

    fun floorTexture(): Int = create { canvas, random ->
        canvas.drawColor(Color.rgb(56, 62, 70))
        val tile = SIZE / 4
        val tilePaint = Paint()
        for (row in 0 until 4) {
            for (col in 0 until 4) {
                val shade = 52 + random.nextInt(16)
                tilePaint.color = Color.rgb(shade, shade + 6, shade + 12)
                canvas.drawRect(
                    (col * tile).toFloat(), (row * tile).toFloat(),
                    ((col + 1) * tile).toFloat(), ((row + 1) * tile).toFloat(),
                    tilePaint
                )
            }
        }
        // Linhas das juntas.
        val jointPaint = Paint().apply {
            color = Color.rgb(30, 34, 40)
            strokeWidth = 3f
        }
        for (i in 0..4) {
            val p = (i * tile).toFloat()
            canvas.drawLine(p, 0f, p, SIZE.toFloat(), jointPaint)
            canvas.drawLine(0f, p, SIZE.toFloat(), p, jointPaint)
        }
        drawNoise(canvas, random, 900, 26)
    }

    fun wallTexture(): Int = create { canvas, random ->
        canvas.drawColor(Color.rgb(58, 60, 66))
        val brickPaint = Paint()
        val rowHeight = 32
        val brickWidth = 64
        for (row in 0 until SIZE / rowHeight) {
            val offset = if (row % 2 == 0) 0 else brickWidth / 2
            var x = -offset
            while (x < SIZE) {
                val shade = 74 + random.nextInt(22)
                brickPaint.color = Color.rgb(shade, shade, shade + 8)
                canvas.drawRect(
                    (x + 2).toFloat(), (row * rowHeight + 2).toFloat(),
                    (x + brickWidth - 2).toFloat(), ((row + 1) * rowHeight - 2).toFloat(),
                    brickPaint
                )
                x += brickWidth
            }
        }
        drawNoise(canvas, random, 1200, 34)
    }

    fun crateTexture(): Int = create { canvas, random ->
        canvas.drawColor(Color.rgb(120, 88, 46))
        val plankPaint = Paint()
        val plankWidth = 32
        for (i in 0 until SIZE / plankWidth) {
            val shade = 108 + random.nextInt(26)
            plankPaint.color = Color.rgb(shade, (shade * 0.72f).toInt(), (shade * 0.38f).toInt())
            canvas.drawRect(
                (i * plankWidth + 1).toFloat(), 0f,
                ((i + 1) * plankWidth - 1).toFloat(), SIZE.toFloat(),
                plankPaint
            )
        }
        // Veios da madeira.
        val grain = Paint().apply {
            color = Color.argb(70, 60, 40, 18)
            strokeWidth = 1f
        }
        for (i in 0 until 60) {
            val x = random.nextInt(SIZE).toFloat()
            val y = random.nextInt(SIZE).toFloat()
            canvas.drawLine(x, y, x + random.nextInt(16).toFloat(), y + 14f, grain)
        }
        // Moldura escura nas bordas do caixote.
        val frame = Paint().apply {
            color = Color.rgb(72, 50, 24)
            strokeWidth = 14f
            style = Paint.Style.STROKE
        }
        canvas.drawRect(7f, 7f, (SIZE - 7).toFloat(), (SIZE - 7).toFloat(), frame)
        drawNoise(canvas, random, 700, 30)
    }

    private fun drawNoise(canvas: Canvas, random: Random, dots: Int, alpha: Int) {
        val paint = Paint()
        for (i in 0 until dots) {
            val dark = random.nextBoolean()
            paint.color = if (dark) Color.argb(alpha, 0, 0, 0) else Color.argb(alpha, 255, 255, 255)
            canvas.drawRect(
                random.nextInt(SIZE).toFloat(), random.nextInt(SIZE).toFloat(),
                random.nextInt(SIZE).toFloat(), random.nextInt(SIZE).toFloat(),
                paint
            )
        }
    }

    private fun create(painter: (Canvas, Random) -> Unit): Int {
        val random = Random(4242)
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        painter(canvas, random)

        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)

        bitmap.recycle()
        return ids[0]
    }
}
