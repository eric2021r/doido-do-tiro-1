package com.example.fpsarena

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Desenha o mundo em OpenGL ES 2.0 e roda o update do jogo a cada frame.
 * O HUD e o menu nao passam por aqui: sao desenhados por Canvas por cima.
 */
class GameRenderer(private val game: Game) : GLSurfaceView.Renderer {

    private lateinit var shader: ShaderProgram
    private lateinit var cube: Mesh
    private lateinit var flashQuad: Mesh

    private var floorTexture = 0
    private var wallTexture = 0
    private var crateTexture = 0

    // Matrizes reaproveitadas (nada de alocacao no loop de render).
    private val identityMatrix = FloatArray(16)
    private val projection = FloatArray(16)
    private val weaponProjection = FloatArray(16)
    private val view = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val gunMatrix = FloatArray(16)
    private val partMatrix = FloatArray(16)
    private val fullMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)

    // Passada atual: mudam entre a camera do mundo e a camera da arma.
    private lateinit var activeView: FloatArray
    private lateinit var activeProjection: FloatArray

    private val tracerBuffer: FloatBuffer = ByteBuffer.allocateDirect(6 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()

    private var aspect = 16f / 9f
    private var lastFrameNanos = 0L
    private var lastDelta = 0.016f
    private var currentFov = 72f

    private val eye = FloatArray(3)
    private val forward = FloatArray(3)
    private val right = FloatArray(3)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(FOG_RED, FOG_GREEN, FOG_BLUE, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)
        GLES20.glFrontFace(GLES20.GL_CCW)

        shader = ShaderProgram()
        shader.use()

        // Atributos 0, 1 e 2 ficam ligados para sempre (as malhas sempre os usam).
        GLES20.glEnableVertexAttribArray(0)
        GLES20.glEnableVertexAttribArray(1)
        GLES20.glEnableVertexAttribArray(2)

        cube = MeshFactory.unitCube()
        cube.upload()
        flashQuad = MeshFactory.quad()
        flashQuad.upload()

        floorTexture = TextureFactory.floorTexture()
        wallTexture = TextureFactory.wallTexture()
        crateTexture = TextureFactory.crateTexture()

        Matrix.setIdentityM(identityMatrix, 0)
        lastFrameNanos = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        aspect = if (height == 0) 16f / 9f else width.toFloat() / height.toFloat()
        // A projecao do mundo e recalculada a cada frame (a luneta muda o FOV).
        Matrix.perspectiveM(projection, 0, 72f, aspect, 0.08f, 180f)
        Matrix.perspectiveM(weaponProjection, 0, 62f, aspect, 0.01f, 30f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = (now - lastFrameNanos) / 1_000_000_000f
        lastFrameNanos = now
        lastDelta = dt.coerceIn(0f, 0.1f)

        game.update(dt)
        render()
    }

    // ========================================================================
    // Render
    // ========================================================================

    private fun render() {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glEnable(GLES20.GL_CULL_FACE)

        val menu = game.state == GameState.MENU

        // A luneta da sniper fecha o FOV suavemente.
        val targetFov = if (game.aiming) 26f else 72f
        currentFov += (targetFov - currentFov) * min(1f, lastDelta * 12f)
        Matrix.perspectiveM(projection, 0, currentFov, aspect, 0.08f, 180f)

        if (menu) {
            // Camera cinematografica orbitando a arena, so como plano de fundo.
            val angle = game.menuAngle
            eye[0] = cos(angle) * Game.MENU_ORBIT_RADIUS
            eye[1] = Game.MENU_CAMERA_HEIGHT
            eye[2] = sin(angle) * Game.MENU_ORBIT_RADIUS
            Matrix.setLookAtM(
                view, 0,
                eye[0], eye[1], eye[2],
                0f, 1.3f, 0f,
                0f, 1f, 0f
            )
        } else {
            val yaw = game.aimYaw
            val pitch = game.aimPitch
            val cosPitch = cos(pitch)
            val sinPitch = sin(pitch)

            forward[0] = -sin(yaw) * cosPitch
            forward[1] = sinPitch
            forward[2] = -cos(yaw) * cosPitch
            right[0] = cos(yaw)
            right[1] = 0f
            right[2] = -sin(yaw)

            eye[0] = game.px
            eye[1] = game.py + Game.EYE_HEIGHT - 0.05f * abs(game.bob)
            eye[2] = game.pz

            Matrix.setLookAtM(
                view, 0,
                eye[0], eye[1], eye[2],
                eye[0] + forward[0], eye[1] + forward[1], eye[2] + forward[2],
                0f, 1f, 0f
            )
        }

        shader.use()
        activeView = view
        activeProjection = projection
        shader.setCameraPosition(eye[0], eye[1], eye[2])
        shader.setFog(FOG_START, FOG_END, FOG_RED, FOG_GREEN, FOG_BLUE)
        shader.setLightDirection(LIGHT_DIR[0], LIGHT_DIR[1], LIGHT_DIR[2])

        drawArena()
        drawGrenades()
        drawEnemies()
        if (!menu) drawTracer()
        drawExplosions()
        if (!menu && !game.aiming) drawWeapon()
    }

    private fun drawArena() {
        GLES20.glEnable(GLES20.GL_CULL_FACE)

        // Piso: caixa fina cujo topo fica exatamente em y = 0.
        drawMesh(
            cube, identityMatrix,
            0f, -0.2f, 0f, 40f, 0.4f, 40f,
            floorTexture, 20f, 20f,
            1f, 1f, 1f
        )

        for (i in game.level.blocks.indices) {
            val block = game.level.blocks[i]
            val box = block.box
            when (block.type) {
                BlockType.WALL -> drawMesh(
                    cube, identityMatrix,
                    box.centerX, box.centerY, box.centerZ,
                    box.sizeX, box.sizeY, box.sizeZ,
                    wallTexture, box.sizeX * 0.5f, box.sizeY * 0.5f,
                    1f, 1f, 1f
                )
                BlockType.PILLAR -> drawMesh(
                    cube, identityMatrix,
                    box.centerX, box.centerY, box.centerZ,
                    box.sizeX, box.sizeY, box.sizeZ,
                    wallTexture, 1.2f, 3f,
                    0.92f, 0.88f, 0.86f
                )
                BlockType.CRATE -> drawMesh(
                    cube, identityMatrix,
                    box.centerX, box.centerY, box.centerZ,
                    box.sizeX, box.sizeY, box.sizeZ,
                    crateTexture, 1f, 1f,
                    1f, 1f, 1f
                )
            }
        }
    }

    private fun drawEnemies() {
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        var blending = false

        for (i in game.enemies.indices) {
            val enemy = game.enemies[i]
            val alpha = enemy.corpseAlpha

            // Corpos desaparecendo precisam de transparencia.
            if (!enemy.alive && !blending) {
                GLES20.glEnable(GLES20.GL_BLEND)
                GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
                blending = true
            }

            val bob = if (enemy.alive) 0.05f * abs(sin(enemy.walkPhase)) else 0f
            val armSwing = 0.16f * sin(enemy.walkPhase)

            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, enemy.x, bob, enemy.z)
            Matrix.rotateM(modelMatrix, 0, Math.toDegrees(enemy.yaw.toDouble()).toFloat(), 0f, 1f, 0f)
            // Tomba para a frente girando em volta dos pes.
            if (!enemy.alive) {
                Matrix.rotateM(modelMatrix, 0, -88f * enemy.fallFactor, 1f, 0f, 0f)
            }

            val flash = if (enemy.alive && enemy.hitFlash > 0f) enemy.hitFlash * 3.5f else 0f
            val tintR = 1f + flash
            val tintG = 1f + flash * 0.6f
            val tintB = 1f + flash * 0.6f

            fun part(
                tx: Float, ty: Float, tz: Float,
                sx: Float, sy: Float, sz: Float,
                r: Float, g: Float, b: Float,
                unlit: Boolean = false
            ) {
                drawMesh(
                    cube, modelMatrix,
                    tx, ty, tz, sx, sy, sz,
                    0, 1f, 1f, r, g, b,
                    alpha = alpha, unlit = unlit,
                    tintR = tintR, tintG = tintG, tintB = tintB
                )
            }

            // Pernas
            part(-0.16f, 0.4f, 0f, 0.22f, 0.8f, 0.28f, 0.26f, 0.25f, 0.27f)
            part(0.16f, 0.4f, 0f, 0.22f, 0.8f, 0.28f, 0.26f, 0.25f, 0.27f)

            // Tronco
            part(0f, 1.12f, 0f, 0.64f, 0.72f, 0.36f, 0.70f, 0.16f, 0.14f)

            // Bracos (balancam ao andar)
            part(-0.42f, 1.08f, armSwing, 0.16f, 0.62f, 0.16f, 0.62f, 0.18f, 0.16f)
            part(0.42f, 1.08f, -armSwing, 0.16f, 0.62f, 0.16f, 0.62f, 0.18f, 0.16f)

            // Cabeca
            part(0f, 1.63f, 0f, 0.34f, 0.34f, 0.32f, 0.86f, 0.70f, 0.56f)

            // "Olhos" brilhantes, marcando o lado que encara o jogador.
            if (enemy.alive) {
                part(-0.09f, 1.68f, -0.17f, 0.09f, 0.06f, 0.04f, 1f, 0.35f, 0.12f, unlit = true)
                part(0.09f, 1.68f, -0.17f, 0.09f, 0.06f, 0.04f, 1f, 0.35f, 0.12f, unlit = true)
            }
        }

        if (blending) GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun drawGrenades() {
        for (i in game.grenades.indices) {
            val grenade = game.grenades[i]

            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, grenade.x, grenade.y, grenade.z)
            val spinDegrees = Math.toDegrees(grenade.spin.toDouble()).toFloat()
            Matrix.rotateM(modelMatrix, 0, spinDegrees, 0f, 1f, 0f)
            Matrix.rotateM(modelMatrix, 0, spinDegrees * 0.4f, 1f, 0f, 0f)

            drawMesh(
                cube, modelMatrix,
                0f, 0f, 0f, 0.17f, 0.21f, 0.17f,
                0, 1f, 1f, 0.22f, 0.28f, 0.16f
            )
            drawMesh(
                cube, modelMatrix,
                0f, 0.13f, 0f, 0.07f, 0.06f, 0.07f,
                0, 1f, 1f, 0.42f, 0.44f, 0.46f
            )
            // Luz do pavio piscando mais rapido no fim.
            val blinking = ((grenade.fuse * 9f).toInt() % 2 == 0)
            val glow = if (blinking) 1f else 0.15f
            drawMesh(
                cube, modelMatrix,
                0f, 0.22f, 0f, 0.05f, 0.05f, 0.05f,
                0, 1f, 1f, 1f * glow, 0.28f * glow, 0.16f * glow,
                unlit = true
            )
        }
    }

    private fun drawTracer() {
        if (game.tracerTimer <= 0f) return

        val alpha = (game.tracerTimer / 0.05f).coerceIn(0f, 1f)
        tracerBuffer.clear()
        tracerBuffer.put(game.tracerStart[0])
        tracerBuffer.put(game.tracerStart[1])
        tracerBuffer.put(game.tracerStart[2])
        tracerBuffer.put(game.tracerEnd[0])
        tracerBuffer.put(game.tracerEnd[1])
        tracerBuffer.put(game.tracerEnd[2])
        tracerBuffer.position(0)

        // Vertices no cliente: desliga o VBO preso e as UVs/normais fixas.
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glDisableVertexAttribArray(1)
        GLES20.glDisableVertexAttribArray(2)
        GLES20.glVertexAttrib3f(1, 0f, 0f, 1f)
        GLES20.glVertexAttrib2f(2, 0f, 0f)
        GLES20.glVertexAttribPointer(0, 3, GLES20.GL_FLOAT, false, 0, tracerBuffer)

        Matrix.multiplyMM(mvpMatrix, 0, projection, 0, view, 0)
        shader.setModel(identityMatrix)
        shader.setMvp(mvpMatrix)
        shader.setTextureEnabled(false)
        shader.setUnlit(true)
        shader.setColor(1f, 0.92f, 0.5f)
        shader.setTint(1f, 1f, 1f)
        shader.setAlpha(alpha)
        GLES20.glLineWidth(3f)
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, 2)

        GLES20.glEnableVertexAttribArray(1)
        GLES20.glEnableVertexAttribArray(2)
    }

    /** Explosao: dois cubos aditivos que crescem e apagam. */
    private fun drawExplosions() {
        if (game.explosions.isEmpty()) return

        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)
        GLES20.glDepthMask(false)
        // Sem neblina: aditivo com neblina clarearia a tela inteira.
        shader.setFog(1000f, 2000f, 0f, 0f, 0f)

        for (i in game.explosions.indices) {
            val explosion = game.explosions[i]
            val t = (explosion.t / 0.55f).coerceIn(0f, 1f)
            val fade = (1f - t) * (1f - t)

            val outer = 0.8f + t * (Game.GRENADE_RADIUS * 1.7f)
            drawMesh(
                cube, identityMatrix,
                explosion.x, explosion.y, explosion.z, outer, outer, outer,
                0, 1f, 1f, 1f, 0.55f, 0.18f,
                alpha = fade * 0.75f, unlit = true
            )

            val inner = outer * 0.55f
            drawMesh(
                cube, identityMatrix,
                explosion.x, explosion.y, explosion.z, inner, inner, inner,
                0, 1f, 1f, 1f, 0.93f, 0.72f,
                alpha = fade, unlit = true
            )

            val core = outer * 0.28f
            drawMesh(
                cube, identityMatrix,
                explosion.x, explosion.y, explosion.z, core, core, core,
                0, 1f, 1f, 1f, 1f, 1f,
                alpha = fade, unlit = true
            )
        }

        shader.setFog(FOG_START, FOG_END, FOG_RED, FOG_GREEN, FOG_BLUE)
        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
    }

    // ========================================================================
    // Arma em primeira pessoa
    // ========================================================================

    private fun gun(
        tx: Float, ty: Float, tz: Float,
        sx: Float, sy: Float, sz: Float,
        r: Float, g: Float, b: Float,
        unlit: Boolean = false,
        alpha: Float = 1f
    ) {
        drawMesh(
            cube, gunMatrix,
            tx, ty, tz, sx, sy, sz,
            0, 1f, 1f, r, g, b,
            alpha = alpha, unlit = unlit
        )
    }

    private fun drawWeapon() {
        // A arma vive no espaco da camera, entao a view e a identidade e o
        // fundo de profundidade e limpo para ela nunca furar as paredes.
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT)
        activeView = identityMatrix
        activeProjection = weaponProjection
        shader.setCameraPosition(0f, 0f, 0f)

        val recoil = game.recoil
        val drop = game.switchDrop
        Matrix.setIdentityM(gunMatrix, 0)

        // Coice: a arma sobe e vem em direcao a camera. A troca derruba o cano.
        Matrix.translateM(
            gunMatrix, 0,
            0.016f * game.bob,
            -0.014f * abs(game.bob) - drop * 0.45f,
            recoil * 0.075f
        )
        Matrix.rotateM(gunMatrix, 0, -recoil * 7f - drop * 25f, 1f, 0f, 0f)
        Matrix.rotateM(gunMatrix, 0, 1.5f, 0f, 0f, 1f)

        val muzzleZ = when (game.currentWeapon.type) {
            WeaponType.PISTOL -> {
                drawPistol()
                -0.68f
            }
            WeaponType.RIFLE -> {
                drawRifle()
                -1.06f
            }
            WeaponType.SHOTGUN -> {
                drawShotgun()
                -1.40f
            }
            WeaponType.SNIPER -> {
                drawSniper()
                -1.52f
            }
            WeaponType.GRENADE -> {
                drawGrenadeInHand()
                Float.NaN
            }
        }

        if (!muzzleZ.isNaN()) drawMuzzleFlash(muzzleZ)

        // Volta para a camera do mundo no proximo frame.
        activeView = view
        activeProjection = projection
    }

    private fun drawPistol() {
        // Slide
        gun(0.17f, -0.15f, -0.46f, 0.075f, 0.085f, 0.30f, 0.20f, 0.21f, 0.23f)
        // Cano
        gun(0.17f, -0.14f, -0.64f, 0.050f, 0.050f, 0.12f, 0.12f, 0.13f, 0.15f)
        // Empunhadura (inclinada para tras)
        gun(0.17f, -0.30f, -0.36f, 0.080f, 0.22f, 0.12f, 0.13f, 0.14f, 0.16f)
        // Miral
        gun(0.17f, -0.095f, -0.58f, 0.018f, 0.028f, 0.02f, 0.85f, 0.85f, 0.88f)
        gun(0.17f, -0.095f, -0.35f, 0.030f, 0.028f, 0.02f, 0.85f, 0.85f, 0.88f)
        // Mao
        gun(0.17f, -0.28f, -0.32f, 0.10f, 0.10f, 0.14f, 0.18f, 0.20f, 0.19f)
    }

    private fun drawRifle() {
        // Corpo
        gun(0.20f, -0.20f, -0.50f, 0.13f, 0.16f, 0.52f, 0.17f, 0.18f, 0.20f)
        // Trilho superior
        gun(0.20f, -0.11f, -0.58f, 0.06f, 0.04f, 0.44f, 0.11f, 0.12f, 0.14f)
        // Cano
        gun(0.20f, -0.16f, -0.86f, 0.05f, 0.05f, 0.32f, 0.10f, 0.10f, 0.12f)
        // Carregador
        gun(0.20f, -0.35f, -0.44f, 0.10f, 0.22f, 0.11f, 0.14f, 0.15f, 0.17f)
        // Empunhadura
        gun(0.20f, -0.34f, -0.30f, 0.09f, 0.20f, 0.11f, 0.16f, 0.17f, 0.19f)
        // Coronha
        gun(0.20f, -0.24f, -0.22f, 0.10f, 0.14f, 0.18f, 0.15f, 0.16f, 0.18f)
        // Miras
        gun(0.20f, -0.07f, -0.64f, 0.03f, 0.05f, 0.03f, 0.85f, 0.85f, 0.88f)
        gun(0.20f, -0.10f, -1.00f, 0.02f, 0.06f, 0.02f, 0.85f, 0.85f, 0.88f)
    }

    private fun drawShotgun() {
        // Coronha de madeira
        gun(0.20f, -0.26f, -0.18f, 0.10f, 0.15f, 0.28f, 0.29f, 0.18f, 0.10f)
        // Caixa da culatra
        gun(0.20f, -0.21f, -0.52f, 0.12f, 0.18f, 0.44f, 0.15f, 0.16f, 0.18f)
        // Bomba (madeira)
        gun(0.20f, -0.21f, -0.86f, 0.11f, 0.13f, 0.24f, 0.31f, 0.20f, 0.11f)
        // Cano duplo
        gun(0.17f, -0.14f, -1.06f, 0.055f, 0.09f, 0.64f, 0.10f, 0.10f, 0.12f)
        gun(0.23f, -0.14f, -1.06f, 0.055f, 0.09f, 0.64f, 0.10f, 0.10f, 0.12f)
        // Mira
        gun(0.20f, -0.06f, -1.32f, 0.02f, 0.04f, 0.02f, 0.85f, 0.85f, 0.88f)
    }

    private fun drawSniper() {
        // Coronha
        gun(0.20f, -0.27f, -0.20f, 0.10f, 0.16f, 0.26f, 0.15f, 0.16f, 0.18f)
        // Corpo
        gun(0.20f, -0.23f, -0.62f, 0.11f, 0.16f, 0.62f, 0.16f, 0.17f, 0.19f)
        // Luneta
        gun(0.20f, -0.07f, -0.60f, 0.075f, 0.075f, 0.44f, 0.10f, 0.11f, 0.13f)
        // Lente
        gun(0.20f, -0.07f, -0.84f, 0.095f, 0.095f, 0.03f, 0.16f, 0.34f, 0.52f, unlit = true)
        // Carregador
        gun(0.20f, -0.37f, -0.50f, 0.08f, 0.17f, 0.10f, 0.14f, 0.15f, 0.17f)
        // Cano
        gun(0.20f, -0.21f, -1.12f, 0.055f, 0.055f, 0.58f, 0.10f, 0.10f, 0.12f)
        // Bipé
        gun(0.13f, -0.36f, -1.08f, 0.028f, 0.20f, 0.028f, 0.13f, 0.14f, 0.16f)
        gun(0.27f, -0.36f, -1.08f, 0.028f, 0.20f, 0.028f, 0.13f, 0.14f, 0.16f)
    }

    private fun drawGrenadeInHand() {
        // Antebraco e mao
        gun(0.24f, -0.30f, -0.30f, 0.105f, 0.105f, 0.36f, 0.20f, 0.22f, 0.20f)
        gun(0.22f, -0.33f, -0.50f, 0.115f, 0.10f, 0.14f, 0.17f, 0.19f, 0.17f)
        // Granada
        gun(0.22f, -0.38f, -0.60f, 0.15f, 0.185f, 0.15f, 0.21f, 0.27f, 0.15f)
        // Tampao e alavanca
        gun(0.22f, -0.275f, -0.60f, 0.07f, 0.05f, 0.07f, 0.40f, 0.42f, 0.44f)
        gun(0.29f, -0.36f, -0.60f, 0.02f, 0.14f, 0.05f, 0.45f, 0.47f, 0.49f)
    }

    private fun drawMuzzleFlash(muzzleZ: Float) {
        if (game.muzzleFlash <= 0f) return

        val strength = (game.muzzleFlash / 0.055f).coerceIn(0f, 1f)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)
        GLES20.glDepthMask(false)
        GLES20.glDisable(GLES20.GL_CULL_FACE)

        drawMesh(
            flashQuad, gunMatrix,
            0.20f, -0.16f, muzzleZ, 0.34f, 0.34f, 0.34f,
            0, 1f, 1f, 1f, 0.82f, 0.35f,
            alpha = strength, unlit = true
        )
        drawMesh(
            flashQuad, gunMatrix,
            0.20f, -0.16f, muzzleZ + 0.01f, 0.62f, 0.62f, 0.62f,
            0, 1f, 1f, 1f, 0.95f, 0.7f,
            alpha = strength * 0.55f, unlit = true
        )

        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
    }

    /**
     * Desenha uma malha com o transform local (translacao + escala) aplicado
     * sobre [parentModel], usando a projecao/view ativas.
     */
    private fun drawMesh(
        mesh: Mesh,
        parentModel: FloatArray,
        tx: Float,
        ty: Float,
        tz: Float,
        sx: Float,
        sy: Float,
        sz: Float,
        texture: Int,
        uvScaleX: Float,
        uvScaleY: Float,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float = 1f,
        unlit: Boolean = false,
        tintR: Float = 1f,
        tintG: Float = 1f,
        tintB: Float = 1f
    ) {
        Matrix.setIdentityM(partMatrix, 0)
        Matrix.translateM(partMatrix, 0, tx, ty, tz)
        Matrix.scaleM(partMatrix, 0, sx, sy, sz)
        Matrix.multiplyMM(fullMatrix, 0, parentModel, 0, partMatrix, 0)

        Matrix.multiplyMM(viewMatrix, 0, activeView, 0, fullMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, activeProjection, 0, viewMatrix, 0)

        if (texture != 0) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
            GLES20.glUniform1i(shader.uTexture, 0)
            shader.setTextureEnabled(true)
        } else {
            shader.setTextureEnabled(false)
        }

        shader.setMvp(mvpMatrix)
        shader.setModel(fullMatrix)
        shader.setUvScale(uvScaleX, uvScaleY)
        shader.setColor(red, green, blue)
        shader.setTint(tintR, tintG, tintB)
        shader.setAlpha(alpha)
        shader.setUnlit(unlit)

        mesh.draw()
    }

    private companion object {
        // Neblina/skybox na mesma cor do clear: da profundidade sem custo.
        const val FOG_RED = 0.50f
        const val FOG_GREEN = 0.58f
        const val FOG_BLUE = 0.70f
        const val FOG_START = 26f
        const val FOG_END = 80f

        /** Direcao da luz do sol, ja normalizada. */
        val LIGHT_DIR: FloatArray = normalize(0.42f, 0.79f, 0.34f)

        fun normalize(x: Float, y: Float, z: Float): FloatArray {
            val length = sqrt(x * x + y * y + z * z)
            return floatArrayOf(x / length, y / length, z / length)
        }
    }
}
