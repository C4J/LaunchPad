package com.commander4j.launchpad;

/*******************************************************************************
 * Title:        Commander4j
 * Description:  LaunchPad Tab Panel (grid using MigLayout; supports growth + pack)
 * Author:       Dave (with ChatGPT assistance)
 * License:      GNU General Public License
 *******************************************************************************/

import javax.swing.JPanel;
import net.miginfocom.swing.MigLayout;

public class LaunchTabPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    // Columns and cell size must match the rest of the app
    private static final int COLS = 7;
    private static final int CELL_SIZE = 150;

    // Start rows; you can change this default
    private int rows = 7;

    public LaunchTabPanel() {
        super(new MigLayout(
            "insets 0, gap 0, hidemode 3, wrap " + COLS,
            repeatCols(COLS),
            ""               // let rows grow as needed
        ));
        addRows(rows); // initial capacity
    }

    private static String repeatCols(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append("[").append(CELL_SIZE).append("px!]");
        }
        return sb.toString();
    }

    /** Add N more full rows of empty cells. */
    public void addRows(int n) {
        if (n <= 0) return;
        int cellsToAdd = COLS * n;
        for (int i = 0; i < cellsToAdd; i++) {
            add(new LaunchCell(), "w " + CELL_SIZE + "!, h " + CELL_SIZE + "!, grow 0");
        }
        rows += n;
        revalidate();
        repaint();
    }

    /** Ensure there is at least one empty cell, growing the grid if needed. */
    public void ensureCapacityFor(int additionalNeeded) {
        int empties = countEmptyCells();
        if (empties >= additionalNeeded) return;

        int missing = additionalNeeded - empties;
        int rowsNeeded = (int)Math.ceil(missing / (double)COLS);
        addRows(Math.max(1, rowsNeeded));
    }

    public LaunchCell findFirstEmptyCell() {
        for (int i = 0; i < getComponentCount(); i++) {
            if (getComponent(i) instanceof LaunchCell cell) {
                if (cell.isEmpty()) return cell;
            }
        }
        return null;
    }

    public int countEmptyCells() {
        int c = 0;
        for (int i = 0; i < getComponentCount(); i++) {
            if (getComponent(i) instanceof LaunchCell cell) {
                if (cell.isEmpty()) c++;
            }
        }
        return c;
    }

    /** Defragment: move all apps to the top-left, eliminating gaps. */
    public void packIcons() {
        java.util.List<AppComponent> apps = new java.util.ArrayList<>();
        // Collect and clear
        for (int i = 0; i < getComponentCount(); i++) {
            if (getComponent(i) instanceof LaunchCell cell) {
                AppComponent app = cell.getApp();
                if (app != null) apps.add(app);
                cell.clear();
            }
        }
        // Place sequentially
        int idx = 0;
        for (int i = 0; i < getComponentCount() && idx < apps.size(); i++) {
            if (getComponent(i) instanceof LaunchCell cell) {
                cell.setApp(apps.get(idx++));
            }
        }
        revalidate();
        repaint();
    }
    
 // in LaunchTabPanel
    public void ensureCellIndex(int index) {
        if (index < getComponentCount()) return;
        int missing = index - getComponentCount() + 1;
        int cols = 7; // keep in sync with COLS
        int rowsNeeded = (int)Math.ceil(missing / (double) cols);
        addRows(rowsNeeded);
    }

}
