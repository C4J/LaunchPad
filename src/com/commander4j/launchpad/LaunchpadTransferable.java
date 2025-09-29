package com.commander4j.launchpad;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

/** Transferable that supports both stringFlavor (path) and a JVM-local DragPayload. */
public class LaunchpadTransferable implements Transferable {
    public static final DataFlavor DRAG_PAYLOAD_FLAVOR;
    static {
        try {
            DRAG_PAYLOAD_FLAVOR = new DataFlavor(
                DataFlavor.javaJVMLocalObjectMimeType + ";class=" + DragPayload.class.getName()
            );
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private final String path;
    private final DragPayload payload;

    public LaunchpadTransferable(String path, DragPayload payload) {
        this.path = path;
        this.payload = payload;
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[] { DataFlavor.stringFlavor, DRAG_PAYLOAD_FLAVOR };
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return DataFlavor.stringFlavor.equals(flavor) || DRAG_PAYLOAD_FLAVOR.equals(flavor);
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
        if (DataFlavor.stringFlavor.equals(flavor)) return path;
        if (DRAG_PAYLOAD_FLAVOR.equals(flavor)) return payload;
        throw new UnsupportedFlavorException(flavor);
    }
}
