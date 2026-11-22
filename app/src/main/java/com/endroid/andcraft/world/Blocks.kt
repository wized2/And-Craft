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
    const val BRICK = 8
    const val WATER = 9
    const val GLASS = 10
    const val SNOW = 11
    const val COAL_ORE = 12
    const val GOLD_ORE = 13
    const val BED = 17
    const val FARMLAND = 29
    const val WHEAT0 = 30
    const val WHEAT1 = 31
    const val WHEAT2 = 32
    const val PATH = 38

    // items (not placed as solid world gen)
    const val SEEDS = 33
    const val BREAD = 34
    const val EMERALD = 37

    fun solid(id: Int): Boolean =
        id != AIR && id != WATER && id != LEAVES && id != WHEAT0 && id != WHEAT1 && id != WHEAT2

    fun plant(id: Int): Boolean = id in WHEAT0..WHEAT2 || id == LEAVES

    fun name(id: Int): String = when (id) {
        GRASS -> "Grass"; DIRT -> "Dirt"; STONE -> "Stone"; LOG -> "Log"
        LEAVES -> "Leaves"; SAND -> "Sand"; PLANKS -> "Planks"; BRICK -> "Brick"
        WATER -> "Water"; GLASS -> "Glass"; SNOW -> "Snow"; COAL_ORE -> "Coal Ore"
        GOLD_ORE -> "Gold Ore"; BED -> "Bed"; FARMLAND -> "Farmland"
        WHEAT0, WHEAT1 -> "Wheat"; WHEAT2 -> "Wheat (grown)"
        SEEDS -> "Seeds"; BREAD -> "Bread"; EMERALD -> "Emerald"; PATH -> "Path"
        else -> "Air"
    }

    fun color(id: Int): FloatArray = when (id) {
        GRASS -> floatArrayOf(0.35f, 0.65f, 0.25f)
        DIRT, FARMLAND, PATH -> floatArrayOf(0.52f, 0.37f, 0.23f)
        STONE -> floatArrayOf(0.55f, 0.55f, 0.55f)
        LOG -> floatArrayOf(0.40f, 0.28f, 0.14f)
        LEAVES -> floatArrayOf(0.22f, 0.55f, 0.20f)
        SAND -> floatArrayOf(0.87f, 0.82f, 0.60f)
        PLANKS -> floatArrayOf(0.70f, 0.54f, 0.32f)
        BRICK -> floatArrayOf(0.62f, 0.28f, 0.22f)
        WATER -> floatArrayOf(0.19f, 0.41f, 0.84f)
        GLASS -> floatArrayOf(0.75f, 0.90f, 0.95f)
        SNOW -> floatArrayOf(0.94f, 0.96f, 0.99f)
        COAL_ORE -> floatArrayOf(0.25f, 0.25f, 0.28f)
        GOLD_ORE -> floatArrayOf(0.85f, 0.72f, 0.22f)
        BED -> floatArrayOf(0.75f, 0.20f, 0.22f)
        WHEAT0 -> floatArrayOf(0.40f, 0.55f, 0.20f)
        WHEAT1 -> floatArrayOf(0.60f, 0.60f, 0.25f)
        WHEAT2 -> floatArrayOf(0.82f, 0.72f, 0.25f)
        else -> floatArrayOf(1f, 0f, 1f)
    }

    val HOTBAR_DEFAULT = intArrayOf(GRASS, DIRT, STONE, LOG, PLANKS, SAND, BRICK, GLASS, BED)
}
