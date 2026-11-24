package com.endroid.andcraft.world

import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class Player(var x: Float, var y: Float, var z: Float) {
    var yaw = 0f
    var pitch = 0f
    var vx = 0f
    var vy = 0f
    var vz = 0f
    var onGround = false
    var inWater = false
    var fly = false
    var creative = false
    var hp = 20
    var food = 20
    var sat = 5f
    var exh = 0f
    var sprint = false
    var hurtFlash = 0f
    var eatCd = 0f
    var slot = 0
    // tool tiers: 0 none, 1 wood, 2 stone. armor: 0 none, 1 leather, 2 gold
    var pick = 0
    var axe = 0
    var sword = 0
    var armor = 0
    var autoJump = true
    var kills = 0
    var brutes = 0
    var ate = 0
    var sleeps = 0
    val hotbar = Blocks.HOTBAR_DEFAULT.copyOf()
    val inv = IntArray(64) { 0 }
    private var fallY = y
    private var regenT = 0f
    private var starveT = 0f

    fun starterKit() {
        inv[Blocks.SEEDS] = 8
        inv[Blocks.DIRT] = 16
        inv[Blocks.GRASS] = 8
    }

    fun selected(): Int = hotbar[slot.coerceIn(0, hotbar.lastIndex)]
    fun eyeY() = y + 1.62f

    fun lookDir(out: FloatArray) {
        val cp = cos(pitch)
        out[0] = -sin(yaw) * cp
        out[1] = sin(pitch)
        out[2] = -cos(yaw) * cp
    }

    fun count(id: Int): Int = if (id in inv.indices) inv[id] else 0
    fun has(id: Int, n: Int = 1) = creative || count(id) >= n
    fun take(id: Int, n: Int = 1): Boolean {
        if (creative) return true
        if (count(id) < n) return false
        inv[id] -= n
        return true
    }
    fun give(id: Int, n: Int = 1) {
        if (creative || id <= 0 || id !in inv.indices || n <= 0) return
        inv[id] = min(999, inv[id] + n)
        autoSlot(id)
    }

    /** put newly collected placeable items on the hotbar (replacing an empty slot) */
    private fun autoSlot(id: Int) {
        if (!(Blocks.placeable(id) || id == Blocks.SEEDS || id == Blocks.SHEARS)) return
        if (hotbar.contains(id)) return
        for (i in hotbar.indices) {
            if (i != slot && count(hotbar[i]) == 0) { hotbar[i] = id; return }
        }
    }

    fun weaponDmg(): Int = intArrayOf(2, 4, 5, 6, 7)[sword.coerceIn(0, 4)]

    fun addExh(v: Float) { if (!creative) exh += v }

    /** damage from a mob (armor helps) */
    fun hurt(n: Int) {
        if (creative || hp <= 0) return
        val red = when (armor) { 1 -> 0.28f; 2 -> 0.44f; 3 -> 0.6f; 4 -> 0.8f; else -> 0f }
        hp = max(0, hp - max(1, Math.round(n * (1f - red))))
        hurtFlash = 1f
        addExh(0.1f)
        com.endroid.andcraft.Sfx.play("hurt"); com.endroid.andcraft.Sfx.vib(40)
    }

    /** returns message or null when nothing eaten */
    fun eat(): String? {
        if (creative || eatCd > 0f) return null
        if (count(Blocks.GOLDEN_APPLE) > 0) {
            inv[Blocks.GOLDEN_APPLE]--
            ate++
            hp = min(20, hp + 4); food = min(20, food + 4); sat = min(food.toFloat(), sat + 9.6f)
            eatCd = 1.2f
            return "Golden Apple"
        }
        if (food >= 20) return null
        for (id in Blocks.EAT_ORDER) {
            if (count(id) > 0) {
                inv[id]--
                ate++
                food = min(20, food + Blocks.foodValue(id))
                sat = min(food.toFloat(), sat + Blocks.satValue(id))
                eatCd = 1.2f
                return Blocks.name(id)
            }
        }
        return null
    }

    fun respawn(sp: IntArray) {
        x = sp[0] + 0.5f; y = sp[1].toFloat(); z = sp[2] + 0.5f
        vx = 0f; vy = 0f; vz = 0f
        hp = 20; food = 20; sat = 5f; exh = 0f; fallY = y
    }

    fun update(world: World, dt: Float, forward: Float, strafe: Float, jumpHeld: Boolean, sprintWanted: Boolean) {
        hurtFlash = max(0f, hurtFlash - dt * 2.2f)
        eatCd = max(0f, eatCd - dt)
        inWater = world.get(floor(x).toInt(), floor(y + 0.5f).toInt(), floor(z).toInt()) == Blocks.WATER
        if (!creative) fly = false
        sprint = sprintWanted && forward > 0.1f && (creative || food > 6)
        var sp = if (fly) 8f else if (creative) 5.2f else 4.3f
        if (sprint && !fly) sp *= 1.3f
        if (inWater && !fly) sp *= 0.55f
        val sy = sin(yaw)
        val cy = cos(yaw)
        val tx = (-sy * forward + cy * strafe) * sp
        val tz = (-cy * forward - sy * strafe) * sp
        val k = (dt * if (onGround || fly) 12f else 4f).coerceAtMost(1f)
        vx += (tx - vx) * k
        vz += (tz - vz) * k
        when {
            fly -> vy = if (jumpHeld) 6.5f else 0f
            inWater -> {
                vy = max(vy - 12f * dt, -4f)
                if (jumpHeld) vy = min(vy + 30f * dt, 3.6f)
            }
            else -> {
                vy = max(vy - 28f * dt, -40f)
                if (jumpHeld && onGround) {
                    vy = 8.4f
                    onGround = false
                    addExh(if (sprint) 0.2f else 0.05f)
                }
            }
        }
        moveAxis(world, vx * dt, 0f, 0f)
        moveAxis(world, 0f, 0f, vz * dt)
        moveAxis(world, 0f, vy * dt, 0f)
        if (y < -20f) { y = 40f; vy = 0f; fallY = y }
        // fall damage (Minecraft: distance - 3)
        if (onGround) {
            val fd = fallY - y
            if (fd > 3.2f && !inWater && !creative) hurt(ceil(fd - 3f).toInt())
            fallY = y
        } else if (inWater || fly) fallY = y else fallY = max(fallY, y)
        if (!creative) tickSurvival(dt)
    }

    // Minecraft hunger rules: exhaustion -> saturation -> food; regen at food>=18; starvation at 0
    private fun tickSurvival(dt: Float) {
        if (sprint && onGround) exh += 0.1f * kotlin.math.hypot(vx, vz) * dt
        if (inWater) exh += 0.01f * kotlin.math.hypot(vx, vz) * dt
        while (exh >= 4f) {
            exh -= 4f
            if (sat > 0f) sat = max(0f, sat - 1f) else food = max(0, food - 1)
        }
        if (food >= 18 && hp in 1..19) {
            regenT += dt
            if (regenT >= 4f) { regenT = 0f; hp++; addExh(1.5f) }
        } else regenT = 0f
        if (food <= 0) {
            starveT += dt
            if (starveT >= 4f) { starveT = 0f; if (hp > 1) hp--; hurtFlash = 1f }
        } else starveT = 0f
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
    private fun floorI(v: Float) = floor(v.toDouble()).toInt()
    private fun moveAxis(world: World, dx: Float, dy: Float, dz: Float) {
        x += dx; y += dy; z += dz
        if (solid(world, x, y, z)) {
            val stepUp = autoJump && onGround && (dx != 0f || dz != 0f) && !solid(world, x, y + 1.05f, z)
            x -= dx; y -= dy; z -= dz
            if (stepUp) { vy = 8.4f; onGround = false }
            if (dy < 0) { onGround = true; vy = 0f } else if (dy > 0) vy = 0f
            if (dx != 0f) vx = 0f
            if (dz != 0f) vz = 0f
        } else if (dy < 0) onGround = false
    }
}
