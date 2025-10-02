package com.commander4j.launchpad;

/*******************************************************************************
 * Title:        Commander4j
 * Description:  TransferHandler for LaunchPad Cells (MOVE-safe, resolves real bundles)
 * Author:       Dave (with ChatGPT assistance)
 * License:      GNU General Public License
 *******************************************************************************/

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;

public class LaunchCellTransferHandler extends TransferHandler {
    private static final long serialVersionUID = 1L;
    private final LaunchCell cell;

    public LaunchCellTransferHandler(LaunchCell cell) {
        this.cell = cell;
    }

    @Override
    public boolean canImport(TransferSupport support) {
        // Only accept drops into empty cells
        if (!cell.isEmpty()) return false;
        return support.isDataFlavorSupported(LaunchpadTransferable.DRAG_PAYLOAD_FLAVOR)
            || support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
            || support.isDataFlavorSupported(DataFlavor.stringFlavor);
    }

    @Override
    public boolean importData(TransferSupport support) {
        if (!canImport(support)) return false;

        try {
            final Transferable t = support.getTransferable();

            // 1) Internal payload? → Treat as MOVE, no duplicate checks.
            DragPayload payload = null;
            if (t.isDataFlavorSupported(LaunchpadTransferable.DRAG_PAYLOAD_FLAVOR)) {
                try {
                    payload = (DragPayload) t.getTransferData(LaunchpadTransferable.DRAG_PAYLOAD_FLAVOR);
                } catch (UnsupportedFlavorException | IOException ignore) {}
            }

            if (payload != null && payload.sourceCell != null) {
                // MOVE the existing component instance safely, without clearing popups from it.
                AppComponent moving = payload.sourceCell.detachAppForMove();
                if (moving == null) return false;

                cell.setApp(moving);
                return true;
            }

            // 2) External drop (Finder / text path) → resolve file to real *.app and duplicate-check.
            File f = null;
            if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                try {
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);
                    if (files != null && !files.isEmpty()) f = files.get(0);
                } catch (UnsupportedFlavorException | IOException ignore) {}
            } else if (t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                try {
                    String path = (String) t.getTransferData(DataFlavor.stringFlavor);
                    if (path != null && !path.isBlank()) f = new File(path);
                } catch (UnsupportedFlavorException | IOException ignore) {}
            }
            if (f == null || !f.exists()) return false;

            // Resolve to the real bundle (handles /Applications → /System/Applications, and inner paths)
            File real = LaunchPadUtils.resolveRealAppBundle(f);
            if (real == null || !real.getName().endsWith(".app")) return false;

            // Duplicate check across all tabs (by canonical real path)
            JTabbedPane tabs = (JTabbedPane) SwingUtilities.getAncestorOfClass(JTabbedPane.class, cell);
            String dropCanon = LaunchPadUtils.canonicalPath(real);
            LaunchPadUtils.Location where = LaunchPadUtils.findApp(tabs, dropCanon);
            if (where != null) {
                JOptionPane.showMessageDialog(
                    tabs,
                    "Application " + real.getName() +
                    " already exists on the \"" + where.tabName + "\" tab (cell " + where.cellIndex + ").",
                    "Duplicate App",
                    JOptionPane.INFORMATION_MESSAGE
                );
                return false;
            }

            // Create and place
            AppComponent app = MacAppUtils.createAppComponent(real);
            if (app == null) return false;
            cell.setApp(app);
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    protected Transferable createTransferable(JComponent c) {
        if (cell.getApp() != null) {
            return new LaunchpadTransferable(
                cell.getApp().getAppPath(),
                new DragPayload(cell.getApp().getAppPath(), cell)
            );
        }
        return null;
    }

    @Override
    public int getSourceActions(JComponent c) {
        return MOVE;
    }

    @Override
    protected void exportDone(JComponent source, Transferable data, int action) {
        // no-op; MOVE is handled inline via detach+setApp
    }
}
