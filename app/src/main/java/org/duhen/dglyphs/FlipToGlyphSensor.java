package org.duhen.dglyphs;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;

public class FlipToGlyphSensor implements SensorEventListener {

    private static final float MOVING_AVERAGE_WEIGHT = 0.7f;
    private static final float ACCELERATION_THRESHOLD = 0.3f;
    private static final float Z_THRESHOLD_STRICT = -9.0f;
    private static final float Z_THRESHOLD_LENIENT = Z_THRESHOLD_STRICT + 1.0f;
    private static final Duration TIME_THRESHOLD = Duration.ofMillis(1_000L);
    private static final Duration MOTION_WINDOW = Duration.ofMillis(1_000L);
    private final ExponentialMovingAverage mXyAccel =
            new ExponentialMovingAverage(MOVING_AVERAGE_WEIGHT);
    private final ExponentialMovingAverage mZAccel =
            new ExponentialMovingAverage(MOVING_AVERAGE_WEIGHT);
    private final Consumer<Boolean> mOnFlip;
    private final SensorManager mSensorManager;
    private final Sensor mAccelerometer;
    private final Sensor mProximity;
    private final Handler mHandler;
    private boolean mIsFlipped = false;
    private boolean mAccelFaceDown = false;
    private boolean mProximityCovered = false;
    private float mPrevXyAccelSample = 0f;
    private long mLastMotionTimeNs = 0L;
    private Runnable mDwellRunnable = null;

    public FlipToGlyphSensor(@NonNull Context context, @NonNull Consumer<Boolean> onFlip) {
        mOnFlip = Objects.requireNonNull(onFlip);
        mSensorManager = context.getSystemService(SensorManager.class);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, false);
        mProximity = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        mHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                handleAccelerometer(event);
                break;
            case Sensor.TYPE_PROXIMITY:
                handleProximity(event);
                break;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void handleAccelerometer(SensorEvent event) {
        final float x = event.values[0];
        final float y = event.values[1];

        mXyAccel.update(x * x + y * y);
        mZAccel.update(event.values[2]);

        if (Math.abs(mXyAccel.get() - mPrevXyAccelSample) > ACCELERATION_THRESHOLD) {
            mPrevXyAccelSample = mXyAccel.get();
            mLastMotionTimeNs = event.timestamp;
        }
        final boolean moving =
                (event.timestamp - mLastMotionTimeNs) <= MOTION_WINDOW.toNanos();

        final float zThreshold = mIsFlipped ? Z_THRESHOLD_LENIENT : Z_THRESHOLD_STRICT;
        mAccelFaceDown = mZAccel.get() < zThreshold;

        evaluateFlipState(moving);
    }

    private void handleProximity(SensorEvent event) {
        mProximityCovered = event.values[0] < event.sensor.getMaximumRange();
        evaluateFlipState(false);
    }

    private void evaluateFlipState(boolean moving) {
        final boolean shouldBeFlipped = mAccelFaceDown && mProximityCovered && !moving;

        if (shouldBeFlipped && !mIsFlipped) {
            scheduleDwellConfirmation();
        } else if (!shouldBeFlipped) {
            cancelDwellConfirmation();
            if (mIsFlipped) dispatchFlip(false);
        }
    }

    private void scheduleDwellConfirmation() {
        if (mDwellRunnable != null) return;

        mDwellRunnable = () -> {
            mDwellRunnable = null;
            if (mAccelFaceDown && mProximityCovered && !mIsFlipped) {
                dispatchFlip(true);
            }
        };
        mHandler.postDelayed(mDwellRunnable, TIME_THRESHOLD.toMillis());
    }

    private void cancelDwellConfirmation() {
        if (mDwellRunnable != null) {
            mHandler.removeCallbacks(mDwellRunnable);
            mDwellRunnable = null;
        }
    }

    private void dispatchFlip(boolean flipped) {
        mIsFlipped = flipped;
        mOnFlip.accept(flipped);
    }

    public void enable() {
        resetState();
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_UI);
        if (mProximity != null) {
            mSensorManager.registerListener(this, mProximity, SensorManager.SENSOR_DELAY_UI);
        }
    }

    public void disable() {
        cancelDwellConfirmation();
        if (mIsFlipped) dispatchFlip(false);
        mSensorManager.unregisterListener(this);
    }

    private void resetState() {
        mAccelFaceDown = false;
        mProximityCovered = false;
        mXyAccel.reset();
        mZAccel.reset();
        mPrevXyAccelSample = 0f;
        mLastMotionTimeNs = 0L;
    }

    private static final class ExponentialMovingAverage {
        private final float mAlpha;
        private float mValue;

        ExponentialMovingAverage(float alpha) {
            mAlpha = alpha;
            mValue = 0f;
        }

        void update(float sample) {
            mValue = sample + mAlpha * (mValue - sample);
        }

        float get() {
            return mValue;
        }

        void reset() {
            mValue = 0f;
        }
    }
}