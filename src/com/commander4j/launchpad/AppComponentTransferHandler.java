package com.commander4j.launchpad;

import java.awt.datatransfer.Transferable;
import javax.swing.JComponent;
import javax.swing.TransferHandler;

public class AppComponentTransferHandler extends TransferHandler {
    private static final long serialVersionUID = 1L;

    @Override
    protected Transferable createTransferable(JComponent c) {
        if (c instanceof AppComponent app) {
            var sourceCell = (LaunchCell) javax.swing.SwingUtilities
                .getAncestorOfClass(LaunchCell.class, app);
            return new LaunchpadTransferable(
                app.getAppPath(),
                new DragPayload(app.getAppPath(), sourceCell)
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
        // No-op: the target's importData() performs source clearing only if the MOVE succeeds.
    }
}
