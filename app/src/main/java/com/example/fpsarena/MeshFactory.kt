package com.example.fpsarena

/**
 * Malhas primitivas geradas em codigo (nenhum arquivo de modelo no projeto).
 * Tudo usa um cubo unitario escalado pela matriz de modelo; o shader multiplica
 * a UV por uUvScale para a textura repetir na proporcao certa.
 */
object MeshFactory {

    /**
     * Cubo de 1x1x1 centrado na origem, 24 vertices (4 por face) para que cada
     * face tenha normal propria e UV 0..1.
     */
    fun unitCube(): Mesh {
        // Para cada face: normal N, tangente U e tangente V, com U x V = N.
        val faces = arrayOf(
            // +X
            floatArrayOf(1f, 0f, 0f, 0f, 0f, -1f, 0f, 1f, 0f),
            // -X
            floatArrayOf(-1f, 0f, 0f, 0f, 0f, 1f, 0f, 1f, 0f),
            // +Y
            floatArrayOf(0f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, -1f),
            // -Y
            floatArrayOf(0f, -1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f),
            // +Z
            floatArrayOf(0f, 0f, 1f, 1f, 0f, 0f, 0f, 1f, 0f),
            // -Z
            floatArrayOf(0f, 0f, -1f, -1f, 0f, 0f, 0f, 1f, 0f)
        )

        val positions = FloatArray(6 * 4 * 3)
        val normals = FloatArray(6 * 4 * 3)
        val uvs = FloatArray(6 * 4 * 2)
        val indices = ShortArray(6 * 6)

        val uvCorners = floatArrayOf(-1f, -1f, 1f, -1f, 1f, 1f, -1f, 1f)

        for (face in 0 until 6) {
            val data = faces[face]
            val nx = data[0]
            val ny = data[1]
            val nz = data[2]
            val ux = data[3]
            val uy = data[4]
            val uz = data[5]
            val vx = data[6]
            val vy = data[7]
            val vz = data[8]

            for (corner in 0 until 4) {
                val su = uvCorners[corner * 2]
                val sv = uvCorners[corner * 2 + 1]

                val px = (nx + ux * su + vx * sv) * 0.5f
                val py = (ny + uy * su + vy * sv) * 0.5f
                val pz = (nz + uz * su + vz * sv) * 0.5f

                val vertex = face * 4 + corner
                positions[vertex * 3] = px
                positions[vertex * 3 + 1] = py
                positions[vertex * 3 + 2] = pz

                normals[vertex * 3] = nx
                normals[vertex * 3 + 1] = ny
                normals[vertex * 3 + 2] = nz

                uvs[vertex * 2] = (su + 1f) * 0.5f
                uvs[vertex * 2 + 1] = (sv + 1f) * 0.5f
            }

            val base = face * 4
            val indexBase = face * 6
            indices[indexBase] = base.toShort()
            indices[indexBase + 1] = (base + 1).toShort()
            indices[indexBase + 2] = (base + 2).toShort()
            indices[indexBase + 3] = base.toShort()
            indices[indexBase + 4] = (base + 2).toShort()
            indices[indexBase + 5] = (base + 3).toShort()
        }

        return Mesh(positions, normals, uvs, indices)
    }

    /** Quadrado de 1x1 no plano XY olhando para +Z (usado no flash do cano). */
    fun quad(): Mesh {
        val positions = floatArrayOf(
            -0.5f, -0.5f, 0f,
            0.5f, -0.5f, 0f,
            0.5f, 0.5f, 0f,
            -0.5f, 0.5f, 0f
        )
        val normals = floatArrayOf(
            0f, 0f, 1f,
            0f, 0f, 1f,
            0f, 0f, 1f,
            0f, 0f, 1f
        )
        val uvs = floatArrayOf(
            0f, 0f,
            1f, 0f,
            1f, 1f,
            0f, 1f
        )
        val indices = shortArrayOf(0, 1, 2, 0, 2, 3)
        return Mesh(positions, normals, uvs, indices)
    }
}
