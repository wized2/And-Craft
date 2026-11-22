package com.endroid.andcraft

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
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
                val p = r.player
                findViewById<TextView>(R.id.hudInfo).text =
                    "${r.fps} fps  |  ${p.x.toInt()}, ${p.y.toInt()}, ${p.z.toInt()}\n" +
                        "Creative  |  ${Blocks.name(p.selected)}"
            }
            handler.postDelayed(this, 250)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        game = findViewById(R.id.gameView)
        bindHold(R.id.btnBreak, { game.renderer.breaking = true }, { game.renderer.breaking = false })
        findViewById<MaterialButton>(R.id.btnPlace).setOnClickListener {
            game.renderer.placeQueued = true
        }
        bindHold(R.id.btnJump, { game.renderer.jumpQueued = true }, {})
        // hotbar cycle on place long-click
        findViewById<MaterialButton>(R.id.btnPlace).setOnLongClickListener {
            val order = intArrayOf(Blocks.GRASS, Blocks.DIRT, Blocks.STONE, Blocks.LOG, Blocks.PLANKS, Blocks.SAND)
            val i = order.indexOf(game.renderer.player.selected).let { if (it < 0) 0 else (it + 1) % order.size }
            game.renderer.player.selected = order[i]
            true
        }
        handler.post(tick)
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

    override fun onPause() {
        super.onPause()
        game.onPause()
    }

    override fun onResume() {
        super.onResume()
        game.onResume()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }
}
