package org.duhen.dglyphs;

import android.content.Intent;
import android.content.SharedPreferences;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class GlyphNotificationListener extends NotificationListenerService {

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (!sbn.isClearable()) return;

        SharedPreferences prefs = getSharedPreferences(getString(R.string.pref_file), MODE_PRIVATE);
        if (prefs.getBoolean("master_allow", false)) {
            Intent intent = new Intent(this, FlipToGlyphService.class);
            intent.setAction(FlipToGlyphService.ACTION_NOTIFICATION_GLYPH);
            startService(intent);
        }
    }
}