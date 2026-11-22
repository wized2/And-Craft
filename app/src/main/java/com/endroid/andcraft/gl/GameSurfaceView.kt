package com.endroid.andcraft.gl

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.MotionEvent
import kotlin.math.hypot

class GameSurfaceView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {
    val renderer = GameRenderer()
    private var lookId = -1
    private var joyId = -1
    private var lastX = 0f
    private var lastY = 0f
    private var joyCx = 0f
    private var joyCy = 0f

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val w = width.toFloat()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val id = e.getPointerId(i)
                val x = e.getX(i); val y = e.getY(i)
                if (x < w * 0.42f && joyId < 0) {
                    joyId = id; joyCx = x; joyCy = y
                } else if (lookId < 0) {
                    lookId = id; lastX = x; lastY = y
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    val id = e.getPointerId(i)
                    val x = e.getX(i); val y = e.getY(i)
                    if (id == joyId) {
                        val dx = x - joyCx; val dy = y - joyCy
                        val m = hypot(dx, dy).coerceAtLeast(1f)
                        val nm = (m / 110f).coerceAtMost(1f)
                        // forward is -dy (up on screen), strafe is dx
                        renderer.forward = (-dy / m) * nm
                        renderer.strafe = (dx / m) * nm
                    } else if (id == lookId) {
                        val dx = x - lastX; val dy = y - lastY
                        lastX = x; lastY = y
                        renderer.player.yaw -= dx * 0.0045f
                        renderer.player.pitch = (renderer.player.pitch - dy * 0.0045f)
                            .coerceIn(-1.55f, 1.55f)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val id = e.getPointerId(e.actionIndex)
                if (id == joyId) {
                    joyId = -1
                    renderer.forward = 0f
                    renderer.strafe = 0f
                }
                if (id == lookId) lookId = -1
            }
        }
        return true
    }
}
