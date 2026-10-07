package com.example.beautify;

public final class BeautyPanelParams {
    public boolean enabled = true;
    public float smooth = BeautyDefaults.SMOOTH;

    public static BeautyPanelParams defaults() {
        BeautyPanelParams p = new BeautyPanelParams();
        p.enabled = true;
        p.smooth = BeautyDefaults.SMOOTH;
        return p;
    }

    public boolean shouldProcess() {
        return enabled && smooth > 0.01f;
    }
}
