package com.commander4j.launchpad;

/*******************************************************************************
 * Title:        Commander4j
 * Description:  LaunchPad Grid Cell (DnD import, context menu, delete key, custom icon)
 * Author:       Dave (with ChatGPT assistance)
 * License:      GNU General Public License
 *******************************************************************************/

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.KeyEvent;
import java.io.File;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.filechooser.FileNameExtensionFilter;

public class LaunchCell extends JPanel
{
    private static final long serialVersionUID = 1L;

    private AppComponent app;
    private final JPopupMenu popup;

    public LaunchCell()
    {
        setBorder(BorderFactory.createEmptyBorder());
        setPreferredSize(new Dimension(100, 100));
        setLayout(new BorderLayout());

        // DnD importer (Finder files + internal moves)
        setTransferHandler(new LaunchCellTransferHandler(this));

        // Build context menu
        popup = buildPopup();

        // Let Swing handle right-clicks anywhere on this component
        setComponentPopupMenu(popup);

        // Keyboard: Delete key removes app
        setFocusable(true);
        getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "removeApp");
        getActionMap().put("removeApp", new AbstractAction() {
            private static final long serialVersionUID = 1L;
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                if (!isEmpty()) clear();
            }
        });

        // When popup opens, grab focus so Delete works immediately after using it
        popup.addPopupMenuListener(new PopupMenuListener() {
            @Override public void popupMenuWillBecomeVisible(PopupMenuEvent e) { requestFocusInWindow(); }
            @Override public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {}
            @Override public void popupMenuCanceled(PopupMenuEvent e) {}
        });
    }

    private JPopupMenu buildPopup()
    {
        JPopupMenu pm = new JPopupMenu();

        JMenuItem assignIcon = new JMenuItem("Assign Custom Icon...");
        assignIcon.addActionListener(e -> {
            AppComponent a = getApp();
            if (a == null) return;

            JFileChooser fc = new JFileChooser(MacAppUtils.getLastChooserDir());
            fc.setDialogTitle("Choose an icon image");
            fc.setAcceptAllFileFilterUsed(false); // don’t show “All files”
            fc.addChoosableFileFilter(new FileNameExtensionFilter(
                "Image files (PNG, JPG, JPEG, GIF, ICNS)", "png", "jpg", "jpeg", "gif", "icns"
            ));

            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File chosen = fc.getSelectedFile();
                MacAppUtils.setLastChooserDir(chosen.getParentFile());
                try {
                    File bundle = new File(a.getAppPath());
                    ImageIcon icon = MacAppUtils.loadAndCacheCustomIcon(bundle, chosen, MacAppUtils.ICON_RENDER_SIZE);
                    if (icon != null) {
                        a.setIcon(icon);
                        a.setCustomIconPath(MacAppUtils.getCachedIconPathForBundle(bundle));
                        revalidate();
                        repaint();
                    } else {
                        javax.swing.JOptionPane.showMessageDialog(
                            this, "Unable to read that image file.",
                            "Icon Import", javax.swing.JOptionPane.WARNING_MESSAGE
                        );
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    javax.swing.JOptionPane.showMessageDialog(
                        this, "Failed to assign icon:\n" + ex.getMessage(),
                        "Icon Import Error", javax.swing.JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        });
        pm.add(assignIcon);

        JMenuItem remove = new JMenuItem("Remove App");
        remove.addActionListener(e -> { if (!isEmpty()) clear(); });
        pm.add(remove);

        JMenuItem reveal = new JMenuItem("Reveal in Finder");
        reveal.addActionListener(e -> {
            AppComponent a = getApp();
            if (a != null) {
                try {
                    new ProcessBuilder("open", "-R", new File(a.getAppPath()).getAbsolutePath()).start();
                } catch (Exception ex) { ex.printStackTrace(); }
            }
        });
        pm.add(reveal);

        return pm;
    }

    public boolean isEmpty() { return app == null; }

    /** Reassert the cell’s popup on itself and its current content. */
    public void reassertPopup() {
        setComponentPopupMenu(popup);
        installPopupRecursively(this, popup);
        if (app != null) {
            app.setComponentPopupMenu(popup);
            installPopupRecursively(app, popup);
        }
    }

    /** Detach the current app component for a MOVE operation without stripping popups. */
    public AppComponent detachAppForMove() {
        if (app == null) return null;
        AppComponent moving = app;
        remove(moving);
        app = null;
        revalidate();
        repaint();
        // Do NOT clear popups here; destination will reassert.
        return moving;
    }

    public void setApp(AppComponent newApp)
    {
        removeAll();

        // Do not strip popups from the previous app; it might be moved elsewhere.
        this.app = newApp;

        if (this.app != null) {
            add(this.app, BorderLayout.CENTER);
        }

        // Ensure right-click works everywhere on the cell and its new content
        reassertPopup();

        revalidate();
        repaint();
    }

    /** Install (or remove) the same popup on all descendants so right-click works anywhere. */
    private void installPopupRecursively(java.awt.Component c, JPopupMenu pm) {
        if (c instanceof JComponent jc) {
            jc.setComponentPopupMenu(pm);
        }
        if (c instanceof java.awt.Container container) {
            for (java.awt.Component child : container.getComponents()) {
                installPopupRecursively(child, pm);
            }
        }
    }

    public AppComponent getApp() { return app; }

    public void clear()
    {
        removeAll();
        // Don’t strip popups off the old app; it could be reused/moved elsewhere.
        this.app = null;

        // Keep popup active on the empty cell
        reassertPopup();

        revalidate();
        repaint();
    }
}
