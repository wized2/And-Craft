package com.endroid.andcraft.world

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

enum class MobType { ZOMBIE, PIG, COW, SHEEP, VILLAGER, CREEPER, CHICKEN, WOLF, GOLEM }

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
        MobType.CREEPER -> 20
        MobType.CHICKEN -> 4
        MobType.WOLF -> 8
        MobType.GOLEM -> 100
    }
    var yaw = 0f
    var hurt = 0f
    var cd = 1f
    var wind = 0f
    var stun = 0f
    var big = false
    var sheared = false
    var prof = 0
    var ft = 0f
    var fuse = 0f
    var tamed = false
    var aggro = false
    var dead = false
    var cropX = Int.MIN_VALUE
    var cropY = 0
    var cropZ = 0
    var homeX = 0f
    var homeZ = 0f
    var dmg = 3
    var onGround = false
    var wt = 2f
    var dirx = 0f
    var dirz = 0f
    var w = if (type == MobType.GOLEM) 0.7f else 0.35f
    var h = when (type) { MobType.ZOMBIE, MobType.VILLAGER -> 1.8f; MobType.CREEPER -> 1.7f; MobType.CHICKEN -> 0.7f; MobType.WOLF -> 0.85f; MobType.GOLEM -> 2.7f; else -> 0.9f }
    var spd = when (type) { MobType.ZOMBIE -> 3.5f; MobType.CREEPER -> 2.7f; MobType.CHICKEN -> 1.0f; MobType.WOLF -> 2.4f; MobType.GOLEM -> 2.2f; else -> 1.1f }

    fun bigify() { big = true; hp = 45; dmg = 5; w = 0.4f; h = 2.3f; spd = 3f }
    fun color(): FloatArray = when (type) {
        MobType.ZOMBIE -> floatArrayOf(0.25f, 0.45f, 0.28f)
        MobType.PIG -> floatArrayOf(0.94f, 0.63f, 0.66f)
        MobType.COW -> floatArrayOf(0.35f, 0.25f, 0.18f)
        MobType.SHEEP -> floatArrayOf(0.9f, 0.9f, 0.88f)
        MobType.VILLAGER -> floatArrayOf(0.55f, 0.40f, 0.30f)
        MobType.CREEPER -> floatArrayOf(0.37f, 0.69f, 0.29f)
        MobType.CHICKEN -> floatArrayOf(0.96f, 0.96f, 0.94f)
        MobType.WOLF -> floatArrayOf(0.85f, 0.83f, 0.8f)
        MobType.GOLEM -> floatArrayOf(0.79f, 0.77f, 0.72f)
    }
}

class MobSystem {
    val mobs = ArrayList<Mob>()
    private var spawnT = 0f
    /** explosions requested by creepers: x, y, z, radius, damage */
    val booms = ArrayList<FloatArray>()

    private fun nearestHostile(m: Mob, range: Float): Mob? {
        var best: Mob? = null; var bd = range
        for (o in mobs) {
            if ((o.type != MobType.ZOMBIE && o.type != MobType.CREEPER) || o.dead) continue
            val d = hypot(o.x - m.x, o.z - m.z)
            if (d < bd && Math.abs(o.y - m.y) < 3f) { bd = d; best = o }
        }
        return best
    }

    private fun strike(m: Mob, t: Mob, dmg: Int) {
        t.hp -= dmg; t.hurt = 0.4f
        val dx = t.x - m.x; val dz = t.z - m.z
        val dd = hypot(dx, dz).coerceAtLeast(0.001f)
        t.vx = dx / dd * 6f; t.vz = dz / dd * 6f; t.vy = if (m.type == MobType.GOLEM) 9f else 5f
        if (t.hp <= 0) t.dead = true
    }

    fun update(world: World, player: Player, dayK: Float, dt: Float, day: Int = 0) {
        spawnT -= dt
        if (spawnT <= 0f) {
            spawnT = 2f
            val night = dayK < 0.32f
            val zc = mobs.count { it.type == MobType.ZOMBIE || it.type == MobType.CREEPER }
            val pc = mobs.size - zc
            if (!player.creative && night && zc < 5) trySpawn(world, player, if (Math.random() < 0.25) MobType.CREEPER else MobType.ZOMBIE, day >= 2)
            else if (!night && pc < 8) {
                val t = when ((Math.random() * 4).toInt()) {
                    0 -> MobType.PIG; 1 -> MobType.COW; 2 -> MobType.SHEEP; else -> if (Math.random() < 0.15 && mobs.none { it.type == MobType.WOLF }) MobType.WOLF else MobType.CHICKEN
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
            if (dist > 64f && m.type != MobType.VILLAGER && m.type != MobType.GOLEM && !m.tamed) { it.remove(); continue }
            if (m.dead) { it.remove(); continue }
            m.hurt = (m.hurt - dt).coerceAtLeast(0f)
            m.cd -= dt
            var spd = 0f
            when (m.type) {
                MobType.CREEPER -> {
                    if (dist < 24f) { m.dirx = dx / dist; m.dirz = dz / dist; spd = if (m.fuse > 0f) m.spd * 0.3f else m.spd }
                    else { m.dirx = 0f; m.dirz = 0f }
                    if (dist < 3f && Math.abs(player.y - m.y) < 2.2f && player.hp > 0) { if (m.fuse == 0f) com.endroid.andcraft.Sfx.play("hiss"); m.fuse += dt }
                    else m.fuse = (m.fuse - dt * 0.6f).coerceAtLeast(0f)
                    if (m.fuse >= 1.5f) { booms.add(floatArrayOf(m.x, m.y + 0.7f, m.z, 3f, 18f)); it.remove(); continue }
                }
                MobType.WOLF, MobType.GOLEM -> {
                    val gol = m.type == MobType.GOLEM
                    m.cd -= dt
                    val tg = if (gol || m.tamed) nearestHostile(m, if (gol) 16f else 14f) else null
                    if (tg != null) {
                        val cx = tg.x - m.x; val cz = tg.z - m.z
                        val cd = hypot(cx, cz).coerceAtLeast(0.001f)
                        m.dirx = cx / cd; m.dirz = cz / cd; spd = m.spd * 1.4f
                        if (cd < (if (gol) 2f else 1.5f) && m.cd <= 0f) {
                            strike(m, tg, if (gol) m.dmg else 4); m.cd = if (gol) 1.2f else 1f
                        }
                    } else if (m.aggro && !m.tamed && !gol) {
                        if (dist < 20f && dist > 0.9f) { m.dirx = dx / dist; m.dirz = dz / dist; spd = m.spd * 1.3f } else { m.dirx = 0f; m.dirz = 0f }
                        if (dist < 1.4f && m.cd <= 0f && player.hp > 0 && Math.abs(player.y - m.y) < 1.6f) { player.hurt(m.dmg); m.cd = 1f }
                    } else if (m.tamed) {
                        if (dist > 26f) { m.x = player.x + 2f; m.z = player.z; m.y = player.y + 1f }
                        if (dist > 5f) { m.dirx = dx / dist; m.dirz = dz / dist; spd = if (dist > 10f) m.spd * 1.5f else m.spd }
                        else { m.dirx = 0f; m.dirz = 0f }
                    } else {
                        m.wt -= dt
                        if (m.wt <= 0f) {
                            m.wt = 2f + Math.random().toFloat() * 3f
                            if (Math.random() < 0.4) { m.dirx = 0f; m.dirz = 0f }
                            else { val a = Math.random() * 6.28; m.dirx = cos(a).toFloat(); m.dirz = sin(a).toFloat() }
                        }
                        if (m.dirx != 0f || m.dirz != 0f) spd = m.spd * 0.7f
                        if (gol && hypot(m.x - m.homeX, m.z - m.homeZ) > 16f) {
                            m.dirx = Math.signum(m.homeX - m.x); m.dirz = Math.signum(m.homeZ - m.z); spd = m.spd
                        }
                    }
                }
                MobType.ZOMBIE -> {
                    if (dist < 24f) {
                        m.dirx = dx / dist; m.dirz = dz / dist; spd = m.spd
                    } else { m.dirx = 0f; m.dirz = 0f }
                    // short wind-up you can dodge or interrupt, then a swipe
                    m.stun = (m.stun - dt).coerceAtLeast(0f)
                    if (m.wind > 0f) {
                        m.wind -= dt
                        if (m.wind <= 0f) {
                            if (dist < 1.9f && Math.abs(player.y - m.y) < 1.7f && !player.creative) player.hurt(m.dmg)
                            m.cd = 1f
                        }
                    } else if (dist < 1.5f && Math.abs(player.y - m.y) < 1.6f && m.cd <= 0f && m.stun <= 0f && !player.creative) m.wind = 0.42f
                    if (m.wind > 0f || m.stun > 0f) { m.dirx = 0f; m.dirz = 0f; spd = 0f }
                    if (dayK > 0.55f) {
                        if (Math.random() < dt * 1.5) m.hp--
                        if (m.hp <= 0) { it.remove(); continue }
                    }
                }
                else -> {
                    var busy = false
                    if (m.type == MobType.VILLAGER) {
                        val hx = m.homeX - m.x; val hz = m.homeZ - m.z
                        val hd = hypot(hx, hz).coerceAtLeast(0.001f)
                        if (dayK < 0.3f) { // night: go home and stay
                            busy = true
                            if (hd > 1.2f) { m.dirx = hx / hd; m.dirz = hz / hd; spd = m.spd } else { m.dirx = 0f; m.dirz = 0f }
                        } else if (hd > 12f) {
                            m.dirx = hx / hd; m.dirz = hz / hd; m.wt = 2f
                        } else if (m.prof == 0) {
                            m.ft -= dt
                            if (m.ft <= 0f) { // look for ripe wheat near home
                                m.ft = 2.5f; m.cropX = Int.MIN_VALUE
                                var bd = 99f
                                val bx = floor(m.homeX).toInt(); val bz = floor(m.homeZ).toInt(); val by = floor(m.y).toInt()
                                for (x in bx - 9..bx + 9) for (z in bz - 9..bz + 9) for (y in by - 2..by + 1) {
                                    if (world.get(x, y, z) == Blocks.WHEAT2) {
                                        val d = hypot(x + 0.5f - m.x, z + 0.5f - m.z)
                                        if (d < bd) { bd = d; m.cropX = x; m.cropY = y; m.cropZ = z }
                                    }
                                }
                            }
                            if (m.cropX != Int.MIN_VALUE) {
                                if (world.get(m.cropX, m.cropY, m.cropZ) != Blocks.WHEAT2) m.cropX = Int.MIN_VALUE
                                else {
                                    val cx = m.cropX + 0.5f - m.x; val cz = m.cropZ + 0.5f - m.z
                                    val cd = hypot(cx, cz).coerceAtLeast(0.001f)
                                    busy = true
                                    if (cd < 1.3f) { world.set(m.cropX, m.cropY, m.cropZ, Blocks.WHEAT0); m.cropX = Int.MIN_VALUE; m.ft = 1.2f; m.dirx = 0f; m.dirz = 0f }
                                    else { m.dirx = cx / cd; m.dirz = cz / cd; spd = m.spd }
                                }
                            }
                        }
                    }
                    if (!busy) {
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
            }
            val k = (dt * 8f).coerceAtMost(1f)
            m.vx += (m.dirx * spd - m.vx) * k
            m.vz += (m.dirz * spd - m.vz) * k
            m.vy -= 28f * dt
            if (m.type == MobType.CHICKEN && m.vy < -3f) m.vy = -3f
            step(world, m, m.vx * dt, 0f, 0f)
            step(world, m, 0f, 0f, m.vz * dt)
            step(world, m, 0f, m.vy * dt, 0f)
            if (hypot(m.vx, m.vz) > 0.2f) m.yaw = atan2(-m.vx, -m.vz)
        }
    }

    fun add(type: MobType, x: Float, y: Float, z: Float, prof: Int = 0): Mob {
        val m = Mob(type, x, y, z)
        m.prof = prof; m.homeX = x; m.homeZ = z
        mobs.add(m)
        return m
    }

    private fun trySpawn(world: World, player: Player, type: MobType, brute: Boolean = false) {
        val a = Math.random() * Math.PI * 2
        val r = 16 + Math.random() * 12
        val x = (player.x + cos(a) * r).toInt()
        val z = (player.z + sin(a) * r).toInt()
        var y = -1
        for (yy in World.H - 1 downTo 1) {
            val b = world.get(x, yy, z)
            if (b == Blocks.AIR || Blocks.plant(b)) continue
            if (b == Blocks.WATER) return
            if (Blocks.solid(b)) { y = yy + 1; break }
        }
        if (y < 1) return
        val nm = Mob(type, x + 0.5f, y.toFloat(), z + 0.5f)
        if (brute && type == MobType.ZOMBIE && Math.random() < 0.22) nm.bigify()
        mobs.add(nm)
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
            if (dy < 0f) { m.vy = 0f; m.onGround = true }
            else if (dy > 0f) m.vy = 0f
            else if (m.onGround) { m.vy = 7.5f; m.onGround = false } // hop over 1-block steps
        } else if (dy < 0f) m.onGround = false
    }

    fun hit(player: Player, look: FloatArray): Mob? {
        var best: Mob? = null
        var bd = 3.6f
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
