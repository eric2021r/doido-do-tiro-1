package com.example.fpsarena

/**
 * Estado de entrada compartilhado entre a thread de UI (HudView) e a thread de
 * render do OpenGL. Os campos simples sao volateis; o look e o "edge" de pulo e
 * recarga sao acumulados e consumidos uma unica vez pela thread do jogo.
 */
object InputState {

    /** Deslocamento lateral do joystick, -1 (esquerda) .. 1 (direita). */
    @Volatile
    var moveX: Float = 0f

    /** Deslocamento frontal do joystick, -1 (tras) .. 1 (frente). */
    @Volatile
    var moveY: Float = 0f

    /** Verdadeiro enquanto o botao de tiro esta pressionado. */
    @Volatile
    var firing: Boolean = false

    private val lock = Any()

    private var lookDx = 0f
    private var lookDy = 0f
    private var jumpPending = false
    private var reloadPending = false

    /** Soma o arrasto do dedo usado para girar a camera. */
    fun addLook(dx: Float, dy: Float) {
        synchronized(lock) {
            lookDx += dx
            lookDy += dy
        }
    }

    /** Retorna [dx, dy] acumulados e zera o acumulador. */
    fun consumeLook(): FloatArray {
        synchronized(lock) {
            val result = floatArrayOf(lookDx, lookDy)
            lookDx = 0f
            lookDy = 0f
            return result
        }
    }

    fun requestJump() {
        synchronized(lock) { jumpPending = true }
    }

    fun consumeJump(): Boolean {
        synchronized(lock) {
            val value = jumpPending
            jumpPending = false
            return value
        }
    }

    fun requestReload() {
        synchronized(lock) { reloadPending = true }
    }

    fun consumeReload(): Boolean {
        synchronized(lock) {
            val value = reloadPending
            reloadPending = false
            return value
        }
    }

    /** Zera tudo (usado ao reiniciar a partida). */
    fun clear() {
        synchronized(lock) {
            lookDx = 0f
            lookDy = 0f
            jumpPending = false
            reloadPending = false
        }
        moveX = 0f
        moveY = 0f
        firing = false
    }
}
