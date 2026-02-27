package org.duhen.dglyphs;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.telephony.TelephonyManager;

public class CallReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(intent.getAction())) {
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences(context.getString(R.string.pref_file), Context.MODE_PRIVATE);

        if (!prefs.getBoolean("master_allow", false)) return;
        if (SleepGuard.isBlocked(prefs)) return;

        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);

        if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
            if (prefs.getBoolean("lockscreen_only", false)) {
                PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                KeyguardManager km = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);

                boolean isScreenOn = (pm != null && pm.isInteractive());
                boolean isLocked = (km != null && km.isKeyguardLocked());

                if (isScreenOn && !isLocked) {
                    return;
                }
            }
            Intent i = new Intent(context, FlipToGlyphService.class);
            i.setAction(FlipToGlyphService.ACTION_CALL_GLYPH);
            context.startService(i);
        }
        else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state) ||
                TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
            Intent i = new Intent(context, FlipToGlyphService.class);
            i.setAction(FlipToGlyphService.ACTION_STOP_CALL_GLYPH);
            context.startService(i);
        }
    }
}