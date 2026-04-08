package com.commander4j.launchpad;

/*******************************************************************************
 * Title:        Commander4j
 * Description:  macOS App Bundle Utilities (display name + icon; resolve-on-add; disk+memory cache)
 * Author:       Dave (with ChatGPT assistance)
 * License:      GNU General Public License
 *******************************************************************************/

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.filechooser.FileSystemView;

import com.dd.plist.NSArray;
import com.dd.plist.NSDictionary;
import com.dd.plist.NSObject;
import com.dd.plist.PropertyListParser;

public class MacAppUtils {

    /* ===================== Sizing ===================== */
    // Keep these aligned with LaunchTabPanel/app cell sizing.
    public static final int CELL_SIZE       = 150;  // visual cell size
    public static final int ICON_RENDER_SIZE = 120; // actual icon pixels drawn inside the cell (gives a border)

    /* ===================== Last used chooser dir ===================== */
    private static File LAST_CHOOSER_DIR = new File(System.getProperty("user.home"));

    public static File getLastChooserDir() {
        return (LAST_CHOOSER_DIR != null && LAST_CHOOSER_DIR.isDirectory())
            ? LAST_CHOOSER_DIR
            : new File(System.getProperty("user.home"));
    }

    public static void setLastChooserDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            LAST_CHOOSER_DIR = dir;
        }
    }

    /* ===================== In-memory + on-disk cache ===================== */
    private static final Map<String, ImageIcon> ICON_CACHE = new ConcurrentHashMap<>();
    private static final Path DISK_CACHE_DIR = Paths.get("./images/appIcons");

    private static void ensureDiskCacheDir() {
        try { Files.createDirectories(DISK_CACHE_DIR); } catch (IOException ignore) {}
    }

    private static String canonical(Path p) {
        try { return p.toFile().getCanonicalPath(); } catch (Exception e) { return p.toAbsolutePath().toString(); }
    }

    @SuppressWarnings("unused")
    private static String sha1(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return Integer.toHexString(s.hashCode()); }
    }

    /** Base filename used for cache: <BundleName>.png */
    private static String bundlePngName(Path bundle) {
        String base = bundle.getFileName().toString();
        if (base.toLowerCase(Locale.ROOT).endsWith(".app")) base = base.substring(0, base.length() - 4);
        return base + ".png";
    }

    /** Disk cache path: ./images/appIcons/<BundleName>.png */
    private static Path iconCacheFile(Path bundle) {
        ensureDiskCacheDir();
        return DISK_CACHE_DIR.resolve(bundlePngName(bundle));
    }

    /** True if cached PNG is newer than Info.plist / Assets.car / Resources dir. */
    private static boolean diskIconFresh(Path bundle, Path iconPng) {
        try {
            if (!Files.exists(iconPng)) return false;
            long iconTime = Files.getLastModifiedTime(iconPng).toMillis();

            Path infoPlist = bundle.resolve("Contents/Info.plist");
            if (Files.exists(infoPlist) && iconTime < Files.getLastModifiedTime(infoPlist).toMillis()) return false;

            Path assets = bundle.resolve("Contents/Resources/Assets.car");
            if (Files.exists(assets) && iconTime < Files.getLastModifiedTime(assets).toMillis()) return false;

            Path resources = bundle.resolve("Contents/Resources");
            if (Files.exists(resources) && iconTime < Files.getLastModifiedTime(resources).toMillis()) return false;

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static ImageIcon loadIconFromDisk(Path bundle) {
        Path png = iconCacheFile(bundle);
        if (!diskIconFresh(bundle, png)) return null;
        try (InputStream in = Files.newInputStream(png)) {
            BufferedImage bi = ImageIO.read(in);
            if (bi == null) return null;
            // Reject blank icons cached before the visibility-check code was added.
            // This triggers a one-time re-resolution via NSWorkspace for affected apps.
            if (!hasVisibleContent(bi)) { Files.deleteIfExists(png); return null; }
            return new ImageIcon(bi);
        } catch (Exception e) {
            return null;
        }
    }

    /** Save image to disk cache as <BundleName>.png. Returns the final path or null on failure. */
    private static Path saveIconToDisk(Path bundle, BufferedImage bi) {
        ensureDiskCacheDir();
        Path out = iconCacheFile(bundle);
        try {
            Path tmp = Files.createTempFile(DISK_CACHE_DIR, "ico-", ".png");
            ImageIO.write(bi, "PNG", tmp.toFile());
            Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return out;
        } catch (Exception ignore) {
            return null;
        }
    }

    public static void clearIconCache() {
        ICON_CACHE.clear();
        try {
            ensureDiskCacheDir();
            try (var s = Files.list(DISK_CACHE_DIR)) {
                s.forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignore) {} });
            }
        } catch (Exception ignore) {}
    }

    public static void evictIcon(File bundle) {
        if (bundle != null) {
            String memKey = canonical(bundle.toPath()) + "|" + ICON_RENDER_SIZE;
            ICON_CACHE.remove(memKey);
            try { Files.deleteIfExists(iconCacheFile(bundle.toPath())); } catch (Exception ignore) {}
        }
    }

    /* ===================== Public helpers for custom icon assignment ===================== */

    /** Return the on-disk cached PNG path for a bundle (./images/appIcons/<BundleName>.png). */
    public static String getCachedIconPathForBundle(File bundle) {
        return iconCacheFile(bundle.toPath()).toString();
    }

    /** Copy+resize a user-chosen image (or ICNS) into the cache as <BundleName>.png, then return it as an ImageIcon. */
    public static ImageIcon loadAndCacheCustomIcon(File bundle, File srcImage, int renderSize) throws IOException {
        if (bundle == null || srcImage == null) return null;

        ensureDiskCacheDir();

        BufferedImage src;
        String name = srcImage.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".icns")) {
            int decodeSize = snapDecodeSize(Math.max(renderSize, CELL_SIZE));
            src = ICNSUtils.loadBestImage(srcImage, decodeSize);
            if (src == null) throw new IOException("No readable image in ICNS file.");
        } else {
            src = ImageIO.read(srcImage);
            if (src == null) throw new IOException("Unsupported or unreadable image format.");
        }

        BufferedImage scaled = scaleToSquare(src, renderSize);

        Path out = saveIconToDisk(bundle.toPath(), scaled);
        if (out == null) throw new IOException("Failed saving icon to cache.");

        // update memory cache too
        String memKey = canonical(bundle.toPath()) + "|" + renderSize;
        ICON_CACHE.put(memKey, new ImageIcon(scaled));

        return new ImageIcon(scaled);
    }

    /**
     * Force-refreshes the icon for the given bundle: evicts both caches, re-resolves using the
     * full strategy (ICNS → iOS PNGs → NSWorkspace → QuickLook → system icon), saves the result
     * to the disk cache, and returns the new ImageIcon.  Returns null only if all strategies fail.
     *
     * Safe to call from a background thread; does NOT touch Swing components.
     */
    public static ImageIcon refreshIcon(File bundle) {
        if (bundle == null || !bundle.exists()) return null;
        // NOTE: do NOT evict caches here. If resolution fails we must leave the existing
        // cached icon intact so the app continues to display something on next load.
        try {
            Path bpath = bundle.toPath();
            Path infoPlist = bpath.resolve("Contents/Info.plist");

            // iOS wrapper apps have no Contents/Info.plist; NSWorkspace is the only option.
            if (!Files.exists(infoPlist)) {
                if (!isIosWrapperBundle(bpath)) return null;
                ImageIcon icon = tryNSWorkspaceIcon(bpath, ICON_RENDER_SIZE);
                if (icon == null) return null;
                String memKey = canonical(bpath) + "|" + ICON_RENDER_SIZE;
                ICON_CACHE.put(memKey, icon);
                BufferedImage bi = iconToBuffered(icon);
                if (bi != null && bi.getWidth() > 0) saveIconToDisk(bpath, bi);
                return icon;
            }

            NSDictionary root = (NSDictionary) PropertyListParser.parse(infoPlist.toFile());

            // resolveIconAtAddTime goes straight to the resolution strategies; it does not
            // read from the memory or disk cache, so no eviction is needed before calling it.
            ImageIcon icon = resolveIconAtAddTime(bpath, root, ICON_RENDER_SIZE);
            if (icon == null) return null;   // leave existing caches untouched

            // Resolution succeeded: now replace both caches with the fresh result.
            String memKey = canonical(bpath) + "|" + ICON_RENDER_SIZE;
            ICON_CACHE.put(memKey, icon);

            BufferedImage bi = iconToBuffered(icon);
            if (bi != null && bi.getWidth() > 0) saveIconToDisk(bpath, bi);

            return icon;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /* ===================== Main API ===================== */

    /** Creates an AppComponent with display name + icon.
     *  Icon is resolved synchronously now (at add-time), then cached to memory+disk.
     *  The returned component has customIconPath set when a disk icon exists. */
    public static AppComponent createAppComponent(File bundle) {
        if (bundle == null || !bundle.exists()) return null;

        try {
            Path bpath = bundle.toPath();
            Path infoPlist = bpath.resolve("Contents/Info.plist");

            // ==== iOS wrapper bundle (Wrapper/<Name>.app): no Contents/Info.plist ====
            if (!Files.exists(infoPlist)) {
                // Try to get the display name from the inner bundle's Info.plist
                String displayName = stripAppExtension(bundle.getName());
                try {
                    try (var s = Files.list(bpath.resolve("Wrapper"))) {
                        Path innerApp = s.filter(p -> p.getFileName().toString().endsWith(".app")).findFirst().orElse(null);
                        if (innerApp != null) {
                            Path innerPlist = innerApp.resolve("Info.plist");
                            if (Files.exists(innerPlist)) {
                                NSDictionary innerRoot = (NSDictionary) PropertyListParser.parse(innerPlist.toFile());
                                if (innerRoot.containsKey("CFBundleDisplayName"))
                                    displayName = innerRoot.objectForKey("CFBundleDisplayName").toString();
                                else if (innerRoot.containsKey("CFBundleName"))
                                    displayName = innerRoot.objectForKey("CFBundleName").toString();
                            }
                        }
                    }
                } catch (Exception ignore) {}

                String memKey = canonical(bpath) + "|" + ICON_RENDER_SIZE;

                // 1) memory cache
                ImageIcon icon = ICON_CACHE.get(memKey);

                // 2) disk cache (valid cached icon from a previous run)
                if (icon == null) {
                    Path png = iconCacheFile(bpath);
                    if (Files.exists(png)) {
                        try (InputStream in = Files.newInputStream(png)) {
                            BufferedImage bi = ImageIO.read(in);
                            if (bi != null && hasVisibleContent(bi)) icon = new ImageIcon(bi);
                        } catch (Exception ignore) {}
                    }
                }

                // 3) NSWorkspace — the only reliable source for iOS wrapper apps
                if (icon == null) {
                    icon = tryNSWorkspaceIcon(bpath, ICON_RENDER_SIZE);
                    if (icon != null) {
                        ICON_CACHE.put(memKey, icon);
                        BufferedImage bi = iconToBuffered(icon);
                        if (bi != null && bi.getWidth() > 0) saveIconToDisk(bpath, bi);
                    }
                }

                // 4) final fallback: blank placeholder
                if (icon == null) icon = new ImageIcon();

                AppComponent comp = new AppComponent(bundle, displayName, icon);
                Path pngPath = iconCacheFile(bpath);
                if (Files.exists(pngPath)) comp.setCustomIconPath(pngPath.toString());
                return comp;
            }
            // ==== END iOS wrapper handling ==== //

            NSDictionary root = (NSDictionary) PropertyListParser.parse(infoPlist.toFile());

            // ---- Display Name ----
            String displayName = bundle.getName();
            if (root.containsKey("CFBundleDisplayName")) {
                displayName = root.objectForKey("CFBundleDisplayName").toString();
            } else if (root.containsKey("CFBundleName")) {
                displayName = root.objectForKey("CFBundleName").toString();
            } else {
                displayName = stripAppExtension(displayName);
            }

            String memKey = canonical(bpath) + "|" + ICON_RENDER_SIZE;

            // ---- Memory cache ----
            ImageIcon icon = ICON_CACHE.get(memKey);

            // ---- Disk cache ----
            if (icon == null) {
                ImageIcon fromDisk = loadIconFromDisk(bpath);
                if (fromDisk != null) {
                    icon = fromDisk;
                }
            }

            // ---- Resolve now if needed ----
            if (icon == null) {
                icon = resolveIconAtAddTime(bpath, root, ICON_RENDER_SIZE);
                if (icon == null) icon = new ImageIcon(); // placeholder

                ICON_CACHE.put(memKey, icon);

                BufferedImage bi = iconToBuffered(icon);
                if (bi != null && bi.getWidth() > 0) {
                    saveIconToDisk(bpath, bi);
                }
            }

            // Build component and remember disk path if exists.
            AppComponent comp = new AppComponent(bundle, displayName, icon);
            Path png = iconCacheFile(bpath);
            if (Files.exists(png)) {
                comp.setCustomIconPath(png.toString());
            }
            return comp;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /* ===================== Resolve-at-add-time strategy ===================== */

    // Order: NSWorkspace (Assets.car apps) → .icns → iOS PNGs → NSWorkspace (non-Assets.car) → Quick Look → System icon
    private static ImageIcon resolveIconAtAddTime(Path bundle, NSDictionary root, int renderSize) {
        // 1) For apps with Assets.car, NSWorkspace is authoritative: it applies the proper macOS
        //    icon rendering, including the rounded-rectangle treatment for iOS-on-Mac apps.
        //    ICNS files in these bundles are often raw/unstyled stubs.
        if (hasAssetsCar(bundle)) {
            ImageIcon nsw = tryNSWorkspaceIcon(bundle, renderSize);
            if (nsw != null && nsw.getIconWidth() > 0) return nsw;
        }

        // 2) Classic .icns – skip transparent stubs (some system apps ship blank ICNS placeholders)
        BufferedImage icns = tryIcnsImage(bundle, root, renderSize);
        if (icns != null && hasVisibleContent(icns)) return new ImageIcon(icns);

        // 3) iOS PNG list (CFBundleIcons)
        BufferedImage ios = tryIosPngImage(bundle, root, renderSize);
        if (ios != null && hasVisibleContent(ios)) return new ImageIcon(ios);

        // 4) NSWorkspace fallback for apps without Assets.car
        if (!hasAssetsCar(bundle)) {
            ImageIcon nsw = tryNSWorkspaceIcon(bundle, renderSize);
            if (nsw != null && nsw.getIconWidth() > 0) return nsw;
        }

        // 5) Quick Look as additional fallback
        if (hasAssetsCar(bundle)) {
            ImageIcon ql = tryQuickLookAppIcon(bundle, renderSize, /*timeoutMs*/ 2500);
            if (ql != null && ql.getIconWidth() > 0) return ql;
        }

        // 6) Last resort: system icon (may look like a folder)
        BufferedImage sys = trySystemIconImage(bundle, renderSize);
        if (sys != null) return new ImageIcon(sys);

        return null;
    }

    private static boolean hasAssetsCar(Path bundle) {
        return Files.exists(bundle.resolve("Contents/Resources/Assets.car"));
    }

    /* ---------- .icns ---------- */
    private static BufferedImage tryIcnsImage(Path bundle, NSDictionary root, int renderSize) {
        try {
            // Prefer CFBundleIconFile; fall back to CFBundleIconName (used by modern apps like BBEdit)
            String iconName = root.containsKey("CFBundleIconFile")
                    ? root.objectForKey("CFBundleIconFile").toString()
                    : root.containsKey("CFBundleIconName")
                    ? root.objectForKey("CFBundleIconName").toString()
                    : null;
            if (iconName == null) return null;
            if (!iconName.toLowerCase(Locale.ROOT).endsWith(".icns")) iconName += ".icns";
            Path icnsPath = bundle.resolve("Contents/Resources").resolve(iconName);
            if (!Files.exists(icnsPath)) return null;

            int decode = snapDecodeSize(renderSize);
            BufferedImage best = ICNSUtils.loadBestImage(icnsPath.toFile(), decode);
            return (best != null) ? scaleToSquare(best, renderSize) : null;
        } catch (Exception ignore) { return null; }
    }

    /* ---------- iOS PNG list ---------- */
    private static BufferedImage tryIosPngImage(Path bundle, NSDictionary root, int renderSize) {
        try {
            NSDictionary icons = (NSDictionary) root.objectForKey("CFBundleIcons");
            if (icons == null) icons = (NSDictionary) root.objectForKey("CFBundleIcons~ipad");
            if (icons == null) return null;

            NSDictionary primary = (NSDictionary) icons.objectForKey("CFBundlePrimaryIcon");
            if (primary == null) return null;

            NSObject filesObj = primary.objectForKey("CFBundleIconFiles");
            if (!(filesObj instanceof NSArray arr)) return null;

            List<Path> candidates = new ArrayList<>();
            for (NSObject o : arr.getArray()) {
                String base = o.toString();
                for (String v : new String[]{ base, base + ".png", base + "@2x.png", base + "@3x.png" }) {
                    Path p = bundle.resolve("Contents/Resources").resolve(v);
                    if (Files.exists(p)) candidates.add(p);
                }
            }
            if (candidates.isEmpty()) return null;

            BufferedImage best = null;
            for (Path p : candidates) {
                try (InputStream in = Files.newInputStream(p)) {
                    BufferedImage img = ImageIO.read(in);
                    if (img != null && (best == null || img.getWidth() > best.getWidth())) best = img;
                } catch (Exception ignore) {}
            }
            return (best != null) ? scaleToSquare(best, renderSize) : null;
        } catch (Exception ignore) { return null; }
    }

    /* ---------- System icon ---------- */
    private static BufferedImage trySystemIconImage(Path bundle, int renderSize) {
        try {
            File f = bundle.toFile();
            Icon raw = FileSystemView.getFileSystemView().getSystemIcon(f);
            if (raw == null) return null;

            BufferedImage src;
            if (raw instanceof ImageIcon ii) {
                src = toBuffered(ii.getImage());
            } else {
                int w = Math.max(raw.getIconWidth(), 16);
                int h = Math.max(raw.getIconHeight(), 16);
                src = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = src.createGraphics();
                try { raw.paintIcon(null, g, 0, 0); } finally { g.dispose(); }
            }
            return (src != null) ? scaleToSquare(src, renderSize) : null;
        } catch (Exception ignore) { return null; }
    }

    /* ---------- Quick Look (used only at add-time, with timeout) ---------- */
    private static ImageIcon tryQuickLookAppIcon(Path bundle, int renderSize, int timeoutMs) {
        try {
            String os = System.getProperty("os.name","").toLowerCase(Locale.ROOT);
            if (!os.contains("mac")) return null;

            Path ql = Paths.get("/usr/bin/qlmanage");
            if (!Files.isExecutable(ql)) return null;

            // Unique per-bundle out dir; clean before run
            String safeName = bundle.getFileName().toString().replaceAll("[^A-Za-z0-9._-]", "_");
            Path outDir = Paths.get(System.getProperty("java.io.tmpdir"), "lp-appicon-" + safeName);
            if (Files.exists(outDir)) {
                try (var s = Files.list(outDir)) { s.forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignore) {} }); }
            }
            Files.createDirectories(outDir);

            long startMs = System.currentTimeMillis();

            ProcessBuilder pb = new ProcessBuilder(
                ql.toString(), "-t",
                "-s", String.valueOf(renderSize),
                "-o", outDir.toString(),
                bundle.toString()
            ).redirectErrorStream(true);

            Process proc = pb.start();

            // Drain output
            ExecutorService drainer = Executors.newSingleThreadExecutor(r -> {
                Thread th = new Thread(r, "qlmanage-drain"); th.setDaemon(true); return th;
            });
            drainer.submit(() -> {
                byte[] buf = new byte[4096];
                try (InputStream in = proc.getInputStream()) { while (in.read(buf) != -1) {} }
                catch (Exception ignore) {}
            });

            boolean finished = proc.waitFor(Math.max(500, timeoutMs), TimeUnit.MILLISECONDS);
            if (!finished) {
                proc.destroyForcibly();
                drainer.shutdownNow();
                return null;
            }
            drainer.shutdownNow();

            File[] pngs = outDir.toFile().listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".png"));
            if (pngs == null || pngs.length == 0) return null;

            // choose newest after we started
            File best = null;
            long bestScore = Long.MIN_VALUE;
            for (File f : pngs) {
                long t = f.lastModified();
                long score = (t < startMs) ? -1 : t;
                if (score > bestScore) { bestScore = score; best = f; }
            }
            if (best == null) {
                Arrays.sort(pngs, Comparator.comparingLong(File::lastModified).reversed());
                best = pngs[0];
            }

            BufferedImage img = ImageIO.read(best);
            if (img == null) return null;

            BufferedImage scaled = scaleToSquare(img, renderSize);
            return new ImageIcon(scaled);

        } catch (Exception ex) {
            return null;
        }
    }

    /* ---------- Visible-content guard ---------- */

    /**
     * Returns true if the image contains enough non-transparent pixels to be a real icon.
     * System and iOS-on-Mac apps ship stub ICNS files that are nearly or fully transparent;
     * their real icons live in Assets.car and must be fetched via NSWorkspace.
     */
    private static boolean hasVisibleContent(BufferedImage img) {
        if (img == null) return false;
        int w = img.getWidth(), h = img.getHeight();
        if (w <= 0 || h <= 0) return false;
        // Require at least 0.5% of pixels to be non-transparent (alpha > 10/255).
        int threshold = Math.max(10, (w * h) / 200);
        int opaque = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((img.getRGB(x, y) >>> 24) > 10 && ++opaque >= threshold) return true;
            }
        }
        return false;
    }

    /* ---------- NSWorkspace icon (macOS native) ---------- */

    /**
     * Extracts the app icon via macOS NSWorkspace using an osascript JXA one-liner.
     * This is the most reliable method: it reads from Assets.car, handles iOS-on-Mac apps,
     * and matches exactly what Finder/Dock display.  Requires macOS; no-ops on other platforms.
     */
    private static ImageIcon tryNSWorkspaceIcon(Path bundle, int renderSize) {
        try {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (!os.contains("mac")) return null;

            Path osa = Paths.get("/usr/bin/osascript");
            if (!Files.isExecutable(osa)) return null;

            Path outPng = Files.createTempFile("lp-nsw-", ".png");
            try {
                // Ask for at least 256 px so downscaling gives a crisp result.
                int dim = Math.max(renderSize, 256);
                String bundlePath = bundle.toAbsolutePath().toString().replace("'", "\\'");
                String pngPath    = outPng.toAbsolutePath().toString().replace("'", "\\'");

                String script =
                    "ObjC.import('AppKit');" +
                    "var w=$.NSWorkspace.sharedWorkspace;" +
                    "var icon=w.iconForFile('" + bundlePath + "');" +
                    "icon.setSize({width:" + dim + ",height:" + dim + "});" +
                    "var tiff=icon.TIFFRepresentation;" +
                    "var rep=$.NSBitmapImageRep.imageRepWithData(tiff);" +
                    "var png=rep.representationUsingTypeProperties(" +
                    "$.NSBitmapImageFileTypePNG,$.NSDictionary.dictionary);" +
                    "png.writeToFileAtomically('" + pngPath + "',true);" +
                    "'done'";

                ProcessBuilder pb = new ProcessBuilder(osa.toString(), "-l", "JavaScript", "-e", script)
                        .redirectErrorStream(true);
                Process proc = pb.start();

                ExecutorService drainer = Executors.newSingleThreadExecutor(r -> {
                    Thread th = new Thread(r, "nsw-drain"); th.setDaemon(true); return th;
                });
                drainer.submit(() -> {
                    byte[] buf = new byte[4096];
                    try (InputStream in = proc.getInputStream()) { while (in.read(buf) != -1) {} }
                    catch (Exception ignore) {}
                });

                boolean finished = proc.waitFor(4000, TimeUnit.MILLISECONDS);
                drainer.shutdownNow();
                if (!finished) { proc.destroyForcibly(); return null; }

                if (!Files.exists(outPng) || Files.size(outPng) == 0) return null;

                BufferedImage img = ImageIO.read(outPng.toFile());
                if (img == null || !hasVisibleContent(img)) return null;
                return new ImageIcon(scaleToSquare(img, renderSize));

            } finally {
                Files.deleteIfExists(outPng);
            }
        } catch (Exception ignore) {
            return null;
        }
    }

    /* ===================== Helpers ===================== */

    private static String stripAppExtension(String s) {
        return (s != null && s.endsWith(".app")) ? s.substring(0, s.length() - 4) : s;
    }

    /** Convert any Image to BufferedImage (best-effort). */
    private static BufferedImage toBuffered(Image img) {
        if (img == null) return null;
        if (img instanceof BufferedImage bi) return bi;
        BufferedImage out = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try { g.drawImage(img, 0, 0, null); } finally { g.dispose(); }
        return out;
    }

    /** Safely rasterize an ImageIcon into a BufferedImage by painting it. */
    private static BufferedImage iconToBuffered(ImageIcon icon) {
        if (icon == null) return null;
        int w = icon.getIconWidth(), h = icon.getIconHeight();
        if (w <= 0 || h <= 0) return null;
        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = bi.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            icon.paintIcon(null, g, 0, 0);
        } finally { g.dispose(); }
        return bi;
    }

    /** Scale into a square with preserved aspect ratio, centered. */
    private static BufferedImage scaleToSquare(BufferedImage img, int side) {
        int w = img.getWidth(), h = img.getHeight();
        float scale = Math.min((float) side / w, (float) side / h);
        int newW = Math.max(1, Math.round(w * scale));
        int newH = Math.max(1, Math.round(h * scale));
        BufferedImage out = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int x = (side - newW) / 2, y = (side - newH) / 2;
            g.drawImage(img, x, y, newW, newH, null);
        } finally { g.dispose(); }
        return out;
    }

    /** Snap requested decode size to common ICNS sizes to avoid slow resampling. */
    private static int snapDecodeSize(int want) {
        int[] sizes = {16, 32, 64, 128, 256, 512, 1024};
        int best = sizes[0];
        for (int s : sizes) {
            if (s >= want) { best = s; break; }
            best = s;
        }
        return best;
    }

    /**
     * Returns true if a directory under the given path matches the iOS-wrapper layout:
     * Wrapper/<something>.app  (pure iOS apps installed on Apple Silicon).
     */
    private static boolean isIosWrapperBundle(java.nio.file.Path bundle) {
        java.nio.file.Path wrapper = bundle.resolve("Wrapper");
        if (!java.nio.file.Files.isDirectory(wrapper)) return false;
        try (var s = java.nio.file.Files.list(wrapper)) {
            return s.anyMatch(p -> p.getFileName().toString().endsWith(".app")
                               && java.nio.file.Files.isDirectory(p));
        } catch (Exception e) { return false; }
    }

    public static boolean isLikelyUserFacingApp(File bundle) {
        try {
            java.nio.file.Path bpath = bundle.toPath();
            java.nio.file.Path infoPlist = bpath.resolve("Contents/Info.plist");

            // iOS wrapper apps (pure iOS on Apple Silicon) have no Contents/Info.plist;
            // their real bundle sits at Wrapper/<Name>.app.  Include them in the scan.
            if (!java.nio.file.Files.exists(infoPlist)) {
                return isIosWrapperBundle(bpath);
            }

            com.dd.plist.NSDictionary root = (com.dd.plist.NSDictionary) com.dd.plist.PropertyListParser.parse(infoPlist.toFile());

            // 1) Must be an application bundle
            String pkgType = optString(root, "CFBundlePackageType");
            if (!"APPL".equalsIgnoreCase(pkgType)) return false;

            // 2) Skip background/agent apps
            if (optBool(root, "LSBackgroundOnly", false)) return false;
            if (optBool(root, "LSUIElement", false)) return false;

            // 3) Name heuristics
            String name = firstNonBlank(
                optString(root, "CFBundleDisplayName"),
                optString(root, "CFBundleName"),
                bundle.getName()
            ).toLowerCase(java.util.Locale.ROOT);

            String bn = bundle.getName().toLowerCase(java.util.Locale.ROOT);
            String bad = "(helper|updat(er|e)|agent|daemon|service|installer|uninstall|crash|report(er)?|diagnostic|plugin|sample|example|test)";
            if (name.matches(".*" + bad + ".*") || bn.matches(".*" + bad + ".*")) return false;

            return true;
        } catch (Exception ignore) {
            return false;
        }
    }

    private static String optString(com.dd.plist.NSDictionary d, String k) {
        return d.containsKey(k) ? String.valueOf(d.objectForKey(k)) : null;
    }
    private static boolean optBool(com.dd.plist.NSDictionary d, String k, boolean def) {
        if (!d.containsKey(k)) return def;
        String v = String.valueOf(d.objectForKey(k)).trim();
        return "1".equals(v) || "true".equalsIgnoreCase(v);
    }
    private static String firstNonBlank(String... ss) {
        for (String s : ss) if (s != null && !s.isBlank()) return s;
        return "";
    }

}
