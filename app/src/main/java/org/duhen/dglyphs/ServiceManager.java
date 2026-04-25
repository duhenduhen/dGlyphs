package org.duhen.dglyphs;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class ServiceManager {

    private ServiceManager() {
    }

    public static void startAll(Context context, SharedPreferences prefs) {
        startIfEnabled(context, prefs, "flip_enabled", FlipToGlyphService.class);
        startIfEnabled(context, prefs, "battery_glyph_enabled", BatteryGlyphService.class);
        startIfEnabled(context, prefs, "volume_glyph_enabled", VolumeGlyphService.class);
    }

    public static void stopAll(Context context) {
        context.stopService(new Intent(context, FlipToGlyphService.class));
        context.stopService(new Intent(context, BatteryGlyphService.class));
        context.stopService(new Intent(context, VolumeGlyphService.class));
    }

    public static void startIfEnabled(Context context, SharedPreferences prefs, String prefKey, Class<?> serviceClass) {
        if (prefs.getBoolean(prefKey, false)) {
            context.startService(new Intent(context, serviceClass));
        }
    }

    public static void stop(Context context, Class<?> serviceClass) {
        context.stopService(new Intent(context, serviceClass));
    }
}