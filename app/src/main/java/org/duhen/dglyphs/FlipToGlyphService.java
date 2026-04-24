package org.duhen.dglyphs;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Vibrator;

public class FlipToGlyphService extends Service {

    public static final String ACTION_NOTIFICATION_GLYPH = "org.duhen.dglyphs.ACTION_NOTIFICATION_GLYPH";
    public static final String ACTION_CALL_GLYPH = "org.duhen.dglyphs.ACTION_CALL_GLYPH";
    public static final String ACTION_STOP_CALL_GLYPH = "org.duhen.dglyphs.ACTION_STOP_CALL_GLYPH";

    private static final long CALL_REPEAT_INTERVAL_MS = 4_000L;
    private static final long WAKELOCK_FLIP_MS = 3_000L;
    private static final long WAKELOCK_NOTIF_MS = 3_000L;
    private static final long WAKELOCK_CALL_CYCLE_MS = 5_000L;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private FlipToGlyphSensor mSensor;
    private AudioManager mAudioManager;
    private Vibrator mVibrator;
    private PowerManager.WakeLock mWakeLock;
    private SharedPreferences mPrefs;
    private boolean mIsFlipped = false;
    private boolean mIsRinging = false;
    private int mSavedRingerMode = AudioManager.RINGER_MODE_NORMAL;
    private Runnable mCallLoopRunnable = null;

    @Override
    public void onCreate() {
        super.onCreate();
        mPrefs = getSharedPreferences(getString(R.string.pref_file), MODE_PRIVATE);
        mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        mVibrator = getSystemService(Vibrator.class);

        mWakeLock = ((PowerManager) getSystemService(POWER_SERVICE))
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dGlyphs:FlipLock");

        mSensor = new FlipToGlyphSensor(this, this::onFlip);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!isFeatureEnabled()) {
            stopSelf();
            return START_NOT_STICKY;
        }

        mSensor.enable();

        if (intent != null && intent.getAction() != null) {
            dispatchAction(intent.getAction());
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        mSensor.disable();
        stopCallLoop();
        mHandler.removeCallbacksAndMessages(null);
        GlyphEffects.stop();
        releaseWakeLock();
        GlyphManager.resetFrame();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void onFlip(boolean flipped) {
        if (flipped == mIsFlipped) return;
        mIsFlipped = flipped;
        if (flipped) {
            onFlipActivated();
        } else {
            onFlipDeactivated();
        }
    }

    private void onFlipActivated() {
        if (mPrefs.getBoolean("lockscreen_only", false) && GlyphManager.isUserActive(this)) return;

        mSavedRingerMode = mAudioManager.getRingerMode();
        mAudioManager.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);

        int flipStep = VibratorUtils.readStep(mPrefs, VibratorUtils.KEY_VIB_FLIP);
        VibratorUtils.quickTick(
                mVibrator,
                VibratorUtils.durationForStep(flipStep),
                VibratorUtils.amplitudeForStep(flipStep));

        if (!SleepGuard.isBlocked(mPrefs)) {
            acquireWakeLock(WAKELOCK_FLIP_MS);
            GlyphEffects.playFromPref(
                    this, "flip_style", GlyphEffects.FOLDER_NOTIF,
                    mVibrator, mPrefs.getInt("brightness", 2048));
        }
    }

    private void onFlipDeactivated() {
        mAudioManager.setRingerMode(mSavedRingerMode);
        GlyphEffects.stop();
        releaseWakeLock();
        GlyphManager.resetFrame();
    }

    private void dispatchAction(String action) {
        switch (action) {
            case ACTION_NOTIFICATION_GLYPH:
                triggerNotificationGlyph();
                break;
            case ACTION_CALL_GLYPH:
                startCallLoop();
                break;
            case ACTION_STOP_CALL_GLYPH:
                stopCallLoop();
                break;
        }
    }

    private void triggerNotificationGlyph() {
        if (SleepGuard.isBlocked(mPrefs)) return;
        acquireWakeLock(WAKELOCK_NOTIF_MS);
        GlyphEffects.playFromPref(
                this, "notif_style", GlyphEffects.FOLDER_NOTIF,
                mVibrator, mPrefs.getInt("brightness", 2048));
    }

    private void startCallLoop() {
        if (mIsRinging) return;
        if (SleepGuard.isBlocked(mPrefs)) return;
        mIsRinging = true;
        scheduleNextCallCycle();
    }

    private void scheduleNextCallCycle() {
        if (!mIsRinging) return;
        acquireWakeLock(WAKELOCK_CALL_CYCLE_MS);
        GlyphEffects.playFromPref(
                this, "call_style", GlyphEffects.FOLDER_CALL,
                mVibrator, mPrefs.getInt("brightness", 2048));
        mCallLoopRunnable = this::scheduleNextCallCycle;
        mHandler.postDelayed(mCallLoopRunnable, CALL_REPEAT_INTERVAL_MS);
    }

    private void stopCallLoop() {
        mIsRinging = false;
        if (mCallLoopRunnable != null) {
            mHandler.removeCallbacks(mCallLoopRunnable);
            mCallLoopRunnable = null;
        }
        GlyphEffects.stop();
        releaseWakeLock();
        GlyphManager.resetFrame();
    }

    private boolean isFeatureEnabled() {
        return mPrefs.getBoolean("master_allow", false)
                && mPrefs.getBoolean("flip_enabled", false);
    }

    private void acquireWakeLock(long timeoutMs) {
        if (mWakeLock != null && !mWakeLock.isHeld()) mWakeLock.acquire(timeoutMs);
    }

    private void releaseWakeLock() {
        if (mWakeLock != null && mWakeLock.isHeld()) mWakeLock.release();
    }

}