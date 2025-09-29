package com.commander4j.launchpad;

import java.io.File;
import java.io.IOException;
import java.nio.file.LinkOption;
import java.nio.file.Path;

import javax.swing.JTabbedPane;
import javax.swing.JScrollPane;

public final class LaunchPadUtils {
    private LaunchPadUtils() {}

    public static class Location {
        public final int tabIndex;
        public final int cellIndex;
        public final String tabName;
        public Location(int tabIndex, int cellIndex, String tabName) {
            this.tabIndex = tabIndex;
            this.cellIndex = cellIndex;
            this.tabName = tabName;
        }
    }

    /** Return canonical absolute path for reliable comparisons. */
    public static String canonicalPath(File f) {
        try {
            return f.getCanonicalPath();
        } catch (IOException e) {
            return f.getAbsolutePath();
        }
    }

    /**
     * Resolve a dropped/selected path to the *real* .app bundle directory.
     * Works when Finder shows /Applications but the bundle actually lives in /System/Applications.
     * If the input path is inside a bundle, this climbs up to the *.app root.
     */
    public static File resolveRealAppBundle(File candidate) {
        if (candidate == null) return null;

        Path p = candidate.toPath();
        // If user picked something inside the bundle, climb to *.app
        Path cur = p;
        while (cur != null) {
            String name = (cur.getFileName() != null) ? cur.getFileName().toString() : "";
            if (name.endsWith(".app")) break;
            cur = cur.getParent();
        }
        if (cur == null) return null;

        try {
            Path real = cur.toRealPath(LinkOption.NOFOLLOW_LINKS);
            File realFile = real.toFile();
            if (realFile.isDirectory() && realFile.getName().endsWith(".app")) {
                return realFile;
            }
        } catch (IOException ignore) {}

        File fallback = cur.toFile();
        return (fallback.isDirectory() && fallback.getName().endsWith(".app")) ? fallback : null;
    }

    /** Find an app by canonical path across all tabs; returns null if not found. */
    public static Location findApp(JTabbedPane tabs, String appPathCanonical) {
        if (tabs == null || appPathCanonical == null) return null;

        for (int t = 0; t < tabs.getTabCount(); t++) {
            LaunchTabPanel panel = unwrapPanel(tabs.getComponentAt(t));
            if (panel == null) continue;

            for (int i = 0; i < panel.getComponentCount(); i++) {
                if (panel.getComponent(i) instanceof LaunchCell cell) {
                    AppComponent app = cell.getApp();
                    if (app != null) {
                        String existing = app.getAppPath();
                        if (existing != null && appPathCanonical.equals(canonicalPath(new File(existing)))) {
                            return new Location(t, i, tabs.getTitleAt(t));
                        }
                    }
                }
            }
        }
        return null;
    }

    private static LaunchTabPanel unwrapPanel(java.awt.Component c) {
        if (c instanceof LaunchTabPanel p) return p;
        if (c instanceof JScrollPane sp) {
            var v = (sp.getViewport() != null) ? sp.getViewport().getView() : null;
            if (v instanceof LaunchTabPanel p) return p;
        }
        return null;
    }
}
