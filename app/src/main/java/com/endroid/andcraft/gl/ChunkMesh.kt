package com.endroid.andcraft.gl

import android.opengl.GLES20
import com.endroid.andcraft.world.Blocks
import com.endroid.andcraft.world.World
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

class ChunkMesh(val cx: Int, val cz: Int) {
    private var vbo = 0
    private var ibo = 0
    private var indexCount = 0
    private var ready = false

    fun build(world: World) {
        val verts = ArrayList<Float>(4096)
        val inds = ArrayList<Short>(4096)
        var vi = 0
        val baseX = cx * World.CS
        val baseZ = cz * World.CS
        fun addFace(x: Int, y: Int, z: Int, face: Int, id: Int, hgt: Float = 1f, bx0: Float = 0f, bx1: Float = 1f, bz0: Float = 0f, bz1: Float = 1f) {
            val c = Blocks.faceColor(id, face)
            val shade = when (face) {
                2 -> 1f; 3 -> 0.65f; 0, 1 -> 0.8f; else -> 0.75f
            }
            var hh = x * 374761393 + y * 668265263 + z * 1274126177 + face * 7919
            hh = (hh xor (hh ushr 13)) * 1274126177
            val jit = 0.93f + 0.07f * ((hh ushr 8) and 0xff) / 255f
            val r = c[0] * shade * jit; val g = c[1] * shade * jit; val b = c[2] * shade * jit
            // face vertices relative
            val corners = when (face) {
                0 -> arrayOf( // +X
                    floatArrayOf(1f,0f,0f), floatArrayOf(1f,1f,0f), floatArrayOf(1f,1f,1f), floatArrayOf(1f,0f,1f)
                )
                1 -> arrayOf( // -X
                    floatArrayOf(0f,0f,1f), floatArrayOf(0f,1f,1f), floatArrayOf(0f,1f,0f), floatArrayOf(0f,0f,0f)
                )
                2 -> arrayOf( // +Y
                    floatArrayOf(0f,1f,0f), floatArrayOf(0f,1f,1f), floatArrayOf(1f,1f,1f), floatArrayOf(1f,1f,0f)
                )
                3 -> arrayOf( // -Y
                    floatArrayOf(0f,0f,0f), floatArrayOf(1f,0f,0f), floatArrayOf(1f,0f,1f), floatArrayOf(0f,0f,1f)
                )
                4 -> arrayOf( // +Z
                    floatArrayOf(0f,0f,1f), floatArrayOf(1f,0f,1f), floatArrayOf(1f,1f,1f), floatArrayOf(0f,1f,1f)
                )
                else -> arrayOf( // -Z
                    floatArrayOf(1f,0f,0f), floatArrayOf(0f,0f,0f), floatArrayOf(0f,1f,0f), floatArrayOf(1f,1f,0f)
                )
            }
            val base = vi.toShort()
            for (p in corners) {
                verts.add(x + bx0 + p[0] * (bx1 - bx0)); verts.add(y + p[1] * hgt); verts.add(z + bz0 + p[2] * (bz1 - bz0))
                verts.add(r); verts.add(g); verts.add(b)
                vi++
            }
            inds.add(base); inds.add((base + 1).toShort()); inds.add((base + 2).toShort())
            inds.add(base); inds.add((base + 2).toShort()); inds.add((base + 3).toShort())
        }
        fun opaque(id: Int) = id != Blocks.AIR && id != Blocks.WATER && id != Blocks.WHEAT0 && id != Blocks.WHEAT1 && id != Blocks.WHEAT2 && id != Blocks.LEAVES && !Blocks.thin(id) && id != Blocks.GLASS
        fun drawAsBlock(id: Int) = id != Blocks.AIR && id != Blocks.WATER
        for (y in 0 until World.H) for (z in 0 until World.CS) for (x in 0 until World.CS) {
            val id = world.get(baseX + x, y, baseZ + z)
            val wx = baseX + x; val wz = baseZ + z
            if (id == Blocks.WATER) {
                if (world.get(wx, y + 1, wz) == Blocks.AIR) addFace(wx, y, wz, 2, id, 0.88f)
                continue
            }
            if (Blocks.thin(id)) {
                val hgt = when (id) { Blocks.TORCH -> 0.6f; Blocks.TALLGRASS -> 0.7f; else -> 0.5f }
                val lo = if (id == Blocks.TORCH) 0.43f else if (id == Blocks.TALLGRASS) 0.2f else 0.35f
                for (f in 0..5) addFace(wx, y, wz, f, id, hgt, lo, 1f - lo, lo, 1f - lo)
                continue
            }
            if (!drawAsBlock(id)) continue
            val hg = when (id) {
                Blocks.WHEAT0 -> 0.35f; Blocks.WHEAT1 -> 0.6f; Blocks.WHEAT2 -> 0.9f
                Blocks.FARMLAND -> 0.94f; else -> 1f
            }
            if (!opaque(world.get(wx + 1, y, wz))) addFace(wx, y, wz, 0, id, hg)
            if (!opaque(world.get(wx - 1, y, wz))) addFace(wx, y, wz, 1, id, hg)
            if (!opaque(world.get(wx, y + 1, wz))) addFace(wx, y, wz, 2, id, hg)
            if (!opaque(world.get(wx, y - 1, wz))) addFace(wx, y, wz, 3, id, hg)
            if (!opaque(world.get(wx, y, wz + 1))) addFace(wx, y, wz, 4, id, hg)
            if (!opaque(world.get(wx, y, wz - 1))) addFace(wx, y, wz, 5, id, hg)
        }
        release()
        if (inds.isEmpty()) { ready = false; return }
        val vArr = verts.toFloatArray()
        val iArr = ShortArray(inds.size) { inds[it] }
        val vb = ByteBuffer.allocateDirect(vArr.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        vb.put(vArr).position(0)
        val ib = ByteBuffer.allocateDirect(iArr.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
        ib.put(iArr).position(0)
        val buf = IntArray(2)
        GLES20.glGenBuffers(2, buf, 0)
        vbo = buf[0]; ibo = buf[1]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vArr.size * 4, vb, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo)
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, iArr.size * 2, ib, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
        indexCount = iArr.size
        ready = true
    }

    fun draw(aPos: Int, aCol: Int) {
        if (!ready) return
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo)
        val stride = 6 * 4
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, stride, 0)
        GLES20.glEnableVertexAttribArray(aCol)
        GLES20.glVertexAttribPointer(aCol, 3, GLES20.GL_FLOAT, false, stride, 12)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    fun release() {
        if (vbo != 0) {
            GLES20.glDeleteBuffers(2, intArrayOf(vbo, ibo), 0)
            vbo = 0; ibo = 0
        }
        ready = false
    }
}
