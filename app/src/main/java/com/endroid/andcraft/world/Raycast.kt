package com.endroid.andcraft.world

data class Hit(val x: Int, val y: Int, val z: Int, val fx: Int, val fy: Int, val fz: Int, val id: Int)

object Raycast {
    fun cast(world: World, ox: Float, oy: Float, oz: Float, dx: Float, dy: Float, dz: Float, max: Float = 5f): Hit? {
        var x = kotlin.math.floor(ox.toDouble()).toInt()
        var y = kotlin.math.floor(oy.toDouble()).toInt()
        var z = kotlin.math.floor(oz.toDouble()).toInt()
        val sx = sign(dx); val sy = sign(dy); val sz = sign(dz)
        val tdx = if (sx != 0) kotlin.math.abs(1f / dx) else Float.POSITIVE_INFINITY
        val tdy = if (sy != 0) kotlin.math.abs(1f / dy) else Float.POSITIVE_INFINITY
        val tdz = if (sz != 0) kotlin.math.abs(1f / dz) else Float.POSITIVE_INFINITY
        var tmx = if (sx != 0) ((if (sx > 0) x + 1 - ox else ox - x) * tdx) else Float.POSITIVE_INFINITY
        var tmy = if (sy != 0) ((if (sy > 0) y + 1 - oy else oy - y) * tdy) else Float.POSITIVE_INFINITY
        var tmz = if (sz != 0) ((if (sz > 0) z + 1 - oz else oz - z) * tdz) else Float.POSITIVE_INFINITY
        var t = 0f
        var fx = 0; var fy = 0; var fz = 0
        while (t <= max) {
            val id = world.get(x, y, z)
            if (id != Blocks.AIR && id != Blocks.WATER) return Hit(x, y, z, fx, fy, fz, id)
            when {
                tmx < tmy && tmx < tmz -> { x += sx; t = tmx; tmx += tdx; fx = -sx; fy = 0; fz = 0 }
                tmy < tmz -> { y += sy; t = tmy; tmy += tdy; fx = 0; fy = -sy; fz = 0 }
                else -> { z += sz; t = tmz; tmz += tdz; fx = 0; fy = 0; fz = -sz }
            }
        }
        return null
    }
    private fun sign(v: Float) = when { v > 0 -> 1; v < 0 -> -1; else -> 0 }
}
