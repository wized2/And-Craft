package com.endroid.andcraft.gl

import android.graphics.Bitmap
import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Procedural 16x16 pixel-art texture atlas (same tiles as the web version). */
object Atlas {
    const val TS = 16
    const val AC = 8
    const val SIZE = TS * AC
    const val WHITE = 63

    object TI {
        const val grassTop = 0; const val grassSide = 1; const val dirt = 2; const val stone = 3
        const val logSide = 4; const val logTop = 5; const val leaves = 6; const val sand = 7
        const val planks = 8; const val brick = 9; const val water = 10; const val glass = 11
        const val snow = 12; const val coal = 13; const val gold = 14; const val flowerR = 15
        const val flowerY = 16; const val tall = 17; const val bedTop = 18; const val bedSide = 19
        const val farmland = 20; const val wheat0 = 21; const val wheat1 = 22; const val wheat2 = 23
        const val path = 24; const val emerald = 25; const val furnace = 26; const val chest = 27
        const val torch = 28
        const val iron = 29; const val diamond = 30; const val tntSide = 31; const val tntTop = 32
        const val gravel = 33; const val lava = 34; const val sapling = 35
    }

    fun bitmapOrNull(): Bitmap? = bitmap
    val pixels = IntArray(SIZE * SIZE)
    val avg = Array(64) { floatArrayOf(0.5f, 0.5f, 0.5f) }
    private var rs = 987654321L
    private fun rnd(): Float {
        rs = (rs * 1664525L + 1013904223L) and 0xffffffffL
        return rs / 4294967296f
    }

    private fun c(r: Int, g: Int, b: Int) = floatArrayOf(r.toFloat(), g.toFloat(), b.toFloat())
    private fun ca(r: Int, g: Int, b: Int, a: Int) = floatArrayOf(r.toFloat(), g.toFloat(), b.toFloat(), a.toFloat())
    private fun vr(col: FloatArray, a: Float): FloatArray {
        val d = (rnd() - 0.5f) * a
        return floatArrayOf(col[0] + d, col[1] + d, col[2] + d)
    }
    private fun vr(r: Int, g: Int, b: Int, a: Int) = vr(c(r, g, b), a.toFloat())
    private fun sub(col: FloatArray, n: Float) = floatArrayOf(col[0] - n, col[1] - n, col[2] - n)

    private fun paint(i: Int, fn: (Int, Int) -> FloatArray?) {
        var r = 0f; var g = 0f; var b = 0f; var n = 0
        val ox = (i % AC) * TS
        val oy = (i / AC) * TS
        for (y in 0 until TS) for (x in 0 until TS) {
            val col = fn(x, y)
            var px = 0
            if (col != null) {
                val a = if (col.size > 3) col[3].toInt() else 255
                val rr = col[0].toInt().coerceIn(0, 255)
                val gg = col[1].toInt().coerceIn(0, 255)
                val bb = col[2].toInt().coerceIn(0, 255)
                px = (a.coerceIn(0, 255) shl 24) or (rr shl 16) or (gg shl 8) or bb
                if (a > 40) { r += rr; g += gg; b += bb; n++ }
            }
            pixels[(oy + y) * SIZE + ox + x] = px
        }
        avg[i] = if (n > 0) floatArrayOf(r / n / 255f, g / n / 255f, b / n / 255f) else floatArrayOf(0.5f, 0.5f, 0.5f)
    }

    private val stoneFn: () -> FloatArray = {
        var col = vr(128, 128, 132, 34)
        if (rnd() < 0.07f) col = sub(col, 28f)
        col
    }

    var bitmap: Bitmap? = null
    var built = false

    fun build() {
        if (built) return
        built = true
        paint(TI.grassTop) { _, _ -> vr(if (rnd() < 0.12f) c(70, 140, 48) else c(98, 168, 64), 30f) }
        val ge = IntArray(TS) { 3 + (rnd() * 3).toInt() }
        paint(TI.grassSide) { x, y ->
            if (y < ge[x]) vr(c(98, 168, 64), 30f)
            else vr(if (rnd() < 0.1f) c(110, 78, 48) else c(134, 96, 60), 30f)
        }
        paint(TI.dirt) { _, _ -> vr(if (rnd() < 0.12f) c(108, 76, 46) else c(134, 96, 60), 30f) }
        paint(TI.stone) { _, _ -> stoneFn() }
        paint(TI.logSide) { x, _ ->
            val s = ((x * 7) % 4) * 9
            vr(c((104 - s * 0.4f).toInt(), (78 - s * 0.3f).toInt(), (44 - s * 0.2f).toInt()), 18f)
        }
        paint(TI.logTop) { x, y ->
            val d = max(abs(x - 7.5f), abs(y - 7.5f))
            if (d > 6.6f) vr(98, 72, 40, 14)
            else vr(if (floor(d).toInt() % 2 != 0) c(170, 134, 78) else c(150, 114, 64), 14f)
        }
        paint(TI.leaves) { _, _ ->
            if (rnd() < 0.1f) null
            else if (rnd() < 0.25f) vr(36, 100, 30, 24) else vr(58, 136, 44, 34)
        }
        paint(TI.sand) { _, _ -> vr(222, 208, 152, 22) }
        paint(TI.planks) { x, y ->
            var col = vr(178, 138, 82, 16)
            if (y % 4 == 3) col = floatArrayOf(col[0] - 34, col[1] - 30, col[2] - 24)
            else if ((x + (y shr 2) * 5) % 8 == 0) col = floatArrayOf(col[0] - 14, col[1] - 12, col[2] - 10)
            col
        }
        paint(TI.brick) { x, y ->
            val off = ((y shr 2) % 2) * 4
            if (y % 4 == 3 || (x + off) % 8 == 0) vr(188, 184, 172, 16) else vr(160, 72, 56, 26)
        }
        paint(TI.water) { _, _ -> vr(48, 104, 214, 16) }
        paint(TI.glass) { x, y ->
            if (x == 0 || y == 0 || x == 15 || y == 15) ca(206, 238, 248, 255)
            else if (x == y || (x == y + 1 && x < 8)) ca(255, 255, 255, 150)
            else ca(190, 228, 240, 46)
        }
        paint(TI.snow) { _, _ -> vr(240, 244, 252, 12) }
        val blobs = arrayOf(intArrayOf(3, 4), intArrayOf(10, 3), intArrayOf(7, 10), intArrayOf(12, 12), intArrayOf(4, 12))
        fun ore(col: FloatArray): (Int, Int) -> FloatArray? = { x, y ->
            var hit = false
            for (b in blobs) if ((x - b[0]) * (x - b[0]) + (y - b[1]) * (y - b[1]) <= 3.5f) { hit = true; break }
            if (hit) vr(col, 22f) else stoneFn()
        }
        paint(TI.coal, ore(c(34, 34, 38)))
        paint(TI.gold, ore(c(236, 200, 60)))
        fun flower(pet: FloatArray, mid: FloatArray): (Int, Int) -> FloatArray? = { x, y ->
            if ((x == 7 || x == 8) && y >= 7) vr(60, 140, 40, 20)
            else if ((x == 6 && y == 11) || (x == 9 && y == 12) || (x == 5 && y == 10) || (x == 10 && y == 11)) vr(60, 150, 40, 20)
            else {
                val d = hypot(x - 7.5f, y - 4.5f)
                if (d <= 1.3f) mid else if (d <= 3.2f) vr(pet, 24f) else null
            }
        }
        paint(TI.flowerR, flower(c(220, 40, 50), c(250, 210, 60)))
        paint(TI.flowerY, flower(c(250, 215, 40), c(240, 140, 30)))
        val bh = IntArray(TS) { if (it % 3 == 2) 0 else 5 + (rnd() * 9).toInt() }
        paint(TI.tall) { x, y ->
            if (y >= 16 - bh[x]) vr(if (y < 18 - bh[x]) c(120, 190, 70) else c(70, 150, 45), 24f) else null
        }
        paint(TI.bedTop) { _, y ->
            if (y < 5) vr(240, 238, 230, 10) else if (y == 5) vr(200, 196, 186, 10) else vr(196, 40, 44, 18)
        }
        paint(TI.bedSide) { _, y ->
            if (y >= 11) vr(150, 112, 64, 14) else if (y == 10) vr(100, 70, 40, 10) else vr(196, 40, 44, 18)
        }
        paint(TI.farmland) { _, y -> if (y > 11) vr(90, 60, 32, 14) else vr(110, 72, 40, 18) }
        paint(TI.path) { _, _ -> vr(if (rnd() < 0.15f) c(150, 120, 70) else c(168, 134, 82), 16f) }
        paint(TI.wheat0) { x, y -> if (x < 5 || x > 10) null else if (y > 12) vr(90, 140, 40, 20) else null }
        paint(TI.wheat1) { x, y ->
            if (x < 4 || x > 11) null else if (y > 8) vr(if (y > 12) c(180, 160, 50) else c(100, 150, 45), 18f) else null
        }
        paint(TI.wheat2) { x, y ->
            if (x < 3 || x > 12) null else if (y > 4) vr(if (y > 10) c(210, 180, 55) else c(120, 150, 50), 16f) else null
        }
        paint(TI.emerald) { x, y ->
            val d = hypot(x - 7.5f, y - 7.5f)
            if (d > 6.5f) vr(40, 40, 42, 10) else vr(if (d < 3f) c(80, 220, 120) else c(40, 160, 80), 18f)
        }
        paint(TI.furnace) { x, y ->
            if (y < 3 || y > 13) vr(60, 60, 60, 12)
            else if (x in 5..10 && y in 6..10) vr(20, 12, 8, 10)
            else vr(90, 90, 90, 16)
        }
        paint(TI.chest) { _, y -> if (y == 7 || y == 8) vr(80, 55, 20, 12) else vr(150, 100, 40, 18) }
        paint(TI.torch) { x, y ->
            if (x < 6 || x > 9) null else if (y > 11) vr(255, 200, 80, 20) else vr(120, 80, 40, 14)
        }
        paint(TI.iron, ore(c(214, 170, 140)))
        paint(TI.diamond, ore(c(90, 226, 232)))
        paint(TI.tntSide) { x, y ->
            if (y in 5..10) { if (x % 4 < 2 && y > 5 && y < 10) vr(40, 40, 40, 10) else vr(226, 224, 214, 12) }
            else vr(200, 44, 34, 20)
        }
        paint(TI.tntTop) { x, y ->
            val d = max(abs(x - 7.5f), abs(y - 7.5f))
            if (d < 1.6f) vr(30, 30, 30, 8) else vr(186, 150, 100, 18)
        }
        paint(TI.gravel) { _, _ ->
            val r = rnd()
            if (r < 0.25f) vr(96, 92, 92, 20) else if (r < 0.5f) vr(150, 146, 144, 24) else vr(124, 120, 118, 26)
        }
        paint(TI.lava) { _, _ ->
            val r = rnd()
            if (r < 0.18f) vr(255, 214, 70, 30) else if (r < 0.55f) vr(240, 120, 24, 40) else vr(206, 70, 10, 40)
        }
        paint(TI.sapling) { x, y ->
            if ((x == 7 || x == 8) && y >= 9) vr(100, 70, 40, 14)
            else { val d = hypot(x - 7.5f, y - 6f); if (d < 4.2f && rnd() > 0.15f) vr(54, 140, 44, 30) else null }
        }
        paint(WHITE) { _, _ -> c(255, 255, 255) }
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        bmp.setPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        bitmap = bmp
    }

    // ---- crack overlay (10 stages) ----
    const val CRACK_STAGES = 10
    val crackPixels = IntArray(CRACK_STAGES * TS * TS)
    fun buildCrack() {
        val pts = ArrayList<IntArray>()
        for (w in 0 until 2) {
            var px = 8f; var py = 8f
            for (i in 0 until 45) {
                pts.add(intArrayOf(px.toInt(), py.toInt()))
                px = max(0f, min(15f, px + (rnd() - 0.5f) * 3.4f))
                py = max(0f, min(15f, py + (rnd() - 0.5f) * 3.4f))
            }
        }
        for (s in 0 until CRACK_STAGES) {
            val n = pts.size * (s + 1) / CRACK_STAGES
            for (i in 0 until n) {
                val p = pts[i]
                crackPixels[p[1] * (CRACK_STAGES * TS) + s * TS + p[0]] = (190 shl 24)
            }
        }
    }

    fun tileFor(b: Int, f: Int): Int = when (b) {
        1 -> if (f == 2) TI.grassTop else if (f == 3) TI.dirt else TI.grassSide
        2 -> TI.dirt
        3 -> TI.stone
        4 -> if (f == 2 || f == 3) TI.logTop else TI.logSide
        5 -> TI.leaves; 6 -> TI.sand; 7 -> TI.planks; 8 -> TI.brick; 9 -> TI.water
        10 -> TI.glass; 11 -> TI.snow; 12 -> TI.coal; 13 -> TI.gold
        17 -> if (f == 2) TI.bedTop else if (f == 3) TI.planks else TI.bedSide
        14 -> TI.flowerR; 15 -> TI.flowerY; 16 -> TI.tall
        29 -> TI.farmland; 30 -> TI.wheat0; 31 -> TI.wheat1; 32 -> TI.wheat2; 38 -> TI.path
        33 -> TI.wheat0; 34 -> TI.wheat2; 35 -> TI.flowerR; 36 -> TI.flowerY; 37 -> TI.emerald
        44 -> TI.iron; 45 -> TI.diamond; 49 -> TI.gravel; 50 -> TI.lava; 51 -> TI.sapling
        48 -> if (f == 2 || f == 3) TI.tntTop else TI.tntSide
        39 -> TI.furnace; 40 -> TI.chest; 41 -> TI.torch; 42 -> TI.planks; 43 -> TI.stone
        else -> 0
    }

    private const val EP = 0.0008f
    /** returns u0, v0, u1, v1 (v grows downward in the bitmap) */
    fun uv(tile: Int, out: FloatArray) {
        val col = tile % AC
        val row = tile / AC
        out[0] = col / AC.toFloat() + EP
        out[1] = row / AC.toFloat() + EP
        out[2] = (col + 1) / AC.toFloat() - EP
        out[3] = (row + 1) / AC.toFloat() - EP
    }

    fun upload(): Int {
        build()
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0])
        val bb = ByteBuffer.allocateDirect(SIZE * SIZE * 4).order(ByteOrder.nativeOrder())
        for (p in pixels) {
            bb.put(((p shr 16) and 0xff).toByte()); bb.put(((p shr 8) and 0xff).toByte())
            bb.put((p and 0xff).toByte()); bb.put(((p ushr 24) and 0xff).toByte())
        }
        bb.position(0)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, SIZE, SIZE, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, bb)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return tex[0]
    }

    fun uploadCrack(): Int {
        buildCrackOnce()
        val w = CRACK_STAGES * TS
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0])
        val bb = ByteBuffer.allocateDirect(w * TS * 4).order(ByteOrder.nativeOrder())
        for (p in crackPixels) {
            bb.put(0); bb.put(0); bb.put(0); bb.put(((p ushr 24) and 0xff).toByte())
        }
        bb.position(0)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, TS, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, bb)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return tex[0]
    }

    private var crackBuilt = false
    private fun buildCrackOnce() { if (!crackBuilt) { crackBuilt = true; buildCrack() } }
}
