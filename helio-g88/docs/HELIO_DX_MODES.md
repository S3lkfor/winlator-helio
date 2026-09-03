# Helio DX Mode Compatibility

This document explains why Mali-G52 (Mali-G52 MP1/MP2) cannot run most PC
games, and what the Winulator fork does about it.

## The problem

Mali-G52 is a **mid-range mobile GPU** with the following limits:

- **OpenGL ES 3.2** only (no full desktop OpenGL 4.6)
- **Vulkan 1.0/1.1** (Helio G99/G200 onwards: Vulkan 1.3)
- **No native DirectX 9/10/11/12 hardware support** — it has to translate

The upstream Winlator ships a `VirGL` driver that runs on Mali but does not
expose desktop OpenGL 4.x. As a result, any DirectX 9/10/11 game that uses
shader model 4 or 5 will fail or run at <5 FPS.

## What Winulator Helio does

### 1. WineD3D as the default DX wrapper (DX1-11)

WineD3D translates DirectX 1-11 calls to OpenGL. On Helio + VirGL the path
is:

```
DX1-11  →  WineD3D  →  GL 3.2  →  VirGL (Mesa virpipe)  →  Mali-G52
```

This is the **only working path** for DX1-11 on Mali. DXVK requires a real
Vulkan ICD that maps to GPU hardware — VirGL provides no such ICD, so DXVK
hangs on first draw call.

**Expected performance:**
- DX7-DX8 games: 15-30 FPS at 800x600 (Doom 3, Half-Life 1, NFS MW 2005)
- DX9c games: 10-20 FPS at 640x480 (GTA SA, Half-Life 2, CS 1.6)
- DX10-DX11 games: 5-10 FPS at 480x320 or unplayable (Crysis, Skyrim)

### 2. VKD3D-Proton for DX12 (opt-in)

VKD3D-Proton translates DX12 to Vulkan. With VirGL on Mali, the path is:

```
DX12  →  VKD3D-Proton  →  SPIR-V  →  VirGL  →  Mali-G52
```

This works for a narrow set of DX12-only titles that don't need
conservative raster, mesh shaders, or ray tracing. Most won't run.

### 3. Settings tuned for Mali

**WineD3D defaults (in `WineD3DConfigDialog`):**
- `csmt=3` (command stream multi-threading) — slight perf gain
- `strict_shader_math=0` — Mali is loose on shader spec
- `renderer=gl` — standard OpenGL path
- `OffscreenRenderingMode=fbo` — framebuffer objects
- `VideoMemorySize=2048` — enough for DX9 textures

**Box64 preset HELIO_G88 (in `Box64PresetManager`):**
- `STRONGMEM=1` — prevent OOM on 8 GB LPDDR4x phones
- `CALLRET=1` — fast call/return on A75
- `BIGBLOCK=3` — bigger translated blocks, less interpreter overhead
- `FORWARD=256` — moderate forward jump optimization
- `SAFEFLAGS=2` — safe flags for shader-heavy games

**Environment (`Container.java`):**
- `MESA_GLTHREAD=true` — threaded GL dispatcher (uses all 8 cores)
- `GALLIUM_DRIVER=virpipe` — software virpipe
- `MESA_SHADER_CACHE_MAX_SIZE=512MB` — cache compiled shaders
- `WINEESYNC=1` — Linux eventfd-based sync (faster than esync)

### 4. Container default resolution 960x544

Halved from upstream's 1280x720 to fit the Mali-G52 shader budget. Games
that need 16:9 can override.

## What does NOT work on Helio

| Engine              | Reason                            | Workaround           |
|---------------------|-----------------------------------|----------------------|
| Unreal Engine 4+    | Needs DX11 SM5, 2 GB VRAM         | WineD3D 480x320      |
| Unity 2019+ DX11    | Compute shaders, SM5              | WineD3D 640x480      |
| Source Engine DX9   | Works but shader-heavy maps choke | WineD3D 800x600      |
| id Tech 4 (Doom 3)  | DX9c, works                       | WineD3D 800x600 30fps|
| Source 2006/2007    | DX8, perfect                      | WineD3D 1024x768 30+ |
| GoldSrc (Half-Life 1)| DX6, perfect                      | WineD3D 1024x768 60  |
| Anything DX12 only  | VKD3D, very limited               | VKD3D, mostly broken |
| Anything Vulkan-only| No path                           | None — does not work |

## The "Helio DX10-12 fix" in this fork

The user asked for a fix for "DX10-12 games that won't run on Helio."

Honest answer: **There is no real fix.** The only way to get DX10-12 games
running is to use WineD3D (DX10-11) or VKD3D (DX12), both of which will
run at <15 FPS on Mali-G52. The CPU budget (2x A75 @ 2 GHz) is the real
bottleneck before the GPU.

What this fork does provide:
1. WineD3D pre-configured and working out of the box
2. VKD3D available for the few DX12 titles that may load
3. Box64 HELIO_G88 preset tuned for the Big.LITTLE layout
4. Default 960x544 resolution to keep Mali from choking

This is the realistic best-case for a phone-class SoC. Anyone claiming
otherwise is overpromising.
