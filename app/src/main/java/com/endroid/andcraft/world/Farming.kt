package com.endroid.andcraft.world

object Farming {
    private var tick = 0f

    fun update(world: World, player: Player, dt: Float) {
        tick += dt
        if (tick < 2.5f) return
        tick = 0f
        val px = player.x.toInt(); val py = player.y.toInt(); val pz = player.z.toInt()
        for (dx in -6..6) for (dz in -6..6) for (dy in -2..2) {
            val x = px + dx; val y = py + dy; val z = pz + dz
            when (world.get(x, y, z)) {
                Blocks.WHEAT0 -> if (Math.random() < 0.35) world.set(x, y, z, Blocks.WHEAT1)
                Blocks.WHEAT1 -> if (Math.random() < 0.3) world.set(x, y, z, Blocks.WHEAT2)
            }
        }
    }

    fun tryUse(world: World, player: Player, hit: Hit): Boolean {
        val id = player.selected()
        val tb = world.get(hit.x, hit.y, hit.z)
        // plant seeds
        if (id == Blocks.SEEDS || id == Blocks.WHEAT0) {
            if (tb == Blocks.DIRT || tb == Blocks.GRASS || tb == Blocks.FARMLAND) {
                if (tb != Blocks.FARMLAND) world.set(hit.x, hit.y, hit.z, Blocks.FARMLAND)
                if (world.get(hit.x, hit.y + 1, hit.z) == Blocks.AIR) {
                    if (player.take(Blocks.SEEDS) || player.creative) {
                        world.set(hit.x, hit.y + 1, hit.z, Blocks.WHEAT0)
                        return true
                    }
                }
            }
            if (tb == Blocks.FARMLAND && world.get(hit.x, hit.y + 1, hit.z) == Blocks.AIR) {
                if (player.take(Blocks.SEEDS) || player.creative) {
                    world.set(hit.x, hit.y + 1, hit.z, Blocks.WHEAT0)
                    return true
                }
            }
        }
        // till with empty-ish hand (any dirt)
        if (tb == Blocks.DIRT || tb == Blocks.GRASS) {
            world.set(hit.x, hit.y, hit.z, Blocks.FARMLAND)
            return true
        }
        return false
    }

    fun harvest(world: World, player: Player, x: Int, y: Int, z: Int, id: Int): Boolean {
        if (id !in Blocks.WHEAT0..Blocks.WHEAT2) return false
        world.set(x, y, z, Blocks.AIR)
        if (id == Blocks.WHEAT2) {
            player.give(Blocks.SEEDS, 1 + (Math.random() * 2).toInt())
            player.give(Blocks.BREAD, 1)
        } else player.give(Blocks.SEEDS, 1)
        return true
    }
}
