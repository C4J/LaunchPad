package com.commander4j.launchpad;

import java.io.Serializable;

/** Carries info about an internal drag (source cell + path). */
public final class DragPayload implements Serializable {
    private static final long serialVersionUID = 1L;
    public final String appPath;
    public final LaunchCell sourceCell;

    public DragPayload(String appPath, LaunchCell sourceCell) {
        this.appPath = appPath;
        this.sourceCell = sourceCell;
    }
}
