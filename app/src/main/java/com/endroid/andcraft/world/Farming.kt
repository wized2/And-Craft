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
        // seeds: till dirt/grass, then plant wheat
        if (id == Blocks.SEEDS && player.has(Blocks.SEEDS)) {
            if (tb == Blocks.DIRT || tb == Blocks.GRASS) world.set(hit.x, hit.y, hit.z, Blocks.FARMLAND)
            val t2 = world.get(hit.x, hit.y, hit.z)
            if (t2 == Blocks.FARMLAND && world.get(hit.x, hit.y + 1, hit.z) == Blocks.AIR) {
                player.take(Blocks.SEEDS)
                world.set(hit.x, hit.y + 1, hit.z, Blocks.WHEAT0)
                return true
            }
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
