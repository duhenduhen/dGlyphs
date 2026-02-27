package org.duhen.dglyphs;

import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.util.HashSet;
import java.util.Set;

public class SleepModeActivity extends AppCompatActivity {
    private SharedPreferences prefs;
    private Vibrator vibrator;
    private TextView tvStart, tvEnd;
    private Set<String> selectedDays;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sleep_mode);

        prefs = getSharedPreferences(getString(R.string.pref_file), MODE_PRIVATE);
        vibrator = getSystemService(Vibrator.class);
        selectedDays = new HashSet<>(prefs.getStringSet("sleep_days", new HashSet<>()));

        initViews();
    }

    private void initViews() {
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        tvStart = findViewById(R.id.tvStartTime);
        tvEnd = findViewById(R.id.tvEndTime);

        MaterialSwitch sw = findViewById(R.id.switchSleepInternal);
        sw.setChecked(prefs.getBoolean("sleep_mode_enabled", false));
        sw.setOnCheckedChangeListener((v, chk) -> {
            quickTick(20, 100);
            prefs.edit().putBoolean("sleep_mode_enabled", chk).apply();
        });

        tvStart.setText(prefs.getString("sleep_start", "23:00"));
        tvEnd.setText(prefs.getString("sleep_end", "07:00"));

        findViewById(R.id.btnStartTime).setOnClickListener(v -> showPicker(true));
        findViewById(R.id.btnEndTime).setOnClickListener(v -> showPicker(false));

        int[] ids = {R.id.day_1, R.id.day_2, R.id.day_3, R.id.day_4, R.id.day_5, R.id.day_6, R.id.day_7};
        for (int i = 0; i < ids.length; i++) {
            MaterialButton btn = findViewById(ids[i]);
            final String dId = String.valueOf(i + 1);
            updateDayUI(btn, selectedDays.contains(dId));

            btn.setOnClickListener(v -> {
                quickTick(10, 80);
                if (selectedDays.contains(dId)) selectedDays.remove(dId);
                else selectedDays.add(dId);

                updateDayUI(btn, selectedDays.contains(dId));
                prefs.edit().putStringSet("sleep_days", new HashSet<>(selectedDays)).apply();
            });
        }
    }

    private void updateDayUI(MaterialButton btn, boolean sel) {
        int colorBg = MaterialColors.getColor(btn, sel ?
                com.google.android.material.R.attr.colorPrimaryContainer :
                com.google.android.material.R.attr.colorSurfaceContainerHigh);
        int colorTxt = MaterialColors.getColor(btn, sel ?
                com.google.android.material.R.attr.colorSurface :
                com.google.android.material.R.attr.colorOnSurfaceVariant);

        btn.setBackgroundTintList(ColorStateList.valueOf(colorBg));
        btn.setTextColor(colorTxt);
    }

    private void showPicker(boolean isStart) {
        TextView target = isStart ? tvStart : tvEnd;
        String[] time = target.getText().toString().split(":");

        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(Integer.parseInt(time[0]))
                .setMinute(Integer.parseInt(time[1]))
                .setTitleText(isStart ? R.string.sleep_picker_start : R.string.sleep_picker_end)
                .build();

        picker.addOnPositiveButtonClickListener(v -> {
            String val = String.format("%02d:%02d", picker.getHour(), picker.getMinute());
            target.setText(val);
            prefs.edit().putString(isStart ? "sleep_start" : "sleep_end", val).apply();
            quickTick(15, 80);
        });
        picker.show(getSupportFragmentManager(), "picker");
    }

    private void quickTick(int d, int a) {
        if (vibrator != null && vibrator.hasVibrator())
            vibrator.vibrate(VibrationEffect.createOneShot(d, a));
    }
}