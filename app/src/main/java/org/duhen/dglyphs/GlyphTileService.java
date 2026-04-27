package org.duhen.dglyphs;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class GlyphTileService extends TileService {

    private static final long LIMITER_DELAY_MS = 90_000L;
    private static final int LIMITER_BRIGHTNESS = Math.round(4095 * 0.7f);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private Runnable limiterTask;

    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener =
            (sharedPreferences, key) -> {
                if ("master_allow".equals(key) || "is_random_active".equals(key)) {
                    syncTile();
                }
            };

    @Override
    public void onStartListening() {
        super.onStartListening();
        prefs = getSharedPreferences(getString(R.string.pref_file), MODE_PRIVATE);
        prefs.registerOnSharedPreferenceChangeListener(prefListener);
        syncTile();
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
        if (prefs != null) prefs.unregisterOnSharedPreferenceChangeListener(prefListener);
    }

    @Override
    public void onClick() {
        super.onClick();
        if (prefs == null)
            prefs = getSharedPreferences(getString(R.string.pref_file), MODE_PRIVATE);

        if (!prefs.getBoolean("master_allow", false) || prefs.getBoolean("is_random_active", false)) {
            syncTile();
            return;
        }

        Tile tile = getQsTile();
        if (tile == null) return;

        boolean currentActive = prefs.getBoolean("is_glyph_active", false);
        boolean newState = !currentActive;

        prefs.edit().putBoolean("is_glyph_active", newState).apply();

        if (newState) {
            int brightness = prefs.getInt("torch_brightness", 2048);
            updateHardware(brightness);
            scheduleLimiter(brightness);
        } else {
            cancelLimiter();
            updateHardware(0);
        }

        tile.setState(newState ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }

    private void scheduleLimiter(int brightness) {
        cancelLimiter();
        if (!prefs.getBoolean("torch_limiter_enabled", false)) return;
        if (brightness < 4095) return;
        limiterTask = () -> {
            updateHardware(LIMITER_BRIGHTNESS);
        };
        handler.postDelayed(limiterTask, LIMITER_DELAY_MS);
    }

    private void cancelLimiter() {
        if (limiterTask != null) {
            handler.removeCallbacks(limiterTask);
            limiterTask = null;
        }
    }

    private void syncTile() {
        if (prefs == null)
            prefs = getSharedPreferences(getString(R.string.pref_file), MODE_PRIVATE);

        boolean isAllowed = prefs.getBoolean("master_allow", false);
        boolean isRandomActive = prefs.getBoolean("is_random_active", false);
        boolean isMeActive = prefs.getBoolean("is_glyph_active", false);

        Tile tile = getQsTile();
        if (tile == null) return;

        tile.setLabel(getString(R.string.tile_light_label));

        if (!isAllowed || isRandomActive) {
            tile.setState(Tile.STATE_UNAVAILABLE);
            if (isMeActive) {
                prefs.edit().putBoolean("is_glyph_active", false).apply();
                cancelLimiter();
                updateHardware(0);
            }
        } else {
            tile.setState(isMeActive ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        }

        tile.updateTile();
    }

    private void updateHardware(int val) {
        for (GlyphManager.Glyph g : GlyphManager.Glyph.values()) GlyphManager.setBrightness(g, val);
    }
}