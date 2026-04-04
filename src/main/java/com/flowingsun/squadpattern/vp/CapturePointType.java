package com.flowingsun.squadpattern.vp;

public enum CapturePointType {
    VICTORY(true, 0, 0),
    NORMAL(false, 5, 3),
    AMMO(false, 11, 0),
    OIL(false, 0, 11);

    private final boolean showInTopHud;
    private final int ammoPerCycle;
    private final int oilPerCycle;

    CapturePointType(boolean showInTopHud, int ammoPerCycle, int oilPerCycle) {
        this.showInTopHud = showInTopHud;
        this.ammoPerCycle = ammoPerCycle;
        this.oilPerCycle = oilPerCycle;
    }

    public boolean showInTopHud() {
        return showInTopHud;
    }

    public int ammoPerCycle() {
        return ammoPerCycle;
    }

    public int oilPerCycle() {
        return oilPerCycle;
    }
}
