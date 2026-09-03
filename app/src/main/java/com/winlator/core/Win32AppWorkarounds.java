package com.winlator.core;

import com.winlator.XServerDisplayActivity;
import com.winlator.container.Container;
import com.winlator.container.DXWrappers;
import com.winlator.winhandler.OnPreExecListener;
import com.winlator.winhandler.WinEnums;
import com.winlator.winhandler.WinHandler;
import com.winlator.xserver.ScreenInfo;
import com.winlator.xserver.Window;

import java.io.File;
import java.util.Locale;

public class Win32AppWorkarounds implements OnPreExecListener {
    private final short taskAffinityMask;
    private final short taskAffinityMaskWoW64;
    private final XServerDisplayActivity activity;

    private interface Workaround {}

    private static class MultiWorkaround implements Workaround {
        private final Workaround[] list;

        public MultiWorkaround(Workaround... list) {
            this.list = list;
        }
    }

    private interface WindowWorkaround extends Workaround {
        void apply(Window window);
    }

    private interface EnvVarsWorkaround extends Workaround {
        void apply(EnvVars envVars);
    }

    private interface ScreenSizeWorkaround extends Workaround {
        String getValue();
    }

    private interface DXWrapperWorkaround extends Workaround {
        String getValue();
    }

    private interface WinComponentsWorkaround extends Workaround {
        void setValue(KeyValueSet wincomponents);
    }

    private interface FileManipulationWorkaround extends Workaround {
        boolean apply(String path);
    }

    public Win32AppWorkarounds(XServerDisplayActivity activity) {
        this.activity = activity;
        Container container = activity.getContainer();
        taskAffinityMask = (short)ProcessHelper.getAffinityMask(container.getCPUList(true));
        taskAffinityMaskWoW64 = (short)ProcessHelper.getAffinityMask(container.getCPUListWoW64(true));
        activity.getWinHandler().setOnPreExecListener(this);
    }

    private void applyWorkaround(Workaround workaround) {
        if (workaround instanceof EnvVarsWorkaround) {
            ((EnvVarsWorkaround)workaround).apply(activity.getOverrideEnvVars());
        }
        else if (workaround instanceof ScreenSizeWorkaround) {
            activity.setScreenInfo(new ScreenInfo(((ScreenSizeWorkaround)workaround).getValue()));
        }
        else if (workaround instanceof DXWrapperWorkaround) {
            activity.setDXWrapper(((DXWrapperWorkaround)workaround).getValue());
        }
        else if (workaround instanceof WinComponentsWorkaround) {
            KeyValueSet wincomponents = new KeyValueSet(Container.DEFAULT_WINCOMPONENTS);
            ((WinComponentsWorkaround)workaround).setValue(wincomponents);
            activity.setWinComponents(wincomponents.toString());
        }
    }

    public void applyStartupWorkarounds(String className) {
        Workaround workaround = getWorkaroundFor(className);
        if (workaround == null) return;

        if (workaround instanceof MultiWorkaround) {
            for (Workaround workaround2 : ((MultiWorkaround)workaround).list) applyWorkaround(workaround2);
        }
        else applyWorkaround(workaround);
    }

    private void setProcessAffinity(Window window, int processAffinity) {
        int processId = window.getProcessId();
        String className = window.getClassName();
        WinHandler winHandler = activity.getWinHandler();

        if (className.equals("steam.exe")) return;

        if (processId > 0) {
            winHandler.setProcessAffinity(processId, processAffinity);
        }
        else if (!className.isEmpty()) {
            winHandler.setProcessAffinity(window.getClassName(), processAffinity);
        }
    }

    public void applyWindowWorkarounds(Window window) {
        Workaround workaround = getWorkaroundFor(window.getClassName());
        if (workaround instanceof WindowWorkaround) {
            ((WindowWorkaround)workaround).apply(window);
        }
        else if (workaround instanceof MultiWorkaround) {
            for (Workaround workaround2 : ((MultiWorkaround) workaround).list) {
                if (workaround2 instanceof WindowWorkaround) {
                    ((WindowWorkaround)workaround2).apply(window);
                    break;
                }
            }
        }

        int windowGroup = window.getWMHintsValue(Window.WMHints.WINDOW_GROUP);
        boolean canApplyProcessAffinity = window.isRenderable() && !window.getClassName().isEmpty() && windowGroup == window.id;
        if (canApplyProcessAffinity) {
            int processAffinity = window.isWoW64() ? taskAffinityMaskWoW64 : taskAffinityMask;
            if (processAffinity != 0) setProcessAffinity(window, processAffinity);
        }
    }

    private Workaround getWorkaroundFor(String className) {
        String appIdentifier;
        if (className.startsWith("steam://")) {
            appIdentifier = className.substring(className.lastIndexOf("/") + 1);
        }
        else appIdentifier = className.toLowerCase(Locale.ENGLISH);
        final WinHandler winHandler = activity.getWinHandler();

        switch (appIdentifier) {
            case "sonicgenerations.exe":
            case "71340":
            case "valkyria.exe":
            case "294860":
                return (EnvVarsWorkaround) (envVars) -> envVars.put("WINEESYNC", "0");
            case "blacklist_game.exe":
            case "blacklist_dx11_game.exe":
                return (EnvVarsWorkaround) (envVars) -> envVars.put("WINEOVERRIDEAFFINITYMASK", taskAffinityMaskWoW64);
            case "fate.exe":
            case "psychotoxic.exe":
                return (ScreenSizeWorkaround) () -> "1024x768";
            case "ffxii_tza.exe":
                ScreenInfo screenInfo = activity.getScreenInfo();
                return (ScreenSizeWorkaround) () -> (screenInfo.width+4)+"x"+(screenInfo.height+4);
            case "chronocross_launcher.exe":
                return (WindowWorkaround) (window) -> {
                    window.attributes.setTransparent(true);
                    AppUtils.runDelayed(() -> {
                        winHandler.showWindow(window.getHandle(), WinEnums.SW_MINIMIZE);
                        winHandler.showWindow(window.getHandle(), WinEnums.SW_RESTORE);
                    }, 500);
                };
            case "dino.exe":
            case "dino2.exe":
            case "bof4.exe":
                return (WinComponentsWorkaround) (wincomponents) -> wincomponents.put("directshow", "1");
            case "discipl2.exe":
                return (DXWrapperWorkaround) () -> DXWrappers.WINED3D;
            case "cnc3.exe":
                return (FileManipulationWorkaround) (path) -> {
                    File executableDir = getExecutableDir(path);
                    File oldFile = new File(executableDir, "CNC3_english_1.10.SkuDef");
                    if (oldFile.isFile()) oldFile.renameTo(new File(executableDir, "CNC3_english_1.10.SkuDef.old"));
                    return false;
                };
            case "start.exe":
                return (WindowWorkaround) (window) -> {
                    if (!window.getName().contains("Easy Anti-Cheat Launch Error")) return;
                    runAnother(window, "bin/DBXV2.exe");
                };
            case "ff9_launcher.exe":
                return (WindowWorkaround) (window) -> AppUtils.runDelayed(() -> winHandler.bringToFront(window.getClassName(), window.getHandle()), 1000);
            case "launcher.exe":
                return (FileManipulationWorkaround) this::runNoLauncher;

            // ============================================================
            // Helio G88 / Mali-G52 Per-Game Workarounds
            // Priority: force DX9 (WineD3D+OpenGL) for games that default to
            // DX10/11 on Mali, and cap resolution for GPU-limited titles.
            // ============================================================

            // Counter-Strike 1.6 — use OpenGL renderer, disable shader cache
            case "hl.exe":
            case "cstrike.exe":
            case "czero.exe":
                return (EnvVarsWorkaround) (envVars) -> {
                    envVars.put("RAD_VIDEO", "gl");
                    envVars.put("MESA_SHADER_CACHE_DISABLE", "true");
                };

            // GTA: San Andreas — cap at 960x544, disable fog for Mali fillrate
            case "gta_sa.exe":
                return (MultiWorkaround) new ScreenSizeWorkaround() {
                    public String getValue() { return "960x544"; }
                },
                new EnvVarsWorkaround() {
                    public void apply(EnvVars envVars) {
                        envVars.put("__GL_THREADED_OPTIMIZATION", "1");
                    }
                };

            // Assassin's Creed series — DX9 mode only, lower res for Mali
            case "assassinscreed.exe":
            case "assassinscreed2.exe":
            case "assassinscreed_brotherhood.exe":
            case "assassinscreed_revelations.exe":
            case "AC2Patch_pc.exe":
                return (MultiWorkaround) new ScreenSizeWorkaround() {
                    public String getValue() { return "960x540"; }
                },
                new EnvVarsWorkaround() {
                    public void apply(EnvVars envVars) {
                        // Force DX9 renderer via registry key before launch
                        envVars.put("__GL_THREADED_OPTIMIZATION", "1");
                    }
                };

            // Need for Speed: Most Wanted (2005) — default 960x544 is fine, ensure WineD3D
            case "speed.exe":
            case "nfsms.exe":
                return (DXWrapperWorkaround) () -> DXWrappers.WINED3D;

            // Need for Speed: Carbon — same as MW
            case "carbon.exe":
            case "nfsc.exe":
                return (DXWrapperWorkaround) () -> DXWrappers.WINED3D;

            // Half-Life 2 — Source engine; use OpenGL, cap shader compile
            case "hl2.exe":
            case "episodicepisode01.exe":
                return (EnvVarsWorkaround) (envVars) -> {
                    envVars.put("MESA_SHADER_CACHE_DISABLE", "true");
                    envVars.put("__GL_THREADED_OPTIMIZATION", "1");
                };

            // Warcraft III: Reforged — force DX9 mode, not Reforged (heavier)
            case "war3.exe":
                return (MultiWorkaround) new ScreenSizeWorkaround() {
                    public String getValue() { return "960x544"; }
                },
                new EnvVarsWorkaround() {
                    public void apply(EnvVars envVars) {
                        // DX9 mode via launch flag handled by Wine; ensure OpenGL path
                        envVars.put("MESA_SHADER_CACHE_DISABLE", "true");
                    }
                };

            // Minecraft (Java) — OptiFine recommended; cap render distance
            case "javaw.exe":
                return (EnvVarsWorkaround) (envVars) -> {
                    envVars.put("MESA_SHADER_CACHE_DISABLE", "true");
                    envVars.put("__GL_THREADED_OPTIMIZATION", "1");
                    // -Dfml.earlyProgressWindow=false handled by user in launcher profile
                };

            // StarCraft II — very demanding; cap at 800x600 minimum viable
            case "sc2.exe":
                return (MultiWorkaround) new ScreenSizeWorkaround() {
                    public String getValue() { return "800x600"; }
                },
                new EnvVarsWorkaround() {
                    public void apply(EnvVars envVars) {
                        envVars.put("MESA_SHADER_CACHE_DISABLE", "true");
                    }
                };

            // Tomb Raider 2013 — DX9 mode recommended, low res for Mali
            case "tomb Raider 2013.exe":
                return (MultiWorkaround) new ScreenSizeWorkaround() {
                    public String getValue() { return "640x360"; }
                },
                new DXWrapperWorkaround() {
                    public String getValue() { return DXWrappers.WINED3D; }
                };

            default:
                return null;
        }
    }

    private File getExecutableDir(String dosPath) {
        return new File(FileUtils.getDirname(WineUtils.dosToUnixPath(dosPath, activity.getContainer())));
    }

    private boolean runNoLauncher(String path) {
        final String[] relativePaths = {"BorderlandsPreSequel.exe"};

        File executableDir = getExecutableDir(path);
        for (String relativePath : relativePaths) {
            if ((new File(executableDir, relativePath)).isFile()) {
                final WinHandler winHandler = activity.getWinHandler();
                AppUtils.runDelayed(() -> winHandler.exec(path.replace(FileUtils.getName(path), relativePath.replace("/", "\\")), null), 500);
                return true;
            }
        }
        return false;
    }

    private void runAnother(Window window, String relativePath) {
        WinHandler winHandler = activity.getWinHandler();
        String path = winHandler.getExecutablePath(window.getProcessId());
        if ((new File(getExecutableDir(path), relativePath)).isFile()) {
            winHandler.killProcess(null, window.getProcessId());
            winHandler.exec(path.replace(FileUtils.getName(path), relativePath.replace("/", "\\")), null);
        }
    }

    @Override
    public boolean onPreExec(String path) {
        String className = FileUtils.getName(path);
        Workaround workaround = getWorkaroundFor(className);

        if (workaround instanceof FileManipulationWorkaround) {
            return ((FileManipulationWorkaround)workaround).apply(path);
        }
        else return false;
    }
}