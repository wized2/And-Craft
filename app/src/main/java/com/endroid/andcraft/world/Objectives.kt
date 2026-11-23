package com.endroid.andcraft.world

class Objective(val text: String, val ok: (Player, Int) -> Boolean)

object Objectives {
    val list: List<Objective> = listOf(
        Objective("Punch a tree: collect 3 logs") { p, _ -> p.count(Blocks.LOG) >= 3 || p.count(Blocks.PLANKS) >= 1 || p.pick >= 1 },
        Objective("Open CRAFT and make planks") { p, _ -> p.count(Blocks.PLANKS) >= 1 || p.pick >= 1 },
        Objective("Craft a Wooden Pickaxe (5 planks)") { p, _ -> p.pick >= 1 },
        Objective("Mine 3 stone (needs the pickaxe to drop)") { p, _ -> p.count(Blocks.STONE) >= 3 || p.pick >= 2 },
        Objective("Collect seeds (break tall grass or find a village farm)") { p, _ -> p.count(Blocks.SEEDS) >= 4 },
        Objective("Harvest grown wheat") { p, _ -> p.count(Blocks.BREAD) >= 1 || p.ate > 0 },
        Objective("Trade with a villager") { p, _ -> p.count(Blocks.EMERALD) >= 1 },
        Objective("Craft a Stone Pickaxe and a sword") { p, _ -> p.pick >= 2 && p.sword >= 1 },
        Objective("Hunt an animal, cook the meat with coal, and eat (EAT)") { p, _ -> p.ate >= 1 },
        Objective("Survive your first night (build a shelter!)") { _, day -> day >= 1 },
        Objective("Craft a Bed (3 wool from sheep + 3 planks) and sleep") { p, _ -> p.sleeps >= 1 },
        Objective("Defeat 3 zombies") { p, _ -> p.kills >= 3 },
        Objective("Craft armor (leather from cows, or gold)") { p, _ -> p.armor >= 1 },
        Objective("Defeat a Brute zombie (from day 3)") { p, _ -> p.brutes >= 1 }
    )
}
