package org.duhen.dglyphs;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.color.DynamicColors;

public class ExtraActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private Vibrator vibrator;

    private TextView textTorchBrightnessLevel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_extra);

        prefs = getSharedPreferences(getString(R.string.pref_file), MODE_PRIVATE);
        vibrator = getSystemService(Vibrator.class);

        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        BrightnessSliderView sliderTorch = findViewById(R.id.sliderTorchBrightness);
        textTorchBrightnessLevel = findViewById(R.id.textTorchBrightnessLevel);

        int savedBrightness = prefs.getInt("torch_brightness", 2048);
        float position = mapBrightnessToPosition(savedBrightness);
        sliderTorch.setValue(position);
        updateTorchBrightnessLabel(position);

        sliderTorch.addOnChangeListener((slider, value, fromUser) -> {
            updateTorchBrightnessLabel(value);
            if (fromUser) {
                quickTick(10, 50);
                prefs.edit().putInt("torch_brightness", mapPositionToBrightness(value)).apply();
            }
        });
    }

    private void updateTorchBrightnessLabel(float position) {
        String[] labels = {
                getString(R.string.brightness_low),
                getString(R.string.brightness_medium),
                getString(R.string.brightness_high),
                getString(R.string.brightness_max)
        };
        int idx = Math.max(0, Math.min((int) position - 1, labels.length - 1));
        textTorchBrightnessLevel.setText(labels[idx]);
    }

    private int mapPositionToBrightness(float p) {
        return p == 2 ? 1024 : p == 3 ? 2048 : p == 4 ? 4095 : 512;
    }

    private float mapBrightnessToPosition(int b) {
        return b >= 4095 ? 4f : b >= 2048 ? 3f : b >= 1024 ? 2f : 1f;
    }

    private void quickTick(int d, int a) {
        if (vibrator != null && vibrator.hasVibrator())
            vibrator.vibrate(VibrationEffect.createOneShot(d, a));
    }
}