# Helio DX Mode Compatibility (Vortek Edition)

**Last updated:** 2026-09-03
**Fork:** winlator-helio v3 (Vortek + WineD3D Vulkan renderer)
**Target device:** Infinix HOT 30 (X6831), XOS 12.6, Android 13, Helio G88, Mali-G52 MC2

---

## Executive Summary

Mali-G52 MP2 has **no BCn hardware decoder** and only **2 shader cores**. This is a hardware fact that no software can change. However, with the right graphics stack — Vortek + WineD3D Vulkan renderer + MESA env overrides — DX9 and DX10-11 games **are** playable on Helio G88 at 10-40 FPS. DX12 is technically possible but not practical (2-8 FPS).

The previous version of this document recommended WineD3D + VirGL as the only working path. That was incorrect. **Vortek** — which has been bundled in upstream Winlator 10.0+ but never used for Mali — is the actual solution.

---

## Why Vortek Matters

Vortek is a Vulkan ICD (Installable Client Driver) that sits between the game and the Mali GPU. It does three critical things:

1. **CPU-decompresses BCn textures JIT** — every DX10-12 game stores textures in BC1/BC3/BC5/BC7 compressed format. Mali has no hardware decoder. Vortek intercepts `vkCreateImage` calls and decompresses on the CPU. This is the fundamental reason DX10+ failed on Mali before Vortek.

2. **Patches SPIR-V shaders** — removes features Mali doesn't support (`gl_ClipDistance`, some `_USCALED`/`_SSCALED` vertex formats). Without this, DXVK's compiled shaders fail Vulkan validation.

3. **Injects WSI extensions** — adds surface presentation extensions Mali's stock driver doesn't expose. Without this, no window is created and the game has no display.

Reference: [leegao/winlator-internals](https://leegao.github.io/winlator-internals/2025/06/02/Vortek2.html) — full reverse engineering of the Vortek architecture.

---

## The 4 Working Configurations

### Config A: VirGL + WineD3D (OpenGL renderer)
**Target:** DX9 games
**Expected FPS:** 25-40 at 960×544

```
Graphics Driver:   VirGL
DX Wrapper:        WineD3D
WineD3D Renderer:  gl (default OpenGL)
```

Path: `DX9 → WineD3D (gl) → OpenGL → VirGL → host Mesa → Mali real GPU`

**Best for:** NFS Most Wanted 2005, GTA San Andreas, GTA Vice City, Half-Life 2, PES 2013, FIFA 07-12.

### Config B: Vortek + WineD3D (Vulkan renderer) — RECOMMENDED
**Target:** DX9 + DX10-11 games
**Expected FPS:** 15-35 at 960×544

```
Graphics Driver:   Vortek
DX Wrapper:        WineD3D
WineD3D Renderer:  vulkan (auto-set by fork)
```

Path: `DX → WineD3D (vulkan) → Vulkan → Vortek (BCn decode + shader patches) → Mali real GPU`

This is the **fork's default** for Helio. Auto-set when you create a container.

**Best for:** NFS Shift, BioShock 2, Dead Space, Far Cry 2, Dirt 2, Source engine games with Vulkan renderer.

### Config C: Vortek + DXVK
**Target:** DX10-11 with more aggressive DXVK optimizations
**Expected FPS:** 20-40 at 960×544 (slightly faster than B for compatible games)

```
Graphics Driver:   Vortek
DX Wrapper:        DXVK
```

Path: `DX → DXVK → Vulkan → Vortek → Mali`

**Trade-off:** Less compatible than WineD3D Vulkan path, but faster when it works. Use as opt-in for specific games.

### Config D: Vortek + VKD3D
**Target:** DX12 games (experimental)
**Expected FPS:** 2-8

```
Graphics Driver:   Vortek
DX Wrapper:        VKD3D
```

**Not recommended.** VKD3D's CPU overhead + Vortek's BCn decode on a 2-core Mali = unplayable for most DX12.

---

## What the Fork Does Automatically

When you create a new container on Helio:

1. **Graphics Driver = Vortek,VirGL** (Vortek primary, VirGL fallback)
2. **DX Wrapper = WineD3D** (most compatible)
3. **WineD3D Registry: `renderer = vulkan`** (set in `ContainerDetailFragment.java`)
4. **Env vars pre-configured:**
   - `MESA_GLSL_VERSION_OVERRIDE=410`
   - `MESA_GL_VERSION_OVERRIDE=4.1COMPAT`
   - `mesa_glthread=false` (single-threaded Mesa, more stable on Helio)
   - `BOX64_MMAP32=1` (Wine 32-bit address layout)
   - `BOX64_DYNAREC_SAFEFLAGS=2` (correct for SEH)
   - `STRONGMEM=1` (shared LPDDR4x memory)
5. **Box64 Preset = HELIO_G88** (Big.LITTLE affinity to A55 cluster, cores 2-7)

---

## What You Need To Do (After Installing New APK)

1. **Uninstall old Winlator-Helio** (different signing key = "app not installed" error)
2. **Install new APK** from https://github.com/S3lkfor/winlator-helio/actions
3. **Settings → Apps → Winlator-Helio → Battery → Don't optimize** (CRITICAL on XOS 12.6 — XOS kills background processes aggressively)
4. **Open app, create new container** (defaults are now Helio-optimized)
5. **Add games, run, report back**

If the container still hangs "Starting up":
- Open recent apps (swipe up), swipe Winlator away, reopen
- This is the XOS task-killer workaround — it forces XOS to re-prioritize Winlator as foreground

---

## The XOS 12.6 "Starting Up" Hang — Diagnosis

The "Starting up" hang on Infinix HOT 30 is caused by:

**Primary:** XOS 12.6's aggressive background task killer. Winlator's container takes 60-100 seconds to initialize. XOS sees a long-running background process and kills it. Fix: battery optimization exclusion.

**Secondary (now fixed by this update):** Previous APK had `graphicsDriver = VirGL,VirGL` and no `WineD3D renderer=vulkan` registry key. VirGL provides no Vulkan ICD, so WineD3D Vulkan init fails silently. Fix: Vortek default + auto-set Vulkan renderer.

---

## Why the Previous Version Was Wrong

The earlier `helio-g88/docs/HELIO_DX_MODES.md` claimed:
- "DXVK requires a real Vulkan ICD that maps to GPU hardware — VirGL provides no such ICD, so DXVK will not work"
- "WineD3D + VirGL is the only working path"

This is true for **plain VirGL**, but ignores that Vortek is already in the upstream Winlator APK. Vortek is a Vulkan ICD that maps DXVK/WineD3D's Vulkan calls to Mali GPU hardware. It's just that nobody had wired it as the default for Mali.

This update wires Vortek as the Mali default. The result: DX10-11 works on Mali with a real Vulkan path, not the degraded OpenGL-through-VirGL fallback.

---

## Research Repository

Full deep-dive research (1,200+ lines across 8 documents) is at:
https://github.com/S3lkfor/mali-dx-research

Includes: Vortek internals, Box64 Big.LITTLE analysis, benchmark tables, game compatibility tiers, and the brutal honest answer.
