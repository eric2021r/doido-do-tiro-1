package com.example.fpsarena

import android.os.Vibrator
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class GameState { PLAYING, WAVE_CLEAR, GAME_OVER }

/** Um inimigo simples do tipo "avanca e ataca corpo a corpo". */
class Enemy(x0: Float, z0: Float) {
    var x = x0
    var z = z0
    var health = 100f
    var alive = true
    var hitFlash = 0f
    var attackCooldown = 0.6f
    var yaw = 0f
    var speed = 2.2f
    var walkPhase = 0f
}

/**
 * Estado completo da partida: jogador, inimigos, arma e ondas.
 * Todo o update roda na thread de render do OpenGL; a HudView apenas le os campos.
 */
class Game(private val vibrator: Vibrator? = null) {

    companion object {
        const val EYE_HEIGHT = 1.62f
        const val PLAYER_RADIUS = 0.42f
        const val ENEMY_RADIUS = 0.5f
        const val ENEMY_HEIGHT = 1.9f
        const val ENEMY_DAMAGE = 9f
        const val MAX_SHOOT_DISTANCE = 90f

        private const val TAU = 6.2831855f
        private const val WALK_SPEED = 5.0f
        private const val JUMP_SPEED = 6.4f
        private const val GRAVITY = 19f
        private const val FIRE_INTERVAL = 0.105f
        private const val RELOAD_DURATION = 1.5f
        private const val LOOK_SENSITIVITY = 0.0042f
        private const val ENEMY_HIT_RADIUS = 0.58f
    }

    val level = Level()
    val random = Random(20261018)

    // ---------------------------------------------------------------- jogador
    var px = level.playerStart[0]
    var pz = level.playerStart[2]
    var py = 0f
    private var verticalSpeed = 0f
    private var grounded = true
    var yaw = 0f
    var pitch = 0f

    var health = 100f
    val maxHealth = 100f

    // ------------------------------------------------------------------- arma
    var ammo = 30
    val magSize = 30
    var reserve = 150
    var reloading = false
    private var reloadTimer = 0f
    private var fireCooldown = 0f
    var spread = 0f

    // ------------------------------------------------------------------ placar
    var score = 0
    var wave = 1
    var kills = 0
    var enemiesLeft = 0
    var state = GameState.PLAYING
    var message = ""
    var messageTimer = 0f
    private var waveCooldown = 0f

    // ------------------------------------------------------- efeitos visuais
    var recoil = 0f
    var muzzleFlash = 0f
    var hitMarker = 0f
    var damageFlash = 0f
    var shakeYaw = 0f
    var shakePitch = 0f
    var bob = 0f
    private var bobPhase = 0f
    private var shakeAmount = 0f
    var time = 0f

    /** Tracante do ultimo disparo (linha do cano ate o ponto de impacto). */
    var tracerTimer = 0f
    val tracerStart = FloatArray(3)
    val tracerEnd = FloatArray(3)

    val enemies = ArrayList<Enemy>()

    /** Pedido de reinicio vindo da UI; aplicado no inicio do proximo update. */
    @Volatile
    private var restartRequested = false

    /** A HudView chama isto em vez de reset(), que so roda na thread de render. */
    fun requestRestart() {
        restartRequested = true
    }

    val reloadProgress: Float
        get() = if (reloading) (1f - reloadTimer / RELOAD_DURATION).coerceIn(0f, 1f) else 1f

    val aimPitch: Float get() = (pitch + shakePitch).coerceIn(-1.45f, 1.45f)
    val aimYaw: Float get() = yaw + shakeYaw

    init {
        reset()
    }

    // ========================================================================
    // Ciclo de vida
    // ========================================================================

    fun reset() {
        px = level.playerStart[0]
        pz = level.playerStart[2]
        py = 0f
        verticalSpeed = 0f
        grounded = true
        yaw = 0f
        pitch = 0f

        health = maxHealth
        ammo = magSize
        reserve = 150
        reloading = false
        reloadTimer = 0f
        fireCooldown = 0f
        spread = 0f

        score = 0
        wave = 1
        kills = 0
        state = GameState.PLAYING
        message = ""
        messageTimer = 0f
        waveCooldown = 0f

        recoil = 0f
        muzzleFlash = 0f
        hitMarker = 0f
        damageFlash = 0f
        shakeYaw = 0f
        shakePitch = 0f
        shakeAmount = 0f
        bob = 0f
        bobPhase = 0f
        time = 0f
        tracerTimer = 0f

        InputState.clear()
        spawnWave()
    }

    private fun spawnWave() {
        enemies.clear()
        val count = min(4 + wave * 2, 20)
        var placed = 0
        var guard = 0
        while (placed < count && guard < 400) {
            guard++
            val spot = level.findSpawnPoint(px, pz, 9f, random) ?: continue
            val enemy = Enemy(spot[0], spot[1])
            enemy.speed = 2.0f + wave * 0.13f
            enemy.health = 100f
            enemy.attackCooldown = 0.7f + random.nextFloat() * 0.8f
            enemies.add(enemy)
            placed++
        }

        // Rede de seguranca: se a arena estiver muito cheia, usa pontos fixos
        // livres. A onda nunca pode ficar vazia, senao o jogo nao avanca.
        if (enemies.isEmpty()) {
            val fallback = arrayOf(
                floatArrayOf(0f, 0f),
                floatArrayOf(13f, 13f),
                floatArrayOf(-13f, -13f),
                floatArrayOf(14f, -14f),
                floatArrayOf(-15f, 15f)
            )
            for (point in fallback) {
                if (enemies.size >= count) break
                if (level.isBlocked(point[0], point[1], 0.9f)) continue
                val enemy = Enemy(point[0], point[1])
                enemy.speed = 2.0f + wave * 0.13f
                enemies.add(enemy)
            }
        }

        enemiesLeft = enemies.size
        message = "ONDA $wave"
        messageTimer = 2.2f
    }

    // ========================================================================
    // Update
    // ========================================================================

    fun update(dtRaw: Float) {
        if (restartRequested) {
            restartRequested = false
            reset()
        }

        val dt = dtRaw.coerceIn(0f, 0.05f)
        time += dt

        if (muzzleFlash > 0f) muzzleFlash -= dt
        if (hitMarker > 0f) hitMarker -= dt
        if (damageFlash > 0f) damageFlash -= dt
        if (tracerTimer > 0f) tracerTimer -= dt
        if (messageTimer > 0f) messageTimer -= dt
        recoil *= (1f - min(1f, dt * 11f))
        shakeAmount *= (1f - min(1f, dt * 6f))
        shakeYaw = (random.nextFloat() - 0.5f) * shakeAmount * 0.09f
        shakePitch = (random.nextFloat() - 0.5f) * shakeAmount * 0.09f

        // A camera continua consumindo o arrasto mesmo na tela de game over,
        // para o acumulador nao explodir ao voltar para o jogo.
        val look = InputState.consumeLook()
        if (state == GameState.GAME_OVER) {
            InputState.consumeJump()
            InputState.consumeReload()
            return
        }

        yaw -= look[0] * LOOK_SENSITIVITY
        pitch -= look[1] * LOOK_SENSITIVITY
        if (yaw > TAU || yaw < -TAU) yaw %= TAU
        pitch = pitch.coerceIn(-1.45f, 1.45f)

        updateMovement(dt)
        updateWeapon(dt)
        updateEnemies(dt)

        if (state == GameState.WAVE_CLEAR) {
            waveCooldown -= dt
            if (waveCooldown <= 0f) {
                wave++
                state = GameState.PLAYING
                health = min(maxHealth, health + 25f)
                reserve += 90
                spawnWave()
            }
        }
    }

    private fun updateMovement(dt: Float) {
        val moveX = InputState.moveX
        val moveY = InputState.moveY

        val forwardX = -sin(yaw)
        val forwardZ = -cos(yaw)
        val rightX = cos(yaw)
        val rightZ = -sin(yaw)

        var dirX = forwardX * moveY + rightX * moveX
        var dirZ = forwardZ * moveY + rightZ * moveX
        val length = sqrt(dirX * dirX + dirZ * dirZ)
        if (length > 1f) {
            dirX /= length
            dirZ /= length
        }

        val intensity = min(1f, length)
        moveWithCollision(dirX * WALK_SPEED * dt, dirZ * WALK_SPEED * dt)

        // Balanco da camera ao andar (deixa o movimento com peso).
        val movingOnGround = intensity > 0.15f && grounded
        if (movingOnGround) {
            bobPhase += dt * 9f
            bob = sin(bobPhase) * 1f
        } else {
            bob *= 0.86f
            bobPhase = 0f
        }

        if (InputState.consumeJump() && grounded) {
            verticalSpeed = JUMP_SPEED
            grounded = false
        }
        verticalSpeed -= GRAVITY * dt
        py += verticalSpeed * dt
        if (py <= 0f) {
            py = 0f
            verticalSpeed = 0f
            grounded = true
        }

        // Espalhamento do tiro: maior em movimento e ao pular.
        spread = 0.004f + intensity * 0.012f + (if (grounded) 0f else 0.018f)
    }

    private fun moveWithCollision(dx: Float, dz: Float) {
        val nextX = px + dx
        if (!level.isBlocked(nextX, pz, PLAYER_RADIUS)) px = nextX
        val nextZ = pz + dz
        if (!level.isBlocked(px, nextZ, PLAYER_RADIUS)) pz = nextZ
    }

    private fun updateWeapon(dt: Float) {
        if (fireCooldown > 0f) fireCooldown -= dt

        if (reloading) {
            reloadTimer -= dt
            if (reloadTimer <= 0f) finishReload()
            return
        }

        if (InputState.consumeReload()) {
            startReload()
            return
        }

        if (InputState.firing && fireCooldown <= 0f) {
            if (ammo > 0) shoot() else startReload()
        }
    }

    private fun startReload() {
        if (reloading || ammo >= magSize || reserve <= 0) return
        reloading = true
        reloadTimer = RELOAD_DURATION
    }

    private fun finishReload() {
        val needed = magSize - ammo
        val taken = min(needed, reserve)
        ammo += taken
        reserve -= taken
        reloading = false
        reloadTimer = 0f
    }

    private fun shoot() {
        ammo--
        fireCooldown = FIRE_INTERVAL
        recoil = min(1f, recoil + 0.5f)
        muzzleFlash = 0.055f
        shakeAmount = min(0.7f, shakeAmount + 0.1f)
        vibrate(10)

        val eyeY = py + EYE_HEIGHT
        val cp = cos(pitch)
        val sp = sin(pitch)

        var dirX = -sin(yaw) * cp + (random.nextFloat() - 0.5f) * spread
        var dirY = sp + (random.nextFloat() - 0.5f) * spread
        var dirZ = -cos(yaw) * cp + (random.nextFloat() - 0.5f) * spread
        val length = sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ)
        dirX /= length
        dirY /= length
        dirZ /= length

        val hit = raycast(px, eyeY, pz, dirX, dirY, dirZ, MAX_SHOOT_DISTANCE)

        tracerStart[0] = px + dirX * 0.5f + cos(yaw) * 0.16f
        tracerStart[1] = eyeY + dirY * 0.5f - 0.14f
        tracerStart[2] = pz + dirZ * 0.5f - sin(yaw) * 0.16f
        tracerEnd[0] = px + dirX * hit.distance
        tracerEnd[1] = eyeY + dirY * hit.distance
        tracerEnd[2] = pz + dirZ * hit.distance
        tracerTimer = 0.05f

        val enemy = hit.enemy
        if (enemy != null) {
            enemy.health -= 34f
            enemy.hitFlash = 0.16f
            hitMarker = 0.22f
            if (enemy.health <= 0f) {
                enemy.alive = false
                kills++
                score += 100
            }
        }
    }

    private class RayHit(val distance: Float, val enemy: Enemy?)

    private fun raycast(
        ox: Float, oy: Float, oz: Float,
        dx: Float, dy: Float, dz: Float,
        maxDistance: Float
    ): RayHit {
        var solidDistance = maxDistance

        // Piso.
        if (dy < -0.0001f) {
            val t = -oy / dy
            if (t > 0f) solidDistance = min(solidDistance, t)
        }

        // Paredes, pilares e caixotes.
        for (i in level.colliders.indices) {
            val t = rayBox(ox, oy, oz, dx, dy, dz, level.colliders[i])
            if (t != null && t < solidDistance) solidDistance = t
        }

        // Inimigos (cilindros verticais).
        var enemyDistance = Float.MAX_VALUE
        var hitEnemy: Enemy? = null
        for (i in enemies.indices) {
            val enemy = enemies[i]
            if (!enemy.alive) continue
            val t = rayCylinder(ox, oy, oz, dx, dy, dz, enemy)
            if (t != null && t < enemyDistance) {
                enemyDistance = t
                hitEnemy = enemy
            }
        }

        if (hitEnemy != null && enemyDistance < solidDistance) {
            return RayHit(enemyDistance, hitEnemy)
        }
        return RayHit(solidDistance, null)
    }

    /** Interseccao raio x caixa (slabs). Retorna a distancia ou null. */
    private fun rayBox(
        ox: Float, oy: Float, oz: Float,
        dx: Float, dy: Float, dz: Float,
        box: AABB
    ): Float? {
        val invX = if (abs(dx) < 0.000001f) 1e8f else 1f / dx
        val invY = if (abs(dy) < 0.000001f) 1e8f else 1f / dy
        val invZ = if (abs(dz) < 0.000001f) 1e8f else 1f / dz

        var tMin = 0f
        var tMax = Float.MAX_VALUE

        var t1 = (box.minX - ox) * invX
        var t2 = (box.maxX - ox) * invX
        if (t1 > t2) { val tmp = t1; t1 = t2; t2 = tmp }
        tMin = maxOf(tMin, t1); tMax = minOf(tMax, t2)
        if (tMin > tMax) return null

        t1 = (box.minY - oy) * invY
        t2 = (box.maxY - oy) * invY
        if (t1 > t2) { val tmp = t1; t1 = t2; t2 = tmp }
        tMin = maxOf(tMin, t1); tMax = minOf(tMax, t2)
        if (tMin > tMax) return null

        t1 = (box.minZ - oz) * invZ
        t2 = (box.maxZ - oz) * invZ
        if (t1 > t2) { val tmp = t1; t1 = t2; t2 = tmp }
        tMin = maxOf(tMin, t1); tMax = minOf(tMax, t2)
        if (tMin > tMax) return null

        return if (tMin > 0f) tMin else null
    }

    /** Interseccao raio x cilindro vertical do inimigo. */
    private fun rayCylinder(
        ox: Float, oy: Float, oz: Float,
        dx: Float, dy: Float, dz: Float,
        enemy: Enemy
    ): Float? {
        val a = dx * dx + dz * dz
        if (a < 0.000001f) return null

        val cx = ox - enemy.x
        val cz = oz - enemy.z
        val b = 2f * (cx * dx + cz * dz)
        val c = cx * cx + cz * cz - ENEMY_HIT_RADIUS * ENEMY_HIT_RADIUS
        val disc = b * b - 4f * a * c
        if (disc < 0f) return null

        val sq = sqrt(disc)
        var t = (-b - sq) / (2f * a)
        if (t < 0f) t = (-b + sq) / (2f * a)
        if (t < 0f) return null

        val y = oy + dy * t
        if (y < -0.05f || y > ENEMY_HEIGHT) return null
        return t
    }

    private fun updateEnemies(dt: Float) {
        var aliveCount = 0

        for (i in enemies.indices) {
            val enemy = enemies[i]
            if (!enemy.alive) continue
            aliveCount++
            if (enemy.hitFlash > 0f) enemy.hitFlash -= dt

            val dx = px - enemy.x
            val dz = pz - enemy.z
            val distance = sqrt(dx * dx + dz * dz)
            enemy.yaw = atan2(-dx, -dz)

            if (distance > 1.25f) {
                val inv = if (distance > 0.0001f) 1f / distance else 0f
                val step = enemy.speed * dt
                moveEnemy(enemy, dx * inv * step, dz * inv * step)
                enemy.walkPhase += dt * 9f
            } else {
                enemy.attackCooldown -= dt
                if (enemy.attackCooldown <= 0f) {
                    enemy.attackCooldown = 0.95f
                    hurtPlayer(ENEMY_DAMAGE)
                }
            }
        }

        separateEnemies()
        enemiesLeft = aliveCount

        if (aliveCount == 0 && state == GameState.PLAYING && enemies.isNotEmpty()) {
            state = GameState.WAVE_CLEAR
            waveCooldown = 3f
            message = "ONDA CONCLUIDA!"
            messageTimer = 3f
            score += 250
            vibrate(30)
        }
    }

    private fun moveEnemy(enemy: Enemy, dx: Float, dz: Float) {
        val nextX = enemy.x + dx
        if (!level.isBlocked(nextX, enemy.z, ENEMY_RADIUS)) enemy.x = nextX
        val nextZ = enemy.z + dz
        if (!level.isBlocked(enemy.x, nextZ, ENEMY_RADIUS)) enemy.z = nextZ
    }

    /** Empurra inimigos que se sobrepoem para eles nao virarem um bloco unico. */
    private fun separateEnemies() {
        val minDistance = 1.0f
        for (i in enemies.indices) {
            val a = enemies[i]
            if (!a.alive) continue
            for (j in i + 1 until enemies.size) {
                val b = enemies[j]
                if (!b.alive) continue
                var dx = b.x - a.x
                var dz = b.z - a.z
                var distance = sqrt(dx * dx + dz * dz)
                if (distance > minDistance) continue
                if (distance < 0.0001f) {
                    dx = 1f
                    dz = 0f
                    distance = 1f
                }
                val push = (minDistance - distance) * 0.5f
                val nx = dx / distance * push
                val nz = dz / distance * push
                if (!level.isBlocked(a.x - nx, a.z - nz, ENEMY_RADIUS)) {
                    a.x -= nx
                    a.z -= nz
                }
                if (!level.isBlocked(b.x + nx, b.z + nz, ENEMY_RADIUS)) {
                    b.x += nx
                    b.z += nz
                }
            }
        }
    }

    private fun hurtPlayer(damage: Float) {
        if (state == GameState.GAME_OVER) return
        health -= damage
        damageFlash = 0.5f
        shakeAmount = min(1f, shakeAmount + 0.6f)
        vibrate(40)

        if (health <= 0f) {
            health = 0f
            state = GameState.GAME_OVER
            message = "VOCE MORREU"
            messageTimer = 999f
            vibrate(150)
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrate(milliseconds: Long) {
        val v = vibrator ?: return
        try {
            v.vibrate(milliseconds)
        } catch (_: Exception) {
            // Alguns dispositivos negam vibracao sem permissao: ignorar.
        }
    }
}
