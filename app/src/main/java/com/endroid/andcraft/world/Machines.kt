package com.endroid.andcraft.world

class Furnace {
    var fuel = 0f
    var prog = 0f
    var inId = 0
    var outId = 0
    var outN = 0
}

/** Furnaces and chests, keyed by block position. */
object Machines {
    val furnaces = HashMap<String, Furnace>()
    val chests = HashMap<String, HashMap<Int, Int>>()

    /** raw item -> smelted result (gold ore -> emerald, like the web version) */
    val SMELT = mapOf(
        Blocks.RAW_PORK to Blocks.COOKED_PORK,
        Blocks.RAW_BEEF to Blocks.STEAK,
        Blocks.RAW_MUTTON to Blocks.COOKED_MUTTON,
        Blocks.GOLD_ORE to Blocks.EMERALD
    )

    fun key(x: Int, y: Int, z: Int) = "$x,$y,$z"
    fun furnace(x: Int, y: Int, z: Int): Furnace = furnaces.getOrPut(key(x, y, z)) { Furnace() }
    fun chest(x: Int, y: Int, z: Int): HashMap<Int, Int> = chests.getOrPut(key(x, y, z)) { HashMap() }

    fun tick(dt: Float) {
        for (f in furnaces.values) {
            if (f.fuel > 0f) f.fuel -= dt
            if (f.inId != 0 && SMELT.containsKey(f.inId) && f.fuel > 0f) {
                f.prog += dt / 8f
                if (f.prog >= 1f) {
                    f.prog = 0f
                    val out = SMELT[f.inId] ?: 0
                    if (out != 0) {
                        if (f.outId != out) { f.outId = out; f.outN = 0 }
                        f.outN++
                    }
                    f.inId = 0
                }
            } else f.prog = 0f
        }
    }

    fun clear() { furnaces.clear(); chests.clear() }

    fun encode(): String {
        val sb = StringBuilder()
        for ((k, f) in furnaces) sb.append("F;").append(k).append(';').append(f.fuel).append(';').append(f.inId).append(';').append(f.outId).append(';').append(f.outN).append('\n')
        for ((k, c) in chests) {
            sb.append("C;").append(k).append(';')
            sb.append(c.entries.filter { it.value > 0 }.joinToString(",") { "${it.key}=${it.value}" })
            sb.append('\n')
        }
        return sb.toString()
    }

    fun decode(s: String) {
        clear()
        for (line in s.split('\n')) {
            val p = line.split(';')
            if (p.size < 3) continue
            if (p[0] == "F" && p.size >= 6) {
                val f = Furnace()
                f.fuel = p[2].toFloatOrNull() ?: 0f
                f.inId = p[3].toIntOrNull() ?: 0
                f.outId = p[4].toIntOrNull() ?: 0
                f.outN = p[5].toIntOrNull() ?: 0
                furnaces[p[1]] = f
            } else if (p[0] == "C") {
                val c = HashMap<Int, Int>()
                for (e in p[2].split(',')) {
                    val q = e.split('=')
                    if (q.size == 2) c[q[0].toIntOrNull() ?: continue] = q[1].toIntOrNull() ?: continue
                }
                chests[p[1]] = c
            }
        }
    }
}

class Trade(val give: Int, val giveN: Int, val take: Int, val takeN: Int, val name: String)

object Trades {
    val BY_PROF: Map<Int, List<Trade>> = mapOf(
        0 to listOf(
            Trade(Blocks.BREAD, 3, Blocks.EMERALD, 1, "3 Bread → Emerald"),
            Trade(Blocks.SEEDS, 16, Blocks.EMERALD, 1, "16 Seeds → Emerald"),
            Trade(Blocks.EMERALD, 1, Blocks.BREAD, 5, "Emerald → 5 Bread"),
            Trade(Blocks.EMERALD, 1, Blocks.SEEDS, 8, "Emerald → 8 Seeds")
        ),
        1 to listOf(
            Trade(Blocks.WOOL, 8, Blocks.EMERALD, 1, "8 Wool → Emerald"),
            Trade(Blocks.EMERALD, 1, Blocks.WOOL, 4, "Emerald → 4 Wool")
        ),
        2 to listOf(
            Trade(Blocks.STONE, 20, Blocks.EMERALD, 1, "20 Stone → Emerald"),
            Trade(Blocks.EMERALD, 1, Blocks.BRICK, 6, "Emerald → 6 Bricks")
        )
    )
    val PROF_NAMES = arrayOf("farmer", "shepherd", "mason")

    fun apply(p: Player, t: Trade): Boolean {
        if (!p.creative) {
            if (p.count(t.give) < t.giveN) return false
            p.inv[t.give] -= t.giveN
        }
        p.give(t.take, t.takeN)
        return true
    }
}
