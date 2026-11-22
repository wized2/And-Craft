package com.endroid.andcraft.world

import kotlin.math.floor
import kotlin.math.sin

class World(val seed: Long = System.currentTimeMillis()) {
    companion object {
        const val CS = 16
        const val H = 48
        const val SEA = 12
    }

    private val chunks = HashMap<Long, ByteArray>()
    val meshDirty = HashSet<Long>()
    private val villagesDone = HashSet<Long>()

    private fun key(cx: Int, cz: Int): Long = (cx.toLong() shl 32) xor (cz.toLong() and 0xffffffffL)

    private fun hash(x: Int, z: Int, salt: Int = 0): Float {
        var n = x * 374761393 + z * 668265263 + salt * 1274126177 + seed.toInt()
        n = (n xor (n ushr 13)) * 1274126177
        n = n xor (n ushr 16)
        return (n and 0x7fffffff) / 2147483647f
    }

    fun heightAt(x: Int, z: Int): Int {
        val n = sin(x * 0.07 + (seed % 100)) * 3 + sin(z * 0.09) * 3 + sin((x + z) * 0.04) * 2
        return (SEA + 4 + n).toInt().coerceIn(4, H - 8)
    }

    private fun genChunk(cx: Int, cz: Int): ByteArray {
        val d = ByteArray(CS * CS * H)
        fun idx(x: Int, y: Int, z: Int) = x + CS * (z + CS * y)
        for (z in 0 until CS) for (x in 0 until CS) {
            val wx = cx * CS + x
            val wz = cz * CS + z
            val h = heightAt(wx, wz)
            val beach = h <= SEA + 1
            val snow = h >= 34
            for (y in 0..h) {
                d[idx(x, y, z)] = when {
                    y == h -> when {
                        beach -> Blocks.SAND
                        snow -> Blocks.SNOW
                        else -> Blocks.GRASS
                    }.toByte()
                    y > h - 3 -> if (beach) Blocks.SAND.toByte() else Blocks.DIRT.toByte()
                    else -> Blocks.STONE.toByte()
                }
            }
            for (y in h + 1..SEA) d[idx(x, y, z)] = Blocks.WATER.toByte()
            // ores
            for (y in 1 until h - 2) {
                if (d[idx(x, y, z)].toInt() != Blocks.STONE) continue
                val r = hash(wx * 3 + y, wz * 5 + y * 7, 1)
                if (r < 0.003f && y < 20) d[idx(x, y, z)] = Blocks.GOLD_ORE.toByte()
                else if (r < 0.02f && y < 36) d[idx(x, y, z)] = Blocks.COAL_ORE.toByte()
            }
            // trees
            if (!beach && !snow && h > SEA + 2 && hash(wx, wz, 2) < 0.03f) {
                val th = 3 + (hash(wx, wz, 3) * 2).toInt()
                for (i in 1..th) if (h + i < H) d[idx(x, h + i, z)] = Blocks.LOG.toByte()
                for (dy in th - 1..th + 1) for (dx in -2..2) for (dz in -2..2) {
                    val lx = x + dx; val lz = z + dz; val ly = h + dy
                    if (lx in 0 until CS && lz in 0 until CS && ly in 0 until H) {
                        if (d[idx(lx, ly, lz)].toInt() == Blocks.AIR)
                            d[idx(lx, ly, lz)] = Blocks.LEAVES.toByte()
                    }
                }
            }
        }
        return d
    }

    private fun chunk(cx: Int, cz: Int): ByteArray =
        chunks.getOrPut(key(cx, cz)) {
            val d = genChunk(cx, cz)
            tryGenVillage(cx, cz, d)
            d
        }

    private fun tryGenVillage(cx: Int, cz: Int, d: ByteArray) {
        val k = key(cx, cz)
        if (!villagesDone.add(k)) return
        if (hash(cx * 17, cz * 13, 7) >= 0.05f) return
        val ox = 2; val oz = 8
        val wx = cx * CS + ox; val wz = cz * CS + oz
        val h = heightAt(wx, wz)
        if (h <= SEA + 2 || h >= 32) return
        fun idx(x: Int, y: Int, z: Int) = x + CS * (z + CS * y)
        fun put(lx: Int, y: Int, lz: Int, id: Int) {
            if (lx !in 0 until CS || lz !in 0 until CS || y !in 1 until H - 1) return
            d[idx(lx, y, lz)] = id.toByte()
        }
        // small house
        for (dx in 0..3) for (dz in 0..3) {
            for (yy in h - 1..h) put(ox + dx, yy, oz + dz, if (yy == h) Blocks.PLANKS else Blocks.DIRT)
            for (yy in h + 1..h + 3) put(ox + dx, yy, oz + dz, Blocks.AIR)
        }
        for (dx in 0..3) for (dz in 0..3) {
            if (dx == 0 || dx == 3 || dz == 0 || dz == 3) {
                put(ox + dx, h + 1, oz + dz, Blocks.PLANKS)
                put(ox + dx, h + 2, oz + dz, Blocks.PLANKS)
            }
            put(ox + dx, h + 3, oz + dz, Blocks.LOG)
        }
        put(ox + 1, h + 1, oz, Blocks.AIR); put(ox + 1, h + 2, oz, Blocks.AIR)
        put(ox + 2, h + 1, oz, Blocks.AIR); put(ox + 2, h + 2, oz, Blocks.AIR)
        put(ox + 2, h + 1, oz + 2, Blocks.BED)
        // farm
        for (dx in 0..4) for (dz in 0..3) {
            val lx = 3 + dx; val lz = 2 + dz
            put(lx, h, lz, Blocks.FARMLAND)
            if (dz == 1 && dx in 1..3) put(lx, h, lz, Blocks.WATER)
            else put(lx, h + 1, lz, if (hash(lx, lz, 9) < 0.7f) Blocks.WHEAT1 else Blocks.WHEAT0)
        }
        meshDirty.add(k)
    }

    fun get(x: Int, y: Int, z: Int): Int {
        if (y < 0) return Blocks.STONE
        if (y >= H) return Blocks.AIR
        val cx = floor(x / CS.toFloat()).toInt()
        val cz = floor(z / CS.toFloat()).toInt()
        val c = chunk(cx, cz)
        val lx = ((x % CS) + CS) % CS
        val lz = ((z % CS) + CS) % CS
        return c[lx + CS * (lz + CS * y)].toInt() and 0xff
    }

    fun set(x: Int, y: Int, z: Int, id: Int) {
        if (y < 0 || y >= H) return
        val cx = floor(x / CS.toFloat()).toInt()
        val cz = floor(z / CS.toFloat()).toInt()
        val c = chunk(cx, cz)
        val lx = ((x % CS) + CS) % CS
        val lz = ((z % CS) + CS) % CS
        c[lx + CS * (lz + CS * y)] = id.toByte()
        meshDirty.add(key(cx, cz))
        if (lx == 0) meshDirty.add(key(cx - 1, cz))
        if (lx == CS - 1) meshDirty.add(key(cx + 1, cz))
        if (lz == 0) meshDirty.add(key(cx, cz - 1))
        if (lz == CS - 1) meshDirty.add(key(cx, cz + 1))
    }

    fun takeDirty(): Set<Long> {
        val s = meshDirty.toSet(); meshDirty.clear(); return s
    }

    fun ensureRadius(px: Int, pz: Int, r: Int) {
        val cx = floor(px / CS.toFloat()).toInt()
        val cz = floor(pz / CS.toFloat()).toInt()
        for (dz in -r..r) for (dx in -r..r) chunk(cx + dx, cz + dz)
    }
}
