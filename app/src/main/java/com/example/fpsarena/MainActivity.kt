package com.example.fpsarena

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.Vibrator
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout

/**
 * Monta a pilha de telas do jogo: o SurfaceView do OpenGL embaixo e a HudView
 * (controles + HUD) por cima.
 */
class MainActivity : Activity() {

    private lateinit var game: Game
    private lateinit var gameView: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        game = Game(vibratorOrNull())
        gameView = GameView(this, game)
        val hudView = HudView(this, game)

        val root = FrameLayout(this)
        val matchParent = FrameLayout.LayoutParams.MATCH_PARENT
        root.addView(gameView, FrameLayout.LayoutParams(matchParent, matchParent))
        root.addView(hudView, FrameLayout.LayoutParams(matchParent, matchParent))
        setContentView(root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Foco na GameView para teclado/controle Bluetooth funcionarem.
        gameView.requestFocus()
        goFullscreen()
    }

    @Suppress("DEPRECATION")
    private fun vibratorOrNull(): Vibrator? =
        getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    /** Modo imersivo: sem barra de status nem de navegacao durante o jogo. */
    @Suppress("DEPRECATION")
    private fun goFullscreen() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) goFullscreen()
    }

    override fun onResume() {
        super.onResume()
        gameView.onResume()
        goFullscreen()
    }

    override fun onPause() {
        super.onPause()
        gameView.onPause()
        // Nao deixa um toque preso quando o app volta do background.
        InputState.clear()
    }
}
