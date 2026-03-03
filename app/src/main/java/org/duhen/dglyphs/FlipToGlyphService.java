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
import android.os.VibrationEffect;
import android.os.Vibrator;

public class FlipToGlyphService extends Service implements SensorEventListener {

    public static final String ACTION_NOTIFICATION_GLYPH = "org.duhen.dglyphs.ACTION_NOTIFICATION_GLYPH";
    public static final String ACTION_CALL_GLYPH = "org.duhen.dglyphs.ACTION_CALL_GLYPH";
    public static final String ACTION_STOP_CALL_GLYPH = "org.duhen.dglyphs.ACTION_STOP_CALL_GLYPH";
    private static final String PREF_BLINK_STYLE_VALUE = "glyph_blink_style";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SensorManager sensorManager;
    private AudioManager audioManager;
    private Vibrator vibrator;
    private SharedPreferences prefs;
    private PowerManager.WakeLock wakeLock;
    private boolean isFaceDown, isProximityCovered, isActive, isRinging;
    private int originalRingerMode;
    private Runnable activationRunnable;
    private Runnable callLoopRunnable;

    @Override
    public void onCreate() {
        super.onCreate();
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        vibrator = getSystemService(Vibrator.class);
        prefs = getSharedPreferences(getString(R.string.pref_file), MODE_PRIVATE);

        Sensor accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor prox = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        if (accel != null) sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI);
        if (prox != null) sensorManager.registerListener(this, prox, SensorManager.SENSOR_DELAY_UI);

        wakeLock = ((PowerManager) getSystemService(POWER_SERVICE))
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dGlyphs:Lock");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!prefs.getBoolean("master_allow", false) || !prefs.getBoolean("flip_enabled", false)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_NOTIFICATION_GLYPH:
                    triggerNotification();
                    break;
                case ACTION_CALL_GLYPH:
                    startCallLoop();
                    break;
                case ACTION_STOP_CALL_GLYPH:
                    stopCallLoop();
                    break;
            }
        }
        return START_STICKY;
    }

    private void triggerNotification() {
        if (SleepGuard.isBlocked(prefs)) return;
        if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire(3000);
        String style = prefs.getString(PREF_BLINK_STYLE_VALUE, "");
        if (!style.isEmpty()) GlyphEffects.play(this, "notification", style, vibrator, prefs.getInt("brightness", 2048));

    }

    private void startCallLoop() {
        if (isRinging) return;
        if (SleepGuard.isBlocked(prefs)) return;
        isRinging = true;
        scheduleNextCallCycle();
    }

    private void scheduleNextCallCycle() {
        if (!isRinging) return;
        String style = prefs.getString("call_style_value", "");
        if (style.isEmpty()) return;

        if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire(5000);
        GlyphEffects.play(this, "call", style, vibrator, prefs.getInt("brightness", 2048));

        callLoopRunnable = this::scheduleNextCallCycle;
        handler.postDelayed(callLoopRunnable, getCallStyleDuration(style));
    }

    private long getCallStyleDuration(String style) {
        return 4000;
    }

    private void stopCallLoop() {
        isRinging = false;
        if (callLoopRunnable != null) {
            handler.removeCallbacks(callLoopRunnable);
            callLoopRunnable = null;
        }
        GlyphEffects.stop();
        updateHardware(0);
    }

    private void updateHardware(int val) {
        for (GlyphManager.Glyph g : GlyphManager.Glyph.values()) GlyphManager.setBrightness(g, val);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
            isFaceDown = event.values[2] < -8.5;
        else if (event.sensor.getType() == Sensor.TYPE_PROXIMITY)
            isProximityCovered = event.values[0] < event.sensor.getMaximumRange();

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

        if (vibrator != null && vibrator.hasVibrator()) {
            long[] timings = {0, 80, 60, 120};
            int[] amplitudes = {0, 220, 0, 255};
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1));
        }

        if (SleepGuard.isBlocked(prefs)) return;
        if (wakeLock != null && !wakeLock.isHeld()) wakeLock.acquire(3000);
        String style = prefs.getString("flip_style_value", "");
        if (!style.isEmpty()) GlyphEffects.play(this, "notification", style, vibrator, prefs.getInt("brightness", 2048));

    }

    private void deactivateFlipMode() {
        isActive = false;
        audioManager.setRingerMode(originalRingerMode);
        GlyphEffects.stop();
        updateHardware(0);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        isRinging = false;
        if (isActive) audioManager.setRingerMode(originalRingerMode);
        if (sensorManager != null) sensorManager.unregisterListener(this);
        handler.removeCallbacksAndMessages(null);
        GlyphEffects.stop();
        updateHardware(0);
        super.onDestroy();
    }
}