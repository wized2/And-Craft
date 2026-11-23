package com.endroid.andcraft.world

class Recipe(
    val name: String,
    val cost: Map<Int, Int>,
    val note: String,
    val owned: (Player) -> Boolean = { false },
    val apply: (Player) -> Unit
) {
    fun canCraft(p: Player) = !owned(p) && cost.all { (id, n) -> p.count(id) >= n }
    fun costText(p: Player) = cost.entries.joinToString(" + ") { (id, n) -> "$n ${Blocks.name(id)} (${p.count(id)})" }
}

object Crafting {
    private fun cook(raw: Int, done: Int, label: String) =
        Recipe("Cook 2 $label", mapOf(raw to 2, Blocks.COAL_ORE to 1), "coal as fuel") { it.give(done, 2) }

    val recipes: List<Recipe> = listOf(
        Recipe("Planks x4", mapOf(Blocks.LOG to 1), "") { it.give(Blocks.PLANKS, 4) },
        Recipe("Sticks x4", mapOf(Blocks.PLANKS to 2), "for torches & tools") { it.give(Blocks.STICK, 4) },
        Recipe("Torch x4", mapOf(Blocks.STICK to 1, Blocks.COAL_ORE to 1), "stick + coal") { it.give(Blocks.TORCH, 4) },
        Recipe("Chest", mapOf(Blocks.PLANKS to 8), "store items") { it.give(Blocks.CHEST, 1) },
        Recipe("Furnace", mapOf(Blocks.STONE to 8), "smelt food & ore") { it.give(Blocks.FURNACE, 1) },
        Recipe("Shears", mapOf(Blocks.STONE to 2), "shear sheep") { it.give(Blocks.SHEARS, 1) },
        Recipe("Bread x1", mapOf(Blocks.SEEDS to 3), "3 seeds of wheat -> bread") { it.give(Blocks.BREAD, 1) },
        Recipe("Bread x4", mapOf(Blocks.SEEDS to 10), "bake a loaf stack") { it.give(Blocks.BREAD, 4) },
        Recipe("Wooden Pickaxe", mapOf(Blocks.PLANKS to 5), "mines stone, coal", { it.pick >= 1 }) { it.pick = 1 },
        Recipe("Wooden Sword", mapOf(Blocks.PLANKS to 3), "damage 4", { it.sword >= 1 }) { it.sword = 1 },
        Recipe("Wooden Axe", mapOf(Blocks.PLANKS to 5), "chops 2x faster", { it.axe >= 1 }) { it.axe = 1 },
        Recipe("Stone Pickaxe", mapOf(Blocks.PLANKS to 2, Blocks.STONE to 3), "mines gold, 2x faster", { it.pick >= 2 }) { it.pick = 2 },
        Recipe("Stone Sword", mapOf(Blocks.PLANKS to 1, Blocks.STONE to 2), "damage 5", { it.sword >= 2 }) { it.sword = 2 },
        Recipe("Stone Axe", mapOf(Blocks.PLANKS to 2, Blocks.STONE to 3), "chops 4x faster", { it.axe >= 2 }) { it.axe = 2 },
        cook(Blocks.RAW_PORK, Blocks.COOKED_PORK, "Porkchops"),
        cook(Blocks.RAW_BEEF, Blocks.STEAK, "Steaks"),
        cook(Blocks.RAW_MUTTON, Blocks.COOKED_MUTTON, "Mutton"),
        Recipe("Bed", mapOf(Blocks.PLANKS to 3, Blocks.WOOL to 3), "sleep at night, set spawn") { it.give(Blocks.BED, 1) },
        Recipe("Leather Armor", mapOf(Blocks.LEATHER to 8), "-28% melee damage", { it.armor >= 1 }) { it.armor = maxOf(it.armor, 1) },
        Recipe("Gold Armor", mapOf(Blocks.GOLD_ORE to 8), "-44% melee damage", { it.armor >= 2 }) { it.armor = 2 },
        Recipe("Golden Apple", mapOf(Blocks.GOLD_ORE to 8, Blocks.APPLE to 1), "heals 4 HP, eat with EAT") { it.give(Blocks.GOLDEN_APPLE, 1) },
        Recipe("Bricks x4", mapOf(Blocks.STONE to 4), "") { it.give(Blocks.BRICK, 4) },
        Recipe("Glass x2", mapOf(Blocks.SAND to 2, Blocks.COAL_ORE to 1), "") { it.give(Blocks.GLASS, 2) }
    )

    fun craft(p: Player, r: Recipe): Boolean {
        if (!r.canCraft(p)) return false
        for ((id, n) in r.cost) p.inv[id] -= n
        r.apply(p)
        return true
    }
}
