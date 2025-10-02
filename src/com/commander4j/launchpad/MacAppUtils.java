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
            return (bi != null) ? new ImageIcon(bi) : null;
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

    /* ===================== Main API ===================== */

    /** Creates an AppComponent with display name + icon.
     *  Icon is resolved synchronously now (at add-time), then cached to memory+disk.
     *  The returned component has customIconPath set when a disk icon exists. */
    public static AppComponent createAppComponent(File bundle) {
        if (bundle == null || !bundle.exists()) return null;

        try {
            Path bpath = bundle.toPath();
            Path infoPlist = bpath.resolve("Contents/Info.plist");

            // ==== NEW BEHAVIOUR: if Info.plist is missing, try manual icon cache first ====
            if (!Files.exists(infoPlist)) {
                String displayName = stripAppExtension(bundle.getName());
                String memKey = canonical(bpath) + "|" + ICON_RENDER_SIZE;

                // 1) memory cache
                ImageIcon icon = ICON_CACHE.get(memKey);

                // 2) disk cache (manual icon), read RAW without freshness checks
                if (icon == null) {
                    Path png = iconCacheFile(bpath);
                    if (Files.exists(png)) {
                        try (InputStream in = Files.newInputStream(png)) {
                            BufferedImage bi = ImageIO.read(in);
                            if (bi != null) icon = new ImageIcon(bi);
                        } catch (Exception ignore) {}
                    }
                }

                // 3) final fallback: blank icon
                if (icon == null) {
                    icon = new ImageIcon();
                } else {
                    ICON_CACHE.put(memKey, icon);
                }

                AppComponent comp = new AppComponent(bundle, displayName, icon);
                Path pngPath = iconCacheFile(bpath);
                if (Files.exists(pngPath)) {
                    comp.setCustomIconPath(pngPath.toString());
                }
                return comp;
            }
            // ==== END NEW BEHAVIOUR ==== //

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

    // Order: .icns → iOS PNGs → (Assets.car) Quick Look → System icon
    private static ImageIcon resolveIconAtAddTime(Path bundle, NSDictionary root, int renderSize) {
        // 1) Classic .icns
        BufferedImage icns = tryIcnsImage(bundle, root, renderSize);
        if (icns != null) return new ImageIcon(icns);

        // 2) iOS PNG list (CFBundleIcons)
        BufferedImage ios = tryIosPngImage(bundle, root, renderSize);
        if (ios != null) return new ImageIcon(ios);

        // 3) Assets.car present? Use Quick Look to render the app icon (CoreUI reads Assets.car)
        if (hasAssetsCar(bundle)) {
            ImageIcon ql = tryQuickLookAppIcon(bundle, renderSize, /*timeoutMs*/ 2500);
            if (ql != null && ql.getIconWidth() > 0) return ql;
        }

        // 4) Last resort: system icon (may look like a folder)
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
            String iconName = root.containsKey("CFBundleIconFile")
                    ? root.objectForKey("CFBundleIconFile").toString() : null;
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

    public static boolean isLikelyUserFacingApp(File bundle) {
        try {
            java.nio.file.Path infoPlist = bundle.toPath().resolve("Contents/Info.plist");
            if (!java.nio.file.Files.exists(infoPlist)) return false;

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
