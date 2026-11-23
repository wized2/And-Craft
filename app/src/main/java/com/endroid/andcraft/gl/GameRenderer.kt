package com.endroid.andcraft.gl

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.endroid.andcraft.world.*
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** a request for the activity to open a dialog (set on the GL thread) */
class UiReq(val kind: String, val x: Int = 0, val y: Int = 0, val z: Int = 0, val prof: Int = 0)

class GameRenderer(ctx: Context) : GLSurfaceView.Renderer {
    private val prefs = ctx.getSharedPreferences("andcraft", Context.MODE_PRIVATE)
    lateinit var world: World
    lateinit var player: Player
    val mobs = MobSystem()
    @Volatile var ready = false
    private var spawn = intArrayOf(0, 40, 0)

    private val meshes = HashMap<Long, ChunkMesh>()
    private val cube = CubeMesh()
    private var program = 0
    private var uMvp = 0
    private var uTint = 0
    private var uLight = 0
    private var aPos = 0
    private var aCol = 0
    private val vp = FloatArray(16)
    private val mvp = FloatArray(16)
    private val model = FloatArray(16)
    private val view = FloatArray(16)
    private val proj = FloatArray(16)
    private val look = FloatArray(3)
    private var aspect = 1.8f

    @Volatile var forward = 0f
    @Volatile var strafe = 0f
    @Volatile var sprint = false
    @Volatile var jumpHeld = false
    @Volatile var breaking = false
    @Volatile var placeQueued = false
    @Volatile var paused = false
    @Volatile var saveRequested = false
    @Volatile var mineP = 0f
    @Volatile var uiRequest: UiReq? = null

    // settings (saved)
    @Volatile var renderDist = prefs.getInt("set_rd", 4)
    @Volatile var fov = prefs.getInt("set_fov", 75)
    @Volatile var sens = prefs.getFloat("set_sens", 1f)
    @Volatile var autoJump = prefs.getBoolean("set_aj", true)
    @Volatile var showInfo = prefs.getBoolean("set_info", false)
    @Volatile var camView = prefs.getInt("view", 0) // 0 first person, 1 third back, 2 third front

    fun saveSettings() {
        prefs.edit().putInt("set_rd", renderDist).putInt("set_fov", fov).putFloat("set_sens", sens)
            .putBoolean("set_aj", autoJump).putBoolean("set_info", showInfo).putInt("view", camView).apply()
        applyFov()
        if (ready) player.autoJump = autoJump
    }

    private fun applyFov() { Matrix.perspectiveM(proj, 0, fov.toFloat(), aspect, 0.1f, 140f) }

    private var atkCd = 0f
    private var mx = Int.MIN_VALUE
    private var my = 0
    private var mz = 0
    private var mineT = 0f
    private var saveT = 0f
    private var objT = 0f
    @Volatile var objIdx = 0
    private var lastNs = System.nanoTime()
    @Volatile var fps = 0
    private var fpsT = 0f
    private var fpsC = 0
    @Volatile var clock = 120f
    val dayLen = 1200f
    val dayK: Float
        get() {
            val s = kotlin.math.sin(clock / dayLen * Math.PI * 2).toFloat()
            return ((s * 1.5f + 0.5f).coerceIn(0f, 1f))
        }
    val dayNo: Int get() = floor(clock / dayLen).toInt()
    @Volatile var toast = ""
    @Volatile var toastT = 0f
    fun toast(msg: String, sec: Float = 2f) { toast = msg; toastT = sec }

    // weather
    @Volatile var rain = 0f
    private var rainTarget = 0f
    private var rainT = 50f + Math.random().toFloat() * 60f
    private var timeAcc = 0f

    // ---------- save / load ----------
    private fun loadOrCreate() {
        val seed = if (prefs.contains("seed")) prefs.getLong("seed", 0L) else System.currentTimeMillis() and 0xffffffL
        world = World(seed)
        val ed = prefs.getString("edits", "") ?: ""
        if (ed.isNotEmpty()) world.decodeEdits(ed)
        Machines.decode(prefs.getString("machines", "") ?: "")
        spawn = world.findSpawn()
        if (prefs.contains("px")) {
            player = Player(prefs.getFloat("px", 0f), prefs.getFloat("py", 40f), prefs.getFloat("pz", 0f))
            player.yaw = prefs.getFloat("yaw", 0f)
            player.pitch = prefs.getFloat("pitch", 0f)
            player.hp = prefs.getInt("hp", 20).coerceIn(1, 20)
            player.food = prefs.getInt("food", 20).coerceIn(0, 20)
            player.sat = prefs.getFloat("sat", 5f)
            player.creative = prefs.getBoolean("creative", false)
            player.pick = prefs.getInt("pick", 0); player.axe = prefs.getInt("axe", 0)
            player.sword = prefs.getInt("sword", 0); player.armor = prefs.getInt("armor", 0)
            player.kills = prefs.getInt("kills", 0); player.brutes = prefs.getInt("brutes", 0)
            player.ate = prefs.getInt("ate", 0); player.sleeps = prefs.getInt("sleeps", 0)
            player.slot = prefs.getInt("slot", 0).coerceIn(0, player.hotbar.size - 1)
            val hb = (prefs.getString("hotbar", "") ?: "").split(',').mapNotNull { it.toIntOrNull() }
            if (hb.size == player.hotbar.size) for (i in hb.indices) player.hotbar[i] = hb[i]
            clock = prefs.getFloat("clock", 120f)
            objIdx = prefs.getInt("obj", 0)
            val inv = (prefs.getString("inv", "") ?: "").split(',')
            for (i in inv.indices) if (i < player.inv.size) player.inv[i] = inv[i].toIntOrNull() ?: 0
            val sp = (prefs.getString("spawn", "") ?: "").split(',').mapNotNull { it.toIntOrNull() }
            if (sp.size == 3) spawn = intArrayOf(sp[0], sp[1], sp[2])
        } else {
            player = Player(spawn[0] + 0.5f, spawn[1].toFloat(), spawn[2] + 0.5f)
            player.starterKit()
        }
        player.autoJump = autoJump
        toast(if (prefs.contains("px")) "Welcome back" else "New world - seed ${world.seed}", 3f)
    }

    fun save() {
        if (!ready) return
        val e = prefs.edit()
        e.putLong("seed", world.seed)
        e.putFloat("px", player.x).putFloat("py", player.y).putFloat("pz", player.z)
        e.putFloat("yaw", player.yaw).putFloat("pitch", player.pitch)
        e.putInt("hp", player.hp).putInt("food", player.food).putFloat("sat", player.sat)
        e.putBoolean("creative", player.creative)
        e.putInt("pick", player.pick).putInt("axe", player.axe).putInt("sword", player.sword).putInt("armor", player.armor)
        e.putInt("kills", player.kills).putInt("brutes", player.brutes).putInt("ate", player.ate).putInt("sleeps", player.sleeps)
        e.putInt("slot", player.slot).putFloat("clock", clock).putInt("obj", objIdx)
        e.putString("hotbar", player.hotbar.joinToString(","))
        e.putString("inv", player.inv.joinToString(","))
        e.putString("spawn", spawn.joinToString(","))
        e.putString("edits", world.encodeEdits())
        e.putString("machines", Machines.encode())
        e.apply()
    }

    // ---------- GL ----------
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.53f, 0.81f, 0.92f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        val vs = """
            uniform mat4 uMVP;
            uniform vec3 uTint;
            uniform float uLight;
            attribute vec3 aPos;
            attribute vec3 aCol;
            varying vec3 vCol;
            void main(){ vCol=aCol*uTint*uLight; gl_Position=uMVP*vec4(aPos,1.0); }
        """.trimIndent()
        val fs = """
            precision mediump float;
            varying vec3 vCol;
            void main(){ gl_FragColor=vec4(vCol,1.0); }
        """.trimIndent()
        program = link(vs, fs)
        uMvp = GLES20.glGetUniformLocation(program, "uMVP")
        uTint = GLES20.glGetUniformLocation(program, "uTint")
        uLight = GLES20.glGetUniformLocation(program, "uLight")
        aPos = GLES20.glGetAttribLocation(program, "aPos")
        aCol = GLES20.glGetAttribLocation(program, "aCol")
        cube.init()
        meshes.clear() // GL buffers are gone after a context loss
        if (!ready) {
            loadOrCreate()
            ready = true
        }
        lastNs = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        aspect = width.toFloat() / height.coerceAtLeast(1)
        applyFov()
    }

    override fun onDrawFrame(gl: GL10?) {
        if (!ready) return
        val now = System.nanoTime()
        val dt = ((now - lastNs) / 1e9f).coerceAtMost(0.05f)
        lastNs = now
        fpsC++; fpsT += dt
        if (fpsT >= 0.5f) { fps = (fpsC / fpsT).toInt(); fpsC = 0; fpsT = 0f }
        timeAcc += dt
        if (!paused) step(dt)
        updateMeshes()
        drawWorld()
    }

    private fun step(dt: Float) {
        clock += dt
        toastT -= dt
        atkCd -= dt
        player.update(world, dt, forward, strafe, jumpHeld, sprint)
        if (player.hp <= 0) {
            player.respawn(spawn)
            toast("You died - back at spawn", 3f)
        }
        Farming.update(world, player, dt)
        Machines.tick(dt)
        // villagers from freshly generated villages
        if (world.villagerSpawns.isNotEmpty()) {
            for (v in world.villagerSpawns.toList()) mobs.add(MobType.VILLAGER, v[0], v[1], v[2], v[3].toInt())
            world.villagerSpawns.clear()
            toast("You found a village!", 2.5f)
        }
        mobs.update(world, player, dayK, dt, dayNo)
        updateWeather(dt)
        player.lookDir(look)
        if (breaking) doBreak(dt) else resetMine()
        if (placeQueued) { placeQueued = false; doPlace() }
        // goals (survival only)
        objT += dt
        if (objT > 1f) {
            objT = 0f
            if (!player.creative && objIdx < Objectives.list.size && Objectives.list[objIdx].ok(player, dayNo)) {
                toast("Goal complete: ${Objectives.list[objIdx].text}", 3f)
                objIdx++
            }
        }
        saveT += dt
        if (saveT > 5f || saveRequested) { saveT = 0f; saveRequested = false; save() }
    }

    private fun updateWeather(dt: Float) {
        rainT -= dt
        if (rainT <= 0f) {
            val on = rainTarget == 0f && Math.random() < 0.6
            rainTarget = if (on) 1f else 0f
            rainT = if (on) 40f + Math.random().toFloat() * 50f else 90f + Math.random().toFloat() * 150f
            if (on) toast("It starts to rain...", 2f) else if (rain > 0.5f) toast("The rain stops", 1.8f)
        }
        rain += (rainTarget - rain) * min(1f, dt * 0.5f)
        if (abs(rainTarget - rain) < 0.01f) rain = rainTarget
    }

    private fun resetMine() { mx = Int.MIN_VALUE; mineT = 0f; mineP = 0f }

    private fun breakTime(id: Int): Float {
        if (player.creative) return 0.1f
        val h = Blocks.hardness(id)
        if (h <= 0f) return 0.05f
        val need = Blocks.needPick(id)
        val canH = need == 0 || player.pick >= need
        val sp = floatArrayOf(1f, 2f, 4f)
        var speed = 1f
        if (need > 0 && player.pick > 0) speed = sp[player.pick.coerceIn(0, 2)]
        if (Blocks.axeBlock(id) && player.axe > 0) speed = sp[player.axe.coerceIn(0, 2)]
        return max(0.05f, h * (if (canH) 1.5f else 5f) / speed)
    }

    private fun drops(id: Int): Int = when (id) {
        Blocks.STONE -> if (player.pick >= 1) Blocks.STONE else 0
        Blocks.COAL_ORE -> if (player.pick >= 1) Blocks.COAL_ORE else 0
        Blocks.BRICK -> if (player.pick >= 1) Blocks.BRICK else 0
        Blocks.FURNACE -> if (player.pick >= 1) Blocks.FURNACE else 0
        Blocks.GOLD_ORE -> if (player.pick >= 2) Blocks.GOLD_ORE else 0
        Blocks.GRASS, Blocks.FARMLAND, Blocks.PATH -> Blocks.DIRT
        Blocks.LEAVES -> if (Math.random() < 0.05) Blocks.APPLE else 0
        Blocks.TALLGRASS -> if (Math.random() < 0.25) Blocks.SEEDS else 0
        Blocks.ROSE, Blocks.DANDELION, Blocks.SNOW -> 0
        else -> id
    }

    private fun doBreak(dt: Float) {
        val mob = mobs.hit(player, look)
        if (mob != null) {
            resetMine()
            if (atkCd <= 0f) {
                var dmg = if (player.creative) 30 else player.weaponDmg()
                if (!player.onGround && player.vy < -1f && !player.inWater) dmg = (dmg * 1.5f).toInt()
                mob.hp -= dmg
                mob.hurt = 0.4f
                mob.stun = 0.35f; mob.wind = 0f // hitting a zombie interrupts its swing
                val kb = if (mob.big) 4f else 7f
                mob.vx += look[0] * kb; mob.vz += look[2] * kb
                player.addExh(0.1f)
                atkCd = 0.5f
                if (mob.hp <= 0) {
                    mobDrops(mob)
                    if (mob.type == MobType.ZOMBIE) { player.kills++; if (mob.big) player.brutes++ }
                    mobs.mobs.remove(mob)
                }
            }
            return
        }
        val hit = Raycast.cast(world, player.x, player.eyeY(), player.z, look[0], look[1], look[2],
            if (player.creative) 6f else 4.5f)
        if (hit == null || hit.y <= 0) { resetMine(); return }
        if (hit.x == mx && hit.y == my && hit.z == mz) mineT += dt
        else { mx = hit.x; my = hit.y; mz = hit.z; mineT = 0f }
        val need = breakTime(hit.id)
        mineP = (mineT / need).coerceIn(0f, 1f)
        if (mineT >= need) {
            resetMine()
            breakBlock(hit)
        }
    }

    private fun breakBlock(hit: Hit) {
        if (Farming.harvest(world, player, hit.x, hit.y, hit.z, hit.id)) return
        val d = drops(hit.id)
        world.set(hit.x, hit.y, hit.z, Blocks.AIR)
        if (hit.id == Blocks.FURNACE) Machines.furnaces.remove(Machines.key(hit.x, hit.y, hit.z))
        val above = world.get(hit.x, hit.y + 1, hit.z)
        if (Blocks.plant(above) && above != Blocks.LEAVES && above != Blocks.TORCH)
            world.set(hit.x, hit.y + 1, hit.z, Blocks.AIR)
        if (d > 0) player.give(d, 1)
        player.addExh(0.005f)
    }

    private fun mobDrops(m: Mob) {
        if (player.creative) return
        val r = { a: Int, b: Int -> a + (Math.random() * (b - a + 1)).toInt() }
        when (m.type) {
            MobType.PIG -> player.give(Blocks.RAW_PORK, r(1, 3))
            MobType.COW -> { player.give(Blocks.RAW_BEEF, r(1, 3)); player.give(Blocks.LEATHER, r(0, 2)) }
            MobType.SHEEP -> { if (!m.sheared) player.give(Blocks.WOOL, 1); player.give(Blocks.RAW_MUTTON, r(1, 2)) }
            MobType.ZOMBIE -> player.give(Blocks.ROTTEN, r(0, 2))
            MobType.VILLAGER -> player.give(Blocks.EMERALD, 1)
        }
    }

    private fun doPlace() {
        // villagers and sheep first
        val mob = mobs.hit(player, look)
        if (mob != null) {
            if (mob.type == MobType.VILLAGER) { uiRequest = UiReq("trade", prof = mob.prof); return }
            if (mob.type == MobType.SHEEP && !mob.sheared) {
                if (!player.creative && player.selected() != Blocks.SHEARS && player.axe < 1) {
                    toast("Need shears (craft) or an axe", 1.5f); return
                }
                mob.sheared = true
                player.give(Blocks.WOOL, 1 + (Math.random() * 2).toInt())
                toast("Sheared wool", 1.2f)
                return
            }
        }
        val hit = Raycast.cast(world, player.x, player.eyeY(), player.z, look[0], look[1], look[2], 5f) ?: return
        if (hit.id == Blocks.BED) { useBed(hit); return }
        if (hit.id == Blocks.FURNACE) { uiRequest = UiReq("furnace", hit.x, hit.y, hit.z); return }
        if (hit.id == Blocks.CHEST) { uiRequest = UiReq("chest", hit.x, hit.y, hit.z); return }
        if (Farming.tryUse(world, player, hit)) return
        val id = player.selected()
        if (id <= 0 || Blocks.isItem(id)) return
        val px = hit.x + hit.fx
        val py = hit.y + hit.fy
        val pz = hit.z + hit.fz
        if (py !in 0 until World.H) return
        val tb = world.get(px, py, pz)
        if (tb != Blocks.AIR && tb != Blocks.WATER && !Blocks.thin(tb)) return
        // do not build inside yourself
        val bx = floor(player.x).toInt(); val bz = floor(player.z).toInt()
        val by0 = floor(player.y).toInt(); val by1 = floor(player.y + 1.7f).toInt()
        if (Blocks.solid(id) && px == bx && pz == bz && py in by0..by1) return
        if (player.take(id)) world.set(px, py, pz, id) else toast("No ${Blocks.name(id)} left")
    }

    private fun useBed(hit: Hit) {
        spawn = intArrayOf(hit.x, hit.y + 1, hit.z)
        if (dayK < 0.32f) {
            clock = (floor(clock / dayLen) + 1f) * dayLen + dayLen * 0.1f
            player.sleeps++
            toast("Good morning! Spawn point set", 3f)
        } else toast("Spawn point set (sleep at night)", 2.5f)
        saveRequested = true
    }

    // ---------- meshes ----------
    private fun updateMeshes() {
        val rd = renderDist.coerceIn(2, 8)
        val pcx = floor(player.x / World.CS).toInt()
        val pcz = floor(player.z / World.CS).toInt()
        for (k in world.takeDirty()) {
            val cx = (k shr 32).toInt()
            val cz = k.toInt()
            if (abs(cx - pcx) > rd + 1 || abs(cz - pcz) > rd + 1) continue
            meshes.getOrPut(k) { ChunkMesh(cx, cz) }.build(world)
        }
        var budget = if (meshes.isEmpty()) 12 else 2
        val want = ArrayList<IntArray>()
        for (dz in -rd..rd) for (dx in -rd..rd) {
            if (dx * dx + dz * dz > rd * rd + 2) continue
            val k = ((pcx + dx).toLong() shl 32) xor ((pcz + dz).toLong() and 0xffffffffL)
            if (!meshes.containsKey(k)) want.add(intArrayOf(pcx + dx, pcz + dz, dx * dx + dz * dz))
        }
        want.sortBy { it[2] }
        for (w in want) {
            if (budget-- <= 0) break
            val k = (w[0].toLong() shl 32) xor (w[1].toLong() and 0xffffffffL)
            val m = ChunkMesh(w[0], w[1])
            m.build(world)
            meshes[k] = m
        }
        val it = meshes.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            val cx = (e.key shr 32).toInt()
            val cz = e.key.toInt()
            if (abs(cx - pcx) > rd + 2 || abs(cz - pcz) > rd + 2) { e.value.release(); it.remove() }
        }
    }

    // ---------- drawing ----------
    private fun drawWorld() {
        val k = dayK * (1f - 0.35f * rain)
        GLES20.glClearColor(0.05f + 0.48f * k, 0.07f + 0.74f * k, 0.12f + 0.80f * k, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        player.lookDir(look)
        val ex = player.x; val ey = player.eyeY(); val ez = player.z
        var cx = ex; var cy = ey; var cz = ez
        var tx = ex + look[0]; var ty = ey + look[1]; var tz = ez + look[2]
        if (camView != 0) {
            val sgn = if (camView == 1) -1f else 1f
            var d = 0.3f
            while (d < 3.6f) {
                val bx = floor(ex + look[0] * sgn * (d + 0.25f)).toInt()
                val by = floor(ey + look[1] * sgn * (d + 0.25f)).toInt()
                val bz = floor(ez + look[2] * sgn * (d + 0.25f)).toInt()
                if (Blocks.solid(world.get(bx, by, bz))) break
                d += 0.2f
            }
            cx = ex + look[0] * sgn * d; cy = ey + look[1] * sgn * d; cz = ez + look[2] * sgn * d
            if (camView == 1) { tx = cx + look[0]; ty = cy + look[1]; tz = cz + look[2] }
            else { tx = cx - look[0]; ty = cy - look[1]; tz = cz - look[2] }
        }
        Matrix.setLookAtM(view, 0, cx, cy, cz, tx, ty, tz, 0f, 1f, 0f)
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)
        GLES20.glUseProgram(program)
        GLES20.glUniform1f(uLight, (0.35f + 0.65f * k).coerceAtLeast(0.3f))
        GLES20.glUniform3f(uTint, 1f, 1f, 1f)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, vp, 0)
        for (m in meshes.values) m.draw(aPos, aCol)
        for (m in mobs.mobs.toList()) drawMob(m)
        if (camView != 0) drawPlayer()
        if (rain > 0.02f) drawRain()
    }

    private fun drawRain() {
        val n = (140 * rain).toInt()
        val s = 26f
        for (i in 0 until n) {
            val rx = ((i * 7919) % 3200) / 100f - 16f
            val rz = ((i * 104729) % 3200) / 100f - 16f
            val ph = ((i * 31 + 7) % 160) / 10f
            val ry = 8f - ((timeAcc * s + ph) % 16f)
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, player.x + rx, player.y + ry, player.z + rz)
            Matrix.scaleM(model, 0, 0.025f, 0.55f, 0.025f)
            Matrix.multiplyMM(mvp, 0, vp, 0, model, 0)
            GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
            GLES20.glUniform3f(uTint, 0.67f, 0.77f, 1f)
            cube.draw(aPos, aCol)
        }
    }

    private val pm = Mob(MobType.PIG, 0f, 0f, 0f)
    private fun drawPlayer() {
        pm.x = player.x; pm.y = player.y; pm.z = player.z; pm.yaw = player.yaw
        pm.hurt = if (player.hurtFlash > 0.3f) 0.1f else 0f
        val pants = floatArrayOf(0.2f, 0.25f, 0.55f)
        val skin = floatArrayOf(0.8f, 0.62f, 0.5f)
        part(pm, -0.13f, 0.4f, 0f, 0.24f, 0.8f, 0.26f, pants)
        part(pm, 0.13f, 0.4f, 0f, 0.24f, 0.8f, 0.26f, pants)
        part(pm, 0f, 1.1f, 0f, 0.5f, 0.7f, 0.28f, floatArrayOf(0.2f, 0.6f, 0.6f))
        part(pm, 0f, 1.68f, 0f, 0.44f, 0.44f, 0.44f, skin)
        part(pm, -0.37f, 1.08f, 0f, 0.2f, 0.7f, 0.24f, skin)
        part(pm, 0.37f, 1.08f, 0f, 0.2f, 0.7f, 0.24f, skin)
    }

    // body parts: center (relative to feet), size, colour
    private fun part(m: Mob, ox: Float, oy: Float, oz: Float, sx: Float, sy: Float, sz: Float, c: FloatArray) {
        val sc = if (m.big) 1.3f else 1f
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, m.x, m.y, m.z)
        Matrix.rotateM(model, 0, Math.toDegrees(m.yaw.toDouble()).toFloat(), 0f, 1f, 0f)
        Matrix.scaleM(model, 0, sc, sc, sc)
        Matrix.translateM(model, 0, ox - sx / 2f, oy - sy / 2f, oz - sz / 2f)
        Matrix.scaleM(model, 0, sx, sy, sz)
        Matrix.multiplyMM(mvp, 0, vp, 0, model, 0)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        val h = if (m.hurt > 0f) 1f else 0f
        val d = if (m.big) 0.72f else 1f
        GLES20.glUniform3f(uTint, d * c[0] + h * 0.8f, d * c[1] * (1f - h * 0.6f), d * c[2] * (1f - h * 0.6f))
        cube.draw(aPos, aCol)
    }

    private fun drawMob(m: Mob) {
        val c = m.color()
        val skin = floatArrayOf(0.75f, 0.58f, 0.45f)
        when (m.type) {
            MobType.ZOMBIE -> {
                val dark = floatArrayOf(0.16f, 0.2f, 0.45f)
                val ar = if (m.wind > 0f) 0.55f else 0.25f // arms raise during the wind-up
                part(m, -0.13f, 0.3f, 0f, 0.24f, 0.6f, 0.26f, dark)
                part(m, 0.13f, 0.3f, 0f, 0.24f, 0.6f, 0.26f, dark)
                part(m, 0f, 0.95f, 0f, 0.5f, 0.7f, 0.28f, floatArrayOf(0.2f, 0.5f, 0.55f))
                part(m, 0f, 1.58f, 0f, 0.42f, 0.42f, 0.42f, c)
                part(m, -0.38f, 1.15f + ar * 0.3f, -0.25f, 0.18f, 0.18f, 0.6f, c)
                part(m, 0.38f, 1.15f + ar * 0.3f, -0.25f, 0.18f, 0.18f, 0.6f, c)
            }
            MobType.VILLAGER -> {
                val robe = when (m.prof) {
                    1 -> floatArrayOf(0.85f, 0.85f, 0.8f)
                    2 -> floatArrayOf(0.5f, 0.5f, 0.55f)
                    else -> c
                }
                part(m, 0f, 0.8f, 0f, 0.55f, 1.2f, 0.34f, robe)
                part(m, 0f, 1.6f, 0f, 0.4f, 0.5f, 0.4f, skin)
                part(m, 0f, 1.5f, -0.26f, 0.12f, 0.2f, 0.12f, skin)
            }
            MobType.PIG -> {
                part(m, 0f, 0.55f, 0f, 0.55f, 0.45f, 0.9f, c)
                part(m, 0f, 0.65f, -0.6f, 0.4f, 0.38f, 0.36f, c)
                part(m, 0f, 0.6f, -0.82f, 0.2f, 0.14f, 0.08f, floatArrayOf(0.85f, 0.5f, 0.55f))
                for (lx in floatArrayOf(-0.17f, 0.17f)) for (lz in floatArrayOf(-0.28f, 0.28f))
                    part(m, lx, 0.17f, lz, 0.16f, 0.34f, 0.16f, c)
            }
            MobType.COW -> {
                part(m, 0f, 0.95f, 0f, 0.7f, 0.7f, 1.2f, c)
                part(m, 0f, 1.1f, -0.78f, 0.5f, 0.5f, 0.45f, c)
                part(m, 0f, 1.0f, -1.0f, 0.3f, 0.2f, 0.08f, floatArrayOf(0.85f, 0.72f, 0.6f))
                for (lx in floatArrayOf(-0.22f, 0.22f)) for (lz in floatArrayOf(-0.42f, 0.42f))
                    part(m, lx, 0.3f, lz, 0.2f, 0.6f, 0.2f, c)
            }
            MobType.SHEEP -> {
                val wool = if (m.sheared) floatArrayOf(0.85f, 0.72f, 0.62f) else c
                part(m, 0f, 0.9f, 0f, 0.7f, 0.65f, 1f, wool)
                part(m, 0f, 1.05f, -0.66f, 0.38f, 0.38f, 0.38f, floatArrayOf(0.75f, 0.65f, 0.55f))
                for (lx in floatArrayOf(-0.2f, 0.2f)) for (lz in floatArrayOf(-0.32f, 0.32f))
                    part(m, lx, 0.27f, lz, 0.16f, 0.54f, 0.16f, floatArrayOf(0.7f, 0.62f, 0.52f))
            }
        }
    }

    private fun link(vsSrc: String, fsSrc: String): Int {
        fun compile(type: Int, src: String): Int {
            val s = GLES20.glCreateShader(type)
            GLES20.glShaderSource(s, src)
            GLES20.glCompileShader(s)
            return s
        }
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, compile(GLES20.GL_VERTEX_SHADER, vsSrc))
        GLES20.glAttachShader(p, compile(GLES20.GL_FRAGMENT_SHADER, fsSrc))
        GLES20.glLinkProgram(p)
        return p
    }
}
