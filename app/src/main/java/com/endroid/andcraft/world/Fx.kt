package com.endroid.andcraft.world

/** world effects: falling sand/gravel, lit TNT, random ticks (saplings grow, grass spreads) */
class Fx(private val world: World) {
    private class Q(val x: Int, val y: Int, val z: Int, var t: Float)
    private val fall = ArrayList<Q>()
    private val tnts = ArrayList<FloatArray>()
    /** explosions ready to detonate: x, y, z, radius, damage */
    val booms = ArrayList<FloatArray>()

    init { world.onChange = { x, y, z, id -> changed(x, y, z, id) } }

    private fun isFaller(b: Int) = b == Blocks.SAND || b == Blocks.GRAVEL
    private fun passable(b: Int) = b == Blocks.AIR || b == Blocks.WATER || b == Blocks.LAVA ||
        Blocks.thin(b) || b in Blocks.WHEAT0..Blocks.WHEAT2

    private fun changed(x: Int, y: Int, z: Int, id: Int) {
        if (fall.size > 600) return
        if (id == Blocks.AIR || id == Blocks.WATER || Blocks.plant(id)) fall.add(Q(x, y + 1, z, 0.1f))
        if (isFaller(id)) fall.add(Q(x, y, z, 0.12f))
    }

    fun ignite(x: Int, y: Int, z: Int, fuse: Float) {
        for (t in tnts) if (t[0].toInt() == x && t[1].toInt() == y && t[2].toInt() == z) return
        tnts.add(floatArrayOf(x.toFloat(), y.toFloat(), z.toFloat(), fuse))
    }

    fun update(dt: Float, px: Float, pz: Float) {
        // falling blocks
        var i = fall.size - 1
        while (i >= 0) {
            if (i < fall.size) {
                val f = fall[i]
                f.t -= dt
                if (f.t <= 0f) {
                    fall.removeAt(i)
                    val b = world.get(f.x, f.y, f.z)
                    if (isFaller(b) && f.y > 1 && passable(world.get(f.x, f.y - 1, f.z))) {
                        world.set(f.x, f.y, f.z, Blocks.AIR)
                        world.set(f.x, f.y - 1, f.z, b)
                    }
                }
            }
            i--
        }
        // lit TNT
        val it = tnts.iterator()
        while (it.hasNext()) {
            val t = it.next()
            t[3] -= dt
            if (t[3] <= 0f) {
                it.remove()
                val x = t[0].toInt(); val y = t[1].toInt(); val z = t[2].toInt()
                if (world.get(x, y, z) == Blocks.TNT) world.set(x, y, z, Blocks.AIR)
                booms.add(floatArrayOf(x + 0.5f, y + 0.5f, z + 0.5f, 4f, 22f))
            }
        }
        randomTicks(px, pz)
    }

    fun growTree(x: Int, y: Int, z: Int) {
        val th = 4 + (Math.random() * 2).toInt()
        for (i in 0 until th) {
            val b = world.get(x, y + i, z)
            if (b != Blocks.AIR && !(i == 0 && b == Blocks.SAPLING)) return
        }
        for (dy in th - 2..th + 1) {
            val r = if (dy >= th) 1 else 2
            for (dx in -r..r) for (dz in -r..r) {
                if (Math.abs(dx) == r && Math.abs(dz) == r && (dy >= th || Math.random() < 0.5)) continue
                if (world.get(x + dx, y + dy, z + dz) == Blocks.AIR) world.set(x + dx, y + dy, z + dz, Blocks.LEAVES)
            }
        }
        for (i in 0 until th) world.set(x, y + i, z, Blocks.LOG)
    }

    private fun randomTicks(px: Float, pz: Float) {
        for (n in 0 until 24) {
            val x = (px + (Math.random() - 0.5) * 40).toInt()
            val z = (pz + (Math.random() - 0.5) * 40).toInt()
            val h = world.heightAt(x, z)
            for (y in h + 1 downTo h - 1) {
                if (y <= 0) break
                val b = world.get(x, y, z)
                if (b == Blocks.SAPLING && Math.random() < 0.08) growTree(x, y, z)
                else if (b == Blocks.DIRT && world.get(x, y + 1, z) == Blocks.AIR && Math.random() < 0.15) {
                    val nb = world.get(x + 1, y, z) == Blocks.GRASS || world.get(x - 1, y, z) == Blocks.GRASS ||
                        world.get(x, y, z + 1) == Blocks.GRASS || world.get(x, y, z - 1) == Blocks.GRASS
                    if (nb) world.set(x, y, z, Blocks.GRASS)
                }
            }
        }
    }

    /** removes blocks in a ragged sphere; onDrop gets the id of each block removed so the caller can award drops */
    fun explodeBlocks(cx: Float, cy: Float, cz: Float, r: Float, onDrop: (Int) -> Unit) {
        val R = Math.ceil(r.toDouble()).toInt()
        val bx = Math.floor(cx.toDouble()).toInt(); val by = Math.floor(cy.toDouble()).toInt(); val bz = Math.floor(cz.toDouble()).toInt()
        for (dx in -R..R) for (dy in -R..R) for (dz in -R..R) {
            val d = Math.sqrt((dx * dx + dy * dy + dz * dz).toDouble())
            if (d > r * (0.8 + Math.random() * 0.35)) continue
            val x = bx + dx; val y = by + dy; val z = bz + dz
            val b = world.get(x, y, z)
            if (b == Blocks.AIR || y <= 0 || b == Blocks.WATER || b == Blocks.LAVA) continue
            if (b == Blocks.TNT) { world.set(x, y, z, Blocks.AIR); ignite(x, y, z, 0.4f + Math.random().toFloat() * 0.6f); continue }
            if (Math.random() < 0.3) onDrop(b)
            world.set(x, y, z, Blocks.AIR)
        }
    }
}
