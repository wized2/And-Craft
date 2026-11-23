package com.endroid.andcraft.gl

import android.opengl.GLES20
import com.endroid.andcraft.world.Blocks
import com.endroid.andcraft.world.World
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Textured chunk mesh with per-vertex ambient occlusion. Two layers: opaque/cutout and translucent (water, glass). */
class ChunkMesh(val cx: Int, val cz: Int) {
    private class Buf {
        var v = FloatArray(8192)
        var vn = 0
        var i = ShortArray(8192)
        var iN = 0
        var verts = 0
        fun vert(x: Float, y: Float, z: Float, u: Float, w: Float, l: Float) {
            if (vn + 6 > v.size) v = v.copyOf(v.size * 2)
            v[vn++] = x; v[vn++] = y; v[vn++] = z; v[vn++] = u; v[vn++] = w; v[vn++] = l
            verts++
        }
        fun tri(a: Int, b: Int, c: Int) {
            if (iN + 3 > i.size) i = i.copyOf(i.size * 2)
            i[iN++] = a.toShort(); i[iN++] = b.toShort(); i[iN++] = c.toShort()
        }
    }

    private class Layer { var vbo = 0; var ibo = 0; var count = 0 }
    private val opq = Layer()
    private val trn = Layer()

    companion object {
        val FACE_CORNERS = arrayOf(
            intArrayOf(1,0,0, 1,1,0, 1,1,1, 1,0,1),
            intArrayOf(0,0,1, 0,1,1, 0,1,0, 0,0,0),
            intArrayOf(0,1,0, 0,1,1, 1,1,1, 1,1,0),
            intArrayOf(0,0,0, 1,0,0, 1,0,1, 0,0,1),
            intArrayOf(0,0,1, 1,0,1, 1,1,1, 0,1,1),
            intArrayOf(1,0,0, 0,0,0, 0,1,0, 1,1,0)
        )
        val FACE_N = arrayOf(
            intArrayOf(1,0,0), intArrayOf(-1,0,0), intArrayOf(0,1,0),
            intArrayOf(0,-1,0), intArrayOf(0,0,1), intArrayOf(0,0,-1)
        )
        val SHADE = floatArrayOf(0.82f, 0.82f, 1f, 0.55f, 0.72f, 0.72f)
        val AO = floatArrayOf(0.5f, 0.68f, 0.84f, 1f)
    }

    private val tuv = FloatArray(4)

    fun build(world: World) {
        val H = World.H
        val CS = World.CS
        val baseX = cx * CS
        val baseZ = cz * CS
        val W = CS + 2
        val cache = IntArray(W * W * H)
        for (lz in 0 until W) for (lx in 0 until W) for (y in 0 until H)
            cache[(lz * W + lx) * H + y] = world.get(baseX + lx - 1, y, baseZ + lz - 1)
        fun g(x: Int, y: Int, z: Int): Int {
            if (y < 0) return Blocks.STONE
            if (y >= H) return Blocks.AIR
            val lx = x - baseX + 1
            val lz = z - baseZ + 1
            if (lx < 0 || lz < 0 || lx >= W || lz >= W) return Blocks.AIR
            return cache[(lz * W + lx) * H + y]
        }
        fun occl(id: Int) = id != Blocks.AIR && id != Blocks.WATER && id != Blocks.GLASS && id != Blocks.LEAVES &&
            !Blocks.thin(id) && id !in Blocks.WHEAT0..Blocks.WHEAT2

        val o = Buf()
        val t = Buf()

        fun aoOf(x: Int, y: Int, z: Int, f: Int, px: Int, py: Int, pz: Int): Float {
            val n = FACE_N[f]
            val bx = x + n[0]; val by = y + n[1]; val bz = z + n[2]
            val s1x: Int; val s1y: Int; val s1z: Int; val s2x: Int; val s2y: Int; val s2z: Int
            when (f) {
                0, 1 -> { // normal X: tangents Y, Z
                    val a = if (py == 1) 1 else -1; val b = if (pz == 1) 1 else -1
                    s1x = 0; s1y = a; s1z = 0; s2x = 0; s2y = 0; s2z = b
                }
                2, 3 -> { // normal Y: tangents X, Z
                    val a = if (px == 1) 1 else -1; val b = if (pz == 1) 1 else -1
                    s1x = a; s1y = 0; s1z = 0; s2x = 0; s2y = 0; s2z = b
                }
                else -> { // normal Z: tangents X, Y
                    val a = if (px == 1) 1 else -1; val b = if (py == 1) 1 else -1
                    s1x = a; s1y = 0; s1z = 0; s2x = 0; s2y = b; s2z = 0
                }
            }
            val sd1 = occl(g(bx + s1x, by + s1y, bz + s1z))
            val sd2 = occl(g(bx + s2x, by + s2y, bz + s2z))
            val cr = occl(g(bx + s1x + s2x, by + s1y + s2y, bz + s1z + s2z))
            val level = if (sd1 && sd2) 0 else 3 - ((if (sd1) 1 else 0) + (if (sd2) 1 else 0) + (if (cr) 1 else 0))
            return AO[level]
        }

        /** one textured face; hgt scales the height (farmland, water) */
        fun face(b: Buf, x: Int, y: Int, z: Int, f: Int, id: Int, hgt: Float, useAo: Boolean) {
            Atlas.uv(Atlas.tileFor(id, f), tuv)
            val c = FACE_CORNERS[f]
            val base = b.verts
            val ls = FloatArray(4)
            for (k in 0 until 4) {
                val px = c[k * 3]; val py = c[k * 3 + 1]; val pz = c[k * 3 + 2]
                val u = when (f) { 0 -> 1 - pz; 1 -> pz; 2 -> px; 3 -> px; 4 -> px; else -> 1 - px }
                val v = when (f) { 2 -> pz; 3 -> 1 - pz; else -> 1 - py }
                ls[k] = SHADE[f] * (if (useAo) aoOf(x, y, z, f, px, py, pz) else 1f)
                b.vert(x + px.toFloat(), y + py * hgt, z + pz.toFloat(),
                    tuv[0] + u * (tuv[2] - tuv[0]), tuv[1] + v * (tuv[3] - tuv[1]), ls[k])
            }
            if (ls[0] + ls[2] > ls[1] + ls[3]) { b.tri(base + 1, base + 2, base + 3); b.tri(base + 1, base + 3, base) }
            else { b.tri(base, base + 1, base + 2); b.tri(base, base + 2, base + 3) }
        }

        fun cross(b: Buf, x: Int, y: Int, z: Int, id: Int) {
            Atlas.uv(Atlas.tileFor(id, 0), tuv)
            val pl = arrayOf(floatArrayOf(0f, 0f, 1f, 1f), floatArrayOf(1f, 0f, 0f, 1f)) // x0,z0,x1,z1
            for (p in pl) for (side in 0..1) {
                val base = b.verts
                val x0 = x + p[0]; val z0 = z + p[1]; val x1 = x + p[2]; val z1 = z + p[3]
                val l = 0.95f
                b.vert(x0, y.toFloat(), z0, tuv[0], tuv[3], l)
                b.vert(x1, y.toFloat(), z1, tuv[2], tuv[3], l)
                b.vert(x1, y + 1f, z1, tuv[2], tuv[1], l)
                b.vert(x0, y + 1f, z0, tuv[0], tuv[1], l)
                if (side == 0) { b.tri(base, base + 1, base + 2); b.tri(base, base + 2, base + 3) }
                else { b.tri(base, base + 2, base + 1); b.tri(base, base + 3, base + 2) }
            }
        }

        for (y in 0 until H) for (z in 0 until CS) for (x in 0 until CS) {
            val wx = baseX + x; val wz = baseZ + z
            val id = g(wx, y, wz)
            if (id == Blocks.AIR) continue
            if (id == Blocks.WATER) {
                if (g(wx, y + 1, wz) != Blocks.WATER) face(t, wx, y, wz, 2, id, 0.88f, false)
                for (f in intArrayOf(0, 1, 4, 5)) {
                    val n = FACE_N[f]
                    val nb = g(wx + n[0], y + n[1], wz + n[2])
                    if (nb != Blocks.WATER && !occl(nb)) face(t, wx, y, wz, f, id, 0.88f, false)
                }
                continue
            }
            if (id == Blocks.GLASS) {
                for (f in 0..5) {
                    val n = FACE_N[f]
                    val nb = g(wx + n[0], y + n[1], wz + n[2])
                    if (nb != Blocks.GLASS && !occl(nb)) face(t, wx, y, wz, f, id, 1f, false)
                }
                continue
            }
            if (Blocks.thin(id) || id in Blocks.WHEAT0..Blocks.WHEAT2) { cross(o, wx, y, wz, id); continue }
            val hgt = if (id == Blocks.FARMLAND) 0.94f else 1f
            for (f in 0..5) {
                val n = FACE_N[f]
                val nb = g(wx + n[0], y + n[1], wz + n[2])
                if (!occl(nb) || (hgt < 1f && f == 2)) face(o, wx, y, wz, f, id, hgt, true)
            }
        }
        upload(opq, o)
        upload(trn, t)
    }

    private fun upload(l: Layer, b: Buf) {
        release(l)
        if (b.iN == 0) return
        val vb = ByteBuffer.allocateDirect(b.vn * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        vb.put(b.v, 0, b.vn).position(0)
        val ib = ByteBuffer.allocateDirect(b.iN * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
        ib.put(b.i, 0, b.iN).position(0)
        val buf = IntArray(2)
        GLES20.glGenBuffers(2, buf, 0)
        l.vbo = buf[0]; l.ibo = buf[1]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, l.vbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, b.vn * 4, vb, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, l.ibo)
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, b.iN * 2, ib, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
        l.count = b.iN
    }

    /** layer 0 = opaque/cutout, 1 = translucent */
    fun draw(layer: Int, aPos: Int, aUV: Int, aL: Int) {
        val l = if (layer == 0) opq else trn
        if (l.count == 0) return
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, l.vbo)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, l.ibo)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 24, 0)
        GLES20.glEnableVertexAttribArray(aUV)
        GLES20.glVertexAttribPointer(aUV, 2, GLES20.GL_FLOAT, false, 24, 12)
        GLES20.glEnableVertexAttribArray(aL)
        GLES20.glVertexAttribPointer(aL, 1, GLES20.GL_FLOAT, false, 24, 20)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, l.count, GLES20.GL_UNSIGNED_SHORT, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    private fun release(l: Layer) {
        if (l.vbo != 0) {
            GLES20.glDeleteBuffers(2, intArrayOf(l.vbo, l.ibo), 0)
            l.vbo = 0; l.ibo = 0
        }
        l.count = 0
    }

    fun release() { release(opq); release(trn) }
}
