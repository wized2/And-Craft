package com.endroid.andcraft

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Procedural sound effects (no asset files) and haptics. */
object Sfx {
    private const val RATE = 22050
    @Volatile var on = true
    @Volatile var haptics = true
    private var vibrator: Vibrator? = null
    private val cache = HashMap<String, ShortArray>()
    private val pool = ThreadPoolExecutor(2, 3, 5, TimeUnit.SECONDS, ArrayBlockingQueue(4), ThreadPoolExecutor.DiscardPolicy())

    fun init(ctx: Context) {
        vibrator = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        val p = ctx.getSharedPreferences("andcraft", Context.MODE_PRIVATE)
        on = p.getBoolean("set_snd", true)
        haptics = p.getBoolean("set_hap", true)
    }

    fun save(ctx: Context) {
        ctx.getSharedPreferences("andcraft", Context.MODE_PRIVATE).edit().putBoolean("set_snd", on).putBoolean("set_hap", haptics).apply()
    }

    fun vib(ms: Long) {
        if (!haptics) return
        try {
            val v = vibrator ?: return
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            else @Suppress("DEPRECATION") v.vibrate(ms)
        } catch (e: Exception) { }
    }

    private class Gen(dur: Float) {
        val n = (RATE * dur).toInt()
        val d = FloatArray(n)
        fun tone(f0: Float, f1: Float, g: Float, saw: Boolean = false): Gen {
            var ph = 0.0
            for (i in 0 until n) {
                val t = i / n.toFloat()
                val f = f0 + (f1 - f0) * t
                ph += 2 * PI * f / RATE
                val s = if (saw) ((ph / (2 * PI)) % 1.0 * 2 - 1).toFloat() else sin(ph).toFloat()
                d[i] += s * g * exp(-4f * t)
            }
            return this
        }
        fun noise(g: Float, lp: Float): Gen {
            var y = 0f
            val rnd = java.util.Random(7)
            for (i in 0 until n) {
                val t = i / n.toFloat()
                y += (rnd.nextFloat() * 2f - 1f - y) * lp
                d[i] += y * g * exp(-5f * t)
            }
            return this
        }
        fun pcm() = ShortArray(n) { (d[it].coerceIn(-1f, 1f) * 30000).toInt().toShort() }
    }

    private fun build(kind: String): ShortArray = when (kind) {
        "break" -> Gen(0.14f).noise(0.9f, 0.35f).tone(150f, 60f, 0.5f).pcm()
        "place" -> Gen(0.08f).tone(220f, 280f, 0.7f).noise(0.4f, 0.5f).pcm()
        "hurt" -> Gen(0.22f).tone(200f, 80f, 0.8f, true).noise(0.3f, 0.3f).pcm()
        "hit" -> Gen(0.09f).noise(0.8f, 0.6f).tone(140f, 90f, 0.5f, true).pcm()
        "eat" -> Gen(0.1f).noise(0.7f, 0.25f).pcm()
        "craft" -> Gen(0.18f).tone(440f, 660f, 0.7f).pcm()
        "boom" -> Gen(0.9f).noise(1.0f, 0.12f).tone(70f, 28f, 0.9f, true).pcm()
        "hiss" -> Gen(0.8f).noise(0.5f, 0.85f).pcm()
        "splash" -> Gen(0.25f).noise(0.8f, 0.3f).tone(220f, 120f, 0.4f).pcm()
        "die" -> Gen(0.7f).tone(220f, 40f, 0.9f, true).pcm()
        "thunder" -> Gen(1.6f).noise(1.0f, 0.06f).tone(55f, 30f, 0.8f, true).pcm()
        "step" -> Gen(0.05f).noise(0.5f, 0.3f).pcm()
        "pop" -> Gen(0.06f).tone(500f, 800f, 0.6f).pcm()
        else -> Gen(0.05f).noise(0.3f, 0.3f).pcm()
    }

    fun play(kind: String, volume: Float = 1f) {
        if (!on) return
        pool.execute {
            try {
                val data = synchronized(cache) { cache.getOrPut(kind) { build(kind) } }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(data.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(data, 0, data.size)
                track.setVolume(volume.coerceIn(0f, 1f))
                track.play()
                Thread.sleep((data.size * 1000L / RATE) + 60)
                track.release()
            } catch (e: Exception) { }
        }
    }
}
