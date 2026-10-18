package com.example.fpsarena

import android.os.Vibrator
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class GameState { MENU, PLAYING, WAVE_CLEAR, GAME_OVER }

/** Inimigo simples: avanca ate o jogador e ataca de perto. */
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
    /** Angulo usado na vitrine do menu. */
    var orbit = 0f
    /** 0 = de pe, 1 = terminou de cair e desaparecer. */
    var deathT = 0f

    val gone: Boolean get() = !alive && deathT >= 1f

    /** 0 = de pe, 1 = deitado no chao. Ja com suavizacao de aceleracao. */
    val fallFactor: Float
        get() {
            if (alive) return 0f
            val t = (deathT / 0.32f).coerceIn(0f, 1f)
            return t * t
        }

    /** Opacidade do corpo: fica solido e depois desaparece. */
    val corpseAlpha: Float
        get() {
            if (alive) return 1f
            if (deathT <= 0.72f) return 1f
            return ((1f - deathT) / 0.28f).coerceIn(0f, 1f)
        }
}

/** Granada arremessada: cai, quica e explode depois do pavio. */
class Grenade(x0: Float, y0: Float, z0: Float, vx0: Float, vy0: Float, vz0: Float) {
    var x = x0
    var y = y0
    var z = z0
    var vx = vx0
    var vy = vy0
    var vz = vz0
    var fuse = 1.7f
    var spin = 0f
}

/** Onda de choque visual da explosao. */
class Explosion(val x: Float, val y: Float, val z: Float) {
    var t = 0f
}

/**
 * Estado completo da partida. Todo o update roda na thread de render do OpenGL;
 * as views (HUD e menu) apenas leem os campos publicos.
 */
class Game(private val vibrator: Vibrator? = null) {

    companion object {
        const val EYE_HEIGHT = 1.62f
        const val PLAYER_RADIUS = 0.42f
        const val ENEMY_RADIUS = 0.5f
        const val ENEMY_HEIGHT = 1.9f
        const val ENEMY_DAMAGE = 9f
        const val MAX_SHOOT_DISTANCE = 90f
        const val GRENADE_RADIUS = 4.8f
        const val GRENADE_DAMAGE = 165f
        const val MENU_ORBIT_RADIUS = 13f
        const val MENU_CAMERA_HEIGHT = 4.3f

        private const val TAU = 6.2831855f
        private const val WALK_SPEED = 5.0f
        private const val AIM_SPEED = 2.2f
        private const val JUMP_SPEED = 6.4f
        private const val GRAVITY = 19f
        private const val LOOK_SENSITIVITY = 0.0042f
        private const val AIM_SENSITIVITY = 0.30f
        private const val ENEMY_HIT_RADIUS = 0.58f
        private const val CORPSE_DURATION = 1.7f
        private const val GRENADE_SPEED = 15.5f
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

    // ------------------------------------------------------------------- armas
    var weaponIndex = 1
    val mags = IntArray(Weapons.ALL.size)
    val reserves = IntArray(Weapons.ALL.size)
    var reloading = false
    private var reloadTimer = 0f
    private var fireCooldown = 0f
    private var switchTimer = 0f
    private var triggerWasHeld = false
    /** Mira telescopica ligada (so faz sentido na sniper). */
    var aiming = false
    var spread = 0f

    val currentWeapon: Weapon get() = Weapons.ALL[weaponIndex]
    val currentMag: Int get() = mags[weaponIndex]
    val currentReserve: Int get() = reserves[weaponIndex]
    val canAim: Boolean get() = currentWeapon.type == WeaponType.SNIPER

    // ------------------------------------------------------------------ placar
    var score = 0
    var wave = 1
    var kills = 0
    var enemiesLeft = 0
    var state = GameState.MENU
    var message = ""
    var messageTimer = 0f
    private var waveCooldown = 0f
    /** Evita marcar "onda concluida" antes de qualquer onda existir. */
    private var waveSpawned = false

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
    var menuAngle = 0f

    /** Tracante do ultimo disparo. */
    var tracerTimer = 0f
    val tracerStart = FloatArray(3)
    val tracerEnd = FloatArray(3)

    val enemies = ArrayList<Enemy>()
    val grenades = ArrayList<Grenade>()
    val explosions = ArrayList<Explosion>()

    @Volatile
    private var restartRequested = false

    @Volatile
    private var menuRequested = false

    val reloadProgress: Float
        get() {
            val duration = currentWeapon.reloadTime
            if (!reloading || duration <= 0f) return 1f
            return (1f - reloadTimer / duration).coerceIn(0f, 1f)
        }

    val aimPitch: Float get() = (pitch + shakePitch).coerceIn(-1.45f, 1.45f)
    val aimYaw: Float get() = yaw + shakeYaw

    /** Queda da arma ao trocar de equipamento (0 = pronta, 1 = abaixada). */
    val switchDrop: Float get() = (switchTimer / 0.25f).coerceIn(0f, 1f)

    init {
        goToMenu()
    }

    // ========================================================================
    // Ciclo de vida
    // ========================================================================

    /** A UI pede o reinicio; a troca de estado acontece na thread de render. */
    fun requestRestart() {
        restartRequested = true
    }

    fun requestMenu() {
        menuRequested = true
    }

    fun goToMenu() {
        reset()
        state = GameState.MENU
        spawnMenuShowcase()
    }

    fun startNewGame() {
        reset()
        state = GameState.PLAYING
        spawnWave()
    }

    private fun reset() {
        px = level.playerStart[0]
        pz = level.playerStart[2]
        py = 0f
        verticalSpeed = 0f
        grounded = true
        yaw = 0f
        pitch = 0f

        health = maxHealth
        weaponIndex = 1
        aiming = false
        for (i in Weapons.ALL.indices) {
            mags[i] = Weapons.ALL[i].magSize
            reserves[i] = Weapons.ALL[i].startReserve
        }
        reloading = false
        reloadTimer = 0f
        fireCooldown = 0f
        switchTimer = 0f
        triggerWasHeld = false
        spread = 0f

        score = 0
        wave = 1
        kills = 0
        enemiesLeft = 0
        message = ""
        messageTimer = 0f
        waveCooldown = 0f
        waveSpawned = false

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

        enemies.clear()
        grenades.clear()
        explosions.clear()
        InputState.clear()
    }

    /** Inimigos parados em circulo, so para a arena do menu nao ficar vazia. */
    private fun spawnMenuShowcase() {
        val count = 5
        for (i in 0 until count) {
            val angle = TAU * i / count
            val enemy = Enemy(cos(angle) * 8.5f, sin(angle) * 8.5f)
            enemy.orbit = angle
            enemy.yaw = atan2(-enemy.x, -enemy.z)
            enemy.speed = 0f
            enemies.add(enemy)
        }
        enemiesLeft = enemies.size
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

        // Rede de seguranca: se a arena estiver muito cheia, usa pontos fixos.
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
        waveSpawned = true
        message = "ONDA $wave"
        messageTimer = 2.2f
    }

    // ========================================================================
    // Update
    // ========================================================================

    fun update(dtRaw: Float) {
        if (restartRequested) {
            restartRequested = false
            startNewGame()
        }
        if (menuRequested) {
            menuRequested = false
            goToMenu()
        }

        val dt = dtRaw.coerceIn(0f, 0.05f)
        time += dt

        if (muzzleFlash > 0f) muzzleFlash -= dt
        if (hitMarker > 0f) hitMarker -= dt
        if (damageFlash > 0f) damageFlash -= dt
        if (tracerTimer > 0f) tracerTimer -= dt
        if (messageTimer > 0f) messageTimer -= dt
        if (switchTimer > 0f) switchTimer -= dt
        recoil *= (1f - min(1f, dt * 11f))
        shakeAmount *= (1f - min(1f, dt * 6f))
        shakeYaw = (random.nextFloat() - 0.5f) * shakeAmount * 0.09f
        shakePitch = (random.nextFloat() - 0.5f) * shakeAmount * 0.09f

        if (state == GameState.MENU) {
            // Descarta qualquer toque acumulado enquanto o menu esta aberto.
            InputState.consumeLook()
            InputState.consumeJump()
            InputState.consumeReload()
            InputState.consumeSwitch()
            InputState.consumeAim()
            InputState.firing = false
            updateMenu(dt)
            return
        }

        val look = InputState.consumeLook()
        if (state == GameState.GAME_OVER) {
            InputState.consumeJump()
            InputState.consumeReload()
            InputState.consumeSwitch()
            InputState.consumeAim()
            return
        }

        val sensitivity = if (aiming) LOOK_SENSITIVITY * AIM_SENSITIVITY else LOOK_SENSITIVITY
        yaw -= look[0] * sensitivity
        pitch -= look[1] * sensitivity
        if (yaw > TAU || yaw < -TAU) yaw %= TAU
        pitch = pitch.coerceIn(-1.45f, 1.45f)

        if (InputState.consumeSwitch()) cycleWeapon()
        if (InputState.consumeAim()) toggleAim()

        updateMovement(dt)
        updateWeapon(dt)
        updateGrenades(dt)
        updateExplosions(dt)
        updateEnemies(dt)

        if (state == GameState.WAVE_CLEAR) {
            waveCooldown -= dt
            if (waveCooldown <= 0f) {
                wave++
                state = GameState.PLAYING
                health = min(maxHealth, health + 25f)
                for (i in Weapons.ALL.indices) {
                    reserves[i] += Weapons.refillFor(i)
                }
                spawnWave()
            }
        }
    }

    private fun updateMenu(dt: Float) {
        menuAngle += dt * 0.11f
        if (menuAngle > TAU) menuAngle -= TAU

        for (i in enemies.indices) {
            val enemy = enemies[i]
            enemy.orbit += dt * 0.30f
            enemy.x = cos(enemy.orbit) * 8.5f
            enemy.z = sin(enemy.orbit) * 8.5f
            // Vira de frente para fora, encarando a camera que orbita.
            enemy.yaw = atan2(-enemy.x, -enemy.z)
            enemy.walkPhase += dt * 6f
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
        val speed = if (aiming) AIM_SPEED else WALK_SPEED
        moveWithCollision(dirX * speed * dt, dirZ * speed * dt)

        val movingOnGround = intensity > 0.15f && grounded
        if (movingOnGround) {
            bobPhase += dt * (if (aiming) 4.5f else 9f)
            bob = sin(bobPhase) * (if (aiming) 0.4f else 1f)
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

        val weapon = currentWeapon
        spread = when {
            aiming -> weapon.spreadIdle * 0.35f
            else -> weapon.spreadIdle + intensity * weapon.spreadMoving + (if (grounded) 0f else 0.02f)
        }
    }

    private fun moveWithCollision(dx: Float, dz: Float) {
        val nextX = px + dx
        if (!level.isBlocked(nextX, pz, PLAYER_RADIUS)) px = nextX
        val nextZ = pz + dz
        if (!level.isBlocked(px, nextZ, PLAYER_RADIUS)) pz = nextZ
    }

    // ========================================================================
    // Armas
    // ========================================================================

    private fun updateWeapon(dt: Float) {
        if (fireCooldown > 0f) fireCooldown -= dt

        // Deteccao de "apertou agora": armas nao automaticas precisam de um
        // toque novo no gatilho para cada tiro.
        val trigger = InputState.firing
        val freshPress = trigger && !triggerWasHeld
        triggerWasHeld = trigger

        if (reloading) {
            reloadTimer -= dt
            if (reloadTimer <= 0f) finishReload()
            return
        }
        if (InputState.consumeReload()) {
            startReload()
            return
        }
        if (!trigger || fireCooldown > 0f) return

        val weapon = currentWeapon
        if (!weapon.automatic && !freshPress) return

        when {
            weapon.explosive -> if (mags[weaponIndex] > 0) throwGrenade()
            mags[weaponIndex] > 0 -> fireHitscan(weapon)
            else -> startReload()
        }
    }

    /** Troca para a proxima arma (o botao TROCAR chama isto). */
    fun cycleWeapon() {
        weaponIndex = (weaponIndex + 1) % Weapons.ALL.size
        reloading = false
        reloadTimer = 0f
        aiming = false
        switchTimer = 0.25f
        fireCooldown = maxOf(fireCooldown, 0.2f)
    }

    /** Liga/desliga a luneta da sniper. */
    fun toggleAim() {
        if (!canAim) return
        if (reloading) return
        aiming = !aiming
        switchTimer = 0.18f
    }

    private fun startReload() {
        val weapon = currentWeapon
        if (reloading || weapon.reloadTime <= 0f) return
        if (mags[weaponIndex] >= weapon.magSize || reserves[weaponIndex] <= 0) return
        reloading = true
        reloadTimer = weapon.reloadTime
    }

    private fun finishReload() {
        val weapon = currentWeapon
        val needed = weapon.magSize - mags[weaponIndex]
        val taken = min(needed, reserves[weaponIndex])
        mags[weaponIndex] += taken
        reserves[weaponIndex] -= taken
        reloading = false
        reloadTimer = 0f
    }

    private fun fireHitscan(weapon: Weapon) {
        mags[weaponIndex]--
        fireCooldown = weapon.fireInterval
        recoil = min(1f, recoil + weapon.recoil)
        muzzleFlash = if (weapon.type == WeaponType.SHOTGUN) 0.085f else 0.055f
        shakeAmount = min(0.9f, shakeAmount + 0.08f + weapon.recoil * 0.12f)
        vibrate(if (weapon.type == WeaponType.SNIPER) 30L else 10L)

        val eyeY = py + EYE_HEIGHT
        val cp = cos(pitch)
        val sp = sin(pitch)

        var firstHitT = MAX_SHOOT_DISTANCE
        for (pellet in 0 until weapon.pellets) {
            var dirX = -sin(yaw) * cp + (random.nextFloat() - 0.5f) * spread
            var dirY = sp + (random.nextFloat() - 0.5f) * spread
            var dirZ = -cos(yaw) * cp + (random.nextFloat() - 0.5f) * spread
            val length = sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ)
            dirX /= length
            dirY /= length
            dirZ /= length

            val hit = raycast(px, eyeY, pz, dirX, dirY, dirZ, MAX_SHOOT_DISTANCE)
            if (pellet == 0) {
                firstHitT = hit.distance
                tracerStart[0] = px + dirX * 0.5f + cos(yaw) * 0.16f
                tracerStart[1] = eyeY + dirY * 0.5f - 0.14f
                tracerStart[2] = pz + dirZ * 0.5f - sin(yaw) * 0.16f
                tracerEnd[0] = px + dirX * hit.distance
                tracerEnd[1] = eyeY + dirY * hit.distance
                tracerEnd[2] = pz + dirZ * hit.distance
                tracerTimer = if (weapon.type == WeaponType.SNIPER) 0.09f else 0.05f
            }
            val enemy = hit.enemy ?: continue
            damageEnemy(enemy, weapon.damage)
            hitMarker = 0.22f
        }

        // Fallback: se nenhum balim acertou, o tracante segue reto.
        if (weapon.pellets > 1 && firstHitT >= MAX_SHOOT_DISTANCE) {
            tracerEnd[0] = px - sin(yaw) * cp * MAX_SHOOT_DISTANCE
            tracerEnd[1] = eyeY + sp * MAX_SHOOT_DISTANCE
            tracerEnd[2] = pz - cos(yaw) * cp * MAX_SHOOT_DISTANCE
        }
    }

    private fun throwGrenade() {
        mags[weaponIndex]--
        fireCooldown = currentWeapon.fireInterval
        recoil = min(1f, recoil + 0.6f)
        vibrate(12)

        val eyeY = py + EYE_HEIGHT
        val cp = cos(pitch)
        val sp = sin(pitch)
        val dirX = -sin(yaw) * cp
        val dirY = sp
        val dirZ = -cos(yaw) * cp

        grenades.add(
            Grenade(
                px + dirX * 0.7f,
                eyeY + dirY * 0.7f - 0.12f,
                pz + dirZ * 0.7f,
                dirX * GRENADE_SPEED,
                dirY * GRENADE_SPEED + 2.6f,
                dirZ * GRENADE_SPEED + 0f
            )
        )
    }

    private fun updateGrenades(dt: Float) {
        var index = 0
        while (index < grenades.size) {
            val grenade = grenades[index]
            grenade.fuse -= dt
            grenade.spin += dt * 12f

            grenade.vy -= 16f * dt
            var nextX = grenade.x + grenade.vx * dt
            var nextY = grenade.y + grenade.vy * dt
            var nextZ = grenade.z + grenade.vz * dt

            if (nextY <= 0.12f) {
                nextY = 0.12f
                grenade.vy = -grenade.vy * 0.42f
                grenade.vx *= 0.72f
                grenade.vz *= 0.72f
                if (abs(grenade.vy) < 0.8f) grenade.vy = 0f
            }
            if (level.isBlocked(nextX, grenade.z, 0.16f)) {
                grenade.vx = -grenade.vx * 0.45f
                nextX = grenade.x
            }
            if (level.isBlocked(grenade.x, nextZ, 0.16f)) {
                grenade.vz = -grenade.vz * 0.45f
                nextZ = grenade.z
            }
            grenade.x = nextX
            grenade.y = nextY
            grenade.z = nextZ

            // Bateu em alguem: explode na hora.
            var touchedEnemy = false
            for (i in enemies.indices) {
                val enemy = enemies[i]
                if (!enemy.alive) continue
                val dx = enemy.x - grenade.x
                val dz = enemy.z - grenade.z
                if (dx * dx + dz * dz < 0.9f && grenade.y < ENEMY_HEIGHT) {
                    touchedEnemy = true
                    break
                }
            }

            if (grenade.fuse <= 0f || touchedEnemy) {
                explode(grenade.x, grenade.y, grenade.z)
                grenades.removeAt(index)
                continue
            }
            index++
        }
    }

    private fun explode(x: Float, y: Float, z: Float) {
        explosions.add(Explosion(x, y, z))
        vibrate(35)

        for (i in enemies.indices) {
            val enemy = enemies[i]
            if (!enemy.alive) continue
            val dx = enemy.x - x
            val dy = 1.0f - y
            val dz = enemy.z - z
            val distance = sqrt(dx * dx + dy * dy + dz * dz)
            if (distance > GRENADE_RADIUS) continue
            val falloff = 1f - distance / GRENADE_RADIUS
            damageEnemy(enemy, GRENADE_DAMAGE * falloff)
        }

        // Susto na camera se a explosao foi perto do jogador.
        val dx = px - x
        val dz = pz - z
        val distanceToPlayer = sqrt(dx * dx + dz * dz)
        if (distanceToPlayer < 12f) {
            val strength = 1f - distanceToPlayer / 12f
            shakeAmount = min(1.2f, shakeAmount + 1.1f * strength)
            damageFlash = maxOf(damageFlash, 0.35f * strength)
        }
    }

    private fun updateExplosions(dt: Float) {
        var index = 0
        while (index < explosions.size) {
            val explosion = explosions[index]
            explosion.t += dt
            if (explosion.t >= 0.55f) {
                explosions.removeAt(index)
                continue
            }
            index++
        }
    }

    private fun damageEnemy(enemy: Enemy, amount: Float) {
        if (!enemy.alive) return
        enemy.health -= amount
        enemy.hitFlash = 0.16f
        if (enemy.health <= 0f) {
            enemy.health = 0f
            enemy.alive = false
            enemy.deathT = 0f
            kills++
            score += 100
        }
    }

    // ========================================================================
    // Tiro (raycast)
    // ========================================================================

    private class RayHit(val distance: Float, val enemy: Enemy?)

    private fun raycast(
        ox: Float, oy: Float, oz: Float,
        dx: Float, dy: Float, dz: Float,
        maxDistance: Float
    ): RayHit {
        var solidDistance = maxDistance

        if (dy < -0.0001f) {
            val t = -oy / dy
            if (t > 0f) solidDistance = min(solidDistance, t)
        }

        for (i in level.colliders.indices) {
            val t = rayBox(ox, oy, oz, dx, dy, dz, level.colliders[i])
            if (t != null && t < solidDistance) solidDistance = t
        }

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

    // ========================================================================
    // Inimigos
    // ========================================================================

    private fun updateEnemies(dt: Float) {
        var aliveCount = 0

        for (i in enemies.indices) {
            val enemy = enemies[i]

            // Cadaver: roda a animacao de cair e depois some.
            if (!enemy.alive) {
                if (enemy.deathT < 1f) {
                    enemy.deathT += dt / CORPSE_DURATION
                    if (enemy.deathT > 1f) enemy.deathT = 1f
                }
                continue
            }

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

        // Remove os corpos que ja terminaram a animacao.
        var index = 0
        while (index < enemies.size) {
            if (enemies[index].gone) enemies.removeAt(index) else index++
        }

        if (aliveCount == 0 && state == GameState.PLAYING && waveSpawned) {
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
            aiming = false
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
            // Alguns aparelhos negam vibracao: ignorar.
        }
    }
}
