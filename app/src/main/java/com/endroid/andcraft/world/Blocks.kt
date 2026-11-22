package com.endroid.andcraft.world

object Blocks {
    const val AIR = 0
    const val GRASS = 1
    const val DIRT = 2
    const val STONE = 3
    const val LOG = 4
    const val LEAVES = 5
    const val SAND = 6
    const val PLANKS = 7
    const val WATER = 9

    fun solid(id: Int): Boolean = id != AIR && id != WATER && id != LEAVES
    fun name(id: Int): String = when (id) {
        GRASS -> "Grass"; DIRT -> "Dirt"; STONE -> "Stone"; LOG -> "Log"
        LEAVES -> "Leaves"; SAND -> "Sand"; PLANKS -> "Planks"; WATER -> "Water"
        else -> "Air"
    }
    /** RGB face tint 0..1 */
    fun color(id: Int): FloatArray = when (id) {
        GRASS -> floatArrayOf(0.35f, 0.65f, 0.25f)
        DIRT -> floatArrayOf(0.52f, 0.37f, 0.23f)
        STONE -> floatArrayOf(0.55f, 0.55f, 0.55f)
        LOG -> floatArrayOf(0.40f, 0.28f, 0.14f)
        LEAVES -> floatArrayOf(0.22f, 0.55f, 0.20f)
        SAND -> floatArrayOf(0.87f, 0.82f, 0.60f)
        PLANKS -> floatArrayOf(0.70f, 0.54f, 0.32f)
        WATER -> floatArrayOf(0.19f, 0.41f, 0.84f)
        else -> floatArrayOf(1f, 0f, 1f)
    }
}
