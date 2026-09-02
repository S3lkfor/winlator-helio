# Winlator Helio G88 - G100 Optimization Guide

## Hardware Specifications & Architecture Overview
The MediaTek Helio G88 (MT6769H) is a popular budget SoC found in devices such as the Xiaomi Redmi 10, Redmi 12, Realme C55, Tecno Spark 10 Pro, and Lenovo Tab M11.

### Chip Architecture Breakdown:
- **CPU**: Octa-core (2× ARM Cortex-A75 @ 2.0 GHz + 6× ARM Cortex-A55 @ 1.8 GHz)
- **GPU**: ARM Mali-G52 MP2 (2 execution engines, 24/32 shading units, 1000 MHz clock)
- **Instruction Set**: ARMv8.2-A (64-bit)
- **Vulkan / OpenGL ES Support**: Vulkan 1.3 / GLES 3.2
- **RAM Bottleneck**: Most Helio G88 devices ship with 4GB - 8GB LPDDR4x (1800 MHz, ~14.4 GB/s bandwidth).

---

## Technical Bottlenecks on Helio G88 / Mali-G52
1. **GPU Fill-rate & Shader Throughput**: The Mali-G52 MP2 produces ~96 GFLOPS. Rendering at native 1080p (2400×1080) causes severe fill-rate bottlenecks in 3D titles.
2. **Lack of Proprietary Vulkan Drivers (No Turnip)**: Qualcomm Adreno GPUs benefit from the open-source Mesa Turnip driver, allowing DXVK to achieve near-native performance. Mali GPUs cannot run Turnip and must rely on **VirGL** or **WineD3D** via OpenGL ES translation.
3. **Big.LITTLE Thread Affinity**: Box64 dynamic recompiler threads must be scheduled on the 2× Cortex-A75 performance cores, leaving the 6× Cortex-A55 efficiency cores for background Wine/system worker threads.

---

## Key Tuning Modifications Applied in This Fork

### 1. Default Renderer: VirGL (Universal Mali Driver)
- In standard Winlator, default settings attempt to initialize Turnip or Vortek Vulkan drivers, resulting in instant crashes or black screens on Mali GPUs.
- This fork detects `Mali` or `Bifrost` in `GPUHelper.glGetRenderer()` and automatically defaults both Vulkan and OpenGL slots to `VirGL`.

### 2. Resolution Scaling (960×544 & 800×450)
- Container defaults are changed from 1280×720 to **960×544** (exact 16:9 1/4th 1080p scale).
- Reduces Mali-G52 pixel fill load by **43%** compared to 720p, granting dramatic FPS improvements in DirectX 9 games.

### 3. Customized Box64 Preset (`HELIO_G88`)
Optimized Box64 environment flags specifically configured for Cortex-A75 big cores:
```bash
BOX64_DYNAREC_SAFEFLAGS=2     # Maintain safe flag handling for x86 accuracy
BOX64_DYNAREC_FASTNAN=1       # Skip IEEE-754 NaN normalization where safe
BOX64_DYNAREC_FASTROUND=1     # Fast float-to-int rounding mode
BOX64_DYNAREC_X87DOUBLE=0     # Disable 80-bit double precision emulation
BOX64_DYNAREC_BIGBLOCK=3      # Maximize block size for Cortex-A75 cache line
BOX64_DYNAREC_STRONGMEM=1     # Medium memory barrier protection
BOX64_DYNAREC_FORWARD=256     # Forward jump cache depth
BOX64_DYNAREC_CALLRET=1       # Enable CALL/RET optimization
BOX64_DYNAREC_WAIT=1          # Wait loop detection
BOX64_DYNAREC_WEAKBARRIER=2   # Optimized weak memory barrier for ARMv8
```

### 4. DirectDraw / Mesa Legacy Environment Flags
Pre-configured in container default environment variables:
- `MESA_EXTENSION_MAX_YEAR=2003`: Fixes legacy DirectX 7/8/9 games (e.g., NFS Underground, GTA III, Half-Life 1) that fail extensions checks on modern OpenGL ES layers.
- `MESA_NO_ERROR=1`: Skips redundant OpenGL error checking in VirGL driver context.
- `GALLIUM_HUD=fps`: Built-in low-overhead hardware FPS overlay.

---

## Playable Game Tier List on Helio G88 / G85 / G96

| Game Title | Year | Target Res | Expected FPS | Recommended Settings |
|------------|------|------------|--------------|----------------------|
| **GTA: San Andreas (PC)** | 2004 | 960×544 | 35 – 50 FPS | VirGL + WineD3D, Medium Graphics |
| **Need for Speed: Most Wanted** | 2005 | 800×450 | 30 – 45 FPS | VirGL + WineD3D, Visual Effects Low |
| **Half-Life 2 / Portal** | 2004 | 960×544 | 30 – 40 FPS | DX9 Low, VirGL |
| **Shovel Knight: Treasure Trove** | 2014 | 1280×720 | 60 FPS | Native / WineD3D |
| **Fallout 3 / New Vegas** | 2008 | 800×450 | 20 – 30 FPS | Low Settings, Fallout_NewVegas.bat |
| **Elder Scrolls III: Morrowind** | 2002 | 960×544 | 40 – 60 FPS | VirGL + WineD3D, Distant Land Off |
| **Devil May Cry 3 (Special Edition)** | 2006 | 800×450 | 30 – 45 FPS | Low Graphics |

---

## Installation & First-Time Setup
1. Download the compiled `Winlator-Helio-G88-Debug.apk` (or Release APK from GitHub Actions).
2. Install the APK on your MediaTek device (e.g., Redmi 10/12, Realme C55).
3. Open Winlator. First launch will unpack system files into the internal container storage.
4. Tap `+` to create a container. Notice that:
   - Screen Size is automatically set to `960x544`.
   - Graphics Driver is pre-selected as `VirGL`.
   - Box64 Preset is pre-selected as `Helio G88 / Mali-G52 (Tuned)`.
5. Launch container and run your game or shortcut!
