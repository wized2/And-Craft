package com.endroid.andcraft.gl

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Unit cube (0..1) with baked per-face shading, used for mob body parts. */
class CubeMesh(private val crack: Boolean = false) {
    private var vbo = 0
    private var ibo = 0

    fun init() {
        val faces = arrayOf(
            floatArrayOf(1f,0f,0f, 1f,1f,0f, 1f,1f,1f, 1f,0f,1f),
            floatArrayOf(0f,0f,1f, 0f,1f,1f, 0f,1f,0f, 0f,0f,0f),
            floatArrayOf(0f,1f,0f, 0f,1f,1f, 1f,1f,1f, 1f,1f,0f),
            floatArrayOf(0f,0f,0f, 1f,0f,0f, 1f,0f,1f, 0f,0f,1f),
            floatArrayOf(0f,0f,1f, 1f,0f,1f, 1f,1f,1f, 0f,1f,1f),
            floatArrayOf(1f,0f,0f, 0f,0f,0f, 0f,1f,0f, 1f,1f,0f)
        )
        val shade = floatArrayOf(0.8f, 0.8f, 1f, 0.6f, 0.75f, 0.75f)
        val v = FloatArray(6 * 4 * 6)
        val wu = (Atlas.WHITE % Atlas.AC + 0.5f) / Atlas.AC
        val wv = (Atlas.WHITE / Atlas.AC + 0.5f) / Atlas.AC
        val idx = ShortArray(36)
        var vi = 0
        var ii = 0
        for (f in 0 until 6) {
            val base = (f * 4).toShort()
            for (c in 0 until 4) {
                v[vi++] = faces[f][c * 3]; v[vi++] = faces[f][c * 3 + 1]; v[vi++] = faces[f][c * 3 + 2]
                if (crack) { v[vi++] = if (c >= 2) 1f else 0f; v[vi++] = if (c == 0 || c == 3) 1f else 0f; v[vi++] = 1f }
                else { v[vi++] = wu; v[vi++] = wv; v[vi++] = shade[f] }
            }
            idx[ii++] = base; idx[ii++] = (base + 1).toShort(); idx[ii++] = (base + 2).toShort()
            idx[ii++] = base; idx[ii++] = (base + 2).toShort(); idx[ii++] = (base + 3).toShort()
        }
        val vb = ByteBuffer.allocateDirect(v.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        vb.put(v).position(0)
        val ib = ByteBuffer.allocateDirect(idx.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
        ib.put(idx).position(0)
        val buf = IntArray(2)
        GLES20.glGenBuffers(2, buf, 0)
        vbo = buf[0]; ibo = buf[1]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, v.size * 4, vb, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo)
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, idx.size * 2, ib, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    fun draw(aPos: Int, aUV: Int, aL: Int) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 24, 0)
        GLES20.glEnableVertexAttribArray(aUV)
        GLES20.glVertexAttribPointer(aUV, 2, GLES20.GL_FLOAT, false, 24, 12)
        GLES20.glEnableVertexAttribArray(aL)
        GLES20.glVertexAttribPointer(aL, 1, GLES20.GL_FLOAT, false, 24, 20)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, 36, GLES20.GL_UNSIGNED_SHORT, 0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
    }
}
