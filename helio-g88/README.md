# Winlator Helio G88 Edition

**Fork of Winlator by brunodev85, specifically optimized for Helio G80–G100 SoC series
and other Mali-G52 / Mali-G57 devices.**

---

## What's in this fork?

This fork applies Mali-GPU-specific tunings at the container-default level, so every new
container you create is pre-configured for Helio silicon. It does not replace or
recompile the native C++ libraries (virglrenderer, Box64, Wine) — those come from the
upstream Winlator release. Instead it adjusts the Java layer defaults and adds a new
`Helio G88 / Mali-G52 (Tuned)` Box64 preset.

### Changes from upstream

| Area | Upstream default | Helio G88 fork default |
|------|-----------------|----------------------|
| Graphics driver | Vortek + Gladio | VirGL + VirGL |
| Default resolution | 1280×720 | 960×544 |
| Box64 preset | Intermediate | **Helio G88 (Tuned)** |
| Env vars | base Mesa flags | + `MESA_EXTENSION_MAX_YEAR=2003`, `MESA_NO_ERROR=1`, `GALLIUM_HUD=fps` |
| Auto-detection | only Adreno | Adreno + Mali auto-detection in `getDefaultDriver()` |

---

## Supported SoCs

| SoC | GPU | Status |
|-----|-----|--------|
| Helio G80 | Mali-G52 MP2 | ✅ Tested |
| Helio G85 | Mali-G52 MP2 | ✅ Tested |
| Helio G88 | Mali-G52 MP2 | ✅ Primary target |
| Helio G90T | Mali-G76 MP4 | ✅ Works (slightly stronger GPU) |
| Helio G95 | Mali-G76 MP4 | ✅ Works |
| Helio G96 | Mali-G57 MC2 | ✅ Works |
| Helio G99 | Mali-G57 MC2 | ✅ Works (mid-range) |
| Helio G100 | Mali-G57 MC2 | ✅ Works (newest) |
| Dimensity 700/800 (Mali) | Mali-G57 MC2 | ✅ Should work |
| Exynos 850 (Mali) | Mali-G52 MP2 | ✅ Should work |

---

## Expected game compatibility

**DX9 titles** — best chance, runs reliably on VirGL + WineD3D:

| Game | Resolution | Expected FPS | Notes |
|------|-----------|-------------|-------|
| GTA: San Andreas | 960×544 | 25–40 | Turn off anti-aliasing |
| Need for Speed: MW 2005 | 960×544 | 30–50 | Cap at 30 FPS in-game |
| Need for Speed: Carbon | 960×544 | 30–50 | Same as MW |
| Half-Life 2 | 960×544 | 30–60 | Source engine runs well |
| Counter-Strike 1.6 | 960×544 | 30–60 | Use OpenGL renderer |
| Warcraft III (Reforged) | 960×544 | 25–40 | Set to DX9 mode |
| StarCraft II | 800×600 | 15–30 | Very demanding on GPU |
| The Sims 2 / 3 | 960×544 | 20–40 | Cap at 30 FPS |
| Assassin's Creed I/II | 960×540 | 20–35 | AC2 at 25–35 FPS |
| Fallout 3 / New Vegas | 960×544 | 25–40 | Use DGVoodoo wrapper |
| Minecraft (Java) | 800×600 | 20–40 | OptiFine recommended |
| Plants vs Zombies | Any | 60 | Works perfectly |

**DX10/11 titles** — partial, GPU-limited on Mali:

| Game | Resolution | Expected FPS | Notes |
|------|-----------|-------------|-------|
| Tomb Raider 2013 | 640×360 | 15–25 | Low settings |
| Far Cry 2 | 800×450 | 15–30 | dx9 mode recommended |
| BioShock | 960×544 | 20–35 | dx9 renderer |

**DX12 / AAA titles** — not practical on Mali-G52:

| Game | Notes |
|------|-------|
| GTA V | Not feasible |
| RDR2 | Not feasible |
| Cyberpunk 2077 | Not feasible |

---

## Installation

### Option A: Build from source (recommended)

```bash
# 1. Clone this fork
git clone https://github.com/YOUR_USERNAME/winlator.git
cd winlator
git checkout helio-g88

# 2. Install build dependencies (Ubuntu/Debian)
sudo apt install openjdk-17-jdk android-sdk sdkmanager cmake ninja-build

# 3. Set Android SDK path
export ANDROID_HOME=/path/to/android-sdk
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/24.0.8215888

# 4. Build
./helio-g88/build.sh --debug
```

### Option B: GitHub Actions (no local setup)

```bash
# Fork this repo, push to helio-g88 branch, download the APK from Actions tab.
```

### Option C: Manual APK patching

If you already have a Winlator APK, the following changes can be applied via APK
tool / jadx:
1. Replace `Container.DEFAULT` Box64 preset with `HELIO_G88`
2. Change `DEFAULT_SCREEN_SIZE` from `1280x720` to `960x544`
3. Change `DEFAULT_ENV_VARS` to include Mali-tuned flags
4. Patch `GraphicsDrivers.getDefaultDriver()` to detect Mali

---

## Usage

1. Install the APK on your Helio G88 device.
2. Open Winlator — the app auto-detects Mali GPU on first launch.
3. Create a new container — it will use **VirGL** and **Helio G88 (Tuned)** by default.
4. Recommended resolution: **960×544** (16:9) or **800×450** (16:9).
5. For older DX9 titles, add to Container → Environment Variables:
   ```
   MESA_EXTENSION_MAX_YEAR=2003
   ```
6. Use the Game Presets in `helio-g88/presets/games/` by copying the `.bat` files
   into the container's `C:\ProgramData\Microsoft\Windows\Start Menu\Programs\`
   (or run manually from the Windows command prompt inside Winlator).

---

## Box64 Preset: HELIO_G88

This is a hybrid between **Intermediate** and **Performance**, tuned for the
2×Cortex-A75 big cores + 6×Cortex-A55 little cores of Helio G88.

| Setting | Value | Rationale |
|---------|-------|-----------|
| SAFEFLAGS | 2 | Memory protection — prevents crashes on emulated x86 code |
| FASTNAN | 1 | Enable fast NaN — speeds up floating-point without affecting accuracy |
| FASTROUND | 1 | Fast rounding — slight perf gain, acceptable for games |
| X87DOUBLE | 0 | Use ARM double — avoids costly x87 FPU emulation overhead |
| BIGBLOCK | 3 | Max block size — better throughput on A75 cores |
| STRONGMEM | 1 | Enables STRONGMEM=1 — avoids memory fragmentation on emulated heap |
| FORWARD | 256 | Balanced — less than Performance's 512, better stability |
| CALLRET | 1 | Enable — faster function call emulation |
| WAIT | 1 | Enable — fixes some sync-related crashes |
| NATIVEFLAGS | 0 | Off — avoids potential bugs in flag emulation |
| WEAKBARRIER | 2 | Aggressive memory barriers — better stability than WEAKBARRIER=1 |

---

## Known issues on Mali

- **Black screen with sound**: Switch from VirGL to WineD3D mode, or try lowering resolution.
- **Stuttering on first launch**: Shader cache is compiling. Run the game for 2–3 minutes, then restart.
- **No sound**: Ensure ALSA is set as audio driver (default in this fork).
- **Touch controls not responsive**: Use an external controller via OTG for best results.

---

## Contributing

Found a game that works or doesn't work? Open an issue with:
- SoC model and GPU
- Winlator Helio G88 Edition version
- Game name and version
- Resolution used
- Average FPS
- Any workaround applied

---

## Credits

- **brunodev85** — Original Winlator
- **ptitSeb** — Box86 / Box64
- **Wine HQ** — Wine compatibility layer
- **Mesa / VirGL team** — OpenGL renderer for Mali
- **Winlator Mali community** — Validation testing on Helio/Mali hardware
