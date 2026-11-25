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
    const val ROSE = 14
    const val DANDELION = 15
    const val TALLGRASS = 16
    const val FURNACE = 39
    const val CHEST = 40
    const val TORCH = 41
    const val STICK = 42
    const val SHEARS = 43
    const val FARMLAND = 29
    const val WHEAT0 = 30
    const val WHEAT1 = 31
    const val WHEAT2 = 32
    const val PATH = 38

    // items (not placed as solid world gen)
    const val SEEDS = 33
    const val BREAD = 34
    const val EMERALD = 37
    const val RAW_PORK = 18
    const val COOKED_PORK = 19
    const val RAW_BEEF = 20
    const val STEAK = 21
    const val RAW_MUTTON = 22
    const val COOKED_MUTTON = 23
    const val APPLE = 24
    const val ROTTEN = 25
    const val WOOL = 26
    const val LEATHER = 27
    const val GOLDEN_APPLE = 28
    const val IRON_ORE = 44
    const val DIAMOND_ORE = 45
    const val IRON_INGOT = 46
    const val DIAMOND = 47
    const val TNT = 48
    const val GRAVEL = 49
    const val LAVA = 50
    const val SAPLING = 51
    const val GUNPOWDER = 52
    const val RAW_CHICKEN = 53
    const val COOKED_CHICKEN = 54
    const val FLINT = 55
    const val BONE = 56
    const val LADDER = 57

    fun solid(id: Int): Boolean =
        id != AIR && id != WATER && id != LAVA && id != LEAVES && id != WHEAT0 && id != WHEAT1 && id != WHEAT2 && !thin(id)

    fun plant(id: Int): Boolean = id in WHEAT0..WHEAT2 || id == LEAVES || thin(id)

    /** small decorations drawn as thin posts */
    fun thin(id: Int): Boolean = id == ROSE || id == DANDELION || id == TALLGRASS || id == TORCH || id == SAPLING || id == LADDER

    fun placeable(id: Int): Boolean = id in 1..8 || id == GLASS || id == SNOW || id == BED || id == FURNACE || id == CHEST || id == TORCH || id == TNT || id == GRAVEL || id == SAPLING || id == LADDER

    fun name(id: Int): String = when (id) {
        GRASS -> "Grass"; DIRT -> "Dirt"; STONE -> "Stone"; LOG -> "Log"
        LEAVES -> "Leaves"; SAND -> "Sand"; PLANKS -> "Planks"; BRICK -> "Brick"
        WATER -> "Water"; GLASS -> "Glass"; SNOW -> "Snow"; COAL_ORE -> "Coal"
        GOLD_ORE -> "Gold"; BED -> "Bed"; FARMLAND -> "Farmland"
        WHEAT0, WHEAT1 -> "Wheat"; WHEAT2 -> "Wheat (grown)"
        ROSE -> "Rose"; DANDELION -> "Dandelion"; TALLGRASS -> "Tall Grass"; FURNACE -> "Furnace"; CHEST -> "Chest"
        TORCH -> "Torch"; STICK -> "Sticks"; SHEARS -> "Shears"
        SEEDS -> "Seeds"; BREAD -> "Bread"; EMERALD -> "Emerald"; PATH -> "Path"
        RAW_PORK -> "Raw Porkchop"; COOKED_PORK -> "Cooked Porkchop"; RAW_BEEF -> "Raw Beef"; STEAK -> "Steak"
        RAW_MUTTON -> "Raw Mutton"; COOKED_MUTTON -> "Cooked Mutton"; APPLE -> "Apple"; ROTTEN -> "Rotten Flesh"
        WOOL -> "Wool"; LEATHER -> "Leather"; GOLDEN_APPLE -> "Golden Apple"
        IRON_ORE -> "Iron Ore"; DIAMOND_ORE -> "Diamond Ore"; IRON_INGOT -> "Iron Ingot"; DIAMOND -> "Diamond"
        TNT -> "TNT"; GRAVEL -> "Gravel"; LAVA -> "Lava"; SAPLING -> "Sapling"; GUNPOWDER -> "Gunpowder"
        RAW_CHICKEN -> "Raw Chicken"; COOKED_CHICKEN -> "Cooked Chicken"; FLINT -> "Flint & Steel"; BONE -> "Bone"; LADDER -> "Ladder"
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
        ROSE -> floatArrayOf(0.85f, 0.15f, 0.2f)
        DANDELION -> floatArrayOf(0.95f, 0.85f, 0.2f)
        TALLGRASS -> floatArrayOf(0.3f, 0.7f, 0.25f)
        FURNACE -> floatArrayOf(0.45f, 0.45f, 0.47f)
        CHEST -> floatArrayOf(0.62f, 0.42f, 0.18f)
        TORCH -> floatArrayOf(1f, 0.85f, 0.35f)
        IRON_ORE -> floatArrayOf(0.8f, 0.65f, 0.55f)
        DIAMOND_ORE -> floatArrayOf(0.35f, 0.85f, 0.88f)
        TNT -> floatArrayOf(0.8f, 0.17f, 0.13f)
        GRAVEL -> floatArrayOf(0.5f, 0.48f, 0.47f)
        LAVA -> floatArrayOf(0.95f, 0.45f, 0.1f)
        SAPLING -> floatArrayOf(0.2f, 0.55f, 0.17f)
        LADDER -> floatArrayOf(0.55f, 0.4f, 0.22f)
        WHEAT0 -> floatArrayOf(0.40f, 0.55f, 0.20f)
        WHEAT1 -> floatArrayOf(0.60f, 0.60f, 0.25f)
        WHEAT2 -> floatArrayOf(0.82f, 0.72f, 0.25f)
        else -> floatArrayOf(1f, 0f, 1f)
    }

    val HOTBAR_DEFAULT = intArrayOf(GRASS, DIRT, STONE, LOG, PLANKS, SAND, BRICK, GLASS, BED, SEEDS)

    // Minecraft hardness values
    fun hardness(id: Int): Float = when (id) {
        GRASS, FARMLAND -> 0.6f
        DIRT, SAND -> 0.5f
        STONE -> 1.5f
        LOG, PLANKS, BRICK -> 2f
        LEAVES, SNOW, BED -> 0.2f
        GLASS -> 0.3f
        COAL_ORE, GOLD_ORE, IRON_ORE, DIAMOND_ORE -> 3f
        GRAVEL -> 0.6f
        TNT, SAPLING -> 0f
        LADDER -> 0.4f
        LAVA -> 999f
        PATH -> 0.65f
        ROSE, DANDELION, TALLGRASS, TORCH -> 0f
        FURNACE -> 3.5f
        CHEST -> 2.5f
        WHEAT0, WHEAT1, WHEAT2 -> 0f
        else -> 1f
    }

    /** pickaxe tier needed to get a drop: 0 none, 1 wood, 2 stone */
    fun needPick(id: Int): Int = when (id) {
        STONE, COAL_ORE, BRICK, FURNACE -> 1
        GOLD_ORE, IRON_ORE -> 2
        DIAMOND_ORE -> 3
        else -> 0
    }

    fun isItem(id: Int): Boolean = id in 18..28 || id in 46..47 || id in 52..56 || id == SEEDS || id == BREAD || id == EMERALD || id == STICK || id == SHEARS

    fun axeBlock(id: Int): Boolean = id == LOG || id == PLANKS || id == BED || id == CHEST

    /** hunger restored (food points) */
    fun foodValue(id: Int): Int = when (id) {
        BREAD -> 5; COOKED_PORK, STEAK -> 8; COOKED_MUTTON -> 6
        RAW_PORK, RAW_BEEF -> 3; RAW_MUTTON -> 2; RAW_CHICKEN -> 2; COOKED_CHICKEN -> 6; APPLE, ROTTEN -> 4; GOLDEN_APPLE -> 4
        else -> 0
    }

    fun satValue(id: Int): Float = when (id) {
        BREAD -> 6f; COOKED_PORK, STEAK -> 12.8f; COOKED_MUTTON -> 9.6f
        RAW_PORK, RAW_BEEF -> 1.8f; RAW_MUTTON -> 1.2f; RAW_CHICKEN -> 1.2f; COOKED_CHICKEN -> 7.2f; APPLE -> 2.4f; ROTTEN -> 0.8f; GOLDEN_APPLE -> 9.6f
        else -> 0f
    }

    val EAT_ORDER = intArrayOf(STEAK, COOKED_PORK, COOKED_MUTTON, COOKED_CHICKEN, BREAD, APPLE, RAW_BEEF, RAW_PORK, RAW_MUTTON, RAW_CHICKEN, ROTTEN)

    private val DIRTC = floatArrayOf(0.52f, 0.37f, 0.23f)
    private val SHADE = floatArrayOf(0f, 0f, 1f, 0.6f, 0.8f, 0.8f, 0.75f)

    /** per-face base colour (face: 0/1 sides X, 2 top, 3 bottom, 4/5 sides Z) */
    fun faceColor(id: Int, face: Int): FloatArray {
        val top = face == 2
        val bottom = face == 3
        return when (id) {
            GRASS -> if (top) color(GRASS) else DIRTC
            LOG -> if (top || bottom) floatArrayOf(0.62f, 0.5f, 0.3f) else color(LOG)
            BED -> if (top) color(BED) else floatArrayOf(0.55f, 0.4f, 0.22f)
            FURNACE -> if (top) floatArrayOf(0.35f, 0.35f, 0.37f) else if (face == 4) floatArrayOf(0.25f, 0.2f, 0.18f) else color(FURNACE)
            CHEST -> if (top) floatArrayOf(0.7f, 0.5f, 0.24f) else color(CHEST)
            SNOW -> if (top) color(SNOW) else floatArrayOf(0.86f, 0.88f, 0.92f)
            else -> color(id)
        }
    }
}
