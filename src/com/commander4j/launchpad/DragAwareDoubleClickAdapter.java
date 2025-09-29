package com.commander4j.launchpad;

import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;

/**
 * Starts a MOVE drag only after the mouse moves beyond a small threshold,
 * and still allows left-button double-click to be detected reliably.
 * Ignores popup triggers / right-clicks.
 */
public class DragAwareDoubleClickAdapter extends MouseAdapter {

    private static final int DRAG_THRESHOLD = 5; // pixels

    private final JComponent dragSource;
    private final Runnable onDoubleClick;

    private Point pressAt;       // where the left-press started
    private boolean dragging;    // whether we've initiated a drag

    public DragAwareDoubleClickAdapter(JComponent dragSource, Runnable onDoubleClick) {
        this.dragSource = dragSource;
        this.onDoubleClick = onDoubleClick;
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (!SwingUtilities.isLeftMouseButton(e) || e.isPopupTrigger()) return;
        pressAt = e.getPoint();
        dragging = false;
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (pressAt == null || dragging) return;
        if (!SwingUtilities.isLeftMouseButton(e)) return;

        int dx = Math.abs(e.getX() - pressAt.x);
        int dy = Math.abs(e.getY() - pressAt.y);
        if (dx >= DRAG_THRESHOLD || dy >= DRAG_THRESHOLD) {
            TransferHandler th = dragSource.getTransferHandler();
            if (th != null) {
                th.exportAsDrag(dragSource, e, TransferHandler.MOVE);
                dragging = true;
            }
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        // reset press point each gesture
        pressAt = null;
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        if (!SwingUtilities.isLeftMouseButton(e) || e.isPopupTrigger()) return;
        if (dragging) return; // don't consider double-click if we dragged
        if (e.getClickCount() == 2 && onDoubleClick != null) {
            onDoubleClick.run();
        }
    }
}
