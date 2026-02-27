package org.duhen.dglyphs;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class GlyphNotificationListener extends NotificationListenerService {

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (!sbn.isClearable()) return;

        SharedPreferences prefs = getSharedPreferences(getString(R.string.pref_file), MODE_PRIVATE);

        if (prefs.getBoolean("master_allow", false)) {

            if (SleepGuard.isBlocked(prefs)) return;

            if (prefs.getBoolean("lockscreen_only", false)) {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);

                boolean isScreenOn = pm != null && pm.isInteractive();
                boolean isLocked = km != null && km.isKeyguardLocked();

                if (isScreenOn && !isLocked) {
                    return;
                }
            }

            Intent intent = new Intent(this, FlipToGlyphService.class);
            intent.setAction(FlipToGlyphService.ACTION_NOTIFICATION_GLYPH);
            startService(intent);
        }
    }
}