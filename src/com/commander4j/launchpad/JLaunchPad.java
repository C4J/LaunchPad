package com.commander4j.launchpad;

/*******************************************************************************
 * Title:        Commander4j
 * Description:  LaunchPad Application for macOS App Bundles
 * Author:       Dave (with ChatGPT assistance)
 * License:      GNU General Public License
 *******************************************************************************/

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.Rectangle;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JToolBar;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;

import com.commander4j.dialog.JDialogAbout;
import com.commander4j.dialog.JDialogLicenses;
import com.commander4j.gui.JButton4j;
import com.commander4j.sys.Common;
import com.commander4j.util.JHelp;
import com.commander4j.util.Utility;

public class JLaunchPad extends JFrame
{
    private static final long serialVersionUID = 1L;
    private Dimension buttonSize = new Dimension(32,32);
    private static int widthadjustment = 0;
    private static int heightadjustment = 0;
    public static String version = "1.21";

    private final JTabbedPane tabs;

    public JLaunchPad()
    {
        super("jLaunchPad"+" ["+version+"]");

        System.setProperty("apple.laf.useScreenMenuBar", "true");
        Utility.setLookAndFeel("Nimbus");

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(1300, 900));
        getContentPane().setLayout(new BorderLayout());

        tabs = new JTabbedPane(JTabbedPane.LEFT);
        tabs.setOpaque(true);
        getContentPane().add(tabs, BorderLayout.CENTER); // ← no outer scrollpane anymore

        TabReorderMouse trm = new TabReorderMouse();
        tabs.addMouseListener(trm);
        tabs.addMouseMotionListener(trm);

        // Shared drop handler used by the tabbedpane AND each tab's scrollpane
        final TransferHandler sharedDropHandler = new DropToTabHandler();
        tabs.setTransferHandler(sharedDropHandler);

        // ===== Toolbar (unchanged layout) =====
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setOrientation(JToolBar.VERTICAL);
        getContentPane().add(toolbar,BorderLayout.WEST);

        JButton4j addTabButton = new JButton4j(Common.icon_add);
        addTabButton.setToolTipText("Add a new Tab");
        addTabButton.setSize(buttonSize);
        addTabButton.setPreferredSize(buttonSize);
        addTabButton.setMaximumSize(buttonSize);
        addTabButton.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(JLaunchPad.this, "Category name:");
            if (name != null && !name.isBlank())
            {
                LaunchTabPanel panel = new LaunchTabPanel();
                JScrollPane sp = wrapPanel(panel);
                // Allow drops when pointer is over the scroller/viewport
                sp.setTransferHandler(sharedDropHandler);
                tabs.addTab(name, sp);
                tabs.setSelectedComponent(sp);
            }
        });

        toolbar.addSeparator();
        toolbar.add(addTabButton);

        JButton4j editTabButton = new JButton4j(Common.icon_edit);
        editTabButton.setToolTipText("Rename Tab");
        editTabButton.setPreferredSize(buttonSize);
        editTabButton.setSize(buttonSize);
        editTabButton.setMaximumSize(buttonSize);
        editTabButton.addActionListener(e -> {
            int selected = tabs.getSelectedIndex();
            if (selected >=0)
            {
                String name = tabs.getTitleAt(selected);
                name = JOptionPane.showInputDialog(JLaunchPad.this, "Category name:", name);
                if (name != null && !name.isBlank())
                {
                    tabs.setTitleAt(selected, name);
                }
            }
        });
        toolbar.add(editTabButton);

        JButton4j deleteTabButton = new JButton4j(Common.icon_delete);
        deleteTabButton.setToolTipText("Delete a Category");
        deleteTabButton.setPreferredSize(buttonSize);
        deleteTabButton.setSize(buttonSize);
        deleteTabButton.setMaximumSize(buttonSize);
        deleteTabButton.addActionListener(e -> {
            int selected = tabs.getSelectedIndex();
            if (selected >=0)
            {
        		int result = JOptionPane.showConfirmDialog(JLaunchPad.this, "Are you sure you want to remove category '" + tabs.getTitleAt(selected) + "'", "Confirm Remove ?", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,Common.app_icon_dialog);

        		if (result == JOptionPane.YES_OPTION)
        		{
                    tabs.remove(selected);
        		}
            }
        });
        toolbar.add(deleteTabButton);

        JButton4j moveTabUpButton = new JButton4j(Common.icon_up);
        moveTabUpButton.setToolTipText("Move selected Category up.");
        moveTabUpButton.setPreferredSize(buttonSize);
        moveTabUpButton.setSize(buttonSize);
        moveTabUpButton.setMaximumSize(buttonSize);
        moveTabUpButton.addActionListener(e -> {
            int selected = tabs.getSelectedIndex();
            if (selected >=0) moveSelectedTabUp();
        });
        toolbar.add(moveTabUpButton);

        JButton4j moveTabDownButton = new JButton4j(Common.icon_down);
        moveTabDownButton.setToolTipText("Move selected Category down.");
        moveTabDownButton.setPreferredSize(buttonSize);
        moveTabDownButton.setSize(buttonSize);
        moveTabDownButton.setMaximumSize(buttonSize);
        moveTabDownButton.addActionListener(e -> {
            int selected = tabs.getSelectedIndex();
            if (selected >=0) moveSelectedTabDown();
        });
        toolbar.add(moveTabDownButton);

        // --- Add App (.app chooser) ---
        JButton4j addAppButton = new JButton4j(Common.icon_select_file);
        addAppButton.setToolTipText("Add Application…");
        addAppButton.setPreferredSize(buttonSize);
        addAppButton.setMaximumSize(buttonSize);
        addAppButton.addActionListener(e -> addSingleAppViaChooser());
        toolbar.add(addAppButton);

        // --- Import Folder (all .app recursively) ---
        JButton4j importFolderButton = new JButton4j(Common.icon_select_folder);
        importFolderButton.setToolTipText("Import Applications from Folder to current Category…");
        importFolderButton.setPreferredSize(buttonSize);
        importFolderButton.setMaximumSize(buttonSize);
        importFolderButton.addActionListener(e -> importAppsFromFolderViaChooser());
        toolbar.add(importFolderButton);

        // --- Pack Tab (defragment current tab) ---
        JButton4j packTabButton = new JButton4j(Common.icon_structure);
        packTabButton.setToolTipText("Pack Icons on This Category (remove spaces).");
        packTabButton.setPreferredSize(buttonSize);
        packTabButton.setMaximumSize(buttonSize);
        packTabButton.addActionListener(e -> packCurrentTab());
        toolbar.add(packTabButton);
        
        
        JButton4j btnHelp = new JButton4j(Common.icon_help);
		btnHelp.setPreferredSize(new Dimension(32, 32));
		btnHelp.setFocusable(false);
		btnHelp.setToolTipText("Help");
		toolbar.add(btnHelp);

		final JHelp help = new JHelp();
		help.enableHelpOnButton(btnHelp, "https://wiki.commander4j.com/index.php?title=LaunchPad");

		JButton4j btnAbout = new JButton4j(Common.icon_about);
		btnAbout.setPreferredSize(new Dimension(32, 32));
		btnAbout.setFocusable(false);
		btnAbout.setToolTipText("About");
		btnAbout.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				JDialogAbout about = new JDialogAbout();
				about.setVisible(true);
			}
		});
		toolbar.add(btnAbout);

		JButton4j btnLicense = new JButton4j(Common.icon_license);
		btnLicense.setPreferredSize(new Dimension(32, 32));
		btnLicense.setFocusable(false);
		btnLicense.setToolTipText("Licences");
		btnLicense.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				JDialogLicenses dl = new JDialogLicenses(JLaunchPad.this);
				dl.setVisible(true);
			}
		});

		toolbar.add(btnLicense);


        JButton4j exitTabButton = new JButton4j(Common.icon_exit);
        exitTabButton.setToolTipText("Exit");
        exitTabButton.setPreferredSize(buttonSize);
        exitTabButton.setSize(buttonSize);
        exitTabButton.setMaximumSize(buttonSize);
        exitTabButton.addActionListener(e -> {
            PersistenceHelper.saveState(tabs);
            dispose();
        });
        toolbar.add(exitTabButton);

        // Load saved state if available
        PersistenceHelper.loadState(tabs);

        // Ensure all existing tab components (added by loadState) have our drop handler
        attachDropHandlerToAllTabComponents(sharedDropHandler);

        // If no tabs loaded, add a default
        if (tabs.getTabCount() == 0)
        {
            LaunchTabPanel panel = new LaunchTabPanel();
            JScrollPane sp = wrapPanel(panel);
            sp.setTransferHandler(sharedDropHandler);
            tabs.addTab("Default", sp);
        }

        // Save on exit
        addWindowListener(new WindowAdapter()
        {
            @Override
            public void windowClosing(WindowEvent e)
            {
                PersistenceHelper.saveState(tabs);
            }
        });

        pack();
        setLocationRelativeTo(null);

        widthadjustment = Utility.getOSWidthAdjustment();
        heightadjustment = Utility.getOSHeightAdjustment();

        GraphicsDevice gd = Utility.getGraphicsDevice();
        GraphicsConfiguration gc = gd.getDefaultConfiguration();
        Rectangle screenBounds = gc.getBounds();

        setBounds(
            screenBounds.x + ((screenBounds.width - JLaunchPad.this.getWidth()) / 2),
            screenBounds.y + ((screenBounds.height - JLaunchPad.this.getHeight()) / 2),
            JLaunchPad.this.getWidth() + widthadjustment,
            JLaunchPad.this.getHeight() + heightadjustment
        );
        setVisible(true);
    }

    /** Wrap a LaunchTabPanel in a vertical-only scroller. */
    private JScrollPane wrapPanel(LaunchTabPanel panel) {
        JScrollPane sp = new JScrollPane(
            panel,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        );
        sp.setBorder(null);
        sp.getVerticalScrollBar().setUnitIncrement(24);
        return sp;
    }

    /** After loadState, ensure the inner scrollers accept drops too. */
    private void attachDropHandlerToAllTabComponents(TransferHandler h) {
        for (int i = 0; i < tabs.getTabCount(); i++) {
            var c = tabs.getComponentAt(i);
            if (c instanceof JScrollPane sp) {
                sp.setTransferHandler(h);
                var v = (sp.getViewport() != null) ? sp.getViewport().getView() : null;
                if (v instanceof JComponent jc) {
                    jc.setTransferHandler(null); // cells handle their own DnD
                }
            }
        }
    }


    /** Return the currently selected LaunchTabPanel or null. */
    private LaunchTabPanel currentPanel() {
        int idx = tabs.getSelectedIndex();
        if (idx < 0) return null;
        return panelFromTabIndex(idx);
    }

    /** Resolve LaunchTabPanel from a tab index (handles JScrollPane wrapper). */
    private LaunchTabPanel panelFromTabIndex(int index) {
        if (index < 0 || index >= tabs.getTabCount()) return null;
        java.awt.Component c = tabs.getComponentAt(index);
        if (c instanceof LaunchTabPanel p) return p;
        if (c instanceof JScrollPane sp) {
            java.awt.Component v = (sp.getViewport() != null) ? sp.getViewport().getView() : null;
            if (v instanceof LaunchTabPanel p) return p;
        }
        return null;
    }

    private final class TabReorderMouse extends java.awt.event.MouseAdapter {
        private int dragFrom = -1;
        @Override public void mousePressed(java.awt.event.MouseEvent e) {
            if (!SwingUtilities.isLeftMouseButton(e)) return;
            dragFrom = tabs.indexAtLocation(e.getX(), e.getY());
        }
        @Override public void mouseDragged(java.awt.event.MouseEvent e) {
            if (dragFrom < 0) return;
            int over = tabs.indexAtLocation(e.getX(), e.getY());
            if (over >= 0 && over != dragFrom) {
                moveTab(dragFrom, over);
                dragFrom = over; // keep dragging smoothly
            }
        }
        @Override public void mouseReleased(java.awt.event.MouseEvent e) { dragFrom = -1; }
    }

    private void moveTab(int from, int to) {
        if (from < 0 || to < 0 || from == to) return;
        if (to >= tabs.getTabCount()) return;

        var comp    = tabs.getComponentAt(from);
        var title   = tabs.getTitleAt(from);
        var icon    = tabs.getIconAt(from);
        var tooltip = tabs.getToolTipTextAt(from);
        var enabled = tabs.isEnabledAt(from);

        tabs.removeTabAt(from);
        tabs.insertTab(title, icon, comp, tooltip, to);
        tabs.setEnabledAt(to, enabled);
        tabs.setSelectedIndex(to);
    }

    private void moveSelectedTabUp() {
        int idx = tabs.getSelectedIndex();
        if (idx > 0) moveTab(idx, idx - 1);
    }

    private void moveSelectedTabDown() {
        int idx = tabs.getSelectedIndex();
        if (idx >= 0 && idx < tabs.getTabCount() - 1) moveTab(idx, idx + 1);
    }

    public static void main(String[] args)
    {
        JLaunchPad lp = new JLaunchPad();
        lp.setVisible(true);
    }

    private void addSingleAppViaChooser() {
        LaunchTabPanel panel = currentPanel();
        if (panel == null) return;

        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Choose an Application (.app)");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        File def = new File("/Applications");
        if (!def.exists()) def = new File(System.getProperty("user.home"));
        fc.setCurrentDirectory(def);

        int res = fc.showOpenDialog(this);
        if (res != JFileChooser.APPROVE_OPTION) return;

        File chosen = fc.getSelectedFile();
        if (chosen == null || !chosen.getName().endsWith(".app")) {
            JOptionPane.showMessageDialog(this, "Please select a .app bundle.",
                "Invalid Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        File real = LaunchPadUtils.resolveRealAppBundle(chosen);
        if (real == null) {
            JOptionPane.showMessageDialog(this, "Could not resolve that application.",
                "Not a Valid App", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String canon = LaunchPadUtils.canonicalPath(real);
        var where = LaunchPadUtils.findApp(tabs, canon);
        if (where != null) {
            JOptionPane.showMessageDialog(
                this,
                "That application already exists on tab \"" + where.tabName + "\" (cell " + where.cellIndex + ").",
                "Already Exists", JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        panel.ensureCapacityFor(1);
        LaunchCell empty = panel.findFirstEmptyCell();
        if (empty == null) { panel.addRows(1); empty = panel.findFirstEmptyCell(); }

        AppComponent app = MacAppUtils.createAppComponent(real);
        if (app != null) empty.setApp(app);
    }

    private void importAppsFromFolderViaChooser() {
        LaunchTabPanel panel = currentPanel();
        if (panel == null) return;

        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Choose a Folder to Import Applications From");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        File def = new File("/Applications");
        if (!def.exists()) def = new File(System.getProperty("user.home"));
        fc.setCurrentDirectory(def);

        int res = fc.showOpenDialog(this);
        if (res != JFileChooser.APPROVE_OPTION) return;

        File folder = fc.getSelectedFile();
        if (folder == null || !folder.isDirectory()) return;

        boolean topLevelOnly = true; // avoids helpers/updaters deep inside bundles
        List<File> apps = findAllApps(folder.toPath(), topLevelOnly);

        if (apps.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No applications (.app) found in that folder.",
                "Nothing Found", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int skippedDuplicates = 0;
        int imported = 0;

        List<File> toImport = new ArrayList<>();
        for (File f : apps) {
            String nameLower = f.getName().toLowerCase(java.util.Locale.ROOT);
            if (nameLower.contains("uninstall")) continue;
            String canon = LaunchPadUtils.canonicalPath(f);
            if (LaunchPadUtils.findApp(tabs, canon) != null) {
                skippedDuplicates++;
                continue;
            }
            toImport.add(f);
        }

        panel.ensureCapacityFor(toImport.size());

        for (File f : toImport) {
            LaunchCell empty = panel.findFirstEmptyCell();
            if (empty == null) {
                panel.addRows(1);
                empty = panel.findFirstEmptyCell();
                if (empty == null) break;
            }
            AppComponent app = MacAppUtils.createAppComponent(f);
            if (app != null) {
                empty.setApp(app);
                imported++;
            }
        }

        if (skippedDuplicates > 0) {
            JOptionPane.showMessageDialog(
                this,
                "One or more apps were not imported as they already exist in LaunchPad.",
                "Some Skipped",
                JOptionPane.INFORMATION_MESSAGE
            );
        } else if (imported == 0) {
            JOptionPane.showMessageDialog(
                this,
                "No applications were imported.",
                "Nothing Imported",
                JOptionPane.INFORMATION_MESSAGE
            );
        }
    }

    // Walk roots and return real *.app bundles (handles /Applications + /System/Applications)
    private List<File> findAllApps(Path chosenRoot, boolean topLevelOnly) {
        Set<String> seen = new LinkedHashSet<>();
        List<File> result = new ArrayList<>();

        List<Path> roots = new ArrayList<>();
        roots.add(chosenRoot);

        try {
            String chosenReal = chosenRoot.toRealPath().toString();
            if ("/Applications".equals(chosenReal)) {
                Path sysApps = Paths.get("/System/Applications");
                if (Files.isDirectory(sysApps)) roots.add(sysApps);
                Path sysUtils = Paths.get("/System/Applications/Utilities");
                if (Files.isDirectory(sysUtils)) roots.add(sysUtils);
            }
        } catch (Exception ignore) {}

        int maxDepth = topLevelOnly ? 1 : Integer.MAX_VALUE;

        for (Path root : roots) {
            try (Stream<Path> s =
                     Files.walk(root, maxDepth, FileVisitOption.FOLLOW_LINKS)) {

                s.filter(p -> {
                        var name = (p.getFileName() != null) ? p.getFileName().toString() : "";
                        return name.endsWith(".app");
                    })
                 .map(p -> LaunchPadUtils.resolveRealAppBundle(p.toFile()))
                 .filter(java.util.Objects::nonNull)
                 .filter(f -> {
                     String path = f.getAbsolutePath();
                     return !path.contains("/Contents/Library/LoginItems/")
                         && !path.contains("/Contents/Helpers/");
                 })
                 .filter(MacAppUtils::isLikelyUserFacingApp)
                 .forEach(f -> {
                     String key = LaunchPadUtils.canonicalPath(f);
                     if (seen.add(key)) result.add(f);
                 });

            } catch (Exception ignore) {}
        }

        return result;
    }

    private void packCurrentTab() {
        LaunchTabPanel panel = currentPanel();
        if (panel == null) return;
        panel.packIcons();
    }

    /**
     * Shared drop handler: accepts drops on the tabbedpane AND on per-tab scrollpanes.
     * It preserves your previous behavior (drop anywhere on a tab inserts into first empty cell).
     */
    private final class DropToTabHandler extends TransferHandler {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean canImport(TransferSupport support) {
            return support.isDataFlavorSupported(LaunchpadTransferable.DRAG_PAYLOAD_FLAVOR)
                || support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                || support.isDataFlavorSupported(DataFlavor.stringFlavor);
        }

        @Override
        public boolean importData(TransferSupport support)
        {
            try
            {
                Transferable t = support.getTransferable();

                // Internal payload first (so we can allow MOVE + clear source)
                DragPayload payload = null;
                if (t.isDataFlavorSupported(LaunchpadTransferable.DRAG_PAYLOAD_FLAVOR))
                {
                    payload = (DragPayload) t.getTransferData(LaunchpadTransferable.DRAG_PAYLOAD_FLAVOR);
                }

                // Determine path from: internal payload -> file list -> string
                String path = null;
                if (payload != null && payload.appPath != null) {
                    path = payload.appPath;
                } else if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    @SuppressWarnings("unchecked")
                    java.util.List<java.io.File> files =
                        (java.util.List<java.io.File>) t.getTransferData(DataFlavor.javaFileListFlavor);
                    if (files != null && !files.isEmpty()) {
                        path = files.get(0).getAbsolutePath();
                    }
                } else if (t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                    path = (String) t.getTransferData(DataFlavor.stringFlavor);
                }
                if (path == null) return false;

                File dropped = new File(path);
                if (!dropped.exists()) return false;

                // Resolve to the *.app root and real path (handles /Applications → /System/Applications)
                File real = LaunchPadUtils.resolveRealAppBundle(dropped);
                if (real == null || !real.getName().endsWith(".app")) return false;

                // Determine target tab:
                // - If drop target is the tabbedpane, location is already in tabs' coords
                // - If it's inside a tab's scrollpane/viewport, convert to tabs' coords
                java.awt.Point p = (support.getDropLocation() != null) ? support.getDropLocation().getDropPoint() : null;
                if (p == null) return false;
                java.awt.Component targetComp = (java.awt.Component) support.getComponent();
                java.awt.Point ptInTabs = SwingUtilities.convertPoint(targetComp, p, tabs);

                int targetIndex = tabs.indexAtLocation(ptInTabs.x, ptInTabs.y);
                if (targetIndex < 0) targetIndex = tabs.getSelectedIndex();
                if (targetIndex < 0) return false;

                LaunchTabPanel panel = panelFromTabIndex(targetIndex);
                if (panel == null) return false;

                // Ensure we have space (grow if needed)
                panel.ensureCapacityFor(1);
                LaunchCell empty = panel.findFirstEmptyCell();
                if (empty == null) {
                    panel.addRows(1);
                    empty = panel.findFirstEmptyCell();
                    if (empty == null) return false;
                }

                // Duplicate check: against CANONICAL REAL PATH
                String canonReal = LaunchPadUtils.canonicalPath(real);
                var where = LaunchPadUtils.findApp(tabs, canonReal);
                if (where != null)
                {
                    boolean isSameSource =
                        payload != null &&
                        payload.sourceCell != null &&
                        tabs.getComponentAt(where.tabIndex) != null &&
                        panelFromTabIndex(where.tabIndex) != null &&
                        panelFromTabIndex(where.tabIndex).getComponent(where.cellIndex) == payload.sourceCell;

                    if (!isSameSource)
                    {
                        JOptionPane.showMessageDialog(
                            tabs,
                            "Application " + real.getName() +
                            " already exists in the \"" + where.tabName + "\" Category (cell " + where.cellIndex + ").",
                            "Duplicate App",
                            JOptionPane.INFORMATION_MESSAGE
                        );
                        return false;
                    }
                }

                AppComponent app = MacAppUtils.createAppComponent(real);
                if (app != null)
                {
                    empty.setApp(app);
                    // If this was an internal MOVE, clear the source cell
                    if (payload != null && payload.sourceCell != null) {
                        payload.sourceCell.clear();
                    }
                    return true;
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
            return false;
        }
    }
}
