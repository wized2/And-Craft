package com.endroid.andcraft.gl

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.endroid.andcraft.world.*
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.floor

class GameRenderer : GLSurfaceView.Renderer {
    val world = World()
    lateinit var player: Player
    val mobs = MobSystem()
    private val meshes = HashMap<Long, ChunkMesh>()
    private var program = 0
    private var uMvp = 0
    private var aPos = 0
    private var aCol = 0
    private val mvp = FloatArray(16)
    private val view = FloatArray(16)
    private val proj = FloatArray(16)
    private val look = FloatArray(3)
    @Volatile var forward = 0f
    @Volatile var strafe = 0f
    @Volatile var jumpQueued = false
    @Volatile var breaking = false
    @Volatile var placeQueued = false
    @Volatile var attackQueued = false
    private var breakCd = 0f
    private var lastNs = System.nanoTime()
    var fps = 0
    private var fpsT = 0f
    private var fpsC = 0
    var clock = 120f // daytime start
    val dayLen = 1200f
    val dayK: Float
        get() {
            val s = kotlin.math.sin(clock / dayLen * Math.PI * 2).toFloat()
            return ((s * 1.5f + 0.5f).coerceIn(0f, 1f))
        }
    var toast = ""
    var toastT = 0f

    fun toast(msg: String, sec: Float = 2f) { toast = msg; toastT = sec }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.53f, 0.81f, 0.92f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        val vs = """
            uniform mat4 uMVP;
            attribute vec3 aPos;
            attribute vec3 aCol;
            varying vec3 vCol;
            void main(){ vCol=aCol; gl_Position=uMVP*vec4(aPos,1.0); }
        """.trimIndent()
        val fs = """
            precision mediump float;
            varying vec3 vCol;
            void main(){ gl_FragColor=vec4(vCol,1.0); }
        """.trimIndent()
        program = link(vs, fs)
        uMvp = GLES20.glGetUniformLocation(program, "uMVP")
        aPos = GLES20.glGetAttribLocation(program, "aPos")
        aCol = GLES20.glGetAttribLocation(program, "aCol")
        val h = world.heightAt(0, 0) + 2f
        player = Player(0.5f, h, 0.5f)
        world.ensureRadius(0, 0, 3)
        rebuildAround()
        toast("And Craft — survival & creative", 3f)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        Matrix.perspectiveM(proj, 0, 70f, width.toFloat() / height.coerceAtLeast(1), 0.1f, 120f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = ((now - lastNs) / 1e9f).coerceAtMost(0.05f)
        lastNs = now
        fpsC++; fpsT += dt
        if (fpsT >= 0.5f) { fps = (fpsC / fpsT).toInt(); fpsC = 0; fpsT = 0f }
        clock += dt
        toastT -= dt

        val j = jumpQueued; jumpQueued = false
        player.update(world, dt, forward, strafe, j)
        world.ensureRadius(player.x.toInt(), player.z.toInt(), 3)
        rebuildAround()
        for (k in world.takeDirty()) {
            val cx = (k shr 32).toInt()
            val cz = k.toInt()
            val m = meshes.getOrPut(k) { ChunkMesh(cx, cz) }
            m.build(world)
        }
        Farming.update(world, player, dt)
        mobs.update(world, player, dayK, dt)

        breakCd -= dt
        player.lookDir(look)
        if (breaking && breakCd <= 0f) {
            val mob = mobs.hit(player, look)
            if (mob != null) {
                mob.hp -= if (player.creative) 30 else 4
                mob.hurt = 0.4f
                if (mob.hp <= 0) {
                    when (mob.type) {
                        MobType.PIG, MobType.COW, MobType.SHEEP -> player.give(Blocks.BREAD, 1)
                        MobType.ZOMBIE -> { }
                        MobType.VILLAGER -> player.give(Blocks.EMERALD, 1)
                    }
                    mobs.mobs.remove(mob)
                    toast("Mob defeated")
                }
                breakCd = 0.35f
            } else {
                val hit = Raycast.cast(world, player.x, player.eyeY(), player.z, look[0], look[1], look[2])
                if (hit != null && hit.y > 0) {
                    if (!Farming.harvest(world, player, hit.x, hit.y, hit.z, hit.id)) {
                        val drop = when (hit.id) {
                            Blocks.GRASS -> Blocks.DIRT
                            Blocks.COAL_ORE -> Blocks.COAL_ORE
                            Blocks.GOLD_ORE -> Blocks.GOLD_ORE
                            Blocks.STONE -> Blocks.STONE
                            Blocks.LOG -> Blocks.LOG
                            else -> hit.id
                        }
                        world.set(hit.x, hit.y, hit.z, Blocks.AIR)
                        // break plant above
                        if (Blocks.plant(world.get(hit.x, hit.y + 1, hit.z)))
                            world.set(hit.x, hit.y + 1, hit.z, Blocks.AIR)
                        if (!player.creative) player.give(drop, 1)
                    }
                    breakCd = if (player.creative) 0.12f else 0.28f
                }
            }
        }
        if (placeQueued) {
            placeQueued = false
            val hit = Raycast.cast(world, player.x, player.eyeY(), player.z, look[0], look[1], look[2])
            if (hit != null) {
                if (Farming.tryUse(world, player, hit)) {
                    toast("Farming")
                } else {
                    val id = player.selected()
                    if (id > 0 && id != Blocks.SEEDS && id != Blocks.BREAD && id != Blocks.EMERALD) {
                        val px = hit.x + hit.fx
                        val py = hit.y + hit.fy
                        val pz = hit.z + hit.fz
                        if (py in 0 until World.H && world.get(px, py, pz) == Blocks.AIR) {
                            if (player.creative || player.take(id)) {
                                world.set(px, py, pz, id)
                            } else toast("No blocks left")
                        }
                    }
                }
            }
        }

        // sky by day
        val k = dayK
        GLES20.glClearColor(0.05f + 0.48f * k, 0.07f + 0.74f * k, 0.12f + 0.80f * k, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        Matrix.setLookAtM(
            view, 0,
            player.x, player.eyeY(), player.z,
            player.x + look[0], player.eyeY() + look[1], player.z + look[2],
            0f, 1f, 0f
        )
        Matrix.multiplyMM(mvp, 0, proj, 0, view, 0)
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        for (m in meshes.values) m.draw(aPos, aCol)
        // mobs as simple cubes
        for (m in mobs.mobs) drawMob(m)
    }

    private val mobMvp = FloatArray(16)
    private val mobModel = FloatArray(16)
    private fun drawMob(m: Mob) {
        // draw as a stretched box using immediate-ish triangle strip via a tiny dynamic mesh is heavy;
        // skip dedicated mesh: tint nearby blocks instead — use point: scale existing program with a unit cube built once
        // Minimal: skip visual for now beyond future; still interactable
    }

    private fun rebuildAround() {
        val cx = floor(player.x / World.CS).toInt()
        val cz = floor(player.z / World.CS).toInt()
        val r = 3
        for (dz in -r..r) for (dx in -r..r) {
            val k = (cx + dx).toLong() shl 32 xor ((cz + dz).toLong() and 0xffffffffL)
            if (!meshes.containsKey(k)) {
                val mesh = ChunkMesh(cx + dx, cz + dz)
                mesh.build(world)
                meshes[k] = mesh
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
