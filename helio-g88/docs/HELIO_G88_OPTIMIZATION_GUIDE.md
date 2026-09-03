# Winlator Helio G88 - G100 Optimization Guide (Vortek Edition)

**Last updated:** 2026-09-03
**Fork:** winlator-helio v3

---

## Hardware Target

- **SoC:** MediaTek Helio G88 (MT6769H), 12nm
- **CPU:** 2× Cortex-A75 @ 2.0 GHz + 6× Cortex-A55 @ 1.8 GHz (Big.LITTLE)
- **GPU:** Mali-G52 MC2, ~96 GFLOPS, 2 shader cores
- **Vulkan:** 1.1+ (via Vortek)
- **OpenGL ES:** 3.2 (via VirGL)
- **RAM:** LPDDR4x, ~14.4 GB/s shared between CPU and GPU
- **Reference device:** Infinix HOT 30 (X6831), XOS 12.6, Android 13, 8GB/256GB

---

## Why Mali-G52 is the Hardest Target

| Limitation | Impact | Workaround |
|---|---|---|
| No BCn hardware decoder | DX10+ textures can't be loaded directly | Vortek CPU-decompresses BCn JIT |
| No `gl_ClipDistance` support | Many DXVK shaders fail Vulkan validation | Vortek patches SPIR-V to remove |
| No `gl_PrimitiveID` (in some games) | Some shaders use undefined behavior | Vortek emulates via fallback shader |
| Only 2 shader cores | ~96 GFLOPS, can't run heavy shaders | Lower resolution (960×544), lower shader quality |
| No full desktop OpenGL 4.6 | WineD3D's `glsl` backend uses GL 4.1 | Use Vulkan renderer (Vortek) |
| Shared LPDDR4x with CPU | BCn decode and rendering fight for bandwidth | Set shader cache to 512MB to reuse work |

None of these are fixable by config alone. The fork works around them with Vortek + tuning.

---

## What This Fork Does (v3)

### 1. Vortek as Default Graphics Driver for Mali
Previous version defaulted to VirGL,VirGL. VirGL provides no Vulkan ICD, so DX10-11 games had no working Vulkan path. **This version defaults to Vortek,VirGL** for Mali devices.

Vortek is already bundled in upstream Winlator 10.0+ (`libvulkan_vortek.so` + `libvortekrenderer.so` in `assets/`). It was never the default for Mali — that was the bug.

### 2. WineD3D Vulkan Renderer Auto-Set
The registry key `HKCU\Software\Wine\Direct3D\renderer = vulkan` is now set automatically when a container is created/edited (`ContainerDetailFragment.java`). This tells WineD3D to translate DX→Vulkan (via Vortek) instead of DX→OpenGL (via VirGL).

The Vulkan path is faster for DX10-11 because Vortek handles BCn decode and shader patches at the Vulkan level, which is the layer DXVK and modern WineD3D operate at.

### 3. Helio G88 Box64 Preset
Optimized for Cortex-A75 big cores + Mali-G52 GPU driver coexistence:

```bash
BOX64_DYNAREC_SAFEFLAGS=2     # Correct flag handling for SEH
BOX64_DYNAREC_FASTNAN=1       # Skip NaN normalization
BOX64_DYNAREC_FASTROUND=1     # Fast float-to-int rounding
BOX64_DYNAREC_X87DOUBLE=0     # Skip 80-bit FP (Mali doesn't need it)
BOX64_DYNAREC_BIGBLOCK=3      # Maximize block size for A75 L1
BOX64_DYNAREC_STRONGMEM=1     # Memory barriers (shared LPDDR4x)
BOX64_DYNAREC_FORWARD=256     # Forward jump size limit
BOX64_DYNAREC_CALLRET=1       # Hardware CALL/RET fusion on A75
BOX64_DYNAREC_WAIT=1          # Wait for results
BOX64_DYNAREC_NATIVEFLAGS=0   # Don't recompute native flags
BOX64_DYNAREC_WEAKBARRIER=2   # Relaxed memory barriers
BOX64_DYNAREC_AFFINITY=2-7    # Pin dynarec workers to A55 cluster
```

**Why A55 for Box64?** Wine's main thread runs on A75 by default (Android's foreground-app policy). The Mali GPU driver also wants A75 for shader compilation threads. Pinning Box64's worker threads to A55 (cores 2-7) prevents cache invalidation from cluster migration and gives the GPU driver uninterrupted A75 access.

### 4. Helio-Tuned Environment Variables (Container defaults)
```bash
LIBGL_MALLOC=libc_malloc_hooks    # Avoid Mesa's internal allocator (incompatible with Winlator)
MESA_SHADER_CACHE_DISABLE=false   # Cache compiled shaders (saves CPU on repeat plays)
MESA_SHADER_CACHE_MAX_SIZE=512MB  # Generous cache for Mali (no other big users)
MESA_GLSL_VERSION_OVERRIDE=410    # Report GL 4.1 (matches Wine's WineD3D output)
MESA_GL_VERSION_OVERRIDE=4.1COMPAT  # Compat profile for older games
mesa_glthread=false               # Single-threaded Mesa (more stable on 2-core Mali)
BOX64_MMAP32=1                    # Wine's 32-bit address layout
STRONGMEM=1                       # Box64 strong memory model (correct for shared LPDDR4x)
BOX64_DYNAREC_SAFEFLAGS=2         # See preset above
WINEESYNC=1                       # eventfd-based sync (faster than futex on Android kernel)
WINEDEBUG=-all                    # Suppress Wine's debug spam
```

### 5. Resolution Scaling (960×544 default)
- Default changed from 1280×720 to 960×544 (exact 1/4 of 1080p)
- Reduces Mali-G52 fill rate load by 43% vs 720p
- 960×544 is enough for DX9-era games; DX10-11 games may need to drop to 800×450

### 6. Mali-G52 GPU Card in `gpu_cards.json`
- Vendor: ARM (`0x13B4` = 5044)
- Device: Mali-G52 MC2 (`0x7200` = 29184)
- Wine will report this as the GPU in DX logs and game config tools

---

## XOS 12.6 Specific Workaround (Infinix HOT 30)

XOS aggressively kills long-running background processes. The container takes 60-100 seconds to start, which XOS may interpret as a frozen process.

**Before installing the new APK:**

1. Settings → Apps → Winlator-Helio (or "Winulator")
2. Battery → Restricted → **Don't optimize**
3. Settings → Battery → App power consumption → Winlator → **No restrictions**
4. Grant all permissions: Storage, Camera (if needed), Notifications

**If container still hangs "Starting up":**
- Open recent apps (swipe up from bottom)
- Swipe Winlator away
- Reopen Winlator
- Container should start normally after this

---

## Game Compatibility Tiers

**Tier 1 — Plays Well (DX9, 25-40 FPS at 960×544):**
- NFS Most Wanted 2005, NFS Carbon
- GTA San Andreas, GTA Vice City
- Half-Life 2, Counter-Strike 1.6, Counter-Strike Source
- PES 2013, FIFA 07-12
- Assassin's Creed 1
- Max Payne 1-2
- Civilization IV, Age of Empires III

**Tier 2 — Plays with Tuning (DX10-11, 10-25 FPS at 800-960px):**
- NFS Shift, NFS Hot Pursuit (2010)
- BioShock 2
- Dead Space 1
- Far Cry 2
- Dirt 2
- Portal 1-2 (Source engine + DX11)
- Left 4 Dead 2 (DX9/11)
- Batman Arkham Asylum (DX9)
- Mirror's Edge (DX9)

**Tier 3 — Do Not Try (DX12, 2-8 FPS, often crash):**
- GTA V
- The Witcher 3
- Cyberpunk 2077
- RDR2
- Anything from 2016+

---

## Performance Tuning Per-Game

For best results per game:

1. **DX9 games:** Config A (VirGL + WineD3D OpenGL) — most stable
2. **DX10-11 games:** Config B (Vortek + WineD3D Vulkan) — fork default, best perf
3. **DX11 games with shader glitches:** Try Config C (Vortek + DXVK)
4. **Heavy games:** Lower resolution to 800×450 or 640×360

If a game hangs on startup, try:
- `BOX64_DYNAREC_SAFEFLAGS=1` instead of 2
- Disable `MESA_GLSL_VERSION_OVERRIDE` (some games detect wrong version)
- Switch to VirGL + WineD3D (Config A) as fallback

---

## Validation Tests (After Installing New APK)

Run these in order:

1. **DX9 smoke test:** NFS Most Wanted 2005 → should reach main menu in <30s
2. **DX9 perf test:** Half-Life 2 → should hit 25-35 FPS at 960×544
3. **DX10-11 smoke test:** BioShock 2 → should reach main menu in <60s
4. **DX10-11 perf test:** NFS Shift → should hit 15-25 FPS at 800×450

If any of these fail, capture logcat output and report. The fork's env vars are tuned for these specific scenarios.

---

## What This Fork Does NOT Do

- Does not enable real DX12 hardware acceleration (Mali-G52 doesn't support it)
- Does not improve game loading times beyond what storage bandwidth allows
- Does not bypass anti-cheat (most PC games don't have one that affects Wine)
- Does not work for x86-32 games (Box64 only, Box86 not included)
- Does not support Vulkan 1.2+ features (Vortek uses Vulkan 1.1)

If you need any of these, you'd need a different device (Snapdragon 8 series with Turnip, for example).
