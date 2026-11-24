package com.endroid.andcraft

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LightingColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import com.endroid.andcraft.gl.Atlas
import com.endroid.andcraft.world.Blocks

/** Hotbar icons drawn from the texture atlas: isometric cubes for blocks, flat sprites for plants and items. */
object Icons {
    private val cache = HashMap<Int, Bitmap>()
    private const val S = 48

    private fun tile(t: Int): Bitmap? {
        val a = Atlas.bitmapOrNull() ?: return null
        return Bitmap.createBitmap(a, (t % Atlas.AC) * Atlas.TS, (t / Atlas.AC) * Atlas.TS, Atlas.TS, Atlas.TS)
    }

    private fun itemColor(id: Int): Int = when (id) {
        Blocks.RAW_PORK, Blocks.RAW_BEEF, Blocks.RAW_MUTTON, Blocks.RAW_CHICKEN -> 0xFFE58A8A.toInt()
        Blocks.COOKED_PORK, Blocks.STEAK, Blocks.COOKED_MUTTON, Blocks.COOKED_CHICKEN -> 0xFF9B5A2E.toInt()
        Blocks.APPLE -> 0xFFD92B2B.toInt()
        Blocks.GOLDEN_APPLE -> 0xFFF5C518.toInt()
        Blocks.ROTTEN -> 0xFF7A8F3A.toInt()
        Blocks.WOOL -> 0xFFEFEFEF.toInt()
        Blocks.LEATHER -> 0xFF8B5A2B.toInt()
        Blocks.BREAD -> 0xFFD9A441.toInt()
        Blocks.SEEDS -> 0xFF7FB03A.toInt()
        Blocks.EMERALD -> 0xFF3CCB6E.toInt()
        Blocks.STICK -> 0xFF8B6B3A.toInt()
        Blocks.SHEARS -> 0xFFBBBBBB.toInt()
        Blocks.IRON_INGOT -> 0xFFD8D8D8.toInt()
        Blocks.DIAMOND -> 0xFF5AE2E8.toInt()
        Blocks.GUNPOWDER -> 0xFF555555.toInt()
        Blocks.FLINT -> 0xFF444455.toInt()
        else -> 0xFF999999.toInt()
    }

    private fun isCube(id: Int) = !Blocks.isItem(id) && !Blocks.thin(id) && id !in Blocks.WHEAT0..Blocks.WHEAT2 && id != Blocks.FARMLAND

    fun get(id: Int): Bitmap? {
        cache[id]?.let { return it }
        if (Atlas.bitmapOrNull() == null) return null
        val bmp = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
        if (Blocks.isItem(id) && id !in intArrayOf(Blocks.SEEDS)) {
            val q = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = itemColor(id) }
            c.drawCircle(S / 2f, S / 2f, 15f, q)
            q.color = 0x55000000; q.style = Paint.Style.STROKE; q.strokeWidth = 2f
            c.drawCircle(S / 2f, S / 2f, 15f, q)
        } else if (!isCube(id)) {
            val t = tile(Atlas.tileFor(id, 0)) ?: return null
            c.drawBitmap(Bitmap.createScaledBitmap(t, 40, 40, false), 4f, 4f, p)
        } else {
            fun face(t: Bitmap, dst: FloatArray, mul: Int) {
                val m = Matrix()
                m.setPolyToPoly(floatArrayOf(0f, 0f, 16f, 0f, 0f, 16f), 0, dst, 0, 3)
                p.colorFilter = LightingColorFilter(mul, 0)
                c.drawBitmap(t, m, p)
            }
            val top = tile(Atlas.tileFor(id, 2)) ?: return null
            val left = tile(Atlas.tileFor(id, 4)) ?: return null
            val right = tile(Atlas.tileFor(id, 0)) ?: return null
            val k = 20f / 16f
            face(top, floatArrayOf(4f, 14f, 24f, 4f, 24f, 24f), 0xFFFFFF)
            face(left, floatArrayOf(4f, 14f, 24f, 24f, 4f, 34f), 0xB0B0B0)
            face(right, floatArrayOf(24f, 24f, 44f, 14f, 24f, 44f), 0x808080)
            if (k < 0) return null
        }
        cache[id] = bmp
        return bmp
    }
}
