package org.duhen.dglyphs;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.Vibrator;
import java.util.Calendar;

public class FlipToGlyphService extends Service implements SensorEventListener {

    public static final String ACTION_NOTIFICATION_GLYPH = "org.duhen.dglyphs.ACTION_NOTIFICATION_GLYPH";
    public static final String ACTION_CALL_GLYPH = "org.duhen.dglyphs.ACTION_CALL_GLYPH";
    public static final String ACTION_STOP_CALL_GLYPH = "org.duhen.dglyphs.ACTION_STOP_CALL_GLYPH";

    private SensorManager sensorManager;
    private AudioManager audioManager;
    private Vibrator vibrator;
    private SharedPreferences prefs;
    private PowerManager.WakeLock wakeLock;

    private boolean isFaceDown, isProximityCovered, isActive, isRinging;
    private int originalRingerMode;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable activationRunnable;

    @Override
    public void onCreate() {
        super.onCreate();
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        prefs = getSharedPreferences(getString(R.string.pref_file), MODE_PRIVATE);
        vibrator = getSystemService(Vibrator.class);

        Sensor accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor prox = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        if (accel != null) sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI);
        if (prox != null) sensorManager.registerListener(this, prox, SensorManager.SENSOR_DELAY_UI);

        wakeLock = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dGlyphs:Lock");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!prefs.getBoolean("master_allow", false) || !prefs.getBoolean("flip_enabled", false)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_NOTIFICATION_GLYPH: triggerEffect("glyph_blink_style"); break;
                case ACTION_CALL_GLYPH: startCallBlinking(); break;
                case ACTION_STOP_CALL_GLYPH: stopCallBlinking(); break;
            }
        }
        return START_STICKY;
    }

    private void triggerEffect(String prefKey) {
        if (isSleepTime()) return;
        new Thread(() -> runEffect(prefs.getString(prefKey, "static"))).start();
    }

    private void startCallBlinking() {
        if (isRinging) return;
        isRinging = true;
        new Thread(() -> {
            String style = prefs.getString("call_style_value", "static");
            while (isRinging) {
                runEffect(style);
                SystemClock.sleep(100);
            }
        }).start();
    }

    private void stopCallBlinking() {
        isRinging = false;
        updateHardware(0);
    }

    private void runEffect(String style) {
        if (wakeLock != null) wakeLock.acquire(1000);
        GlyphEffects.run(style, prefs.getInt("brightness", 2048), vibrator);
    }

    private void updateHardware(int val) {
        for (GlyphManager.Glyph g : GlyphManager.Glyph.values()) GlyphManager.setBrightness(g, val);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) isFaceDown = event.values[2] < -8.5;
        else if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) isProximityCovered = event.values[0] < event.sensor.getMaximumRange();

        if (isFaceDown && isProximityCovered) {
            if (!isActive && activationRunnable == null) {
                activationRunnable = this::activateFlipMode;
                handler.postDelayed(activationRunnable, 1500);
            }
        } else {
            if (activationRunnable != null) {
                handler.removeCallbacks(activationRunnable);
                activationRunnable = null;
            }
            if (isActive) deactivateFlipMode();
        }
    }

    private void activateFlipMode() {
        isActive = true;
        activationRunnable = null;
        originalRingerMode = audioManager.getRingerMode();
        audioManager.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
        triggerEffect("flip_style_value");
    }

    private void deactivateFlipMode() {
        isActive = false;
        audioManager.setRingerMode(originalRingerMode);
        updateHardware(0);
    }

    private boolean isSleepTime() {
        if (!prefs.getBoolean("sleep_mode_enabled", false)) return false;
        try {
            String[] s = prefs.getString("sleep_start", "23:00").split(":");
            String[] e = prefs.getString("sleep_end", "07:00").split(":");
            int startMin = Integer.parseInt(s[0]) * 60 + Integer.parseInt(s[1]);
            int endMin = Integer.parseInt(e[0]) * 60 + Integer.parseInt(e[1]);
            int nowMin = Calendar.getInstance().get(Calendar.HOUR_OF_DAY) * 60 + Calendar.getInstance().get(Calendar.MINUTE);
            return startMin < endMin ? (nowMin >= startMin && nowMin <= endMin) : (nowMin >= startMin || nowMin <= endMin);
        } catch (Exception ex) { return false; }
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        isRinging = false;
        if (isActive) audioManager.setRingerMode(originalRingerMode);
        if (sensorManager != null) sensorManager.unregisterListener(this);
        handler.removeCallbacksAndMessages(null);
        updateHardware(0);
        super.onDestroy();
    }
}