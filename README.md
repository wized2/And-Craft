# And Craft

Native **Android** voxel sandbox (Kotlin + OpenGL ES 2.0) — port of [Endroid Craft](https://github.com/wized2/endroid-craft).

## Features
- Chunk world with trees, ores, water (swimming), villages with houses, beds and farms; safe spawn
- **Survival** (default) with Minecraft values: hunger / saturation / exhaustion, regen at food 18+, starvation, fall damage, drowning-free swimming, death → respawn
- Tool tiers (wood, stone) with Minecraft break-time formula; stone/coal/gold need the right pickaxe to drop
- Mobs with models: zombies at night (burn in daylight), pigs, cows, sheep — drops (meat, leather, wool, rotten flesh)
- Eating (EAT button), cooking with coal, crafting menu (tools, armor, bed, golden apple, bricks, glass)
- Farming: seeds → wheat → bread
- Bed: set spawn, sleep through the night
- 20-minute day/night cycle with darker world at night
- Creative mode (unlimited blocks, double-tap JUMP to fly)
- Auto-save (world edits, inventory, position) in app storage

## Controls
| Action | |
|--------|--|
| Move | Left side of screen (push to the edge to sprint) |
| Look | Drag right side |
| Break / attack | Hold BREAK |
| Place / plant / sleep | PLACE |
| Eat | EAT |
| Hotbar | tap a slot |
| Crafting / menu | CRAFT and ≡ (top right) |

## Build
```bash
./gradlew assembleDebug
```

## CI / Releases
`.github/workflows/build.yml` builds on every push, and **every push to `main` publishes a GitHub Release** (`v1.0.<run number>`) with the release APK; pushing a `v*` tag publishes a release with that tag.

For a stable signing key (so new releases install over old ones) add these repository secrets: `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_PASSWORD`, `RELEASE_KEY_ALIAS`. Without them the release APK is signed with a throwaway debug key and its file name ends in `-debugsigned`.
