package com.example.fpsarena

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sin

/**
 * Tela inicial: fundo com brilho laranja, titulo, botoes e um operador armado
 * no estilo da capa do Counter-Strike 1.6 (desenhado 100% em codigo, sem
 * nenhuma imagem no projeto).
 *
 * A View fica sempre presente na pilha, mas so trata toques quando o jogo esta
 * em GameState.MENU.
 */
class MenuView(context: Context, private val game: Game) : View(context) {

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private val rect = RectF()

    private val newGameRect = RectF()
    private val helpRect = RectF()
    private val exitRect = RectF()

    private var helpOpen = false

    // Cache de layout (recalculado so quando a tela muda de tamanho).
    private var titleSize = 60f
    private var subtitleSize = 17f
    private var textScale = 1f
    private var soldierScale = 1f
    private var soldierX = 0f
    private var soldierY = 0f
    private var buttonLabelSize = 22f

    init {
        isClickable = true
        isFocusable = false
    }

    // ========================================================================
    // Layout
    // ========================================================================

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        val wf = w.toFloat()
        val hf = h.toFloat()
        textScale = hf / 560f

        subtitleSize = 17f * textScale
        buttonLabelSize = 22f * textScale

        // Diminui o titulo ate caber na metade esquerda da tela.
        titleSize = 72f * textScale
        textPaint.textSize = titleSize
        val maxTitleWidth = wf * 0.56f
        var guard = 0
        while (textPaint.measureText(TITLE) > maxTitleWidth && titleSize > 14f && guard < 80) {
            titleSize -= 2f
            textPaint.textSize = titleSize
            guard++
        }

        val buttonWidth = min(wf * 0.31f, hf * 0.58f)
        val buttonX = wf * 0.055f

        newGameRect.set(buttonX, hf * 0.520f, buttonX + buttonWidth, hf * 0.641f)
        helpRect.set(buttonX, hf * 0.672f, buttonX + buttonWidth, hf * 0.772f)
        exitRect.set(buttonX, hf * 0.803f, buttonX + buttonWidth, hf * 0.903f)

        // O boneco ocupa ~66% da altura, com o corpo na altura dos olhos.
        soldierScale = hf * 0.0033f
        soldierX = wf * 0.70f - 50f * soldierScale
        soldierY = hf * 0.09f
    }

    // ========================================================================
    // Input
    // ========================================================================

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (game.state != GameState.MENU) return false
        if (event.actionMasked != MotionEvent.ACTION_DOWN) return true

        val x = event.x
        val y = event.y

        if (helpOpen) {
            helpOpen = false
            return true
        }
        if (newGameRect.contains(x, y)) {
            game.requestRestart()
            return true
        }
        if (helpRect.contains(x, y)) {
            helpOpen = true
            return true
        }
        if (exitRect.contains(x, y)) {
            (context as? Activity)?.finish()
            return true
        }
        return true
    }

    // ========================================================================
    // Desenho
    // ========================================================================

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        if (game.state == GameState.MENU) {
            drawBackground(canvas, w, h)
            drawSoldier(canvas)
            drawTexts(canvas, w, h)
            drawButtons(canvas, w, h)
            if (helpOpen) drawHelp(canvas, w, h)
        }

        postInvalidateOnAnimation()
    }

    private fun drawBackground(canvas: Canvas, w: Float, h: Float) {
        // Base semitransparente: deixa a arena em 3D (com inimigos andando)
        // aparecer de leve atras do menu.
        fillPaint.style = Paint.Style.FILL
        fillPaint.color = Color.argb(208, 9, 13, 18)
        canvas.drawRect(0f, 0f, w, h, fillPaint)

        val base = LinearGradient(
            0f, 0f, w, h,
            intArrayOf(0x7A0A0F15, 0x7A111C26, 0x9A1A1209.toInt()),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        fillPaint.shader = base
        canvas.drawRect(0f, 0f, w, h, fillPaint)

        // Brilho laranja atras do operador.
        val glow = RadialGradient(
            soldierX + 50f * soldierScale,
            soldierY + 95f * soldierScale,
            maxOf(w, h) * 0.52f,
            intArrayOf(0xB3E8452C.toInt(), 0x33BE4818, 0x00000000),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        fillPaint.shader = glow
        canvas.drawRect(0f, 0f, w, h, fillPaint)

        fillPaint.shader = null

        // Circulo grande e listras diagonais.
        fillPaint.color = Color.argb(26, 242, 163, 60)
        canvas.drawCircle(soldierX + 50f * soldierScale, soldierY + 95f * soldierScale, h * 0.36f, fillPaint)

        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = 2f * textScale
        strokePaint.color = Color.argb(12, 255, 255, 255)
        var i = -h
        while (i < w) {
            canvas.drawLine(i, 0f, i + h, h, strokePaint)
            i += 26f * textScale
        }

        // Escurece a base para o texto respirar.
        val bottom = LinearGradient(
            0f, h - h * 0.28f, 0f, h,
            intArrayOf(0x00000000, 0xB3000000.toInt()),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        fillPaint.shader = bottom
        canvas.drawRect(0f, h - h * 0.28f, w, h, fillPaint)
        fillPaint.shader = null
    }

    private fun drawTexts(canvas: Canvas, w: Float, h: Float) {
        val x = w * 0.052f

        // Titulo: sombra laranja deslocada + letra branca por cima.
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = titleSize
        textPaint.color = Color.rgb(184, 52, 31)
        canvas.drawText(TITLE, x + titleSize * 0.08f, h * 0.321f + titleSize * 0.08f, textPaint)
        textPaint.color = Color.WHITE
        canvas.drawText(TITLE, x, h * 0.321f, textPaint)

        // Sublinhado que pulsa de leve.
        val pulse = 0.55f + 0.45f * sin(nowSeconds() * 1.6f)
        fillPaint.style = Paint.Style.FILL
        fillPaint.color = Color.argb((255 * (0.55f + 0.45f * pulse)).toInt().coerceIn(0, 255), 232, 69, 44)
        canvas.drawRect(x, h * 0.366f, x + w * 0.352f, h * 0.366f + 4f * textScale, fillPaint)

        // Subtitulo com espacamento entre letras.
        textPaint.textSize = subtitleSize
        textPaint.color = Color.rgb(147, 167, 188)
        drawSpacedText(canvas, "FPS DE ARENA PARA ANDROID", x + 2f, h * 0.440f, 3f * textScale)

        // Versao / rodape.
        textPaint.textSize = subtitleSize * 0.85f
        textPaint.color = Color.argb(150, 140, 155, 172)
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("v1.0 - 5 ARMAS - ONDAS INFINITAS", x, h * 0.962f, textPaint)
    }

    private fun drawSpacedText(canvas: Canvas, text: String, x: Float, y: Float, spacing: Float) {
        var cursor = x
        for (character in text) {
            val single = character.toString()
            canvas.drawText(single, cursor, y, textPaint)
            cursor += textPaint.measureText(single) + spacing
        }
    }

    private fun drawButtons(canvas: Canvas, w: Float, h: Float) {
        drawPrimaryButton(canvas, newGameRect, "NOVO JOGO")
        drawOutlineButton(canvas, helpRect, "COMO JOGAR")
        drawOutlineButton(canvas, exitRect, "SAIR")
    }

    private fun drawPrimaryButton(canvas: Canvas, bounds: RectF, label: String) {
        val radius = bounds.height() * 0.16f
        val gradient = LinearGradient(
            bounds.left, bounds.top, bounds.left, bounds.bottom,
            Color.rgb(244, 89, 60), Color.rgb(172, 42, 24),
            Shader.TileMode.CLAMP
        )
        fillPaint.style = Paint.Style.FILL
        fillPaint.shader = gradient
        canvas.drawRoundRect(bounds, radius, radius, fillPaint)
        fillPaint.shader = null

        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = 1.5f * textScale
        strokePaint.color = Color.argb(150, 255, 210, 196)
        rect.set(
            bounds.left + bounds.height() * 0.09f,
            bounds.top + bounds.height() * 0.09f,
            bounds.right - bounds.height() * 0.09f,
            bounds.bottom - bounds.height() * 0.09f
        )
        canvas.drawRoundRect(rect, radius * 0.7f, radius * 0.7f, strokePaint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = buttonLabelSize * 1.05f
        textPaint.color = Color.WHITE
        canvas.drawText(label, bounds.centerX(), bounds.centerY() + buttonLabelSize * 0.36f, textPaint)
    }

    private fun drawOutlineButton(canvas: Canvas, bounds: RectF, label: String) {
        val radius = bounds.height() * 0.16f
        fillPaint.style = Paint.Style.FILL
        fillPaint.color = Color.argb(190, 9, 15, 21)
        canvas.drawRoundRect(bounds, radius, radius, fillPaint)

        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = 2f * textScale
        strokePaint.color = Color.rgb(60, 75, 92)
        canvas.drawRoundRect(bounds, radius, radius, strokePaint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = buttonLabelSize
        textPaint.color = Color.rgb(201, 214, 228)
        canvas.drawText(label, bounds.centerX(), bounds.centerY() + buttonLabelSize * 0.36f, textPaint)
    }

    private fun drawHelp(canvas: Canvas, w: Float, h: Float) {
        fillPaint.style = Paint.Style.FILL
        fillPaint.color = Color.argb(232, 6, 10, 14)
        canvas.drawRect(0f, 0f, w, h, fillPaint)

        val left = w * 0.09f
        var y = h * 0.16f
        val step = h * 0.052f

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = 30f * textScale
        textPaint.color = Color.rgb(232, 69, 44)
        canvas.drawText("COMO JOGAR", left, y, textPaint)
        y += step * 1.4f

        textPaint.textSize = 15f * textScale
        val linhas = arrayOf(
            "ESQUERDA: ARRASTE O DEDO PARA ANDAR (JOYSTICK)" to Color.rgb(220, 228, 240),
            "DIREITA: ARRASTE PARA OLHAR / GIRAR A CAMERA" to Color.rgb(220, 228, 240),
            "ATIRAR: DISPARA A ARMA OU JOGA A GRANADA" to Color.rgb(220, 228, 240),
            "PULO / RECARGA / TROCAR: BOTOES DO CANTO DIREITO" to Color.rgb(220, 228, 240),
            "MIRA: LUNETA COM ZOOM (SO APARECE COM A SNIPER)" to Color.rgb(220, 228, 240),
            "TECLADO (EMULADOR): WASD, MOUSE, CTRL, ESPACO, R" to Color.rgb(150, 162, 178),
            "" to Color.BLACK,
            "AS 5 ARMAS" to Color.rgb(255, 190, 120),
            "PISTOLA  - precisa, municao farta, tiro a tiro" to Color.rgb(190, 200, 214),
            "FUZIL    - rajada automatica, arma de uso geral" to Color.rgb(190, 200, 214),
            "SHOTGUN  - 8 balins por tiro, devasta de perto" to Color.rgb(190, 200, 214),
            "SNIPER   - um tiro por vez, dano altissimo" to Color.rgb(190, 200, 214),
            "GRANADA  - arremessada, explode e atinge em area" to Color.rgb(190, 200, 214),
            "" to Color.BLACK,
            "LIMPE AS ONDAS. A MUNICAO E A VIDA VOLTAM A CADA ONDA." to Color.rgb(150, 220, 255),
            "TOQUE NA TELA PARA VOLTAR" to Color.rgb(255, 214, 120)
        )

        for ((texto, color) in linhas) {
            if (texto.isNotEmpty()) {
                textPaint.color = color
                canvas.drawText(texto, left, y, textPaint)
            }
            y += step
        }
    }

    private fun nowSeconds(): Float =
        (System.nanoTime() % 1_000_000_000_000L) / 1_000_000_000f

    // ========================================================================
    // O operador (boneco)
    // ========================================================================

    private val soldierPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val soldierPath = Path()
    private val soldierRect = RectF()

    private fun drawSoldier(canvas: Canvas) {
        val scale = soldierScale
        canvas.save()
        canvas.translate(soldierX, soldierY)
        canvas.scale(scale, scale)

        fun rounded(
            x: Float, y: Float, w: Float, h: Float, rad: Float, color: Int, alpha: Int = 255
        ) {
            soldierPath.reset()
            soldierRect.set(x, y, x + w, y + h)
            soldierPath.addRoundRect(soldierRect, rad, rad, Path.Direction.CW)
            soldierPaint.style = Paint.Style.FILL
            soldierPaint.color = color
            soldierPaint.alpha = alpha
            canvas.drawPath(soldierPath, soldierPaint)
            soldierPaint.alpha = 255
        }

        fun oval(cx: Float, cy: Float, rx: Float, ry: Float, color: Int, alpha: Int = 255) {
            soldierPaint.style = Paint.Style.FILL
            soldierPaint.color = color
            soldierPaint.alpha = alpha
            canvas.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, soldierPaint)
            soldierPaint.alpha = 255
        }

        fun polygon(points: FloatArray, color: Int, alpha: Int = 255) {
            soldierPath.reset()
            soldierPath.moveTo(points[0], points[1])
            var i = 2
            while (i < points.size) {
                soldierPath.lineTo(points[i], points[i + 1])
                i += 2
            }
            soldierPath.close()
            soldierPaint.style = Paint.Style.FILL
            soldierPaint.color = color
            soldierPaint.alpha = alpha
            canvas.drawPath(soldierPath, soldierPaint)
            soldierPaint.alpha = 255
        }

        fun limb(points: FloatArray, width: Float, color: Int, alpha: Int = 255) {
            soldierPath.reset()
            soldierPath.moveTo(points[0], points[1])
            var i = 2
            while (i < points.size) {
                soldierPath.lineTo(points[i], points[i + 1])
                i += 2
            }
            soldierPaint.style = Paint.Style.STROKE
            soldierPaint.strokeWidth = width
            soldierPaint.strokeCap = Paint.Cap.ROUND
            soldierPaint.strokeJoin = Paint.Join.ROUND
            soldierPaint.color = color
            soldierPaint.alpha = alpha
            canvas.drawPath(soldierPath, soldierPaint)
            soldierPaint.alpha = 255
        }

        fun rim(points: FloatArray, width: Float) = limb(points, width, RIM, 76)

        // Sombra no chao.
        oval(50f, 195f, 44f, 9f, Color.BLACK, 102)

        // ---- pernas ----
        polygon(floatArrayOf(29f, 112f, 49f, 112f, 47f, 180f, 24f, 180f), SUIT)
        polygon(floatArrayOf(51f, 112f, 71f, 112f, 76f, 180f, 53f, 180f), SUIT)
        polygon(floatArrayOf(49f, 112f, 49f, 180f, 38f, 180f, 42f, 112f), SUIT_DARK, 71)
        rim(floatArrayOf(71f, 114f, 76f, 178f), 7f)
        oval(36f, 146f, 11.5f, 10f, VEST)
        oval(64f, 146f, 11.5f, 10f, VEST)
        rim(floatArrayOf(73f, 142f, 75f, 150f), 5f)
        rounded(19f, 174f, 28f, 20f, 6f, BOOT)
        rounded(51f, 174f, 28f, 20f, 6f, BOOT)
        rounded(19f, 188f, 28f, 6f, 3f, 0xFF26313C.toInt(), 90)
        rounded(51f, 188f, 28f, 6f, 3f, 0xFF26313C.toInt(), 90)

        // ---- quadril / cinto ----
        rounded(27f, 100f, 46f, 16f, 7f, SUIT)
        rounded(28f, 96f, 44f, 11f, 4f, BELT)
        rounded(46f, 98f, 8f, 7f, 2f, RAIL)

        // ---- tronco afunilado ----
        polygon(floatArrayOf(24f, 50f, 76f, 50f, 67f, 102f, 68f, 112f, 32f, 112f, 33f, 102f), SUIT)
        polygon(floatArrayOf(29f, 56f, 71f, 56f, 65f, 101f, 35f, 101f), VEST)
        rounded(29f, 50f, 42f, 12f, 5f, VEST_LIGHT)
        rounded(38f, 54f, 6f, 46f, 3f, VEST_LIGHT, 217)
        rounded(56f, 54f, 6f, 46f, 3f, VEST_LIGHT, 217)
        rounded(31f, 68f, 15f, 15f, 4f, POUCH)
        rounded(52f, 68f, 15f, 15f, 4f, POUCH)
        rounded(32f, 87f, 14f, 13f, 4f, POUCH)
        rounded(52f, 87f, 14f, 13f, 4f, POUCH)
        rounded(36f, 58f, 12f, 8f, 3f, POUCH)
        rounded(53f, 58f, 12f, 8f, 3f, POUCH)
        limb(floatArrayOf(35f, 52f, 70f, 102f), 6f, 0xFF1D2833.toInt())
        oval(26f, 54f, 9f, 8f, VEST)
        oval(74f, 54f, 9f, 8f, VEST)
        rim(floatArrayOf(24f, 47f, 24f, 58f), 4f)

        // ---- bracos ----
        limb(floatArrayOf(76f, 57f, 83f, 67f, 74f, 72f), 11f, SUIT)
        limb(floatArrayOf(25f, 57f, 25f, 83f, 43f, 106f), 11f, SUIT)
        rim(floatArrayOf(83f, 62f, 84f, 70f), 4f)

        // ---- cabeca ----
        oval(50f, 44f, 8f, 6f, SUIT_DARK, 128)
        oval(50f, 28f, 12f, 13.5f, SUIT)
        oval(45f, 29f, 6f, 11f, SUIT_DARK, 66)
        oval(50f, 22f, 14f, 11f, 0xFF33445A.toInt())
        oval(49f, 15f, 12f, 4.5f, 0xFF4A5E79.toInt(), 115)
        rounded(36f, 29f, 28f, 3f, 1f, SUIT_DARK)
        rounded(37f, 30f, 26f, 10f, 4f, 0xFF090F14.toInt())
        rounded(39f, 32f, 10f, 6f, 2.5f, AMBER)
        rounded(51f, 32f, 10f, 6f, 2.5f, AMBER)
        rounded(39f, 32f, 10f, 2.5f, 1.5f, 0xFFFDDE9A.toInt(), 128)
        rounded(51f, 32f, 10f, 2.5f, 1.5f, 0xFFFDDE9A.toInt(), 128)
        rounded(33f, 27f, 6f, 10f, 3f, VEST)
        limb(floatArrayOf(37f, 37f, 44f, 41f), 3f, 0xFF0A1016.toInt())
        rim(floatArrayOf(62f, 17f, 62f, 33f), 4f)

        // ---- fuzil atravessado no peito (por cima de tudo) ----
        canvas.save()
        canvas.translate(42f, 92f)
        canvas.rotate(Math.toDegrees(atan2(-0.674, 0.739).toDouble()).toFloat())
        rounded(-34f, -9f, 20f, 18f, 4f, RIFLE_DARK)
        rounded(-16f, -8f, 44f, 16f, 3f, RIFLE)
        rounded(-8f, -15f, 34f, 7f, 2f, RAIL)
        rounded(2f, -23f, 20f, 8f, 2f, 0xFF0C1116.toInt())
        rounded(9f, -15f, 5f, 8f, 1f, RAIL)
        rounded(26f, -8f, 24f, 16f, 5f, RAIL)
        rounded(30f, 8f, 18f, 5f, 2f, RAIL)
        rounded(48f, -4f, 18f, 8f, 1f, RIFLE)
        rounded(64f, -5f, 9f, 10f, 2f, RAIL)
        rounded(-2f, 8f, 12f, 26f, 4f, RIFLE_DARK)
        rounded(-14f, 7f, 9f, 16f, 4f, RIFLE_DARK)
        oval(37f, 6f, 10f, 9f, GLOVE)
        oval(-9f, 13f, 10f, 10f, GLOVE)
        canvas.restore()

        canvas.restore()
    }

    private companion object {
        const val TITLE = "DOIDO DO TIRO"

        val SUIT = 0xFF2E3D4B.toInt()
        val SUIT_DARK = 0xFF1A2430.toInt()
        val VEST = 0xFF141D26.toInt()
        val VEST_LIGHT = 0xFF26333F.toInt()
        val POUCH = 0xFF202D3A.toInt()
        val BELT = 0xFF0E141B.toInt()
        val BOOT = 0xFF0B1116.toInt()
        val RIFLE = 0xFF232A33.toInt()
        val RIFLE_DARK = 0xFF0D1217.toInt()
        val RAIL = 0xFF4A5A6E.toInt()
        val GLOVE = 0xFF10171E.toInt()
        val AMBER = 0xFFF2A33C.toInt()
        val RIM = 0xFFF0A85C.toInt()
    }
}
