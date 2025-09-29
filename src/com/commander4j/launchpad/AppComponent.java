package com.commander4j.launchpad;

import java.awt.BorderLayout;
import java.awt.Dimension;
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

    private final JLabel iconLabel;
    private final JLabel nameLabel;
    
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

        iconLabel = new JLabel(icon);
        iconLabel.setHorizontalAlignment(JLabel.CENTER);

        nameLabel = new JLabel(this.displayName, JLabel.CENTER);
     // after creating nameLabel
        nameLabel.setText("<html><div style='text-align:center;width:130px;'>"
            + escapeHtml(this.displayName) + "</div></html>");
        nameLabel.setToolTipText(this.displayName);
        nameLabel.setFont(nameLabel.getFont().deriveFont(12f));


        add(iconLabel, BorderLayout.CENTER);
        add(nameLabel, BorderLayout.SOUTH);

        setTransferHandler(new AppComponentTransferHandler());

        // Shared adapter so drag vs double-click works reliably
        DragAwareDoubleClickAdapter adapter =
                new DragAwareDoubleClickAdapter(this, this::launchApp);

        addMouseListener(adapter);
        addMouseMotionListener(adapter);
        iconLabel.addMouseListener(adapter);
        iconLabel.addMouseMotionListener(adapter);
        nameLabel.addMouseListener(adapter);
        nameLabel.addMouseMotionListener(adapter);
    }

    private static String stripAppSuffix(String s) {
        return (s != null && s.endsWith(".app")) ? s.substring(0, s.length() - 4) : s;
    }

    public String getAppPath()     { return appPath; }
    public String getAppName()     { return appName; }
    public String getDisplayName() { return displayName; }

    /** Allows icon updates after construction. */
    public void setIcon(ImageIcon icon) {
        iconLabel.setIcon(icon);
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
