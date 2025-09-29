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
        if (!cell.isEmpty()) return false; // only empty cells accept drops
        return support.isDataFlavorSupported(LaunchpadTransferable.DRAG_PAYLOAD_FLAVOR)
            || support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
            || support.isDataFlavorSupported(DataFlavor.stringFlavor);
    }

    @Override
    public boolean importData(TransferSupport support) {
        if (!canImport(support)) return false;

        try {
            Transferable t = support.getTransferable();

            // Internal payload
            DragPayload payload = null;
            if (t.isDataFlavorSupported(LaunchpadTransferable.DRAG_PAYLOAD_FLAVOR)) {
                try {
                    payload = (DragPayload) t.getTransferData(LaunchpadTransferable.DRAG_PAYLOAD_FLAVOR);
                } catch (UnsupportedFlavorException | IOException ignore) {}
            }

            // Determine file being dropped
            File f = null;
            if (payload != null && payload.appPath != null) {
                f = new File(payload.appPath);
            } else if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
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

            if (f == null || !f.exists() || !f.getName().endsWith(".app")) return false;

            // Resolve to the real bundle path (handles /Applications → /System/Applications)
            File real = LaunchPadUtils.resolveRealAppBundle(f);
            if (real == null) return false;

            // Duplicate check across tabs (use canon of real bundle)
            JTabbedPane tabs = (JTabbedPane) SwingUtilities.getAncestorOfClass(JTabbedPane.class, cell);
            String dropCanon = LaunchPadUtils.canonicalPath(real);

            // payloadCanon with fallback to source cell path
            String payloadCanon = null;
            if (payload != null) {
                String p = payload.appPath;
                if ((p == null || p.isBlank())
                    && payload.sourceCell != null
                    && payload.sourceCell.getApp() != null) {
                    p = payload.sourceCell.getApp().getAppPath();
                }
                if (p != null && !p.isBlank()) {
                    payloadCanon = LaunchPadUtils.canonicalPath(new File(p));
                }
            }

            boolean movingSameInstance =
                (payload != null) && (payloadCanon != null) && dropCanon.equals(payloadCanon);

            LaunchPadUtils.Location where = LaunchPadUtils.findApp(tabs, dropCanon);

            if (where != null) {
                if (movingSameInstance) {
                    boolean isSameSource = false;
                    if (payload.sourceCell != null
                        && tabs.getComponentAt(where.tabIndex) instanceof LaunchTabPanel panel
                        && where.cellIndex >= 0 && where.cellIndex < panel.getComponentCount()
                        && panel.getComponent(where.cellIndex) == payload.sourceCell) {
                        isSameSource = true;
                    }
                    if (!isSameSource) {
                        JOptionPane.showMessageDialog(
                            tabs,
                            "Application " + real.getName() +
                                " already exists on the \"" + where.tabName + "\" tab (cell " + where.cellIndex + ").",
                            "Duplicate App",
                            JOptionPane.INFORMATION_MESSAGE
                        );
                        return false;
                    }
                } else {
                    JOptionPane.showMessageDialog(
                        tabs,
                        "Application " + real.getName() +
                            " already exists on the \"" + where.tabName + "\" tab (cell " + where.cellIndex + ").",
                        "Duplicate App",
                        JOptionPane.INFORMATION_MESSAGE
                    );
                    return false;
                }
            }

            AppComponent app = MacAppUtils.createAppComponent(real);
            if (app == null) return false;

            cell.setApp(app);

            // Clear source on internal MOVE
            if (movingSameInstance && payload.sourceCell != null && payload.sourceCell != cell) {
                payload.sourceCell.clear();
            }

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
        // no-op (we clear source explicitly in importData when needed)
    }
}
