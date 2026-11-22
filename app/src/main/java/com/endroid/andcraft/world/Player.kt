package com.endroid.andcraft.world

import kotlin.math.cos
import kotlin.math.sin

class Player(var x: Float, var y: Float, var z: Float) {
    var yaw = 0f
    var pitch = 0f
    var vx = 0f
    var vy = 0f
    var vz = 0f
    var onGround = false
    var creative = true
    var hp = 20
    var food = 20
    var slot = 0
    val hotbar = Blocks.HOTBAR_DEFAULT.copyOf()
    val inv = IntArray(64) { 0 }

    init {
        // starter kit survival
        inv[Blocks.SEEDS] = 16
        inv[Blocks.BREAD] = 4
        inv[Blocks.PLANKS] = 16
    }

    fun selected(): Int = hotbar[slot.coerceIn(0, hotbar.lastIndex)]
    fun eyeY() = y + 1.62f

    fun lookDir(out: FloatArray) {
        val cp = cos(pitch)
        out[0] = -sin(yaw) * cp
        out[1] = sin(pitch)
        out[2] = -cos(yaw) * cp
    }

    fun has(id: Int, n: Int = 1) = creative || inv.getOrElse(id) { 0 } >= n
    fun take(id: Int, n: Int = 1): Boolean {
        if (creative) return true
        if (inv.getOrElse(id) { 0 } < n) return false
        inv[id] -= n
        return true
    }
    fun give(id: Int, n: Int = 1) {
        if (creative || id <= 0) return
        inv[id] = (inv.getOrElse(id) { 0 } + n).coerceAtMost(64)
    }

    fun update(world: World, dt: Float, forward: Float, strafe: Float, jump: Boolean) {
        val sp = if (creative) 5.2f else 4.4f
        val sy = sin(yaw)
        val cy = cos(yaw)
        val tx = (-sy * forward + cy * strafe) * sp
        val tz = (-cy * forward - sy * strafe) * sp
        val k = (dt * if (onGround) 12f else 4f).coerceAtMost(1f)
        vx += (tx - vx) * k
        vz += (tz - vz) * k
        if (!creative || true) {
            vy -= 28f * dt
            if (jump && onGround) {
                vy = 8.4f
                onGround = false
            }
        }
        moveAxis(world, vx * dt, 0f, 0f)
        moveAxis(world, 0f, 0f, vz * dt)
        moveAxis(world, 0f, vy * dt, 0f)
        // drown / heal lite
        if (!creative) {
            val inWater = world.get(x.toInt(), (y + 0.5f).toInt(), z.toInt()) == Blocks.WATER
            if (inWater && food > 0 && Math.random() < dt * 0.02) food = (food - 1).coerceAtLeast(0)
            if (hp < 20 && food > 16) {
                // slow regen
            }
        }
    }

    private fun solid(world: World, x: Float, y: Float, z: Float): Boolean {
        val w = 0.3f; val h = 1.8f
        val x0 = floorI(x - w); val x1 = floorI(x + w)
        val y0 = floorI(y); val y1 = floorI(y + h - 0.01f)
        val z0 = floorI(z - w); val z1 = floorI(z + w)
        for (i in x0..x1) for (j in y0..y1) for (k in z0..z1) {
            if (Blocks.solid(world.get(i, j, k))) return true
        }
        return false
    }
    private fun floorI(v: Float) = kotlin.math.floor(v.toDouble()).toInt()
    private fun moveAxis(world: World, dx: Float, dy: Float, dz: Float) {
        x += dx; y += dy; z += dz
        if (solid(world, x, y, z)) {
            x -= dx; y -= dy; z -= dz
            if (dy < 0) { onGround = true; vy = 0f } else if (dy > 0) vy = 0f
            if (dx != 0f) vx = 0f
            if (dz != 0f) vz = 0f
        } else if (dy < 0) onGround = false
    }
}
