package com.endroid.andcraft

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.endroid.andcraft.gl.GameSurfaceView
import com.endroid.andcraft.world.Blocks
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {
    private lateinit var game: GameSurfaceView
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            if (::game.isInitialized) {
                val r = game.renderer
                try { r.player } catch (_: Exception) { handler.postDelayed(this, 200); return }
                val p = r.player
                val day = if (r.dayK > 0.55f) "Day" else if (r.dayK < 0.3f) "Night" else "Dusk"
                findViewById<TextView>(R.id.hudInfo).text =
                    "${r.fps} fps | ${p.x.toInt()}, ${p.y.toInt()}, ${p.z.toInt()}\n" +
                        "${if (p.creative) "Creative" else "Survival"} · $day · HP ${p.hp} · Food ${p.food}\n" +
                        "${Blocks.name(p.selected())} ×${if (p.creative) "∞" else p.inv.getOrElse(p.selected()) { 0 }}"
                val toast = findViewById<TextView>(R.id.toast)
                toast.text = if (r.toastT > 0f) r.toast else ""
                refreshHotbar()
            }
            handler.postDelayed(this, 200)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        game = findViewById(R.id.gameView)
        bindHold(R.id.btnBreak, { game.renderer.breaking = true }, { game.renderer.breaking = false })
        findViewById<MaterialButton>(R.id.btnPlace).setOnClickListener { game.renderer.placeQueued = true }
        bindHold(R.id.btnJump, { game.renderer.jumpQueued = true }, {})
        findViewById<MaterialButton>(R.id.btnMode).setOnClickListener {
            val p = game.renderer.player
            p.creative = !p.creative
            game.renderer.toast(if (p.creative) "Creative mode" else "Survival mode")
        }
        findViewById<MaterialButton>(R.id.btnPlace).setOnLongClickListener {
            val p = game.renderer.player
            p.slot = (p.slot + 1) % p.hotbar.size
            game.renderer.toast(Blocks.name(p.selected()))
            true
        }
        handler.post(tick)
    }

    private fun refreshHotbar() {
        try { game.renderer.player } catch (_: Exception) { return }
        val bar = findViewById<LinearLayout>(R.id.hotbar)
        val p = game.renderer.player
        if (bar.childCount != p.hotbar.size) {
            bar.removeAllViews()
            for (i in p.hotbar.indices) {
                val tv = TextView(this)
                val lp = LinearLayout.LayoutParams(48, 48)
                lp.marginStart = 3; lp.marginEnd = 3
                tv.layoutParams = lp
                tv.textSize = 9f
                tv.setTextColor(Color.WHITE)
                tv.setBackgroundColor(0x99000000.toInt())
                tv.setPadding(4, 4, 4, 4)
                tv.setOnClickListener { p.slot = i; game.renderer.toast(Blocks.name(p.hotbar[i])) }
                bar.addView(tv)
            }
        }
        for (i in 0 until bar.childCount) {
            val tv = bar.getChildAt(i) as TextView
            val id = p.hotbar[i]
            tv.text = Blocks.name(id).take(4)
            tv.setBackgroundColor(if (i == p.slot) 0xCCFFFFFF.toInt() else 0x99000000.toInt())
            tv.setTextColor(if (i == p.slot) Color.BLACK else Color.WHITE)
        }
    }

    private fun bindHold(id: Int, down: () -> Unit, up: () -> Unit) {
        findViewById<MaterialButton>(id).setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { down(); true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { up(); true }
                else -> false
            }
        }
    }

    override fun onPause() { super.onPause(); game.onPause() }
    override fun onResume() { super.onResume(); game.onResume() }
    override fun onDestroy() { handler.removeCallbacks(tick); super.onDestroy() }
}
