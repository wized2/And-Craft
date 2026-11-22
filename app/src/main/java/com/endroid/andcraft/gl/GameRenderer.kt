package com.endroid.andcraft.gl

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.endroid.andcraft.world.Blocks
import com.endroid.andcraft.world.Player
import com.endroid.andcraft.world.Raycast
import com.endroid.andcraft.world.World
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.floor

class GameRenderer : GLSurfaceView.Renderer {
    val world = World()
    lateinit var player: Player
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
    private var breakCd = 0f
    private var lastNs = System.nanoTime()
    var fps = 0
    private var fpsT = 0f
    private var fpsC = 0

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.53f, 0.81f, 0.92f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        val vs = """
            uniform mat4 uMVP;
            attribute vec3 aPos;
            attribute vec3 aCol;
            varying vec3 vCol;
            void main(){
              vCol=aCol;
              gl_Position=uMVP*vec4(aPos,1.0);
            }
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
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / height.coerceAtLeast(1)
        Matrix.perspectiveM(proj, 0, 70f, aspect, 0.1f, 120f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = ((now - lastNs) / 1e9f).coerceAtMost(0.05f)
        lastNs = now
        fpsC++; fpsT += dt
        if (fpsT >= 0.5f) { fps = (fpsC / fpsT).toInt(); fpsC = 0; fpsT = 0f }

        val j = jumpQueued; jumpQueued = false
        player.update(world, dt, forward, strafe, j)
        world.ensureRadius(player.x.toInt(), player.z.toInt(), 3)
        rebuildAround()
        for (k in world.takeDirty()) {
            val cx = (k shr 32).toInt()
            val cz = k.toInt()
            meshes[k]?.build(world) ?: run {
                val m = ChunkMesh(cx, cz); m.build(world); meshes[k] = m
            }
        }

        breakCd -= dt
        player.lookDir(look)
        if (breaking && breakCd <= 0f) {
            val hit = Raycast.cast(world, player.x, player.eyeY(), player.z, look[0], look[1], look[2])
            if (hit != null && hit.y > 0) {
                world.set(hit.x, hit.y, hit.z, Blocks.AIR)
                breakCd = 0.12f
            }
        }
        if (placeQueued) {
            placeQueued = false
            val hit = Raycast.cast(world, player.x, player.eyeY(), player.z, look[0], look[1], look[2])
            if (hit != null) {
                val px = hit.x + hit.fx
                val py = hit.y + hit.fy
                val pz = hit.z + hit.fz
                if (py in 0 until World.H) world.set(px, py, pz, player.selected)
            }
        }

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
    }

    private fun rebuildAround() {
        val cx = floor(player.x / World.CS).toInt()
        val cz = floor(player.z / World.CS).toInt()
        val r = 3
        for (dz in -r..r) for (dx in -r..r) {
            val k = (cx + dx).toLong() shl 32 xor ((cz + dz).toLong() and 0xffffffffL)
            if (!meshes.containsKey(k)) {
                val m = ChunkMesh(cx + dx, cz + dz)
                m.build(world)
                meshes[k] = m
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
        val vs = compile(GLES20.GL_VERTEX_SHADER, vsSrc)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fsSrc)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, vs)
        GLES20.glAttachShader(p, fs)
        GLES20.glLinkProgram(p)
        return p
    }
}
