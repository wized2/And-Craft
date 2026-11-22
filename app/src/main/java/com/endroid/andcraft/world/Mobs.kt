package com.endroid.andcraft.world

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

enum class MobType { ZOMBIE, PIG, COW, SHEEP, VILLAGER }

class Mob(
    var type: MobType,
    var x: Float, var y: Float, var z: Float
) {
    var vx = 0f; var vy = 0f; var vz = 0f
    var hp = when (type) {
        MobType.ZOMBIE -> 20
        MobType.VILLAGER -> 20
        MobType.COW -> 10
        MobType.SHEEP -> 8
        MobType.PIG -> 10
    }
    var yaw = 0f
    var hurt = 0f
    var cd = 1f
    var wt = 2f
    var dirx = 0f
    var dirz = 0f
    val w = 0.35f
    val h = if (type == MobType.ZOMBIE || type == MobType.VILLAGER) 1.8f else 0.9f
    val spd = if (type == MobType.ZOMBIE) 3.2f else 1.1f
    fun color(): FloatArray = when (type) {
        MobType.ZOMBIE -> floatArrayOf(0.25f, 0.45f, 0.28f)
        MobType.PIG -> floatArrayOf(0.94f, 0.63f, 0.66f)
        MobType.COW -> floatArrayOf(0.35f, 0.25f, 0.18f)
        MobType.SHEEP -> floatArrayOf(0.9f, 0.9f, 0.88f)
        MobType.VILLAGER -> floatArrayOf(0.55f, 0.40f, 0.30f)
    }
}

class MobSystem {
    val mobs = ArrayList<Mob>()
    private var spawnT = 0f

    fun update(world: World, player: Player, dayK: Float, dt: Float) {
        spawnT -= dt
        if (spawnT <= 0f) {
            spawnT = 2f
            val night = dayK < 0.32f
            val zc = mobs.count { it.type == MobType.ZOMBIE }
            val pc = mobs.size - zc
            if (!player.creative && night && zc < 5) trySpawn(world, player, MobType.ZOMBIE)
            else if (!night && pc < 8) {
                val t = when ((Math.random() * 3).toInt()) {
                    0 -> MobType.PIG; 1 -> MobType.COW; else -> MobType.SHEEP
                }
                trySpawn(world, player, t)
            }
        }
        val it = mobs.listIterator()
        while (it.hasNext()) {
            val m = it.next()
            val dx = player.x - m.x
            val dz = player.z - m.z
            val dist = hypot(dx, dz).coerceAtLeast(0.001f)
            if (dist > 64f) { it.remove(); continue }
            m.hurt = (m.hurt - dt).coerceAtLeast(0f)
            m.cd -= dt
            var spd = 0f
            when (m.type) {
                MobType.ZOMBIE -> {
                    if (dist < 24f) {
                        m.dirx = dx / dist; m.dirz = dz / dist; spd = m.spd
                    }
                    if (dist < 1.5f && m.cd <= 0f && !player.creative) {
                        player.hp = (player.hp - 2).coerceAtLeast(0)
                        m.cd = 1f
                    }
                    if (dayK > 0.55f) {
                        m.hp -= (3 * dt).toInt().coerceAtLeast(0)
                        if (Math.random() < dt) m.hp--
                        if (m.hp <= 0) { it.remove(); continue }
                    }
                }
                else -> {
                    m.wt -= dt
                    if (m.wt <= 0f) {
                        m.wt = 2f + Math.random().toFloat() * 3f
                        if (Math.random() < 0.4) { m.dirx = 0f; m.dirz = 0f }
                        else {
                            val a = Math.random() * 6.28
                            m.dirx = cos(a).toFloat(); m.dirz = sin(a).toFloat()
                        }
                    }
                    if (m.dirx != 0f || m.dirz != 0f) spd = m.spd
                }
            }
            val k = (dt * 8f).coerceAtMost(1f)
            m.vx += (m.dirx * spd - m.vx) * k
            m.vz += (m.dirz * spd - m.vz) * k
            m.vy -= 28f * dt
            step(world, m, m.vx * dt, 0f, 0f)
            step(world, m, 0f, 0f, m.vz * dt)
            step(world, m, 0f, m.vy * dt, 0f)
            if (hypot(m.vx, m.vz) > 0.2f) m.yaw = atan2(-m.vx, -m.vz)
        }
    }

    private fun trySpawn(world: World, player: Player, type: MobType) {
        val a = Math.random() * Math.PI * 2
        val r = 16 + Math.random() * 12
        val x = (player.x + cos(a) * r).toInt()
        val z = (player.z + sin(a) * r).toInt()
        var y = -1
        for (yy in World.H - 1 downTo 1) {
            val b = world.get(x, yy, z)
            if (b == Blocks.AIR || Blocks.plant(b)) continue
            if (Blocks.solid(b) || b == Blocks.WATER) { y = yy + 1; break }
        }
        if (y < 1) return
        mobs.add(Mob(type, x + 0.5f, y.toFloat(), z + 0.5f))
    }

    private fun step(world: World, m: Mob, dx: Float, dy: Float, dz: Float) {
        m.x += dx; m.y += dy; m.z += dz
        val solid = run {
            val x0 = kotlin.math.floor((m.x - m.w).toDouble()).toInt()
            val x1 = kotlin.math.floor((m.x + m.w).toDouble()).toInt()
            val y0 = kotlin.math.floor(m.y.toDouble()).toInt()
            val y1 = kotlin.math.floor((m.y + m.h - 0.01f).toDouble()).toInt()
            val z0 = kotlin.math.floor((m.z - m.w).toDouble()).toInt()
            val z1 = kotlin.math.floor((m.z + m.w).toDouble()).toInt()
            var s = false
            for (i in x0..x1) for (j in y0..y1) for (k in z0..z1)
                if (Blocks.solid(world.get(i, j, k))) s = true
            s
        }
        if (solid) {
            m.x -= dx; m.y -= dy; m.z -= dz
            if (dy < 0) m.vy = 0f
        }
    }

    fun hit(player: Player, look: FloatArray): Mob? {
        var best: Mob? = null
        var bd = 3.5f
        for (m in mobs) {
            val dx = m.x - player.x
            val dy = (m.y + m.h * 0.5f) - player.eyeY()
            val dz = m.z - player.z
            val d = hypot(dx, hypot(dy, dz))
            if (d > bd) continue
            val dot = (dx * look[0] + dy * look[1] + dz * look[2]) / d
            if (dot > 0.85f) { bd = d; best = m }
        }
        return best
    }
}
