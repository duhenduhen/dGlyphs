package org.duhen.dglyphs;

import android.content.SharedPreferences;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class GlyphTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        syncTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        SharedPreferences prefs = getSharedPreferences(getString(R.string.pref_file), MODE_PRIVATE);

        if (!prefs.getBoolean("master_allow", false)) {
            syncTile();
            return;
        }

        boolean currentActive = (getQsTile().getState() == Tile.STATE_ACTIVE);
        boolean newState = !currentActive;

        updateHardware(newState ? prefs.getInt("brightness", 2048) : 0);

        Tile tile = getQsTile();
        tile.setState(newState ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }

    private void syncTile() {
        SharedPreferences prefs = getSharedPreferences(getString(R.string.pref_file), MODE_PRIVATE);
        boolean isAllowed = prefs.getBoolean("master_allow", false);
        Tile tile = getQsTile();

        if (tile != null) {
            tile.setLabel(getString(R.string.tile_light_label));

            if (!isAllowed) {
                tile.setState(Tile.STATE_UNAVAILABLE);
            } else {
                tile.setState(Tile.STATE_INACTIVE);
                updateHardware(0);
            }

            tile.updateTile();
        }
    }

    private void updateHardware(int val) {
        for (GlyphManager.Glyph g : GlyphManager.Glyph.values()) {
            GlyphManager.setBrightness(g, val);
        }
    }
}