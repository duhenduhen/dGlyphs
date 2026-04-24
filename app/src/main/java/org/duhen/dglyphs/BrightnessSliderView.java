package org.duhen.dglyphs;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.AttrRes;

public class BrightnessSliderView extends View {

    public interface OnChangeListener {
        void onValueChange(BrightnessSliderView slider, float value, boolean fromUser);
    }

    private float value = 1f;
    private OnChangeListener changeListener;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public BrightnessSliderView(Context context) { super(context); }
    public BrightnessSliderView(Context context, AttributeSet attrs) { super(context, attrs); }
    public BrightnessSliderView(Context context, AttributeSet attrs, int defStyle) { super(context, attrs, defStyle); }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    private int getThemeColor(@AttrRes int attrRes) {
        TypedValue typedValue = new TypedValue();
        if (getContext().getTheme().resolveAttribute(attrRes, typedValue, true)) {
            return typedValue.data;
        }
        return Color.WHITE;
    }

    public float getValue() { return value; }

    public void setValue(float v) {
        value = Math.max(1f, Math.min(4f, Math.round(v)));
        invalidate();
    }

    public void addOnChangeListener(OnChangeListener l) { changeListener = l; }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setAlpha(enabled ? 1f : 0.5f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cy = getHeight() / 2f;
        float pad = dp(21f);
        float w = getWidth() - pad * 2;

        paint.setColor(getThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant));
        paint.setStrokeWidth(dp(2.5f));
        paint.setStrokeCap(Paint.Cap.ROUND);

        canvas.drawLine(pad, cy, pad + w, cy, paint);

        for (int i = 0; i < 4; i++) {
            float x = pad + w * i / 3f;
            float r = (i == (int)(value - 1)) ? dp(9f) : dp(3f);
            canvas.drawCircle(x, cy, r, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
            float pad = dp(21f);
            float w = getWidth() - pad * 2;
            float frac = Math.max(0f, Math.min(1f, (event.getX() - pad) / w));
            float newValue = Math.round(frac * 3) + 1f;
            if (newValue != value) {
                value = newValue;
                invalidate();
                if (changeListener != null) changeListener.onValueChange(this, value, true);
            }
            return true;
        }
        return super.onTouchEvent(event);
    }
}