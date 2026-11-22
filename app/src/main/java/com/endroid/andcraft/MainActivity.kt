package com.endroid.andcraft

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.endroid.andcraft.gl.GameSurfaceView
import com.endroid.andcraft.world.Blocks
import com.endroid.andcraft.world.Crafting
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {
    private lateinit var game: GameSurfaceView
    private val handler = Handler(Looper.getMainLooper())
    private var lastJumpTap = 0L
    private var dialog: AlertDialog? = null

    private val tick = object : Runnable {
        override fun run() {
            val r = game.renderer
            if (r.ready) {
                val p = r.player
                val day = if (r.dayK > 0.55f) "Day" else if (r.dayK < 0.3f) "Night" else "Dusk"
                val dayNo = (r.clock / r.dayLen).toInt() + 1
                findViewById<TextView>(R.id.hudInfo).text =
                    "${r.fps} fps | ${p.x.toInt()}, ${p.y.toInt()}, ${p.z.toInt()}\n" +
                        "${if (p.creative) "Creative" else "Survival"} · $day $dayNo · seed ${r.world.seed}"
                findViewById<TextView>(R.id.toast).text = if (r.toastT > 0f) r.toast else ""
                val stats = findViewById<View>(R.id.stats)
                stats.visibility = if (p.creative) View.GONE else View.VISIBLE
                findViewById<TextView>(R.id.hearts).text = "♥".repeat((p.hp + 1) / 2)
                findViewById<TextView>(R.id.foodbar).text = "●".repeat((p.food + 1) / 2)
                val mp = r.mineP
                findViewById<TextView>(R.id.crosshair).text = if (mp > 0f) "+\n${(mp * 100).toInt()}%" else "+"
                refreshHotbar()
            }
            handler.postDelayed(this, 150)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        game = findViewById(R.id.gameView)
        val r = game.renderer
        bindHold(R.id.btnBreak, { r.breaking = true }, { r.breaking = false })
        bindHold(R.id.btnJump, {
            r.jumpHeld = true
            val now = System.currentTimeMillis()
            if (now - lastJumpTap < 300) game.queueEvent { if (r.ready && r.player.creative) r.player.fly = !r.player.fly }
            lastJumpTap = now
        }, { r.jumpHeld = false })
        findViewById<MaterialButton>(R.id.btnPlace).setOnClickListener { r.placeQueued = true }
        findViewById<MaterialButton>(R.id.btnEat).setOnClickListener {
            game.queueEvent {
                if (r.ready) {
                    val m = r.player.eat()
                    r.toast(if (m != null) "Ate $m" else "Nothing to eat")
                }
            }
        }
        findViewById<MaterialButton>(R.id.btnCraft).setOnClickListener { showCraft() }
        findViewById<MaterialButton>(R.id.btnMenu).setOnClickListener { showMenu() }
        handler.post(tick)
    }

    private fun showDialog(b: AlertDialog.Builder) {
        dialog?.dismiss()
        game.renderer.paused = true
        b.setOnDismissListener { game.renderer.paused = false }
        dialog = b.show()
    }

    private fun showCraft() {
        val r = game.renderer
        if (!r.ready) return
        val p = r.player
        if (p.creative) {
            showDialog(AlertDialog.Builder(this).setTitle("Crafting")
                .setMessage("Creative mode has unlimited blocks. Switch to Survival in the menu to craft.")
                .setPositiveButton("OK", null))
            return
        }
        val rec = Crafting.recipes
        val labels = rec.map {
            (if (it.owned(p)) "✓ " else if (it.canCraft(p)) "● " else "○ ") + it.name +
                "\n    " + it.costText(p) + (if (it.note.isNotEmpty()) " · ${it.note}" else "")
        }.toTypedArray()
        showDialog(AlertDialog.Builder(this).setTitle("Crafting")
            .setItems(labels) { _, i ->
                game.queueEvent {
                    val ok = Crafting.craft(r.player, rec[i])
                    r.toast(if (ok) "Crafted ${rec[i].name}" else "Missing materials")
                    runOnUiThread { showCraft() }
                }
            }
            .setNegativeButton("Close", null))
    }

    private fun showBackpack() {
        val p = game.renderer.player
        val tier = arrayOf("none", "wooden", "stone")
        val sb = StringBuilder()
        sb.append("Pickaxe: ${tier[p.pick.coerceIn(0, 2)]}\nAxe: ${tier[p.axe.coerceIn(0, 2)]}\nSword: ${tier[p.sword.coerceIn(0, 2)]}\n")
        sb.append("Armor: ${arrayOf("none", "leather", "gold")[p.armor.coerceIn(0, 2)]}\n\n")
        var any = false
        for (id in 1 until p.inv.size) if (p.inv[id] > 0) { sb.append("${Blocks.name(id)} ×${p.inv[id]}\n"); any = true }
        if (!any) sb.append("Empty")
        showDialog(AlertDialog.Builder(this).setTitle("Backpack").setMessage(sb.toString()).setPositiveButton("OK", null))
    }

    private fun showMenu() {
        val r = game.renderer
        if (!r.ready) return
        val items = arrayOf(
            "Mode: ${if (r.player.creative) "Creative" else "Survival"} (tap to switch)",
            "Backpack",
            "Back to spawn",
            "New world",
            "Close"
        )
        showDialog(AlertDialog.Builder(this).setTitle("And Craft").setItems(items) { _, i ->
            when (i) {
                0 -> game.queueEvent {
                    r.player.creative = !r.player.creative
                    if (!r.player.creative) r.player.fly = false
                    r.toast(if (r.player.creative) "Creative mode" else "Survival mode")
                    r.saveRequested = true
                }
                1 -> showBackpack()
                2 -> game.queueEvent { r.player.respawn(r.world.findSpawn()); r.toast("Teleported to spawn") }
                3 -> confirmNewWorld()
            }
        })
    }

    private fun confirmNewWorld() {
        showDialog(AlertDialog.Builder(this).setTitle("New world?")
            .setMessage("This deletes your current world.")
            .setPositiveButton("Delete & restart") { _, _ ->
                game.renderer.ready = false
                getSharedPreferences("andcraft", MODE_PRIVATE).edit().clear().commit()
                recreate()
            }
            .setNegativeButton("Cancel", null))
    }

    private fun refreshHotbar() {
        val p = game.renderer.player
        val bar = findViewById<LinearLayout>(R.id.hotbar)
        val dp = resources.displayMetrics.density
        if (bar.childCount != p.hotbar.size) {
            bar.removeAllViews()
            for (i in p.hotbar.indices) {
                val tv = TextView(this)
                val lp = LinearLayout.LayoutParams((46 * dp).toInt(), (46 * dp).toInt())
                lp.marginStart = (2 * dp).toInt(); lp.marginEnd = (2 * dp).toInt()
                tv.layoutParams = lp
                tv.textSize = 9f
                tv.gravity = android.view.Gravity.CENTER
                tv.setTextColor(Color.WHITE)
                tv.setPadding(2, 2, 2, 2)
                tv.setOnClickListener {
                    p.slot = i
                    game.renderer.toast(Blocks.name(p.hotbar[i]), 1f)
                }
                bar.addView(tv)
            }
        }
        for (i in 0 until bar.childCount) {
            val tv = bar.getChildAt(i) as TextView
            val id = p.hotbar[i]
            val n = if (p.creative) "∞" else p.count(id).toString()
            tv.text = Blocks.name(id).take(5) + "\n" + n
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

    override fun onPause() {
        game.queueEvent { game.renderer.save() }
        game.onPause()
        super.onPause()
    }

    override fun onResume() { super.onResume(); game.onResume() }
    override fun onDestroy() { handler.removeCallbacks(tick); dialog?.dismiss(); super.onDestroy() }
}
