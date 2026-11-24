package com.endroid.andcraft.world

import kotlin.math.floor
import kotlin.math.sin

class World(val seed: Long = System.currentTimeMillis() and 0xffffffL) {
    companion object {
        const val CS = 16
        const val H = 48
        const val SEA = 12
    }

    private val chunks = HashMap<Long, ByteArray>()
    /** player-made changes per chunk (index -> block id); saved and re-applied after generation */
    val edits = HashMap<Long, HashMap<Int, Int>>()
    val meshDirty = HashSet<Long>()
    /** called after every set(): x, y, z, new id */
    var onChange: ((Int, Int, Int, Int) -> Unit)? = null
    /** villager spawn spots created by village generation: x, y, z, profession */
    val villagerSpawns = ArrayList<FloatArray>()
    private val villagesDone = HashSet<Long>()

    private fun key(cx: Int, cz: Int): Long = (cx.toLong() shl 32) xor (cz.toLong() and 0xffffffffL)

    private fun hash(x: Int, z: Int, salt: Int = 0): Float {
        var n = x * 374761393 + z * 668265263 + salt * 1274126177 + seed.toInt()
        n = (n xor (n ushr 13)) * 1274126177
        n = n xor (n ushr 16)
        return (n and 0x7fffffff) / 2147483647f
    }

    private fun smooth(t: Float) = t * t * (3f - 2f * t)
    /** 3D value noise 0..1 */
    private fun n3(x: Float, y: Float, z: Float): Float {
        val x0 = floor(x).toInt(); val y0 = floor(y).toInt(); val z0 = floor(z).toInt()
        val fx = smooth(x - x0); val fy = smooth(y - y0); val fz = smooth(z - z0)
        fun v(a: Int, b: Int, c: Int) = hash(a * 31 + c * 7, b * 17 + c * 13, 77)
        fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
        val x00 = lerp(v(x0, y0, z0), v(x0 + 1, y0, z0), fx)
        val x10 = lerp(v(x0, y0 + 1, z0), v(x0 + 1, y0 + 1, z0), fx)
        val x01 = lerp(v(x0, y0, z0 + 1), v(x0 + 1, y0, z0 + 1), fx)
        val x11 = lerp(v(x0, y0 + 1, z0 + 1), v(x0 + 1, y0 + 1, z0 + 1), fx)
        return lerp(lerp(x00, x10, fy), lerp(x01, x11, fy), fz)
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
            // tunnels and caverns
            val lim = if (beach) h - 4 else h
            for (y in 3..lim) {
                val a = n3(wx * 0.06f, y * 0.09f, wz * 0.06f)
                val b2 = n3(wx * 0.06f + 100f, y * 0.09f + 50f, wz * 0.06f + 100f)
                if (Math.abs(a - 0.5f) < 0.05f && Math.abs(b2 - 0.5f) < 0.05f) d[idx(x, y, z)] = 0
            }
            if (!beach) for (y in 5 until h - 4) {
                if (n3(wx * 0.045f + 300f, y * 0.07f, wz * 0.045f - 200f) > 0.78f) d[idx(x, y, z)] = 0
            }
            for (y in 1..5) if (d[idx(x, y, z)].toInt() == 0 && y < h - 1) d[idx(x, y, z)] = Blocks.LAVA.toByte()
            // ores
            for (y in 1 until h - 2) {
                if (d[idx(x, y, z)].toInt() != Blocks.STONE) continue
                val r = hash(wx * 3 + y, wz * 5 + y * 7, 1)
                if (r < 0.0015f && y < 9) d[idx(x, y, z)] = Blocks.DIAMOND_ORE.toByte()
                else if (r < 0.0045f && y < 14) d[idx(x, y, z)] = Blocks.GOLD_ORE.toByte()
                else if (r < 0.012f && y < 18) d[idx(x, y, z)] = Blocks.IRON_ORE.toByte()
                else if (r < 0.03f && y < 36) d[idx(x, y, z)] = Blocks.COAL_ORE.toByte()
            }
            // tall grass and flowers
            if (!beach && !snow && h + 1 < H && h > SEA + 1) {
                val fr = hash(wx, wz, 11)
                val fid = if (fr < 0.1f) Blocks.TALLGRASS else if (fr < 0.108f) Blocks.ROSE else if (fr < 0.116f) Blocks.DANDELION else 0
                if (fid != 0) d[idx(x, h + 1, z)] = fid.toByte()
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
            edits[key(cx, cz)]?.forEach { (i, id) -> d[i] = id.toByte() }
            d
        }

    class Village(val cx: Int, val cz: Int, val x: Float, val z: Float, val y: Int)
    private val vCache = HashMap<Long, Village?>()
    private val VR = 6

    fun villageAt(rx: Int, rz: Int): Village? = vCache.getOrPut(key(rx, rz)) {
        if (hash(rx * 7 + 1, rz * 11 + 5, 23) >= 0.5f || (rx == 0 && rz == 0)) return@getOrPut null
        val vx = rx * VR + 1 + (hash(rx, rz, 21) * 4).toInt().coerceIn(0, 3)
        val vz = rz * VR + 1 + (hash(rx, rz, 22) * 4).toInt().coerceIn(0, 3)
        val x = vx * CS + 8; val z = vz * CS + 8
        val h = heightAt(x, z)
        if (h > SEA + 2 && h < 34) Village(vx, vz, x.toFloat(), z.toFloat(), h) else null
    }

    fun villageOfChunk(cx: Int, cz: Int): Village? {
        val rx = floor(cx / VR.toFloat()).toInt(); val rz = floor(cz / VR.toFloat()).toInt()
        for (a in -1..1) for (b in -1..1) {
            val v = villageAt(rx + a, rz + b) ?: continue
            if (Math.abs(cx - v.cx) <= 1 && Math.abs(cz - v.cz) <= 1) return v
        }
        return null
    }

    fun nearestVillage(x: Float, z: Float, maxD: Float): Village? {
        val rx = floor(x / CS / VR).toInt(); val rz = floor(z / CS / VR).toInt()
        var best: Village? = null; var bd = maxD
        for (a in -1..1) for (b in -1..1) {
            val v = villageAt(rx + a, rz + b) ?: continue
            val d = Math.hypot((v.x - x).toDouble(), (v.z - z).toDouble()).toFloat()
            if (d < bd) { bd = d; best = v }
        }
        return best
    }

    private fun tryGenVillage(cx: Int, cz: Int, d: ByteArray) {
        val k = key(cx, cz)
        if (!villagesDone.add(k)) return
        val v = villageOfChunk(cx, cz) ?: return
        val dx = cx - v.cx; val dz = cz - v.cz
        val hc = heightAt(cx * CS + 8, cz * CS + 8)
        if (Math.abs(hc - v.y) > 4 || hc <= SEA + 1) return
        val y0 = hc.coerceIn(SEA + 1, H - 9)
        fun idx(x: Int, y: Int, z: Int) = x + CS * (z + CS * y)
        fun put(lx: Int, y: Int, lz: Int, id: Int) {
            if (lx !in 0 until CS || lz !in 0 until CS || y !in 1 until H - 1) return
            d[idx(lx, y, lz)] = id.toByte()
        }
        fun at(lx: Int, y: Int, lz: Int): Int =
            if (lx !in 0 until CS || lz !in 0 until CS || y !in 0 until H) 0 else d[idx(lx, y, lz)].toInt() and 0xff
        fun surf(lx: Int, lz: Int): Int {
            for (y in H - 2 downTo 1) { val b = at(lx, y, lz); if (b != 0 && b != Blocks.WATER && b != Blocks.LOG && b != Blocks.LEAVES && !Blocks.plant(b)) return y }
            return SEA
        }
        fun found(lx: Int, lz: Int, mat: Int, depth: Int = 6) {
            var yy = y0 - 1
            while (yy > y0 - depth) { val b = at(lx, yy, lz); if (b != 0 && b != Blocks.WATER) break; put(lx, yy, lz, mat); yy-- }
        }
        fun clear(lx: Int, lz: Int, h: Int) { for (yy in y0 + 1..y0 + h) put(lx, yy, lz, Blocks.AIR) }
        val swap = dx == 0 && dz != 0
        // frame: a along axis, b across
        fun lx(a: Int, b: Int) = if (swap) b else a
        fun lz(a: Int, b: Int) = if (swap) a else b
        fun P(a: Int, b: Int, y: Int, id: Int) = put(lx(a, b), y, lz(a, b), id)
        fun house(a0: Int, b0: Int, prof: Int) {
            for (da in 0 until 5) for (db in 0 until 5) {
                val a = a0 + da; val b = b0 + db
                found(lx(a, b), lz(a, b), Blocks.STONE)
                P(a, b, y0, Blocks.PLANKS)
                clear(lx(a, b), lz(a, b), 7)
            }
            for (da in 0 until 5) for (db in 0 until 5) {
                if (da in 1..3 && db in 1..3) continue
                val corner = da % 4 == 0 && db % 4 == 0
                for (h in 1..3) P(a0 + da, b0 + db, y0 + h, if (corner) Blocks.LOG else Blocks.PLANKS)
            }
            P(a0 + 2, b0, y0 + 1, Blocks.AIR); P(a0 + 2, b0, y0 + 2, Blocks.AIR); P(a0 + 2, b0, y0, Blocks.PATH)
            P(a0, b0 + 2, y0 + 2, Blocks.GLASS); P(a0 + 4, b0 + 2, y0 + 2, Blocks.GLASS); P(a0 + 2, b0 + 4, y0 + 2, Blocks.GLASS)
            for (da in -1..5) for (db in -1..5) {
                val inner = da in 0..4 && db in 0..4
                val rim = (da == -1 || da == 5) && db in 0..4 || (db == -1 || db == 5) && da in 0..4
                if (inner || rim) P(a0 + da, b0 + db, y0 + 4, if (inner) Blocks.PLANKS else Blocks.LOG)
            }
            for (da in 1..3) for (db in 1..3) P(a0 + da, b0 + db, y0 + 5, Blocks.PLANKS)
            P(a0 + 2, b0 + 2, y0 + 6, Blocks.LOG)
            P(a0 + 3, b0 + 3, y0 + 1, Blocks.BED); P(a0 + 1, b0 + 3, y0 + 1, Blocks.TORCH)
            P(a0 + 1, b0 + 1, y0 + 1, if (prof == 2) Blocks.FURNACE else Blocks.CHEST)
            val x = cx * CS + lx(a0 + 2, b0 + 2); val z = cz * CS + lz(a0 + 2, b0 + 2)
            villagerSpawns.add(floatArrayOf(x + 0.5f, y0 + 1f, z + 0.5f, prof.toFloat()))
        }
        fun farm(a0: Int, b0: Int) {
            for (da in 0 until 7) for (db in 0 until 5) {
                val a = a0 + da; val b = b0 + db
                found(lx(a, b), lz(a, b), Blocks.DIRT, 5)
                P(a, b, y0 - 1, Blocks.DIRT)
                clear(lx(a, b), lz(a, b), 3)
                val edge = da == 0 || da == 6 || db == 0 || db == 4
                when {
                    edge -> P(a, b, y0, Blocks.LOG)
                    db == 2 -> P(a, b, y0, Blocks.WATER)
                    else -> {
                        P(a, b, y0, Blocks.FARMLAND)
                        val r = hash(cx * CS + lx(a, b), cz * CS + lz(a, b), 31)
                        val c = if (r < 0.12f) 0 else if (r < 0.4f) Blocks.WHEAT0 else if (r < 0.7f) Blocks.WHEAT1 else Blocks.WHEAT2
                        P(a, b, y0 + 1, c)
                    }
                }
            }
            P(a0, b0, y0 + 1, Blocks.TORCH); P(a0 + 6, b0 + 4, y0 + 1, Blocks.TORCH)
            val x = cx * CS + lx(a0 + 3, b0 + 5); val z = cz * CS + lz(a0 + 3, b0 + 5)
            villagerSpawns.add(floatArrayOf(x + 0.5f, y0 + 1f, z + 0.5f, 0f))
        }
        val prof = (hash(cx, cz, 41) * 3f).toInt().coerceIn(0, 2)
        if (dx == 0 && dz == 0) {
            // well + crossroad
            for (ax in 5..9) for (az in 5..9) { found(ax, az, Blocks.STONE); clear(ax, az, 6); put(ax, y0, az, Blocks.PATH) }
            for (ax in 6..8) for (az in 6..8) put(ax, y0, az, Blocks.STONE)
            put(7, y0, 7, Blocks.WATER); put(7, y0 - 1, 7, Blocks.WATER)
            for ((px, pz) in listOf(6 to 6, 8 to 6, 6 to 8, 8 to 8)) { put(px, y0 + 1, pz, Blocks.LOG); put(px, y0 + 2, pz, Blocks.LOG) }
            for (ax in 6..8) for (az in 6..8) put(ax, y0 + 3, az, Blocks.PLANKS)
            put(7, y0 + 4, 7, Blocks.LOG)
            for (i in 0 until CS) for (j in 7..8) {
                if (i in 5..9) continue
                put(i, surf(i, j), j, Blocks.PATH); put(j, surf(j, i), i, Blocks.PATH)
            }
            villagerSpawns.add(floatArrayOf(cx * CS + 4.5f, y0 + 1f, cz * CS + 4.5f, 0f))
        } else if (dx == 0 || dz == 0) {
            for (a in 0 until CS) for (b in 7..8) put(lx(a, b), surf(lx(a, b), lz(a, b)), lz(a, b), Blocks.PATH)
            farm(2, 1)
            house(6, 10, prof)
        } else if (hash(cx, cz, 43) < 0.45f) farm(4, 4)
        else house(5, 5, prof)
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
        val idx = lx + CS * (lz + CS * y)
        c[idx] = id.toByte()
        edits.getOrPut(key(cx, cz)) { HashMap() }[idx] = id
        meshDirty.add(key(cx, cz))
        if (lx == 0) meshDirty.add(key(cx - 1, cz))
        if (lx == CS - 1) meshDirty.add(key(cx + 1, cz))
        if (lz == 0) meshDirty.add(key(cx, cz - 1))
        if (lz == CS - 1) meshDirty.add(key(cx, cz + 1))
        onChange?.invoke(x, y, z, id)
    }

    fun takeDirty(): Set<Long> {
        val s = meshDirty.toSet(); meshDirty.clear(); return s
    }

    fun ensureRadius(px: Int, pz: Int, r: Int) {
        val cx = floor(px / CS.toFloat()).toInt()
        val cz = floor(pz / CS.toFloat()).toInt()
        for (dz in -r..r) for (dx in -r..r) chunk(cx + dx, cz + dz)
    }

    /** open grass/sand ground above sea level, no trees nearby */
    fun findSpawn(): IntArray {
        fun top(x: Int, z: Int): Int {
            for (y in H - 1 downTo 1) { val b = get(x, y, z); if (b != Blocks.AIR) return y }
            return -1
        }
        for (r in 0..64 step 2) for (dx in -r..r step 2) for (dz in -r..r step 2) {
            if (Math.abs(dx) != r && Math.abs(dz) != r) continue
            val h = top(dx, dz)
            if (h <= SEA + 1) continue
            val t = get(dx, h, dz)
            if (t != Blocks.GRASS && t != Blocks.SAND && t != Blocks.SNOW) continue
            var ok = true
            for (ax in -2..2) for (az in -2..2) {
                val hh = top(dx + ax, dz + az)
                if (hh < 0 || Math.abs(hh - h) > 1) ok = false
                for (dy in 1..6) { val b = get(dx + ax, h + dy, dz + az); if (b == Blocks.LOG || b == Blocks.LEAVES) ok = false }
            }
            if (ok) return intArrayOf(dx, h + 1, dz)
        }
        return intArrayOf(0, heightAt(0, 0) + 2, 0)
    }

    fun encodeEdits(): String {
        val sb = StringBuilder()
        for ((k, m) in edits) {
            if (m.isEmpty()) continue
            sb.append(k).append(':')
            var first = true
            for ((i, id) in m) { if (!first) sb.append('|'); first = false; sb.append(i).append('=').append(id) }
            sb.append(';')
        }
        return sb.toString()
    }

    fun decodeEdits(s: String) {
        for (part in s.split(';')) {
            val c = part.indexOf(':')
            if (c <= 0) continue
            val k = part.substring(0, c).toLongOrNull() ?: continue
            val m = HashMap<Int, Int>()
            for (e in part.substring(c + 1).split('|')) {
                val q = e.indexOf('=')
                if (q <= 0) continue
                val i = e.substring(0, q).toIntOrNull() ?: continue
                val id = e.substring(q + 1).toIntOrNull() ?: continue
                m[i] = id
            }
            edits[k] = m
        }
    }
}
