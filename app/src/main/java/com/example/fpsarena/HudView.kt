package com.example.fpsarena

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.min

/**
 * Camada 2D desenhada por cima do OpenGL: HUD (vida, municao, placar, mira) e
 * todos os controles de toque. Tambem e ela que captura o input, repassando
 * para o InputState que a thread de render le.
 */
class HudView(context: Context, private val game: Game) : View(context) {

    private enum class Role { MOVE, LOOK, FIRE, JUMP, RELOAD }

    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = value * density

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        color = Color.WHITE
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()

    private var vignettePaint: Paint? = null

    // ------------------------------------------------------------ geometria UI
    private var joystickRadius = 80f
    private var joystickRestX = 0f
    private var joystickRestY = 0f

    private var fireX = 0f
    private var fireY = 0f
    private var fireRadius = 54f

    private var jumpX = 0f
    private var jumpY = 0f
    private var jumpRadius = 40f

    private var reloadX = 0f
    private var reloadY = 0f
    private var reloadRadius = 34f

    // ------------------------------------------------------------- estado touch
    private val roles = HashMap<Int, Role>()
    private var joystickPointer = -1
    private var joystickOriginX = 0f
    private var joystickOriginY = 0f
    private var joystickKnobX = 0f
    private var joystickKnobY = 0f
    private var lookPointer = -1
    private var lookLastX = 0f
    private var lookLastY = 0f

    init {
        isClickable = true
        // Nao rouba o foco: quem precisa dele (teclado/controle) e a GameView.
        isFocusable = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        joystickRadius = dp(80f)
        joystickRestX = dp(135f)
        joystickRestY = h - dp(120f)

        fireRadius = dp(54f)
        fireX = w - dp(105f)
        fireY = h - dp(100f)

        jumpRadius = dp(40f)
        jumpX = w - dp(232f)
        jumpY = h - dp(100f)

        reloadRadius = dp(34f)
        reloadX = w - dp(105f)
        reloadY = h - dp(212f)

        buildVignette(w.toFloat(), h.toFloat())
    }

    private fun buildVignette(w: Float, h: Float) {
        val gradient = RadialGradient(
            w / 2f, h / 2f, maxOf(w, h) * 0.68f,
            intArrayOf(0x00000000, 0x00000000, 0x8CB40000.toInt()),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = gradient }
    }

    // ========================================================================
    // Input
    // ========================================================================

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val index = event.actionIndex
                if (game.state == GameState.GAME_OVER) {
                    game.requestRestart()
                    return true
                }
                val id = event.getPointerId(index)
                val x = event.getX(index)
                val y = event.getY(index)
                assignRole(id, x, y)
            }

            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val id = event.getPointerId(i)
                    val x = event.getX(i)
                    val y = event.getY(i)
                    when (roles[id]) {
                        Role.MOVE -> if (id == joystickPointer) updateJoystick(x, y)
                        Role.LOOK -> if (id == lookPointer) {
                            InputState.addLook(x - lookLastX, y - lookLastY)
                            lookLastX = x
                            lookLastY = y
                        }
                        else -> Unit
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val index = event.actionIndex
                releaseRole(event.getPointerId(index))
            }

            MotionEvent.ACTION_CANCEL -> releaseAll()
        }
        return true
    }

    private fun assignRole(id: Int, x: Float, y: Float) {
        if (distance(x, y, fireX, fireY) < fireRadius * 1.4f) {
            roles[id] = Role.FIRE
            InputState.firing = true
            return
        }
        if (distance(x, y, reloadX, reloadY) < reloadRadius * 1.5f) {
            roles[id] = Role.RELOAD
            InputState.requestReload()
            return
        }
        if (distance(x, y, jumpX, jumpY) < jumpRadius * 1.5f) {
            roles[id] = Role.JUMP
            InputState.requestJump()
            return
        }
        if (x < width * 0.45f) {
            roles[id] = Role.MOVE
            joystickPointer = id
            joystickOriginX = x
            joystickOriginY = y
            updateJoystick(x, y)
            return
        }
        roles[id] = Role.LOOK
        lookPointer = id
        lookLastX = x
        lookLastY = y
    }

    private fun updateJoystick(x: Float, y: Float) {
        var dx = x - joystickOriginX
        var dy = y - joystickOriginY
        val length = hypot(dx, dy)
        if (length > joystickRadius) {
            val scale = joystickRadius / length
            dx *= scale
            dy *= scale
        }
        joystickKnobX = dx
        joystickKnobY = dy

        InputState.moveX = dx / joystickRadius
        // Na tela o Y cresce para baixo; empurrar para cima = andar para frente.
        InputState.moveY = -dy / joystickRadius
    }

    private fun releaseRole(id: Int) {
        when (roles.remove(id)) {
            Role.MOVE -> {
                if (joystickPointer == id) {
                    joystickPointer = -1
                    joystickKnobX = 0f
                    joystickKnobY = 0f
                    InputState.moveX = 0f
                    InputState.moveY = 0f
                }
            }
            Role.LOOK -> if (lookPointer == id) lookPointer = -1
            Role.FIRE -> {
                // Só solta o gatilho quando nenhum outro dedo continua no botao.
                if (!roles.containsValue(Role.FIRE)) InputState.firing = false
            }
            else -> Unit
        }
    }

    private fun releaseAll() {
        roles.clear()
        joystickPointer = -1
        lookPointer = -1
        joystickKnobX = 0f
        joystickKnobY = 0f
        InputState.moveX = 0f
        InputState.moveY = 0f
        InputState.firing = false
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float = hypot(x1 - x2, y1 - y2)

    // ========================================================================
    // Desenho
    // ========================================================================

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        drawVignette(canvas, w, h)
        drawCrosshair(canvas, w, h)
        drawTopBar(canvas, w)
        drawHealth(canvas, h)
        drawAmmo(canvas, w, h)
        drawJoystick(canvas)
        drawButtons(canvas)
        drawMessages(canvas, w, h)

        // O HUD precisa se redesenhar continuamente junto com o OpenGL.
        postInvalidateOnAnimation()
    }

    private fun drawVignette(canvas: Canvas, w: Float, h: Float) {
        val paint = vignettePaint ?: return
        val healthFraction = game.health / game.maxHealth
        val damaged = (game.damageFlash / 0.5f).coerceIn(0f, 1f)
        val low = if (healthFraction < 0.4f) (0.4f - healthFraction) / 0.4f else 0f
        val strength = maxOf(damaged, low * 0.85f)
        if (strength <= 0.01f) return

        val previousAlpha = paint.alpha
        paint.alpha = (strength * 255f).toInt().coerceIn(0, 255)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.alpha = previousAlpha
    }

    private fun drawCrosshair(canvas: Canvas, w: Float, h: Float) {
        val cx = w / 2f
        val cy = h / 2f
        val gap = dp(5f) + game.spread * 380f * density
        val length = dp(9f)

        val hit = game.hitMarker > 0f
        strokePaint.color = if (hit) Color.rgb(255, 70, 60) else Color.rgb(120, 255, 140)
        strokePaint.strokeWidth = dp(2f)
        strokePaint.style = Paint.Style.STROKE

        canvas.drawLine(cx, cy - gap - length, cx, cy - gap, strokePaint)
        canvas.drawLine(cx, cy + gap, cx, cy + gap + length, strokePaint)
        canvas.drawLine(cx - gap - length, cy, cx - gap, cy, strokePaint)
        canvas.drawLine(cx + gap, cy, cx + gap + length, cy, strokePaint)

        fillPaint.color = strokePaint.color
        fillPaint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, dp(1.6f), fillPaint)

        if (hit) {
            // Marcador de acerto em X.
            strokePaint.color = Color.rgb(255, 255, 255)
            strokePaint.strokeWidth = dp(3f)
            val size = dp(11f)
            val offset = dp(7f)
            canvas.drawLine(cx - offset - size, cy - offset - size, cx - offset, cy - offset, strokePaint)
            canvas.drawLine(cx + offset, cy + offset, cx + offset + size, cy + offset + size, strokePaint)
            canvas.drawLine(cx + offset + size, cy - offset - size, cx + offset, cy - offset, strokePaint)
            canvas.drawLine(cx - offset, cy + offset, cx - offset - size, cy + offset + size, strokePaint)
        }
    }

    private fun drawTopBar(canvas: Canvas, w: Float) {
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = dp(19f)
        textPaint.color = Color.rgb(255, 214, 120)
        canvas.drawText("ONDA ${game.wave}", w / 2f, dp(30f), textPaint)

        textPaint.textSize = dp(15f)
        textPaint.color = Color.rgb(230, 236, 245)
        canvas.drawText("INIMIGOS: ${game.enemiesLeft}", w / 2f, dp(52f), textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.textSize = dp(17f)
        textPaint.color = Color.rgb(150, 220, 255)
        canvas.drawText("PONTOS ${game.score}", w - dp(20f), dp(32f), textPaint)

        textPaint.textSize = dp(13f)
        textPaint.color = Color.rgb(170, 180, 195)
        canvas.drawText("ABATES ${game.kills}", w - dp(20f), dp(52f), textPaint)
    }

    private fun drawHealth(canvas: Canvas, h: Float) {
        val x = dp(22f)
        val y = h - dp(58f)
        val barWidth = dp(190f)
        val barHeight = dp(18f)

        rect.set(x - dp(4f), y - dp(4f), x + barWidth + dp(4f), y + barHeight + dp(4f))
        fillPaint.style = Paint.Style.FILL
        fillPaint.color = Color.argb(150, 0, 0, 0)
        canvas.drawRoundRect(rect, dp(6f), dp(6f), fillPaint)

        fillPaint.color = Color.rgb(48, 30, 30)
        canvas.drawRect(x, y, x + barWidth, y + barHeight, fillPaint)

        val fraction = (game.health / game.maxHealth).coerceIn(0f, 1f)
        fillPaint.color = when {
            fraction > 0.5f -> Color.rgb(64, 196, 96)
            fraction > 0.25f -> Color.rgb(228, 176, 48)
            else -> Color.rgb(216, 56, 48)
        }
        canvas.drawRect(x, y, x + barWidth * fraction, y + barHeight, fillPaint)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = dp(13f)
        textPaint.color = Color.rgb(220, 226, 236)
        canvas.drawText("VIDA ${game.health.toInt()}", x, y - dp(8f), textPaint)
    }

    private fun drawAmmo(canvas: Canvas, w: Float, h: Float) {
        val baseY = h - dp(190f)

        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.textSize = dp(30f)
        textPaint.color = if (game.ammo <= 6) Color.rgb(255, 90, 70) else Color.rgb(255, 255, 255)
        canvas.drawText("${game.ammo}", w - dp(24f), baseY, textPaint)

        val ammoWidth = textPaint.measureText("${game.ammo}")
        textPaint.textSize = dp(16f)
        textPaint.color = Color.rgb(170, 180, 195)
        canvas.drawText("${game.reserve}", w - dp(24f) - ammoWidth - dp(6f), baseY, textPaint)

        textPaint.textSize = dp(12f)
        textPaint.color = Color.rgb(150, 158, 172)
        canvas.drawText("MUNICAO", w - dp(24f), baseY + dp(16f), textPaint)

        if (game.reloading) {
            val barWidth = dp(110f)
            val barX = w - dp(24f) - barWidth
            val barY = baseY + dp(26f)
            fillPaint.style = Paint.Style.FILL
            fillPaint.color = Color.rgb(40, 44, 52)
            canvas.drawRect(barX, barY, barX + barWidth, barY + dp(8f), fillPaint)
            fillPaint.color = Color.rgb(255, 200, 80)
            canvas.drawRect(barX, barY, barX + barWidth * game.reloadProgress, barY + dp(8f), fillPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.textSize = dp(12f)
            textPaint.color = Color.rgb(255, 210, 120)
            canvas.drawText("RECARREGANDO", w - dp(24f), barY + dp(24f), textPaint)
        }
    }

    private fun drawJoystick(canvas: Canvas) {
        val active = joystickPointer >= 0
        val cx = if (active) joystickOriginX else joystickRestX
        val cy = if (active) joystickOriginY else joystickRestY

        fillPaint.style = Paint.Style.STROKE
        fillPaint.strokeWidth = dp(3f)
        fillPaint.color = Color.argb(if (active) 170 else 70, 235, 240, 250)
        canvas.drawCircle(cx, cy, joystickRadius, fillPaint)

        fillPaint.style = Paint.Style.FILL
        fillPaint.color = Color.argb(if (active) 190 else 80, 90, 210, 150)
        canvas.drawCircle(cx + joystickKnobX, cy + joystickKnobY, dp(34f), fillPaint)
    }

    private fun drawButtons(canvas: Canvas) {
        // Tiro
        fillPaint.style = Paint.Style.FILL
        fillPaint.color = if (InputState.firing) Color.argb(190, 255, 90, 70) else Color.argb(140, 230, 70, 60)
        canvas.drawCircle(fireX, fireY, fireRadius, fillPaint)
        strokePaint.color = Color.argb(210, 255, 200, 190)
        strokePaint.strokeWidth = dp(3f)
        canvas.drawCircle(fireX, fireY, fireRadius, strokePaint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = dp(15f)
        textPaint.color = Color.WHITE
        canvas.drawText("ATIRAR", fireX, fireY + dp(5f), textPaint)

        // Pulo
        fillPaint.color = Color.argb(140, 90, 140, 230)
        canvas.drawCircle(jumpX, jumpY, jumpRadius, fillPaint)
        strokePaint.color = Color.argb(200, 200, 220, 255)
        canvas.drawCircle(jumpX, jumpY, jumpRadius, strokePaint)
        textPaint.textSize = dp(12f)
        canvas.drawText("PULO", jumpX, jumpY + dp(4f), textPaint)

        // Recarga
        fillPaint.color = Color.argb(140, 200, 170, 70)
        canvas.drawCircle(reloadX, reloadY, reloadRadius, fillPaint)
        strokePaint.color = Color.argb(200, 255, 235, 190)
        canvas.drawCircle(reloadX, reloadY, reloadRadius, strokePaint)
        textPaint.textSize = dp(11f)
        canvas.drawText("RECARGA", reloadX, reloadY + dp(4f), textPaint)
    }

    private fun drawMessages(canvas: Canvas, w: Float, h: Float) {
        val cx = w / 2f
        val cy = h / 2f

        if (game.state == GameState.GAME_OVER) {
            fillPaint.style = Paint.Style.FILL
            fillPaint.color = Color.argb(170, 0, 0, 0)
            canvas.drawRect(0f, cy - dp(110f), w, cy + dp(110f), fillPaint)

            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = dp(42f)
            textPaint.color = Color.rgb(230, 60, 50)
            canvas.drawText("VOCE MORREU", cx, cy - dp(30f), textPaint)

            textPaint.textSize = dp(20f)
            textPaint.color = Color.rgb(235, 240, 248)
            canvas.drawText("PONTOS ${game.score}  -  ABATES ${game.kills}", cx, cy + dp(10f), textPaint)
            canvas.drawText("CHEGOU NA ONDA ${game.wave}", cx, cy + dp(38f), textPaint)

            textPaint.textSize = dp(18f)
            textPaint.color = Color.rgb(255, 214, 120)
            canvas.drawText("TOQUE PARA JOGAR DE NOVO", cx, cy + dp(78f), textPaint)
            return
        }

        val msg = game.message
        if (game.messageTimer > 0f && msg.isNotEmpty()) {
            val alpha = min(1f, game.messageTimer) * 255f
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = dp(36f)
            textPaint.color = Color.argb(alpha.toInt(), 255, 220, 120)
            canvas.drawText(msg, cx, cy - dp(20f), textPaint)

            if (game.state == GameState.WAVE_CLEAR) {
                textPaint.textSize = dp(18f)
                textPaint.color = Color.argb((alpha * 0.9f).toInt(), 230, 236, 246)
                canvas.drawText("PREPARE-SE PARA A PROXIMA ONDA", cx, cy + dp(16f), textPaint)
            }
        }

        // Dica de controle nos primeiros segundos.
        if (game.time < 9f && game.state == GameState.PLAYING) {
            val alpha = ((9f - game.time).coerceAtMost(3f) / 3f * 220f).toInt()
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = dp(14f)
            textPaint.color = Color.argb(alpha, 220, 228, 240)
            canvas.drawText("ESQUERDA: JOYSTICK  -  DIREITA: ARRASTE PARA OLHAR", cx, h - dp(20f), textPaint)
        }
    }
}
