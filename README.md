# And Craft

Native **Android** voxel sandbox (Kotlin + OpenGL ES 2.0) — a port of [Endroid Craft](https://github.com/wized2/endroid-craft).

## Features
- Infinite-ish chunk world (grass, dirt, stone, trees, water, sand)
- First-person creative mode
- Touch look + virtual stick (left half of screen)
- **BREAK** / **PLACE** / **JUMP** buttons
- Long-press **PLACE** to cycle blocks
- Immediate mesh rebuild when breaking/placing (no ghost blocks)

## Build
```bash
./gradlew assembleDebug
```

## CI
Push to `main` builds debug + signed release (requires repo secrets).
Tag `v*` publishes a GitHub Release.

### Signing secrets
- `RELEASE_KEYSTORE_BASE64`
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_PASSWORD`
- `RELEASE_KEY_ALIAS`
