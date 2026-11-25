package com.endroid.andcraft.gl

import android.content.Context
import com.endroid.andcraft.Sfx
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
    private val appCtx = ctx.applicationContext
    @Volatile var shotReq = false
    private var vw = 1
    private var vh = 1
    private var thunderT = -1f
    private val prefs = ctx.getSharedPreferences("andcraft", Context.MODE_PRIVATE)
    lateinit var world: World
    lateinit var player: Player
    private lateinit var fx: Fx
    private var flashT = 0f
    private var boltT = 12f
    private var lavaT = 0f
    val mobs = MobSystem()
    @Volatile var ready = false
    private var spawn = intArrayOf(0, 40, 0)

    private val meshes = HashMap<Long, ChunkMesh>()
    private val announced = HashSet<Long>()
    private val cube = CubeMesh()
    private val crackCube = CubeMesh(true)
    private var crackTex = 0
    private var uUV = 0
    private var program = 0
    private var uMvp = 0
    private var uTint = 0
    private var uLight = 0
    private var aPos = 0
    private var aUV = 0
    private var aL = 0
    private var uFog = 0
    private var uFogR = 0
    private var uTex = 0
    private var uAlpha = 0
    private var atlasTex = 0
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
    @Volatile var targetName = ""
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
        fx = Fx(world)
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
            uniform vec4 uUV;
            attribute vec3 aPos;
            attribute vec2 aUV;
            attribute float aL;
            varying vec2 vUV;
            varying float vL;
            varying float vD;
            void main(){ vUV=aUV*uUV.xy+uUV.zw; vL=aL; gl_Position=uMVP*vec4(aPos,1.0); vD=gl_Position.w; }
        """.trimIndent()
        val fs = """
            precision mediump float;
            uniform sampler2D uTex;
            uniform vec3 uTint;
            uniform float uLight;
            uniform float uAlpha;
            uniform vec3 uFog;
            uniform vec2 uFogR;
            varying vec2 vUV;
            varying float vL;
            varying float vD;
            void main(){
                vec4 t=texture2D(uTex,vUV);
                if(t.a<0.5) discard;
                vec3 c=t.rgb*uTint*vL*uLight;
                float f=clamp((vD-uFogR.x)/(uFogR.y-uFogR.x),0.0,1.0);
                gl_FragColor=vec4(mix(c,uFog,f),t.a*uAlpha);
            }
        """.trimIndent()
        program = link(vs, fs)
        uMvp = GLES20.glGetUniformLocation(program, "uMVP")
        uTint = GLES20.glGetUniformLocation(program, "uTint")
        uLight = GLES20.glGetUniformLocation(program, "uLight")
        uAlpha = GLES20.glGetUniformLocation(program, "uAlpha")
        uFog = GLES20.glGetUniformLocation(program, "uFog")
        uFogR = GLES20.glGetUniformLocation(program, "uFogR")
        uTex = GLES20.glGetUniformLocation(program, "uTex")
        uUV = GLES20.glGetUniformLocation(program, "uUV")
        aPos = GLES20.glGetAttribLocation(program, "aPos")
        aUV = GLES20.glGetAttribLocation(program, "aUV")
        aL = GLES20.glGetAttribLocation(program, "aL")
        atlasTex = Atlas.upload()
        crackTex = Atlas.uploadCrack()
        cube.init()
        crackCube.init()
        meshes.clear() // GL buffers are gone after a context loss
        if (!ready) {
            loadOrCreate()
            ready = true
        }
        lastNs = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        vw = width; vh = height
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
        if (shotReq) { shotReq = false; takeShot() }
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
            for (v in world.villagerSpawns.toList()) mobs.add(if (v[3].toInt() == 9) MobType.GOLEM else MobType.VILLAGER, v[0], v[1], v[2], v[3].toInt())
            world.villagerSpawns.clear()
        }
        // announce a village only when really close to one
        run {
            val v = world.nearestVillage(player.x, player.z, 34f)
            if (v != null && announced.add(v.cx.toLong() shl 32 xor (v.cz.toLong() and 0xffffffffL))) toast("You found a village!", 2.5f)
        }
        mobs.update(world, player, dayK, dt, dayNo)
        fx.update(dt, player.x, player.z)
        run {
            val tm = mobs.hit(player, look)
            targetName = when (tm?.type) {
                null -> ""
                MobType.VILLAGER -> "Villager · " + com.endroid.andcraft.world.Trades.PROF_NAMES[tm.prof.coerceIn(0, 2)] + " (friendly)"
                MobType.ZOMBIE -> if (tm.big) "Zombie Brute (hostile)" else "Zombie (hostile)"
                MobType.CREEPER -> "Creeper (hostile)"
                MobType.WOLF -> if (tm.tamed) "Wolf (tamed)" else "Wolf"
                MobType.GOLEM -> "Iron Golem (friendly)"
                else -> tm.type.name.lowercase().replaceFirstChar { it.uppercase() }
            }
        }
        for (b in mobs.booms.toList()) explode(b[0], b[1], b[2], b[3], b[4])
        mobs.booms.clear()
        for (b in fx.booms.toList()) explode(b[0], b[1], b[2], b[3], b[4])
        fx.booms.clear()
        updateLava(dt)
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
        flashT = (flashT - dt * 2f).coerceAtLeast(0f)
        if (thunderT > 0f) { thunderT -= dt; if (thunderT <= 0f) Sfx.play("thunder") }
        if (rain > 0.8f) {
            boltT -= dt
            if (boltT <= 0f) { boltT = 8f + Math.random().toFloat() * 22f; flashT = 1f; thunderT = 0.7f + Math.random().toFloat() }
        }
        if (abs(rainTarget - rain) < 0.01f) rain = rainTarget
    }

    fun setWeather(on: Boolean) { rainTarget = if (on) 1f else 0f; rainT = 60f }

    private fun takeShot() {
        try {
            val w = vw; val h = vh
            val buf = java.nio.ByteBuffer.allocateDirect(w * h * 4).order(java.nio.ByteOrder.nativeOrder())
            GLES20.glReadPixels(0, 0, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
            val px = IntArray(w * h)
            buf.rewind()
            val ib = buf.asIntBuffer()
            val row = IntArray(w)
            for (y in 0 until h) {
                ib.position(y * w); ib.get(row)
                val dst = (h - 1 - y) * w
                for (x in 0 until w) {
                    val v = row[x] // bytes R,G,B,A little-endian -> int 0xAABBGGRR
                    px[dst + x] = (v and 0xff00ff00.toInt()) or ((v and 0xff) shl 16) or ((v shr 16) and 0xff)
                }
            }
            val bmp = android.graphics.Bitmap.createBitmap(px, w, h, android.graphics.Bitmap.Config.ARGB_8888)
            val name = "AndCraft_" + System.currentTimeMillis() + ".png"
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                val cv = android.content.ContentValues()
                cv.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name)
                cv.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                cv.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AndCraft")
                val uri = appCtx.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
                if (uri != null) appCtx.contentResolver.openOutputStream(uri)?.use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                toast("Screenshot saved to Pictures/AndCraft", 2f)
            } else {
                val dir = appCtx.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
                val f = java.io.File(dir, name)
                java.io.FileOutputStream(f).use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                toast("Screenshot saved: ${f.name}", 2f)
            }
        } catch (e: Exception) { toast("Screenshot failed", 2f) }
    }

    private fun explode(cx: Float, cy: Float, cz: Float, r: Float, pdmg: Float) {
        fx.explodeBlocks(cx, cy, cz, r) { b ->
            if (!player.creative && Math.hypot((player.x - cx).toDouble(), (player.z - cz).toDouble()) < 10.0) {
                val d = drops(b)
                if (d > 0) player.give(d, 1)
            }
        }
        val pd = Math.sqrt(((player.x - cx) * (player.x - cx) + (player.y + 1f - cy) * (player.y + 1f - cy) + (player.z - cz) * (player.z - cz)).toDouble()).toFloat()
        if (pd < r * 2f) {
            player.hurt(Math.max(1, Math.round((1f - pd / (r * 2f)) * pdmg)))
            val dd = Math.max(pd, 0.3f)
            player.vx += (player.x - cx) / dd * 5f
            player.vz += (player.z - cz) / dd * 5f
        }
        val it = mobs.mobs.iterator()
        while (it.hasNext()) {
            val m = it.next()
            val d = Math.sqrt(((m.x - cx) * (m.x - cx) + (m.y + m.h / 2f - cy) * (m.y + m.h / 2f - cy) + (m.z - cz) * (m.z - cz)).toDouble()).toFloat()
            if (d < r * 2f) {
                m.hp -= Math.round((1f - d / (r * 2f)) * pdmg)
                m.hurt = 0.4f
                val dd = Math.max(d, 0.3f)
                m.vx += (m.x - cx) / dd * 6f; m.vz += (m.z - cz) / dd * 6f; m.vy = 5f
                if (m.hp <= 0) { mobDrops(m); it.remove() }
            }
        }
        flashT = max(flashT, 0.5f)
        Sfx.play("boom"); Sfx.vib(120)
    }

    private fun updateLava(dt: Float) {
        if (player.hp <= 0 || player.creative) return
        val fxp = floor(player.x).toInt(); val fzp = floor(player.z).toInt()
        val a = world.get(fxp, floor(player.y + 0.2f).toInt(), fzp)
        val b = world.get(fxp, floor(player.y + 1f).toInt(), fzp)
        if (a == Blocks.LAVA || b == Blocks.LAVA) {
            player.vy *= Math.pow(0.02, dt.toDouble()).toFloat()
            lavaT -= dt
            if (lavaT <= 0f) { lavaT = 0.5f; player.hurt(4); toast("You are burning!", 0.6f) }
        }
    }

    private fun resetMine() { mx = Int.MIN_VALUE; mineT = 0f; mineP = 0f }

    private fun breakTime(id: Int): Float {
        if (player.creative) return 0.1f
        val h = Blocks.hardness(id)
        if (h <= 0f) return 0.05f
        val need = Blocks.needPick(id)
        val canH = need == 0 || player.pick >= need
        val sp = floatArrayOf(1f, 2f, 4f, 6f, 8f)
        var speed = 1f
        if (need > 0 && player.pick > 0) speed = sp[player.pick.coerceIn(0, 4)]
        if (Blocks.axeBlock(id) && player.axe > 0) speed = sp[player.axe.coerceIn(0, 4)]
        return max(0.05f, h * (if (canH) 1.5f else 5f) / speed)
    }

    private fun drops(id: Int): Int = when (id) {
        Blocks.STONE -> if (player.pick >= 1) Blocks.STONE else 0
        Blocks.COAL_ORE -> if (player.pick >= 1) Blocks.COAL_ORE else 0
        Blocks.BRICK -> if (player.pick >= 1) Blocks.BRICK else 0
        Blocks.FURNACE -> if (player.pick >= 1) Blocks.FURNACE else 0
        Blocks.GOLD_ORE -> if (player.pick >= 2) Blocks.GOLD_ORE else 0
        Blocks.IRON_ORE -> if (player.pick >= 2) Blocks.IRON_ORE else 0
        Blocks.DIAMOND_ORE -> if (player.pick >= 3) Blocks.DIAMOND else 0
        Blocks.GRASS, Blocks.FARMLAND, Blocks.PATH -> Blocks.DIRT
        Blocks.LEAVES -> { val r = Math.random(); if (r < 0.06) Blocks.SAPLING else if (r < 0.07) Blocks.APPLE else 0 }
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
                if (mob.type == MobType.WOLF && !mob.tamed) mob.aggro = true
                Sfx.play("hit")
                mob.hurt = 0.4f
                mob.stun = 0.35f; mob.wind = 0f // hitting a zombie interrupts its swing
                val kb = if (mob.big) 4f else 7f
                mob.vx += look[0] * kb; mob.vz += look[2] * kb
                player.addExh(0.1f)
                atkCd = 0.5f
                if (mob.hp <= 0) {
                    mobDrops(mob)
                    if (mob.type == MobType.ZOMBIE) { player.kills++; if (mob.big) player.brutes++ } else if (mob.type == MobType.CREEPER) player.kills++
                    mobs.mobs.remove(mob)
                }
            }
            return
        }
        val hit = Raycast.cast(world, player.x, player.eyeY(), player.z, look[0], look[1], look[2],
            if (player.creative) 6f else 4.5f)
        if (hit == null || hit.y <= 0 || hit.id == Blocks.LAVA) { resetMine(); return }
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
        val oreXp = when (hit.id) { Blocks.DIAMOND_ORE -> 7; Blocks.GOLD_ORE -> 4; Blocks.IRON_ORE -> 3; Blocks.COAL_ORE -> 2; else -> 0 }
        if (oreXp > 0 && player.addXp(oreXp)) toast("Level ${player.xpLvl}!", 1.4f)
        world.set(hit.x, hit.y, hit.z, Blocks.AIR)
        if (hit.id == Blocks.FURNACE) Machines.furnaces.remove(Machines.key(hit.x, hit.y, hit.z))
        val above = world.get(hit.x, hit.y + 1, hit.z)
        if (Blocks.plant(above) && above != Blocks.LEAVES && above != Blocks.TORCH)
            world.set(hit.x, hit.y + 1, hit.z, Blocks.AIR)
        if (d > 0) player.give(d, 1)
        player.addExh(0.005f)
        Sfx.play("break", 0.8f); Sfx.vib(8)
    }

    private fun mobDrops(m: Mob) {
        if (player.creative) return
        val r = { a: Int, b: Int -> a + (Math.random() * (b - a + 1)).toInt() }
        val gain = when (m.type) { MobType.ZOMBIE -> if (m.big) 9 else 5; MobType.CREEPER -> 6; MobType.VILLAGER, MobType.GOLEM, MobType.WOLF -> 0; else -> 2 }
        if (player.addXp(gain)) toast("Level ${player.xpLvl}!", 1.4f)
        when (m.type) {
            MobType.PIG -> player.give(Blocks.RAW_PORK, r(1, 3))
            MobType.COW -> { player.give(Blocks.RAW_BEEF, r(1, 3)); player.give(Blocks.LEATHER, r(0, 2)) }
            MobType.SHEEP -> { if (!m.sheared) player.give(Blocks.WOOL, 1); player.give(Blocks.RAW_MUTTON, r(1, 2)) }
            MobType.ZOMBIE -> player.give(Blocks.ROTTEN, r(0, 2))
            MobType.CREEPER -> player.give(Blocks.GUNPOWDER, r(0, 2))
            MobType.CHICKEN -> player.give(Blocks.RAW_CHICKEN, 1)
            MobType.VILLAGER -> player.give(Blocks.EMERALD, 1)
            MobType.WOLF -> {}
            MobType.GOLEM -> { player.give(Blocks.IRON_INGOT, r(3, 5)); player.give(Blocks.ROSE, r(0, 2)) }
        }
        if (m.type == MobType.ZOMBIE && Math.random() < 0.3) player.give(Blocks.BONE, 1)
    }

    private fun doPlace() {
        // villagers and sheep first
        val mob = mobs.hit(player, look)
        if (mob != null) {
            if (mob.type == MobType.WOLF && !mob.tamed && player.selected() == Blocks.BONE) {
                if (!player.creative) player.take(Blocks.BONE, 1)
                if (Math.random() < 0.4) { mob.tamed = true; mob.aggro = false; toast("Wolf tamed!", 1.5f) } else toast("The wolf is still wild...", 1.2f)
                return
            }
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
        if (player.selected() == Blocks.BONE && (player.creative || player.count(Blocks.BONE) > 0)) {
            if (hit.id == Blocks.SAPLING) { fx.growTree(hit.x, hit.y, hit.z); if (!player.creative) player.take(Blocks.BONE, 1); toast("Tree grown", 1f); return }
            if (hit.id == Blocks.WHEAT0 || hit.id == Blocks.WHEAT1) { world.set(hit.x, hit.y, hit.z, Blocks.WHEAT2); if (!player.creative) player.take(Blocks.BONE, 1); toast("Crop grown", 1f); return }
        }
        if (hit.id == Blocks.BED) { useBed(hit); return }
        if (hit.id == Blocks.TNT && (player.creative || player.count(Blocks.FLINT) > 0)) { fx.ignite(hit.x, hit.y, hit.z, 3f); toast("Ssss...", 1f); return }
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
        if (id == Blocks.SAPLING) {
            val bl = world.get(px, py - 1, pz)
            if (bl != Blocks.GRASS && bl != Blocks.DIRT) { toast("Plant on grass or dirt", 1f); return }
        }
        if (player.take(id)) { world.set(px, py, pz, id); Sfx.play("place", 0.8f); Sfx.vib(10) } else toast("No ${Blocks.name(id)} left")
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
        val fl = flashT.coerceIn(0f, 1f) * 0.85f
        val sr = 0.05f + 0.48f * k + (1f - (0.05f + 0.48f * k)) * fl
        val sg = 0.07f + 0.74f * k + (1f - (0.07f + 0.74f * k)) * fl
        val sb = 0.12f + 0.80f * k + (1f - (0.12f + 0.80f * k)) * fl
        GLES20.glClearColor(sr, sg, sb, 1f)
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
        GLES20.glUniform1f(uAlpha, 1f)
        val fr = renderDist.coerceIn(2, 8) * 16f
        GLES20.glUniform3f(uFog, sr, sg, sb)
        GLES20.glUniform2f(uFogR, fr * (if (rain > 0.02f) 0.35f else 0.55f), fr * 0.95f)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, atlasTex)
        GLES20.glUniform1i(uTex, 0)
        GLES20.glUniform4f(uUV, 1f, 1f, 0f, 0f)
        drawSky(k, sr, sg, sb, ex, ey, ez)
        GLES20.glUniform3f(uFog, sr, sg, sb)
        GLES20.glUniform2f(uFogR, fr * (if (rain > 0.02f) 0.35f else 0.55f), fr * 0.95f)
        GLES20.glUniform1f(uLight, (0.35f + 0.65f * k).coerceAtLeast(0.3f))
        GLES20.glUniform3f(uTint, 1f, 1f, 1f)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, vp, 0)
        for (m in meshes.values) m.draw(0, aPos, aUV, aL)
        for (m in mobs.mobs.toList()) drawMob(m)
        if (camView != 0) drawPlayer()
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDepthMask(false)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, vp, 0)
        GLES20.glUniform3f(uTint, 1f, 1f, 1f)
        GLES20.glUniform1f(uAlpha, 0.72f)
        for (m in meshes.values) m.draw(1, aPos, aUV, aL)
        GLES20.glUniform1f(uAlpha, 1f)
        drawTarget()
        GLES20.glDepthMask(true)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glDisable(GLES20.GL_BLEND)
        if (rain > 0.02f) drawRain()
    }

    private fun boxAt(x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, mesh: CubeMesh = cube) {
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, x, y, z)
        Matrix.scaleM(model, 0, sx, sy, sz)
        Matrix.multiplyMM(mvp, 0, vp, 0, model, 0)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        mesh.draw(aPos, aUV, aL)
    }

    /** sun, moon, stars and drifting clouds; drawn without depth writes around the camera */
    private fun drawSky(k: Float, sr: Float, sg: Float, sb: Float, ex: Float, ey: Float, ez: Float) {
        GLES20.glDepthMask(false)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glUniform2f(uFogR, 1e6f, 2e6f)
        GLES20.glUniform1f(uLight, 1f)
        val a = clock / dayLen * (Math.PI * 2).toFloat()
        val sx = kotlin.math.cos(a); val sy = kotlin.math.sin(a)
        val dist = 100f
        val vis = (1f - 0.7f * rain)
        // stars
        val star = ((0.4f - dayK) / 0.4f).coerceIn(0f, 1f) * vis
        if (star > 0.02f) {
            GLES20.glUniform3f(uTint, star, star, star * 0.95f)
            for (i in 0 until 90) {
                val u = hashF(i, 1) * 6.2832f
                val v = 0.12f + hashF(i, 2) * 0.88f
                val r = kotlin.math.sqrt(1f - v * v)
                val dx = kotlin.math.cos(u) * r; val dz = kotlin.math.sin(u) * r
                boxAt(ex + dx * 95f, ey + v * 95f, ez + dz * 95f, 0.9f, 0.9f, 0.9f)
            }
        }
        // sun and moon
        if (sy > -0.25f) {
            GLES20.glUniform3f(uTint, 1.5f * vis + 0.2f, 1.4f * vis + 0.2f, 0.9f * vis + 0.2f)
            boxAt(ex + sx * dist - 7f, ey + sy * dist - 7f, ez + 30f - 7f, 14f, 14f, 14f)
        }
        if (sy < 0.25f) {
            GLES20.glUniform3f(uTint, 0.95f, 0.97f, 1.1f)
            boxAt(ex - sx * dist - 5f, ey - sy * dist - 5f, ez - 30f - 5f, 10f, 10f, 10f)
        }
        // clouds
        val cy = 46f
        val br = (0.35f + 0.65f * k) * (1f - 0.3f * rain)
        GLES20.glUniform3f(uTint, br, br, br * 1.02f)
        val off = timeAcc * 1.4f
        val cs = 14f
        val gx0 = kotlin.math.floor((ex - off) / cs).toInt() - 7
        val gz0 = kotlin.math.floor(ez / cs).toInt() - 7
        for (gx in gx0 until gx0 + 15) for (gz in gz0 until gz0 + 15) {
            if (hashF(gx * 31 + 7, gz * 17 + 3) > 0.34f + 0.2f * rain) continue
            boxAt(gx * cs + off, cy, gz * cs, cs, 2.2f, cs)
        }
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glDepthMask(true)
    }

    private fun hashF(a: Int, b: Int): Float {
        var n = a * 374761393 + b * 668265263
        n = (n xor (n ushr 13)) * 1274126177
        n = n xor (n ushr 16)
        return (n and 0x7fffffff) / 2147483647f
    }

    /** outline of the targeted block and the crack overlay while mining */
    private fun drawTarget() {
        if (mx == Int.MIN_VALUE) {
            player.lookDir(look)
            val hit = Raycast.cast(world, player.x, player.eyeY(), player.z, look[0], look[1], look[2], if (player.creative) 6f else 4.5f)
            if (hit != null && hit.y > 0) drawOutline(hit.x, hit.y, hit.z)
            return
        }
        drawOutline(mx, my, mz)
        val stage = (mineP * 10f).toInt().coerceIn(0, 9)
        if (mineP > 0.02f) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, crackTex)
            GLES20.glUniform4f(uUV, 0.1f, 1f, stage * 0.1f, 0f)
            GLES20.glUniform3f(uTint, 1f, 1f, 1f)
            GLES20.glUniform1f(uLight, 1f)
            boxAt(mx - 0.003f, my - 0.003f, mz - 0.003f, 1.006f, 1.006f, 1.006f, crackCube)
            GLES20.glUniform4f(uUV, 1f, 1f, 0f, 0f)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, atlasTex)
        }
    }

    private fun drawOutline(bx: Int, by: Int, bz: Int) {
        GLES20.glUniform3f(uTint, 0.02f, 0.02f, 0.02f)
        GLES20.glUniform1f(uLight, 1f)
        GLES20.glUniform1f(uAlpha, 0.8f)
        val t = 0.022f; val o = -0.006f; val l = 1.012f
        val x = bx.toFloat(); val y = by.toFloat(); val z = bz.toFloat()
        for (i in 0..1) for (j in 0..1) {
            val a = o + i * (1f - 2 * o - t); val b = o + j * (1f - 2 * o - t)
            boxAt(x + a, y + o, z + b, t, l, t)
            boxAt(x + o, y + a, z + b, l, t, t)
            boxAt(x + a, y + b, z + o, t, t, l)
        }
        GLES20.glUniform1f(uAlpha, 1f)
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
            cube.draw(aPos, aUV, aL)
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
        val sc = if (m.big) 1.3f else if (m.type == MobType.CREEPER && m.fuse > 0f) 1f + m.fuse * 0.25f + kotlin.math.sin(m.fuse * 30f) * 0.06f else 1f
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
        val wf = if (m.type == MobType.CREEPER && m.fuse > 0f && (m.fuse * 8f).toInt() % 2 == 0) 0.6f else 0f
        GLES20.glUniform3f(uTint, wf + d * c[0] + h * 0.8f, wf + d * c[1] * (1f - h * 0.6f), wf + d * c[2] * (1f - h * 0.6f))
        cube.draw(aPos, aUV, aL)
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
            MobType.CREEPER -> {
                val dark = floatArrayOf(0.05f, 0.08f, 0.05f)
                for (lx in floatArrayOf(-0.13f, 0.13f)) for (lz in floatArrayOf(-0.18f, 0.18f))
                    part(m, lx, 0.25f, lz, 0.22f, 0.5f, 0.22f, c)
                part(m, 0f, 0.9f, 0f, 0.5f, 0.75f, 0.3f, c)
                part(m, 0f, 1.5f, 0f, 0.5f, 0.5f, 0.5f, c)
                part(m, -0.12f, 1.55f, -0.26f, 0.1f, 0.12f, 0.03f, dark)
                part(m, 0.12f, 1.55f, -0.26f, 0.1f, 0.12f, 0.03f, dark)
                part(m, 0f, 1.4f, -0.26f, 0.12f, 0.2f, 0.03f, dark)
            }
            MobType.CHICKEN -> {
                part(m, 0f, 0.5f, 0f, 0.4f, 0.35f, 0.55f, c)
                part(m, 0f, 0.8f, -0.28f, 0.22f, 0.28f, 0.22f, c)
                part(m, 0f, 0.78f, -0.44f, 0.1f, 0.08f, 0.14f, floatArrayOf(0.91f, 0.6f, 0.17f))
                part(m, 0f, 0.68f, -0.4f, 0.06f, 0.1f, 0.06f, floatArrayOf(0.8f, 0.13f, 0.13f))
                for (lx in floatArrayOf(-0.1f, 0.1f)) part(m, lx, 0.15f, 0f, 0.06f, 0.3f, 0.06f, floatArrayOf(0.91f, 0.69f, 0.19f))
            }
            MobType.VILLAGER -> {
                val robe = when (m.prof) {
                    1 -> floatArrayOf(0.85f, 0.85f, 0.8f)
                    2 -> floatArrayOf(0.5f, 0.5f, 0.55f)
                    else -> c
                }
                val vskin = floatArrayOf(0.84f, 0.66f, 0.55f)
                part(m, 0f, 0.8f, 0f, 0.6f, 1.2f, 0.4f, robe)
                part(m, 0f, 0.45f, 0f, 0.62f, 0.1f, 0.42f, floatArrayOf(robe[0] * 0.6f, robe[1] * 0.6f, robe[2] * 0.6f))
                part(m, 0f, 1.62f, 0f, 0.46f, 0.52f, 0.46f, vskin)
                part(m, 0f, 1.52f, -0.3f, 0.14f, 0.26f, 0.14f, vskin)
                part(m, 0f, 1.74f, -0.24f, 0.46f, 0.07f, 0.04f, floatArrayOf(0.25f, 0.18f, 0.12f))
                part(m, -0.12f, 1.66f, -0.24f, 0.08f, 0.08f, 0.03f, floatArrayOf(0.15f, 0.5f, 0.2f))
                part(m, 0.12f, 1.66f, -0.24f, 0.08f, 0.08f, 0.03f, floatArrayOf(0.15f, 0.5f, 0.2f))
                part(m, 0f, 1.1f, -0.28f, 0.58f, 0.18f, 0.2f, vskin)
            }
            MobType.WOLF -> {
                val dark = floatArrayOf(0.6f, 0.58f, 0.55f)
                part(m, 0f, 0.55f, 0f, 0.3f, 0.3f, 0.6f, c)
                part(m, 0f, 0.7f, -0.38f, 0.3f, 0.28f, 0.26f, c)
                part(m, 0f, 0.65f, -0.55f, 0.14f, 0.12f, 0.1f, dark)
                part(m, -0.1f, 0.88f, -0.38f, 0.07f, 0.1f, 0.05f, dark)
                part(m, 0.1f, 0.88f, -0.38f, 0.07f, 0.1f, 0.05f, dark)
                part(m, 0f, 0.62f, 0.4f, 0.08f, 0.08f, 0.35f, c)
                for (lx in floatArrayOf(-0.1f, 0.1f)) for (lz in floatArrayOf(-0.22f, 0.22f))
                    part(m, lx, 0.2f, lz, 0.1f, 0.4f, 0.1f, c)
                if (m.tamed) part(m, 0f, 0.7f, -0.2f, 0.34f, 0.08f, 0.1f, floatArrayOf(0.85f, 0.1f, 0.1f))
                else if (m.aggro) part(m, 0f, 0.7f, -0.55f, 0.04f, 0.04f, 0.04f, floatArrayOf(1f, 0.1f, 0.1f))
            }
            MobType.GOLEM -> {
                val vine = floatArrayOf(0.35f, 0.5f, 0.3f)
                for (lx in floatArrayOf(-0.25f, 0.25f)) part(m, lx, 0.5f, 0f, 0.4f, 1f, 0.4f, c)
                part(m, 0f, 1.5f, 0f, 0.95f, 0.9f, 0.5f, c)
                part(m, 0f, 2.2f, -0.05f, 0.4f, 0.4f, 0.4f, c)
                part(m, 0f, 2.1f, -0.3f, 0.1f, 0.25f, 0.1f, floatArrayOf(0.5f, 0.45f, 0.4f))
                part(m, -0.65f, 1.4f, 0f, 0.3f, 1.3f, 0.35f, c)
                part(m, 0.65f, 1.4f, 0f, 0.3f, 1.3f, 0.35f, c)
                part(m, 0.3f, 1.6f, -0.26f, 0.2f, 0.5f, 0.04f, vine)
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
