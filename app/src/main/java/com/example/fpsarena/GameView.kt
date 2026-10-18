package com.example.fpsarena

import android.content.Context
import android.opengl.GLSurfaceView
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent

/**
 * SurfaceView do OpenGL. Tambem aceita teclado e mouse, o que ajuda a testar
 * no emulador ou com um controle/teclado Bluetooth no celular.
 */
class GameView(context: Context, game: Game) : GLSurfaceView(context) {

    private val pressedKeys = HashSet<Int>()

    private var mouseX = 0f
    private var mouseY = 0f
    private var mouseInitialized = false

    init {
        setEGLContextClientVersion(2)
        setRenderer(GameRenderer(game))
        renderMode = RENDERMODE_CONTINUOUSLY
        preserveEGLContextOnPause = true
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!isGameKey(keyCode)) return super.onKeyDown(keyCode, event)

        if (event.repeatCount == 0) {
            when (keyCode) {
                KeyEvent.KEYCODE_SPACE -> InputState.requestJump()
                KeyEvent.KEYCODE_R -> InputState.requestReload()
            }
        }
        pressedKeys.add(keyCode)
        applyKeys()
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (!isGameKey(keyCode)) return super.onKeyUp(keyCode, event)
        pressedKeys.remove(keyCode)
        applyKeys()
        return true
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val fromMouse = (event.source and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE
        if (fromMouse && event.actionMasked == MotionEvent.ACTION_HOVER_MOVE) {
            if (!mouseInitialized) {
                mouseInitialized = true
            } else {
                InputState.addLook((event.x - mouseX) * 1.8f, (event.y - mouseY) * 1.8f)
            }
            mouseX = event.x
            mouseY = event.y
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    private fun applyKeys() {
        var moveX = 0f
        var moveY = 0f

        if (pressedKeys.contains(KeyEvent.KEYCODE_W) || pressedKeys.contains(KeyEvent.KEYCODE_DPAD_UP)) moveY += 1f
        if (pressedKeys.contains(KeyEvent.KEYCODE_S) || pressedKeys.contains(KeyEvent.KEYCODE_DPAD_DOWN)) moveY -= 1f
        if (pressedKeys.contains(KeyEvent.KEYCODE_A) || pressedKeys.contains(KeyEvent.KEYCODE_DPAD_LEFT)) moveX -= 1f
        if (pressedKeys.contains(KeyEvent.KEYCODE_D) || pressedKeys.contains(KeyEvent.KEYCODE_DPAD_RIGHT)) moveX += 1f

        InputState.moveX = moveX
        InputState.moveY = moveY
        InputState.firing = pressedKeys.contains(KeyEvent.KEYCODE_CTRL_LEFT) ||
            pressedKeys.contains(KeyEvent.KEYCODE_ENTER) ||
            pressedKeys.contains(KeyEvent.KEYCODE_DPAD_CENTER) ||
            pressedKeys.contains(KeyEvent.KEYCODE_BUTTON_R1)
    }

    private fun isGameKey(keyCode: Int): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D,
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_R,
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BUTTON_R1 -> true
            else -> false
        }
    }
}
