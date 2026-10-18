package com.example.fpsarena

import kotlin.math.abs

/** Caixa alinhada aos eixos, usada para paredes, pilares e caixotes. */
class AABB(
    val minX: Float,
    val minY: Float,
    val minZ: Float,
    val maxX: Float,
    val maxY: Float,
    val maxZ: Float
) {
    val centerX: Float get() = (minX + maxX) * 0.5f
    val centerY: Float get() = (minY + maxY) * 0.5f
    val centerZ: Float get() = (minZ + maxZ) * 0.5f
    val sizeX: Float get() = maxX - minX
    val sizeY: Float get() = maxY - minY
    val sizeZ: Float get() = maxZ - minZ
}

enum class BlockType { WALL, PILLAR, CRATE }

class Block(val box: AABB, val type: BlockType)

/**
 * A arena: piso 40x40, quatro paredes externas, quatro pilares e caixotes
 * espalhados para dar cobertura. Todas as colisoes do jogo saem daqui.
 */
class Level {

    val halfSize = 20f

    val blocks = ArrayList<Block>()
    val colliders = ArrayList<AABB>()

    /** Ponto onde o jogador nasce (olhando para o centro da arena). */
    val playerStart = floatArrayOf(0f, 0f, 13f)

    private fun add(minX: Float, minY: Float, minZ: Float, maxX: Float, maxY: Float, maxZ: Float, type: BlockType) {
        val box = AABB(minX, minY, minZ, maxX, maxY, maxZ)
        blocks.add(Block(box, type))
        colliders.add(box)
    }

    init {
        val wallHeight = 3.5f
        val limit = halfSize + 1f

        // Paredes externas (formam uma "moldura" em volta da arena).
        add(-limit, 0f, -limit, limit, wallHeight, -halfSize, BlockType.WALL)
        add(-limit, 0f, halfSize, limit, wallHeight, limit, BlockType.WALL)
        add(-limit, 0f, -limit, -halfSize, wallHeight, limit, BlockType.WALL)
        add(halfSize, 0f, -limit, limit, wallHeight, limit, BlockType.WALL)

        // Quatro pilares nos cantos internos.
        for (sx in intArrayOf(-1, 1)) {
            for (sz in intArrayOf(-1, 1)) {
                val cx = sx * 7f
                val cz = sz * 7f
                add(cx - 0.7f, 0f, cz - 0.7f, cx + 0.7f, wallHeight, cz + 0.7f, BlockType.PILLAR)
            }
        }

        // Caixotes de cobertura.
        val crates = arrayOf(
            floatArrayOf(-3.6f, 4.2f, 1.0f),
            floatArrayOf(4.4f, 3.0f, 1.0f),
            floatArrayOf(-6.2f, -3.4f, 1.0f),
            floatArrayOf(6.8f, -5.4f, 1.0f),
            floatArrayOf(2.2f, -8.6f, 1.0f),
            floatArrayOf(-9.4f, 8.2f, 1.0f),
            floatArrayOf(10.6f, 6.4f, 1.0f),
            floatArrayOf(-2.4f, 12.4f, 1.0f),
            floatArrayOf(12.0f, -1.5f, 1.0f),
            floatArrayOf(-12.5f, -9.0f, 1.0f)
        )
        for (crate in crates) {
            val half = crate[2] * 0.5f
            val cx = crate[0]
            val cz = crate[1]
            add(cx - half, 0f, cz - half, cx + half, crate[2], cz + half, BlockType.CRATE)
        }

        // Alguns caixotes empilhados para dar altura visual.
        add(4.0f, 1.0f, 2.6f, 5.0f, 2.0f, 3.6f, BlockType.CRATE)
        add(-6.7f, 1.0f, -3.9f, -5.7f, 2.0f, -2.9f, BlockType.CRATE)
    }

    /**
     * Verifica se um circulo de raio [radius] centrado em ([x], [z]) bate em
     * algum bloco solido. Usado para o jogador e para os inimigos.
     */
    fun isBlocked(x: Float, z: Float, radius: Float): Boolean {
        if (abs(x) > halfSize - radius || abs(z) > halfSize - radius) return true
        for (i in colliders.indices) {
            val b = colliders[i]
            if (x > b.minX - radius && x < b.maxX + radius &&
                z > b.minZ - radius && z < b.maxZ + radius
            ) {
                return true
            }
        }
        return false
    }

    /** Ponto de nascimento livre de inimigo, ou null se nao achou. */
    fun findSpawnPoint(originX: Float, originZ: Float, minDistanceFromPlayer: Float, random: kotlin.random.Random): FloatArray? {
        var attempt = 0
        while (attempt < 60) {
            attempt++
            val angle = random.nextFloat() * TWO_PI
            val radius = 8f + random.nextFloat() * 10.5f
            val x = kotlin.math.cos(angle) * radius
            val z = kotlin.math.sin(angle) * radius
            if (abs(x) > halfSize - 1.5f || abs(z) > halfSize - 1.5f) continue
            if (isBlocked(x, z, 0.9f)) continue
            val dx = x - originX
            val dz = z - originZ
            if (dx * dx + dz * dz < minDistanceFromPlayer * minDistanceFromPlayer) continue
            return floatArrayOf(x, z)
        }
        return null
    }

    companion object {
        const val TWO_PI = 6.2831855f
    }
}
