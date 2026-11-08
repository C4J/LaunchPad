package com.commander4j.launchpad;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.io.File;
import java.io.IOException;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class AppComponent extends JPanel
{
    private static final long serialVersionUID = 1L;

    private final String appPath;
    private final String appName;
    private final String displayName;

    /**
     * Single compound label: icon on top, caption centred underneath.
     * One label (rather than icon + caption labels in a BorderLayout) means the caption is
     * centred on the icon by construction and the icon/caption spacing is a single knob.
     */
    private final JLabel label;

    /**
     * Gap between the icon's bounding box and the caption, in pixels. Negative on purpose:
     * macOS app icons carry a ~10px transparent margin inside the 120px render square, so the
     * caption may sit inside that margin without touching the visible artwork. Full-bleed custom
     * PNG icons will come within a pixel or so of the caption; raise this if that ever matters.
     */
    private static final int ICON_TEXT_GAP = -3;
    
 // --- Add in AppComponent ---
    private String customIconPath;

    public String getCustomIconPath() {
        return customIconPath;
    }

    public void setCustomIconPath(String customIconPath) {
        this.customIconPath = customIconPath;
    }


    /** Legacy ctor kept for compatibility. */
    public AppComponent(File bundle, ImageIcon icon) {
        this(bundle, stripAppSuffix(bundle != null ? bundle.getName() : ""), icon);
    }

    /** Preferred ctor with display name. */
    public AppComponent(File bundle, String displayName, ImageIcon icon)
    {
        this.appPath = bundle.getAbsolutePath();
        this.appName = bundle.getName();
        this.displayName = (displayName != null && !displayName.isBlank())
                ? displayName : stripAppSuffix(this.appName);

        setLayout(new BorderLayout());
        setOpaque(false);
        setPreferredSize(new Dimension(60, 60));

        // No fixed CSS width on the div: Swing scales CSS px by 1.3, so width:130px produced a
        // 169px view inside a 150px cell and pushed the caption ~9px right of centre.
        // The HTML div still wraps long names to the cell width.
        label = new JLabel("<html><div style='text-align:center;'>"
                + escapeHtml(this.displayName) + "</div></html>", icon, JLabel.CENTER);
        label.setVerticalAlignment(JLabel.TOP);              // icon pinned to the cell top; wrapped names grow downwards
        label.setHorizontalTextPosition(JLabel.CENTER);       // caption centred on the icon
        label.setVerticalTextPosition(JLabel.BOTTOM);         // caption below the icon
        label.setIconTextGap(ICON_TEXT_GAP);
        label.setToolTipText(this.displayName);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));

        add(label, BorderLayout.CENTER);

        setTransferHandler(new AppComponentTransferHandler());

        // Shared adapter so drag vs double-click works reliably
        DragAwareDoubleClickAdapter adapter =
                new DragAwareDoubleClickAdapter(this, this::launchApp);

        addMouseListener(adapter);
        addMouseMotionListener(adapter);
        label.addMouseListener(adapter);
        label.addMouseMotionListener(adapter);
    }

    private static String stripAppSuffix(String s) {
        return (s != null && s.endsWith(".app")) ? s.substring(0, s.length() - 4) : s;
    }

    public String getAppPath()     { return appPath; }
    public String getAppName()     { return appName; }
    public String getDisplayName() { return displayName; }

    /** Allows icon updates after construction. */
    public void setIcon(ImageIcon icon) {
        label.setIcon(icon);
        revalidate();
        repaint();
    }

    private void launchApp() {
        try { new ProcessBuilder("open", appPath).start(); }
        catch (IOException ex) { ex.printStackTrace(); }
    }
    
    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }

}
