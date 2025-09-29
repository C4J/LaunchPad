package com.commander4j.launchpad;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.MouseListener;

public final class SwingUtils {
    private SwingUtils() {}

    public static void addMouseListenerRecursively(Component c, MouseListener ml) {
        c.addMouseListener(ml);
        if (c instanceof Container cont) {
            for (Component child : cont.getComponents()) {
                addMouseListenerRecursively(child, ml);
            }
        }
    }
}
