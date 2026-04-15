package org.duhen.dglyphs;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class BatteryGlyphService extends Service {

    private static final float ACCEL_THRESHOLD = 10.0f;
    private static final float ZFACEDOWN_THRESHOLD = -5.0f;
    private SharedPreferences prefs;
    private BatteryManager batteryManager;
    private SensorManager sensorManager;
    private PowerManager powerManager;
    private Sensor accelerometer;
    private ExecutorService executor;
    private volatile Future<?> animationFuture;
    private volatile boolean isCharging = false;
    private final SensorEventListener sensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            float accel = (float) Math.sqrt(x * x + y * y + z * z);

            if (accel > ACCEL_THRESHOLD
                    && z <= ZFACEDOWN_THRESHOLD
                    && !powerManager.isInteractive()) {
                playChargingAnimation(false);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };
    private final BroadcastReceiver powerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_POWER_CONNECTED.equals(intent.getAction())) {
                onPowerConnected();
            } else if (Intent.ACTION_POWER_DISCONNECTED.equals(intent.getAction())) {
                onPowerDisconnected();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(getString(R.string.pref_file), MODE_PRIVATE);
        batteryManager = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        executor = Executors.newSingleThreadExecutor();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        registerReceiver(powerReceiver, filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Intent sticky = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (sticky != null) {
            int status = sticky.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;
            if (charging) onPowerConnected();
        }
        return START_STICKY;
    }

    private void onPowerConnected() {
        isCharging = true;
        playChargingAnimation(true);
        sensorManager.registerListener(sensorListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void onPowerDisconnected() {
        isCharging = false;
        sensorManager.unregisterListener(sensorListener);
        cancelAnimation();
        turnOff();
    }

    private void playChargingAnimation(boolean wait) {
        if (!prefs.getBoolean("master_allow", false)) return;
        if (SleepGuard.isBlocked(prefs)) return;

        if (animationFuture != null && !animationFuture.isDone()) {
            if (wait) {
                try {
                    animationFuture.get();
                } catch (Exception ignored) {
                }
            } else {
                return;
            }
        }

        final int level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);

        animationFuture = executor.submit(() -> {
            int brightness = prefs.getInt("brightness", 2048);
            int segmentsToLight = Math.max(1, (level * 8) / 100);

            try {
                GlyphManager.setBrightness(GlyphManager.Glyph.DOT, brightness);

                int[] frame = new int[15];
                frame[6] = brightness;

                for (int s = 0; s < segmentsToLight; s++) {
                    if (!isCharging) throw new InterruptedException();
                    frame[7 + s] = brightness;
                    GlyphManager.setFrame(frame);
                    SystemClock.sleep(60);
                }

                SystemClock.sleep(2000);

                for (int b = brightness; b >= 0; b -= 200) {
                    if (!isCharging) throw new InterruptedException();
                    GlyphManager.setBrightness(GlyphManager.Glyph.DOT, b);
                    for (int i = 7; i < 15; i++) if (frame[i] > 0) frame[i] = b;
                    GlyphManager.setFrame(frame);
                    SystemClock.sleep(20);
                }
            } catch (InterruptedException e) {
                turnOff();
            } finally {
                turnOff();
            }
        });
    }

    private void cancelAnimation() {
        if (animationFuture != null) {
            animationFuture.cancel(true);
            animationFuture = null;
        }
    }

    private void turnOff() {
        GlyphManager.setBrightness(GlyphManager.Glyph.LINE, 0);
        GlyphManager.setBrightness(GlyphManager.Glyph.DOT, 0);
    }

    @Override
    public void onDestroy() {
        isCharging = false;
        unregisterReceiver(powerReceiver);
        sensorManager.unregisterListener(sensorListener);
        cancelAnimation();
        turnOff();
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}