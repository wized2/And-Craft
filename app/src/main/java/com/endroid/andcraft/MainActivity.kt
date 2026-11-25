package com.endroid.andcraft

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.widget.EditText
import android.widget.Toast
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
import com.endroid.andcraft.world.Machines
import com.endroid.andcraft.world.Objectives
import com.endroid.andcraft.world.Trades
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {
    private lateinit var game: GameSurfaceView
    private val handler = Handler(Looper.getMainLooper())
    private var lastJumpTap = 0L
    private var dialog: AlertDialog? = null
    private var lastScrollSlot = -1

    private val tick = object : Runnable {
        override fun run() {
            val r = game.renderer
            if (r.ready) {
                val p = r.player
                val day = if (r.dayK > 0.55f) "Day" else if (r.dayK < 0.3f) "Night" else "Dusk"
                val dayNo = (r.clock / r.dayLen).toInt() + 1
                val info = findViewById<TextView>(R.id.hudInfo)
                val hh = ((r.clock / r.dayLen * 24f + 6f) % 24f).toInt()
                val mm = ((r.clock / r.dayLen * 24f * 60f) % 60f).toInt()
                val icon = if (r.dayK > 0.55f) "☀" else if (r.dayK < 0.3f) "☾" else "◐"
                findViewById<TextView>(R.id.hudDay).text = "$icon $day · Day $dayNo · %02d:%02d".format(hh, mm)
                val tierN = arrayOf("Fist", "Wood Sword", "Stone Sword", "Iron Sword", "Diamond Sword")
                findViewById<TextView>(R.id.hudCombat).text = if (p.creative) "Creative" else "⚔ ${tierN[p.sword.coerceIn(0, 4)]} · ${p.kills} kills"
                info.visibility = if (r.showInfo) View.VISIBLE else View.GONE
                info.text = "${r.fps} fps | ${p.x.toInt()}, ${p.y.toInt()}, ${p.z.toInt()} | seed ${r.world.seed}"
                val q = findViewById<TextView>(R.id.quest)
                val oi = r.objIdx
                if (!p.creative && oi < Objectives.list.size) {
                    q.visibility = View.VISIBLE
                    q.text = "★ ${oi + 1}/${Objectives.list.size}: ${Objectives.list[oi].text}"
                } else q.visibility = View.GONE
                val req = r.uiRequest
                if (req != null) {
                    r.uiRequest = null
                    when (req.kind) {
                        "trade" -> showTrade(req.prof)
                        "furnace" -> showFurnace(req.x, req.y, req.z)
                        "chest" -> showChest(req.x, req.y, req.z)
                    }
                }
                findViewById<TextView>(R.id.toast).text = if (r.toastT > 0f) r.toast else ""
                val stats = findViewById<View>(R.id.stats)
                stats.visibility = if (p.creative) View.GONE else View.VISIBLE
                val armorTxt = if (p.armor > 0) "  🛡-${intArrayOf(0, 28, 44, 60, 80)[p.armor.coerceIn(0, 4)]}%" else ""
                findViewById<TextView>(R.id.hearts).text = meter(p.hp, "♥", 0xFFE53935.toInt(), armorTxt)
                findViewById<TextView>(R.id.foodbar).text = meter(p.food, "●", 0xFFE0952B.toInt(), "")
                val mp = r.mineP
                val tn = r.targetName
                findViewById<TextView>(R.id.crosshair).text = if (mp > 0f) "+\n${(mp * 100).toInt()}%" else if (tn.isNotEmpty()) "+\n$tn" else "+"
                refreshHotbar()
            }
            handler.postDelayed(this, 150)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Sfx.init(this)
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
                    if (m != null) Sfx.play("eat")
                }
            }
        }
        findViewById<MaterialButton>(R.id.btnCraft).setOnClickListener { showCraft() }
        findViewById<MaterialButton>(R.id.btnMenu).setOnClickListener { showMenu() }
        findViewById<MaterialButton>(R.id.btnBlocks).setOnClickListener { showBlockPicker() }
        handler.post(tick)
    }

    /** 10 icons, filled by value/2 (half steps count as full), the rest dimmed */
    private fun meter(v: Int, sym: String, on: Int, tail: String): SpannableString {
        val s = SpannableString(sym.repeat(10) + tail)
        val full = (v + 1) / 2
        s.setSpan(ForegroundColorSpan(on), 0, full.coerceIn(0, 10) * sym.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        s.setSpan(ForegroundColorSpan(0x66FFFFFF), full.coerceIn(0, 10) * sym.length, 10 * sym.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        return s
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
                    if (ok) Sfx.play("craft")
                    runOnUiThread { showCraft() }
                }
            }
            .setNegativeButton("Close", null))
    }

    /** grid of every placeable block: tap to put it in the selected hotbar slot */
    private fun showBlockPicker() {
        val r = game.renderer
        if (!r.ready) return
        val p = r.player
        val dp = resources.displayMetrics.density
        val ids = (1..57).filter { (Blocks.placeable(it) || it == Blocks.SEEDS) && (p.creative || p.count(it) > 0) }
        val grid = android.widget.GridLayout(this)
        grid.columnCount = 5
        grid.setPadding((8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt())
        val sv = android.widget.ScrollView(this)
        sv.addView(grid)
        val dlg = AlertDialog.Builder(this).setTitle("Blocks (tap to put in slot ${p.slot + 1})").setView(sv).setNegativeButton("Close", null)
        for (id in ids) {
            val tv = TextView(this)
            tv.layoutParams = android.widget.GridLayout.LayoutParams().apply { width = (62 * dp).toInt(); height = (68 * dp).toInt(); setMargins(3, 3, 3, 3) }
            tv.gravity = android.view.Gravity.CENTER_HORIZONTAL
            tv.textSize = 9f
            tv.setTextColor(Color.WHITE)
            tv.setBackgroundColor(0xFF333333.toInt())
            tv.text = Blocks.name(id).take(8)
            val ic = Icons.get(id)
            if (ic != null) {
                val d = BitmapDrawable(resources, ic)
                d.setBounds(0, 0, (34 * dp).toInt(), (34 * dp).toInt()); d.isFilterBitmap = false
                tv.setCompoundDrawables(null, d, null, null)
            }
            tv.setOnClickListener {
                p.hotbar[p.slot] = id
                lastScrollSlot = -1
                dialog?.dismiss()
            }
            grid.addView(tv)
        }
        showDialog(dlg)
    }

    private fun showBackpack() {
        val p = game.renderer.player
        val tier = arrayOf("none", "wooden", "stone", "iron", "diamond")
        val sb = StringBuilder()
        sb.append("Pickaxe: ${tier[p.pick.coerceIn(0, 4)]}\nAxe: ${tier[p.axe.coerceIn(0, 4)]}\nSword: ${tier[p.sword.coerceIn(0, 4)]}\n")
        sb.append("Armor: ${arrayOf("none", "leather", "gold", "iron", "diamond")[p.armor.coerceIn(0, 4)]}\n\n")
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
            "Settings",
            "Back to spawn",
            "New world",
            "Commands",
            "Screenshot",
            "Copy seed (${r.world.seed})",
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
                2 -> showSettings()
                3 -> game.queueEvent { r.player.respawn(r.world.findSpawn()); r.toast("Teleported to spawn") }
                4 -> confirmNewWorld()
                5 -> showCommands()
                6 -> { r.shotReq = true }
                7 -> {
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("seed", r.world.seed.toString()))
                    Toast.makeText(this, "Seed copied", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    /** run on the GL thread, then reopen a dialog on the UI thread */
    private fun glThen(block: () -> Unit, then: () -> Unit) {
        game.queueEvent { block(); runOnUiThread { then() } }
    }

    private fun showTrade(prof: Int) {
        val r = game.renderer
        val trades = Trades.BY_PROF[prof] ?: Trades.BY_PROF.getValue(0)
        val labels = trades.map { it.name }.toTypedArray()
        showDialog(AlertDialog.Builder(this).setTitle("Villager - ${Trades.PROF_NAMES[prof.coerceIn(0, 2)]}")
            .setItems(labels) { _, i ->
                glThen({
                    val ok = Trades.apply(r.player, trades[i])
                    r.toast(if (ok) "Trade done!" else "Not enough items", 1.5f)
                }) { showTrade(prof) }
            }
            .setNegativeButton("Close", null))
    }

    private fun showFurnace(x: Int, y: Int, z: Int) {
        val r = game.renderer
        val p = r.player
        val f = Machines.furnace(x, y, z)
        val labels = ArrayList<String>()
        val acts = ArrayList<() -> Unit>()
        labels.add("Add coal (+40s fuel)  [have ${p.count(Blocks.COAL_ORE)}]")
        acts.add {
            if (p.take(Blocks.COAL_ORE)) { f.fuel += 40f; r.toast("Fueled furnace", 1f) } else r.toast("No coal", 1f)
        }
        labels.add("Add log as fuel (+15s)  [have ${p.count(Blocks.LOG)}]")
        acts.add {
            if (p.take(Blocks.LOG)) { f.fuel += 15f; r.toast("Fueled furnace", 1f) } else r.toast("No logs", 1f)
        }
        for ((raw, out) in Machines.SMELT) {
            if (!p.creative && p.count(raw) <= 0) continue
            labels.add("Smelt ${Blocks.name(raw)} -> ${Blocks.name(out)}  [have ${p.count(raw)}]")
            acts.add {
                if (f.inId != 0) r.toast("Furnace busy", 1f)
                else if (p.take(raw)) { f.inId = raw; f.prog = 0f; r.toast("Smelting ${Blocks.name(raw)}", 1f) }
            }
        }
        labels.add("Take output: ${f.outN}x ${if (f.outId != 0) Blocks.name(f.outId) else "-"}")
        acts.add {
            if (f.outN > 0) { p.give(f.outId, f.outN); r.toast("Took ${f.outN}x ${Blocks.name(f.outId)}", 1.2f); f.outN = 0; f.outId = 0 }
            else r.toast("Nothing ready", 1f)
        }
        val status = if (f.fuel > 0f) "Fuel ${f.fuel.toInt()}s · smelting ${(f.prog * 100).toInt()}%" else "Add coal, then raw food or gold ore"
        showDialog(AlertDialog.Builder(this).setTitle("Furnace").setMessage(status)
            .setItems(labels.toTypedArray()) { _, i -> glThen({ acts[i]() }) { showFurnace(x, y, z) } }
            .setNegativeButton("Close", null))
    }

    private fun showChest(x: Int, y: Int, z: Int) {
        val r = game.renderer
        val p = r.player
        val c = Machines.chest(x, y, z)
        val labels = ArrayList<String>()
        val acts = ArrayList<() -> Unit>()
        for ((id, n) in c.entries.filter { it.value > 0 }.toList()) {
            labels.add("Take ${Blocks.name(id)} x$n")
            acts.add { p.give(id, n); c[id] = 0 }
        }
        val sel = p.selected()
        labels.add("Deposit selected: ${Blocks.name(sel)} x${if (p.creative) 64 else p.count(sel)}")
        acts.add {
            if (p.creative) c[sel] = (c[sel] ?: 0) + 64
            else if (p.count(sel) > 0) { val n = p.count(sel); p.inv[sel] = 0; c[sel] = (c[sel] ?: 0) + n }
            else r.toast("Nothing to deposit", 1f)
        }
        showDialog(AlertDialog.Builder(this).setTitle("Chest")
            .setItems(labels.toTypedArray()) { _, i -> glThen({ acts[i]() }) { showChest(x, y, z) } }
            .setNegativeButton("Close", null))
    }

    private fun showSettings() {
        val r = game.renderer
        val views = arrayOf("First person", "Third person (back)", "Third person (front)")
        val items = arrayOf(
            "Render distance: ${r.renderDist}",
            "Look sensitivity: ${r.sens}",
            "Field of view: ${r.fov}",
            "Auto-jump: ${if (r.autoJump) "on" else "off"}",
            "Camera: ${views[r.camView.coerceIn(0, 2)]}",
            "Debug info: ${if (r.showInfo) "on" else "off"}",
            "Sound: ${if (Sfx.on) "on" else "off"}",
            "Haptics: ${if (Sfx.haptics) "on" else "off"}"
        )
        showDialog(AlertDialog.Builder(this).setTitle("Settings").setItems(items) { _, i ->
            fun <T> next(a: List<T>, cur: T): T = a[(a.indexOf(cur) + 1).mod(a.size)]
            when (i) {
                0 -> r.renderDist = next(listOf(3, 4, 5, 6, 8), r.renderDist)
                1 -> r.sens = next(listOf(0.5f, 0.75f, 1f, 1.5f, 2f), r.sens)
                2 -> r.fov = next(listOf(60, 75, 90, 100), r.fov)
                3 -> r.autoJump = !r.autoJump
                4 -> r.camView = (r.camView + 1) % 3
                5 -> r.showInfo = !r.showInfo
                6 -> { Sfx.on = !Sfx.on; Sfx.save(this) }
                7 -> { Sfx.haptics = !Sfx.haptics; Sfx.save(this) }
            }
            r.saveSettings()
            showSettings()
        }.setNegativeButton("Close", null))
    }

    private fun confirmNewWorld() {
        val input = EditText(this)
        input.hint = "Seed (blank = random)"
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        showDialog(AlertDialog.Builder(this).setTitle("New world?")
            .setMessage("This deletes your current world.")
            .setView(input)
            .setPositiveButton("Delete & restart") { _, _ ->
                val seed = input.text.toString().trim().toLongOrNull()
                game.renderer.ready = false
                val ed = getSharedPreferences("andcraft", MODE_PRIVATE).edit().clear()
                if (seed != null) ed.putLong("seed", seed and 0xffffffL)
                ed.commit()
                Sfx.save(this)
                recreate()
            }
            .setNegativeButton("Cancel", null))
    }

    private fun showCommands() {
        val r = game.renderer
        val names = arrayOf("Set day", "Set night", "Clear weather", "Start rain", "Heal and feed",
            "Iron tool kit", "Diamond tool kit", "Kill all mobs", "TNT x10 + flint & steel", "Jump up 40 blocks", "Spawn a villager")
        showDialog(AlertDialog.Builder(this).setTitle("Commands").setItems(names) { _, i ->
            glThen({
                val p = r.player
                when (i) {
                    0 -> r.clock = (Math.floor((r.clock / r.dayLen).toDouble()).toFloat()) * r.dayLen + r.dayLen * 0.1f
                    1 -> r.clock = (Math.floor((r.clock / r.dayLen).toDouble()).toFloat()) * r.dayLen + r.dayLen * 0.6f
                    2 -> r.setWeather(false)
                    3 -> r.setWeather(true)
                    4 -> { p.hp = 20; p.food = 20; p.sat = 20f }
                    5 -> { p.pick = 3; p.axe = 3; p.sword = 3; p.armor = 3 }
                    6 -> { p.pick = 4; p.axe = 4; p.sword = 4; p.armor = 4 }
                    7 -> r.mobs.mobs.removeAll { it.type != com.endroid.andcraft.world.MobType.VILLAGER }
                    8 -> { p.give(Blocks.TNT, 10); p.give(Blocks.FLINT, 1) }
                    9 -> p.y += 40f
                    10 -> r.mobs.add(com.endroid.andcraft.world.MobType.VILLAGER, p.x + 2f, p.y, p.z, 0)
                }
                r.toast("Done: ${names[i]}")
            }) { }
        }.setNegativeButton("Close", null))
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
            tv.text = n
            val ic = Icons.get(id)
            if (ic != null) {
                val d = BitmapDrawable(resources, ic)
                d.setBounds(0, 0, (30 * resources.displayMetrics.density).toInt(), (30 * resources.displayMetrics.density).toInt())
                d.isFilterBitmap = false
                tv.setCompoundDrawables(null, d, null, null)
            } else tv.text = Blocks.name(id).take(5) + "\n" + n
            tv.setBackgroundColor(if (i == p.slot) 0xCCFFFFFF.toInt() else 0x99000000.toInt())
            if (i == p.slot && lastScrollSlot != i) {
                lastScrollSlot = i
                val sv = findViewById<android.widget.HorizontalScrollView>(R.id.hotbarScroll)
                sv.post { sv.smoothScrollTo(tv.left - sv.width / 2 + tv.width / 2, 0) }
            }
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
