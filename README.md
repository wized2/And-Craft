# And Craft

Native **Android** voxel sandbox (Kotlin + OpenGL ES 2.0) — port of [Endroid Craft](https://github.com/wized2/endroid-craft).

## Features
- Chunk world: grass, dirt, stone, sand, snow, ores, trees, water
- **Villages** with houses, beds, farmland & wheat
- **Day / night** cycle (sky color)
- **Creative / Survival** toggle (HP, food)
- Hotbar + inventory counts
- **Farming**: till land, plant seeds, grow & harvest wheat → bread
- **Mobs**: zombies at night, pigs/cows/sheep by day; attack with BREAK
- Touch look + virtual stick · BREAK / PLACE / JUMP
- Immediate mesh rebuild on break/place

## Controls
| Action | |
|--------|--|
| Move | Left half of screen (stick) |
| Look | Drag right side |
| Break / attack | Hold BREAK |
| Place / farm | PLACE |
| Cycle hotbar | Long-press PLACE or tap hotbar |
| Mode | MODE button |

## Build
```bash
./gradlew assembleDebug
```

## CI
Signed release on `main` (Fern-style secrets). Tag `v*` → GitHub Release.
