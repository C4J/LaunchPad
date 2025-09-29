package com.commander4j.launchpad;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ICNSUtils {

    public static BufferedImage loadBestImage(File icnsFile, int targetSize) throws IOException {
        // 1) Try ImageIO ICNS plugin (TwelveMonkeys)
        BufferedImage img = tryImageIOIcns(icnsFile, targetSize);
        if (img != null) return img;

        // 2) Fallback: manual block scan (lets ImageIO decode PNG/JP2 blocks)
        List<BufferedImage> all = extractAllImages(icnsFile);
        img = pickBestBySize(all, targetSize);
        if (img != null) return img;

        // 3) macOS QuickLook fallback (no extra jars, but requires macOS tools)
        img = tryQuickLook(icnsFile, targetSize);
        return img; // may be null; caller will handle
    }

    /* ---------- 1) ImageIO ICNS plugin path ---------- */

    private static BufferedImage tryImageIOIcns(File icnsFile, int targetSize) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(icnsFile)) {
            if (iis == null) return null;

            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            ImageReader chosen = null;
            while (readers.hasNext()) {
                ImageReader r = readers.next();
                // Prefer an ICNS reader if present
                String fmt = safeFormatName(r);
                if ("icns".equalsIgnoreCase(fmt)) { chosen = r; break; }
                if (chosen == null) chosen = r; // fallback to first
            }
            if (chosen == null) return null;

            try {
                chosen.setInput(iis, true, true);
                int num = chosen.getNumImages(true);
                if (num <= 0) num = 1; // some readers report 0 but allow index 0

                // Collect all subimages with sizes
                List<IndexedImage> imgs = new ArrayList<>();
                for (int i = 0; i < num; i++) {
                    int w, h;
                    try {
                        w = chosen.getWidth(i);
                        h = chosen.getHeight(i);
                    } catch (Exception ignore) {
                        // if metadata lookup fails, try read and measure
                        BufferedImage bi = chosen.read(i);
                        if (bi != null) imgs.add(new IndexedImage(i, bi.getWidth(), bi.getHeight(), bi));
                        continue;
                    }
                    BufferedImage bi = chosen.read(i);
                    if (bi != null) imgs.add(new IndexedImage(i, w, h, bi));
                }
                chosen.dispose();

                if (!imgs.isEmpty()) {
                    return pickBestBySizeIndexed(imgs, targetSize).image;
                }
            } finally {
                try { chosen.dispose(); } catch (Exception ignore) {}
            }
        } catch (Exception ignore) {
            // No plugin or read failed; fall through to manual
        }
        return null;
    }

    private static String safeFormatName(ImageReader r) {
        try { return r.getFormatName(); } catch (Exception e) { return ""; }
    }

    private static class IndexedImage {
        final int w; final int h; final BufferedImage image;
        IndexedImage(int index, int w, int h, BufferedImage image) {
             this.w = w; this.h = h; this.image = image;
        }
    }

    private static IndexedImage pickBestBySizeIndexed(List<IndexedImage> list, int target) {
        IndexedImage bestGE = null, bestLT = null;
        for (IndexedImage ii : list) {
            int s = Math.max(ii.w, ii.h);
            if (s >= target) {
                if (bestGE == null || s < Math.max(bestGE.w, bestGE.h)) bestGE = ii;
            } else {
                if (bestLT == null || s > Math.max(bestLT.w, bestLT.h)) bestLT = ii;
            }
        }
        return (bestGE != null) ? bestGE : bestLT;
    }

    private static BufferedImage pickBestBySize(List<BufferedImage> list, int target) {
        BufferedImage bestGE = null, bestLT = null;
        for (BufferedImage bi : list) {
            int s = Math.max(bi.getWidth(), bi.getHeight());
            if (s >= target) {
                if (bestGE == null || s < Math.max(bestGE.getWidth(), bestGE.getHeight())) bestGE = bi;
            } else {
                if (bestLT == null || s > Math.max(bestLT.getWidth(), bestLT.getHeight())) bestLT = bi;
            }
        }
        return (bestGE != null) ? bestGE : bestLT;
    }

    /* ---------- 2) Manual block scan path (PNG/JP2 via ImageIO) ---------- */

    private static List<BufferedImage> extractAllImages(File icnsFile) throws IOException {
        List<BufferedImage> result = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(icnsFile)) {
            byte[] all = fis.readAllBytes();
            if (all.length < 8) return result;

            String sig = new String(all, 0, 4, StandardCharsets.US_ASCII);
            if (!"icns".equals(sig)) return result;

            int fileLen = beInt(all, 4);
            int offset = 8;
            while (offset + 8 <= all.length && offset + 8 <= fileLen) {
                int length = beInt(all, offset + 4);
                if (length < 8 || offset + length > all.length) break;

                int dataOffset = offset + 8;
                int dataLen = length - 8;

                try (ByteArrayInputStream bin = new ByteArrayInputStream(all, dataOffset, dataLen)) {
                    BufferedImage img = ImageIO.read(bin); // will use PNG, JP2, etc. if plugins present
                    if (img != null) result.add(img);
                } catch (Throwable ignore) {}

                offset += length;
            }
        }
        return result;
    }

    private static int beInt(byte[] b, int off) {
        return ((b[off] & 0xFF) << 24) | ((b[off + 1] & 0xFF) << 16)
             | ((b[off + 2] & 0xFF) << 8)  |  (b[off + 3] & 0xFF);
    }

    /* ---------- 3) macOS QuickLook fallback ---------- */

    private static BufferedImage tryQuickLook(File icnsFile, int targetSize) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!os.contains("mac")) return null;

        File tmpDir = new File(System.getProperty("java.io.tmpdir"), "lp-icns");
        tmpDir.mkdirs();
        File outPng = new File(tmpDir, icnsFile.getName() + "-" + targetSize + ".png");

        try {
            // Ask QuickLook to render a thumbnail PNG of the ICNS
            // -s <size> is the max dimension; QuickLook keeps aspect ratio
            new ProcessBuilder("qlmanage", "-t", "-s", String.valueOf(targetSize),
                               "-o", tmpDir.getAbsolutePath(),
                               icnsFile.getAbsolutePath())
                .redirectErrorStream(true)
                .start()
                .waitFor();

            if (outPng.exists()) {
                return ImageIO.read(outPng);
            } else {
                // qlmanage names the output as <name>.icns.png typically
                File[] pngs = tmpDir.listFiles((dir, name) -> name.startsWith(icnsFile.getName()) && name.endsWith(".png"));
                if (pngs != null && pngs.length > 0) {
                    Arrays.sort(pngs, Comparator.comparingLong(File::lastModified).reversed());
                    return ImageIO.read(pngs[0]);
                }
            }
        } catch (Exception ignore) {
        }
        return null;
    }
}
