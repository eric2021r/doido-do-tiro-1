package com.example.fpsarena

/** As cinco armas do jogo. A ordem aqui e a ordem do botao TROCAR. */
enum class WeaponType { PISTOL, RIFLE, SHOTGUN, SNIPER, GRENADE }

/**
 * Ficha tecnica de uma arma. O dano do shotgun e por balim (8 balins por tiro).
 * A granada nao usa hitscan: ela vira um projetil com fisica e explode.
 */
class Weapon(
    val type: WeaponType,
    val label: String,
    val damage: Float,
    val pellets: Int,
    val magSize: Int,
    val startReserve: Int,
    val fireInterval: Float,
    val reloadTime: Float,
    val spreadIdle: Float,
    val spreadMoving: Float,
    val recoil: Float,
    val automatic: Boolean,
    val explosive: Boolean
)

object Weapons {

    val PISTOL = Weapon(
        type = WeaponType.PISTOL,
        label = "PISTOLA",
        damage = 30f,
        pellets = 1,
        magSize = 12,
        startReserve = 72,
        fireInterval = 0.20f,
        reloadTime = 1.1f,
        spreadIdle = 0.0015f,
        spreadMoving = 0.010f,
        recoil = 0.45f,
        automatic = false,
        explosive = false
    )

    val RIFLE = Weapon(
        type = WeaponType.RIFLE,
        label = "FUZIL",
        damage = 34f,
        pellets = 1,
        magSize = 30,
        startReserve = 150,
        fireInterval = 0.105f,
        reloadTime = 1.5f,
        spreadIdle = 0.006f,
        spreadMoving = 0.016f,
        recoil = 0.5f,
        automatic = true,
        explosive = false
    )

    val SHOTGUN = Weapon(
        type = WeaponType.SHOTGUN,
        label = "SHOTGUN",
        damage = 16f,
        pellets = 8,
        magSize = 6,
        startReserve = 36,
        fireInterval = 0.80f,
        reloadTime = 2.0f,
        spreadIdle = 0.030f,
        spreadMoving = 0.022f,
        recoil = 1.0f,
        automatic = false,
        explosive = false
    )

    val SNIPER = Weapon(
        type = WeaponType.SNIPER,
        label = "SNIPER",
        damage = 130f,
        pellets = 1,
        magSize = 5,
        startReserve = 25,
        fireInterval = 1.30f,
        reloadTime = 2.4f,
        spreadIdle = 0.0006f,
        spreadMoving = 0.028f,
        recoil = 1.0f,
        automatic = false,
        explosive = false
    )

    val GRENADE = Weapon(
        type = WeaponType.GRENADE,
        label = "GRANADA",
        damage = 0f,
        pellets = 0,
        magSize = 4,
        startReserve = 8,
        fireInterval = 0.85f,
        reloadTime = 0f,
        spreadIdle = 0f,
        spreadMoving = 0f,
        recoil = 0.7f,
        automatic = false,
        explosive = true
    )

    val ALL = listOf(PISTOL, RIFLE, SHOTGUN, SNIPER, GRENADE)

    /** Quanto cada arma recebe de municao ao limpar uma onda. */
    fun refillFor(index: Int): Int = when (index) {
        0 -> 36
        1 -> 90
        2 -> 18
        3 -> 10
        else -> 3
    }
}
